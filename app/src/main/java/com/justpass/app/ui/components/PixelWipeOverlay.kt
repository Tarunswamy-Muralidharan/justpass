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

                // Skip cells that have already completed (so the area can show
                // through to whatever's underneath the curtain).
                if (contracting && rawP >= 1f) continue
                if (!contracting && rawP <= 0f) continue

                // For contracting cells that haven't started yet (rawP < 0),
                // render in the "fully filled" state so the entire screen is
                // covered the instant the wipe begins. The spec calls this the
                // "px-out 0%" keyframe: black opaque, scale 1.05.
                val p = rawP.coerceIn(0f, 1f)
                val state = cellState(p, contracting)
                if (state.scale <= 0f) continue

                val cx = c * cellW + cellW / 2f
                val cy = r * cellH + cellH / 2f
                val drawW = cellW * state.scale
                val drawH = cellH * state.scale
                val corner = max(drawW, drawH) * state.cornerPct

                // Outer white glow (the soft "whisper" leading edge).
                if (state.glowAlpha > 0f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = state.glowAlpha),
                        topLeft = Offset(cx - drawW * 0.6f, cy - drawH * 0.6f),
                        size = androidx.compose.ui.geometry.Size(drawW * 1.2f, drawH * 1.2f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
                    )
                }
                // Solid black base — drawn first so the white overlay sits
                // on top during the whisper phase but the cell still reads
                // as opaque black once the wipe completes.
                if (state.blackAlpha > 0f) {
                    drawRoundRect(
                        color = Color.Black.copy(alpha = state.blackAlpha),
                        topLeft = Offset(cx - drawW / 2f, cy - drawH / 2f),
                        size = androidx.compose.ui.geometry.Size(drawW, drawH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
                    )
                }
                // White whisper overlay.
                if (state.whiteAlpha > 0f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = state.whiteAlpha),
                        topLeft = Offset(cx - drawW / 2f, cy - drawH / 2f),
                        size = androidx.compose.ui.geometry.Size(drawW, drawH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
                    )
                }
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

/**
 * Per-cell visual state. Cells are rendered as two layers stacked: a black
 * base (the "solid" curtain that hides what's underneath once filled) and a
 * white overlay (the soft "whisper" leading edge that flashes as the cell
 * grows/shrinks). [glowAlpha] is the larger halo behind both layers.
 */
private data class CellState(
    val scale: Float,
    val blackAlpha: Float,
    val whiteAlpha: Float,
    val cornerPct: Float,
    val glowAlpha: Float
)

/**
 * Translates one cell's normalised local progress [p] (0..1) into the spec
 * px-in / px-out keyframes from TRANSITION_SPEC.md § 3.
 *
 * Expand (px-in):
 *   0%   scale 0    radius 50%  white α 0.55  black α 0     glow 0.35
 *   35%  scale 1.0  radius 18%  white α 0.22  black α 0.30  glow 0.15
 *   100% scale 1.05 radius 0    white α 0     black α 1.0   glow 0
 *
 * Contract (px-out) — mirror, ending as a small white dot:
 *   0%   scale 1.05 radius 0    white α 0     black α 1.0   glow 0
 *   50%  scale 1.0  radius 18%  white α 0.20  black α 0.30  glow 0.20
 *   100% scale 0    radius 50%  white α 0.55  black α 0     glow 0.30
 */
private fun DrawScope.cellState(p: Float, contracting: Boolean): CellState {
    return if (contracting) {
        if (p < 0.5f) {
            val t = p / 0.5f
            CellState(
                scale = 1.05f - t * 0.05f,                  // 1.05 → 1.0
                blackAlpha = 1f - t * 0.70f,                // 1.0  → 0.30
                whiteAlpha = t * 0.20f,                     // 0    → 0.20
                cornerPct = t * 0.18f,                      // 0    → 0.18
                glowAlpha = t * 0.20f,                      // 0    → 0.20
            )
        } else {
            val t = (p - 0.5f) / 0.5f
            CellState(
                scale = 1f - t,                             // 1.0  → 0
                blackAlpha = 0.30f * (1f - t),              // 0.30 → 0
                whiteAlpha = 0.20f + t * 0.35f,             // 0.20 → 0.55
                cornerPct = 0.18f + t * 0.32f,              // 0.18 → 0.50
                glowAlpha = 0.20f + t * 0.10f,              // 0.20 → 0.30
            )
        }
    } else {
        if (p < 0.35f) {
            val t = p / 0.35f
            CellState(
                scale = t,                                  // 0    → 1.0
                blackAlpha = 0f,                            // 0    → 0
                whiteAlpha = 0.55f - t * 0.33f,             // 0.55 → 0.22
                cornerPct = 0.50f - t * 0.32f,              // 0.50 → 0.18
                glowAlpha = 0.35f - t * 0.20f,              // 0.35 → 0.15
            )
        } else {
            val t = (p - 0.35f) / 0.65f
            CellState(
                scale = 1f + t * 0.05f,                     // 1.0  → 1.05
                blackAlpha = t,                             // 0    → 1.0
                whiteAlpha = 0.22f * (1f - t),              // 0.22 → 0
                cornerPct = 0.18f * (1f - t),               // 0.18 → 0
                glowAlpha = 0.15f * (1f - t),               // 0.15 → 0
            )
        }
    }
}
