package com.justpass.app.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.justpass.app.data.model.Circular
import com.justpass.app.data.model.CircularAttachment
import com.justpass.app.data.model.CircularDetail
import com.justpass.app.data.repository.AttendanceRepository
import com.justpass.app.data.repository.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CircularsUiState(
    val isLoading: Boolean = false,
    val circulars: List<Circular> = emptyList(),
    val errorMessage: String? = null
)

data class PdfViewerState(
    val isLoading: Boolean = false,
    val circularDetail: CircularDetail? = null,
    val pdfPages: List<Bitmap> = emptyList(),
    val errorMessage: String? = null
)

class CircularViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AttendanceRepository.getInstance(application)

    private val _uiState = MutableStateFlow(CircularsUiState())
    val uiState: StateFlow<CircularsUiState> = _uiState.asStateFlow()

    private val _pdfState = MutableStateFlow(PdfViewerState())
    val pdfState: StateFlow<PdfViewerState> = _pdfState.asStateFlow()

    init {
        fetchCirculars()
    }

    fun fetchCirculars() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = repository.fetchCirculars()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        circulars = result.data.records
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

    fun loadCircularPdf(circularId: String) {
        viewModelScope.launch {
            _pdfState.value = PdfViewerState(isLoading = true)
            com.justpass.app.data.analytics.Analytics.logCircularViewed(circularId, null)

            // Fetch circular detail
            val detailResult = repository.fetchCircularDetail(circularId)
            if (detailResult !is Result.Success) {
                _pdfState.value = PdfViewerState(
                    errorMessage = (detailResult as? Result.Error)?.message ?: "Failed to load circular"
                )
                return@launch
            }

            val detail = detailResult.data
            _pdfState.value = _pdfState.value.copy(circularDetail = detail)

            // Find PDF attachment
            val pdfAttachment = detail.attachments.firstOrNull {
                it.contentType?.contains("pdf", ignoreCase = true) == true
            }

            if (pdfAttachment == null) {
                _pdfState.value = _pdfState.value.copy(
                    isLoading = false,
                    errorMessage = "No PDF attachment found"
                )
                return@launch
            }

            // Get signed URL
            val urlResult = repository.fetchCircularPdfUrl(pdfAttachment)
            if (urlResult !is Result.Success) {
                _pdfState.value = _pdfState.value.copy(
                    isLoading = false,
                    errorMessage = (urlResult as? Result.Error)?.message ?: "Failed to get PDF URL"
                )
                return@launch
            }

            // Download PDF bytes
            val pdfBytes = repository.downloadPdfBytes(urlResult.data)
            if (pdfBytes == null) {
                _pdfState.value = _pdfState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to download PDF"
                )
                return@launch
            }

            // Render PDF pages to bitmaps
            val pages = renderPdfToBitmaps(pdfBytes)
            _pdfState.value = _pdfState.value.copy(
                isLoading = false,
                pdfPages = pages,
                errorMessage = if (pages.isEmpty()) "Failed to render PDF" else null
            )
        }
    }

    fun clearPdfState() {
        _pdfState.value.pdfPages.forEach { it.recycle() }
        _pdfState.value = PdfViewerState()
    }

    private suspend fun renderPdfToBitmaps(pdfBytes: ByteArray): List<Bitmap> {
        return withContext(Dispatchers.IO) {
            val bitmaps = mutableListOf<Bitmap>()
            var tempFile: File? = null
            try {
                tempFile = File.createTempFile("circular_", ".pdf", getApplication<Application>().cacheDir)
                tempFile.writeBytes(pdfBytes)

                val fd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)

                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    // Render at 2x for crisp display
                    val scale = 2
                    val bitmap = Bitmap.createBitmap(
                        page.width * scale,
                        page.height * scale,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmaps.add(bitmap)
                }

                renderer.close()
                fd.close()
            } catch (e: Exception) {
                android.util.Log.e("CircularVM", "PDF render error: ${e.message}")
            } finally {
                tempFile?.delete()
            }
            bitmaps
        }
    }

    override fun onCleared() {
        super.onCleared()
        _pdfState.value.pdfPages.forEach { it.recycle() }
    }
}
