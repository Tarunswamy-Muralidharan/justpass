package com.justpass.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.data.model.TournamentAdmins
import com.justpass.app.data.repository.AdminRolesRepository
import com.justpass.app.data.repository.ChessRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

data class AdminRolesUiState(
    val isAdmin: Boolean = false,
    val myPlayerId: String = "",
    val admins: List<AdminRolesRepository.AdminEntry> = emptyList(),
    val pendingRollInput: String = "",
    val pendingNameInput: String = "",
    val errorMessage: String? = null,
    val isWorking: Boolean = false
)

class AdminRolesViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AdminRolesRepository()
    private val chessRepo = ChessRepository()
    private val securePrefs = SecurePreferences.getInstance(application)

    private val _uiState = MutableStateFlow(AdminRolesUiState())
    val uiState: StateFlow<AdminRolesUiState> = _uiState.asStateFlow()

    init {
        val roll = securePrefs.rollNumber.orEmpty()
        val pid = if (roll.isNotBlank()) chessRepo.getPlayerId(roll) else ""
        _uiState.value = _uiState.value.copy(
            myPlayerId = pid,
            isAdmin = TournamentAdmins.isAdmin(pid)
        )
        refreshList()
    }

    fun setRoll(v: String) {
        _uiState.value = _uiState.value.copy(pendingRollInput = v.take(20).trim())
    }

    fun setName(v: String) {
        _uiState.value = _uiState.value.copy(pendingNameInput = v.take(60))
    }

    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }

    fun refreshList() {
        viewModelScope.launch {
            val list = repo.listAdmins()
            _uiState.value = _uiState.value.copy(admins = list)
        }
    }

    /** Convert a roll number to its player id and add to admin_roles. */
    fun addAdmin() {
        val s = _uiState.value
        if (s.pendingRollInput.isBlank()) {
            _uiState.value = s.copy(errorMessage = "Roll number required")
            return
        }
        val targetPid = "p_${abs(s.pendingRollInput.hashCode()).toString(16)}"
        if (targetPid == s.myPlayerId) {
            _uiState.value = s.copy(errorMessage = "You're already an admin")
            return
        }
        if (s.admins.any { it.playerId == targetPid }) {
            _uiState.value = s.copy(errorMessage = "${s.pendingRollInput} is already an admin")
            return
        }
        _uiState.value = s.copy(isWorking = true, errorMessage = null)
        viewModelScope.launch {
            val ok = repo.addAdmin(targetPid, s.pendingNameInput.ifBlank { s.pendingRollInput })
            _uiState.value = if (ok) {
                _uiState.value.copy(
                    isWorking = false,
                    pendingRollInput = "",
                    pendingNameInput = ""
                )
            } else {
                _uiState.value.copy(isWorking = false, errorMessage = "Failed to add admin (rule denied?)")
            }
            refreshList()
        }
    }

    fun removeAdmin(playerId: String) {
        if (playerId in TournamentAdmins.HARDCODED_PLAYER_IDS) {
            _uiState.value = _uiState.value.copy(errorMessage = "Cannot remove a bootstrap admin (hardcoded in source)")
            return
        }
        if (playerId == _uiState.value.myPlayerId) {
            _uiState.value = _uiState.value.copy(errorMessage = "Cannot remove yourself")
            return
        }
        _uiState.value = _uiState.value.copy(isWorking = true)
        viewModelScope.launch {
            val ok = repo.removeAdmin(playerId)
            _uiState.value = _uiState.value.copy(
                isWorking = false,
                errorMessage = if (ok) null else "Failed to remove"
            )
            refreshList()
        }
    }
}
