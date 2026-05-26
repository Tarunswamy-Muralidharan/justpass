// HTTP entry point for the chess lobby Worker.
//
// - GET    /health                    -> liveness probe
// - GET    /privacy                   -> JustPass privacy policy (Play Store requirement)
// - GET    /class/:classKey           -> class marks stats (Firebase auth)
// - POST   /class/marks               -> upload own marks (Firebase auth)
// - DELETE /class/me                  -> wipe my row (Firebase auth)
// - GET    /ws (Upgrade: ws)          -> validates Firebase ID token, forwards
//                                         to the single global Lobby Durable
//                                         Object with X-Player-Id header set.
// - everything else                   -> 404
//
// Auth failures are returned as HTTP 401 (NOT a WS close) so the
// Android client sees a clean error instead of a mysterious 1006.

import { verifyFirebaseIdToken } from "./auth";
import {
  handleDeleteMe,
  handleGetClassStats,
  handleUploadMarks,
} from "./class";
import { handlePwaRegister, handlePwaUnregister } from "./pwa";
import { runPwaSyncTick } from "./pwa_cron";
import type { Env } from "./types";

export { Lobby } from "./lobby";

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
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

    if (url.pathname === "/privacy" || url.pathname === "/privacy/") {
      return new Response(PRIVACY_POLICY_HTML, {
        status: 200,
        headers: {
          "Content-Type": "text/html; charset=utf-8",
          "Cache-Control": "public, max-age=3600",
          "X-Content-Type-Options": "nosniff",
        },
      });
    }

    // Class marks comparison routes — all require Firebase auth.
    if (url.pathname.startsWith("/class")) {
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
          reason: "Missing Authorization: Bearer <id_token> header",
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

      // POST /class/marks
      if (
        url.pathname === "/class/marks" &&
        request.method === "POST"
      ) {
        return handleUploadMarks(request, env, verified.uid);
      }

      // DELETE /class/me
      if (
        url.pathname === "/class/me" &&
        request.method === "DELETE"
      ) {
        return handleDeleteMe(request, env, verified.uid);
      }

      // GET /class/:classKey
      if (request.method === "GET") {
        const match = url.pathname.match(/^\/class\/([^/]+)\/?$/);
        if (match) {
          return handleGetClassStats(request, env, verified.uid, match[1]);
        }
      }

      return jsonResponse(404, { error: "not_found", path: url.pathname });
    }

    // PWA server-side cron polling routes — register encrypted SIS creds.
    if (url.pathname.startsWith("/pwa")) {
      if (!env.FIREBASE_PROJECT_ID) {
        return jsonResponse(500, {
          error: "server_misconfigured",
          reason: "FIREBASE_PROJECT_ID secret not set",
        });
      }
      const token = extractBearer(request);
      if (!token) {
        return jsonResponse(401, { error: "unauthorized" });
      }
      const verified = await verifyFirebaseIdToken(token, env.FIREBASE_PROJECT_ID);
      if (!verified) {
        return jsonResponse(401, { error: "unauthorized" });
      }
      if (url.pathname === "/pwa/register" && request.method === "POST") {
        return handlePwaRegister(request, env, verified.uid);
      }
      if (url.pathname === "/pwa/register" && request.method === "DELETE") {
        return handlePwaUnregister(request, env, verified.uid);
      }
      return jsonResponse(404, { error: "not_found", path: url.pathname });
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

    try {
      return await stub.fetch(forwarded);
    } catch (err) {
      console.error(`[worker] DO fetch threw:`, err instanceof Error ? err.stack : err);
      return jsonResponse(500, { error: "do_fetch_failed", reason: err instanceof Error ? err.message : String(err) });
    }
  },

  // Scheduled handler — Cloudflare Workers cron trigger. Runs every 10 min
  // via wrangler.toml [triggers] crons configuration. Picks 16 PWA-registered
  // users (oldest-synced first) and refreshes their class_marks row.
  async scheduled(
    _event: ScheduledEvent,
    env: Env,
    ctx: ExecutionContext,
  ): Promise<void> {
    ctx.waitUntil(runPwaSyncTick(env));
  },
};

const PRIVACY_POLICY_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Privacy Policy - JustPass</title>
<style>
  :root { color-scheme: light dark; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    max-width: 760px;
    margin: 0 auto;
    padding: 32px 20px 64px;
    line-height: 1.6;
    color: #1a1a1a;
    background: #fafafa;
  }
  @media (prefers-color-scheme: dark) {
    body { color: #e5e5e5; background: #0f1115; }
    a { color: #7cb7ff; }
  }
  h1 { font-size: 1.9rem; margin-bottom: 0.25rem; }
  h2 { font-size: 1.25rem; margin-top: 2rem; border-bottom: 1px solid currentColor; padding-bottom: 0.3rem; opacity: 0.95; }
  .meta { opacity: 0.7; font-size: 0.9rem; margin-bottom: 1.5rem; }
  ul { padding-left: 1.4rem; }
  li { margin-bottom: 0.4rem; }
  code {
    background: rgba(127, 127, 127, 0.18);
    color: inherit;
    padding: 0.1rem 0.35rem;
    border-radius: 4px;
    font-size: 0.9em;
  }
  a { color: #1a73e8; }
</style>
</head>
<body>
  <h1>JustPass — Privacy Policy</h1>
  <p class="meta">
    Effective date: 10 May 2026<br>
    Application: JustPass (<code>com.justpass.app</code>)<br>
    Developer: Tarunswamy Muralidharan
  </p>

  <p>
    JustPass is a student utility for PSG Institute of Technology (iTech) that
    surfaces attendance, CA marks, timetable, results, circulars, and a
    student-to-student chess lobby. This policy explains what information the
    app handles, where it goes, and the choices you have. By installing or
    using JustPass you agree to this policy.
  </p>

  <h2>1. Information we handle</h2>
  <ul>
    <li>
      <strong>LAUDEA SIS credentials (roll number + password):</strong>
      entered by you on the in-app login screen. Stored only on your device
      using Android <code>EncryptedSharedPreferences</code> (AES-256, hardware-
      backed Keystore where available). Sent only over HTTPS to the official
      PSG iTech LAUDEA SIS authentication endpoint to obtain access tokens on
      your behalf. The credentials are never sent to any server operated by the
      developer.
    </li>
    <li>
      <strong>Academic data fetched from LAUDEA SIS:</strong> attendance
      records, CA marks, exam results, timetable, profile picture URL, and
      student biodata. This data is fetched on demand from the SIS, cached on
      your device for offline display, and is not transmitted elsewhere.
    </li>
    <li>
      <strong>Firebase Authentication anonymous UID:</strong> created when you
      open the chess lobby. Used to identify you to the multiplayer backend so
      challenges can be routed. No personal information is attached unless you
      choose a display name.
    </li>
    <li>
      <strong>Chess lobby presence:</strong> while you are in the chess screen,
      your chosen display name (or a randomly generated anonymous name) and
      your online/offline state are visible to other JustPass users in the
      lobby and stored transiently in Cloudflare Durable Objects and Firebase
      Firestore.
    </li>
    <li>
      <strong>Class marks comparison (anonymous):</strong> when this feature is
      enabled by us via remote configuration, your continuous assessment (CA)
      marks are uploaded under a one-way anonymous identifier (a SHA-style
      hash derived from your roll number, never the roll number itself). They
      are stored alongside a class key composed of your batch year, department,
      section, and current semester, in Cloudflare D1 (an edge SQLite database).
      Other students in the same class only ever see aggregated statistics
      (averages, distributions, your rank) — never anyone else&rsquo;s raw
      marks or identifying information. Comparison statistics are hidden
      entirely until at least 15 students from your class have signed in.
      You can wipe your data anytime via Profile &rsquo; Delete my class data.
    </li>
    <li>
      <strong>Web (PWA) credential storage for background sync:</strong> if you
      use the iOS / browser-based version of JustPass (justpass-eta.vercel.app),
      your LAUDEA SIS credentials are stored encrypted (AES-GCM with a key held
      only by our server) in Cloudflare D1. A scheduled job on our backend uses
      those credentials to refresh your marks every ~10 minutes so that you can
      contribute to the class comparison feature without having to open the
      website. Credentials are decrypted in memory only during the refresh,
      never logged, and are deleted immediately when you tap &ldquo;Stop
      background sync &amp; delete class data&rdquo; in your profile or when
      you delete your class data through any client. This only affects the PWA
      &mdash; the Android app never sends your credentials off your device.
    </li>
    <li>
      <strong>Firebase Analytics:</strong> anonymous, aggregated usage events
      (screen views, feature opens, crash counts) collected via the Google
      Firebase SDK. No advertising IDs are correlated with your roll number.
    </li>
    <li>
      <strong>AdMob:</strong> banner ads on a small number of secondary screens
      may use Google&rsquo;s standard ad identifiers. You can reset or limit
      this via Android Settings &rsquo; Privacy &rsquo; Ads.
    </li>
    <li>
      <strong>Notifications token (Firebase Cloud Messaging / WorkManager):</strong>
      used to deliver college circular and holiday alerts. The push token is
      stored on your device.
    </li>
  </ul>

  <h2>2. What we do NOT collect</h2>
  <ul>
    <li>We do not collect your name, email, phone number, or photographs from your device.</li>
    <li>We do not access your contacts, SMS, call logs, calendar, or microphone.</li>
    <li>We do not access location, even coarse location.</li>
    <li>We do not sell, rent, or share your data with advertisers or data brokers beyond the standard Google Analytics / AdMob aggregations described above.</li>
  </ul>

  <h2>3. Permissions used</h2>
  <ul>
    <li><code>INTERNET</code> &mdash; communicate with LAUDEA SIS, Firebase, Cloudflare, AdMob, and Lichess.</li>
    <li><code>POST_NOTIFICATIONS</code> (Android 13+) &mdash; deliver circular and holiday reminders.</li>
    <li><code>READ_MEDIA_IMAGES</code> / file picker &mdash; only when you import an exam-seat Excel file or pick an APK to share.</li>
    <li><code>FOREGROUND_SERVICE</code> &mdash; show download progress for the optional on-device AI model.</li>
  </ul>

  <h2>4. Third-party services</h2>
  <ul>
    <li>PSG iTech LAUDEA SIS &mdash; primary data source for academic information. Subject to PSG iTech&rsquo;s own policies.</li>
    <li>Google Firebase (Authentication, Firestore, Analytics, Cloud Messaging) &mdash; <a href="https://firebase.google.com/support/privacy">firebase.google.com/support/privacy</a></li>
    <li>Cloudflare Workers &amp; Durable Objects (chess presence + matchmaking) &mdash; <a href="https://www.cloudflare.com/privacypolicy/">cloudflare.com/privacypolicy</a></li>
    <li>Lichess.org &mdash; opened in an in-app WebView when you start a chess game. Subject to Lichess&rsquo;s privacy policy.</li>
    <li>Google AdMob &mdash; <a href="https://policies.google.com/technologies/ads">policies.google.com/technologies/ads</a></li>
  </ul>

  <h2>5. Data retention</h2>
  <p>
    Cached academic data lives on your device until you uninstall the app or
    clear app storage. Chess presence is removed from the lobby within seconds
    of you closing the chess screen. Firebase Analytics events follow Google&rsquo;s
    default retention (currently 14 months for event-level data).
  </p>

  <h2>6. Your choices and rights</h2>
  <ul>
    <li>
      <strong>Sign out / wipe local data:</strong> use Profile &rsquo; Sign Out
      inside the app, or uninstall the app, to remove all credentials and
      cached SIS data from your device.
    </li>
    <li>
      <strong>Opt out of analytics / ads:</strong> revoke ad personalisation in
      Android Settings, or uninstall to stop all analytics events.
    </li>
    <li>
      <strong>Delete chess presence:</strong> closing the chess screen removes
      your record. To remove your anonymous Firebase UID and stored display
      name, contact the developer (see below).
    </li>
    <li>
      <strong>Access / deletion request:</strong> email the developer at the
      address below and we will respond within 30 days.
    </li>
  </ul>

  <h2>7. Children</h2>
  <p>
    JustPass is intended for college students of PSG Institute of Technology
    and is not directed at children under 13. We do not knowingly collect data
    from children under 13.
  </p>

  <h2>8. Security</h2>
  <p>
    All network traffic uses HTTPS / WSS. Credentials are encrypted at rest on
    the device. The developer does not operate any server that stores your
    SIS roll number or password.
  </p>

  <h2>9. Changes to this policy</h2>
  <p>
    We may update this policy as the app evolves. Material changes will be
    announced inside the app and via the Play Store listing. The &ldquo;Effective
    date&rdquo; at the top will reflect the latest revision.
  </p>

  <h2>10. Contact</h2>
  <p>
    Tarunswamy Muralidharan<br>
    Email: <a href="mailto:tmswamy10@gmail.com">tmswamy10@gmail.com</a><br>
    App package: <code>com.justpass.app</code>
  </p>
</body>
</html>`;
