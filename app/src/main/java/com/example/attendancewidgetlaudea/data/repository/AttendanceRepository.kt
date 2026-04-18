package com.example.attendancewidgetlaudea.data.repository

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    private val gson = com.google.gson.Gson()
    private val absentDayListType = object : com.google.gson.reflect.TypeToken<List<AbsentDay>>() {}.type
    private val courseMarksListType = object : com.google.gson.reflect.TypeToken<List<CourseMarks>>() {}.type

    // In-memory caches — avoid duplicate network calls across screens
    @Volatile var cachedCourseMarks: List<CourseMarks>? = null
        private set
    @Volatile var cachedPresentDays: List<AbsentDay>? = null
        private set
    @Volatile var cachedAbsentDays: List<AbsentDay>? = null
        private set

    init {
        // Restore cached data from disk on startup — instant load
        try {
            securePrefs.cachedPresentDaysJson?.let {
                cachedPresentDays = gson.fromJson(it, absentDayListType)
            }
            securePrefs.cachedAbsentDaysJson?.let {
                cachedAbsentDays = gson.fromJson(it, absentDayListType)
            }
            securePrefs.cachedCourseMarksFullJson?.let {
                cachedCourseMarks = gson.fromJson(it, courseMarksListType)
            }
        } catch (_: Exception) {}
    }

    private fun persistPresentDays(data: List<AbsentDay>) {
        cachedPresentDays = data
        try { securePrefs.cachedPresentDaysJson = gson.toJson(data) } catch (_: Exception) {}
    }

    private fun persistAbsentDays(data: List<AbsentDay>) {
        cachedAbsentDays = data
        try { securePrefs.cachedAbsentDaysJson = gson.toJson(data) } catch (_: Exception) {}
    }

    suspend fun login(rollNumber: String, password: String): Result<AttendanceData> {
        // FAST PATH: Direct Keycloak login + direct API fetch (no WebView needed)
        try {
            android.util.Log.d("AttendanceRepo", "Validating credentials for: $rollNumber")
            val loginOk = webViewAuthenticator.loginViaKeycloak(rollNumber, password)
            if (loginOk) {
                // Token acquired — try fetching attendance directly
                android.util.Log.d("AttendanceRepo", "Token acquired, trying direct attendance fetch")
                val directResult = webViewAuthenticator.fetchAttendanceDirect(rollNumber)
                if (directResult != null && directResult.isSuccess) {
                    val attendanceData = directResult.getOrThrow()
                    securePrefs.rollNumber = rollNumber
                    securePrefs.password = password
                    securePrefs.saveAttendanceData(attendanceData)
                    securePrefs.setLoggedIn(true)
                    try {
                        com.example.attendancewidgetlaudea.widget.AttendanceWidgetReceiver.updateWidget(context)
                    } catch (_: Exception) {}
                    android.util.Log.d("AttendanceRepo", "Fast login successful: ${attendanceData.attendancePercentage}%")
                    return Result.Success(attendanceData)
                }
                // Token works but attendance failed — log in anyway with empty/cached data
                // User can still use chess, CA marks, results, etc.
                val failure = directResult?.exceptionOrNull()
                if (failure is WebViewAuthenticator.ServerDownException) {
                    android.util.Log.w("AttendanceRepo", "Login OK but SIS server down (HTTP ${failure.statusCode}) — logging in with cached data")
                } else {
                    android.util.Log.d("AttendanceRepo", "Token OK but attendance fetch failed — logging in with empty attendance")
                }
                securePrefs.rollNumber = rollNumber
                securePrefs.password = password
                securePrefs.setLoggedIn(true)
                val cached = securePrefs.getAttendanceData()
                return Result.Success(cached)
            }
        } catch (e: InvalidCredentialsException) {
            android.util.Log.e("AttendanceRepo", "Invalid credentials: ${e.message}")
            return Result.Error("Invalid roll number or password")
        } catch (e: Exception) {
            android.util.Log.d("AttendanceRepo", "Fast login failed: ${e.message}")
        }

        // SLOW PATH: WebView login fallback
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
                        val msg = exception.message ?: ""
                        // If the SIS page loaded but attendance fetch failed (HTTP 5xx),
                        // the user IS authenticated — log them in with cached/empty data
                        if (msg.contains("HTTP 5") || msg.contains("Could not fetch attendance")) {
                            android.util.Log.w("AttendanceRepo", "WebView login: authenticated but attendance API down — logging in with cached data")
                            securePrefs.rollNumber = rollNumber
                            securePrefs.password = password
                            securePrefs.setLoggedIn(true)
                            val cached = securePrefs.getAttendanceData()
                            Result.Success(cached)
                        } else {
                            android.util.Log.e("AttendanceRepo", "Login failed: ${exception.message}")
                            Result.Error("Login failed: ${exception.message}", exception as? Exception)
                        }
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
            if (directResult != null) {
                if (directResult.isSuccess) {
                    val attendanceData = directResult.getOrThrow()
                    securePrefs.saveAttendanceData(attendanceData)
                    try {
                        com.example.attendancewidgetlaudea.widget.AttendanceWidgetReceiver.updateWidget(context)
                    } catch (e: Exception) {}
                    android.util.Log.d("AttendanceRepo", "Fast refresh successful: ${attendanceData.attendanceWithExemption}%")
                    return Result.Success(attendanceData)
                }
                // Server is down (5xx) — skip all retry paths, show cached data
                val failure = directResult.exceptionOrNull()
                if (failure is WebViewAuthenticator.ServerDownException) {
                    android.util.Log.w("AttendanceRepo", "LAUDEA server down (HTTP ${failure.statusCode})")
                    return Result.Error("LAUDEA server is temporarily down. Showing cached data.")
                }
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
                val data = result.getOrThrow()
                cachedCourseMarks = data
                try { securePrefs.cachedCourseMarksFullJson = gson.toJson(data) } catch (_: Exception) {}
                android.util.Log.d("AttendanceRepo", "CA marks fast fetch successful")
                return Result.Success(data)
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

        // Last resort: do a full WebView login to get a token, then retry
        android.util.Log.d("AttendanceRepo", "No token available, doing WebView login first for CA marks")
        if (!password.isNullOrEmpty()) {
            val loginResult = login(rollNumber, password)
            if (loginResult is Result.Success) {
                // Retry with whatever token was captured during login
                try {
                    val retryResult = webViewAuthenticator.fetchCAMarksDirect(rollNumber)
                    if (retryResult != null && retryResult.isSuccess) {
                        val data = retryResult.getOrThrow()
                        cachedCourseMarks = data
                        try { securePrefs.cachedCourseMarksFullJson = gson.toJson(data) } catch (_: Exception) {}
                        android.util.Log.d("AttendanceRepo", "CA marks after login successful: ${data.size} courses")
                        return Result.Success(data)
                    }
                } catch (_: Exception) {}
            }
        }
        return Result.Error("Could not fetch CA marks. Please refresh attendance from the dashboard first.")
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
                val data = result.getOrThrow()
                persistAbsentDays(data)
                return Result.Success(data)
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
                val data = result.getOrThrow()
                persistPresentDays(data)
                return Result.Success(data)
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

    suspend fun fetchRegistrations(): Result<String> {
        val rollNumber = securePrefs.rollNumber ?: return Result.Error("Not logged in")
        try {
            val result = webViewAuthenticator.fetchRegistrationsDirect(rollNumber)
            if (result != null && result.isSuccess) return Result.Success(result.getOrThrow())
        } catch (_: Exception) {}
        return Result.Error("Could not fetch registrations")
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

    /**
     * Prefetch data from all tiles and cache as compact strings for AI advisor.
     * Called after successful attendance refresh. Runs silently — failures are ignored.
     */
    suspend fun prefetchForAI() = coroutineScope {
        val gson = com.google.gson.Gson()

        // Run all fetches in parallel — server is slow (~15-20s each), parallel cuts total time
        val caDeferred = async { try { fetchCAMarks() } catch (_: Exception) { null } }
        val resultDeferred = async { try { fetchResult() } catch (_: Exception) { null } }
        val absentDeferred = async { try { fetchAbsentDays() } catch (_: Exception) { null } }
        val presentDeferred = async { try { fetchPresentDays() } catch (_: Exception) { null } }
        val circDeferred = async { try { fetchCirculars() } catch (_: Exception) { null } }
        val timetableDeferred = async { try { fetchTimetable() } catch (_: Exception) { null } }

        // CA Marks → compact summary
        try {
            val caResult = caDeferred.await()
            if (caResult is Result.Success) {
                val summary = caResult.data.joinToString("; ") { course ->
                    // Read individual components (CT1, CT2, Assignment, etc.)
                    val componentMarks = course.testDetails.components.mapNotNull { comp ->
                        val marks = comp.marks
                        if (marks != null && !marks.actual.isNotEntered()) {
                            "${comp.name}: ${marks.actual.getSecuredDisplay()}/${marks.actual.getMaxAsDouble().toInt()}"
                        } else if (comp.hasSubComponent && comp.subComponents != null) {
                            // Check sub-components
                            val subs = comp.subComponents.mapNotNull { sub ->
                                val subMarks = sub.marks
                                if (subMarks != null && !subMarks.actual.isNotEntered()) {
                                    "${sub.name}: ${subMarks.actual.getSecuredDisplay()}/${subMarks.actual.getMaxAsDouble().toInt()}"
                                } else null
                            }
                            if (subs.isNotEmpty()) "${comp.name}(${subs.joinToString(", ")})" else null
                        } else null
                    }
                    val total = course.testDetails.total
                    val totalStr = if (!total.isNotEntered()) " Total: ${total.getSecuredDisplay()}/${total.getMaxAsDouble().toInt()}" else ""
                    if (componentMarks.isNotEmpty()) {
                        "${course.courseTitle}: ${componentMarks.joinToString(", ")}$totalStr"
                    } else {
                        "${course.courseTitle}: Not entered yet"
                    }
                }
                securePrefs.cachedCAMarksJson = summary
                android.util.Log.d("AttendanceRepo", "AI prefetch: CA marks cached (${caResult.data.size} subjects)")
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "AI prefetch CA marks failed: ${e.message}")
        }

        // Results → compact "Sem 4: DBMS=A(9), OS=B+(8), ... SGPA=8.2"
        try {
            val resultData = resultDeferred.await()
            if (resultData is Result.Success) {
                val entries = gson.fromJson(resultData.data, Array<com.example.attendancewidgetlaudea.data.model.GradeEntry>::class.java)
                if (entries != null && entries.isNotEmpty()) {
                    val bySem = entries.groupBy { it.semester }
                    val summary = bySem.entries.sortedBy { it.key }.joinToString(" | ") { (sem, grades) ->
                        val gradeStr = grades.take(6).joinToString(", ") { "${it.courseCode}=${it.letterGrade ?: "?"}" }
                        val sgpa = grades.filter { it.isPassed() }.let { passed ->
                            if (passed.isNotEmpty()) {
                                val totalCredits = passed.sumOf { it.getCreditsValue() }
                                val totalPoints = passed.sumOf { it.gradePoint * it.getCreditsValue() }
                                if (totalCredits > 0) "%.2f".format(totalPoints.toDouble() / totalCredits) else "?"
                            } else "?"
                        }
                        "Sem $sem: $gradeStr SGPA=$sgpa"
                    }
                    securePrefs.cachedResultsJson = summary
                    android.util.Log.d("AttendanceRepo", "AI prefetch: Results cached (${entries.size} entries)")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "AI prefetch results failed: ${e.message}")
        }

        // Subject attendance → compact "DBMS: 45/52 (86.5%), OS: 38/50 (76.0%), ..."
        try {
            val presentResult = presentDeferred.await()
            val absentResult = absentDeferred.await()
            if (presentResult is Result.Success && absentResult is Result.Success) {
                // Flatten sessions to get per-subject counts
                val presentSessions = presentResult.data.flatMap { it.sessions }
                val absentSessions = absentResult.data.flatMap { it.sessions }
                val presentBySubject = presentSessions.groupBy { it.courseTitle }
                val absentBySubject = absentSessions.groupBy { it.courseTitle }
                val allSubjects = (presentBySubject.keys + absentBySubject.keys).distinct()
                val summary = allSubjects.joinToString("; ") { subject ->
                    val present = presentBySubject[subject]?.size ?: 0
                    val absent = absentBySubject[subject]?.size ?: 0
                    val total = present + absent
                    val pct = if (total > 0) "%.1f".format(present.toDouble() / total * 100) else "0"
                    "$subject: $present/$total ($pct%)"
                }
                securePrefs.cachedSubjectAttendanceJson = summary
                android.util.Log.d("AttendanceRepo", "AI prefetch: Subject attendance cached (${allSubjects.size} subjects)")
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "AI prefetch subject attendance failed: ${e.message}")
        }

        // Circulars → latest 3 titles
        try {
            val circResult = circDeferred.await()
            if (circResult is Result.Success) {
                val circulars = circResult.data.records
                val summary = circulars.take(3).joinToString("; ") { it.title ?: "Untitled" }
                securePrefs.cachedCircularsSummary = summary
                android.util.Log.d("AttendanceRepo", "AI prefetch: Circulars cached (${circulars.size} total)")
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "AI prefetch circulars failed: ${e.message}")
        }

        // Timetable — await so it's cached for timetable screen
        try {
            val timetableResult = timetableDeferred.await()
            if (timetableResult is Result.Success) {
                securePrefs.cachedTimetableJson = com.google.gson.Gson().toJson(timetableResult.data)
                android.util.Log.d("AttendanceRepo", "AI prefetch: Timetable cached")
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceRepo", "AI prefetch timetable failed: ${e.message}")
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
