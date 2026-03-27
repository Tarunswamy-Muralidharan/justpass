package com.example.attendancewidgetlaudea.data.model

data class ExamSeatData(
    val hall: String,
    val seatNumber: String,
    val examName: String = "",
    val date: String = ""
)
