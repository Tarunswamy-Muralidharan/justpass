package com.example.attendancewidgetlaudea.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.data.model.AbsentDay
import com.example.attendancewidgetlaudea.ui.viewmodel.AbsentDaysViewModel
import java.text.SimpleDateFormat
import java.util.*

// Pre-allocate shapes to avoid recreating on every recomposition
private val RowShape = RoundedCornerShape(12.dp)
private val PillShape = RoundedCornerShape(8.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbsentDaysScreen(
    viewModel: AbsentDaysViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Absent Days") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.fetchAbsentDays() }) {
                            Text("Retry")
                        }
                    }
                }
                uiState.absentDays.isEmpty() -> {
                    Text(
                        text = "No absent days found",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    // Pre-compute all display strings outside of composition
                    val flatItems = remember(uiState.absentDays) {
                        buildFlatList(uiState.absentDays)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        items(
                            items = flatItems,
                            key = { it.stableKey },
                            contentType = { it.type }
                        ) { item ->
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

    data class DateHeader(
        val formattedDate: String,
        val sessionCount: Int,
        override val stableKey: String
    ) : FlatItem() {
        override val type: Int = 0
    }

    data class SessionRow(
        val timeText: String,
        val courseTitle: String,
        val courseCode: String,
        val sessionLabel: String?,
        val isLastInDay: Boolean,
        override val stableKey: String
    ) : FlatItem() {
        override val type: Int = 1
    }
}

// Pre-compute formatted dates and display strings during list building (off composition)
private val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
private val outputFormat = SimpleDateFormat("EEE, MMM d", Locale.US)

private fun formatDate(isoDate: String): String {
    return try {
        val date = inputFormat.parse(isoDate)
        outputFormat.format(date!!)
    } catch (e: Exception) {
        isoDate
    }
}

private fun buildFlatList(absentDays: List<AbsentDay>): List<FlatItem> {
    val list = ArrayList<FlatItem>(absentDays.size * 4) // pre-size
    for (day in absentDays) {
        val formattedDate = formatDate(day.date)
        list.add(FlatItem.DateHeader(formattedDate, day.sessions.size, "d_${day.date}"))
        day.sessions.forEachIndexed { index, session ->
            list.add(
                FlatItem.SessionRow(
                    timeText = "${session.startTime}\n${session.endTime}",
                    courseTitle = session.courseTitle,
                    courseCode = session.courseCode,
                    sessionLabel = session.session?.replace("Session ", "S"),
                    isLastInDay = index == day.sessions.lastIndex,
                    stableKey = "s_${day.date}_$index"
                )
            )
        }
    }
    return list
}

@Composable
private fun DateHeaderRow(item: FlatItem.DateHeader) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = item.formattedDate,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "${item.sessionCount} hrs",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.errorContainer, PillShape)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SessionRowItem(item: FlatItem.SessionRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (item.isLastInDay) 4.dp else 6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RowShape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time pill
        Text(
            text = item.timeText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            lineHeight = 14.sp,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer, PillShape)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.courseTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.courseCode,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (item.sessionLabel != null) {
            Text(
                text = item.sessionLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, PillShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
