// PWA server-side cron polling — encrypted SIS credential storage.
//
// Routes:
// - POST   /pwa/register  → store / update encrypted creds + classKey
// - DELETE /pwa/register  → wipe my pwa_creds row (also wipes class_marks)
//
// Auth: Firebase ID token (same scheme chess + /class routes use).
// anon_id = Firebase UID. Roll number + password never logged. Password is
// AES-GCM encrypted with the master key from env.PWA_CRED_KEY before storage.

import type { Env } from "./types";

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, DELETE, OPTIONS",
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

/* ───────── AES-GCM helpers (WebCrypto) ───────── */

const ENC_ALG = { name: "AES-GCM", length: 256 };

async function importMasterKey(rawBase64: string): Promise<CryptoKey> {
  const raw = base64ToBytes(rawBase64);
  return crypto.subtle.importKey(
    "raw",
    raw,
    ENC_ALG,
    false,
    ["encrypt", "decrypt"],
  );
}

export async function encryptPassword(plaintext: string, masterKeyB64: string): Promise<string> {
  const key = await importMasterKey(masterKeyB64);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const enc = new TextEncoder().encode(plaintext);
  const ct = new Uint8Array(
    await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, enc),
  );
  // Output = base64(iv || ciphertext+tag)
  const out = new Uint8Array(iv.length + ct.length);
  out.set(iv, 0);
  out.set(ct, iv.length);
  return bytesToBase64(out);
}

export async function decryptPassword(stored: string, masterKeyB64: string): Promise<string> {
  const key = await importMasterKey(masterKeyB64);
  const bytes = base64ToBytes(stored);
  const iv = bytes.slice(0, 12);
  const ct = bytes.slice(12);
  const pt = await crypto.subtle.decrypt({ name: "AES-GCM", iv }, key, ct);
  return new TextDecoder().decode(pt);
}

function base64ToBytes(b64: string): Uint8Array {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function bytesToBase64(bytes: Uint8Array): string {
  let s = "";
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s);
}

/* ───────── Route handlers ───────── */

interface RegisterBody {
  rollNumber: string;
  password: string;
  classKey: string;
}

export async function handlePwaRegister(
  request: Request,
  env: Env,
  uid: string,
): Promise<Response> {
  if (!env.PWA_CRED_KEY) {
    return jsonResponse(500, {
      error: "server_misconfigured",
      reason: "PWA_CRED_KEY secret not set",
    });
  }

  let body: RegisterBody;
  try {
    body = (await request.json()) as RegisterBody;
  } catch {
    return jsonResponse(400, { error: "invalid_json" });
  }
  if (!body.rollNumber || !body.password || !body.classKey) {
    return jsonResponse(400, { error: "missing_fields" });
  }
  if (
    typeof body.rollNumber !== "string" ||
    typeof body.password !== "string" ||
    typeof body.classKey !== "string"
  ) {
    return jsonResponse(400, { error: "invalid_field_type" });
  }

  const encPassword = await encryptPassword(body.password, env.PWA_CRED_KEY);
  const classKey = body.classKey.toUpperCase().trim();
  const now = Date.now();

  try {
    await env.CLASS_MARKS_DB
      .prepare(
        `INSERT INTO pwa_creds
           (anon_id, roll_number, enc_password, class_key, created_at, last_synced_at, last_error)
         VALUES (?1, ?2, ?3, ?4, ?5, NULL, NULL)
         ON CONFLICT(anon_id) DO UPDATE SET
           roll_number = excluded.roll_number,
           enc_password = excluded.enc_password,
           class_key = excluded.class_key,
           last_error = NULL`,
      )
      .bind(uid, body.rollNumber, encPassword, classKey, now)
      .run();
  } catch (err) {
    console.error("pwa_creds upsert failed:", err);
    return jsonResponse(500, { error: "db_write_failed" });
  }
  return jsonResponse(200, { ok: true, registeredAt: now });
}

export async function handlePwaUnregister(
  _request: Request,
  env: Env,
  uid: string,
): Promise<Response> {
  try {
    const result = await env.CLASS_MARKS_DB
      .prepare("DELETE FROM pwa_creds WHERE anon_id = ?1")
      .bind(uid)
      .run();
    return jsonResponse(200, { ok: true, deleted: result.meta.changes });
  } catch (err) {
    console.error("pwa_creds delete failed:", err);
    return jsonResponse(500, { error: "db_delete_failed" });
  }
}
