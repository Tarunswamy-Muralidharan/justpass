package com.example.attendancewidgetlaudea.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.data.model.CalendarEvent
import com.example.attendancewidgetlaudea.data.model.CalendarEventType
import com.example.attendancewidgetlaudea.ui.components.GlassCardShape
import com.example.attendancewidgetlaudea.ui.components.GlassCardShapeSmall
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.components.GlassListSurface
import com.example.attendancewidgetlaudea.ui.viewmodel.CalendarViewModel
import io.github.fletchmckee.liquid.LiquidState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val HolidayColor = Color(0xFF8E24AA)       // Purple (matches Google Calendar)
private val WorkingDayColor = Color(0xFFF9A825)     // Amber
private val AcademicColor = Color(0xFF1E88E5)       // Blue
private val ExamColor = Color(0xFFE53935)           // Red
private val EventColor = Color(0xFF43A047)          // Green
private val TodayBorderColor = Color(0xFF00E676)    // Bright green

@Composable
fun AcademicCalendarScreen(
    cardState: LiquidState,
    viewModel: CalendarViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = isSystemInDarkTheme()
    val today = remember { LocalDate.now() }

    // Map events by date for quick lookup
    val eventsByDate = remember(uiState.events) {
        uiState.events.groupBy { it.startDate }
    }

    // Events in selected month
    val monthEvents = remember(uiState.events, uiState.selectedMonth) {
        uiState.events.filter {
            try {
                val date = LocalDate.parse(it.startDate)
                YearMonth.from(date) == uiState.selectedMonth
            } catch (_: Exception) { false }
        }
    }

    // Upcoming events (from today forward)
    val upcomingEvents = remember(uiState.events) {
        uiState.events.filter {
            try { LocalDate.parse(it.startDate) >= today }
            catch (_: Exception) { false }
        }.take(5)
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Header
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = GlassCardShape
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("Academic Calendar", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.fetchEvents() }, enabled = !uiState.isLoading) {
                    Icon(Icons.Default.Refresh, "Refresh",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiState.errorMessage!!, color = Color(0xFFFF5252))
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { viewModel.fetchEvents() }) {
                        Text("Retry")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Month navigation
                item {
                    MonthNavigator(
                        month = uiState.selectedMonth,
                        onPrevious = { viewModel.previousMonth() },
                        onNext = { viewModel.nextMonth() }
                    )
                }

                // Calendar grid
                item {
                    CalendarGrid(
                        month = uiState.selectedMonth,
                        today = today,
                        eventsByDate = eventsByDate,
                        isDark = isDark
                    )
                }

                // Legend
                item {
                    GlassListSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = GlassCardShapeSmall
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            LegendDot(HolidayColor, "Holiday")
                            LegendDot(WorkingDayColor, "Working")
                            LegendDot(AcademicColor, "Academic")
                            LegendDot(ExamColor, "Exam")
                        }
                    }
                }

                // Events this month header
                if (monthEvents.isNotEmpty()) {
                    item {
                        Text(
                            "Events in ${uiState.selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }

                    items(monthEvents, key = { it.id }) { event ->
                        EventCard(event, isDark)
                    }
                }

                // Upcoming events
                if (upcomingEvents.isNotEmpty() && uiState.selectedMonth == YearMonth.now()) {
                    item {
                        Text(
                            "Coming Up",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                    items(upcomingEvents, key = { "upcoming_${it.id}" }) { event ->
                        EventCard(event, isDark)
                    }
                }

                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }
}

@Composable
private fun MonthNavigator(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    GlassListCard(
        modifier = Modifier.fillMaxWidth(),
        shape = GlassCardShapeSmall
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    today: LocalDate,
    eventsByDate: Map<String, List<CalendarEvent>>,
    isDark: Boolean
) {
    val firstDay = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()
    // Monday = 0 offset
    val startOffset = (firstDay.dayOfWeek.value % 7) // SUN=0..SAT=6 mapping
    val sundayOffset = firstDay.dayOfWeek.value % 7

    GlassListSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = GlassCardShapeSmall
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Day headers
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Calendar cells
            var dayCounter = 1
            val rows = ((sundayOffset + daysInMonth + 6) / 7)
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0..6) {
                        val cellIndex = row * 7 + col
                        if (cellIndex < sundayOffset || dayCounter > daysInMonth) {
                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = month.atDay(dayCounter)
                            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                            val events = eventsByDate[dateStr]
                            val isToday = date == today
                            val isSunday = col == 0

                            CalendarDayCell(
                                day = dayCounter,
                                events = events,
                                isToday = isToday,
                                isSunday = isSunday,
                                isDark = isDark,
                                modifier = Modifier.weight(1f)
                            )
                            dayCounter++
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    events: List<CalendarEvent>?,
    isToday: Boolean,
    isSunday: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val hasEvent = events != null && events.isNotEmpty()
    val eventColor = events?.firstOrNull()?.let { colorForEventType(it.eventType) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .then(
                if (isToday) Modifier.border(1.5.dp, TodayBorderColor, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (hasEvent) Modifier.background(eventColor!!.copy(alpha = if (isDark) 0.25f else 0.15f))
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$day",
                fontSize = 13.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> TodayBorderColor
                    isSunday -> HolidayColor
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            if (hasEvent) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(eventColor!!)
                )
            }
        }
    }
}

@Composable
private fun EventCard(event: CalendarEvent, isDark: Boolean) {
    val color = colorForEventType(event.eventType)
    val displayDate = try {
        val date = LocalDate.parse(event.startDate)
        "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
    } catch (_: Exception) { event.startDate }

    val displayName = event.summary
        .removePrefix("Holiday ")
        .removePrefix("Holiday")
        .trim()
        .ifEmpty { event.summary }

    GlassListSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = GlassCardShapeSmall
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = event.eventType.name.replace("_", " ").lowercase()
                        .replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp,
                    color = color
                )
            }
            Text(
                text = displayDate,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

private fun colorForEventType(type: CalendarEventType): Color = when (type) {
    CalendarEventType.HOLIDAY -> HolidayColor
    CalendarEventType.WORKING_DAY -> WorkingDayColor
    CalendarEventType.ACADEMIC -> AcademicColor
    CalendarEventType.EXAM -> ExamColor
    CalendarEventType.EVENT -> EventColor
}
