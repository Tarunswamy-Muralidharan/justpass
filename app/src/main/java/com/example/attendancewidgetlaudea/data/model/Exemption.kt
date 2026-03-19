package com.example.attendancewidgetlaudea.data.model

import com.google.gson.annotations.SerializedName

data class Exemption(
    @SerializedName("_id")
    val id: String,

    @SerializedName("rollNumber")
    val rollNumber: String,

    @SerializedName("category")
    val category: String,  // "Day" or "Session"

    @SerializedName("sessions")
    val sessions: List<String>?,  // null for Day, array of time strings for Session category

    @SerializedName("reason")
    val reason: String,

    @SerializedName("status")
    val status: String,  // "V" = Verified

    @SerializedName("exemptionType")
    val exemptionType: String,  // Symposium, Internship, Workshop, Volunteering Activity

    @SerializedName("fromDate")
    val fromDate: String,  // "2025-03-11"

    @SerializedName("toDate")
    val toDate: String  // "2025-03-13"
)
