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
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChessUiState(
    val isOnline: Boolean = false,
    val isLoading: Boolean = true,
    val myProfile: ChessProfile? = null,
    val onlinePlayers: List<OnlinePlayer> = emptyList(),
    val leaderboard: List<ChessProfile> = emptyList(),
    val friendRequests: List<FriendRequest> = emptyList(),
    val pendingChallenge: ChessChallenge? = null,
    val sentChallengeId: String? = null,
    val sentChallengeName: String? = null,
    val acceptedChallenge: ChessChallenge? = null,
    val showNameSetup: Boolean = false,
    val showLeaderboard: Boolean = false,
    val errorMessage: String? = null
)

class ChessViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ChessRepository()
    private val securePrefs = SecurePreferences.getInstance(application)

    private val _uiState = MutableStateFlow(ChessUiState())
    val uiState: StateFlow<ChessUiState> = _uiState.asStateFlow()

    private var onlineListener: ListenerRegistration? = null
    private var incomingListener: ListenerRegistration? = null
    private var friendReqListener: ListenerRegistration? = null
    private var sentChallengeListener: ListenerRegistration? = null
    private var heartbeatJob: Job? = null
    private var friendIds: Set<String> = emptySet()

    fun goOnline() {
        val rollNumber = securePrefs.rollNumber ?: return
        val realName = securePrefs.displayName ?: "Player"
        val playerId = repo.getPlayerId(rollNumber)

        viewModelScope.launch {
            val profile = repo.getOrCreateProfile(playerId, realName, rollNumber)
            if (profile == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Failed to connect")
                return@launch
            }

            _uiState.value = _uiState.value.copy(myProfile = profile, isLoading = false)

            // If first time (no name chosen), show setup
            if (profile.gamesPlayed == 0 && profile.nameMode == "random") {
                _uiState.value = _uiState.value.copy(showNameSetup = true)
            }

            // Go online with visible name
            repo.goOnline(playerId, profile.visibleName)
            _uiState.value = _uiState.value.copy(isOnline = true)

            // Load friends
            friendIds = repo.getFriendIds(playerId)

            // Listen for online players
            onlineListener = repo.listenOnlinePlayers(playerId, friendIds) { players ->
                _uiState.value = _uiState.value.copy(onlinePlayers = players)
            }

            // Listen for incoming challenges
            incomingListener = repo.listenIncomingChallenges(playerId) { challenge ->
                _uiState.value = _uiState.value.copy(pendingChallenge = challenge)
            }

            // Listen for friend requests
            friendReqListener = repo.listenFriendRequests(playerId) { requests ->
                _uiState.value = _uiState.value.copy(friendRequests = requests)
            }

            // Cleanup + heartbeat
            repo.cleanupExpiredChallenges()
            heartbeatJob = viewModelScope.launch {
                while (isActive) {
                    delay(30_000L)
                    repo.heartbeat(playerId)
                }
            }
        }
    }

    fun goOffline() {
        val id = _uiState.value.myProfile?.id ?: return
        viewModelScope.launch { repo.goOffline(id) }
        cleanup()
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
            // Update online presence with new name
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
                repo.processGameResult(game)
            }
            // Refresh profile after processing
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
        val id = _uiState.value.myProfile?.id
        if (!id.isNullOrBlank()) {
            viewModelScope.launch { repo.goOffline(id) }
        }
        cleanup()
    }
}
