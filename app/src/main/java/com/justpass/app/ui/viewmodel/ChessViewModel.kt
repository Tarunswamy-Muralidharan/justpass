package com.justpass.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.data.model.BoardTheme
import com.justpass.app.data.model.ChessChallenge
import com.justpass.app.data.model.ChessProfile
import com.justpass.app.data.model.FriendRequest
import com.justpass.app.data.model.OnlinePlayer
import com.justpass.app.data.repository.ChessLobby
import com.justpass.app.data.repository.ChessRepository
import com.justpass.app.data.repository.ChessRepositoryV2
import com.justpass.app.data.repository.FirestoreChessLobby
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MatchHistoryEntry(
    val opponentName: String,
    val result: String, // "win", "loss", "draw", "aborted"
    val timestamp: Long,
    val lichessGameId: String
)

data class ChessUiState(
    val isOnline: Boolean = false,
    val isLoading: Boolean = true,
    val myProfile: ChessProfile? = null,
    val onlinePlayers: List<OnlinePlayer> = emptyList(),
    val leaderboard: List<ChessProfile> = emptyList(),
    val friendRequests: List<FriendRequest> = emptyList(),
    val matchHistory: List<MatchHistoryEntry> = emptyList(),
    val pendingChallenge: ChessChallenge? = null,
    val sentChallengeId: String? = null,
    val sentChallengeName: String? = null,
    val sentChallengeToId: String? = null,
    val acceptedChallenge: ChessChallenge? = null,
    val showNameSetup: Boolean = false,
    val showLeaderboard: Boolean = false,
    val showHistory: Boolean = false,
    val showFriends: Boolean = false,
    val friendProfiles: List<ChessProfile> = emptyList(),
    val challengeCountdown: Int? = null,  // countdown for incoming challenge (receiver sees)
    val senderCountdown: Int? = null,     // countdown for sent challenge (sender sees)
    val boardTheme: BoardTheme = BoardTheme.CHESS_COM,
    val showThemePicker: Boolean = false,
    val errorMessage: String? = null
)

class ChessViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ChessRepository()

    // Lobby backend selected at construction time from Remote Config.
    // Flag OFF (default) → FirestoreChessLobby (byte-identical to v2.2.1 behaviour — delegates to repo).
    // Flag ON              → ChessRepositoryV2 (Cloudflare Durable Objects over WebSocket).
    // Flip the flag in Firebase Console to switch every client on next Remote Config activation.
    // The constructor-time capture is intentional: switching mid-session without tearing down listeners
    // would mix backends inside one VM instance. Instead the switch takes effect on the next lobby entry.
    private val lobby: ChessLobby = run {
        val useV2 = try {
            val rc = FirebaseRemoteConfig.getInstance()
            // Cloudflare Durable Object chess lobby. The Worker now rehydrates
            // its in-memory `players` map from state.getWebSockets() on
            // hibernation wake (chess-lobby/src/lobby.ts), so post-hibernation
            // joiners can see existing peers again. Server RC value still wins
            // — flip it false to roll back to Firestore without an APK update.
            rc.setDefaultsAsync(mapOf("chess_backend_v2" to true))
            rc.getBoolean("chess_backend_v2")
        } catch (_: Exception) { true }
        if (useV2) ChessRepositoryV2.getInstance() else FirestoreChessLobby(repo)
    }

    private val securePrefs = SecurePreferences.getInstance(application)
    private val prefs = application.getSharedPreferences("chess_history", 0)

    private val _uiState = MutableStateFlow(ChessUiState())
    val uiState: StateFlow<ChessUiState> = _uiState.asStateFlow()

    private var onlineListener: ListenerRegistration? = null
    private var incomingListener: ListenerRegistration? = null
    private var friendReqListener: ListenerRegistration? = null
    private var sentChallengeListener: ListenerRegistration? = null
    private var gameLeftListener: ListenerRegistration? = null
    private var heartbeatJob: Job? = null
    private var countdownJob: Job? = null
    private var senderCountdownJob: Job? = null
    private var friendIds: Set<String> = emptySet()
    private var myPlayerId: String = ""

    init {
        val saved = try { BoardTheme.valueOf(securePrefs.chessBoardTheme) } catch (_: Exception) { BoardTheme.CHESS_COM }
        _uiState.value = _uiState.value.copy(boardTheme = saved)
    }

    fun toggleThemePicker() {
        _uiState.value = _uiState.value.copy(showThemePicker = !_uiState.value.showThemePicker)
    }

    fun setBoardTheme(theme: BoardTheme) {
        securePrefs.chessBoardTheme = theme.name
        _uiState.value = _uiState.value.copy(boardTheme = theme, showThemePicker = false)
    }

    fun goOnline() {
        val rollNumber = securePrefs.rollNumber ?: return
        val realName = securePrefs.displayName ?: "Player"
        myPlayerId = repo.getPlayerId(rollNumber)

        viewModelScope.launch {
            val profile = repo.getOrCreateProfile(myPlayerId, realName, rollNumber)
            if (profile == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Failed to connect")
                return@launch
            }

            _uiState.value = _uiState.value.copy(myProfile = profile, isLoading = false)

            if (profile.gamesPlayed == 0 && profile.nameMode == "random") {
                _uiState.value = _uiState.value.copy(showNameSetup = true)
            }

            lobby.goOnline(myPlayerId, profile.visibleName)
            _uiState.value = _uiState.value.copy(isOnline = true)

            friendIds = repo.getFriendIds(myPlayerId)

            onlineListener = lobby.listenOnlinePlayers(myPlayerId, friendIds) { players ->
                _uiState.value = _uiState.value.copy(onlinePlayers = players)
            }

            incomingListener = lobby.listenIncomingChallenges(
                myId = myPlayerId,
                onChallengeRemoved = { removedId ->
                    // Challenger cancelled on their side (e.g. from the PWA) — clear the prompt
                    if (_uiState.value.pendingChallenge?.id == removedId) {
                        countdownJob?.cancel()
                        _uiState.value = _uiState.value.copy(
                            pendingChallenge = null,
                            challengeCountdown = null
                        )
                    }
                },
                onChallenge = { challenge ->
                // Mutual challenge: if we already sent a challenge to this same person,
                // auto-accept theirs (both want to play!) and cancel ours
                if (_uiState.value.sentChallengeId != null && challenge.fromId == _uiState.value.sentChallengeToId) {
                    val ourChallengeId = _uiState.value.sentChallengeId!!
                    senderCountdownJob?.cancel()
                    sentChallengeListener?.remove()
                    _uiState.value = _uiState.value.copy(
                        sentChallengeId = null, sentChallengeName = null,
                        sentChallengeToId = null, senderCountdown = null
                    )
                    // Cancel our outgoing challenge and accept theirs
                    viewModelScope.launch {
                        lobby.declineChallenge(ourChallengeId)
                        val urls = lobby.acceptChallenge(challenge.id, challenge.timeControl.toString())
                        if (urls != null) {
                            _uiState.value = _uiState.value.copy(
                                acceptedChallenge = challenge.copy(status = "accepted", gameUrl = urls.second, opponentUrl = urls.first),
                                pendingChallenge = null,
                                challengeCountdown = null
                            )
                            watchForOpponentLeave(challenge.id)
                        }
                    }
                    return@listenIncomingChallenges
                }

                // Prefer the server-anchored timestamp so sender + receiver
                // count from the same instant (no network latency / clock skew
                // gap). Falls back to the local timestamp when the sender is
                // on an older client that didn't write `serverTs`.
                val anchor = if (challenge.serverTimestamp > 0L) challenge.serverTimestamp else challenge.timestamp
                val elapsed = System.currentTimeMillis() - anchor
                val remaining = ((15_000L - elapsed) / 1000).toInt().coerceIn(0, 15)
                if (remaining <= 0) return@listenIncomingChallenges // already expired

                _uiState.value = _uiState.value.copy(
                    pendingChallenge = challenge,
                    challengeCountdown = remaining
                )

                // Show notification if app is in background
                showChallengeNotification(challenge.fromName)

                // Start receiver countdown
                countdownJob?.cancel()
                countdownJob = viewModelScope.launch {
                    var timeLeft = remaining
                    while (timeLeft > 0 && isActive) {
                        delay(1000L)
                        timeLeft--
                        _uiState.value = _uiState.value.copy(challengeCountdown = timeLeft)
                    }
                    // Auto-decline when countdown reaches 0
                    if (_uiState.value.pendingChallenge?.id == challenge.id) {
                        lobby.declineChallenge(challenge.id)
                        _uiState.value = _uiState.value.copy(
                            pendingChallenge = null,
                            challengeCountdown = null
                        )
                    }
                }
                }
            )

            friendReqListener = repo.listenFriendRequests(myPlayerId) { requests ->
                _uiState.value = _uiState.value.copy(friendRequests = requests)
            }

            lobby.cleanupExpiredChallenges()

            // Load local match history
            loadMatchHistory()

            heartbeatJob = viewModelScope.launch {
                // Heartbeat-then-delay so a freshly mounted ViewModel writes its
                // first timestamp immediately instead of waiting a full 90s —
                // defensive against the (rare) case where goOnline's initial
                // write lands but state churn causes a re-mount before the loop
                // would otherwise fire.
                while (isActive) {
                    lobby.heartbeat(myPlayerId)
                    delay(90_000L) // heartbeat every 90s (stale threshold is 150s) — V2 no-ops
                }
            }

            // Periodic result checker — polls every 30s for unchecked game results
            viewModelScope.launch {
                delay(5_000L) // initial delay
                while (isActive) {
                    checkPendingResults()
                    delay(30_000L)
                }
            }

            // Periodic challenge cleanup — expire stale challenges every 60s
            viewModelScope.launch {
                while (isActive) {
                    delay(60_000L)
                    lobby.cleanupExpiredChallenges()
                }
            }
        }
    }

    fun goOffline() {
        cleanup()
        // NonCancellable so the backend cleanup completes even if scope is cancelled.
        // Delegates to whichever lobby is active: FirestoreChessLobby → deletes the
        // presence doc, ChessRepositoryV2 → closes the WebSocket (server sees onClose
        // and broadcasts PRESENCE_DIFF removal to other clients).
        if (myPlayerId.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                try { lobby.goOffline(myPlayerId) } catch (_: Exception) {}
            }
        }
        _uiState.value = ChessUiState()
    }

    // ─── Name Setup ─────────────────────────────────────────────────────────

    fun setNameMode(mode: String, customNickname: String = "") {
        val profile = _uiState.value.myProfile ?: return
        val rollNumber = securePrefs.rollNumber ?: return
        val nickname = when (mode) {
            "custom" -> customNickname
            "real" -> profile.displayName
            else -> repo.generateRandomName(rollNumber)
        }

        viewModelScope.launch {
            repo.updateProfile(profile.id, nickname, mode)
            val updated = profile.copy(nickname = nickname, nameMode = mode)
            _uiState.value = _uiState.value.copy(myProfile = updated, showNameSetup = false)
            lobby.goOnline(profile.id, updated.visibleName)
        }
    }

    fun dismissNameSetup() {
        _uiState.value = _uiState.value.copy(showNameSetup = false)
    }

    fun openNameSetup() {
        _uiState.value = _uiState.value.copy(showNameSetup = true)
    }

    // ─── Leaderboard ────────────────────────────────────────────────────────

    fun toggleLeaderboard() {
        val show = !_uiState.value.showLeaderboard
        _uiState.value = _uiState.value.copy(showLeaderboard = show)
        if (show) {
            viewModelScope.launch {
                val board = repo.getLeaderboard()
                _uiState.value = _uiState.value.copy(leaderboard = board)
            }
        }
    }

    fun toggleFriends() {
        val show = !_uiState.value.showFriends
        _uiState.value = _uiState.value.copy(showFriends = show)
        if (show) {
            viewModelScope.launch {
                val profiles = repo.getFriendProfiles(friendIds)
                _uiState.value = _uiState.value.copy(friendProfiles = profiles)
            }
        }
    }

    // ─── Match History (local) ──────────────────────────────────────────────

    fun toggleHistory() {
        _uiState.value = _uiState.value.copy(showHistory = !_uiState.value.showHistory)
    }

    private fun loadMatchHistory() {
        val json = prefs.getString("history", null) ?: return
        try {
            val entries = json.split("|||").filter { it.isNotBlank() }.map { entry ->
                val parts = entry.split("||")
                MatchHistoryEntry(
                    opponentName = parts.getOrElse(0) { "Unknown" },
                    result = parts.getOrElse(1) { "unknown" },
                    timestamp = parts.getOrElse(2) { "0" }.toLongOrNull() ?: 0L,
                    lichessGameId = parts.getOrElse(3) { "" }
                )
            }.sortedByDescending { it.timestamp }
            _uiState.value = _uiState.value.copy(matchHistory = entries)
        } catch (_: Exception) {}
    }

    private fun saveMatchToHistory(opponentName: String, result: String, lichessGameId: String) {
        val entry = "$opponentName||$result||${System.currentTimeMillis()}||$lichessGameId"
        val existing = prefs.getString("history", "") ?: ""
        // Keep max 50 entries
        val entries = existing.split("|||").filter { it.isNotBlank() }.takeLast(49)
        val updated = (entries + entry).joinToString("|||")
        prefs.edit().putString("history", updated).apply()
        loadMatchHistory()
    }

    // ─── Friends ────────────────────────────────────────────────────────────

    fun sendFriendRequest(player: OnlinePlayer) {
        val profile = _uiState.value.myProfile ?: return
        viewModelScope.launch {
            val sent = repo.sendFriendRequest(profile.id, profile.visibleName, player.id, player.displayName)
            if (!sent) {
                _uiState.value = _uiState.value.copy(errorMessage = "Already friends or request pending")
            }
        }
    }

    fun acceptFriendRequest(request: FriendRequest) {
        viewModelScope.launch {
            repo.acceptFriendRequest(request.id)
            friendIds = friendIds + request.fromId
        }
    }

    fun declineFriendRequest(request: FriendRequest) {
        viewModelScope.launch { repo.declineFriendRequest(request.id) }
    }

    // ─── Challenges ─────────────────────────────────────────────────────────

    fun sendChallenge(player: OnlinePlayer, timeControl: String = "rapid_10") {
        val profile = _uiState.value.myProfile ?: return
        // Guard against double-send: either a real outgoing challenge exists,
        // OR the optimistic "waiting" UI is already showing.
        if (_uiState.value.sentChallengeId != null || _uiState.value.sentChallengeName != null) return

        // Optimistic UI — show "Waiting for opponent…" instantly. IMPORTANT: keep
        // sentChallengeId null during this window. The incoming-challenge listener
        // at line ~134 uses `sentChallengeId != null` to detect a mutual-challenge
        // scenario; if we set a placeholder ID here, a stale pending inbound doc
        // would auto-accept and open Lichess before the real opponent responded.
        _uiState.value = _uiState.value.copy(
            sentChallengeName = player.displayName,
            sentChallengeToId = player.id, senderCountdown = 15
        )

        // Anchor point for the sender countdown. Seeded with the local clock
        // so the optimistic UI shows 15s instantly; refreshed below with the
        // server-anchored timestamp the moment Firestore confirms the write,
        // so sender and receiver tick from the exact same instant.
        var anchorMs = System.currentTimeMillis()

        viewModelScope.launch {
            // Mutual challenge prevention: check if they already challenged us
            val existingChallenge = lobby.checkExistingChallenge(player.id, myPlayerId)
            if (existingChallenge != null) {
                // They already sent us a challenge — auto-show it instead
                _uiState.value = _uiState.value.copy(
                    sentChallengeId = null, sentChallengeName = null,
                    sentChallengeToId = null, senderCountdown = null,
                    errorMessage = "${player.displayName} already challenged you!"
                )
                return@launch
            }

            val challengeId = lobby.sendChallenge(profile.id, profile.visibleName, player.id, player.displayName, timeControl)
            if (challengeId != null) {
                _uiState.value = _uiState.value.copy(
                    sentChallengeId = challengeId, sentChallengeName = player.displayName,
                    sentChallengeToId = player.id
                )
                sentChallengeListener = lobby.listenChallengeStatus(challengeId) { challenge ->
                    // Re-anchor to server-anchored timestamp once it lands so
                    // the sender countdown matches the receiver's exactly.
                    if (challenge.serverTimestamp > 0L) {
                        anchorMs = challenge.serverTimestamp
                    }
                    when (challenge.status) {
                        "accepted" -> {
                            senderCountdownJob?.cancel()
                            _uiState.value = _uiState.value.copy(
                                acceptedChallenge = challenge, sentChallengeId = null,
                                sentChallengeName = null, sentChallengeToId = null, senderCountdown = null
                            )
                            watchForOpponentLeave(challenge.id)
                        }
                        "declined" -> {
                            senderCountdownJob?.cancel()
                            _uiState.value = _uiState.value.copy(
                                sentChallengeId = null, sentChallengeName = null,
                                sentChallengeToId = null, senderCountdown = null,
                                errorMessage = "${challenge.toName} declined"
                            )
                            sentChallengeListener?.remove()
                            // Auto-clear declined message after 3 seconds
                            viewModelScope.launch {
                                delay(3000L)
                                if (_uiState.value.errorMessage == "${challenge.toName} declined") {
                                    _uiState.value = _uiState.value.copy(errorMessage = null)
                                }
                            }
                        }
                    }
                }
                // Sender countdown: anchor every tick to anchorMs instead of a
                // local counter. Keeps sender + receiver in sync even if the
                // coroutine is briefly suspended; the server-confirmation path
                // above re-points anchorMs at the server-anchored timestamp.
                senderCountdownJob?.cancel()
                senderCountdownJob = viewModelScope.launch {
                    while (isActive) {
                        val elapsedSec = ((System.currentTimeMillis() - anchorMs) / 1000).toInt()
                        val timeLeft = (15 - elapsedSec).coerceAtLeast(0)
                        if (_uiState.value.sentChallengeId == challengeId) {
                            _uiState.value = _uiState.value.copy(senderCountdown = timeLeft)
                        }
                        if (timeLeft <= 0) break
                        delay(1000L)
                    }
                    // Auto-expire when countdown reaches 0
                    if (_uiState.value.sentChallengeId == challengeId) {
                        _uiState.value = _uiState.value.copy(
                            sentChallengeId = null, sentChallengeName = null,
                            sentChallengeToId = null, senderCountdown = null,
                            errorMessage = "${player.displayName} didn't respond"
                        )
                        sentChallengeListener?.remove()
                        lobby.declineChallenge(challengeId)
                        // Auto-clear after 3 seconds
                        viewModelScope.launch {
                            delay(3000L)
                            if (_uiState.value.errorMessage == "${player.displayName} didn't respond") {
                                _uiState.value = _uiState.value.copy(errorMessage = null)
                            }
                        }
                    }
                }
            } else {
                // Firestore write failed — clear the optimistic "pending" state
                // we set above so the user isn't stuck in a fake "waiting" UI.
                _uiState.value = _uiState.value.copy(
                    sentChallengeId = null, sentChallengeName = null,
                    sentChallengeToId = null, senderCountdown = null,
                    errorMessage = "Couldn't send challenge — please try again"
                )
            }
        }
    }

    fun acceptChallenge() {
        val challenge = _uiState.value.pendingChallenge ?: return
        countdownJob?.cancel()
        viewModelScope.launch {
            val urls = lobby.acceptChallenge(challenge.id, challenge.timeControl.toString())
            if (urls != null) {
                _uiState.value = _uiState.value.copy(
                    acceptedChallenge = challenge.copy(status = "accepted", gameUrl = urls.second, opponentUrl = urls.first),
                    pendingChallenge = null,
                    challengeCountdown = null
                )
                watchForOpponentLeave(challenge.id)
            }
        }
    }

    fun declineChallenge() {
        val challenge = _uiState.value.pendingChallenge ?: return
        countdownJob?.cancel()
        viewModelScope.launch {
            lobby.declineChallenge(challenge.id)
            _uiState.value = _uiState.value.copy(pendingChallenge = null, challengeCountdown = null)
        }
    }

    fun clearAcceptedChallenge() {
        gameLeftListener?.remove(); gameLeftListener = null
        _uiState.value = _uiState.value.copy(acceptedChallenge = null)
        // Check results shortly after returning from Lichess
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000L)
            checkPendingResults()
        }
    }

    /**
     * User exited an in-progress game — write leftBy to Firestore so the opponent's
     * client can show "Opponent left the game" instead of waiting for Lichess timeout.
     */
    fun notifyGameLeft() {
        val challengeId = _uiState.value.acceptedChallenge?.id ?: return
        val myId = _uiState.value.myProfile?.id ?: return
        val myName = _uiState.value.myProfile?.visibleName ?: "Opponent"
        viewModelScope.launch { repo.markGameLeft(challengeId, myId, myName) }
    }

    /**
     * Watch the accepted challenge doc for a leftBy field set by the opponent.
     * Called when a game starts; dropped in clearAcceptedChallenge().
     */
    fun watchForOpponentLeave(challengeId: String) {
        gameLeftListener?.remove()
        val myId = _uiState.value.myProfile?.id
        var claimed = false
        gameLeftListener = repo.listenGameLeft(challengeId) { leaverId, leaverName ->
            if (myId != null && leaverId == myId) return@listenGameLeft
            if (claimed) return@listenGameLeft
            claimed = true
            _uiState.value = _uiState.value.copy(
                errorMessage = "$leaverName left the game — you win!"
            )
            // Credit the win immediately — don't wait for Lichess flag fall.
            val accepted = _uiState.value.acceptedChallenge
            if (myId != null && accepted != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    val ok = repo.recordAbandonmentResult(challengeId, winnerId = myId, loserId = leaverId)
                    if (ok) {
                        val lichessId = accepted.lichessGameId
                        val existingIds = _uiState.value.matchHistory.map { it.lichessGameId }.toSet()
                        if (lichessId.isNotBlank() && lichessId !in existingIds) {
                            saveMatchToHistory(leaverName, "win", lichessId)
                        }
                        // Pull fresh profile so the dashboard stats update
                        val fresh = repo.getOrCreateProfile(myId, accepted.toName, "")
                        if (fresh != null) {
                            _uiState.value = _uiState.value.copy(myProfile = fresh)
                        }
                    }
                }
            }
        }
    }

    fun cancelSentChallenge() {
        val challengeId = _uiState.value.sentChallengeId
        sentChallengeListener?.remove()
        senderCountdownJob?.cancel()
        _uiState.value = _uiState.value.copy(
            sentChallengeId = null, sentChallengeName = null, sentChallengeToId = null, senderCountdown = null
        )
        // Clean up on whichever backend is active
        if (challengeId != null) {
            viewModelScope.launch { lobby.declineChallenge(challengeId) }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // ─── Auto game result checking ─────────────────────────────────────────

    fun checkPendingResults() {
        val profile = _uiState.value.myProfile ?: return
        viewModelScope.launch {
            val recentGames = repo.getRecentGames(profile.id)
            val unchecked = recentGames.filter { !it.resultChecked && it.lichessGameId.isNotBlank() }
            var anyProcessed = false
            for (game in unchecked) {
                val result = repo.checkLichessGameResult(game.lichessGameId)
                if (result != null && result != "ongoing") {
                    // processGameResult uses atomic claim — safe for both players to call
                    repo.processGameResult(game)
                    anyProcessed = true

                    // Save to local history (each device saves their own view)
                    val myColor = if (game.fromId == profile.id) game.fromColor.ifBlank { "white" }
                                  else if (game.fromColor == "white") "black" else "white"
                    val opponentName = if (game.fromId == profile.id) game.toName else game.fromName
                    val myResult = when {
                        result == "draw" -> "draw"
                        result == "aborted" -> "aborted"
                        result == myColor -> "win"
                        else -> "loss"
                    }
                    // Avoid duplicate local history entries
                    val existingIds = _uiState.value.matchHistory.map { it.lichessGameId }.toSet()
                    if (game.lichessGameId !in existingIds) {
                        saveMatchToHistory(opponentName, myResult, game.lichessGameId)
                    }
                }
            }
            // Also check for games that were already processed by the other player
            // but we haven't saved to local history yet
            val checked = recentGames.filter { it.resultChecked && it.lichessGameId.isNotBlank() }
            val existingIds = _uiState.value.matchHistory.map { it.lichessGameId }.toSet()
            for (game in checked) {
                if (game.lichessGameId in existingIds) continue
                val result = repo.checkLichessGameResult(game.lichessGameId)
                if (result != null && result != "ongoing") {
                    val myColor = if (game.fromId == profile.id) game.fromColor.ifBlank { "white" }
                                  else if (game.fromColor == "white") "black" else "white"
                    val opponentName = if (game.fromId == profile.id) game.toName else game.fromName
                    val myResult = when {
                        result == "draw" -> "draw"
                        result == "aborted" -> "aborted"
                        result == myColor -> "win"
                        else -> "loss"
                    }
                    saveMatchToHistory(opponentName, myResult, game.lichessGameId)
                    anyProcessed = true
                }
            }
            // Refresh profile + leaderboard after processing
            if (anyProcessed || unchecked.isNotEmpty()) {
                val refreshed = repo.getOrCreateProfile(profile.id, profile.displayName, "")
                if (refreshed != null) {
                    _uiState.value = _uiState.value.copy(myProfile = refreshed)
                }
                // Always refresh leaderboard cache so it's fresh when opened
                val board = repo.getLeaderboard()
                _uiState.value = _uiState.value.copy(leaderboard = board)
            }
        }
    }

    private fun cleanup() {
        onlineListener?.remove()
        incomingListener?.remove()
        friendReqListener?.remove()
        sentChallengeListener?.remove()
        heartbeatJob?.cancel()
        countdownJob?.cancel()
        senderCountdownJob?.cancel()
    }

    private fun showChallengeNotification(fromName: String) {
        val app = getApplication<Application>()
        // Only show notification if app is in background
        val am = app.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val isInForeground = am.runningAppProcesses?.any { proc ->
            proc.processName == app.packageName &&
            proc.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } ?: false

        if (isInForeground) return

        val notificationManager = app.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "chess_challenge_channel",
                "Chess Challenges",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming chess challenges"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = android.content.Intent(app, com.justpass.app.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "chess")
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            app, 2003, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(app, "chess_challenge_channel")
            .setSmallIcon(com.justpass.app.R.drawable.ic_launcher_foreground)
            .setContentTitle("Chess Challenge!")
            .setContentText("$fromName wants to play chess with you!")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000L) // auto-dismiss after 15s
            .build()

        notificationManager.notify(2003, notification)
    }

    override fun onCleared() {
        super.onCleared()
        // NonCancellable so the backend cleanup completes even after ViewModel destruction.
        // Routes through the active lobby (Firestore delete for V1, WS close for V2).
        if (myPlayerId.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                try { lobby.goOffline(myPlayerId) } catch (_: Exception) {}
            }
        }
        cleanup()
    }
}
