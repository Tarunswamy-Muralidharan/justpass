# COMPOSE-AUDIT-REPORT.md

**Project:** `AttendanceWidgetLaudea` (`com.justpass.app`)  
**Audit date:** 2026-05-28  
**Skill version:** `jetpack-compose-audit` 2.1.1 (source-inferred mode)  
**Auditor:** Kimi Code CLI  
**Module scoped:** `:app`

---

## Executive Summary

**Overall: 48/100**

| Category | Score | Weight | Weighted |
|----------|-------|--------|----------|
| Performance | 5/10 | 35% | 1.75 |
| State management | 4/10 | 25% | 1.00 |
| Side effects | 6/10 | 20% | 1.20 |
| Composable API quality | 4/10 | 20% | 0.80 |

**Confidence:** Reduced — compiler reports could not be generated (Android SDK unavailable in audit environment). Performance is capped at `7` per rubric; actual inferred score is `5`.

**Top 3 fixes (act on these alone if short on time):**

1. **Swap `collectAsState()` → `collectAsStateWithLifecycle()` at 24 call sites** — stops flow collection when the UI is backgrounded. Highest impact fix in the repo.
2. **Add stable `key =` to 12 `items(...)` factories in lazy lists** — removes recomposition churn on scroll/reorder and prevents key-collision crashes.
3. **Remove unnecessary `AndroidViewModel` inheritance** and centralize hardcoded strings/colors — improves testability and dark-mode correctness.

---

## Compiler / Measurement

**Build status:** Could not execute Gradle build (no Android SDK in audit environment).  
**Mode:** Source-inferred fallback. Performance ceiling applied: **7**.  
**Strong Skipping:** Assumed **ON** (Kotlin 2.3.0 / Compose Compiler 1.5.4+ track; default-on).  
**Ceiling table applied:** SSM-on (qualitative).  
**Module-wide skippable%:** Unknown (requires compiler report).  
**Named-only skippable%:** Unknown (requires compiler report).  
**Unstable-class count:** Unknown.

> Every Performance finding below is scored against the source-inferred ceiling of `7`. To lift the ceiling, re-run this audit in an environment with Android SDK + Gradle wrapper and enable the bundled `compose-reports.init.gradle`.

---

## Category: Performance (5/10)

### Deductions

#### P1. Lazy-list `items(...)` without stable `key =` — 12 occurrences
**Impact:** Recomposition churn on reorder/scroll; potential `IllegalArgumentException: Key already used` crashes.

| File | Line | List content |
|------|------|--------------|
| `BugReportInboxScreen.kt` | 78 | `items(state.reports) { r ->` |
| `BugReportScreen.kt` | 244 | `items(reports) { r ->` |
| `CAMarksScreen.kt` | 125 | `items(uiState.courseMarksList) { CourseCard(it) }` |
| `ChessScreen.kt` | 936 | `items(history) { match ->` |
| `ChessScreen.kt` | 1627 | `items(sortedFriends) { friend ->` |
| `ExemptionsScreen.kt` | 93 | `items(sorted) { exemption ->` |
| `ManageAdminsScreen.kt` | 107 | `items(HARDCODED_PLAYER_IDS.toList())` |
| `ManageAdminsScreen.kt` | 110 | `items(state.admins) { entry ->` |
| `ProfileScreen.kt` | 517 | `items(WeatherScene.entries.toList())` |
| `SubjectAttendanceScreen.kt` | 103 | `items(uiState.subjects) { subject ->` |
| `TournamentApprovalScreen.kt` | 74 | `items(state.pendingRequests) { req ->` |
| `TimetableScreen.kt` | 109 | `items(day.sessions) { session ->` |

**Fix pattern:**
```kotlin
// Before
items(state.reports) { r -> ReportCard(r) }

// After
items(state.reports, key = { it.id }) { r -> ReportCard(r) }
```
**References:** https://developer.android.com/develop/ui/compose/lists#item-keys

---

#### P2. No `@Stable` / `@Immutable` on UI state data classes
**Impact:** Under SSM, raw `data class` UiState types are treated as unstable. While Strong Skipping mitigates direct recompositions, expensive `equals()` on large state objects still forces instance-level work and can cap performance in deeply-nested trees.

**Affected files (all `data class *UiState` in ViewModels):**
- `AbsentDaysUiState`, `CalendarUiState`, `BugReportUiState`, `AdminRolesUiState`, `CircularsUiState`, `PdfViewerState`
- `ChessUiState`, `CAMarksUiState`, `CgpaUiState`, `ExamSeatUiState`, `LiteRtUiState`
- `DashboardUiState`, `ExemptionsUiState`, `ResultUiState`, `LoginUiState`, `SyllabusUiState`
- `TimetableUiState`, `TournamentUiState`, `SubjectAttendanceUiState`

**Fix pattern:**
```kotlin
@Immutable
data class DashboardUiState(
    val attendanceData: AttendanceData = AttendanceData(),
    ...
)
```
**References:** https://developer.android.com/reference/kotlin/androidx/compose/runtime/Immutable

---

#### P3. Autoboxing hot-path state in game screens
**Impact:** `mutableStateOf<Set<Int>>` and `mutableStateOf<Job?>` force allocation overhead per recomposition in time-sensitive game loops.

| File | Line | Smell |
|------|------|-------|
| `VisualMemoryScreen.kt` | 69 | `var litCells by remember { mutableStateOf(setOf<Int>()) }` |
| `VisualMemoryScreen.kt` | 71 | `var tapped by remember { mutableStateOf(setOf<Int>()) }` |
| `VisualMemoryScreen.kt` | 72 | `var missed by remember { mutableStateOf(setOf<Int>()) }` |
| `ReactionTimeScreen.kt` | 72 | `var sequenceJob by remember { mutableStateOf<Job?>(null) }` |

**Fix:** Use `mutableIntStateOf` where possible; for sets, consider a snapshot-backed mutable set or hoist the mutation out of Compose state if the UI only needs to observe a derived boolean.
**References:** https://developer.android.com/develop/ui/compose/performance/stability/fix

---

#### P4. Heavy `rememberInfiniteTransition` usage in always-visible UI
**Impact:** Multiple infinite transitions run simultaneously in the bottom bar (`AnimatedHomeIcon`, `AnimatedCalendarIcon`, `AnimatedCalculatorIcon`, `AnimatedStarIcon`, `AnimatedControllerIcon`, `AnimatedChessIcon`) and dashboard (`profilePulse`, `orb`, `cgpaIndicator`). While these are visible, they burn animation clock ticks continuously.

| File | Approx. line | Label |
|------|--------------|-------|
| `GlassComponents.kt` | 469 | `levitate` |
| `GlassComponents.kt` | 1891 | `loader` |
| `DashboardScreen.kt` | 289 | `profilePulse` |
| `DashboardScreen.kt` | 382 | `orb` |
| `DashboardScreen.kt` | 455 | `cgpaIndicator` |

**Note:** Not scored as a hard deduction because the host content is generally visible; however, scoping transitions to `AnimatedVisibility` or `DisposableEffect` cleanup would reduce offscreen work when dialogs overlay them.
**References:** https://developer.android.com/develop/ui/compose/animation/introduction

---

#### P5. `contentPadding = PaddingValues(bottom = 160.dp)` copy-pasted across 10+ screens
**Impact:** Magic number repeated everywhere; makes design-system changes brittle.

**References:** https://developer.android.com/develop/ui/compose/layouts/spacing

---

## Category: State Management (4/10)

### Deductions

#### S1. `collectAsState()` used instead of `collectAsStateWithLifecycle()` — 24 call sites
**Impact:** Flows continue collecting while the UI is in the background (e.g., user switches apps, screen is covered by a dialog), wasting CPU and potentially emitting stale navigation events.

| File | Line | Flow source |
|------|------|-------------|
| `LiteRtScreen.kt` | 63 | `viewModel.uiState.collectAsState()` |
| `ClassCompareScreen.kt` | 36 | `viewModel.state.collectAsState()` |
| `ClassCompareScreen.kt` | 116 | `viewModel.state.collectAsState()` |
| `BugReportScreen.kt` | 52 | `viewModel.uiState.collectAsState()` |
| `DashboardScreen.kt` | 124 | `viewModel.uiState.collectAsState()` |
| `ExemptionsScreen.kt` | 37 | `viewModel.uiState.collectAsState()` |
| `CircularsScreen.kt` | 52 | `viewModel.uiState.collectAsState()` |
| `CircularsScreen.kt` | 53 | `viewModel.pdfState.collectAsState()` |
| `BugReportInboxScreen.kt` | 41 | `viewModel.uiState.collectAsState()` |
| `CreateTournamentScreen.kt` | 33 | `viewModel.uiState.collectAsState()` |
| `ExamSeatScreen.kt` | 52 | `viewModel.uiState.collectAsState()` |
| `AcademicCalendarScreen.kt` | 65 | `viewModel.uiState.collectAsState()` |
| `ChessScreen.kt` | 73 | `viewModel.uiState.collectAsState()` |
| `ResultScreen.kt` | 46 | `viewModel.uiState.collectAsState()` |
| `AbsentDaysScreen.kt` | 35 | `viewModel.uiState.collectAsState()` |
| `CgpaCalculatorScreen.kt` | 69 | `viewModel.uiState.collectAsState()` |
| `CgpaCalculatorScreen.kt` | 71 | `resultViewModel.uiState.collectAsState()` |
| `TournamentApprovalScreen.kt` | 31 | `viewModel.uiState.collectAsState()` |
| `ManageAdminsScreen.kt` | 34 | `viewModel.uiState.collectAsState()` |
| `CAMarksScreen.kt` | 50 | `viewModel.uiState.collectAsState()` |
| `TimetableScreen.kt` | 37 | `viewModel.uiState.collectAsState()` |
| `LoginScreen.kt` | 34 | `viewModel.uiState.collectAsState()` |
| `SubjectAttendanceScreen.kt` | 40 | `viewModel.uiState.collectAsState()` |
| `SyllabusScreen.kt` | 42 | `viewModel.uiState.collectAsState()` |

**Fix pattern:**
```kotlin
// Before
val uiState by viewModel.uiState.collectAsState()

// After
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```
**Add dependency if missing:** `androidx.lifecycle:lifecycle-runtime-compose` (already present via BOM).  
**References:** https://developer.android.com/develop/ui/compose/side-effects

---

#### S2. Excessive `AndroidViewModel` inheritance
**Impact:** Ties ViewModels to the Android `Application` context, making unit testing harder and violating the ViewModel boundary guideline. Most ViewModels in this repo do not appear to use `Application` for anything beyond obtaining `SecurePreferences` or similar, which can be injected or obtained via a repository/factory.

**Affected ViewModels (all extend `AndroidViewModel`):**
`AdminRolesViewModel`, `AbsentDaysViewModel`, `CgpaViewModel`, `CAMarksViewModel`, `BugReportViewModel`, `TournamentViewModel`, `TimetableViewModel`, `SyllabusViewModel`, `SubjectAttendanceViewModel`, `ResultViewModel`, `LoginViewModel`, `LiteRtViewModel`, `ExemptionsViewModel`, `ExamSeatViewModel`, `DashboardViewModel`, `ClassRanksViewModel`, `CircularViewModel`, `ChessViewModel`

**Fix:** Extend `ViewModel()` and inject dependencies via constructor/factory. If `Application` is only needed for `SecurePreferences.getInstance()`, move that lookup into a repository or use a `SavedStateHandle`-backed approach.
**References:** https://developer.android.com/topic/libraries/architecture/viewmodel

---

#### S3. State hoisting violations in game screens
**Impact:** Game state (`level`, `lives`, `stage`, `bestLevel`) is owned directly inside game composables rather than hoisted to a ViewModel or at least a screen-level holder. Configuration changes and process death will reset game progress.

| File | Lines | State owned inline |
|------|-------|--------------------|
| `VisualMemoryScreen.kt` | 64–72 | `level`, `lives`, `stage`, `bestLevel`, `litCells`, `revealed`, `tapped`, `missed` |
| `ReactionTimeScreen.kt` | 67–72 | `stage`, `litCount`, `lightsOutAt`, `lastMs`, `bestMs`, `sequenceJob` |
| `ChimpTestScreen.kt` | ~63–71 | `level`, `lives`, `stage`, `grid`, `bestLevel` |
| `NumberMemoryScreen.kt` | ~80–88 | `stage`, `current`, `userInput`, `bestLevel` |

**Note:** Games are short-lived; however, rotation during a game destroys progress. At minimum, wrap game state in a `rememberSaveable` holder or a small `ViewModel` scoped to the game route.
**References:** https://developer.android.com/develop/ui/compose/state#state-hoisting

---

## Category: Side Effects (6/10)

### Deductions

#### E1. `DisposableEffect(lifecycleOwner)` pattern in `DashboardScreen.kt`
**Impact:** Uses the verbose `DisposableEffect` + `LifecycleEventObserver` combo to observe lifecycle events. On `lifecycle-runtime-compose` 2.8+ (you are on 2.10.0), `LifecycleStartEffect` / `LifecycleResumeEffect` are the modern, half-the-code replacements.

| File | Line | Old pattern |
|------|------|-------------|
| `DashboardScreen.kt` | 179 | `DisposableEffect(lifecycleOwner) { val observer = LifecycleEventObserver { _, event -> if (event == ON_RESUME) ... }` |

**Fix:**
```kotlin
LifecycleResumeEffect(lifecycleOwner) {
    viewModel.refreshIfStale()
    onPauseOrDispose { }
}
```
**References:** https://developer.android.com/reference/kotlin/androidx/lifecycle/compose/package-summary

---

#### E2. Broad `LaunchedEffect(Unit)` keys for one-shot work
**Impact:** Several `LaunchedEffect(Unit)` blocks perform fire-and-forget initialization. While not inherently wrong, they silently re-fire after process death + restoration if the surrounding composable is still in the backstack. Keying on the actual changing identity (e.g., `userId`, `screenRoute`) is safer.

**Moderate-risk sites:**
- `MainActivity.kt:214` — `LaunchedEffect(Unit)` for Firebase remote-config fetch
- `MainActivity.kt:243` — `LaunchedEffect(Unit)` for sideload check
- `MainActivity.kt:251` — `LaunchedEffect(Unit)` for update check
- `MainActivity.kt:300` — `LaunchedEffect(Unit)` for battery optimization dialog
- `DashboardScreen.kt:147` — `LaunchedEffect(Unit)` for deep-link handling
- `DashboardScreen.kt:172` — `LaunchedEffect(Unit)` for version check

**References:** https://developer.android.com/develop/ui/compose/side-effects#launchedeffect

---

#### E3. `LaunchedEffect(uiState.messages.size, uiState.isGenerating, uiState.streamingText)` in `LiteRtScreen.kt`
**Impact:** Three separate state fields in one `LaunchedEffect` key. Any change to any field restarts the effect. If the effect is scrolling a list to bottom, `streamingText` changing every token causes redundant scroll coroutines.

| File | Line | Key |
|------|------|-----|
| `LiteRtScreen.kt` | 68 | `LaunchedEffect(uiState.messages.size, uiState.isGenerating, uiState.streamingText)` |

**Fix:** Split into two effects: one keyed on `messages.size` for scroll-to-bottom, one keyed on `isGenerating` for focus/IME management.
**References:** https://developer.android.com/develop/ui/compose/side-effects#launchedeffect

---

## Category: Composable API Quality (4/10)

### Deductions

#### A1. Zero `@Preview` coverage
**Impact:** No composable in the `:app` module has a `@Preview` annotation. UI iterations require full app build + deploy; designers and QA cannot verify components in isolation.

**Files with 0 previews:** All 50+ `@Composable` files inspected.

**Fix:** Add `@Preview` to reusable components and screen-level composables (using `PreviewParameterProvider` for state variants). Start with:
- `GlassComponents.kt` (design-system primitives)
- `DashboardScreen.kt` (highest-traffic screen)
- `LoginScreen.kt` (first-run experience)

**References:** https://developer.android.com/develop/ui/compose/tooling/previews

---

#### A2. Hardcoded strings / missing i18n
**Impact:** English literals embedded throughout UI make future localization impossible and complicate testing.

**Representative samples:**

| File | Line | String |
|------|------|--------|
| `MainActivity.kt` | 272 | `"Update Required"` |
| `MainActivity.kt` | 287 | `"Update Now"` |
| `MainActivity.kt` | 328 | `"Open Play Store"` |
| `MainActivity.kt` | 392 | `"Check Again"` |
| `MainActivity.kt` | 446 | `"Restart"` |
| `MainActivity.kt` | 461 | `"Later"` |
| `LoginScreen.kt` | 80 | `"JustPass"` |
| `LoginScreen.kt` | 82 | `"PSG iTech"` |
| `LoginScreen.kt` | 88 | `"Roll Number"` |
| `LoginScreen.kt` | (nearby) | `"Password"` |
| `ChessScreen.kt` | (dialogs) | `"Name Setup"`, `"Leaderboard"`, etc. |

**Fix:** Extract to `res/values/strings.xml` and consume via `stringResource(R.string.xxx)`.
**References:** https://developer.android.com/guide/topics/resources/string-resource

---

#### A3. Hardcoded colors / dark-mode regression risk
**Impact:** `Color.White`, `Color.Black`, and raw ARGB literals over theme-derived backgrounds break in dark mode or when dynamic color is enabled.

**High-volume sites:**
- `MainActivity.kt`: dialogs use `Color(0xFF1E2A3A)` container + `Color.White` text
- `LeaderboardScreen.kt`: `Color.White.copy(alpha = ...)`, `Color.White` icons
- `ChessScreen.kt`: `Color.White` text in dialogs
- `Widget`: `Color(0xFF1A1A1A)`, `Color(0xFFFF3B30)`, etc. (widget has limited theming, but should still use `ColorProvider` consistently)

**Fix:** Read from `MaterialTheme.colorScheme` roles (`onSurface`, `surfaceVariant`, `primary`, `onPrimary`, etc.). For brand colors, define them in the theme and expose `onBrand` pairs.
**References:** https://developer.android.com/develop/ui/compose/designsystems/material3

---

#### A4. Missing `modifier: Modifier = Modifier` parameter
**Impact:** Reusable components and even some screens do not accept an external `Modifier`, violating the AndroidX component guideline and making layout adjustments from callers impossible.

**Affected composables (partial list):**

| File | Composable | Issue |
|------|------------|-------|
| `GlassComponents.kt` | `AnimatedHomeIcon` | No modifier param |
| `GlassComponents.kt` | `AnimatedCalendarIcon` | No modifier param |
| `GlassComponents.kt` | `AnimatedCalculatorIcon` | No modifier param |
| `GlassComponents.kt` | `AnimatedStarIcon` | No modifier param |
| `GlassComponents.kt` | `AnimatedControllerIcon` | No modifier param |
| `GlassComponents.kt` | `AnimatedChessIcon` | No modifier param; has `size` but no `modifier` |
| `GlassComponents.kt` | `RoseFourLoader` | Has `modifier` ✓ (good example) |
| `LeaderboardScreen.kt` | `LeaderboardScreen` | No modifier param |
| `HomeScreen.kt` | `GamesHomeScreen` | No modifier param |

**Fix:** Add `modifier: Modifier = Modifier` as the first optional parameter after required data params.
**References:** https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier

---

#### A5. Scaffold / `LiquidGlassScaffold` content ignoring inner padding
**Impact:** Content draws behind system bars, bottom nav, and keyboard insets because the padding lambda parameter is discarded (`_`).

| File | Line | Pattern |
|------|------|---------|
| `LoginScreen.kt` | 75 | `LiquidGlassScaffold { _ ->` |
| `PrivacyPolicyScreen.kt` | 20 | `LiquidGlassScaffold { _ ->` |
| `GameScaffold.kt` | 124 | `GameScaffold(...) { _ ->` |
| `MainActivity.kt` | 663 | `LiquidGlassScaffold { ...` (need to verify propagation) |

**Fix:**
```kotlin
LiquidGlassScaffold { innerPadding ->
    Column(Modifier.padding(innerPadding)) { ... }
}
```
**References:** https://developer.android.com/develop/ui/compose/layouts/insets

---

#### A6. Parameter order violations in private composables
**Impact:** Data parameters should precede `modifier`, which should precede lambda slots.

| File | Composable | Issue |
|------|------------|-------|
| `ChessScreen.kt` | `StatChip(label, value, color)` | OK, no modifier — should add one |
| `ChessScreen.kt` | `PlayerCard(...)` | Large param list; verify `modifier` placement |
| `LeaderboardScreen.kt` | `FilterChip(text, active)` | `active` is boolean state; should follow `modifier` |

---

## Performance Ceiling Check

| Check | Value |
|-------|-------|
| Strong Skipping | ON (assumed, Kotlin 2.3.0 default) |
| Ceiling table | SSM-on (qualitative) |
| Module-wide skippable% | Unknown — requires compiler report |
| Named-only skippable% | Unknown — requires compiler report |
| Unstable shared-type count | Unknown |
| Binding cap | N/A (source-inferred) |
| Applied Performance score | 5 (raw) / capped at 7 |

---

## Prioritized Fixes

### 1. `collectAsState` → `collectAsStateWithLifecycle` across 24 call sites
- **Files:** Every screen listed in S1.
- **Doc:** https://developer.android.com/develop/ui/compose/side-effects
- **Impact:** Stops redundant flow collection when UI is paused; lifecycle-correct; zero behavior change when foregrounded.
- **Effort:** Low (global find/replace + add import).

### 2. Add stable `key =` to 12 lazy-list `items(...)` factories
- **Files:** `BugReportInboxScreen.kt`, `BugReportScreen.kt`, `CAMarksScreen.kt`, `ChessScreen.kt` (×2), `ExemptionsScreen.kt`, `ManageAdminsScreen.kt` (×2), `ProfileScreen.kt`, `SubjectAttendanceScreen.kt`, `TournamentApprovalScreen.kt`, `TimetableScreen.kt`
- **Doc:** https://developer.android.com/develop/ui/compose/lists#item-keys
- **Impact:** Fewer reallocated compositions on scroll/reorder; eliminates `Key already used` crash surface.
- **Effort:** Low.

### 3. Remove unnecessary `AndroidViewModel` + centralize hardcoded strings & colors
- **Files:** 18 ViewModels; `MainActivity.kt`; all game screens; `LeaderboardScreen.kt`; `ChessScreen.kt`
- **Doc:** https://developer.android.com/topic/libraries/architecture/viewmodel, https://developer.android.com/guide/topics/resources/string-resource
- **Impact:** Unit-testable ViewModels; i18n-ready; dark-mode-safe.
- **Effort:** Medium (structural).

---

## Scope Notes

- **Accessibility:** Not scored (out of scope for v1.x). Touch targets and semantics were not audited.
- **Material 3 compliance:** Not scored (theming is custom glass aesthetic; out of scope).
- **UI tests:** Not scored. No `ComposeTestRule` usage was checked.
- **Build performance:** Not scored.
- **Compose Multiplatform:** Not applicable (Android-only codebase).

---

## How to Re-run with Compiler Metrics

1. Ensure Android SDK is installed and `ANDROID_HOME` is set.
2. Run:
   ```bash
   ./gradlew :app:assembleRelease \
     --init-script jetpack-compose-audit/scripts/compose-reports.init.gradle
   ```
3. The script writes metrics to `app/build/compose_metrics/`.
4. Re-run the audit skill pointing at the module path to lift the Performance ceiling and get measured `skippable%`.

---

*Report generated by `jetpack-compose-audit` 2.1.1 source-inferred analysis.*
