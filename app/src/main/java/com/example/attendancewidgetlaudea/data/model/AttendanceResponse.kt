package com.example.attendancewidgetlaudea.data.model

import com.google.gson.annotations.SerializedName

data class AttendanceResponse(
    @SerializedName("presentCountTillDate")
    val presentCount: Int = 0,

    @SerializedName("absentCountTillDate")
    val absentCount: Int = 0,

    @SerializedName("netPresentPercentageTillDate")
    val attendancePercentage: Double = 0.0,

    @SerializedName("totalworkingTillDate")
    val totalClasses: Int? = null,

    @SerializedName("enteredTillDate")
    val enteredTillDate: Int? = null,

    @SerializedName("notEnteredTillDate")
    val notEnteredTillDate: Int? = null,

    @SerializedName("exemptionCountTillDate")
    val exemptionCount: Int? = null,

    @SerializedName("presentWithExemptionCountTillDate")
    val presentWithExemptionCount: Int? = null,

    @SerializedName("netPresentExcemptionPercentage")
    val attendanceWithExemption: Double? = null
)
