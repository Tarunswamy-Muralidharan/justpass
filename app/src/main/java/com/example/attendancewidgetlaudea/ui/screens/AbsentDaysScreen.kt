package com.example.attendancewidgetlaudea.ui.screens

import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.data.model.AbsentDay
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.components.LiquidGlassScaffold
import com.example.attendancewidgetlaudea.ui.viewmodel.AbsentDaysViewModel
import java.text.SimpleDateFormat
import java.util.*

private val RowShape = RoundedCornerShape(14.dp)
private val PillShape = RoundedCornerShape(8.dp)

@Composable
fun AbsentDaysScreen(viewModel: AbsentDaysViewModel = viewModel(), onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        GlassListCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = MaterialTheme.colorScheme.onSurface) }
                Text("Absent Days", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.errorMessage != null -> {
                    Column(modifier = Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.errorMessage!!, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.fetchAbsentDays() }) { Text("Retry") }
                    }
                }
                uiState.absentDays.isEmpty() -> Text("No absent days found", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val flatItems = remember(uiState.absentDays) { buildFlatList(uiState.absentDays) }
                    LazyColumn(modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp)) {
                        items(items = flatItems, key = { it.stableKey }, contentType = { it.type }) { item ->
                            when (item) {
                                is FlatItem.DateHeader -> DateHeaderRow(item)
                                is FlatItem.SessionRow -> SessionRowItem(item)
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed class FlatItem {
    abstract val stableKey: String
    abstract val type: Int
    data class DateHeader(val formattedDate: String, val sessionCount: Int, override val stableKey: String) : FlatItem() { override val type = 0 }
    data class SessionRow(val timeText: String, val courseTitle: String, val courseCode: String, val sessionLabel: String?, val isLastInDay: Boolean, override val stableKey: String) : FlatItem() { override val type = 1 }
}

private val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
private val outputFormat = SimpleDateFormat("EEE, MMM d", Locale.US)
private fun formatDate(isoDate: String): String = try { outputFormat.format(inputFormat.parse(isoDate)!!) } catch (_: Exception) { isoDate }

private fun buildFlatList(absentDays: List<AbsentDay>): List<FlatItem> {
    val list = ArrayList<FlatItem>(absentDays.size * 4)
    for (day in absentDays) {
        list.add(FlatItem.DateHeader(formatDate(day.date), day.sessions.size, "d_${day.date}"))
        day.sessions.forEachIndexed { index, session ->
            list.add(FlatItem.SessionRow("${session.startTime}\n${session.endTime}", session.courseTitle, session.courseCode,
                session.session?.replace("Session ", "S"), index == day.sessions.lastIndex, "s_${day.date}_$index"))
        }
    }
    return list
}

@Composable
private fun DateHeaderRow(item: FlatItem.DateHeader) {
    val isDark = isSystemInDarkTheme()
    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(item.formattedDate, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        Text("${item.sessionCount} hrs", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.clip(PillShape).drawBehind { drawRect(if (isDark) Color(0xFFF44336).copy(alpha = 0.15f) else Color(0xFFF44336).copy(alpha = 0.10f)) }
                .border(0.5.dp, if (isDark) Color(0xFFF44336).copy(alpha = 0.25f) else Color(0xFFF44336).copy(alpha = 0.20f), PillShape)
                .padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun SessionRowItem(item: FlatItem.SessionRow) {
    GlassListCard(modifier = Modifier.fillMaxWidth().padding(bottom = if (item.isLastInDay) 4.dp else 6.dp), shape = RowShape) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val isDark = isSystemInDarkTheme()
            val pillFill = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            Text(item.timeText, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary, lineHeight = 14.sp,
                modifier = Modifier.clip(PillShape).drawBehind { drawRect(pillFill) }.border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f), PillShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.courseTitle, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text(item.courseCode, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.sessionLabel != null) {
                val sessionFill = if (isDark) MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
                Text(item.sessionLabel, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.clip(PillShape).drawBehind { drawRect(sessionFill) }.border(0.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f), PillShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}
