package com.justpass.app.data.model

import com.google.gson.annotations.SerializedName

data class TimetableResponse(
    @SerializedName("noOfSessions")
    val noOfSessions: Int,

    @SerializedName("noOfDays")
    val noOfDays: Int,

    @SerializedName("timeTable")
    val timeTable: List<TimetableSlotEntry>,

    @SerializedName("academicYear")
    val academicYear: String,

    @SerializedName("academicSemester")
    val academicSemester: String,

    @SerializedName("sessionTimings")
    val sessionTimings: Map<String, String>
)

data class TimetableSlotEntry(
    @SerializedName("slot")
    val slot: List<TimetableSlot>,

    @SerializedName("dayKey")
    val dayKey: String,

    @SerializedName("sessionKey")
    val sessionKey: String
)

data class TimetableSlot(
    @SerializedName("courseDetails")
    val courseDetails: TimetableCourse,

    @SerializedName("facultyDetails")
    val facultyDetails: List<TimetableFaculty>
)

data class TimetableCourse(
    @SerializedName("courseCode")
    val courseCode: String,

    @SerializedName("courseTitle")
    val courseTitle: String
)

data class TimetableFaculty(
    @SerializedName("facultyId")
    val facultyId: String,

    @SerializedName("facultyName")
    val facultyName: String
)

/**
 * Processed timetable for UI display — organized by day with sorted sessions.
 */
data class DayTimetable(
    val dayNumber: Int,       // 1-6
    val dayName: String,      // "Monday"-"Saturday"
    val sessions: List<SessionInfo>
)

data class SessionInfo(
    val sessionNumber: Int,
    val startTime: String,    // "08:30"
    val endTime: String,      // "09:20"
    val courseCode: String,
    val courseTitle: String,
    val facultyNames: List<String>,
    val isHonours: Boolean = false
)

/**
 * Registration API response models — used to detect honours courses.
 * Honours = courses in timetable but NOT in the registration list.
 */
data class RegistrationResponse(
    @SerializedName("registrationGroups")
    val registrationGroups: List<RegistrationGroup> = emptyList(),

    @SerializedName("additionalGroups")
    val additionalGroups: List<RegistrationGroup> = emptyList()
)

data class RegistrationGroup(
    @SerializedName("courses")
    val courses: List<RegistrationCourse> = emptyList()
)

data class RegistrationCourse(
    @SerializedName("code")
    val code: String = "",

    @SerializedName("registration")
    val registration: Boolean = false,

    @SerializedName("placeholder")
    val placeholder: Boolean = false,

    @SerializedName("course")
    val course: RegistrationNestedCourse? = null,

    @SerializedName("ltpc")
    val ltpc: RegistrationLtpc? = null
)

data class RegistrationNestedCourse(
    @SerializedName("code")
    val code: String = "",

    @SerializedName("ltpc")
    val ltpc: RegistrationLtpc? = null
)

data class RegistrationLtpc(
    @SerializedName("credits")
    val credits: Double = 0.0
)

/**
 * Extract the set of registered course codes from a RegistrationResponse.
 * For placeholder courses (electives), the actual code is in the nested course object.
 */
fun RegistrationResponse.extractRegisteredCourseCodes(): Set<String> {
    val codes = mutableSetOf<String>()
    val allGroups = registrationGroups + additionalGroups
    for (group in allGroups) {
        for (course in group.courses) {
            if (!course.registration) continue
            // For placeholders, prefer the nested actual course code
            val code = if (course.placeholder && course.course != null && course.course.code.isNotBlank()) {
                course.course.code
            } else {
                course.code
            }
            // Skip placeholder codes with underscores (e.g. PE64__, OE61__) — they don't map to real codes
            if (code.isNotBlank() && !code.contains("_")) {
                codes.add(code.trim().uppercase())
            }
        }
    }
    return codes
}

/**
 * Convert raw API response into organized day-wise timetable.
 */
fun TimetableResponse.toDayTimetables(): List<DayTimetable> {
    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    return (1..noOfDays).map { dayNum ->
        val dayKey = "day$dayNum"
        val entries = timeTable.filter { it.dayKey == dayKey }
            .sortedBy { it.sessionKey.removePrefix("session").toIntOrNull() ?: 0 }

        val sessions = entries.map { entry ->
            val slot = entry.slot.firstOrNull()
            val sessionNum = entry.sessionKey.removePrefix("session").toIntOrNull() ?: 0
            val timing = sessionTimings[entry.sessionKey] ?: ""
            val parts = timing.split("-")

            SessionInfo(
                sessionNumber = sessionNum,
                startTime = parts.getOrElse(0) { "" },
                endTime = parts.getOrElse(1) { "" },
                courseCode = slot?.courseDetails?.courseCode ?: "",
                courseTitle = slot?.courseDetails?.courseTitle ?: "",
                facultyNames = slot?.facultyDetails?.map { it.facultyName } ?: emptyList()
            )
        }

        DayTimetable(
            dayNumber = dayNum,
            dayName = dayNames.getOrElse(dayNum - 1) { "Day $dayNum" },
            sessions = sessions
        )
    }
}
