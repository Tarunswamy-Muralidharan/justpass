package com.justpass.app.ui.components.water

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Water state holder + per-frame physics driver. Returned object is
 * mutated in place each frame; the caller passes it to [DrawScope.drawWater]
 * inside their own `Modifier.drawBehind { ... }` block. This avoids the
 * Canvas/Box overlay pattern that conflicts with the LiquidGlassCard's
 * GraphicsLayer + RenderEffect blur compositing.
 *
 * Use:
 *
 * ```
 * val water = rememberWaterState(
 *     fillFraction = attendancePct / 100f,
 *     scrollOffsetPx = scrollState.value.toFloat()
 * )
 * LiquidGlassCard(...) {
 *     Column(modifier = Modifier.drawBehind { drawWater(water) }) { ... }
 * }
 * ```
 */
class WaterState internal constructor(
    internal val physics: WaterPhysics,
    internal val maxFillFraction: Float
) {
    internal var ready: Boolean = false
}

@Composable
fun rememberWaterState(
    fillFraction: Float,
    scrollOffsetPx: Float = 0f,
    nodeCount: Int = 30,
    maxFillFraction: Float = 0.95f,
    active: Boolean = true,
): WaterState {
    val state = remember(nodeCount) {
        WaterState(WaterPhysics(nodeCount = nodeCount), maxFillFraction)
    }
    val gravity by rememberGravity()
    var lastFrameNanos by remember { mutableLongStateOf(0L) }
    var prevScrollOffset by remember { mutableStateOf(scrollOffsetPx) }
    var initialised by remember { mutableStateOf(false) }

    // Smoothly tween the resting waterline so a 0 -> 78 jump on data
    // load doesn't cause a tsunami.
    val animatedFill = remember { Animatable(fillFraction.coerceIn(0f, 1f)) }
    LaunchedEffect(fillFraction) {
        animatedFill.animateTo(
            targetValue = fillFraction.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 800)
        )
    }
    val targetFraction = (1f - animatedFill.value).coerceAtLeast(1f - maxFillFraction)

    LaunchedEffect(Unit) {
        state.physics.reset(targetFraction)
        initialised = true
        state.ready = true
    }
    LaunchedEffect(targetFraction) {
        if (initialised) state.physics.setBase(targetFraction)
    }

    // Tilt feeds gravity.x — phone tipped right makes the water slope down to the right.
    LaunchedEffect(gravity, active) {
        if (active) state.physics.setTilt(gravity.first)
    }

    // Scroll perturbation — clamp delta to avoid runaway impulse on flings.
    LaunchedEffect(active) {
        snapshotFlow { scrollOffsetPx }
            .distinctUntilChanged()
            .collect { offset ->
                val delta = offset - prevScrollOffset
                prevScrollOffset = offset
                if (active && delta != 0f) {
                    val v = (delta / 1500f).coerceIn(-0.012f, 0.012f)
                    state.physics.perturb(v)
                }
            }
    }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            withFrameNanos { now ->
                val dtScale = if (lastFrameNanos == 0L) 1f
                              else ((now - lastFrameNanos) / 16_666_667f).coerceIn(0.5f, 2f)
                lastFrameNanos = now
                state.physics.step(dtScale)
            }
        }
    }
    return state
}

/**
 * Render the water inside the caller's draw scope. The caller is
 * responsible for clipping (use [Modifier.clip] on the parent).
 *
 * Layered for realism:
 *   1. Main fill -- soft cyan -> deep teal vertical gradient, low alpha
 *      everywhere so the LiquidGlass blur underneath shows through.
 *   2. Sub-surface light band -- a brighter strip just under the surface
 *      where light penetrates real shallow water.
 *   3. Side vignette -- darker tint at left + right edges, suggesting
 *      the bowl walls curving in (the "3D-ish" cue without going full 3D).
 *   4. Foam edge -- a thin near-white surface stroke that traces every
 *      wave, looks like the meniscus of real water.
 */
fun DrawScope.drawWater(
    state: WaterState,
    sharedPath: Path = Path(),
    sharedHighlight: Path = Path(),
    sharedSubSurface: Path = Path()
) {
    if (!state.ready) return
    val w = size.width
    val h = size.height
    if (w <= 0 || h <= 0) return
    val positions = state.physics.positions
    val n = positions.size
    if (n < 2) return

    // Build the main water polygon path once + reuse for clipping.
    sharedPath.reset()
    val firstY = positions[0] * h
    sharedPath.moveTo(0f, firstY)
    for (i in 1 until n) {
        val x = (i.toFloat() / (n - 1)) * w
        val y = positions[i] * h
        sharedPath.lineTo(x, y)
    }
    sharedPath.lineTo(w, h)
    sharedPath.lineTo(0f, h)
    sharedPath.close()

    // Layer 1: realistic water tint. Light cyan at the surface, deeper
    // teal at depth. Low alpha throughout so the glass blur reads through.
    val mainBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFB3E5FC).copy(alpha = 0.18f),  // surface — pale cyan
            Color(0xFF4FC3F7).copy(alpha = 0.32f),  // mid — sky blue
            Color(0xFF0277BD).copy(alpha = 0.55f),  // deep — teal-blue
            Color(0xFF01579B).copy(alpha = 0.70f)   // floor — darker teal
        ),
        startY = firstY,
        endY = h
    )
    drawPath(path = sharedPath, brush = mainBrush)

    // Layer 2: sub-surface light band. Trace the surface, then offset
    // down to form a thin polygon that's brighter than the main fill —
    // mimics light penetration in shallow water.
    val bandHeight = (h * 0.06f).coerceAtMost(18f)
    sharedSubSurface.reset()
    sharedSubSurface.moveTo(0f, firstY)
    for (i in 1 until n) {
        val x = (i.toFloat() / (n - 1)) * w
        sharedSubSurface.lineTo(x, positions[i] * h)
    }
    for (i in n - 1 downTo 0) {
        val x = (i.toFloat() / (n - 1)) * w
        sharedSubSurface.lineTo(x, positions[i] * h + bandHeight)
    }
    sharedSubSurface.close()
    val bandBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.20f),
            Color.White.copy(alpha = 0.0f)
        ),
        startY = firstY,
        endY = firstY + bandHeight
    )
    drawPath(path = sharedSubSurface, brush = bandBrush)

    // Layer 3: side vignette — darker bands at left + right edges of
    // the water region only. Clipped via clipPath so it doesn't escape
    // into the dry area above the surface.
    clipPath(sharedPath) {
        val sideShade = Brush.horizontalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.18f),
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = 0.18f)
            ),
            startX = 0f, endX = w
        )
        drawRect(brush = sideShade, topLeft = Offset(0f, firstY), size = Size(w, h - firstY))
    }

    // Layer 4: foam-edge surface stroke. Thin near-white line traces the
    // surface so individual waves catch the eye.
    sharedHighlight.reset()
    sharedHighlight.moveTo(0f, firstY)
    for (i in 1 until n) {
        val x = (i.toFloat() / (n - 1)) * w
        sharedHighlight.lineTo(x, positions[i] * h)
    }
    drawPath(
        path = sharedHighlight,
        color = Color.White.copy(alpha = 0.55f),
        style = Stroke(width = 1.8f)
    )
}
