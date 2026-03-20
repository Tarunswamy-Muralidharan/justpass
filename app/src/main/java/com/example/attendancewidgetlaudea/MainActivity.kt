package com.example.attendancewidgetlaudea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
import com.example.attendancewidgetlaudea.ui.theme.AttendanceWidgetLaudeaTheme
import com.example.attendancewidgetlaudea.worker.AttendanceRefreshWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Analytics.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
        AttendanceRefreshWorker.schedulePeriodicRefresh(this)
        setContent {
            AttendanceWidgetLaudeaTheme {
                AttendanceApp()
            }
        }
    }
}

enum class Screen {
    Login, Dashboard, AbsentDays, SubjectAttendance, SubjectDetail, Exemptions, Result, PrivacyPolicy, CAMarks, Timetable, Profile
}

private val bottomTabs = listOf(
    TabItemData("Home", Icons.Default.Home),
    TabItemData("Timetable", Icons.Default.DateRange),
    TabItemData("CA Marks", Icons.Default.Star),
    TabItemData("Profile", Icons.Default.Person)
)

@Composable
fun AttendanceApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = AttendanceRepository.getInstance(context)
    val securePrefs = SecurePreferences.getInstance(context)
    val scope = rememberCoroutineScope()

    val isLoggedIn = repository.isLoggedIn()
    var currentScreen by remember { mutableStateOf(if (isLoggedIn) Screen.Dashboard else Screen.Login) }
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
                selectedTabIndex = 3
            })
        }
        else -> {
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
                                1 -> Screen.Timetable
                                2 -> Screen.CAMarks
                                3 -> Screen.Profile
                                else -> Screen.Dashboard
                            }
                            Analytics.logFeatureUsed(bottomTabs[index].label.lowercase())
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            ) { cardState ->
                Crossfade(
                    targetState = if (currentScreen in listOf(Screen.AbsentDays, Screen.SubjectAttendance, Screen.SubjectDetail, Screen.Exemptions, Screen.Result)) currentScreen.name
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
                            }
                        )
                        "tab_1" -> TimetableScreen(cardState = cardState)
                        "tab_2" -> CAMarksScreen(
                            cardState = cardState,
                            onBack = {
                                selectedTabIndex = 0
                                currentScreen = Screen.Dashboard
                            }
                        )
                        "tab_3" -> ProfileScreen(
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
