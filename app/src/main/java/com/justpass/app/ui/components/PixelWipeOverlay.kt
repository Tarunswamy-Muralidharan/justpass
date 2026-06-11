package com.justpass.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.max

/**
 * Pixel-wipe reveal between two screens. Renders the destination screen
 * inside [content] and progressively masks it to a growing/shrinking grid
 * of cells radiating from [originX] / [originY]. Outside the mask the
 * parent's underlying composable shows through, so the wipe acts as a
 * *true* reveal — every cell that turns on uncovers a piece of the next
 * page rather than first painting a black curtain and then yanking it
 * away.
 *
 * The whisper / spark / bloom layers from the original implementation are
 * preserved on top of the reveal for the soft white leading edge.
 *
 * Grid is 20 × 38 — twice the density of the spec's 14 × 26 so the cells
 * read as smaller and sharper on a phone screen.
 */
@Composable
fun PixelWipeOverlay(
    contracting: Boolean = false,
    originX: Float = 0.5f,
    originY: Float = 0.5f,
    durationMillis: Int = if (contracting) 700 else 900,
    onFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(contracting, durationMillis) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        )
        onFinished?.invoke()
    }

    val cols = 20
    val rows = 38
    val spread = 0.55f
    val cellDur = if (contracting) 0.30f else 0.32f

    // Reused across frames (rewind keeps the internal storage) — the mask is
    // rebuilt ~60×/s, so per-frame Path allocation churn shows up as jank.
    val maskPath = remember { Path() }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Reveal layer ── destination clipped to the union of all visible
        // cells. Outside the cells the parent screen shows through.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    onDrawWithContent {
                        val p = progress.value
                        // Every cell settled (expanding endgame) → mask covers
                        // the whole screen; skip the clip entirely.
                        if (!contracting && p >= spread + cellDur) {
                            this@onDrawWithContent.drawContent()
                            return@onDrawWithContent
                        }
                        maskPath.rewind()
                        buildCellMask(
                            path = maskPath,
                            progress = p,
                            cols = cols,
                            rows = rows,
                            originX = originX,
                            originY = originY,
                            contracting = contracting,
                            spread = spread,
                            cellDur = cellDur,
                            canvasSize = size,
                        )
                        clipPath(maskPath) {
                            this@onDrawWithContent.drawContent()
                        }
                    }
                }
        ) {
            content()
        }

        // ── Whisper edges ── thin white halo on cells in their early-life
        // phase. Drawn after the reveal so it sits on top of the freshly
        // exposed destination pixels and reads as a soft leading edge.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawWhispers(
                progress = progress.value,
                cols = cols,
                rows = rows,
                originX = originX,
                originY = originY,
                contracting = contracting,
                spread = spread,
                cellDur = cellDur,
            )
            drawSpark(progress.value, originX, originY, contracting)
            drawBloom(progress.value, originX, originY, contracting)
        }
    }
}

// ── Mask construction ────────────────────────────────────────────────────────

private fun buildCellMask(
    path: Path,
    progress: Float,
    cols: Int,
    rows: Int,
    originX: Float,
    originY: Float,
    contracting: Boolean,
    spread: Float,
    cellDur: Float,
    canvasSize: Size,
) {
    val cellW = canvasSize.width / cols
    val cellH = canvasSize.height / rows
    val originCol = originX * cols
    val originRow = originY * rows
    val maxDist = hypot(
        max(originCol, cols - originCol),
        max(originRow, rows - originRow),
    )

    // Per-frame cost lives here: the path is fed to clipPath, and Skia's clip
    // cost scales with subpath count. Two reductions vs the naive 760-cell
    // version:
    //  1. Fully-on cells (settled at scale 1.05 / corner 0) are merged into a
    //     single rect per contiguous run in each row. Within a row the fully-
    //     on set {dist ≤ threshold} is one contiguous span, and 1.05-scaled
    //     neighbours overlap, so the union is exactly one rect.
    //  2. Transitioning cells use a plain rect once the corner radius decays
    //     below visibility (< 4% of a ~25 px cell ≈ 1 px) — rounded corners
    //     are conic subpaths and dominate clip cost.
    for (r in 0 until rows) {
        var runStart = -1 // first column of the current fully-on run
        for (c in 0 until cols) {
            val dist = hypot(c - originCol, r - originRow)
            val ratio = dist / maxDist
            val phaseStart = if (contracting) (1f - ratio) * spread else ratio * spread
            val rawP = (progress - phaseStart) / cellDur

            // Fully on: expanding cells that finished, contracting cells that
            // haven't started. Both sit at scale 1.05 / corner 0.
            val fullyOn = if (contracting) rawP < 0f else rawP >= 1f
            if (fullyOn) {
                if (runStart < 0) runStart = c
                continue
            }
            if (runStart >= 0) {
                addFullRun(path, runStart, c - 1, r, cellW, cellH)
                runStart = -1
            }

            // Off: expanding not started / contracting finished.
            val transitioning = if (contracting) rawP < 1f else rawP > 0f
            if (!transitioning) continue

            val shape = cellShape(rawP.coerceIn(0f, 1f), contracting)
            val scale = shape.first
            val corner = shape.second
            if (scale <= 0f) continue
            val cx = c * cellW + cellW / 2f
            val cy = r * cellH + cellH / 2f
            val halfW = cellW * scale / 2f
            val halfH = cellH * scale / 2f
            val rect = Rect(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
            if (corner < 0.04f) {
                path.addRect(rect)
            } else {
                val cornerPx = max(halfW, halfH) * 2f * corner
                path.addRoundRect(RoundRect(rect, CornerRadius(cornerPx, cornerPx)))
            }
        }
        if (runStart >= 0) addFullRun(path, runStart, cols - 1, r, cellW, cellH)
    }
}

/** One rect covering columns [c0..c1] of row [r] at the settled 1.05 scale. */
private fun addFullRun(path: Path, c0: Int, c1: Int, r: Int, cellW: Float, cellH: Float) {
    val overhang = 0.05f / 2f // settled cells render at scale 1.05, centred
    path.addRect(
        Rect(
            left = c0 * cellW - cellW * overhang,
            top = r * cellH - cellH * overhang,
            right = (c1 + 1) * cellW + cellW * overhang,
            bottom = (r + 1) * cellH + cellH * overhang,
        )
    )
}

/**
 * Returns (scale, cornerPct) for a cell at local progress [p].
 *
 *  Expand (open):   p 0..0.35 grow scale 0 → 1, corner 50% → 18%
 *                   p 0.35..1 settle scale 1 → 1.05, corner 18% → 0%
 *
 *  Contract (close, mirrored): p 0..0.5 shrink stays mostly full
 *                              p 0.5..1 collapses back through the white dot
 */
private fun cellShape(p: Float, contracting: Boolean): Pair<Float, Float> {
    return if (contracting) {
        if (p < 0.5f) {
            val t = p / 0.5f
            Pair(1.05f - t * 0.05f, t * 0.18f)
        } else {
            val t = (p - 0.5f) / 0.5f
            Pair(1f - t, 0.18f + t * 0.32f)
        }
    } else {
        if (p < 0.35f) {
            val t = p / 0.35f
            Pair(t, 0.50f - t * 0.32f)
        } else {
            val t = (p - 0.35f) / 0.65f
            Pair(1f + t * 0.05f, 0.18f * (1f - t))
        }
    }
}

// ── Whisper / spark / bloom ──────────────────────────────────────────────────

private fun DrawScope.drawWhispers(
    progress: Float,
    cols: Int,
    rows: Int,
    originX: Float,
    originY: Float,
    contracting: Boolean,
    spread: Float,
    cellDur: Float,
) {
    val cellW = size.width / cols
    val cellH = size.height / rows
    val originCol = originX * cols
    val originRow = originY * rows
    val maxDist = hypot(
        max(originCol, cols - originCol),
        max(originRow, rows - originRow),
    )

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val dist = hypot(c - originCol.toFloat(), r - originRow.toFloat())
            val ratio = dist / maxDist
            val phaseStart = if (contracting) (1f - ratio) * spread else ratio * spread
            val rawP = (progress - phaseStart) / cellDur
            // Whispers are only visible during the cell's transition phase.
            val p = rawP.coerceIn(0f, 1f)
            if (rawP <= 0f || rawP >= 1f) continue

            val whisperAlpha = if (contracting) {
                if (p < 0.5f) {
                    // Cell starting to fade — gentle white shimmer rising.
                    p / 0.5f * 0.20f
                } else {
                    // Cell collapsing toward white dot.
                    0.20f + (p - 0.5f) / 0.5f * 0.30f
                }
            } else {
                if (p < 0.35f) {
                    // Cell forming — strong white whisper.
                    0.55f - p / 0.35f * 0.30f
                } else if (p < 0.55f) {
                    // Cell hardening — whisper fades away as the destination
                    // pixels show through.
                    0.25f * (1f - (p - 0.35f) / 0.20f)
                } else 0f
            }
            if (whisperAlpha <= 0f) continue

            val (scale, cornerPct) = cellShape(p, contracting)
            if (scale <= 0f) continue
            val cx = c * cellW + cellW / 2f
            val cy = r * cellH + cellH / 2f
            val drawW = cellW * scale
            val drawH = cellH * scale
            val cornerPx = max(drawW, drawH) * cornerPct
            drawRoundRect(
                color = Color.White.copy(alpha = whisperAlpha.coerceIn(0f, 1f)),
                topLeft = Offset(cx - drawW / 2f, cy - drawH / 2f),
                size = Size(drawW, drawH),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
            )
        }
    }
}

private fun DrawScope.drawSpark(
    progress: Float,
    originX: Float,
    originY: Float,
    contracting: Boolean,
) {
    if (contracting) return
    val sparkProgress = (progress / 0.25f).coerceIn(0f, 1f)
    if (sparkProgress <= 0f) return
    val sparkAlpha = if (sparkProgress < 0.6f) sparkProgress / 0.6f * 0.9f
    else 0.9f * (1f - (sparkProgress - 0.6f) / 0.4f)
    val sparkScale = 1f + sparkProgress * 1.5f
    val cx = originX * size.width
    val cy = originY * size.height
    val r = 4.dp.toPx() * sparkScale
    drawCircle(Color.White.copy(alpha = sparkAlpha.coerceIn(0f, 0.9f)), r, Offset(cx, cy))
    drawCircle(Color.White.copy(alpha = sparkAlpha.coerceIn(0f, 0.9f) * 0.5f), r * 2f, Offset(cx, cy))
}

private fun DrawScope.drawBloom(
    progress: Float,
    originX: Float,
    originY: Float,
    contracting: Boolean,
) {
    val bloomProgress = if (contracting) {
        (progress / 0.7f).coerceIn(0f, 1f)
    } else {
        ((progress - 0.50f) / 0.50f).coerceIn(0f, 1f)
    }
    if (bloomProgress <= 0f) return
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
                Color.Transparent,
            ),
            center = Offset(originX * size.width, originY * size.height),
            radius = max(size.width, size.height) * 0.8f,
        ),
        size = size,
    )
}
