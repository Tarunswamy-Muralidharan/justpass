package com.example.attendancewidgetlaudea.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.R
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import com.example.attendancewidgetlaudea.ui.components.GlassCardShape
import com.example.attendancewidgetlaudea.ui.components.GlassCardShapeSmall
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.components.GlassListSurface
import com.example.attendancewidgetlaudea.ui.components.LiquidGlassCard
import com.example.attendancewidgetlaudea.ui.viewmodel.DashboardViewModel
import io.github.fletchmckee.liquid.LiquidState
import com.example.attendancewidgetlaudea.data.analytics.Analytics
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    cardState: LiquidState,
    viewModel: DashboardViewModel = viewModel(),
    displayName: String = "",
    onLogout: () -> Unit,
    onAbsentDaysClick: () -> Unit = {},
    onSubjectAttendanceClick: () -> Unit = {},
    onExemptionsClick: () -> Unit = {},
    onResultClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    onCircularsClick: () -> Unit = {},
    onCgpaClick: () -> Unit = {},
    onExamSeatClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val securePrefs = remember { SecurePreferences.getInstance(context) }
    val attendanceTarget = remember { securePrefs.attendanceTarget }

    // Fetch biodata (department, programmeName) on first Dashboard load if missing
    // Safe here because login WebView is fully destroyed before Dashboard renders
    LaunchedEffect(Unit) {
        if (securePrefs.programmeName == null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val repo = com.example.attendancewidgetlaudea.data.repository.AttendanceRepository.getInstance(context)
                    val bio = repo.fetchStudentBiodata()
                    bio?.let {
                        it.programmeName?.let { p -> securePrefs.programmeName = p }
                        it.batchYear?.let { b -> securePrefs.batchYear = b }
                        it.currentSem?.let { s -> securePrefs.cachedCurrentSem = s }
                        it.section?.let { s -> securePrefs.cachedSection = s }
                        val detected = com.example.attendancewidgetlaudea.data.model.detectDepartment(it.programmeName)
                            ?: com.example.attendancewidgetlaudea.data.model.detectDepartment(it.department)
                        securePrefs.cachedDepartment = detected?.shortName ?: it.programmeName ?: it.department
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Glow animation around the header card on refresh — persists 2s after refresh ends
    var refreshGlowKey by remember { mutableIntStateOf(0) }
    val glowAnim = remember { Animatable(0f) }
    LaunchedEffect(refreshGlowKey) {
        if (refreshGlowKey > 0) {
            glowAnim.snapTo(0f)
            glowAnim.animateTo(1f, tween(2500, easing = LinearEasing))
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            com.example.attendancewidgetlaudea.data.analytics.Analytics.logPullToRefresh()
            refreshGlowKey++
            viewModel.refreshAttendance()
        },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        indicator = {} // Hidden — the glass comet glow is our refresh indicator
    ) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 130.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header — liquid glass with light sweep on refresh
        Box(modifier = Modifier.fillMaxWidth()) {
            GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShape) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (displayName.isNotEmpty()) "Welcome, ${displayName.split(" ").firstOrNull() ?: displayName}" else "Attendance",
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(uiState.rollNumber, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            refreshGlowKey++
                            viewModel.refreshAttendance()
                        },
                        enabled = !uiState.isRefreshing
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Smooth comet light travelling inside the glass border
            val g = glowAnim.value
            if (uiState.isRefreshing || g in 0.001f..0.999f) {
                val orbTransition = rememberInfiniteTransition(label = "orb")
                val lightPos by orbTransition.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "lp"
                )
                val fadeAlpha = if (uiState.isRefreshing) 1f else ((1f - g) * 2f).coerceIn(0f, 1f)

                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    val perimeter = 2f * (w + h)
                    val r = 28f

                    // Clip inside the glass card shape
                    val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, r, r)) }
                    clipPath(clip) {
                        fun posOnBorder(t: Float): Offset {
                            val d = ((t % 1f + 1f) % 1f) * perimeter
                            return when {
                                d < w -> Offset(d, 0f)
                                d < w + h -> Offset(w, d - w)
                                d < 2 * w + h -> Offset(w - (d - w - h), h)
                                else -> Offset(0f, h - (d - 2 * w - h))
                            }
                        }

                        // Smooth comet trail — 20 overlapping gradient circles
                        val trailLen = 20
                        for (i in 0 until trailLen) {
                            val t = lightPos - i * 0.012f
                            val pt = posOnBorder(t)
                            val progress = i.toFloat() / trailLen
                            val a = (1f - progress) * fadeAlpha

                            // Color shifts from white→purple→pink→blue along the trail
                            val coreColor = when {
                                progress < 0.15f -> Color.White
                                progress < 0.4f -> Color(0xFFAD5FFF)
                                progress < 0.7f -> Color(0xFFD60A47)
                                else -> Color(0xFF471EEC)
                            }

                            val radius = 90f - progress * 50f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        coreColor.copy(alpha = a * 0.85f),
                                        coreColor.copy(alpha = a * 0.3f),
                                        Color.Transparent
                                    ),
                                    center = pt,
                                    radius = radius
                                ),
                                radius = radius,
                                center = pt
                            )
                        }

                        // Bright white core at the head
                        val head = posOnBorder(lightPos)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.95f * fadeAlpha),
                            radius = 6f,
                            center = head
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Attendance % — real liquid glass with color tint
        val attendanceTint = getAttendanceTintColor(uiState.attendanceData.attendanceWithExemption)
        LiquidGlassCard(cardState = cardState,
            modifier = Modifier.fillMaxWidth().clickable { Analytics.logTileClicked("attendance"); onSubjectAttendanceClick() },
            tintColor = attendanceTint) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Attendance (with exemption)", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))
                Text("${String.format("%.1f", uiState.attendanceData.attendanceWithExemption)}%",
                    fontSize = 52.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, color = MaterialTheme.colorScheme.onSurface)
                if (uiState.attendanceData.attendanceWithExemption != uiState.attendanceData.attendancePercentage) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Without exemption: ${String.format("%.1f", uiState.attendanceData.attendancePercentage)}%",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                // Warning: hours needed for target %
                val targetPct = attendanceTarget / 100.0
                if (uiState.attendanceData.attendanceWithExemption < attendanceTarget && uiState.attendanceData.enteredTillDate > 0) {
                    val present = uiState.attendanceData.presentWithExemptionCount
                    val total = uiState.attendanceData.enteredTillDate
                    val hoursNeeded = kotlin.math.ceil((targetPct * total - present) / (1.0 - targetPct)).toInt()
                    if (hoursNeeded > 0) {
                        val approxDays = kotlin.math.ceil(hoursNeeded / 6.0).toInt()
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Attend $hoursNeeded more hours (~$approxDays days) to reach $attendanceTarget%",
                            fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = Color(0xFFFF8A80))
                    }
                }

                // Stats inside the main card
                if (uiState.attendanceData.enteredTillDate > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(uiState.attendanceData.presentWithExemptionCount.toString(),
                                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                            Text("Present", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        GlassListSurface(
                            shape = RoundedCornerShape(12.dp),
                            tintColor = Color(0xFFFF5252).copy(alpha = 0.12f),
                            modifier = Modifier.clickable { onAbsentDaysClick() }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(uiState.attendanceData.absentCount.toString(),
                                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                                Text("Absent", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFF5252).copy(alpha = 0.85f))
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(uiState.attendanceData.enteredTillDate.toString(),
                                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Total", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (uiState.attendanceData.exemptionCount > 0) {
                            GlassListSurface(
                                shape = RoundedCornerShape(12.dp),
                                tintColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                                modifier = Modifier.clickable { onExemptionsClick() }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(uiState.attendanceData.exemptionCount.toString(),
                                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                    Text("Exempt", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f))
                                }
                            }
                        }
                        if (uiState.attendanceData.notEnteredTillDate > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text(uiState.attendanceData.notEnteredTillDate.toString(),
                                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Pending", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Tap for subject-wise details →",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Leave calculator
        if (uiState.attendanceData.enteredTillDate > 0) {
            var leaveDays by remember { mutableFloatStateOf(0f) }
            var showLeavePopup by remember { mutableStateOf(false) }
            var leaveStartDate by remember { mutableStateOf(java.time.LocalDate.now().plusDays(1)) }
            var showDatePicker by remember { mutableStateOf(false) }
            val days = leaveDays.toInt()
            val present = uiState.attendanceData.presentWithExemptionCount
            val total = uiState.attendanceData.enteredTillDate
            val currentPct = uiState.attendanceData.attendanceWithExemption
            val leaveHours = if (days > 0) viewModel.calculateLeaveHours(leaveStartDate, days) else 0
            val newTotal = total + leaveHours
            val newPercentage = if (days > 0 && newTotal > 0) (present.toDouble() / newTotal) * 100.0 else currentPct
            val drop = currentPct - newPercentage
            val dateFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy") }

            // Inline card — tap to expand
            GlassListCard(
                modifier = Modifier.fillMaxWidth().clickable { showLeavePopup = true },
                shape = GlassCardShapeSmall
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("What if I take leave?", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("Tap to expand", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    }
                    if (days > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "$days day${if (days > 1) "s" else ""} → ${String.format("%.1f", newPercentage)}%",
                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = if (newPercentage < attendanceTarget) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tap to calculate", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }

            // Fullscreen leave calculator popup
            if (showLeavePopup) {
                AlertDialog(
                    onDismissRequest = { showLeavePopup = false },
                    title = {
                        Text("What if I take leave?", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface)
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Date picker row
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .clickable { showDatePicker = true }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Starting from", fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(leaveStartDate.format(dateFormatter),
                                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                                Text("Change", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Big day count
                            Text(
                                "${days} day${if (days > 1) "s" else ""}",
                                fontSize = 48.sp, fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Bigger slider
                            Slider(
                                value = leaveDays,
                                onValueChange = { leaveDays = it; if (it.toInt() > 0) Analytics.logSliderUsed("leave_calculator", it.toInt()) },
                                valueRange = 0f..30f,
                                steps = 29,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            )

                            if (days == 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Slide to see how your attendance changes",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth())
                            }

                            if (days > 0) {
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${String.format("%.1f", newPercentage)}%",
                                            fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                            color = if (newPercentage < attendanceTarget) Color(0xFFFF5252) else Color(0xFF00E676))
                                        Text("New attendance", fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("-${String.format("%.1f", drop)}%",
                                            fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF8A80))
                                        Text("Drop", fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("$leaveHours",
                                        fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text("Hours missed", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (uiState.holidayDates.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Holidays & Sundays are excluded",
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLeavePopup = false }) {
                            Text("Done")
                        }
                    },
                    containerColor = Color(0xFF1E2A3A)
                )
            }

            // Date picker dialog
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = leaveStartDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
                val datePickerColors = DatePickerDefaults.colors(
                    containerColor = Color(0xFF1A2535),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    headlineContentColor = MaterialTheme.colorScheme.onSurface,
                    weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationContentColor = MaterialTheme.colorScheme.onSurface,
                    yearContentColor = MaterialTheme.colorScheme.onSurface,
                    dayContentColor = MaterialTheme.colorScheme.onSurface,
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                    todayDateBorderColor = Color(0xFF00E676),
                    todayContentColor = Color(0xFF00E676)
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                leaveStartDate = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            }
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                    },
                    colors = DatePickerDefaults.colors(containerColor = Color(0xFF1A2535))
                ) {
                    DatePicker(state = datePickerState, colors = datePickerColors)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Result + Calendar tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardTile("Semester Result", "Grades & GPA", Icons.Default.School, Color(0xFF42A5F5),
                Modifier.weight(1f)) { Analytics.logTileClicked("result"); onResultClick() }
            DashboardTile("Calendar", "Holidays & events", Icons.Default.DateRange, Color(0xFF8E24AA),
                Modifier.weight(1f)) { Analytics.logTileClicked("calendar"); onCalendarClick() }
        }

        Spacer(modifier = Modifier.height(8.dp))

        DashboardTile("GPA Calculator", "Calculate SGPA & CGPA", Icons.Default.Calculate, Color(0xFF00E676),
            Modifier.fillMaxWidth()) { Analytics.logTileClicked("gpa_calculator"); onCgpaClick() }

        Spacer(modifier = Modifier.height(8.dp))

        DashboardTile("Circulars", "Notices & updates from college", Icons.Default.Mail, Color(0xFFFFC107),
            Modifier.fillMaxWidth()) { Analytics.logTileClicked("circulars"); onCircularsClick() }

        Spacer(modifier = Modifier.height(8.dp))

        DashboardTile("Exam Seat Finder", "Find your hall & seat", Icons.Default.EventSeat, Color(0xFFFF8A65),
            Modifier.fillMaxWidth()) { Analytics.logTileClicked("exam_seat"); onExamSeatClick() }

        uiState.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            GlassListCard(modifier = Modifier.fillMaxWidth(), tintColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Credit
        Text("for features or colabs", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.attendanceData.lastUpdated > 0) {
            Text("Last updated: ${formatTimestamp(uiState.attendanceData.lastUpdated)}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
    } // PullToRefreshBox
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    GlassListCard(modifier = modifier, shape = GlassCardShapeSmall) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun getAttendanceTintColor(percentage: Double): Color {
    val isDark = isSystemInDarkTheme()
    return when {
        percentage >= 75 -> if (isDark) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFF00C853).copy(alpha = 0.12f)
        percentage >= 65 -> if (isDark) Color(0xFFFFEA00).copy(alpha = 0.15f) else Color(0xFFFFD600).copy(alpha = 0.12f)
        else -> if (isDark) Color(0xFFFF5252).copy(alpha = 0.18f) else Color(0xFFFF1744).copy(alpha = 0.14f)
    }
}

private fun formatTimestamp(timestamp: Long): String = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun DashboardTile(
    title: String, subtitle: String, icon: ImageVector, iconTint: Color,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    GlassListCard(modifier = modifier.clickable(onClick = onClick), shape = GlassCardShapeSmall) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(icon, title, tint = iconTint, modifier = Modifier.size(24.dp))
        }
    }
}
