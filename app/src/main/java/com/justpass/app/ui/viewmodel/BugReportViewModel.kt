package com.justpass.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.data.model.BugReport
import com.justpass.app.data.model.TournamentAdmins
import com.justpass.app.data.repository.AttendanceRepository
import com.justpass.app.data.repository.BugReportRepository
import com.justpass.app.data.repository.ChessRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BugSubmitStep { IDLE, SUBMITTING, SUBMITTED, FAILED }

data class BugReportUiState(
    val isAdmin: Boolean = false,
    val reporterName: String = "",
    val reporterRoll: String = "",
    val reporterDept: String = "",
    val reporterPlayerId: String = "",

    // Form
    val title: String = "",
    val description: String = "",
    val imageUri: Uri? = null,
    val submitStep: BugSubmitStep = BugSubmitStep.IDLE,
    val errorMessage: String? = null,

    // Inbox
    val reports: List<BugReport> = emptyList(),
    // Reporter's own reports (for "My Reports" tab on BugReportScreen)
    val myReports: List<BugReport> = emptyList()
)

class BugReportViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = BugReportRepository(application)
    private val chessRepo = ChessRepository()
    private val attendanceRepo = AttendanceRepository(application)
    private val securePrefs = SecurePreferences.getInstance(application)

    private var inboxListener: ListenerRegistration? = null
    private var mineListener: ListenerRegistration? = null

    private val _uiState = MutableStateFlow(BugReportUiState())
    val uiState: StateFlow<BugReportUiState> = _uiState.asStateFlow()

    init { prefill() }

    private fun prefill() {
        viewModelScope.launch {
            val roll = securePrefs.rollNumber.orEmpty()
            val name = securePrefs.displayName.orEmpty()
            val pid = if (roll.isNotBlank()) chessRepo.getPlayerId(roll) else ""
            val dept = withContext(Dispatchers.IO) {
                runCatching { attendanceRepo.fetchStudentBiodata()?.department }.getOrNull().orEmpty()
            }
            _uiState.value = _uiState.value.copy(
                isAdmin = TournamentAdmins.isAdmin(pid),
                reporterName = name,
                reporterRoll = roll,
                reporterDept = dept,
                reporterPlayerId = pid
            )
        }
    }

    fun setTitle(v: String) { _uiState.value = _uiState.value.copy(title = v.take(80)) }
    fun setDescription(v: String) { _uiState.value = _uiState.value.copy(description = v.take(1000)) }
    fun setImage(uri: Uri?) { _uiState.value = _uiState.value.copy(imageUri = uri) }
    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }

    fun submit() {
        val s = _uiState.value
        if (s.title.isBlank() || s.description.isBlank()) {
            _uiState.value = s.copy(errorMessage = "Title + description are required")
            return
        }
        _uiState.value = s.copy(submitStep = BugSubmitStep.SUBMITTING, errorMessage = null)
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val appVersion = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
            } catch (_: Exception) { "?" }
            val report = BugReport(
                reporterPlayerId = s.reporterPlayerId,
                reporterName = s.reporterName,
                reporterRollNumber = s.reporterRoll,
                reporterDepartment = s.reporterDept,
                title = s.title.trim(),
                description = s.description.trim(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                osVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                appVersion = appVersion
            )
            val id = repo.submitReport(report, s.imageUri)
            _uiState.value = if (id != null) {
                _uiState.value.copy(submitStep = BugSubmitStep.SUBMITTED)
            } else {
                _uiState.value.copy(submitStep = BugSubmitStep.FAILED, errorMessage = "Failed to submit — please retry")
            }
        }
    }

    fun resetForm() {
        _uiState.value = _uiState.value.copy(
            title = "", description = "", imageUri = null,
            submitStep = BugSubmitStep.IDLE, errorMessage = null
        )
    }

    fun startListeningInbox() {
        if (!_uiState.value.isAdmin) return
        inboxListener?.remove()
        inboxListener = repo.listenAllReports { list ->
            _uiState.value = _uiState.value.copy(reports = list)
        }
    }

    fun stopListeningInbox() {
        inboxListener?.remove(); inboxListener = null
    }

    /** Reporter-side: listen to my own reports so admin replies appear live. */
    fun startListeningMine() {
        val pid = _uiState.value.reporterPlayerId
        if (pid.isBlank()) return
        mineListener?.remove()
        mineListener = repo.listenMyReports(pid) { list ->
            _uiState.value = _uiState.value.copy(myReports = list)
        }
    }

    fun stopListeningMine() {
        mineListener?.remove(); mineListener = null
    }

    fun setReportStatus(reportId: String, status: String, resolution: String) {
        viewModelScope.launch {
            repo.setStatus(reportId, status, resolution)
        }
    }

    fun replyToReport(reportId: String, message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            repo.setReply(reportId, message.trim().take(500))
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListeningInbox()
        stopListeningMine()
    }
}
