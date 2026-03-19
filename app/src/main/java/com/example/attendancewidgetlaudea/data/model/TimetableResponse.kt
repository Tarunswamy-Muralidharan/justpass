package com.example.attendancewidgetlaudea.data.model

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
    val facultyNames: List<String>
)

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
