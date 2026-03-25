package com.example.attendancewidgetlaudea.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.attendancewidgetlaudea.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CgpaUiState(
    val department: Department = Department.CSE,
    val regulation: Regulation = Regulation.R2021,
    val batchYear: Int = 2023,
    val selectedSemester: Int = 1,
    val semesterGrades: Map<Int, List<SubjectGrade>> = emptyMap(),
    val availableSemesters: List<Int> = listOf(1)
)

class CgpaViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CgpaUiState())
    val uiState: StateFlow<CgpaUiState> = _uiState

    fun initialize(department: Department?, batchYear: Int?) {
        val dept = department ?: Department.CSE
        val batch = batchYear ?: 2023
        val reg = getRegulationForBatch(batch)
        val curriculum = getCurriculum(dept, reg)
        val semesters = curriculum.keys.sorted()

        val semGrades = semesters.associateWith { sem ->
            curriculum[sem]?.map { SubjectGrade(it) } ?: emptyList()
        }

        _uiState.value = CgpaUiState(
            department = dept,
            regulation = reg,
            batchYear = batch,
            selectedSemester = semesters.firstOrNull() ?: 1,
            semesterGrades = semGrades,
            availableSemesters = semesters
        )
    }

    fun selectDepartment(dept: Department) {
        val reg = _uiState.value.regulation
        val curriculum = getCurriculum(dept, reg)
        val semesters = curriculum.keys.sorted()
        val semGrades = semesters.associateWith { sem ->
            curriculum[sem]?.map { SubjectGrade(it) } ?: emptyList()
        }
        _uiState.value = _uiState.value.copy(
            department = dept,
            selectedSemester = semesters.firstOrNull() ?: 1,
            semesterGrades = semGrades,
            availableSemesters = semesters
        )
    }

    fun selectSemester(sem: Int) {
        _uiState.value = _uiState.value.copy(selectedSemester = sem)
    }

    fun setGrade(semester: Int, subjectIndex: Int, grade: LetterGrade?) {
        val current = _uiState.value.semesterGrades.toMutableMap()
        val semGrades = current[semester]?.toMutableList() ?: return
        if (subjectIndex >= semGrades.size) return
        semGrades[subjectIndex] = semGrades[subjectIndex].copy(grade = grade)
        current[semester] = semGrades
        _uiState.value = _uiState.value.copy(semesterGrades = current)
    }

    fun getSGPA(semester: Int): Double {
        val grades = _uiState.value.semesterGrades[semester] ?: return 0.0
        return calculateSGPA(grades)
    }

    fun getCGPA(): Double {
        return calculateCGPA(_uiState.value.semesterGrades.values.toList())
    }

    fun getFilledSemesterCount(): Int {
        return _uiState.value.semesterGrades.count { (_, grades) ->
            grades.any { it.grade != null }
        }
    }
}
