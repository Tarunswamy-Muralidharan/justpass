package com.example.attendancewidgetlaudea.data.repository

import android.content.Context
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import com.example.attendancewidgetlaudea.data.model.AbsentDay
import com.example.attendancewidgetlaudea.data.model.AttendanceData
import com.example.attendancewidgetlaudea.data.model.CourseMarks
import com.example.attendancewidgetlaudea.data.model.CircularAttachment
import com.example.attendancewidgetlaudea.data.model.CircularDetail
import com.example.attendancewidgetlaudea.data.model.CircularListResponse
import com.example.attendancewidgetlaudea.data.model.Exemption
import com.example.attendancewidgetlaudea.data.model.TimetableResponse
import com.example.attendancewidgetlaudea.data.webview.InvalidCredentialsException
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
        // FAST CHECK: Validate credentials via direct Keycloak before slow WebView
        try {
            android.util.Log.d("AttendanceRepo", "Validating credentials for: $rollNumber")
            webViewAuthenticator.loginViaKeycloak(rollNumber, password)
            // Credentials are valid — proceed with WebView to get a proper auth-code token
        } catch (e: InvalidCredentialsException) {
            android.util.Log.e("AttendanceRepo", "Invalid credentials: ${e.message}")
            return Result.Error("Invalid roll number or password")
        } catch (e: Exception) {
            // Keycloak unreachable — skip validation, try WebView anyway
            android.util.Log.d("AttendanceRepo", "Credential check skipped: ${e.message}")
        }

        // WebView login (gets the proper authorization-code token that the SIS API accepts)
        return withContext(Dispatchers.Main) {
            try {
                android.util.Log.d("AttendanceRepo", "Starting WebView login for: $rollNumber")

                val result = webViewAuthenticator.loginAndFetchAttendance(rollNumber, password)

                result.fold(
                    onSuccess = { attendanceData ->
                        securePrefs.rollNumber = rollNumber
                        securePrefs.password = password
                        securePrefs.saveAttendanceData(attendanceData)
                        securePrefs.setLoggedIn(true)
                        try {
                            com.example.attendancewidgetlaudea.widget.AttendanceWidgetReceiver.updateWidget(context)
                        } catch (e: Exception) {}
                        android.util.Log.d("AttendanceRepo", "WebView login successful: ${attendanceData.attendancePercentage}%")
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

        // FAST PATH: Try direct HTTP with cached token (instant)
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
            android.util.Log.d("AttendanceRepo", "Fast refresh failed, trying token renewal")
        }

        // MEDIUM PATH: Try refresh token, then password grant, then retry fetch
        try {
            val refreshed = webViewAuthenticator.refreshAccessToken()
                || webViewAuthenticator.loginViaKeycloak(rollNumber!!, password!!)
            if (refreshed) {
                val retryResult = webViewAuthenticator.fetchAttendanceDirect(rollNumber)
                if (retryResult != null && retryResult.isSuccess) {
                    val attendanceData = retryResult.getOrThrow()
                    securePrefs.saveAttendanceData(attendanceData)
                    try {
                        com.example.attendancewidgetlaudea.widget.AttendanceWidgetReceiver.updateWidget(context)
                    } catch (e: Exception) {}
                    android.util.Log.d("AttendanceRepo", "Token renewal refresh successful: ${attendanceData.attendanceWithExemption}%")
                    return Result.Success(attendanceData)
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("AttendanceRepo", "All direct methods failed, falling back to WebView")
        }

        // SLOW PATH: Fall back to WebView (only if direct Keycloak grant is disabled)
        return withContext(Dispatchers.Main) {
            try {
                android.util.Log.d("AttendanceRepo", "WebView refresh for: $rollNumber")

                val result = webViewAuthenticator.fetchAttendanceOnly(rollNumber)

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
                        login(rollNumber, password)
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

        // Token expired — try refresh token first, then password login
        android.util.Log.d("AttendanceRepo", "Token expired for CA marks, trying refresh token")
        try {
            val refreshed = webViewAuthenticator.refreshAccessToken()
                || (!password.isNullOrEmpty() && webViewAuthenticator.loginViaKeycloak(rollNumber, password))
            if (refreshed) {
                val retryResult = webViewAuthenticator.fetchCAMarksDirect(rollNumber)
                if (retryResult != null && retryResult.isSuccess) {
                    return Result.Success(retryResult.getOrThrow())
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "CA marks token refresh error: ${e.message}")
        }

        // Last resort: full WebView login
        if (!password.isNullOrEmpty()) {
            android.util.Log.d("AttendanceRepo", "Direct login failed, falling back to WebView for CA marks")
            val loginResult = login(rollNumber, password)
            if (loginResult is Result.Success) {
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

        // Token expired — try refresh token first, then password login
        android.util.Log.d("AttendanceRepo", "Token expired for absent days, trying refresh token")
        try {
            val refreshed = webViewAuthenticator.refreshAccessToken()
                || (!password.isNullOrEmpty() && webViewAuthenticator.loginViaKeycloak(rollNumber, password))
            if (refreshed) {
                val retryResult = webViewAuthenticator.fetchAbsentDays(rollNumber)
                if (retryResult != null && retryResult.isSuccess) {
                    return Result.Success(retryResult.getOrThrow())
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "Absent days token refresh error: ${e.message}")
        }

        // Last resort: full WebView login
        if (!password.isNullOrEmpty()) {
            android.util.Log.d("AttendanceRepo", "Direct login failed, falling back to WebView for absent days")
            val loginResult = login(rollNumber, password)
            if (loginResult is Result.Success) {
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

    suspend fun fetchPresentDays(): Result<List<AbsentDay>> {
        val rollNumber = securePrefs.rollNumber
        val password = securePrefs.password
        if (rollNumber.isNullOrEmpty()) {
            return Result.Error("Not logged in")
        }

        // Try with cached token first
        try {
            val result = webViewAuthenticator.fetchPresentDays(rollNumber)
            if (result != null && result.isSuccess) {
                return Result.Success(result.getOrThrow())
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "Present days error: ${e.message}")
        }

        // Token expired — try refresh token first, then password login
        android.util.Log.d("AttendanceRepo", "Token expired for present days, trying refresh token")
        try {
            val refreshed = webViewAuthenticator.refreshAccessToken()
                || (!password.isNullOrEmpty() && webViewAuthenticator.loginViaKeycloak(rollNumber, password))
            if (refreshed) {
                val retryResult = webViewAuthenticator.fetchPresentDays(rollNumber)
                if (retryResult != null && retryResult.isSuccess) {
                    return Result.Success(retryResult.getOrThrow())
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "Present days token refresh error: ${e.message}")
        }

        // Last resort: full WebView login
        if (!password.isNullOrEmpty()) {
            android.util.Log.d("AttendanceRepo", "Direct login failed, falling back to WebView for present days")
            val loginResult = login(rollNumber, password)
            if (loginResult is Result.Success) {
                try {
                    val retryResult = webViewAuthenticator.fetchPresentDays(rollNumber)
                    if (retryResult != null && retryResult.isSuccess) {
                        return Result.Success(retryResult.getOrThrow())
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AttendanceRepo", "Present days retry error: ${e.message}")
                }
            }
        }

        return Result.Error("Could not fetch present days. Try refreshing first.")
    }

    suspend fun fetchExemptions(): Result<List<Exemption>> {
        val rollNumber = securePrefs.rollNumber
        val password = securePrefs.password
        if (rollNumber.isNullOrEmpty()) {
            return Result.Error("Not logged in")
        }

        // Try with cached token first
        try {
            val result = webViewAuthenticator.fetchExemptionsDirect(rollNumber)
            if (result != null && result.isSuccess) {
                return Result.Success(result.getOrThrow())
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "Exemptions error: ${e.message}")
        }

        // Token expired — try refresh token first, then password login
        android.util.Log.d("AttendanceRepo", "Token expired for exemptions, trying refresh token")
        try {
            val refreshed = webViewAuthenticator.refreshAccessToken()
                || (!password.isNullOrEmpty() && webViewAuthenticator.loginViaKeycloak(rollNumber, password))
            if (refreshed) {
                val retryResult = webViewAuthenticator.fetchExemptionsDirect(rollNumber)
                if (retryResult != null && retryResult.isSuccess) {
                    return Result.Success(retryResult.getOrThrow())
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "Exemptions token refresh error: ${e.message}")
        }

        // Last resort: full WebView login
        if (!password.isNullOrEmpty()) {
            android.util.Log.d("AttendanceRepo", "Direct login failed, falling back to WebView for exemptions")
            val loginResult = login(rollNumber, password)
            if (loginResult is Result.Success) {
                try {
                    val retryResult = webViewAuthenticator.fetchExemptionsDirect(rollNumber)
                    if (retryResult != null && retryResult.isSuccess) {
                        return Result.Success(retryResult.getOrThrow())
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AttendanceRepo", "Exemptions retry error: ${e.message}")
                }
            }
        }

        return Result.Error("Could not fetch exemptions. Try refreshing first.")
    }

    suspend fun fetchTimetable(): Result<TimetableResponse> {
        val rollNumber = securePrefs.rollNumber
        val password = securePrefs.password

        if (rollNumber.isNullOrEmpty()) {
            return Result.Error("Not logged in")
        }

        // Get the student's nodeId (timetable config) — fetch from API if not cached
        var configId = securePrefs.timetableConfigId
        if (configId.isNullOrEmpty()) {
            android.util.Log.d("AttendanceRepo", "No cached nodeId, fetching from student profile")
            configId = webViewAuthenticator.fetchStudentNodeId(rollNumber)
            if (configId != null) {
                securePrefs.timetableConfigId = configId
                android.util.Log.d("AttendanceRepo", "Cached nodeId: $configId")
            } else {
                // Token might be expired, try refresh then retry
                val refreshed = webViewAuthenticator.refreshAccessToken()
                    || (!password.isNullOrEmpty() && webViewAuthenticator.loginViaKeycloak(rollNumber, password))
                if (refreshed) {
                    configId = webViewAuthenticator.fetchStudentNodeId(rollNumber)
                    if (configId != null) securePrefs.timetableConfigId = configId
                }
            }
            if (configId.isNullOrEmpty()) {
                return Result.Error("Could not determine your timetable. Try refreshing attendance first.")
            }
        }

        // Fast path: direct HTTP with cached token
        try {
            val result = webViewAuthenticator.fetchTimetableDirect(configId, rollNumber)
            if (result != null && result.isSuccess) {
                android.util.Log.d("AttendanceRepo", "Timetable fast fetch successful")
                return Result.Success(result.getOrThrow())
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "Timetable fast fetch error: ${e.message}")
        }

        // Token expired — try refresh token first, then password login
        android.util.Log.d("AttendanceRepo", "Token expired for timetable, trying refresh token")
        try {
            val refreshed = webViewAuthenticator.refreshAccessToken()
                || (!password.isNullOrEmpty() && webViewAuthenticator.loginViaKeycloak(rollNumber, password))
            if (refreshed) {
                val retryResult = webViewAuthenticator.fetchTimetableDirect(configId, rollNumber)
                if (retryResult != null && retryResult.isSuccess) {
                    return Result.Success(retryResult.getOrThrow())
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "Timetable token refresh error: ${e.message}")
        }

        // Last resort: full WebView login
        if (!password.isNullOrEmpty()) {
            android.util.Log.d("AttendanceRepo", "Falling back to WebView for timetable")
            val loginResult = login(rollNumber, password)
            if (loginResult is Result.Success) {
                try {
                    val retryResult = webViewAuthenticator.fetchTimetableDirect(configId, rollNumber)
                    if (retryResult != null && retryResult.isSuccess) {
                        return Result.Success(retryResult.getOrThrow())
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AttendanceRepo", "Timetable retry error: ${e.message}")
                }
            }
        }

        return Result.Error("Could not fetch timetable. Try refreshing first.")
    }

    suspend fun fetchResult(): Result<String> {
        val rollNumber = securePrefs.rollNumber
        val password = securePrefs.password
        if (rollNumber.isNullOrEmpty()) {
            return Result.Error("Not logged in")
        }

        // Try with cached token first
        try {
            val result = webViewAuthenticator.fetchResultDirect(rollNumber)
            if (result != null && result.isSuccess) {
                return Result.Success(result.getOrThrow())
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "Result fetch error: ${e.message}")
        }

        // Token expired — try refresh then password login
        android.util.Log.d("AttendanceRepo", "Token expired for result, trying refresh token")
        try {
            val refreshed = webViewAuthenticator.refreshAccessToken()
                || (!password.isNullOrEmpty() && webViewAuthenticator.loginViaKeycloak(rollNumber, password))
            if (refreshed) {
                val retryResult = webViewAuthenticator.fetchResultDirect(rollNumber)
                if (retryResult != null && retryResult.isSuccess) {
                    return Result.Success(retryResult.getOrThrow())
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "Result token refresh error: ${e.message}")
        }

        // Last resort: full WebView login
        if (!password.isNullOrEmpty()) {
            val loginResult = login(rollNumber, password)
            if (loginResult is Result.Success) {
                try {
                    val retryResult = webViewAuthenticator.fetchResultDirect(rollNumber)
                    if (retryResult != null && retryResult.isSuccess) {
                        return Result.Success(retryResult.getOrThrow())
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AttendanceRepo", "Result retry error: ${e.message}")
                }
            }
        }

        return Result.Error("Could not fetch results. Results may not be published yet.")
    }

    fun isLoggedIn(): Boolean {
        return securePrefs.isLoggedIn()
    }

    fun getRollNumber(): String? {
        return securePrefs.rollNumber
    }

    fun getCachedToken(): String? {
        return webViewAuthenticator.cachedAuthToken
    }

    suspend fun fetchProfilePicture(): ByteArray? {
        val rollNumber = securePrefs.rollNumber ?: return null
        val password = securePrefs.password

        // Fast path: try with cached token
        try {
            val bytes = webViewAuthenticator.fetchProfilePicture(rollNumber)
            if (bytes != null && bytes.isNotEmpty()) return bytes
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "Profile pic fast fetch error: ${e.message}")
        }

        // Token expired — refresh then retry
        try {
            val refreshed = webViewAuthenticator.refreshAccessToken()
                || (!password.isNullOrEmpty() && webViewAuthenticator.loginViaKeycloak(rollNumber, password))
            if (refreshed) {
                val bytes = webViewAuthenticator.fetchProfilePicture(rollNumber)
                if (bytes != null && bytes.isNotEmpty()) return bytes
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "Profile pic token refresh error: ${e.message}")
        }

        return null
    }

    suspend fun fetchStudentBiodata(): com.example.attendancewidgetlaudea.data.model.StudentBiodata? {
        val rollNumber = securePrefs.rollNumber ?: return null
        val password = securePrefs.password

        try {
            val bio = webViewAuthenticator.fetchStudentProfile(rollNumber)
            if (bio != null) return bio
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "Biodata fast fetch error: ${e.message}")
        }

        // Token expired — refresh then retry
        try {
            val refreshed = webViewAuthenticator.refreshAccessToken()
                || (!password.isNullOrEmpty() && webViewAuthenticator.loginViaKeycloak(rollNumber, password))
            if (refreshed) {
                return webViewAuthenticator.fetchStudentProfile(rollNumber)
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "Biodata token refresh error: ${e.message}")
        }

        return null
    }

    suspend fun fetchCirculars(): Result<CircularListResponse> {
        // Ensure we have a meetings token
        if (!webViewAuthenticator.ensureMeetingsToken()) {
            return Result.Error("Could not authenticate with meetings module")
        }

        // Try with cached meetings token
        try {
            val result = webViewAuthenticator.fetchCircularsDirect()
            if (result != null && result.isSuccess) {
                return Result.Success(result.getOrThrow())
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "Circulars fetch error: ${e.message}")
        }

        // Token expired — re-authenticate meetings
        val rollNumber = securePrefs.rollNumber
        val password = securePrefs.password
        if (!rollNumber.isNullOrEmpty() && !password.isNullOrEmpty()) {
            try {
                if (webViewAuthenticator.loginViaMeetingsKeycloak(rollNumber, password)) {
                    val retryResult = webViewAuthenticator.fetchCircularsDirect()
                    if (retryResult != null && retryResult.isSuccess) {
                        return Result.Success(retryResult.getOrThrow())
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AttendanceRepo", "Circulars retry error: ${e.message}")
            }
        }

        return Result.Error("Could not fetch circulars")
    }

    suspend fun fetchCircularDetail(circularId: String): Result<CircularDetail> {
        if (!webViewAuthenticator.ensureMeetingsToken()) {
            return Result.Error("Could not authenticate with meetings module")
        }

        try {
            val result = webViewAuthenticator.fetchCircularDetailDirect(circularId)
            if (result != null && result.isSuccess) {
                return Result.Success(result.getOrThrow())
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "Circular detail error: ${e.message}")
        }

        // Token expired — re-authenticate
        val rollNumber = securePrefs.rollNumber
        val password = securePrefs.password
        if (!rollNumber.isNullOrEmpty() && !password.isNullOrEmpty()) {
            try {
                if (webViewAuthenticator.loginViaMeetingsKeycloak(rollNumber, password)) {
                    val retryResult = webViewAuthenticator.fetchCircularDetailDirect(circularId)
                    if (retryResult != null && retryResult.isSuccess) {
                        return Result.Success(retryResult.getOrThrow())
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AttendanceRepo", "Circular detail retry error: ${e.message}")
            }
        }

        return Result.Error("Could not fetch circular details")
    }

    suspend fun fetchCircularPdfUrl(attachment: CircularAttachment): Result<String> {
        if (!webViewAuthenticator.ensureMeetingsToken()) {
            return Result.Error("Could not authenticate with meetings module")
        }

        try {
            val result = webViewAuthenticator.fetchCircularPdfUrl(attachment)
            if (result != null && result.isSuccess) {
                return Result.Success(result.getOrThrow())
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "PDF URL error: ${e.message}")
        }

        // Token expired — re-authenticate
        val rollNumber = securePrefs.rollNumber
        val password = securePrefs.password
        if (!rollNumber.isNullOrEmpty() && !password.isNullOrEmpty()) {
            try {
                if (webViewAuthenticator.loginViaMeetingsKeycloak(rollNumber, password)) {
                    val retryResult = webViewAuthenticator.fetchCircularPdfUrl(attachment)
                    if (retryResult != null && retryResult.isSuccess) {
                        return Result.Success(retryResult.getOrThrow())
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AttendanceRepo", "PDF URL retry error: ${e.message}")
            }
        }

        return Result.Error("Could not get PDF download URL")
    }

    suspend fun downloadPdfBytes(signedUrl: String): ByteArray? {
        return webViewAuthenticator.downloadPdfBytes(signedUrl)
    }

    fun logout() {
        securePrefs.setLoggedIn(false)
        webViewAuthenticator.cachedAuthToken = null
        webViewAuthenticator.cachedMeetingsToken = null
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
