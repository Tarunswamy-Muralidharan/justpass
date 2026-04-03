package com.example.attendancewidgetlaudea.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInBrowser
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
import androidx.compose.ui.viewinterop.AndroidView
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

    // In-app game WebView state
    var activeGameUrl by remember { mutableStateOf<String?>(null) }
    var gameResult by remember { mutableStateOf<String?>(null) }
    var lastChallengedPlayer by remember { mutableStateOf<OnlinePlayer?>(null) }
    var challengeTarget by remember { mutableStateOf<OnlinePlayer?>(null) }

    // Game result dialog
    if (gameResult != null) {
        val resultColor = when {
            gameResult!!.contains("win", ignoreCase = true) -> Color(0xFF00E676)
            gameResult!!.contains("lose", ignoreCase = true) || gameResult!!.contains("loss", ignoreCase = true) -> Color(0xFFFF5252)
            gameResult!!.contains("draw", ignoreCase = true) -> Color(0xFFFFC107)
            else -> MaterialTheme.colorScheme.primary
        }
        AlertDialog(
            onDismissRequest = { gameResult = null },
            containerColor = Color(0xFF1E2A3A),
            title = {
                Text("Game Over", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(gameResult!!, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = resultColor,
                        textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                if (lastChallengedPlayer != null) {
                    Button(
                        onClick = {
                            gameResult = null
                            // Re-challenge same opponent
                            challengeTarget = lastChallengedPlayer
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Play Again", color = Color.Black) }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { gameResult = null },
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Back to Lobby") }
            }
        )
    }

    DisposableEffect(Unit) {
        viewModel.goOnline()
        onDispose { viewModel.goOffline() }
    }

    // Auto-open in-app game when challenge accepted
    LaunchedEffect(uiState.acceptedChallenge) {
        val challenge = uiState.acceptedChallenge ?: return@LaunchedEffect
        val url = challenge.gameUrl.ifBlank { challenge.opponentUrl }
        if (url.isNotBlank()) {
            activeGameUrl = url
            viewModel.clearAcceptedChallenge()
        }
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
            onAnalyze = { gameId ->
                if (gameId.isNotBlank()) {
                    activeGameUrl = "https://lichess.org/$gameId"
                    viewModel.toggleHistory()
                }
            },
            onDismiss = { viewModel.toggleHistory() }
        )
    }

    // Friends list dialog
    if (uiState.showFriends) {
        FriendsDialog(
            friends = uiState.friendProfiles,
            onlinePlayers = uiState.onlinePlayers,
            onDismiss = { viewModel.toggleFriends() }
        )
    }

    // ── In-app game WebView ──
    // Track the last opponent for "Play Again"

    if (activeGameUrl != null) {
        LichessGameScreen(
            url = activeGameUrl!!,
            onClose = { result ->
                activeGameUrl = null
                viewModel.checkPendingResults()
                // If game ended with a result, show result dialog
                if (result != null) {
                    gameResult = result
                }
            },
            onOpenExternal = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activeGameUrl)))
            }
        )
        return
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
                    Text("Chess Lobby", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
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
                // Friends
                IconButton(onClick = { viewModel.toggleFriends() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.PersonAdd, "Friends", modifier = Modifier.size(16.dp),
                        tint = Color(0xFF7C4DFF))
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
        if (challengeTarget != null) {
            TimeControlDialog(
                playerName = challengeTarget!!.displayName,
                onSelect = { tc ->
                    lastChallengedPlayer = challengeTarget
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
                        color = MaterialTheme.colorScheme.onSurface,
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
        title = { Text("Choose your name", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column {
                // Random option
                Row(Modifier.fillMaxWidth().clickable { selectedMode = "random" }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedMode == "random", onClick = { selectedMode = "random" })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Random nickname", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        Text(currentProfile?.let { com.example.attendancewidgetlaudea.data.repository.ChessRepository().generateRandomName("placeholder") } ?: "SilentKnight#42",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Custom option
                Row(Modifier.fillMaxWidth().clickable { selectedMode = "custom" }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedMode == "custom", onClick = { selectedMode = "custom" })
                    Spacer(Modifier.width(8.dp))
                    Text("Custom nickname", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
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
                        Text("College name & roll number", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
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
                Text("College Leaderboard", fontWeight = FontWeight.Bold, color = Color.White)
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
    onAnalyze: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2A3A),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, "History", tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Match History", fontWeight = FontWeight.Bold, color = Color.White)
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
                                    color = Color.White,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatTimeAgo(match.timestamp), fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (match.lichessGameId.isNotBlank()) {
                                TextButton(
                                    onClick = { onAnalyze(match.lichessGameId) },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Analyze", fontSize = 11.sp, color = Color(0xFF64B5F6))
                                }
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
                Text("Challenge $playerName", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
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

/**
 * Full-screen in-app Lichess game via WebView.
 * Hides Lichess header/nav so it feels native to JustPass.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LichessGameScreen(
    url: String,
    onClose: (result: String?) -> Unit,
    onOpenExternal: () -> Unit
) {
    var showExitConfirm by remember { mutableStateOf(false) }

    var gameEnded by remember { mutableStateOf<String?>(null) }

    BackHandler {
        if (gameEnded != null) onClose(gameEnded) else showExitConfirm = true
    }

    // Auto-close when game ends
    LaunchedEffect(gameEnded) {
        if (gameEnded != null) {
            kotlinx.coroutines.delay(1500) // let user see the final position
            onClose(gameEnded)
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            containerColor = Color(0xFF1E2A3A),
            title = { Text("Leave game?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("If the game is still ongoing, you will lose on abandonment.",
                color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = { showExitConfirm = false; onClose(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Leave", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitConfirm = false },
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Keep playing") }
            }
        )
    }

    var isLoading by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E))) {
        // Top bar — close + open in browser
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { showExitConfirm = true },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            ) {
                Icon(Icons.Default.Close, "Close game", tint = Color.White)
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF8BC34A),
                    strokeWidth = 2.dp
                )
            }
            IconButton(
                onClick = onOpenExternal,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            ) {
                Icon(Icons.Default.OpenInBrowser, "Open in browser", tint = Color.White)
            }
        }

        // WebView takes remaining space — no overlays on top of it
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.userAgentString = settings.userAgentString + " JustPass-Chess"
                    setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))

                    var pageReady = false
                    val hideJs = "javascript:(function(){if(document.getElementById('jp'))return;var s=document.createElement('style');s.id='jp';s.textContent='header,.header,#top,.site-title,.site-buttons,.mchat,footer,.fbt,.topnav,.clinput,.dasher,.hamburger,.signin,.signup,nav,.chat__members,.lobby__table,.lobby__app,.round__top__table,.game__meta__infos{display:none!important}#top,.top,div[role=banner],div[class*=site-buttons],div[class*=topnav]{display:none!important}body,.round__app,.round{padding-top:0!important;margin-top:0!important}';(document.head||document.documentElement).appendChild(s);})()"

                    // Poll for game-over status in Lichess DOM
                    val pollGameEnd = """javascript:(function(){
                        if(window._jpPoll)return;window._jpPoll=1;
                        setInterval(function(){
                            var st=document.querySelector('.result-wrap .status,.rresult,.status');
                            if(st){
                                var txt=st.textContent.trim();
                                if(txt&&(txt.indexOf('win')>=0||txt.indexOf('lose')>=0||txt.indexOf('draw')>=0||txt.indexOf('time')>=0||txt.indexOf('resign')>=0||txt.indexOf('mate')>=0||txt.indexOf('abort')>=0||txt.indexOf('stalemate')>=0)){
                                    if(!window._jpDone){window._jpDone=1;JustPass.onGameEnd(txt);}
                                }
                            }
                        },2000);
                    })()""".trimIndent().replace("\n", "")

                    // JS interface to receive game-end callback
                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun onGameEnd(result: String) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                if (gameEnded == null) gameEnded = result
                            }
                        }
                    }, "JustPass")

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            super.onPageFinished(view, pageUrl)
                            view?.evaluateJavascript(hideJs, null)
                            view?.evaluateJavascript(pollGameEnd, null)
                            if (!pageReady) {
                                pageReady = true
                                view?.postDelayed({ isLoading = false }, 400)
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): Boolean {
                            val reqUrl = request?.url?.toString() ?: return false
                            return if (reqUrl.contains("lichess.org")) {
                                false
                            } else {
                                view?.context?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(reqUrl)))
                                true
                            }
                        }
                    }

                    webChromeClient = WebChromeClient()
                    loadUrl(url)
                }
            }
        )

    }
}

@Composable
private fun FriendsDialog(
    friends: List<ChessProfile>,
    onlinePlayers: List<OnlinePlayer>,
    onDismiss: () -> Unit
) {
    val onlineIds = onlinePlayers.map { it.id }.toSet()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2A3A),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, "Friends", tint = Color(0xFF7C4DFF))
                Spacer(Modifier.width(8.dp))
                Text("Friends (${friends.size})", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        text = {
            if (friends.isEmpty()) {
                Text("No friends yet. Tap the person+ icon on any player to send a friend request!",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(friends) { friend ->
                        val isOnline = friend.id in onlineIds
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Online/offline indicator
                            Box(
                                Modifier.size(10.dp).clip(CircleShape)
                                    .background(if (isOnline) Color(0xFF00E676) else Color(0xFF757575))
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(friend.visibleName, fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium, color = Color.White)
                                Text(
                                    if (isOnline) "Online now" else "Last seen ${formatTimeAgo(friend.lastOnline)}",
                                    fontSize = 11.sp,
                                    color = if (isOnline) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Stats
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${friend.rating} SR", fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("${friend.wins}W ${friend.losses}L ${friend.draws}D",
                                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
