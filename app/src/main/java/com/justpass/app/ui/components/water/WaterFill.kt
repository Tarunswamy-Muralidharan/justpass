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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.sin
import kotlin.random.Random

/**
 * Smooth piecewise lerp across three keyframes:
 *   pct ≤ k0      → c0
 *   k0..k1        → lerp(c0, c1, t)  with smoothstep
 *   k1..k2        → lerp(c1, c2, t)  with smoothstep
 *   pct ≥ k2      → c2
 *
 * smoothstep gives a softer ease at boundaries so the colour transition
 * is not visible as a hard line.
 */
private fun smoothLerpColor(
    pct: Float,
    k0: Float, c0: Color,
    k1: Float, c1: Color,
    k2: Float, c2: Color,
): Color {
    fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    fun mix(a: Color, b: Color, t: Float) = Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = 1f
    )
    return when {
        pct <= k0 -> c0
        pct < k1 -> mix(c0, c1, smoothstep(k0, k1, pct))
        pct < k2 -> mix(c1, c2, smoothstep(k1, k2, pct))
        else -> c2
    }
}

// Three vertical-gradient stops, each smoothly interpolating across the
// same three pct keyframes (red @≤60%, amber @75%, cyan @≥90%) so that
// every band in the gradient transitions in lock-step — no rectangular
// banding at zone boundaries.
private fun surfaceTintAt(pct: Float): Color = smoothLerpColor(
    pct,
    0.60f, Color(0xFFFF8A8A),     // surface red
    0.75f, Color(0xFFFFD180),     // surface amber
    0.90f, Color(0xFFB3E5FC),     // surface cyan
)
private fun midTintAt(pct: Float): Color = smoothLerpColor(
    pct,
    0.60f, Color(0xFFE57373),
    0.75f, Color(0xFFFFB74D),
    0.90f, Color(0xFF4FC3F7),
)
private fun deepTintAt(pct: Float): Color = smoothLerpColor(
    pct,
    0.60f, Color(0xFFB71C1C),
    0.75f, Color(0xFFB45309),
    0.90f, Color(0xFF0277BD),
)
private fun floorTintAt(pct: Float): Color = smoothLerpColor(
    pct,
    0.60f, Color(0xFF7F1D1D),
    0.75f, Color(0xFF8E5A00),
    0.90f, Color(0xFF01579B),
)

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
    // Per-frame invalidation tick. drawBehind reads this so Compose
    // re-runs the draw lambda whenever physics step mutates the
    // (non-observable) FloatArray of node positions. Without this,
    // drawBehind has no snapshot dependency and never redraws.
    internal val tick: androidx.compose.runtime.MutableIntState =
        androidx.compose.runtime.mutableIntStateOf(0)

    // Phase clocks for surface micro-waves + idle LFO modulation. Updated
    // every frame from withFrameNanos. Visual-only — does not feed physics.
    internal var microPhase: Float = 0f
    internal var lfoPhase: Float = 0f

    // Current attendance fraction (0..1). Drives the surface tint:
    //   < 0.65 → red; 0.65..0.75 → amber; ≥ 0.75 → cyan/teal (healthy).
    internal var attendance: Float = 0.75f

    // Active droplet falling from above the surface. Spawned on a big
    // upward jump in fillFraction. Fraction-space (0..1 of card height);
    // <0 means above the visible top. Inactive when dropletYFraction == -1.
    internal var dropletXFraction: Float = 0f
    internal var dropletYFraction: Float = -1f
    internal var dropletVyFraction: Float = 0f   // fraction/frame

    fun spawnDroplet(xFraction: Float) {
        dropletXFraction = xFraction.coerceIn(0.05f, 0.95f)
        dropletYFraction = -0.05f             // start just above the top edge
        dropletVyFraction = 0.008f            // initial fall speed
    }
}

@Composable
fun rememberWaterState(
    fillFraction: Float,
    nodeCount: Int = 30,
    maxFillFraction: Float = 0.95f,
    active: Boolean = true,
    scrollOffsetPx: Float = 0f,
): WaterState {
    val state = remember(nodeCount) {
        WaterState(WaterPhysics(nodeCount = nodeCount), maxFillFraction)
    }
    val gravity by rememberGravity()
    val accel by rememberLinearAcceleration()
    var lastFrameNanos by remember { mutableLongStateOf(0L) }
    var initialised by remember { mutableStateOf(false) }
    var prevAccel by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
    var prevScrollPx by remember { mutableStateOf(scrollOffsetPx) }

    // Smoothly tween the resting waterline only when the change is large
    // (e.g. 0 -> 78 on data load). When the caller is dragging a slider
    // the fillFraction updates 60×/sec by tiny increments; tweening each
    // step would re-cancel the in-flight tween every frame, leaving the
    // spring chasing a constantly moving target — slosh impulses get
    // drowned out and the surface looks frozen. Snap small deltas so
    // sliders feel responsive and the spring/slosh stays alive.
    val animatedFill = remember { Animatable(fillFraction.coerceIn(0f, 1f)) }
    var splashFlipFlop by remember { mutableStateOf(1f) }
    var lastSloshMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(fillFraction) {
        val target = fillFraction.coerceIn(0f, 1f)
        val signedDelta = target - animatedFill.value
        val absDelta = kotlin.math.abs(signedDelta)
        if (absDelta < 0.05f) {
            animatedFill.snapTo(target)
        } else {
            animatedFill.animateTo(target, tween(durationMillis = 800))
        }
        // Slider/data refresh: kick a slosh, but throttle so a continuous
        // slider drag does not pump the spring 60×/sec — that drowned the
        // surface in conflicting impulses. One slosh / 90ms is enough to
        // read as motion without saturating velocities.
        val nowMs = System.currentTimeMillis()
        if (absDelta > 0.002f && (nowMs - lastSloshMs) > 90L) {
            val mag = (absDelta * 0.4f).coerceAtMost(0.008f)
            state.physics.slosh(directionSign = splashFlipFlop, magnitude = mag)
            splashFlipFlop = -splashFlipFlop
            lastSloshMs = nowMs
        }
        // Big upward jump (e.g. real-data load 0% → 78%) → spawn a droplet
        // falling into the tank from above. Lands on the surface and kicks
        // a localised splash. Only on positive jumps (water rising), and
        // only meaningfully large ones to avoid droplet spam during drags.
        if (signedDelta > 0.10f) {
            state.spawnDroplet(xFraction = 0.5f + (kotlin.random.Random.nextFloat() - 0.5f) * 0.4f)
        }
    }
    val targetFraction = (1f - animatedFill.value).coerceAtLeast(1f - maxFillFraction)

    LaunchedEffect(Unit) {
        state.physics.reset(targetFraction)
        initialised = true
        state.ready = true
    }

    // Idle ripple + frame stepper. Continuous gentle wave generator keeps
    // the surface alive even when the phone is still and there's no
    // external input. Two sine sources at different frequencies feed tiny
    // velocities into a few nodes per frame so ripples emerge organically.
    // Per-frame: read tilt from gravity sensor + jerk from linear-accel
    // sensor on the same tick so the surface responds in lock-step with
    // device motion (no LaunchedEffect lag from snapshot dispatch).
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val rng = Random(System.currentTimeMillis())
        var t = 0f
        while (true) {
            withFrameNanos { now ->
                val dtScale = if (lastFrameNanos == 0L) 1f
                              else ((now - lastFrameNanos) / 16_666_667f).coerceIn(0.5f, 2f)
                lastFrameNanos = now
                t += 0.016f * dtScale

                // Update spring base each frame from the current animated
                // fill — keeps the resting waterline in lockstep with the
                // caller's input (slider drag, real attendance % update,
                // post-load tween) without LaunchedEffect snapshot lag.
                state.physics.setBase(
                    (1f - animatedFill.value).coerceAtLeast(1f - maxFillFraction)
                )

                // Live tilt — phone tipped right slopes water in the bowl
                // so the right side rises (real container behaviour).
                state.physics.setTilt(gravity.first)

                // Sideways acceleration drives an asymmetric SLOSH: water
                // piles up on the trailing wall, drops on the leading wall.
                // Net velocity sum is zero so total water volume stays the
                // same. Use jerk (delta accel) so sustained motion only
                // kicks at start + stop, like an actual fluid impulse.
                val ax = accel.first
                val ay = accel.second
                val az = accel.third
                val jx = ax - prevAccel.first
                prevAccel = Triple(ax, ay, az)

                // Shake → slosh, but throttle so a sustained shake at 50Hz
                // sensor-rate doesn't pile up impulses every frame. Without
                // this the velocities saturate, the wall-bounce clamps every
                // node, and the surface visibly freezes at min/max.
                // Refractory window: ~120ms between slosh kicks (≈8/sec max).
                val absJx = kotlin.math.abs(jx)
                if (absJx > 0.6f) {
                    val nowMsShake = System.currentTimeMillis()
                    if (nowMsShake - lastSloshMs > 120L) {
                        val mag = (absJx / 80f).coerceAtMost(0.009f)
                        val sign = if (jx > 0f) 1f else -1f
                        state.physics.slosh(directionSign = sign, magnitude = mag)
                        lastSloshMs = nowMsShake
                    }
                }
                // Vertical accel (jz) intentionally ignored — pushing the
                // phone up/down should not change the water amount inside
                // the bowl. Real water in a closed container does not gain
                // or lose volume from vertical motion; it only sloshes,
                // which is already handled via tilt + slosh + neighbor
                // coupling.

                // Scroll → slosh. When the host content scrolls, the tank
                // visually translates relative to the user's frame; water
                // inertia means the surface lags. Use scroll-delta (px/frame)
                // as a vertical impulse — feed it into a uniform downward
                // velocity bump (positive scroll = content moving up = water
                // appears to lag down → surface dips, walls catch up). One
                // refractory-windowed kick per ~70ms keeps fast flings from
                // saturating the spring.
                val dScroll = scrollOffsetPx - prevScrollPx
                prevScrollPx = scrollOffsetPx
                val absDs = kotlin.math.abs(dScroll)
                // Higher threshold (40 px vs 6 px) + longer refractory (140 ms
                // vs 70 ms) + smaller cap (0.0035 vs 0.010) — earlier values
                // made the surface bounce far harder than a real container
                // would on a scroll. Now a slow scroll barely registers; only
                // an aggressive fling sends a small wave across.
                if (absDs > 40f) {
                    val nowMsScroll = System.currentTimeMillis()
                    if (nowMsScroll - lastSloshMs > 140L) {
                        val mag = (absDs / 2000f).coerceAtMost(0.0035f)
                        val sign = if (dScroll > 0f) 1f else -1f
                        state.physics.slosh(directionSign = sign, magnitude = mag)
                        lastSloshMs = nowMsScroll
                    }
                }

                // Wave variety — modulate idle frequencies via a slow LFO so
                // the surface texture isn't a single fixed pattern. Two LFOs
                // drift in/out of phase, producing organic-feeling beat
                // patterns instead of a metronomic ripple.
                val lfo = 0.5f + 0.5f * sin(t * 0.27f)
                val freqA = 1.5f + 1.0f * lfo
                val freqB = 2.7f + 1.4f * (1f - lfo)
                val a = 0.00050f * sin(t * freqA)
                val b = 0.00040f * sin(t * freqB + 1.3f)
                state.physics.injectIdle(a, b)
                if (rng.nextFloat() < 0.06f) {
                    val x = rng.nextFloat()
                    val v = (rng.nextFloat() - 0.5f) * 0.0025f
                    state.physics.splashAt(x, v)
                }

                // Droplet fall step (fraction-space). Apply gravity each
                // frame; impact detection + splash happen in the draw layer
                // since drawWater is the only call site that knows the
                // surface position in pixel terms after physics has run.
                if (state.dropletYFraction >= -0.10f && state.dropletYFraction < 1.5f) {
                    state.dropletVyFraction += 0.0015f * dtScale
                    state.dropletYFraction += state.dropletVyFraction * dtScale
                }

                state.physics.step(dtScale)

                // Phase clocks for visual-only effects (micro-waves + tint
                // shimmer). Driven from frame time so animation is smooth
                // even on dropped-frame ticks.
                state.microPhase += 0.18f * dtScale
                state.lfoPhase = lfo

                // Tint follows the animatedFill (smoothed) so the colour
                // transitions visibly with the waterline change.
                state.attendance = animatedFill.value

                // Tick a snapshot-observed value so any drawBehind that
                // reads state.tick.value re-runs this frame.
                state.tick.intValue = state.tick.intValue + 1
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
    // Read tick to register snapshot dependency — drives per-frame redraw.
    @Suppress("UNUSED_VARIABLE")
    val frameTick = state.tick.intValue
    if (!state.ready) return
    val w = size.width
    val h = size.height
    if (w <= 0 || h <= 0) return
    val positions = state.physics.positions
    val n = positions.size
    if (n < 2) return

    // Helper — sample surface y in px at fractional x (0..1), with optional
    // micro-wave overlay applied. Micro-waves are tiny higher-frequency
    // ripples drawn ONLY on the surface line; they don't perturb physics.
    val microPhase = state.microPhase
    fun surfaceYAt(xFrac: Float, includeMicro: Boolean = true): Float {
        val idxF = xFrac.coerceIn(0f, 1f) * (n - 1)
        val i0 = idxF.toInt().coerceIn(0, n - 1)
        val i1 = (i0 + 1).coerceAtMost(n - 1)
        val frac = idxF - i0
        val base = positions[i0] * (1f - frac) + positions[i1] * frac
        val baseY = base * h
        if (!includeMicro) return baseY
        // Two stacked micro-wavelengths at small amplitude (≈1px @ 440dp).
        val microAmpPx = (h * 0.0035f).coerceAtMost(2.5f)
        val m =
            sin(xFrac * 18.0f + microPhase * 1.2f) * 0.6f +
            sin(xFrac * 33.0f - microPhase * 0.8f) * 0.4f
        return baseY + m * microAmpPx
    }

    // Build a high-resolution surface path (subdivided beyond the 30
    // physics nodes) so micro-waves render smoothly. 4× subdivision is
    // enough; visually overkill more than that.
    val subdiv = 4
    val totalSamples = (n - 1) * subdiv + 1
    val firstY = surfaceYAt(0f)

    // Build the main water polygon path once + reuse for clipping.
    sharedPath.reset()
    sharedPath.moveTo(0f, firstY)
    for (i in 1 until totalSamples) {
        val xf = i.toFloat() / (totalSamples - 1)
        val y = surfaceYAt(xf)
        sharedPath.lineTo(xf * w, y)
    }
    sharedPath.lineTo(w, h)
    sharedPath.lineTo(0f, h)
    sharedPath.close()

    // ──── Layer 1: water tint with attendance-driven colour shift ────
    //
    // <65%  → red/pink   (warning)
    // 65-75 → amber      (caution)
    // ≥75%  → cyan/teal  (healthy)
    //
    // Smooth piecewise-lerp across THREE keyframes per gradient stop
    // (surface/mid/deep/floor), so all four bands of the vertical gradient
    // shift colour in lock-step. Earlier impl picked palettes per-zone for
    // mid/deep, which caused a visible rectangular band at the boundaries
    // where surface had moved on but mid/deep hadn't.
    val pct = state.attendance.coerceIn(0f, 1f)
    val tintT = surfaceTintAt(pct)

    // Single-hue body — earlier multi-anchor (surface/mid/deep/floor) lerp
    // produced visible horizontal bands because the eye reads any change
    // in colour direction (hue/luminance slope) as an edge. Solid-ish
    // tint with a subtle vertical luminance fall-off reads as water
    // depth without painting visible stripes.
    val baseAlpha = 0.55f
    val deepShade = Color(
        red = (tintT.red * 0.55f).coerceIn(0f, 1f),
        green = (tintT.green * 0.55f).coerceIn(0f, 1f),
        blue = (tintT.blue * 0.65f).coerceIn(0f, 1f),
        alpha = 1f,
    )
    val mainBrush = Brush.verticalGradient(
        colors = listOf(
            tintT.copy(alpha = baseAlpha * 0.65f),
            deepShade.copy(alpha = baseAlpha)
        ),
        startY = firstY,
        endY = h
    )
    drawPath(path = sharedPath, brush = mainBrush)
    // Stash for downstream layers (droplet, edge foam) that want the
    // current surface tint without recomputing.
    val surfaceTint = tintT

    // ──── Layer 2: sub-surface light band ────
    val bandHeight = (h * 0.06f).coerceAtMost(18f)
    sharedSubSurface.reset()
    sharedSubSurface.moveTo(0f, firstY)
    for (i in 1 until totalSamples) {
        val xf = i.toFloat() / (totalSamples - 1)
        sharedSubSurface.lineTo(xf * w, surfaceYAt(xf))
    }
    for (i in totalSamples - 1 downTo 0) {
        val xf = i.toFloat() / (totalSamples - 1)
        sharedSubSurface.lineTo(xf * w, surfaceYAt(xf) + bandHeight)
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

    // ──── Layer 3: side vignette ────
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

    // ──── Layer 4: foam-edge surface stroke (with edge brightening) ────
    sharedHighlight.reset()
    sharedHighlight.moveTo(0f, firstY)
    for (i in 1 until totalSamples) {
        val xf = i.toFloat() / (totalSamples - 1)
        sharedHighlight.lineTo(xf * w, surfaceYAt(xf))
    }
    drawPath(
        path = sharedHighlight,
        color = Color.White.copy(alpha = 0.55f),
        style = Stroke(width = 1.8f)
    )

    // ──── Layer 6: droplet (if active) + impact splash ────
    if (state.dropletYFraction in -0.10f..1.5f) {
        val dropX = state.dropletXFraction * w
        val dropY = state.dropletYFraction * h
        val surfaceY = surfaceYAt(state.dropletXFraction, includeMicro = false)
        if (dropY >= surfaceY - 2f) {
            // Impact: kick a localised splash, retire the droplet.
            state.physics.splashAt(state.dropletXFraction, 0.012f)
            state.dropletYFraction = -1f
            state.dropletVyFraction = 0f
        } else {
            drawCircle(
                color = surfaceTint.copy(alpha = 0.85f),
                radius = (w * 0.012f).coerceAtMost(7f),
                center = Offset(dropX, dropY)
            )
            // Trail
            drawCircle(
                color = surfaceTint.copy(alpha = 0.30f),
                radius = (w * 0.018f).coerceAtMost(11f),
                center = Offset(dropX, dropY - 6f)
            )
        }
    }
}
