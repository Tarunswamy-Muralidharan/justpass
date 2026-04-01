package com.example.attendancewidgetlaudea.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import com.example.attendancewidgetlaudea.data.model.ChessChallenge
import com.example.attendancewidgetlaudea.data.model.ChessProfile
import com.example.attendancewidgetlaudea.data.model.FriendRequest
import com.example.attendancewidgetlaudea.data.model.OnlinePlayer
import com.example.attendancewidgetlaudea.data.repository.ChessRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
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
    private var friendIds: Set<String> = emptySet()
    private var myPlayerId: String = ""

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
                _uiState.value = _uiState.value.copy(pendingChallenge = challenge)
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
        }
    }

    fun goOffline() {
        cleanup()
        // Use GlobalScope + Dispatchers.IO so the delete completes even if ViewModel is destroyed
        if (myPlayerId.isNotBlank()) {
            GlobalScope.launch(Dispatchers.IO) {
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

    fun sendChallenge(player: OnlinePlayer) {
        val profile = _uiState.value.myProfile ?: return
        if (_uiState.value.sentChallengeId != null) return

        viewModelScope.launch {
            val challengeId = repo.sendChallenge(profile.id, profile.visibleName, player.id, player.displayName)
            if (challengeId != null) {
                _uiState.value = _uiState.value.copy(
                    sentChallengeId = challengeId, sentChallengeName = player.displayName
                )
                sentChallengeListener = repo.listenChallengeStatus(challengeId) { challenge ->
                    when (challenge.status) {
                        "accepted" -> {
                            _uiState.value = _uiState.value.copy(
                                acceptedChallenge = challenge, sentChallengeId = null, sentChallengeName = null
                            )
                        }
                        "declined" -> {
                            _uiState.value = _uiState.value.copy(
                                sentChallengeId = null, sentChallengeName = null,
                                errorMessage = "${challenge.toName} declined"
                            )
                            sentChallengeListener?.remove()
                        }
                    }
                }
            }
        }
    }

    fun acceptChallenge() {
        val challenge = _uiState.value.pendingChallenge ?: return
        viewModelScope.launch {
            val urls = repo.acceptChallenge(challenge.id)
            if (urls != null) {
                _uiState.value = _uiState.value.copy(
                    acceptedChallenge = challenge.copy(status = "accepted", gameUrl = urls.second, opponentUrl = urls.first),
                    pendingChallenge = null
                )
            }
        }
    }

    fun declineChallenge() {
        val challenge = _uiState.value.pendingChallenge ?: return
        viewModelScope.launch {
            repo.declineChallenge(challenge.id)
            _uiState.value = _uiState.value.copy(pendingChallenge = null)
        }
    }

    fun clearAcceptedChallenge() {
        _uiState.value = _uiState.value.copy(acceptedChallenge = null)
    }

    fun cancelSentChallenge() {
        sentChallengeListener?.remove()
        _uiState.value = _uiState.value.copy(sentChallengeId = null, sentChallengeName = null)
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
            for (game in unchecked) {
                val result = repo.checkLichessGameResult(game.lichessGameId)
                if (result != null && result != "ongoing") {
                    repo.processGameResult(game)

                    // Save to local history
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
                }
            }
            // Refresh profile
            if (unchecked.isNotEmpty()) {
                val refreshed = repo.getOrCreateProfile(profile.id, profile.displayName, "")
                if (refreshed != null) {
                    _uiState.value = _uiState.value.copy(myProfile = refreshed)
                }
            }
        }
    }

    private fun cleanup() {
        onlineListener?.remove()
        incomingListener?.remove()
        friendReqListener?.remove()
        sentChallengeListener?.remove()
        heartbeatJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        // GlobalScope so delete completes even after ViewModel destruction
        if (myPlayerId.isNotBlank()) {
            GlobalScope.launch(Dispatchers.IO) {
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
