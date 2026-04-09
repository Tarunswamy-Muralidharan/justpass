package com.example.attendancewidgetlaudea.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import com.example.attendancewidgetlaudea.data.model.AttendanceData
import com.example.attendancewidgetlaudea.data.model.CalendarEventType
import com.example.attendancewidgetlaudea.data.model.DayTimetable
import com.example.attendancewidgetlaudea.data.model.RegistrationResponse
import com.example.attendancewidgetlaudea.data.model.TimetableResponse
import com.example.attendancewidgetlaudea.data.model.extractRegisteredCourseCodes
import com.example.attendancewidgetlaudea.data.model.toDayTimetables
import com.example.attendancewidgetlaudea.data.repository.AttendanceRepository
import com.example.attendancewidgetlaudea.data.repository.Result
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DashboardUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val attendanceData: AttendanceData = AttendanceData.empty(),
    val rollNumber: String = "",
    val errorMessage: String? = null,
    // Per-day non-honours session counts (Mon=0 .. Sat=5)
    val sessionsPerDay: List<Int> = listOf(6, 6, 6, 6, 6, 6),
    // Holiday dates with names from calendar
    val holidays: Map<LocalDate, String> = emptyMap()
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AttendanceRepository.getInstance(application)
    private val securePrefs = SecurePreferences.getInstance(application)
    private val gson = Gson()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    companion object {
        private const val CALENDAR_ID = "c_f65646ec47f509e6a093824790c28766188222d525707dfb817f80ac21e9e24c%40group.calendar.google.com"
        private const val API_KEY = "AIzaSyBNlYH01_9Hc5S1J9vuFmu2nUqBZJNAXxs"
    }

    init {
        loadInitialData()
        startBackgroundRefresh()
        loadHolidayDates()
    }

    /**
     * Silently refresh attendance every 8 minutes while the app is open.
     * Keeps the token alive so manual refresh is always instant.
     */
    private fun startBackgroundRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(8 * 60 * 1000L) // 8 minutes
                android.util.Log.d("DashboardVM", "Background token keep-alive refresh")
                repository.refreshAttendance() // Silent — don't update UI loading state
                    .let { result ->
                        if (result is Result.Success) {
                            _uiState.value = _uiState.value.copy(
                                attendanceData = result.data,
                                rollNumber = repository.getRollNumber() ?: ""
                            )
                        }
                    }
            }
        }
    }

    private fun loadInitialData() {
        _uiState.value = _uiState.value.copy(
            rollNumber = repository.getRollNumber() ?: "",
            attendanceData = repository.getCachedAttendance()
        )
        // Load timetable session counts from cache immediately (quick, no network)
        loadCachedSessionCounts()
        refreshAttendance()
    }

    /** Quick load from cached timetable + cached attendance — no network needed.
     *  Uses cached subject attendance to identify registered courses and exclude honours.
     *  Gets overwritten by loadTimetableSessionCounts() after refresh succeeds. */
    private fun loadCachedSessionCounts() {
        try {
            val cachedJson = securePrefs.cachedTimetableJson ?: return
            val response = gson.fromJson(cachedJson, TimetableResponse::class.java)
            val days = response.toDayTimetables()

            // Build registered course names from cached attendance data
            val registeredNames = mutableSetOf<String>()
            securePrefs.cachedSubjectAttendanceJson?.split(";")?.forEach { entry ->
                val name = entry.substringBefore(":").trim().uppercase()
                if (name.isNotBlank()) registeredNames.add(name)
            }
            securePrefs.cachedCAMarksJson?.split(";")?.forEach { entry ->
                val name = entry.substringBefore(":").trim().uppercase()
                if (name.isNotBlank()) registeredNames.add(name)
            }

            val excludedCodes = setOf("LIB", "MM")
            // Map timetable course titles to codes, then find unregistered (honours)
            val allSessions = days.flatMap { it.sessions }.filter { it.courseCode.isNotBlank() }
            val registeredCodes = mutableSetOf<String>()
            for (session in allSessions) {
                val code = session.courseCode.trim().uppercase()
                val title = session.courseTitle.trim().uppercase()
                // Match if any cached subject name contains the title or vice versa
                if (registeredNames.any { it.contains(title) || title.contains(it) }) {
                    registeredCodes.add(code)
                }
            }

            val allTimetableCodes = allSessions.map { it.courseCode.trim().uppercase() }.toSet()
            // Honours = courses in timetable but NOT matched to attendance/marks
            val honoursCodes = if (registeredNames.isNotEmpty()) {
                (allTimetableCodes - registeredCodes) - excludedCodes
            } else emptySet()

            val counts = (0..5).map { dayIndex ->
                val day = days.getOrNull(dayIndex)
                day?.sessions?.count {
                    it.courseCode.isNotBlank() && it.courseCode.trim().uppercase() !in honoursCodes
                } ?: 6
            }
            if (counts.any { it != 6 }) {
                _uiState.value = _uiState.value.copy(sessionsPerDay = counts)
            }
        } catch (_: Exception) {}
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
                    // Load timetable session counts after token is ready
                    if (_uiState.value.sessionsPerDay == listOf(6, 6, 6, 6, 6, 6)) {
                        loadTimetableSessionCounts()
                    }
                    // Reload holidays if not yet loaded (retry on each refresh)
                    if (_uiState.value.holidays.isEmpty()) {
                        loadHolidayDates()
                    }
                    // Prefetch all tile data for AI advisor (background, silent)
                    launch { repository.prefetchForAI() }
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

    /**
     * Load timetable from cache, fetch registrations to exclude honours,
     * then compute non-honours session counts per day (Mon-Sat).
     */
    private fun loadTimetableSessionCounts() {
        viewModelScope.launch {
            try {
                val cachedJson = securePrefs.cachedTimetableJson ?: return@launch
                val response = gson.fromJson(cachedJson, TimetableResponse::class.java)
                val days = response.toDayTimetables()

                // Fetch registered courses to identify honours
                var registeredCodes = emptySet<String>()
                var honoursCodes = emptySet<String>()
                val regResult = repository.fetchRegistrations()
                if (regResult is Result.Success) {
                    try {
                        val regResponse = gson.fromJson(regResult.data, RegistrationResponse::class.java)
                        registeredCodes = regResponse.extractRegisteredCourseCodes()
                    } catch (_: Exception) {}
                }
                // Also use attendance data to catch elective courses
                try {
                    val presentResult = repository.fetchPresentDays()
                    val absentResult = repository.fetchAbsentDays()
                    val attendanceCodes = mutableSetOf<String>()
                    if (presentResult is Result.Success) {
                        presentResult.data.flatMap { it.sessions }.forEach { s ->
                            if (s.courseCode.isNotBlank()) attendanceCodes.add(s.courseCode.trim().uppercase())
                        }
                    }
                    if (absentResult is Result.Success) {
                        absentResult.data.flatMap { it.sessions }.forEach { s ->
                            if (s.courseCode.isNotBlank()) attendanceCodes.add(s.courseCode.trim().uppercase())
                        }
                    }
                    registeredCodes = registeredCodes + attendanceCodes
                } catch (_: Exception) {}
                val excludedCodes = setOf("LIB", "MM")
                val allTimetableCodes = days.flatMap { it.sessions }
                    .map { it.courseCode.trim().uppercase() }
                    .filter { it.isNotBlank() }.toSet()
                honoursCodes = (allTimetableCodes - registeredCodes) - excludedCodes

                // Count non-honours sessions per day
                val counts = (0..5).map { dayIndex ->
                    val day = days.getOrNull(dayIndex)
                    day?.sessions?.count { it.courseCode.isNotBlank() && it.courseCode.trim().uppercase() !in honoursCodes } ?: 6
                }
                _uiState.value = _uiState.value.copy(sessionsPerDay = counts)
            } catch (_: Exception) {}
        }
    }

    /**
     * Fetch holidays from Google Calendar for leave calculation.
     */
    private fun loadHolidayDates() {
        viewModelScope.launch {
            try {
                val holidays: Map<LocalDate, String> = withContext(Dispatchers.IO) {
                    val now = LocalDate.now()
                    val timeMin = now.format(DateTimeFormatter.ISO_LOCAL_DATE) + "T00:00:00Z"
                    val timeMax = now.plusMonths(3).format(DateTimeFormatter.ISO_LOCAL_DATE) + "T00:00:00Z"
                    val url = "https://www.googleapis.com/calendar/v3/calendars/$CALENDAR_ID/events" +
                            "?key=$API_KEY&timeMin=$timeMin&timeMax=$timeMax" +
                            "&singleEvents=true&orderBy=startTime&maxResults=200"
                    val response = URL(url).readText()
                    val json = gson.fromJson(response, JsonObject::class.java)
                    val items = json.getAsJsonArray("items") ?: return@withContext emptyMap<LocalDate, String>()

                    items.mapNotNull { item ->
                        val obj = item.asJsonObject
                        val summary = obj.get("summary")?.asString ?: return@mapNotNull null
                        if (CalendarEventType.fromSummary(summary) != CalendarEventType.HOLIDAY) return@mapNotNull null
                        val start = obj.getAsJsonObject("start")
                        val dateStr = start?.get("date")?.asString ?: start?.get("dateTime")?.asString?.substring(0, 10) ?: return@mapNotNull null
                        try {
                            val date = LocalDate.parse(dateStr)
                            val name = summary.removePrefix("Holiday ").removePrefix("Holiday").trim().ifEmpty { summary }
                            date to name
                        } catch (_: Exception) { null }
                    }.toMap()
                }
                android.util.Log.d("DashboardVM", "Loaded ${holidays.size} holidays: ${holidays.keys}")
                _uiState.value = _uiState.value.copy(holidays = holidays)
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Failed to load holidays", e)
            }
        }
    }

    /**
     * Calculate total sessions missed for leave starting from [startDate] for [numDays] working days.
     * Skips Sundays, holidays from calendar, and uses timetable session counts per weekday.
     */
    fun calculateLeaveHours(startDate: LocalDate, numDays: Int, holidays: Map<LocalDate, String> = _uiState.value.holidays): Int {
        if (numDays <= 0) return 0
        val state = _uiState.value
        var date = startDate
        var workingDaysCounted = 0
        var totalSessions = 0

        while (workingDaysCounted < numDays) {
            val dow = date.dayOfWeek
            val isHoliday = date in holidays
            val isSunday = dow == DayOfWeek.SUNDAY

            if (!isSunday && !isHoliday) {
                val dayIndex = when (dow) {
                    DayOfWeek.MONDAY -> 0; DayOfWeek.TUESDAY -> 1; DayOfWeek.WEDNESDAY -> 2
                    DayOfWeek.THURSDAY -> 3; DayOfWeek.FRIDAY -> 4; DayOfWeek.SATURDAY -> 5
                    else -> 0
                }
                totalSessions += state.sessionsPerDay.getOrElse(dayIndex) { 6 }
                workingDaysCounted++
            }
            date = date.plusDays(1)
        }
        return totalSessions
    }

    /** Get hours for a single date based on timetable */
    fun getHoursForDate(date: LocalDate): Int {
        val state = _uiState.value
        val dow = date.dayOfWeek
        if (dow == DayOfWeek.SUNDAY) return 0
        if (date in state.holidays) return 0
        val dayIndex = when (dow) {
            DayOfWeek.MONDAY -> 0; DayOfWeek.TUESDAY -> 1; DayOfWeek.WEDNESDAY -> 2
            DayOfWeek.THURSDAY -> 3; DayOfWeek.FRIDAY -> 4; DayOfWeek.SATURDAY -> 5
            else -> 0
        }
        return state.sessionsPerDay.getOrElse(dayIndex) { 6 }
    }

    /** Calculate total hours for a set of individually selected dates */
    fun calculateHoursForDates(dates: Set<LocalDate>): Int {
        val state = _uiState.value
        var totalSessions = 0
        for (date in dates) {
            val dow = date.dayOfWeek
            if (dow == DayOfWeek.SUNDAY) continue
            if (date in state.holidays) continue
            val dayIndex = when (dow) {
                DayOfWeek.MONDAY -> 0; DayOfWeek.TUESDAY -> 1; DayOfWeek.WEDNESDAY -> 2
                DayOfWeek.THURSDAY -> 3; DayOfWeek.FRIDAY -> 4; DayOfWeek.SATURDAY -> 5
                else -> 0
            }
            totalSessions += state.sessionsPerDay.getOrElse(dayIndex) { 6 }
        }
        return totalSessions
    }

    fun getHolidaysInLeaveRange(startDate: LocalDate, numDays: Int, holidays: Map<LocalDate, String> = _uiState.value.holidays): List<Pair<LocalDate, String>> {
        if (numDays <= 0) return emptyList()
        val result = mutableListOf<Pair<LocalDate, String>>()
        var date = startDate
        var workingDaysCounted = 0

        while (workingDaysCounted < numDays) {
            val dow = date.dayOfWeek
            val holidayName = holidays[date]
            val isSunday = dow == DayOfWeek.SUNDAY

            if (holidayName != null) {
                result.add(date to holidayName)
            }
            if (!isSunday && holidayName == null) {
                workingDaysCounted++
            }
            date = date.plusDays(1)
        }
        return result
    }

    fun getWorkingDaysInLeaveRange(startDate: LocalDate, numDays: Int, holidays: Map<LocalDate, String> = _uiState.value.holidays): List<LocalDate> {
        if (numDays <= 0) return emptyList()
        val result = mutableListOf<LocalDate>()
        var date = startDate
        var workingDaysCounted = 0

        while (workingDaysCounted < numDays) {
            val dow = date.dayOfWeek
            val isHoliday = date in holidays
            val isSunday = dow == DayOfWeek.SUNDAY

            if (!isSunday && !isHoliday) {
                result.add(date)
                workingDaysCounted++
            }
            date = date.plusDays(1)
        }
        return result
    }
}
