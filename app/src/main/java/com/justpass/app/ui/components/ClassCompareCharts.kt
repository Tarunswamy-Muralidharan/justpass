package com.justpass.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Reusable charts for the Class Compare screen. All pure Canvas — no
 * external chart lib needed.
 */

/**
 * Semicircular gauge 0..100. Marker positioned at [percentile]. Gradient
 * red → amber → green along the arc.
 */
@Composable
fun PercentileGauge(
    percentile: Int,
    modifier: Modifier = Modifier,
) {
    val pct = percentile.coerceIn(0, 100)
    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(160.dp)
        .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        val w = size.width
        val h = size.height
        val radius = min(w / 2f, h)
        val centerX = w / 2f
        val centerY = h * 0.95f
        val stroke = radius * 0.18f
        // Background arc
        val arcRect = Rect(
            left = centerX - radius + stroke / 2f,
            top = centerY - radius + stroke / 2f,
            right = centerX + radius - stroke / 2f,
            bottom = centerY + radius - stroke / 2f,
        )
        // Background grey track
        drawArc(
            color = Color(0xFF2A3340).copy(alpha = 0.55f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Coloured progress arc — gradient sweep red→green
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFFFF5252),     // red at 0 percentile
                    Color(0xFFFFA63D),     // amber at 50
                    Color(0xFF00E676),     // green at 100
                    Color.Transparent,     // fade after the half-circle
                ),
                center = Offset(centerX, centerY),
            ),
            startAngle = 180f,
            sweepAngle = 180f * (pct / 100f),
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Marker dot
        val markerAngle = (180f + 180f * (pct / 100f)) * PI.toFloat() / 180f
        val markerR = (radius - stroke / 2f) - stroke / 2f
        val markerX = centerX + cos(markerAngle) * (markerR + stroke / 2f)
        val markerY = centerY + sin(markerAngle) * (markerR + stroke / 2f)
        drawCircle(
            color = Color.White,
            radius = stroke * 0.55f,
            center = Offset(markerX, markerY),
        )
        drawCircle(
            color = Color(0xFF14181F),
            radius = stroke * 0.35f,
            center = Offset(markerX, markerY),
        )
    }
}

/**
 * Horizontal bar for one subject: gradient track [min..max], two markers
 * (avg + you), histogram strip below.
 */
@Composable
fun SubjectBar(
    label: String,
    yourMark: Double?,
    avg: Double,
    min: Double,
    max: Double,
    histogram: List<Int>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (yourMark != null) "You ${fmt(yourMark)} · Avg ${fmt(avg)} · Max ${fmt(max)}"
                else "Avg ${fmt(avg)} · Max ${fmt(max)}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
        ) {
            val w = size.width
            val h = size.height
            val range = max - min
            // Track: grey rounded rect with gradient overlay (red → green)
            drawRoundRect(
                color = Color(0xFF2A3340).copy(alpha = 0.55f),
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f),
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFF5252).copy(alpha = 0.55f),
                        Color(0xFFFFA63D).copy(alpha = 0.55f),
                        Color(0xFF00E676).copy(alpha = 0.65f),
                    ),
                ),
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f),
            )
            // Avg marker
            val avgX = w * normalise(avg, min, max)
            drawLine(
                color = Color(0xFFB6CFFF),
                start = Offset(avgX, -2f),
                end = Offset(avgX, h + 2f),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
            // Your marker
            if (yourMark != null) {
                val youX = w * normalise(yourMark, min, max)
                drawLine(
                    color = Color.White,
                    start = Offset(youX, -4f),
                    end = Offset(youX, h + 4f),
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Histogram strip — 10 tiny vertical bars
        if (histogram.isNotEmpty()) {
            val maxCount = histogram.maxOrNull() ?: 1
            // Compute your-bucket index (matches server normalisation)
            val yourBucket = if (yourMark != null && max > min) {
                ((yourMark - min) / (max - min) * 10.0).toInt().coerceIn(0, 9)
            } else -1
            Canvas(modifier = Modifier.fillMaxWidth().height(18.dp)) {
                val w = size.width
                val h = size.height
                val gap = 1.6f
                val barW = (w - gap * (histogram.size - 1)) / histogram.size
                histogram.forEachIndexed { i, count ->
                    val barH = if (maxCount > 0) h * (count.toFloat() / maxCount) else 0f
                    val x = i * (barW + gap)
                    val color = if (i == yourBucket) Color.White
                    else Color(0xFFB6CFFF).copy(alpha = 0.55f)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, h - barH),
                        size = Size(barW, barH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW * 0.3f),
                    )
                }
            }
        }
    }
}

/**
 * 10-bucket histogram for overall avg. Each bucket = 10% band (0-9 → 0-100).
 */
@Composable
fun DistributionHistogram(
    histogram: List<Int>,
    yourPercentile: Int?,
    modifier: Modifier = Modifier,
) {
    val yourBucket = yourPercentile?.let { (it / 10).coerceIn(0, 9) } ?: -1
    val maxCount = histogram.maxOrNull() ?: 1
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        val w = size.width
        val h = size.height
        val gap = 4f
        val barW = (w - gap * (histogram.size - 1)) / histogram.size
        histogram.forEachIndexed { i, count ->
            val barH = if (maxCount > 0) h * (count.toFloat() / maxCount) else 0f
            val x = i * (barW + gap)
            val color = if (i == yourBucket) Color(0xFF00E676)
            else Color(0xFFB6CFFF).copy(alpha = 0.50f)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, h - barH),
                size = Size(barW, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW * 0.25f),
            )
        }
    }
}

private fun normalise(value: Double, min: Double, max: Double): Float {
    if (max <= min) return 0.5f
    return ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f)
}

private fun fmt(value: Double): String =
    if (value == value.toInt().toDouble()) value.toInt().toString()
    else String.format("%.1f", value)
