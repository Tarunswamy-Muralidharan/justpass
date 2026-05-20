package com.justpass.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.justpass.app.data.analytics.Analytics
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.data.repository.AttendanceRepository
import com.justpass.app.data.update.UpdateChecker
import com.justpass.app.data.update.UpdateInfo
import com.justpass.app.ui.components.GlassCardFallback
import com.justpass.app.ui.components.LiquidGlassBottomBar
import com.justpass.app.ui.components.LiquidGlassScaffold
import com.justpass.app.ui.components.PixelWipeOverlay
import com.justpass.app.ui.components.TabItemData
import com.justpass.app.ui.screens.AbsentDaysScreen
import com.justpass.app.ui.screens.CAMarksScreen
import com.justpass.app.ui.screens.DashboardScreen
import com.justpass.app.ui.screens.LoginScreen
import com.justpass.app.ui.screens.ProfileScreen
import com.justpass.app.ui.screens.ExemptionsScreen
import com.justpass.app.ui.screens.SubjectAttendanceScreen
import com.justpass.app.ui.screens.ResultScreen
import com.justpass.app.ui.screens.SubjectDetailScreen
import com.justpass.app.ui.screens.TimetableScreen
import com.justpass.app.ui.screens.AcademicCalendarScreen
import com.justpass.app.ui.screens.CgpaCalculatorScreen
import com.justpass.app.ui.screens.CircularsScreen
import com.justpass.app.ui.screens.ChessScreen
import com.justpass.app.ui.screens.ExamSeatScreen
import com.justpass.app.ui.screens.SyllabusScreen
import com.justpass.app.ui.theme.AttendanceWidgetLaudeaTheme
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.justpass.app.worker.AttendanceRefreshWorker
import com.justpass.app.worker.CircularNotificationWorker
import com.justpass.app.worker.HolidayNotificationWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Analytics.init(this)
        // Crashlytics: disabled in debug so dev-time crashes don't pollute the
        // production crash list. Release builds opt-in by default.
        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(!com.justpass.app.BuildConfig.DEBUG)
        // Group crashes per user using the same hashed playerId pattern that
        // chess uses (p_${rollHash}). Lets us see "47 users affected" in the
        // Crashlytics console without leaking the raw roll number.
        try {
            val roll = com.justpass.app.data.local.SecurePreferences
                .getInstance(this).rollNumber
            if (!roll.isNullOrBlank()) {
                val pid = "p_${kotlin.math.abs(roll.hashCode()).toString(16)}"
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                    .setUserId(pid)

                // Live-load the dynamic admin set from Firestore. The
                // hardcoded bootstrap admin (TournamentAdmins.HARDCODED_PLAYER_IDS)
                // always works even if this listener never fires — so we never
                // get locked out. New admin doc -> isAdmin() flips on next read.
                val adminRepo = com.justpass.app.data.repository.AdminRolesRepository()
                adminRepo.listenAdminPlayerIds { ids ->
                    com.justpass.app.data.model.TournamentAdmins.setDynamicAdmins(ids)
                }
                // If the current user is in admin_roles but their Firebase UID
                // isn't yet mirrored in admin_uids (which the rules engine
                // exists()-checks), self-register. Lets newly-added admins
                // start writing on their first app open.
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { adminRepo.registerSelfUidIfAdmin(pid) }
                }
            }
        } catch (_: Exception) {}
        com.justpass.app.ui.components.AdConfig.init(this)
        MobileAds.initialize(this) {
            com.justpass.app.ui.components.InterstitialAdManager.preload(this)
            // Pre-warm a banner AdView before any screen mounts one — first
            // navigation to a screen that hosts an AdBanner pulls the ready
            // creative from the pool instead of waiting for a fresh AdRequest.
            val widthDp = resources.configuration.screenWidthDp
            val adSize = com.google.android.gms.ads.AdSize
                .getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, widthDp)
                ?: com.google.android.gms.ads.AdSize.BANNER
            com.justpass.app.ui.components.AdBannerPool.preload(this, adSize, widthDp)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
        // v2.0: Clear hardcoded timetable config so each user fetches their own nodeId
        val prefs = SecurePreferences.getInstance(this)
        if (prefs.timetableConfigId == "65d6ee42722e1e6d3ed430b0") {
            prefs.timetableConfigId = null
        }
        AttendanceRefreshWorker.schedulePeriodicRefresh(this)
        CircularNotificationWorker.schedule(this)
        HolidayNotificationWorker.schedule(this)
        com.justpass.app.worker.ClassMarksUploadWorker.schedule(this)
        setContent {
            AttendanceWidgetLaudeaTheme {
                AttendanceApp()
            }
        }
    }
}

enum class Screen {
    Login, Dashboard, AbsentDays, SubjectAttendance, SubjectDetail, Exemptions, Result, PrivacyPolicy, CAMarks, ClassCompare, Timetable, Profile, AcademicCalendar, Circulars, CgpaCalculator, ExamSeat, Syllabus, Chess, Games, GamesLeaderboard, LiteRt, CreateTournament, TournamentApproval, BugReport, BugReportInbox, ManageAdmins
}

private val bottomTabs = listOf(
    TabItemData("Home", Icons.Default.Home),
    TabItemData("CA Marks", Icons.Default.Star),
    TabItemData("Games", Icons.Default.SportsEsports),
    TabItemData("GPA", Icons.Default.Calculate),
    TabItemData("Timetable", Icons.Default.DateRange)
)

@Composable
fun AttendanceApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = AttendanceRepository.getInstance(context)
    val securePrefs = SecurePreferences.getInstance(context)
    val scope = rememberCoroutineScope()

    val isLoggedIn = repository.isLoggedIn()
    // Handle navigate_to from notification intents and shared file intents
    val activity = context as? ComponentActivity
    val navigateTo = remember { activity?.intent?.getStringExtra("navigate_to") }
    val sharedFileUri = remember {
        activity?.intent?.let { intent ->
            when (intent.action) {
                Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                Intent.ACTION_VIEW -> intent.data
                else -> null
            }
        }
    }
    val initialScreen = if (!isLoggedIn) Screen.Login
        else if (sharedFileUri != null) Screen.ExamSeat
        else when (navigateTo) {
            "calendar" -> Screen.AcademicCalendar
            "circulars" -> Screen.Circulars
            else -> Screen.Dashboard
        }
    var currentScreen by remember { mutableStateOf(initialScreen) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedCourseCode by remember { mutableStateOf("") }
    var selectedCourseTitle by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf(securePrefs.displayName ?: "") }

    LaunchedEffect(Unit) {
        Analytics.logAppOpen()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        Analytics.logAppVersion(packageInfo.versionName ?: "unknown")
        Analytics.logInstallSource()
        if (repository.isLoggedIn()) {
            val rollNumber = repository.getRollNumber()
            val token = repository.getCachedToken()
            val name = token?.let { Analytics.extractNameFromToken(it) }
            rollNumber?.let { Analytics.setUser(it, name) }
            if (name != null) {
                displayName = name
                securePrefs.displayName = name
            }
            // Fix cachedDepartment from programmeName (more specific than API department field)
            // No network calls here — just re-detect from already-stored data
            securePrefs.programmeName?.let { prog ->
                val detected = com.justpass.app.data.model.detectDepartment(prog)
                if (detected != null) securePrefs.cachedDepartment = detected.shortName
            }
        }
    }

    LaunchedEffect(currentScreen) { Analytics.logScreenView(currentScreen.name) }

    var loginScreenKey by remember { mutableIntStateOf(0) }
    var isLoggingOut by remember { mutableStateOf(false) }

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val currentVersion = packageInfo.versionName ?: "1.0"
        updateInfo = UpdateChecker.checkForUpdate(currentVersion)
    }

    // Force update check via Firebase Remote Config
    var forceUpdate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            remoteConfig.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 })
            remoteConfig.setDefaultsAsync(mapOf("min_version_code" to 1L))
            remoteConfig.fetchAndActivate().addOnCompleteListener {
                val minVersion = remoteConfig.getLong("min_version_code")
                val currentCode = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).let {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) it.longVersionCode
                        else @Suppress("DEPRECATION") it.versionCode.toLong()
                    }
                } catch (_: Exception) { Long.MAX_VALUE }
                if (currentCode < minVersion) forceUpdate = true
            }
        } catch (_: Exception) {}
    }

    if (forceUpdate) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Update Required", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White) },
            text = { Text("A new version of JustPass is available. Please update to continue using the app.",
                color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = {
                Button(
                    onClick = {
                        updateInfo?.let { info ->
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)))
                        } ?: run {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/Tarunswamy-Muralidharan/-AttendanceWidgetLaudea/releases/latest")))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                ) { Text("Update Now", color = Color.White) }
            },
            containerColor = Color(0xFF1E2A3A),
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
        return
    }

    // Sideload gate: block non-Play-Store installs when `sideload_block_enabled` is true in
    // Remote Config. Debug builds and Play Store installs always bypass. Flip the flag in
    // Firebase Console once Play listing is live to push sideloaded users to reinstall from
    // Play (certified traffic pays higher AdMob eCPM).
    var sideloadBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val isDebuggable = (context.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) return@LaunchedEffect
        val installer = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (_: Exception) { null }
        if (installer == "com.android.vending") return@LaunchedEffect
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            remoteConfig.setDefaultsAsync(mapOf("sideload_block_enabled" to false))
            remoteConfig.fetchAndActivate().addOnCompleteListener {
                if (remoteConfig.getBoolean("sideload_block_enabled")) sideloadBlocked = true
            }
        } catch (_: Exception) {}
    }

    if (sideloadBlocked) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Install from Play Store",
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White) },
            text = { Text("JustPass is now on Google Play. Please install it from the Play Store to keep using the app and get automatic updates.",
                color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = {
                Button(
                    onClick = {
                        val pkg = context.packageName
                        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                            .setPackage("com.android.vending")
                        try {
                            context.startActivity(marketIntent)
                        } catch (_: Exception) {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                ) { Text("Open Play Store", color = Color.White) }
            },
            containerColor = Color(0xFF1E2A3A),
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
        return
    }

    // Maintenance / announcement dialog: fully controlled from Firebase Remote Config.
    // Flip `maintenance_enabled` to true and edit `maintenance_message` in Firebase Console
    // to broadcast a blocking notice to every user (outages, SIS-down, etc.). "Check Again"
    // re-runs the fetch — if the flag is still on, the dialog re-shows.
    var maintenanceMessage by remember { mutableStateOf<String?>(null) }
    var maintenanceRetryKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(maintenanceRetryKey) {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            remoteConfig.setDefaultsAsync(mapOf(
                "maintenance_enabled" to false,
                "maintenance_message" to ""
            ))
            val fetchTask = if (maintenanceRetryKey == 0) {
                remoteConfig.fetchAndActivate()
            } else {
                // Force-fresh fetch on Retry by bypassing the 1h cache
                remoteConfig.fetch(0L).continueWithTask { remoteConfig.activate() }
            }
            fetchTask.addOnCompleteListener {
                val enabled = remoteConfig.getBoolean("maintenance_enabled")
                val msg = remoteConfig.getString("maintenance_message").ifBlank {
                    "The app is currently under maintenance. Please try again later."
                }
                maintenanceMessage = if (enabled) msg else null
            }
        } catch (_: Exception) {}
    }

    maintenanceMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Notice",
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White) },
            text = { Text(msg, color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = {
                Button(
                    onClick = { maintenanceRetryKey++ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                ) { Text("Check Again", color = Color.White) }
            },
            containerColor = Color(0xFF1E2A3A),
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
        return
    }

    var showBatteryDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            showBatteryDialog = true
        }
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text("Keep Widget Updated") },
            text = { Text("To keep your attendance widget updated in the background, please allow unrestricted battery usage for this app.") },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    })
                    showBatteryDialog = false
                }) { Text("Allow") }
            },
            dismissButton = { TextButton(onClick = { showBatteryDialog = false }) { Text("Later") } },
            containerColor = Color(0xFF1E2A3A)
        )
    }

    updateInfo?.let { update ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("Update Available") },
            text = {
                Column {
                    Text("Version ${update.versionName} is available!")
                    if (!update.releaseNotes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = update.releaseNotes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)))
                    updateInfo = null
                }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { updateInfo = null }) { Text("Later") } },
            containerColor = Color(0xFF1E2A3A)
        )
    }

    if (isLoggingOut) {
        Dialog(onDismissRequest = { }) {
            GlassCardFallback(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Logging out...")
                }
            }
        }
    }

    val handleLogout: () -> Unit = {
        val activity = context as? ComponentActivity
        scope.launch {
            isLoggingOut = true
            Analytics.logLogout()
            Analytics.clearUser()
            repository.logout()
            delay(1000)
            activity?.finishAffinity()
        }
    }

    when (currentScreen) {
        Screen.Login -> {
            key(loginScreenKey) {
                LoginScreen(onLoginSuccess = {
                    val token = repository.getCachedToken()
                    val name = token?.let { Analytics.extractNameFromToken(it) }
                    if (name != null) { displayName = name; securePrefs.displayName = name }
                    currentScreen = Screen.Dashboard
                })
            }
        }
        Screen.PrivacyPolicy -> {
            com.justpass.app.ui.screens.PrivacyPolicyScreen(onBack = {
                currentScreen = Screen.Profile
                selectedTabIndex = 0
            })
        }
        else -> {
            var gamesPopupOpen by remember { mutableStateOf(false) }
            var wipeState by remember { mutableIntStateOf(0) } // 0=hidden, 1=expanding, 2=contracting
            var wipeOriginX by remember { mutableFloatStateOf(0.5f) }
            var wipeOriginY by remember { mutableFloatStateOf(0.5f) }

            fun launchHB(ox: Float = 0.5f, oy: Float = 0.5f) {
                wipeOriginX = ox
                wipeOriginY = oy
                wipeState = 1
                Analytics.logFeatureUsed("human_benchmark")
                scope.launch {
                    // Wipe runs ~900 ms. By 700 ms the cell mask is mostly
                    // full (Games is essentially visible everywhere inside
                    // the overlay), so we kick off the Crossfade swap so the
                    // underlying Crossfade has settled to Games by the time
                    // the overlay unmounts — no end-of-wipe pop.
                    kotlinx.coroutines.delay(700)
                    currentScreen = Screen.Games
                    kotlinx.coroutines.delay(400)
                    wipeState = 0
                }
            }

            fun closeHB() {
                // Swap the underlying Crossfade to Dashboard first, then
                // start the contracting wipe. The overlay's mask renders
                // Games at full coverage at progress 0, so the underlying
                // Crossfade transition (Games → Dashboard, 200 ms) is hidden
                // behind the mask while it shrinks back to the origin.
                currentScreen = Screen.Dashboard
                selectedTabIndex = 0
                wipeState = 2
                scope.launch {
                    kotlinx.coroutines.delay(700)
                    wipeState = 0
                }
            }

            // Handle system back button: navigate back within app instead of exiting
            BackHandler(enabled = gamesPopupOpen || wipeState != 0 || currentScreen != Screen.Dashboard || selectedTabIndex != 0) {
                when {
                    // Games popup eats back press first
                    gamesPopupOpen -> gamesPopupOpen = false
                    // Sub-screens go back to dashboard
                    currentScreen == Screen.SubjectDetail -> currentScreen = Screen.SubjectAttendance
                    currentScreen == Screen.PrivacyPolicy -> {
                        currentScreen = Screen.Profile
                        selectedTabIndex = 0
                    }
                    currentScreen != Screen.Dashboard -> {
                        currentScreen = Screen.Dashboard
                        selectedTabIndex = 0
                    }
                    // Non-home tabs go back to home tab
                    selectedTabIndex != 0 -> {
                        selectedTabIndex = 0
                        currentScreen = Screen.Dashboard
                    }
                }
            }
            // Dual-state: cardState for card refraction, barState for bottom bar blur
            var weatherScene by remember {
                mutableStateOf(com.justpass.app.ui.components.WeatherScene.fromString(securePrefs.weatherScene))
            }
            var autoWeatherEnabled by remember {
                mutableStateOf(securePrefs.autoWeatherEnabled)
            }

            // Refresh scene from cached prefs (in case background fetch updated it).
            val refreshSceneFromPrefs: () -> Unit = {
                val cached = com.justpass.app.data.repository.WeatherRepository
                    .getCachedWeatherScene(context)
                if (cached != null && cached != weatherScene) {
                    weatherScene = cached
                    securePrefs.weatherScene = cached.name
                }
            }

            // Run an auto-weather fetch loop while enabled — first run on switch
            // flip, repeats every cache window (1 h) plus on app foreground.
            LaunchedEffect(autoWeatherEnabled) {
                if (!autoWeatherEnabled) return@LaunchedEffect
                while (true) {
                    val fetched = com.justpass.app.data.repository.WeatherRepository
                        .fetchAndStoreWeather(context)
                    if (fetched != null) {
                        weatherScene = fetched
                        securePrefs.weatherScene = fetched.name
                    }
                    kotlinx.coroutines.delay(60L * 60L * 1000L)
                }
            }
            LiquidGlassScaffold(
                weatherScene = weatherScene,
                bottomBar = { barState ->
                    LiquidGlassBottomBar(
                        barState = barState,
                        tabs = bottomTabs,
                        selectedIndex = selectedTabIndex,
                        onTabSelected = { index ->
                            selectedTabIndex = index
                            currentScreen = when (index) {
                                0 -> Screen.Dashboard
                                1 -> Screen.CAMarks
                                3 -> Screen.CgpaCalculator
                                4 -> Screen.Timetable
                                else -> Screen.Dashboard
                            }
                            Analytics.logFeatureUsed(bottomTabs[index].label.lowercase())
                        },
                        onCenterTap = {
                            gamesPopupOpen = !gamesPopupOpen
                            Analytics.logFeatureUsed("games_popup_toggle")
                        },
                        centerSelected = gamesPopupOpen,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            ) { cardState ->
                Crossfade(
                    targetState = if (currentScreen in listOf(Screen.AbsentDays, Screen.SubjectAttendance, Screen.SubjectDetail, Screen.Exemptions, Screen.Result, Screen.AcademicCalendar, Screen.Circulars, Screen.CgpaCalculator, Screen.ExamSeat, Screen.Syllabus, Screen.Chess, Screen.Games, Screen.GamesLeaderboard, Screen.Profile, Screen.LiteRt, Screen.CreateTournament, Screen.TournamentApproval, Screen.BugReport, Screen.BugReportInbox, Screen.ManageAdmins)) currentScreen.name
                                  else "tab_$selectedTabIndex",
                    animationSpec = tween(200),
                    label = "screenFade"
                ) { target ->
                    when (target) {
                        Screen.AbsentDays.name -> AbsentDaysScreen(onBack = {
                            currentScreen = Screen.Dashboard
                            selectedTabIndex = 0
                        })
                        Screen.SubjectAttendance.name -> SubjectAttendanceScreen(
                            cardState = cardState,
                            onBack = {
                                currentScreen = Screen.Dashboard
                                selectedTabIndex = 0
                            },
                            onSubjectClick = { code, title ->
                                selectedCourseCode = code
                                selectedCourseTitle = title
                                currentScreen = Screen.SubjectDetail
                            }
                        )
                        Screen.SubjectDetail.name -> SubjectDetailScreen(
                            cardState = cardState,
                            courseCode = selectedCourseCode,
                            courseTitle = selectedCourseTitle,
                            onBack = {
                                currentScreen = Screen.SubjectAttendance
                            }
                        )
                        Screen.Exemptions.name -> ExemptionsScreen(
                            cardState = cardState,
                            onBack = {
                                currentScreen = Screen.Dashboard
                                selectedTabIndex = 0
                            }
                        )
                        Screen.Result.name -> ResultScreen(
                            cardState = cardState,
                            onBack = {
                                currentScreen = Screen.Dashboard
                                selectedTabIndex = 0
                            }
                        )
                        Screen.AcademicCalendar.name -> AcademicCalendarScreen(
                            cardState = cardState,
                            onBack = {
                                currentScreen = Screen.Dashboard
                                selectedTabIndex = 0
                            }
                        )
                        Screen.Circulars.name -> CircularsScreen(
                            cardState = cardState,
                            onBack = {
                                currentScreen = Screen.Dashboard
                                selectedTabIndex = 0
                            }
                        )
                        Screen.CgpaCalculator.name -> {
                            val batch = securePrefs.batchYear.takeIf { it > 0 }
                                ?: securePrefs.rollNumber?.drop(4)?.take(2)?.toIntOrNull()?.let { 2000 + it }
                            val detectedDept = com.justpass.app.data.model.detectDepartment(
                                securePrefs.cachedDepartment
                            ) ?: com.justpass.app.data.model.detectDepartment(
                                securePrefs.programmeName
                            )
                            CgpaCalculatorScreen(
                                onBack = {
                                    currentScreen = Screen.Dashboard
                                    selectedTabIndex = 0
                                },
                                userDepartment = detectedDept,
                                userBatchYear = batch,
                                cardState = cardState,
                            )
                        }
                        Screen.Syllabus.name -> {
                            val detectedDept = com.justpass.app.data.model.detectDepartment(
                                securePrefs.cachedDepartment
                            ) ?: com.justpass.app.data.model.detectDepartment(
                                securePrefs.programmeName
                            )
                            val batch = securePrefs.batchYear.takeIf { it > 0 }
                                ?: securePrefs.rollNumber?.drop(4)?.take(2)?.toIntOrNull()?.let { 2000 + it }
                            val regulation = com.justpass.app.data.model.getRegulationForBatch(batch ?: 2021)
                            SyllabusScreen(
                                cardState = cardState,
                                userDepartment = detectedDept,
                                userRegulation = regulation,
                                onBack = {
                                    currentScreen = Screen.Dashboard
                                    selectedTabIndex = 0
                                }
                            )
                        }
                        Screen.Chess.name -> ChessScreen(
                            cardState = cardState,
                            onBack = {
                                currentScreen = Screen.Dashboard
                                selectedTabIndex = 0
                            },
                            onCreateTournament = { currentScreen = Screen.CreateTournament }
                        )
                        Screen.CreateTournament.name -> com.justpass.app.ui.screens.CreateTournamentScreen(
                            onBack = { currentScreen = Screen.Chess }
                        )
                        Screen.TournamentApproval.name -> com.justpass.app.ui.screens.TournamentApprovalScreen(
                            onBack = { currentScreen = Screen.Profile }
                        )
                        Screen.BugReport.name -> com.justpass.app.ui.screens.BugReportScreen(
                            onBack = { currentScreen = Screen.Profile }
                        )
                        Screen.BugReportInbox.name -> com.justpass.app.ui.screens.BugReportInboxScreen(
                            onBack = { currentScreen = Screen.Profile }
                        )
                        Screen.ManageAdmins.name -> com.justpass.app.ui.screens.ManageAdminsScreen(
                            onBack = { currentScreen = Screen.Profile }
                        )
                        Screen.LiteRt.name -> com.justpass.app.ui.screens.LiteRtScreen(
                            onBack = {
                                currentScreen = Screen.Dashboard
                                selectedTabIndex = 0
                            },
                            onNavigate = { action ->
                                currentScreen = when (action) {
                                    com.justpass.app.data.model.NavAction.SUBJECT_ATTENDANCE -> Screen.SubjectAttendance
                                    com.justpass.app.data.model.NavAction.ABSENT_DAYS -> Screen.AbsentDays
                                    com.justpass.app.data.model.NavAction.CA_MARKS -> Screen.CAMarks
                                    com.justpass.app.data.model.NavAction.EXEMPTIONS -> Screen.Exemptions
                                    com.justpass.app.data.model.NavAction.RESULTS -> Screen.Result
                                    com.justpass.app.data.model.NavAction.GPA_CALCULATOR -> Screen.CgpaCalculator
                                    com.justpass.app.data.model.NavAction.TIMETABLE -> { selectedTabIndex = 4; Screen.Dashboard }
                                    com.justpass.app.data.model.NavAction.CALENDAR -> Screen.AcademicCalendar
                                    com.justpass.app.data.model.NavAction.CIRCULARS -> Screen.Circulars
                                    com.justpass.app.data.model.NavAction.SYLLABUS -> Screen.Syllabus
                                }
                            }
                        )
                        Screen.ExamSeat.name -> ExamSeatScreen(
                            cardState = cardState,
                            onBack = {
                                currentScreen = Screen.Dashboard
                                selectedTabIndex = 0
                            },
                            sharedFileUri = sharedFileUri
                        )
                        "tab_0" -> DashboardScreen(
                            cardState = cardState,
                            displayName = displayName,
                            onLogout = handleLogout,
                            onAbsentDaysClick = {
                                Analytics.logFeatureUsed("absent_days")
                                currentScreen = Screen.AbsentDays
                            },
                            onSubjectAttendanceClick = {
                                Analytics.logFeatureUsed("subject_attendance")
                                currentScreen = Screen.SubjectAttendance
                            },
                            onExemptionsClick = {
                                Analytics.logFeatureUsed("exemptions")
                                currentScreen = Screen.Exemptions
                            },
                            onResultClick = {
                                Analytics.logFeatureUsed("result")
                                currentScreen = Screen.Result
                            },
                            onCalendarClick = {
                                Analytics.logFeatureUsed("academic_calendar")
                                currentScreen = Screen.AcademicCalendar
                            },
                            onCircularsClick = {
                                Analytics.logFeatureUsed("circulars")
                                currentScreen = Screen.Circulars
                            },
                            onCgpaClick = {
                                Analytics.logFeatureUsed("cgpa_calculator")
                                currentScreen = Screen.CgpaCalculator
                            },
                            onExamSeatClick = {
                                Analytics.logFeatureUsed("exam_seat")
                                currentScreen = Screen.ExamSeat
                            },
                            onSyllabusClick = {
                                Analytics.logFeatureUsed("syllabus")
                                currentScreen = Screen.Syllabus
                            },
                            onChessClick = {
                                Analytics.logFeatureUsed("chess")
                                currentScreen = Screen.Chess
                            },
                            onHumanBenchmarkClick = {
                                wipeOriginX = 0.5f
                                wipeOriginY = 0.32f
                                launchHB()
                            },
                            onLiteRtClick = {
                                currentScreen = Screen.LiteRt
                            },
                            onProfileClick = {
                                Analytics.logFeatureUsed("profile")
                                currentScreen = Screen.Profile
                            }
                        )
                        "tab_1" -> CAMarksScreen(
                            cardState = cardState,
                            onBack = {
                                selectedTabIndex = 0
                                currentScreen = Screen.Dashboard
                            },
                            onClassCompareClick = { currentScreen = Screen.ClassCompare },
                        )
                        Screen.ClassCompare.name -> com.justpass.app.ui.screens.ClassCompareScreen(
                            cardState = cardState,
                            onBack = {
                                currentScreen = Screen.CAMarks
                                selectedTabIndex = 1
                            },
                        )
                        // tab_2 (Games) and tab_3 (GPA) are routed via screen name
                        "tab_2" -> {}
                        "tab_3" -> {}
                        Screen.Games.name -> com.justpass.app.games.ui.screens.GamesNav(
                            onBack = { currentScreen = Screen.Dashboard; selectedTabIndex = 0 },
                            onLeaderboard = { currentScreen = Screen.GamesLeaderboard }
                        )
                        Screen.GamesLeaderboard.name -> com.justpass.app.games.ui.screens.LeaderboardScreen(
                            onBack = { currentScreen = Screen.Games }
                        )
                        "tab_4" -> TimetableScreen(cardState = cardState)
                        // Profile accessed via header profile pic
                        Screen.Profile.name -> ProfileScreen(
                            cardState = cardState,
                            displayName = displayName,
                            onLogout = handleLogout,
                            onPrivacyPolicyClick = { currentScreen = Screen.PrivacyPolicy },
                            onTournamentApprovalClick = { currentScreen = Screen.TournamentApproval },
                            onBugReportClick = { currentScreen = Screen.BugReport },
                            onBugReportInboxClick = { currentScreen = Screen.BugReportInbox },
                            onManageAdminsClick = { currentScreen = Screen.ManageAdmins },
                            weatherScene = weatherScene,
                            onWeatherSceneChange = { newScene ->
                                weatherScene = newScene
                                securePrefs.weatherScene = newScene.name
                            },
                            autoWeatherEnabled = autoWeatherEnabled,
                            onAutoWeatherToggle = { enabled ->
                                autoWeatherEnabled = enabled
                                securePrefs.autoWeatherEnabled = enabled
                                // Off → leave the currently-set scene alone so the
                                // user can still tweak via the picker. On → the
                                // LaunchedEffect above fires fetchAndStoreWeather.
                            }
                        )
                    }
                }
                // Games popup — controller tap toggles. Floats above content,
                // below the bottom bar (rendered in scaffold's bottomBar slot).
                com.justpass.app.ui.components.GamesPopup(
                    open = gamesPopupOpen,
                    onDismiss = { gamesPopupOpen = false },
                    onChess = {
                        gamesPopupOpen = false
                        currentScreen = Screen.Chess
                        Analytics.logFeatureUsed("games_popup_chess")
                    },
                    onBrain = {
                        gamesPopupOpen = false
                        launchHB(ox = 0.25f, oy = 0.78f)
                    }
                )
            }
            // Pixel wipe transition overlay — renders Games inside a growing /
            // shrinking pixel mask. The underlying Crossfade shows Dashboard
            // during open and Dashboard during close (after the pre-emptive
            // swap), so the cells act as a real reveal — every cell that
            // turns on uncovers a piece of HB instead of waiting for the
            // whole curtain and then yanking it.
            if (wipeState != 0) {
                PixelWipeOverlay(
                    contracting = wipeState == 2,
                    originX = wipeOriginX,
                    originY = wipeOriginY,
                ) {
                    // Destination screen rendered inside the cell mask. We
                    // pass no-op handlers — the real interactive Games screen
                    // comes from the Crossfade once the wipe lands.
                    com.justpass.app.games.ui.screens.GamesNav(
                        onBack = {},
                        onLeaderboard = {},
                    )
                }
            }
        }
    }
}

