package com.example.attendancewidgetlaudea.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.example.attendancewidgetlaudea.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

data class CgpaUiState(
    val department: Department = Department.CSE,
    val regulation: Regulation = Regulation.R2021,
    val batchYear: Int = 2023,
    val selectedSemester: Int = 1,
    val semesterGrades: Map<Int, List<SubjectGrade>> = emptyMap(),
    val availableSemesters: List<Int> = listOf(1),
    val resultsApplied: Boolean = false
)

class CgpaViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(CgpaUiState())
    val uiState: StateFlow<CgpaUiState> = _uiState

    private val prefs = application.getSharedPreferences("laudea_prefs", Context.MODE_PRIVATE)

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

        restoreGrades()
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
            availableSemesters = semesters,
            resultsApplied = false
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
        saveGrades()
    }

    fun setElectiveName(semester: Int, subjectIndex: Int, name: String) {
        val current = _uiState.value.semesterGrades.toMutableMap()
        val semGrades = current[semester]?.toMutableList() ?: return
        if (subjectIndex >= semGrades.size) return
        semGrades[subjectIndex] = semGrades[subjectIndex].copy(customName = name)
        current[semester] = semGrades
        _uiState.value = _uiState.value.copy(semesterGrades = current)
        saveElectiveNames()
    }

    fun applyResults(grades: List<GradeEntry>) {
        if (_uiState.value.resultsApplied) return
        val current = _uiState.value.semesterGrades.toMutableMap()

        // Group result grades by semester
        val resultsBySemester = grades.groupBy { it.semester }

        for ((sem, resultGrades) in resultsBySemester) {
            val semGrades = current[sem]?.toMutableList() ?: continue
            val unmatchedElectives = mutableListOf<GradeEntry>()

            for (entry in resultGrades) {
                val letterGrade = mapLetterGrade(entry.letterGrade) ?: continue
                val code = entry.courseCode?.trim()?.uppercase() ?: continue

                // Try matching by course code first
                val matchIndex = semGrades.indexOfFirst {
                    !it.subject.isElective && it.subject.code.trim().uppercase() == code
                }

                if (matchIndex >= 0) {
                    semGrades[matchIndex] = semGrades[matchIndex].copy(grade = letterGrade)
                } else {
                    unmatchedElectives.add(entry)
                }
            }

            // Fill elective slots in order with unmatched results
            val electiveSlots = semGrades.indices.filter { semGrades[it].subject.isElective && semGrades[it].grade == null }
            unmatchedElectives.forEachIndexed { i, entry ->
                if (i < electiveSlots.size) {
                    val slotIdx = electiveSlots[i]
                    val letterGrade = mapLetterGrade(entry.letterGrade) ?: return@forEachIndexed
                    semGrades[slotIdx] = semGrades[slotIdx].copy(
                        grade = letterGrade,
                        customName = entry.courseTitle
                    )
                }
            }

            current[sem] = semGrades
        }

        _uiState.value = _uiState.value.copy(semesterGrades = current, resultsApplied = true)
        saveGrades()
        saveElectiveNames()
    }

    fun resetSemester(semester: Int) {
        val current = _uiState.value.semesterGrades.toMutableMap()
        val semGrades = current[semester] ?: return
        current[semester] = semGrades.map { it.copy(grade = null, customName = null) }
        _uiState.value = _uiState.value.copy(semesterGrades = current)
        saveGrades()
        saveElectiveNames()
    }

    /** Apply grades parsed from OCR — matches by course code across all semesters.
     *  Returns set of semester numbers that were affected. */
    fun applyOcrGrades(ocrGrades: List<Pair<String, String>>): Set<Int> {
        val current = _uiState.value.semesterGrades.toMutableMap()
        val affectedSemesters = mutableSetOf<Int>()

        for ((code, gradeStr) in ocrGrades) {
            val grade = mapLetterGrade(gradeStr) ?: continue
            val upperCode = code.trim().uppercase()

            // Search across all semesters for matching course code
            for ((sem, semGrades) in current) {
                val list = semGrades.toMutableList()
                val idx = list.indexOfFirst { it.subject.code.trim().uppercase() == upperCode }
                if (idx >= 0) {
                    list[idx] = list[idx].copy(grade = grade)
                    current[sem] = list
                    affectedSemesters.add(sem)
                    break
                }
            }
        }

        _uiState.value = _uiState.value.copy(semesterGrades = current)
        saveGrades()
        return affectedSemesters
    }

    private fun mapLetterGrade(label: String?): LetterGrade? {
        return when (label?.trim()?.uppercase()) {
            "O" -> LetterGrade.O
            "A+" -> LetterGrade.A_PLUS
            "A" -> LetterGrade.A
            "B+" -> LetterGrade.B_PLUS
            "B" -> LetterGrade.B
            "C" -> LetterGrade.C
            "RA" -> LetterGrade.RA
            "AB" -> LetterGrade.AB
            else -> null
        }
    }

    private fun saveGrades() {
        try {
            val root = JSONObject()
            for ((sem, grades) in _uiState.value.semesterGrades) {
                val semObj = JSONObject()
                grades.forEachIndexed { index, sg ->
                    if (sg.grade != null) {
                        semObj.put(index.toString(), sg.grade.label)
                    }
                }
                if (semObj.length() > 0) {
                    root.put(sem.toString(), semObj)
                }
            }
            prefs.edit().putString("cgpa_grades", root.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun restoreGrades() {
        try {
            val json = prefs.getString("cgpa_grades", null) ?: return
            val root = JSONObject(json)
            val current = _uiState.value.semesterGrades.toMutableMap()

            for (semKey in root.keys()) {
                val sem = semKey.toIntOrNull() ?: continue
                val semObj = root.getJSONObject(semKey)
                val semGrades = current[sem]?.toMutableList() ?: continue

                for (idxKey in semObj.keys()) {
                    val idx = idxKey.toIntOrNull() ?: continue
                    if (idx >= semGrades.size) continue
                    val gradeLabel = semObj.getString(idxKey)
                    val grade = mapLetterGrade(gradeLabel)
                    if (grade != null) {
                        semGrades[idx] = semGrades[idx].copy(grade = grade)
                    }
                }
                current[sem] = semGrades
            }

            _uiState.value = _uiState.value.copy(semesterGrades = current)
        } catch (_: Exception) {}

        restoreElectiveNames()
    }

    private fun saveElectiveNames() {
        try {
            val root = JSONObject()
            for ((sem, grades) in _uiState.value.semesterGrades) {
                val semObj = JSONObject()
                grades.forEachIndexed { index, sg ->
                    if (sg.customName != null) {
                        semObj.put(index.toString(), sg.customName)
                    }
                }
                if (semObj.length() > 0) {
                    root.put(sem.toString(), semObj)
                }
            }
            prefs.edit().putString("cgpa_elective_names", root.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun restoreElectiveNames() {
        try {
            val json = prefs.getString("cgpa_elective_names", null) ?: return
            val root = JSONObject(json)
            val current = _uiState.value.semesterGrades.toMutableMap()

            for (semKey in root.keys()) {
                val sem = semKey.toIntOrNull() ?: continue
                val semObj = root.getJSONObject(semKey)
                val semGrades = current[sem]?.toMutableList() ?: continue

                for (idxKey in semObj.keys()) {
                    val idx = idxKey.toIntOrNull() ?: continue
                    if (idx >= semGrades.size) continue
                    val name = semObj.getString(idxKey)
                    semGrades[idx] = semGrades[idx].copy(customName = name)
                }
                current[sem] = semGrades
            }

            _uiState.value = _uiState.value.copy(semesterGrades = current)
        } catch (_: Exception) {}
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
