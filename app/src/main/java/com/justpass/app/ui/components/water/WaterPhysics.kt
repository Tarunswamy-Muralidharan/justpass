package com.justpass.app.ui.components.water

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 2D water surface simulated as a chain of vertical springs coupled to
 * each other (Prime31 / Tuts+ canonical pattern). Each node has a target
 * y (the resting waterline) and a current y + velocity. Per frame:
 *
 *  1. Spring force pulls each node toward its target_y
 *  2. Two-pass neighbor coupling propagates perturbations as a wave
 *
 * Tilt input shifts the per-node target_y so the surface settles to the
 * correct slope when the phone is tilted. Scroll input injects a velocity
 * uniformly into all nodes so a fast scroll causes a slosh.
 *
 * Performance budget: 30 nodes × ~6 float ops/frame = trivial. Reuses
 * float arrays — zero allocation per frame.
 */
class WaterPhysics(
    private val nodeCount: Int = 30,
    // Slightly softer spring + lower damping than v1 — waves linger longer
    // so a single slosh produces a few visible cycles of motion before
    // calming, instead of snapping back in a single frame.
    private val springK: Float = 0.017f,        // restoring force coefficient
    private val damping: Float = 0.022f,        // velocity bleed per frame (lower = waves linger)
    private val spread: Float = 0.11f,          // how strongly neighbors couple
    private val maxTiltOffset: Float = 0.35f,   // max fraction of cardHeight a tilt can shift
    private val maxPerturbVelocity: Float = 0.015f,
    private val minPosition: Float = 0.02f,     // surface can never go above 2% from top
    private val maxPosition: Float = 0.98f,     // surface can never go below 98% from top (i.e. into floor)
    private val wallBounce: Float = 0.45f       // velocity retention when surface hits ceiling/floor
) {
    val positions: FloatArray = FloatArray(nodeCount)
    private val velocities: FloatArray = FloatArray(nodeCount)
    private var driftFrameCounter = 0
    private val leftDelta: FloatArray = FloatArray(nodeCount)
    private val rightDelta: FloatArray = FloatArray(nodeCount)

    /** Resting waterline as a fraction of card height (0 = top, 1 = bottom). */
    private var baseTarget: Float = 0.5f

    /** Current tilt slope, mapped from -1..1. */
    private var tiltSlope: Float = 0f

    /** One-time initialiser — call when card size first known. */
    fun reset(initialFraction: Float) {
        baseTarget = initialFraction.coerceIn(0f, 1f)
        for (i in 0 until nodeCount) {
            positions[i] = baseTarget
            velocities[i] = 0f
        }
    }

    /** Update the resting waterline (e.g. attendance % changed). */
    fun setBase(fraction: Float) {
        baseTarget = fraction.coerceIn(0f, 1f)
    }

    /** [slope] in -1..1; 1.0 = max-tilt right, -1.0 = max-tilt left. */
    fun setTilt(slope: Float) {
        tiltSlope = slope.coerceIn(-1f, 1f)
    }

    /**
     * Asymmetric sideways slosh — when the container is shoved sideways,
     * water lags behind: piles on the trailing side, drops on the leading
     * side. Net velocity sum is zero so total fluid volume is conserved.
     *
     * [directionSign] = +1 means container shoved right (water piles on the
     * left); -1 means shoved left (water piles on right). [magnitude] is
     * the peak per-node velocity, clamped to [maxPerturbVelocity].
     */
    fun slosh(directionSign: Float, magnitude: Float) {
        val mag = magnitude.coerceAtMost(maxPerturbVelocity).coerceAtLeast(0f)
        if (mag == 0f) return
        val mid = (nodeCount - 1) * 0.5f
        for (i in 0 until nodeCount) {
            val sideRamp = (i - mid) / mid              // -1 at left, +1 at right
            // Right shove (directionSign=+1) → water piles LEFT → left positions
            // DECREASE (surface rises). Position's spring target is unchanged;
            // we inject velocity in the direction that lifts the trailing edge.
            // Left node (sideRamp < 0): velocity NEGATIVE (positions decrease).
            velocities[i] += sideRamp * directionSign * mag
        }
    }

    /**
     * Continuous idle ripple — apply a per-node velocity offset that varies
     * sinusoidally across the array so two long wavelengths overlap. Keeps
     * the surface alive even with no external input.
     */
    fun injectIdle(amplitudeA: Float, amplitudeB: Float) {
        val n = nodeCount
        for (i in 0 until n) {
            val u = i.toFloat() / (n - 1)
            // Two stationary waveforms shifted in phase — combined effect is a
            // gentle, ever-shifting surface texture.
            velocities[i] += amplitudeA * kotlin.math.sin(u * 6.28318f * 1.5f)
            velocities[i] += amplitudeB * kotlin.math.sin(u * 6.28318f * 2.7f + 0.9f)
        }
    }

    /** Local splash at fractional x (0..1). */
    fun splashAt(xFraction: Float, velocity: Float) {
        val center = (xFraction * (nodeCount - 1)).toInt().coerceIn(0, nodeCount - 1)
        val v = velocity.coerceIn(-maxPerturbVelocity, maxPerturbVelocity)
        for (i in max(0, center - 2)..min(nodeCount - 1, center + 2)) {
            val falloff = 1f - abs(i - center) / 3f
            velocities[i] += v * falloff
        }
    }

    /**
     * Advance one frame. [dtScale] should be ~1.0 at 60fps; higher when
     * frame intervals stretch (e.g. dropped frames) so physics doesn't
     * slow down visually. Cap to avoid integration explosion.
     */
    fun step(dtScale: Float = 1f) {
        val dt = dtScale.coerceIn(0.5f, 2.0f)

        // Velocity sanitiser — clamp any node that has run away (NaN, Inf,
        // or saturation from sustained sensor input). Without this a long
        // shake builds velocities far past the wave amplitude budget and
        // the surface visibly freezes at min/max while the integrator
        // thrashes. Cap is generous (≈ 3× perturb max) so legitimate big
        // sloshes still play through; only runaway energy is bled.
        val vCap = maxPerturbVelocity * 3f
        for (i in 0 until nodeCount) {
            val v = velocities[i]
            if (v.isNaN() || v.isInfinite()) {
                velocities[i] = 0f
            } else if (v > vCap) velocities[i] = vCap
            else if (v < -vCap) velocities[i] = -vCap
        }

        // DC-offset bleed — every 6 seconds (~360 frames @60fps) compute the
        // mean velocity across all nodes and subtract it. Floating-point drift
        // + repeated unidirectional impulses (sensor noise on a phone resting
        // on a table, scroll input that doesn't perfectly cancel) accumulate a
        // small constant velocity bias the spring can't restore against. After
        // a few hours that bias becomes visible vibration. Removing the mean
        // preserves wave motion (relative differences between nodes) while
        // zeroing the rigid-body drift component.
        driftFrameCounter += 1
        if (driftFrameCounter >= 360) {
            driftFrameCounter = 0
            var sum = 0f
            for (i in 0 until nodeCount) sum += velocities[i]
            val mean = sum / nodeCount
            if (kotlin.math.abs(mean) > 0.00005f) {
                for (i in 0 until nodeCount) velocities[i] -= mean
            }
        }

        // Spring pass
        val mid = (nodeCount - 1) * 0.5f
        for (i in 0 until nodeCount) {
            // target = baseline + tilt-induced ramp.
            //
            // Real container physics: tilt right (slope > 0) → world's lowest
            // point shifts to the right edge → water flows there → right side
            // gets DEEPER (surface rises in container frame, positions[i]
            // smaller because positions are depth-from-top). So the per-node
            // offset must be NEGATIVE on the right when slope is positive.
            // The historical (pre-fix) sign was inverted.
            val tiltOffset = -((i - mid) / mid) * tiltSlope * maxTiltOffset
            val target = (baseTarget + tiltOffset).coerceIn(0.05f, 0.95f)
            val displacement = positions[i] - target
            val acc = -springK * displacement - damping * velocities[i]
            velocities[i] += acc * dt
            positions[i] += velocities[i] * dt

            // Container walls: surface can't escape past the rim (top) or
            // sink through the floor. On contact, retain a fraction of the
            // velocity but reversed — the water "bounces" off the boundary
            // like a real meniscus snap.
            if (positions[i] < minPosition) {
                positions[i] = minPosition
                if (velocities[i] < 0f) velocities[i] = -velocities[i] * wallBounce
            } else if (positions[i] > maxPosition) {
                positions[i] = maxPosition
                if (velocities[i] > 0f) velocities[i] = -velocities[i] * wallBounce
            }
        }

        // Single-pass neighbor coupling (was two-pass — too much energy
        // injection per frame caused waves to amplify rather than damp).
        for (i in 0 until nodeCount) {
            if (i > 0) leftDelta[i] = spread * (positions[i] - positions[i - 1])
            if (i < nodeCount - 1) rightDelta[i] = spread * (positions[i] - positions[i + 1])
        }
        for (i in 0 until nodeCount) {
            if (i > 0) velocities[i - 1] += leftDelta[i]
            if (i < nodeCount - 1) velocities[i + 1] += rightDelta[i]
        }
    }
}
