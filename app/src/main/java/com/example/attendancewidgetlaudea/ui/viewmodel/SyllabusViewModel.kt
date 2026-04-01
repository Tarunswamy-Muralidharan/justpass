package com.example.attendancewidgetlaudea.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancewidgetlaudea.data.model.Department
import com.example.attendancewidgetlaudea.data.model.Regulation
import com.example.attendancewidgetlaudea.data.model.SyllabusSubject
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SyllabusUiState(
    val isLoading: Boolean = false,
    val subjects: List<SyllabusSubject> = emptyList(),
    val semesters: List<Int> = emptyList(),
    val selectedSemester: Int = 0, // 0 = all
    val selectedSubject: SyllabusSubject? = null,
    val searchQuery: String = "",
    val department: Department? = null,
    val regulation: Regulation = Regulation.R2021,
    val errorMessage: String? = null
)

class SyllabusViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SyllabusUiState())
    val uiState: StateFlow<SyllabusUiState> = _uiState.asStateFlow()

    private var allSubjects: List<SyllabusSubject> = emptyList()

    fun loadSyllabus(department: Department?, regulation: Regulation) {
        if (department == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Could not detect your department")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, department = department, regulation = regulation)

        viewModelScope.launch {
            try {
                val subjects = withContext(Dispatchers.IO) {
                    val json = getApplication<Application>().assets
                        .open("syllabus_r2021.json")
                        .bufferedReader().use { it.readText() }

                    val type = object : TypeToken<Map<String, DeptWrapper>>() {}.type
                    val data: Map<String, DeptWrapper> = Gson().fromJson(json, type)

                    // JSON key: "CSE" for R2021, "CSE_R2025" for R2025
                    val key = if (regulation == Regulation.R2025) "${department.name}_R2025" else department.name
                    data[key]?.subjects ?: emptyList()
                }

                allSubjects = subjects
                val semesters = subjects.map { it.semester }.distinct().sorted()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    subjects = subjects,
                    semesters = semesters
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load syllabus: ${e.message}"
                )
            }
        }
    }

    fun selectSemester(semester: Int) {
        _uiState.value = _uiState.value.copy(selectedSemester = semester)
    }

    fun selectSubject(subject: SyllabusSubject?) {
        _uiState.value = _uiState.value.copy(selectedSubject = subject)
    }

    fun updateSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun getFilteredSubjects(): List<SyllabusSubject> {
        val state = _uiState.value
        var filtered = allSubjects

        if (state.selectedSemester > 0) {
            filtered = filtered.filter { it.semester == state.selectedSemester }
        }

        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.lowercase()
            filtered = filtered.filter {
                it.code.lowercase().contains(q) || it.title.lowercase().contains(q)
            }
        }

        return filtered.sortedWith(compareBy({ it.semester }, { it.code }))
    }

    private data class DeptWrapper(val subjects: List<SyllabusSubject>)
}
