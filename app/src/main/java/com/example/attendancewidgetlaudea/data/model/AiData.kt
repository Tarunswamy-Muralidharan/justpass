package com.example.attendancewidgetlaudea.data.model

/**
 * Navigation actions the AI advisor can suggest.
 * Maps user intent to app screens.
 */
enum class NavAction(val label: String, val icon: String) {
    SUBJECT_ATTENDANCE("Subject Attendance", "📊"),
    ABSENT_DAYS("Absent Days", "📅"),
    CA_MARKS("CA Marks", "📝"),
    EXEMPTIONS("Exemptions", "✅"),
    RESULTS("Semester Results", "🎓"),
    TIMETABLE("Timetable", "🕐"),
    GPA_CALCULATOR("GPA Calculator", "🧮"),
    CALENDAR("Academic Calendar", "📆"),
    CIRCULARS("Circulars", "📢"),
    SYLLABUS("Syllabus", "📖"),
}

/**
 * AI chat data model — shared by LiteRT-LM advisor.
 */
data class AiChatMessage(
    val role: String,       // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val navAction: NavAction? = null
)
