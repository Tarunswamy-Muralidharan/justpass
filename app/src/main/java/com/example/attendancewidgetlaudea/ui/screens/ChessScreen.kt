package com.example.attendancewidgetlaudea.ui.screens

import com.example.attendancewidgetlaudea.ui.components.AdBanner
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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.attendancewidgetlaudea.data.model.BoardTheme
import com.example.attendancewidgetlaudea.data.model.ChessProfile
import com.example.attendancewidgetlaudea.data.model.OnlinePlayer
import com.example.attendancewidgetlaudea.data.model.TimeControl
import com.example.attendancewidgetlaudea.ui.viewmodel.MatchHistoryEntry
import com.example.attendancewidgetlaudea.ui.components.GlassCardShapeSmall
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.components.RoseFourLoader
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

    // In-app game WebView state — rememberSaveable so rotation / config change
    // doesn't kick the user out mid-game.
    var activeGameUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var gameResult by rememberSaveable { mutableStateOf<String?>(null) }
    var lastChallengedPlayer by remember { mutableStateOf<OnlinePlayer?>(null) }
    var challengeTarget by remember { mutableStateOf<OnlinePlayer?>(null) }
    // Track the active challenge for name-based results
    var activeChallenge by remember { mutableStateOf<com.example.attendancewidgetlaudea.data.model.ChessChallenge?>(null) }

    // Game result dialog — show player names instead of "White wins"/"Black wins"
    if (gameResult != null) {
        val myName = uiState.myProfile?.visibleName ?: "You"
        val opponentName = lastChallengedPlayer?.displayName
            ?: activeChallenge?.let { ch ->
                if (ch.fromId == (uiState.myProfile?.id ?: "")) ch.toName else ch.fromName
            } ?: "Opponent"
        // Parse "winner|text|myColor" format from JS (e.g. "white|Checkmate|black")
        val parts = gameResult!!.split("|", limit = 3)
        val winnerColor = parts.getOrNull(0)?.lowercase()?.trim() ?: "unknown"
        val rawResult = (parts.getOrNull(1) ?: gameResult!!).lowercase()
        // My color: prefer the board orientation detected by JS, fall back to challenge data
        val myColor = parts.getOrNull(2)?.lowercase()?.trim()?.takeIf { it == "white" || it == "black" }
            ?: activeChallenge?.let { ch ->
                val fromColor = ch.fromColor.ifBlank { "white" }
                if (ch.fromId == (uiState.myProfile?.id ?: "")) fromColor
                else if (fromColor == "white") "black" else "white"
            }
        // Extract lichess game ID from the active challenge for replay/analysis
        val resultGameId = activeChallenge?.lichessGameId ?: ""

        val namedResult = when {
            rawResult.contains("draw") || rawResult.contains("stalemate") || winnerColor == "draw" -> "Draw!"
            rawResult.contains("abort") -> "Game Aborted"
            // Use extracted winner color for reliable mapping
            winnerColor == "white" || winnerColor == "black" -> {
                val winnerIsMe = winnerColor == myColor
                if (winnerIsMe) "$myName wins!" else "$opponentName wins!"
            }
            // Fallback: parse from raw text
            rawResult.contains("win") -> "$myName wins!"
            rawResult.contains("lose") || rawResult.contains("loss") -> "$opponentName wins!"
            else -> parts.getOrNull(1) ?: gameResult!!
        }
        val resultColor = when {
            namedResult.contains(myName) && namedResult.contains("win", ignoreCase = true) -> Color(0xFF00E676)
            namedResult.contains(opponentName) && namedResult.contains("win", ignoreCase = true) -> Color(0xFFFF5252)
            namedResult.contains("Draw") -> Color(0xFFFFC107)
            namedResult.contains("Abort") -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.primary
        }
        AlertDialog(
            onDismissRequest = { gameResult = null; activeChallenge = null },
            containerColor = Color(0xFF1E2A3A),
            title = {
                Text("Game Over", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(namedResult, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = resultColor,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    // Replay & Analysis buttons
                    if (resultGameId.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    gameResult = null; activeChallenge = null
                                    activeGameUrl = "https://lichess.org/$resultGameId#last"
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.History, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Replay")
                            }
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://lichess.org/$resultGameId#analysis")))
                                    gameResult = null; activeChallenge = null
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Info, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Analysis")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (lastChallengedPlayer != null) {
                    Button(
                        onClick = {
                            gameResult = null
                            activeChallenge = null
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
                    onClick = { gameResult = null; activeChallenge = null },
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
        activeChallenge = challenge // save for name-based results
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
                    activeGameUrl = "https://lichess.org/$gameId#last"
                    viewModel.toggleHistory()
                }
            },
            onDismiss = { viewModel.toggleHistory() }
        )
    }

    // Board theme picker dialog
    if (uiState.showThemePicker) {
        BoardThemeDialog(
            currentTheme = uiState.boardTheme,
            onSelect = { viewModel.setBoardTheme(it) },
            onDismiss = { viewModel.toggleThemePicker() }
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
            boardTheme = uiState.boardTheme,
            onClose = { result ->
                // User exited before the game ended — signal the opponent
                if (result == null) viewModel.notifyGameLeft()
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

    var showInfo by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        // Header
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Chess Lobby", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (uiState.myProfile != null) {
                        val p = uiState.myProfile!!
                        Text("${p.visibleName} · ${p.rating} SR",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // Online count
                if (uiState.isOnline) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF00E676)))
                    Spacer(Modifier.width(4.dp))
                    Text("${uiState.onlinePlayers.size + 1}", fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                }
            }
        }

        // ── Action bar — Friends, Theme, History, Leaderboard, Edit Name ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Friends
            val onlineFriendCount = uiState.onlinePlayers.count { it.isFriend }
            GlassListCard(
                modifier = Modifier.weight(1f).clickable { viewModel.toggleFriends() },
                shape = RoundedCornerShape(12.dp),
                tintColor = Color(0xFF7C4DFF).copy(alpha = 0.10f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.People, null, Modifier.size(18.dp), tint = Color(0xFF7C4DFF))
                    Spacer(Modifier.width(6.dp))
                    Text("Friends", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White, maxLines = 1)
                    if (onlineFriendCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier.size(18.dp).clip(CircleShape)
                                .background(Color(0xFF00E676)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$onlineFriendCount", fontSize = 9.sp,
                                fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
            // Board Theme tile removed — Lichess' styling can't be reliably themed from
            // the WebView, so the setting did nothing. Users can pick a board on
            // lichess.org preferences if they want one.
            // History
            GlassListCard(
                modifier = Modifier.weight(1f).clickable { viewModel.toggleHistory() },
                shape = RoundedCornerShape(12.dp),
                tintColor = Color(0xFF64B5F6).copy(alpha = 0.08f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.History, null, Modifier.size(18.dp), tint = Color(0xFF64B5F6))
                    Spacer(Modifier.width(6.dp))
                    Text("History", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White, maxLines = 1)
                }
            }
        }
        // Second row: Leaderboard + Edit Name + Info
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Leaderboard
            GlassListCard(
                modifier = Modifier.weight(1f).clickable { viewModel.toggleLeaderboard() },
                shape = RoundedCornerShape(12.dp),
                tintColor = Color(0xFFFFC107).copy(alpha = 0.10f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.EmojiEvents, null, Modifier.size(18.dp), tint = Color(0xFFFFC107))
                    Spacer(Modifier.width(6.dp))
                    Text("Leaderboard", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Edit Name
            GlassListCard(
                modifier = Modifier.weight(1f).clickable { viewModel.openNameSetup() },
                shape = RoundedCornerShape(12.dp),
                tintColor = Color(0xFF80CBC4).copy(alpha = 0.08f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp), tint = Color(0xFF80CBC4))
                    Spacer(Modifier.width(6.dp))
                    Text("Name", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White, maxLines = 1)
                }
            }
            // How it works
            GlassListCard(
                modifier = Modifier.weight(1f).clickable { showInfo = !showInfo },
                shape = RoundedCornerShape(12.dp),
                tintColor = Color(0xFF64B5F6).copy(alpha = 0.06f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = Color(0xFF64B5F6))
                    Spacer(Modifier.width(6.dp))
                    Text("Info", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White, maxLines = 1)
                }
            }
        }

        // ── How it works — expandable info ──
        AnimatedVisibility(visible = showInfo) {
            GlassListCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                shape = RoundedCornerShape(12.dp),
                tintColor = Color(0xFF64B5F6).copy(alpha = 0.06f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val infoItems = listOf(
                        "What is this?" to "A live chess lobby for PSG iTech students. See who's online, challenge them, and play on Lichess!",
                        "Getting started" to "1. Set your display name\n2. Link your Lichess username\n3. You'll appear online when you open this screen",
                        "Challenging" to "Tap the sword icon next to any player. They get 15s to accept. Once accepted, Lichess opens for both.",
                        "Ratings" to "Win = +25 SR, Loss = -20, Draw = +5. Check the leaderboard!",
                        "Friends" to "Send friend requests — friends show a star badge and appear at the top.",
                        "Need Lichess?" to "100% free — no account needed for casual games. Get it from Play Store or lichess.org"
                    )
                    infoItems.forEach { (title, desc) ->
                        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF64B5F6))
                        Text(desc, fontSize = 11.sp, lineHeight = 15.sp,
                            color = Color.White.copy(alpha = 0.7f))
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

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

        // Incoming challenge with countdown
        if (uiState.pendingChallenge != null) {
            Spacer(Modifier.height(4.dp))
            val countdown = uiState.challengeCountdown ?: 15
            val urgentColor = if (countdown <= 5) Color(0xFFFF5252) else Color(0xFFFFA000)
            GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShapeSmall,
                tintColor = urgentColor.copy(alpha = 0.1f)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        Text("Challenge!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = urgentColor)
                        Spacer(Modifier.width(12.dp))
                        // Countdown badge
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(urgentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$countdown", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = urgentColor)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val tcLabel = try {
                        val tc = TimeControl.valueOf(uiState.pendingChallenge!!.timeControl.toString().uppercase())
                        "${tc.icon} ${tc.description} ${tc.label}"
                    } catch (_: Exception) { "Rapid 10 min" }
                    Text("${uiState.pendingChallenge!!.fromName} wants to play", fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
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

        // Waiting for response with sender countdown
        if (uiState.sentChallengeId != null) {
            Spacer(Modifier.height(4.dp))
            val senderCountdown = uiState.senderCountdown ?: 15
            GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShapeSmall,
                tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    // Circular countdown indicator
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { senderCountdown / 15f },
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp,
                            color = if (senderCountdown <= 5) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                        )
                        Text("$senderCountdown", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = if (senderCountdown <= 5) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Waiting for ${uiState.sentChallengeName}...", fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            uiState.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { RoseFourLoader(modifier = Modifier.size(48.dp)) }
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
                contentPadding = PaddingValues(bottom = 160.dp),
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
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    if (player.isFriend) {
                        Spacer(Modifier.width(6.dp))
                        Text("friend", fontSize = 9.sp, color = Color(0xFF7C4DFF),
                            fontWeight = FontWeight.Bold, maxLines = 1)
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
    boardTheme: BoardTheme = BoardTheme.CHESS_COM,
    onClose: (result: String?) -> Unit,
    onOpenExternal: () -> Unit
) {
    var showExitConfirm by remember { mutableStateOf(false) }

    // Don't auto-close for analysis/replay — only for live games
    val isLiveGame = remember(url) { !url.contains("#") && !url.contains("/analysis") }
    var gameEnded by remember { mutableStateOf<String?>(null) }

    BackHandler {
        if (!isLiveGame) onClose(null)
        else if (gameEnded != null) onClose(gameEnded)
        else showExitConfirm = true
    }

    // Auto-close when game ends (live games only)
    LaunchedEffect(gameEnded) {
        if (gameEnded != null && isLiveGame) {
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
                onClick = { if (isLiveGame) showExitConfirm = true else onClose(null) },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            ) {
                Icon(Icons.Default.Close, "Close game", tint = Color.White)
            }
            if (isLoading) {
                RoseFourLoader(modifier = Modifier.size(24.dp))
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
            modifier = Modifier.weight(1f).fillMaxWidth(),
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
                    // Hide Lichess chrome (header/footer/nav) but KEEP .mchat and .clinput
                    // so the in-game chat + input stays usable inside the WebView.
                    val hideJs = "javascript:(function(){if(document.getElementById('jp'))return;var s=document.createElement('style');s.id='jp';s.textContent='header,.header,#top,.site-title,.site-buttons,footer,.fbt,.topnav,.dasher,.hamburger,.signin,.signup,nav,.lobby__table,.lobby__app,.round__top__table,.game__meta__infos{display:none!important}#top,.top,div[role=banner],div[class*=site-buttons],div[class*=topnav]{display:none!important}body,.round__app,.round{padding-top:0!important;margin-top:0!important}';(document.head||document.documentElement).appendChild(s);})()"
                    val boardCss = boardTheme.css
                    val themeJs = "javascript:(function(){if(document.getElementById('jp-theme'))return;var s=document.createElement('style');s.id='jp-theme';s.textContent='$boardCss';(document.head||document.documentElement).appendChild(s);})()"

                    // Poll for game-over status in Lichess DOM
                    // Extracts winner color + my color from board orientation for accurate name mapping
                    val pollGameEnd = """javascript:(function(){
                        if(window._jpPoll)return;window._jpPoll=1;
                        setInterval(function(){
                            var st=document.querySelector('.result-wrap .status,.rresult,.status');
                            if(st){
                                var txt=st.textContent.trim();
                                if(txt&&(txt.indexOf('win')>=0||txt.indexOf('lose')>=0||txt.indexOf('draw')>=0||txt.indexOf('time')>=0||txt.indexOf('resign')>=0||txt.indexOf('mate')>=0||txt.indexOf('abort')>=0||txt.indexOf('stalemate')>=0)){
                                    if(!window._jpDone){
                                        window._jpDone=1;
                                        var winner='unknown';
                                        var wr=document.querySelector('.result-wrap');
                                        if(wr){
                                            var cl=wr.className||'';
                                            if(cl.indexOf('white')>=0)winner='white';
                                            else if(cl.indexOf('black')>=0)winner='black';
                                        }
                                        if(winner==='unknown'){
                                            var rm=document.querySelector('.rmoves');
                                            if(rm){
                                                var rmt=rm.textContent||'';
                                                if(rmt.indexOf('1-0')>=0)winner='white';
                                                else if(rmt.indexOf('0-1')>=0)winner='black';
                                                else if(rmt.indexOf('½')>=0)winner='draw';
                                            }
                                        }
                                        var myC='unknown';
                                        var bd=document.querySelector('cg-board,.cg-board');
                                        if(bd){
                                            var bcl=(bd.className||'')+' '+(bd.parentElement?bd.parentElement.className:'');
                                            if(bcl.indexOf('orientation-black')>=0)myC='black';
                                            else if(bcl.indexOf('orientation-white')>=0)myC='white';
                                        }
                                        if(myC==='unknown'){
                                            var bw=document.querySelector('.round__app,.board-wrap');
                                            if(bw){var bwc=bw.className||'';if(bwc.indexOf('black')>=0)myC='black';else myC='white';}
                                        }
                                        JustPass.onGameEnd(winner+'|'+txt+'|'+myC);
                                    }
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
                            view?.evaluateJavascript(themeJs, null)
                            if (isLiveGame) view?.evaluateJavascript(pollGameEnd, null)
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

        AdBanner(modifier = Modifier.padding(vertical = 4.dp), screenName = "ChessGame")
    }
}

@Composable
private fun BoardThemeDialog(
    currentTheme: BoardTheme,
    onSelect: (BoardTheme) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2A3A),
        title = {
            Text("Board Theme", fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(BoardTheme.entries.toList(), key = { it.name }) { theme ->
                    val isSelected = theme == currentTheme
                    GlassListCard(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(theme) },
                        shape = RoundedCornerShape(12.dp),
                        tintColor = if (isSelected) Color(0xFF4285F4).copy(alpha = 0.12f)
                            else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Preview: two squares side by side
                            Row(modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                            ) {
                                Box(Modifier.size(24.dp).background(
                                    Color(android.graphics.Color.parseColor(theme.lightSquare))))
                                Box(Modifier.size(24.dp).background(
                                    Color(android.graphics.Color.parseColor(theme.darkSquare))))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                theme.label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF4285F4) else Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Text("✓", fontSize = 16.sp, color = Color(0xFF4285F4),
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color(0xFF4285F4))
            }
        }
    )
}

@Composable
private fun FriendsDialog(
    friends: List<ChessProfile>,
    onlinePlayers: List<OnlinePlayer>,
    onDismiss: () -> Unit
) {
    val onlineIds = onlinePlayers.map { it.id }.toSet()
    val onlineCount = friends.count { it.id in onlineIds }
    // Sort: online friends first, then by rating
    val sortedFriends = friends.sortedWith(compareByDescending<ChessProfile> { it.id in onlineIds }.thenByDescending { it.rating })

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2A3A),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.People, "Friends", tint = Color(0xFF7C4DFF))
                Spacer(Modifier.width(8.dp))
                Text("Friends", fontWeight = FontWeight.Bold, color = Color.White)
                if (onlineCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF00E676).copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("$onlineCount online", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676))
                    }
                }
            }
        },
        text = {
            if (friends.isEmpty()) {
                Text("No friends yet. Tap the + icon on any player in the lobby to send a friend request!",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(sortedFriends) { friend ->
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
