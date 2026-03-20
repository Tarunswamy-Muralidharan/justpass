package com.example.attendancewidgetlaudea.data.model

import com.google.gson.annotations.SerializedName

data class GradeEntry(
    @SerializedName("_id") val id: String? = null,
    @SerializedName("examId") val examId: String? = null,
    @SerializedName("attempt") val attempt: Int = 1,
    @SerializedName("semester") val semester: Int = 0,
    @SerializedName("courseCode") val courseCode: String? = null,
    @SerializedName("courseTitle") val courseTitle: String? = null,
    @SerializedName("letterGrade") val letterGrade: String? = null,
    @SerializedName("gradePoint") val gradePoint: Int = 0,
    @SerializedName("status") val status: String? = null,
    @SerializedName("examName") val examName: String? = null,
    @SerializedName("credit") val credit: Any? = null,
    @SerializedName("credits") val credits: Any? = null
) {
    fun getCreditsValue(): Int {
        return when (val c = credit ?: credits) {
            is Number -> c.toInt()
            is String -> c.toIntOrNull() ?: 0
            else -> 0
        }
    }

    fun isPassed(): Boolean {
        val s = (status ?: "").uppercase()
        val g = (letterGrade ?: "").uppercase()
        return s == "PASS" || s == "P" || (g.isNotEmpty() && g != "U" && g != "F" && g != "RA" && g != "AB")
    }
}
