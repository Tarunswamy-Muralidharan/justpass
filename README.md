# Attendance Widget - LAUDEA SIS

Android attendance widget & app for PSG iTech LAUDEA Student Information System.

## Features

- **Attendance Dashboard** — View attendance percentage (with/without exemption), present/absent counts, total classes
- **Home Screen Widget** — Quick glance at attendance percentage without opening the app
- **Absent Day Details** — Tap the absent card to see detailed breakdown by date, course, and time
- **CA Marks** — View continuous assessment marks with component-level breakdown
- **Auto Refresh** — Background periodic refresh via WorkManager
- **Session Management** — Automatic re-login when session expires, no manual re-authentication needed

## Tech Stack

- Kotlin + Jetpack Compose
- WebView-based Keycloak authentication
- Glance for home screen widget
- EncryptedSharedPreferences for secure credential storage
- WorkManager for background refresh

## Building

1. Clone the repo
2. Open in Android Studio
3. Build & run (debug or release)

## Download

Check [Releases](../../releases) for the latest APK.
