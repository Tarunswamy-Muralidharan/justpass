package com.example.attendancewidgetlaudea.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import com.example.attendancewidgetlaudea.data.model.DayTimetable
import com.example.attendancewidgetlaudea.data.model.TimetableResponse
import com.example.attendancewidgetlaudea.data.model.toDayTimetables
import com.example.attendancewidgetlaudea.data.repository.AttendanceRepository
import com.example.attendancewidgetlaudea.data.repository.Result
import com.google.gson.Gson
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
            _uiState.value = _uiState.value.copy(days = days)
        } catch (e: Exception) {
            android.util.Log.e("TimetableVM", "Failed to load cached timetable: ${e.message}")
        }
    }

    fun fetchTimetable() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = repository.fetchTimetable()) {
                is Result.Success -> {
                    val days = result.data.toDayTimetables()
                    // Cache the response
                    try {
                        securePrefs.cachedTimetableJson = gson.toJson(result.data)
                    } catch (e: Exception) {
                        android.util.Log.e("TimetableVM", "Failed to cache timetable: ${e.message}")
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        days = days
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    fun selectDay(index: Int) {
        _uiState.value = _uiState.value.copy(selectedDayIndex = index)
    }
}
