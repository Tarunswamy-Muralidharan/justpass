package com.justpass.app.data.model

data class CalendarEvent(
    val id: String,
    val summary: String,
    val startDate: String, // "YYYY-MM-DD"
    val endDate: String?,
    val eventType: CalendarEventType
)

enum class CalendarEventType {
    HOLIDAY, WORKING_DAY, ACADEMIC, EXAM, EVENT;

    companion object {
        fun fromSummary(summary: String): CalendarEventType {
            val s = summary.lowercase()
            return when {
                s.startsWith("holiday") -> HOLIDAY
                s.contains("working day") -> WORKING_DAY
                s.contains("reopening") || s.contains("last working") || s.contains("commencement") -> ACADEMIC
                s.contains("exam") || s.contains("assessment") || s.contains("cia") || s.contains("cat") -> EXAM
                else -> EVENT
            }
        }
    }
}
