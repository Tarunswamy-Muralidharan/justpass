package com.justpass.app.ui.viewmodel

import android.app.Application
import android.app.Activity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.firestore.ListenerRegistration
import com.justpass.app.data.auth.PhoneAuthHelper
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.data.model.Tournament
import com.justpass.app.data.model.TournamentAdmins
import com.justpass.app.data.model.TournamentRequest
import com.justpass.app.data.repository.AttendanceRepository
import com.justpass.app.data.repository.ChessRepository
import com.justpass.app.data.repository.TournamentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OtpStep { IDLE, SENDING, OTP_SENT, VERIFYING, VERIFIED, FAILED, SUBMITTING, SUBMITTED }

data class TournamentUiState(
    val isAdmin: Boolean = false,
    val myName: String = "",
    val myRoll: String = "",
    val myDept: String = "",
    val myPlayerId: String = "",

    // Form
    val tournamentName: String = "",
    val format: String = "Blitz",
    val maxParticipants: Int = 16,
    val description: String = "",
    val phone: String = "",

    // OTP
    val otpStep: OtpStep = OtpStep.IDLE,
    val verificationId: String = "",
    val otpInput: String = "",
    val errorMessage: String? = null,

    // Admin
    val pendingRequests: List<TournamentRequest> = emptyList(),
    val activeTournaments: List<Tournament> = emptyList(),
    val myRequests: List<TournamentRequest> = emptyList()
)

class TournamentViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = TournamentRepository()
    private val chessRepo = ChessRepository()
    private val attendanceRepo = AttendanceRepository(application)
    private val securePrefs = SecurePreferences.getInstance(application)

    private var phoneAuth: PhoneAuthHelper? = null
    private var pendingListener: ListenerRegistration? = null

    private val _uiState = MutableStateFlow(TournamentUiState())
    val uiState: StateFlow<TournamentUiState> = _uiState.asStateFlow()

    init {
        prefillFromCachedProfile()
    }

    /** Load name + dept + roll + admin gate from cached SIS biodata. */
    private fun prefillFromCachedProfile() {
        viewModelScope.launch {
            val roll = securePrefs.rollNumber.orEmpty()
            val name = securePrefs.displayName.orEmpty()
            val pid = if (roll.isNotBlank()) chessRepo.getPlayerId(roll) else ""
            val dept = withContext(Dispatchers.IO) {
                runCatching { attendanceRepo.fetchStudentBiodata()?.department }.getOrNull().orEmpty()
            }
            _uiState.value = _uiState.value.copy(
                isAdmin = TournamentAdmins.isAdmin(pid),
                myName = name,
                myRoll = roll,
                myDept = dept,
                myPlayerId = pid
            )
        }
    }

    fun bindActivity(activity: Activity) {
        phoneAuth = PhoneAuthHelper(activity)
    }

    // ─── Form setters ──────────────────────────────────────────────────────
    fun setTournamentName(v: String) { _uiState.value = _uiState.value.copy(tournamentName = v.take(50)) }
    fun setFormat(v: String) { _uiState.value = _uiState.value.copy(format = v) }
    fun setMaxParticipants(v: Int) { _uiState.value = _uiState.value.copy(maxParticipants = v) }
    fun setDescription(v: String) { _uiState.value = _uiState.value.copy(description = v.take(200)) }
    fun setPhone(v: String) { _uiState.value = _uiState.value.copy(phone = v.take(15)) }
    fun setOtpInput(v: String) { _uiState.value = _uiState.value.copy(otpInput = v.filter { it.isDigit() }.take(6)) }
    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }

    // ─── OTP flow ──────────────────────────────────────────────────────────
    fun sendOtp() {
        val s = _uiState.value
        if (s.tournamentName.isBlank()) {
            _uiState.value = s.copy(errorMessage = "Enter a tournament name")
            return
        }
        // Normalise to E.164 — assume India if no country code.
        val rawPhone = s.phone.trim()
        val e164 = when {
            rawPhone.startsWith("+") -> rawPhone
            rawPhone.length == 10 && rawPhone.all { it.isDigit() } -> "+91$rawPhone"
            else -> ""
        }
        if (e164.isEmpty()) {
            _uiState.value = s.copy(errorMessage = "Invalid phone — use 10-digit IN number or +<cc><number>")
            return
        }
        val helper = phoneAuth ?: run {
            _uiState.value = s.copy(errorMessage = "OTP unavailable — please reopen this screen")
            return
        }
        _uiState.value = s.copy(otpStep = OtpStep.SENDING, errorMessage = null)
        helper.sendOtp(e164, object : PhoneAuthHelper.Callback {
            override fun onCodeSent(verificationId: String) {
                _uiState.value = _uiState.value.copy(
                    otpStep = OtpStep.OTP_SENT, verificationId = verificationId
                )
            }
            override fun onAutoVerified(credential: PhoneAuthCredential) {
                // Some Android versions auto-read the SMS — skip straight to submit
                _uiState.value = _uiState.value.copy(otpStep = OtpStep.VERIFIED)
                submitRequest()
            }
            override fun onError(message: String) {
                _uiState.value = _uiState.value.copy(otpStep = OtpStep.FAILED, errorMessage = message)
            }
        })
    }

    fun verifyOtp() {
        val s = _uiState.value
        if (s.otpInput.length < 6) {
            _uiState.value = s.copy(errorMessage = "Enter the 6-digit OTP")
            return
        }
        val helper = phoneAuth ?: return
        _uiState.value = s.copy(otpStep = OtpStep.VERIFYING, errorMessage = null)
        helper.verifyCode(s.verificationId, s.otpInput) { ok, err ->
            if (ok) {
                _uiState.value = _uiState.value.copy(otpStep = OtpStep.VERIFIED)
                submitRequest()
            } else {
                _uiState.value = _uiState.value.copy(
                    otpStep = OtpStep.OTP_SENT,
                    errorMessage = err ?: "Wrong OTP"
                )
            }
        }
    }

    private fun submitRequest() {
        val s = _uiState.value
        _uiState.value = s.copy(otpStep = OtpStep.SUBMITTING)
        viewModelScope.launch {
            val phoneE164 = if (s.phone.startsWith("+")) s.phone else "+91${s.phone}"
            val request = TournamentRequest(
                creatorPlayerId = s.myPlayerId,
                creatorName = s.myName,
                creatorRollNumber = s.myRoll,
                creatorDepartment = s.myDept,
                creatorPhone = phoneE164,
                tournamentName = s.tournamentName.trim(),
                format = s.format,
                maxParticipants = s.maxParticipants,
                description = s.description.trim()
            )
            val id = repo.submitRequest(request)
            _uiState.value = if (id != null) {
                _uiState.value.copy(otpStep = OtpStep.SUBMITTED)
            } else {
                _uiState.value.copy(otpStep = OtpStep.FAILED, errorMessage = "Failed to submit — please retry")
            }
        }
    }

    fun resetForm() {
        _uiState.value = _uiState.value.copy(
            tournamentName = "", description = "", phone = "",
            otpInput = "", verificationId = "",
            otpStep = OtpStep.IDLE, errorMessage = null
        )
    }

    // ─── Admin ─────────────────────────────────────────────────────────────
    fun startListeningPending() {
        if (!_uiState.value.isAdmin) return
        pendingListener?.remove()
        pendingListener = repo.listenPendingRequests { list ->
            _uiState.value = _uiState.value.copy(pendingRequests = list)
        }
    }

    fun stopListeningPending() {
        pendingListener?.remove(); pendingListener = null
    }

    fun approve(requestId: String) {
        viewModelScope.launch {
            val ok = repo.approveRequest(requestId)
            if (!ok) _uiState.value = _uiState.value.copy(errorMessage = "Approve failed")
        }
    }

    fun reject(requestId: String, reason: String) {
        viewModelScope.launch {
            val ok = repo.rejectRequest(requestId, reason)
            if (!ok) _uiState.value = _uiState.value.copy(errorMessage = "Reject failed")
        }
    }

    fun loadMyRequests() {
        val pid = _uiState.value.myPlayerId
        if (pid.isBlank()) return
        viewModelScope.launch {
            val mine = repo.getMyRequests(pid)
            _uiState.value = _uiState.value.copy(myRequests = mine)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListeningPending()
    }
}
