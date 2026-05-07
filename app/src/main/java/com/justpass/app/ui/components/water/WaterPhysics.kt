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
    private val springK: Float = 0.025f,        // restoring force coefficient
    private val damping: Float = 0.025f,        // velocity bleed per frame
    private val spread: Float = 0.25f,          // how strongly neighbors couple
    private val maxTiltOffset: Float = 0.18f,   // max fraction of cardHeight a tilt can shift
    private val maxPerturbVelocity: Float = 0.05f
) {
    val positions: FloatArray = FloatArray(nodeCount)
    private val velocities: FloatArray = FloatArray(nodeCount)
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

    /** Inject a slosh impulse — positive = downward push, negative = upward. */
    fun perturb(velocity: Float) {
        val v = velocity.coerceIn(-maxPerturbVelocity, maxPerturbVelocity)
        for (i in 0 until nodeCount) {
            velocities[i] += v
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

        // Spring pass
        val mid = (nodeCount - 1) * 0.5f
        for (i in 0 until nodeCount) {
            // target = baseline + tilt-induced ramp across the array
            val tiltOffset = ((i - mid) / mid) * tiltSlope * maxTiltOffset
            val target = (baseTarget + tiltOffset).coerceIn(0.05f, 0.95f)
            val displacement = positions[i] - target
            val acc = -springK * displacement - damping * velocities[i]
            velocities[i] += acc * dt
            positions[i] += velocities[i] * dt
        }

        // Neighbor coupling — two passes for stability
        repeat(2) {
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
}
