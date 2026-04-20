package com.justpass.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.justpass.app.data.model.Exemption
import com.justpass.app.data.model.SubjectAttendance
import com.justpass.app.data.model.calculateSubjectAttendance
import com.justpass.app.data.repository.AttendanceRepository
import com.justpass.app.data.repository.Result
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        // Load from cache instantly, then refresh in background
        loadFromCache()
        fetchSubjectAttendance()
    }

    private fun loadFromCache() {
        val present = repository.cachedPresentDays
        val absent = repository.cachedAbsentDays
        if (present != null && absent != null) {
            val subjects = calculateWithoutExemptions(present, absent)
            if (subjects.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(subjects = subjects)
            }
        }
    }

    fun fetchSubjectAttendance() {
        viewModelScope.launch {
            // Only show loading spinner if we have no cached data
            if (_uiState.value.subjects.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            }

            // Parallel fetch — all 4 APIs fire simultaneously (~20s vs ~60s sequential)
            val presentResult: Result<List<com.justpass.app.data.model.AbsentDay>>
            val absentResult: Result<List<com.justpass.app.data.model.AbsentDay>>
            val exemptionResult: Result<List<Exemption>>
            val timetableResult: Result<com.justpass.app.data.model.TimetableResponse>
            coroutineScope {
                val p = async { repository.fetchPresentDays() }
                val a = async { repository.fetchAbsentDays() }
                val e = async { repository.fetchExemptions() }
                val t = async { repository.fetchTimetable() }
                presentResult = p.await()
                absentResult = a.await()
                exemptionResult = e.await()
                timetableResult = t.await()
            }

            if (presentResult is Result.Success && absentResult is Result.Success) {
                // Only include exemptions if the attendance API actually counts them
                val cachedAttendance = repository.getCachedAttendance()
                val hasExemptions = cachedAttendance.exemptionCount > 0
                val exemptions: List<Exemption> = if (hasExemptions && exemptionResult is Result.Success) exemptionResult.data else emptyList()
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
                _uiState.value = _uiState.value.copy(isLoading = false, subjects = subjects, errorMessage = null)
            } else {
                // Only show error if we have no cached data
                if (_uiState.value.subjects.isEmpty()) {
                    val error = when {
                        presentResult is Result.Error -> presentResult.message
                        absentResult is Result.Error -> absentResult.message
                        else -> "Could not load subject attendance"
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = error)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    /** Simple fallback when timetable isn't available — just present + absent */
    private fun calculateWithoutExemptions(
        presentDays: List<com.justpass.app.data.model.AbsentDay>,
        absentDays: List<com.justpass.app.data.model.AbsentDay>
    ): List<SubjectAttendance> {
        return calculateSubjectAttendance(
            presentDays = presentDays,
            absentDays = absentDays,
            exemptions = emptyList(),
            timetableResponse = com.justpass.app.data.model.TimetableResponse(
                noOfSessions = 0, noOfDays = 0, timeTable = emptyList(),
                academicYear = "", academicSemester = "", sessionTimings = emptyMap()
            )
        )
    }
}
