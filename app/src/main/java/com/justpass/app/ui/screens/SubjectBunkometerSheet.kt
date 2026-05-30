package com.justpass.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.data.model.BunkProjection
import com.justpass.app.data.model.SubjectAttendance
import com.justpass.app.data.model.TimetableResponse
import com.justpass.app.data.model.periodsByWeekdayBySubject
import com.justpass.app.data.model.periodsForSubjectOnDate
import com.justpass.app.data.model.projectSubjectBunk
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Mode B — single-subject Bunkometer with a speedometer gauge that sweeps to the
 * projected %. Slider + 4-week calendar below. A pinned "scroll" hint nudges the
 * user toward the calendar and disappears once they scroll. Offline math.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SubjectBunkometerSheet(
    subject: SubjectAttendance,
    timetable: TimetableResponse?,
    attendanceTarget: Double,
    onDismiss: () -> Unit
) {
    val selectedDates = remember { mutableStateListOf<LocalDate>() }
    var sliderPeriods by remember { mutableFloatStateOf(0f) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val calendarPeriods = remember(selectedDates.toList(), timetable) {
        if (timetable == null) 0
        else selectedDates.sumOf { periodsForSubjectOnDate(subject.courseCode, it, timetable) }
    }
    val periods = if (selectedDates.isNotEmpty()) calendarPeriods else sliderPeriods.roundToInt()
    val projection = remember(periods, subject, attendanceTarget) {
        projectSubjectBunk(subject, periods, attendanceTarget)
    }

    val avgPerDay = remember(timetable, subject.courseCode) {
        val byDay = timetable?.periodsByWeekdayBySubject() ?: return@remember 0.0
        val perDay = (1..6).mapNotNull { byDay[it]?.get(subject.courseCode) }.filter { it > 0 }
        if (perDay.isNotEmpty()) perDay.sum().toDouble() / perDay.size else 0.0
    }
    val budgetDays = if (avgPerDay > 0) (projection.budget / avgPerDay).roundToInt() else 0
    val sliderMax = (projection.budget + 5).coerceIn(10, 60).toFloat()
    val accent = Color(0xFFFF9800)

    // Show the scroll hint while there's room below and the user hasn't scrolled yet.
    val showScrollHint = scrollState.value < 24 && scrollState.maxValue > 60

    // Custom (non-draggable) bottom sheet — a draggable ModalBottomSheet springs/bounces
    // when content fits and the user tries to scroll. Dialog + scrim avoids that.
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val maxSheetHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.92f).dp
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim
            Box(modifier = Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { onDismiss() })
            // Sheet
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(Color(0xFF15202E))
            ) {
                CompositionLocalProvider(androidx.compose.foundation.LocalOverscrollFactory provides null) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp)
                            .verticalScroll(scrollState)
                    ) {
                        // Decorative drag handle
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.size(width = 36.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.25f)))
                        }
                    GaugeHeader(subject, projection, attendanceTarget, budgetDays, periods, accent)

                    Spacer(Modifier.height(10.dp))

                    // ── Slider ──
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Bunk how many periods?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f))
                        Text("$periods", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
                    }
                    Slider(
                        value = if (selectedDates.isNotEmpty()) calendarPeriods.toFloat().coerceAtMost(sliderMax) else sliderPeriods,
                        onValueChange = { selectedDates.clear(); sliderPeriods = it },
                        valueRange = 0f..sliderMax,
                        steps = (sliderMax.toInt() - 1).coerceAtLeast(0),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = accent, activeTrackColor = accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                        )
                    )

                    // ── Calendar ──
                    if (timetable != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Or pick specific days", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f))
                        Spacer(Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                                Text(d, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(4.dp))

                        val today = LocalDate.now()
                        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
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
                                                    isSelected -> accent
                                                    !isDisabled -> accent.copy(alpha = 0.14f)
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
                                                    color = if (isSelected) Color.Black.copy(alpha = 0.7f) else accent)
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
                                    .background(accent.copy(alpha = 0.08f))
                                    .padding(10.dp)
                            ) {
                                Text("Selected days:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent)
                                Spacer(Modifier.height(2.dp))
                                sorted.forEach { d ->
                                    val p = periodsForSubjectOnDate(subject.courseCode, d, timetable)
                                    val dayName = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                                    Text("• ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ($dayName) — $p period${if (p != 1) "s" else ""}",
                                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                                }
                            }
                            TextButton(onClick = { selectedDates.clear() }) {
                                Text("Clear days", fontSize = 12.sp, color = accent)
                            }
                        }
                    }
                    }
                }

                // ── Pinned scroll affordance ──
                val hintAlpha by animateFloatAsState(
                    targetValue = if (showScrollHint) 1f else 0f,
                    animationSpec = tween(250), label = "hintAlpha"
                )
                if (hintAlpha > 0.01f) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .graphicsLayer { alpha = hintAlpha }
                    ) {
                        ScrollDownHint(accent) {
                            scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                        }
                    }
                }
            }
        }
    }
}

/** Bottom-pinned, bouncing "scroll for days" pill over a fade gradient. Tap = scroll down. */
@Composable
private fun ScrollDownHint(accent: Color, onClick: () -> Unit) {
    val bounce = rememberInfiniteTransition(label = "scrollBounce")
    val dy by bounce.animateFloat(
        initialValue = 0f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse), label = "dy"
    )
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF15202E))))
            .padding(top = 28.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(accent)
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Scroll for days & calendar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null, tint = Color.Black,
                modifier = Modifier.size(18.dp).graphicsLayer { translationY = dy }
            )
        }
    }
}

@Composable
private fun GaugeHeader(
    subject: SubjectAttendance, projection: BunkProjection, attendanceTarget: Double,
    budgetDays: Int, periods: Int, accent: Color
) {
    val belowAfter = projection.projectedPercentage < attendanceTarget
    val resultColor = if (belowAfter) Color(0xFFFF5252) else Color(0xFF00E676)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Text("⚡", fontSize = 18.sp) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(subject.courseCode, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subject.courseTitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(16.dp))
    BunkGauge(projection.projectedPercentage, attendanceTarget, resultColor, periods)
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        StatChip("Now", "${String.format("%.1f", projection.currentPercentage)}%", Color.White.copy(alpha = 0.7f))
        Text("  →  ", fontSize = 18.sp, color = Color.White.copy(alpha = 0.4f))
        StatChip(if (periods > 0) "After $periods" else "After 0",
            "${String.format("%.1f", projection.projectedPercentage)}%", resultColor)
    }
    Spacer(Modifier.height(16.dp))
    if (projection.belowTarget) {
        VerdictCard(Color(0xFFFF5252).copy(alpha = 0.14f), Color(0xFFFF8A80),
            "Below your ${attendanceTarget.roundToInt()}% target",
            "Attend ${projection.recoveryPeriods} more period${if (projection.recoveryPeriods != 1) "s" else ""} without absence to climb back to ${attendanceTarget.roundToInt()}%.")
    } else {
        VerdictCard(Color(0xFF00E676).copy(alpha = 0.12f), Color(0xFF00E676),
            "${projection.budget} period${if (projection.budget != 1) "s" else ""} to spare" +
                if (budgetDays > 0) "  ·  ≈ $budgetDays day${if (budgetDays != 1) "s" else ""}" else "",
            "before you drop below ${attendanceTarget.roundToInt()}%.")
    }
}

/**
 * Full 270° speedometer: ticks, sweeping value arc, target marker + label, and a
 * needle from a center hub. Animates 0 → projected on open and on every change.
 */
@Composable
private fun BunkGauge(projected: Double, target: Double, color: Color, periods: Int) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(projected) {
        anim.animateTo(projected.toFloat().coerceIn(0f, 100f),
            animationSpec = tween(850, easing = FastOutSlowInEasing))
    }
    val track = Color.White.copy(alpha = 0.14f)
    val dimTick = Color.White.copy(alpha = 0.22f)
    val labelArgb = Color.White.copy(alpha = 0.65f).toArgb()

    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        Box(modifier = Modifier.fillMaxSize().drawBehind {
            val start = 135f          // bottom-left
            val total = 270f          // 90° gap at the bottom
            val cx = size.width / 2f
            val cy = size.height * 0.54f
            val R = minOf(size.width / 2f, cy) - 30f
            val arcStroke = 11f
            val frac = (anim.value / 100f).coerceIn(0f, 1f)
            val valAngle = start + total * frac

            val topLeft = androidx.compose.ui.geometry.Offset(cx - R, cy - R)
            val box = androidx.compose.ui.geometry.Size(R * 2, R * 2)

            // base + value arcs
            drawArc(track, start, total, false, topLeft, box, style = Stroke(arcStroke, cap = StrokeCap.Round))
            drawArc(color, start, total * frac, false, topLeft, box, style = Stroke(arcStroke, cap = StrokeCap.Round))

            // ticks (inside the ring)
            val ticks = 40
            for (i in 0..ticks) {
                val a = start + total * (i / ticks.toFloat())
                val major = i % 4 == 0
                val len = if (major) 14f else 7f
                val rOut = R - arcStroke / 2 - 6f
                val rIn = rOut - len
                val rad = Math.toRadians(a.toDouble())
                val tc = if (i / ticks.toFloat() <= frac) color else dimTick
                drawLine(tc,
                    androidx.compose.ui.geometry.Offset((cx + rIn * Math.cos(rad)).toFloat(), (cy + rIn * Math.sin(rad)).toFloat()),
                    androidx.compose.ui.geometry.Offset((cx + rOut * Math.cos(rad)).toFloat(), (cy + rOut * Math.sin(rad)).toFloat()),
                    strokeWidth = if (major) 3f else 2f, cap = StrokeCap.Round)
            }

            // target marker (white) + number label outside
            val ta = start + total * (target / 100f).toFloat()
            val tRad = Math.toRadians(ta.toDouble())
            val tIn = R - arcStroke / 2 - 18f
            val tOut = R + 5f
            drawLine(Color.White,
                androidx.compose.ui.geometry.Offset((cx + tIn * Math.cos(tRad)).toFloat(), (cy + tIn * Math.sin(tRad)).toFloat()),
                androidx.compose.ui.geometry.Offset((cx + tOut * Math.cos(tRad)).toFloat(), (cy + tOut * Math.sin(tRad)).toFloat()),
                strokeWidth = 4f, cap = StrokeCap.Round)
            val lblR = R + 24f
            val paint = android.graphics.Paint().apply {
                this.color = labelArgb; textSize = 27f
                textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText("${target.roundToInt()}",
                (cx + lblR * Math.cos(tRad)).toFloat(), (cy + lblR * Math.sin(tRad)).toFloat() + 9f, paint)

            // needle + hub
            val nLen = R - arcStroke - 16f
            val nRad = Math.toRadians(valAngle.toDouble())
            val tip = androidx.compose.ui.geometry.Offset((cx + nLen * Math.cos(nRad)).toFloat(), (cy + nLen * Math.sin(nRad)).toFloat())
            val tail = androidx.compose.ui.geometry.Offset((cx - 20f * Math.cos(nRad)).toFloat(), (cy - 20f * Math.sin(nRad)).toFloat())
            drawLine(color, tail, tip, strokeWidth = 6f, cap = StrokeCap.Round)
            drawCircle(color, radius = 13f, center = androidx.compose.ui.geometry.Offset(cx, cy))
            drawCircle(Color(0xFF15202E), radius = 7.5f, center = androidx.compose.ui.geometry.Offset(cx, cy))
            drawCircle(color, radius = 3.5f, center = androidx.compose.ui.geometry.Offset(cx, cy))
        })
        // Readout in the lower half
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("${String.format("%.1f", anim.value)}%", fontSize = 38.sp, fontWeight = FontWeight.Black, color = color)
            Text("target ${target.roundToInt()}%", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun VerdictCard(bg: Color, accentColor: Color, headline: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(bg, bg.copy(alpha = bg.alpha * 0.4f))))
            .padding(16.dp)
    ) {
        Text(headline, fontSize = 16.sp, fontWeight = FontWeight.Black, color = accentColor)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
    }
}
