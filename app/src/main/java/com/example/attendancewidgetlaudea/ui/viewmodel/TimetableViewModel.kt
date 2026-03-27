package com.example.attendancewidgetlaudea.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import com.example.attendancewidgetlaudea.data.model.DayTimetable
import com.example.attendancewidgetlaudea.data.model.RegistrationResponse
import com.example.attendancewidgetlaudea.data.model.TimetableResponse
import com.example.attendancewidgetlaudea.data.model.extractRegisteredCourseCodes
import com.example.attendancewidgetlaudea.data.model.toDayTimetables
import com.example.attendancewidgetlaudea.data.repository.AttendanceRepository
import com.example.attendancewidgetlaudea.data.repository.Result
import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class TimetableUiState(
    val isLoading: Boolean = false,
    val days: List<DayTimetable> = emptyList(),
    val selectedDayIndex: Int = 0,
    val todayDayIndex: Int = 0,
    val errorMessage: String? = null
)

class TimetableViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AttendanceRepository.getInstance(application)
    private val securePrefs = SecurePreferences.getInstance(application)
    private val gson = Gson()

    private val _uiState = MutableStateFlow(TimetableUiState())
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()

    // Cached registered course codes for honours detection
    private var registeredCourseCodes: Set<String> = emptySet()

    init {
        val todayIndex = getTodayDayIndex()
        _uiState.value = _uiState.value.copy(
            selectedDayIndex = todayIndex,
            todayDayIndex = todayIndex
        )
        loadCachedTimetable()
        fetchTimetable()
    }

    private fun getTodayDayIndex(): Int {
        // Calendar.MONDAY=2 ... Calendar.SATURDAY=7, SUNDAY=1
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return when (dayOfWeek) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 0 // Sunday -> show Monday
        }
    }

    private fun loadCachedTimetable() {
        val cached = securePrefs.cachedTimetableJson ?: return
        try {
            val response = gson.fromJson(cached, TimetableResponse::class.java)
            val days = response.toDayTimetables()
            // Honours marking will be applied once registrations are fetched
            _uiState.value = _uiState.value.copy(days = days)
        } catch (e: Exception) {
            android.util.Log.e("TimetableVM", "Failed to load cached timetable: ${e.message}")
        }
    }

    fun fetchTimetable() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Fetch timetable and registrations in parallel
            val timetableDeferred = async { repository.fetchTimetable() }
            val registrationDeferred = async { repository.fetchRegistrations() }

            val timetableResult = timetableDeferred.await()
            val registrationResult = registrationDeferred.await()

            // Parse registered course codes
            if (registrationResult is Result.Success) {
                try {
                    val regResponse = gson.fromJson(registrationResult.data, RegistrationResponse::class.java)
                    registeredCourseCodes = regResponse.extractRegisteredCourseCodes()
                    android.util.Log.d("TimetableVM", "Registered courses: $registeredCourseCodes")
                } catch (e: Exception) {
                    android.util.Log.e("TimetableVM", "Failed to parse registrations: ${e.message}")
                }
            }

            when (timetableResult) {
                is Result.Success -> {
                    val days = timetableResult.data.toDayTimetables()
                    val markedDays = markHonoursCourses(days)
                    // Cache the response
                    try {
                        securePrefs.cachedTimetableJson = gson.toJson(timetableResult.data)
                    } catch (e: Exception) {
                        android.util.Log.e("TimetableVM", "Failed to cache timetable: ${e.message}")
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        days = markedDays
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = timetableResult.message
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    /**
     * Mark timetable sessions as honours if their course code is NOT in the registered set.
     * If we have no registration data, nothing is marked.
     */
    private fun markHonoursCourses(days: List<DayTimetable>): List<DayTimetable> {
        if (registeredCourseCodes.isEmpty()) return days

        // Collect all unique course codes from the timetable
        val timetableCodes = days.flatMap { it.sessions }.map { it.courseCode }.filter { it.isNotBlank() }.toSet()

        // Non-academic slots to exclude from honours marking
        val excludedCodes = setOf("LIB", "MM")

        // Honours = in timetable but NOT in registration, excluding non-academic slots
        val honoursCodes = (timetableCodes - registeredCourseCodes) - excludedCodes
        if (honoursCodes.isEmpty()) return days

        android.util.Log.d("TimetableVM", "Honours courses detected: $honoursCodes")

        return days.map { day ->
            day.copy(sessions = day.sessions.map { session ->
                if (session.courseCode in honoursCodes) {
                    session.copy(isHonours = true)
                } else {
                    session
                }
            })
        }
    }

    fun selectDay(index: Int) {
        _uiState.value = _uiState.value.copy(selectedDayIndex = index)
    }
}
