// Admin-gated routes — currently just announcement publishing.
//
// Auth model:
//   1. Caller provides a Firebase ID token via Authorization: Bearer.
//   2. Worker verifies the token (already done at the worker.ts level).
//   3. Worker checks `admin_uids/{uid}` exists in Firestore — same gate the
//      Android Firestore rules use. We hit Firestore via REST API with a
//      service-account access token (Cloud Datastore User role).
//   4. If admin → forward to Remote Config update.

import {
  getGoogleAccessToken,
  getProjectIdFromServiceAccount,
} from "./google_auth";
import {
  updateAnnouncement,
  REMOTE_CONFIG_SCOPE,
  type AnnouncementUpdate,
} from "./remoteconfig";
import type { Env } from "./types";

const FIRESTORE_SCOPE = "https://www.googleapis.com/auth/datastore";

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type",
  "Access-Control-Max-Age": "86400",
};

function jsonResponse(
  status: number,
  body: unknown,
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...CORS_HEADERS,
    },
  });
}

async function isAdminUid(
  uid: string,
  projectId: string,
  accessToken: string,
): Promise<boolean> {
  const url =
    `https://firestore.googleapis.com/v1/projects/${projectId}` +
    `/databases/(default)/documents/admin_uids/${encodeURIComponent(uid)}`;
  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  // 200 → doc exists, 404 → not admin, anything else → treat as not admin
  // but log enough context to debug.
  if (res.status === 200) return true;
  if (res.status === 404) return false;
  const body = await res.text().catch(() => "");
  console.warn(`isAdminUid: unexpected ${res.status} for uid=${uid}: ${body}`);
  return false;
}

function sanitiseString(v: unknown, max = 500): string {
  if (typeof v !== "string") return "";
  // Limit length defensively; Remote Config has no hard cap but huge values
  // are pointless for a dialog and would explode the template.
  return v.slice(0, max);
}

/**
 * POST /admin/announcement
 * Body: { active: boolean, id: string, title: string, message: string }
 * Returns: { ok: true } on success.
 */
export async function handleUpdateAnnouncement(
  request: Request,
  env: Env,
  callerUid: string,
): Promise<Response> {
  if (request.method !== "POST") {
    return jsonResponse(405, { error: "method_not_allowed" });
  }
  if (!env.FIREBASE_SERVICE_ACCOUNT) {
    return jsonResponse(500, {
      error: "server_misconfigured",
      reason: "FIREBASE_SERVICE_ACCOUNT secret not set",
    });
  }

  const projectId =
    env.FIREBASE_PROJECT_ID ||
    getProjectIdFromServiceAccount(env.FIREBASE_SERVICE_ACCOUNT);
  if (!projectId) {
    return jsonResponse(500, {
      error: "server_misconfigured",
      reason: "FIREBASE_PROJECT_ID missing and not in service account",
    });
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return jsonResponse(400, { error: "invalid_json" });
  }
  const b = (body ?? {}) as Record<string, unknown>;
  const update: AnnouncementUpdate = {
    active: b.active === true,
    id: sanitiseString(b.id, 100),
    title: sanitiseString(b.title, 200),
    message: sanitiseString(b.message, 2000),
  };

  // active=true requires id + message — otherwise the Android client would
  // bail silently and the admin would think it worked.
  if (update.active && (!update.id || !update.message)) {
    return jsonResponse(400, {
      error: "missing_required",
      reason: "active=true requires non-empty id and message",
    });
  }

  let datastoreToken: string;
  let rcToken: string;
  try {
    datastoreToken = await getGoogleAccessToken(
      env.FIREBASE_SERVICE_ACCOUNT,
      [FIRESTORE_SCOPE],
    );
  } catch (e) {
    console.error("datastore token:", e);
    return jsonResponse(500, { error: "auth_failed" });
  }

  const isAdmin = await isAdminUid(callerUid, projectId, datastoreToken);
  if (!isAdmin) {
    return jsonResponse(403, {
      error: "forbidden",
      reason: "uid not in admin_uids",
    });
  }

  try {
    rcToken = await getGoogleAccessToken(
      env.FIREBASE_SERVICE_ACCOUNT,
      [REMOTE_CONFIG_SCOPE],
    );
  } catch (e) {
    console.error("rc token:", e);
    return jsonResponse(500, { error: "auth_failed" });
  }

  try {
    await updateAnnouncement(rcToken, projectId, update);
  } catch (e) {
    console.error("updateAnnouncement:", e);
    return jsonResponse(502, {
      error: "remote_config_update_failed",
      reason: e instanceof Error ? e.message : String(e),
    });
  }

  return jsonResponse(200, { ok: true });
}
