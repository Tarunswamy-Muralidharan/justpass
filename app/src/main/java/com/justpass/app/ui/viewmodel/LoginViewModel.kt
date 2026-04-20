package com.justpass.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.justpass.app.data.analytics.Analytics
import com.justpass.app.data.repository.AttendanceRepository
import com.justpass.app.data.repository.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val errorMessage: String? = null,
    val rollNumber: String = "",
    val password: String = ""
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AttendanceRepository.getInstance(application)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun updateRollNumber(rollNumber: String) {
        _uiState.value = _uiState.value.copy(rollNumber = rollNumber, errorMessage = null)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun login() {
        val currentState = _uiState.value

        if (currentState.rollNumber.isBlank()) {
            _uiState.value = currentState.copy(errorMessage = "Please enter your roll number")
            return
        }

        if (currentState.password.isBlank()) {
            _uiState.value = currentState.copy(errorMessage = "Please enter your password")
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, errorMessage = null)

            when (val result = repository.login(currentState.rollNumber, currentState.password)) {
                is Result.Success -> {
                    // Extract name from JWT token for analytics
                    val token = repository.getCachedToken()
                    val displayName = token?.let { Analytics.extractNameFromToken(it) }
                    Analytics.setUser(currentState.rollNumber, displayName)
                    Analytics.logLogin(currentState.rollNumber, displayName)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        password = ""
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun resetForNewLogin() {
        _uiState.value = LoginUiState()
    }
}
