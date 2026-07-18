# JustPass — Development Log

A journal of building **JustPass**, an Android attendance app + home-screen widget for PSG iTech students, from first prototype to a Play Store release used by roughly 1,400 students. This log is written for someone with general programming knowledge but no Android-specific background — every piece of Android/networking jargon is explained on first use. It's organized so you can read a theme end-to-end, or use it as flash cards before an interview.

**Developer:** Tarunswamy Muralidharan · **Started:** December 2025 · **Status:** Live on Google Play

---

## Project Overview

### What it is

JustPass (originally "Laudea Attendance") is an Android app and companion home-screen widget that gives PSG iTech students a fast, native way to check their attendance, marks, timetable, and exam results — all of which otherwise live behind a slow, desktop-oriented college web portal called **LAUDEA SIS**. Over several months it grew from "show my attendance percentage" into a full academic companion: CGPA/GPA calculators, exam-seat lookup, a chess lobby for playing with classmates, a suite of reflex/memory mini-games with a section leaderboard, and a fully **on-device** AI study advisor (no cloud API calls, so no server costs and no privacy exposure).

### Who it's for

College students at PSG Institute of Technology who are tired of logging into a slow, desktop-only web portal just to check whether they're above 75% attendance. The app is distributed both through the Google Play Store and by students sharing the APK file directly with each other (sideloading) — a distribution pattern that shows up repeatedly in this log because it creates constraints (see [Release, Monetization & Publishing](#release-monetization--publishing)) that a normal Play-only app wouldn't have.

### Tech stack

| Layer | Technology | Why |
|---|---|---|
| Language | Kotlin | Android's modern first-class language |
| UI framework | Jetpack Compose | Android's declarative UI toolkit — you describe *what* the screen should look like for a given state, and the framework figures out *how* to render it, instead of manually mutating UI widgets |
| Widget | Android **RemoteViews**, later Jetpack **Glance** | Home-screen widgets run in a different process than the app and can't use full Compose — RemoteViews (and its modern Compose-flavored wrapper, Glance) is the restricted UI toolkit Android forces widgets to use |
| Auth | WebView (an embedded browser inside the app) + Keycloak SSO | The college's login system requires a real browser-style OAuth login flow |
| Networking | `HttpURLConnection` (built into Android), briefly benchmarked against OkHttp | Direct HTTP calls to the college's REST APIs once we have a token |
| Local storage | `EncryptedSharedPreferences`, plain `SharedPreferences` | Encrypted for credentials/tokens, plain for non-sensitive cached data |
| Background work | `WorkManager` | Android's system for scheduling work that must survive app kills and respect battery optimization |
| Backend (chess + class comparison) | **Cloudflare Workers**, **Durable Objects**, **D1** (edge SQLite) | Free-tier real-time backend that replaced an early Firebase Firestore design once user count made Firestore's free quota too expensive |
| Backend (legacy chess, profiles, admin) | **Firebase**: Firestore, Authentication, Remote Config, Analytics, Crashlytics, Cloud Storage | Google's mobile backend-as-a-service, used for anything that doesn't need Cloudflare's free-tier scale advantages |
| On-device AI | **Cactus** (llama.cpp wrapper) and **Google LiteRT-LM**, running Gemma/Qwen models | Local LLM inference so student data never leaves the phone |
| Ads | Google AdMob | Banner + interstitial ads, gated behind a remote kill-switch |
| Multiplayer chess engine | **Lichess.org public API** | Free, anonymous, no-signup game hosting — JustPass never had to write its own chess engine |
| Backend leaderboards | Supabase (Postgres) | Free-tier database for the mini-games leaderboard |

### High-level architecture (plain English)

```
Login:
  User opens app → embedded browser (WebView) loads the college's
  login page → college's SSO server (Keycloak) authenticates the
  student → app "listens in" on the network calls the browser makes
  and captures the security token → token is cached on the device.

Getting data after login (fast path):
  Cached token → direct HTTP call to college API → JSON response
  → parsed into Kotlin objects → shown in Compose UI.
  If the token has expired, the app climbs a ladder of fallback
  strategies before finally falling back to the slow embedded-browser
  flow (see "Authentication & Login" for the full 4-tier system).

Widget:
  A background job (WorkManager) periodically refreshes attendance
  data and writes it to encrypted local storage. The home-screen
  widget reads from that storage and redraws itself — it never talks
  to the network directly.

Chess / real-time features:
  Phone opens a WebSocket to a Cloudflare "Durable Object" (a
  single stateful server instance that holds the live list of who's
  online and pending challenges in memory). When a challenge is
  accepted, the server creates an anonymous match on Lichess.org and
  hands back a URL, which the app displays inside its own in-app
  browser view.

On-device AI advisor:
  A small language model (under 1–2 GB) is downloaded once and runs
  fully on the phone's CPU/GPU. A layer of ordinary Kotlin code does
  all the arithmetic (attendance percentages, "can I skip tomorrow"
  math) and hands the LLM only the final numbers to phrase into a
  natural-language answer — the model is never trusted to do math
  itself.
```

---

## Table of Contents

1. [Authentication & Login](#authentication--login)
2. [The Home-Screen Widget & Background Sync](#the-home-screen-widget--background-sync)
3. [Attendance & Data Fetching](#attendance--data-fetching)
4. [UI & Design: Liquid Glass, Animation, and Responsive Layouts](#ui--design-liquid-glass-animation-and-responsive-layouts)
5. [Weather Backgrounds & the Water Animation](#weather-backgrounds--the-water-animation)
6. [Chess: Building a Real-Time Multiplayer Feature](#chess-building-a-real-time-multiplayer-feature)
7. [The On-Device AI Study Advisor](#the-on-device-ai-study-advisor)
8. [Mini-Games, GPA Calculator & Academic Tools](#mini-games-gpa-calculator--academic-tools)
9. [Release, Monetization & Publishing](#release-monetization--publishing)
10. [Security Research (Authorized, On My Own College Account)](#security-research-authorized-on-my-own-college-account)
11. [Technical Deep-Dives](#technical-deep-dives)
12. [Interview Talking Points](#interview-talking-points)

---

## Authentication & Login

The single hardest, longest-running problem in this whole project was: **how does an Android app log into a system that was only ever designed for a human clicking through a web browser?** The college's identity system (**Keycloak**, an open-source single-sign-on server) issues short-lived security tokens through a multi-step browser redirect dance. Everything in this section is the story of chasing a fast, reliable way to get and refresh that token without asking the student to sit through a slow login every time they open the app.

### The Problem → Why → Solution → Lesson, for the biggest fights

#### 1. First login always timed out

**The Problem:** The very first login attempt failed after 30 seconds with "could not fetch attendance data." The flow was: open a WebView (an embedded, invisible browser inside the app) → it loads the college's site → the site redirects to Keycloak → Keycloak's login page loads → credentials are typed in via JavaScript → Keycloak redirects back to the college site → the college's single-page app (built in Angular) boots up and fetches attendance data.

**Why It Happened:** That's five separate network round trips chained together, and mobile networks are slow. 30 seconds wasn't enough.

**How I Solved It:** Bumped the timeout to 60 seconds and trimmed unnecessary delays elsewhere in the chain.

**What I Learned:** Any authentication flow built on browser redirects is going to be slow by nature — budget generous timeouts and show the user *something* is happening, rather than a plain frozen spinner.

#### 2. Hunting for the token in JavaScript was fragile

**The Problem:** Early code tried to find the login token by having injected JavaScript search through the browser page's global `window` object, looking for something that looked like a Keycloak session object with a `.token` field.

**Why It Happened:** The Keycloak JavaScript library the college's site uses doesn't put its token anywhere predictable — the object's name varies, and it might not exist yet when our script runs (JavaScript on the page hasn't finished setting things up).

**How I Solved It:** Stopped guessing and instead **intercepted network requests directly**. Every browser makes an `XMLHttpRequest` (XHR) — the browser API used for one page to fetch data from a server without a full page reload — under the hood when it calls an API. I overrode (monkey-patched) `XMLHttpRequest.prototype` so that every time the college's own Angular code called the API, my code saw the request headers (which include the security token) and the response body fly past, and copied them out.

```javascript
// Hook into XHR to capture auth headers and responses
XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
    if (name === 'authorization') {
        Android.onAuthToken(value.replace('Bearer ', ''));  // hand the token to Kotlin
    }
    return origSetHeader.apply(this, arguments);
};
```

**What I Learned:** Don't fight a framework by trying to replicate what it does — intercept what it *already does* naturally. This XHR-interception trick became the backbone of the entire login system.

#### 3. All the data showed up as zero

**The Problem:** Login appeared to succeed, but every attendance number on screen read 0.

**Why It Happened:** I assumed I could just point the WebView straight at the API URL (`https://laudea.psgitech.ac.in/sis/attendance/<rollnumber>`) like a REST endpoint and read the JSON. But that URL doesn't return JSON at all — it returns the HTML shell of the college's single-page web app (an "Angular SPA," a website that loads once and then swaps content in and out via JavaScript instead of full page reloads). My JSON parser (Gson) got HTML text where it expected numbers, and silently produced all-zero defaults instead of crashing.

**How I Solved It:** Instead of navigating to the API URL, I stayed on the actual college web app and changed its *internal* route (`window.location.hash = '#!/attendanceStudentView'`), which makes Angular itself fetch the data — and my XHR interceptor from problem #2 caught that request.

**What I Learned:** If a URL loads a JavaScript web app rather than raw JSON, you can't shortcut it — you have to trigger the app's own internal navigation and eavesdrop on the network call it makes as a result. Also: silent wrong-type parsing (Gson quietly defaulting to 0 instead of throwing) can hide bugs for a long time. Always sanity-check against the *real* server response, not what the field names imply.

#### 4. A misspelled field name broke exemption data

**The Problem:** "Attendance with exemption" always showed the same number as plain attendance — exemptions (approved absences that don't count against you) never seemed to apply.

**Why It Happened:** The server has a typo in its own JSON: the field is spelled `netPresentExcemptionPercentage` ("excemption," not "exemption"). My Kotlin data model used the correctly-spelled English word, so Gson (the JSON parsing library) never matched it and always defaulted to null.

```kotlin
// WRONG — correct English, but doesn't match the server's actual field
@SerializedName("netPresentExemptionPercentage")

// RIGHT — matches the server's typo exactly
@SerializedName("netPresentExcemptionPercentage")
```

**How I Solved It:** Compared the raw JSON response byte-for-byte against my model annotations and fixed the spelling to match the server, typo and all.

**What I Learned:** `@SerializedName` (the annotation that tells Gson which JSON key maps to which Kotlin field) must match the server's *exact* spelling — including its mistakes. Never type field names from memory; copy them straight from a real response.

#### 5. Refreshing data took 15–20 seconds every time

**The Problem:** Every pull-to-refresh repeated the *entire* WebView login dance — spin up a browser, load the page, wait for the SPA to boot — even though nothing about the session had actually changed.

**How I Solved It:** Built a **two-tier refresh strategy**. Once we've captured a token via the WebView flow once, cache it. On every subsequent refresh, try a plain, direct HTTP request (`HttpURLConnection`, Android's built-in HTTP client) with that cached token in the `Authorization` header first — this takes about 200 milliseconds. Only fall back to the full WebView flow if that direct call fails (for example, HTTP 401 because the token expired).

**What I Learned:** Cache tokens aggressively. Most APIs happily accept a Bearer token via a plain HTTP call — you don't need a full browser session for every single request, only to *obtain* the token in the first place.

#### 6. The token-refresh system grew from 2 tiers to 4

Refresh reliability kept getting attacked from a new angle every few weeks, and the system grew a new fallback tier each time:

- **Tier 1 — cached token, direct HTTP (~200ms).** The fast path from problem #5.
- **Tier 2 — refresh token (~500ms).** Keycloak issues a *refresh token* alongside the access token; this lets you mint a new access token without asking for a password again.
- **Tier 3 — direct password grant (~500ms–1.5s).** Keycloak supports a mode (`grant_type=password`) where you can trade a username+password directly for a token over plain HTTP, no browser needed at all. I discovered that adding `scope=openid offline_access` to this request made Keycloak return a refresh token that **never expires** (`refresh_expires_in=0`) — meaning the widget could refresh forever without ever showing a login screen again, even after the phone had been off for days. This "offline_access" discovery was one of the best wins in the whole project. (I confirmed it end-to-end via on-device ADB testing: force an invalid cached token → confirm the fallback path recovers → confirm the freshly-obtained token actually works against the real attendance API, not just against Keycloak.)
- **Tier 4 — full WebView login (~15–30s), last resort.** Only used if every faster tier fails — for example, if the college disables direct password grants server-side (which it eventually did — see below).

**What I Learned:** Design fallback ladders so each tier degrades gracefully into the next, and so a change on the server side (which you don't control) breaks the *slowest* tier last, not the whole system at once.

#### 7. The college disabled the fast password-grant login

**The Problem:** One day, Tier 3 above started returning HTTP 400 `"unauthorized_client"` instead of a token. The code treated *every* 400 response as "wrong password" and blocked login entirely, instead of falling through to Tier 4.

**Why It Happened:** The college's Keycloak admin had disabled "Direct Access Grants" (the setting that allows `grant_type=password`) on our client. This is a legitimate security hardening move on their end, and I had no way to know it was coming.

**How I Solved It:** Only treat an HTTP 400 as "wrong password" if the response body specifically contains `"invalid_grant"`. Any other 400 error (like `"unauthorized_client"`) now falls through to the next tier instead of hard-failing.

**What I Learned:** When you don't control the server, don't assume today's error codes mean the same thing tomorrow. Inspect the *response body*, not just the HTTP status code, before deciding how to react — and always leave a path forward instead of a dead end.

#### 8. Capturing the token from inside the browser flow, the hard way

With the direct password grant disabled, the *only* remaining way to get a fresh token was to intercept it from inside the WebView's normal login flow (Keycloak's "Authorization Code" flow — the standard OAuth redirect dance). This took six attempts:

1. Scan the page's `window` object for a token — doesn't work, the Keycloak instance isn't stored there.
2. Reach into Angular's internal dependency injector to grab its HTTP service — sometimes not registered yet when our script runs.
3. Same idea, different Angular API — same problem.
4. Override `console.log` to detect the string "authenticated" — our hook ran *after* the page's own scripts had already logged and moved on.
5. Use WebView's `shouldInterceptRequest` callback to inject a script into the page's `<head>` *before any page content loads* — this worked, until it broke because Android's networking stack for this specific call used the phone's plain "system DNS," which occasionally failed when the WebView's own connection used encrypted DNS-over-HTTPS with a different resolution path.
6. **Final approach:** hook `onPageStarted` (fires as soon as a new page begins loading, before its scripts run) to inject the XHR/`fetch` interceptors from problem #2, so they're in place the instant the token-exchange request happens.

**What I Learned:** When you're racing a page's own JavaScript to inject a script "early enough," the WebView lifecycle callback you pick matters enormously, and what "early enough" means can shift under you due to things as subtle as which DNS path a particular network stack chooses.

#### 9. The college changed its login server's name

**The Problem:** Attendance, marks, and everything else in the app suddenly failed. The server was up, but every login attempt got HTTP 404 from the token endpoint.

**Why It Happened:** The college renamed its Keycloak "realm" (a named tenant/namespace inside Keycloak) from `itech` to `psgitech`, without telling anyone. This was actually the *third* time an implementation detail like this had silently changed under us — the client ID had already been renamed once before (`sis_web` → `ies_sis`).

**How I Solved It:** Instead of hardcoding the realm name *again*, I found the same configuration endpoint the browser itself uses (`/sis/auth/config`) and built an auto-detection system: on every session, the app fetches this endpoint, parses out the current realm name, server URL, and client ID, and uses those — falling back to hardcoded defaults only if that endpoint is unreachable.

**What I Learned:** If a third-party system exposes a "here's my current configuration" endpoint (even one meant for its own browser client), use it instead of hardcoding values that server administrators can and will change without notice. This turns a "the app broke" incident into "the app quietly adapts."

#### 10. Removing the WebView from the *default* login path entirely

**The Problem:** Even after all the refresh optimizations above, a brand-new install's *very first* login still took 40+ seconds and often timed out, because the code always fell through to the slow WebView flow for the initial login — the direct-grant path was originally wired up only for *refreshing*, not for first-time login.

**How I Solved It:** Rewired the login function to try the fast password-grant request *first* even on a fresh install, and only fall back to the WebView if that fails. First login time dropped from 40+ seconds (with frequent timeouts) to about 21 seconds.

**Related fix:** All 18 places in the networking code that set connection/read timeouts were hardcoded to 10 seconds, but I'd measured the real college server taking 15–25 seconds to respond on a bad day. Every one of those calls was failing on the first try, triggering an expensive retry-and-fallback chain. Bumping every timeout to 30 seconds eliminated that entire class of unnecessary retries.

**What I Learned:** Measure your actual server's real-world response times before picking a timeout value — a timeout that's technically "generous" by common wisdom can still be far too short for a specific, slow, third-party server you don't control.

#### 11. Login no longer required attendance to succeed

**The Problem:** If the attendance API happened to be down (which the college's servers were, periodically), login would fail *entirely* — locking students out of chess, marks, results, and every other feature just because one specific API was unavailable.

**How I Solved It:** Decoupled login from attendance: if the security token exchange succeeds but the attendance fetch fails, the user is logged in anyway with a placeholder 0% attendance. Every other feature works normally, and a pull-to-refresh later picks up attendance once the server recovers.

**What I Learned:** Don't let one flaky downstream dependency gate access to features that don't need it. Treat "am I logged in" and "do I have fresh data for feature X" as two separate questions.

### Where the token flow ended up

```
Tier 1: grant_type=password (direct login by username+password)
        → Works normally. Disabled server-side as of one incident;
          the app detects the specific error and falls through cleanly.

Tier 2: grant_type=refresh_token
        → Works whenever a refresh token is cached from a previous
          successful login. Never expires, thanks to offline_access.

Tier 3: WebView XHR hook (onPageStarted injection)
        → Captures a fresh Bearer token by intercepting the token
          exchange during a real browser-based login. Timing-sensitive.

Tier 4: Full WebView login (browser session + cookies)
        → Always works, slowest (~15-30s). True last resort.
```

---

## The Home-Screen Widget & Background Sync

### The core idea

Android home-screen widgets don't run inside your app's normal UI system — they're drawn by a separate system process using **RemoteViews**, a deliberately restricted UI toolkit (you can't use arbitrary custom views or full Jetpack Compose inside a widget; only a fixed set of pre-approved layout elements). Jetpack **Glance** is a newer library that lets you *describe* a widget using Compose-like syntax, which then gets compiled down into RemoteViews under the hood — so you get a nicer developer experience without the widget actually running full Compose.

Because a widget can't make its own network calls reliably (it isn't a full running app), the pattern is always the same: a **background job** periodically fetches fresh data and writes it to local storage, and the widget simply reads whatever's currently in that storage and redraws itself. This section covers how that background job evolved.

#### 1. Self-chaining background refresh instead of Android's normal periodic jobs

**The Problem:** Android's standard `PeriodicWorkRequest` (the API for "run this every N minutes in the background") has a hard minimum interval of 15 minutes. We wanted the widget to feel closer to real-time.

**How I Solved It:** Used a "self-chaining" `OneTimeWorkRequest` instead — a background job that, when it finishes, schedules *another* one-time job 8 minutes later. Because each link in the chain is a `OneTimeWorkRequest`, it isn't bound by the 15-minute periodic minimum.

**What I Learned:** When a platform API's built-in minimum doesn't fit your use case, check whether a *different* primitive in the same API family (one-time vs. periodic jobs) can be chained to get the effect you want.

#### 2. Getting the OS to actually let the background job run

Even a correctly-scheduled background job can get killed by Android's aggressive battery optimization on some phone manufacturers (Samsung, Xiaomi, etc. are notorious for this). The fix was a one-time dialog asking the user to add the app to the battery-optimization whitelist, which stops the OS from freezing the app's background jobs.

#### 3. Push notifications piggybacked on the same background job

Once a reliable background job existed for refreshing attendance, it became the natural home for several other "check something and notify the user" features, all following the same shape: **run periodically → check a condition → show a system notification with a deep link back into the specific screen the notification is about.**

- **Update notifications** — checks GitHub's releases API for a newer app version and notifies with a download link, tracking the last-notified version so it doesn't repeat itself.
- **Holiday notifications** — checks a shared Google Calendar the evening before a holiday and notifies "no classes tomorrow."
- **Circular notifications** — checks for newly-published college circulars (announcements) every few hours.
- **Chess challenge notifications** — checks `ActivityManager.runningAppProcesses` to see whether the app is currently in the foreground; if it's backgrounded, fires a heads-up notification for an incoming chess challenge so the 15-second accept window doesn't silently expire while the user isn't looking. (This only works while the app process is still alive in the background — if the user force-stops the app entirely, there's no way to wake it up without a real push-notification service like Firebase Cloud Messaging.)

**What I Learned:** Once you have one reliable "wake up periodically and do something" mechanism, it's cheap to reuse it for several unrelated notification features rather than building a separate scheduling system for each one. Also: a background-job-based notification is fundamentally different from a true push notification — it only fires if the app process is still alive, which is a real limitation worth documenting rather than assuming.

#### 4. Widgets can't blur, no matter how nice the rest of the app looks

Once the main app grew a whole "liquid glass" frosted-blur visual design (see [UI & Design](#ui--design-liquid-glass-animation-and-responsive-layouts)), it was tempting to want the same look on the widget. RemoteViews doesn't support real-time GPU blur shaders — it's a much more limited rendering surface than a full Compose screen. The widget stays a solid, opaque dark background by design; there's no way around this on stock Android.

**What I Learned:** Know the hard technical ceilings of a platform surface (widgets, in this case) before promising design parity with the main app. Some visual effects are simply off the table, and it's better to design a good-looking *opaque* widget than to chase an impossible blur effect.

---

## Attendance & Data Fetching

This section covers everything about turning the college's raw API responses into accurate, fast-loading screens: per-subject breakdowns, timetables, exam results, and — a recurring theme — making a *slow* server (15–25 second responses on a bad day) feel fast through caching and parallelism.

#### 1. Building accurate subject-wise attendance (three iterations)

**The Problem:** Students wanted to know their attendance *per subject*, not just overall. The college's SIS portal doesn't expose this directly.

**Iteration 1 — Estimates.** Combined the timetable (how many periods per week for each subject) with the list of absent days to *estimate* total classes per subject proportionally. This was rough — it worked, but produced numbers that could be visibly wrong for a subject that met at an unusual frequency.

**Iteration 2 — Exact counts.** Discovered a second college API that returns actual present-day counts per subject (`/sis/attendance/present/{roll}`), alongside the absent-days API that already existed. Present + Absent + Exemption = exact total per subject, no estimation needed.

**Iteration 3 — Honours-course detection (three stacked bugs).** Some students take extra "honours" elective courses beyond their normal curriculum, but the timetable API doesn't flag which courses are honours vs. regular. I built a detector that compares a student's actual timetable against the *standard* published curriculum for their department — any course beyond the expected slots must be an honours addition. Getting this right took three separate bug fixes:
   - The registration API returned some course credit values as decimals (`1.5`), but my Kotlin model declared the field as `Int`. Gson threw a parsing exception on *every* decimal credit, silently failing the entire registration fetch — so the honours detector always saw zero registered courses and misfired on every elective. Fix: change the field type to `Double`.
   - After fixing that, the registration API returned placeholder codes like `PE64__` for elective slots instead of the actual course the student picked. These placeholders never matched real timetable codes, so *every* elective looked like an honours course. Fix: skip any code containing an underscore.
   - Even with placeholders skipped, the registration API alone didn't reliably say which specific elective a student had chosen. The fix was to **cross-reference two independent data sources**: the registration API (regular courses) and the attendance API (every course the student has actually attended, including electives, with real course codes). If a student has attendance records for a course, they're definitely enrolled in it — that's ground truth. Merging both sets closed the gap.

A related bug: the timetable API returns *every* possible elective option for a given time slot, not just the one a specific student picked (so two different courses would show up scheduled at the exact same time). Fixed by filtering out any option not present in the student's actual attendance/registration data, with a safety fallback to show everything if there's no data yet to filter against (e.g., very start of a new semester).

**What I Learned:** When one API gives you incomplete or ambiguous data, look for a *second* API whose data can serve as a cross-check. The attendance record is inherently "ground truth" — if a student has attendance rows for a course, they are enrolled in it, full stop. Also: always verify a third-party API's numeric fields against real responses; assuming "credits are always whole numbers" cost real debugging time.

#### 2. Discovering undocumented APIs by watching the browser, not reading docs

Several features (exam results, exemption details, the exact timetable structure) had **no public documentation** at all. The technique that worked every time: use browser automation tools to log into the actual college website, open its browser developer tools, and watch which network requests the site's own JavaScript makes when a given page loads. This is how the semester results endpoint was found — by reading the compiled JavaScript source for the "View All Results" page, spotting the internal function name (`viewAllResultsServices.getAllResults()`), and then watching the *actual* network request it produced to get the real, working URL and parameters (documentation and source-reading alone got the URL wrong; only watching the live network tab revealed the missing required parameter).

**What I Learned:** For any web app built with a JavaScript framework, "the API" is whatever network calls the page's own code makes when a human uses it — you can discover this by driving a real browser session and reading the network log, even with zero official documentation.

#### 3. Making a slow server feel fast

**The Problem:** The app felt sluggish. Sequential API calls (fetch attendance, *then* fetch marks, *then* fetch timetable, one after another) meant waiting for the sum of every single call's response time — 60+ seconds in the worst case, since individual college-server responses regularly took 15–25 seconds each.

**How I Solved It — three changes, in order of impact:**

1. **Benchmarked before optimizing.** Rather than guessing, I built a small standalone test app and ran 10-round timed benchmarks comparing `HttpURLConnection` (Android's built-in client) against OkHttp (a popular third-party client known for connection pooling), both sequentially and in parallel.

   | Method | Average time |
   |---|---|
   | HttpURLConnection, sequential | 41.5 seconds |
   | OkHttp, sequential (with connection reuse) | 45.7 seconds — *10% slower* |
   | OkHttp, parallel (all requests fired at once) | 10.1 seconds — *75% faster* |

   The surprising finding: connection pooling (OkHttp's headline feature) provided **zero** benefit here, because the college server's own processing time (15–25 seconds per request) dwarfs any savings from reusing a TLS connection. The only thing that actually mattered was **firing every independent API call at the same time** instead of one after another.

2. **Fired everything in parallel from the start**, not just after the first screen loaded. Using Kotlin's structured concurrency (`coroutineScope { async { ... } }`, which lets you launch several operations that all run concurrently and then wait for all of them together), every independent API call — attendance, CA marks, results, present/absent days, timetable, circulars — now starts the instant the app opens, instead of waiting for attendance to finish first. Total load time became "however long the *slowest single* call takes," not "the sum of every call."

3. **Persisted the full JSON of everything to disk**, not just an in-memory cache, so that a cold app restart shows instantly-available (if slightly stale) data immediately, while a background refresh quietly updates it. This "stale-while-revalidate" pattern (show old data immediately, replace it silently once fresh data arrives) eliminated blank loading screens almost everywhere in the app.

**Decision I explicitly did *not* make:** switch the whole app to OkHttp. The existing 4-tier authentication system (see [Authentication & Login](#authentication--login)) was thoroughly tested and working for 1,700+ users; the benchmark proved the *real* win (parallelism) was achievable without touching that battle-tested code at all.

**What I Learned:** Measure before optimizing — the "obviously better" tool (OkHttp) was actually slightly *slower* for this specific workload. The real lesson is architectural: don't sequence independent network calls. Fire them all at once and wait for the slowest one, rather than waiting for the sum of all of them.

#### 4. A time-of-day bug: "Today" stuck on yesterday

**The Problem:** The timetable screen's "Today" highlight sometimes pointed at the wrong day.

**Why It Happened:** The day-of-week was computed exactly once, inside the ViewModel's `init` block (code that runs only when the screen's state-holder object is first created). Android can keep an app's process alive in memory across midnight — if a student opened the app Tuesday night and it stayed loaded into Wednesday, "today" was frozen at Tuesday forever.

**How I Solved It:** Recompute the day index every time the screen becomes visible (in a `LaunchedEffect(Unit)`, code that reruns whenever the screen is freshly composed), not just once at creation.

**What I Learned:** Never compute anything date- or time-dependent only in a screen's initializer. Long-lived app processes will make that value stale. Recompute it on every visibility change instead.

---

## UI & Design: Liquid Glass, Animation, and Responsive Layouts

### "Liquid Glass": building a real frosted-glass UI

The visual centerpiece of the app is an iOS-style "liquid glass" effect — cards and the bottom navigation bar look like real frosted, refractive glass, using actual GPU shader effects (not a flat semi-transparent color, which is what most "glassmorphism" tutorials actually do). This came from a third-party library (`io.github.fletchmckee.liquid`) after evaluating a couple of alternatives that were either too experimental or required an incompatible Kotlin/build-tool version.

#### 1. Making glass performant: two tiers of "glass"

**The Problem:** Applying the real GPU-shader glass effect to *every* card, including items inside a scrolling list, made scrolling visibly laggy.

**How I Solved It:** Split glass rendering into two tiers:
- **`LiquidGlassCard`** — the real, expensive GPU shader (refraction, edge reflections, chromatic dispersion) used only on *static* elements that don't scroll: headers and the main dashboard card.
- **`GlassListCard`** — a lightweight, cheap approximation (drawn with plain Canvas operations, zero GPU shader cost) used for anything inside a scrolling list.

**What I Learned:** A single "does this look nice" component often needs two implementations at two different cost tiers — one for elements the user stares at, and one for elements that fly past during a scroll.

#### 2. A subtle rendering bug: glass blur was actually a crash risk

The underlying glass library required careful separation of *state* — cards use one shared "liquid state" object (`cardState`) and the bottom bar uses a separate one (`barState`). Nesting a `liquid()` shader node as a descendant of a `liquefiable()` node using the *same* state object crashed the app outright (a low-level rendering crash, SIGSEGV). The fix was architectural: keep the two state objects strictly separate, and make sure content lives *inside* the liquefiable layer so the glass bottom bar can blur the actual scrolling content behind it, not just a static background gradient.

**What I Learned:** Third-party rendering/shader libraries can have hard architectural constraints (in this case, "never share state across nested liquid nodes") that aren't always obvious from the API surface — they show up as crashes, and the fix is often "restructure your composition tree," not "pass a different parameter."

#### 3. Compose performance: debug builds lie to you about speed

**The Problem:** A scrolling list of absence records was visibly jittery and dropped frames — even after flattening nested loops, replacing shadowed `Card` components with plain backgrounds, pre-computing date-formatting strings instead of doing it during rendering, and sharing shape objects instead of recreating them every frame.

**Why It Happened (the real answer):** All of the above *did* help, but the scrolling was still laggy — because I was testing a **debug build**. Compose's debug builds disable essentially all compiler optimizations (no skipping unchanged UI, no memoization) so that development tools work correctly. This can make a debug build 5–10x slower than the same code in a release build.

**How I Solved It:** Built and tested a **release** build (with R8, Android's code shrinker/optimizer, enabled) — the scrolling was smooth.

**What I Learned:** Never judge Compose UI performance from a debug build. Always benchmark scrolling and animation smoothness on a release-configured build.

#### 4. Font scaling and screen-size defensiveness

**The Problem:** A friend using a phone with a larger-than-default system font size (an accessibility setting many users enable) saw multiple screens visibly break — text wrapping vertically one character per line, icons pushed off-screen, badges squeezed to nothing.

**Why It Happened:** Compose text uses `sp` units, which scale with the *user's* font-size preference, but surrounding containers are often sized in fixed `dp` units that don't scale. When text grows and its container doesn't, one of two things happens depending on the layout: text overflows and wraps unpredictably, or it steals all the horizontal space from its siblings.

**How I Solved It, twice — once broadly, once for the specific case that slipped through:** Audited every `Row` (Compose's horizontal-layout container) across the app for two defensive patterns:
   - Any text that could grow unbounded gets `maxLines = 1` plus `overflow = TextOverflow.Ellipsis` (truncate with "…" instead of wrapping).
   - Any text sharing a row with icons or badges gets `Modifier.weight(1f, fill = false)` — this is the crucial, easy-to-get-wrong detail. `weight(1f)` alone forces an item to *always* fill its share of the row, even if its content is short, which can visually stretch small text into a huge uneven gap. Adding `fill = false` says "take up to this share of the space, but only if you actually need it," letting siblings (icons, badges) keep the space they need.

   The dashboard's own attendance stats row (Present/Absent/Total/Exempt/Pending) was fixed *later*, in a second pass, after a friend's screenshot showed the same vertical-letter-wrapping bug on "Pending." It had been missed in the first sweep because the developer's own test account happened to always show fewer than 5 stats at once (some students have 0 exemptions, hiding a column) — the bug only appeared with the *maximum* number of simultaneously-visible items, at a specific font scale, with three-digit numbers. That combination never occurred during normal development testing.

**What I Learned:** When auditing a UI for a defensive pattern, don't just fix the rows you notice — the pattern needs to be applied to *every* row with dynamic content, and testing needs to specifically exercise the worst case (maximum item count, largest font scale, longest text), not just the common case a developer's own test account happens to produce.

#### 5. Custom animations, iterated against real user feedback

Several bespoke animations were built from scratch using Compose's `Canvas` API (draw primitives — lines, circles, paths — directly, rather than composing pre-built widgets), each going through multiple rounds of "no, not like that" feedback:

- **A "comet" light-trail animation** on pull-to-refresh went through three failed shapes (a rotating orb, a rotating rectangle sweep, a stroke sweep) before landing on the final design: 20 overlapping radial-gradient circles trailing along the card's rounded-rectangle border, clipped so the glow never spills outside the card shape.
- **An animated 3D isometric chess icon** for the bottom navigation bar took nine visible iterations — flat rectangles → fake depth via extra side-panels → true isometric projection math → adding "glossy" highlight/shadow layers → shrinking distracting sparkle effects the user found "weird" → replacing sine-wave "bouncing" piece movement with flat sliding movement → replacing a fixed repeating move-loop (which visibly restarted every few seconds) with independent randomized coroutine loops per chess piece, so the pattern never repeats.
- **A "rose curve" loading spinner**, replacing the generic default spinner everywhere in the app, is built from actual polar-coordinate math (`r = a·cos(4θ)`, the classic four-petaled rose curve) with 78 trailing particles and a slow "breathing" amplitude modulation — deployed across 14 different screens.
- **A slot-machine-style digit animation** for the attendance percentage: each digit scrolls like a physical slot-machine reel, with staggered start times per digit and an easing curve tuned for a "fast spin → slow settle" feel.

**What I Learned:** Building a custom animation from a reference image or video is inherently an iterative back-and-forth — the person giving feedback will notice things like "that looks bouncy, I wanted sliding" or "there's a weird glow, remove it" that are hard to predict up front. Budget for several rounds, and treat early attempts as disposable sketches, not committed designs.

#### 6. Discoverability: making a tappable element look tappable

**The Problem:** Users didn't realize the profile picture on the dashboard was tappable — it read as a static decoration.

**How I Solved It:** Added a subtle animated double-ripple ring (like radar) expanding outward from the picture in an infinite loop, plus a faint colored border.

**What I Learned:** A subtle *animation*, not a text label, is often the most effective way to communicate "this is interactive" without cluttering the UI — a static border alone doesn't read as clearly as something gently moving.

---

## Weather Backgrounds & the Water Animation

Two of the most visually distinctive (and technically involved) features are a background weather-scene system and a physics-driven "water" animation inside the attendance card.

### Weather-themed backgrounds

The app can render a configurable ambient background behind every glass card — Sunny, Cloudy, Rain, or Thunderstorm — cycling through a toggle in the Profile screen. Every effect had to live specifically *inside* the same rendering layer that the glass cards refract, so that the glass on top of a card genuinely shows a blurred version of the rain/clouds/rays behind it, not a flat static image.

#### A critical rendering lesson: never put a Canvas overlay inside a Box next to a glass-shader parent

**The Problem:** An early implementation of the water animation (see below) put a `Canvas` composable as a sibling inside a `Box`, layered with `Modifier.matchParentSize()` next to the glass card's content. The result was bizarre: the water rendered as full-screen-tall vertical stripes, smeared across the *entire* dashboard, bleeding into every other card.

**Why It Happened:** The glass library achieves its blur/refraction using `RenderEffect.createBlurEffect` inside a `graphicsLayer` modifier — a low-level Compose API that captures the pixels *behind* a composable and re-projects them through a shader. When a plain `Canvas` sits inside that same composition subtree as a sibling, the graphics layer's compositing pipeline treats the Canvas's pixels as part of the "backdrop" it's supposed to blur, and the shader's sampling smears it vertically across the whole screen.

**How I Solved It:** Never add a *new* Canvas composable or Box layer inside a glass-shader parent. Instead, expose the animated state as a plain state-holder object, and draw into the *existing* layout's own draw scope using `Modifier.drawBehind { ... }` (a modifier that lets a composable draw extra content behind its normal children, in the same draw pass, without adding a new layout node). This draws in the same pass as the glass refraction, with no separate compositing layer to collide with.

**What I Learned:** This is the single most important lesson in the whole "weather + water" thread, worth remembering for any project using shader-based blur effects: **any animated overlay that needs to live underneath content inside a `graphicsLayer`-based blur parent must be a `drawBehind` extension on the existing draw scope — never a new Canvas/Box sibling.** The compositor cannot tell the difference between "this is new decorative content" and "this is part of the backdrop to blur."

### The water physics animation

Beyond the weather backgrounds, the attendance card itself got an animated "water fill" — water height maps to the attendance percentage, it tilts when you tilt the phone, and it sloshes gently when you scroll.

#### Choosing the right kind of "physics"

The first instinct — using a general-purpose 2D physics engine (matter.js-style rigid-body simulation) — turned out to be the wrong tool. A rigid-body engine simulates individual solid objects colliding, which means "simulating water" requires spawning hundreds of small circles held together with constraints: expensive to compute on entry-level phones, and visually it looks like a ball pit, not a water surface.

**The right model:** a small number (dozens) of **spring-coupled surface nodes** — evenly spaced points across the water's width, each with a vertical position and velocity. Each frame does two passes: (1) a spring force pulls each node toward its resting height, with damping to bleed off energy, and (2) each node nudges its immediate left/right neighbors' velocities proportionally to the height difference between them — this second pass is what turns a local disturbance into a wave that visibly *travels* across the surface instead of staying put.

Tuning this took real iteration: an initial two-pass coupling with too strong a "spread" constant caused waves to grow instead of damp out (an unstable feedback loop); switching to a single-pass coupling with lower spread and higher damping made the system settle cleanly within about 30 frames after any disturbance.

#### Feeding real-world inputs into the simulation

- **Phone tilt** comes from the device's gravity sensor, read only while the dashboard is visible (registered/unregistered via `DisposableEffect`, a Compose API that runs cleanup automatically when a composable leaves the screen) — so there's zero battery cost when the screen isn't showing.
- **Scroll** is observed by watching the scroll position and converting frame-to-frame deltas into velocity impulses on the water nodes. The very first attempt over-scaled this and produced a "tsunami" on a fast scroll fling, because the impulse was re-applied *every single frame* for the whole duration of the scroll gesture; the fix was a much smaller scale factor and a hard cap on the impulse per frame.
- **The attendance percentage itself** can't be fed straight into the spring solver — it jumps instantly from 0 to (say) 78.4% the moment the network call resolves, and a spring system cannot absorb an instant step input cleanly; it tries to catch up in one frame and overshoots violently. The fix: wrap the raw percentage in a smoothing animation (`Animatable` + a tween over 800 milliseconds) before feeding it to the physics system as its new resting target — the visible water height ramps up smoothly, and the springs track that smooth ramp instead of getting yanked.

#### Bugs that only showed up in a hands-on test sandbox

Because the water was originally embedded directly on the crowded dashboard, several bugs were nearly undiagnosable there (too many other things recomposing at the same time to isolate a cause). Building a tiny, standalone debug-only test screen — just a slider and a plain rectangle running the same water code — made each bug obvious once isolated:

- **The tank looked completely empty at first.** The physics was running every frame, but the drawing wasn't — because the drawing code read from a plain `FloatArray` (a low-level array of numbers), and mutating that array's contents isn't something Compose's rendering system can "see" as a change. The fix was to add an unrelated but *observable* piece of state (a simple integer "tick" counter) that increments every physics frame, and have the drawing code read that counter — even though the counter's value is never *used* for anything except forcing Compose to notice something changed and re-run the draw.
- **Dragging a test slider killed all wave motion.** Every tiny slider movement (dozens per second) was restarting an in-flight smoothing animation from scratch, so the system was permanently chasing a target that reset every 16 milliseconds and never got anywhere. The fix: snap instantly to very small changes (below a threshold) instead of animating them, and only run the full smoothing tween for genuinely large jumps.
- **A sustained device shake froze the water and dropped the frame rate.** An accelerometer-driven "slosh" impulse was firing on every single sensor sample (50 times a second) with no throttling, saturating the velocities until every node hit its physical position limit and the solver got stuck oscillating on tiny timestep noise. The fix: add a minimum time gap between accepted impulses (a "refractory window," borrowed from how real neurons work) plus a hard velocity clamp as a safety net.

**What I Learned (the general pattern, not just for water):** For any animated draw that's backed by mutating a plain, non-observable data structure (an array, a mutable object field), expose a trivial observable counter and increment it every frame — Compose's re-drawing is driven entirely by what state it can "see" being read, not by what actually changed underneath. Also: throttle *every* external impulse source (sensors, sliders, scroll) feeding into a continuous physics solver, sized to the solver's own natural settling time — sensors, drag gestures, and scroll events can all fire far faster than a physics system can meaningfully absorb.

#### Removing visible color "banding"

A gradient behind the water initially used four distinct color-and-transparency stops (surface to floor). Feedback: "I can see bars, like rectangles — the color should blend smoothly." Adding *more* intermediate stops (4 → 9 → 13) helped but never fully fixed it. The actual fix was to throw out the multi-color-anchor approach entirely and use a **single hue family** with a monotonically decreasing brightness (surface color fading down to a 55%-brightness version of the *same* color) — no stops where the slope of the gradient changes direction.

**What I Learned:** The human eye reads a visible "band" or "edge" wherever a gradient's slope changes direction, even at very low contrast — no number of additional intermediate stops fixes that; you have to remove the slope changes altogether by sticking to one color family with a single, smooth brightness ramp.

---

## Chess: Building a Real-Time Multiplayer Feature

### Why chess, and why not build a chess engine

With roughly 1,500 active users, many of them playing chess informally, the goal was: let two students find each other and start a real game, for free, anonymously, with no account sign-up. Rather than writing a chess engine and rules validator from scratch, the entire approach was to build only a **matchmaking lobby**, and hand off the actual game to **Lichess.org**, a free, open chess platform with a public API that can create anonymous game links (`POST /api/challenge/open`) requiring no login and no API key.

### Version 1: Firestore-based lobby

The first working version used **Firebase Firestore** (a real-time cloud database) for everything: a collection of "who's currently online" documents, a collection of pending challenges, and a collection of player profiles/stats.

- **Anonymous identity:** each student's roll number is hashed into a stable, non-reversible player ID, and paired with a randomly-assigned chess-themed nickname (e.g., "SilentKnight#42") — the same student always gets the same nickname, but a nickname alone can't be reverse-engineered back to a roll number.
- **Presence:** each online player writes a "heartbeat" document with a timestamp every so often; other clients watch the whole collection and filter out anyone whose timestamp is too old.

#### The recurring cost problem with "presence via a database"

**The Problem:** As the user base grew, Firestore's free-tier daily quota (50,000 reads / 20,000 writes) started to bite. Every online player's heartbeat write triggers a live-update notification to *every other* connected client's listener — meaning the *read* cost scales roughly with the **square** of the number of concurrent users, not linearly. With N concurrently-online players each heartbeating every K seconds, the daily read count is approximately `N² × (86,400 / K)`.

**Interim mitigation:** stretched the heartbeat interval from 25 seconds to 90 seconds, buying roughly 3.6x more headroom before hitting the quota — but this was a forced compromise, not a design choice: any *slower* heartbeat interval would make "is this person online?" feel sluggish and outdated (users would see "active 90 seconds ago" constantly), while any *faster* interval would blow through the daily quota sooner.

**What I Learned:** A general-purpose database's read/write billing model is fundamentally the wrong fit for *presence* (the "is this specific connection still alive right now?" problem) — every implementation ends up needing a heartbeat, a staleness window, and workarounds for browser tabs freezing background timers. This is a strong signal that presence wants a fundamentally different kind of primitive.

### Version 2: Migrating to Cloudflare Durable Objects

At roughly 2,500 users, the read-cost math above made the Firestore-based lobby a dead end. The fix was to move only the *ephemeral* lobby state (who's online, pending challenges) to a completely different kind of backend, while leaving persistent data (player profiles, win/loss stats, match history) on Firestore untouched.

**The new primitive:** a **Cloudflare Durable Object** — a single, stateful server instance (one JavaScript/TypeScript object, running at the edge) that every client connects to over a **WebSocket** (a persistent, two-way network connection, unlike an HTTP request which is a one-shot round trip). The Durable Object keeps an in-memory list of who's connected and broadcasts presence changes directly over each open connection.

Why this eliminates the whole cost problem: **presence becomes the WebSocket connection itself.** There's no heartbeat to write, because a closed TCP connection *is* the "user went offline" signal, detected within about a second — versus Firestore's 90-second heartbeat-plus-staleness-window design. On the free tier, Cloudflare bills per *message*, and a WebSocket's idle time (thanks to a feature called **WebSocket Hibernation**) costs essentially nothing — the server can "sleep" while a connection stays technically open, waking only when an actual message arrives.

#### A subtle bug: hibernation resets in-memory state, but not the sockets

**The Problem:** After deploying, two clients would both show "online," but neither would ever see the *other* in their lobby list.

**Why It Happened:** WebSocket Hibernation means Cloudflare can put the Durable Object to sleep to save cost, and *does* keep the actual socket connections alive across that sleep — but the object's own in-memory JavaScript fields (like a `Map` of connected players) get wiped and rebuilt fresh whenever the object wakes back up. The code assumed the in-memory `players` Map would always reflect who was actually connected; after a hibernation cycle, it started empty even though real connections were still attached.

**How I Solved It:** In the Durable Object's constructor, explicitly rebuild the in-memory map from the *sockets themselves* (which Cloudflare's API lets you enumerate even after hibernation) plus a small piece of metadata "attached" to each socket when it first connected (its player ID and display name), rather than assuming any in-memory state survived.

**What I Learned:** With Cloudflare Durable Object hibernation, the sockets persist across a sleep cycle, but ordinary in-memory class fields do **not** — anything you want to survive hibernation must either go through the Durable Object's dedicated persistent storage API, or be rebuilt from the live sockets and their attached metadata every time the object wakes up. This is easy to miss because it can't be reproduced locally (hibernation only kicks in under real, idle-for-a-while runtime conditions) — a locally-run copy of the exact same code can look completely correct.

### Playing the actual game inside the app

#### Why WebView instead of a real native chess board

Building a full native chess board (drag-and-drop pieces, move validation, clock, resign/draw handling) is a large undertaking. Instead, since the game itself lives on Lichess.org, the app opens the Lichess web game **inside an in-app WebView** rather than kicking the user out to Chrome. Lichess's own mobile web interface is lightweight (their board rendering library is about 10KB) and already has everything needed — no native chess logic to write at all.

#### The flickering saga: five attempts to stop a WebView from strobing

**The Problem:** Once wired up, the in-app Lichess board flickered badly — visibly strobing whenever anything on the page changed (a clock tick, a piece move).

Five attempts, in order:

1. **Layer the loading spinner and buttons on top of the WebView in a `Box`.** Severe flickering — Compose had to composite three separate layers every single frame, and even an *invisible* overlay component remained in the composition tree and interfered with rendering.
2. **Inject CSS to hide Lichess's own header/chat, re-enforced via a `MutationObserver`** (a browser API that watches for any change to the page's structure and re-runs a callback). Even *worse* flickering — the observer fired on every DOM mutation, including the clock ticking every single second, forcing constant layout recalculation.
3. **Same CSS injection, but only once, when the page finishes loading.** Still flickering, because in a single-page web app like Lichess's, the "page finished loading" event fires repeatedly for sub-resources, and injecting mid-load forced the browser to reparse content it had already started rendering.
4. **Remove all JavaScript injection entirely.** The flickering stopped, but now Lichess's own header (sign-in/register buttons, hamburger menu) was visible — functional, but visually non-native.
5. **The actual root cause, found last: it was never the CSS injection — it was the `Box`-based layering.** Switching the *layout* from a `Box` (stacking things on top of the WebView) to a plain `Column` (buttons in a row *above* the WebView, WebView taking the remaining space *below*) meant nothing ever overlaid the WebView at all. Once that structural change was in place, the original one-time CSS injection could be safely re-added (with an idempotency guard so it never re-runs) with zero flickering.

**What I Learned:** Never stack Compose UI *on top of* an `AndroidView`-wrapped WebView inside a `Box`. Compose's own renderer and a WebView's Chromium-based renderer are two entirely separate rendering pipelines, and forcing Compose to composite both together every frame causes real GPU contention and visible flicker — regardless of what JavaScript you're or aren't injecting into the page. Keep them side-by-side (`Column`/`Row`), never layered. When a JavaScript-injection fix "helps a little but doesn't fully solve it," that's a signal the actual root cause might be somewhere else entirely — in this case, five separate JavaScript-tuning attempts were treating a symptom of a *layout* problem.

A related discovery: `WebChromeClient` (the API for handling things like JavaScript `alert()` popups and console logging inside a WebView) doesn't forward `console.log()` output to Android's log system by default. Overriding `onConsoleMessage` to pipe injected-script logs to a dedicated log tag turned an otherwise invisible debugging problem (silently failing JavaScript with zero output) into a two-line `adb logcat` fix.

#### The "two ID systems" architecture debt

As the migration to Cloudflare progressed, a subtle mismatch emerged: the *old* Firestore-based system identified each player by a hash of their roll number (`p_<hash>`), used everywhere for friends, profiles, and match history — but the *new* Cloudflare-based presence system identified each connection by a **Firebase Authentication UID** (a different, unrelated identifier used only for verifying the WebSocket connection's token). The same physical student ended up with two completely different IDs depending on which subsystem was looking at them, which meant "is this online player one of my friends?" comparisons silently failed, because they were comparing IDs from two different ID spaces that happened to look superficially similar.

**The workaround (not a full fix):** bridge the two ID spaces using **display name** as a secondary matching key, since display names are — in practice, though not by strict guarantee — unique per student. **The proper fix** (deferred) is having the server pass through the roll-number-based ID instead of the unrelated auth UID, which requires a coordinated schema change across three separate codebases (Android app, web app, and the Cloudflare server) at once.

**What I Learned:** When two systems built at different times each invent their own identifier for "the same real-world person," they *will* eventually need to compare or merge that identity, and by then the two ID spaces are already baked into a lot of stored data. It's worth explicitly deciding on and documenting a single canonical identity scheme as early as possible, even for what looks like a small side feature — retrofitting one later touches every layer at once.

#### A grab-bag of smaller, instructive chess bugs

- **Reading state from inside a long-lived listener closure, after the state had already changed.** A "notify me if my opponent leaves" listener read a piece of state (`acceptedChallenge`) that had already been cleared to null by the time the listener actually fired, because the code that cleared it ran the instant the game screen opened, while the listener itself only fires *later*, asynchronously. **Fix pattern:** capture the values you need into local variables at the moment you *register* the listener, not read them fresh from shared state whenever the listener eventually fires.
- **Two independent code paths could both try to set "who won" at once** (Lichess's own polling detecting a resignation, versus a synthetic "opponent abandoned" result from a disconnect), causing the winner's name to visibly flicker between two different values. **Fix pattern:** a simple "first writer wins" guard (`if (gameResult == null) gameResult = ...`) on every single code path that could set the same piece of state.
- **A Firestore query combining an inequality filter on one field with sorting by a *different* field** (`whereGreaterThan("gamesPlayed", 0)` combined with `orderBy("wins")`) is a documented Firestore limitation that silently produces an empty result unless a matching composite index exists — and the failure was being silently swallowed by a catch block returning an empty list, with zero visible error. **Fix:** for small collections, it's simpler and more robust to just fetch everything and filter/sort on the client than to manage a composite index.
- **Firestore field type mismatches crash at *read* time, not write time.** A `timeControl` field was written as a plain number in one code path and as a string in another; reading it with a type-specific getter (`doc.getString(...)`) threw a runtime exception the moment it hit a document written the other way. **Fix:** read with a type-agnostic getter and convert with `.toString()`, rather than assuming a Firestore field's type is consistent across every code path that ever wrote to it.

---

## The On-Device AI Study Advisor

### The goal, and the core constraint

Add a chat-style AI advisor that can answer natural-language questions about a student's own attendance, marks, and syllabus — entirely **on the phone**, with no cloud API calls. This constraint wasn't arbitrary: an API key bundled into the APK is trivially extractable by decompiling the app, and per-token cloud costs for 1,400+ users would be real, ongoing money for a free student project.

### Picking an on-device inference engine

Several frameworks for running a language model locally on a phone were evaluated:

| Option | Verdict |
|---|---|
| A cloud LLM API (GPT/Claude-style) | Rejected — API key would be extractable from the APK, and per-token cost doesn't scale to a free app with 1,400+ users |
| Google **LiteRT-LM** running Gemma | Best acceleration *if* the phone has a capable NPU/GPU (Google's own on-device inference stack) — but most students' phones are mid-range, without that hardware, dropping speed to unusably slow on CPU alone |
| **Cactus** (a wrapper around `llama.cpp`, a popular open-source CPU-optimized inference engine) | Fast enough on ordinary mid-range CPUs, simple Kotlin SDK, small model download — **chosen for the first version** |
| Raw `llama.cpp` directly | Maximum control, but requires hand-writing C++/JNI bindings — too much integration effort for the payoff over using Cactus's ready-made wrapper |

Because most students' phones are mid-range (no dedicated NPU/AI-acceleration chip), an engine tuned for plain CPU inference beat one that's much faster *only* on flagship hardware.

### The key architectural insight: split "doing math" from "talking"

A very small language model (under 1 billion parameters, which is what fits comfortably on a phone) **will get arithmetic wrong** — "52 out of 67, what percentage is that after 3 more absences?" is trivial for ordinary code but genuinely unreliable for a tiny model to compute correctly on its own.

The architecture that solved this: **ordinary Kotlin code computes every number first**, and only the *final, already-correct* numbers get handed to the language model, whose only job is to phrase them into a natural, conversational sentence.

```
User: "Can I bunk 3 more DBMS classes?"
   ↓
Step 1 — plain Kotlin code computes, instantly:
   Current: 52/67 = 77.6%
   After 3 more absences: 52/70 = 74.3%  → below the 75% target
   Maximum safe skips: 2
   ↓
Step 2 — these already-correct numbers get folded into the prompt
   ↓
Step 3 — the local model generates a natural-language reply:
   "3 is too risky — you'd drop to 74.3%, below your 75% target.
    You can safely skip 2 more classes though."
```

**What I Learned:** For a small on-device model, this hybrid approach (deterministic code for anything numeric, the model only for language) isn't a workaround for a weak model's limitations — it's genuinely the *correct* architecture even for much larger models. Even large cloud models get arithmetic wrong sometimes. Let code compute, let language models handle language.

### Making a tiny model behave like a clean chat assistant

#### 1. Suppressing "thinking" tokens (a three-layer defense)

**The Problem:** The chosen model (Qwen3) is a "reasoning model" — trained to write out its internal step-by-step reasoning wrapped in `<think>...</think>` tags before giving a final answer. This is genuinely useful for developers building complex reasoning pipelines, but for a simple chat UI it's a bug: the user sees pages of internal monologue like "I need to answer this in two sentences as per instructions" before the actual answer, and — critically — generating all those hidden reasoning tokens is *also* the majority of the response latency (as much as 100 "thinking" tokens versus 30 actual answer tokens, more than doubling response time for content the user never wanted to see in the first place).

**How I Solved It — three independent layers, because no single one was reliable on its own:**
1. **Stop sequences at the inference-engine level** — tell the engine to halt generation entirely the instant it starts producing `<think>`, so the wasted tokens are never generated at all (the most efficient layer, since it prevents the work rather than hiding its output).
2. **A prompt-level instruction** (`/no_think`, a directive Qwen3 was specifically trained to recognize) telling the model to skip its reasoning phase — helpful, but a small model doesn't reliably obey every instruction every time.
3. **A regex-based cleanup pass** on the final text, as a last-resort safety net for anything that slipped past the first two layers.

Because stop-sequence matching happens at the level of generated *tokens*, not the final string, and a phrase like `<think>` can be split across 2–3 separate tokens by the model's tokenizer, a couple of "leading" tokens of a thinking block could still slip out before the full stop-sequence matched. The fix was adding *partial*-match stop sequences (`"<think"` without the closing bracket, and even `"\n<"` — since the model's thinking blocks always start on a fresh line) to catch the pattern one token sooner.

**Measured impact:** for a typical question, effective token generation dropped from ~130 tokens (100 hidden + 30 visible) to ~30 tokens (visible only) — cutting response time from 8–10 seconds down to 2–3 seconds, entirely by *not generating* content the user never sees, with no change to the model itself.

**What I Learned:** Small on-device models inherit training-time behaviors (like "always show your reasoning") that make sense for a research/developer context but are actively wrong for a consumer chat product — and the fix belongs at the layer with the least room for the model to disobey (stop sequences), with prompt instructions and text cleanup as backup layers, never as the *only* layer.

#### 2. Cutting the prompt from 500 tokens to 25 — the biggest speed win of all

**The Problem:** Even after suppressing the thinking tokens, a simple "hi" still took about 3 seconds to get a reply. The bottleneck wasn't generating the response at all — it was **prefill**: every single message, regardless of what it actually asked, sent the model roughly 500 tokens of context (full attendance numbers, five pre-computed "what if I skip N days" scenarios, marks data, syllabus pointers, and a long block of behavioral instructions) that the model has to *read* before it can even start replying.

**How I Solved It:** A simple keyword-based intent classifier (plain regex/string matching in Kotlin — deliberately *not* using the LLM itself to classify intent, since that would mean running a full extra inference pass, doubling the latency for a "was this about marks or attendance?" question) routes each message to one of a handful of minimal, purpose-built context templates:

| Intent | Tokens sent |
|---|---|
| Greeting ("hi", "hey") | ~25 (just the system prompt + the student's name) |
| "Can I skip class tomorrow?" | ~40 (attendance numbers + a pre-computed skip analysis) |
| Attendance summary | ~35 |
| Marks | ~20 |

That's up to a **20x reduction** in the tokens the model has to process before it can start replying — at roughly 16 tokens/second on a mid-range phone CPU, cutting 500 tokens of prefill down to 25 saves nearly 3 full seconds on *every single message*, dwarfing any gain achievable by suppressing thinking tokens or optimizing the inference engine itself.

**What I Learned:** For on-device inference, the single biggest performance lever isn't a faster engine or a smaller model — it's **sending less data**. Every token in a prompt costs real, measurable wall-clock time before the model can even begin its answer. The right question is rarely "how do I make the model process 500 tokens faster?" — it's "why am I sending 500 tokens to answer 'hi'?"

#### 3. Trusting a third-party model registry, not a guessed name

**The Problem:** When a user tapped to download the "larger" model option, it failed instantly with `Failed to get model qwen3-1.5`.

**Why It Happened:** The specific identifier string ("qwen3-1.5") for that model had been *guessed*, based on the assumption that the hosting service's naming convention would match the model's publicly-known parameter count (1.5 billion). It didn't — the service's actual internal catalog hosted a 1.7-billion-parameter variant under a differently-numbered identifier. This bug had gone unnoticed for a while because the developer's own test device always defaulted to the smaller, correctly-named model; the broken option only appeared (and was only tested) on devices with enough RAM to unlock the "bigger model" choice.

**How I Solved It, the right way rather than the quick way:** Rather than just fixing the one wrong string, replaced the hardcoded model list with a **live query against the hosting service's own model registry API**, filtering out entries that clearly aren't chat models (embedding models, speech-to-text models) by checking their names for telltale substrings, and falling back to one known-good hardcoded option only if the registry call itself fails (e.g., no internet).

**How this bug was actually found and diagnosed:** Rather than debugging blind, this used **Maestro** (a mobile UI automation tool) connected over USB to a real test phone — taking screenshots, inspecting the on-screen element tree, and tapping through the exact flow a real user would follow, all from a terminal. This turned "a user reported it's broken" into a directly reproducible, inspectable failure.

**What I Learned:** Never hardcode a third-party service's internal identifiers based on an assumption about their naming convention — verify against the service's actual, live registry. And when a bug only manifests on a specific device configuration a developer doesn't personally use every day, remote device automation tooling (Maestro, in this case) is far more reliable than trying to reason about it from logs alone.

#### 4. Feeding the model *real*, personalized data (not just generic advice)

Once the intent-detection and prompt-trimming above were working, the AI advisor still gave generic, non-personalized responses whenever a student asked about marks, results, or per-subject attendance — because those data sources were only ever fetched when the corresponding screen was physically opened.

**The fix:** a background "prefetch" pipeline fetches CA marks, semester results, per-subject attendance, and recent circulars — the same data every other screen in the app already fetches — and caches each one as a short, hand-built **summary string** (not the raw JSON, which for CA marks alone could run to roughly 2,000 tokens). A compact string like `"DBMS: 38.00/50; OS: 29.50/50"` conveys the same information in roughly 30 tokens — a 60x+ reduction that matters enormously for a model with a limited context window.

Later, this was refined further with **fuzzy subject-name matching**: when a question mentions a specific subject ("what's my cloud computing mark?"), only that one subject's data is injected into the prompt, instead of all seven. The subject list itself is built dynamically from whatever data is cached for a *given* student — never hardcoded — so the same code works identically for a Computer Science student and an Electronics student without any per-department configuration.

**What I Learned:** An on-device model is only as useful as the data it can see. A capable model with zero context produces generic, forgettable answers indistinguishable from a static FAQ page — the real engineering effort, and the real payoff, is in the data pipeline that gets a student's *own* numbers in front of the model efficiently, not in the model itself. This is also a clean example of "you don't always need a vector database": when your entire relevant dataset for a query is a few hundred tokens, keyword-based intent detection plus targeted string injection beats building a full retrieval-augmented-generation (RAG) pipeline with embeddings.

#### 5. A lightweight version of "tool calling"

Students would ask things like "show my subject attendance" and expect the app to actually *navigate* there, not just describe it in text. Rather than trusting a small model to reliably output structured "call this function" instructions (a technique called tool-calling or function-calling, which even large cloud models can get wrong, let alone a phone-sized one), navigation intent is detected the same way as the query-type classification above — with plain Kotlin keyword matching on the *user's* input, completely independent of whatever text the model generates. The chat UI then shows a small tappable "Open Subject Attendance →" chip alongside the model's natural-language reply.

**What I Learned:** When a capability (routing to a specific screen) can be reliably determined by a simple deterministic classifier on the *input*, there's no need to make an unreliable small model responsible for outputting a structured "action" — split the two concerns and let each be handled by the tool best suited to it.

---

## Mini-Games, GPA Calculator & Academic Tools

A grab-bag of secondary features, each with its own distinct engineering lesson.

### GPA/CGPA Calculator

Built from scratch by extracting official curriculum data (subject codes, names, credit values, semester assignments) from **17 official syllabus PDFs** across 7 departments and 2 academic regulations. To parallelize this tedious extraction work, 7 separate AI coding agents each handled one or two departments simultaneously, all completing within a few minutes and returning ready-to-use structured Kotlin data.

**A recurring correctness bug:** department auto-detection (used to pick the right curriculum for a student automatically) initially defaulted everyone to Computer Science whenever its input data was missing or ambiguous. The fix, applied more than once as new edge cases surfaced, was always the same shape: build a **fallback chain** (try the short department code first, then the longer descriptive programme name, then a safe default) rather than trusting a single source of truth, and always prefer matching against a *short, consistent* identifier field over a longer descriptive one that varies in wording ("Electrical and Electronics Engineering" vs. "BE Electrical & Electronics Engineering").

**What I Learned:** Any auto-detection logic driven by string matching against real-world data needs a fallback chain, not a single lookup — and should be re-verified whenever it's tested against an account genuinely different from the developer's own (a bug that showed correct CSE data for a CSE developer stayed hidden for a while, only surfacing when tested against a friend's EEE account).

### OCR: reading grades from a photographed or scanned mark sheet

**The Problem:** Google's on-device text recognition (ML Kit) read tables **column-by-column**, not row-by-row — course codes, letter grades, and grade points all came back as three separate vertical blocks of text with no indication of which grade belonged to which course.

**How I Solved It:** Rather than trying to match text row-by-row, the parser was rewritten to rely on **grade point *numbers*** (10, 9, 8, 7, 6, 5) instead of **letter grades** ("O", "A+", "B+") — because OCR reads numbers far more reliably than short strings of letters, which are often misread (a scanned "O" missed entirely, "B+" read as "B4"). The parser finds all course codes via regex, finds all runs of valid grade-point numbers, and matches them up positionally.

**A second, unrelated OCR win:** for PDFs where the text is *already embedded and selectable* (like official university-generated result PDFs), running OCR on a rendered image of the page at all was unnecessary and error-prone — a direct PDF text-extraction library (PdfiumAndroid) reads the actual embedded text with 100% accuracy and near-zero cost, with OCR kept only as a fallback for genuinely scanned/photographed documents.

**What I Learned:** When parsing OCR output from a table, prefer whatever data type OCR is most reliable at recognizing — numbers over multi-character strings, multi-character strings over single characters — and match everything else by *position* relative to that reliable anchor. Also: don't reach for OCR at all if the source document already has embedded, extractable text; check that first.

### Exam Seat Finder: when a Google API limit blocks the "obvious" solution

**The Problem:** The original design used Google Sign-In with Gmail read access, to automatically search a student's inbox for the college's exam-seating-arrangement email. After going live, Google's policy revealed a **hard 100-user lifetime cap** on any app using a "restricted" Gmail scope without an expensive third-party security audit (officially, $500–$75,000/year) — a complete dealbreaker for an app already past 1,600 users.

**How I Solved It:** Abandoned the Gmail-API approach entirely in favor of Android's **Share Intent** system: a student receives the seating-arrangement email as normal in their own mail app, taps "Share" → "Open with JustPass," and the file (an Excel spreadsheet) is parsed locally on-device (using Apache POI, a Java library for reading Office file formats) with zero Google authentication of any kind involved.

**What I Learned:** A cloud API's restricted-scope policy, designed for large enterprise integrations, is often a poor fit for a small free student project even when it looks like the "correct" solution on paper. For a feature that only needs to read *one* file a student already has in their inbox, a native OS share-intent is not just a workaround — it's simpler, has no user cap, and is *more* private (the app never touches the mailbox at all).

### Mini-Games and the section leaderboard

A suite of reflex/memory mini-games (reaction time, sequence memory, aim trainer, typing speed, etc.) with a leaderboard backed by Supabase (a free-tier hosted Postgres database), scoped to a student's own section rather than a global ranking — reusing the same "small groups are more meaningful and less gameable than a global leaderboard" principle applied to attendance and marks comparison elsewhere in the app. Small polish details mattered here too: replacing a plain rank number with a large, color-coded medal badge for the top 3 positions after a user specifically said the old rank text was "hardly visible," and adding an in-game "rival" indicator showing the *next* person above you on the leaderboard and by how much you need to improve to pass them, to make the leaderboard feel like an active nudge rather than a static list you check separately.

### A privacy-first design for a class-wide marks comparison feature

A planned (and, at the time of this writing, fully built but deliberately dark behind a remote kill-switch) feature lets students see how their own CA marks compare to their section's distribution. Since this involves uploading personal academic data to a shared backend — a materially different privacy situation from attendance data, which never leaves the device — three deliberate design decisions were made up front:

- **A hard minimum of 15 students** must have opted into a given class-section before *any* comparison view is shown to anyone in it. This is a direct application of **k-anonymity**: with only a handful of students uploaded, any one of them could trivially reverse-engineer which score belongs to which classmate. Fifteen was chosen — stricter than a five-student floor initially proposed — specifically so the smallest possible identifiable group ("one of fifteen") is still meaningfully anonymous.
- **Server-side enforcement, not client-side.** The k-anonymity check happens on the backend, which simply refuses to return real statistics below the threshold — a client-side-only check could be trivially bypassed by anyone willing to intercept and inspect the network response.
- **No student credentials are ever stored on a server.** Marks are uploaded only by the student's *own* device, via the same background-worker pattern already used for attendance refresh — never fetched centrally using stored login credentials, which would create a single catastrophic point of failure (one server breach = every student's college password compromised) entirely disproportionate to a free side project run by one student.

**What I Learned:** Whenever a feature moves personal data from "cached locally on the user's own device" to "shared with other users via a backend," that's the line that triggers real privacy obligations (disclosure requirements, anonymity guarantees) — and the right response is architectural (enforce anonymity server-side, never centralize credentials) rather than just a permission dialog.

---

## Release, Monetization & Publishing

### R8/ProGuard: release builds are not just "the same code, but smaller"

**The Problem:** A release build (with `isMinifyEnabled = true`, which shrinks and obfuscates the compiled code via a tool called R8) repeatedly broke features that worked perfectly in every debug build — reflection-based libraries, in particular, kept getting silently stripped.

Recurring failure patterns, each requiring its own explicit "keep this" rule in `proguard-rules.pro`:
- **`@JavascriptInterface`-annotated methods** (the bridge that lets JavaScript inside a WebView call back into Kotlin code) got stripped from anonymous inner classes, silently breaking the login token-capture flow described earlier.
- **Gson's generic type information** (`TypeToken`, used for parsing `List<T>`-style JSON) got erased, breaking JSON parsing for specific list-shaped responses.
- **Apache POI** (the Excel-parsing library used for the exam seat finder) reaches into a large tree of schema classes via runtime reflection (`Class.forName(...).newInstance()`), which R8's static analysis has no way to "see" as reachable code — it silently stripped the no-argument constructors those classes need, and POI's own error-handling swallowed the resulting exception internally, so the feature just silently returned "0 rows found" with zero visible error in a release build.
- **A generic JSON parsing pattern** (an anonymous `TypeToken` wrapping a `private` nested data class) had its generic signature stripped even with what looked like a matching keep rule — the eventual fix was avoiding the fragile pattern altogether (public top-level classes, parsed one element at a time) rather than fighting R8 rule-by-rule.

**What I Learned:** R8 is aggressive specifically about anything reached through **reflection** — annotation-driven APIs, generic type erasure, and libraries that construct objects by class name at runtime are the recurring danger zones. The single most important habit this taught me: **always sideload and manually test a release-configured build before ever uploading it anywhere** — debug and release builds can behave completely differently, and the difference is invisible until you specifically go looking for it. A `minifiedDebug` build variant (debug-signed, but with minification turned on) that you test regularly during development catches these regressions weeks earlier than discovering them post-release.

### Remote Config: a server-side switchboard for a client you can't force to update

Since the app is distributed both through Play *and* by students sharing APK files directly, there's no way to force every installed copy to update at once. **Firebase Remote Config** (a simple key/value store that every running app instance fetches from periodically) became the switchboard for everything that might need to change *after* an app version has already shipped, without requiring a new release:

| Flag | Purpose |
|---|---|
| `min_version_code` | Force-update wall — shows a non-dismissable dialog if the installed version is below this number |
| `ads_enabled` | Master on/off switch for all advertising |
| `ads_use_test_ids` | Swap real ad units for Google's official test ad units — lets you demo or debug the app without risking a policy violation for accidentally clicking a real ad |
| `sideload_block_enabled` | Nudges users who installed outside the Play Store to reinstall from Play |
| `maintenance_enabled` / `maintenance_message` | A general-purpose "the college's server is down, please try later" broadcast banner |
| `class_compare_enabled` | Kill-switch for the marks-comparison feature described above |
| `chess_backend_v2` | Which chess backend (Firestore or Cloudflare) a given client should connect to |

**A near-miss worth remembering:** one flag update was deployed by editing the underlying config file with a blunt find-and-replace (`sed`) command, without checking that *every other* flag in that same file happened to share the exact same "false" string being replaced. For about 30 seconds, every flag in the file — including the sideload-block wall and the maintenance dialog — was accidentally flipped to "true" at once, before being caught and reverted. Any user whose app happened to refresh its config in that exact window kept the bad values cached for up to an hour.

**What I Learned:** A remote kill-switch only has to ship *once*, from the very first release — retrofitting one after the fact means the exact users you'd most want to reach with it (people stuck on an old, broken version) are the ones who can never receive it, because they don't have the code that reads the flag. Treat "no in-app fix needed" as leverage: any config value that might need emergency adjustment belongs in a scriptable, source-controlled config file deployed via a CLI command — never a manual regex edit, and never something clicked through a web console with no history.

### AdMob and the certified-vs-uncertified traffic distinction

Integrating banner and interstitial ads surfaced a genuinely surprising platform detail: AdMob classifies traffic from apps **not linked to a recognized app-store listing** (Google Play, or a small list of alternatives) as "uncertified" — it still serves real ads and pays real (if reduced) revenue, but at a meaningfully lower fill rate than "certified" traffic. Since this app was originally sideloaded/WhatsApp-distributed only, every early ad impression was uncertified by definition, until Play Store publishing linked the AdMob app entry to a real store listing.

Two smaller lessons from this thread: an ad-blocking DNS filter (AdGuard) on a test device produced a confusing "internal error" that had nothing to do with the app's own integration — always test with ad blockers both on and off before assuming a code bug. And a fresh AdMob ad unit takes real time (hours, sometimes a day) to start actually serving ads after creation — a "no fill" response from a brand-new ad unit is expected, not a sign of broken integration.

### Rebranding and the package-name point of no return

Partway through development the app was renamed from "Laudea Attendance" to "JustPass," and — separately, right before the first Play Store upload — the Android package identifier (`applicationId`, the permanent string that uniquely identifies an app to the OS and to the Play Store) was changed from a generic placeholder name to a real one. This had to happen in exactly this order, and exactly at this moment: **once an `applicationId` has been published to the Play Store, it can never be changed again.** The rename touched about 80 source files, moved every file into a new package directory, and required updating every `-keep` rule in the ProGuard configuration (which references classes by their full package path) and the widget's intent-filter action name.

A related, permanent consequence: because Android treats two different `applicationId`s as two completely unrelated apps (even with byte-identical code), any student who already had the old-package version installed would see the Play Store release show up as a brand new, separate app — not an update — and would need to manually uninstall the old one. Separately, Google Play's default signing scheme means an app built and signed locally (for sideloading) uses a different cryptographic signature than the same app built and signed through the Play Store's own signing service — so a sideloaded install and a Play Store install of the "same" app can't upgrade into each other either; the user has to uninstall one before installing the other.

**What I Learned:** A package rename is a one-shot window that closes forever the moment you publish — do it *before* the first store upload, never after. And any migration between two different distribution channels for the same app needs a human-communication plan (a message telling users what to do), because the platform itself provides no automatic bridge between them.

### Play Store submission: the parts that were surprisingly manual

A few Play Console quirks are worth remembering purely because they cost real debugging time for reasons that had nothing to do with the app's own code:

- **A form field's "required" asterisk can be aspirational, not enforced** — the actual save-time validator is the only ground truth for what's genuinely mandatory.
- **Deep-linked declaration pages sometimes only populate correctly when reached via the specific "Go to declaration" button from a review page**, not via a direct URL — the console's client-side router depends on prior in-app navigation state that a bookmarked URL skips past.
- **A permission-usage demonstration video only needs to show the *specific behavior being declared*** (in this case: a background download continuing after the app is put in the background, then resuming when reopened) — not a full feature walkthrough.
- **Automating a web form with a headless browser tool can silently fail** on modern JavaScript frameworks (Angular Material, React) if the automation only sets a value directly rather than dispatching a full, "trusted" event sequence a real click would generate — checkboxes visually toggling but never actually registering with the underlying form state was a repeated time sink.
- **For a personal (non-organization) Google Play developer account, a mandatory 14-day closed-testing period with at least 12 active opt-in testers is the real bottleneck**, not the review turnaround time — the clock starts the day Google approves the *first* closed-testing submission, not the day you submit it, and shipping several small bug-fix releases *during* that window doesn't reset or pause the 14-day timer (it only resets if the active tester count drops below 12).

---

## Security Research (Authorized, On My Own College Account)

As part of building and hardening this app for the college's own identity system, I did authorized testing against the SIS portal using my own credentials and the app's own network traffic — not against any other student's account or data.

**A case-sensitivity access-control bypass.** At one point the college's attendance API (`/sis/attendance/*`) started returning HTTP 403 "Forbidden" for every request, while every *other* API on the same server (marks, results, timetable) kept working fine with the exact same security token. Systematically probing the path (different HTTP verbs, trailing slashes, header tricks) found that simply capitalizing the path (`/sis/Attendance/*` instead of `/sis/attendance/*`) returned a full, valid 200 response with the identical data. The underlying cause was a classic access-control mismatch: whatever middleware enforced the "deny" rule matched the path as a lowercase literal string, while the actual backend web router underneath it resolved routes case-insensitively — capitalizing even one letter slipped past the deny check while the router still resolved to the exact same working handler.

I shipped the capitalized path as a fix in the app so students' attendance would keep working, with an explicit, documented note-to-self that distributing a discovered access-control bypass at the scale of 1,400+ Play Store installs is a materially different ethical situation than a single researcher reading their own account's data once — every refresh from every installed copy would now hit the college's access logs identifiably. That's a real, acknowledged tradeoff I made deliberately (leaving 1,400+ students with broken attendance versus a bypass that could draw attention once the college eventually normalizes its case-handling), not an accident.

**What I Learned:** Access-control middleware that string-matches a URL path is only as strong as the *exact* string it matches against — if the underlying router beneath it is case-insensitive (as most web frameworks are, by default), any capitalization variant of a blocked path can slip through untouched. And a security finding's ethical weight scales with your distribution, not just with the technical discovery itself — what's a defensible "I checked my own account" action alone becomes a different, more serious decision once it's built into software running on thousands of other people's phones.

---

## Technical Deep-Dives

The sections above are written to be readable end-to-end. This section exists for a different purpose: it's where I force myself to go one or two layers past the headline answer, for the specific topics I'd expect a technical interviewer to drill into. Each write-up assumes you've read the relevant theme section above and goes straight to the mechanism.

#### 1. The 4-Tier Token Refresh Ladder

The headline ("four fallback tiers, fastest first") hides a specific OAuth/Keycloak mechanism worth being able to draw on a whiteboard.

Keycloak (the college's SSO server) issues two tokens on every successful login: a short-lived **access token** (the one sent as `Authorization: Bearer <token>` on every API call — logs showed it typically expiring in around 600 seconds) and a **refresh token** (a longer-lived credential whose only job is to mint a new access token without re-entering a password). A refresh token obtained through the normal browser login flow expired in about 1800 seconds (`refresh_expires_in=1800`) — fine for a session, useless for "the widget should still work three days from now."

The tiers, and exactly what triggers the fall-through to the next one:

```
Tier 1 — cached access token, direct HttpURLConnection call (~200ms)
   Trigger to fall through: HTTP 401 (token expired/invalid)

Tier 2 — grant_type=refresh_token against Keycloak's token endpoint (~500ms)
   Trigger to fall through: refresh token itself rejected/expired

Tier 3 — grant_type=password, direct username+password POST to the token
   endpoint, no browser involved (~500ms–1.5s)
   Trigger to fall through: HTTP 400 body specifically containing
   "unauthorized_client" (server disabled this grant type) — NOT any 400,
   since "invalid_grant" (wrong password) must NOT fall through, it must
   fail loudly.

Tier 4 — full WebView OAuth Authorization Code flow, XHR-intercepted (~15-30s)
   Always works if the college's identity server is up at all. True last resort.
```

The `scope=openid offline_access` discovery (Tier 3) was the single highest-leverage line of code in the project: adding that scope to the password-grant request made Keycloak return `refresh_expires_in=0`, which in Keycloak's protocol means "this refresh token does not expire." Before this, every fallback ladder eventually bottomed out in a login screen; after it, a refresh token obtained once could regenerate access tokens indefinitely, which is what let the WorkManager background sync run for days without a human touching the app. It also had a side effect that wasn't obvious up front: the SIS backend API had previously rejected password-grant tokens outright with HTTP 500, while accepting browser-flow tokens — adding the `offline_access` scope changed the token's claims/audience enough that the *same* backend started accepting password-grant tokens too. That was confirmed empirically, not derived from documentation: force an invalid cached token on a real device via ADB, watch the refresh chain execute through each tier, and confirm the final token actually works against the real attendance endpoint (not just that Keycloak accepted it).

All tokens are cached in `EncryptedSharedPreferences` (AES-encrypted, backed by a key in the Android Keystore — see the dedicated Keystore deep-dive below for what that actually buys you and where it can bite you).

```kotlin
// Tier 3 request shape (simplified)
POST https://accounts.psgitech.ac.in/realms/psgitech/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
&client_id=ies_sis
&username=<rollNumber>
&password=<password>
&scope=openid%20offline_access
```

**Likely follow-up questions:**
- Why is a direct `grant_type=password` request (the "resource owner password credentials" OAuth grant) considered legacy/discouraged, and what's the actual risk of using it in a mobile app?
- How do you tell "the password is wrong" apart from "the server changed its policy" when both can return HTTP 400? What's the concrete signal you check?
- What would happen to a device mid-chain if the *offline* refresh token itself got revoked server-side (e.g., an admin forced logout) — does the ladder recover, or does it need Tier 4?
- Why cache the refresh token at all instead of just re-running the fast password grant every time?

---

#### 2. XHR Interception and the JavaScript-to-Kotlin Bridge

This is the mechanism that made login possible at all once the fast password-grant path was disabled server-side, so it's worth being able to explain precisely, not just "we intercepted the token."

**The JS side.** Every browser-based HTTP call the college's Angular app makes goes through the standard `XMLHttpRequest` object under the hood (even calls made via the newer `fetch` API can be normalized to also patch `fetch` for coverage). Before any page script runs, an injected script monkey-patches `XMLHttpRequest.prototype.setRequestHeader`, replacing it with a wrapper that inspects every header the *page's own code* sets, looks specifically for the `authorization` header, strips the `Bearer ` prefix, and hands the raw token to Kotlin — then calls through to the original implementation so the page's own request behaves completely normally:

```javascript
const origSetHeader = XMLHttpRequest.prototype.setRequestHeader;
XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
    if (name.toLowerCase() === 'authorization') {
        Android.onAuthToken(value.replace('Bearer ', ''));
    }
    return origSetHeader.apply(this, arguments);
};
```

**The bridge.** `Android.onAuthToken(...)` only exists because the Kotlin side called `webView.addJavascriptInterface(bridgeObject, "Android")`, and `bridgeObject` has a method annotated `@JavascriptInterface`. That annotation is not decorative — since Android 4.2 (API 17), the WebView's JS engine will only expose methods explicitly marked with it; before that API level, `addJavascriptInterface` exposed the *entire* Java object via reflection, which was a real, actively-exploited vulnerability (arbitrary method invocation from any web content the WebView loaded, including `Runtime.exec`). Marshalling across this bridge is limited to primitive/string types — you cannot pass a JS object graph across it directly, only strings/numbers/booleans, which is exactly what a token is, so it's a good fit here. The callback also does not run on the main/UI thread by default, so anything the Kotlin side does with the token that touches UI state has to be dispatched back onto the main dispatcher.

**Why this beats reverse-engineering the endpoints directly:** the token exchange during Keycloak's Authorization Code flow is a multi-step redirect dance with CSRF state parameters, PKCE-style nonces, and a code-for-token exchange that Keycloak's own JS adapter handles internally — replicating that by hand means re-implementing an OAuth client library and keeping it in sync with whatever Keycloak version the college runs. Intercepting the XHR instead means the app never needs to understand *how* the token was obtained — it just watches the page's own trusted client library do it correctly, and copies the result. When the login server's realm name changed (`itech` → `psgitech`) or the client ID changed (`sis_web` → `ies_sis`), this interception layer didn't need to change at all, because it was never hardcoding those details in the first place.

**Likely follow-up questions:**
- What thread does a `@JavascriptInterface` callback execute on, and what's the actual failure mode if you touch a `MutableState` from it directly?
- Why hook `setRequestHeader` specifically instead of hooking `.open()` or `.send()`?
- What's the security exposure of exposing a `JavascriptInterface` bridge to a WebView that also loads third-party content, and how would you scope it down?
- The WebView lifecycle callback you inject the hook from (`onPageStarted` vs `shouldInterceptRequest`) mattered a lot here — why?

---

#### 3. WorkManager Self-Chaining Background Sync

**Why a widget can't just fetch its own data.** A home-screen widget is drawn by a `RemoteViews`-based app-widget host process, not your app's normal running process — there's no guarantee it's ever "running" in a way that can safely make a blocking network call, and Android's Doze/App Standby power model is specifically designed to prevent background processes from waking the radio arbitrarily. The only supported pattern is: something with `WorkManager`'s guarantees does the network call, writes the result somewhere durable, and the widget's `onUpdate`/provider callback just reads that durable state and redraws — the widget never touches the network directly, ever.

**Why `WorkManager` specifically, not a raw `AlarmManager` or a foreground `Service`.** `WorkManager` is Android's unified abstraction over `JobScheduler`, `AlarmManager`, and a persistent internal database of pending work — it survives process death, app kills by the user, and device reboots, because the scheduled work itself is durable (backed by WorkManager's own SQLite store), not just an in-memory timer. It also automatically respects battery/Doze constraints rather than fighting them.

**The self-chaining trick.** `PeriodicWorkRequest`, Android's built-in "run this every N minutes" API, has a hard-enforced minimum interval of 15 minutes — that's a platform-level floor, not a WorkManager-specific limitation, and it exists specifically to stop apps from draining battery with frequent wake-ups. To get closer to real-time refresh without violating that floor, the refresh job is a `OneTimeWorkRequest` that, as the very last step of its own `doWork()`, enqueues *another* `OneTimeWorkRequest` with an 8-minute initial delay:

```kotlin
override fun doWork(): Result {
    refreshAttendanceAndCache()
    WorkManager.getInstance(applicationContext).enqueueUniqueWork(
        SYNC_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<AttendanceSyncWorker>()
            .setInitialDelay(8, TimeUnit.MINUTES)
            .build()
    )
    return Result.success()
}
```

Because each link in the chain is a distinct one-time request, none of them are individually bound by the periodic-request floor — the chain, not any single request, defines the cadence. `enqueueUniqueWork` (or `enqueueUniquePeriodicWork` for the genuinely periodic jobs riding on the same mechanism, like circular/holiday checks) with a policy like `KEEP` or `REPLACE` matters for a second reason: without a *unique* work name, a reinstall or a duplicate scheduling call spawns a second, parallel chain running alongside the first, silently doubling network calls and battery cost.

**Doze/battery-optimization handling.** Even a correctly-scheduled `WorkManager` job can be deferred or killed outright by OEM-specific battery managers (Samsung, Xiaomi, and others are notorious for going beyond stock Android's Doze restrictions). The practical fix is a one-time system dialog (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) asking the user to exempt the app from battery optimization — this doesn't bypass Doze's network-batching entirely, but it stops the OEM layer from freezing the app's job scheduler outright.

**Data flow, end to end:**
```
WorkManager (self-chaining OneTimeWorkRequest, ~8 min)
   → AttendanceRepository (parallel API calls, see the "Parallel Fetching" deep-dive)
   → EncryptedSharedPreferences / SecurePreferences (durable local cache)
   → Widget provider's onUpdate() reads the cache, calls AppWidgetManager.updateAppWidget()
```

**Likely follow-up questions:**
- Why is `PeriodicWorkRequest`'s 15-minute minimum a platform-enforced floor rather than a library choice, and what's Google's stated rationale?
- What exactly does `enqueueUniqueWork` with `ExistingWorkPolicy.REPLACE` do if a new chain link is enqueued while the previous one is still mid-`doWork()`?
- What happens to the chain if the process is killed by the OS in the middle of `doWork()` — does the chain resume, and from where?
- Why not run this as a foreground `Service` with a persistent notification instead?

---

#### 4. Cloudflare Durable Objects and WebSocket Hibernation (Chess Presence)

**What a Durable Object actually is.** A normal Cloudflare Worker is stateless — every incoming request can be routed to any edge location and gets a fresh execution context with no memory of any other request. A **Durable Object (DO)** is different: for a given DO *ID* (here, effectively "the lobby"), Cloudflare guarantees there is exactly one live instance of that JavaScript object in the entire world at a time, and every request/connection for that ID is routed to that same instance. That single-instance guarantee is what makes it usable as a coordination point — there's no distributed-consensus problem to solve for "who's online right now," because there's only ever one process that could know the answer.

**Why "presence via an open WebSocket" beats "presence via a heartbeat document."** In the original Firestore-based lobby, every online player wrote a timestamped "heartbeat" document every 25–90 seconds, and every other connected client held a live listener on the whole collection — so every single heartbeat write fanned out as a change notification to every other listener. With N concurrently-online players, that's a read cost that scales roughly with **N²**, not N (`≈ N² × (86400 / heartbeat_interval_seconds)` reads/day) — a classic case where a general-purpose database's per-read billing model is the wrong primitive for the specific problem of "is this connection still alive." Moving presence onto a Durable Object over a WebSocket makes presence *identical to* the transport-layer connection state: a closed TCP socket (detected via the WS `close`/`error` event) **is** the "user went offline" signal, typically observed within about a second, with nothing to poll and nothing to write.

**WebSocket Hibernation.** Normally, keeping a WebSocket open on a DO keeps that DO's JavaScript isolate resident (and billed) in memory indefinitely, even while nothing is happening. The Hibernation API lets Cloudflare's runtime detach the DO's in-memory JS state entirely while leaving the raw socket physically open at the edge — the DO effectively goes to sleep — and only re-instantiates the object (reruns its constructor) when an actual message arrives on one of its sockets. This is what makes idle lobby presence functionally free.

**The bug this caused, and the fix.** Hibernation wipes ordinary JavaScript class fields (like an in-memory `Map<playerId, PlayerInfo>`) but does **not** close the sockets attached to the object — so after a hibernation cycle, the DO would wake up with a completely empty `players` map even though real clients were still connected. Two players would both show "online" locally, but neither ever appeared in the other's lobby list, because a new joiner's `handleJoin` broadcast against an empty map, and the existing (still-connected, but map-forgotten) peer was never in that map to be notified either.

The fix rebuilds the map from the sockets themselves on every construction, using Cloudflare's own hibernation-aware APIs:

```typescript
// In the Durable Object's constructor
for (const ws of this.state.getWebSockets()) {
  const att = ws.deserializeAttachment() as { playerId: string; hintedName?: string };
  if (!att?.playerId) continue;
  this.players.set(att.playerId, {
    ws,
    id: att.playerId,
    displayName: (att.hintedName ?? "").trim() || `Player-${att.playerId.slice(0, 6)}`,
    joinedAt: Date.now(),
  });
}
```

`ws.serializeAttachment({ playerId, displayName })` is called when a player joins, stashing small metadata directly on the socket object itself (which Cloudflare's runtime *does* preserve through hibernation, unlike class fields) — and this had to be revisited once more, because the first version only stashed `playerId`, so a hibernated-and-rehydrated peer would show a generic placeholder name (`Player-abc123`) since the underlying Firebase anonymous auth token carries no display-name claim at all.

**Why it couldn't be reproduced locally:** hibernation only triggers under real, sustained-idle runtime conditions at Cloudflare's actual edge; `wrangler dev --local` runs the identical code in an environment that never truly hibernates, so a locally-run copy looked completely correct. It was only found by authenticating against the real deployed Worker and tailing its live logs (`wrangler tail`), watching an actual production join sequence produce `liveSockets=0 mapPlayers=0`.

**Likely follow-up questions:**
- How is a Durable Object different from a stateless serverless function (a Lambda/Worker invocation) in terms of what state survives between calls?
- What's the difference between the DO's in-memory class fields, a socket's `serializeAttachment`, and the DO's persistent `storage` API — when would you use each?
- Why couldn't this bug be reproduced in local development, and what would you change about your dev/test setup to catch it earlier?
- What would you have needed to add if you wanted match history or stats (not just ephemeral presence) to survive hibernation too?

---

#### 5. On-Device LLM: Prefill/Decode Economics and the "Compute vs. Phrase" Split

**Two distinct costs in every LLM response.** Generating a reply from a language model has two phases with very different performance characteristics: **prefill** — processing every token of the input prompt to build up the model's internal attention state — which is parallelizable across the prompt's tokens but still has to happen in full before the *first* output token can be produced, and **decode** — generating the reply token-by-token, autoregressively, where each new token depends on every previous one and so cannot be parallelized across the output. On a mid-range phone CPU (no dedicated NPU/GPU acceleration for most students' devices), decode throughput measured at roughly 16 tokens/second. That means prefill latency is pure "the user is waiting and nothing is being generated yet" time, and it scales directly with prompt length.

**Why cutting the prompt mattered more than cutting the reasoning.** Trimming ~500 tokens of context (full attendance numbers, five pre-computed skip-scenarios, marks, syllabus pointers, long instruction blocks) down to a per-intent minimal template (as little as ~25 tokens for a plain greeting) is a roughly 20x reduction in what has to be prefilled before *any* reply token appears — worth close to 3 seconds on every single message at this token rate. That's a larger, more consistent win than suppressing the model's own hidden "thinking" tokens (see below), because prefill happens on *every* message regardless of what's asked, while thinking-token volume varies per question.

**The routing mechanism.** A message is never classified by asking the LLM itself "what is this about" (that would mean paying for a second full inference pass just to route the first one) — it's routed by plain deterministic Kotlin string/keyword matching into one of a small number of purpose-built context templates (greeting / "can I skip class" / attendance summary / marks, etc.), each carrying only the data that specific intent actually needs.

**The architectural rule this all sits under: deterministic code computes, the model only phrases.** A sub-1B-parameter model reliably gets multi-step arithmetic wrong ("52/67, then 3 more absences, what's the new percentage, and is it above 75%?"). So ordinary Kotlin code computes every number first — current percentage, projected percentage after N absences, maximum safe skips — and only the *already-correct* final numbers are folded into the prompt; the model's only remaining job is turning `"52/70 = 74.3%, below 75% target, max 2 safe skips"` into a natural sentence. This isn't a workaround scoped to weak on-device models — it's presented as the correct architecture at any model size, since even large cloud models are unreliable at exact arithmetic; a model should never be the thing doing the math it's also being asked to report on.

**Engine selection.** Three options were weighed: Google's **LiteRT-LM** (best acceleration *if* the phone has a capable NPU/GPU, which most mid-range student phones don't, making it unusably slow on CPU-only fallback), **Cactus** (a Kotlin-friendly wrapper around `llama.cpp`, a CPU-optimized inference engine with mature quantized-model support) — chosen because it's fast enough on ordinary CPUs with a simple SDK and small model download — and raw `llama.cpp` directly (maximum control, but requires hand-writing C++/JNI bindings, too much integration cost for the payoff over Cactus's ready-made wrapper).

**Suppressing "thinking" tokens — a three-layer defense, because no single layer was reliable alone.** The chosen model (Qwen3) is a reasoning model trained to emit a `<think>...</think>` block of internal monologue before its real answer — useful for a developer building reasoning pipelines, actively wrong for a consumer chat UI, and *also* the majority of response latency (as much as ~100 hidden tokens versus ~30 visible ones). The fix stacks three independent layers:
1. **Inference-engine stop sequences** — halting generation the instant `<think>` starts being produced, so those tokens are never generated at all (the cheapest layer, since it prevents the work rather than hiding the output).
2. **A prompt-level `/no_think` directive** Qwen3 was specifically trained to honor — helps, but a small model doesn't obey every instruction every time.
3. **A regex cleanup pass** on the final text as a last-resort net.

A subtlety that made layer 1 alone insufficient: stop-sequence matching happens against generated *tokens*, and a phrase like `<think>` can be split across 2–3 tokens by the model's own tokenizer — so a couple of leading tokens of a thinking block could slip out before the full string matched. The fix was adding partial-match stop sequences (`"<think"` without the closing bracket, and `"\n<"`, since thinking blocks always start on a fresh line) to catch the pattern one token sooner. Net measured effect: ~130 generated tokens (100 hidden + 30 visible) down to ~30 (visible only), cutting response time from 8–10 seconds to 2–3 seconds — with zero change to the model itself.

**Likely follow-up questions:**
- Why does trimming prompt tokens save more wall-clock time than trimming output tokens, given decode is the slower per-token phase?
- What would change about this architecture if the phone *did* have a capable NPU — would you still separate "compute" from "phrase"?
- Why not have the model itself decide which screen to navigate to (tool-calling) instead of a separate keyword classifier?
- What happens when the keyword-based intent classifier picks the wrong template — does the user get a wrong answer, or a generic one?

---

#### 6. Compose Glass Rendering: `graphicsLayer`, `RenderEffect`, and the Sibling-Canvas Bug

**How the blur/refraction actually composites.** The "liquid glass" effect is built on `Modifier.graphicsLayer { renderEffect = RenderEffect.createBlurEffect(...) }` — a low-level Compose/Android API. Wrapping a composable in `graphicsLayer` promotes its drawing output to its own hardware-accelerated **RenderNode**, a separate GPU-backed drawing surface rather than being flattened directly into its parent's draw calls. A `RenderEffect` (Android's `android.graphics.RenderEffect`, exposed through Compose) is then applied to *that node's rasterized output* — for a blur/refraction shader, this means the library samples the pixels of whatever got composited *behind* the glass layer and re-projects them through a distortion/blur kernel, which is what produces the "you can see a warped version of what's underneath" look, not just a flat translucent overlay.

**The bug.** An early implementation of the water/weather animation placed a plain `Canvas` composable as a sibling inside a `Box`, next to the glass card, using `Modifier.matchParentSize()`. The result: the water rendered as full-screen vertical stripes, smeared across the entire dashboard. The cause is that the graphics-layer's compositing pipeline doesn't distinguish "new decorative content someone just added to this subtree" from "the backdrop this layer is supposed to blur" — since the `Canvas` existed in the same composition subtree as content the glass layer treats as its backdrop source, its pixels got pulled into the same texture the refraction shader samples from, and the shader's own distortion field (built for blurring a static background, not a fast-drawing animation) smeared it according to that field's geometry.

**The fix, and why it's structural rather than a parameter tweak.** Never add a *new* `Canvas`/`Box` layer as a sibling inside a glass-shader parent's composition subtree. Instead, expose the animation as a plain state-holder object and draw it via `Modifier.drawBehind { ... }` on the *existing* layout node — `drawBehind` executes extra draw calls within the same draw pass as its host composable, without creating a new compositing layer or RenderNode, so there's no separate "layer" for the compositor to mistake for backdrop.

```kotlin
// Wrong: a new Canvas sibling inside a glass-shader Box — gets captured as "backdrop"
Box {
    LiquidGlassCard(state = cardState) { /* card content */ }
    Canvas(Modifier.matchParentSize()) { drawWaterSurface(waterState) } // smears
}

// Right: draw into the existing node's draw scope, same pass as the glass refraction
Box(
    Modifier.drawBehind { drawWaterSurface(waterState) } // no new layer, no smear
) {
    LiquidGlassCard(state = cardState) { /* card content */ }
}
```

**Likely follow-up questions:**
- What's the actual difference between a `Canvas` composable and a `Modifier.drawBehind` call in terms of Compose's layout/draw pass model?
- Why does wrapping something in `graphicsLayer` force a separate `RenderNode`, and what does that cost in GPU terms versus flattening into the parent?
- What Android API level does `RenderEffect`/AGSL blur require, and what would you do on older devices that don't support it?
- How would you go about diagnosing "which layer is swallowing which" if you hit a similar compositing bug with a library you didn't write?

---

#### 7. Parallel Data Fetching: Coroutines, `async`/`await`, and Why Connection Pooling Didn't Help

**The benchmark, and why it was run before touching any code.** Rather than assuming OkHttp (a well-known HTTP client with connection pooling) would obviously beat Android's built-in `HttpURLConnection`, a standalone test app ran 10-round timed comparisons:

| Method | Average time |
|---|---|
| `HttpURLConnection`, sequential | 41.5s |
| OkHttp, sequential (connection reuse) | 45.7s — *10% slower* |
| OkHttp, parallel (all requests fired at once) | 10.1s — *75% faster* |

The counterintuitive result: connection pooling — OkHttp's headline feature, which avoids repeating TCP/TLS handshake overhead across requests to the same host — provided **zero** benefit, because the college server's own request-processing time (15–25 seconds per call on a bad day) dwarfs any handshake savings a pooled connection could offer. The only variable that mattered was whether independent calls were issued concurrently or one after another.

**The concurrency mechanism.** Kotlin's structured concurrency: `coroutineScope { async { ... } }` launches each independent API call (attendance, CA marks, results, present/absent days, timetable, circulars) as its own coroutine that starts running immediately against the given dispatcher, returning a `Deferred<T>` handle. Calling `.await()` on that handle suspends *only the caller waiting on that specific result* — it does not block a thread — and `coroutineScope` as the enclosing scope means the function as a whole only returns once every child coroutine has completed (and if one child throws, structured concurrency propagates that failure and can cancel the siblings, rather than leaving orphaned work running).

```kotlin
suspend fun prefetchForDashboard(): DashboardData = coroutineScope {
    val attendance = async { fetchAttendance() }
    val marks = async { fetchCAMarks() }
    val timetable = async { fetchTimetable() }
    val circulars = async { fetchCirculars() }
    DashboardData(
        attendance = attendance.await(),
        marks = marks.await(),
        timetable = timetable.await(),
        circulars = circulars.await(),
    )
}
```

Total wall-clock time becomes "however long the single slowest call takes," not the sum of every call — a structural fix, not a library swap.

**The decision explicitly *not* made:** migrate the whole app to OkHttp. The existing 4-tier authentication system was already battle-tested against 1,700+ real users on `HttpURLConnection`; since the benchmark proved the real win was architectural (parallelism), rewriting the networking layer to change client libraries would have meant re-testing a system that didn't need to change, for a benefit the benchmark had already shown didn't exist.

**Likely follow-up questions:**
- Why did connection pooling provide no measurable benefit here specifically, and under what workload *would* it matter?
- What's the difference between `async { }` and `launch { }` in Kotlin coroutines, and why does fetching data (versus firing a side effect) call for the former?
- If one of the four parallel calls in the snippet above throws, what happens to the other three, and why?
- The college server has no visible rate limit in this account — how would firing five requests simultaneously interact with a server that *did* rate-limit per IP or per token?

---

#### 8. The Gson Silent Type-Mismatch Problem

Two related but distinct failure modes showed up under the same library, and it's worth being able to tell them apart precisely:

**Wrong shape entirely (HTML instead of JSON).** Pointing the WebView directly at what looked like a REST URL (`/sis/attendance/<rollNumber>`) didn't return JSON at all — it returned the HTML shell of the college's Angular single-page app, because that URL is a client-side route, not an API endpoint; the real data only gets fetched by JavaScript *after* Angular boots and does its own internal routing. Whatever fed that HTML string into the parsing path produced an all-default (zeroed) data object rather than a visible error reaching the UI — the practical lesson is the same regardless of the exact internal mechanism: a response that is the wrong *shape* entirely (a webpage instead of a data payload) can end up masquerading as "successfully parsed, just empty," which is far more dangerous than a loud crash, because it looks like legitimate — if wrong — data. The fix wasn't a parsing fix at all: it was triggering Angular's own internal navigation (`window.location.hash = '#!/attendanceStudentView'`) so the app fetched data itself, and catching that real request with the XHR interceptor described above.

**Wrong field name (a typo baked into the server's own JSON).** `@SerializedName` tells Gson which JSON key maps to which Kotlin field, and Gson does zero fuzzy matching — a field annotated `@SerializedName("netPresentExemptionPercentage")` (correct English spelling) simply never matches a server response that actually contains `netPresentExcemptionPercentage` (the server's own typo). An unmatched key isn't an error to Gson at all; the Kotlin field just keeps its default value (`null`/`0`), silently, forever, unless someone diffs the annotation against a real captured response byte-for-byte.

**Wrong field *type* (this one, notably, does throw).** A registration API returning course credits as decimals (`1.5`) into a Kotlin field declared `Int` produced an actual parsing exception on *every* record with a fractional credit — worth contrasting directly with the two failures above: a type mismatch on a still-recognized key throws, while an unmatched key or an entirely wrong response shape can silently default instead. Knowing which failure mode you're looking at changes where you go looking for the bug.

**What this adds up to, generally:** never trust that a JSON model's field names or types match a third-party API from memory or from what the field name "ought" to be — always diff against a captured, real response, and be explicit that a silently-defaulted field and a thrown exception are two different failure classes that need two different debugging instincts.

**Likely follow-up questions:**
- What's the practical difference between Gson's default (lenient-ish) behavior on an unmatched key versus a type mismatch, and how would you make the unmatched-key case fail loudly instead?
- How would you defensively detect "this response is HTML, not JSON" before handing it to Gson at all?
- Why does `@SerializedName` need to match a server's typo exactly rather than the grammatically correct spelling?
- If you owned this API, what would you change about the contract to make this class of bug impossible?

---

#### 9. Case-Sensitivity ACL Bypass (Authorized Security Research)

**The mechanism, precisely.** The college's attendance API (`/sis/attendance/*`) began returning HTTP 403 on every request, while every other endpoint on the same server, using the exact same Bearer token, kept returning 200. A systematic probe — different HTTP verbs, trailing slashes, `?`/`#`/`;` path tricks, header-smuggling attempts (`X-Forwarded-For`, `X-Original-URL`, `X-Rewrite-URL`, `X-Forwarded-User`, `X-Bypass`, an admin-looking `Referer`) — ruled out a token or claims problem (the JWT's own payload, decoded manually, showed the same audience and role claims as the day before, when the endpoint worked). What actually worked: capitalizing a single letter in the path, `/sis/Attendance/*` instead of `/sis/attendance/*`, returned a full valid 200 with the identical data.

This is a textbook **access-control-layer canonicalization mismatch**: whatever middleware enforces the deny rule (most plausibly a reverse-proxy or gateway-level ACL sitting in front of the actual application) matched the path as an exact, case-sensitive literal string against `sis/attendance/`. The web framework's own router underneath it, however, resolves routes case-insensitively (a common default for frameworks like Express or many NGINX-style configurations) — so a request for `/sis/Attendance/` fails the ACL's exact-string check but is still resolved by the router to the *identical* handler that `/sis/attendance/` would have hit. Two layers disagreeing about what "the same path" means is the whole vulnerability; neither layer is individually "wrong" by its own logic, they just don't agree with each other.

**The ethical framing, at a professional level.** Using this bypass to keep reading your own account's data with your own token is a defensible, authorized security-testing action. Shipping the identical bypass into a public app update installed on 1,400+ other students' phones is a categorically different act: every refresh from every install now sends the college's access logs an identifiable, uniquely-fingerprinted (via User-Agent) request against a path that was explicitly denied — at scale, that's no longer "a researcher reading their own data," it's a developer distributing a live access-control bypass. That tradeoff (working attendance for 1,400+ students today, versus a bypass some of those installs will keep using even after the college eventually normalizes its case handling and the deny rule catches everyone at once) was made explicitly and documented, not stumbled into.

**Likely follow-up questions:**
- What's the correct server-side fix for this class of bug — normalize the path's case before the ACL check runs, or move the access-control decision into the router itself rather than a middleware layer in front of it?
- How do you distinguish an authentication problem (bad/expired token) from an authorization problem (valid token, denied by policy) when both can return non-2xx codes — what did you check here to rule out the former?
- What would responsible disclosure of this finding to the college have looked like, and why might a student in your position not take that path?
- What logging or monitoring, if the college had it, would retroactively reveal that this path had been exploited at scale?

---

#### 10. EncryptedSharedPreferences and the Android Keystore: the `AEADBadTagException`

**What `EncryptedSharedPreferences` actually is.** It's a wrapper (from Jetpack's Security library) around an ordinary `SharedPreferences` file that transparently encrypts both keys and values with AES, using a master key that is generated and held inside the **Android Keystore** — a system-level secure key store (hardware-backed on capable devices, software-backed otherwise) that is not part of the app's own private storage and is *scoped to the app's signing certificate*, not just its package name.

**The bug.** Testing a debug-signed build and a release-signed build of the same package (same `applicationId`) on the same physical device, back to back: uninstalling the debug build and installing the release-signed one crashed instantly on launch, before even reaching the login screen:

```
java.lang.RuntimeException: Unable to start activity ... MainActivity
javax.crypto.AEADBadTagException
    at android.security.keystore2.AndroidKeyStoreCipherSpiBase.engineDoFinal
```

`AEADBadTagException` means the Authenticated-Encryption-with-Associated-Data tag verification failed on decrypt — in plain terms, the key being used to decrypt the stored preferences file is not the same key that encrypted it. The root cause: `pm uninstall` removes the APK, the app's private data directory, and its permissions — but it does **not** remove the package's associated entries in the Android Keystore. Those Keystore entries persist across install/uninstall cycles as long as the package name stays the same. So the debug-signed build's Keystore-backed master key was still present when the release-signed build (a different signing certificate, same package name) tried to open the same `EncryptedSharedPreferences` file — the file's encryption is tied to a key the new app's identity doesn't actually own, and decryption fails its integrity check outright rather than returning garbage data.

**The fix, and the more durable one.** `adb shell pm clear <package>` (not just uninstall) wipes the encrypted-preferences XML file itself, so the next launch generates a fresh master key compatible with whichever signing certificate is currently installed. The more durable production practice is giving debug builds a distinct `applicationIdSuffix` (e.g., `.debug`) so a debug build is, from the OS's perspective, an entirely different package with its own Keystore namespace — it can never collide with the release build's encrypted storage in the first place.

**Likely follow-up questions:**
- Why doesn't `pm uninstall` remove Android Keystore entries, and what's actually left behind on the device after uninstalling an app?
- What's the practical difference between a hardware-backed (StrongBox/TEE) Keystore key and a software-backed one, and does `EncryptedSharedPreferences` guarantee which one you get?
- What does `applicationIdSuffix` isolate besides the Keystore namespace — what else would break (or not break) between a debug and release build sharing one device?
- If you pulled the raw `EncryptedSharedPreferences` XML file off a rooted device, what would actually be readable in it without the Keystore key?

---

#### 11. R8/ProGuard and Reflection: Why Release Builds Break What Debug Builds Never Do

**Why R8 targets reflection specifically.** R8 (Android's default shrinker/optimizer/obfuscator, which replaced ProGuard as the default in AGP) works by static reachability analysis: starting from declared entry points (Activities, Services, manifest components), it traces every class and method actually *called* from visible code, and strips anything it cannot prove is reachable. Reflection is, by construction, invisible to this analysis — a method invoked by name at runtime, or a class instantiated from a string, has no ordinary call site for R8 to trace, so R8's default behavior is to treat it as dead code and remove it.

Three concrete cases from this project, each needing a different *shape* of keep rule:

- **`@JavascriptInterface` methods** are called by the WebView's JavaScript engine, not by any Kotlin call site — R8 stripped them from the anonymous inner classes they lived in. A class-specific `-keep` rule wasn't the right fix, because the classes are anonymous and their generated names aren't stable; the working rule has to be a wildcard across every class in the app:
  ```proguard
  -keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
  ```
- **Gson's `TypeToken`** relies on capturing generic type information at the bytecode level (via the class file's `Signature` attribute) to know, at runtime, that a `List<AbsentDay>` should deserialize element-by-element as `AbsentDay`, not just as a `List` of untyped objects — that's how Java's generics, normally erased at compile time, get reified for a library like Gson. R8's default settings strip that attribute along with unreachable generic metadata, so parsing broke specifically for list-shaped JSON responses; the fix needs `-keepattributes Signature` plus explicit keep rules for the `TypeToken` machinery itself.
- **Apache POI** (Excel parsing for the exam-seat-finder feature) builds a large tree of schema objects via `Class.forName(name).newInstance()` — instantiation entirely by class-name-as-string, completely invisible to static analysis. The subtle part: even with `-keep class org.apache.poi.** { *; }`, R8 can keep a class's presence while still deciding its no-argument constructor is "unused" and stripping it — because nothing in the visible call graph *appears* to call that constructor. The rule that actually fixes it targets the constructor explicitly:
  ```proguard
  -keepclassmembers class org.apache.logging.log4j.** { <init>(...); }
  ```
  The `<init>(...)` pattern, not a bare `{ *; }`, is the critical detail — POI's runtime-reflective construction needs the constructor kept as a member, specifically, not just the class kept as a type.

**When the fix was "stop fighting the shrinker" instead.** One recurring pattern — an anonymous `TypeToken` wrapping a `private` nested data class — kept losing its generic signature even with a keep rule that looked like it should match. The eventual fix wasn't a fourth attempt at the ProGuard rule; it was restructuring the code entirely: public top-level data classes, parsed one element at a time with `gson.fromJson(element, SyllabusSubject::class.java)` instead of a generic `TypeToken<List<T>>`. Removing the fragile generic-erasure pattern removed the R8 surface area it depended on, rather than trying to carve out a keep rule precise enough to save it.

**The process lesson that mattered most.** All of the above are invisible in a debug build, because debug builds don't run R8 at all — they only surface after a release upload, often in production, with a stack trace that just says "null" or silently returns zero rows with no exception at all (POI's own error handling swallowed the stripped-constructor failure internally). The standing practice that came out of this: maintain a `minifiedDebug` build variant (debug-signed, but with `isMinifyEnabled = true`) and test it regularly during development, and always manually sideload and exercise a release-configured build before ever uploading anywhere.

**Likely follow-up questions:**
- Why is `-keep class Foo { *; }` sometimes insufficient to keep a constructor callable via reflection alive, and what does `<init>(...)` do differently?
- What's the practical difference between `-keep`, `-keepclassmembers`, and `-keepattributes`, and when do you reach for each?
- Given a release-only bug with no stack trace pointing at ProGuard at all (silently wrong output, not a crash), how would you even go about proving R8 stripped something?
- Why maintain a separate `minifiedDebug` variant instead of just testing the real release build more often?

---

#### 12. Honours-Course Detection: Cross-Referencing Two Unreliable Data Sources

Some students take extra "honours" elective courses beyond their department's standard curriculum, and no single college API flags which courses are honours versus regular — this is, underneath the beginner-friendly framing, a small **record-linkage** problem: reconciling two independently-maintained upstream datasets that don't share a fully reliable common key.

**Source 1 — the registration API.** Lists a student's officially registered courses, but had two separate data-quality problems that each looked like a different bug until diagnosed: course credit values sometimes arrived as decimals (`1.5`) against a Kotlin field declared `Int`, which threw a parsing exception on *every* record with a fractional credit — silently zeroing out the entire registration fetch, not just the affected row (fixed by widening the field to `Double`). Separately, elective slots were represented by placeholder codes like `PE64__` rather than the actual course the student picked, which meant *every* elective looked indistinguishable from an honours addition until placeholder codes (detected by an underscore in the code) were explicitly filtered out.

**Source 2 — the attendance API.** Lists every course code the student has *actually accrued attendance records for* — including electives, with the real course code, never a placeholder. This is treated as ground truth for enrollment: if a student has attendance rows for a course, they are, by definition, taking it, regardless of what the registration API says or fails to say.

**The reconciliation.** Honours detection compares a student's real timetable slots against the *standard published curriculum* for their department; anything beyond what the standard curriculum accounts for is flagged as an honours addition — but only after cross-referencing both course-source APIs together closes the ambiguity neither one resolves alone (registration data alone is incomplete/placeholder-riddled; attendance data alone doesn't distinguish "additional honours course" from "normal course" without the curriculum baseline to diff against). A related consequence of the same ambiguity: the timetable API returns *every* possible elective option for a shared time slot, not just the one a given student picked, so filtering displayed timetable entries against the student's own attendance/registration records was necessary to avoid showing two different courses scheduled in the same slot — with an explicit fallback to show everything unfiltered if there's no attendance/registration data yet to filter against (e.g., the very start of a new semester, before any classes have happened).

**Likely follow-up questions:**
- What do you do at the very start of a semester, when the "ground truth" source (attendance records) has no data yet to cross-reference against?
- Why treat attendance records as more authoritative than the registration API, given the registration API is nominally the "official" source?
- How would you handle a course code that's genuinely ambiguous between two departments' published curricula?
- This is a data-reconciliation problem across two APIs you don't control — what would you ask the college's IT team to change, if you could get one fix into their systems?

---

## Interview Talking Points

Short, rehearsable answers for the recurring engineering decisions in this project.

1. **"Walk me through the authentication system."**
   A four-tier fallback ladder: a cached token via direct HTTP (fastest), a refresh-token exchange, a direct password-grant login, and a full embedded-browser (WebView) login as the last resort. Each tier only runs if every faster one fails, so the system degrades gracefully instead of falling over the moment any single mechanism (like a server-side policy change disabling password grants) stops working.
   **If they dig deeper:**
   - *"What exact signal decides whether Tier 3 falls through to Tier 4, versus failing outright?"* — The HTTP 400 response *body* is inspected, not just the status code: only a body containing `"invalid_grant"` (wrong password) fails loudly; anything else, like `"unauthorized_client"` (the college disabling the grant type server-side), falls through to the next tier.
   - *"Where are the tokens actually stored, and what protects them?"* — `EncryptedSharedPreferences`, AES-encrypted with a master key held in the Android Keystore, scoped to the app's signing certificate (see the Keystore deep-dive for what that scoping costs you across debug/release builds).
   - *"What made the refresh token effectively permanent?"* — Adding `scope=openid offline_access` to the password-grant request made Keycloak return `refresh_expires_in=0` — a refresh token that, by Keycloak's own protocol, never expires.

2. **"How did you make a slow backend feel fast?"**
   I benchmarked before optimizing, and found that firing independent API calls **in parallel** cut load time by 75%, while switching HTTP libraries (to one with connection pooling) made things *slower* — because the college server's own processing time, not connection setup, was the actual bottleneck. The lesson: measure the real bottleneck before reaching for the "obviously better" tool.
   **If they dig deeper:**
   - *"Why didn't OkHttp's connection pooling help at all?"* — The server's own request-processing time (15–25s) dwarfs any TLS-handshake savings a pooled connection could offer; pooling only pays off when connection setup is a meaningful fraction of total request time, which it wasn't here.
   - *"What's the actual Kotlin mechanism behind the parallelism?"* — `coroutineScope { async { ... } }` around each independent call, collecting `Deferred` handles and calling `.await()` on each — every call starts immediately and total time becomes "the slowest one," not the sum.
   - *"What happens if one of the parallel calls throws?"* — Structured concurrency propagates the failure up through `coroutineScope` and can cancel sibling coroutines; if you wanted independent failures to not take down the others, you'd reach for `supervisorScope` instead.

3. **"Tell me about a bug that took a long time to find, and why."**
   Attendance data always showed 0 despite a successful login, because the API URL loaded a JavaScript single-page app's HTML shell, not raw JSON — Gson silently parsed the wrong shape into all-default zero values instead of throwing an error. The lesson: silent type-mismatch failures are far more dangerous than loud crashes, because they masquerade as legitimate (if wrong) data.
   **If they dig deeper:**
   - *"Why didn't this throw an obvious parse exception?"* — The URL wasn't a REST endpoint at all; it was a client-side SPA route that returns the app's HTML shell, so the failure was a wrong *shape* problem (webpage instead of a data payload) rather than a same-shape type mismatch — those two failure classes need different debugging instincts (see the Gson deep-dive for the contrast with a case that genuinely does throw).
   - *"How was it actually fixed?"* — Not by fixing the parser: by triggering Angular's own internal route change (`window.location.hash = '#!/attendanceStudentView'`) so the SPA fetched data the normal way, and catching that real request with the XHR interceptor.
   - *"How do you defend against this class of bug in general?"* — Never assume a URL that *looks* REST-shaped returns JSON; check the actual response content-type/shape before parsing, and diff field names/types against a captured real response rather than typing them from memory.

4. **"How do you handle a third-party server changing its behavior under you?"**
   By auto-detecting configuration from the same endpoint the browser client uses, rather than hardcoding values (a login server's internal name/ID changed three separate times over the project). Where hardcoding was unavoidable, I built fallback chains and treated unexpected error responses as "try the next tier," never as an automatic hard failure.
   **If they dig deeper:**
   - *"What exactly gets auto-detected?"* — The realm name, server URL, and client ID, fetched from the same `/sis/auth/config` endpoint the browser's own login page reads, with hardcoded values kept only as a last-resort fallback if that endpoint itself is unreachable.
   - *"What's another example of a silent server-side change you had to react to?"* — The college disabling Keycloak's Direct Access Grants (password grant) mid-project, and, separately, an access-control change that started 403-ing the lowercase attendance path outright (see the case-sensitivity deep-dive) — both were detected by treating unexpected response codes/bodies as signals to probe, not as immediate hard failures.
   - *"How do you avoid over-trusting an auto-detected value if the detection endpoint itself starts lying?"* — Always keep a hardcoded fallback path as the true last resort, and validate the detected token/config actually works against the real target API, not just that the config-fetch itself succeeded.

5. **"Describe a performance bug that turned out not to be what it looked like."**
   A scrolling list felt laggy even after multiple rounds of Compose optimization (flattening layouts, removing shadows, precomputing strings) — the actual cause was testing a **debug** build, which disables essentially all of Compose's compiler optimizations. A release build was smooth with none of those "fixes" even applied. Lesson: always benchmark UI performance on a release configuration.
   **If they dig deeper:**
   - *"What specifically does a debug build disable in Compose?"* — Essentially all compiler-level skipping/memoization of unchanged composables — the same UI tree that would skip recomposing untouched parts in release re-runs far more of its recomposition logic in debug, which alone can be a 5–10x slowdown.
   - *"What had already been tried before finding the real cause?"* — Flattening nested loops, replacing shadowed `Card`s with plain backgrounds, pre-computing date-formatting strings instead of doing it during rendering, and sharing shape objects instead of recreating them per frame — all genuinely good practice, none of which was the actual bottleneck here.
   - *"How do you catch this earlier next time instead of chasing debug-build ghosts?"* — Maintain a `minifiedDebug`-style build variant (debug-signed but with release-like optimization flags) and use it as the default for any scrolling/animation performance check, not just the final release candidate.

6. **"What's a subtle Compose/Android rendering bug you've hit?"**
   Placing a Canvas-drawn animation as a sibling inside a `Box` next to a GPU-blur-shader-based "glass" component caused the animation to smear across the entire screen — because the shader's compositing pipeline treated the new Canvas as part of the "backdrop" it should blur. The fix was drawing into the *existing* layout's own draw scope via `Modifier.drawBehind`, never adding a new Canvas/Box sibling near a blur-shader parent.
   **If they dig deeper:**
   - *"Mechanically, why did it smear vertically specifically?"* — `Modifier.graphicsLayer { renderEffect = RenderEffect.createBlurEffect(...) }` promotes content to its own RenderNode and samples the composited backdrop texture through a distortion/blur kernel; the sibling Canvas's pixels got baked into that same backdrop texture and were warped by the same distortion field built for a static background.
   - *"What's the general fix pattern for this class of bug?"* — Never add a new `Canvas`/`Box` layer as a sibling inside a `graphicsLayer`-based blur parent's subtree — draw into the *existing* draw scope via `Modifier.drawBehind` instead, which shares the same draw pass and never becomes a separate compositing layer.
   - *"How would you diagnose this if you hadn't already suspected the glass library?"* — The tell is content smearing/stretching in a shape that matches the blur/distortion field's geometry, not the content's own shape — that's the fingerprint of "this got sampled as backdrop," and the fix-by-elimination is to remove any new sibling layer near the blur parent first.

7. **"How did you design an AI feature to be reliable on weak hardware?"**
   By strictly separating "does math" from "talks" — plain Kotlin code computes every number (attendance percentages, bunk-safety thresholds) and only hands the final, correct numbers to a small on-device language model, whose only job is generating the natural-language phrasing. This isn't a workaround for a small model's limitations; it's the right architecture at any model size, since language models are fundamentally unreliable at arithmetic.
   **If they dig deeper:**
   - *"What inference engine did you actually pick, and why not the alternatives?"* — Cactus, a Kotlin-friendly `llama.cpp` wrapper tuned for plain CPU inference, over Google's LiteRT-LM (best only with a capable NPU/GPU most mid-range student phones lack) and raw `llama.cpp` (too much C++/JNI integration cost for the payoff).
   - *"Why not just fine-tune the model to be better at arithmetic instead?"* — Even large cloud models are unreliable at exact multi-step arithmetic; the architecture (deterministic code computes, model only phrases) is presented as correct at any model size, not a workaround specific to a weak on-device model.
   - *"What's the actual measured cost this design avoided?"* — A model that's asked to also do the math risks silently wrong numbers reaching the user with no error signal at all — arguably worse than a slow answer, since it looks authoritative.

8. **"What was your biggest on-device AI performance win, and why?"**
   Cutting the prompt from ~500 tokens to ~25–40 tokens using intent classification, which mattered more than every model/engine optimization combined — because every token of "prefill" (context the model must read before it can start replying) costs real wall-clock time on a phone CPU. The lesson: for on-device inference, sending less data usually beats a faster engine.
   **If they dig deeper:**
   - *"Prefill vs. decode — which one did this actually target?"* — Prefill (reading the input context before any output token appears), not decode (generating the reply token-by-token) — prefill happens on *every* message regardless of what's asked, which is why shrinking it had a larger, more consistent effect than trimming output tokens.
   - *"How is a message actually classified into an intent?"* — Plain deterministic Kotlin keyword/string matching, deliberately not another LLM call, since that would mean paying for a second full inference pass just to route the first one.
   - *"What happens when the keyword classifier picks the wrong intent?"* — The user gets a reply from a generic/less-specific template rather than a crash — a graceful degradation, not a hard failure, though it can read as a less personalized answer.

9. **"Tell me about a real-time feature you had to re-architect for cost."**
   A Firestore-based chess lobby hit a scaling wall because "presence via a heartbeat document" bills reads proportional to the *square* of concurrent users (every heartbeat write notifies every other listener). I migrated the ephemeral lobby state to a Cloudflare Durable Object over a WebSocket, where presence *is* the connection itself — no heartbeat, no per-user read multiplication, and disconnects are detected via a closed socket instead of a staleness timeout.
   **If they dig deeper:**
   - *"Why is the cost specifically N-squared and not linear?"* — Every online player's heartbeat write triggers a live-update notification to every *other* connected client's Firestore listener, so with N concurrent players the daily read count scales roughly as `N² × (86400 / heartbeat_interval_seconds)`.
   - *"What is WebSocket Hibernation and why does it matter for cost, not just correctness?"* — It lets Cloudflare detach the Durable Object's in-memory JS state while keeping the raw socket open, so an idle lobby connection costs essentially nothing until an actual message arrives — the mechanism that makes presence-as-connection genuinely free at rest.
   - *"What did migrating off Firestore cost you architecturally?"* — A lingering "two ID systems" debt: the old system keyed players by a roll-number hash, the new one by a Firebase Authentication UID, requiring a display-name-based bridge as a stopgap until a proper unified identity migration.

10. **"Describe a bug you couldn't reproduce locally, and how you found it anyway."**
    A production-only chess presence bug (two users online, neither saw the other) only happened after Cloudflare's WebSocket Hibernation cycle put a server object to sleep — hibernation preserves live socket connections but wipes ordinary in-memory class state, which a local dev server never actually hibernates under normal test conditions. I confirmed the theory using the platform's live log-tailing tool against the real deployed server, then fixed it by rebuilding in-memory state from the sockets' own attached metadata on wake-up, rather than trusting anything to survive across a sleep cycle.
    **If they dig deeper:**
    - *"Why doesn't `wrangler dev --local` reproduce this?"* — Hibernation only triggers under real, sustained-idle conditions at Cloudflare's actual edge runtime; a local dev server runs identical code without ever genuinely hibernating, so it looks correct by construction.
    - *"What exactly survives hibernation, and what doesn't?"* — The raw WebSocket connections and anything explicitly stashed on them via `serializeAttachment` survive; ordinary in-memory class fields (like a `Map` of connected players) do not and must be rebuilt from the sockets on the next construction.
    - *"How was it actually diagnosed in production?"* — By authenticating into the real deployed Worker and tailing its live logs (`wrangler tail`), watching an actual join sequence report `liveSockets=0 mapPlayers=0` — a symptom no local run could ever produce.

11. **"What's your approach to feature flags / remote kill-switches?"**
    Ship them from day one, even dormant with a harmless default — retrofitting a kill-switch later means the exact users you'd most want to reach with it (people stuck on an old broken build) can never receive it, since they don't have the code that reads the flag at all. I also learned to deploy flag changes through a source-controlled config file via a CLI command rather than a manual edit or a web console click, after a careless find-and-replace briefly flipped every unrelated flag in the same file at once.
    **If they dig deeper:**
    - *"What actually went wrong in that find-and-replace incident?"* — A `sed` command meant to flip one flag's value matched the literal string `"value": "false"` across the whole config file, briefly flipping every other flag on the same value — including a sideload-block wall and a maintenance dialog — to `true` for about 30 seconds before being reverted.
    - *"How do you prevent that specific mistake going forward?"* — Edit the config as structured data bound to a single key (e.g. a small script using a JSON parser) rather than a string-pattern replace, and always re-read the diff before deploying.
    - *"What flags actually exist in this project, and what do they gate?"* — Among others: `min_version_code` (force-update wall), `ads_enabled`, `sideload_block_enabled`, `maintenance_enabled`, `class_compare_enabled` (the marks-comparison kill-switch), and `chess_backend_v2` (which backend a client connects to).

12. **"Tell me about a privacy-sensitive design decision you made."**
    For a feature comparing a student's marks against their section's average, I enforced a hard server-side minimum of 15 participating students before showing any comparison data at all — a direct k-anonymity guard against a small group being able to reverse-engineer an individual's exact score. Enforcing it server-side (not just in the UI) matters because a client-side-only gate can be bypassed by anyone willing to inspect the raw network response.
    **If they dig deeper:**
    - *"Why 15 specifically, and not some other number?"* — It's a deliberate margin over an initially proposed floor of 5 — the goal is that even the smallest identifiable group ("one of fifteen") is still meaningfully anonymous, not just technically non-empty.
    - *"Why does server-side enforcement matter more than a client-side check here?"* — A client-side-only gate is trivially bypassed by anyone willing to intercept and inspect the raw network response; the anonymity guarantee has to be enforced by the party that controls what data actually leaves the server.
    - *"How do credentials stay out of this entirely?"* — Marks are uploaded only by the student's own device via the same background-worker pattern already used for attendance refresh, never fetched centrally with stored login credentials — avoiding a single point of failure where one server breach compromises every student's college password.

13. **"What did you learn from R8/release-build bugs specifically?"**
    R8's aggressive optimization targets anything reached through reflection — JavaScript-interface bridges, generic type tokens, libraries that instantiate classes by name at runtime — and these bugs are invisible in debug builds, only surfacing after a release upload. My biggest process change was always sideloading and manually testing a release-configured build before ever publishing it, rather than trusting "it worked in debug."
    **If they dig deeper:**
    - *"Give a concrete keep-rule gotcha beyond 'add a keep rule.'"* — Apache POI's `Class.forName(...).newInstance()` reflective construction needed the constructor kept explicitly (`-keepclassmembers class org.apache.logging.log4j.** { <init>(...); }`) — even a `-keep class Foo { *; }` rule can still let R8 strip a constructor it decides looks unused.
    - *"What are the three different reflection surfaces you actually hit?"* — `@JavascriptInterface` methods on anonymous inner classes (needed a wildcard `-keepclassmembers class *`), Gson `TypeToken`'s generic signature (needed `-keepattributes Signature`), and Apache POI's class-name-based instantiation (needed explicit `<init>(...)` keep rules).
    - *"What's the standing process change, concretely?"* — A `minifiedDebug` build variant (debug-signed, `isMinifyEnabled = true`) tested regularly during development, plus always manually sideloading and exercising a release-configured build before any upload.

14. **"How did you discover undocumented third-party APIs?"**
    By driving a real browser session (via browser-automation tooling) into the target web app, then reading the network requests its own JavaScript made — rather than guessing endpoint URLs or reading stale docs. For a JavaScript single-page app, "the API" is defined by whatever calls the app's own code actually makes; the network tab is more reliable than any documentation.
    **If they dig deeper:**
    - *"Walk through a concrete example."* — The semester results endpoint was found by reading the compiled JavaScript source for the "View All Results" page, spotting the internal function name (`viewAllResultsServices.getAllResults()`), then watching the actual network request that function produced when triggered live.
    - *"Why wasn't reading the compiled source alone enough?"* — It got the function name and rough shape right but missed a required parameter that only became visible by watching the live, actual request the browser made — documentation and static source-reading alone produced a URL that didn't work.
    - *"What's the failure mode of guessing an endpoint shape instead?"* — Silently wrong or incomplete requests (missing required parameters, wrong casing, wrong verb) that may return a plausible-looking but incorrect response rather than an obvious error.

15. **"What's an example of you making an explicit ethical tradeoff during development?"**
    I found (and used) an access-control bypass on the college's own API using my own account, then had to decide whether to ship that same bypass to 1,400+ students via a public app update. I chose to ship it (the alternative was leaving all their attendance broken indefinitely) but documented the decision explicitly, because distributing a discovered access-control flaw at scale is a categorically different action from a single researcher checking their own data once.
    **If they dig deeper:**
    - *"What's the actual technical bypass, precisely?"* — A case-folding mismatch: the ACL middleware matched the deny rule against a case-sensitive lowercase literal string, while the underlying router resolved routes case-insensitively — capitalizing one letter (`/sis/Attendance/` vs `/sis/attendance/`) slipped past the deny check while the router still resolved to the identical working handler.
    - *"How did you rule out a token-side cause before concluding it was an ACL bug?"* — Manually decoded the JWT's payload and confirmed it carried the same audience and role claims as the day before, when the endpoint had worked normally, and every other endpoint on the same server accepted the same token fine — isolating the problem to that one path's access-control layer specifically.
    - *"What's the professional, defensive fix you'd recommend to the college?"* — Normalize the path's case before the ACL check runs, or better, move the access-control decision into the router itself rather than a separate middleware/gateway layer that can disagree with the router about canonical path form.
