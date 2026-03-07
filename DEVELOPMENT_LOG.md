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
| Auto re-login on token expiry | Users never see login screen again after initial setup |
| Flattened LazyColumn list | Prevents jitter from nested forEach inside items |
| Surface/Box instead of Card | Cards render shadows which are expensive in lists |
| Pre-computed display strings | Avoid SimpleDateFormat and string operations during composition |
| EncryptedSharedPreferences | Credentials stored securely on device |
| WorkManager for background refresh | Survives app kills, respects battery optimization |

---

## Architecture Overview

```
Login Flow:
  WebView → Keycloak SSO → XHR Intercept → Capture Token + Data → Cache Token

Refresh Flow (Fast):
  Cached Token → Direct HTTP → 200ms response

Refresh Flow (Fallback):
  Token Expired → WebView Session → XHR Intercept → New Token

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

---

## Resources That Helped

- Android WebView documentation — understanding JavascriptInterface and page lifecycle
- XMLHttpRequest MDN docs — for writing the XHR intercept hooks
- Jetpack Compose performance guide — understanding debug vs release behavior
- R8/ProGuard documentation — keeping annotation-based APIs alive

---

## Final Reflections

**What went well:**
- The XHR interception approach is elegant and reliable
- Cached token refresh gives near-instant data updates
- Auto re-login means zero friction for users
- The app provides a much better mobile experience than the website

**What could have been better:**
- Should have used git from the start to avoid losing working code
- Should have tested release builds earlier for performance
- Should have copied API field names directly instead of typing them

**Skills gained:**
- WebView JavaScript interception and bridge communication
- Keycloak/SSO authentication flows
- Android ProGuard/R8 configuration
- Jetpack Compose performance optimization
- Glance widget development
