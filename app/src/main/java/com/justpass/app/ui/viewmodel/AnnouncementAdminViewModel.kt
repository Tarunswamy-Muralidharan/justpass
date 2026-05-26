package com.justpass.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.justpass.app.data.repository.AnnouncementAdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnnouncementAdminUiState(
    val active: Boolean = false,
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val isPublishing: Boolean = false,
    val lastResult: String? = null,
    val isError: Boolean = false,
)

class AnnouncementAdminViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AnnouncementAdminRepository()
    private val _uiState = MutableStateFlow(AnnouncementAdminUiState())
    val uiState: StateFlow<AnnouncementAdminUiState> = _uiState.asStateFlow()

    init {
        prefillFromRemoteConfig()
    }

    /** Pre-populate the form with the current published values so the admin
     *  edits *deltas* instead of re-typing everything. Forces a fresh fetch
     *  (0L interval) so the admin sees what's live this second, not what the
     *  1-hour cache holds. */
    private fun prefillFromRemoteConfig() {
        val rc = FirebaseRemoteConfig.getInstance()
        rc.fetch(0L).addOnCompleteListener {
            rc.activate().addOnCompleteListener {
                _uiState.value = _uiState.value.copy(
                    active = rc.getBoolean("announcement_active"),
                    id = rc.getString("announcement_id"),
                    title = rc.getString("announcement_title"),
                    message = rc.getString("announcement_message"),
                )
            }
        }
    }

    fun setActive(v: Boolean) { _uiState.value = _uiState.value.copy(active = v) }
    fun setId(v: String) { _uiState.value = _uiState.value.copy(id = v.take(100)) }
    fun setTitle(v: String) { _uiState.value = _uiState.value.copy(title = v.take(200)) }
    fun setMessage(v: String) { _uiState.value = _uiState.value.copy(message = v.take(2000)) }
    fun dismissResult() { _uiState.value = _uiState.value.copy(lastResult = null, isError = false) }

    fun publish() {
        val s = _uiState.value
        if (s.active && (s.id.isBlank() || s.message.isBlank())) {
            _uiState.value = s.copy(
                lastResult = "Active = ON requires an id and a message",
                isError = true,
            )
            return
        }
        _uiState.value = s.copy(isPublishing = true, lastResult = null, isError = false)
        viewModelScope.launch {
            val result = repo.publish(
                active = s.active,
                id = s.id.trim(),
                title = s.title.trim(),
                message = s.message.trim(),
            )
            _uiState.value = when (result) {
                is AnnouncementAdminRepository.Result.Success -> _uiState.value.copy(
                    isPublishing = false,
                    lastResult = "Published. Clients see this on next fetch.",
                    isError = false,
                )
                is AnnouncementAdminRepository.Result.Error -> _uiState.value.copy(
                    isPublishing = false,
                    lastResult = result.message,
                    isError = true,
                )
            }
        }
    }
}
