package com.justpass.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class WeatherMode {
    OFF,
    SUNNY,
    CLOUDY,
    RAIN,
    THUNDERSTORM;

    fun next(): WeatherMode = entries[(ordinal + 1) % entries.size]

    val displayName: String
        get() = when (this) {
            OFF -> "Off"
            SUNNY -> "Sunny"
            CLOUDY -> "Cloudy"
            RAIN -> "Rain"
            THUNDERSTORM -> "Thunderstorm"
        }

    companion object {
        fun fromString(s: String): WeatherMode =
            entries.firstOrNull { it.name == s } ?: OFF
    }
}

/**
 * Full-screen weather effect drawn between the base gradient and the glass tiles.
 * Sits inside the cardState liquefiable so glass cards refract these effects.
 */
@Composable
fun WeatherBackground(
    mode: WeatherMode,
    modifier: Modifier = Modifier,
    testing: Boolean = false,
) {
    when (mode) {
        WeatherMode.OFF -> Unit
        WeatherMode.SUNNY -> SunnyOverlay(modifier)
        WeatherMode.CLOUDY -> CloudyOverlay(modifier, dark = false)
        WeatherMode.RAIN -> RainOverlay(modifier, withLightning = false, testing = testing)
        WeatherMode.THUNDERSTORM -> RainOverlay(modifier, withLightning = true, testing = testing)
    }
}

/* ----- Sunny: warm radial glow + slowly rotating sun rays ----- */
@Composable
private fun SunnyOverlay(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "sunny")
    val rayAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 38_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rays",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val sunCenter = Offset(size.width * 0.82f, size.height * 0.18f)
        val sunRadius = size.minDimension * 0.32f * pulse

        // Warm radial glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFE08A).copy(alpha = 0.55f),
                    Color(0xFFFFB347).copy(alpha = 0.18f),
                    Color.Transparent,
                ),
                center = sunCenter,
                radius = sunRadius,
            ),
            radius = sunRadius,
            center = sunCenter,
        )

        // Subtle sun rays
        val rayCount = 14
        val rayLen = sunRadius * 1.4f
        for (i in 0 until rayCount) {
            val a = (rayAngle + i * (360f / rayCount)) * PI.toFloat() / 180f
            val end = Offset(
                sunCenter.x + cos(a) * rayLen,
                sunCenter.y + sin(a) * rayLen,
            )
            drawLine(
                color = Color(0xFFFFD480).copy(alpha = 0.08f),
                start = sunCenter,
                end = end,
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/* ----- Cloudy: layered horizontal mist bands drifting across ----- */
@Composable
private fun CloudyOverlay(modifier: Modifier, dark: Boolean) {
    val transition = rememberInfiniteTransition(label = "cloudy")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "drift",
    )

    val isDark = isSystemInDarkTheme()
    val baseColor = when {
        dark -> Color(0xFF1F2A3A)
        isDark -> Color(0xFFB7C0CE)
        else -> Color.White
    }
    val maxAlpha = when {
        dark -> 0.55f
        isDark -> 0.14f
        else -> 0.50f
    }

    // Each band is a long horizontal soft blob. Multiple bands at different
    // y-positions create overlapping layered mist like the reference video.
    val bands = remember(dark) {
        List(if (dark) 9 else 7) { i ->
            BandSeed(
                yFraction = Random(i * 53).nextFloat() * 0.95f + 0.02f,
                heightFraction = 0.10f + Random(i * 53 + 7).nextFloat() * 0.18f,
                widthScale = 1.4f + Random(i * 53 + 11).nextFloat() * 0.9f,
                phase = Random(i * 53 + 17).nextFloat(),
                speedFactor = 0.55f + Random(i * 53 + 23).nextFloat() * 0.7f,
                alphaScale = 0.55f + Random(i * 53 + 29).nextFloat() * 0.45f,
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        bands.forEach { b ->
            val bandW = w * b.widthScale
            val bandH = h * b.heightFraction
            val travel = w + bandW
            val x = -bandW + ((drift * b.speedFactor + b.phase) % 1f) * travel
            val cy = b.yFraction * h
            val cx = x + bandW / 2f

            // Radial gradient gives the soft, diffuse fog look. The brush is
            // anchored to a moving center, with horizontal radius >> vertical
            // so the band reads as a stretched mist layer rather than a puff.
            val rH = bandW * 0.55f
            val rV = bandH * 0.6f
            val gradient = Brush.radialGradient(
                colors = listOf(
                    baseColor.copy(alpha = maxAlpha * b.alphaScale),
                    baseColor.copy(alpha = maxAlpha * b.alphaScale * 0.55f),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = rH,
            )
            // Approximate ellipse-shaped brush via a wide rectangle with the
            // radial gradient centered inside; wider-than-tall feels right.
            drawRect(
                brush = gradient,
                topLeft = Offset(cx - rH, cy - rV),
                size = androidx.compose.ui.geometry.Size(rH * 2f, rV * 2f),
            )
        }
    }
}

private data class BandSeed(
    val yFraction: Float,
    val heightFraction: Float,
    val widthScale: Float,
    val phase: Float,
    val speedFactor: Float,
    val alphaScale: Float,
)

/* ----- Rain (and thunderstorm): falling streaks + splash ripples + dark overlay ----- */
@Composable
private fun RainOverlay(
    modifier: Modifier,
    withLightning: Boolean,
    testing: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize()) {
        CloudyOverlay(Modifier, dark = true)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF000000).copy(alpha = if (withLightning) 0.34f else 0.24f))
        )
        if (testing) {
            ParallaxRain(Modifier, intense = withLightning)
        } else {
            RainAndSplashes(Modifier, intense = withLightning)
        }
        if (withLightning) {
            if (testing) SpriteLightningOverlay(Modifier)
            else LightningOverlay(Modifier)
        }
    }
}

/* ===== Testing-only: ColorOS-style parallax rain (2 layers, drops aligned to velocity) ===== */
private data class TiltedDrop(
    val xSeed: Float,
    val phase: Float,
    val speedFactor: Float,
    val lengthFactor: Float,
    val sizeFactor: Float,
    val depth: Float, // 0=front, 1=back
)

@Composable
private fun ParallaxRain(modifier: Modifier, intense: Boolean) {
    val frontCount = if (intense) 90 else 60
    val backCount = if (intense) 130 else 80
    val drops = remember {
        val list = mutableListOf<TiltedDrop>()
        repeat(frontCount) { i ->
            val s = i * 23
            list += TiltedDrop(
                xSeed = Random(s).nextFloat(),
                phase = Random(s + 1).nextFloat(),
                speedFactor = 1.4f + Random(s + 2).nextFloat() * 0.6f,
                lengthFactor = 0.7f + Random(s + 3).nextFloat() * 0.4f,
                sizeFactor = 1.6f + Random(s + 4).nextFloat() * 0.5f,
                depth = 0f,
            )
        }
        repeat(backCount) { i ->
            val s = (i + 5000) * 23
            list += TiltedDrop(
                xSeed = Random(s).nextFloat(),
                phase = Random(s + 1).nextFloat(),
                speedFactor = 0.55f + Random(s + 2).nextFloat() * 0.35f,
                lengthFactor = 0.4f + Random(s + 3).nextFloat() * 0.3f,
                sizeFactor = 0.7f + Random(s + 4).nextFloat() * 0.4f,
                depth = 1f,
            )
        }
        list
    }

    var elapsedNs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                elapsedNs += now - last
                last = now
            }
        }
    }

    // Wind tilt — drops fall at angle. Matches ColorOS rotateUV(atan(speed)).
    val tiltRad = 0.20f // ~11.5deg
    val sinT = sin(tiltRad)
    val cosT = cos(tiltRad)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val time = elapsedNs / 1_000_000_000f

        drops.forEach { d ->
            val travel = h * 1.2f
            val cycle = 1.1f / d.speedFactor
            val frac = ((time / cycle) + d.phase) % 1f
            val baseX = d.xSeed * w
            val baseY = frac * travel - 30f
            val len = h * 0.04f * d.lengthFactor + 6f
            // Drop direction = (sinT, cosT) — fall down and slightly right
            val dx = sinT * len
            val dy = cosT * len
            val sx = baseX
            val sy = baseY
            val ex = baseX - dx
            val ey = baseY - dy
            val alpha = if (d.depth == 0f) 0.55f else 0.30f
            val color = if (d.depth == 0f)
                Color(0xFFC4D8FF).copy(alpha = alpha)
            else
                Color(0xFF8AA2C4).copy(alpha = alpha)
            drawLine(
                color = color,
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = 1.2f * d.sizeFactor,
                cap = StrokeCap.Round,
            )
        }
    }
}

/* ===== Testing-only: sprite-style bolt — wider painted bolt with bright halo + flash ===== */
@Composable
private fun SpriteLightningOverlay(modifier: Modifier) {
    var nowMs by remember { mutableLongStateOf(0L) }
    var bolt by remember { androidx.compose.runtime.mutableStateOf<SpriteBolt?>(null) }
    var lightStrength by remember { mutableFloatStateOf(0f) }
    var flashStrength by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now -> nowMs = now / 1_000_000L }
        }
    }

    // ColorOS triangular ramp: smoothstep(0, 0.444, x) - smoothstep(0.778, 1.0, x)
    fun colorOSRamp(t: Float): Float {
        val a = if (t <= 0f) 0f else if (t >= 0.444f) 1f
            else { val u = (t / 0.444f); u * u * (3 - 2 * u) }
        val b = if (t <= 0.778f) 0f else if (t >= 1f) 1f
            else { val u = (t - 0.778f) / 0.222f; u * u * (3 - 2 * u) }
        return (a - b).coerceIn(0f, 1f)
    }

    LaunchedEffect(Unit) {
        val rng = Random(System.currentTimeMillis() xor 0xCAFE)
        while (true) {
            val waitMs = 3000L + rng.nextInt(7000).toLong()
            kotlinx.coroutines.delay(waitMs)
            val newBolt = SpriteBolt(
                seed = rng.nextLong(),
                xCenter = 0.20f + rng.nextFloat() * 0.60f,
                topY = -0.05f + rng.nextFloat() * 0.10f,
                bottomY = 0.55f + rng.nextFloat() * 0.30f,
                segments = 9 + rng.nextInt(4),
                width = 9f + rng.nextFloat() * 6f,
                flashCenter = Offset(0.5f, 0.30f),
            )
            bolt = newBolt
            // Drive uniforms ~480ms via colorOS-style triangular ramp
            val total = 480L
            val startMs = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startMs
                val t = (elapsed.toFloat() / total).coerceIn(0f, 1f)
                lightStrength = colorOSRamp(t)
                // Flash has 2 quick peaks
                flashStrength = colorOSRamp(t) * (0.55f +
                    0.45f * sin(t * 12f * PI.toFloat()).let { kotlin.math.abs(it) })
                if (t >= 1f) break
                kotlinx.coroutines.delay(16)
            }
            lightStrength = 0f
            flashStrength = 0f
            bolt = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // u_texFlash equivalent — radial bright flash centered on bolt's top
        val activeBolt = bolt
        if (flashStrength > 0.01f && activeBolt != null) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(
                    activeBolt.flashCenter.x * size.width,
                    activeBolt.flashCenter.y * size.height,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE8F0FF).copy(alpha = flashStrength * 0.85f),
                            Color(0xFFAEC8FF).copy(alpha = flashStrength * 0.40f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.minDimension * 0.95f,
                    ),
                    radius = size.minDimension * 0.95f,
                    center = center,
                )
            }
        }
        if (activeBolt != null && lightStrength > 0.01f) {
            Canvas(Modifier.fillMaxSize()) {
                drawSpriteBolt(activeBolt, lightStrength)
            }
        }
    }
}

private data class SpriteBolt(
    val seed: Long,
    val xCenter: Float,
    val topY: Float,
    val bottomY: Float,
    val segments: Int,
    val width: Float,
    val flashCenter: Offset,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpriteBolt(
    b: SpriteBolt,
    intensity: Float,
) {
    val rng = Random(b.seed)
    val w = size.width
    val h = size.height
    val main = buildBoltPath(
        rng = rng,
        startX = b.xCenter * w,
        startY = b.topY * h,
        endY = b.bottomY * h,
        segments = b.segments,
        jitter = w * 0.10f,
    )
    // Outer halo (wide, soft)
    drawPath(
        path = main,
        color = Color(0xFFAEC8FF).copy(alpha = intensity * 0.35f),
        style = Stroke(width = b.width * 3.5f, cap = StrokeCap.Round),
    )
    drawPath(
        path = main,
        color = Color(0xFFCDE0FF).copy(alpha = intensity * 0.55f),
        style = Stroke(width = b.width * 2f, cap = StrokeCap.Round),
    )
    drawPath(
        path = main,
        color = Color(0xFFE8F0FF).copy(alpha = intensity * 0.80f),
        style = Stroke(width = b.width, cap = StrokeCap.Round),
    )
    drawPath(
        path = main,
        color = Color.White.copy(alpha = intensity),
        style = Stroke(width = b.width * 0.35f, cap = StrokeCap.Round),
    )
    // 1-2 forks
    val branchCount = 1 + rng.nextInt(2)
    repeat(branchCount) {
        val tBranch = 0.30f + rng.nextFloat() * 0.40f
        val bx = b.xCenter * w + (rng.nextFloat() - 0.5f) * w * 0.16f
        val by = b.topY * h + (b.bottomY * h - b.topY * h) * tBranch
        val ex = bx + (rng.nextFloat() - 0.5f) * w * 0.30f
        val ey = by + h * (0.10f + rng.nextFloat() * 0.18f)
        val branch = buildBoltPath(
            rng = rng,
            startX = bx,
            startY = by,
            endY = ey,
            segments = 4 + rng.nextInt(3),
            jitter = w * 0.06f,
            endX = ex,
        )
        drawPath(
            path = branch,
            color = Color(0xFFCDE0FF).copy(alpha = intensity * 0.45f),
            style = Stroke(width = b.width * 1.2f, cap = StrokeCap.Round),
        )
        drawPath(
            path = branch,
            color = Color.White.copy(alpha = intensity * 0.85f),
            style = Stroke(width = b.width * 0.30f, cap = StrokeCap.Round),
        )
    }
}

private data class RainDrop(
    val xSeed: Float,
    val phase: Float,
    val speedFactor: Float,
    val lengthFactor: Float,
)

/**
 * One impact = one cluster of tiny scattered droplet specks. They appear at a
 * fixed Y line (mimicking water hitting a card edge) and disperse outward, not
 * a big circular ring. Each speck is a 0.8–2.0 px dot that drifts a few px and
 * fades. Multiple impacts happen per second along several "edge" Y-lines so the
 * whole frame gets that wet-glass-rim look from the reference video.
 */
private data class Speck(
    val xPx: Float,
    val yPx: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val startTime: Float,
    val lifetime: Float,
)

@Composable
private fun RainAndSplashes(modifier: Modifier, intense: Boolean) {
    val dropCount = if (intense) 160 else 100
    val drops = remember {
        List(dropCount) { i ->
            RainDrop(
                xSeed = Random(i * 17).nextFloat(),
                phase = Random(i * 17 + 3).nextFloat(),
                speedFactor = 0.55f + Random(i * 17 + 9).nextFloat() * 0.65f,
                lengthFactor = 0.35f + Random(i * 17 + 11).nextFloat() * 0.45f,
            )
        }
    }

    // Y-lines where splashes happen — these read as the top edges of glass
    // cards. Distributed across visible card-zone fractions of the screen.
    val edgeYFractions = remember {
        floatArrayOf(0.18f, 0.30f, 0.42f, 0.55f, 0.68f, 0.82f, 0.92f)
    }

    val specks = remember { mutableStateListOf<Speck>() }
    var elapsedNs by remember { mutableLongStateOf(0L) }
    var lastSpawn by remember { mutableFloatStateOf(0f) }
    val spawnInterval = if (intense) 0.06f else 0.10f

    LaunchedEffect(Unit) {
        var last = 0L
        val rng = Random(System.currentTimeMillis())
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                elapsedNs += (now - last)
                last = now

                val time = elapsedNs / 1_000_000_000f
                if (time - lastSpawn > spawnInterval) {
                    lastSpawn = time
                    // 1–2 impacts per tick, each spawns a cluster of tiny specks
                    val impactCount = if (intense) 2 + rng.nextInt(2) else 1 + rng.nextInt(2)
                    repeat(impactCount) {
                        val edgeY = edgeYFractions[rng.nextInt(edgeYFractions.size)] +
                            (rng.nextFloat() - 0.5f) * 0.012f
                        val centerX = rng.nextFloat()
                        val cluster = 6 + rng.nextInt(7)
                        repeat(cluster) {
                            // Specks scatter mostly horizontally + slight upward kick
                            val angle = (-PI.toFloat() + rng.nextFloat() * 2f * PI.toFloat()) // any dir
                            val speed = 6f + rng.nextFloat() * 22f
                            specks.add(
                                Speck(
                                    xPx = centerX,                           // store fraction; px in render
                                    yPx = edgeY,                              // store fraction; px in render
                                    vx = cos(angle) * speed,
                                    vy = sin(angle) * speed - rng.nextFloat() * 6f,
                                    radius = 0.8f + rng.nextFloat() * 1.4f,
                                    startTime = time,
                                    lifetime = 0.4f + rng.nextFloat() * 0.5f,
                                )
                            )
                        }
                    }
                    specks.removeAll { time - it.startTime > it.lifetime }
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val time = elapsedNs / 1_000_000_000f

        // Falling streaks
        drops.forEach { d ->
            val travel = h + 60f
            val cycle = 0.9f / d.speedFactor
            val frac = ((time / cycle) + d.phase) % 1f
            val x = d.xSeed * w - 20f + (frac * 40f)
            val y = frac * travel - 30f
            val len = h * 0.05f * d.lengthFactor + 8f
            drawLine(
                color = Color(0xFFAAC6FF).copy(alpha = 0.45f),
                start = Offset(x, y),
                end = Offset(x - 3f, y + len),
                strokeWidth = 1.7f,
                cap = StrokeCap.Round,
            )
        }

        // Speck droplets disperse from an edge line + fade
        specks.forEach { s ->
            val age = (time - s.startTime).coerceIn(0f, s.lifetime)
            val t = age / s.lifetime
            val px = s.xPx * w + s.vx * age * 14f
            val py = s.yPx * h + s.vy * age * 14f + (age * age) * 30f // tiny gravity
            val alpha = (1f - t) * 0.85f
            if (alpha > 0.02f) {
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = s.radius,
                    center = Offset(px, py),
                )
            }
        }
    }
}

/* ----- Glass raindrops: static beads + sliding drops with trails ----- */

/** Drawn on TOP of glass tiles (the drops sit on the glass surface). */
@Composable
fun GlassRainDroplets(modifier: Modifier = Modifier, intense: Boolean) {
    val beads = remember { mutableStateListOf<StaticBead>() }
    val active = remember { mutableStateListOf<SlidingDrop>() }
    var elapsedNs by remember { mutableLongStateOf(0L) }
    var lastBeadSpawn by remember { mutableFloatStateOf(0f) }
    var lastDropSpawn by remember { mutableFloatStateOf(0f) }

    val maxBeads = if (intense) 80 else 55
    val maxDrops = if (intense) 14 else 9

    LaunchedEffect(Unit) {
        var last = 0L
        val rng = Random(System.currentTimeMillis() xor 0xBADL)
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dtNs = now - last
                last = now
                elapsedNs += dtNs
                val dt = dtNs / 1_000_000_000f
                val time = elapsedNs / 1_000_000_000f

                // Spawn idle beads
                if (time - lastBeadSpawn > (if (intense) 0.06f else 0.10f) && beads.size < maxBeads) {
                    lastBeadSpawn = time
                    beads.add(
                        StaticBead(
                            x = rng.nextFloat(),
                            y = rng.nextFloat(),
                            radius = 1.2f + rng.nextFloat() * 2.2f,
                            bornAt = time,
                            ttl = 4f + rng.nextFloat() * 6f,
                        )
                    )
                }
                beads.removeAll { time - it.bornAt > it.ttl }

                // Spawn sliding drops
                if (time - lastDropSpawn > (if (intense) 0.20f else 0.35f) && active.size < maxDrops) {
                    lastDropSpawn = time
                    active.add(
                        SlidingDrop(
                            xFrac = rng.nextFloat(),
                            yFrac = -0.02f + rng.nextFloat() * 0.05f,
                            radius = 4f + rng.nextFloat() * 6f,
                            velocity = 30f + rng.nextFloat() * 80f, // px/sec
                            acceleration = 60f + rng.nextFloat() * 80f,
                            trailEveryS = 0.04f + rng.nextFloat() * 0.04f,
                            lastTrailAt = time,
                        )
                    )
                }

                // Update sliding drops
                val toRemove = mutableListOf<SlidingDrop>()
                active.forEach { d ->
                    d.velocity += d.acceleration * dt
                    d.yFrac += d.velocity * dt / 2400f // assume ~2400 px height ref; calibrated visually
                    if (time - d.lastTrailAt > d.trailEveryS) {
                        d.lastTrailAt = time
                        d.trail.add(
                            TrailBead(
                                xFrac = d.xFrac + (rng.nextFloat() - 0.5f) * 0.004f,
                                yFrac = d.yFrac,
                                radius = d.radius * (0.35f + rng.nextFloat() * 0.30f),
                                bornAt = time,
                                ttl = 2.5f + rng.nextFloat() * 2.0f,
                            )
                        )
                    }
                    d.trail.removeAll { time - it.bornAt > it.ttl }
                    if (d.yFrac > 1.05f) toRemove += d
                }
                active.removeAll(toRemove)
            }
        }
    }

    val highlightColor = Color.White.copy(alpha = 0.85f)
    val edgeColor = Color(0xFF14202E).copy(alpha = 0.55f)
    val coreColor = Color(0xFFE9F2FF).copy(alpha = 0.18f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val time = elapsedNs / 1_000_000_000f

        // Static beads
        beads.forEach { b ->
            val age = time - b.bornAt
            val t = (age / b.ttl).coerceIn(0f, 1f)
            val alpha = if (t < 0.15f) (t / 0.15f) else (1f - (t - 0.15f) / 0.85f)
            val cx = b.x * w
            val cy = b.y * h
            drawDrop(cx, cy, b.radius, alpha, highlightColor, edgeColor, coreColor)
        }

        // Sliding drops + their trails
        active.forEach { d ->
            d.trail.forEach { t ->
                val age = time - t.bornAt
                val ageT = (age / t.ttl).coerceIn(0f, 1f)
                val alpha = (1f - ageT) * 0.85f
                drawDrop(t.xFrac * w, t.yFrac * h, t.radius, alpha,
                    highlightColor, edgeColor, coreColor)
            }
            drawDrop(d.xFrac * w, d.yFrac * h, d.radius, 1f,
                highlightColor, edgeColor, coreColor)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDrop(
    cx: Float,
    cy: Float,
    radius: Float,
    alpha: Float,
    highlight: Color,
    edge: Color,
    core: Color,
) {
    if (alpha < 0.02f || radius < 0.4f) return
    // Subtle core fill (lens body)
    drawCircle(
        color = core.copy(alpha = core.alpha * alpha),
        radius = radius,
        center = Offset(cx, cy),
    )
    // Dark lower-right edge (refracted boundary)
    drawCircle(
        color = edge.copy(alpha = edge.alpha * alpha),
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = (radius * 0.28f).coerceAtLeast(0.8f)),
    )
    // Bright top-left highlight (specular)
    drawCircle(
        color = highlight.copy(alpha = highlight.alpha * alpha),
        radius = radius * 0.35f,
        center = Offset(cx - radius * 0.30f, cy - radius * 0.32f),
    )
}

private data class StaticBead(
    val x: Float,
    val y: Float,
    val radius: Float,
    val bornAt: Float,
    val ttl: Float,
)

private data class TrailBead(
    val xFrac: Float,
    val yFrac: Float,
    val radius: Float,
    val bornAt: Float,
    val ttl: Float,
)

private class SlidingDrop(
    var xFrac: Float,
    var yFrac: Float,
    var radius: Float,
    var velocity: Float,
    var acceleration: Float,
    var trailEveryS: Float,
    var lastTrailAt: Float,
    val trail: SnapshotStateList<TrailBead> = mutableStateListOf(),
)

/* ----- Lightning: jagged bolt path + screen flash ----- */
private data class Bolt(
    val seed: Long,
    val startX: Float,        // 0..1
    val startY: Float = 0f,
    val endY: Float,          // 0..1
    val segmentCount: Int,
    val branchCount: Int,
    val startTimeMs: Long,
    val durationMs: Long,
)

@Composable
private fun LightningOverlay(modifier: Modifier) {
    var flash by remember { mutableFloatStateOf(0f) }
    var bolt by remember { androidx.compose.runtime.mutableStateOf<Bolt?>(null) }
    var nowMs by remember { mutableLongStateOf(0L) }

    // Frame ticker so the bolt fade can re-render each frame
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                nowMs = now / 1_000_000L
            }
        }
    }

    LaunchedEffect(Unit) {
        val rng = Random(System.currentTimeMillis())
        while (true) {
            val waitMs = 3500L + rng.nextInt(6500).toLong()
            kotlinx.coroutines.delay(waitMs)
            // Spawn bolt
            val newBolt = Bolt(
                seed = rng.nextLong(),
                startX = 0.20f + rng.nextFloat() * 0.60f,
                endY = 0.55f + rng.nextFloat() * 0.30f,
                segmentCount = 8 + rng.nextInt(4),
                branchCount = 1 + rng.nextInt(2),
                startTimeMs = System.currentTimeMillis(),
                durationMs = 360L + rng.nextInt(140).toLong(),
            )
            bolt = newBolt
            // Quick flash sequence
            flash = 0.50f
            kotlinx.coroutines.delay(60)
            flash = 0.10f
            kotlinx.coroutines.delay(70)
            flash = 0.85f
            kotlinx.coroutines.delay(90)
            flash = 0.20f
            kotlinx.coroutines.delay(120)
            flash = 0f
            // Let bolt linger past its duration then clear
            kotlinx.coroutines.delay(newBolt.durationMs)
            bolt = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (flash > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFFE8F0FF).copy(alpha = flash))
            )
        }

        val activeBolt = bolt
        if (activeBolt != null) {
            val ageMs = nowMs - activeBolt.startTimeMs
            val t = (ageMs.toFloat() / activeBolt.durationMs).coerceIn(0f, 1f)
            val alpha = (1f - t).let { it * it } // ease-out
            if (alpha > 0.01f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawBolt(activeBolt, alpha)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBolt(
    bolt: Bolt,
    alpha: Float,
) {
    val rng = Random(bolt.seed)
    val w = size.width
    val h = size.height

    val main = buildBoltPath(
        rng = rng,
        startX = bolt.startX * w,
        startY = bolt.startY * h,
        endY = bolt.endY * h,
        segments = bolt.segmentCount,
        jitter = w * 0.08f,
    )

    // Outer halo
    drawPath(
        path = main,
        color = Color(0xFFAEC8FF).copy(alpha = alpha * 0.45f),
        style = Stroke(width = 14f, cap = StrokeCap.Round),
    )
    // Mid glow
    drawPath(
        path = main,
        color = Color(0xFFE6EEFF).copy(alpha = alpha * 0.85f),
        style = Stroke(width = 6f, cap = StrokeCap.Round),
    )
    // Hot core
    drawPath(
        path = main,
        color = Color.White.copy(alpha = alpha),
        style = Stroke(width = 2.4f, cap = StrokeCap.Round),
    )

    // Branches forking off the main path
    repeat(bolt.branchCount) {
        val branchStartT = 0.25f + rng.nextFloat() * 0.45f
        val branchStartX = bolt.startX * w + (rng.nextFloat() - 0.5f) * w * 0.18f
        val branchStartY = bolt.startY * h + (bolt.endY * h - bolt.startY * h) * branchStartT
        val branchEndX = branchStartX + (rng.nextFloat() - 0.5f) * w * 0.30f
        val branchEndY = branchStartY + h * (0.10f + rng.nextFloat() * 0.18f)
        val branch = buildBoltPath(
            rng = rng,
            startX = branchStartX,
            startY = branchStartY,
            endY = branchEndY,
            segments = 4 + rng.nextInt(3),
            jitter = w * 0.05f,
            endX = branchEndX,
        )
        drawPath(
            path = branch,
            color = Color(0xFFE6EEFF).copy(alpha = alpha * 0.55f),
            style = Stroke(width = 3f, cap = StrokeCap.Round),
        )
        drawPath(
            path = branch,
            color = Color.White.copy(alpha = alpha * 0.85f),
            style = Stroke(width = 1.2f, cap = StrokeCap.Round),
        )
    }
}

private fun buildBoltPath(
    rng: Random,
    startX: Float,
    startY: Float,
    endY: Float,
    segments: Int,
    jitter: Float,
    endX: Float = startX + (rng.nextFloat() - 0.5f) * jitter * 1.4f,
): Path {
    val path = Path()
    path.moveTo(startX, startY)
    val totalDy = endY - startY
    var cx = startX
    var cy = startY
    val targetEndX = endX
    for (i in 1..segments) {
        val t = i.toFloat() / segments
        // Linear path from start to end with random horizontal jitter
        val baseX = startX + (targetEndX - startX) * t
        val baseY = startY + totalDy * t
        val nx = baseX + (rng.nextFloat() - 0.5f) * jitter
        val ny = baseY + (rng.nextFloat() - 0.3f) * jitter * 0.4f
        path.lineTo(nx, ny)
        cx = nx
        cy = ny
    }
    return path
}
