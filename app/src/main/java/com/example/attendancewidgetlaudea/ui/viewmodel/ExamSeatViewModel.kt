package com.example.attendancewidgetlaudea.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import com.example.attendancewidgetlaudea.data.model.ExamSeatData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream

data class ExamSeatUiState(
    val isSearching: Boolean = false,
    val examSeat: ExamSeatData? = null,
    val errorMessage: String? = null
)

class ExamSeatViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ExamSeatUiState())
    val uiState: StateFlow<ExamSeatUiState> = _uiState.asStateFlow()

    private val securePrefs = SecurePreferences.getInstance(application)

    companion object {
        private const val TAG = "ExamSeatVM"

        private val HALL_HEADERS = listOf("hall", "room", "venue", "hall no", "hall name", "room no")
        private val SEAT_HEADERS = listOf("seat", "seat no", "seat number", "seat no.", "s.no", "bench")
    }

    fun processFileUri(uri: Uri) {
        val rollNumber = securePrefs.rollNumber
        if (rollNumber.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Roll number not found. Please log in to the app first.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, errorMessage = null, examSeat = null)

            try {
                val result = withContext(Dispatchers.IO) {
                    val contentResolver = getApplication<Application>().contentResolver
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw Exception("Could not read file")

                    // Determine file type from URI or content type
                    val mimeType = contentResolver.getType(uri) ?: ""
                    val isXlsx = mimeType.contains("spreadsheetml") ||
                            uri.toString().endsWith(".xlsx", ignoreCase = true)

                    parseExcelForSeat(bytes, isXlsx, rollNumber)
                }

                if (result != null) {
                    _uiState.value = _uiState.value.copy(isSearching = false, examSeat = result)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        errorMessage = "Your roll number ($rollNumber) was not found in this Excel file"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "File processing error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    errorMessage = "Failed to read file: ${e.message}"
                )
            }
        }
    }

    fun clearState() {
        _uiState.value = ExamSeatUiState()
    }

    // --- Excel parsing ---

    private fun parseExcelForSeat(bytes: ByteArray, isXlsx: Boolean, rollNumber: String): ExamSeatData? {
        return try {
            val workbook: Workbook = if (isXlsx) {
                XSSFWorkbook(ByteArrayInputStream(bytes))
            } else {
                HSSFWorkbook(ByteArrayInputStream(bytes))
            }

            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)
                val result = searchSheetForSeat(sheet, rollNumber)
                if (result != null) {
                    workbook.close()
                    return result
                }
            }

            workbook.close()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Excel parse error: ${e.message}", e)
            null
        }
    }

    private fun searchSheetForSeat(sheet: Sheet, rollNumber: String): ExamSeatData? {
        var hallColIndex = -1
        var seatColIndex = -1
        var headerRowIndex = -1

        for (rowIndex in 0..minOf(10, sheet.lastRowNum)) {
            val row = sheet.getRow(rowIndex) ?: continue
            for (cellIndex in 0 until row.lastCellNum) {
                val cellValue = getCellStringValue(row, cellIndex).lowercase().trim()
                if (cellValue.isBlank()) continue

                if (HALL_HEADERS.any { cellValue.contains(it) }) {
                    hallColIndex = cellIndex
                    headerRowIndex = rowIndex
                }
                if (SEAT_HEADERS.any { cellValue.contains(it) }) {
                    seatColIndex = cellIndex
                    headerRowIndex = rowIndex
                }
            }
            if (hallColIndex >= 0 || seatColIndex >= 0) break
        }

        for (rowIndex in 0..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            if (rowIndex == headerRowIndex) continue

            var rollFound = false
            for (cellIndex in 0 until row.lastCellNum) {
                val cellValue = getCellStringValue(row, cellIndex).trim()
                if (cellValue.equals(rollNumber, ignoreCase = true)) {
                    rollFound = true
                    break
                }
            }

            if (rollFound) {
                val hall = if (hallColIndex >= 0) getCellStringValue(row, hallColIndex).trim() else ""
                val seat = if (seatColIndex >= 0) getCellStringValue(row, seatColIndex).trim() else ""

                if (hall.isBlank() && seat.isBlank()) {
                    return findSeatByProximity(row, rollNumber)
                }

                return ExamSeatData(
                    hall = hall.ifBlank { "N/A" },
                    seatNumber = seat.ifBlank { "N/A" }
                )
            }
        }
        return null
    }

    private fun findSeatByProximity(row: Row, rollNumber: String): ExamSeatData? {
        val values = mutableListOf<String>()
        for (cellIndex in 0 until row.lastCellNum) {
            val value = getCellStringValue(row, cellIndex).trim()
            if (value.isNotBlank() && !value.equals(rollNumber, ignoreCase = true)) {
                values.add(value)
            }
        }

        return if (values.size >= 2) {
            ExamSeatData(hall = values[0], seatNumber = values[1])
        } else if (values.size == 1) {
            ExamSeatData(hall = "N/A", seatNumber = values[0])
        } else {
            null
        }
    }

    private fun getCellStringValue(row: Row, cellIndex: Int): String {
        val cell = row.getCell(cellIndex) ?: return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                val num = cell.numericCellValue
                if (num == num.toLong().toDouble()) num.toLong().toString() else num.toString()
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> try {
                cell.stringCellValue
            } catch (e: Exception) {
                try { cell.numericCellValue.toString() } catch (e2: Exception) { "" }
            }
            else -> ""
        }
    }
}
