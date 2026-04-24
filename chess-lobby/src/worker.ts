// HTTP entry point for the chess lobby Worker.
//
// - GET /health           -> liveness probe
// - GET /ws (Upgrade: ws) -> validates Firebase ID token, forwards
//                             to the single global Lobby Durable
//                             Object with X-Player-Id header set.
// - everything else       -> 404
//
// Auth failures are returned as HTTP 401 (NOT a WS close) so the
// Android client sees a clean error instead of a mysterious 1006.

import { verifyFirebaseIdToken } from "./auth";
import type { Env } from "./types";

export { Lobby } from "./lobby";

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type, Upgrade",
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

function extractBearer(request: Request): string | null {
  // Prefer Authorization header (used by native Android OkHttp client).
  const raw = request.headers.get("Authorization");
  if (raw) {
    const match = raw.match(/^Bearer\s+(.+)$/i);
    if (match) return match[1].trim();
  }
  // Fallback: ?token= query param. Browsers cannot set custom headers on
  // the WebSocket upgrade (the WebSocket constructor accepts no header
  // arg), so the PWA passes the Firebase ID token as a query parameter.
  // Tokens in URLs can leak via access logs, but CF Workers logs don't
  // persist query strings by default and the ID token lifetime is 1 hour,
  // so the exposure window is bounded.
  const url = new URL(request.url);
  const queryToken = url.searchParams.get("token");
  if (queryToken && queryToken.trim().length > 0) {
    return queryToken.trim();
  }
  return null;
}

export default {
  async fetch(
    request: Request,
    env: Env,
    _ctx: ExecutionContext,
  ): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    if (url.pathname === "/health") {
      return jsonResponse(200, { ok: true });
    }

    if (url.pathname !== "/ws") {
      return jsonResponse(404, { error: "not_found" });
    }

    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return jsonResponse(400, {
        error: "upgrade_required",
        reason: "Expected Upgrade: websocket",
      });
    }

    if (!env.FIREBASE_PROJECT_ID) {
      return jsonResponse(500, {
        error: "server_misconfigured",
        reason: "FIREBASE_PROJECT_ID secret not set",
      });
    }

    const token = extractBearer(request);
    if (!token) {
      return jsonResponse(401, {
        error: "unauthorized",
        reason: "Missing Authorization: Bearer <id_token> header or ?token= query param",
      });
    }

    const verified = await verifyFirebaseIdToken(
      token,
      env.FIREBASE_PROJECT_ID,
    );
    if (!verified) {
      return jsonResponse(401, {
        error: "unauthorized",
        reason: "Invalid or expired Firebase ID token",
      });
    }

    // Forward to the single global Lobby DO with a trusted
    // X-Player-Id header. The DO trusts this header because only
    // this Worker can reach the DO (clients cannot address it
    // directly).
    const id = env.LOBBY.idFromName("global");
    const stub = env.LOBBY.get(id);

    const forwarded = new Request(request, request);
    forwarded.headers.set("X-Player-Id", verified.uid);
    if (verified.name) {
      forwarded.headers.set("X-Player-Name", verified.name);
    }

    return stub.fetch(forwarded);
  },
};
