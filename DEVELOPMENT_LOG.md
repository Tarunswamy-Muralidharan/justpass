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
11. [Interview Talking Points](#interview-talking-points)

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

## Interview Talking Points

Short, rehearsable answers for the recurring engineering decisions in this project.

1. **"Walk me through the authentication system."**
   A four-tier fallback ladder: a cached token via direct HTTP (fastest), a refresh-token exchange, a direct password-grant login, and a full embedded-browser (WebView) login as the last resort. Each tier only runs if every faster one fails, so the system degrades gracefully instead of falling over the moment any single mechanism (like a server-side policy change disabling password grants) stops working.

2. **"How did you make a slow backend feel fast?"**
   I benchmarked before optimizing, and found that firing independent API calls **in parallel** cut load time by 75%, while switching HTTP libraries (to one with connection pooling) made things *slower* — because the college server's own processing time, not connection setup, was the actual bottleneck. The lesson: measure the real bottleneck before reaching for the "obviously better" tool.

3. **"Tell me about a bug that took a long time to find, and why."**
   Attendance data always showed 0 despite a successful login, because the API URL loaded a JavaScript single-page app's HTML shell, not raw JSON — Gson silently parsed the wrong shape into all-default zero values instead of throwing an error. The lesson: silent type-mismatch failures are far more dangerous than loud crashes, because they masquerade as legitimate (if wrong) data.

4. **"How do you handle a third-party server changing its behavior under you?"**
   By auto-detecting configuration from the same endpoint the browser client uses, rather than hardcoding values (a login server's internal name/ID changed three separate times over the project). Where hardcoding was unavoidable, I built fallback chains and treated unexpected error responses as "try the next tier," never as an automatic hard failure.

5. **"Describe a performance bug that turned out not to be what it looked like."**
   A scrolling list felt laggy even after multiple rounds of Compose optimization (flattening layouts, removing shadows, precomputing strings) — the actual cause was testing a **debug** build, which disables essentially all of Compose's compiler optimizations. A release build was smooth with none of those "fixes" even applied. Lesson: always benchmark UI performance on a release configuration.

6. **"What's a subtle Compose/Android rendering bug you've hit?"**
   Placing a Canvas-drawn animation as a sibling inside a `Box` next to a GPU-blur-shader-based "glass" component caused the animation to smear across the entire screen — because the shader's compositing pipeline treated the new Canvas as part of the "backdrop" it should blur. The fix was drawing into the *existing* layout's own draw scope via `Modifier.drawBehind`, never adding a new Canvas/Box sibling near a blur-shader parent.

7. **"How did you design an AI feature to be reliable on weak hardware?"**
   By strictly separating "does math" from "talks" — plain Kotlin code computes every number (attendance percentages, bunk-safety thresholds) and only hands the final, correct numbers to a small on-device language model, whose only job is generating the natural-language phrasing. This isn't a workaround for a small model's limitations; it's the right architecture at any model size, since language models are fundamentally unreliable at arithmetic.

8. **"What was your biggest on-device AI performance win, and why?"**
   Cutting the prompt from ~500 tokens to ~25–40 tokens using intent classification, which mattered more than every model/engine optimization combined — because every token of "prefill" (context the model must read before it can start replying) costs real wall-clock time on a phone CPU. The lesson: for on-device inference, sending less data usually beats a faster engine.

9. **"Tell me about a real-time feature you had to re-architect for cost."**
   A Firestore-based chess lobby hit a scaling wall because "presence via a heartbeat document" bills reads proportional to the *square* of concurrent users (every heartbeat write notifies every other listener). I migrated the ephemeral lobby state to a Cloudflare Durable Object over a WebSocket, where presence *is* the connection itself — no heartbeat, no per-user read multiplication, and disconnects are detected via a closed socket instead of a staleness timeout.

10. **"Describe a bug you couldn't reproduce locally, and how you found it anyway."**
    A production-only chess presence bug (two users online, neither saw the other) only happened after Cloudflare's WebSocket Hibernation cycle put a server object to sleep — hibernation preserves live socket connections but wipes ordinary in-memory class state, which a local dev server never actually hibernates under normal test conditions. I confirmed the theory using the platform's live log-tailing tool against the real deployed server, then fixed it by rebuilding in-memory state from the sockets' own attached metadata on wake-up, rather than trusting anything to survive across a sleep cycle.

11. **"What's your approach to feature flags / remote kill-switches?"**
    Ship them from day one, even dormant with a harmless default — retrofitting a kill-switch later means the exact users you'd most want to reach with it (people stuck on an old broken build) can never receive it, since they don't have the code that reads the flag at all. I also learned to deploy flag changes through a source-controlled config file via a CLI command rather than a manual edit or a web console click, after a careless find-and-replace briefly flipped every unrelated flag in the same file at once.

12. **"Tell me about a privacy-sensitive design decision you made."**
    For a feature comparing a student's marks against their section's average, I enforced a hard server-side minimum of 15 participating students before showing any comparison data at all — a direct k-anonymity guard against a small group being able to reverse-engineer an individual's exact score. Enforcing it server-side (not just in the UI) matters because a client-side-only gate can be bypassed by anyone willing to inspect the raw network response.

13. **"What did you learn from R8/release-build bugs specifically?"**
    R8's aggressive optimization targets anything reached through reflection — JavaScript-interface bridges, generic type tokens, libraries that instantiate classes by name at runtime — and these bugs are invisible in debug builds, only surfacing after a release upload. My biggest process change was always sideloading and manually testing a release-configured build before ever publishing it, rather than trusting "it worked in debug."

14. **"How did you discover undocumented third-party APIs?"**
    By driving a real browser session (via browser-automation tooling) into the target web app, then reading the network requests its own JavaScript made — rather than guessing endpoint URLs or reading stale docs. For a JavaScript single-page app, "the API" is defined by whatever calls the app's own code actually makes; the network tab is more reliable than any documentation.

15. **"What's an example of you making an explicit ethical tradeoff during development?"**
    I found (and used) an access-control bypass on the college's own API using my own account, then had to decide whether to ship that same bypass to 1,400+ students via a public app update. I chose to ship it (the alternative was leaving all their attendance broken indefinitely) but documented the decision explicitly, because distributing a discovered access-control flaw at scale is a categorically different action from a single researcher checking their own data once.
