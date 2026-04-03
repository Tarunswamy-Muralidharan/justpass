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

---

### Challenge 17: Login Broke After Adding fetchStudentBiodata() on Startup

**Problem:**
After adding `fetchStudentBiodata()` calls to the startup `LaunchedEffect` and `onLoginSuccess` callback to pre-fetch department data, the app could no longer log in — stuck at "Could not fetch attendance data" after 60 seconds.

**Root Cause:**
Race condition between the login WebView flow and the biodata fetch:
1. `fetchStudentBiodata()` uses `webViewAuthenticator.fetchStudentProfile()` which shares mutable token state (`_cachedAuthToken`) and the global `CookieManager` singleton with the login WebView
2. The IO thread biodata fetch raced with the Main thread login flow, corrupting the Keycloak session
3. This caused the attendance XHR intercept to fail silently

**Solution:**
- Removed ALL `fetchStudentBiodata()` calls from MainActivity (startup + login callback)
- Department detection on startup uses only locally-stored `programmeName` — no network calls
- Moved biodata fetch to `DashboardScreen` LaunchedEffect, which runs safely AFTER login WebView is fully destroyed
- Critical rule: NEVER call WebViewAuthenticator methods during or immediately after login

**Lesson Learned:**
WebView-based authentication flows have shared global state (CookieManager, cached tokens). Never make concurrent network calls that use the same auth infrastructure while login is in progress. Defer data fetching to after the login flow is completely finished and the WebView is destroyed.

---

### Challenge 18: OCR Grade Import — ML Kit Reads Tables Column-by-Column

**Problem:**
Google ML Kit Text Recognition on Anna University grade sheets returned "no grades detected." The OCR output showed course codes, grades, and grade points, but they were in separate blocks — ML Kit reads table columns vertically, not rows horizontally.

**Root Cause:**
The initial parser expected course codes and letter grades on the same line (row-by-row). But ML Kit extracted:
- Column 1: BS3171, CY3151, GE3151... (course codes)
- Column 2: A, B+, A... (letter grades — often misread: "O" missed, "B+" → "B4")
- Column 3: 10, 8, 7, 8... (grade points — read perfectly as numbers)

**Solution:**
Rewrote the parser to use **grade points instead of letter grades** (10=O, 9=A+, 8=A, 7=B+, 6=B, 5=C):
1. Extract all course codes (regex: 2-4 letters + 3-4 digits)
2. Find consecutive runs of valid grade point numbers (10, 9, 8, 7, 6, 5, 0)
3. Match positionally: first code with first grade point, etc.
4. Falls back to line-by-line parsing for well-structured text

**Lesson Learned:**
OCR on structured tables produces column-first output, not row-first. Numbers are far more reliably recognized than single letters. When parsing tabular data from OCR, use the most reliable column (numbers > multi-character strings > single characters).

---

### Challenge 19: Timetable Showing Yesterday as "Today"

**Problem:**
Users reported the timetable screen showing Tuesday as "Today" when it was actually Wednesday. The "Today" badge was stuck on the previous day.

**Root Cause:**
`getTodayDayIndex()` was only called once in the ViewModel's `init {}` block. Android keeps app processes alive across midnight, so if the user opened the app on Tuesday and it stayed in memory overnight, the `todayDayIndex` was permanently set to Tuesday (index 1) instead of updating to Wednesday (index 2).

**Debugging Process:**
1. Took ADB screenshot — confirmed "Today" label was on TUE, device date was WED
2. Ran `adb shell date` — confirmed Wednesday April 1
3. Traced the code: `Calendar.getInstance().get(Calendar.DAY_OF_WEEK)` mapping was correct, but it only ran once during ViewModel initialization

**Solution:**
Added `refreshTodayIndex()` method to `TimetableViewModel` that recomputes the day index and updates both `todayDayIndex` and `selectedDayIndex` if they changed. Called it from the screen's `LaunchedEffect(Unit)` so it runs every time the timetable screen becomes visible.

```kotlin
fun refreshTodayIndex() {
    val todayIndex = getTodayDayIndex()
    if (_uiState.value.todayDayIndex != todayIndex) {
        _uiState.value = _uiState.value.copy(
            selectedDayIndex = todayIndex,
            todayDayIndex = todayIndex
        )
    }
}
```

**Lesson Learned:**
Never compute time-dependent state only in ViewModel `init`. Android processes survive days in the background. Any date/time-based state must be recomputed on screen visibility, not just on creation.

---

### Challenge 20: Exam Seat Finder — Roll Number Not Found in Excel

**Problem:**
Users reported the exam seat finder sometimes failing to find their roll number even though it was clearly present in the Excel sheet. The app showed "Your roll number was not found."

**Root Cause:**
The matching used `String.equals()` (exact match). Excel cells often contain:
- Non-breaking spaces (`\u00A0`) or zero-width spaces (`\u200B`)
- Dots or dashes in roll numbers (e.g., "22Z.301" vs "22Z301")
- Roll numbers embedded in longer strings (e.g., "22Z301 - CSE")
- Invisible Unicode characters from copy-paste

**Solution:**
1. Added `normalizeRollNumber()` that strips all spaces, dots, dashes, and invisible Unicode characters, then uppercases
2. Changed matching from exact `equals` to normalized comparison + `contains` check
3. Added debug logging to show exactly what was matched and where

```kotlin
private fun normalizeRollNumber(raw: String): String {
    return raw.trim().replace(Regex("[\\s.\\-\\u00A0\\u200B]+"), "").uppercase()
}
```

Also added date/time extraction from the filename pattern (e.g., "3 Yr 03-02 FN-II.xlsx" → "2 Mar 2026 · 10:45 AM - 12:30 PM") and a step-by-step usage guide with annotated screenshots.

**Lesson Learned:**
Never use exact string matching on data from external sources (Excel, PDFs, user input). Always normalize first. Excel in particular loves invisible Unicode characters that look identical visually but fail programmatic comparison.

---

### Challenge 21: Custom Animated Bottom Nav Icons with Canvas

**Problem:**
Wanted rich animated icons (house door opening, calendar page flip, calculator sparks, star with comet) in the bottom navigation bar. Material Icons are static — no animation support.

**Design Approach:**
Each icon is drawn entirely with Compose `Canvas` using `drawLine`, `drawRect`, `drawPath`, `drawCircle`, etc. Animations driven by `animateFloatAsState` with springs and tweens.

**Implementation Challenges:**

1. **Home icon chimney smoke**: Needed continuously looping smoke puffs. Used `Animatable` inside a `LaunchedEffect(selected)` with an infinite `while(true)` loop. Three staggered puff circles rise and fade. The loop auto-cancels when `selected` changes to false via coroutine cancellation.

2. **Calendar page flip — 3 pages**: Needed staggered multi-page flip. Used 3 separate `animateFloatAsState` with increasing `delayMillis` (0, 120, 250). Each page draws its own curl shadow, edge highlight, and lift amount at slightly different angles for depth.

3. **Calculator × rotation**: The multiply symbol (×) is two crossed lines. Rotating them required manual trigonometry — computing new endpoints using `cos(angle)` and `sin(angle)` for each line. The rotation is driven by `symbolRotation` going from 0° to 360°.

4. **Star comet orbit**: A comet orbits the star using polar coordinates (angle → x,y on a circle). The trail is 12 circles drawn at decreasing angles behind the comet head, each smaller and more transparent. Used `Animatable` with `tween(800ms)` for the 360° sweep.

5. **GPA calculator settled state looked "blunt"**: Initial version had electric sparks that persisted after animation settled, looking static and unfinished. Fixed by fading sparks out above 95% progress and adding a subtle dark blue "screen on" glow with specular highlight that appears at the end — like a calculator that just powered on.

**Architecture:**
The `LiquidGlassBottomBar` composable in `GlassComponents.kt` uses a `when(index)` block to dispatch to the correct animated icon. Each icon takes `selected: Boolean` and `tint: Color`. The existing bounce scale animation wraps everything, so the custom icons also bounce on tap.

**Lesson Learned:**
Canvas-drawn icons are powerful but verbose (~100-150 lines each). The key to good icon animation is staggered timing — don't animate everything at once. Having distinct phases (entrance → active → settled) makes animations feel alive rather than mechanical. Spring animations with low damping (0.5-0.6) give the best "physical" feel.

---

### Challenge 22: Subject-Wise Syllabus — PDF Extraction at Scale

**Problem:**
Wanted offline syllabus viewing for all 7 departments, both R2021 (Anna University, ~400 pages per PDF) and R2025 (PSGiTech autonomous) regulations. Total: 14 PDFs, ~3000+ pages.

**Approach:**
1. Extract text from all PDFs using PyMuPDF (fitz) — fast (~30 seconds for all 7 R2021 PDFs vs 80+ minutes with pdfplumber)
2. Parse subject headers using regex: `^([A-Z]{2,4}\d{3,4})\s+(.+?)\s+L\s*T\s+P\s+C`
3. Parse curriculum tables (pages 5-10) for semester assignments
4. Bundle as JSON in app assets

**Obstacles:**

1. **Missing semester 1 subjects**: The initial extraction script filtered out "common" prefixes (GE, MA, PH, CY, HS) assuming they were shared across departments. But these ARE part of each department's curriculum and users expect to see them. Fixed by removing the prefix filter and using the curriculum table to determine which subjects belong to each department.

2. **Cross-PDF syllabus lookup**: Some common subjects (English, Math) only have their syllabus text in ONE department's PDF, not all. Built a global syllabus pool from all 7 PDFs first, then for each department, look up missing syllabi from the pool.

3. **R2025 format differences**: R2025 PDFs use bold topic headers instead of "UNIT I/II/III" numbering, and the header regex needed adjustment (`L\s*T` instead of `L\s+T` for EEE PDF where L and T had no space).

4. **Multi-line titles**: Some subject titles span two lines in the PDF. Required post-processing to reassemble broken titles before the "L T P C" marker.

**Result:**
- R2021: 462 subjects across 7 departments (80-112 per dept, semesters 1-8 + electives)
- R2025: 147 subjects across 7 departments (21 per dept, semesters 1-2)
- JSON file: 2.5MB bundled in app assets
- Auto-detects regulation from batch year (≥2025 → R2025, else R2021)

**Lesson Learned:**
PDF text extraction is never clean. Every PDF has quirks — inconsistent spacing, merged columns, broken lines. Build your parser incrementally: start with the most common pattern, then add fallbacks for edge cases. Always verify by printing counts per semester per department.

---

### Challenge 23: Chess Lobby — Real-Time Matchmaking on Free Tier

**Problem:**
1500 app users, many play chess. Wanted in-app matchmaking to find opponents and play together. Requirements: free, anonymous, no accounts needed.

**Architecture Decisions:**

1. **Why Firestore over Realtime Database**: Firestore has better querying (filter challenges by status + user ID), offline support, and the free tier (50k reads/20k writes/day) is more than enough for ~300 DAU.

2. **Why Lichess over Chess.com**: Chess.com has no public API for creating anonymous game links. Lichess has `POST /api/challenge/open` that returns two URLs (white + black) — no authentication, no accounts needed, completely free.

3. **Anonymous identity**: Roll numbers are hashed to generate stable player IDs (`p_${abs(hashCode).toString(16)}`) and display names from a pool of 50 chess-themed names + numeric suffix (e.g., "SilentKnight#42"). Same user always gets the same anonymous name.

**Implementation:**

1. **Presence system**: Write to `chess_online/{playerId}` on screen enter, delete on leave. Heartbeat every 30 seconds updates timestamp. Stale entries (>60s old) filtered client-side. `DisposableEffect` in Compose handles cleanup.

2. **Challenge flow**: Sender writes to `chess_challenges` → receiver listens with `whereEqualTo("toId", myId).whereEqualTo("status", "pending")` → accept triggers Lichess API → both URLs written back → sender's listener picks up the accepted status → both phones auto-open Lichess.

3. **Lichess integration**: Simple HTTP POST to `lichess.org/api/challenge/open` with `clock.limit=600&clock.increment=0&rated=false` (10+0 casual). Response parsed with regex for the challenge URL.

**Firestore Setup:**
Enabled Firestore in Firebase Console via browser automation (Comet browser):
- Standard edition, asia-south1 (Mumbai) region
- Test mode security rules (allow all reads/writes for 30 days — needs proper rules before release)

**Lesson Learned:**
Real-time features don't require expensive infrastructure. Firestore's free tier + Lichess's free API = zero cost for a full matchmaking system. The key insight was reframing the problem: instead of building a chess game, just build the lobby and hand off to an existing platform.

---

### Challenge 24: 5-Tab Bottom Navigation — Fitting Without Clutter

**Problem:**
Added GPA Calculator as a 5th tab in the center of the bottom nav (Home | Timetable | GPA | CA Marks | Profile). Concerned about cramped layout on smaller screens.

**Implementation:**
The `LiquidGlassBottomBar` already used `Arrangement.SpaceAround` with weighted tabs, so adding a 5th tab just made each slot narrower. The sliding glass bubble indicator auto-calculated its width from `tabWidth = maxWidth / tabs.size`.

**Index Shift Impact:**
Adding GPA at index 2 shifted CA Marks to index 3 and Profile to index 4. This required updating:
- Tab selection mapping in `onTabSelected` callback
- All `selectedTabIndex = N` assignments in back handlers
- The Crossfade `"tab_N"` route strings
- The animated icon `when(index)` dispatch

The CgpaCalculator screen was already in the special screens list (routed by `Screen.CgpaCalculator.name`), so it worked from both the dashboard tile AND the bottom nav without duplication.

**Result:**
5 tabs fit cleanly on both the Motorola Edge 60 Fusion (1220px wide) and Moto G54 (smaller screen). Each tab has its own custom animated icon, maintaining visual distinction despite the tighter spacing.

**Lesson Learned:**
When adding navigation items, audit ALL places that reference tab indices — they're hardcoded integers, not enums. A single missed index shift causes silent navigation bugs. Consider using named constants for tab indices in the future.

---

### Challenge 25: Animated Isometric Chess Icon — Canvas Drawing From Reference Image

**Problem:**
The center bump bar tab for Chess needed a custom animated icon — not a static vector, but a colorful 3D chessboard with pieces moving around it. User provided reference images of what they wanted: a colorful chess board from a clipart, and a 3D isometric rendered chessboard viewed from a low front angle.

**Approach:**
Built entirely in Compose `Canvas` — no images, no vectors, pure draw calls. True isometric projection with parallelogram-shaped diamond tiles.

**Implementation:**

1. **Isometric projection**: Each grid cell (row, col) maps to screen coordinates via `isoPos(row, col) = (centerX + (col - row) * tileHW, centerY + (col + row) * tileHH)`. Four corners of each tile form a diamond shape.

2. **3D tile depth**: Each tile has three visible faces — top (diamond), right side, and front side — each with different shading (top = bright, right = medium, front = slightly lighter). Tiny gaps between tiles so they read as individual raised blocks.

3. **Ultra glossy effects**: Specular highlight (bright circle near top vertex), gradient shine band (top half brighter), bottom darkening, bright edge highlights along top-left edges, dark edges along bottom-right, and a glossy strip on side faces.

4. **Thick platform base**: Isometric box underneath with top surface, front-left face (medium dark), and front-right face (darkest shadow). Padding around tiles so the base is slightly larger.

5. **Color animation**: All tile colors, side colors, and base colors are `animateColorAsState` — default is dark slate/silver, selected smoothly transitions to chess.com green (#739552) + cream (#EBECD0).

6. **Random piece movement**: White knight and dark bishop each run in independent `LaunchedEffect` coroutine loops. Each loop: wait random delay (800-1600ms) → pick a random valid move (knight L-shapes, bishop diagonals on 4x4 grid) → animate X and Y with `Animatable.animateTo()` using random duration (400-900ms) and `FastOutSlowInEasing`. Both axes animate in parallel via `coroutineScope { launch { ... } launch { ... } }`.

7. **Calligraphy label**: "Chess" rendered in Great Vibes font (downloaded from Google Fonts repo), 16sp bold, turns green (#4CAF50) when selected.

8. **Levitation**: Entire icon + label floats up/down 3dp in infinite loop (1.8s cycle, `FastOutSlowInEasing`, `RepeatMode.Reverse`).

**Obstacles (9 iterations!):**

1. **Flat rectangles**: First attempt drew a 3x3 grid of flat `drawRect` squares with lavender/purple colors. User said "it looks horrible" — it looked nothing like a 3D chessboard.

2. **Fake 3D with side panels**: Second attempt added bottom and right "side face" rectangles for depth, chess.com green/cream colors, and rectangular tiles. Better but still flat — no isometric perspective.

3. **True isometric projection**: Third attempt used proper isometric math — `isoPos(row, col)` transforms grid coordinates to diamond-shaped screen positions. Each tile is a `Path` with 4 diamond vertices. Added individual right and front side faces per tile. This finally matched the reference image.

4. **Missing glossy look**: User wanted "ultra glossy". Added specular hotspot circles, gradient shine bands (top half highlight, bottom half darken), bright/dark edge lines, side face highlight strips.

5. **Size iterations**: Started at 36dp → 52dp → 62dp → 78dp → settled at 70dp. Glass bubble expanded from 62dp to 88dp to cover the board + calligraphy text.

6. **Bouncing pieces**: Initial piece movement used sine-wave hop arcs. User said they look like they're bouncing, wanted sliding. Removed all `hopArc()` functions — pieces now slide flat along the board surface.

7. **Loop stutter**: Used `infiniteRepeatable` with a fixed 6-move path that visibly restarted every 7.2 seconds. User noticed the stutter. Replaced with independent random `LaunchedEffect` coroutines — each piece picks its next valid move randomly, so the pattern never repeats.

8. **Distracting sparkles**: Had orbiting 4-point star sparkles around the board. User said "there is small star kinda thingy really small circling above the chess icon, remove that". Removed all sparkle code.

9. **Green glow circle**: Had a `drawCircle` glow effect behind the board when selected. User said "some weird green circle is popping up". Removed it.

10. **Calligraphy clipping**: The "Chess" text bottom was getting cut off by the nav bar. Fixed by pushing the entire Column up with `-12dp` offset and tuning font size from 16→14→16sp.

**Lesson Learned:**
Building UI from a reference image is deeply iterative. The user sees things you don't — a "flat" board, a "weird" glow, "bouncing" instead of "sliding". Each iteration taught me to look at the output more critically. True isometric projection math is straightforward but the visual polish (glossy effects, edge highlights) is what makes it look real. Random coroutine-based animation feels much more alive than fixed loops.

---

### Challenge 26: App Rebrand — "Laudea Attendance" to "JustPass"

**Problem:**
The name "Laudea Attendance" was generic and forgettable. User wanted a rebrand to "JustPass" — meme-worthy student survival branding ("Bro enakku topper venam… just pass podhum da").

**Implementation:**
Renamed across 7 files, 12+ locations:
- `strings.xml`: app_name, widget_name
- `LoginScreen.kt`: title text (32sp bold)
- `DashboardScreen.kt`: fallback header text
- `ProfileScreen.kt`: app info, share text (2 locations), APK filename
- `ExamSeatScreen.kt`: guide text "Open with JustPass"
- `PrivacyPolicyScreen.kt`: app reference in policy text

**Logo:**
Adaptive icon (`ic_launcher_foreground.xml` + `ic_launcher_background.xml`):
- Dark navy background (#1A1A2E)
- Tilted graduation cap (dark gray diamond + brim shadow)
- Gold tassel with string path, dangling threads, and button
- Big green checkmark (#4CD964) slashing across — the "barely made it" energy
- Brighter inner highlight (#5CE76F) on the checkmark
- Small blue sweat drop (#64B5F6) near the cap for comedic effect

**Lesson Learned:**
Rebranding is mostly grep + replace, but easy to miss locations like share text fallbacks, privacy policy references, and APK filenames. The logo needed to convey the app's personality in a tiny icon — the graduation cap + checkmark + sweat drop combo works because each element tells part of the "just barely passed" story.

---

### Challenge 27: Chess Time Controls — Bullet to Classical

**Problem:**
All chess games were hardcoded to 10+0 (Rapid). Users wanted chess.com-style time control selection — Bullet, Blitz, Rapid, Classical.

**Implementation:**

1. **TimeControl enum** in `ChessData.kt` with 10 presets, each carrying `clockLimit` (seconds), `increment` (seconds), `description` (e.g., "5+3"), `icon` (emoji), and `paramString` property that builds Lichess API params.

2. **Time control picker dialog**: When tapping "Play", a `TimeControlDialog` pops up with buttons grouped by category (Bullet/Blitz/Rapid/Classical). Each button shows icon + time description.

3. **Data flow**: Selected `TimeControl.name.lowercase()` → stored in Firestore challenge doc as `timeControl` field → read when opponent receives challenge → shown on incoming challenge popup (e.g., "⚡ 1 min Bullet") → passed to `createLichessGame()` which uses `tc.paramString` for `POST /api/challenge/open`.

4. **Backward compatible**: Default `"rapid_10"` for challenges without the field (old clients).

**Bug discovered during testing — Match results not showing:**

The `listenChallengeStatus()` function was constructing `ChessChallenge` objects from Firestore documents but was NOT reading `fromColor`, `resultChecked`, or `timeControl` fields. When `checkPendingResults()` ran, challenges had empty `fromColor` — the `ifBlank { "white" }` fallback should have worked, but the incomplete data propagation meant accepted challenges weren't being processed correctly.

Fixed both `listenChallengeStatus` and `listenIncomingChallenges` to read ALL fields from Firestore documents.

Also added:
- 90-second auto-expiry for unanswered challenges ("X didn't respond")
- Periodic 30s result polling while on chess screen
- Periodic 60s challenge cleanup
- 3-second delayed result check after returning from Lichess

**Lesson Learned:**
When you add a field to a data model, audit EVERY place that constructs that model from external data (Firestore, API responses, etc.). The compiler won't catch missing fields if they have defaults — the object just silently gets empty/default values. This is especially dangerous with Firestore where you manually map document fields.

---

### Challenge 28: In-App Lichess via WebView — Eliminating the Browser Switch

**Problem:**
When a chess challenge was accepted, the app opened Chrome to play on Lichess. This broke the user flow — switching apps, losing context, no guarantee they'd come back for result tracking.

**Research:**
Studied the Lichess API extensively:
- **Board API** (`/api/board/game/{id}/move/{move}`, `/api/board/game/stream/{id}`): Allows fully native play but requires OAuth2 with `board:play` scope for BOTH players. This means every user needs a Lichess account — massive friction for college students.
- **Spectator streaming** (`/api/stream/game/{id}`): No auth needed but has 3-move delay. Good for watching, not playing.
- **Game export** (`/game/export/{gameId}`): No auth, returns PGN, opening name, clock times, accuracy. Useful for post-game summary.
- **Embed options**: Lichess has `/tv/frame` for spectating but no game-specific embed URL.
- **Libraries**: No official Android SDK. `chariot` (Java) requires Java 21+ (Android incompatible). `chessground` is web-only TypeScript.

**Decision: WebView wins.**
- `/api/challenge/open` already creates anonymous games (no auth)
- Lichess mobile web is lightweight SVG-based (`chessground` library, 10KB gzipped)
- Full interactive board — drag-drop pieces, clock, resign, draw offer, chat
- Users never leave JustPass
- Zero signup required

**Implementation:**

1. **State management**: `activeGameUrl` state in `ChessScreen`. When challenge accepted, sets URL instead of launching Chrome intent. When set, lobby is hidden and `LichessGameScreen` composable renders.

2. **WebView setup**: `AndroidView` wrapping a `WebView` with JavaScript enabled, DOM storage, WebSocket support (for real-time moves), wide viewport.

3. **CSS injection**: JavaScript executed `onPageFinished` that creates a `<style>` element hiding Lichess chrome:
   ```css
   header, .site-title, .site-buttons, .mchat, footer { display: none !important; }
   .round__app { padding-top: 0 !important; }
   ```
   Re-injected at 1.5s and 4s delays because Lichess loads content dynamically via JavaScript.

4. **Loading UX**: Dark overlay (#1A1A2E) with `CircularProgressIndicator` + "Loading game..." text. Uses `AnimatedVisibility` with fade to smoothly disappear when `onPageFinished` fires.

5. **Navigation controls**:
   - Close button (top-left, semi-transparent black circle) — returns to lobby + triggers `checkPendingResults()`
   - Open in browser button (top-right) — fallback if WebView has issues
   - `BackHandler` composable intercepts Android back button

6. **URL safety**: `shouldOverrideUrlLoading` keeps `lichess.org` URLs in the WebView but opens anything else (ads, external links) in the system browser.

7. **User agent**: Appended `JustPass-Chess` to the default user agent string so Lichess can identify the traffic source.

**Result:**
Games now play entirely inside JustPass. The Lichess board renders cleanly — pieces are draggable, clock ticks in real-time, resign/draw/takeback buttons work. When the game ends, user taps close (or back) → returns to the lobby → results auto-detected from Lichess API within seconds.

**Lesson Learned:**
Before building a complex native solution, check if the target service has a good mobile web experience. Lichess's web UI is essentially their Android app's UI (the official Lichess app is also a WebView wrapper). CSS injection to hide site chrome is a powerful technique — 5 lines of CSS replaced months of native chess board development. The key trade-off: you depend on Lichess not changing their HTML structure, but the time savings are enormous.

---

### Challenge 29: Dark Mode Header Visibility

**Problem:**
Multiple screen headers (Chess Lobby, Syllabus, Privacy Policy) and dialog titles were using default text color (black) which was invisible against the dark glass backgrounds in dark mode.

**Root Cause:**
Compose `Text` defaults to `Color.Unspecified` which resolves to `LocalContentColor` → typically black in many contexts. Headers inside `GlassListCard` and dialogs with `containerColor = Color(0xFF1E2A3A)` inherited this wrong default.

**Fix:**
- Screen headers inside glass cards: `color = MaterialTheme.colorScheme.onSurface` (adapts to dark/light theme)
- Dialog titles/options with dark `containerColor`: explicit `color = Color.White`
- Fixed across: ChessScreen (7 texts), SyllabusScreen (4 texts), PrivacyPolicyScreen (1 text)

**Lesson Learned:**
Never rely on default text colors in custom-themed containers. Compose's color resolution chain (`LocalContentColor` → `ContentColorFor` → theme) doesn't always match your visual expectation, especially inside glass/translucent layouts. Always specify `color` explicitly for text that must be readable in both themes.

---

### Challenge 30: Profile Picture Discoverability

**Problem:**
Users didn't realize the profile picture in the dashboard header was tappable to view their profile. It looked like a static avatar.

**Implementation:**
1. **Pulsing double-ripple ring**: Two concentric rings expand outward from the profile pic in an infinite loop (2s cycle). Ring 1: 0.9→0 alpha, 1x→1.6x scale. Ring 2: staggered behind, semi-transparent.
2. **Visual affordance**: Added a subtle 1.5dp primary-color border ring on the pic itself, making it look intentionally tappable.
3. **Centering fix**: Wrapped in a 52dp `Box(contentAlignment = Center)` instead of a `Column` that was shifting alignment.

Also added:
- **"Report Bug / Feature Request"** in ProfileScreen → opens GitHub Issues
- **Collapsible "How does Chess Lobby work?"** info card with 7 sections

**Lesson Learned:**
Invisible interaction targets are a common UX failure. Animation draws the eye and communicates "this is interactive" without text labels. The double-ripple pattern (like a radar ping) is particularly effective because it suggests "something is here, tap to discover."

---

### Challenge 31: In-App Lichess WebView — The Flickering Saga

**Problem:**
When a chess challenge was accepted, the app opened Chrome to play on Lichess. Users had to switch apps, losing context. Wanted to play entirely within JustPass.

**Research (Lichess API deep dive):**
Studied the Lichess open-source project extensively:
- **Board API** (`/api/board/game/{id}/move/{move}`): Allows fully native play but requires OAuth2 with `board:play` scope for BOTH players — massive friction for college students who don't have Lichess accounts.
- **Spectator streaming** (`/api/stream/game/{id}`): No auth, but 3-move delay. Good for watching, not playing.
- **Game export** (`/game/export/{gameId}`): No auth, returns PGN, opening name, clock times, accuracy.
- **Embed options**: Lichess has `/tv/frame` for spectating but no game-specific embed.
- **Libraries**: No official Android SDK. `chariot` (Java) requires Java 21+ (Android incompatible).

**Decision:** WebView wins — `/api/challenge/open` already creates anonymous games (no auth), Lichess mobile web is lightweight SVG-based (`chessground` library, 10KB gzipped), and users never leave JustPass.

**Implementation Attempts & Obstacles:**

**Attempt 1 — Box overlay stacking (FAILED):**
```
Box {
    WebView          ← bottom layer
    LoadingOverlay   ← stacked ON TOP
    ButtonsRow       ← stacked ON TOP
}
```
Result: **Severe flickering.** Compose had to composite 3 layers every frame. The overlay and buttons were fighting with the WebView's GPU rendering pipeline. Even when the `AnimatedVisibility` loading overlay was invisible, it remained in the composition tree and intercepted both rendering and touch events.

**Attempt 2 — CSS injection via MutationObserver (FAILED):**
Injected JavaScript on `onPageStarted` that created a `<style>` tag hiding Lichess chrome (header, footer, chat) and a `MutationObserver` to enforce hiding on every DOM change. Result: **Even worse flickering.** The MutationObserver fired on every single DOM mutation — clock ticks (every second), move animations, Lichess SPA updates. Each firing ran `querySelector` + set `style.display`, causing constant layout reflows.

**Attempt 3 — CSS injection only on onPageFinished (FAILED):**
Removed MutationObserver, injected CSS only once in `onPageFinished`. Also injected in `onPageStarted` for early hiding. Result: **Still flickering** because `onPageFinished` fires for every sub-resource load in SPAs, and `onPageStarted` injection while the page is still loading forces a reparse.

**Attempt 4 — Remove all CSS injection (PARTIAL SUCCESS):**
Stripped all JavaScript injection. Let Lichess render naturally with its header visible. The flickering stopped because there was no JS fighting with the DOM. But the Lichess header (SIGN IN, REGISTER, hamburger menu) was visible, which looked non-native.

**Attempt 5 — Column layout + single CSS injection (SUCCESS):**
The root cause was finally identified: **Box overlay stacking, not CSS injection.** Changed the layout from `Box` (everything overlaid) to `Column` (buttons Row at top, WebView below taking remaining space). Nothing overlays the WebView. Added `setLayerType(LAYER_TYPE_HARDWARE)` for explicit GPU rendering.

Then added back CSS injection — but only once in `onPageFinished`, with idempotency check (`if(document.getElementById('jp'))return`), and NO `onPageStarted` injection, NO `MutationObserver`. Just a static `<style>` tag.

Result: **Zero flickering + Lichess header hidden.** The CSS `!important` rules persist without needing JS to re-enforce them. The `Column` layout means Compose doesn't fight the WebView for GPU rendering.

**Touch event bug discovered during testing:**
ADB `input tap` commands couldn't reach the WebView to tap "JOIN THE GAME". The `AnimatedVisibility` overlay with `Modifier.fillMaxSize()` stayed in the composition tree even when invisible and **blocked touch events**. Fixed by replacing `AnimatedVisibility` with a simple `if(isLoading)` that completely removes the overlay from the tree.

**Exit confirmation dialog:**
Added `BackHandler` and close button that both show "Leave game? If the game is still ongoing, you will lose on abandonment." with red Leave button and "Keep playing" dismiss.

**Game-end detection:**
Injected a JS polling script (`setInterval` every 2s) that checks for Lichess's game-over DOM elements (`.result-wrap .status`). When detected, calls `JustPass.onGameEnd(result)` via `@JavascriptInterface`. The result text (e.g., "White wins", "Draw") is passed back to Compose, which shows a Game Over dialog with the result color-coded + "Play Again" / "Back to Lobby" buttons.

**Lesson Learned:**
Never overlay Compose elements on top of `AndroidView` WebViews. The rendering pipelines fight each other — Compose uses Skia/RenderNode, WebView uses its own Chromium compositor. Stacking them in a `Box` forces Compose to composite both every frame, causing GPU contention and visible flicker. Use `Column`/`Row` to keep them side-by-side instead. CSS injection in WebViews should be minimal (one static `<style>` tag) and never use `MutationObserver` on real-time content.

---

### Challenge 32: Firestore Composite Index — Missing Index Blocking Match Results

**Problem:**
After playing chess games, match results weren't appearing in history and leaderboard wasn't updating. Users could play but results vanished.

**Root Cause (found via logcat):**
```
FAILED_PRECONDITION: The query requires an index.
```
The `getRecentGames()` function queried `chess_challenges` with `whereEqualTo("fromId") + whereEqualTo("status") + orderBy("timestamp", DESCENDING)`. Firestore requires a composite index for queries combining equality filters with ordering on different fields. Two queries needed indexes:
1. `fromId` + `status` + `timestamp` (for games where player was the challenger)
2. `toId` + `status` + `timestamp` (for games where player was the acceptor)

**Fix — Index 1 (fromId):**
Firestore helpfully includes a direct URL to create the missing index in the error message. Opened the URL in Firebase Console via Chrome browser automation — navigated to the page, found the "Save" button in the "Create or update indexes" dialog, clicked it. Index built instantly (status went from "Building..." to "Enabled" in under a minute).

**Fix — Index 2 (toId) — the struggle:**
Tried to create the second index via:
1. **Firebase Console UI**: The "Add index" form had a text field that wouldn't accept "toId" — it kept showing "told" due to a React state sync issue with the input. Tried triple-click select, Home+Shift+End, Ctrl+A — the form wouldn't clear properly.
2. **JavaScript injection**: Set the input value via `nativeInputValueSetter` + dispatched `input`/`change` events. The JS returned "toId" but the React state still showed "told".
3. **Direct URL with proto-encoded params**: Generated a base64-encoded protobuf matching Firestore's URL format using Python. The URL loaded the Indexes page but the create dialog didn't auto-open.
4. **gcloud CLI**: `gcloud firestore indexes composite create` — not installed on the machine.

**Final solution:** Removed the `orderBy("timestamp")` clause from the `toId` query entirely. This means the query only uses `whereEqualTo("toId") + whereEqualTo("status")` which doesn't need a composite index (Firestore auto-indexes individual fields). Results are still sorted client-side via `sortedByDescending { it.timestamp }` after merging both queries.

**Lesson Learned:**
Firestore composite indexes are silently required — your app compiles fine, Firestore doesn't throw at write time, and the error only appears at query time buried in logcat. Always check logcat for `FAILED_PRECONDITION` after adding new compound queries. When possible, design queries to avoid composite indexes by dropping `orderBy` and sorting client-side — one fewer infrastructure dependency.

---

### Challenge 33: Friends List & Game Analysis

**Problem:**
Friends were only visible as badges on online players. No way to see offline friends. Match history showed results but no way to review the actual game.

**Implementation:**

1. **Friends Dialog**: New `FriendsDialog` composable showing all friends (online + offline) with:
   - Green/gray online indicator
   - "Online now" or "Last seen X ago" status
   - SR rating + W/L/D stats
   - Added `getFriendProfiles()` to ChessRepository that fetches full profiles from Firestore (handles `whereIn` 30-item limit via chunking)
   - Purple person icon in header bar opens the dialog

2. **Analyze Button in Match History**: Each match entry now has an "Analyze" button that opens `https://lichess.org/{gameId}` in the same in-app WebView. This gives access to Lichess's full post-game analysis — move list, opening name, blunders, engine evaluation — all without any API auth.

3. **Game Result Dialog with Play Again**: When a game ends (detected via JS polling), auto-closes WebView after 1.5s and shows:
   - "Game Over" dialog with result text color-coded (green=win, red=loss, yellow=draw)
   - "Play Again" button → re-opens time control picker for the same opponent
   - "Back to Lobby" button → returns to player list
   - Tracks `lastChallengedPlayer` state across the challenge flow

**Lesson Learned:**
Lichess's game URLs (`lichess.org/{gameId}`) double as analysis boards after the game ends — the same URL that was used for playing automatically shows the analysis interface post-game. No separate API endpoint needed. This is an elegant design choice by Lichess that makes integration trivial.

---

### Challenge 34: Installing Two Copies of the Same App for Chess Testing

**Problem:**
Chess matchmaking requires two players online simultaneously. Only had one test phone (Moto G54). Needed two separate instances of JustPass, each logged into different student accounts, to test the full challenge → accept → play → result flow.

**Approach:**
Android identifies apps by `applicationId`. Two apps with different `applicationId` values can coexist on the same device. Changed the `applicationId` in `build.gradle.kts` from `com.example.attendancewidgetlaudea` to `com.example.attendancewidgetlaudea2` and installed a second copy.

**Obstacle 1 — Firebase google-services.json:**
First build with the new `applicationId` failed immediately:
```
No matching client found for package name 'com.example.attendancewidgetlaudea2'
in google-services.json
```
The `google-services.json` file maps package names to Firebase config. It only had an entry for the original package. Fix: Added a second `client` block to `google-services.json` with `package_name: "com.example.attendancewidgetlaudea2"` using the same Firebase project credentials (same `mobilesdk_app_id`, same `api_key`). Both apps share the same Firestore database, which is exactly what we want for testing matchmaking.

**Obstacle 2 — Activity class path:**
Tried launching JustPass 2 via ADB:
```
adb shell am start -n com.example.attendancewidgetlaudea2/com.example.attendancewidgetlaudea2.MainActivity
```
Error: `Activity class does not exist`. The `applicationId` changed but the `namespace` in `build.gradle.kts` stayed as `com.example.attendancewidgetlaudea`. In Android, `applicationId` is the package for installation/identity, but `namespace` determines the actual Java/Kotlin package where classes live. The correct launch command was:
```
adb shell am start -n com.example.attendancewidgetlaudea2/com.example.attendancewidgetlaudea.MainActivity
```
Note: `applicationId` (left of `/`) differs from `namespace` (right of `/`).

**Obstacle 3 — App name collision:**
Both apps showed "JustPass" in the launcher, making them indistinguishable. Changed `strings.xml` `app_name` to "JustPass 1" and "JustPass 2" respectively for each build.

**Obstacle 4 — Old launcher icon persisting:**
After the rebrand to JustPass, the old dot-matrix checkmark icon was still showing instead of the new graduation cap + checkmark. Root cause: **Old `.webp` raster icons** in `mipmap-hdpi/xhdpi/xxhdpi/xxxhdpi` folders were taking priority over the `mipmap-anydpi-v26` adaptive icon XML. Android launchers prefer density-specific raster icons over adaptive icons on many devices. Fix: Deleted all 10 `.webp` files (`ic_launcher.webp` + `ic_launcher_round.webp` × 5 densities), leaving only the adaptive icon XML + vector foreground/background. Fresh install after uninstall showed the correct new logo.

**Obstacle 5 — Credentials lost on uninstall:**
Each uninstall/reinstall cycle wiped `EncryptedSharedPreferences` (login credentials, cached tokens). Had to re-login on both apps after every rebuild cycle. This became the biggest time sink — each login requires WebView-based Keycloak SSO flow (~15 seconds). Over the testing session, re-logged approximately 8 times across both apps.

**Build cycle automation:**
Developed a workflow for dual-app testing:
1. Build and install JustPass 1 (original `applicationId`)
2. Change `applicationId` + `app_name` + `google-services.json`
3. Build and install JustPass 2
4. Revert all 3 files back to original
5. Test on phone

This 5-step cycle was repeated ~6 times during the WebView flickering debugging session. Each cycle took ~40 seconds (build + install × 2).

**ADB testing capabilities discovered:**
- `adb shell input tap X Y` — works for native Compose UI but NOT for WebView content inside `AndroidView` (touch events don't dispatch through the Compose layer)
- `adb shell uiautomator dump` — can find native UI element bounds but can't see inside WebViews
- `adb exec-out screencap -p > file.png` — screenshots work perfectly for visual verification
- `adb shell am start -n package/activity` — app switching works, but background apps lose Compose state (Chess screen resets to dashboard)

**Lesson Learned:**
Testing real-time multiplayer features on a single device is inherently painful but possible. The key insight: `applicationId` ≠ `namespace` in Android — changing one doesn't change the other, and both matter for different things (installation identity vs class resolution). Keep a mental note of every file that references the package name: `build.gradle.kts`, `google-services.json`, `strings.xml`, and any ADB commands. Automate the swap if you'll do it more than twice.
