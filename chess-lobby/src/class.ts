// Class marks comparison — D1-backed routes.
//
// - GET    /class/:classKey       → returns precomputed stats JSON
// - POST   /class/marks            → upsert own marks (body = ClassMarksUploadBody)
// - DELETE /class/me               → wipe my row (anonId derived from auth UID)
//
// All routes require a verified Firebase ID token. anonId = uid (Firebase
// auth UIDs are 28-char opaque strings, suitable as PK). class_key is
// supplied by the client and normalised (uppercase, trim).

import type {
  Env,
  ClassMarksUploadBody,
  ClassStatsResponse,
  ClassSubjectStats,
  ClassOverallStats,
} from "./types";

const MIN_CLASS_SIZE = 15;

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type",
  "Access-Control-Max-Age": "86400",
};

function jsonResponse(
  status: number,
  body: unknown,
  extraHeaders: Record<string, string> = {},
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...CORS_HEADERS,
      ...extraHeaders,
    },
  });
}

function normaliseClassKey(raw: string): string {
  return raw.toUpperCase().trim();
}

// POST /class/marks
export async function handleUploadMarks(
  request: Request,
  env: Env,
  uid: string,
): Promise<Response> {
  let body: ClassMarksUploadBody;
  try {
    body = (await request.json()) as ClassMarksUploadBody;
  } catch {
    return jsonResponse(400, { error: "invalid_json" });
  }

  if (!body.classKey || typeof body.classKey !== "string") {
    return jsonResponse(400, { error: "missing_class_key" });
  }
  if (!body.subjects || typeof body.subjects !== "object") {
    return jsonResponse(400, { error: "missing_subjects" });
  }
  if (typeof body.overallAvg !== "number" || isNaN(body.overallAvg)) {
    return jsonResponse(400, { error: "missing_overall_avg" });
  }

  const classKey = normaliseClassKey(body.classKey);
  const subjectsJson = JSON.stringify(body.subjects);
  const now = Date.now();

  try {
    await env.CLASS_MARKS_DB.prepare(
      `INSERT INTO class_marks (anon_id, class_key, subjects, overall_avg, uploaded_at)
       VALUES (?1, ?2, ?3, ?4, ?5)
       ON CONFLICT(anon_id) DO UPDATE SET
         class_key = excluded.class_key,
         subjects = excluded.subjects,
         overall_avg = excluded.overall_avg,
         uploaded_at = excluded.uploaded_at`,
    )
      .bind(uid, classKey, subjectsJson, body.overallAvg, now)
      .run();
  } catch (err) {
    console.error("D1 upsert failed:", err);
    return jsonResponse(500, { error: "db_write_failed" });
  }

  return jsonResponse(200, { ok: true, uploadedAt: now });
}

// DELETE /class/me
// Wipes BOTH class_marks row + pwa_creds row (so server-side cron stops
// polling for this user immediately when they delete their data).
export async function handleDeleteMe(
  _request: Request,
  env: Env,
  uid: string,
): Promise<Response> {
  try {
    const marksResult = await env.CLASS_MARKS_DB
      .prepare("DELETE FROM class_marks WHERE anon_id = ?1")
      .bind(uid)
      .run();
    await env.CLASS_MARKS_DB
      .prepare("DELETE FROM pwa_creds WHERE anon_id = ?1")
      .bind(uid)
      .run();
    return jsonResponse(200, { ok: true, deleted: marksResult.meta.changes });
  } catch (err) {
    console.error("D1 delete failed:", err);
    return jsonResponse(500, { error: "db_delete_failed" });
  }
}

// GET /class/:classKey
export async function handleGetClassStats(
  request: Request,
  env: Env,
  uid: string,
  rawClassKey: string,
): Promise<Response> {
  const classKey = normaliseClassKey(decodeURIComponent(rawClassKey));
  if (!classKey) {
    return jsonResponse(400, { error: "missing_class_key" });
  }

  // Fetch all rows for the class.
  let rows: Array<{
    anon_id: string;
    subjects: string;
    overall_avg: number;
  }>;
  try {
    const result = await env.CLASS_MARKS_DB
      .prepare(
        "SELECT anon_id, subjects, overall_avg FROM class_marks WHERE class_key = ?1",
      )
      .bind(classKey)
      .all<{ anon_id: string; subjects: string; overall_avg: number }>();
    rows = result.results ?? [];
  } catch (err) {
    console.error("D1 read failed:", err);
    return jsonResponse(500, { error: "db_read_failed" });
  }

  const studentCount = rows.length;

  // Below threshold: return count only, no stats. Client renders "need N
  // more classmates".
  if (studentCount < MIN_CLASS_SIZE) {
    const partial: ClassStatsResponse = {
      classKey,
      studentCount,
      overall: { avg: 0, min: 0, max: 0 },
      subjects: {},
      overallHistogram: new Array(10).fill(0),
    };
    return jsonResponse(200, partial);
  }

  // Compute overall stats + your-rank from overall_avg array.
  const overallAvgs = rows.map((r) => r.overall_avg);
  const overallMin = Math.min(...overallAvgs);
  const overallMax = Math.max(...overallAvgs);
  const overallMean =
    overallAvgs.reduce((s, v) => s + v, 0) / overallAvgs.length;

  // 10-bucket histogram for overall (0..100 split into 10 bands of 10%).
  const overallHistogram = new Array(10).fill(0);
  for (const v of overallAvgs) {
    const bucket = Math.min(9, Math.max(0, Math.floor(v / 10)));
    overallHistogram[bucket] += 1;
  }

  // Per-subject aggregation: build a map subjectCode → array of student totals.
  const subjectTotals: Record<string, number[]> = {};
  let yourSubjectMarks: Record<string, number> = {};
  let yourOverall: number | undefined;
  for (const row of rows) {
    let parsed: Record<string, { total?: number; status?: string }>;
    try {
      parsed = JSON.parse(row.subjects);
    } catch {
      continue;
    }
    for (const [subj, data] of Object.entries(parsed)) {
      if (
        typeof data.total !== "number" ||
        isNaN(data.total) ||
        data.status === "NOT_ENTERED"
      ) {
        continue;
      }
      if (!subjectTotals[subj]) subjectTotals[subj] = [];
      subjectTotals[subj].push(data.total);
    }
    if (row.anon_id === uid) {
      yourOverall = row.overall_avg;
      for (const [subj, data] of Object.entries(parsed)) {
        if (typeof data.total === "number") {
          yourSubjectMarks[subj] = data.total;
        }
      }
    }
  }

  // Per-subject stats.
  const subjects: Record<string, ClassSubjectStats> = {};
  for (const [subj, totals] of Object.entries(subjectTotals)) {
    if (totals.length === 0) continue;
    const min = Math.min(...totals);
    const max = Math.max(...totals);
    const avg = totals.reduce((s, v) => s + v, 0) / totals.length;
    const histogram = new Array(10).fill(0);
    for (const v of totals) {
      // Subject totals can exceed 100 (e.g. 50-mark CA scaled). Normalise via
      // local min/max for histogram. Avoids out-of-range bucket misses.
      const range = max - min;
      const norm = range > 0 ? (v - min) / range : 0.5;
      const bucket = Math.min(9, Math.max(0, Math.floor(norm * 10)));
      histogram[bucket] += 1;
    }
    subjects[subj] = {
      avg,
      min,
      max,
      yourMark: yourSubjectMarks[subj],
      histogram,
    };
  }

  // Rank + percentile from your overall.
  let yourRank: number | undefined;
  let yourPercentile: number | undefined;
  if (typeof yourOverall === "number") {
    const sortedDesc = [...overallAvgs].sort((a, b) => b - a);
    const idx = sortedDesc.findIndex((v) => v <= yourOverall!);
    yourRank = idx >= 0 ? idx + 1 : sortedDesc.length;
    const belowCount = overallAvgs.filter((v) => v < yourOverall!).length;
    yourPercentile = Math.round((belowCount / overallAvgs.length) * 100);
  }

  const overall: ClassOverallStats = {
    avg: round(overallMean),
    min: round(overallMin),
    max: round(overallMax),
  };

  const response: ClassStatsResponse = {
    classKey,
    studentCount,
    overall,
    subjects,
    yourRank,
    yourPercentile,
    overallHistogram,
  };
  return jsonResponse(200, response);
}

function round(x: number): number {
  return Math.round(x * 100) / 100;
}
