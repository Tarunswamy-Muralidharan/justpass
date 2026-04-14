package com.example.attendancewidgetlaudea.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import com.example.attendancewidgetlaudea.data.model.BoardTheme
import com.example.attendancewidgetlaudea.data.model.ChessChallenge
import com.example.attendancewidgetlaudea.data.model.ChessProfile
import com.example.attendancewidgetlaudea.data.model.FriendRequest
import com.example.attendancewidgetlaudea.data.model.OnlinePlayer
import com.example.attendancewidgetlaudea.data.repository.ChessRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
    private val securePrefs = SecurePreferences.getInstance(application)
    private val prefs = application.getSharedPreferences("chess_history", 0)

    private val _uiState = MutableStateFlow(ChessUiState())
    val uiState: StateFlow<ChessUiState> = _uiState.asStateFlow()

    private var onlineListener: ListenerRegistration? = null
    private var incomingListener: ListenerRegistration? = null
    private var friendReqListener: ListenerRegistration? = null
    private var sentChallengeListener: ListenerRegistration? = null
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

            repo.goOnline(myPlayerId, profile.visibleName)
            _uiState.value = _uiState.value.copy(isOnline = true)

            friendIds = repo.getFriendIds(myPlayerId)

            onlineListener = repo.listenOnlinePlayers(myPlayerId, friendIds) { players ->
                _uiState.value = _uiState.value.copy(onlinePlayers = players)
            }

            incomingListener = repo.listenIncomingChallenges(myPlayerId) { challenge ->
                // Check if there's already a pending challenge we sent to this person
                // (mutual challenge prevention — first one wins)
                if (_uiState.value.sentChallengeId != null && _uiState.value.sentChallengeName == challenge.fromName) {
                    // We already challenged them — ignore their challenge, ours was first
                    // Actually we need to check timestamps: whoever sent first wins
                    viewModelScope.launch {
                        repo.declineChallenge(challenge.id) // auto-decline theirs
                    }
                    return@listenIncomingChallenges
                }

                val elapsed = System.currentTimeMillis() - challenge.timestamp
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
                        repo.declineChallenge(challenge.id)
                        _uiState.value = _uiState.value.copy(
                            pendingChallenge = null,
                            challengeCountdown = null
                        )
                    }
                }
            }

            friendReqListener = repo.listenFriendRequests(myPlayerId) { requests ->
                _uiState.value = _uiState.value.copy(friendRequests = requests)
            }

            repo.cleanupExpiredChallenges()

            // Load local match history
            loadMatchHistory()

            heartbeatJob = viewModelScope.launch {
                while (isActive) {
                    delay(25_000L) // heartbeat every 25s (stale threshold is 45s)
                    repo.heartbeat(myPlayerId)
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
                    repo.cleanupExpiredChallenges()
                }
            }
        }
    }

    fun goOffline() {
        cleanup()
        // NonCancellable so the delete completes even if scope is cancelled
        if (myPlayerId.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                try {
                    FirebaseFirestore.getInstance()
                        .collection("chess_online")
                        .document(myPlayerId)
                        .delete()
                        .await()
                } catch (_: Exception) {}
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
            repo.goOnline(profile.id, updated.visibleName)
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
        if (_uiState.value.sentChallengeId != null) return

        viewModelScope.launch {
            // Mutual challenge prevention: check if they already challenged us
            val existingChallenge = repo.checkExistingChallenge(player.id, myPlayerId)
            if (existingChallenge != null) {
                // They already sent us a challenge — auto-show it instead
                _uiState.value = _uiState.value.copy(
                    errorMessage = "${player.displayName} already challenged you!"
                )
                return@launch
            }

            val challengeId = repo.sendChallenge(profile.id, profile.visibleName, player.id, player.displayName, timeControl)
            if (challengeId != null) {
                _uiState.value = _uiState.value.copy(
                    sentChallengeId = challengeId, sentChallengeName = player.displayName,
                    senderCountdown = 15
                )
                sentChallengeListener = repo.listenChallengeStatus(challengeId) { challenge ->
                    when (challenge.status) {
                        "accepted" -> {
                            senderCountdownJob?.cancel()
                            _uiState.value = _uiState.value.copy(
                                acceptedChallenge = challenge, sentChallengeId = null,
                                sentChallengeName = null, senderCountdown = null
                            )
                        }
                        "declined" -> {
                            senderCountdownJob?.cancel()
                            _uiState.value = _uiState.value.copy(
                                sentChallengeId = null, sentChallengeName = null,
                                senderCountdown = null,
                                errorMessage = "${challenge.toName} declined"
                            )
                            sentChallengeListener?.remove()
                        }
                    }
                }
                // Start sender countdown (15 seconds)
                senderCountdownJob?.cancel()
                senderCountdownJob = viewModelScope.launch {
                    var timeLeft = 15
                    while (timeLeft > 0 && isActive) {
                        delay(1000L)
                        timeLeft--
                        if (_uiState.value.sentChallengeId == challengeId) {
                            _uiState.value = _uiState.value.copy(senderCountdown = timeLeft)
                        }
                    }
                    // Auto-expire when countdown reaches 0
                    if (_uiState.value.sentChallengeId == challengeId) {
                        _uiState.value = _uiState.value.copy(
                            sentChallengeId = null, sentChallengeName = null,
                            senderCountdown = null,
                            errorMessage = "${player.displayName} didn't respond"
                        )
                        sentChallengeListener?.remove()
                        repo.declineChallenge(challengeId)
                    }
                }
            }
        }
    }

    fun acceptChallenge() {
        val challenge = _uiState.value.pendingChallenge ?: return
        countdownJob?.cancel()
        viewModelScope.launch {
            val urls = repo.acceptChallenge(challenge.id, challenge.timeControl)
            if (urls != null) {
                _uiState.value = _uiState.value.copy(
                    acceptedChallenge = challenge.copy(status = "accepted", gameUrl = urls.second, opponentUrl = urls.first),
                    pendingChallenge = null,
                    challengeCountdown = null
                )
            }
        }
    }

    fun declineChallenge() {
        val challenge = _uiState.value.pendingChallenge ?: return
        countdownJob?.cancel()
        viewModelScope.launch {
            repo.declineChallenge(challenge.id)
            _uiState.value = _uiState.value.copy(pendingChallenge = null, challengeCountdown = null)
        }
    }

    fun clearAcceptedChallenge() {
        _uiState.value = _uiState.value.copy(acceptedChallenge = null)
        // Check results shortly after returning from Lichess
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000L)
            checkPendingResults()
        }
    }

    fun cancelSentChallenge() {
        val challengeId = _uiState.value.sentChallengeId
        sentChallengeListener?.remove()
        senderCountdownJob?.cancel()
        _uiState.value = _uiState.value.copy(
            sentChallengeId = null, sentChallengeName = null, senderCountdown = null
        )
        // Clean up in Firestore
        if (challengeId != null) {
            viewModelScope.launch { repo.declineChallenge(challengeId) }
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

        val intent = android.content.Intent(app, com.example.attendancewidgetlaudea.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "chess")
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            app, 2003, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(app, "chess_challenge_channel")
            .setSmallIcon(com.example.attendancewidgetlaudea.R.drawable.ic_launcher_foreground)
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
        // NonCancellable so delete completes even after ViewModel destruction
        if (myPlayerId.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                try {
                    FirebaseFirestore.getInstance()
                        .collection("chess_online")
                        .document(myPlayerId)
                        .delete()
                        .await()
                } catch (_: Exception) {}
            }
        }
        cleanup()
    }
}
