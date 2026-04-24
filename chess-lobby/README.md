# chess-lobby

Cloudflare Worker + Durable Object that replaces Firestore as the
presence and challenge broker for the JustPass Android chess
lobby. Clients connect over a single WebSocket (`/ws`) and receive
real-time presence diffs and challenge events. The actual chess
game still runs on Lichess.org inside a WebView — this Worker only
matchmakes. One global Durable Object (`idFromName("global")`)
holds all live state in memory using the WebSocket Hibernation API.

## Dev

```
npm install
npx wrangler login
npx wrangler dev
```

Then open another terminal and hit `http://localhost:8787/health`
to confirm the Worker is up.

## Deploy

```
npx wrangler secret put FIREBASE_PROJECT_ID
npx wrangler deploy
```

`FIREBASE_PROJECT_ID` **must match the Firebase project the
Android app is configured for**. Find it in the app's
`google-services.json` under `project_info.project_id`. The Worker
uses this to validate inbound Firebase ID tokens against Google's
JWKS (`securetoken@system.gserviceaccount.com`). Mismatched IDs
will cause every connection to be rejected with HTTP 401.

## Verifying

```
curl https://chess-lobby.<your-subdomain>.workers.dev/health
```

should return `{"ok":true}`.

The client WebSocket endpoint is:

```
wss://chess-lobby.<your-subdomain>.workers.dev/ws
```

Clients must send `Authorization: Bearer <firebase-id-token>` on
the upgrade request. Auth failures come back as HTTP 401 with a
JSON body (not a post-upgrade close), so the Android client can
distinguish them from network issues.

## Protocol (summary)

Client sends one of: `JOIN`, `CHALLENGE`, `ACCEPT`, `DECLINE`,
`CANCEL`. Server sends one of: `PRESENCE_SNAPSHOT`,
`PRESENCE_DIFF`, `CHALLENGE_INCOMING`, `CHALLENGE_ACCEPTED`,
`CHALLENGE_DECLINED`, `CHALLENGE_CANCELED`, `ERROR`. All messages
are JSON with a `"type"` discriminator. `PRESENCE_DIFF` always
contains both `added` and `removed` arrays (possibly empty).
Challenges auto-expire 20 seconds after creation.
