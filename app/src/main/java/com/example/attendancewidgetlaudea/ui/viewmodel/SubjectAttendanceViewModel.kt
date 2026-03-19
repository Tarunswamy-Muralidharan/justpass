package com.example.attendancewidgetlaudea.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancewidgetlaudea.data.model.Exemption
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

            val presentResult = repository.fetchPresentDays()
            val absentResult = repository.fetchAbsentDays()
            val exemptionResult = repository.fetchExemptions()
            val timetableResult = repository.fetchTimetable()

            if (presentResult is Result.Success && absentResult is Result.Success) {
                // Exemptions and timetable are optional — if they fail, calculate without them
                val exemptions: List<Exemption> = if (exemptionResult is Result.Success) exemptionResult.data else emptyList()
                val timetable = if (timetableResult is Result.Success) timetableResult.data else null

                val subjects = if (timetable != null && exemptions.isNotEmpty()) {
                    calculateSubjectAttendance(
                        presentDays = presentResult.data,
                        absentDays = absentResult.data,
                        exemptions = exemptions,
                        timetableResponse = timetable
                    )
                } else {
                    // Fallback: calculate without exemptions (present + absent only)
                    calculateSubjectAttendance(
                        presentDays = presentResult.data,
                        absentDays = absentResult.data,
                        exemptions = emptyList(),
                        timetableResponse = timetable ?: return@launch run {
                            // If no timetable AND no exemptions, just use present+absent
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                subjects = calculateWithoutExemptions(presentResult.data, absentResult.data)
                            )
                        }
                    )
                }
                _uiState.value = _uiState.value.copy(isLoading = false, subjects = subjects)
            } else {
                val error = when {
                    presentResult is Result.Error -> presentResult.message
                    absentResult is Result.Error -> absentResult.message
                    else -> "Could not load subject attendance"
                }
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = error)
            }
        }
    }

    /** Simple fallback when timetable isn't available — just present + absent */
    private fun calculateWithoutExemptions(
        presentDays: List<com.example.attendancewidgetlaudea.data.model.AbsentDay>,
        absentDays: List<com.example.attendancewidgetlaudea.data.model.AbsentDay>
    ): List<SubjectAttendance> {
        return calculateSubjectAttendance(
            presentDays = presentDays,
            absentDays = absentDays,
            exemptions = emptyList(),
            timetableResponse = com.example.attendancewidgetlaudea.data.model.TimetableResponse(
                noOfSessions = 0, noOfDays = 0, timeTable = emptyList(),
                academicYear = "", academicSemester = "", sessionTimings = emptyMap()
            )
        )
    }
}
