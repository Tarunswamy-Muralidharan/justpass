package com.example.attendancewidgetlaudea.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import com.example.attendancewidgetlaudea.data.model.ExamSeatData
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
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
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ExamSeatUiState(
    val isSignedIn: Boolean = false,
    val isSearching: Boolean = false,
    val examSeat: ExamSeatData? = null,
    val errorMessage: String? = null
)

class ExamSeatViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ExamSeatUiState())
    val uiState: StateFlow<ExamSeatUiState> = _uiState.asStateFlow()

    private val securePrefs = SecurePreferences.getInstance(application)

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/gmail.readonly"))
            .build()
        GoogleSignIn.getClient(getApplication(), gso)
    }

    companion object {
        private const val TAG = "ExamSeatVM"
        private const val GMAIL_BASE = "https://gmail.googleapis.com/gmail/v1/users/me"

        private val SEARCH_QUERIES = listOf(
            "subject:(seating arrangement) has:attachment newer_than:30d",
            "subject:(exam seat) has:attachment newer_than:30d",
            "subject:(hall ticket OR seat number) has:attachment newer_than:30d",
            "subject:(CA OR CAT) subject:(seat OR hall) has:attachment newer_than:30d",
            "(seating arrangement) has:attachment newer_than:60d"
        )

        private val HALL_HEADERS = listOf("hall", "room", "venue", "hall no", "hall name", "room no")
        private val SEAT_HEADERS = listOf("seat", "seat no", "seat number", "seat no.", "s.no", "bench")
    }

    init {
        val account = GoogleSignIn.getLastSignedInAccount(getApplication())
        _uiState.value = _uiState.value.copy(isSignedIn = account != null)
    }

    fun signIn(): Intent {
        return googleSignInClient.signInIntent
    }

    fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.result
            if (account != null) {
                _uiState.value = _uiState.value.copy(isSignedIn = true, errorMessage = null)
                Log.d(TAG, "Signed in as ${account.email}")
            } else {
                _uiState.value = _uiState.value.copy(
                    isSignedIn = false,
                    errorMessage = "Sign-in failed"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in error: ${e.message}")
            _uiState.value = _uiState.value.copy(
                isSignedIn = false,
                errorMessage = "Sign-in failed: ${e.message}"
            )
        }
    }

    fun signOut() {
        googleSignInClient.signOut().addOnCompleteListener {
            _uiState.value = ExamSeatUiState(isSignedIn = false)
        }
    }

    fun searchForSeat() {
        val rollNumber = securePrefs.rollNumber
        if (rollNumber.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Roll number not found. Please log in first.")
            return
        }

        val account = GoogleSignIn.getLastSignedInAccount(getApplication())
        if (account == null) {
            _uiState.value = _uiState.value.copy(
                isSignedIn = false,
                errorMessage = "Please sign in with Google first"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, errorMessage = null, examSeat = null)

            try {
                val accessToken = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        getApplication(),
                        account.account!!,
                        "oauth2:https://www.googleapis.com/auth/gmail.readonly"
                    )
                }

                val result = withContext(Dispatchers.IO) {
                    searchGmailForSeat(accessToken, rollNumber)
                }

                if (result != null) {
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        examSeat = result
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        errorMessage = "No seating arrangement found for $rollNumber in recent emails"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    errorMessage = "Search failed: ${e.message}"
                )
            }
        }
    }

    fun clearState() {
        _uiState.value = _uiState.value.copy(examSeat = null, errorMessage = null)
    }

    // --- Gmail API calls ---

    private fun searchGmailForSeat(accessToken: String, rollNumber: String): ExamSeatData? {
        for (query in SEARCH_QUERIES) {
            Log.d(TAG, "Trying query: $query")
            val messageIds = listMessages(accessToken, query)
            if (messageIds.isEmpty()) continue

            for (messageId in messageIds) {
                val result = processMessage(accessToken, messageId, rollNumber)
                if (result != null) return result
            }
        }
        return null
    }

    private fun listMessages(accessToken: String, query: String): List<String> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL("$GMAIL_BASE/messages?q=$encodedQuery&maxResults=10")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 15000
            readTimeout = 15000
        }

        return try {
            if (connection.responseCode != 200) {
                Log.w(TAG, "List messages failed: ${connection.responseCode}")
                return emptyList()
            }
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val messages = json.optJSONArray("messages") ?: return emptyList()

            (0 until messages.length()).map { messages.getJSONObject(it).getString("id") }
        } catch (e: Exception) {
            Log.e(TAG, "List messages error: ${e.message}")
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun processMessage(accessToken: String, messageId: String, rollNumber: String): ExamSeatData? {
        val url = URL("$GMAIL_BASE/messages/$messageId")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 15000
            readTimeout = 15000
        }

        return try {
            if (connection.responseCode != 200) return null
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)

            // Extract subject for exam name
            val headers = json.getJSONObject("payload").optJSONArray("headers")
            var subject = ""
            var date = ""
            if (headers != null) {
                for (i in 0 until headers.length()) {
                    val header = headers.getJSONObject(i)
                    when (header.getString("name").lowercase()) {
                        "subject" -> subject = header.getString("value")
                        "date" -> date = header.getString("value")
                    }
                }
            }

            // Find Excel attachments in message parts
            val parts = json.getJSONObject("payload").optJSONArray("parts") ?: return null
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val filename = part.optString("filename", "")
                val mimeType = part.optString("mimeType", "")

                if (isExcelFile(filename, mimeType)) {
                    val attachmentId = part.getJSONObject("body").optString("attachmentId", "")
                    if (attachmentId.isBlank()) continue

                    val attachmentBytes = downloadAttachment(accessToken, messageId, attachmentId)
                    if (attachmentBytes != null) {
                        val result = parseExcelForSeat(attachmentBytes, filename, rollNumber)
                        if (result != null) {
                            return result.copy(
                                examName = result.examName.ifBlank { subject },
                                date = result.date.ifBlank { date }
                            )
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Process message error: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadAttachment(accessToken: String, messageId: String, attachmentId: String): ByteArray? {
        val url = URL("$GMAIL_BASE/messages/$messageId/attachments/$attachmentId")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 15000
            readTimeout = 15000
        }

        return try {
            if (connection.responseCode != 200) return null
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val data = json.getString("data")
            // Gmail API returns URL-safe base64
            Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Download attachment error: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    // --- Excel parsing ---

    private fun parseExcelForSeat(bytes: ByteArray, filename: String, rollNumber: String): ExamSeatData? {
        var tempFile: File? = null
        return try {
            // Save to temp file for POI to read
            val extension = if (filename.endsWith(".xlsx", ignoreCase = true)) ".xlsx" else ".xls"
            tempFile = File.createTempFile("seat_", extension, getApplication<Application>().cacheDir)
            tempFile.writeBytes(bytes)

            val workbook: Workbook = if (extension == ".xlsx") {
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
        } finally {
            tempFile?.delete()
        }
    }

    private fun searchSheetForSeat(sheet: Sheet, rollNumber: String): ExamSeatData? {
        // First, find the header row to identify column indices
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

        // Search all rows for the roll number
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

                // If we couldn't identify columns by header, try to find them by position
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
        // Fallback: collect all non-empty cell values in the row (excluding the roll number itself)
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

    private fun isExcelFile(filename: String, mimeType: String): Boolean {
        val lowerName = filename.lowercase()
        return lowerName.endsWith(".xlsx") ||
                lowerName.endsWith(".xls") ||
                mimeType.contains("spreadsheet") ||
                mimeType.contains("excel") ||
                mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
                mimeType == "application/vnd.ms-excel"
    }
}
