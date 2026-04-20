package com.justpass.app.data.model

data class ExamTimetableEntry(
    val date: String,
    val day: String,
    val session: String,
    val timing: String,
    val courseCode: String,
    val courseName: String,
    val branch: String,
    val examType: String
)
