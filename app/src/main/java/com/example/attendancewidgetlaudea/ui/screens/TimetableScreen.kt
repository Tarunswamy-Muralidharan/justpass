package com.example.attendancewidgetlaudea.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.data.model.DayTimetable
import com.example.attendancewidgetlaudea.data.model.SessionInfo
import com.example.attendancewidgetlaudea.ui.components.GlassCardShapeSmall
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.components.GlassListSurface

import com.example.attendancewidgetlaudea.ui.viewmodel.TimetableViewModel
import io.github.fletchmckee.liquid.LiquidState
import java.util.Calendar

private val TabShape = RoundedCornerShape(12.dp)

@Composable
fun TimetableScreen(cardState: LiquidState, viewModel: TimetableViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = isSystemInDarkTheme()

    // Recompute "Today" each time the screen is shown (survives midnight)
    LaunchedEffect(Unit) { viewModel.refreshTodayIndex() }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Day tabs — real liquid glass
        if (uiState.days.isNotEmpty()) {
            GlassListCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                ScrollableTabRow(
                    selectedTabIndex = uiState.selectedDayIndex, edgePadding = 4.dp,
                    containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface,
                    divider = {}, indicator = {}
                ) {
                    uiState.days.forEachIndexed { index, day ->
                        val isToday = index == uiState.todayDayIndex
                        val isSelected = uiState.selectedDayIndex == index
                        val tabBg = if (isSelected) {
                            if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.7f)
                        } else Color.Transparent

                        Tab(
                            selected = isSelected, onClick = { viewModel.selectDay(index) },
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp).clip(TabShape).background(tabBg),
                            text = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(day.dayName.take(3).uppercase(),
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    if (isToday) Text("Today", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            when {
                uiState.isLoading && uiState.days.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.errorMessage != null && uiState.days.isEmpty() -> {
                    Column(modifier = Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.errorMessage ?: "Unknown error", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.fetchTimetable() }) { Text("Retry") }
                    }
                }
                uiState.days.isEmpty() -> Text("No timetable available", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val selectedDay = uiState.days.getOrNull(uiState.selectedDayIndex)
                    if (selectedDay != null) DaySchedule(selectedDay, uiState.selectedDayIndex == uiState.todayDayIndex)
                }
            }
            if (uiState.isLoading && uiState.days.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            }
        }
    }
}

@Composable
private fun DaySchedule(day: DayTimetable, isToday: Boolean) {
    val currentSessionIndex = if (isToday) getCurrentSessionIndex(day) else -1
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(day.sessions) { session -> SessionCard(session, isToday && session.sessionNumber == currentSessionIndex) }
        if (day.sessions.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                Text("No classes scheduled", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp) } }
        }
    }
}

@Composable
private fun SessionCard(session: SessionInfo, isCurrentSession: Boolean) {
    val isDark = isSystemInDarkTheme()
    val tintColor = if (isCurrentSession) MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.15f else 0.08f) else Color.Transparent

    val currentProgress = if (isCurrentSession) {
        val cal = Calendar.getInstance()
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startParts = session.startTime.split(":")
        val endParts = session.endTime.split(":")
        if (startParts.size == 2 && endParts.size == 2) {
            val startMin = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
            val endMin = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)
            val duration = (endMin - startMin).coerceAtLeast(1)
            ((nowMinutes - startMin).toFloat() / duration).coerceIn(0f, 1f)
        } else 0f
    } else 0f

    val progressOverlay = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)

    GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShapeSmall, tintColor = tintColor) {
        Row(modifier = Modifier.fillMaxWidth()
            .drawBehind {
                if (currentProgress > 0f) {
                    drawRect(
                        color = progressOverlay,
                        size = size.copy(width = size.width * currentProgress)
                    )
                }
            }
            .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(session.startTime, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    color = if (isCurrentSession) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text(session.endTime, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            Box(modifier = Modifier.width(3.dp).height(44.dp).clip(RoundedCornerShape(2.dp))
                .background(if (isCurrentSession) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(session.courseCode, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    if (isCurrentSession) {
                        Spacer(modifier = Modifier.width(8.dp))
                        GlassListSurface(shape = RoundedCornerShape(4.dp), tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) {
                            Text("NOW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    if (session.isHonours) {
                        Spacer(modifier = Modifier.width(8.dp))
                        GlassListSurface(shape = RoundedCornerShape(4.dp), tintColor = Color(0xFFFFA000).copy(alpha = 0.25f)) {
                            Text("HONOURS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFA000),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(session.courseTitle, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (session.facultyNames.isNotEmpty()) {
                    Text(session.facultyNames.joinToString(", "), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun getCurrentSessionIndex(day: DayTimetable): Int {
    val cal = Calendar.getInstance()
    val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    for (session in day.sessions) {
        val s = session.startTime.split(":"); val e = session.endTime.split(":")
        if (s.size == 2 && e.size == 2) {
            val sm = s[0].toIntOrNull()?.let { it * 60 + (s[1].toIntOrNull() ?: 0) } ?: continue
            val em = e[0].toIntOrNull()?.let { it * 60 + (e[1].toIntOrNull() ?: 0) } ?: continue
            if (currentMinutes in sm..em) return session.sessionNumber
        }
    }
    return -1
}
