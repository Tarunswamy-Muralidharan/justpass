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
import com.example.attendancewidgetlaudea.data.repository.AttendanceRepository
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

    // Counter to force LoginScreen recreation after logout
    var loginScreenKey by remember { mutableIntStateOf(0) }

    // Global logout loading state
    var isLoggingOut by remember { mutableStateOf(false) }

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
                    currentScreen = Screen.CAMarks
                },
                onAbsentDaysClick = {
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
