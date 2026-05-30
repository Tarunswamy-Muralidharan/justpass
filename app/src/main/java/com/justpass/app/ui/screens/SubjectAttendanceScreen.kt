package com.justpass.app.ui.screens

import com.justpass.app.ui.components.AdBanner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.data.model.SubjectAttendance
import com.justpass.app.data.model.TimetableResponse
import com.justpass.app.ui.components.GlassCardShapeSmall
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.components.RoseFourLoader

import com.justpass.app.ui.viewmodel.SubjectAttendanceViewModel
import io.github.fletchmckee.liquid.LiquidState
import kotlin.math.roundToInt

@Composable
fun SubjectAttendanceScreen(
    cardState: LiquidState,
    viewModel: SubjectAttendanceViewModel = viewModel(),
    onBack: () -> Unit,
    onSubjectClick: (courseCode: String, courseTitle: String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val securePrefs = remember { SecurePreferences.getInstance(context) }
    val timetable = remember {
        try {
            securePrefs.cachedTimetableJson?.let {
                com.google.gson.Gson().fromJson(it, TimetableResponse::class.java)
            }
        } catch (_: Exception) { null }
    }
    // Which subject's leave-planner sheet is open (null = none).
    var bunkSubject by remember { mutableStateOf<SubjectAttendance?>(null) }
    // Warm up the interstitial so it's ready when Plan leave is tapped.
    LaunchedEffect(Unit) { com.justpass.app.ui.components.InterstitialAdManager.preload(context) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Header
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = com.justpass.app.ui.components.GlassCardShape
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("Subject Attendance", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { viewModel.fetchSubjectAttendance() },
                    enabled = !uiState.isLoading
                ) {
                    Icon(Icons.Default.Refresh, "Refresh",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        AdBanner(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), screenName = "SubjectAttendance")

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    RoseFourLoader(modifier = Modifier.size(48.dp).align(Alignment.Center))
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
                            top = 8.dp, bottom = 160.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.subjects, key = { it.courseCode }) { subject ->
                            SubjectCardBold(
                                subject = subject,
                                onClick = { onSubjectClick(subject.courseCode, subject.courseTitle) },
                                onBunk = {
                                    // Interstitial ad before the leave planner opens.
                                    val activity = context as? android.app.Activity
                                    if (activity != null) {
                                        com.justpass.app.ui.components.InterstitialAdManager.show(activity) {
                                            bunkSubject = subject
                                        }
                                    } else {
                                        bunkSubject = subject
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Leave planner for the chosen subject — slider/calendar → projected drop.
    bunkSubject?.let { subj ->
        SubjectBunkometerSheet(
            subject = subj,
            timetable = timetable,
            attendanceTarget = securePrefs.attendanceTarget.toDouble(),
            onDismiss = { bunkSubject = null }
        )
    }
}

// Status color shared by all card styles.
private fun statusColor(percentage: Double): Color = when {
    percentage >= 75 -> Color(0xFF4CAF50)
    percentage >= 65 -> Color(0xFFFFC107)
    else -> Color(0xFFF44336)
}

/** Orange "Plan leave" Bunkometer chip. */
@Composable
private fun PlanLeaveChip(onBunk: () -> Unit, isDark: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFF9800).copy(alpha = if (isDark) 0.16f else 0.12f))
            .clickable { onBunk() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFFFF9800),
            modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text("Plan leave", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF9800))
    }
}

@Composable
private fun SubjectStatsRow(subject: SubjectAttendance, color: Color, compact: Boolean = false) {
    val sep = if (compact) " \u00b7 " else null
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (compact) Arrangement.Start else Arrangement.SpaceBetween
    ) {
        Text("P:${subject.presentCount}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        sep?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
        Text("A:${subject.absentCount}", fontSize = 11.sp,
            color = if (subject.absentCount > 0) color.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant)
        if (subject.exemptionCount > 0) {
            sep?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
            Text("E:${subject.exemptionCount}", fontSize = 11.sp, color = Color(0xFF64B5F6).copy(alpha = 0.85f))
        }
        sep?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
        Text("T:${subject.totalCount}", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

// \u2500\u2500 Subject card: big % box + gradient wash \u2500\u2500
@Composable
private fun SubjectCardBold(subject: SubjectAttendance, onClick: () -> Unit, onBunk: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val color = statusColor(subject.attendancePercentage)
    val pct = subject.attendancePercentage
    GlassListCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Box(
            modifier = Modifier.fillMaxWidth().background(
                Brush.horizontalGradient(
                    listOf(color.copy(alpha = if (isDark) 0.22f else 0.16f), Color.Transparent)
                )
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Big % box
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(color.copy(alpha = if (isDark) 0.20f else 0.14f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("${String.format("%.1f", pct)}%", fontSize = 24.sp,
                            fontWeight = FontWeight.Black, color = color)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(subject.courseCode, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subject.courseTitle, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp))
                                .drawBehind {
                                    drawRect(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                                    val w = size.width * (pct / 100.0).toFloat().coerceIn(0f, 1f)
                                    drawRect(color.copy(alpha = 0.8f), size = size.copy(width = w))
                                }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                SubjectStatsRow(subject, color)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlanLeaveChip(onBunk, isDark)
                    Text("Tap for details \u2192", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                }
            }
        }
    }
}
