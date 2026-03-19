# Attendance Widget - LAUDEA SIS

An Android app and home screen widget for PSG iTech LAUDEA Student Information System. Instantly view your attendance, timetable, CA marks, and more -- wrapped in an iOS-inspired liquid glass UI.

## Features

- **Attendance Dashboard** -- View attendance percentage (with/without exemption), present/absent counts, total classes, and a 75% target warning
- **Subject-wise Attendance** -- Per-subject attendance breakdown with color-coded progress bars. Tap any subject to see day-by-day session details (Present, Absent, Exemption)
- **Timetable** -- Daily class schedule with day tabs and a "NOW" badge for the current session
- **CA Marks** -- Continuous assessment marks with component-level expandable breakdown
- **Exemptions** -- View all exemption applications with type, category, date range, reason, and status
- **Home Screen Widget** -- Quick glance at attendance percentage without opening the app, refreshes in the background every 8 minutes
- **Auto Refresh** -- 4-tier token refresh strategy (cached token, offline refresh, password grant, WebView fallback) ensures instant data at any time
- **Session Management** -- Automatic re-login when session expires; no manual re-authentication needed
- **Update Notifications** -- Background check for new releases via GitHub, notifies you when an update is available

## Glass UI

The app uses an iOS-style liquid glass design powered by the [FletchMcKee/liquid](https://github.com/FletchMcKee/liquid) library:

- GPU-accelerated frosted glass effects with real AGSL shaders (refraction, edge reflections, chromatic dispersion)
- Floating pill-shaped bottom navigation bar with animated sliding glass bubble indicator
- Diagonal light reflection animation on refresh
- Custom dark/light glass color schemes with translucent surfaces

## Screenshots

Screenshots are available in the [Releases](../../releases) page.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Glass Effects:** [FletchMcKee/liquid](https://github.com/FletchMcKee/liquid) (v1.1.1)
- **Widget:** Glance (home screen widget)
- **Auth:** WebView-based Keycloak SSO with XHR interception
- **Storage:** EncryptedSharedPreferences for secure credential and token storage
- **Background:** WorkManager (self-chaining 8-min refresh cycle)
- **Networking:** Direct HTTP with Bearer token authentication

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

## Credits

Built by **Tarunswamy Muralidharan**
