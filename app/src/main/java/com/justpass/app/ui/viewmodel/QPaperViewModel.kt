package com.justpass.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.justpass.app.data.local.SecurePreferences
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
    val isReplacing: Boolean = false,
    val errorMessage: String? = null,
)

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

    val userRegulation: Regulation by lazy {
        getRegulationForBatch(securePrefs.batchYear)
    }

    val userDepartment: Department? by lazy {
        val raw = securePrefs.cachedDepartment ?: securePrefs.programmeName
        raw?.let { detectDepartment(it) }
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
        val mine = repo.listMyContributions().associateBy {
            Triple(it.subjectCode, it.category, it.examYear)
        }
        val gaps = mutableListOf<UploadIntent>()
        for (subj in subjects.take(8)) {
            for (cat in PaperCategory.all) {
                val key = Triple(subj.code, cat.key, justUploaded.examYear)
                if (key == Triple(justUploaded.subjectCode, justUploaded.category.key, justUploaded.examYear)) continue
                if (mine.containsKey(key)) continue
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

    fun approveSelected() {
        val paper = _adminState.value.selectedPaper ?: return
        viewModelScope.launch {
            val res = repo.approve(paper.id)
            if (res.isSuccess) {
                clearSelection()
                loadPending()
            } else {
                _adminState.value = _adminState.value.copy(
                    errorMessage = res.exceptionOrNull()?.message ?: "Approve failed",
                )
            }
        }
    }

    fun rejectSelected() {
        val paper = _adminState.value.selectedPaper ?: return
        viewModelScope.launch {
            val res = repo.reject(paper.id)
            if (res.isSuccess) {
                clearSelection()
                loadPending()
            } else {
                _adminState.value = _adminState.value.copy(
                    errorMessage = res.exceptionOrNull()?.message ?: "Reject failed",
                )
            }
        }
    }

    /**
     * Admin edit flow: upload a replacement PDF for the selected paper and
     * approve in one shot. UI calls this with the bytes from a file picker.
     */
    fun replaceAndApproveSelected(bytes: ByteArray) {
        val paper = _adminState.value.selectedPaper ?: return
        viewModelScope.launch {
            _adminState.value = _adminState.value.copy(isReplacing = true, errorMessage = null)
            val res = repo.replaceAndApprove(paper.id, bytes)
            if (res.isSuccess) {
                _adminState.value = _adminState.value.copy(isReplacing = false)
                clearSelection()
                loadPending()
            } else {
                _adminState.value = _adminState.value.copy(
                    isReplacing = false,
                    errorMessage = res.exceptionOrNull()?.message ?: "Replace failed",
                )
            }
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
