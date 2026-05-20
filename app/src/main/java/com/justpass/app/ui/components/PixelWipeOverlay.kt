package com.justpass.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.max

@Composable
fun PixelWipeOverlay(
    contracting: Boolean = false,
    originX: Float = 0.5f,
    originY: Float = 0.5f,
    durationMillis: Int = if (contracting) 700 else 900,
    onFinished: (() -> Unit)? = null
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(contracting, durationMillis) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing
            )
        )
        onFinished?.invoke()
    }

    val cols = 14
    val rows = 26
    val spread = 0.55f
    val cellDurOpen = 0.32f
    val cellDurClose = 0.30f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cellW = size.width / cols
        val cellH = size.height / rows
        val originCol = originX * cols
        val originRow = originY * rows
        val maxDist = hypot(
            max(originCol, cols - originCol),
            max(originRow, rows - originRow)
        )

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val dist = hypot(c - originCol, r - originRow)
                val ratio = dist / maxDist
                val delay = ratio * spread
                val cellDur = if (contracting) cellDurClose else cellDurOpen
                val rawP = if (contracting) {
                    (progress.value - (1f - ratio) * spread) / cellDur
                } else {
                    (progress.value - delay) / cellDur
                }
                val p = rawP.coerceIn(0f, 1f)
                if (p <= 0f) continue

                val state = cellState(p, contracting)
                if (state.scale <= 0f) continue

                val cx = c * cellW + cellW / 2f
                val cy = r * cellH + cellH / 2f
                val drawW = cellW * state.scale
                val drawH = cellH * state.scale
                val corner = max(drawW, drawH) * state.cornerPct

                if (state.glowAlpha > 0f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = state.glowAlpha),
                        topLeft = Offset(cx - drawW * 0.6f, cy - drawH * 0.6f),
                        size = androidx.compose.ui.geometry.Size(drawW * 1.2f, drawH * 1.2f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
                    )
                }

                drawRoundRect(
                    color = state.color,
                    topLeft = Offset(cx - drawW / 2f, cy - drawH / 2f),
                    size = androidx.compose.ui.geometry.Size(drawW, drawH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
                )
            }
        }

        val sparkProgress = (progress.value / 0.25f).coerceIn(0f, 1f)
        if (sparkProgress > 0f && !contracting) {
            val sparkP = sparkProgress
            val sparkAlpha = when {
                sparkP < 0.6f -> sparkP / 0.6f * 0.9f
                else -> 0.9f * (1f - (sparkP - 0.6f) / 0.4f)
            }
            val sparkScale = 1f + sparkP * 1.5f
            val sparkPx = originX * size.width
            val sparkPy = originY * size.height
            val sparkRadius = 4.dp.toPx() * sparkScale
            drawCircle(
                color = Color.White.copy(alpha = sparkAlpha.coerceIn(0f, 0.9f)),
                radius = sparkRadius,
                center = Offset(sparkPx, sparkPy)
            )
            drawCircle(
                color = Color.White.copy(alpha = sparkAlpha.coerceIn(0f, 0.9f) * 0.5f),
                radius = sparkRadius * 2f,
                center = Offset(sparkPx, sparkPy)
            )
        }

        val bloomProgress = if (contracting) {
            (progress.value / 0.7f).coerceIn(0f, 1f)
        } else {
            ((progress.value - 0.50f) / 0.50f).coerceIn(0f, 1f)
        }
        if (bloomProgress > 0f) {
            val bloomAlpha = if (contracting) {
                if (bloomProgress < 0.4f) bloomProgress / 0.4f * 0.6f
                else 0.6f * (1f - (bloomProgress - 0.4f) / 0.6f)
            } else {
                when {
                    bloomProgress < 0.36f -> 0f
                    bloomProgress < 0.68f -> (bloomProgress - 0.36f) / 0.32f
                    else -> 1f - (bloomProgress - 0.68f) / 0.32f
                }
            }
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = bloomAlpha * 0.40f),
                        Color.White.copy(alpha = bloomAlpha * 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(originX * size.width, originY * size.height),
                    radius = max(size.width, size.height) * 0.8f
                ),
                size = size
            )
        }
    }
}

private data class CellState(
    val scale: Float,
    val color: Color,
    val cornerPct: Float,
    val glowAlpha: Float
)

private fun DrawScope.cellState(p: Float, contracting: Boolean): CellState {
    return if (contracting) {
        val scale = when {
            p < 0.5f -> 1.05f - p / 0.5f * 0.05f
            else -> 1f - (p - 0.5f) / 0.5f
        }
        var alpha = 0f
        var corner = 0f
        var glow = 0f
        when {
            p < 0.5f -> {
                val t = p / 0.5f
                alpha = 0.20f * t
                corner = 0.18f * t
                glow = 0.20f * t
            }
            else -> {
                val t = (p - 0.5f) / 0.5f
                alpha = 0.20f + t * 0.35f
                corner = 0.18f + t * 0.32f
                glow = 0.20f + t * 0.10f
            }
        }
        CellState(scale, Color.White.copy(alpha = alpha), corner, glow)
    } else {
        val scale = when {
            p < 0.35f -> p / 0.35f
            else -> 1f + (p - 0.35f) / 0.65f * 0.05f
        }
        var alpha = 0f
        var corner = 0f
        var glow = 0f
        when {
            p < 0.35f -> {
                val t = p / 0.35f
                alpha = 0.55f - t * 0.33f
                corner = 0.50f - t * 0.32f
                glow = 0.35f - t * 0.20f
            }
            else -> {
                val t = (p - 0.35f) / 0.65f
                alpha = 0.22f * (1f - t)
                corner = 0f
                glow = 0.15f * (1f - t)
            }
        }
        CellState(scale, Color.White.copy(alpha = alpha), corner, glow)
    }
}
