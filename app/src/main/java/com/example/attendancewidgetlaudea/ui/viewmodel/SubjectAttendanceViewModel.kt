package com.example.attendancewidgetlaudea.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancewidgetlaudea.data.model.SubjectAttendance
import com.example.attendancewidgetlaudea.data.model.calculateSubjectAttendance
import com.example.attendancewidgetlaudea.data.repository.AttendanceRepository
import com.example.attendancewidgetlaudea.data.repository.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SubjectAttendanceUiState(
    val isLoading: Boolean = false,
    val subjects: List<SubjectAttendance> = emptyList(),
    val errorMessage: String? = null
)

class SubjectAttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AttendanceRepository.getInstance(application)

    private val _uiState = MutableStateFlow(SubjectAttendanceUiState())
    val uiState: StateFlow<SubjectAttendanceUiState> = _uiState.asStateFlow()

    init {
        fetchSubjectAttendance()
    }

    fun fetchSubjectAttendance() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Fetch timetable and absent days in parallel
            val timetableResult = repository.fetchTimetable()
            val absentResult = repository.fetchAbsentDays()

            // Get total entered classes from cached attendance data
            val cachedAttendance = repository.getCachedAttendance()
            val totalEntered = cachedAttendance.enteredTillDate

            if (timetableResult is Result.Success && absentResult is Result.Success && totalEntered > 0) {
                val subjects = calculateSubjectAttendance(
                    timetableResponse = timetableResult.data,
                    absentDays = absentResult.data,
                    totalEnteredClasses = totalEntered
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    subjects = subjects
                )
            } else {
                val error = when {
                    timetableResult is Result.Error -> timetableResult.message
                    absentResult is Result.Error -> absentResult.message
                    totalEntered == 0 -> "Refresh attendance first"
                    else -> "Could not load subject attendance"
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error
                )
            }
        }
    }
}
