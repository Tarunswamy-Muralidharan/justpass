package com.example.attendancewidgetlaudea.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancewidgetlaudea.data.model.Exemption
import com.example.attendancewidgetlaudea.data.repository.AttendanceRepository
import com.example.attendancewidgetlaudea.data.repository.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExemptionsUiState(
    val isLoading: Boolean = false,
    val exemptions: List<Exemption> = emptyList(),
    val errorMessage: String? = null
)

class ExemptionsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AttendanceRepository.getInstance(application)
    private val _uiState = MutableStateFlow(ExemptionsUiState())
    val uiState: StateFlow<ExemptionsUiState> = _uiState.asStateFlow()

    init { fetchExemptions() }

    fun fetchExemptions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = repository.fetchExemptions()) {
                is Result.Success -> _uiState.value = _uiState.value.copy(isLoading = false, exemptions = result.data)
                is Result.Error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
                is Result.Loading -> {}
            }
        }
    }
}
