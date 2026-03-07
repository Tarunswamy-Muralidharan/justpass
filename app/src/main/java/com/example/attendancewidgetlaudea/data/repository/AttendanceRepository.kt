package com.example.attendancewidgetlaudea.data.repository

import android.content.Context
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import com.example.attendancewidgetlaudea.data.model.AbsentDay
import com.example.attendancewidgetlaudea.data.model.AttendanceData
import com.example.attendancewidgetlaudea.data.model.CourseMarks
import com.example.attendancewidgetlaudea.data.webview.WebViewAuthenticator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String, val exception: Exception? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}

class AttendanceRepository(private val context: Context) {

    private val securePrefs = SecurePreferences.getInstance(context)
    private val webViewAuthenticator = WebViewAuthenticator(context)

    suspend fun login(rollNumber: String, password: String): Result<AttendanceData> {
        return withContext(Dispatchers.Main) {
            try {
                android.util.Log.d("AttendanceRepo", "Starting WebView login for: $rollNumber")

                val result = webViewAuthenticator.loginAndFetchAttendance(rollNumber, password)

                result.fold(
                    onSuccess = { attendanceData ->
                        // Save credentials and attendance data
                        securePrefs.rollNumber = rollNumber
                        securePrefs.password = password
                        securePrefs.saveAttendanceData(attendanceData)
                        securePrefs.setLoggedIn(true)

                        // Update widget with new data
                        try {
                            com.example.attendancewidgetlaudea.widget.AttendanceWidgetReceiver.updateWidget(context)
                        } catch (e: Exception) {
                            // Ignore widget update errors
                        }

                        android.util.Log.d("AttendanceRepo", "Login successful, attendance: ${attendanceData.attendancePercentage}%")
                        Result.Success(attendanceData)
                    },
                    onFailure = { exception ->
                        android.util.Log.e("AttendanceRepo", "Login failed: ${exception.message}")
                        Result.Error("Login failed: ${exception.message}", exception as? Exception)
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("AttendanceRepo", "Login error: ${e.message}")
                Result.Error("Login error: ${e.message}", e)
            }
        }
    }

    suspend fun refreshAttendance(): Result<AttendanceData> {
        val rollNumber = securePrefs.rollNumber
        val password = securePrefs.password

        if (rollNumber.isNullOrEmpty() || password.isNullOrEmpty()) {
            return Result.Error("Not logged in")
        }

        android.util.Log.d("AttendanceRepo", "Refreshing attendance for: $rollNumber")

        // FAST PATH: Try direct HTTP call with cached token (instant)
        try {
            val directResult = webViewAuthenticator.fetchAttendanceDirect(rollNumber)
            if (directResult != null && directResult.isSuccess) {
                val attendanceData = directResult.getOrThrow()
                securePrefs.saveAttendanceData(attendanceData)
                try {
                    com.example.attendancewidgetlaudea.widget.AttendanceWidgetReceiver.updateWidget(context)
                } catch (e: Exception) {}
                android.util.Log.d("AttendanceRepo", "Fast refresh successful: ${attendanceData.attendanceWithExemption}%")
                return Result.Success(attendanceData)
            }
        } catch (e: Exception) {
            android.util.Log.d("AttendanceRepo", "Fast refresh failed, falling back to WebView")
        }

        // SLOW PATH: Fall back to WebView (token expired or not cached)
        return withContext(Dispatchers.Main) {
            try {
                android.util.Log.d("AttendanceRepo", "WebView refresh for: $rollNumber")

                val result = webViewAuthenticator.fetchAttendanceOnly(rollNumber!!)

                result.fold(
                    onSuccess = { attendanceData ->
                        securePrefs.saveAttendanceData(attendanceData)
                        try {
                            com.example.attendancewidgetlaudea.widget.AttendanceWidgetReceiver.updateWidget(context)
                        } catch (e: Exception) {}
                        android.util.Log.d("AttendanceRepo", "WebView refresh successful: ${attendanceData.attendanceWithExemption}%")
                        Result.Success(attendanceData)
                    },
                    onFailure = { exception ->
                        // Session might have expired, try full login
                        android.util.Log.d("AttendanceRepo", "Session expired, re-logging in")
                        login(rollNumber, password!!)
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("AttendanceRepo", "Refresh error: ${e.message}")
                Result.Error("Refresh error: ${e.message}", e)
            }
        }
    }

    fun getCachedAttendance(): AttendanceData {
        return securePrefs.getAttendanceData()
    }

    suspend fun fetchCAMarks(): Result<List<CourseMarks>> {
        val rollNumber = securePrefs.rollNumber
        val password = securePrefs.password

        if (rollNumber.isNullOrEmpty()) {
            return Result.Error("Not logged in")
        }

        // Fast path: direct HTTP with cached token
        try {
            val result = webViewAuthenticator.fetchCAMarksDirect(rollNumber)
            if (result != null && result.isSuccess) {
                android.util.Log.d("AttendanceRepo", "CA marks fast fetch successful")
                return Result.Success(result.getOrThrow())
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "CA marks fast fetch error: ${e.message}")
        }

        // Token expired or missing — silently re-login to get a fresh token
        if (!password.isNullOrEmpty()) {
            android.util.Log.d("AttendanceRepo", "Token expired, re-logging in for CA marks")
            val loginResult = login(rollNumber, password)
            if (loginResult is Result.Success) {
                // Retry with new token
                try {
                    val retryResult = webViewAuthenticator.fetchCAMarksDirect(rollNumber)
                    if (retryResult != null && retryResult.isSuccess) {
                        return Result.Success(retryResult.getOrThrow())
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AttendanceRepo", "CA marks retry error: ${e.message}")
                }
            }
        }

        return Result.Error("Could not fetch CA marks. Try refreshing attendance first.")
    }

    suspend fun fetchAbsentDays(): Result<List<AbsentDay>> {
        val rollNumber = securePrefs.rollNumber
        val password = securePrefs.password
        if (rollNumber.isNullOrEmpty()) {
            return Result.Error("Not logged in")
        }

        // Try with cached token first
        try {
            val result = webViewAuthenticator.fetchAbsentDays(rollNumber)
            if (result != null && result.isSuccess) {
                return Result.Success(result.getOrThrow())
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "Absent days error: ${e.message}")
        }

        // Token expired or missing — silently re-login to get a fresh token
        if (!password.isNullOrEmpty()) {
            android.util.Log.d("AttendanceRepo", "Token expired, re-logging in for absent days")
            val loginResult = login(rollNumber, password)
            if (loginResult is Result.Success) {
                // Retry with new token
                try {
                    val retryResult = webViewAuthenticator.fetchAbsentDays(rollNumber)
                    if (retryResult != null && retryResult.isSuccess) {
                        return Result.Success(retryResult.getOrThrow())
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AttendanceRepo", "Absent days retry error: ${e.message}")
                }
            }
        }

        return Result.Error("Could not fetch absent details. Try refreshing first.")
    }

    fun isLoggedIn(): Boolean {
        return securePrefs.isLoggedIn()
    }

    fun getRollNumber(): String? {
        return securePrefs.rollNumber
    }

    fun logout() {
        securePrefs.setLoggedIn(false)
        webViewAuthenticator.cachedAuthToken = null
        webViewAuthenticator.clearSession()
        securePrefs.clearAll()
        // Update widget to show logged out state
        try {
            com.example.attendancewidgetlaudea.widget.AttendanceWidgetReceiver.updateWidget(context)
        } catch (e: Exception) {
            // Ignore widget update errors
        }
    }

    companion object {
        @Volatile
        private var instance: AttendanceRepository? = null

        fun getInstance(context: Context): AttendanceRepository {
            return instance ?: synchronized(this) {
                instance ?: AttendanceRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
