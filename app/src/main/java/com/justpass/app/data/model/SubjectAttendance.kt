package com.justpass.app.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Per-subject attendance with present, absent, and exemption counts.
 */
data class SubjectAttendance(
    val courseCode: String,
    val courseTitle: String,
    val presentCount: Int,
    val absentCount: Int,
    val exemptionCount: Int,
    val totalCount: Int,              // present + absent + exemption
    val attendancePercentage: Double  // (present + exemption) / total * 100
)

/**
 * Calculate accurate subject-wise attendance from present days, absent days,
 * exemptions, and timetable data.
 *
 * - Present/absent days give per-session course info directly.
 * - Exemptions need to be mapped to subjects via the timetable:
 *   "Day" exemptions → all subjects on those dates
 *   "Session" exemptions → match session times to timetable for those dates
 */
fun calculateSubjectAttendance(
    presentDays: List<AbsentDay>,
    absentDays: List<AbsentDay>,
    exemptions: List<Exemption>,
    timetableResponse: TimetableResponse
): List<SubjectAttendance> {
    val presentBySubject = mutableMapOf<String, Int>()
    val absentBySubject = mutableMapOf<String, Int>()
    val exemptionBySubject = mutableMapOf<String, Int>()
    val courseNames = mutableMapOf<String, String>()

    // Count present per subject
    for (day in presentDays) {
        for (session in day.sessions) {
            if (session.courseCode.isBlank()) continue
            presentBySubject[session.courseCode] = (presentBySubject[session.courseCode] ?: 0) + 1
            courseNames[session.courseCode] = session.courseTitle
        }
    }

    // Count absent per subject
    for (day in absentDays) {
        for (session in day.sessions) {
            if (session.courseCode.isBlank()) continue
            absentBySubject[session.courseCode] = (absentBySubject[session.courseCode] ?: 0) + 1
            if (!courseNames.containsKey(session.courseCode)) {
                courseNames[session.courseCode] = session.courseTitle
            }
        }
    }

    // Build timetable lookup: dayOfWeek (1=Mon..6=Sat) → list of (sessionKey, courseCode, timing)
    val dayTimetables = timetableResponse.toDayTimetables()
    // Map dayNumber (1=Monday) to list of SessionInfo
    val timetableByDay = dayTimetables.associate { it.dayNumber to it.sessions }

    // Count exemption hours per subject by mapping exemption dates to timetable
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // Find the earliest date from present/absent data to determine current semester boundary
    val allDates = (presentDays + absentDays).mapNotNull {
        try { isoFormat.parse(it.date) } catch (_: Exception) { null }
    }
    val semesterStart = allDates.minOrNull()

    // Only include verified exemptions from the CURRENT semester
    val verifiedExemptions = exemptions.filter { exemption ->
        if (exemption.status != "V") return@filter false
        if (semesterStart == null) return@filter true
        val exemptionEnd = try { dateFormat.parse(exemption.toDate) } catch (_: Exception) { null }
        exemptionEnd != null && !exemptionEnd.before(semesterStart)
    }

    for (exemption in verifiedExemptions) {
        val fromDate = try { dateFormat.parse(exemption.fromDate) } catch (_: Exception) { null } ?: continue
        val toDate = try { dateFormat.parse(exemption.toDate) } catch (_: Exception) { null } ?: continue

        val cal = Calendar.getInstance()
        cal.time = fromDate

        // Iterate each day in the exemption date range
        while (!cal.time.after(toDate)) {
            // Calendar.DAY_OF_WEEK: Sun=1, Mon=2, ..., Sat=7
            // Timetable dayNumber: Mon=1, ..., Sat=6
            val calDow = cal.get(Calendar.DAY_OF_WEEK)
            val timetableDayNum = when (calDow) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                else -> 0  // Sunday — no classes
            }

            val sessionsForDay = timetableByDay[timetableDayNum] ?: emptyList()

            if (exemption.category == "Day" || exemption.sessions.isNullOrEmpty()) {
                // Full day exemption — all subjects on this day get +1
                for (s in sessionsForDay) {
                    if (s.courseCode.isBlank()) continue
                    exemptionBySubject[s.courseCode] = (exemptionBySubject[s.courseCode] ?: 0) + 1
                    if (!courseNames.containsKey(s.courseCode)) courseNames[s.courseCode] = s.courseTitle
                }
            } else {
                // Session exemption — match session times from exemption to timetable
                for (exemptionTime in exemption.sessions) {
                    // exemptionTime format: "8:30 AM - 9:20 AM" or "1:10 PM - 2:00 PM"
                    // Timetable times: "08:30" - "09:20" (24h format)
                    // Try to match by finding a session whose timing overlaps
                    val matchedSession = sessionsForDay.firstOrNull { session ->
                        timesMatch(exemptionTime, session.startTime, session.endTime)
                    }
                    if (matchedSession != null && matchedSession.courseCode.isNotBlank()) {
                        exemptionBySubject[matchedSession.courseCode] =
                            (exemptionBySubject[matchedSession.courseCode] ?: 0) + 1
                        if (!courseNames.containsKey(matchedSession.courseCode))
                            courseNames[matchedSession.courseCode] = matchedSession.courseTitle
                    }
                }
            }

            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    // Build final subject attendance list
    val allCodes = (presentBySubject.keys + absentBySubject.keys + exemptionBySubject.keys).distinct()

    return allCodes.map { code ->
        val present = presentBySubject[code] ?: 0
        val absent = absentBySubject[code] ?: 0
        val exemption = exemptionBySubject[code] ?: 0
        val total = present + absent + exemption
        val percentage = if (total > 0) ((present + exemption).toDouble() / total) * 100 else 100.0

        SubjectAttendance(
            courseCode = code,
            courseTitle = courseNames[code] ?: code,
            presentCount = present,
            absentCount = absent,
            exemptionCount = exemption,
            totalCount = total,
            attendancePercentage = percentage
        )
    }.sortedBy { it.attendancePercentage }
}

/**
 * Check if an exemption time string (e.g. "8:30 AM - 9:20 AM") matches a timetable session
 * time (e.g. startTime="08:30", endTime="09:20" in 24h format).
 */
private fun timesMatch(exemptionTime: String, startTime24: String, endTime24: String): Boolean {
    // Try to extract start time from the exemption string and match against timetable
    val parts = exemptionTime.split("-").map { it.trim() }
    if (parts.size < 2) return false

    val exemptStart = parse12hTo24h(parts[0])
    val timetableStart = startTime24.trim()

    // Match by start time (most reliable)
    return exemptStart == timetableStart
}

/**
 * Convert "8:30 AM" or "1:10 PM" to "08:30" or "13:10" (24h format).
 */
private fun parse12hTo24h(time12: String): String {
    return try {
        val clean = time12.trim().uppercase()
        val isPM = clean.contains("PM")
        val isAM = clean.contains("AM")
        val timePart = clean.replace("AM", "").replace("PM", "").trim()
        val (hourStr, minStr) = timePart.split(":").map { it.trim() }
        var hour = hourStr.toInt()

        if (isPM && hour != 12) hour += 12
        if (isAM && hour == 12) hour = 0

        "%02d:%s".format(hour, minStr)
    } catch (_: Exception) {
        time12.trim()
    }
}
