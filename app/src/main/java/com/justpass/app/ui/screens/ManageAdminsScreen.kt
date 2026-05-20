package com.justpass.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.justpass.app.data.model.TournamentAdmins
import com.justpass.app.data.repository.AdminRolesRepository
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.viewmodel.AdminRolesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAdminsScreen(
    onBack: () -> Unit,
    viewModel: AdminRolesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var pendingRemoval by remember { mutableStateOf<AdminRolesRepository.AdminEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Admins", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        if (!state.isAdmin) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Admin only", color = Color(0xFF90A4AE))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Add or remove admins. New admins gain access on their next app open. Bootstrap admin (you) cannot be removed.",
                fontSize = 12.sp, color = Color(0xFFB0BEC5)
            )

            // Add admin form
            GlassListCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add admin", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    OutlinedTextField(
                        value = state.pendingRollInput,
                        onValueChange = viewModel::setRoll,
                        label = { Text("Roll number") },
                        placeholder = { Text("e.g. 23pcs102") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.pendingNameInput,
                        onValueChange = viewModel::setName,
                        label = { Text("Name (for your reference)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = viewModel::addAdmin,
                        enabled = !state.isWorking && state.pendingRollInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isWorking) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.PersonAdd, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Grant admin access")
                    }
                    state.errorMessage?.let { Text(it, color = Color(0xFFFF5252), fontSize = 12.sp) }
                }
            }

            HorizontalDivider(color = Color(0xFF263238))
            Text(
                "Current admins (${state.admins.size + TournamentAdmins.HARDCODED_PLAYER_IDS.size})",
                fontWeight = FontWeight.Bold, fontSize = 14.sp
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TournamentAdmins.HARDCODED_PLAYER_IDS.toList()) { pid ->
                    BootstrapAdminCard(playerId = pid, isMe = pid == state.myPlayerId)
                }
                items(state.admins) { entry ->
                    DynamicAdminCard(
                        entry = entry,
                        isMe = entry.playerId == state.myPlayerId,
                        onRemove = { pendingRemoval = entry }
                    )
                }
            }
        }
    }

    pendingRemoval?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            containerColor = Color(0xFF1E2A3A),
            title = { Text("Remove admin?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Revoke admin access for ${target.name.ifBlank { target.playerId }}? They lose inbox + approval access on next app open.",
                    color = Color(0xFFB0BEC5)
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.removeAdmin(target.playerId); pendingRemoval = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) { Text("Remove", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BootstrapAdminCard(playerId: String, isMe: Boolean) {
    GlassListCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        tintColor = Color(0xFFFFD700).copy(alpha = 0.06f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Bootstrap admin", fontSize = 12.sp, color = Color(0xFFFFD700))
                Text(playerId, fontSize = 11.sp, color = Color(0xFF90A4AE))
                if (isMe) Text("(you)", fontSize = 11.sp, color = Color(0xFF00E676))
            }
            Text("hardcoded", fontSize = 10.sp, color = Color(0xFF607D8B))
        }
    }
}

@Composable
private fun DynamicAdminCard(
    entry: AdminRolesRepository.AdminEntry,
    isMe: Boolean,
    onRemove: () -> Unit
) {
    val date = remember(entry.addedAt) {
        if (entry.addedAt > 0) SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(entry.addedAt))
        else ""
    }
    GlassListCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name.ifBlank { "(no name)" }, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(entry.playerId, fontSize = 11.sp, color = Color(0xFF90A4AE))
                if (date.isNotEmpty()) Text("Added $date", fontSize = 10.sp, color = Color(0xFF607D8B))
                if (isMe) Text("(you)", fontSize = 11.sp, color = Color(0xFF00E676))
            }
            if (!isMe) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, "Remove", tint = Color(0xFFFF5252))
                }
            }
        }
    }
}
