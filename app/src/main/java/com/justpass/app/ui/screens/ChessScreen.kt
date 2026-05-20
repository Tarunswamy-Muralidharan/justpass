package com.justpass.app.ui.screens

import com.justpass.app.ui.components.AdBanner
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.justpass.app.data.model.BoardTheme
import com.justpass.app.data.model.ChessProfile
import com.justpass.app.data.model.OnlinePlayer
import com.justpass.app.data.model.TimeControl
import com.justpass.app.ui.viewmodel.MatchHistoryEntry
import com.justpass.app.ui.components.GlassCardShapeSmall
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.components.RoseFourLoader
import com.justpass.app.ui.viewmodel.ChessViewModel
import io.github.fletchmckee.liquid.LiquidState

@Composable
fun ChessScreen(
    cardState: LiquidState,
    onBack: () -> Unit,
    onCreateTournament: () -> Unit = {},
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
    var activeChallenge by remember { mutableStateOf<com.justpass.app.data.model.ChessChallenge?>(null) }

    // Game result dialog — show player names instead of "White wins"/"Black wins"
    if (gameResult != null) {
        val myName = uiState.myProfile?.visibleName ?: "You"
        val opponentName = lastChallengedPlayer?.displayName
            ?: activeChallenge?.let { ch ->
                if (ch.fromId == (uiState.myProfile?.id ?: "")) ch.toName else ch.fromName
            } ?: "Opponent"
        // Parse "winner|text|myColor|result" format from JS (e.g. "white|Checkmate|black|mywin").
        // result is the authoritative hint; all other parts are diagnostics only.
        val parts = gameResult!!.split("|", limit = 4)
        val winnerColor = parts.getOrNull(0)?.lowercase()?.trim() ?: "unknown"
        val rawResult = (parts.getOrNull(1) ?: gameResult!!).lowercase()
        // My color: prefer the board orientation detected by JS, fall back to challenge data
        val myColor = parts.getOrNull(2)?.lowercase()?.trim()?.takeIf { it == "white" || it == "black" }
            ?: activeChallenge?.let { ch ->
                val fromColor = ch.fromColor.ifBlank { "white" }
                if (ch.fromId == (uiState.myProfile?.id ?: "")) fromColor
                else if (fromColor == "white") "black" else "white"
            }
        val resultHint = parts.getOrNull(3)?.lowercase()?.trim()
        // Extract lichess game ID from the active challenge for replay/analysis
        val resultGameId = activeChallenge?.lichessGameId ?: ""

        val namedResult = when {
            // Authoritative path — JS computed it from winner + my orientation.
            resultHint == "mywin" -> "$myName wins!"
            resultHint == "oppwin" -> "$opponentName wins!"
            resultHint == "draw" -> "Draw!"
            // Pre-result-hint clients (or unknown) — fall back to color matching.
            rawResult.contains("draw") || rawResult.contains("stalemate") || winnerColor == "draw" -> "Draw!"
            rawResult.contains("abort") -> "Game Aborted"
            (winnerColor == "white" || winnerColor == "black") && (myColor == "white" || myColor == "black") -> {
                if (winnerColor == myColor) "$myName wins!" else "$opponentName wins!"
            }
            // Last-resort: never trust raw rewritten text — show generic so we don't
            // attribute the wrong winner.
            else -> "Game Over"
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
                                    gameResult = null; activeChallenge = null
                                    activeGameUrl = "https://lichess.org/$resultGameId#analysis"
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

    // Opponent abandoned mid-game — close the Lichess WebView Dialog
    // immediately and route into the standard Game Over dialog so the user
    // actually sees the "you win" message instead of a frozen Lichess loading
    // screen. The WebView Dialog overlays the entire app, so showing a snackbar
    // behind it has no effect.
    // First-writer-wins: if Lichess's pollGameEnd already wrote a Game Over
    // result, don't overwrite it with the synthetic abandon string (would flip
    // the displayed name back and forth between attributions).
    LaunchedEffect(uiState.pendingAbandonResult) {
        val abandonResult = uiState.pendingAbandonResult ?: return@LaunchedEffect
        activeGameUrl = null
        if (gameResult == null) {
            gameResult = abandonResult
        }
        viewModel.consumeAbandonResult()
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
            onRemove = { viewModel.removeFriend(it) },
            onDismiss = { viewModel.toggleFriends() }
        )
    }

    // ── In-app game WebView ──
    // Track the last opponent for "Play Again"

    if (activeGameUrl != null) {
        // Render the game in a Dialog so it gets its own Window — fully covers
        // the app bottom nav + ad banner. The dialog window is sized to fill
        // the whole screen, and imePadding() inside the game screen handles
        // the keyboard.
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            LichessGameScreen(
                url = activeGameUrl!!,
                boardTheme = uiState.boardTheme,
                myName = uiState.myProfile?.visibleName ?: "You",
                opponentName = lastChallengedPlayer?.displayName ?: "Opponent",
                onClose = { result ->
                    if (result == null) viewModel.notifyGameLeft()
                    activeGameUrl = null
                    viewModel.checkPendingResults()
                    // First-writer-wins: if pendingAbandonResult already routed
                    // a synthetic gameResult through this composable, don't let
                    // a late-firing Lichess pollGameEnd overwrite it (causes the
                    // dialog to flicker between two attributions).
                    if (result != null && gameResult == null) {
                        gameResult = result
                    }
                },
                onAbandon = { viewModel.notifyGameLeft() },
                onOpenExternal = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activeGameUrl)))
                }
            )
        }
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
                            // includeFontPadding=false + lineHeight=fontSize to
                            // strip the default ascender/descender padding that
                            // pushes single digits off-center in tight circles.
                            Text(
                                "$onlineFriendCount",
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                textAlign = TextAlign.Center,
                                style = LocalTextStyle.current.copy(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
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
            // Tourney — visible in all builds. Tap behavior is gated by the
            // `tournament_enabled` Firebase Remote Config flag: while false
            // we surface a "Feature under development" toast instead of
            // opening the create flow.
            GlassListCard(
                modifier = Modifier.weight(1f).clickable {
                    val rc = com.google.firebase.remoteconfig.FirebaseRemoteConfig.getInstance()
                    if (rc.getBoolean("tournament_enabled")) {
                        onCreateTournament()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "Feature under development",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                tintColor = Color(0xFFFFD700).copy(alpha = 0.10f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.EmojiEvents, null, Modifier.size(18.dp), tint = Color(0xFFFFD700))
                    Spacer(Modifier.width(6.dp))
                    Text("Tourney", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
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
                    Text("Ranks", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White, maxLines = 1)
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

        // Waiting for response with sender countdown. Use sentChallengeName
        // (set optimistically) so the UI shows instantly — sentChallengeId is
        // deliberately null until the Firestore write returns, to avoid fooling
        // the mutual-challenge auto-accept check.
        if (uiState.sentChallengeName != null) {
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
                        canChallenge = uiState.sentChallengeId == null && uiState.sentChallengeName == null && uiState.pendingChallenge == null,
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
            if (player.isFriend) {
                // Non-clickable confirmed-friend marker. Replaces the +icon
                // so users don't think they need to re-add an existing friend.
                Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.People, "Friend", Modifier.size(16.dp),
                        tint = Color(0xFF7C4DFF))
                }
            } else {
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
                        Text(currentProfile?.let { com.justpass.app.data.repository.ChessRepository().generateRandomName("placeholder") } ?: "SilentKnight#42",
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
    myName: String = "",
    opponentName: String = "",
    onClose: (result: String?) -> Unit,
    onAbandon: () -> Unit = {},
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

    // Belt-and-braces: if the game window leaves composition while still live
    // (user navigates away from chess tab, app put in background and reclaimed,
    // dialog disposed without going through onClose) — notify the opponent so
    // they get the "you win" toast instead of a frozen loading screen.
    // markGameLeft on the repo side is idempotent (transaction checks existing
    // leftBy), so this safely co-exists with the explicit onClose(null) path.
    val gameEndedRef = rememberUpdatedState(gameEnded)
    DisposableEffect(Unit) {
        onDispose {
            if (isLiveGame && gameEndedRef.value == null) {
                onAbandon()
            }
        }
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

    // Hold a WebView reference so we can null it out on dispose and stop the
    // JS-fired onGameEnd callback from touching a destroyed Compose state.
    // Without this, a delayed `JustPass.onGameEnd` Handler.post after the
    // user has navigated away can trigger NPE / IllegalStateException.
    val webViewRef = remember { object { var view: WebView? = null; var alive = true } }
    DisposableEffect(Unit) {
        onDispose {
            webViewRef.alive = false
            try {
                webViewRef.view?.let { wv ->
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.onPause()
                    wv.removeAllViews()
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.destroy()
                }
            } catch (_: Throwable) {}
            webViewRef.view = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)).imePadding()) {
        // Top bar — close + open in browser
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { if (isLiveGame) showExitConfirm = true else onClose(null) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Leave Game",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
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
                    // LAYER_TYPE_HARDWARE inside a Compose Dialog window can swallow
                    // touch events on Android 16 WebView (Edge 60 Fusion reproed).
                    // Default layer is fine — Lichess board renders smoothly without
                    // forcing an offscreen hardware layer.
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
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
                    // Hide Lichess chrome (header/footer/site nav) but KEEP .mchat,
                    // .clinput, and the in-game chat tabs so the in-app chat works.
                    // Avoid bare `nav` — Lichess uses <nav> for the chat tab strip.
                    val hideJs = "javascript:(function(){if(document.getElementById('jp'))return;var s=document.createElement('style');s.id='jp';s.textContent='body>header,body>.header,#top,.site-title,.site-buttons,body>footer,.fbt,.topnav,.dasher,.hamburger,.signin,.signup,body>nav,.site-nav,.lobby__table,.lobby__app,.round__top__table,.game__meta__infos{display:none!important}#top,.top,div[role=banner],div[class*=site-buttons],div[class*=topnav]{display:none!important}body,.round__app,.round{padding-top:0!important;margin-top:0!important}';(document.head||document.documentElement).appendChild(s);})()"
                    val boardCss = boardTheme.css
                    val themeJs = "javascript:(function(){if(document.getElementById('jp-theme'))return;var s=document.createElement('style');s.id='jp-theme';s.textContent='$boardCss';(document.head||document.documentElement).appendChild(s);})()"

                    // Dock Lichess chat at the bottom with app theme colours so it's always
                    // visible + styled to match the rest of JustPass.
                    val chatCss = (
                        ".mchat{position:fixed!important;bottom:0!important;left:0!important;right:0!important;" +
                        "max-height:48vh!important;display:flex!important;flex-direction:column!important;" +
                        "background:#1A1A2E!important;color:#FFFFFF!important;" +
                        "border-top:1px solid rgba(0,230,118,0.25)!important;" +
                        "box-shadow:0 -4px 14px rgba(0,0,0,0.4)!important;z-index:999!important;font-size:13px!important}" +
                        ".mchat__tabs{background:#0A0F1A!important;border-bottom:1px solid rgba(255,255,255,0.08)!important;" +
                        "padding:2px 4px!important;flex-shrink:0!important}" +
                        ".mchat__tab{color:rgba(255,255,255,0.55)!important;padding:6px 10px!important;" +
                        "font-weight:500!important;text-transform:none!important;font-size:12px!important}" +
                        ".mchat__tab.mchat__tab-active,.mchat__tab.active{color:#00E676!important;" +
                        "border-bottom:2px solid #00E676!important}" +
                        ".mchat__content{flex:1!important;overflow-y:auto!important;background:#1A1A2E!important;" +
                        "padding:4px 6px!important}" +
                        // iMessage-style bubble list — use block layout so flex squish can't shrink bubbles
                        ".mchat__messages{background:transparent!important;color:#FFFFFF!important;" +
                        "padding:8px 6px!important;display:block!important;list-style:none!important;" +
                        "margin:0!important}" +
                        ".mchat__messages li,.mchat li{color:#FFFFFF!important;" +
                        "padding:9px 14px!important;line-height:1.4!important;border:none!important;" +
                        "background:#2A3240!important;border-radius:18px!important;" +
                        "max-width:75%!important;width:fit-content!important;" +
                        "word-break:break-word!important;white-space:normal!important;" +
                        "margin:4px auto 4px 0!important;" +  // opponent = left (auto right margin pushes left)
                        "border-bottom-left-radius:5px!important;font-size:14px!important;" +
                        "min-height:0!important;height:auto!important;flex:none!important;" +
                        "display:block!important;" +
                        "animation:jpBubbleIn 0.18s ease-out!important}" +
                        // Force all descendants to inherit bubble colour + size — some Lichess
                        // rules colour usernames/timestamps differently and shrink the text.
                        ".mchat__messages li *,.mchat li *{color:inherit!important;" +
                        "font-size:inherit!important;line-height:inherit!important;" +
                        "background:transparent!important}" +
                        "@keyframes jpBubbleIn{from{opacity:0;transform:translateY(4px)}to{opacity:1;transform:none}}" +
                        // Own messages = right + iMessage blue
                        ".mchat__messages li.jp-self,.mchat li.jp-self{" +
                        "background:linear-gradient(180deg,#2F95FF 0%,#007AFF 100%)!important;" +
                        "color:#FFFFFF!important;" +
                        "margin:4px 0 4px auto!important;" +  // flip: auto left margin pushes right
                        "border-bottom-right-radius:5px!important;border-bottom-left-radius:18px!important}" +
                        // Hide the "Anonymous:" labels — bubbles carry the sender context
                        ".mchat__messages .user-link,.mchat__messages li .user-link," +
                        ".mchat__messages li a,.mchat li a{display:none!important}" +
                        // Wrap the say row in a flex so we can stick an icon before the input.
                        // Target every form control inside .mchat so we don't miss Lichess' input.
                        ".mchat__say,.mchat__content .mchat__say,.mchat form{background:#0A0F1A!important;" +
                        "padding:10px 12px!important;flex-shrink:0!important;" +
                        "border-top:1px solid rgba(255,255,255,0.08)!important;" +
                        "display:flex!important;align-items:center!important;gap:8px!important;position:relative!important}" +
                        // Speech-bubble glyph signals "type here"
                        ".mchat__say::before,.mchat form::before{content:'\\1F4AC'!important;font-size:20px!important;opacity:0.8!important;flex-shrink:0!important}" +
                        // Every input/textarea that might be the chat input — broad net
                        ".mchat input,.mchat textarea,.mchat__say,input.mchat__say,.clinput," +
                        ".mchat__say input,.mchat__say textarea,.mchat form input,.mchat form textarea{" +
                        "background:#1E2A3A!important;" +
                        "color:#FFFFFF!important;border:1.5px solid rgba(0,230,118,0.45)!important;" +
                        "border-radius:22px!important;padding:10px 16px!important;font-size:14px!important;" +
                        "width:100%!important;flex:1!important;box-sizing:border-box!important;" +
                        "caret-color:#00E676!important;transition:border-color 0.15s,box-shadow 0.15s!important;" +
                        "min-height:40px!important;line-height:1.3!important;" +
                        "box-shadow:0 0 0 0 rgba(0,230,118,0)!important;display:block!important}" +
                        ".mchat input::placeholder,.mchat textarea::placeholder,.mchat__say::placeholder," +
                        "input.mchat__say::placeholder,.clinput::placeholder{" +
                        "color:rgba(255,255,255,0.55)!important;font-style:normal!important}" +
                        ".mchat input:focus,.mchat textarea:focus,.mchat__say:focus," +
                        "input.mchat__say:focus,.clinput:focus{" +
                        "border-color:#00E676!important;outline:none!important;" +
                        "box-shadow:0 0 0 3px rgba(0,230,118,0.2)!important;" +
                        "background:#22304A!important}" +
                        // Preset chat buttons for anonymous users (HI / GL / HF / U2)
                        ".mchat__presets,.preset,[class*=preset]{background:#0A0F1A!important;" +
                        "padding:8px!important;display:flex!important;flex-wrap:wrap!important;gap:6px!important;" +
                        "border-top:1px solid rgba(255,255,255,0.08)!important}" +
                        ".mchat__presets button,.preset button,[class*=preset] button,.mchat__say button{" +
                        "background:#1E2A3A!important;color:#FFFFFF!important;" +
                        "border:1.5px solid rgba(0,230,118,0.35)!important;" +
                        "border-radius:18px!important;padding:8px 14px!important;" +
                        "font-size:13px!important;font-weight:600!important;" +
                        "min-width:60px!important;cursor:pointer!important;flex:1!important}" +
                        ".mchat__presets button:active,.preset button:active{" +
                        "background:#00E676!important;color:#000!important}" +
                        // Leave room below the board so chat doesn't cover it
                        "main,.round,.round__app{padding-bottom:48vh!important}"
                    )
                    val chatJs = "javascript:(function(){var id='jp-chat';var st=document.getElementById(id);if(!st){st=document.createElement('style');st.id=id;(document.head||document.documentElement).appendChild(st);}st.textContent=\"$chatCss\";" +
                        // Friendly placeholder on whatever input Lichess rendered
                        "function setPh(){var nodes=document.querySelectorAll('.mchat input, .mchat textarea, input.mchat__say, .clinput, .mchat__say input, .mchat form input, .mchat form textarea');nodes.forEach(function(i){if(!i.getAttribute('data-jp-ph')){i.setAttribute('placeholder','Type a message…');i.setAttribute('data-jp-ph','1');}});}" +
                        // iMessage-style self-message tagging: remember every outgoing message (input Enter + preset clicks)
                        // and tag matching <li>s with .jp-self so CSS can flip them to the right.
                        "window.jpPending=window.jpPending||[];" +
                        "function armChat(){" +
                          "document.querySelectorAll('.mchat input, input.mchat__say, .mchat textarea').forEach(function(i){" +
                            "if(i._jpArmed)return;i._jpArmed=true;" +
                            "i.addEventListener('keydown',function(e){if(e.key==='Enter'&&!e.shiftKey){var t=(i.value||'').trim();if(t)window.jpPending.push(t);}});" +
                          "});" +
                          "document.querySelectorAll('.mchat__presets button,.preset button,.mchat__say button').forEach(function(b){" +
                            "if(b._jpArmed)return;b._jpArmed=true;" +
                            "b.addEventListener('click',function(){var t=(b.textContent||'').trim();if(t)window.jpPending.push(t);});" +
                          "});" +
                        "}" +
                        "function tagSelf(){" +
                          "document.querySelectorAll('.mchat__messages li:not([data-jp-tagged])').forEach(function(li){" +
                            "li.setAttribute('data-jp-tagged','1');" +
                            "var txt=(li.textContent||'').trim();" +
                            "for(var i=0;i<window.jpPending.length;i++){" +
                              "if(txt.indexOf(window.jpPending[i])>=0){li.classList.add('jp-self');window.jpPending.splice(i,1);break;}" +
                            "}" +
                          "});" +
                        "}" +
                        "setPh();armChat();tagSelf();" +
                        // Slowed from 600ms → 2500ms. The chat polling caused
                        // ~500 querySelectorAll passes per 5min on long games,
                        // contributing to a slow OOM (bug report: "crash in
                        // 5 min"). 2.5s is still responsive enough for chat.
                        // Auto-stop after game ends.
                        "var _jpChatT=setInterval(function(){" +
                          "if(window._jpDone){clearInterval(_jpChatT);return;}" +
                          "setPh();armChat();tagSelf();" +
                        "},2500);" +
                        "})()"

                    // Rewrite any "White"/"Black" text on the Lichess page with the
                    // actual player names — so when the game ends the user sees
                    // "Tarun is victorious" instead of "White is victorious" during
                    // the brief window before the app's result dialog takes over.
                    val myNameJs = org.json.JSONObject.quote(myName.ifBlank { "You" })
                    val oppNameJs = org.json.JSONObject.quote(opponentName.ifBlank { "Opponent" })
                    val renameJs = """javascript:(function(){
                        if(window._jpName)return;window._jpName=1;
                        var MY=${myNameJs};var OPP=${oppNameJs};
                        function detectMyColor(){
                            var bd=document.querySelector('cg-board,.cg-board');
                            var cl='';
                            if(bd){cl=(bd.className||'')+' '+(bd.parentElement?bd.parentElement.className:'');}
                            if(cl.indexOf('orientation-black')>=0)return 'black';
                            if(cl.indexOf('orientation-white')>=0)return 'white';
                            var bw=document.querySelector('.round__app,.board-wrap');
                            if(bw){var c=bw.className||'';if(c.indexOf('orientation-black')>=0)return 'black';if(c.indexOf('orientation-white')>=0)return 'white';}
                            return '';
                        }
                        function rewrite(){
                            var myC=detectMyColor();if(!myC)return;
                            var whiteName=(myC==='white')?MY:OPP;
                            var blackName=(myC==='white')?OPP:MY;
                            var containers=document.querySelectorAll('.result-wrap,.status,.rresult,.result,.game-end,.round__app__table,.rmoves,.mchat__messages,.tour-table');
                            containers.forEach(function(c){
                                var walker=document.createTreeWalker(c,NodeFilter.SHOW_TEXT,null);
                                var nodes=[];var n;while(n=walker.nextNode())nodes.push(n);
                                nodes.forEach(function(tn){
                                    var t=tn.nodeValue;if(!t)return;
                                    var t2=t.replace(/\bWhite\b/g,whiteName).replace(/\bBlack\b/g,blackName);
                                    if(t2!==t)tn.nodeValue=t2;
                                });
                            });
                        }
                        // Slowed from 400ms → 2000ms. The treeWalker rewrite
                        // pass was running 750+ times per 5min, allocating
                        // arrays for every text node on every tick. Major
                        // contributor to the 5-min OOM. Auto-stop on game end.
                        rewrite();
                        var _jpNameT=setInterval(function(){
                            if(window._jpDone){clearInterval(_jpNameT);return;}
                            rewrite();
                        },2000);
                    })()""".trimIndent().replace("\n", "")

                    // Poll for game-over with the broadest possible detection.
                    // Lichess mobile uses hashed class names for some elements,
                    // so .result-wrap / .rmoves can be missing or renamed.
                    // Strategy: scan document.body.innerText for a result token
                    // (1-0 / 0-1 / ½-½). On clock-flag-fall the move list AND
                    // the bottom status text both render the token, so a body
                    // scan catches it regardless of selector drift.
                    // Console.log every iteration so logcat (via WebChromeClient
                    // .onConsoleMessage) shows whether the poll is even firing.
                    val pollGameEnd = """javascript:(function(){
                        if(window._jpPoll)return;window._jpPoll=1;
                        console.log('[JP] poll installed');
                        var _jpPollT=setInterval(function(){
                            if(window._jpDone){clearInterval(_jpPollT);return;}
                            var b=document.body?(document.body.innerText||''):'';
                            var m=b.match(/(?:^|\s)(1-0|0-1|½-½|1\/2-1\/2)(?:\s|$)/);
                            var wr=document.querySelector('.result-wrap');
                            // No console logging on every tick — body.innerText
                            // can be 50KB+ on a long game, and ~200 ticks per
                            // 5 min flooded logcat + contributed to memory.
                            if(!m&&!wr)return;
                            var winner='unknown';
                            if(wr){
                                var cl=wr.className||'';
                                if(cl.indexOf('white')>=0)winner='white';
                                else if(cl.indexOf('black')>=0)winner='black';
                            }
                            if(winner==='unknown'&&m){
                                var t=m[1];
                                if(t==='1-0')winner='white';
                                else if(t==='0-1')winner='black';
                                else winner='draw';
                            }
                            if(winner==='unknown'){console.log('[JP] poll: winner unknown');return;}
                            var stEl=(wr&&wr.querySelector('.status'))||document.querySelector('.rresult,.status');
                            var txt=stEl?(stEl.textContent||'').trim():'Game Over';
                            var myC='unknown';
                            // Lichess actually puts orientation-white/orientation-black on
                            // the .cg-wrap div, NOT on cg-board itself (cg-board's className
                            // is empty). Check .cg-wrap first — that's where the orientation
                            // class lives. Without this, the JS poll reported "myC=unknown",
                            // so result=unknown, and ChessScreen never auto-exited after a
                            // game ended (you'd end up stuck on the post-game screen with
                            // only "Leave Game" working).
                            var wrap=document.querySelector('.cg-wrap,[class*=orientation]');
                            if(wrap){
                                var wcl=wrap.className||'';
                                if(wcl.indexOf('orientation-black')>=0)myC='black';
                                else if(wcl.indexOf('orientation-white')>=0)myC='white';
                            }
                            if(myC==='unknown'){
                                var bd=document.querySelector('cg-board,.cg-board');
                                if(bd){
                                    var bcl=(bd.className||'')+' '+(bd.parentElement?bd.parentElement.className:'');
                                    if(bcl.indexOf('orientation-black')>=0)myC='black';
                                    else if(bcl.indexOf('orientation-white')>=0)myC='white';
                                }
                            }
                            if(myC==='unknown'){
                                var bw=document.querySelector('.round__app,.board-wrap,main');
                                if(bw){var bwc=bw.className||'';if(bwc.indexOf('black')>=0)myC='black';else if(bwc.indexOf('white')>=0)myC='white';}
                            }
                            var result='unknown';
                            if(winner==='draw')result='draw';
                            else if(myC!=='unknown')result=(winner===myC)?'mywin':'oppwin';
                            window._jpDone=1;
                            console.log('[JP] FIRING winner='+winner+' myC='+myC+' result='+result);
                            JustPass.onGameEnd(winner+'|'+txt+'|'+myC+'|'+result);
                        },1500);
                    })()""".trimIndent().replace("\n", "")

                    // JS interface to receive game-end callback. Wrap in try/catch
                    // because pollGameEnd runs on a setInterval inside Lichess — if
                    // the WebView is destroyed but the interval hasn't been cleared
                    // yet (race during navigation), the callback can fire after
                    // composition is gone and crash on stale state references.
                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun onGameEnd(result: String) {
                            try {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    if (!webViewRef.alive) return@post
                                    if (gameEnded == null) gameEnded = result
                                }
                            } catch (_: Throwable) { /* webview gone — drop */ }
                        }
                    }, "JustPass")

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            super.onPageFinished(view, pageUrl)
                            view?.evaluateJavascript(hideJs, null)
                            view?.evaluateJavascript(themeJs, null)
                            view?.evaluateJavascript(chatJs, null)
                            view?.evaluateJavascript(renameJs, null)
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

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                            val m = msg?.message() ?: return false
                            if (m.contains("[JP]")) {
                                android.util.Log.d("ChessJS", "${msg.messageLevel()} $m")
                            }
                            return true
                        }
                    }
                    webViewRef.view = this
                    loadUrl(url)
                }
            }
        )
        // AdBanner removed from inside the live game — its auto-refresh cycle
        // (every 60s) plus the WebView's running Lichess page caused enough
        // memory pressure to crash long games (bug report: "crash in 5 min").
        // Ads still show in the chess lobby (outside this Dialog) so total
        // ad impressions per session stay roughly the same.
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
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingRemoval by remember { mutableStateOf<ChessProfile?>(null) }
    val onlineIds = onlinePlayers.map { it.id }.toSet()
    // CF Worker tags presence with Firebase UID; chess_profiles keys by
    // p_${rollHash}. id-set lookup never matches across the two ID-spaces, so
    // also match by displayName as fallback. See ChessRepositoryV2.emitOnlinePlayers.
    val onlineNames = onlinePlayers.map { it.displayName }.filter { it.isNotBlank() }.toSet()
    fun ChessProfile.isOnlineNow(): Boolean =
        id in onlineIds || (visibleName.isNotBlank() && visibleName in onlineNames)
    val onlineCount = friends.count { it.isOnlineNow() }
    val sortedFriends = friends.sortedWith(
        compareByDescending<ChessProfile> { it.isOnlineNow() }.thenByDescending { it.rating }
    )

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
                        val isOnline = friend.isOnlineNow()
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
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { pendingRemoval = friend },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close, "Remove friend",
                                    Modifier.size(16.dp),
                                    tint = Color(0xFFFF5252).copy(alpha = 0.7f)
                                )
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

    pendingRemoval?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            containerColor = Color(0xFF1E2A3A),
            title = { Text("Remove friend?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text("Remove ${target.visibleName} from your friends list?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                Button(
                    onClick = { onRemove(target.id); pendingRemoval = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) { Text("Remove", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") }
            }
        )
    }
}
