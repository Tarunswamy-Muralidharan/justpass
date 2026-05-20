package com.justpass.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DotMatrixBolt(
    modifier: Modifier = Modifier,
    canvasSize: Dp = 56.dp,
    animated: Boolean = true,
    cycleMs: Int = 4200
) {
    val transition = rememberInfiniteTransition(label = "bolt")
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(cycleMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "boltCycle"
    )

    Canvas(modifier = modifier.size(canvasSize)) {
        val cellSize = size.width / 16f
        val rectSize = cellSize * 0.78f
        val inset = (cellSize - rectSize) / 2f
        val radius = rectSize * 0.08f

        for (r in 0..15) {
            for (c in 0..15) {
                val x = c * cellSize + inset
                val y = r * cellSize + inset
                val color = cellColor(c, r, if (animated) cycle else 0f)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(rectSize, rectSize),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
                )
            }
        }
    }
}

private fun cellColor(c: Int, r: Int, cycle: Float): Color {
    val key = "$c,$r"
    val boltIndex = boltMap[key]
    val sparkDelay = sparkMap[key]

    return when {
        boltIndex != null -> boltColor(boltIndex, cycle)
        sparkDelay != null -> sparkColor(sparkDelay, cycle)
        else -> Color.White.copy(alpha = 0.05f)
    }
}

// Cycle length must match the call site — SPEC_FOR_KIMI § 3 uses 4.2 s, with
// a per-cell delay of 0.035 s × index. We translate that into a phase offset
// here so the keyframe math works for any cycleMs (smaller cycle = same
// shape, just faster).
private const val CYCLE_SECONDS = 4.2f
private const val PER_CELL_DELAY_SEC = 0.035f

/**
 * Bolt keyframes from SPEC_FOR_KIMI § 3:
 *   0%, 30%  → α 0.07
 *   45%      → α 0.95
 *   50%-62%  → α 1.00
 *   78%      → α 0.18
 *   100%     → α 0.07
 */
private fun boltColor(index: Int, cycle: Float): Color {
    val phaseShift = (index * PER_CELL_DELAY_SEC) / CYCLE_SECONDS
    val raw = (cycle - phaseShift) % 1f
    val t = if (raw < 0f) raw + 1f else raw
    val alpha = when {
        t < 0.30f -> 0.07f
        t < 0.45f -> 0.07f + (t - 0.30f) / 0.15f * (0.95f - 0.07f)
        t < 0.50f -> 0.95f + (t - 0.45f) / 0.05f * (1.00f - 0.95f)
        t < 0.62f -> 1.00f
        t < 0.78f -> 1.00f - (t - 0.62f) / 0.16f * (1.00f - 0.18f)
        else      -> 0.18f - (t - 0.78f) / 0.22f * (0.18f - 0.07f)
    }
    return Color.White.copy(alpha = alpha.coerceIn(0f, 1f))
}

/**
 * Spark keyframes from SPEC_FOR_KIMI § 3 — quick flash near 50 % phase.
 *   0 / 47 / 53 / 100 % → α 0.05
 *   50 %                → α 0.85
 */
private fun sparkColor(delaySec: Float, cycle: Float): Color {
    val phaseShift = delaySec / CYCLE_SECONDS
    val raw = (cycle - phaseShift) % 1f
    val t = if (raw < 0f) raw + 1f else raw
    val alpha = when {
        t < 0.47f -> 0.05f
        t < 0.50f -> 0.05f + (t - 0.47f) / 0.03f * (0.85f - 0.05f)
        t < 0.53f -> 0.85f - (t - 0.50f) / 0.03f * (0.85f - 0.05f)
        else      -> 0.05f
    }
    return Color.White.copy(alpha = alpha.coerceIn(0f, 1f))
}

// Bolt cell coords from SPEC_FOR_KIMI § 3 — listed top → bottom; the index
// in this list becomes the charge order (lower index lights first).
private val boltMap: Map<String, Int> = listOf(
    9 to 2, 10 to 2,
    8 to 3, 9 to 3, 10 to 3,
    7 to 4, 8 to 4, 9 to 4,
    6 to 5, 7 to 5, 8 to 5,
    5 to 6, 6 to 6, 7 to 6, 8 to 6, 9 to 6, 10 to 6,
    7 to 7, 8 to 7, 9 to 7,
    6 to 8, 7 to 8, 8 to 8,
    5 to 9, 6 to 9, 7 to 9,
    4 to 10, 5 to 10, 6 to 10,
    4 to 11, 5 to 11,
    4 to 12
).mapIndexed { idx, (c, r) -> "$c,$r" to idx }.toMap()

// Spark coords + per-cell start delay in seconds, also from § 3.
private val sparkMap: Map<String, Float> = mapOf(
    "2,3"  to 0.3f,
    "13,4" to 0.8f,
    "3,8"  to 1.5f,
    "12,10" to 2.1f,
    "1,11" to 2.7f,
    "14,7" to 3.2f,
    "11,2" to 3.6f,
    "2,13" to 3.9f
)
