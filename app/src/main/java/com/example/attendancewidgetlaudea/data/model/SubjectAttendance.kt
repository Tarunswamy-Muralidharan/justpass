package com.example.attendancewidgetlaudea.data.model

/**
 * Per-subject attendance calculated from timetable (total periods/week) and absent days data.
 */
data class SubjectAttendance(
    val courseCode: String,
    val courseTitle: String,
    val periodsPerWeek: Int,         // From timetable
    val absentCount: Int,            // From absent days
    val estimatedTotal: Int,         // Estimated total periods based on proportion
    val presentCount: Int,           // estimatedTotal - absentCount
    val attendancePercentage: Double // (presentCount / estimatedTotal) * 100
)

/**
 * Calculate subject-wise attendance by combining timetable and absent days data.
 *
 * @param timetableResponse Raw timetable from API
 * @param absentDays List of absent days with per-session course info
 * @param totalEnteredClasses Total classes entered from attendance API (e.g., 324)
 */
fun calculateSubjectAttendance(
    timetableResponse: TimetableResponse,
    absentDays: List<AbsentDay>,
    totalEnteredClasses: Int
): List<SubjectAttendance> {
    // Step 1: Count periods per subject per week from timetable
    val periodsPerSubject = mutableMapOf<String, Int>()
    val courseNames = mutableMapOf<String, String>()

    for (entry in timetableResponse.timeTable) {
        val slot = entry.slot.firstOrNull() ?: continue
        val code = slot.courseDetails.courseCode
        if (code.isBlank()) continue
        periodsPerSubject[code] = (periodsPerSubject[code] ?: 0) + 1
        courseNames[code] = slot.courseDetails.courseTitle
    }

    val totalPeriodsPerWeek = periodsPerSubject.values.sum()
    if (totalPeriodsPerWeek == 0) return emptyList()

    // Step 2: Count absences per subject from absent days
    val absencesPerSubject = mutableMapOf<String, Int>()
    for (day in absentDays) {
        for (session in day.sessions) {
            val code = session.courseCode
            if (code.isBlank()) continue
            absencesPerSubject[code] = (absencesPerSubject[code] ?: 0) + 1
            // Also capture course name from absent data in case timetable doesn't have it
            if (!courseNames.containsKey(code)) {
                courseNames[code] = session.courseTitle
            }
        }
    }

    // Step 3: Calculate estimated total and attendance per subject
    val allCodes = (periodsPerSubject.keys + absencesPerSubject.keys).distinct()

    return allCodes.map { code ->
        val periodsWeek = periodsPerSubject[code] ?: 1
        val absent = absencesPerSubject[code] ?: 0

        // Estimate total classes for this subject based on its proportion of weekly periods
        val proportion = periodsWeek.toDouble() / totalPeriodsPerWeek
        val estimatedTotal = (totalEnteredClasses * proportion).toInt().coerceAtLeast(absent)
        val present = estimatedTotal - absent
        val percentage = if (estimatedTotal > 0) (present.toDouble() / estimatedTotal) * 100 else 100.0

        SubjectAttendance(
            courseCode = code,
            courseTitle = courseNames[code] ?: code,
            periodsPerWeek = periodsWeek,
            absentCount = absent,
            estimatedTotal = estimatedTotal,
            presentCount = present,
            attendancePercentage = percentage
        )
    }.sortedBy { it.attendancePercentage } // Show lowest attendance first
}
