package com.justpass.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.data.model.ExamSeatData
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ExamSeatUiState(
    val isSearching: Boolean = false,
    val examSeat: ExamSeatData? = null,
    val errorMessage: String? = null,
    val importTimestamp: String? = null,
    val fileUri: Uri? = null,
    val showRawData: Boolean = false,
    val allRows: List<List<String>> = emptyList()
)

class ExamSeatViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ExamSeatUiState())
    val uiState: StateFlow<ExamSeatUiState> = _uiState.asStateFlow()

    private val securePrefs = SecurePreferences.getInstance(application)

    companion object {
        private const val TAG = "ExamSeatVM"

        private val HALL_HEADERS = listOf("hall", "room", "venue", "hall no", "hall name", "room no")
        private val SEAT_HEADERS = listOf("seat", "seat no", "seat number", "seat no.", "s.no", "bench")

        // Standard college session timings (per PSG iTech exam circular)
        private val SESSION_TIMINGS = mapOf(
            "FN-I" to "8:45 AM - 10:30 AM",
            "FN-II" to "10:45 AM - 12:30 PM",
            "AN-I" to "12:45 PM - 2:30 PM",
            "AN-II" to "2:45 PM - 4:30 PM",
            "FN" to "8:45 AM - 12:30 PM",
            "AN" to "12:45 PM - 4:30 PM"
        )
    }

    fun processFileUri(uri: Uri) {
        val contentResolver = getApplication<Application>().contentResolver
        val mimeType = contentResolver.getType(uri) ?: ""
        val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } else null
        } ?: uri.lastPathSegment ?: ""
        Log.d(TAG, "File: $fileName, mimeType: $mimeType")

        processExcelSeat(uri, fileName, mimeType)
    }

    private fun processExcelSeat(uri: Uri, fileName: String, mimeType: String) {
        val rollNumber = securePrefs.rollNumber
        if (rollNumber.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Roll number not found. Please log in to the app first.")
            return
        }

        viewModelScope.launch {
            // Clear old data completely when importing a new file
            _uiState.value = ExamSeatUiState(isSearching = true)

            try {
                val result = withContext(Dispatchers.IO) {
                    val contentResolver = getApplication<Application>().contentResolver
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw Exception("Could not read file")

                    val isXlsx = mimeType.contains("spreadsheetml") ||
                            fileName.endsWith(".xlsx", ignoreCase = true)

                    val seatResult = parseExcelForSeat(bytes, isXlsx, rollNumber)
                    val allRows = parseAllRows(bytes, isXlsx)

                    val finalResult = if (seatResult != null) {
                        val examInfo = parseExamInfoFromFilename(fileName)
                        seatResult.copy(examName = examInfo.first, date = examInfo.second)
                    } else seatResult

                    Pair(finalResult, allRows)
                }

                val timestamp = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date())

                if (result.first != null) {
                    _uiState.value = ExamSeatUiState(
                        isSearching = false,
                        examSeat = result.first,
                        importTimestamp = timestamp,
                        fileUri = uri,
                        allRows = result.second
                    )
                } else {
                    _uiState.value = ExamSeatUiState(
                        isSearching = false,
                        errorMessage = "Your roll number ($rollNumber) was not found in this Excel file",
                        importTimestamp = timestamp,
                        fileUri = uri,
                        allRows = result.second
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excel processing error: ${e.message}", e)
                _uiState.value = ExamSeatUiState(
                    isSearching = false,
                    errorMessage = "Failed to read file: ${e.message}"
                )
            }
        }
    }

    /**
     * Parse exam date and session from filename.
     * Common patterns: "3 Yr 03-02 FN-II.xlsx", "2 Yr 04-01 AN-I.xls"
     * Returns Pair(examName, date+time string)
     */
    private fun parseExamInfoFromFilename(fileName: String): Pair<String, String> {
        val name = fileName.substringBeforeLast(".")  // strip extension

        // Extract session code (FN-I, FN-II, AN-I, AN-II, FN, AN)
        val sessionRegex = Regex("(FN-II|FN-I|AN-II|AN-I|FN|AN)", RegexOption.IGNORE_CASE)
        val sessionMatch = sessionRegex.find(name)
        val sessionCode = sessionMatch?.value?.uppercase() ?: ""
        val sessionTime = SESSION_TIMINGS[sessionCode] ?: ""

        // Extract date — pattern MM-DD or DD-MM in filename
        val dateRegex = Regex("(\\d{2})-(\\d{2})")
        val dateMatch = dateRegex.find(name)

        var dateStr = ""
        if (dateMatch != null) {
            val part1 = dateMatch.groupValues[1].toInt()
            val part2 = dateMatch.groupValues[2].toInt()

            // Determine if MM-DD or DD-MM: if part1 > 12, it's DD-MM
            val month: Int
            val day: Int
            if (part1 > 12) {
                day = part1; month = part2
            } else if (part2 > 12) {
                day = part2; month = part1
            } else {
                // Ambiguous — assume MM-DD (college convention from the email)
                month = part1; day = part2
            }

            val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val year = Calendar.getInstance().get(Calendar.YEAR)
            dateStr = "$day ${monthNames.getOrElse(month - 1) { "?" }} $year"
        }

        // Build exam name from session
        val examName = if (sessionCode.isNotBlank()) "Session: $sessionCode" else ""

        // Build combined date + time
        val dateTime = buildString {
            if (dateStr.isNotBlank()) append(dateStr)
            if (sessionTime.isNotBlank()) {
                if (isNotBlank()) append(" · ")
                append(sessionTime)
            }
        }

        Log.d(TAG, "Parsed exam info — name='$examName', dateTime='$dateTime' from '$fileName'")
        return Pair(examName, dateTime)
    }

    fun clearState() {
        _uiState.value = ExamSeatUiState()
    }

    fun toggleRawData() {
        _uiState.value = _uiState.value.copy(showRawData = !_uiState.value.showRawData)
    }

    // --- Excel parsing ---

    private fun parseAllRows(bytes: ByteArray, isXlsx: Boolean): List<List<String>> {
        return try {
            val workbook: Workbook = if (isXlsx) {
                XSSFWorkbook(ByteArrayInputStream(bytes))
            } else {
                HSSFWorkbook(ByteArrayInputStream(bytes))
            }
            val rows = mutableListOf<List<String>>()
            val sheet = workbook.getSheetAt(0)
            for (rowIndex in 0..minOf(100, sheet.lastRowNum)) {
                val row = sheet.getRow(rowIndex) ?: continue
                val cells = mutableListOf<String>()
                for (cellIndex in 0 until row.lastCellNum) {
                    cells.add(getCellStringValue(row, cellIndex).trim())
                }
                if (cells.any { it.isNotBlank() }) {
                    rows.add(cells)
                }
            }
            workbook.close()
            rows
        } catch (e: Exception) {
            Log.e(TAG, "parseAllRows error: ${e.message}", e)
            emptyList()
        }
    }

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

    private fun normalizeRollNumber(raw: String): String {
        // Strip spaces, dots, dashes, and invisible characters to get a canonical form
        return raw.trim().replace(Regex("[\\s.\\-\\u00A0\\u200B]+"), "").uppercase()
    }

    private fun searchSheetForSeat(sheet: Sheet, rollNumber: String): ExamSeatData? {
        val normalizedRoll = normalizeRollNumber(rollNumber)
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
                if (cellValue.isBlank()) continue
                val normalizedCell = normalizeRollNumber(cellValue)
                // Exact match after normalization, or cell contains the roll number
                if (normalizedCell == normalizedRoll || normalizedCell.contains(normalizedRoll)) {
                    rollFound = true
                    Log.d(TAG, "Found roll '$rollNumber' in row $rowIndex, cell $cellIndex (raw='$cellValue')")
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
        Log.d(TAG, "Roll '$rollNumber' (normalized='$normalizedRoll') not found in sheet '${sheet.sheetName}' (${sheet.lastRowNum + 1} rows)")
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
