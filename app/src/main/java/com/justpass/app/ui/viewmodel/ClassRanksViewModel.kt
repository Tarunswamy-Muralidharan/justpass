package com.justpass.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.justpass.app.data.model.ClassStatsResponse
import com.justpass.app.data.repository.ClassMarksRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State + actions for [com.justpass.app.ui.screens.ClassCompareScreen].
 * Resolves classKey from prefs on init, kicks an initial fetch.
 */
class ClassRanksViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ClassMarksRepository.getInstance(app)

    sealed class State {
        object Loading : State()
        data class Missing(val reason: String) : State()
        data class Ready(val classKey: String, val stats: ClassStatsResponse) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = State.Loading
            val classKey = repo.resolveClassKey()
            if (classKey == null) {
                _state.value = State.Missing(
                    "Open the dashboard and let it sync your batch / department / section first."
                )
                return@launch
            }
            val stats = repo.fetchClassStats(classKey)
            if (stats == null) {
                _state.value = State.Error("Could not load class comparison. Check your network.")
                return@launch
            }
            _state.value = State.Ready(classKey, stats)
        }
    }

    fun deleteMyData(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.deleteMyData()
            onResult(ok)
            if (ok) refresh()
        }
    }
}
