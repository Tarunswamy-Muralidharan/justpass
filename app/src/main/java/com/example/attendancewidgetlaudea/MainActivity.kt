package com.example.attendancewidgetlaudea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.attendancewidgetlaudea.data.repository.AttendanceRepository
import com.example.attendancewidgetlaudea.data.update.UpdateChecker
import com.example.attendancewidgetlaudea.data.update.UpdateInfo
import com.example.attendancewidgetlaudea.ui.screens.AbsentDaysScreen
import com.example.attendancewidgetlaudea.ui.screens.CAMarksScreen
import com.example.attendancewidgetlaudea.ui.screens.DashboardScreen
import com.example.attendancewidgetlaudea.ui.screens.LoginScreen
import com.example.attendancewidgetlaudea.ui.theme.AttendanceWidgetLaudeaTheme
import com.example.attendancewidgetlaudea.worker.AttendanceRefreshWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize analytics
        Analytics.init(this)

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        // Schedule periodic background refresh
        AttendanceRefreshWorker.schedulePeriodicRefresh(this)

        setContent {
            AttendanceWidgetLaudeaTheme {
                AttendanceApp()
            }
        }
    }
}

@Composable
fun AttendanceApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = AttendanceRepository.getInstance(context)
    val scope = rememberCoroutineScope()

    // Determine initial screen based on login status
    val initialScreen = if (repository.isLoggedIn()) Screen.Dashboard else Screen.Login
    var currentScreen by remember { mutableStateOf(initialScreen) }

    // Set user ID and name if already logged in, log app open
    LaunchedEffect(Unit) {
        Analytics.logAppOpen()
        if (repository.isLoggedIn()) {
            val rollNumber = repository.getRollNumber()
            val token = repository.getCachedToken()
            val displayName = token?.let { Analytics.extractNameFromToken(it) }
            rollNumber?.let { Analytics.setUser(it, displayName) }
        }
    }

    // Track screen views
    LaunchedEffect(currentScreen) {
        Analytics.logScreenView(currentScreen.name)
    }

    // Counter to force LoginScreen recreation after logout
    var loginScreenKey by remember { mutableIntStateOf(0) }

    // Global logout loading state
    var isLoggingOut by remember { mutableStateOf(false) }

    // Update check
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val currentVersion = packageInfo.versionName ?: "1.0"
        updateInfo = UpdateChecker.checkForUpdate(currentVersion)
    }

    // Battery optimization whitelist — ensures background refresh runs reliably
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
            text = {
                Text("To keep your attendance widget updated in the background, please allow unrestricted battery usage for this app.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                    showBatteryDialog = false
                }) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

    // Update available dialog
    updateInfo?.let { update ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("Update Available") },
            text = {
                Column {
                    Text("Version ${update.versionName} is available!")
                    if (!update.releaseNotes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = update.releaseNotes,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
                    context.startActivity(intent)
                    updateInfo = null
                }) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) {
                    Text("Later")
                }
            }
        )
    }

    // Show loading dialog at app level (persists across screen changes)
    if (isLoggingOut) {
        Dialog(onDismissRequest = { }) {
            Card(
                modifier = Modifier.padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Logging out...")
                }
            }
        }
    }

    when (currentScreen) {
        Screen.Login -> {
            // Use key to force fresh ViewModel after logout
            key(loginScreenKey) {
                LoginScreen(
                    onLoginSuccess = {
                        currentScreen = Screen.Dashboard
                    }
                )
            }
        }
        Screen.Dashboard -> {
            val activity = context as? ComponentActivity
            DashboardScreen(
                onLogout = {
                    // Show loading, clear data, exit app
                    scope.launch {
                        isLoggingOut = true

                        // Perform actual logout - clear all data
                        Analytics.logLogout()
                        Analytics.clearUser()
                        repository.logout()

                        // Wait for async clearing to complete
                        delay(1000)

                        // Exit the app
                        activity?.finishAffinity()
                    }
                },
                onPrivacyPolicyClick = {
                    currentScreen = Screen.PrivacyPolicy
                },
                onCAMarksClick = {
                    Analytics.logFeatureUsed("ca_marks")
                    currentScreen = Screen.CAMarks
                },
                onAbsentDaysClick = {
                    Analytics.logFeatureUsed("absent_days")
                    currentScreen = Screen.AbsentDays
                }
            )
        }
        Screen.AbsentDays -> {
            AbsentDaysScreen(
                onBack = { currentScreen = Screen.Dashboard }
            )
        }
        Screen.PrivacyPolicy -> {
            com.example.attendancewidgetlaudea.ui.screens.PrivacyPolicyScreen(
                onBack = { currentScreen = Screen.Dashboard }
            )
        }
        Screen.CAMarks -> {
            CAMarksScreen(
                onBack = { currentScreen = Screen.Dashboard }
            )
        }
    }
}

enum class Screen {
    Login,
    Dashboard,
    AbsentDays,
    PrivacyPolicy,
    CAMarks
}
