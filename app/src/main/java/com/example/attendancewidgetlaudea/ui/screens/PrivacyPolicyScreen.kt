package com.example.attendancewidgetlaudea.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Privacy Policy",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Last updated: December 08, 2025\n\n" +
                        "1. Data Collection\n" +
                        "This app ('AttendanceWidgetLaudea') collects your student Roll Number and Password solely for the purpose of fetching your attendance data from the Laudea portal.\n\n" +
                        "2. Data Storage\n" +
                        "Your credentials are encrypted and stored LOCALLY on your device using Android's EncryptedSharedPreferences. We do NOT transmit your data to any third-party server. Your data never leaves your device except to communicate directly with the official college portal.\n\n" +
                        "3. Data Usage\n" +
                        "The app uses your credentials to log in to the Laudea portal automatically in the background to update the widget. This happens periodically (approx. every 4 hours).\n\n" +
                        "4. Third-Party Services\n" +
                        "This app interacts with the Laudea Student Information System. Please refer to their terms of service regarding your student account.\n\n" +
                        "5. Contact\n" +
                        "For any questions, please contact the developer via the app's about section.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
