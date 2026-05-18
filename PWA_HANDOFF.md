# JustPass PWA — Project Handoff

Companion file to `ANDROID_HANDOFF.md`. Documents the Next.js PWA (`justpass-eta.vercel.app`). Last updated 2026-05-18.

---

## 1. Project at a glance

| Field | Value |
|-------|-------|
| **App name** | JustPass PWA (formerly "Laudea Attendance PWA") |
| **Repo** | `https://github.com/Tarunswamy-Muralidharan/AttendanceWidgetLaudea-Web` |
| **Default branch** | `master` |
| **Deployed URL** | `https://justpass-eta.vercel.app` |
| **Hosting** | Vercel (free tier), auto-deploys on push to `master`, manual via `npx vercel --prod` |
| **Developer** | Tarunswamy Muralidharan (`tmswamy10@gmail.com`) |
| **Local path** | `C:\Users\tmswa\WebProjects\AttendanceWidgetLaudea-Web` |
| **Sister project** | JustPass Android at `C:\Users\tmswa\AndroidStudioProjects\AttendanceWidgetLaudea` — see `ANDROID_HANDOFF.md`. |
| **Started** | 2026-03-31 |
| **Status** | 16 screens working with live data, 0 TS errors, 0 lint errors |

### Why a PWA

iPhone users cannot use the native Android app. Apple Developer Program costs $99/year. PWA is free: no App Store, no Mac needed, Safari → Share → "Add to Home Screen" gives a native-feeling icon. Same LAUDEA SIS APIs as the Android app, just hit over HTTPS.

The PWA shares the Firebase project and chess lobby Firestore collections with the Android app, so chess matches between an iPhone PWA user and an Android user work cross-platform.

---

## 2. Tech stack

| Layer | Choice |
|---|---|
| Framework | Next.js **16.2.2** (App Router) — note: NOT Next.js 15 like your training data |
| React | **19.2.4** + React DOM 19.2.4 |
| Language | TypeScript 5 |
| Styling | Tailwind CSS **v4** (`@tailwindcss/postcss`) |
| Auth | Keycloak password grant via Next.js API proxy routes |
| State | React Context (`AuthProvider`) + plain `useState` / `useEffect`. SWR was considered but not used — direct `fetch` + `useState` instead. |
| Storage | `localStorage` for tokens + cached SIS data |
| Hosting | Vercel free tier (auto HTTPS, CDN) |
| Real-time | Firebase **12.11.0** (Firestore + Anonymous Auth) — shared chess lobby with Android |
| Service Worker | Custom in `public/sw.js` + `next.config.ts` register hook. Offline caching + cache-busting on update. |
| Browser automation | **Playwright 1.59.1** in devDependencies — used for cross-platform chess test scenarios |

### `AGENTS.md` warning

The PWA root has an `AGENTS.md` that begins:

> # This is NOT the Next.js you know
>
> This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.

**Take this seriously.** Next.js 16 changed enough that LLM-default Next.js code may not compile or may use deprecated patterns. Inspect `node_modules/next/dist/docs/` before authoring nontrivial Next code.

---

## 3. Architecture

### Routing

App Router (`src/app/` directory). Each subfolder is a route:

```
src/app/
├── layout.tsx           # Root layout — viewport meta, manifest link, AuthProvider wrap
├── page.tsx             # Login page (redirects to /dashboard if already authed)
├── globals.css
├── favicon.ico
│
├── api/                 # Server-side API proxies (Vercel runtime)
│   ├── auth/login/route.ts            # POST — Keycloak password grant
│   ├── auth/refresh/route.ts           # POST — refresh token grant
│   ├── attendance/route.ts             # Attendance summary
│   ├── attendance/absent/route.ts
│   ├── attendance/present/route.ts
│   ├── attendance/exemptions/route.ts
│   ├── marks/route.ts                  # CA marks
│   ├── results/route.ts                # Semester results
│   ├── timetable/route.ts
│   ├── calendar/route.ts               # Google Calendar API
│   ├── circulars/route.ts              # Meetings module proxy
│   ├── circulars/pdf/route.ts          # Signed URL exchange
│   ├── circulars/[id]/route.ts         # Single circular detail
│   ├── student/route.ts                # Biodata
│   └── student/photo/route.ts          # S3 pre-signed photo URL
│
├── dashboard/page.tsx
├── subjects/page.tsx
├── absent/page.tsx
├── exemptions/page.tsx
├── marks/page.tsx
├── results/page.tsx
├── gpa/page.tsx
├── timetable/page.tsx
├── calendar/page.tsx
├── circulars/page.tsx
├── syllabus/page.tsx
├── chess/page.tsx                       # Real-time Firestore chess
├── profile/page.tsx
└── privacy/page.tsx
```

### Why API proxy routes (not direct browser calls to SIS)

Browser-direct calls to `laudea.psgitech.ac.in` were blocked by CORS — the SIS allows web browsers but the CORS preflight response isn't permissive for browsers. So every SIS call goes through a Next.js API route running on Vercel, which forwards the request server-side. The route adds a real browser-style User-Agent (since some SIS endpoints reject non-browser UAs — see commit `559c01c`).

### Auth flow

```
LoginPage form submit
  → POST /api/auth/login (Next API route)
  → Keycloak password grant (server-side from Vercel edge):
      POST https://accounts.psgitech.ac.in/realms/itech/protocol/openid-connect/token
      grant_type=password, client_id=ies_sis, scope=openid (NOT offline_access — causes 500)
  → Receive { access_token, refresh_token, expires_in }
  → Send back to client
  → AuthProvider stores in localStorage
  → Redirect to /dashboard
```

Differences from Android:

- **Scope is just `openid`** — including `offline_access` returns HTTP 500 from Keycloak. The Android app uses `openid offline_access` successfully against the same client. Apparently the web client registration doesn't support offline_access. Investigation deferred.
- **Token lifetime is 5 minutes** (without `offline_access`), much shorter than Android's never-expires refresh token.
- **No password storage** in browser — only the refresh token. Once refresh fails, user must re-enter password (no silent re-login).
- **Proactive refresh** at 30s before expiry to avoid wasted 401 round-trips. Implemented in `src/lib/api.ts`'s `apiFetch` wrapper.

### Service Worker

`public/sw.js` registered in `next.config.ts`. Cache-first for static assets, network-first for SIS responses. Auto cache-busting via build hash injected into the SW.

### iOS PWA hardening

`layout.tsx` includes:
- `viewport-fit=cover` for notch-aware safe areas
- `apple-mobile-web-app-capable=yes`
- `apple-mobile-web-app-status-bar-style=black-translucent`
- `apple-touch-icon` link
- 100vh trick for iOS Safari address bar issues
- `safe-area-inset-*` CSS custom properties
- `touch-action: manipulation` on all buttons (avoids 300ms tap delay)

---

## 4. SIS APIs used (via Next proxy routes)

Same backend as the Android app. The Next route at `/api/<x>` proxies to `https://laudea.psgitech.ac.in/sis/<x>` with the access token in `Authorization: Bearer <token>`.

| Route | Maps to | Notes |
|---|---|---|
| `/api/auth/login` | Keycloak token endpoint | Password grant |
| `/api/auth/refresh` | Keycloak token endpoint | Refresh grant |
| `/api/attendance` | `/sis/attendance/...` | Server still uses `excemption` typo |
| `/api/attendance/absent` | `/sis/absentdays` | |
| `/api/attendance/present` | `/sis/presentdays` | |
| `/api/attendance/exemptions` | `/sis/exemptions` | |
| `/api/marks` | `/sis/students/CAMarks/{rollNumber}` | |
| `/api/results` | `/sis/remote/all/results?rollNo=` | |
| `/api/timetable` | `/sis/students/timetable/{nodeId}` | nodeId fetched per-user |
| `/api/student` | `/sis/students/{rollNumber}` | |
| `/api/student/photo` | `/sis/students/downloadUrl` | Returns S3 pre-signed URL |
| `/api/calendar` | Google Calendar API | Public holidays calendar |
| `/api/circulars` | `/meetings/circulars/user/pagination` | Separate auth — `ies_meetings` client_id |
| `/api/circulars/[id]` | `/meetings/circular/{id}` | |
| `/api/circulars/pdf` | `/meetings/s3/download/url` | |

### Server-down handling (recent)

Commit `33979f1` (2026-05+): when an attendance proxy hits SIS 5xx, the dashboard swaps the toolbar banner for a full Android-style server-down card. Auto-refresh kicks in to retry. Commit `3851cd1` added the same to attendance subpages.

---

## 5. Feature matrix (16 screens)

| Screen | Feature | Status |
|---|---|---|
| Login | Keycloak password grant + roll/password form | ✅ |
| Dashboard | Attendance %, present/absent/exemption stats, leave calc modal, profile pic | ✅ |
| Subjects | Per-subject attendance grid, click → detail | ✅ |
| Subject detail | Day-by-day session timeline + 80% warning | ✅ |
| Absent days | Session-level absent list with date headers | ✅ |
| Exemptions | Subject-wise exemption sessions | ✅ |
| CA Marks | Subject-wise marks, components expanded | ✅ |
| Results | Semester results, grade cards, SGPA | ✅ |
| GPA Calculator | Manual grade input, R2021 + R2025 curriculum, live SGPA + CGPA, target CGPA reverse-calc | ✅ |
| Timetable | Weekly schedule, now-period highlight, bunkometer | ✅ |
| Calendar | Google Calendar holidays + events, month grid | ✅ |
| Circulars | List + PDF viewer (uses S3 signed URL) | ✅ |
| Syllabus | Offline bundled JSON (R2021 + R2025) | ✅ |
| Chess | Live Firestore presence + matchmaking + Lichess in iframe | ✅ |
| Profile | Biodata, attendance target, sign-out | ✅ |
| Privacy | Static text page now redirects to Cloudflare Worker policy at `chess-lobby.tmswamy10.workers.dev/privacy` | ✅ |

### Chess (cross-platform with Android)

Currently runs on **Firestore (V1)** path — the Cloudflare Durable Object (V2) was found to close WebSocket connections aggressively, see commit `2af216a` "Fall back to Firestore (V1) chess lobby — V2 worker is closing every WS". Subsequent commits (`6e02dd4`, `45b6bfd`) re-enabled V2 after fixing the DO hibernation bug.

Today the PWA chess uses the same backend the Android app picks via the `chess_backend_v2` Remote Config flag. Default true (Cloudflare DO). Falls back to Firestore if Worker is unhealthy.

**Cross-platform timing fix:**

Sender + receiver countdowns historically drifted on iOS Safari because `setInterval` gets throttled. Fixed (commit `c42e37e`) by anchoring both sides to the absolute Firestore `timestamp` server-time, not local decrements. Every tick recomputes `remaining = COUNTDOWN - floor((Date.now() - serverTimestamp) / 1000)`. iOS suspension causes the next tick to jump to the correct value instead of lagging.

**Double-tap protection:**

`sendChallenge` uses `useRef(false)` single-flight guard + closes the modal before any await. Prevents the iOS pattern of "user taps fast → multiple Firestore writes → orphan challenges that never resolve".

### Server-down banner

Dashboard + attendance subpages show a full Android-style card when SIS is unreachable. Refreshes automatically every 30s.

### Privacy page

`/privacy` redirects to `https://chess-lobby.tmswamy10.workers.dev/privacy` (the Cloudflare Worker page from the Android repo). One canonical privacy policy across both apps.

---

## 6. Key files (PWA)

```
src/
├── app/                                  # Routes — see §3
│
├── components/
│   ├── AuthProvider.tsx                  # React Context for auth state
│   ├── BottomNav.tsx                     # Same 5-tab layout as Android
│   ├── ServerDownCard.tsx                # Full-screen "servers are down" UI
│   ├── ChessBoard.tsx
│   ├── ChessChallengeModal.tsx
│   └── ... (one per screen, roughly)
│
└── lib/
    ├── api.ts                             # apiFetch wrapper — auto-refresh, 30s pre-expiry refresh
    ├── auth.ts                            # Token storage helpers, login/refresh/logout
    ├── auth-anonymous.ts                  # Firebase anonymous auth for chess
    ├── firebase.ts                        # Firebase JS SDK init (env-driven)
    ├── constants.ts                       # API URLs, time control mappings
    ├── targetCgpa.ts                      # Reverse-calculate ESE needed to hit a target CGPA
    ├── remote-config.ts                   # Firebase Remote Config wrapper
    └── chess/                             # Chess client helpers
        ├── presence.ts                    # Firestore online/offline writes
        ├── challenges.ts                  # Send/accept/decline flow
        └── lichess.ts                     # Game URL generation

public/
├── sw.js                                  # Service worker
├── manifest.json                          # PWA manifest
├── icons/                                 # 192px, 512px, maskable, apple-touch-icon
└── ... (static images)

scripts/                                   # Node helpers (Playwright tests, build scripts)
tests/                                     # Playwright cross-platform chess scenarios
```

---

## 7. Environment variables

`.env.local` (NOT committed):

```
# Firebase (same project as Android app)
NEXT_PUBLIC_FIREBASE_API_KEY=AIzaSyDcSp_eQ6ThOXlSMfO-jQKufKDfIKQgWbQ
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=attendacewidget.firebaseapp.com
NEXT_PUBLIC_FIREBASE_PROJECT_ID=attendacewidget
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=attendacewidget.firebasestorage.app
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=112210895254
NEXT_PUBLIC_FIREBASE_APP_ID=1:112210895254:web:c37ad4a79e4e2a190c7db7
NEXT_PUBLIC_FIREBASE_MEASUREMENT_ID=G-6JV2WW4ZZX
```

Note: the Firebase project name has a typo (`attendacewidget` instead of `attendancewidget`) — historical, never renamed. Don't try to fix it.

Vercel project dashboard has these same vars set for production builds. Adding new env vars: Vercel → project → Settings → Environment Variables.

### Optional / inferred env

- `LAUDEA_API_BASE=https://laudea.psgitech.ac.in` (server-side, defaults inline)
- `KEYCLOAK_BASE=https://accounts.psgitech.ac.in`
- `KEYCLOAK_CLIENT_ID=ies_sis`
- `KEYCLOAK_MEETINGS_CLIENT_ID=ies_meetings`

---

## 8. Deployment

```bash
# Local dev
npm run dev

# Production build (locally)
npm run build && npm start

# Deploy to Vercel
git push origin master            # auto-deploys via Vercel GitHub integration
npx vercel --prod                 # manual deploy
```

Vercel auto-deploys every push to `master`. Preview deploys for every PR. Production domain: `justpass-eta.vercel.app`.

---

## 9. Cross-references with Android

| Concept | Android | PWA | Shared? |
|---|---|---|---|
| Auth | WebView + token capture | Keycloak password grant via Next API | NO — different mechanisms, same Keycloak |
| SIS data | Direct OkHttp from app | Next API proxy → SIS | NO — both hit SIS independently |
| Privacy policy | `https://chess-lobby.tmswamy10.workers.dev/privacy` | redirects to same URL | YES |
| Chess presence | Firestore + DO Worker | Firestore + DO Worker | **YES** (same project, same collections, same Worker) |
| Firebase Auth UID | Anonymous | Anonymous | Per-device — Android user and PWA user of the same person have different UIDs |
| Class marks comparison (in flight) | `ClassMarksUploadWorker` posts to `/class/marks` on chess-lobby Worker | NOT YET WIRED — Phase 5 deferred | Will share `class_marks` D1 table |

---

## 10. Privacy + compliance

- One privacy policy across both apps (Cloudflare Worker URL)
- Android app's Play Store Data Safety form covers Android data flows
- PWA has no app-store equivalent — Vercel doesn't require disclosure, but GDPR/India IT Rules still apply
- **Class marks comparison** is currently Android-only because the Android worker uploads. PWA users won't contribute unless we implement Phase 5 (server-side cron polling — under discussion as of 2026-05-17)

### Disclosed in policy

- Anonymous Firebase Authentication UID per device
- Chess lobby presence (Firestore + DO Worker)
- Future: class marks comparison (anonymized CA marks under hashed anonId in D1)

### Not stored anywhere except the device

- LAUDEA roll number + password
- Cached attendance / marks / timetable / etc.

---

## 11. Known limitations + open issues

### iOS PWA constraints (browser-side)

- No push notifications (until iOS 16.4+ web push gets stable + reliable)
- No home-screen widget
- No `setInterval` reliability when backgrounded — fixed for chess by anchoring to wall-clock, same pattern needed for any new timer
- ITP (Intelligent Tracking Prevention) gets aggressive in standalone PWA mode — Firestore listener errors surface here that don't show in regular tab mode

### Data + content

- R2025 curriculum data not yet populated (only R2021 currently)
- ECE / EEE / MECH / CIVIL missing for R2021 in some screens (GPA mostly)
- Bunkometer needs timetable visited once for accurate session counts (timetable caches `nodeId`)

### Auth

- Stuck with 5-minute access token because `offline_access` scope returns 500
- No silent re-login after refresh expires — user has to type password again
- Solution would be: investigate why Keycloak client `ies_sis` doesn't accept `offline_access` from web. May need a different client_id registration.

### Chess

- V2 (Cloudflare DO) had a hibernation bug where the global DO was dropping the in-memory `players` Map (commit `2af216a` reverted to V1 Firestore). Fix shipped 2026-05-05 in commit `6220be1` (Android repo). Then `6e02dd4` re-enabled V2 default. Watch for regressions.
- Requires Firebase env vars set in Vercel; local dev without them shows chess as "loading…" forever

### Service Worker

- Cache-busting works but new SW activates on next navigation, not immediately. Hard refresh needed for instant updates.
- Subtle bug: if you push a deployment while a user has the PWA open, they may see stale chunks until they navigate. Acceptable for now.

---

## 12. Recent session history (last 19 dev log entries summarized)

The PWA's `DEVELOPMENT_LOG.md` has 19 numbered "Challenge" entries (most recent: Challenge 19). Highlights:

| # | Date | Topic |
|---|---|---|
| 1 | 2026-03-31 | Initial Next 16 scaffold + PWA manifest |
| 2 | 2026-04-01 | Keycloak password grant — `offline_access` 500 worked around |
| 3 | 2026-04-01 | SGPA calc matched to Android exactly (credit fallback chain) |
| 4 | 2026-04-01 | Exemptions endpoint shape (sessions as `List<String>`) |
| 5 | 2026-04-02 | Server-down detection + retry banner |
| 6 | 2026-04-03 | Bunkometer (target attendance + leaves) |
| 7 | 2026-04-04 | Bottom nav layout, safe-area-inset for iPhone notch |
| 8 | 2026-04-05 | Chess presence migration to Firestore (matched Android collections) |
| 9 | 2026-04-06 | Cross-platform chess test scenarios (Playwright) |
| 10 | 2026-04-08 | Lichess iframe game flow |
| 11 | 2026-04-10 | Service Worker offline-first + cache-bust |
| 12 | 2026-04-12 | Dashboard redesign (glass-morphism matching Android) |
| 13 | 2026-04-14 | Targeted CGPA reverse-calc |
| 14 | 2026-04-16 | Calendar API + holiday list rendering |
| 15 | 2026-04-17 | Circulars PDF viewer (signed URL exchange) |
| 16 | 2026-04-18 | Profile screen + privacy policy redirect |
| 17 | 2026-04-19 | Anonymous Firebase Auth for chess |
| 18 | 2026-04-19 | Optimistic online/offline toggle, `touch-action: manipulation` |
| 19 | 2026-04-20 | Double-tap challenge race + countdown drift — single-flight ref + wall-clock anchored countdown |

Latest commits (May 2026):
- `33979f1` Swap server-down toolbar banner for full Android-style card
- `cc258aa` Replace internal privacy stub with public Play-compliant policy
- `b448720` Fix opponent-leave on V2 chess + remove double-count
- `e302181` Skip the heartbeat useEffect on V2 (battery save)
- `45b6bfd` Chess module audit fixes (#5, #6, #9) cross-ported from Android repo

Full session detail in `C:\Users\tmswa\WebProjects\AttendanceWidgetLaudea-Web\DEVELOPMENT_LOG.md`.

---

## 13. Pending decisions (2026-05-18)

### Class marks comparison — should PWA users contribute?

Under discussion. Three options surfaced:

**A — Full server-side cron polling.** Backend stores PWA users' SIS credentials encrypted, runs a Cloudflare Worker cron every 6h, fetches marks, writes to D1. Highest reach, biggest security debt (backend becomes credential custodian for college SIS passwords).

**A-opt-in — Opt-in toggle in PWA settings.** Same code as A but only fires for PWA users who explicitly turn on "background sync". Smaller credential surface.

**B — Manual sync nudge.** PWA shows "Last contributed X days ago" banner that nudges users to open the app, where existing client JS uploads via the same `/class/marks` route. Zero backend creds, requires occasional PWA opens.

Feasibility (Cloudflare free tier at 5k total users, ~30% PWA):
- ≤1000 PWA users → free tier comfortable on option A
- 1000–2500 PWA → tight, needs careful subrequest chunking
- 2500+ → upgrade to Workers Paid ($5/mo)

User has not picked yet. No PWA-side implementation work has started for class marks.

---

## 14. How to bootstrap on this project

1. Read this file end to end.
2. Read `ANDROID_HANDOFF.md` for the sister project — same SIS APIs, shared Firebase, related decisions.
3. Read `PLAN.md` in the PWA repo for the original architecture intent.
4. Read `DEVELOPMENT_LOG.md` in the PWA repo for chronological challenges + solutions.
5. Read `AGENTS.md` warning about Next 16.
6. Run `npm install` then `npm run dev` — needs `.env.local` populated with Firebase vars.
7. For local chess testing: provide all `NEXT_PUBLIC_FIREBASE_*` vars or chess screen will hang.

### Build state

- 0 TypeScript errors (`npx tsc --noEmit` clean)
- 0 ESLint errors
- Latest commit `33979f1` on `master`
- Vercel auto-deploys

---

*End of PWA_HANDOFF.md. Companion file: ANDROID_HANDOFF.md.*
