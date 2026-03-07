package com.example.attendancewidgetlaudea.data.model

import com.google.gson.annotations.SerializedName

data class CourseMarks(
    @SerializedName("courseCode")
    val courseCode: String,

    @SerializedName("courseTitle")
    val courseTitle: String,

    @SerializedName("testDetails")
    val testDetails: TestDetails
)

data class TestDetails(
    @SerializedName("components")
    val components: List<Component>,

    @SerializedName("total")
    val total: MarksValue
)

data class Component(
    @SerializedName("name")
    val name: String,

    @SerializedName("marks")
    val marks: Marks? = null,

    @SerializedName("hasSubComponent")
    val hasSubComponent: Boolean = false,

    @SerializedName("sub")
    val subComponents: List<SubComponent>? = null,

    @SerializedName("hasConversion")
    val hasConversion: Boolean = false,

    @SerializedName("attendance")
    val attendance: String? = null,

    @SerializedName("markEntered")
    val markEntered: Int? = null
)

data class SubComponent(
    @SerializedName("name")
    val name: String,

    @SerializedName("marks")
    val marks: Marks? = null,

    @SerializedName("hasSubComponent")
    val hasSubComponent: Boolean = false,

    @SerializedName("hasConversion")
    val hasConversion: Boolean = false,

    @SerializedName("attendance")
    val attendance: String? = null,

    @SerializedName("markEntered")
    val markEntered: Int? = null
)

data class Marks(
    @SerializedName("actual")
    val actual: MarksValue,

    @SerializedName("scaled")
    val scaled: MarksValue,

    @SerializedName("mode")
    val mode: String? = null
)

data class MarksValue(
    @SerializedName("max")
    val max: Any, // Can be Int or Double

    @SerializedName("secured")
    val secured: Any // Can be Int, Double, or String ("NE" for not entered)
) {
    fun getMaxAsDouble(): Double {
        return when (max) {
            is Number -> max.toDouble()
            is String -> max.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    fun getSecuredAsDouble(): Double? {
        return when (secured) {
            is Number -> secured.toDouble()
            is String -> secured.toDoubleOrNull()
            else -> null
        }
    }

    fun getSecuredDisplay(): String {
        return when (secured) {
            is Number -> String.format("%.2f", secured.toDouble())
            is String -> if (secured == "NE") "N/E" else secured
            else -> "-"
        }
    }

    fun isNotEntered(): Boolean {
        return secured is String && secured == "NE"
    }
}
