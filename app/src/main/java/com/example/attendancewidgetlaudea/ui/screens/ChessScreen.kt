package com.example.attendancewidgetlaudea.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.data.model.OnlinePlayer
import com.example.attendancewidgetlaudea.ui.components.GlassCardShapeSmall
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.viewmodel.ChessViewModel
import io.github.fletchmckee.liquid.LiquidState

@Composable
fun ChessScreen(
    cardState: LiquidState,
    onBack: () -> Unit,
    viewModel: ChessViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Go online when entering, offline when leaving
    DisposableEffect(Unit) {
        viewModel.goOnline()
        onDispose { viewModel.goOffline() }
    }

    // Auto-open Lichess when challenge is accepted
    LaunchedEffect(uiState.acceptedChallenge) {
        val challenge = uiState.acceptedChallenge ?: return@LaunchedEffect
        val url = challenge.gameUrl.ifBlank { challenge.opponentUrl }
        if (url.isNotBlank()) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            viewModel.clearAcceptedChallenge()
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        // Header
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Chess Lobby", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (uiState.isOnline) {
                        Text("You're ${uiState.myName}", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Online indicator
                if (uiState.isOnline) {
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("${uiState.onlinePlayers.size + 1}", fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676))
                }
            }
        }

        // Incoming challenge dialog
        if (uiState.pendingChallenge != null) {
            Spacer(Modifier.height(8.dp))
            GlassListCard(
                modifier = Modifier.fillMaxWidth(),
                shape = GlassCardShapeSmall,
                tintColor = Color(0xFFFFA000).copy(alpha = 0.1f)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Challenge!", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFA000))
                    Spacer(Modifier.height(4.dp))
                    Text("${uiState.pendingChallenge!!.fromName} wants to play chess",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.acceptChallenge() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Accept", color = Color.Black)
                        }
                        OutlinedButton(
                            onClick = { viewModel.declineChallenge() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Decline")
                        }
                    }
                }
            }
        }

        // Waiting for response
        if (uiState.sentChallengeId != null) {
            Spacer(Modifier.height(8.dp))
            GlassListCard(
                modifier = Modifier.fillMaxWidth(),
                shape = GlassCardShapeSmall,
                tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Waiting for ${uiState.sentChallengeName}...",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.cancelSentChallenge() }) {
                        Text("Cancel", fontSize = 12.sp)
                    }
                }
            }
        }

        // Error
        if (uiState.errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            GlassListCard(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.clearError() },
                shape = GlassCardShapeSmall,
                tintColor = Color(0xFFFF5252).copy(alpha = 0.08f)
            ) {
                Text(uiState.errorMessage!!, fontSize = 12.sp, color = Color(0xFFFF8A80),
                    modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Online players header
        Text("Players Online", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(8.dp))

        // Player list
        if (!uiState.isOnline) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.onlinePlayers.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No other players online", fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Stay here — others will see you're available",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(uiState.onlinePlayers, key = { it.id }) { player ->
                    PlayerCard(
                        player = player,
                        canChallenge = uiState.sentChallengeId == null && uiState.pendingChallenge == null,
                        onChallenge = { viewModel.sendChallenge(player) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerCard(
    player: OnlinePlayer,
    canChallenge: Boolean,
    onChallenge: () -> Unit
) {
    GlassListCard(
        modifier = Modifier.fillMaxWidth(),
        shape = GlassCardShapeSmall
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Green dot
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape)
                    .background(Color(0xFF00E676))
            )
            Spacer(Modifier.width(12.dp))
            // Name
            Text(player.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            // Challenge button
            if (canChallenge) {
                Button(
                    onClick = onChallenge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text("Challenge", fontSize = 12.sp)
                }
            }
        }
    }
}
