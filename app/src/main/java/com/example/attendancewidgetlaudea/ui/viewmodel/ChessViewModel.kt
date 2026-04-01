package com.example.attendancewidgetlaudea.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import com.example.attendancewidgetlaudea.data.model.ChessChallenge
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
    val myName: String = "",
    val myId: String = "",
    val onlinePlayers: List<OnlinePlayer> = emptyList(),
    val pendingChallenge: ChessChallenge? = null,    // Incoming challenge
    val sentChallengeId: String? = null,               // Outgoing challenge ID
    val sentChallengeName: String? = null,             // Who we challenged
    val acceptedChallenge: ChessChallenge? = null,     // Accepted — has game URLs
    val errorMessage: String? = null
)

class ChessViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ChessRepository()
    private val securePrefs = SecurePreferences.getInstance(application)

    private val _uiState = MutableStateFlow(ChessUiState())
    val uiState: StateFlow<ChessUiState> = _uiState.asStateFlow()

    private var onlineListener: ListenerRegistration? = null
    private var incomingListener: ListenerRegistration? = null
    private var sentChallengeListener: ListenerRegistration? = null
    private var heartbeatJob: Job? = null

    fun goOnline() {
        val rollNumber = securePrefs.rollNumber ?: return
        val playerId = repo.getPlayerId(rollNumber)
        val displayName = repo.getAnonymousName(rollNumber)

        _uiState.value = _uiState.value.copy(myId = playerId, myName = displayName)

        viewModelScope.launch {
            repo.goOnline(playerId, displayName)
            repo.cleanupExpiredChallenges()
            _uiState.value = _uiState.value.copy(isOnline = true)
        }

        // Listen for online players
        onlineListener = repo.listenOnlinePlayers(playerId) { players ->
            _uiState.value = _uiState.value.copy(onlinePlayers = players)
        }

        // Listen for incoming challenges
        incomingListener = repo.listenIncomingChallenges(playerId) { challenge ->
            _uiState.value = _uiState.value.copy(pendingChallenge = challenge)
        }

        // Heartbeat every 30 seconds
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                repo.heartbeat(playerId)
            }
        }
    }

    fun goOffline() {
        val id = _uiState.value.myId
        if (id.isNotBlank()) {
            viewModelScope.launch { repo.goOffline(id) }
        }
        cleanup()
        _uiState.value = ChessUiState()
    }

    fun sendChallenge(player: OnlinePlayer) {
        val state = _uiState.value
        if (state.sentChallengeId != null) return // Already sent one

        viewModelScope.launch {
            val challengeId = repo.sendChallenge(
                fromId = state.myId, fromName = state.myName,
                toId = player.id, toName = player.displayName
            )
            if (challengeId != null) {
                _uiState.value = _uiState.value.copy(
                    sentChallengeId = challengeId,
                    sentChallengeName = player.displayName
                )
                // Listen for acceptance
                sentChallengeListener = repo.listenChallengeStatus(challengeId) { challenge ->
                    when (challenge.status) {
                        "accepted" -> {
                            _uiState.value = _uiState.value.copy(
                                acceptedChallenge = challenge,
                                sentChallengeId = null
                            )
                        }
                        "declined" -> {
                            _uiState.value = _uiState.value.copy(
                                sentChallengeId = null,
                                sentChallengeName = null,
                                errorMessage = "${challenge.toName} declined your challenge"
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
                    acceptedChallenge = challenge.copy(
                        status = "accepted",
                        gameUrl = urls.second,      // opponent gets second URL
                        opponentUrl = urls.first     // challenger gets first URL
                    ),
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun cancelSentChallenge() {
        sentChallengeListener?.remove()
        _uiState.value = _uiState.value.copy(sentChallengeId = null, sentChallengeName = null)
    }

    private fun cleanup() {
        onlineListener?.remove()
        incomingListener?.remove()
        sentChallengeListener?.remove()
        heartbeatJob?.cancel()
        onlineListener = null
        incomingListener = null
        sentChallengeListener = null
        heartbeatJob = null
    }

    override fun onCleared() {
        super.onCleared()
        val id = _uiState.value.myId
        if (id.isNotBlank()) {
            // Fire and forget — ViewModel is being destroyed
            viewModelScope.launch { repo.goOffline(id) }
        }
        cleanup()
    }
}
