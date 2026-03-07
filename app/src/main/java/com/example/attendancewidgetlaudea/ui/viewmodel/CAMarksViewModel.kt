package com.example.attendancewidgetlaudea.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancewidgetlaudea.data.model.CourseMarks
import com.example.attendancewidgetlaudea.data.repository.AttendanceRepository
import com.example.attendancewidgetlaudea.data.repository.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CAMarksUiState(
    val isLoading: Boolean = false,
    val courseMarksList: List<CourseMarks> = emptyList(),
    val errorMessage: String? = null,
    val selectedCourse: CourseMarks? = null
)

class CAMarksViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AttendanceRepository.getInstance(application)

    private val _uiState = MutableStateFlow(CAMarksUiState())
    val uiState: StateFlow<CAMarksUiState> = _uiState.asStateFlow()

    init {
        fetchCAMarks()
    }

    fun fetchCAMarks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = repository.fetchCAMarks()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        courseMarksList = result.data
                    )
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

    fun selectCourse(course: CourseMarks?) {
        _uiState.value = _uiState.value.copy(selectedCourse = course)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
