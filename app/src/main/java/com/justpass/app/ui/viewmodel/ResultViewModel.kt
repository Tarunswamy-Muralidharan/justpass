package com.justpass.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.justpass.app.data.model.GradeEntry
import com.justpass.app.data.repository.AttendanceRepository
import com.justpass.app.data.repository.Result
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResultUiState(
    val isLoading: Boolean = false,
    val grades: List<GradeEntry> = emptyList(),
    val semesters: List<Int> = emptyList(),
    val selectedSemester: Int = 0,
    val errorMessage: String? = null
)

class ResultViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AttendanceRepository.getInstance(application)
    private val gson = Gson()

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        fetchResult()
    }

    fun fetchResult() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = repository.fetchResult()) {
                is Result.Success -> {
                    try {
                        val json = result.data
                        val grades: List<GradeEntry> = try {
                            val listType = object : TypeToken<List<GradeEntry>>() {}.type
                            gson.fromJson(json, listType)
                        } catch (_: Exception) {
                            emptyList()
                        }

                        val semesters = grades.map { it.semester }.distinct().filter { it > 0 }.sorted()
                        val selectedSem = semesters.maxOrNull() ?: 0

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            grades = grades,
                            semesters = semesters,
                            selectedSemester = selectedSem
                        )
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to parse result data"
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    fun selectSemester(semester: Int) {
        _uiState.value = _uiState.value.copy(selectedSemester = semester)
        com.justpass.app.data.analytics.Analytics.logResultViewed(semester)
    }
}
