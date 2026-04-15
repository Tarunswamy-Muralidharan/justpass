package com.example.attendancewidgetlaudea.ui.screens

import com.example.attendancewidgetlaudea.ui.components.AdBanner
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.attendancewidgetlaudea.data.model.AbsentDay
import com.example.attendancewidgetlaudea.data.model.AbsentSession
import com.example.attendancewidgetlaudea.data.model.Exemption
import com.example.attendancewidgetlaudea.data.model.TimetableResponse
import com.example.attendancewidgetlaudea.data.model.toDayTimetables
import com.example.attendancewidgetlaudea.data.repository.AttendanceRepository
import com.example.attendancewidgetlaudea.data.repository.Result
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.components.GlassCardShape
import com.example.attendancewidgetlaudea.ui.components.RoseFourLoader
import com.example.attendancewidgetlaudea.ui.components.GlassCardShapeSmall

import io.github.fletchmckee.liquid.LiquidState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

data class DayEntry(
    val date: String,        // formatted display date
    val rawDate: String,     // original for sorting
    val time: String,        // "08:30 - 09:20"
    val session: String?,    // "Session 2"
    val type: String         // "Present", "Absent", "Exemption"
)

@Composable
fun SubjectDetailScreen(
    cardState: LiquidState,
    courseCode: String,
    courseTitle: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AttendanceRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var presentEntries by remember { mutableStateOf<List<DayEntry>>(emptyList()) }
    var absentEntries by remember { mutableStateOf<List<DayEntry>>(emptyList()) }
    var exemptionEntries by remember { mutableStateOf<List<DayEntry>>(emptyList()) }

    val inputFormat = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    val displayFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.US) }

    fun formatDate(isoDate: String): String {
        return try { displayFormat.format(inputFormat.parse(isoDate)!!) } catch (_: Exception) { isoDate }
    }

    LaunchedEffect(courseCode) {
        isLoading = true
        errorMessage = null

        // Use cached data if available (instant), otherwise fetch from server
        val presentResult = repository.cachedPresentDays?.let { Result.Success(it) }
            ?: repository.fetchPresentDays()
        val absentResult = repository.cachedAbsentDays?.let { Result.Success(it) }
            ?: repository.fetchAbsentDays()

        if (presentResult is Result.Success) {
            presentEntries = presentResult.data.flatMap { day ->
                day.sessions.filter { it.courseCode == courseCode }.map { session ->
                    DayEntry(formatDate(day.date), day.date, "${session.startTime} - ${session.endTime}", session.session, "Present")
                }
            }.sortedByDescending { it.rawDate }
        }

        if (absentResult is Result.Success) {
            absentEntries = absentResult.data.flatMap { day ->
                day.sessions.filter { it.courseCode == courseCode }.map { session ->
                    DayEntry(formatDate(day.date), day.date, "${session.startTime} - ${session.endTime}", session.session, "Absent")
                }
            }.sortedByDescending { it.rawDate }
        }

        // Show present/absent immediately — don't wait for exemptions
        isLoading = false

        // Fetch exemptions and timetable in background (updates UI when ready)
        val exemptionResult = repository.fetchExemptions()
        val timetableResult = repository.fetchTimetable()

        // Only include exemptions if overall attendance has exemptions, and filter to current semester
        val cachedAttendance = repository.getCachedAttendance()
        val hasExemptions = cachedAttendance.exemptionCount > 0

        if (hasExemptions && exemptionResult is Result.Success && timetableResult is Result.Success) {
            // Find semester start from present/absent data
            val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val allDates = (presentResult.let { if (it is Result.Success) it.data else emptyList() } +
                absentResult.let { if (it is Result.Success) it.data else emptyList() })
                .mapNotNull { try { isoFmt.parse(it.date) } catch (_: Exception) { null } }
            val semesterStart = allDates.minOrNull()

            val verifiedExemptions = exemptionResult.data.filter { ex ->
                if (ex.status != "V") return@filter false
                if (semesterStart == null) return@filter true
                val exEnd = try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(ex.toDate) } catch (_: Exception) { null }
                exEnd != null && !exEnd.before(semesterStart)
            }
            val dayTimetables = timetableResult.data.toDayTimetables()
            val timetableByDayNum = dayTimetables.associate { it.dayNumber to it.sessions }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val exemptionList = mutableListOf<DayEntry>()

            for (exemption in verifiedExemptions) {
                val fromDate = dateFormat.parse(exemption.fromDate) ?: continue
                val toDate = dateFormat.parse(exemption.toDate) ?: continue
                val cal = Calendar.getInstance()
                cal.time = fromDate

                while (!cal.time.after(toDate)) {
                    val calDow = cal.get(Calendar.DAY_OF_WEEK)
                    val timetableDayNum = when (calDow) {
                        Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
                        Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
                        else -> 0
                    }

                    val sessionsForDay = timetableByDayNum[timetableDayNum] ?: emptyList()
                    val matchingSessions = sessionsForDay.filter { it.courseCode == courseCode }

                    if (exemption.category == "Day" || exemption.sessions.isNullOrEmpty()) {
                        // Full day — add all sessions for this subject on this day
                        for (s in matchingSessions) {
                            exemptionList.add(DayEntry(
                                formatDate(dateFormat.format(cal.time) + "T00:00:00.000Z"),
                                dateFormat.format(cal.time) + "T00:00:00.000Z",
                                "${s.startTime} - ${s.endTime}",
                                null,
                                "Exemption"
                            ))
                        }
                    } else {
                        // Session-specific — match times
                        for (exemptionTime in exemption.sessions) {
                            val matched = matchingSessions.firstOrNull { s ->
                                val exemptStart = parse12hTo24h(exemptionTime.split("-")[0].trim())
                                exemptStart == s.startTime.trim()
                            }
                            if (matched != null) {
                                exemptionList.add(DayEntry(
                                    formatDate(dateFormat.format(cal.time) + "T00:00:00.000Z"),
                                    dateFormat.format(cal.time) + "T00:00:00.000Z",
                                    "${matched.startTime} - ${matched.endTime}",
                                    null,
                                    "Exemption"
                                ))
                            }
                        }
                    }
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            exemptionEntries = exemptionList.sortedByDescending { it.rawDate }
        }

        if (presentResult is Result.Error && absentResult is Result.Error) {
            errorMessage = "Could not load data"
        }
    }

    val presentCount = presentEntries.size
    val absentCount = absentEntries.size
    val exemptionCount = exemptionEntries.size
    val totalCount = presentCount + absentCount + exemptionCount
    val percentage = if (totalCount > 0) ((presentCount + exemptionCount).toDouble() / totalCount) * 100 else 100.0

    // Calculate hours needed for 80%
    val targetPercentage = 80.0
    val effectivePresent = presentCount + exemptionCount
    val hoursNeeded = if (percentage < targetPercentage && totalCount > 0) {
        // Need: (effectivePresent + x) / (total + x) >= 0.8
        // effectivePresent + x >= 0.8 * total + 0.8 * x
        // 0.2x >= 0.8 * total - effectivePresent
        // x >= (0.8 * total - effectivePresent) / 0.2
        val x = ceil((targetPercentage / 100.0 * totalCount - effectivePresent) / (1.0 - targetPercentage / 100.0)).toInt()
        if (x > 0) x else null
    } else null

    val isDark = isSystemInDarkTheme()
    val barColor = when {
        percentage >= 75 -> Color(0xFF4CAF50)
        percentage >= 65 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Header
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = GlassCardShape
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = MaterialTheme.colorScheme.onSurface) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(courseCode, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(courseTitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${String.format("%.1f", percentage)}%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = barColor,
                    maxLines = 1)
            }
        }

        AdBanner(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), screenName = "SubjectDetail")

        // Warning card if below 80%
        if (hoursNeeded != null) {
            GlassListCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                tintColor = Color(0xFFFF5252).copy(alpha = if (isDark) 0.15f else 0.10f)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("\u26A0", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Below 80% target", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            color = Color(0xFFFF8A80))
                        Text("Attend $hoursNeeded more hours without absence to reach 80%",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Stats summary
        GlassListCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$presentCount", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    Text("Present", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$absentCount", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
                    Text("Absent", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$exemptionCount", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))
                    Text("Exemption", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$totalCount", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Total", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Day-by-day list
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RoseFourLoader(modifier = Modifier.size(48.dp))
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
        } else {
            // Combine and sort all entries by date (newest first)
            val allEntries = remember(presentEntries, absentEntries, exemptionEntries) {
                (presentEntries + absentEntries + exemptionEntries).sortedByDescending { it.rawDate }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Group by date
                val grouped = allEntries.groupBy { it.date }
                grouped.forEach { (date, entries) ->
                    item(key = "header_$date") {
                        Text(date, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    items(entries, key = { "${it.rawDate}_${it.time}_${it.type}" }) { entry ->
                        val typeColor = when (entry.type) {
                            "Present" -> Color(0xFF4CAF50)
                            "Absent" -> Color(0xFFF44336)
                            else -> Color(0xFF64B5F6)
                        }
                        val typeBg = typeColor.copy(alpha = if (isDark) 0.10f else 0.06f)

                        GlassListCard(modifier = Modifier.fillMaxWidth(), tintColor = typeBg) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                // Type indicator dot
                                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(typeColor))
                                Spacer(modifier = Modifier.width(10.dp))
                                // Time
                                Text(entry.time, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                // Type label
                                Text(entry.type, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = typeColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parse12hTo24h(time12: String): String {
    return try {
        val clean = time12.trim().uppercase()
        val isPM = clean.contains("PM")
        val isAM = clean.contains("AM")
        val timePart = clean.replace("AM", "").replace("PM", "").trim()
        val (hourStr, minStr) = timePart.split(":").map { it.trim() }
        var hour = hourStr.toInt()
        if (isPM && hour != 12) hour += 12
        if (isAM && hour == 12) hour = 0
        "%02d:%s".format(hour, minStr)
    } catch (_: Exception) { time12.trim() }
}
