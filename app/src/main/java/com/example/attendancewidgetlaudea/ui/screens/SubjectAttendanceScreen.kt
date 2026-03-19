package com.example.attendancewidgetlaudea.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import com.example.attendancewidgetlaudea.data.model.SubjectAttendance
import com.example.attendancewidgetlaudea.ui.components.GlassCardShapeSmall
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.components.LiquidGlassCard
import com.example.attendancewidgetlaudea.ui.viewmodel.SubjectAttendanceViewModel
import io.github.fletchmckee.liquid.LiquidState

@Composable
fun SubjectAttendanceScreen(
    cardState: LiquidState,
    viewModel: SubjectAttendanceViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Header — real liquid glass
        LiquidGlassCard(
            cardState = cardState,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Text("Subject Attendance", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { viewModel.fetchSubjectAttendance() },
                    enabled = !uiState.isLoading
                ) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(uiState.errorMessage ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.fetchSubjectAttendance() }) {
                            Text("Retry")
                        }
                    }
                }
                uiState.subjects.isEmpty() -> {
                    Text("No subject data available",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp,
                            top = 8.dp, bottom = 100.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.subjects) { subject ->
                            SubjectCard(subject)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectCard(subject: SubjectAttendance) {
    val isDark = isSystemInDarkTheme()
    val percentage = subject.attendancePercentage
    val barColor = when {
        percentage >= 75 -> Color(0xFF4CAF50)
        percentage >= 65 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
    val tintColor = barColor.copy(alpha = if (isDark) 0.08f else 0.05f)

    GlassListCard(
        modifier = Modifier.fillMaxWidth(),
        tintColor = tintColor
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            // Course code + percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subject.courseCode,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${String.format("%.1f", subject.attendancePercentage)}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = barColor
                )
            }

            // Course title
            Text(
                text = subject.courseTitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            val progressShape = RoundedCornerShape(4.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(progressShape)
                    .drawBehind {
                        // Background track
                        drawRect(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                        // Filled portion
                        val fillWidth = size.width * (percentage / 100.0).toFloat().coerceIn(0f, 1f)
                        drawRect(barColor.copy(alpha = 0.7f), size = size.copy(width = fillWidth))
                    }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Present / Absent / Total stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Present: ${subject.presentCount}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Absent: ${subject.absentCount}",
                    fontSize = 11.sp,
                    color = if (subject.absentCount > 0) barColor.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Total: ~${subject.estimatedTotal}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
