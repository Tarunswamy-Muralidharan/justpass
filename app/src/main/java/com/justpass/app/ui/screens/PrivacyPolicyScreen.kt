package com.justpass.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.components.LiquidGlassScaffold

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LiquidGlassScaffold { _ ->
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            GlassListCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Text("Privacy Policy", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Column(modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 160.dp).verticalScroll(rememberScrollState())) {
                GlassListCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Privacy Policy", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
                        Text(
                            text = "Last updated: December 08, 2025\n\n" +
                                "1. Data Collection\nThis app ('JustPass') collects your student Roll Number and Password solely for the purpose of fetching your attendance data from the Laudea portal.\n\n" +
                                "2. Data Storage\nYour credentials are encrypted and stored LOCALLY on your device using Android's EncryptedSharedPreferences. We do NOT transmit your data to any third-party server.\n\n" +
                                "3. Data Usage\nThe app uses your credentials to log in to the Laudea portal automatically in the background to update the widget.\n\n" +
                                "4. Third-Party Services\nThis app interacts with the Laudea Student Information System. Please refer to their terms of service.\n\n" +
                                "5. Contact\nFor any questions, please contact the developer via the app's about section.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
