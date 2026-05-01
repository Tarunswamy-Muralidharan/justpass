package com.justpass.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.data.model.AttendanceData
import com.justpass.app.data.model.CalendarEventType
import com.justpass.app.data.model.CourseMarks
import com.justpass.app.data.model.DayTimetable
import com.justpass.app.data.model.GradeEntry
import com.justpass.app.data.model.RegistrationResponse
import com.justpass.app.data.model.TargetCgpaResult
import com.justpass.app.data.model.TimetableResponse
import com.justpass.app.data.model.CaComponentData
import com.justpass.app.data.model.calculateTargetCgpaFromLocal
import com.justpass.app.data.model.detectDepartment
import com.justpass.app.data.model.extractRegisteredCourseCodes
import com.justpass.app.data.model.getCurriculum
import com.justpass.app.data.model.getRegulationForBatch
import com.justpass.app.data.model.LetterGrade
import com.justpass.app.data.model.toDayTimetables
import com.google.gson.reflect.TypeToken
import com.justpass.app.data.repository.AttendanceRepository
import com.justpass.app.data.repository.Result
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

data class Announcement(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val active: Boolean = false
)

data class DashboardUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val attendanceData: AttendanceData = AttendanceData.empty(),
    val rollNumber: String = "",
    val errorMessage: String? = null,
    // Per-day non-honours session counts (Mon=0 .. Sat=5)
    val sessionsPerDay: List<Int> = listOf(6, 6, 6, 6, 6, 6),
    // Holiday dates with names from calendar
    val holidays: Map<LocalDate, String> = emptyMap(),
    // Target CGPA calculation
    val targetCgpaResult: TargetCgpaResult? = null,
    // CGPA from GPA calculator (null = not calculated yet)
    val calculatorCgpa: Double? = null,
    val hasGpaData: Boolean = false,
    // Remote announcement from Firestore
    val announcement: Announcement? = null
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

    private val localPrefs = application.getSharedPreferences("laudea_prefs", android.content.Context.MODE_PRIVATE)

    init {
        loadInitialData()
        startBackgroundRefresh()
        loadHolidayDates()
        loadCalculatorCgpa()
        loadTargetCgpa()
        fetchAnnouncement()
        // Fire prefetch immediately alongside attendance refresh — don't wait
        viewModelScope.launch { try { repository.prefetchForAI() } catch (_: Exception) {} }
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
                    // Load target CGPA now — token is warm, CA marks fetch will be fast
                    loadTargetCgpa()
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

    /** Update target CGPA and recalculate */
    /**
     * Read CGPA from the GPA calculator's saved grades.
     * Uses the same SharedPreferences + curriculum data as CgpaViewModel.
     */
    fun loadCalculatorCgpa() {
        try {
            val json = localPrefs.getString("cgpa_grades", null)
            if (json.isNullOrEmpty()) {
                _uiState.value = _uiState.value.copy(calculatorCgpa = null, hasGpaData = false)
                return
            }
            val root = org.json.JSONObject(json)
            if (root.length() == 0) {
                _uiState.value = _uiState.value.copy(calculatorCgpa = null, hasGpaData = false)
                return
            }

            // Get department + batch year to look up curriculum credits
            val deptName = securePrefs.cachedDepartment ?: securePrefs.programmeName
            val dept = detectDepartment(deptName)
            val batchYear = securePrefs.batchYear.let { if (it > 0) it else 2023 }
            val reg = getRegulationForBatch(batchYear)
            val curriculum = if (dept != null) getCurriculum(dept, reg) else null

            var totalWeighted = 0.0
            var totalCredits = 0.0

            for (semKey in root.keys()) {
                val sem = semKey.toIntOrNull() ?: continue
                val semObj = root.getJSONObject(semKey)
                val semSubjects = curriculum?.get(sem) ?: continue

                for (idxKey in semObj.keys()) {
                    val idx = idxKey.toIntOrNull() ?: continue
                    if (idx >= semSubjects.size) continue
                    val gradeLabel = semObj.getString(idxKey)
                    val grade = LetterGrade.entries.find { it.label == gradeLabel } ?: continue
                    val credits = semSubjects[idx].credits
                    totalWeighted += credits * grade.gradePoint
                    totalCredits += credits
                }
            }

            if (totalCredits > 0) {
                val cgpa = totalWeighted.toDouble() / totalCredits
                _uiState.value = _uiState.value.copy(calculatorCgpa = cgpa, hasGpaData = true)
            } else {
                _uiState.value = _uiState.value.copy(calculatorCgpa = null, hasGpaData = false)
            }
        } catch (e: Exception) {
            android.util.Log.e("DashVM", "loadCalculatorCgpa error", e)
            _uiState.value = _uiState.value.copy(calculatorCgpa = null, hasGpaData = false)
        }
    }

    /** Fetch announcement from Firestore announcements/current */
    private fun fetchAnnouncement() {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("announcements").document("current")
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && doc.getBoolean("active") == true) {
                    val id = doc.getString("id") ?: doc.id
                    // Don't show if user already dismissed this announcement
                    if (id == securePrefs.dismissedAnnouncementId) return@addOnSuccessListener
                    _uiState.value = _uiState.value.copy(
                        announcement = Announcement(
                            id = id,
                            title = doc.getString("title") ?: "Announcement",
                            message = doc.getString("message") ?: "",
                            active = true
                        )
                    )
                }
            }
            .addOnFailureListener { /* silently ignore — don't block app */ }
    }

    fun dismissAnnouncement() {
        val announcement = _uiState.value.announcement ?: return
        securePrefs.dismissedAnnouncementId = announcement.id
        _uiState.value = _uiState.value.copy(announcement = null)
    }

    fun updateTargetCgpa(target: Float) {
        securePrefs.targetCgpa = target
        loadTargetCgpa()
    }

    /** Load target CGPA using local GPA calculator data + server CA marks */
    fun loadTargetCgpa() {
        val target = securePrefs.targetCgpa.toDouble()
        if (target <= 0) {
            _uiState.value = _uiState.value.copy(targetCgpaResult = null)
            return
        }

        viewModelScope.launch {
            try {
                val currentSem = securePrefs.cachedCurrentSem
                if (currentSem <= 0) return@launch

                // ── Previous semesters: read from GPA calculator (local, not server) ──
                val deptName = securePrefs.cachedDepartment ?: securePrefs.programmeName
                val dept = detectDepartment(deptName)
                val batchYear = securePrefs.batchYear.let { if (it > 0) it else 2023 }
                val reg = getRegulationForBatch(batchYear)
                val curriculum = if (dept != null) getCurriculum(dept, reg) else null

                var previousCredits = 0
                var previousWeightedSum = 0
                var filledSemCount = 0
                val currentCgpa: Double

                val gradesJson = localPrefs.getString("cgpa_grades", null)
                if (gradesJson.isNullOrEmpty() || curriculum == null) {
                    // No GPA calculator data — can't calculate
                    _uiState.value = _uiState.value.copy(targetCgpaResult = com.justpass.app.data.model.TargetCgpaResult(
                        targetCgpa = target, currentCgpa = 0.0,
                        previousCredits = 0, previousWeightedSum = 0,
                        currentSemCredits = 0, requiredSgpa = 0.0,
                        subjects = emptyList(), isAchievable = false,
                        message = "Update your grades in GPA Calculator to use target CGPA"
                    ))
                    return@launch
                }

                val root = org.json.JSONObject(gradesJson)
                for (semKey in root.keys()) {
                    val sem = semKey.toIntOrNull() ?: continue
                    if (sem >= currentSem) continue // only previous semesters
                    val semObj = root.getJSONObject(semKey)
                    val semSubjectsLocal = curriculum[sem] ?: continue
                    var semHasGrades = false

                    for (idxKey in semObj.keys()) {
                        val idx = idxKey.toIntOrNull() ?: continue
                        if (idx >= semSubjectsLocal.size) continue
                        val gradeLabel = semObj.getString(idxKey)
                        val grade = LetterGrade.entries.find { it.label == gradeLabel } ?: continue
                        val credits = semSubjectsLocal[idx].credits.toInt()
                        if (credits > 0 && grade.gradePoint > 0) {
                            previousCredits += credits
                            previousWeightedSum += credits * grade.gradePoint
                            semHasGrades = true
                        }
                    }
                    if (semHasGrades) filledSemCount++
                }

                // Use the full calculator CGPA for display (matches the tile)
                currentCgpa = _uiState.value.calculatorCgpa
                    ?: if (previousCredits > 0) previousWeightedSum.toDouble() / previousCredits else 0.0

                // Don't block — proceed even with 0 semesters filled
                // The calculation will use whatever data is available

                // ── Current semester CA marks: use cache if available, otherwise fetch ──
                val caMarks: List<CourseMarks> = repository.cachedCourseMarks ?: run {
                    val caResult = repository.fetchCAMarks()
                    if (caResult is Result.Success) caResult.data else emptyList()
                }
                if (caMarks.isEmpty()) return@launch

                // Build CA marks map + component breakdown
                val caMap = mutableMapOf<String, Pair<Double, Double>>()
                val caCompMap = mutableMapOf<String, CaComponentData>()
                for (course in caMarks) {
                    val components = course.testDetails.components
                    var ca1Scored: Double? = null
                    var ca1Max = 20.0
                    var ca2Scored: Double? = null
                    var ca2Max = 20.0
                    var ca2Name = "IAT-2"
                    var testMax = 65.0
                    var testScaled = 60.0
                    var ca2TestMax = 65.0
                    var ca2TestScaled = 60.0

                    if (components.isNotEmpty()) {
                        val c1 = components[0]
                        ca1Scored = c1.marks?.scaled?.getSecuredAsDouble()
                            ?: c1.marks?.actual?.getSecuredAsDouble()
                        ca1Max = c1.marks?.scaled?.getMaxAsDouble()
                            ?: c1.marks?.actual?.getMaxAsDouble() ?: 20.0
                        val c1Test = c1.subComponents?.firstOrNull { it.name.contains("TEST", true) }
                        if (c1Test != null) {
                            testMax = c1Test.marks?.actual?.getMaxAsDouble() ?: 65.0
                            testScaled = c1Test.marks?.scaled?.getMaxAsDouble() ?: 60.0
                        }
                    }
                    if (components.size >= 2) {
                        val c2 = components[1]
                        ca2Name = c2.name
                        ca2Scored = c2.marks?.scaled?.getSecuredAsDouble()
                            ?: c2.marks?.actual?.getSecuredAsDouble()
                        ca2Max = c2.marks?.scaled?.getMaxAsDouble()
                            ?: c2.marks?.actual?.getMaxAsDouble() ?: 20.0
                        val c2Test = c2.subComponents?.firstOrNull {
                            it.name.contains("TEST", true) || it.name.contains("MODEL", true)
                        }
                        if (c2Test != null) {
                            ca2TestMax = c2Test.marks?.actual?.getMaxAsDouble() ?: 65.0
                            ca2TestScaled = c2Test.marks?.scaled?.getMaxAsDouble() ?: 60.0
                        }
                    }

                    caCompMap[course.courseCode] = CaComponentData(
                        ca1Scored = ca1Scored, ca1Max = ca1Max,
                        ca2Scored = ca2Scored, ca2Max = ca2Max,
                        ca2Name = ca2Name,
                        testMax = testMax, testScaled = testScaled,
                        ca2TestMax = ca2TestMax, ca2TestScaled = ca2TestScaled
                    )

                    val totalScored = course.testDetails.total.scaled.getSecuredAsDouble()
                    val totalMax = course.testDetails.total.scaled.getMaxAsDouble()
                    if (totalScored != null && totalMax > 0) {
                        caMap[course.courseCode] = Pair(totalScored, totalMax)
                    } else {
                        val sumScored = (ca1Scored ?: 0.0) + (ca2Scored ?: 0.0)
                        val sumMax = ca1Max + ca2Max
                        if (sumMax > 0) caMap[course.courseCode] = Pair(sumScored, sumMax)
                    }
                }

                // Build subjects map with credits from curriculum
                val subjectsMap = mutableMapOf<String, Pair<String, Int>>()
                val semSubjects = curriculum[currentSem]
                val auditKeywords = listOf("STATE, NATION", "NATION BUILDING", "CONSTITUTION", "INDIAN POLITY")

                for (course in caMarks) {
                    val isAudit = auditKeywords.any { course.courseTitle.uppercase().contains(it) }
                    var credits = 3
                    if (isAudit) {
                        credits = 0
                    } else if (curriculum != null) {
                        val match = semSubjects?.find { it.code == course.courseCode }
                            ?: curriculum.values.flatten().find { it.code == course.courseCode }
                        if (match != null) credits = match.credits.toInt()
                    }
                    subjectsMap[course.courseCode] = Pair(course.courseTitle, credits)
                }

                val gradedSubjects = subjectsMap.filter { it.value.second > 0 }
                if (gradedSubjects.isEmpty()) return@launch

                var result = calculateTargetCgpaFromLocal(
                    targetCgpa = target,
                    currentCgpa = currentCgpa,
                    previousCredits = previousCredits,
                    previousWeightedSum = previousWeightedSum,
                    currentCAMarks = caMap,
                    currentSemSubjects = gradedSubjects,
                    caComponents = caCompMap
                )

                // Add accuracy note if not all previous semesters filled
                val expectedPrevSems = currentSem - 1
                if (filledSemCount < expectedPrevSems) {
                    val note = " (Update all $expectedPrevSems sems in GPA Calculator for more accuracy)"
                    result = result.copy(message = result.message + note)
                }

                // Add audit/0-credit courses as pass-only entries
                val auditSubjects = subjectsMap.filter { it.value.second == 0 }
                if (auditSubjects.isNotEmpty()) {
                    val auditResults = auditSubjects.map { (code, titleCredits) ->
                        com.justpass.app.data.model.TargetSubjectResult(
                            courseCode = code, courseTitle = titleCredits.first, credits = 0,
                            caMarksScored = 0.0, caMarksMax = 0.0,
                            requiredGradePoint = 5, requiredGrade = "Pass",
                            requiredTotalMarks = 50, requiredEseMarks = 45,
                            isPossible = true, isAlreadySecured = false,
                            ca2Needed = null, ca2Max = 65, ca2Name = "",
                            hasCa2Pending = false, hasCa1Pending = false,
                            ca1Needed = null, ca1Max = 65
                        )
                    }
                    result = result.copy(subjects = result.subjects + auditResults)
                }

                _uiState.value = _uiState.value.copy(targetCgpaResult = result)
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Target CGPA calc failed", e)
            }
        }
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
