package com.example.attendancewidgetlaudea.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.data.model.ChessProfile
import com.example.attendancewidgetlaudea.data.model.OnlinePlayer
import com.example.attendancewidgetlaudea.data.model.TimeControl
import com.example.attendancewidgetlaudea.ui.viewmodel.MatchHistoryEntry
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

    // Track if we left for Lichess
    var wentToLichess by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.goOnline()
        onDispose { viewModel.goOffline() }
    }

    // Auto-open Lichess when challenge accepted
    LaunchedEffect(uiState.acceptedChallenge) {
        val challenge = uiState.acceptedChallenge ?: return@LaunchedEffect
        val url = challenge.gameUrl.ifBlank { challenge.opponentUrl }
        if (url.isNotBlank()) {
            wentToLichess = true
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            viewModel.clearAcceptedChallenge()
        }
    }

    // Auto-check game results when returning from Lichess
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && wentToLichess) {
                wentToLichess = false
                viewModel.checkPendingResults()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Name setup dialog
    if (uiState.showNameSetup) {
        NameSetupDialog(
            currentProfile = uiState.myProfile,
            onConfirm = { mode, nickname -> viewModel.setNameMode(mode, nickname) },
            onDismiss = { viewModel.dismissNameSetup() }
        )
    }

    // Leaderboard dialog
    if (uiState.showLeaderboard) {
        LeaderboardDialog(
            leaderboard = uiState.leaderboard,
            myId = uiState.myProfile?.id ?: "",
            onDismiss = { viewModel.toggleLeaderboard() }
        )
    }

    // Match history dialog
    if (uiState.showHistory) {
        MatchHistoryDialog(
            history = uiState.matchHistory,
            onDismiss = { viewModel.toggleHistory() }
        )
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Chess Lobby", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (uiState.myProfile != null) {
                        val p = uiState.myProfile!!
                        Text("${p.visibleName} · ${p.rating} SR",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Edit name
                IconButton(onClick = { viewModel.openNameSetup() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, "Edit name", modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // History
                IconButton(onClick = { viewModel.toggleHistory() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.History, "History", modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Leaderboard
                IconButton(onClick = { viewModel.toggleLeaderboard() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.EmojiEvents, "Leaderboard", modifier = Modifier.size(18.dp),
                        tint = Color(0xFFFFC107))
                }
                Spacer(Modifier.width(4.dp))
                // Online count
                if (uiState.isOnline) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF00E676)))
                    Spacer(Modifier.width(4.dp))
                    Text("${uiState.onlinePlayers.size + 1}", fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                }
            }
        }

        // ── How it works — collapsible info card ──
        var showInfo by remember { mutableStateOf(false) }
        GlassListCard(
            modifier = Modifier.fillMaxWidth().clickable { showInfo = !showInfo },
            shape = GlassCardShapeSmall,
            tintColor = Color(0xFF64B5F6).copy(alpha = 0.06f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, "Info",
                        tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("How does Chess Lobby work?", fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f))
                    Icon(
                        if (showInfo) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        "Toggle", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (showInfo) {
                    Spacer(Modifier.height(10.dp))
                    val infoItems = listOf(
                        "What is this?" to "A live chess matchmaking lobby for PSG iTech students. See who's online, challenge them, and play on Lichess!",
                        "Getting started" to "1. Set your display name (tap the pencil icon)\n2. Link your Lichess username in your profile\n3. You'll automatically appear online when you open this screen",
                        "Challenging someone" to "Tap the sword icon next to any online player. They'll get a popup to accept or decline. Once accepted, Lichess opens automatically for both of you.",
                        "Ratings & Leaderboard" to "Every game updates your SR (Skill Rating). Win = +25, Loss = -20, Draw = +5. Tap the trophy icon to see the leaderboard!",
                        "Friends" to "Tap the person+ icon to send a friend request. Friends show a star badge and appear at the top of the list.",
                        "Match History" to "Tap the clock icon to see your recent games, results, and opponents.",
                        "Need Lichess?" to "Lichess is 100% free — no account needed to play casual games. Download it from Play Store or visit lichess.org"
                    )
                    infoItems.forEach { (title, desc) ->
                        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Text(desc, fontSize = 11.sp, lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        // My stats bar
        if (uiState.myProfile != null && uiState.myProfile!!.gamesPlayed > 0) {
            val p = uiState.myProfile!!
            GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShapeSmall,
                tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)) {
                Row(modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatChip("W", "${p.wins}", Color(0xFF00E676))
                    StatChip("L", "${p.losses}", Color(0xFFFF5252))
                    StatChip("D", "${p.draws}", Color(0xFFFFC107))
                    StatChip("Played", "${p.gamesPlayed}", MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Friend requests
        if (uiState.friendRequests.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            uiState.friendRequests.forEach { req ->
                GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShapeSmall,
                    tintColor = Color(0xFF7C4DFF).copy(alpha = 0.08f)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${req.fromName} wants to be friends", fontSize = 12.sp,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.acceptFriendRequest(req) },
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Accept", fontSize = 11.sp, color = Color(0xFF00E676))
                        }
                        TextButton(onClick = { viewModel.declineFriendRequest(req) },
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Ignore", fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        // Incoming challenge
        if (uiState.pendingChallenge != null) {
            Spacer(Modifier.height(4.dp))
            GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShapeSmall,
                tintColor = Color(0xFFFFA000).copy(alpha = 0.1f)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Challenge!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFA000))
                    Spacer(Modifier.height(4.dp))
                    val tcLabel = try {
                        val tc = TimeControl.valueOf(uiState.pendingChallenge!!.timeControl.uppercase())
                        "${tc.icon} ${tc.description} ${tc.label}"
                    } catch (_: Exception) { "Rapid 10 min" }
                    Text("${uiState.pendingChallenge!!.fromName} wants to play", fontSize = 13.sp)
                    Text(tcLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { viewModel.acceptChallenge() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            shape = RoundedCornerShape(12.dp)) { Text("Accept", color = Color.Black) }
                        OutlinedButton(onClick = { viewModel.declineChallenge() },
                            shape = RoundedCornerShape(12.dp)) { Text("Decline") }
                    }
                }
            }
        }

        // Waiting for response
        if (uiState.sentChallengeId != null) {
            Spacer(Modifier.height(4.dp))
            GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShapeSmall,
                tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Waiting for ${uiState.sentChallengeName}...", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.cancelSentChallenge() }) { Text("Cancel", fontSize = 12.sp) }
                }
            }
        }

        // Error
        if (uiState.errorMessage != null) {
            Spacer(Modifier.height(4.dp))
            GlassListCard(modifier = Modifier.fillMaxWidth().clickable { viewModel.clearError() },
                shape = GlassCardShapeSmall, tintColor = Color(0xFFFF5252).copy(alpha = 0.08f)) {
                Text(uiState.errorMessage!!, fontSize = 12.sp, color = Color(0xFFFF8A80),
                    modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Players Online", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(6.dp))

        // Time control picker dialog
        var challengeTarget by remember { mutableStateOf<OnlinePlayer?>(null) }
        if (challengeTarget != null) {
            TimeControlDialog(
                playerName = challengeTarget!!.displayName,
                onSelect = { tc ->
                    viewModel.sendChallenge(challengeTarget!!, tc.name.lowercase())
                    challengeTarget = null
                },
                onDismiss = { challengeTarget = null }
            )
        }

        // Player list
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            uiState.onlinePlayers.isEmpty() -> Box(Modifier.fillMaxSize().weight(1f), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No other players online", fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Stay here — others will see you", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(uiState.onlinePlayers, key = { it.id }) { player ->
                    PlayerCard(
                        player = player,
                        canChallenge = uiState.sentChallengeId == null && uiState.pendingChallenge == null,
                        onChallenge = { challengeTarget = player },
                        onAddFriend = { viewModel.sendFriendRequest(player) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlayerCard(
    player: OnlinePlayer, canChallenge: Boolean,
    onChallenge: () -> Unit, onAddFriend: () -> Unit
) {
    GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShapeSmall) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF00E676)))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(player.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (player.isFriend) {
                        Spacer(Modifier.width(6.dp))
                        Text("friend", fontSize = 9.sp, color = Color(0xFF7C4DFF),
                            fontWeight = FontWeight.Bold)
                    }
                }
                val ago = formatTimeAgo(player.timestamp)
                Text("Active $ago", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
            if (!player.isFriend) {
                IconButton(onClick = onAddFriend, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.PersonAdd, "Add friend", Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
            if (canChallenge) {
                Spacer(Modifier.width(4.dp))
                Button(onClick = onChallenge,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                    Text("Play", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> "${diff / 86400_000}d ago"
    }
}

// ─── Name Setup Dialog ──────────────────────────────────────────────────────

@Composable
private fun NameSetupDialog(
    currentProfile: ChessProfile?,
    onConfirm: (mode: String, nickname: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(currentProfile?.nameMode ?: "random") }
    var customNickname by remember { mutableStateOf(currentProfile?.nickname ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2A3A),
        title = { Text("Choose your name", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // Random option
                Row(Modifier.fillMaxWidth().clickable { selectedMode = "random" }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedMode == "random", onClick = { selectedMode = "random" })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Random nickname", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(currentProfile?.let { com.example.attendancewidgetlaudea.data.repository.ChessRepository().generateRandomName("placeholder") } ?: "SilentKnight#42",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Custom option
                Row(Modifier.fillMaxWidth().clickable { selectedMode = "custom" }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedMode == "custom", onClick = { selectedMode = "custom" })
                    Spacer(Modifier.width(8.dp))
                    Text("Custom nickname", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                if (selectedMode == "custom") {
                    OutlinedTextField(
                        value = customNickname,
                        onValueChange = { if (it.length <= 20) customNickname = it },
                        placeholder = { Text("Enter nickname...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(start = 40.dp)
                    )
                }

                // Real name option
                Row(Modifier.fillMaxWidth().clickable { selectedMode = "real" }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedMode == "real", onClick = { selectedMode = "real" })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("College name & roll number", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(currentProfile?.displayName ?: "", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedMode, customNickname) },
                shape = RoundedCornerShape(12.dp)) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─── Leaderboard Dialog ─────────────────────────────────────────────────────

@Composable
private fun LeaderboardDialog(
    leaderboard: List<ChessProfile>,
    myId: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2A3A),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, "Trophy", tint = Color(0xFFFFC107))
                Spacer(Modifier.width(8.dp))
                Text("College Leaderboard", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (leaderboard.isEmpty()) {
                Text("No games played yet. Be the first!", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    itemsIndexed(leaderboard) { index, profile ->
                        val isMe = profile.id == myId
                        val rankColor = when (index) {
                            0 -> Color(0xFFFFD700) // Gold
                            1 -> Color(0xFFC0C0C0) // Silver
                            2 -> Color(0xFFCD7F32) // Bronze
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .then(if (isMe) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)) else Modifier)
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("#${index + 1}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = rankColor, modifier = Modifier.width(32.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.visibleName, fontSize = 13.sp,
                                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${profile.wins}W ${profile.losses}L ${profile.draws}D",
                                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${profile.rating}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            Text(" SR", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// ─── Match History Dialog ───────────────────────────────────────────────────

@Composable
private fun MatchHistoryDialog(
    history: List<MatchHistoryEntry>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2A3A),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, "History", tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Match History", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (history.isEmpty()) {
                Text("No games played yet", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(history) { match ->
                        val resultColor = when (match.result) {
                            "win" -> Color(0xFF00E676)
                            "loss" -> Color(0xFFFF5252)
                            "draw" -> Color(0xFFFFC107)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val resultText = when (match.result) {
                            "win" -> "Won"
                            "loss" -> "Lost"
                            "draw" -> "Draw"
                            "aborted" -> "Aborted"
                            else -> "?"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(resultText, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = resultColor, modifier = Modifier.width(56.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("vs ${match.opponentName}", fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatTimeAgo(match.timestamp), fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun TimeControlDialog(
    playerName: String,
    onSelect: (TimeControl) -> Unit,
    onDismiss: () -> Unit
) {
    val categories = listOf(
        "Bullet" to listOf(TimeControl.BULLET, TimeControl.BULLET_1_1),
        "Blitz" to listOf(TimeControl.BLITZ_3, TimeControl.BLITZ_3_2, TimeControl.BLITZ_5, TimeControl.BLITZ_5_3),
        "Rapid" to listOf(TimeControl.RAPID_10, TimeControl.RAPID_10_5, TimeControl.RAPID_15_10),
        "Classical" to listOf(TimeControl.CLASSICAL)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2A3A),
        title = {
            Column {
                Text("Challenge $playerName", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Pick a time control", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column {
                categories.forEach { (category, controls) ->
                    Text(category, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        controls.forEach { tc ->
                            OutlinedButton(
                                onClick = { onSelect(tc) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("${tc.icon} ${tc.description}", fontSize = 12.sp)
                            }
                        }
                        repeat((3 - controls.size).coerceAtLeast(0)) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {}
    )
}
