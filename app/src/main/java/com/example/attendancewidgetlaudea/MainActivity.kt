package com.example.attendancewidgetlaudea

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
import com.example.attendancewidgetlaudea.data.analytics.Analytics
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import com.example.attendancewidgetlaudea.data.repository.AttendanceRepository
import com.example.attendancewidgetlaudea.data.update.UpdateChecker
import com.example.attendancewidgetlaudea.data.update.UpdateInfo
import com.example.attendancewidgetlaudea.ui.components.GlassCardFallback
import com.example.attendancewidgetlaudea.ui.components.LiquidGlassBottomBar
import com.example.attendancewidgetlaudea.ui.components.LiquidGlassScaffold
import com.example.attendancewidgetlaudea.ui.components.TabItemData
import com.example.attendancewidgetlaudea.ui.screens.AbsentDaysScreen
import com.example.attendancewidgetlaudea.ui.screens.CAMarksScreen
import com.example.attendancewidgetlaudea.ui.screens.DashboardScreen
import com.example.attendancewidgetlaudea.ui.screens.LoginScreen
import com.example.attendancewidgetlaudea.ui.screens.ProfileScreen
import com.example.attendancewidgetlaudea.ui.screens.ExemptionsScreen
import com.example.attendancewidgetlaudea.ui.screens.SubjectAttendanceScreen
import com.example.attendancewidgetlaudea.ui.screens.ResultScreen
import com.example.attendancewidgetlaudea.ui.screens.SubjectDetailScreen
import com.example.attendancewidgetlaudea.ui.screens.TimetableScreen
import com.example.attendancewidgetlaudea.ui.screens.AcademicCalendarScreen
import com.example.attendancewidgetlaudea.ui.screens.CgpaCalculatorScreen
import com.example.attendancewidgetlaudea.ui.screens.CircularsScreen
import com.example.attendancewidgetlaudea.ui.screens.ChessScreen
import com.example.attendancewidgetlaudea.ui.screens.ExamSeatScreen
import com.example.attendancewidgetlaudea.ui.screens.SyllabusScreen
import com.example.attendancewidgetlaudea.ui.theme.AttendanceWidgetLaudeaTheme
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.example.attendancewidgetlaudea.worker.AttendanceRefreshWorker
import com.example.attendancewidgetlaudea.worker.CircularNotificationWorker
import com.example.attendancewidgetlaudea.worker.HolidayNotificationWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Analytics.init(this)
        com.example.attendancewidgetlaudea.ui.components.AdConfig.init()
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(listOf("7A84F9727359E7E95B313BCCA0FC5DA8"))
                .build()
        )
        MobileAds.initialize(this) {
            com.example.attendancewidgetlaudea.ui.components.InterstitialAdManager.preload(this)
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
        setContent {
            AttendanceWidgetLaudeaTheme {
                AttendanceApp()
            }
        }
    }
}

enum class Screen {
    Login, Dashboard, AbsentDays, SubjectAttendance, SubjectDetail, Exemptions, Result, PrivacyPolicy, CAMarks, Timetable, Profile, AcademicCalendar, Circulars, CgpaCalculator, ExamSeat, Syllabus, Chess, LiteRt
}

private val bottomTabs = listOf(
    TabItemData("Home", Icons.Default.Home),
    TabItemData("CA Marks", Icons.Default.Star),
    TabItemData("Chess", Icons.Default.SportsEsports),
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
                val detected = com.example.attendancewidgetlaudea.data.model.detectDepartment(prog)
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
            com.example.attendancewidgetlaudea.ui.screens.PrivacyPolicyScreen(onBack = {
                currentScreen = Screen.Profile
                selectedTabIndex = 0
            })
        }
        else -> {
            // Handle system back button: navigate back within app instead of exiting
            BackHandler(enabled = currentScreen != Screen.Dashboard || selectedTabIndex != 0) {
                when {
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
            LiquidGlassScaffold(
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
                                2 -> Screen.Chess
                                3 -> Screen.CgpaCalculator
                                4 -> Screen.Timetable
                                else -> Screen.Dashboard
                            }
                            Analytics.logFeatureUsed(bottomTabs[index].label.lowercase())
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            ) { cardState ->
                Crossfade(
                    targetState = if (currentScreen in listOf(Screen.AbsentDays, Screen.SubjectAttendance, Screen.SubjectDetail, Screen.Exemptions, Screen.Result, Screen.AcademicCalendar, Screen.Circulars, Screen.CgpaCalculator, Screen.ExamSeat, Screen.Syllabus, Screen.Chess, Screen.Profile, Screen.LiteRt)) currentScreen.name
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
                            val detectedDept = com.example.attendancewidgetlaudea.data.model.detectDepartment(
                                securePrefs.cachedDepartment
                            ) ?: com.example.attendancewidgetlaudea.data.model.detectDepartment(
                                securePrefs.programmeName
                            )
                            CgpaCalculatorScreen(
                                onBack = {
                                    currentScreen = Screen.Dashboard
                                    selectedTabIndex = 0
                                },
                                userDepartment = detectedDept,
                                userBatchYear = batch
                            )
                        }
                        Screen.Syllabus.name -> {
                            val detectedDept = com.example.attendancewidgetlaudea.data.model.detectDepartment(
                                securePrefs.cachedDepartment
                            ) ?: com.example.attendancewidgetlaudea.data.model.detectDepartment(
                                securePrefs.programmeName
                            )
                            val batch = securePrefs.batchYear.takeIf { it > 0 }
                                ?: securePrefs.rollNumber?.drop(4)?.take(2)?.toIntOrNull()?.let { 2000 + it }
                            val regulation = com.example.attendancewidgetlaudea.data.model.getRegulationForBatch(batch ?: 2021)
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
                            }
                        )
                        Screen.LiteRt.name -> com.example.attendancewidgetlaudea.ui.screens.LiteRtScreen(
                            onBack = {
                                currentScreen = Screen.Dashboard
                                selectedTabIndex = 0
                            },
                            onNavigate = { action ->
                                currentScreen = when (action) {
                                    com.example.attendancewidgetlaudea.data.model.NavAction.SUBJECT_ATTENDANCE -> Screen.SubjectAttendance
                                    com.example.attendancewidgetlaudea.data.model.NavAction.ABSENT_DAYS -> Screen.AbsentDays
                                    com.example.attendancewidgetlaudea.data.model.NavAction.CA_MARKS -> Screen.CAMarks
                                    com.example.attendancewidgetlaudea.data.model.NavAction.EXEMPTIONS -> Screen.Exemptions
                                    com.example.attendancewidgetlaudea.data.model.NavAction.RESULTS -> Screen.Result
                                    com.example.attendancewidgetlaudea.data.model.NavAction.GPA_CALCULATOR -> Screen.CgpaCalculator
                                    com.example.attendancewidgetlaudea.data.model.NavAction.TIMETABLE -> { selectedTabIndex = 4; Screen.Dashboard }
                                    com.example.attendancewidgetlaudea.data.model.NavAction.CALENDAR -> Screen.AcademicCalendar
                                    com.example.attendancewidgetlaudea.data.model.NavAction.CIRCULARS -> Screen.Circulars
                                    com.example.attendancewidgetlaudea.data.model.NavAction.SYLLABUS -> Screen.Syllabus
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
                            }
                        )
                        // tab_2 (Games) and tab_3 (GPA) are routed via screen name
                        "tab_2" -> {}
                        "tab_3" -> {}
                        "tab_4" -> TimetableScreen(cardState = cardState)
                        // Profile accessed via header profile pic
                        Screen.Profile.name -> ProfileScreen(
                            cardState = cardState,
                            displayName = displayName,
                            onLogout = handleLogout,
                            onPrivacyPolicyClick = { currentScreen = Screen.PrivacyPolicy }
                        )
                    }
                }
            }
        }
    }
}

