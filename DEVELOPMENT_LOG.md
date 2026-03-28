# AttendanceWidgetLaudea - Development Log

A detailed journal documenting every challenge, mistake, and solution encountered while building the Attendance Widget app for PSG iTech LAUDEA SIS.

---

## Project Overview

**Project Name:** AttendanceWidgetLaudea
**Type:** Android Widget + Full App
**Language:** Kotlin + Jetpack Compose
**Started:** December 2025
**Developer:** Tarunswamy Muralidharan

---

## Hurdles & Challenges

### Challenge 1: Login Timeout — "Could not fetch attendance data"
**Problem:**
The very first login attempt would timeout after 30 seconds with "Login failed: timeout could not fetch attendance data". The app was creating a WebView, loading the LAUDEA SIS page, getting redirected to Keycloak for authentication, but then failing to capture the attendance data after successful login.

**Root Cause:**
The initial timeout was too short (30 seconds) for the full WebView flow: load SIS → redirect to Keycloak → fill credentials → submit → redirect back to SIS → Angular SPA boots → XHR fires → data received. This chain involves multiple page loads and redirects that can be slow on mobile networks.

**Solution:**
Increased the login timeout from 30 seconds to 60 seconds and reduced unnecessary delays in the login flow.

**Lesson Learned:**
WebView-based authentication flows are inherently slow due to multiple redirects. Always set generous timeouts and provide user feedback during long operations.

---

### Challenge 2: "Failed to Fetch" — JavaScript Token Hunting Was Fragile
**Problem:**
After login, the app tried to find the Keycloak auth token by searching through `window` object properties in JavaScript, then using `fetch()` to call the attendance API directly. This approach was extremely fragile and would fail silently.

**Root Cause:**
The JavaScript code was hunting through `window` keys looking for objects with a `.token` property (Keycloak instance). This is unreliable because:
- The Keycloak object name varies
- It might not be initialized yet when the script runs
- Angular's digest cycle might not have completed

**Solution:**
Completely rewrote the approach to use **XHR (XMLHttpRequest) interception**. Instead of hunting for tokens, we hook into `XMLHttpRequest.prototype` to intercept all XHR calls. When Angular makes its own attendance API call, we capture both the auth token and the response data. This is reliable because we're piggy-backing on Angular's own authenticated requests.

```javascript
// Hook into XHR to capture auth headers and responses
XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
    if (name === 'authorization') {
        // Capture the token Angular is using
        Android.onAuthToken(value.replace('Bearer ', ''));
    }
    return origSetHeader.apply(this, arguments);
};
```

**Lesson Learned:**
Don't fight the framework — work with it. Instead of trying to replicate what Angular does, intercept what Angular already does naturally.

---

### Challenge 3: All Data Showing 0 After Login
**Problem:**
Login appeared successful, but the dashboard showed 0 for everything — attendance percentage, present count, absent count, all zeros.

**Root Cause:**
The initial approach tried to navigate the WebView directly to `https://laudea.psgitech.ac.in/sis/attendance/ROLLNUMBER`. But this URL loads the Angular SPA (an HTML page), not raw JSON. The body text was HTML, not JSON, so Gson parsed it and got all default values (0).

**Solution:**
Instead of navigating to the API URL directly, we:
1. Stay on the SIS Angular app
2. Navigate Angular's internal router using `window.location.hash = '#!/attendanceStudentView'`
3. Intercept the XHR that Angular makes to fetch the attendance data
4. Capture the JSON response from that XHR

**Lesson Learned:**
Angular SPAs with hash routing (`#!/route`) make their own API calls internally. You can't just load the API URL in a browser — you need to trigger Angular's route change and intercept its XHR.

---

### Challenge 4: Wrong Angular Route
**Problem:**
After implementing XHR interception, the app still wasn't getting data because Angular wasn't making the attendance API call.

**Root Cause:**
Used `#/studentAttendance` as the Angular route, which was wrong. The correct route was `#!/attendanceStudentView` (with the `!` — Angular's hashbang mode).

**Solution:**
Checked the actual website's URL bar while on the attendance page and found the correct route: `#!/attendanceStudentView`. Updated the JavaScript navigation accordingly.

**Lesson Learned:**
Always verify routes by checking the actual website. Angular apps can use different routing modes (`#/`, `#!/`, or HTML5 pushState), and getting it wrong means the page never loads.

---

### Challenge 5: Exemption Percentage Always Wrong
**Problem:**
The app was showing attendance without exemption (74.3%) instead of with exemption (90%). The exemption fields were always null or showing the same value as regular attendance.

**Root Cause:**
The server has a **typo** in the JSON field name. The field is `netPresentExcemptionPercentage` (note: "excemption" not "exemption"). The `@SerializedName` annotation had the correctly spelled version, so Gson couldn't match it.

```kotlin
// WRONG — correct English but doesn't match server
@SerializedName("netPresentExemptionPercentage")

// RIGHT — matches server's typo
@SerializedName("netPresentExcemptionPercentage")
```

**Solution:**
Fixed the `@SerializedName` to match the server's exact (misspelled) field name. The developer (Tarunswamy) caught this by comparing the raw JSON response with the model annotations.

**Lesson Learned:**
`@SerializedName` must match the server's EXACT field names, including typos. Always verify against actual API responses, not documentation (which might be corrected while the API isn't).

---

### Challenge 6: InvocationTargetException Crash
**Problem:**
After modifying the XHR interception code, the app started crashing with `java.lang.reflect.InvocationTargetException` immediately on login.

**Root Cause:**
The XHR hook was creating infinite recursion. Inside the hooked `XMLHttpRequest.prototype.send`, the code was creating a new `XMLHttpRequest` (for a fallback fetch), which would trigger the hook again, which would create another XHR, and so on.

**Solution:**
Used `setTimeout(0)` to break the recursion chain, and restored the original `XMLHttpRequest.prototype` methods before making any fallback requests.

**Lesson Learned:**
When monkey-patching prototypes (XHR, fetch, etc.), be extremely careful about recursion. Any code inside the hook that uses the same API will trigger the hook again.

---

### Challenge 7: Extremely Slow Refresh
**Problem:**
Refreshing attendance data took 15-20 seconds. The website loads instantly on refresh, but the app was painfully slow.

**Root Cause:**
Every refresh was creating a brand new WebView, loading the SIS page, waiting for Angular to boot, intercepting XHR, etc. The website is fast because the browser maintains the session — but the app was doing a full WebView lifecycle every time.

**Solution:**
Implemented a **two-tier refresh strategy**:
1. **Fast path**: Use the cached Bearer token from the last login/refresh to make a direct HTTP call (`HttpURLConnection`) to the attendance API. This is instant (~200ms).
2. **Slow path**: If the fast path fails (token expired, 401), fall back to the full WebView flow.

```kotlin
// Fast: Direct HTTP with cached token
val connection = url.openConnection() as HttpURLConnection
connection.setRequestProperty("Authorization", "Bearer $token")
// Returns in ~200ms

// Slow: Full WebView flow (only if fast fails)
// Takes 15-20 seconds
```

**Lesson Learned:**
Cache auth tokens aggressively. Most APIs accept Bearer tokens via direct HTTP — you don't need a full browser session for every request.

---

### Challenge 8: Jittery/Laggy Scrolling in Absent Days Screen
**Problem:**
The Absent Days detail screen scrolled with visible jitter and frame drops, making it feel broken.

**Root Cause (Stage 1 — Code):**
The initial implementation used nested `forEach` loops inside `LazyColumn` items, which meant Compose was creating items incorrectly. Fixed by flattening into a single list of `DateHeader` and `SessionRow` items.

**Root Cause (Stage 2 — Still laggy after fix):**
- `Card` composable renders elevation/shadows — expensive per item
- `clip() + background()` creates extra compositing layers
- `SimpleDateFormat` was being created during composition (every frame)
- `.copy(alpha = 0.5f)` forces alpha blending layers
- New `RoundedCornerShape` instances created every recomposition

**Root Cause (Stage 3 — STILL laggy):**
Even after all optimizations, scrolling was still jittery. The real culprit: **debug builds**. Compose debug builds disable ALL compiler optimizations — no skipping, no memoization, everything recomposes every frame. This is a well-known Android/Compose limitation.

**Solution (accumulated):**
1. Flattened nested lists into `DateHeader` + `SessionRow` items
2. Replaced `Card` with plain `Row` + `background(color, shape)`
3. Removed `Surface` wrapper (still has internal overhead)
4. Pre-computed all strings (`formatDate`, `session.replace()`) during list building, not during composition
5. Shared `RoundedCornerShape` instances as top-level constants
6. Replaced `.copy(alpha)` with solid theme colors
7. Added stable keys and `contentType` to LazyColumn
8. **Built release APK** with R8 optimizations enabled

**Lesson Learned:**
Never judge Compose scrolling performance in debug builds. Always test with release/minified builds. Debug Compose can be 5-10x slower than release.

---

### Challenge 9: Release Build Failures
**Problem:**
Couldn't build release APK — multiple issues in sequence:

1. **Missing keystore**: `Keystore file 'release-keystore.jks' not found` — the `keystore.properties` file existed but pointed to a non-existent keystore file.
2. **R8 missing classes**: `Missing classes detected while running R8` — Error Prone annotations were referenced but not present.
3. **R8 stripping JavascriptInterface**: After getting release to build, the app couldn't fetch data — R8 was stripping the `@JavascriptInterface` methods from anonymous inner classes.
4. **Gson TypeToken stripped**: `List<AbsentDay>` deserialization broke because R8 removed generic type information.

**Solutions:**
1. Made release build fall back to debug signing key when keystore doesn't exist
2. Added `-dontwarn` rules for Error Prone annotations
3. Changed ProGuard rules from class-specific to wildcard: `-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }`
4. Added keep rules for Gson TypeToken

**Lesson Learned:**
R8/ProGuard is aggressive. Anonymous inner classes, reflection-based frameworks (Gson), and annotation-based APIs (@JavascriptInterface) all need explicit keep rules. Test release builds thoroughly — they can behave completely differently from debug.

---

### Challenge 10: CA Marks HTTP 500 Error
**Problem:**
CA Marks screen showed an error — the server returned HTTP 500.

**Root Cause:**
The CA marks fetch was using an old WebView-based approach that tried to find tokens by searching `window` object properties in JavaScript. This approach was unreliable (same issue as Challenge 2) and the token search was failing, causing the request to go without proper authentication.

**Solution:**
Added a direct HTTP fetch method (`fetchCAMarksDirect`) using the cached Bearer token, identical to how absent days works. Added auto-re-login fallback when the token expires.

**Lesson Learned:**
Consistency matters. Once you find a reliable approach (cached token + direct HTTP), use it everywhere. Don't leave old fragile code in some paths while using the new approach in others.

---

### Challenge 11: Multiple Times Breaking Login While Changing UI
**Problem:**
On at least two occasions, making UI-only changes (AbsentDaysScreen rewrite, DashboardScreen styling) appeared to break login. The app would show "could not fetch attendance data, check internet connection."

**Root Cause:**
The login code was never actually broken. The real causes were:
1. **Stale APK**: Build failed silently, so the old APK was still running on the device
2. **Token expiry**: The time spent editing code (30+ minutes) caused the server session to expire naturally
3. **Coincidence**: Server-side issues happening to coincide with code changes

**Solution:**
Always verify:
1. Check the Build tab for errors
2. Do a Clean Build before testing
3. Remember that tokens expire — a fresh login after editing is normal

**Lesson Learned:**
Correlation doesn't imply causation. UI-only changes can't break network code. Always check if the build actually succeeded before debugging phantom issues.

---

## Mistakes Made

### Mistake 1: Spelling @SerializedName Correctly
**What happened:**
Used correct English spelling (`exemption`) instead of the server's misspelled version (`excemption`), causing exemption data to always be null.

**Why it happened:**
Natural assumption that API field names are spelled correctly.

**How to avoid in future:**
Always copy field names directly from actual API responses, never type them manually.

---

### Mistake 2: Navigating WebView Directly to API URL
**What happened:**
Tried to load `https://laudea.psgitech.ac.in/sis/attendance/ROLL` directly in WebView, expecting JSON. Got HTML instead because it's an Angular SPA.

**Why it happened:**
Assumed the URL would return raw JSON like a REST API. Didn't realize the Angular app serves HTML from all routes and fetches data via XHR internally.

**How to avoid in future:**
Check what a URL returns in a browser first. If it loads a web app, the data comes from XHR calls, not the page itself.

---

### Mistake 3: Rewriting Working Code Multiple Times
**What happened:**
The XHR interception approach was rewritten several times, each time breaking login. Had to revert to the working version multiple times.

**Why it happened:**
Trying to "improve" code that was already working, without proper version control or incremental changes.

**How to avoid in future:**
Use git commits before making changes. If it works, commit first, then experiment. Much easier to revert.

---

### Mistake 4: Not Testing Release Builds Early
**What happened:**
Spent hours optimizing Compose UI for scroll performance, when the real fix was simply building in release mode.

**Why it happened:**
Didn't know that Compose debug builds disable all compiler optimizations.

**How to avoid in future:**
Test performance in release builds from day one. Debug builds are for correctness, not performance.

---

## Key Decisions

| Decision | Reason |
|----------|--------|
| WebView-based auth instead of direct API | LAUDEA uses Keycloak SSO with complex redirect flows that can't be replicated with simple HTTP |
| XHR interception instead of token hunting | Reliable — captures the exact token Angular uses, guaranteed to work |
| Cached token + direct HTTP for refresh | 200ms vs 15-20 seconds. Massive UX improvement |
| 3-tier refresh (token → Keycloak → WebView) | Handles every failure scenario: cached token, expired token, disabled grant |
| Persist token in EncryptedSharedPreferences | Survives process death — no more slow refreshes after Android kills the app |
| Firebase Analytics for usage tracking | Free, automatic tracking of active users, sessions, and custom events |
| Roll number as Firebase user ID | Lets us see exactly who is using the app |
| Auto re-login on token expiry | Users never see login screen again after initial setup |
| Flattened LazyColumn list | Prevents jitter from nested forEach inside items |
| Surface/Box instead of Card | Cards render shadows which are expensive in lists |
| Pre-computed display strings | Avoid SimpleDateFormat and string operations during composition |
| EncryptedSharedPreferences | Credentials stored securely on device |
| WorkManager for background refresh | Survives app kills, respects battery optimization |
| offline_access scope on password grant | Never-expiring refresh token — widget works forever |
| Self-chaining OneTimeWorkRequest (8 min) | More frequent than PeriodicWorkRequest (min 15 min) |
| Battery whitelist dialog | Ensures WorkManager actually runs in background |
| Background update notification | Users know about new versions without opening app |
| JWT name extraction for analytics | Track who uses the app via Firebase user properties |
| Pull-to-refresh with hidden indicator | Comet glow animation serves as refresh indicator — cleaner than default spinner |
| Result API via browser discovery | No docs existed — inspected Angular service source to find endpoint |
| clipPath for animation containment | Keeps glow effects inside glass cards — no spill outside |

---

## Architecture Overview

```
Login Flow:
  WebView → Keycloak SSO → XHR Intercept → Capture Token + Data → Cache Token

Refresh Flow (Fast — ~200ms):
  Persisted Token → Direct HTTP → Instant response

Refresh Flow (Medium — ~500ms):
  Token Expired → Direct Keycloak password grant → New Token → Direct HTTP

Refresh Flow (Slow — ~15s, last resort):
  Keycloak grant disabled → Full WebView login → XHR Intercept → New Token

Data Flow:
  WebViewAuthenticator → AttendanceRepository → ViewModel → Compose UI

Widget Flow:
  WorkManager (periodic) → AttendanceRepository → SecurePreferences → Glance Widget
```

---

## Progress Timeline

### Phase 1: Core Login & Dashboard
- Implemented WebView-based Keycloak authentication
- Built attendance dashboard with percentage, present/absent counts
- Created home screen widget with Glance

### Phase 2: Bug Fixes & Data Accuracy
- Fixed timeout issues (30s → 60s)
- Switched from token hunting to XHR interception
- Fixed Angular route (`#!/attendanceStudentView`)
- Fixed server field name typo (`excemption`)
- Added exemption data display

### Phase 3: Performance
- Implemented cached token + direct HTTP refresh (200ms vs 15s)
- Added `enteredTillDate` and `notEnteredTillDate` from API

### Phase 4: Absent Days Feature
- Added absent days API fetch
- Built detail screen with date headers and session cards
- Fixed LazyColumn jitter (flatten, optimize composables)
- Added auto re-login for expired tokens

### Phase 5: CA Marks
- Built CA marks screen with expandable course cards
- Switched from WebView fetch to direct HTTP
- Added auto re-login fallback

### Phase 6: Release & Distribution
- Fixed ProGuard/R8 rules for release builds
- Set up GitHub repository
- Prepared for release distribution
- Added in-app update checker via GitHub Releases API

### Phase 7: 3-Tier Token Refresh (March 14, 2026)
- Upgraded from 2-tier to 3-tier refresh strategy
- Problem: After a few days, refresh was slow again (~15s). The cached token was only in-memory, so when Android killed the process, it was lost. Every refresh after that fell back to the slow WebView path.
- Solution: Implemented 3-tier refresh:
  1. **Fast path (~200ms)**: Direct HTTP with cached token (now persisted to EncryptedSharedPreferences)
  2. **Medium path (~500ms)**: Direct Keycloak `grant_type=password` POST to get a fresh token, then direct HTTP fetch
  3. **Slow path (~15s)**: Full WebView login (only if Keycloak direct grant is disabled by the server)
- Added `loginViaKeycloak()` to WebViewAuthenticator — sends username/password directly to the Keycloak token endpoint, bypassing WebView entirely
- Changed `cachedAuthToken` from in-memory variable to a property backed by `SecurePreferences.accessToken` so it survives process death
- Fixed `fetchAttendanceOnly` (slow path) to also capture tokens via `onAuthToken` JS interface, so even after a slow-path refresh, subsequent refreshes are fast
- Applied 3-tier strategy to all data fetchers: attendance, CA marks, and absent days
- Error handled: If direct Keycloak grant is disabled by the college server, the app gracefully falls back to the WebView path — no regression

### Phase 8: Firebase Analytics & Name Tracking (March 18, 2026)
- Wanted to track how many people are downloading and using the app
- Set up Firebase project "AttendanceWidget" on Firebase Console (Spark/free plan)
- Registered Android app with package name `com.example.attendancewidgetlaudea`
- Downloaded `google-services.json` and placed in `app/` directory
- Challenge: Firebase Console kept rejecting the package name — turned out to be invisible characters or typos when pasting. Had to type it manually character by character.
- Added Firebase dependencies via version catalog (`libs.versions.toml`):
  - `com.google.gms:google-services` plugin (v4.4.2) in both root and app gradle
  - `firebase-bom` (v33.7.0) for version management
  - `firebase-analytics-ktx` for the analytics SDK
- Created `Analytics.kt` utility object wrapping Firebase Analytics with these events:
  - `setUser()` — sets roll number as Firebase user ID (so we can see who logged in)
  - `logLogin()` — tracks login events with roll number
  - `logScreenView()` — tracks which screens users visit
  - `logRefresh()` — tracks attendance refreshes
  - `logLogout()` — tracks logouts
  - `logFeatureUsed()` — tracks CA marks and absent days usage
- Wired analytics into:
  - `MainActivity.onCreate()` — initialize Firebase Analytics
  - `AttendanceApp` composable — set user ID on app start, track screen views on navigation
  - `LoginViewModel` — log login event + set user ID on successful login
  - `MainActivity` logout handler — log logout + clear user ID
  - Navigation callbacks — log feature usage for CA marks and absent days
- Added `google-services.json` to `.gitignore` (contains Firebase API key)
- Build successful, released as v1.1

### Phase 9: Offline Tokens & Instant Refresh — The Breakthrough (March 18, 2026)

The holy grail: making the widget refresh instantly at any time, even after the phone has been off for days.

**Challenge 12: SIS API Rejecting Password Grant Tokens**

Earlier testing showed that tokens from `loginViaKeycloak()` (grant_type=password) were rejected by the SIS API with HTTP 500, while auth-code flow tokens (from WebView) worked fine. This meant every refresh still potentially needed the slow WebView path.

**The offline_access Discovery:**
- Added `scope=openid offline_access` to the password grant request
- Keycloak returned `refresh_expires_in=0` — a never-expiring offline refresh token!
- But the critical question remained: would the SIS API accept tokens obtained this way?

**Testing Methodology (On-Device via ADB):**

*Test 1: Invalidate cached token, force refresh path*
- Set `cachedAuthToken = "INVALID_TOKEN_FOR_TESTING"`
- Fast path → HTTP 500 (expected — garbage token)
- Refresh path → `refreshAccessToken()` used stored refresh token → new access token
- SIS API → HTTP 200! Attendance fetched successfully
- BUT: `refresh_expires_in=1800s, scope=openid` — this was the auth-code refresh token, not offline

*Test 2: Force fresh password grant, test SIS API directly*
- Forced `loginViaKeycloak()` at the start of `refreshAttendance()`
- Logs: `token expires in 600s, refresh expires in 0s, scope=openid offline_access`
- Immediately called SIS API with this password-grant token
- **Result: HTTP 200! `PASSWORD GRANT TOKEN WORKS WITH SIS API! Attendance: 88.4%`**

**Why it works now (contradiction resolved):**
Previously, password grant tokens were rejected (HTTP 500). With `offline_access` scope added, they're now accepted. The scope likely changes the token's claims or audience in a way the SIS server accepts.

**What this means:**
- Offline refresh token never expires → can always get a fresh access token
- Fresh access token works with SIS API → can always fetch attendance
- **Widget refresh is instant at ANY time**, no matter how long since last use
- WebView is essentially never needed for refreshes anymore

**Implementation:**
- `loginViaKeycloak()`: added `scope=openid%20offline_access`
- `refreshAccessToken()`: added `scope=openid%20offline_access`
- Self-chaining WorkManager (OneTimeWorkRequest every 8 min) replaces old 4-hour periodic
- Battery optimization whitelist dialog ensures background execution
- Cleaned up test code after confirming results

**Challenge 13: Background Update Notifications**

Users needed to know when a new version was available, even without opening the app.

**Solution:**
- Added `POST_NOTIFICATIONS` permission to AndroidManifest
- Created notification channel "App Updates" in the worker
- `AttendanceRefreshWorker.doWork()` now calls `checkForUpdateAndNotify()`
- Checks GitHub releases API, compares versions, shows notification with download link
- Tracks `last_notified_version` in SharedPreferences to avoid duplicate notifications
- Runtime permission request for Android 13+ (TIRAMISU) in MainActivity

**Released as v1.2** — committed, pushed, and created GitHub release with signed APK.

---

### Phase 9 Architecture Update

```
Refresh Flow (v1.2 — instant at any time):
  Cached Token (valid) → Direct HTTP (~750ms)
  Token Expired → Offline Refresh Token (never expires) → New Access Token → HTTP (~1.5s)
  Refresh Failed → Password Grant + offline_access → New Token → HTTP (~1.5s)
  Everything Failed → WebView Fallback (~15s, essentially never happens)

Background:
  WorkManager (self-chaining, 8 min) → Refresh Attendance + Check Updates
  Battery Whitelist → Ensures WorkManager runs reliably
  Notification → Alert users of new versions
```

### Phase 10: v2.0 — Major UI Revamp + Semester Results (March 20, 2026)
- Added Semester Result screen with API discovered via Chrome browser automation
- Pull-to-refresh on dashboard with comet light glass animation
- Chomping BITE ME easter egg animation with Canvas teeth
- UI polish: scroll padding (130dp), button tints, absent tile hints
- New files: ResultScreen.kt, ResultViewModel.kt, ResultData.kt

---

## Resources That Helped

- Android WebView documentation — understanding JavascriptInterface and page lifecycle
- XMLHttpRequest MDN docs — for writing the XHR intercept hooks
- Jetpack Compose performance guide — understanding debug vs release behavior
- R8/ProGuard documentation — keeping annotation-based APIs alive

---

## v1.3 Development (2026-03-19) — Glass UI + Subject Attendance (IN PROGRESS)

### Changes Made
1. **iOS Liquid Glass UI** — Integrated FletchMcKee/liquid library (v1.1.1) for GPU-accelerated frosted glass effects
   - Dual LiquidState architecture: cardState for card refraction, barState for bottom bar blur
   - Real AGSL shader effects: refraction, edge reflections, chromatic dispersion, lensing
   - Content inside liquefiable layer so glass bottom bar blurs actual scrolling content
   - Lightweight GlassListCard for scrolling items (zero GPU cost)
   - LiquidGlassCard for static elements (headers, main cards) with real glass refraction

2. **Floating Glass Bottom Bar** — iOS-style pill-shaped floating navigation
   - Frosted glass blur (28dp frost) with rounded corners
   - Animated sliding glass bubble indicator between tabs (bouncy spring: dampingRatio=0.7)
   - Haptic feedback on every tab press
   - Bouncy icon scale animation (1.3x → spring back) on tap

3. **Glass Light Reflection Animation** — Diagonal specular beam on refresh
   - Realistic light sweep from top-left to bottom-right across header card
   - Soft white highlight band with blue tint, smooth fade in/out

4. **Subject-wise Attendance** — New feature calculating per-subject attendance
   - Combines timetable (periods/week) + absent days (absences per subject)
   - Estimates total classes per subject from timetable proportions
   - Color-coded progress bars (red < 65%, yellow 65-75%, green ≥ 75%)
   - Accessible by tapping the main attendance % card on Dashboard

5. **Custom Glass Theme** — Custom dark/light color schemes with translucent surfaces
   - Disabled dynamic colors for consistent glass look
   - Gradient backgrounds on all screens

### Dependency Upgrades
- Kotlin: 2.0.21 → 2.3.0
- Compose BOM: 2024.09.00 → 2025.10.00 (Foundation 1.9.3)
- Added: io.github.fletchmckee.liquid:liquid:1.1.1
- Added: material-icons-extended
- Navigation: 2.7.7 → 2.8.5
- LifecycleViewModel: 2.7.0 → 2.8.7
- kotlinOptions DSL → kotlin.compilerOptions DSL (Kotlin 2.3 requirement)

### Obstacles Overcome
1. **Library compatibility** — Kyant0/AndroidLiquidGlass required Kotlin 2.3.10 + AGP 9.0 (too aggressive). Mortd3kay/liquid-glass-android was v0.1.0 experimental. FletchMcKee/liquid worked with Kotlin 2.3.0 + AGP 8.13.1.
2. **Scroll performance** — Initially every card used liquid() GPU shaders causing lag. Solution: only static elements use LiquidGlassCard, scrolling items use lightweight GlassListCard with canvas drawing.
3. **Content visibility through glass** — Bottom bar initially only blurred gradient background, not scrolling content. Solution: dual LiquidState architecture with content inside liquefiable layer.
4. **SIGSEGV constraint** — Liquid library crashes if liquid() nodes are descendants of liquefiable() with same state. Solution: separate cardState and barState, cards use cardState while bar uses barState.
5. **Glow animation rendering** — Canvas overlay was hidden by liquid() shader's GPU rendering layer. Solution: Canvas drawn as sibling AFTER the liquid card in compose tree (z-order).
6. **Kotlin 2.3 migration** — kotlinOptions { jvmTarget } was removed in Kotlin 2.3. Migrated to kotlin { compilerOptions { jvmTarget.set(...) } }.
7. **Compose BOM icons split** — New BOM (2025.10.00) moved Material Icons to separate dependency. Added material-icons-extended.

### New Files
- ui/components/GlassComponents.kt — LiquidGlassScaffold, LiquidGlassCard, GlassBottomBar, GlassListCard
- ui/screens/SubjectAttendanceScreen.kt — Per-subject attendance breakdown
- ui/viewmodel/SubjectAttendanceViewModel.kt — Calculates subject attendance from timetable + absent days
- data/model/SubjectAttendance.kt — Data model for subject-wise attendance

### Additional v1.3 Changes (2026-03-19 continued)

#### New Features
6. **Subject Detail Screen** — Tap any subject in Subject Attendance to see day-by-day attendance
   - Chronological list of every session: Present (green), Absent (red), Exemption (blue)
   - Stats summary: Present/Absent/Exemption/Total counts
   - 80% target warning: "Attend X more hours without absence to reach 80%"

7. **Exemptions Screen** — View all exemption applications
   - Fetches from `/sis/remote/exemptions/{rollNumber}` API
   - Shows exemption type, category (Day/Session), date range, session times, reason, status
   - Accessible by tapping the Exemption count on Dashboard

8. **Accurate Subject Attendance** — No more estimates
   - Present days from `/sis/attendance/present/{rollNumber}` (new API)
   - Absent days from `/sis/attendance/absent/{rollNumber}` (existing)
   - Exemptions mapped to subjects via timetable (Day → all subjects, Session → time-matched)
   - Exact totals: Present + Absent + Exemption = Total per subject

9. **75% Warning on Dashboard** — "Attend X more hours (~Y days) to reach 75%"
   - Formula: hours = ceil((0.75 * total - present) / 0.25)
   - Approx days = hours / 6 (avg classes per day)

10. **UI Polish**
    - All headers: LiquidGlassCard → GlassListCard for crisp readable text
    - CA Marks: "Tap to expand" / "Awaiting marks" (context-aware, not generic "No marks yet")
    - Profile: Attendance Overview card + App Info card with version
    - Crossfade tab transitions (200ms)
    - Bolder attendance % (52sp, Black weight, -1sp letter-spacing)
    - Brighter attendance tint colors (vivid red #FF5252, green #00E676)
    - "Tap for subject-wise details →" in primary blue

#### Obstacles Overcome (continued)
8. **Exemption sessions field type** — API returns `sessions` as JSON array (not string). Gson crashed with "Expected string but was BEGIN_ARRAY". Fixed by changing model from `String?` to `List<String>?`.
9. **Brown-looking red tint** — Dark red (#FF1744) at high alpha on dark navy gradient looked brown. Fixed by using lighter red (#FF5252) at lower alpha (18%).
10. **Header text readability** — LiquidGlassCard refraction distorted header text making it unreadable. Switched all headers to lightweight GlassListCard with explicit `onSurface` text colors.
11. **CA Marks "No marks yet"** — Showed even when IAT marks were entered (total was 0 but components had values). Fixed with component-level check: if any sub-marks exist, show "Tap to expand".

#### New Files (continued)
- ui/screens/SubjectDetailScreen.kt — Per-subject day-by-day attendance with exemption mapping
- ui/screens/ExemptionsScreen.kt — Exemption list with glass cards
- ui/viewmodel/ExemptionsViewModel.kt — Fetches exemptions from API
- data/model/Exemption.kt — Exemption data class with Gson annotations

---

### Phase 10: v2.0 — Major UI Revamp + Semester Results (2026-03-20)

#### New Features

1. **Semester Result Screen** — Full exam results with semester-wise tabs
   - API discovery: Used Chrome browser automation to explore the LAUDEA SIS Angular app
   - Found the API by inspecting Angular's `viewAllResultsServices` service → `getAllResults()` function
   - Discovered endpoint: `GET /sis/remote/all/results?rollNo={rollNumber}`
   - Response format: JSON array of `{_id, examId, attempt, semester, courseCode, courseTitle, letterGrade, gradePoint, status, examName}`
   - Built semester tabs, SGPA calculation, color-coded grade cards (O/S/A+/A/B+/B/C/D/U/F), pass/fail badges

2. **Pull-to-Refresh** — Swipe down on dashboard to refresh attendance
   - Used Material3 `PullToRefreshBox` with hidden default indicator
   - Made dashboard Column scrollable (replaced weight spacer)
   - Triggers comet glow animation + haptic feedback

3. **Comet Light Glass Animation** — Colorful light travels along glass border on refresh
   - Multiple iterations: started with rotating orb (wrong), then rotating rectangle (ugly), then travelling dots (disconnected)
   - Final: 20 overlapping radial gradient circles forming a smooth comet trail, clipped inside card shape using `clipPath`
   - Colors shift: white (head) → purple → pink → blue (tail)
   - Persists 2.5s after refresh for visibility

4. **Chomping BITE ME Animation** — Easter egg card gets "bitten"
   - Card squashes (scaleY: 1.0 → 0.8) with horizontal stretch
   - White triangle teeth close from top and bottom (Canvas drawPath)
   - Impact shake via translationX animation
   - Keyframe animation: open → pause → CHOMP → bounce → settle → open
   - Text: "BITE ME" in bold crimson red (#FF1744), wide letter spacing

5. **UI Polish**
   - "Tap for details →" on absent tile: 9sp/0.6alpha → 11sp/Medium/0.8alpha primary blue
   - Back/reload button tints: explicit onSurface color on CA Marks screen
   - Scroll bottom padding: 100dp → 130dp on ALL 9 scrollable screens to clear glass bottom bar

#### Obstacles Overcome

1. **Result API discovery** — No documentation existed. Tried 9 different endpoint patterns, all returned 404. Used Chrome browser automation to:
   - Navigate to SIS portal's "View All Results" page
   - Extracted Angular service function source: `viewAllResultsServices.getAllResults()`
   - Found URL pattern `remote/all/results` from function source
   - But direct fetch returned 500 (missing rollNo param)
   - Finally captured the actual XHR via network monitoring: `GET /sis/remote/all/results?rollNo={roll}` → 200 OK

2. **Comet animation iterations** — Three failed attempts before the working version:
   - Attempt 1: Rotating orb near refresh button → user said "must be around the glass, not corner"
   - Attempt 2: Rotating sweep gradient on rounded rect → looked like "a rectangle just rotating"
   - Attempt 3: Sweep gradient stroke → still rectangle-like
   - Solution: 20 overlapping radial gradient circles along perimeter path, clipped inside rounded rect path

3. **Pull-to-refresh default indicator** — PullToRefreshBox shows a circular spinner at top center that clashed with the glass header. Fixed with `indicator = {}` to hide it, using the comet animation as the visual indicator instead.

4. **Scroll bottom padding** — Users couldn't see the last item behind the floating glass bottom bar. Increased from 100dp to 130dp across all screens. Investigated if this caused jitter — confirmed it's debug APK overhead from Liquid Glass GPU shaders.

#### New Files
- ui/screens/ResultScreen.kt — Semester result screen with grade cards
- ui/viewmodel/ResultViewModel.kt — Result fetching and semester filtering
- data/model/ResultData.kt — GradeEntry data class for exam results

---

## Final Reflections

**What went well:**
- The XHR interception approach is elegant and reliable
- Cached token refresh gives near-instant data updates
- Auto re-login means zero friction for users
- The app provides a much better mobile experience than the website
- The offline_access breakthrough means the widget works forever without re-login
- Methodical on-device testing (invalidate token → test refresh → verify API) confirmed the solution
- v2.0 brought a polished, feature-rich experience: semester results, pull-to-refresh, animated glass effects, and playful easter eggs
- Chrome browser automation proved invaluable for discovering undocumented APIs — a technique that can be reused for any Angular/SPA-based portal
- The comet animation, despite three failed attempts, resulted in a unique visual effect that no other attendance app has

**What could have been better:**
- Should have used git from the start to avoid losing working code
- Should have tested release builds earlier for performance
- Should have copied API field names directly instead of typing them
- Should have tested offline_access scope earlier — it was the key to instant refresh all along
- Should have tried browser automation for API discovery earlier — would have saved hours of guessing endpoints
- Animation iterations could have been reduced by prototyping the visual concept before coding

**Skills gained:**
- WebView JavaScript interception and bridge communication
- Keycloak/SSO authentication flows (including direct `grant_type=password` token requests)
- Keycloak offline_access scope and never-expiring refresh tokens
- Android ProGuard/R8 configuration
- Jetpack Compose performance optimization
- Glance widget development
- Firebase Analytics integration and custom event tracking
- Multi-tier authentication strategies with graceful fallbacks
- Self-chaining WorkManager pattern for frequent background tasks
- Android notification channels and runtime permission requests (Android 13+)
- On-device testing via ADB for token flow verification
- Chrome browser automation for API discovery
- Compose Canvas animations (clipPath, radial gradients, comet trails)
- Material3 PullToRefreshBox
- PDF syllabus data extraction and structured curriculum modeling
- Multi-regulation CGPA calculator with department auto-detection

---

## v2.1 Development Session — March 25, 2026

### New Features Built

#### 1. Profile Academic Info Display
**What:** Added current year, semester, section, and programme name below the user's name on the Profile screen.

**Challenge:** The SIS API returns `department` as "COMPUTER SCIENCE AND ENGINEERING" even for CSBS students, because CSBS falls under the CSE department. The actual programme is in the `programmeName` field ("BTECH COMPUTER SCIENCE AND BUSINESS SYSTEMS").

**Solution:** Added 7 new fields to `StudentBiodata` model (`currentSem`, `section`, `department`, `batchYear`, `programDuration`, `degreeName`, `programmeName`), parsed from the API JSON. Profile now shows `programmeName` instead of `department` for accurate display. Also fixed the family member parsing to use the `members` array format (FATHER/MOTHER types) since the API doesn't use flat `fatherName`/`motherName` fields.

**How we discovered the API structure:** Used Chrome browser automation (Claude-in-Chrome) to log into LAUDEA SIS, then used Angular's `$http` service to make authenticated API calls and logged the full JSON response to console — revealing all available fields.

---

#### 2. Dashboard Consolidation
**What:** Moved Present, Absent, Total, Exempt, and Pending (Not Entered) counts from separate stat cards into the main attendance percentage card.

**Result:** Cleaner dashboard with fewer cards, more breathing room. Stats appear as a compact colored row with a divider below the percentage. Absent and Exempt counts are still tappable for navigation.

---

#### 3. Leave Calculator Redesign
**What:** Replaced the text input field with a slider (0-30 days) and added a hint message "Slide to see how your attendance changes" when at zero.

**Before:** Plain `OutlinedTextField` that looked out of place and required keyboard interaction.
**After:** Interactive `Slider` that instantly shows New Attendance %, Drop %, and Hours Missed in a 3-column layout when dragged.

---

#### 4. GPA Calculator (Major Feature)
**What:** Full SGPA/CGPA calculator with curriculum data for 7 departments across 2 regulations.

**Data Source Challenge:** Needed complete subject/credit data for all 8 semesters across 7 departments and 2 regulations. Downloaded 17 official syllabus PDFs:
- 7 PDFs from Anna University (R2021 regulation) — CSE, EEE, ECE, MECH, CIVIL, AI&DS, CSBS
- 10 PDFs from PSGiTech website (R2025 autonomous regulation) — CSE, EEE, ECE, MECH, CIVIL, AI&DS, ICE, VLSI, + 2 PG programmes

**Extraction Strategy:** Launched 7 parallel AI agents to read PDFs simultaneously — each agent handled 1-2 departments, extracting subject codes, names, credits, and semester mapping. All agents completed within ~3 minutes, returning structured data ready for Kotlin.

**Architecture:**
- `CgpaData.kt` — ~800 lines of curriculum data + grade system + calculation functions
- `CgpaViewModel.kt` — State management for department/semester selection and grade entry
- `CgpaCalculatorScreen.kt` — Full UI with semester tabs, expandable grade pickers, live SGPA/CGPA

**Auto-detection Challenge:** The app initially defaulted to CSE for all users. Fixed by:
1. Storing `programmeName` and `batchYear` in SecurePreferences when biodata is fetched
2. Matching programme name against department patterns (e.g., "BUSINESS SYSTEMS" → CSBS, "ARTIFICIAL INTELLIGENCE" → AI&DS)
3. Using batch year to select regulation (≤2024 → R2021, ≥2025 → R2025)

**Key design decisions:**
- Grade picker expands inline on tap (no dialog) for fast entry
- Color-coded grades: O=green, A+=light green, A=blue, B+=yellow, B=orange, C=red
- SGPA shown in each semester tab for quick overview
- Grade scale reference card at the bottom
- Elective subjects shown with "Elective" tag instead of course code

---

### Struggles & How We Overcame Them

1. **Phone going offline during testing:** The Moto G54 kept going offline via ADB mid-session, requiring USB replug. Worked around it by using Chrome browser automation for API discovery and preparing all code changes before installing.

2. **LAUDEA API token extraction:** Couldn't grab the Keycloak token from localStorage/sessionStorage (it's not stored there). Discovered that Angular's `$http` service has interceptors that auto-attach the Bearer token, so we used `angular.element(document.body).injector().get('$http')` to make authenticated requests directly.

3. **Department vs Programme mismatch:** The SIS API's `department` field is the administrative department (CSE), not the specific programme (CSBS). Had to use `programmeName` field for accurate detection, with careful matching order (check CSBS before CSE to avoid false positives).

4. **ADB touch coordinates:** Tapping UI elements via ADB was unreliable — wrong coordinates would navigate to wrong screens. Solution was to rebuild/relaunch the app and let the user interact directly for testing.

---

## v2.1 Development (2026-03-26)

### Feature: Honours Course Detection in Timetable

**Goal:** Automatically label honours periods in the timetable so students know which classes are part of their honours/minor degree.

**Challenge: No API-level distinction.** The LAUDEA SIS timetable API returns all courses identically — there's no field or flag to distinguish honours courses from regular ones. Honours courses in Anna University R2021 regulations are simply additional professional elective courses (18 extra credits from semesters 5-8) taken from the same verticals.

**How we solved it:**
1. Read all 7 department R2021 regulation PDFs from Anna University to understand the curriculum structure
2. Discovered that PCC (Programme Core) courses have fixed course codes per semester, while PEC (Professional Elective) courses are chosen from vertical pools
3. The standard curriculum defines exactly how many PEC/OEC slots exist per semester (e.g., CSE Sem V = 4 PCC + 2 PEC)
4. Built `detectHonoursCourses()` in CgpaData.kt that compares timetable courses against the standard curriculum:
   - Identifies fixed PCC codes (definitely standard)
   - Counts expected elective slots
   - If there are more non-PCC courses than expected → the surplus are honours
5. The TimetableViewModel reads the student's department (from `programmeName`), semester, and batch year from SecurePreferences, then marks matching sessions with `isHonours = true`
6. TimetableScreen shows an amber "HONOURS" pill badge next to the course code

**Key error faced:** Could not read PDFs directly in the tool (pdftoppm not available on Windows). Solved by using Python's PyPDF2 library to extract text, with UTF-8 encoding workaround for special characters (√ symbol caused charmap codec error on cp1252).

**Design decision:** Honours detection only activates from semester 5 onwards (per R2021 regulations). For semesters 1-4, all courses are standard curriculum — no honours possible.

---

### Feature: Absent Days Descending Order

Simple but important UX fix — absent days list now sorts by date descending (most recent first) so students see their latest absences at the top instead of scrolling to the bottom.

---

### Feature: Holiday Notification Worker

**Goal:** Notify students the evening before a holiday so they know they don't have classes tomorrow.

**Implementation:**
- New `HolidayNotificationWorker` (CoroutineWorker) runs every 6 hours via WorkManager
- Fetches upcoming events from the Google Calendar API (same calendar ID as AcademicCalendarScreen)
- Filters for events with `CalendarEventType.HOLIDAY` that fall on tomorrow's date
- Shows notification with holiday name(s); strips "Holiday - " prefix for cleaner display
- Tracks `last_holiday_notified_date` in SharedPreferences to avoid duplicate notifications
- Tapping the notification navigates to the Academic Calendar screen

**Challenge: Notification deep linking.** The CircularNotificationWorker already used `putExtra("navigate_to", "circulars")` in the intent, but MainActivity never actually read this extra. Fixed by adding intent handling in `AttendanceApp()` — reads `navigate_to` from the activity intent and sets the initial screen accordingly (`"calendar"` → AcademicCalendar, `"circulars"` → Circulars).

---

### Feature: Dashboard & Profile Improvements (Batch 1)

Additional v2.1 features implemented in the same session:

1. **Clickable Absent/Exempt Mini-Tiles** — Floating glass pills with red background for absent, tertiary for exempt
2. **Leave Slider Tap-to-Expand** — Compact card expands to fullscreen dialog with 48sp day counter
3. **GPA Calculator Upgrades** — Elective custom names, auto-fill from semester results, grade persistence
4. **Profile Instant Cache** — Profile picture + academic info cached locally for instant display
5. **Login Typing Animation** — Typewriter effect cycling sample roll numbers with blinking cursor
6. **Circulars Push Notifications** — Background check every 3 hours, notification on new circulars
7. **Enhanced Analytics** — 7 new event types (tile clicks, screen duration, slider, GPA, circular, calendar, profile actions)
8. **Fullscreen Side Eye Dog Meme** — Easter egg meme now opens as fullscreen black overlay

---

### Challenge 12: Google OAuth 100-User Cap on Exam Seat Finder

**Problem:**
The Exam Seat Finder feature used Google Sign-In with `gmail.readonly` scope to search the student's Gmail for seating arrangement emails. After publishing the OAuth consent screen, we discovered Google imposes a **100-user lifetime cap** on unverified apps using restricted scopes. With 1600+ active users, this was a dealbreaker.

**Root Cause:**
All Gmail API scopes (`gmail.readonly`, `gmail.modify`, `gmail.metadata`, `mail.google.com`) are classified as **restricted** by Google. Removing the cap requires CASA (Cloud Application Security Assessment) verification, which costs **$500–$75,000/year** — absurd for a free college app.

**Research:**
Explored every alternative: Google Apps Script as middleman, Gmail add-ons, multiple GCP projects (violates ToS), IMAP with OAuth2 (same restricted scope), Microsoft Graph API (students use Gmail not Outlook), Google Drive API with `drive.file` (sensitive, not restricted — but requires students to save attachments to Drive first).

**Solution:**
Completely replaced Gmail OAuth with Android's **Share Intent + File Picker** approach:
1. Added intent-filter in AndroidManifest for Excel MIME types (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `application/vnd.ms-excel`)
2. Users can share Excel attachments directly from Gmail → "Open with" → AttendanceWidget
3. Or tap "Import Excel File" button → system file picker → select the seating arrangement
4. Apache POI Excel parsing logic (same as before) extracts hall/seat from the file
5. Removed `play-services-auth` dependency entirely — zero Google OAuth, zero user cap

**Also fixed:** R8/ProGuard release build failures — OSGI framework classes (referenced by Log4j), `java.awt.Shape` (referenced by Apache POI's graphbuilder) were missing. Added `-dontwarn` rules for `org.osgi.framework.**`, `org.apache.logging.log4j.**`, and `java.awt.**`.

**Lesson Learned:**
Google's restricted scope verification is designed for enterprise apps, not student projects. For features that only need to read one file from email, a share intent is simpler, faster, has no user cap, and gives users more privacy (the app never accesses their inbox).

---

### Challenge 13: GPA Calculator Wrong Department Detection

**Problem:**
The GPA calculator was showing CSE curriculum for students from other departments (EEE, ECE, etc.). Users reported getting the wrong subject list.

**Root Cause:**
`detectDepartment()` was called with only `securePrefs.programmeName`, which could be **null** if the student hadn't visited the Profile screen yet (that's where `programmeName` gets fetched and cached from the SIS API). When null, it defaulted to CSE.

Additionally, the `department` field from the SIS API uses short names like `"CSE"`, `"ECE"` directly, while `detectDepartment()` only did substring matching on long programme names like `"BTECH COMPUTER SCIENCE AND ENGINEERING"`.

**Solution:**
1. Changed the detection input to `securePrefs.programmeName ?: securePrefs.cachedDepartment` — falls back to the `department` field (which is stored as `programmeName ?? department` from biodata)
2. Added **exact short-name matching** as the first check — handles `"CSE"`, `"ECE"`, `"EEE"`, `"MECH"`, `"CIVIL"`, `"AI&DS"`, `"CSBS"` directly
3. Added `"AI DS"` (space variant) and `"INFORMATION TECHNOLOGY"` → CSE fallback

**Lesson Learned:**
Never assume a cached value will be populated. Always have a fallback chain, and match against all possible formats the API might return.

---

### Challenge 14: Honours Timetable Detection — Three Stacked Bugs

**Problem:**
Honours courses were not being detected at all, or when they were, regular elective courses were falsely marked as "HONOURS".

**Root Cause — Bug 1: Gson NumberFormatException**
The registration API returns `credits: 1.5` (a decimal) for some courses, but `RegistrationLtpc.credits` was declared as `Int`. Gson threw a `NumberFormatException` and the **entire registration response failed to parse** silently. Result: `registeredCourseCodes` was always empty, `markHonoursCourses()` returned early without marking anything. Fixed by changing `credits: Int` to `credits: Double`.

**Root Cause — Bug 2: Placeholder elective codes**
After fixing Bug 1, the registration API returned placeholder codes like `PE64__`, `OE61__` for elective slots instead of the actual elected course codes (like `OCS352`, `CEI331`). These placeholder codes never matched real timetable course codes, so all elective courses were falsely flagged as honours. Fixed by skipping codes containing underscores in `extractRegisteredCourseCodes()`.

**Root Cause — Bug 3: Registration API can't identify elected courses**
Even after removing placeholders, the registration API alone couldn't tell us which elective the student actually chose. The registered set was `[EE3601, EE3602, EE3611, MX3089]` (4 regular courses), but the timetable had 11 courses — the remaining 7 were all marked as honours.

**Solution:**
Combined **two data sources** for enrolled course detection:
- **Registration API** → regular courses with real codes (`EE3601, EE3602, ...`)
- **Attendance API** (present + absent days) → all courses the student has actually attended, including electives with their real codes (`CEI331, OCS353, EE3012, ...`)

Merging both sets gives a complete picture. Honours = timetable codes NOT in either set, minus non-academic slots (LIB, MM).

Also normalized all course code comparisons to uppercase + trimmed to prevent case/whitespace mismatches.

**Lesson Learned:**
When one API gives incomplete data, cross-reference with another. The attendance API is ground truth — if a student has attended a class, that course code is definitely enrolled. Also: always check Gson model types against actual API responses, especially for numeric fields that might be decimals.

---

### Challenge 15: Duplicate Elective Slots in Timetable

**Problem:**
Monday's timetable showed an extra period at 15:55–16:45 — both `OCS352` (IoT Concepts) and `OCS353` (Data Science Fundamentals) were displayed, even though the student only attends one.

**Root Cause:**
The timetable API returns **all elective options** for a time slot, not just the one the student chose. The raw API response contains:
```json
{ "dayKey": "day1", "sessionKey": "session8", "courseCode": "OCS352", "courseTitle": "IOT CONCEPTS AND APPLICATIONS" }
{ "dayKey": "day1", "sessionKey": "session8", "courseCode": "OCS353", "courseTitle": "DATA SCIENCE FUNDAMENTALS" }
```
Both entries have the same day + session, but the API doesn't indicate which one the student is enrolled in.

**Solution:**
New `filterDuplicateSlots()` function runs before honours marking:
1. Groups all sessions in a day by their time slot (`startTime-endTime`)
2. If a time slot has only 1 session → keep as-is
3. If multiple sessions share a time slot → keep only the one(s) whose course code appears in the attendance/registered set
4. Fallback: if none match (e.g. new semester, no attendance data yet), keep all so nothing disappears

This correctly filtered out `OCS352` (not in attendance data) and kept `OCS353` (student has attended it).

**Pipeline order:** Raw timetable → filter duplicate slots → mark honours → display.

**Lesson Learned:**
University timetable APIs often return all possible course options, not personalized schedules. Cross-referencing with attendance data personalizes the timetable to what the student actually attends.

---

### UI Change: Remove Developer Name & Discord from App

**Change:**
Removed developer's name ("Tarunswamy M") and Discord icon from all visible screens:
- **LoginScreen:** "built by Tarunswamy M" → "built with love for PSG iTech"
- **DashboardScreen:** Removed "for features or colabs: Tarunswamy M" + Discord icon → just "for features or colabs"
- **ProfileScreen:** Same removal of name + Discord icon

**Reason:** Privacy preference — with 1600+ users, developer preferred not to have personal info visible in the app UI.

---

### Challenge 16: Wrong SGPA & Department Detection on Other Accounts

**Problem:**
When logging into a friend's account (EEE department), the GPA Calculator showed "Computer Science and Engineering" instead of "Electrical and Electronics Engineering". The Semester Result screen also calculated a wrong SGPA because it was looking up credits from the wrong department's curriculum.

**Root Cause:**
Three compounding issues in the department detection pipeline:

1. **`cachedDepartment` stored the wrong field:** `ProfileScreen.kt` saved `bio.programmeName ?: bio.department` — preferring the long programme name (e.g., "BE ELECTRICAL & ELECTRONICS ENGINEERING") over the API's clean `department` field (e.g., "ELECTRICAL AND ELECTRONICS ENGINEERING"). The order was backwards.

2. **`ResultScreen` ignored `cachedDepartment` entirely:** The SGPA fallback calculation used only `detectDepartment(prefs.programmeName)`. If `programmeName` didn't match any pattern, it silently defaulted to `Department.CSE` — giving completely wrong curriculum credits.

3. **`MainActivity` had the wrong priority:** The GPA Calculator used `detectDepartment(prefs.programmeName ?: prefs.cachedDepartment)` — trying `programmeName` first, making `cachedDepartment` redundant since `programmeName` was almost always non-null.

**Solution:**
Three surgical fixes:

1. **ProfileScreen.kt:** Flipped to `bio.department ?: bio.programmeName` — now `cachedDepartment` stores the API's direct `department` field first (shorter, more reliable for matching).

2. **ResultScreen.kt:** Changed from single-source to fallback chain:
   ```kotlin
   val dept = detectDepartment(prefs.cachedDepartment)
       ?: detectDepartment(prefs.programmeName)
       ?: Department.CSE
   ```

3. **MainActivity.kt:** Same fallback chain — try `cachedDepartment` first, then `programmeName`.

**Verification:**
Tested on an EEE student's account (BARATH, 715523105010):
- GPA Calculator: Now correctly shows "Electrical and Electronics Engineer..." with proper EEE curriculum
- Semester Result: SGPA 7.581 matches across both screens
- All EE course codes (EE3401, EE3402, etc.) correctly identified

**Lesson Learned:**
When an API returns both a short identifier field (`department`) and a long descriptive field (`programmeName`), always prefer the short one for detection/matching — it's more consistent and less prone to format variations. Build fallback chains (try A, then B, then default) instead of relying on a single source for critical calculations like GPA.
