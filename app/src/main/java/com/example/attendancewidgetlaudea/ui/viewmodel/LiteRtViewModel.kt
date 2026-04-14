package com.example.attendancewidgetlaudea.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import com.example.attendancewidgetlaudea.data.model.AiChatMessage
import com.example.attendancewidgetlaudea.data.model.NavAction
import com.example.attendancewidgetlaudea.data.model.toDayTimetables
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.ActivityManager
import android.content.Context
import com.example.attendancewidgetlaudea.service.ModelDownloadService
import java.io.File

data class LiteRtModelOption(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeMb: Int
)

enum class LiteRtState {
    NOT_DOWNLOADED, DOWNLOADING, LOADING, READY, ERROR
}

data class LiteRtUiState(
    val state: LiteRtState = LiteRtState.NOT_DOWNLOADED,
    val models: List<LiteRtModelOption> = emptyList(),
    val selectedModelId: String? = null,
    val messages: List<AiChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val errorMessage: String? = null,
    val downloadProgress: Int = 0, // 0-100
    val downloadedMb: Long = 0,
    val totalMb: Long = 0,
    val downloadSpeedKbps: Long = 0,
    val downloadEtaSeconds: Long = 0,
    val isHighEndDevice: Boolean = false,
    val useGpu: Boolean = false,
    val showGpuSuggestion: Boolean = false
)

class LiteRtViewModel(application: Application) : AndroidViewModel(application) {

    private val securePrefs = SecurePreferences.getInstance(application)
    val modelDir = File(application.filesDir, "litert_models")
    private val _uiState = MutableStateFlow(LiteRtUiState())
    val uiState: StateFlow<LiteRtUiState> = _uiState.asStateFlow()

    private var engine: Engine? = null
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null
    private val chatHistory = mutableListOf<AiChatMessage>()

    private val isHighEnd: Boolean

    init {
        modelDir.mkdirs()
        isHighEnd = detectHighEndDevice()
        _uiState.value = _uiState.value.copy(
            models = buildModelList(),
            selectedModelId = "gemma4-e2b",
            isHighEndDevice = isHighEnd
        )
        checkDownloadedModels()
        // Prefetch all tile data for AI context
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repo = com.example.attendancewidgetlaudea.data.repository.AttendanceRepository
                    .getInstance(getApplication())
                repo.prefetchForAI()
            } catch (_: Exception) {}
        }
        // Observe background download service progress
        viewModelScope.launch {
            ModelDownloadService.downloadState.collect { progress ->
                if (progress.isDownloading) {
                    _uiState.value = _uiState.value.copy(
                        state = LiteRtState.DOWNLOADING,
                        downloadProgress = progress.progress,
                        downloadedMb = progress.downloadedMb,
                        totalMb = progress.totalMb,
                        downloadSpeedKbps = progress.speedKbps,
                        downloadEtaSeconds = progress.etaSeconds
                    )
                } else if (progress.completed) {
                    ModelDownloadService.resetState()
                    val model = _uiState.value.models.firstOrNull { it.id == _uiState.value.selectedModelId }
                    if (model != null) {
                        val targetFile = File(modelDir, model.fileName)
                        if (targetFile.exists()) {
                            loadModel(targetFile.absolutePath)
                        }
                    }
                } else if (progress.error != null) {
                    ModelDownloadService.resetState()
                    _uiState.value = _uiState.value.copy(
                        state = LiteRtState.ERROR,
                        errorMessage = progress.error
                    )
                }
            }
        }
    }

    // Only models with verified .litertlm files on ungated HuggingFace repos
    private fun buildModelList(): List<LiteRtModelOption> = listOf(
        LiteRtModelOption(
            id = "gemma4-e2b",
            displayName = "Gemma 4 E2B",
            fileName = "gemma-4-E2B-it.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            sizeMb = 2464
        )
    )

    /** High-end = 8+ GB RAM and 6+ cores */
    private fun detectHighEndDevice(): Boolean {
        val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val cores = Runtime.getRuntime().availableProcessors()
        return totalRamGb >= 8.0 && cores >= 6
    }

    fun toggleGpu(enabled: Boolean) {
        if (!isHighEnd) return // locked for non-high-end
        _uiState.value = _uiState.value.copy(useGpu = enabled)
        // If model is loaded, reload with new backend
        if (_uiState.value.state == LiteRtState.READY) {
            val model = _uiState.value.models.firstOrNull { it.id == _uiState.value.selectedModelId } ?: return
            val targetFile = File(modelDir, model.fileName)
            if (targetFile.exists()) loadModel(targetFile.absolutePath)
        }
    }

    fun dismissGpuSuggestion() {
        _uiState.value = _uiState.value.copy(showGpuSuggestion = false)
    }

    private fun checkDownloadedModels() {
        val models = _uiState.value.models
        // Auto-select first downloaded model if any
        val downloaded = models.firstOrNull { File(modelDir, it.fileName).exists() }
        if (downloaded != null) {
            _uiState.value = _uiState.value.copy(selectedModelId = downloaded.id)
        }
    }

    fun downloadAllModels() {
        val models = _uiState.value.models
        if (models.isEmpty()) return
        val model = models.firstOrNull { !File(modelDir, it.fileName).exists() } ?: return
        val targetFile = File(modelDir, model.fileName)
        _uiState.value = _uiState.value.copy(state = LiteRtState.DOWNLOADING, downloadProgress = 0)
        ModelDownloadService.start(
            getApplication(),
            model.downloadUrl,
            targetFile.absolutePath,
            model.displayName
        )
    }

    fun selectModel(modelId: String) {
        _uiState.value = _uiState.value.copy(selectedModelId = modelId)
    }

    fun downloadAndLoad() {
        val model = _uiState.value.models.firstOrNull { it.id == _uiState.value.selectedModelId } ?: return
        val targetFile = File(modelDir, model.fileName)

        if (targetFile.exists()) {
            loadModel(targetFile.absolutePath)
            return
        }

        // Already downloading in background service
        if (ModelDownloadService.isDownloading) return

        _uiState.value = _uiState.value.copy(state = LiteRtState.DOWNLOADING, downloadProgress = 0)
        ModelDownloadService.start(
            getApplication(),
            model.downloadUrl,
            targetFile.absolutePath,
            model.displayName
        )
    }

    private fun loadModel(modelPath: String) {
        _uiState.value = _uiState.value.copy(state = LiteRtState.LOADING)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Close previous engine
                conversation?.close()
                engine?.close()

                val backend = if (_uiState.value.useGpu) Backend.GPU() else Backend.CPU()
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    cacheDir = getApplication<Application>().cacheDir.path
                )
                val eng = Engine(config)
                eng.initialize()
                engine = eng

                val systemPrompt = buildSystemPrompt()
                val convConfig = ConversationConfig(
                    systemInstruction = com.google.ai.edge.litertlm.Contents.of(systemPrompt),
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.5)
                )
                conversation = eng.createConversation(convConfig)

                withContext(Dispatchers.Main) {
                    chatHistory.clear()
                    _uiState.value = _uiState.value.copy(
                        state = LiteRtState.READY,
                        messages = listOf(
                            AiChatMessage(
                                role = "assistant",
                                content = "Hey! Ask me about your attendance, marks, or anything academic!"
                            )
                        ),
                        // Show GPU suggestion on first load for high-end devices using CPU
                        showGpuSuggestion = isHighEnd && !_uiState.value.useGpu
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        state = LiteRtState.ERROR,
                        errorMessage = "Failed to load: ${e.message}"
                    )
                }
            }
        }
    }

    // ── Intent detection + subject extraction + nav action routing ──
    private enum class QueryIntent { GREETING, BUNK, ATTENDANCE, MARKS, MARKS_NEEDED, SYLLABUS, GENERAL }

    /** Detect which app screen to suggest opening based on the user's question */
    private fun detectNavAction(question: String): NavAction? {
        val q = question.lowercase()
        return when {
            // Specific screen requests
            q.let { it.contains("subject") && (it.contains("attendance") || it.contains("wise")) } -> NavAction.SUBJECT_ATTENDANCE
            q.let { it.contains("absent") && (it.contains("day") || it.contains("list") || it.contains("show") || it.contains("when")) } -> NavAction.ABSENT_DAYS
            q.let { it.contains("exemption") || it.contains("exempt") } -> NavAction.EXEMPTIONS
            q.let { (it.contains("ca") || it.contains("internal")) && (it.contains("mark") || it.contains("score")) } -> NavAction.CA_MARKS
            q.let { it.contains("result") || (it.contains("semester") && (it.contains("grade") || it.contains("mark"))) } -> NavAction.RESULTS
            q.let { (it.contains("cgpa") || it.contains("sgpa") || it.contains("gpa")) && (it.contains("calc") || it.contains("what if") || it.contains("predict")) } -> NavAction.GPA_CALCULATOR
            q.let { it.contains("timetable") || it.contains("schedule") || it.contains("class") && (it.contains("today") || it.contains("tomorrow") || it.contains("when")) } -> NavAction.TIMETABLE
            q.let { it.contains("calendar") || it.contains("holiday") || it.contains("event") } -> NavAction.CALENDAR
            q.let { it.contains("circular") || it.contains("notice") || it.contains("announcement") } -> NavAction.CIRCULARS
            q.let { it.contains("syllabus") || it.contains("curriculum") || (it.contains("unit") && it.contains("topic")) } -> NavAction.SYLLABUS
            // Broader intent matches
            q.let { it.contains("mark") || it.contains("grade") || it.contains("score") } -> NavAction.CA_MARKS
            q.let { it.contains("attendance") || it.contains("present") || it.contains("percent") } -> NavAction.SUBJECT_ATTENDANCE
            q.let { it.contains("bunk") || it.contains("skip") || it.contains("leave") } -> NavAction.SUBJECT_ATTENDANCE
            else -> null
        }
    }

    private fun detectIntent(question: String): QueryIntent {
        val q = question.lowercase().trim()
        return when {
            q.matches(Regex("^(hi|hey|hello|sup|yo|what'?s ?up|good ?(morning|evening|night|afternoon)|howdy|greetings)[!?.,:;\\s]*$")) -> QueryIntent.GREETING
            q.let { it.contains("bunk") || it.contains("skip") || it.contains("miss") || it.contains("leave") || it.contains("safe") || it.contains("tomorrow") || it.contains("today") } -> QueryIntent.BUNK
            q.let { (it.contains("marks needed") || it.contains("how much") || it.contains("need to score") || it.contains("need to get") || it.contains("target marks") || it.contains("ca marks needed") || it.contains("should i get") || it.contains("must i score") || it.contains("required marks")) && (it.contains("internal") || it.contains("iat") || it.contains("test") || it.contains("next") || it.contains("score") || it.contains("get") || it.contains("mark") || it.contains("need")) } -> QueryIntent.MARKS_NEEDED
            q.let { it.contains("mark") || it.contains("grade") || it.contains("score") || it.contains("ct") || it.contains("cgpa") || it.contains("gpa") || it.contains("test") || it.contains("internal") || it.contains("subject wise") || it.contains("subject-wise") || it.contains("how much") || it.contains("need to score") || it.contains("semester") } -> QueryIntent.MARKS
            q.let { it.contains("attendance") || it.contains("present") || it.contains("absent") || it.contains("percent") } -> QueryIntent.ATTENDANCE
            q.let { it.contains("unit") || it.contains("topic") || it.contains("syllabus") } -> QueryIntent.SYLLABUS
            else -> QueryIntent.GENERAL
        }
    }

    /** Extract subject name from query by fuzzy-matching against cached subject names */
    private fun extractSubject(query: String): String? {
        val q = query.lowercase()
        // Build subject list from cached data (works for any department)
        val subjects = mutableSetOf<String>()
        securePrefs.cachedSubjectAttendanceJson?.split(";")?.forEach { entry ->
            val name = entry.substringBefore(":").trim()
            if (name.isNotBlank()) subjects.add(name)
        }
        securePrefs.cachedCAMarksJson?.split(";")?.forEach { entry ->
            val name = entry.substringBefore(":").trim()
            if (name.isNotBlank()) subjects.add(name)
        }
        // Match: full name, or any word (3+ chars) from subject name
        for (subject in subjects) {
            if (q.contains(subject.lowercase())) return subject
            // Try matching significant words (e.g. "cloud" matches "CLOUD COMPUTING")
            val words = subject.lowercase().split(" ").filter { it.length >= 3 }
            if (words.any { word -> q.contains(word) && word !in setOf("and", "the", "for") }) return subject
        }
        return null
    }

    /** Filter semicolon-delimited cached data to just the matched subject */
    private fun filterToSubject(data: String?, subject: String): String? {
        if (data.isNullOrBlank()) return null
        val subjectLower = subject.lowercase()
        val match = data.split(";").firstOrNull { it.lowercase().contains(subjectLower) }?.trim()
        return match
    }

    /** Get today's/tomorrow's timetable as a DayTimetable object for calculations */
    private fun getTimetableForDayDetailed(dayOffset: Int = 0): com.example.attendancewidgetlaudea.data.model.DayTimetable? {
        val timetableJson = securePrefs.cachedTimetableJson ?: return null
        return try {
            val gson = com.google.gson.Gson()
            val response = gson.fromJson(timetableJson, com.example.attendancewidgetlaudea.data.model.TimetableResponse::class.java)
            val days = response.toDayTimetables()
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
            val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
            val dayNum = dayOfWeek - 1
            if (dayNum < 1 || dayNum > days.size) return null
            days.firstOrNull { it.dayNumber == dayNum }
        } catch (_: Exception) { null }
    }

    /** Get today's/tomorrow's classes from cached timetable */
    private fun getTimetableForDay(dayOffset: Int = 0): String? {
        val timetableJson = securePrefs.cachedTimetableJson ?: return null
        return try {
            val gson = com.google.gson.Gson()
            val response = gson.fromJson(timetableJson, com.example.attendancewidgetlaudea.data.model.TimetableResponse::class.java)
            val days = response.toDayTimetables()
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
            val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) // Sun=1, Mon=2...Sat=7
            val dayNum = dayOfWeek - 1 // Mon=1...Sat=6, Sun=0
            if (dayNum < 1 || dayNum > days.size) return if (dayOffset == 0) "No classes today (weekend)" else "No classes tomorrow (weekend)"
            val day = days.firstOrNull { it.dayNumber == dayNum } ?: return null
            val dayLabel = if (dayOffset == 0) "Today" else "Tomorrow"
            "$dayLabel (${day.dayName}): " + day.sessions.joinToString(", ") { "${it.courseTitle} ${it.startTime}-${it.endTime}" }
        } catch (_: Exception) { null }
    }

    private fun buildDynamicContext(userMessage: String): String {
        val name = securePrefs.displayName?.split(" ")?.firstOrNull() ?: "Student"
        val intent = detectIntent(userMessage)
        val subject = extractSubject(userMessage)
        val context = when (intent) {
            QueryIntent.GREETING -> "[Student: $name | GREETING — respond warmly, use their name, and briefly mention you can help with attendance, marks, bunk calculations, timetable, GPA, syllabus, and more.]"
            QueryIntent.MARKS_NEEDED -> {
                buildMarksNeededContext(name, userMessage, subject)
            }
            QueryIntent.BUNK -> {
                buildBunkContext(name, userMessage, subject)
            }
            QueryIntent.ATTENDANCE -> {
                buildAttendanceContext(name, userMessage, subject)
            }
            QueryIntent.MARKS -> {
                val sb = StringBuilder("[Student: $name")
                if (subject != null) {
                    // Only inject the requested subject's marks
                    val subjectCA = filterToSubject(securePrefs.cachedCAMarksJson, subject)
                    val subjectResult = filterToSubject(securePrefs.cachedResultsJson, subject)
                    if (subjectCA != null) sb.append(" | CA: $subjectCA")
                    if (subjectResult != null) sb.append(" | Result: $subjectResult")
                    if (subjectCA == null && subjectResult == null) sb.append(" | No data for $subject")
                } else {
                    val caMarks = securePrefs.cachedCAMarksJson
                    val results = securePrefs.cachedResultsJson
                    if (!caMarks.isNullOrBlank()) sb.append(" | CA Marks: $caMarks")
                    if (!results.isNullOrBlank()) sb.append(" | Past Results: $results")
                    if (caMarks.isNullOrBlank() && results.isNullOrBlank()) sb.append(" | CA marks max is typically 40-50. For 8+ CGPA aim for 35+ each.")
                }
                sb.append("]")
                sb.toString()
            }
            QueryIntent.SYLLABUS -> "[Student: $name | Give general study advice.]"
            QueryIntent.GENERAL -> {
                val pct = securePrefs.cachedAttendanceWithExemption
                "[Student: $name | Attendance: ${"%.1f".format(pct)}%]"
            }
        }
        return "$context\n$userMessage"
    }

    /** Compute bunk impact with exact numbers like the leave calculator */
    private fun buildBunkContext(name: String, userMessage: String, subject: String?): String {
        val present = securePrefs.cachedPresentCount
        val absent = securePrefs.cachedAbsentCount
        val exempt = securePrefs.cachedExemptionCount
        val total = present + absent
        val target = securePrefs.attendanceTarget
        val effective = present + exempt // with exemption
        val pctWithExempt = if (total > 0) effective.toDouble() / total * 100 else 0.0
        val pctWithout = if (total > 0) present.toDouble() / total * 100 else 0.0

        // How many total hours can skip while staying above target
        var canSkip = 0
        if (total > 0) while (canSkip < 200) {
            if (effective.toDouble() / (total + canSkip + 1) * 100 < target) break
            canSkip++
        }

        // Per-hour drop calculation
        val dropPerHour = if (total > 0) pctWithExempt - (effective.toDouble() / (total + 1) * 100) else 0.0

        val sb = StringBuilder()
        sb.append("[DATA FOR ANSWERING — use these exact numbers:\n")
        sb.append("Student: $name\n")
        sb.append("Attendance WITH exemptions: ${"%.1f".format(pctWithExempt)}% ($effective present+exempt out of $total total)\n")
        sb.append("Attendance WITHOUT exemptions: ${"%.1f".format(pctWithout)}% ($present present out of $total total)\n")
        sb.append("Exemptions: $exempt hours | Absent: $absent hours\n")
        sb.append("Target: $target%\n")
        sb.append("Can skip: $canSkip more hours total and stay above $target%\n")
        sb.append("Each hour bunked drops attendance by ~${"%.2f".format(dropPerHour)}%\n")

        // Determine which day they're asking about
        val q = userMessage.lowercase()
        val dayOffset = if (q.contains("tomorrow")) 1 else 0
        val dayLabel = if (dayOffset == 1) "tomorrow" else "today"

        val timetableInfo = getTimetableForDayDetailed(dayOffset)
        if (timetableInfo != null) {
            val classCount = timetableInfo.sessions.size
            val classList = timetableInfo.sessions.joinToString(", ") { it.courseTitle }

            // With exemption calc
            val newTotal = total + classCount
            val newPctWithExempt = if (newTotal > 0) effective.toDouble() / newTotal * 100 else 0.0
            val dropWithExempt = pctWithExempt - newPctWithExempt

            // Without exemption calc
            val newPctWithout = if (newTotal > 0) present.toDouble() / newTotal * 100 else 0.0
            val dropWithout = pctWithout - newPctWithout

            val safe = newPctWithExempt >= target

            sb.append("\n$dayLabel's classes (${timetableInfo.dayName}): $classCount hours — $classList\n")
            sb.append("IF BUNK FULL DAY ($classCount hrs):\n")
            sb.append("  With exemptions: ${"%.1f".format(pctWithExempt)}% → ${"%.1f".format(newPctWithExempt)}% (drops ${"%.1f".format(dropWithExempt)}%)\n")
            sb.append("  Without exemptions: ${"%.1f".format(pctWithout)}% → ${"%.1f".format(newPctWithout)}% (drops ${"%.1f".format(dropWithout)}%)\n")
            sb.append("  Verdict: ${if (safe) "SAFE — still above $target%" else "DANGER — falls below $target%!"}\n")

            if (canSkip < classCount) {
                sb.append("  WARNING: Can only safely skip $canSkip of $classCount classes\n")
            }
        } else {
            sb.append("\nNo classes $dayLabel (weekend)\n")
        }

        if (subject != null) {
            val subjectAtt = filterToSubject(securePrefs.cachedSubjectAttendanceJson, subject)
            if (subjectAtt != null) sb.append("Subject detail: $subjectAtt\n")
        }

        sb.append("]")
        return sb.toString()
    }

    /** Rich attendance context with exemption breakdown */
    private fun buildAttendanceContext(name: String, userMessage: String, subject: String?): String {
        val present = securePrefs.cachedPresentCount
        val absent = securePrefs.cachedAbsentCount
        val exempt = securePrefs.cachedExemptionCount
        val total = present + absent
        val target = securePrefs.attendanceTarget
        val effective = present + exempt
        val pctWithExempt = if (total > 0) effective.toDouble() / total * 100 else 0.0
        val pctWithout = if (total > 0) present.toDouble() / total * 100 else 0.0

        var canSkip = 0
        if (total > 0) while (canSkip < 200) {
            if (effective.toDouble() / (total + canSkip + 1) * 100 < target) break
            canSkip++
        }

        val dropPerHour = if (total > 0) pctWithExempt - (effective.toDouble() / (total + 1) * 100) else 0.0

        val sb = StringBuilder()
        sb.append("[DATA FOR ANSWERING — use these exact numbers:\n")
        sb.append("Student: $name\n")
        sb.append("Present: $present hrs | Absent: $absent hrs | Exemptions: $exempt hrs | Total: $total hrs\n")
        sb.append("Attendance WITH exemptions: ${"%.1f".format(pctWithExempt)}% (counts $effective/$total)\n")
        sb.append("Attendance WITHOUT exemptions: ${"%.1f".format(pctWithout)}% (counts $present/$total)\n")
        sb.append("Exemption boost: ${"%.1f".format(pctWithExempt - pctWithout)}% (exemptions add ${"%.1f".format(pctWithExempt - pctWithout)}% to your attendance)\n")
        sb.append("Target: $target%\n")
        sb.append("Can skip: $canSkip more hours and stay above $target%\n")
        sb.append("Each hour bunked drops ~${"%.2f".format(dropPerHour)}%\n")

        if (subject != null) {
            val subjectAtt = filterToSubject(securePrefs.cachedSubjectAttendanceJson, subject)
            if (subjectAtt != null) sb.append("Subject: $subjectAtt\n")
        } else {
            val subjectAtt = securePrefs.cachedSubjectAttendanceJson
            if (!subjectAtt.isNullOrBlank()) sb.append("All subjects: $subjectAtt\n")
        }
        sb.append("]")
        return sb.toString()
    }

    /** Build context for "how much should I score in next internals" questions */
    private fun buildMarksNeededContext(name: String, userMessage: String, subject: String?): String {
        val targetCgpa = securePrefs.targetCgpa
        val sb = StringBuilder()
        sb.append("[DATA FOR ANSWERING — use these exact numbers:\n")
        sb.append("Student: $name\n")
        sb.append("Target CGPA: ${"%.2f".format(targetCgpa)}\n")

        if (subject != null) {
            val subjectCA = filterToSubject(securePrefs.cachedCAMarksJson, subject)
            val subjectResult = filterToSubject(securePrefs.cachedResultsJson, subject)
            if (subjectCA != null) sb.append("Current CA marks for $subject: $subjectCA\n")
            if (subjectResult != null) sb.append("Past result for $subject: $subjectResult\n")
            if (subjectCA == null && subjectResult == null) sb.append("No marks data found for $subject\n")
        } else {
            val caMarks = securePrefs.cachedCAMarksJson
            if (!caMarks.isNullOrBlank()) sb.append("All CA marks: $caMarks\n")
            else sb.append("No CA marks cached yet\n")
        }

        sb.append("\nINSTRUCTIONS: Based on the current CA marks and target CGPA, calculate and tell the student how much they need to score in their next internals/IAT. CA marks typically have max 40-50 total across all tests. Use the numbers above to give specific advice.\n")
        sb.append("]")
        return sb.toString()
    }

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank() || _uiState.value.isGenerating) return
        val conv = conversation ?: return

        // Detect nav action from user's question (before sending to model)
        val navAction = detectNavAction(userMessage)

        val userMsg = AiChatMessage(role = "user", content = userMessage)
        chatHistory.add(userMsg)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg,
            isGenerating = true,
            streamingText = ""
        )

        val enrichedMessage = buildDynamicContext(userMessage)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val responseBuilder = StringBuilder()
                conv.sendMessageAsync(
                        enrichedMessage,
                        extraContext = mapOf("enable_thinking" to false)
                    )
                    .catch { e ->
                        withContext(Dispatchers.Main) {
                            val errorMsg = AiChatMessage(role = "assistant", content = "Error: ${e.message}")
                            _uiState.value = _uiState.value.copy(
                                messages = _uiState.value.messages + errorMsg,
                                isGenerating = false,
                                streamingText = ""
                            )
                        }
                    }
                    .collect { message ->
                        val token = message.toString()
                        responseBuilder.append(token)
                        val raw = responseBuilder.toString()
                        // During streaming, hide everything if still inside <think> block
                        val cleaned = if (raw.contains("<think>") && !raw.contains("</think>")) {
                            "" // Still thinking — show nothing
                        } else {
                            raw.replace(Regex("<think>[\\s\\S]*?</think>"), "")
                                .replace("<|im_end|>", "")
                                .replace("<|im_start|>", "")
                                .trim()
                        }
                        if (cleaned.isNotBlank()) {
                            withContext(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(streamingText = cleaned)
                            }
                        }
                    }

                val finalText = responseBuilder.toString()
                    .replace(Regex("<think>[\\s\\S]*?</think>"), "")
                    .replace("<|im_end|>", "")
                    .replace("<|im_start|>", "")
                    .trim()
                    .ifBlank { "I couldn't generate a response." }

                val assistantMsg = AiChatMessage(role = "assistant", content = finalText, navAction = navAction)
                chatHistory.add(assistantMsg)

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + assistantMsg,
                        isGenerating = false,
                        streamingText = ""
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = AiChatMessage(role = "assistant", content = "Error: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + errorMsg,
                        isGenerating = false,
                        streamingText = ""
                    )
                }
            }
        }
    }

    fun resetToModelPicker() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                conversation?.close()
                engine?.close()
            } catch (_: Exception) {}
            conversation = null
            engine = null
        }
        chatHistory.clear()
        _uiState.value = _uiState.value.copy(
            state = LiteRtState.NOT_DOWNLOADED,
            messages = emptyList(),
            streamingText = "",
            errorMessage = null
        )
    }

    // Minimal system prompt — processed ONCE at session start, cached in KV
    private fun buildSystemPrompt(): String =
        "/no_think\nYou are a friendly student academic advisor called JustPass. RULES: 1) Every answer MUST quote the exact numbers from [DATA]. 2) For bunk/skip/leave questions: state classes count, percentage before→after, exact drop, and SAFE/DANGER. 3) For attendance questions: state with-exemption %, without-exemption %, and the difference. 4) NEVER give generic advice — always use the numbers. 5) Keep it to 2-3 sentences with numbers. 6) Do NOT use <think> tags. 7) Never say you don't have data — it's in the [DATA] block. 8) For GREETING: be warm and friendly, greet the student by name, briefly mention you can help with attendance, bunking, marks, GPA, timetable, and syllabus. 9) For marks-needed questions: use the CA marks data and target CGPA to calculate how much they need in their next test."

    override fun onCleared() {
        super.onCleared()
        try { conversation?.close() } catch (_: Exception) {}
        try { engine?.close() } catch (_: Exception) {}
    }
}
