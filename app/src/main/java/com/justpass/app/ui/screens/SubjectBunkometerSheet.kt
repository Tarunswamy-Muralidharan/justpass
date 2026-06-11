package com.justpass.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { selectedDates.clear() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Clear days", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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

    // ── Switchable gauge styles ──
    val context = LocalContext.current
    val prefs = remember { com.justpass.app.data.local.SecurePreferences.getInstance(context) }
    var gaugeStyle by remember { mutableStateOf(BunkGaugeStyle.fromString(prefs.bunkGaugeStyle)) }
    val haptics = LocalHapticFeedback.current

    Crossfade(
        targetState = gaugeStyle,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "gaugeStyle"
    ) { style ->
        when (style) {
            BunkGaugeStyle.CLASSIC -> ClassicGauge(projection.projectedPercentage, attendanceTarget, resultColor)
            BunkGaugeStyle.NEON -> NeonGauge(projection.projectedPercentage, attendanceTarget, resultColor)
            BunkGaugeStyle.LIQUID -> LiquidGauge(projection.projectedPercentage, attendanceTarget, resultColor)
            BunkGaugeStyle.COMET -> CometGauge(projection.projectedPercentage, attendanceTarget, resultColor)
        }
    }
    Spacer(Modifier.height(6.dp))
    GaugeStyleSelector(gaugeStyle, accent) { picked ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        gaugeStyle = picked
        prefs.bunkGaugeStyle = picked.name
    }
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

/* ═══════════════════════════ Gauge styles ═══════════════════════════ */

enum class BunkGaugeStyle(val label: String) {
    CLASSIC("Classic"), NEON("Neon"), LIQUID("Liquid"), COMET("Comet");

    companion object {
        fun fromString(s: String): BunkGaugeStyle = entries.firstOrNull { it.name == s } ?: CLASSIC
    }
}

/** Pill row to switch gauge styles. Selection persists across opens. */
@Composable
private fun GaugeStyleSelector(selected: BunkGaugeStyle, accent: Color, onPick: (BunkGaugeStyle) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        BunkGaugeStyle.entries.forEach { style ->
            val isSel = style == selected
            val bg by animateFloatAsState(if (isSel) 1f else 0f, tween(250), label = "selBg")
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.10f + bg * 0.85f))
                    .clickable { if (!isSel) onPick(style) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    style.label, fontSize = 11.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSel) Color.Black else accent
                )
            }
        }
    }
}

/** Shared spring-physics gauge value: overshoots slightly then settles — feels mechanical. */
@Composable
private fun rememberGaugeValue(projected: Double): Animatable<Float, *> {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(projected) {
        anim.animateTo(
            projected.toFloat().coerceIn(0f, 100f),
            animationSpec = spring(dampingRatio = 0.58f, stiffness = 65f)
        )
    }
    return anim
}

/** Spectrum used by the dials: danger red → caution amber → safe green. */
private val GaugeSpectrum = listOf(Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFC400), Color(0xFF00E676))

private fun spectrumAt(frac: Float): Color {
    val f = frac.coerceIn(0f, 1f) * (GaugeSpectrum.size - 1)
    val i = f.toInt().coerceAtMost(GaugeSpectrum.size - 2)
    return lerp(GaugeSpectrum[i], GaugeSpectrum[i + 1], f - i)
}

/**
 * Style 1 — CLASSIC+: the original 270° speedometer, refined. Sweep-gradient
 * spectrum dial, spring needle with overshoot, glowing arc tip, illuminated ticks.
 */
@Composable
private fun ClassicGauge(projected: Double, target: Double, color: Color) {
    val anim = rememberGaugeValue(projected)
    val track = Color.White.copy(alpha = 0.12f)
    val dimTick = Color.White.copy(alpha = 0.20f)
    val labelArgb = Color.White.copy(alpha = 0.65f).toArgb()

    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        Box(modifier = Modifier.fillMaxSize().drawBehind {
            val start = 135f
            val total = 270f
            val cx = size.width / 2f
            val cy = size.height * 0.54f
            val R = minOf(size.width / 2f, cy) - 30f
            val arcStroke = 12f
            val frac = (anim.value / 100f).coerceIn(0f, 1f)
            val valAngle = start + total * frac
            val center = androidx.compose.ui.geometry.Offset(cx, cy)
            val topLeft = androidx.compose.ui.geometry.Offset(cx - R, cy - R)
            val box = androidx.compose.ui.geometry.Size(R * 2, R * 2)

            // Spectrum sweep brush — canvas rotated so sweep 0° aligns with arc start.
            val sweepBrush = Brush.sweepGradient(
                0.00f to GaugeSpectrum[0], 0.28f to GaugeSpectrum[1],
                0.50f to GaugeSpectrum[2], 0.75f to GaugeSpectrum[3],
                center = center
            )
            drawArc(track, start, total, false, topLeft, box, style = Stroke(arcStroke, cap = StrokeCap.Round))
            rotate(degrees = start, pivot = center) {
                // soft bloom underneath, then crisp arc
                drawArc(sweepBrush, 0f, total * frac, false, topLeft, box,
                    style = Stroke(arcStroke * 2.4f, cap = StrokeCap.Round), alpha = 0.18f)
                drawArc(sweepBrush, 0f, total * frac, false, topLeft, box,
                    style = Stroke(arcStroke, cap = StrokeCap.Round))
            }

            // ticks
            val ticks = 40
            for (i in 0..ticks) {
                val tFrac = i / ticks.toFloat()
                val a = start + total * tFrac
                val major = i % 4 == 0
                val len = if (major) 14f else 7f
                val rOut = R - arcStroke / 2 - 6f
                val rIn = rOut - len
                val rad = Math.toRadians(a.toDouble())
                val tc = if (tFrac <= frac) spectrumAt(tFrac) else dimTick
                drawLine(tc,
                    androidx.compose.ui.geometry.Offset((cx + rIn * Math.cos(rad)).toFloat(), (cy + rIn * Math.sin(rad)).toFloat()),
                    androidx.compose.ui.geometry.Offset((cx + rOut * Math.cos(rad)).toFloat(), (cy + rOut * Math.sin(rad)).toFloat()),
                    strokeWidth = if (major) 3f else 2f, cap = StrokeCap.Round)
            }

            // glowing tip
            val tipRad = Math.toRadians(valAngle.toDouble())
            val tipPos = androidx.compose.ui.geometry.Offset(
                (cx + R * Math.cos(tipRad)).toFloat(), (cy + R * Math.sin(tipRad)).toFloat())
            val tipColor = spectrumAt(frac)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(tipColor.copy(alpha = 0.75f), Color.Transparent),
                    center = tipPos, radius = 30f),
                radius = 30f, center = tipPos)
            drawCircle(Color.White, radius = 4.5f, center = tipPos)

            // target marker + label
            val ta = start + total * (target / 100f).toFloat()
            val tRad = Math.toRadians(ta.toDouble())
            drawLine(Color.White,
                androidx.compose.ui.geometry.Offset((cx + (R - arcStroke / 2 - 18f) * Math.cos(tRad)).toFloat(), (cy + (R - arcStroke / 2 - 18f) * Math.sin(tRad)).toFloat()),
                androidx.compose.ui.geometry.Offset((cx + (R + 5f) * Math.cos(tRad)).toFloat(), (cy + (R + 5f) * Math.sin(tRad)).toFloat()),
                strokeWidth = 4f, cap = StrokeCap.Round)
            val paint = android.graphics.Paint().apply {
                this.color = labelArgb; textSize = 27f
                textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText("${target.roundToInt()}",
                (cx + (R + 24f) * Math.cos(tRad)).toFloat(), (cy + (R + 24f) * Math.sin(tRad)).toFloat() + 9f, paint)

            // needle + hub
            val nLen = R - arcStroke - 16f
            val tip = androidx.compose.ui.geometry.Offset((cx + nLen * Math.cos(tipRad)).toFloat(), (cy + nLen * Math.sin(tipRad)).toFloat())
            val tail = androidx.compose.ui.geometry.Offset((cx - 20f * Math.cos(tipRad)).toFloat(), (cy - 20f * Math.sin(tipRad)).toFloat())
            drawLine(tipColor, tail, tip, strokeWidth = 6f, cap = StrokeCap.Round)
            drawCircle(tipColor, radius = 13f, center = center)
            drawCircle(Color(0xFF15202E), radius = 7.5f, center = center)
            drawCircle(tipColor, radius = 3.5f, center = center)
        })
        GaugeReadout(anim.value, target, color, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Style 2 — NEON: minimal full ring with a cyan→violet→pink gradient sweep,
 * triple-pass bloom, and a pulsing orb riding the leading edge.
 */
@Composable
private fun NeonGauge(projected: Double, target: Double, color: Color) {
    val anim = rememberGaugeValue(projected)
    val pulse by rememberInfiniteTransition(label = "neonPulse").animateFloat(
        initialValue = 0.80f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val neon = listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF), Color(0xFFFF4081))

    Box(modifier = Modifier.fillMaxWidth().height(210.dp)) {
        Box(modifier = Modifier.fillMaxSize().drawBehind {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val R = minOf(cx, cy) - 26f
            val center = androidx.compose.ui.geometry.Offset(cx, cy)
            val frac = (anim.value / 100f).coerceIn(0f, 1f)
            val topLeft = androidx.compose.ui.geometry.Offset(cx - R, cy - R)
            val box = androidx.compose.ui.geometry.Size(R * 2, R * 2)
            val brush = Brush.sweepGradient(
                0.00f to neon[0], 0.45f to neon[1], 0.90f to neon[2], 1.00f to neon[0],
                center = center
            )
            // track
            drawCircle(Color.White.copy(alpha = 0.08f), radius = R, center = center, style = Stroke(13f))
            rotate(degrees = -90f, pivot = center) {
                // bloom passes widest→core
                drawArc(brush, 0f, 360f * frac, false, topLeft, box, style = Stroke(34f, cap = StrokeCap.Round), alpha = 0.13f)
                drawArc(brush, 0f, 360f * frac, false, topLeft, box, style = Stroke(22f, cap = StrokeCap.Round), alpha = 0.28f)
                drawArc(brush, 0f, 360f * frac, false, topLeft, box, style = Stroke(11f, cap = StrokeCap.Round))
            }
            // target tick on the ring
            val tRad = Math.toRadians((-90f + 360f * (target / 100f)).toDouble())
            drawLine(Color.White.copy(alpha = 0.85f),
                androidx.compose.ui.geometry.Offset((cx + (R - 12f) * Math.cos(tRad)).toFloat(), (cy + (R - 12f) * Math.sin(tRad)).toFloat()),
                androidx.compose.ui.geometry.Offset((cx + (R + 12f) * Math.cos(tRad)).toFloat(), (cy + (R + 12f) * Math.sin(tRad)).toFloat()),
                strokeWidth = 3.5f, cap = StrokeCap.Round)
            // pulsing orb at leading edge
            val oRad = Math.toRadians((-90f + 360f * frac).toDouble())
            val orb = androidx.compose.ui.geometry.Offset((cx + R * Math.cos(oRad)).toFloat(), (cy + R * Math.sin(oRad)).toFloat())
            val orbColor = lerp(neon[0], neon[2], frac)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(orbColor.copy(alpha = 0.8f), Color.Transparent),
                    center = orb, radius = 26f * pulse),
                radius = 26f * pulse, center = orb)
            drawCircle(Color.White, radius = 5f, center = orb)
        })
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("${String.format("%.1f", anim.value)}%", fontSize = 36.sp, fontWeight = FontWeight.Black, color = color)
            Text("target ${target.roundToInt()}%", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

/**
 * Style 3 — LIQUID: circular vessel filling to the projected %. Two offset sine
 * waves slosh continuously; bubbles rise through the fill; dashed target line.
 */
@Composable
private fun LiquidGauge(projected: Double, target: Double, color: Color) {
    val anim = rememberGaugeValue(projected)
    val transition = rememberInfiniteTransition(label = "liquid")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase"
    )
    val bubbleT by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "bubbles"
    )

    Box(modifier = Modifier.fillMaxWidth().height(210.dp)) {
        Box(modifier = Modifier.fillMaxSize().drawBehind {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val R = minOf(cx, cy) - 20f
            val center = androidx.compose.ui.geometry.Offset(cx, cy)
            val frac = (anim.value / 100f).coerceIn(0f, 1f)
            val levelY = cy + R - 2 * R * frac

            val vessel = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(cx - R, cy - R, cx + R, cy + R))
            }
            clipPath(vessel) {
                // back wave (lighter, phase-shifted)
                drawWave(levelY + 4f, phase + 2.2f, 7f, size.width,
                    color.copy(alpha = 0.35f), cy + R)
                // front wave
                drawWave(levelY, phase, 9f, size.width,
                    color.copy(alpha = 0.85f), cy + R, gradient = true, deepColor = color)
                // bubbles inside the fill
                for (i in 0 until 6) {
                    val seedX = (i * 0.37f + 0.13f) % 1f
                    val p = (bubbleT + i * 0.167f) % 1f
                    val bx = cx - R + seedX * 2 * R + kotlin.math.sin(p * 9f + i) * 8f
                    val byStart = cy + R - 6f
                    val by = byStart - p * (byStart - levelY - 8f)
                    if (by > levelY + 6f) {
                        drawCircle(Color.White.copy(alpha = (1f - p) * 0.35f),
                            radius = 2.5f + (i % 3), center = androidx.compose.ui.geometry.Offset(bx, by))
                    }
                }
                // dashed target line inside vessel
                val targetY = cy + R - 2 * R * (target / 100f).toFloat()
                drawLine(Color.White.copy(alpha = 0.55f),
                    androidx.compose.ui.geometry.Offset(cx - R, targetY),
                    androidx.compose.ui.geometry.Offset(cx + R, targetY),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
            // vessel rim
            drawCircle(Color.White.copy(alpha = 0.20f), radius = R, center = center, style = Stroke(3f))
            drawCircle(color.copy(alpha = 0.45f), radius = R + 5f, center = center, style = Stroke(1.5f))
        })
        Column(
            modifier = Modifier.align(Alignment.Center).graphicsLayer { translationY = -10f },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("${String.format("%.1f", anim.value)}%", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("target ${target.roundToInt()}%", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWave(
    levelY: Float, phase: Float, amp: Float, width: Float,
    waveColor: Color, bottomY: Float, gradient: Boolean = false, deepColor: Color = waveColor,
) {
    val path = Path()
    path.moveTo(0f, levelY)
    var x = 0f
    while (x <= width) {
        path.lineTo(x, levelY + kotlin.math.sin(x / width * 2f * Math.PI.toFloat() * 1.6f + phase) * amp)
        x += 8f
    }
    path.lineTo(width, bottomY + 20f)
    path.lineTo(0f, bottomY + 20f)
    path.close()
    if (gradient) {
        drawPath(path, Brush.verticalGradient(
            listOf(waveColor, deepColor.copy(alpha = 0.55f)),
            startY = levelY, endY = bottomY))
    } else {
        drawPath(path, waveColor)
    }
}

/**
 * Style 4 — COMET: 270° dial of discrete segments lighting along the spectrum,
 * with a glowing comet head at the leading edge and a fading trail behind it.
 */
@Composable
private fun CometGauge(projected: Double, target: Double, color: Color) {
    val anim = rememberGaugeValue(projected)
    val shimmer by rememberInfiniteTransition(label = "cometShimmer").animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )

    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        Box(modifier = Modifier.fillMaxSize().drawBehind {
            val start = 135f
            val total = 270f
            val cx = size.width / 2f
            val cy = size.height * 0.54f
            val R = minOf(size.width / 2f, cy) - 28f
            val frac = (anim.value / 100f).coerceIn(0f, 1f)
            val segments = 36
            val segSweep = total / segments

            for (i in 0 until segments) {
                val segFrac = (i + 0.5f) / segments
                val a0 = start + i * segSweep + 1.2f
                val lit = segFrac <= frac
                val headDist = (frac - segFrac) * segments     // segments behind head
                // trail boost: segments just behind the head glow brighter
                val trail = if (lit) kotlin.math.exp(-headDist.coerceAtLeast(0f) * 0.30f) else 0f
                val twinkle = 0.92f + 0.08f * kotlin.math.sin(shimmer + i * 0.7f)
                val segColor = if (lit) spectrumAt(segFrac) else Color.White
                val alpha = if (lit) ((0.55f + 0.45f * trail) * twinkle) else 0.10f
                drawArc(
                    segColor.copy(alpha = alpha.coerceIn(0f, 1f)),
                    a0, segSweep - 2.4f, false,
                    topLeft = androidx.compose.ui.geometry.Offset(cx - R, cy - R),
                    size = androidx.compose.ui.geometry.Size(R * 2, R * 2),
                    style = Stroke(if (lit) 14f + 6f * trail else 10f, cap = StrokeCap.Round)
                )
            }

            // comet head — glow at leading edge
            val headRad = Math.toRadians((start + total * frac).toDouble())
            val head = androidx.compose.ui.geometry.Offset(
                (cx + R * Math.cos(headRad)).toFloat(), (cy + R * Math.sin(headRad)).toFloat())
            val headColor = spectrumAt(frac)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.9f), headColor.copy(alpha = 0.55f), Color.Transparent),
                    center = head, radius = 34f),
                radius = 34f, center = head)

            // target marker
            val tRad = Math.toRadians((start + total * (target / 100f)).toDouble())
            drawLine(Color.White.copy(alpha = 0.9f),
                androidx.compose.ui.geometry.Offset((cx + (R - 16f) * Math.cos(tRad)).toFloat(), (cy + (R - 16f) * Math.sin(tRad)).toFloat()),
                androidx.compose.ui.geometry.Offset((cx + (R + 12f) * Math.cos(tRad)).toFloat(), (cy + (R + 12f) * Math.sin(tRad)).toFloat()),
                strokeWidth = 3.5f, cap = StrokeCap.Round)
        })
        GaugeReadout(anim.value, target, color, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun GaugeReadout(value: Float, target: Double, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier.padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("${String.format("%.1f", value)}%", fontSize = 38.sp, fontWeight = FontWeight.Black, color = color)
        Text("target ${target.roundToInt()}%", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
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
