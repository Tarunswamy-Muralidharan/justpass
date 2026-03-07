package com.example.attendancewidgetlaudea.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancewidgetlaudea.data.model.AttendanceData
import com.example.attendancewidgetlaudea.data.repository.AttendanceRepository
import com.example.attendancewidgetlaudea.data.repository.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val attendanceData: AttendanceData = AttendanceData.empty(),
    val rollNumber: String = "",
    val errorMessage: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AttendanceRepository.getInstance(application)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        _uiState.value = _uiState.value.copy(
            rollNumber = repository.getRollNumber() ?: "",
            attendanceData = repository.getCachedAttendance()
        )
        refreshAttendance()
    }

    fun refreshAttendance() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)

            when (val result = repository.refreshAttendance()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        attendanceData = result.data,
                        rollNumber = repository.getRollNumber() ?: ""
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        errorMessage = result.message
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
