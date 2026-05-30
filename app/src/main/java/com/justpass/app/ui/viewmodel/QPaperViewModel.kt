package com.justpass.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.data.model.DeclineKind
import com.justpass.app.data.model.Department
import com.justpass.app.data.model.PaperCategory
import com.justpass.app.data.model.QPaper
import com.justpass.app.data.model.QPaperContributor
import com.justpass.app.data.model.Regulation
import com.justpass.app.data.model.UploadIntent
import com.justpass.app.data.model.detectDepartment
import com.justpass.app.data.model.getCurriculum
import com.justpass.app.data.model.getRegulationForBatch
import com.justpass.app.data.repository.QPaperRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class QPaperCategoryState(
    val isLoading: Boolean = false,
    val papersByCategory: Map<PaperCategory, List<QPaper>> = emptyMap(),
    val errorMessage: String? = null,
)

@Immutable
data class QPaperUploadState(
    val isUploading: Boolean = false,
    val isCheckingExisting: Boolean = false,
    val alreadyContributed: QPaperContributor? = null,
    val uploadedPaper: QPaper? = null,
    val errorMessage: String? = null,
    val gapsForFollowUp: List<UploadIntent> = emptyList(),
)

@Immutable
data class QPaperAdminState(
    val isLoading: Boolean = false,
    val pending: List<QPaper> = emptyList(),
    val selectedPaper: QPaper? = null,
    val selectedContributor: QPaperContributor? = null,
    val isDownloading: Boolean = false,
    val downloadMessage: String? = null,   // one-shot toast after a download
    val errorMessage: String? = null,
)

@Immutable
data class QPaperMyContribState(
    val isLoading: Boolean = false,
    val items: List<QPaperContributor> = emptyList(),
    val errorMessage: String? = null,
)

/** How a destination-picker session was started. */
enum class ReuploadMode {
    /** Clone an already-approved paper into another slot (new doc). */
    CLONE,
    /** Approve a pending paper by re-homing the SAME doc into the chosen slot. */
    PLACE,
}

@Immutable
data class QPaperHistoryState(
    val isLoading: Boolean = false,
    val processed: List<QPaper> = emptyList(),
    val errorMessage: String? = null,
)

@Immutable
data class QPaperReuploadState(
    val sourcePaper: QPaper? = null,
    val mode: ReuploadMode = ReuploadMode.CLONE,
    val isPreparing: Boolean = false,
    val bytes: ByteArray? = null,
    // Optional admin-edited replacement chosen during PLACE (download → edit
    // → publish). Null means publish the contributor's original file as-is.
    val editedBytes: ByteArray? = null,
    val isSubmitting: Boolean = false,
    val uploadedPaper: QPaper? = null,
    val errorMessage: String? = null,
) {
    // Custom equals to compare arrays by content rather than identity, so
    // recomposes don't keep firing while the same bytes are cached.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QPaperReuploadState) return false
        return sourcePaper == other.sourcePaper &&
            mode == other.mode &&
            isPreparing == other.isPreparing &&
            (bytes?.contentEquals(other.bytes ?: byteArrayOf()) ?: (other.bytes == null)) &&
            (editedBytes?.contentEquals(other.editedBytes ?: byteArrayOf()) ?: (other.editedBytes == null)) &&
            isSubmitting == other.isSubmitting &&
            uploadedPaper == other.uploadedPaper &&
            errorMessage == other.errorMessage
    }

    override fun hashCode(): Int {
        var result = sourcePaper?.hashCode() ?: 0
        result = 31 * result + mode.hashCode()
        result = 31 * result + isPreparing.hashCode()
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        result = 31 * result + (editedBytes?.contentHashCode() ?: 0)
        result = 31 * result + isSubmitting.hashCode()
        result = 31 * result + (uploadedPaper?.hashCode() ?: 0)
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        return result
    }
}

class QPaperViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = QPaperRepository.getInstance(application)
    private val securePrefs = SecurePreferences.getInstance(application)
    private val auth = FirebaseAuth.getInstance()

    private val _categoryState = MutableStateFlow(QPaperCategoryState())
    val categoryState: StateFlow<QPaperCategoryState> = _categoryState.asStateFlow()

    private val _uploadState = MutableStateFlow(QPaperUploadState())
    val uploadState: StateFlow<QPaperUploadState> = _uploadState.asStateFlow()

    private val _adminState = MutableStateFlow(QPaperAdminState())
    val adminState: StateFlow<QPaperAdminState> = _adminState.asStateFlow()

    private val _historyState = MutableStateFlow(QPaperHistoryState())
    val historyState: StateFlow<QPaperHistoryState> = _historyState.asStateFlow()

    private val _reuploadState = MutableStateFlow(QPaperReuploadState())
    val reuploadState: StateFlow<QPaperReuploadState> = _reuploadState.asStateFlow()

    private val _myContribState = MutableStateFlow(QPaperMyContribState())
    val myContribState: StateFlow<QPaperMyContribState> = _myContribState.asStateFlow()

    val userRegulation: Regulation by lazy {
        getRegulationForBatch(securePrefs.batchYear)
    }

    val userDepartment: Department? by lazy {
        val raw = securePrefs.cachedDepartment ?: securePrefs.programmeName
        raw?.let { detectDepartment(it) }
    }

    // Admin-only regulation override. When non-null the browse screens
    // (semester list, subject list, category detail) use this instead of
    // the user's own regulation, so an R2021 admin can still see and
    // verify R2025 papers and vice-versa.
    private val _browseRegulation = MutableStateFlow<Regulation?>(null)
    val browseRegulation: StateFlow<Regulation?> = _browseRegulation.asStateFlow()

    /** Effective regulation for browse: admin override OR user's own. */
    val effectiveRegulation: Regulation
        get() = _browseRegulation.value ?: userRegulation

    fun setBrowseRegulation(reg: Regulation) {
        _browseRegulation.value = reg
    }

    val isBatchEligible: Boolean
        get() = securePrefs.batchYear >= 2025

    val isStorageReady: Boolean get() = repo.isStorageConfigured

    fun loadCategoryDetail(department: String, subjectCode: String, regulation: String) {
        viewModelScope.launch {
            _categoryState.value = _categoryState.value.copy(isLoading = true, errorMessage = null)
            try {
                val byCat = repo.listApprovedForSubject(department, subjectCode, regulation)
                _categoryState.value = _categoryState.value.copy(
                    isLoading = false,
                    papersByCategory = byCat,
                )
            } catch (e: Exception) {
                _categoryState.value = _categoryState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load papers",
                )
            }
        }
    }

    fun checkAlreadyContributed(intent: UploadIntent) {
        viewModelScope.launch {
            _uploadState.value = _uploadState.value.copy(
                isCheckingExisting = true,
                alreadyContributed = null,
                uploadedPaper = null,
                errorMessage = null,
            )
            val existing = repo.findMyContributionForSlot(intent)
            _uploadState.value = _uploadState.value.copy(
                isCheckingExisting = false,
                alreadyContributed = existing,
            )
        }
    }

    fun upload(bytes: ByteArray, intent: UploadIntent) {
        viewModelScope.launch {
            _uploadState.value = _uploadState.value.copy(
                isUploading = true,
                errorMessage = null,
            )
            val result = repo.upload(bytes, intent)
            if (result.isSuccess) {
                val paper = result.getOrThrow()
                val gaps = computeGaps(intent)
                _uploadState.value = _uploadState.value.copy(
                    isUploading = false,
                    uploadedPaper = paper,
                    gapsForFollowUp = gaps,
                )
            } else {
                _uploadState.value = _uploadState.value.copy(
                    isUploading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Upload failed",
                )
            }
        }
    }

    fun resetUploadState() {
        _uploadState.value = QPaperUploadState()
    }

    /**
     * Build the "more to contribute" suggestion list shown after a successful
     * upload. For each subject in the user's current semester's curriculum,
     * offer the categories (CA1/CA2/Sem) they haven't contributed yet.
     * Excludes the just-uploaded slot.
     */
    private suspend fun computeGaps(justUploaded: UploadIntent): List<UploadIntent> {
        val dept = detectDepartment(justUploaded.department) ?: return emptyList()
        val regulation = Regulation.entries.firstOrNull { it.name == justUploaded.regulation }
            ?: return emptyList()
        val curr = getCurriculum(dept, regulation)
        val subjects = curr[justUploaded.semester] ?: return emptyList()
        // Include the just-uploaded slot in `mine` even if Firestore's
        // post-write read hasn't propagated yet — otherwise the gap list
        // re-suggests the slot we just filled.
        val justUploadedKey = Triple(
            justUploaded.subjectCode, justUploaded.category.key, justUploaded.examYear
        )
        val mine: Set<Triple<String, String, Int>> = repo.listMyContributions()
            .map { Triple(it.subjectCode, it.category, it.examYear) }
            .toMutableSet()
            .apply { add(justUploadedKey) }
        val gaps = mutableListOf<UploadIntent>()
        for (subj in subjects.take(8)) {
            for (cat in PaperCategory.all) {
                val key = Triple(subj.code, cat.key, justUploaded.examYear)
                if (key in mine) continue
                gaps.add(
                    UploadIntent(
                        department = justUploaded.department,
                        subjectCode = subj.code,
                        subjectName = subj.name,
                        semester = justUploaded.semester,
                        regulation = justUploaded.regulation,
                        category = cat,
                        examYear = justUploaded.examYear,
                    )
                )
                if (gaps.size >= 6) return gaps
            }
        }
        return gaps
    }

    // ─── admin ──────────────────────────────────────────────────────────

    fun loadPending() {
        viewModelScope.launch {
            _adminState.value = _adminState.value.copy(isLoading = true, errorMessage = null)
            val pending = repo.listPending()
            _adminState.value = _adminState.value.copy(
                isLoading = false,
                pending = pending,
            )
        }
    }

    /** Admin history: approved + rejected papers, newest first. */
    fun loadHistory() {
        viewModelScope.launch {
            _historyState.value = _historyState.value.copy(isLoading = true, errorMessage = null)
            val processed = repo.listProcessed()
            _historyState.value = _historyState.value.copy(
                isLoading = false,
                processed = processed,
            )
        }
    }

    fun openPaperForReview(paper: QPaper) {
        viewModelScope.launch {
            _adminState.value = _adminState.value.copy(
                selectedPaper = paper,
                selectedContributor = null,
            )
            val contributor = repo.getContributor(paper.id)
            _adminState.value = _adminState.value.copy(selectedContributor = contributor)
        }
    }

    fun clearSelection() {
        _adminState.value = _adminState.value.copy(
            selectedPaper = null,
            selectedContributor = null,
        )
    }

    /**
     * Decline the selected pending paper with a reason. [kind] picks the
     * canned message; for [DeclineKind.CUSTOM] the admin supplies [message].
     */
    fun declineSelected(kind: DeclineKind, message: String) {
        val paper = _adminState.value.selectedPaper ?: return
        val reason = if (kind == DeclineKind.CUSTOM) message.trim() else kind.contributorMessage
        viewModelScope.launch {
            val res = repo.decline(paper.id, kind, reason)
            if (res.isSuccess) {
                clearSelection()
                loadPending()
            } else {
                _adminState.value = _adminState.value.copy(
                    errorMessage = res.exceptionOrNull()?.message ?: "Decline failed",
                )
            }
        }
    }

    /** Admin: save the selected paper's PDF to Downloads to examine/edit. */
    fun downloadSelectedToDevice() {
        val paper = _adminState.value.selectedPaper ?: return
        if (_adminState.value.isDownloading) return
        viewModelScope.launch {
            _adminState.value = _adminState.value.copy(isDownloading = true, downloadMessage = null)
            val res = repo.downloadToDownloads(paper)
            _adminState.value = _adminState.value.copy(
                isDownloading = false,
                downloadMessage = if (res.isSuccess) {
                    "Saved to ${res.getOrNull()}"
                } else {
                    res.exceptionOrNull()?.message ?: "Download failed"
                },
            )
        }
    }

    fun consumeDownloadMessage() {
        if (_adminState.value.downloadMessage != null) {
            _adminState.value = _adminState.value.copy(downloadMessage = null)
        }
    }

    // ─── Re-upload elsewhere (admin) ────────────────────────────────────

    /**
     * Begin a re-upload session for [paper]. Downloads its bytes into the
     * VM so the destination picker can submit without re-hitting Cloudinary.
     */
    fun startReupload(paper: QPaper) {
        _reuploadState.value = QPaperReuploadState(
            sourcePaper = paper,
            mode = ReuploadMode.CLONE,
            isPreparing = true,
        )
        viewModelScope.launch {
            val res = repo.downloadPdfBytes(paper)
            _reuploadState.value = _reuploadState.value.copy(
                isPreparing = false,
                bytes = res.getOrNull(),
                errorMessage = if (res.isFailure) {
                    res.exceptionOrNull()?.message ?: "Failed to fetch source PDF"
                } else null,
            )
        }
    }

    /**
     * Begin an approve-and-place session for a PENDING paper. Unlike CLONE,
     * this re-homes the same doc, so we don't need to pre-fetch the source
     * bytes — the contributor's file is published as-is unless the admin
     * picks an edited replacement ([setEditedBytes]).
     */
    fun startPlacement(paper: QPaper) {
        _reuploadState.value = QPaperReuploadState(
            sourcePaper = paper,
            mode = ReuploadMode.PLACE,
            isPreparing = false,
        )
    }

    /** Admin picked an edited PDF to publish instead of the original. */
    fun setEditedBytes(bytes: ByteArray) {
        _reuploadState.value = _reuploadState.value.copy(editedBytes = bytes)
    }

    /**
     * Push cached bytes to a new (subject, semester, category, year) slot
     * and write Firestore with status = "approved" so it's immediately
     * browseable. No contributor doc is written — credit stays with the
     * original uploader of [sourcePaper].
     */
    fun submitReupload(intent: UploadIntent) {
        val state = _reuploadState.value
        _reuploadState.value = state.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val res = when (state.mode) {
                ReuploadMode.PLACE -> {
                    val paperId = state.sourcePaper?.id
                    if (paperId.isNullOrBlank()) {
                        Result.failure(IllegalStateException("Missing source paper"))
                    } else {
                        repo.approveIntoSlot(paperId, intent, state.editedBytes)
                            .map { state.sourcePaper.copy(status = "approved") }
                    }
                }
                ReuploadMode.CLONE -> {
                    val bytes = state.bytes
                    if (bytes == null) {
                        Result.failure(IllegalStateException("Source PDF not loaded"))
                    } else {
                        repo.uploadApproved(bytes, intent)
                    }
                }
            }
            if (res.isSuccess) {
                _reuploadState.value = _reuploadState.value.copy(
                    isSubmitting = false,
                    uploadedPaper = res.getOrNull(),
                )
                if (state.mode == ReuploadMode.PLACE) loadPending()
            } else {
                _reuploadState.value = _reuploadState.value.copy(
                    isSubmitting = false,
                    errorMessage = res.exceptionOrNull()?.message
                        ?: if (state.mode == ReuploadMode.PLACE) "Publish failed" else "Re-upload failed",
                )
            }
        }
    }

    fun clearReupload() {
        _reuploadState.value = QPaperReuploadState()
    }

    // ─── My Contributions (contributor-facing) ─────────────────────────

    fun loadMyContributions() {
        viewModelScope.launch {
            _myContribState.value = _myContribState.value.copy(isLoading = true, errorMessage = null)
            val items = repo.listMyContributions()
            _myContribState.value = QPaperMyContribState(isLoading = false, items = items)
        }
    }

    // ─── PDF download (for viewer) ─────────────────────────────────────

    suspend fun downloadPaperBytes(paper: QPaper): ByteArray? {
        val res = repo.downloadPdfBytes(paper)
        return if (res.isSuccess) {
            // Fire-and-forget view count bump
            viewModelScope.launch { repo.incrementViewCount(paper.id) }
            res.getOrNull()
        } else {
            null
        }
    }
}
