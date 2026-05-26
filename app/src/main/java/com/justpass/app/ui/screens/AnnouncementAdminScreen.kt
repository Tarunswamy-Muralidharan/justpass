package com.justpass.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.justpass.app.ui.viewmodel.AnnouncementAdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementAdminScreen(
    onBack: () -> Unit,
    viewModel: AnnouncementAdminViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Color(0xFF0A0F1A),
        topBar = {
            TopAppBar(
                title = { Text("Announcement", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A0F1A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero card with description
            Surface(
                color = Color(0xFF1E2A3A),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Filled.Campaign,
                        contentDescription = null,
                        tint = Color(0xFFFFAB00),
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Broadcast a dialog to every active user",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Active = ON shows the dialog on every Dashboard open. " +
                                "Change the id to re-prompt users who dismissed " +
                                "an earlier announcement.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            // Active toggle
            Surface(
                color = Color(0xFF1E2A3A),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Active", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (state.active) "Dialog will appear for all users"
                            else "Dialog hidden",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = state.active,
                        onCheckedChange = { viewModel.setActive(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4CAF50),
                            checkedTrackColor = Color(0xFF2E7D32),
                        ),
                    )
                }
            }

            OutlinedTextField(
                value = state.id,
                onValueChange = viewModel::setId,
                label = { Text("ID (unique per announcement)") },
                placeholder = { Text("exam-week-2026-06-10") },
                supportingText = {
                    Text(
                        "Change this to re-prompt users who tapped Got It on a previous one.",
                        fontSize = 11.sp,
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = darkFieldColors(),
            )

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text("Title") },
                placeholder = { Text("Announcement") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = darkFieldColors(),
            )

            OutlinedTextField(
                value = state.message,
                onValueChange = viewModel::setMessage,
                label = { Text("Message") },
                placeholder = { Text("What do you want everyone to see?") },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
                colors = darkFieldColors(),
            )

            Button(
                onClick = { viewModel.publish() },
                enabled = !state.isPublishing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            ) {
                if (state.isPublishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(
                        if (state.active) "Publish & Show" else "Publish (Dialog OFF)",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            state.lastResult?.let { msg ->
                Surface(
                    color = if (state.isError) Color(0xFF4A1E1E) else Color(0xFF1E3A2A),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            msg,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.dismissResult() }) {
                            Text("Dismiss", color = Color(0xFF64B5F6))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color(0xFF4CAF50),
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    focusedLabelColor = Color(0xFF4CAF50),
    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
    cursorColor = Color(0xFF4CAF50),
    focusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
    focusedSupportingTextColor = Color.White.copy(alpha = 0.5f),
    unfocusedSupportingTextColor = Color.White.copy(alpha = 0.5f),
)
