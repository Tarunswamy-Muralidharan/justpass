package com.justpass.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.justpass.app.data.model.TournamentRequest
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.viewmodel.TournamentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentApprovalScreen(
    onBack: () -> Unit,
    viewModel: TournamentViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var rejectTarget by remember { mutableStateOf<TournamentRequest?>(null) }

    LaunchedEffect(state.isAdmin) {
        if (state.isAdmin) viewModel.startListeningPending()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopListeningPending() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tournament Approvals", fontWeight = FontWeight.Bold) },
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

        if (state.pendingRequests.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No pending requests", color = Color(0xFF90A4AE), fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("New tournament submissions appear here.", color = Color(0xFF607D8B), fontSize = 12.sp)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.pendingRequests) { req ->
                RequestCard(
                    req = req,
                    onApprove = { viewModel.approve(req.id) },
                    onReject = { rejectTarget = req }
                )
            }
        }
    }

    rejectTarget?.let { target ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            containerColor = Color(0xFF1E2A3A),
            title = { Text("Reject request?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Reject \"${target.tournamentName}\" by ${target.creatorName}?",
                        color = Color(0xFFB0BEC5)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(120) },
                        label = { Text("Reason (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.reject(target.id, reason); rejectTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) { Text("Reject", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RequestCard(
    req: TournamentRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    GlassListCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(req.tournamentName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "${req.format} · max ${req.maxParticipants}",
                fontSize = 12.sp, color = Color(0xFF90A4AE)
            )
            if (req.description.isNotBlank()) {
                Text(req.description, fontSize = 12.sp, color = Color(0xFFCFD8DC))
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = Color(0xFF263238))
            Spacer(Modifier.height(4.dp))
            Text("Requester", fontSize = 11.sp, color = Color(0xFF607D8B))
            Text(req.creatorName.ifBlank { "(unknown)" }, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                "${req.creatorRollNumber.ifBlank { "—" }} · ${req.creatorDepartment.ifBlank { "—" }}",
                fontSize = 12.sp, color = Color(0xFFB0BEC5)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, null, Modifier.size(14.dp), tint = Color(0xFF64B5F6))
                Spacer(Modifier.width(4.dp))
                Text(
                    req.creatorPhone.ifBlank { "phone missing" },
                    fontSize = 12.sp, color = Color(0xFF64B5F6)
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = Color.Black)
                    Spacer(Modifier.width(4.dp))
                    Text("Approve", color = Color.Black)
                }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Cancel, null, Modifier.size(16.dp), tint = Color(0xFFFF5252))
                    Spacer(Modifier.width(4.dp))
                    Text("Reject", color = Color(0xFFFF5252))
                }
            }
        }
    }
}
