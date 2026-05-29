package com.justpass.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.data.model.SubjectAttendance
import com.justpass.app.data.model.TimetableResponse
import com.justpass.app.data.model.periodsByWeekdayBySubject
import com.justpass.app.data.model.periodsForSubjectOnDate
import com.justpass.app.data.model.projectSubjectBunk
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Mode B — single-subject Bunkometer. Slider + 4-week calendar stay synced:
 * tapping days drives the slider; dragging the slider clears the calendar.
 * All math is offline (pure functions in SubjectBunkometer.kt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectBunkometerSheet(
    subject: SubjectAttendance,
    timetable: TimetableResponse?,
    attendanceTarget: Double,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedDates = remember { mutableStateListOf<LocalDate>() }
    var sliderPeriods by remember { mutableFloatStateOf(0f) }

    val calendarPeriods = remember(selectedDates.toList(), timetable) {
        if (timetable == null) 0
        else selectedDates.sumOf { periodsForSubjectOnDate(subject.courseCode, it, timetable) }
    }
    val periods = if (selectedDates.isNotEmpty()) calendarPeriods else sliderPeriods.roundToInt()
    val projection = remember(periods, subject, attendanceTarget) {
        projectSubjectBunk(subject, periods, attendanceTarget)
    }

    // Avg periods per scheduled day for this subject → used for the "≈ N days" estimate.
    val avgPerDay = remember(timetable, subject.courseCode) {
        val byDay = timetable?.periodsByWeekdayBySubject() ?: return@remember 0.0
        val perDay = (1..6).mapNotNull { byDay[it]?.get(subject.courseCode) }.filter { it > 0 }
        if (perDay.isNotEmpty()) perDay.sum().toDouble() / perDay.size else 0.0
    }
    val budgetDays = if (avgPerDay > 0) (projection.budget / avgPerDay).roundToInt() else 0

    val sliderMax = (projection.budget + 5).coerceIn(10, 60).toFloat()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E2A3A)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Title
            Text(subject.courseCode, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subject.courseTitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.height(16.dp))

            // ── Hero card ──
            if (projection.belowTarget) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFF5252).copy(alpha = 0.14f))
                        .padding(16.dp)
                ) {
                    Text("Below your ${attendanceTarget.roundToInt()}% target",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF8A80))
                    Spacer(Modifier.height(4.dp))
                    Text("Attend ${projection.recoveryPeriods} more period${if (projection.recoveryPeriods != 1) "s" else ""} without absence to climb back to ${attendanceTarget.roundToInt()}%.",
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF00E676).copy(alpha = 0.12f))
                        .padding(16.dp)
                ) {
                    Text("${projection.budget} period${if (projection.budget != 1) "s" else ""} to spare",
                        fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF00E676))
                    if (budgetDays > 0) {
                        Text("≈ $budgetDays full day${if (budgetDays != 1) "s" else ""} of this class",
                            fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                    Text("before you drop below ${attendanceTarget.roundToInt()}%",
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Projection (current → new) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${String.format("%.1f", projection.currentPercentage)}%",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                    Text("Now", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                }
                Text("→", fontSize = 20.sp, color = Color.White.copy(alpha = 0.5f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val newColor = if (projection.projectedPercentage < attendanceTarget) Color(0xFFFF5252) else Color(0xFF00E676)
                    Text("${String.format("%.1f", projection.projectedPercentage)}%",
                        fontSize = 28.sp, fontWeight = FontWeight.Black, color = newColor)
                    Text(if (periods > 0) "after $periods period${if (periods != 1) "s" else ""}" else "no bunks yet",
                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Slider ──
            Text("Bunk how many periods?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.8f))
            Slider(
                value = if (selectedDates.isNotEmpty()) calendarPeriods.toFloat().coerceAtMost(sliderMax) else sliderPeriods,
                onValueChange = {
                    selectedDates.clear()   // dragging the slider takes over from calendar
                    sliderPeriods = it
                },
                valueRange = 0f..sliderMax,
                steps = (sliderMax.toInt() - 1).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00E676),
                    activeTrackColor = Color(0xFF00E676),
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )

            // ── Calendar (next 4 weeks) ──
            if (timetable != null) {
                Spacer(Modifier.height(8.dp))
                Text("Or pick specific days", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(8.dp))

                // Weekday headers (Mon..Sun)
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                        Text(d, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(4.dp))

                val today = LocalDate.now()
                val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong()) // Monday of this week
                for (week in 0 until 4) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (col in 0..6) {
                            val date = weekStart.plusDays((week * 7 + col).toLong())
                            val subjPeriods = periodsForSubjectOnDate(subject.courseCode, date, timetable)
                            val isPast = date.isBefore(today)
                            val isSelected = date in selectedDates
                            val isDisabled = isPast || subjPeriods == 0
                            Box(
                                modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isSelected -> Color(0xFF00E676)
                                            !isDisabled -> Color(0xFF00E676).copy(alpha = 0.12f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (!isDisabled) Modifier.clickable {
                                            if (isSelected) selectedDates.remove(date) else selectedDates.add(date)
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${date.dayOfMonth}", fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> Color.Black
                                            isDisabled -> Color.White.copy(alpha = 0.25f)
                                            else -> Color.White
                                        })
                                    if (subjPeriods > 0 && !isPast) {
                                        Text("${subjPeriods}p", fontSize = 8.sp,
                                            color = if (isSelected) Color.Black.copy(alpha = 0.7f)
                                            else Color(0xFF00E676))
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedDates.isNotEmpty()) {
                    val sorted = selectedDates.sorted()
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF00E676).copy(alpha = 0.08f))
                            .padding(10.dp)
                    ) {
                        Text("Selected days:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF00E676))
                        Spacer(Modifier.height(2.dp))
                        sorted.forEach { d ->
                            val p = periodsForSubjectOnDate(subject.courseCode, d, timetable)
                            val dayName = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                            Text("• ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ($dayName) — $p period${if (p != 1) "s" else ""}",
                                fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                    TextButton(onClick = { selectedDates.clear() }) {
                        Text("Clear days", fontSize = 12.sp, color = Color(0xFF00E676))
                    }
                }
            }
        }
    }
}
