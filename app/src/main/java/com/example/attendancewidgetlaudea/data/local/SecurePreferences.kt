package com.example.attendancewidgetlaudea.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.attendancewidgetlaudea.data.model.AttendanceData

class SecurePreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        SECURE_PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val regularPrefs: SharedPreferences = context.getSharedPreferences(
        REGULAR_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // Secure storage for sensitive data
    var rollNumber: String?
        get() = securePrefs.getString(KEY_ROLL_NUMBER, null)
        set(value) = securePrefs.edit().putString(KEY_ROLL_NUMBER, value).apply()

    var password: String?
        get() = securePrefs.getString(KEY_PASSWORD, null)
        set(value) = securePrefs.edit().putString(KEY_PASSWORD, value).apply()

    private var _loggedIn: Boolean
        get() = securePrefs.getBoolean(KEY_LOGGED_IN, false)
        set(value) = securePrefs.edit().putBoolean(KEY_LOGGED_IN, value).apply()

    var accessToken: String?
        get() = securePrefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = securePrefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = securePrefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = securePrefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var tokenExpiryTime: Long
        get() = securePrefs.getLong(KEY_TOKEN_EXPIRY, 0L)
        set(value) = securePrefs.edit().putLong(KEY_TOKEN_EXPIRY, value).apply()

    var meetingsAccessToken: String?
        get() = securePrefs.getString(KEY_MEETINGS_ACCESS_TOKEN, null)
        set(value) = securePrefs.edit().putString(KEY_MEETINGS_ACCESS_TOKEN, value).apply()

    var timetableConfigId: String?
        get() = regularPrefs.getString(KEY_TIMETABLE_CONFIG_ID, null)
        set(value) = regularPrefs.edit().putString(KEY_TIMETABLE_CONFIG_ID, value).apply()

    var cachedTimetableJson: String?
        get() = regularPrefs.getString(KEY_CACHED_TIMETABLE, null)
        set(value) = regularPrefs.edit().putString(KEY_CACHED_TIMETABLE, value).apply()

    var displayName: String?
        get() = regularPrefs.getString(KEY_DISPLAY_NAME, null)
        set(value) = regularPrefs.edit().putString(KEY_DISPLAY_NAME, value).apply()

    var attendanceTarget: Int
        get() = regularPrefs.getInt(KEY_ATTENDANCE_TARGET, 75)
        set(value) = regularPrefs.edit().putInt(KEY_ATTENDANCE_TARGET, value).apply()

    var targetCgpa: Float
        get() = regularPrefs.getFloat(KEY_TARGET_CGPA, 0f)
        set(value) = regularPrefs.edit().putFloat(KEY_TARGET_CGPA, value).apply()

    var dismissedAnnouncementId: String?
        get() = regularPrefs.getString(KEY_DISMISSED_ANNOUNCEMENT, null)
        set(value) = regularPrefs.edit().putString(KEY_DISMISSED_ANNOUNCEMENT, value).apply()

    var chessBoardTheme: String
        get() = regularPrefs.getString(KEY_CHESS_BOARD_THEME, "CHESS_COM") ?: "CHESS_COM"
        set(value) = regularPrefs.edit().putString(KEY_CHESS_BOARD_THEME, value).apply()

    var programmeName: String?
        get() = regularPrefs.getString(KEY_PROGRAMME_NAME, null)
        set(value) = regularPrefs.edit().putString(KEY_PROGRAMME_NAME, value).apply()

    var batchYear: Int
        get() = regularPrefs.getInt(KEY_BATCH_YEAR, 0)
        set(value) = regularPrefs.edit().putInt(KEY_BATCH_YEAR, value).apply()

    var cachedProfilePicPath: String?
        get() = regularPrefs.getString(KEY_CACHED_PROFILE_PIC, null)
        set(value) = regularPrefs.edit().putString(KEY_CACHED_PROFILE_PIC, value).apply()

    var cachedCurrentSem: Int
        get() = regularPrefs.getInt(KEY_CACHED_CURRENT_SEM, 0)
        set(value) = regularPrefs.edit().putInt(KEY_CACHED_CURRENT_SEM, value).apply()

    var cachedSection: String?
        get() = regularPrefs.getString(KEY_CACHED_SECTION, null)
        set(value) = regularPrefs.edit().putString(KEY_CACHED_SECTION, value).apply()

    var cachedDepartment: String?
        get() = regularPrefs.getString(KEY_CACHED_DEPARTMENT, null)
        set(value) = regularPrefs.edit().putString(KEY_CACHED_DEPARTMENT, value).apply()

    // Regular storage for non-sensitive data (attendance cache)
    var cachedPresentCount: Int
        get() = regularPrefs.getInt(KEY_PRESENT_COUNT, 0)
        set(value) = regularPrefs.edit().putInt(KEY_PRESENT_COUNT, value).apply()

    var cachedPresentWithExemptionCount: Int
        get() = regularPrefs.getInt(KEY_PRESENT_WITH_EXEMPTION_COUNT, 0)
        set(value) = regularPrefs.edit().putInt(KEY_PRESENT_WITH_EXEMPTION_COUNT, value).apply()

    var cachedAbsentCount: Int
        get() = regularPrefs.getInt(KEY_ABSENT_COUNT, 0)
        set(value) = regularPrefs.edit().putInt(KEY_ABSENT_COUNT, value).apply()

    var cachedEnteredTillDate: Int
        get() = regularPrefs.getInt(KEY_ENTERED_TILL_DATE, 0)
        set(value) = regularPrefs.edit().putInt(KEY_ENTERED_TILL_DATE, value).apply()

    var cachedNotEnteredTillDate: Int
        get() = regularPrefs.getInt(KEY_NOT_ENTERED_TILL_DATE, 0)
        set(value) = regularPrefs.edit().putInt(KEY_NOT_ENTERED_TILL_DATE, value).apply()

    var cachedAttendancePercentage: Double
        get() = Double.fromBits(regularPrefs.getLong(KEY_ATTENDANCE_PERCENTAGE, 0.0.toBits()))
        set(value) = regularPrefs.edit().putLong(KEY_ATTENDANCE_PERCENTAGE, value.toBits()).apply()

    var cachedAttendanceWithExemption: Double
        get() = Double.fromBits(regularPrefs.getLong(KEY_ATTENDANCE_WITH_EXEMPTION, 0.0.toBits()))
        set(value) = regularPrefs.edit().putLong(KEY_ATTENDANCE_WITH_EXEMPTION, value.toBits()).apply()

    var cachedExemptionCount: Int
        get() = regularPrefs.getInt(KEY_EXEMPTION_COUNT, 0)
        set(value) = regularPrefs.edit().putInt(KEY_EXEMPTION_COUNT, value).apply()

    // ── AI-prefetched data (cached JSON for AI advisor context) ──
    var cachedCAMarksJson: String?
        get() = regularPrefs.getString(KEY_CACHED_CA_MARKS, null)
        set(value) = regularPrefs.edit().putString(KEY_CACHED_CA_MARKS, value).apply()

    var cachedResultsJson: String?
        get() = regularPrefs.getString(KEY_CACHED_RESULTS, null)
        set(value) = regularPrefs.edit().putString(KEY_CACHED_RESULTS, value).apply()

    var cachedSubjectAttendanceJson: String?
        get() = regularPrefs.getString(KEY_CACHED_SUBJECT_ATTENDANCE, null)
        set(value) = regularPrefs.edit().putString(KEY_CACHED_SUBJECT_ATTENDANCE, value).apply()

    var cachedCircularsSummary: String?
        get() = regularPrefs.getString(KEY_CACHED_CIRCULARS, null)
        set(value) = regularPrefs.edit().putString(KEY_CACHED_CIRCULARS, value).apply()

    var cachedPresentDaysJson: String?
        get() = regularPrefs.getString(KEY_CACHED_PRESENT_DAYS, null)
        set(value) = regularPrefs.edit().putString(KEY_CACHED_PRESENT_DAYS, value).apply()

    var cachedAbsentDaysJson: String?
        get() = regularPrefs.getString(KEY_CACHED_ABSENT_DAYS, null)
        set(value) = regularPrefs.edit().putString(KEY_CACHED_ABSENT_DAYS, value).apply()

    var cachedCourseMarksFullJson: String?
        get() = regularPrefs.getString(KEY_CACHED_COURSE_MARKS_FULL, null)
        set(value) = regularPrefs.edit().putString(KEY_CACHED_COURSE_MARKS_FULL, value).apply()

    var lastUpdatedTime: Long
        get() = regularPrefs.getLong(KEY_LAST_UPDATED, 0L)
        set(value) = regularPrefs.edit().putLong(KEY_LAST_UPDATED, value).apply()

    fun saveAttendanceData(data: AttendanceData) {
        cachedPresentCount = data.presentCount
        cachedPresentWithExemptionCount = data.presentWithExemptionCount
        cachedAbsentCount = data.absentCount
        cachedEnteredTillDate = data.enteredTillDate
        cachedNotEnteredTillDate = data.notEnteredTillDate
        cachedAttendancePercentage = data.attendancePercentage
        cachedAttendanceWithExemption = data.attendanceWithExemption
        cachedExemptionCount = data.exemptionCount
        lastUpdatedTime = data.lastUpdated
    }

    fun getAttendanceData(): AttendanceData {
        return AttendanceData(
            presentCount = cachedPresentCount,
            presentWithExemptionCount = cachedPresentWithExemptionCount,
            absentCount = cachedAbsentCount,
            enteredTillDate = cachedEnteredTillDate,
            notEnteredTillDate = cachedNotEnteredTillDate,
            attendancePercentage = cachedAttendancePercentage,
            attendanceWithExemption = cachedAttendanceWithExemption,
            exemptionCount = cachedExemptionCount,
            lastUpdated = lastUpdatedTime
        )
    }

    fun isLoggedIn(): Boolean {
        return _loggedIn && !rollNumber.isNullOrEmpty() && !password.isNullOrEmpty()
    }

    fun setLoggedIn(value: Boolean) {
        _loggedIn = value
    }

    fun isTokenExpired(): Boolean {
        val currentTime = System.currentTimeMillis()
        val bufferTime = 5 * 60 * 1000 // 5 minutes buffer
        return currentTime >= (tokenExpiryTime - bufferTime)
    }

    fun clearAll() {
        securePrefs.edit().clear().apply()
        regularPrefs.edit().clear().apply()
    }

    fun clearTokens() {
        securePrefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .apply()
    }

    companion object {
        private const val SECURE_PREFS_NAME = "laudea_secure_prefs"
        private const val REGULAR_PREFS_NAME = "laudea_prefs"

        private const val KEY_ROLL_NUMBER = "roll_number"
        private const val KEY_PASSWORD = "password"
        private const val KEY_LOGGED_IN = "logged_in"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"

        private const val KEY_PRESENT_COUNT = "present_count"
        private const val KEY_PRESENT_WITH_EXEMPTION_COUNT = "present_with_exemption_count"
        private const val KEY_ABSENT_COUNT = "absent_count"
        private const val KEY_ENTERED_TILL_DATE = "entered_till_date"
        private const val KEY_NOT_ENTERED_TILL_DATE = "not_entered_till_date"
        private const val KEY_ATTENDANCE_PERCENTAGE = "attendance_percentage"
        private const val KEY_ATTENDANCE_WITH_EXEMPTION = "attendance_with_exemption"
        private const val KEY_EXEMPTION_COUNT = "exemption_count"
        private const val KEY_LAST_UPDATED = "last_updated"
        private const val KEY_TIMETABLE_CONFIG_ID = "timetable_config_id"
        private const val KEY_CACHED_TIMETABLE = "cached_timetable"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_MEETINGS_ACCESS_TOKEN = "meetings_access_token"
        private const val KEY_ATTENDANCE_TARGET = "attendance_target"
        private const val KEY_PROGRAMME_NAME = "programme_name"
        private const val KEY_BATCH_YEAR = "batch_year"
        private const val KEY_CACHED_PROFILE_PIC = "cached_profile_pic"
        private const val KEY_CACHED_CURRENT_SEM = "cached_current_sem"
        private const val KEY_CACHED_SECTION = "cached_section"
        private const val KEY_CACHED_DEPARTMENT = "cached_department"
        private const val KEY_CACHED_CA_MARKS = "cached_ca_marks_json"
        private const val KEY_CACHED_RESULTS = "cached_results_json"
        private const val KEY_CACHED_SUBJECT_ATTENDANCE = "cached_subject_attendance_json"
        private const val KEY_CACHED_CIRCULARS = "cached_circulars_summary"
        private const val KEY_CACHED_PRESENT_DAYS = "cached_present_days_json"
        private const val KEY_CACHED_ABSENT_DAYS = "cached_absent_days_json"
        private const val KEY_CACHED_COURSE_MARKS_FULL = "cached_course_marks_full_json"
        private const val KEY_CHESS_BOARD_THEME = "chess_board_theme"
        private const val KEY_TARGET_CGPA = "target_cgpa"
        private const val KEY_DISMISSED_ANNOUNCEMENT = "dismissed_announcement_id"

        @Volatile
        private var instance: SecurePreferences? = null

        fun getInstance(context: Context): SecurePreferences {
            return instance ?: synchronized(this) {
                instance ?: SecurePreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
