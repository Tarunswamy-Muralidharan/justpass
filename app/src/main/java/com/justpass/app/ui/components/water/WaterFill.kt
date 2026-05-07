package com.justpass.app.ui.components.water

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Animated 2D water fill that overlays its parent.
 *
 *  - [fillFraction] is the resting waterline (0..1, where 1 = top of card,
 *    so attendance percent / 100). Capped to maxFillFraction so even at
 *    100% there's a thin sky strip above the surface.
 *  - Tilt the phone -> water surface tilts (gravity sensor).
 *  - Pass [scrollOffsetPx] from a parent ScrollState — every change
 *    injects a slosh impulse proportional to the delta.
 *  - Pauses physics when [active] is false (e.g. card scrolled
 *    off-screen) so background CPU is zero.
 *
 * Caller stacks UI content on top of this composable inside the same
 * Box — the water draws beneath whatever's drawn after it.
 */
@Composable
fun WaterFill(
    fillFraction: Float,
    modifier: Modifier = Modifier,
    waterColor: Color = Color(0xFF4FC3F7),
    waterColorDeep: Color = Color(0xFF0288D1),
    surfaceHighlight: Color = Color.White.copy(alpha = 0.35f),
    nodeCount: Int = 30,
    maxFillFraction: Float = 0.95f,
    scrollOffsetPx: Float = 0f,
    active: Boolean = true,
) {
    val physics = remember(nodeCount) { WaterPhysics(nodeCount = nodeCount) }
    val gravity by rememberGravity()
    var lastFrameNanos by remember { mutableLongStateOf(0L) }
    var prevScrollOffset by remember { mutableStateOf(scrollOffsetPx) }
    var initialised by remember { mutableStateOf(false) }

    // Initialise spring positions on first composition + whenever fill
    // jumps (e.g. user logs in and percentage flips from 0 to 78).
    val targetFraction = (1f - fillFraction.coerceIn(0f, 1f)).coerceAtLeast(1f - maxFillFraction)
    LaunchedEffect(targetFraction) {
        if (!initialised) {
            physics.reset(targetFraction)
            initialised = true
        } else {
            physics.setBase(targetFraction)
        }
    }

    // Feed gravity x to physics every frame. Use the x component (left/right
    // portrait tilt) so the surface visibly leans the way the phone is held.
    LaunchedEffect(gravity, active) {
        if (active) physics.setTilt(gravity.first)
    }

    // Inject scroll perturbation. snapshotFlow keeps work off the main
    // recompose path so we don't recompose every scroll pixel.
    LaunchedEffect(active) {
        snapshotFlow { scrollOffsetPx }
            .distinctUntilChanged()
            .collect { offset ->
                val delta = offset - prevScrollOffset
                prevScrollOffset = offset
                if (active && delta != 0f) {
                    // Scale: 200px scroll -> max impulse. Sign matches scroll
                    // direction so scrolling down pushes water down (jolt).
                    val v = (delta / 200f).coerceIn(-0.05f, 0.05f)
                    physics.perturb(v)
                }
            }
    }

    // Frame loop. withFrameNanos emits in step with the display refresh.
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            withFrameNanos { now ->
                val dtScale = if (lastFrameNanos == 0L) 1f
                              else ((now - lastFrameNanos) / 16_666_667f).coerceIn(0.5f, 2f)
                lastFrameNanos = now
                physics.step(dtScale)
            }
        }
    }

    // Reuse a single Path across frames — Compose Canvas handles diffing.
    val path = remember { Path() }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0 || h <= 0) return@Canvas
            val n = physics.positions.size
            if (n < 2) return@Canvas

            path.reset()
            // Top-left corner anchored at first node's y.
            val firstY = physics.positions[0] * h
            path.moveTo(0f, firstY)
            // Smooth surface via short straight segments — n=30 nodes is
            // dense enough that anti-aliasing makes them look continuous.
            // Cheaper than quadTo and visually equivalent at this density.
            for (i in 1 until n) {
                val x = (i.toFloat() / (n - 1)) * w
                val y = physics.positions[i] * h
                path.lineTo(x, y)
            }
            // Close: down right edge -> across bottom -> up left edge.
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()

            // Vertical gradient — lighter at the surface, darker below.
            // Gives the fill volumetric depth without an extra draw call.
            val brush = Brush.verticalGradient(
                colors = listOf(waterColor, waterColorDeep),
                startY = firstY,
                endY = h
            )
            drawPath(path = path, brush = brush)

            // Surface highlight stroke — same path traced just along the top.
            // We rebuild a tiny path because drawPath with stroke would also
            // outline the bottom rectangle.
            val highlight = Path()
            highlight.moveTo(0f, firstY)
            for (i in 1 until n) {
                val x = (i.toFloat() / (n - 1)) * w
                val y = physics.positions[i] * h
                highlight.lineTo(x, y)
            }
            drawPath(
                path = highlight,
                color = surfaceHighlight,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
            )
        }
    }
}

