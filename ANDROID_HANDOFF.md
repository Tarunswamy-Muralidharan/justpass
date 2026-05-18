# JustPass Android — Project Handoff

A complete project context dump for the JustPass Android app (PSG iTech LAUDEA SIS companion). Hand this file (alongside `PWA_HANDOFF.md`) to a new agent to bootstrap full context. Last updated 2026-05-18.

---

## 1. Project at a glance

| Field | Value |
|-------|-------|
| **App name** | JustPass (rebranded 2026-04-03 from "Laudea Attendance") |
| **Package** | `com.justpass.app` |
| **Repo** | `https://github.com/Tarunswamy-Muralidharan/-AttendanceWidgetLaudea` (note dash prefix in name) |
| **Repo visibility** | PUBLIC since 2026-05-15 (was private since 2026-04-25; flipped so old sideloaded users' UpdateChecker could ping). |
| **Default branch** | `main` |
| **Developer** | Tarunswamy Muralidharan (`tmswamy10@gmail.com`) |
| **Play Store** | Live since 2026-05-15 at `https://play.google.com/store/apps/details?id=com.justpass.app` |
| **Current version** | `3.0.3` (versionCode 12), full Production rollout to 176 countries |
| **Local path** | `C:\Users\tmswa\AndroidStudioProjects\AttendanceWidgetLaudea` |
| **Sister project (PWA)** | `C:\Users\tmswa\WebProjects\AttendanceWidgetLaudea-Web` → `justpass-eta.vercel.app`. See `PWA_HANDOFF.md`. |
| **Backend Worker** | Same `chess-lobby` Cloudflare Worker at `https://chess-lobby.tmswamy10.workers.dev` serves chess + privacy policy + class marks routes. Source in `chess-lobby/` subdirectory of THIS repo. |
| **OS target** | minSdk 26, compileSdk 36, targetSdk 36 |
| **Language** | Kotlin 2.3.0 |
| **UI toolkit** | Jetpack Compose (BOM 2025.10.00) |

### Pre-Play-Store history

| Version | Date | Released to | Notes |
|---|---|---|---|
| 1.0 | 2026-03-07 | Internal | Initial release |
| 1.1 | 2026-03-18 | Internal | Firebase Analytics + 3-tier refresh |
| 1.2 | 2026-03-18 | Internal | Instant refresh + update notifications |
| 2.0 | 2026-03-20 | Friends (sideloaded as `com.example.attendancewidgetlaudea`) | Glass UI + many features |
| 2.0.1 | 2026-03-20 | Friends | Hotfix: timetable nodeId for non-CSE |
| 2.1 | 2026-04-01 (committed) | NOT released | Big feature batch (chess, calendar, syllabus etc.) |
| 2.2.1 | 2026-04-22 | Closed testing on `com.justpass.app` | Approved on Play |
| 3.0 | 2026-04-24 | Closed testing | CF DO chess v2 |
| 3.0.3 | 2026-05-15 | **Public Play Store** | Privacy URL fix + 176 countries |

### Identity / dev info

| Item | Value |
|---|---|
| ADB path | `/c/Users/tmswa/AppData/Local/Android/Sdk/platform-tools/adb.exe` |
| Primary test device | `ZN4224BRCG` (Motorola Edge 60 Fusion, Android 16) |
| Secondary | `ZD222GJ6WD` (Moto G54, API 33) — currently connected |
| Emulator | `emulator-5554` (Pixel_5 AVD, `-scale 0.4` for compactness) |
| JAVA_HOME for builds | `/c/Program Files/Android/Android Studio/jbr` |
| GitHub CLI | Authenticated as `Tarunswamy-Muralidharan` |
| Tester opt-in link (legacy) | `https://play.google.com/apps/testing/com.justpass.app` |
| Old sideloaded APK | `C:\Users\tmswa\Desktop\AttendanceWidget-v2.0.1.apk` |

---

## 2. Tech stack

### Core

- Kotlin 2.3.0, Compose Material3
- Liquid Glass UI via `io.github.fletchmckee.liquid:liquid:1.1.1` — GPU-accelerated frosted glass shaders
- WorkManager (4 workers)
- Coroutines + Flow
- Gson for JSON
- OkHttp + Retrofit for SIS APIs

### Firebase

- **Analytics** (private) — usage events
- **Crashlytics** — release-only crash reporting
- **Auth (anonymous)** — sole purpose: issue ID tokens to authenticate against the Cloudflare Worker for chess + class marks
- **Firestore** — chess lobby state only (legacy fallback when Durable Object v2 backend is off)
- **Remote Config** — feature flags (see §7)
- **Cloud Messaging** — currently not used for push, just kept warm

### Cloud backend (in `chess-lobby/` of this repo)

- Cloudflare **Worker** (`https://chess-lobby.tmswamy10.workers.dev`)
- Cloudflare **Durable Object** `Lobby` — chess presence + matchmaking
- Cloudflare **D1** database `class_marks_db` (ID `b3daf7d3-5e5e-475c-bf23-92be944afda2`, APAC) — anonymous CA marks comparison
- Routes:
  - `GET /health`
  - `GET /privacy` — JustPass privacy policy (rendered HTML)
  - `GET /ws` — chess WebSocket (Firebase auth required)
  - `POST /class/marks` — upload anonymous CA marks (Firebase auth)
  - `GET /class/:classKey` — class stats aggregation
  - `DELETE /class/me` — wipe my class data row

### Native libs / extras

- AdMob (banner ads on secondary screens, gated by `ads_enabled` remote config)
- ML Kit OCR (exam-seat Excel parsing)
- Apache POI (Excel parsing fallback)
- Cactus + LiteRT-LM (on-device AI chat — Gemma 4 E2B 0.6B)
- Lichess WebView for chess games

---

## 3. Architecture

### Login + Auth

```
WebView loads SIS_BASE_URL
  → redirects to Keycloak login
  → JS fills username/password, clicks login
  → after redirect to SIS, hooks XMLHttpRequest.prototype
  → navigates Angular to #!/attendanceStudentView
  → intercepts attendance XHR + captures Bearer token
  → persists token to SecurePreferences (EncryptedSharedPreferences)
  → 3s fallback: direct fetch with captured token if XHR intercept misses
  → fast credential validation via direct Keycloak POST (~500ms)
```

**Keycloak:**
- Endpoint: `https://accounts.psgitech.ac.in/realms/itech/protocol/openid-connect/token`
- `client_id`: `ies_sis`
- Password grant: `grant_type=password`, `scope=openid offline_access`
- Refresh: `grant_type=refresh_token`, `scope=openid offline_access`
- Access token lifetime: 600s (10 min)
- Offline refresh token: NEVER expires

### 4-tier refresh

1. **Fast path (~750ms)** — direct HTTP with cached/persisted Bearer
2. **Refresh path (~1.5s)** — offline refresh token → new access token → direct HTTP
3. **Password grant path (~1.5s)** — `loginViaKeycloak` with `scope=openid offline_access` → new token → direct HTTP
4. **WebView fallback (~15s)** — full re-login (essentially never needed since offline token never expires)

### Background work (WorkManager)

| Worker | Cadence | Purpose |
|---|---|---|
| `AttendanceRefreshWorker` | 8 min self-chain | Refresh attendance + check GitHub releases for updates |
| `CircularNotificationWorker` | ~3h | Check new circulars + push notification |
| `HolidayNotificationWorker` | ~6h | Day-before holiday alert |
| `ClassMarksUploadWorker` | 6h periodic | Upload anonymous CA marks to D1. Bails early if `class_compare_enabled` Remote Config is false. |

All scheduled in `MainActivity.onCreate`. ProGuard rules in `app/proguard-rules.pro` keep Worker classes + Gson models.

### Dual-state Liquid Glass scaffold

```
LiquidGlassScaffold:
  Box (root, .liquefiable(barState))                // For bottom bar frost
    Box (.liquefiable(cardState))                   // For card refraction
      gradient.background(...)                       // Base sky gradient
      WeatherBackgroundLayer(scene, drawSplashes=false)  // Behind cards
    content(cardState)                               // Cards refract everything below
    WeatherBackgroundLayer(scene, drawSplashes=true) // Splash particles ON cards
  bottomBar(barState)                                // Refracts entire stack
```

- `cardState`: refraction-only (no blur), individual cards sample the gradient + weather
- `barState`: frosted (with blur), bottom bar samples the entire stack including cards
- `LocalSplashZones` `CompositionLocal<MutableStateList<Rect>>` registry — `Modifier.registerAsSplashTarget()` opt-in (only attendance card on dashboard registers; auto-registration removed)

### Screen navigation

No Jetpack Navigation library — simple `enum class Screen` in `MainActivity.kt` with state-driven `Crossfade` (200ms). Bottom tabs: Home / CA Marks / Games / GPA / Timetable.

```kotlin
enum class Screen {
    Login, Dashboard, AbsentDays, SubjectAttendance, SubjectDetail, Exemptions,
    Result, PrivacyPolicy, CAMarks, ClassCompare, Timetable, Profile,
    AcademicCalendar, Circulars, CgpaCalculator, ExamSeat, Syllabus, Chess,
    Games, GamesLeaderboard, LiteRt, CreateTournament, TournamentApproval,
    BugReport, BugReportInbox, ManageAdmins
}
```

### Storage

- **`SecurePreferences`** — EncryptedSharedPreferences-backed singleton. SIS credentials (`rollNumber`, `password`), tokens (`accessToken`, `refreshToken`), all cached SIS data (CA marks, attendance, results, biodata, timetable), user prefs (attendance target, weather scene, last-uploaded-marks-hash).
- **Regular `SharedPreferences`** — non-sensitive flags (chess board theme, weather scene, AdMob testing).
- **Room?** No. Everything in JSON-serialized strings in EncryptedSharedPreferences.

### Network

- Retrofit + OkHttp for SIS REST calls (`NetworkModule.kt`)
- Direct OkHttp in `ClassMarksRepository` + `ChessRepositoryV2` for Worker calls
- All SIS APIs use Bearer auth header

---

## 4. SIS APIs in use

| Endpoint | Purpose |
|---|---|
| `/sis/students/{rollNumber}` | Student profile + `nodeId` for timetable |
| `/sis/attendance/...` | Attendance summary (server typo: `excemption` not `exemption`) |
| `/sis/absentdays` | Per-session absent list |
| `/sis/presentdays` | Per-session present list |
| `/sis/exemptions` | Subject-wise exemption sessions |
| `/sis/students/CAMarks/{rollNumber}` | CA marks per subject |
| `/sis/remote/all/results?rollNo=` | Semester results (grades) |
| `/sis/students/downloadUrl` | S3 pre-signed URL for profile picture |
| `/sis/students/timetable/{nodeId}` | Timetable |
| (separate auth: `ies_meetings` client_id) | |
| `/meetings/circulars/user/pagination` | Circular list |
| `/meetings/circular/{id}` | Circular detail |
| `/meetings/s3/download/url` | Circular PDF URL |

API response includes the server typo `excemption` instead of `exemption` — `@SerializedName` must match.

---

## 5. Feature matrix

### Dashboard

- Profile pic from SIS S3
- Attendance % (with + without exemption) — semester-filtered
- Leave calculator slider with holiday calendar picker
- Per-subject attendance grid (clickable → SubjectDetailScreen)
- Pull-to-refresh with comet glow border animation
- "Servers down" fallback when 5xx errors hit
- Water-fill animation behind attendance card (spring physics, see §6)
- Update notification banner (from `UpdateChecker`)

### CA Marks (`CAMarksScreen`)

- Color-coded marks per subject
- Tap-to-expand subject for component breakdown
- "Awaiting marks" for NE (not entered)
- **Compare icon (new 2026-05-17)** — opens `ClassCompareScreen` if `class_compare_enabled` remote config is true

### Class Comparison (`ClassCompareScreen`, 2026-05-17)

- Anonymous aggregate of CA marks scoped to `{batchYear}_{dept}_{section}_{sem}`
- Min-15 k-anonymity floor (server-side)
- Percentile gauge + class min/avg/max + your rank + per-subject bars + histogram
- Delete-my-data button → wipes own D1 row
- Auto-sync via `ClassMarksUploadWorker` every 6h + one-shot on every CA marks fetch
- **Currently dark**: gated by `class_compare_enabled` Remote Config flag (default false). Server backend live; flip the flag in Firebase Console to enable.
- See `project_class_marks_comparison.md` memory for full details.

### Timetable

- NOW period highlight with progress overlay
- Honours subjects with amber badge
- Dynamic `nodeId` fetch (per-user, not hardcoded)

### Subject Attendance / Subject Detail

- Per-subject card with exemption count
- Day-by-day session timeline
- 80% threshold warning

### Exemptions

- Sessions as `List<String>` per subject (server returns this shape)
- Semester-filtered (earliest present/absent date)

### Results (`ResultScreen`)

- Grade cards per subject
- Semester tabs
- SGPA per semester (live computed)

### GPA Calculator (`CgpaCalculatorScreen`)

- R2021 + R2025 curriculum for 7 departments
- Live SGPA + CGPA
- Camera OCR for grade extraction (ML Kit)
- Semester tabs with **AnimatedSlideInTabs** pill chips (mist-from-right entry)
- Target CGPA reverse-calc

### Circulars

- List + in-app PDF viewer (PdfRenderer → Bitmap → pinch-to-zoom)
- Background notification worker

### Academic Calendar

- Google Calendar API integration (read-only)
- Month grid view + holiday/event color-coding

### Chess Lobby

- Cloudflare Durable Object backend (`chess-lobby` Worker), Firestore fallback gated by `chess_backend_v2` remote config (default true)
- Firebase Anonymous Auth → ID token → Worker WS upgrade
- Live presence (15s challenge countdown, mutual-challenge prevention)
- Lichess WebView for actual game play
- 10 board themes (CSS injection into Lichess)
- Friends + leaderboard
- See `project_chess_lobby_v3.md` etc.

### Games (humanbenchmark merger)

- 8 mini-games (reaction time, memory, etc.)
- Supabase backend for scores (separate from JustPass Firebase)
- See `project_humanbenchmark.md`

### Profile

- Profile pic with pulse ring
- Biodata card
- Attendance target slider (50–100%)
- APK sharing (FileProvider, `apk_share/` cache-path)
- "Check for updates" (GitHub releases API)
- Weather Scene picker (17 options — Off + 16 weather scenes)
- Easter eggs (Bite Me sound, Side Eye Dog meme)
- "Delete my class data" (gated by `class_compare_enabled`)

### Bug Report

- Form to submit bugs, stored in Firestore (gated by admin role)
- Inbox for admins

### Other

- Exam seat finder (file picker + Apache POI Excel parsing)
- Syllabus viewer (R2021 + R2025 bundled JSON)
- Chat Advisor (on-device LiteRT-LM Gemma 4 E2B, intent-based action routing → screen navigation)

### Weather scenes (2026-05-15 rewrite per HANDOFF.md)

16 ambient backgrounds + Off, picked from Profile → Weather Scene:

| Category | Scenes |
|---|---|
| Clear | clear-day, clear-night |
| Cloud Cover | partly-day, partly-night, cloudy, overcast |
| Magic Hour | sunset, sunrise |
| Precipitation | rain, heavy-rain, thunderstorm, snow |
| Atmospheric | fog, haze, windy |
| Special | aurora |

Stored in `SecurePreferences.weatherScene` (String, default `"OFF"`).

Architecture:
- `WeatherBackgroundLayer` mounted twice by `LiquidGlassScaffold` (behind cards + above cards)
- Per-scene `SkyGradient` + scene-specific layers (Cloudscape, SunRays, RainCanvas, SnowCanvas, Stars, LightningCanvas, FogBands, HazyClouds, WindyStreaks, AuroraBands)
- Per-scene black readability scrim (0.08..0.28 alpha) so daytime sky doesn't wash out tile text
- LiquidGlassCard params softened (refraction 0.14, curve 0.35, dispersion 0.025, frost 2dp) so refraction through cards doesn't warp rain weirdly
- `LocalSplashZones` `CompositionLocal` — rain splashes only on tiles that explicitly call `.registerAsSplashTarget()` (only the attendance card on Dashboard)
- See `project_weather_modes.md` memory + `~/Downloads/HANDOFF.md` reference

Moon was implemented per MOON.md spec (NASA full-moon photo + phase-correct mask) then **removed entirely 2026-05-17** at user request — `CLEAR_NIGHT` scene now just shows a soft cool `CornerGlow` as ambient moonlight.

### Water animation (Dashboard attendance card)

- Spring-physics water surface with damping
- Tilt response (accelerometer)
- Scroll-induced sloshing
- Idle ripple (two-LFO modulated sine sources)
- Splash impacts on attendance % crossings
- Color shifts by attendance band (<60% red → 75% amber → ≥90% cyan)
- Periodic mean-velocity zero pass every ~6s to prevent long-session DC drift
- See `feedback_water_drift_sanitiser.md`

---

## 6. Key files (Android side)

```
app/src/main/java/com/justpass/app/
├── MainActivity.kt                          # Screen enum + crossfade routing + worker schedules
├── WaterTestActivity.kt                     # Debug-only sandbox for water animation (am start only)
│
├── data/
│   ├── analytics/Analytics.kt
│   ├── auth/PhoneAuthHelper.kt              # Firebase Phone Auth helper (chess login)
│   ├── local/SecurePreferences.kt           # EncryptedSharedPreferences singleton (~50 props)
│   ├── model/
│   │   ├── CAMarksResponse.kt
│   │   ├── ClassRanksData.kt                # NEW 2026-05-17 (class compare DTOs)
│   │   ├── StudentBiodata.kt
│   │   ├── CgpaData.kt                      # R2021 + R2025 curriculum for 7 depts
│   │   ├── ElectiveData.kt
│   │   ├── ChessData.kt
│   │   ├── ResultData.kt
│   │   ├── CalendarData.kt
│   │   ├── CircularData.kt
│   │   ├── SyllabusData.kt
│   │   ├── ExamSeatData.kt
│   │   ├── AiData.kt
│   │   ├── BugReportData.kt
│   │   └── TournamentAdmins.kt              # Hardcoded admin allowlist + dynamic Firestore list
│   ├── network/NetworkModule.kt             # Retrofit + OkHttp (SIS APIs)
│   ├── repository/
│   │   ├── AttendanceRepository.kt          # Main SIS data layer (singleton)
│   │   ├── ChessRepository.kt               # Firestore (legacy)
│   │   ├── ChessRepositoryV2.kt             # Cloudflare DO (active)
│   │   ├── ClassMarksRepository.kt          # NEW 2026-05-17 — D1-backed class compare
│   │   ├── AdminRolesRepository.kt
│   │   └── BugReportRepository.kt
│   ├── remote/LobbyWebSocket.kt             # WebSocket client for chess DO
│   ├── update/UpdateChecker.kt              # GitHub releases API poller
│   └── webview/WebViewAuthenticator.kt      # The crown jewel — SIS auth via WebView JS hooks
│
├── ui/
│   ├── components/
│   │   ├── GlassComponents.kt               # LiquidGlassScaffold + LiquidGlassCard + GlassListCard + LiquidGlassBottomBar + AnimatedSlideInTabs + nav icons
│   │   ├── WeatherBackground.kt             # 16 scenes, post-MOON-removal
│   │   ├── ClassCompareCharts.kt            # NEW 2026-05-17 — PercentileGauge / SubjectBar / DistributionHistogram
│   │   ├── AdBanner.kt
│   │   ├── RoseFourLoader.kt
│   │   └── water/
│   │       ├── WaterFill.kt
│   │       └── WaterPhysics.kt
│   ├── screens/
│   │   ├── LoginScreen.kt
│   │   ├── DashboardScreen.kt               # The biggest screen — leave calc, profile pic, attendance card with water, etc.
│   │   ├── CAMarksScreen.kt
│   │   ├── ClassCompareScreen.kt            # NEW 2026-05-17
│   │   ├── ProfileScreen.kt                 # Weather scene picker, delete-my-class-data, attendance target, easter eggs
│   │   ├── TimetableScreen.kt
│   │   ├── ChessScreen.kt
│   │   ├── CgpaCalculatorScreen.kt          # 12-sem GPA + camera OCR + pill tabs
│   │   ├── SyllabusScreen.kt                # Offline R2021 + R2025
│   │   ├── ResultScreen.kt
│   │   ├── ExemptionsScreen.kt
│   │   ├── SubjectAttendanceScreen.kt
│   │   ├── SubjectDetailScreen.kt
│   │   ├── AcademicCalendarScreen.kt
│   │   ├── CircularsScreen.kt
│   │   ├── ExamSeatScreen.kt
│   │   ├── BugReportScreen.kt
│   │   ├── BugReportInboxScreen.kt
│   │   ├── PrivacyPolicyScreen.kt
│   │   └── (and more)
│   ├── theme/Theme.kt
│   └── viewmodel/
│       ├── AttendanceViewModel.kt
│       ├── CAMarksViewModel.kt
│       ├── ChessViewModel.kt
│       ├── CgpaViewModel.kt
│       ├── ClassRanksViewModel.kt           # NEW 2026-05-17
│       ├── ResultViewModel.kt
│       ├── SyllabusViewModel.kt
│       └── (and more)
│
├── worker/
│   ├── AttendanceRefreshWorker.kt
│   ├── CircularNotificationWorker.kt
│   ├── HolidayNotificationWorker.kt
│   └── ClassMarksUploadWorker.kt            # NEW 2026-05-17
│
├── widget/
│   ├── AttendanceWidget.kt                  # Glance widget
│   └── AttendanceWidgetReceiver.kt          # RemoteViews widget
│
└── games/                                   # Brain-games module (humanbenchmark merger)
    └── (multiple subdirectories)

app/src/main/res/
├── xml/remote_config_defaults.xml           # Bundled Firebase Remote Config defaults
├── drawable*/                               # icons, easter egg images, RoseFour loader, etc.
└── raw/
    ├── faah.mp3                             # Easter egg sound
    ├── dog_side_eye.jpg
    └── (syllabus + curriculum JSONs)

chess-lobby/                                 # Cloudflare Worker source (inside this repo)
├── src/
│   ├── worker.ts                            # HTTP entry — routes /health, /privacy, /ws, /class/*
│   ├── lobby.ts                             # Durable Object class
│   ├── class.ts                             # NEW 2026-05-17 — D1 route handlers
│   ├── auth.ts                              # verifyFirebaseIdToken via JWKS
│   ├── lichess.ts
│   └── types.ts
├── migrations/
│   └── 0001_class_marks.sql                 # NEW 2026-05-17
├── wrangler.toml                            # Worker config (DO + D1 bindings)
└── package.json
```

---

## 7. Firebase Remote Config flags

All in `app/src/main/res/xml/remote_config_defaults.xml` (bundled defaults, server overrides):

| Key | Default | Purpose |
|---|---|---|
| `ads_enabled` | false | Show AdMob banners on secondary screens |
| `ads_use_test_ids` | false | Use AdMob test ad IDs |
| `chess_backend_v2` | **true** | Use Cloudflare DO backend (false = Firestore fallback) |
| `maintenance_enabled` | false | Block app launch with maintenance message |
| `maintenance_message` | "" | Text shown in maintenance dialog |
| `sideload_block_enabled` | false | Block app from running if not installed from Play Store |
| `tournament_enabled` | false | Show tournament UI surfaces in chess + profile |
| `class_compare_enabled` | **false** | NEW 2026-05-17. Master switch for class marks comparison feature (uploads + Compare icon + delete row). |

To enable a feature: Firebase Console → Remote Config → edit value → Publish. New installs see it after `fetchAndActivate`; existing installs within 1h.

---

## 8. Privacy + compliance

### Privacy policy

Hosted at `https://chess-lobby.tmswamy10.workers.dev/privacy` (rendered by the chess-lobby Cloudflare Worker — `PRIVACY_POLICY_HTML` constant in `chess-lobby/src/worker.ts`). Update by editing that constant and redeploying.

**Why a Worker, not a static page?**
v3.0.3 was initially rejected by Play because the URL field pointed to `justpass-eta.vercel.app/privacy` which 404'd into the PWA login shell. Moved to Worker route — guaranteed to serve real HTML with no auth gate. Worker URL has been stable since 2026-05-10.

**What's disclosed:**
1. SIS credentials stored locally only (never sent off-device by us — only to PSG's SIS)
2. Academic data fetched + cached locally
3. Firebase Authentication anonymous UID
4. Chess lobby presence (Firestore + Durable Object)
5. Firebase Analytics (anonymous usage)
6. AdMob
7. FCM notifications token (local only)
8. **Class marks comparison (anonymous)** — disclosed for the new feature (2026-05-17)

### Play Store gotchas

- v3.0.3 (versionCode 12) was rejected once on 2026-05-10 for the broken privacy URL → fixed → resubmitted via Claude-in-Chrome Play Console flow → approved + live since 2026-05-15
- Closed Testing → Production transitions leave **orphan package records** on tester devices (`pm list packages -u` shows them). Symptom: "incompatible version" on Play Store install. Fix: `adb uninstall com.justpass.app`.
- Old sideloaded `com.example.attendancewidgetlaudea` v2.0.1 users are migrated via a GitHub release on this repo (v3.0.3 tag with placeholder APK + release notes pointing to Play Store URL). UpdateChecker polls the public API; that's why the repo was made PUBLIC on 2026-05-15.

### Data Safety form (Play Console)

Currently declares:
- Personal info: roll number (collected, not shared)
- App activity: stored on device only

**Needs update before next submission:**
- Academic info → shared with other users (anonymized) — for class marks comparison

---

## 9. Build + release

### Local build

```bash
JAVA_HOME='/c/Program Files/Android/Android Studio/jbr' ./gradlew.bat installDebug
```

### Release signing

`keystore.properties` exists but points to a non-existent keystore by default — release builds fall back to debug signing locally. Production AAB is built and uploaded via Android Studio's Generate Signed Bundle wizard using the real keystore (separately managed).

### Repo dash-prefix gotcha

`https://github.com/Tarunswamy-Muralidharan/-AttendanceWidgetLaudea` — note the `-` before `AttendanceWidgetLaudea`. This trips up shell parsers occasionally (`gh release create -- v3.0.3` etc.).

---

## 10. Backend (chess-lobby Worker) deploy

Source lives in `chess-lobby/` of this repo. Deploy via:

```bash
cd chess-lobby
npx wrangler deploy
```

**Bindings (in `wrangler.toml`):**
- `LOBBY` — Durable Object for chess
- `CLASS_MARKS_DB` — D1 database for class marks

**Secrets:**
- `FIREBASE_PROJECT_ID` — set via `npx wrangler secret put FIREBASE_PROJECT_ID`. Must match `google-services.json → project_info.project_id`. Used by `verifyFirebaseIdToken` for JWKS lookup.

**D1 migration (one-time per environment):**
```bash
npx wrangler d1 execute class_marks_db --remote --file migrations/0001_class_marks.sql
```

---

## 11. Memory system

This project has 162 memory files at `C:\Users\tmswa\.claude\projects\C--Users-tmswa-AndroidStudioProjects-AttendanceWidgetLaudea\memory\` covering every major decision, fix, and pattern from project inception. Key indexes in `MEMORY.md`. The most load-bearing ones:

| Memory file | What it captures |
|---|---|
| `architecture.md` | Auth flow, 4-tier refresh, API field names, ProGuard rules |
| `project_class_marks_comparison.md` | Full class compare feature (current focus) |
| `project_weather_modes.md` | 16-scene weather system per HANDOFF.md |
| `project_old_user_migration_release.md` | GitHub release + repo public flip |
| `project_v303_privacy_rejection_fix.md` | Why the privacy URL lives on the Worker |
| `project_chess_lobby_v3.md` | Cloudflare DO chess migration |
| `project_chess_audit_2026_05_05.md` | 8 chess module bug fixes |
| `project_humanbenchmark.md` + `reference_humanbenchmark_supabase.md` | Games merger + Supabase schema |
| `feedback_match_video_exactly.md` | User-supplied reference videos must be replicated pixel-close |
| `feedback_water_drift_sanitiser.md` | Long-session physics drift fix |
| `feedback_glass_ui.md`, `feedback_color_tuning.md`, `feedback_responsive_ui.md` | Visual design feedback rules |

---

## 12. Open items / current state (2026-05-18)

### Currently dark (off by default)

- **Class marks comparison** — fully built (worker + Android + UI) but `class_compare_enabled` Remote Config is false. Flip in Firebase Console to enable.
- **Tournament UI** — built but `tournament_enabled` is false.
- **AdMob ads** — built but `ads_enabled` is false.

### In review on Play

Nothing pending review. v3.0.3 is live.

### Pending decisions

- **PWA option A (server-side cron polling for class marks)** — under discussion as of 2026-05-17. Three paths surfaced: full Option A (store SIS creds server-side, biggest security debt), Option A-opt-in (only opt-in PWA users get cred storage), or lighter "manual sync nudge" in PWA. User hasn't picked yet.
- **Phase 5 of class compare** — server-side cron for PWA users — deferred until path decision above.
- **Data Safety form update** — needed before next Play submission to declare anonymous academic info sharing.

### Known gotchas

- Server typo: `excemption` (not exemption) on every attendance API field. `@SerializedName` must match.
- Compose `LazyColumn` is unusably laggy in debug builds; release is fine.
- WebView JS interfaces in anonymous Kotlin objects need wildcard ProGuard rules.
- Repo name has dash prefix (`/-AttendanceWidgetLaudea`).
- Section field in biodata is sometimes null for non-CSE departments — class compare bails silently if it can't resolve a class key.
- iOS Safari (PWA side) throttles `setInterval` — chess countdown bugs got fixed by anchoring to wall-clock timestamp, not local decrement.

### Tooling caches

- Reverse-engineering tools: `C:\Users\tmswa\bin\re\` (apktool + jadx)
- ColorOS Weather APK decoded: `C:\Users\tmswa\re\coloros\` (~50 MB)
- Old sideloaded APK: `C:\Users\tmswa\Desktop\AttendanceWidget-v2.0.1.apk`

---

## 13. User profile (for picking up tone + decisions)

- **Tarunswamy Muralidharan**, BTech student at PSG iTech (3rd year as of writing)
- Builds JustPass for friends + classmates — friend-distribution college app, not a startup
- Strong design sense — supplies reference videos (Mi Weather, ColorOS Weather, planner-app pill tabs) and expects pixel-close matches in 2-3 iteration rounds
- Strong on security — initially asked about reverse-engineering ColorOS Weather, doesn't push past safety boundaries when explained
- Comfortable with terminal + git + Android Studio + Firebase Console — operates the deploy pipeline himself
- Uses Comet browser (Chromium-based, supports Claude-in-Chrome extension for Play Console automation)
- Prefers terse / caveman-mode communication; explicit toggles between modes
- Treats Claude as a sometimes-autonomous collaborator — has granted "do everything for the next 10 hours" autonomy on multiple sessions

---

## 14. How to bootstrap on this project

1. Read `MEMORY.md` for the index of 149 memory files (each is 1-3 paragraphs).
2. Read `DEVELOPMENT_LOG.md` — append-only journal, ~8000 lines, 9 session entries. Tail is most recent.
3. Read `architecture.md` for the auth + refresh + API conventions.
4. Skim `CLAUDE.md` (if present) for project-level instructions.
5. For class marks comparison specifically (current focus): read `project_class_marks_comparison.md`.
6. For the PWA: read `PWA_HANDOFF.md` + that project's own `DEVELOPMENT_LOG.md` + `CLAUDE.md`.

Build state: clean. `gradlew installDebug` works. Worker deploys clean via `wrangler deploy` from `chess-lobby/`.

---

*End of ANDROID_HANDOFF.md. Companion file: PWA_HANDOFF.md.*
