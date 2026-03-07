package com.example.attendancewidgetlaudea.data.model

import com.google.gson.annotations.SerializedName

data class AbsentDay(
    @SerializedName("date")
    val date: String,

    @SerializedName("sessions")
    val sessions: List<AbsentSession>
)

data class AbsentSession(
    @SerializedName("session")
    val session: String?,

    @SerializedName("startTime")
    val startTime: String,

    @SerializedName("endTime")
    val endTime: String,

    @SerializedName("courseCode")
    val courseCode: String,

    @SerializedName("courseTitle")
    val courseTitle: String
)
