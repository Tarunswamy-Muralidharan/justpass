package com.example.attendancewidgetlaudea.data.model

data class SyllabusSubject(
    val code: String,
    val title: String,
    val semester: Int,
    val credits: String,
    val syllabus: String
)

data class DepartmentSyllabus(
    val subjects: List<SyllabusSubject>
)
