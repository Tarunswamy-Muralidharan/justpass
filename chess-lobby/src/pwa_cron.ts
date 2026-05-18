// PWA cron — runs on a Workers scheduled trigger. Picks the 16
// oldest-synced pwa_creds rows and refreshes their class_marks entry.
//
// Cron: every 10 min via wrangler.toml [triggers] crons.
// 16 users/tick × 144 ticks/day = 2304 syncs/day at free-tier subrequest
// budget (each user costs ~3 subrequests = Keycloak auth + SIS fetch +
// D1 upsert). Stays comfortably under the 50-subrequest-per-request limit
// (16 × 3 = 48).
//
// Each row's last_synced_at is updated on success; failures write
// last_error so we can debug stuck users without keeping a separate log.

import { decryptPassword } from "./pwa";
import type { Env } from "./types";

const BATCH_SIZE = 16;
const KEYCLOAK_TOKEN_URL =
  "https://accounts.psgitech.ac.in/realms/itech/protocol/openid-connect/token";
const KEYCLOAK_CLIENT_ID = "ies_sis";
const SIS_BASE = "https://laudea.psgitech.ac.in";

interface PwaCredsRow {
  anon_id: string;
  roll_number: string;
  enc_password: string;
  class_key: string;
}

/**
 * Entry point called from the Worker's `scheduled()` export.
 * Picks a batch and processes them sequentially. Sequential keeps the
 * subrequest budget predictable; we have plenty of headroom on time.
 */
export async function runPwaSyncTick(env: Env): Promise<void> {
  if (!env.PWA_CRED_KEY) {
    console.error("PWA_CRED_KEY not set, skipping cron");
    return;
  }

  let rows: PwaCredsRow[];
  try {
    const result = await env.CLASS_MARKS_DB
      .prepare(
        `SELECT anon_id, roll_number, enc_password, class_key
         FROM pwa_creds
         ORDER BY COALESCE(last_synced_at, 0) ASC
         LIMIT ?1`,
      )
      .bind(BATCH_SIZE)
      .all<PwaCredsRow>();
    rows = result.results ?? [];
  } catch (err) {
    console.error("pwa cron read failed:", err);
    return;
  }

  if (rows.length === 0) return;

  for (const row of rows) {
    await processOneUser(env, row);
  }
}

async function processOneUser(env: Env, row: PwaCredsRow): Promise<void> {
  const now = Date.now();
  let password: string;
  try {
    password = await decryptPassword(row.enc_password, env.PWA_CRED_KEY);
  } catch (err) {
    await recordError(env, row.anon_id, `decrypt: ${(err as Error).message}`);
    return;
  }

  let accessToken: string;
  try {
    accessToken = await keycloakLogin(row.roll_number, password);
  } catch (err) {
    await recordError(env, row.anon_id, `auth: ${(err as Error).message}`);
    return;
  }

  let courseMarks: SisCourseMarks[];
  try {
    courseMarks = await fetchCAMarks(row.roll_number, accessToken);
  } catch (err) {
    await recordError(env, row.anon_id, `marks: ${(err as Error).message}`);
    return;
  }

  const built = buildUploadBody(courseMarks);
  if (built === null) {
    // No subjects with valid totals — count as success but record reason.
    await recordError(env, row.anon_id, "no_subjects_yet");
    return;
  }

  try {
    await env.CLASS_MARKS_DB
      .prepare(
        `INSERT INTO class_marks (anon_id, class_key, subjects, overall_avg, uploaded_at)
         VALUES (?1, ?2, ?3, ?4, ?5)
         ON CONFLICT(anon_id) DO UPDATE SET
           class_key = excluded.class_key,
           subjects = excluded.subjects,
           overall_avg = excluded.overall_avg,
           uploaded_at = excluded.uploaded_at`,
      )
      .bind(row.anon_id, row.class_key, JSON.stringify(built.subjects), built.overallAvg, now)
      .run();
    await env.CLASS_MARKS_DB
      .prepare("UPDATE pwa_creds SET last_synced_at = ?1, last_error = NULL WHERE anon_id = ?2")
      .bind(now, row.anon_id)
      .run();
  } catch (err) {
    await recordError(env, row.anon_id, `d1: ${(err as Error).message}`);
  }
}

async function recordError(env: Env, anonId: string, msg: string): Promise<void> {
  try {
    await env.CLASS_MARKS_DB
      .prepare("UPDATE pwa_creds SET last_error = ?1 WHERE anon_id = ?2")
      .bind(msg.slice(0, 200), anonId)
      .run();
  } catch (_) {
    // best-effort; nothing to do if even the error update fails
  }
}

/* ─── Keycloak password grant ─── */

async function keycloakLogin(rollNumber: string, password: string): Promise<string> {
  const form = new URLSearchParams();
  form.set("grant_type", "password");
  form.set("client_id", KEYCLOAK_CLIENT_ID);
  form.set("username", rollNumber);
  form.set("password", password);
  form.set("scope", "openid offline_access");

  const resp = await fetch(KEYCLOAK_TOKEN_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      "Accept": "application/json",
    },
    body: form.toString(),
  });
  if (!resp.ok) {
    throw new Error(`keycloak http ${resp.status}`);
  }
  const json = (await resp.json()) as { access_token?: string; error?: string };
  if (!json.access_token) {
    throw new Error(`keycloak: ${json.error ?? "no_access_token"}`);
  }
  return json.access_token;
}

/* ─── SIS CA marks fetch ─── */

interface SisCourseMarks {
  courseCode: string;
  testDetails: {
    components?: Array<{
      name?: string;
      marks?: { actual?: { max?: unknown; secured?: unknown } };
    }>;
    total: { actual: { max: unknown; secured: unknown } };
  };
}

async function fetchCAMarks(rollNumber: string, accessToken: string): Promise<SisCourseMarks[]> {
  const url = `${SIS_BASE}/sis/students/CAMarks/${encodeURIComponent(rollNumber)}`;
  const resp = await fetch(url, {
    headers: {
      "Authorization": `Bearer ${accessToken}`,
      "Accept": "application/json",
      // Real browser UA — SIS rejects non-browser callers (commit 559c01c).
      "User-Agent":
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36",
    },
  });
  if (!resp.ok) {
    throw new Error(`sis http ${resp.status}`);
  }
  return (await resp.json()) as SisCourseMarks[];
}

/* ─── Body builder — mirrors ClassMarksRepository.buildUploadBody on Android ─── */

interface UploadBuilt {
  subjects: Record<string, {
    ca1?: number;
    ca2?: number;
    ca3?: number;
    total?: number;
    status?: string;
  }>;
  overallAvg: number;
}

function num(v: unknown): number | undefined {
  if (typeof v === "number" && !isNaN(v)) return v;
  if (typeof v === "string") {
    const parsed = parseFloat(v);
    return isNaN(parsed) ? undefined : parsed;
  }
  return undefined;
}

function isNE(v: unknown): boolean {
  return typeof v === "string" && v === "NE";
}

function buildUploadBody(courseMarks: SisCourseMarks[]): UploadBuilt | null {
  const subjects: UploadBuilt["subjects"] = {};
  const percents: number[] = [];
  for (const c of courseMarks) {
    const t = c.testDetails?.total?.actual;
    if (!t || isNE(t.secured)) continue;
    const securedNum = num(t.secured);
    const maxNum = num(t.max);
    if (securedNum === undefined || maxNum === undefined || maxNum <= 0) continue;

    const ca1 = num(c.testDetails.components?.find((x) => x.name?.toUpperCase().includes("CA1"))?.marks?.actual?.secured);
    const ca2 = num(c.testDetails.components?.find((x) => x.name?.toUpperCase().includes("CA2"))?.marks?.actual?.secured);
    const ca3 = num(c.testDetails.components?.find((x) => x.name?.toUpperCase().includes("CA3"))?.marks?.actual?.secured);

    subjects[c.courseCode] = {
      ca1,
      ca2,
      ca3,
      total: securedNum,
      status: "ENTERED",
    };
    percents.push((securedNum / maxNum) * 100);
  }
  if (percents.length === 0) return null;
  const overallAvg = percents.reduce((s, v) => s + v, 0) / percents.length;
  return { subjects, overallAvg };
}
