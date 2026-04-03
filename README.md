# JustPass - PSG iTech Attendance App

> *"Bro enakku topper venam... just pass podhum da."*

An Android app and home screen widget for PSG iTech LAUDEA Student Information System. Track your attendance, timetable, CA marks, semester results, chess matchmaking, and more -- wrapped in an iOS-inspired liquid glass UI.

## Features

### Core
- **Attendance Dashboard** -- Overall attendance percentage (with/without exemption), present/absent counts, total classes, configurable target (default 75%)
- **Subject-wise Attendance** -- Per-subject breakdown with color-coded progress bars. Tap to see day-by-day session timeline
- **Leave Calculator** -- "What if I take leave?" simulator with holiday calendar integration and actual missed working days count
- **Semester Results** -- Exam grades per semester with letter grade, grade point, SGPA via semester tabs
- **Timetable** -- Daily schedule with day tabs, "NOW" badge, period progress overlay, auto-detected honours courses
- **CA Marks** -- Continuous assessment with color-coded expandable breakdown
- **GPA Calculator** -- R2021 + R2025 curriculum support, auto-detects department, elective dropdown, OCR grade import via camera
- **Exemptions** -- View all exemption applications with status tracking

### Social
- **Chess Lobby** -- Real-time matchmaking with PSG iTech students via Firestore + Lichess
  - Time controls: Bullet (1min, 1+1), Blitz (3/5min), Rapid (10/15min), Classical (30min)
  - Auto-detect game results from Lichess API
  - SR rating system, leaderboard, friend system, match history
  - Animated isometric 3D chessboard icon with randomized piece movement
  - "Chess" label in Great Vibes calligraphy font

### Utilities
- **College Circulars** -- In-app PDF viewer with pinch-to-zoom
- **Academic Calendar** -- Google Calendar integration, color-coded events, holiday notifications
- **Exam Seat Finder** -- Import Excel seating chart via share intent / file picker
- **Offline Syllabus** -- Bundled R2021 + R2025 syllabus viewer with search
- **Profile** -- SIS profile picture, biodata, attendance overview, APK sharing, bug report

### System
- **Home Screen Widget** -- Quick attendance glance, auto-refreshes every 8 minutes
- **4-Tier Auto Refresh** -- Cached token, offline refresh, password grant, WebView fallback
- **Push Notifications** -- New circulars (every 3h), upcoming holidays (every 6h), app updates
- **Pull-to-Refresh** -- Comet light animation along glass header border

## Glass UI

The app uses an iOS-style liquid glass design powered by [FletchMcKee/liquid](https://github.com/FletchMcKee/liquid):

- GPU-accelerated frosted glass with real AGSL shaders (refraction, edge reflections, chromatic dispersion)
- Floating bottom navigation bar with center bump for Chess, pill-to-circle morph animation
- Custom animated icons for all 5 nav tabs (Home, CA Marks, Chess, GPA, Timetable)
- Comet light animation on glass headers during refresh
- Crossfade transitions (200ms) between all screens
- Dark/light glass color schemes with translucent surfaces

## Tech Stack

- **Language:** Kotlin 2.3.0
- **UI:** Jetpack Compose + Material 3 (BOM 2025.10.00)
- **Glass Effects:** [FletchMcKee/liquid](https://github.com/FletchMcKee/liquid) (v1.1.1)
- **Widget:** Glance (home screen widget)
- **Auth:** WebView-based Keycloak SSO with XHR interception
- **Storage:** EncryptedSharedPreferences for secure credential and token storage
- **Backend:** Firebase Firestore (chess lobby), Firebase Analytics
- **Background:** WorkManager (refresh cycle, circular/holiday checks)
- **Networking:** Direct HTTP with Bearer token authentication
- **OCR:** ML Kit Text Recognition for grade import

## Building

**Requirements:**
- Android Studio (latest stable)
- Kotlin 2.3.0
- Compose BOM 2025.10.00

**Steps:**
1. Clone the repo
2. Open in Android Studio
3. Sync Gradle and let dependencies download
4. Build and run (debug or release)

> **Note:** Release builds use R8 minification. ProGuard rules are pre-configured for Gson, JavascriptInterface, and Keycloak token handling.

## Download

Check [Releases](../../releases) for the latest APK.

## Development Log

### v1.0 -- Initial Release
- Basic attendance dashboard + home screen widget
- WebView-based Keycloak authentication

### v1.1 -- Analytics & Refresh
- Firebase Analytics integration
- 3-tier token refresh strategy

### v1.2 -- Instant Refresh (2026-03-18)
- 4-tier refresh (added password grant)
- Background update check via GitHub API
- Push notification when new version available

### v2.0 -- Glass UI Overhaul (2026-03-20)
- Complete redesign with liquid glass UI (real GPU shaders)
- Subject-wise attendance with per-subject day-by-day timeline
- Exemptions screen with semester filtering
- SIS profile picture via S3 pre-signed URL
- Semester results with grade cards and SGPA
- Pull-to-refresh with comet glow animation
- Responsive stat cards for all screen sizes
- Chomping easter egg

**Obstacles:**
- Glass on widgets is impossible (RemoteViews can't do GPU shaders) -- kept widget dark/opaque
- Timetable nodeId was hardcoded for CSE -- made it dynamic per-user via `/sis/students/{rollNumber}`

### v2.0.1 -- Hotfix (2026-03-20)
- Fixed timetable showing wrong schedule for non-CSE users (nodeId was hardcoded)
- Released same day as v2.0 after friends reported the bug

### v2.1 -- Feature Explosion (2026-04-01, committed, not released)
- **Exam Seat Finder** -- Import Excel via share intent, Apache POI parsing
- **GPA Calculator** -- R2021 + R2025 curriculum for 7 departments, elective dropdowns, OCR grade import
- **College Circulars** -- Meetings API integration, in-app PDF viewer (PdfRenderer + pinch-to-zoom)
- **Academic Calendar** -- Google Calendar API, month grid, color-coded events
- **Holiday Notifications** -- Background worker (every 6h), notifies day before holiday
- **Circular Notifications** -- Background worker (every 3h), checks for new circulars
- **Offline Syllabus** -- Bundled JSON for R2021 + R2025, search + semester filter
- **Chess Lobby** -- Firestore presence, real-time challenges, Lichess open challenge API, auto-detect results
- **5-Tab Navigation** -- Home / CA Marks / Chess / GPA / Timetable with animated icons
- **Honours Detection** -- Registration API cross-ref, amber badge, excludes LIB/MM
- **ProGuard/R8** -- Minification enabled with custom keep rules
- **Glass Dashboard Tiles** -- Tappable feature tiles with glass effect
- **Student Biodata** -- Collapsible profile card with personal/contact/family/admissions info

**Obstacles:**
- OCR grade import: ML Kit text blocks had wrong spatial ordering -- built order-preserving matching with fuzzy grade point lookup and 2x bitmap upscale
- Honours detection: Gson crash on empty arrays + placeholder subjects in curriculum data + needed attendance API cross-reference
- Timetable nodeId race: fetchStudentBiodata during login caused race condition -- NEVER call it during login/startup
- CSBS department: API returns wrong dept field -- detect from programmeName instead
- Circular auth: Needed separate meetings token (client_id=ies_meetings) with its own refresh flow

### v2.1 WIP -- Nav Redesign + Polish (2026-04-02, uncommitted)
- **Bump Bar Navigation** -- Chess tab elevated in center with pill-to-circle glass morph
- **Leave Calculator v2** -- Holiday calendar picker, crossed-out holidays, actual missed working days
- **Star Comet Animation** -- Diagonal behind/in-front orbit for CA Marks tab icon
- **Exam Seat Arrow Fix** -- Corrected guide arrow positioning
- **Removed Credit Text** -- No developer name/Discord in app UI

### v2.1 WIP -- JustPass Rebrand + Chess Overhaul (2026-04-03, uncommitted)

**App Rebrand:**
- Renamed from "Laudea Attendance" to "JustPass" across all screens, strings, APK filename, share text, privacy policy
- New logo: tilted graduation cap + green checkmark + sweat drop on dark navy background
- "JustPass" -- relatable student survival branding that's meme-worthy and memorable

**Animated Chess Icon (center nav tab):**
- True isometric 3D chessboard drawn in Canvas with diamond-shaped tiles
- Each tile has top face + right side face + front side face with ultra glossy effects (specular highlights, gradient shine bands, bright edge lines)
- Thick platform base with 3D front-left and front-right faces
- Default: dark slate/silver. Selected: smoothly animates to chess.com green (#739552) + cream (#EBECD0) via animateColorAsState
- White knight + dark bishop slide randomly across the board (valid chess moves, random delays/durations)
- "Chess" in Great Vibes calligraphy font (16sp bold), turns green when selected
- Entire icon levitates up/down in infinite loop
- Glass bubble expanded to 88dp to cover board + label

**Obstacles overcome building the chess icon:**
1. First attempt used flat rectangles -- looked horrible. Rewrote with true isometric diamond projection (parallelogram tiles)
2. Second attempt had flat tiles with no depth -- added 3D side faces per tile with different shading
3. Board not matching reference image -- studied the 3D render closely and matched the perspective exactly
4. Pieces were bouncing (hop arcs) -- user wanted sliding, removed all sine-wave hop offsets
5. Visible loop stutter at animation restart -- replaced fixed 6-move `infiniteRepeatable` with random `LaunchedEffect` coroutines that pick valid moves independently
6. Orbiting sparkle stars were distracting -- removed entirely
7. Green circle glow around board was ugly -- removed the `drawCircle` glow
8. Calligraphy text bottom getting clipped -- pushed entire Column up with -12dp offset and reduced font
9. Glass bubble too small -- increased `circleD` from 62dp to 88dp

**Chess Time Controls:**
- Added `TimeControl` enum with 10 presets (Bullet 1min to Classical 30min)
- Time control picker dialog when tapping "Play" on any player
- Stored in Firestore challenge doc, shown on incoming challenge popup
- Passed to Lichess API `POST /api/challenge/open` with correct clock params

**Match Result Bug Fix:**
- `listenChallengeStatus` was NOT reading `fromColor`, `resultChecked`, `timeControl` from Firestore
- `listenIncomingChallenges` was also missing most fields
- Fixed both to read ALL fields -- this was why match results weren't showing after games
- Added 3-second delayed result check after returning from Lichess
- Added periodic 30s result polling while on chess screen
- Added 90-second auto-expiry for unanswered challenges

**Profile Discoverability:**
- Pulsing double-ripple ring animation around profile picture in header
- Ring expands 1x→1.6x and fades, with staggered second ring
- Profile pic properly centered in 52dp Box

**Bug Report / Feature Request:**
- New "Report Bug / Feature Request" option in ProfileScreen
- Opens GitHub Issues page directly
- "Help us improve JustPass" subtitle

**Chess Lobby Info Card:**
- Collapsible "How does Chess Lobby work?" card
- 7 sections covering getting started, challenges, ratings, friends, history, Lichess info

## Feedback

Found a bug or have a feature request? [Open an issue](../../issues/new/choose)

## Credits

Built by **Tarunswamy Muralidharan**
