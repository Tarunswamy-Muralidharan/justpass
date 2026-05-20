package com.justpass.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/* ===================================================================
 * 16-scene weather background per HANDOFF.md spec.
 * Scenes: clear-day, clear-night, partly-day, partly-night, cloudy,
 * overcast, sunset, sunrise, rain, heavy-rain, thunderstorm, snow,
 * fog, haze, windy, aurora.
 * =================================================================== */

enum class WeatherScene(val displayName: String) {
    OFF("Off"),
    CLEAR_DAY("Clear Day"),
    CLEAR_NIGHT("Clear Night"),
    PARTLY_DAY("Partly Day"),
    PARTLY_NIGHT("Partly Night"),
    CLOUDY("Cloudy"),
    OVERCAST("Overcast"),
    OVERCAST_NIGHT("Overcast Night"),
    SUNSET("Sunset"),
    SUNRISE("Sunrise"),
    RAIN("Rain"),
    HEAVY_RAIN("Heavy Rain"),
    THUNDERSTORM("Thunderstorm"),
    SNOW("Snow"),
    FOG("Fog"),
    HAZE("Haze"),
    WINDY("Windy"),
    AURORA("Aurora");

    companion object {
        fun fromString(s: String): WeatherScene =
            entries.firstOrNull { it.name == s } ?: OFF
    }
}

/* ---------- splash zone registry (HANDOFF section 3) ---------- */

/**
 * List of glass-tile rects in root coordinates. SplashCanvas reads this list
 * to know where rain should bounce. LiquidGlassCard registers itself via
 * [registerAsSplashTarget].
 */
val LocalSplashZones = compositionLocalOf<SnapshotStateList<Rect>?> { null }

fun Modifier.registerAsSplashTarget(): Modifier = composed {
    val zones = LocalSplashZones.current
    if (zones == null) this
    else onGloballyPositioned { coords ->
        val r = coords.boundsInRoot()
        // Replace any near-duplicate (within 1px) instead of stacking many copies
        val existingIdx = zones.indexOfFirst {
            kotlin.math.abs(it.top - r.top) < 1f &&
                kotlin.math.abs(it.left - r.left) < 1f &&
                kotlin.math.abs(it.right - r.right) < 1f
        }
        if (existingIdx == -1) zones.add(r) else zones[existingIdx] = r
    }
}

/* ---------- public entrypoint ---------- */

/**
 * Renders the weather scene. Mounted twice by LiquidGlassScaffold:
 *  - drawSplashes=false → sky/clouds/rain/lightning behind glass cards.
 *  - drawSplashes=true → splash particles ON TOP of cards.
 */
@Composable
fun WeatherBackgroundLayer(
    scene: WeatherScene,
    drawSplashes: Boolean,
) {
    if (scene == WeatherScene.OFF) return
    if (drawSplashes) {
        // Only some scenes spawn splashes
        when (scene) {
            WeatherScene.RAIN, WeatherScene.HEAVY_RAIN, WeatherScene.THUNDERSTORM ->
                SplashLayer(
                    intensity = when (scene) {
                        WeatherScene.RAIN -> 1.2f
                        WeatherScene.HEAVY_RAIN -> 2.6f
                        else -> 2.8f
                    }
                )
            else -> Unit
        }
        return
    }
    Box(modifier = Modifier.fillMaxSize()) {
        SceneRenderer(scene)
        // Readability scrim — darkens lower 75% so tile text contrasts against
        // bright daytime gradients (clear/partly/sunrise/sunset/haze/etc).
        // Strength scales by scene: bright daytime = heavier, dark scenes
        // (storm/night/aurora) = lighter so the sky doesn't get muddy.
        val scrimAlpha = when (scene) {
            WeatherScene.CLEAR_DAY,
            WeatherScene.PARTLY_DAY,
            WeatherScene.SUNRISE,
            WeatherScene.HAZE -> 0.28f
            WeatherScene.CLOUDY,
            WeatherScene.SUNSET,
            WeatherScene.WINDY,
            WeatherScene.SNOW -> 0.22f
            WeatherScene.OVERCAST,
            WeatherScene.FOG -> 0.18f
            WeatherScene.RAIN,
            WeatherScene.HEAVY_RAIN,
            WeatherScene.THUNDERSTORM,
            WeatherScene.CLEAR_NIGHT,
            WeatherScene.PARTLY_NIGHT,
            WeatherScene.OVERCAST_NIGHT,
            WeatherScene.AURORA -> 0.08f
            WeatherScene.OFF -> 0f
        }
        if (scrimAlpha > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                // Sky stays untouched (top 25%) → scrim ramps up to full strength
                // by 50% of screen height. Cards typically sit below this band.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = scrimAlpha * 0.35f),
                            Color.Black.copy(alpha = scrimAlpha),
                            Color.Black.copy(alpha = scrimAlpha * 0.95f),
                        ),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SceneRenderer(scene: WeatherScene) {
    when (scene) {
        WeatherScene.OFF -> Unit
        WeatherScene.CLEAR_DAY -> {
            SkyGradient(listOf(Color(0xFF2A6FC4), Color(0xFFB9D8F5)))
            CornerGlow(0.75f, 0.22f, Color(0xFFFFF1D6).copy(alpha = 0.55f), radiusFrac = 0.45f)
            SunRays(angleDeg = 115f, durationSec = 90, intensity = 0.9f)
            Cloudscape(tint = CloudTint.WHITE, density = 0.4f, layers = 3, baseDurSec = 280)
        }
        WeatherScene.CLEAR_NIGHT -> {
            SkyGradient(listOf(Color(0xFF02030A), Color(0xFF131A3A)))
            // Subtle, ambient moonlight from upper-right — no moon disc.
            CornerGlow(0.82f, 0.16f, Color(0xFFD9E4FF).copy(alpha = 0.22f), radiusFrac = 0.55f)
            Stars(density = 1.4f, brightness = 1f)
        }
        WeatherScene.PARTLY_DAY -> {
            SkyGradient(listOf(Color(0xFF3A78C9), Color(0xFFA8CBED)))
            CornerGlow(0.75f, 0.22f, Color(0xFFFFF1D6).copy(alpha = 0.42f), radiusFrac = 0.42f)
            SunRays(angleDeg = 115f, durationSec = 80, intensity = 0.85f)
            Cloudscape(tint = CloudTint.WHITE, density = 0.5f, layers = 3, baseDurSec = 280)
        }
        WeatherScene.PARTLY_NIGHT -> {
            SkyGradient(listOf(Color(0xFF050816), Color(0xFF1A2148)))
            Stars(density = 0.8f, brightness = 0.85f)
            Cloudscape(tint = CloudTint.NIGHT, density = 0.7f, layers = 3, baseDurSec = 300)
        }
        WeatherScene.CLOUDY -> {
            SkyGradient(listOf(Color(0xFF6A93C0), Color(0xFFC2D7E8)))
            CornerGlow(0.78f, 0.20f, Color(0xFFFFE9C0).copy(alpha = 0.32f), radiusFrac = 0.38f)
            Cloudscape(tint = CloudTint.WHITE, density = 1.0f, layers = 4, baseDurSec = 260)
        }
        WeatherScene.OVERCAST -> {
            SkyGradient(listOf(Color(0xFF4A5460), Color(0xFF7A8390)))
            Cloudscape(tint = CloudTint.OVERCAST, density = 1.35f, layers = 4, baseDurSec = 300)
        }
        WeatherScene.OVERCAST_NIGHT -> {
            // Deep slate-blue sky behind night clouds. Cloud bottoms catch
            // residual city/moon light → NIGHT tint reads as dark grey-blue.
            SkyGradient(listOf(Color(0xFF0E131C), Color(0xFF222A3E)))
            Cloudscape(tint = CloudTint.NIGHT, density = 1.35f, layers = 4, baseDurSec = 320)
        }
        WeatherScene.SUNSET -> {
            SkyGradient(listOf(Color(0xFF2A1A4A), Color(0xFFF8B06A)))
            HorizonGlow(Color(0xFFFF9E5A).copy(alpha = 0.55f))
            SunRays(angleDeg = 72f, durationSec = 100, intensity = 1.1f,
                rayColor = Color(0xFFFFC896))
            Cloudscape(tint = CloudTint.SUNSET, density = 1.0f, layers = 3, baseDurSec = 280)
        }
        WeatherScene.SUNRISE -> {
            SkyGradient(listOf(Color(0xFF1A3A6E), Color(0xFFFCE5B8)))
            HorizonGlow(Color(0xFFFFC080).copy(alpha = 0.55f))
            SunRays(angleDeg = 72f, durationSec = 110, intensity = 1.0f,
                rayColor = Color(0xFFFFDCA0))
            Cloudscape(tint = CloudTint.SUNSET, density = 0.9f, layers = 3, baseDurSec = 280)
        }
        WeatherScene.RAIN -> {
            SkyGradient(listOf(Color(0xFF2C3744), Color(0xFF4D5868)))
            Cloudscape(tint = CloudTint.STORM, density = 1.4f, layers = 3, baseDurSec = 220)
            RainCanvas(intensity = 1.2f)
        }
        WeatherScene.HEAVY_RAIN -> {
            SkyGradient(listOf(Color(0xFF1A2230), Color(0xFF2E3A4A)))
            Cloudscape(tint = CloudTint.STORM, density = 1.5f, layers = 4, baseDurSec = 200)
            RainCanvas(intensity = 2.6f, dropColor = Color(0xFFB4C3DC), fallSpeedMul = 1.45f)
            MistyBottomSpray()
        }
        WeatherScene.THUNDERSTORM -> {
            SkyGradient(listOf(Color(0xFF14181F), Color(0xFF28303C)))
            Cloudscape(tint = CloudTint.STORM, density = 1.6f, layers = 4, baseDurSec = 180)
            RainCanvas(intensity = 2.8f, dropColor = Color(0xFFC8D7F0), fallSpeedMul = 1.9f)
            LightningCanvas()
        }
        WeatherScene.SNOW -> {
            SkyGradient(listOf(Color(0xFF5A6878), Color(0xFFA4AFBE)))
            Cloudscape(tint = CloudTint.OVERCAST, density = 1.2f, layers = 3, baseDurSec = 280)
            SnowCanvas(density = 1.4f)
        }
        WeatherScene.FOG -> {
            SkyGradient(listOf(Color(0xFF788490), Color(0xFFB8C0C9)))
            FogBands()
        }
        WeatherScene.HAZE -> {
            SkyGradient(listOf(Color(0xFF8A7A64), Color(0xFFD4BA94)))
            HazyClouds()
        }
        WeatherScene.WINDY -> {
            SkyGradient(listOf(Color(0xFF6A8AA6), Color(0xFFB4C6D8)))
            WindyStreaks()
        }
        WeatherScene.AURORA -> {
            SkyGradient(listOf(Color(0xFF030519), Color(0xFF1A2660)))
            Stars(density = 1.6f, brightness = 0.9f)
            AuroraBands()
        }
    }
}

/* ---------- sky + glow primitives ---------- */

@Composable
private fun SkyGradient(stops: List<Color>) {
    Box(
        modifier = Modifier.fillMaxSize().background(
            brush = Brush.verticalGradient(stops)
        )
    )
}

@Composable
private fun CornerGlow(cx: Float, cy: Float, color: Color, radiusFrac: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension * radiusFrac
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, color.copy(alpha = color.alpha * 0.35f), Color.Transparent),
                center = Offset(size.width * cx, size.height * cy),
                radius = r,
            ),
            radius = r,
            center = Offset(size.width * cx, size.height * cy),
        )
    }
}

@Composable
private fun HorizonGlow(color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.90f
        val rx = size.width * 0.65f
        val ry = size.height * 0.28f
        // Approximate ellipse via radial gradient masked by aspect-ratio rect
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(color, color.copy(alpha = color.alpha * 0.40f), Color.Transparent),
                center = Offset(cx, cy),
                radius = rx,
            ),
            topLeft = Offset(cx - rx, cy - ry),
            size = Size(rx * 2f, ry * 2f),
        )
    }
}


/* ---------- SunRays ---------- */

@Composable
private fun SunRays(
    angleDeg: Float,
    durationSec: Int,
    intensity: Float,
    rayColor: Color = Color(0xFFFFE8C8),
) {
    val transition = rememberInfiniteTransition(label = "sun-rays")
    val drift by transition.animateFloat(
        initialValue = -0.08f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationSec * 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )
    val angleRad = angleDeg * (PI.toFloat() / 180f)

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // HANDOFF section 2: repeating-linear-gradient with bell-shaped 3-stop
        // per period. ~260px period (HANDOFF: 0..260 covers transparent + bell).
        // Few wide soft beams instead of many thin lines.
        val periodPx = 260f
        val perpX = -(-cos(angleRad))   // = cos(angleRad) but explicit for clarity
        val perpY = sin(angleRad)
        val centerShift = drift * w

        // Build the linearGradient brush spanning ONE period along the
        // perpendicular direction. tileMode = Repeated fills the screen.
        val startOff = Offset(perpX * 0f + centerShift, perpY * 0f)
        val endOff = Offset(perpX * periodPx + centerShift, perpY * periodPx)
        val a05 = (0.05f * intensity).coerceIn(0f, 1f)
        val a08 = (0.08f * intensity).coerceIn(0f, 1f)
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.31f to Color.Transparent,
                    0.385f to rayColor.copy(alpha = a05),
                    0.442f to rayColor.copy(alpha = a08),
                    0.500f to rayColor.copy(alpha = a05),
                    0.60f to Color.Transparent,
                    1.00f to Color.Transparent,
                ),
                start = startOff,
                end = endOff,
                tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
            ),
            blendMode = BlendMode.Screen,
        )
        // Radial center mask — beams brightest in middle, fade at edges.
        // Drawn with DstIn... but BlendMode.Screen + DstIn don't compose well
        // on a single Canvas. Instead overlay a center-bright multiply:
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.30f),
                    Color.Black.copy(alpha = 0.55f),
                ),
                center = Offset(w * 0.55f, h * 0.40f),
                radius = (w + h) * 0.55f,
            ),
            blendMode = BlendMode.DstOut,
        )
    }
}

/* ---------- Cloudscape ----------
 *
 * Photographic-style multi-layer parallax clouds. Each layer = drifting band of
 * stretched radial-gradient blobs whose densities and softness vary per layer.
 * Approximates iOS Weather's photographic clouds without a fractal shader.
 */

enum class CloudTint(val top: Color, val bottom: Color, val skyBlend: Color) {
    WHITE(Color(0xFFFFFFFF), Color(0xFFC3CDDC), Color(0xAAB7C7D7)),
    OVERCAST(Color(0xFFC4CCD6), Color(0xFF66707E), Color(0xAA3C4654)),
    STORM(Color(0xFF6E7887), Color(0xFF28303C), Color(0xAA141C28)),
    SUNSET(Color(0xFFFFE8C8), Color(0xFFC47678), Color(0xAA8C4862)),
    NIGHT(Color(0xFF5E6C8A), Color(0xFF222A3E), Color(0xAA121828)),
}

private data class CloudBlob(
    val yFrac: Float,
    val widthFrac: Float,
    val heightFrac: Float,
    val phase: Float,
    val opacityScale: Float,
)

private data class CloudLayer(
    val blobs: List<CloudBlob>,
    val speed: Float,                // +1 right, -1 left
    val durationSec: Int,
    val opacityScale: Float,
    val yBand: Float,                 // 0..1 center band
)

@Composable
private fun Cloudscape(
    tint: CloudTint,
    density: Float,
    layers: Int,
    baseDurSec: Int,
) {
    val layerData = remember(tint, density, layers, baseDurSec) {
        // Stratocumulus stack with 4 fixed depth bands. Top layer = slowest +
        // softest (background), front layer = sharpest + fastest.
        val depthSpecs = listOf(
            Triple(0.95f, baseDurSec, -1),
            Triple(0.85f, (baseDurSec * 0.72f).toInt(), +1),
            Triple(0.70f, (baseDurSec * 0.52f).toInt(), -1),
            Triple(0.55f, (baseDurSec * 0.40f).toInt(), +1),
        ).take(layers)

        depthSpecs.mapIndexed { idx, (opacity, dur, sign) ->
            val blobCount = (12 + idx * 4) * density.coerceAtLeast(0.4f)
            val blobs = (0 until blobCount.toInt()).map { i ->
                val seed = idx * 137 + i * 23
                CloudBlob(
                    yFrac = Random(seed).nextFloat() * 0.55f + 0.05f + idx * 0.06f,
                    widthFrac = (0.28f + Random(seed + 1).nextFloat() * 0.35f) * (1.2f - idx * 0.15f),
                    heightFrac = 0.10f + Random(seed + 2).nextFloat() * 0.10f,
                    phase = Random(seed + 3).nextFloat(),
                    opacityScale = 0.5f + Random(seed + 4).nextFloat() * 0.5f,
                )
            }
            CloudLayer(
                blobs = blobs,
                speed = sign.toFloat(),
                durationSec = dur,
                opacityScale = opacity,
                yBand = 0.25f + idx * 0.12f,
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "cloudscape")
    layerData.forEachIndexed { idx, layer ->
        val anim by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(layer.durationSec * 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "layer-$idx",
        )
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            layer.blobs.forEach { b ->
                val bw = w * b.widthFrac
                val bh = h * b.heightFrac
                val travel = w + bw
                val raw = (anim * layer.speed + b.phase) % 1f
                val frac = if (raw < 0f) raw + 1f else raw
                val cx = -bw + frac * travel
                val cy = b.yFrac * h
                val baseAlpha = layer.opacityScale * b.opacityScale *
                    (density.coerceIn(0.4f, 1.6f) / 1.2f)
                // Top half: bright tint. Bottom half: darker tint.
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tint.top.copy(alpha = (baseAlpha * 0.85f).coerceIn(0f, 1f)),
                            tint.bottom.copy(alpha = (baseAlpha * 0.55f).coerceIn(0f, 1f)),
                            Color.Transparent,
                        ),
                        center = Offset(cx + bw / 2f, cy),
                        radius = bw * 0.55f,
                    ),
                    topLeft = Offset(cx, cy - bh),
                    size = Size(bw, bh * 2f),
                )
            }
        }
    }
}

/* ---------- Rain ---------- */

private class RainDropMutable(
    var xFrac: Float,
    var yFrac: Float,
    var lenFrac: Float,
    var speed: Float,        // px/sec
    var alpha: Float,
    var depth: Float,
    var splatted: Boolean,
)

@Composable
private fun RainCanvas(
    intensity: Float,
    dropColor: Color = Color(0xFFAEC4E0),
    fallSpeedMul: Float = 1.0f,
) {
    val drops = remember { mutableStateListOf<RainDropMutable>() }
    val maxDrops = (240 * intensity).toInt().coerceAtMost(450)
    var elapsedNs by remember { mutableLongStateOf(0L) }
    val rng = remember { Random(System.currentTimeMillis() xor 0xDEAD) }

    LaunchedEffect(intensity) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dtNs = now - last
                last = now
                elapsedNs += dtNs
                val dt = dtNs / 1_000_000_000f

                // Spawn drops up to maxDrops
                while (drops.size < maxDrops) {
                    val depth = 0.4f + rng.nextFloat() * 0.6f
                    drops.add(
                        RainDropMutable(
                            xFrac = rng.nextFloat() * 1.2f - 0.10f,
                            yFrac = -rng.nextFloat() * 0.5f,
                            lenFrac = (0.018f + rng.nextFloat() * 0.022f) * depth,
                            speed = (440f + rng.nextFloat() * 380f) * depth * fallSpeedMul,
                            alpha = (0.45f + rng.nextFloat() * 0.47f) * depth,
                            depth = depth,
                            splatted = false,
                        )
                    )
                }

                // Update all drops
                val iter = drops.iterator()
                while (iter.hasNext()) {
                    val d = iter.next()
                    // px/sec → frac/sec: divide by approx scene height. We don't
                    // know exact px here, use frac-relative speed of 1/(2400) scale.
                    val moveFrac = d.speed * dt / 2400f
                    d.yFrac += moveFrac
                    d.xFrac += moveFrac * 0.18f // 18% slant per HANDOFF
                    if (d.yFrac > 1.10f || d.xFrac > 1.20f) iter.remove()
                }
            }
        }
    }

    val zones = LocalSplashZones.current

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Snapshot zone fractions
        val zoneFracs = zones?.map {
            ZoneFrac(it.top / h, it.left / w, it.right / w)
        } ?: emptyList()
        drops.forEach { d ->
            // Check splash crossing
            if (!d.splatted && d.depth > 0.55f) {
                zoneFracs.forEach { z ->
                    if (d.yFrac >= z.y && d.yFrac - z.y < 0.01f &&
                        d.xFrac in z.x1..z.x2) {
                        spawnSplash(d.xFrac, z.y, intensity, rng)
                        d.splatted = true
                    }
                }
            }
            val sx = d.xFrac * w
            val sy = d.yFrac * h
            val ex = sx - sin(0.18f) * d.lenFrac * h
            val ey = sy - cos(0.18f) * d.lenFrac * h
            drawLine(
                color = dropColor.copy(alpha = d.alpha.coerceIn(0f, 1f)),
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = 1.6f * d.depth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private data class ZoneFrac(val y: Float, val x1: Float, val x2: Float)

/* ---------- Splashes (separate Canvas above cards) ---------- */

private data class SplashParticle(
    var xFrac: Float,
    var yFrac: Float,
    var vx: Float,            // frac/sec
    var vy: Float,
    var life: Float,
    var age: Float,
    var radius: Float,        // px
)

private data class SplashRing(
    val xFrac: Float,
    val yFrac: Float,
    val maxRx: Float,         // px
    val maxRy: Float,
    val life: Float,
    val startDelay: Float,
    var age: Float,
)

private val splashParticles = mutableStateListOf<SplashParticle>()
private val splashRings = mutableStateListOf<SplashRing>()

private fun spawnSplash(xFrac: Float, yFrac: Float, intensity: Float, rng: Random) {
    val isBig = rng.nextFloat() < 0.15f
    val burstN = if (isBig) 5 + rng.nextInt(7) else 2 + rng.nextInt(4)
    val pIntensity = if (isBig) 0.85f + rng.nextFloat() * 0.55f
                     else 0.45f + rng.nextFloat() * 0.45f
    val ringCt = if (isBig) (if (rng.nextFloat() < 0.5f) 2 else 1)
                 else (if (rng.nextFloat() < 0.55f) 1 else 0)
    repeat(burstN) {
        val sideways = rng.nextFloat() < 0.12f
        val angle = if (sideways) {
            // Wide horizontal scatter
            -PI.toFloat() / 2f + (rng.nextFloat() - 0.5f) * 2f * PI.toFloat()
        } else {
            // Mostly upward fan ± 0.45π
            -PI.toFloat() / 2f + (rng.nextFloat() - 0.5f) * 0.9f * PI.toFloat()
        }
        val speed = 60f + rng.nextFloat() * 180f
        splashParticles.add(
            SplashParticle(
                xFrac = xFrac,
                yFrac = yFrac,
                vx = cos(angle) * speed * pIntensity,
                vy = sin(angle) * speed * pIntensity,
                life = (0.25f + rng.nextFloat() * 0.50f) * pIntensity,
                age = 0f,
                radius = 0.5f + rng.nextFloat() * 1.1f,
            )
        )
    }
    repeat(ringCt) { i ->
        val scale = 0.5f + rng.nextFloat() * 0.5f
        splashRings.add(
            SplashRing(
                xFrac = xFrac,
                yFrac = yFrac,
                maxRx = (1.8f + rng.nextFloat() * 5.5f) * scale,
                maxRy = (0.5f + rng.nextFloat() * 1.5f) * scale,
                life = (0.28f + rng.nextFloat() * 0.17f) * pIntensity,
                startDelay = i * 0.03f,
                age = 0f,
            )
        )
    }
}

@Composable
private fun SplashLayer(intensity: Float) {
    var elapsedNs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dtNs = now - last
                last = now
                elapsedNs += dtNs
                val dt = dtNs / 1_000_000_000f
                // Update particles
                val it1 = splashParticles.iterator()
                while (it1.hasNext()) {
                    val p = it1.next()
                    p.age += dt
                    if (p.age > p.life) { it1.remove(); continue }
                    p.vy += 360f * dt           // gravity in px/s² equivalent
                    p.xFrac += p.vx * dt / 1080f // assume ~1080px width unit
                    p.yFrac += p.vy * dt / 2400f
                }
                val it2 = splashRings.iterator()
                while (it2.hasNext()) {
                    val r = it2.next()
                    r.age += dt
                    if (r.age - r.startDelay > r.life) it2.remove()
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        splashRings.forEach { r ->
            val t = ((r.age - r.startDelay) / r.life).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val a = (1f - t) * 0.45f
            drawOval(
                color = Color(0xFFDDEAFF).copy(alpha = a),
                topLeft = Offset(
                    r.xFrac * w - r.maxRx * t,
                    r.yFrac * h - r.maxRy * t,
                ),
                size = Size(r.maxRx * 2f * t, r.maxRy * 2f * t),
                style = Stroke(width = 1.2f),
            )
        }
        splashParticles.forEach { p ->
            val fade = (1f - p.age / p.life) * 0.95f
            drawCircle(
                color = Color.White.copy(alpha = fade.coerceIn(0f, 1f)),
                radius = p.radius,
                center = Offset(p.xFrac * w, p.yFrac * h),
            )
        }
    }
}

/* ---------- Snow ---------- */

private class FlakeMutable(
    var xFrac: Float,
    var yFrac: Float,
    var fallSpeed: Float,
    var alpha: Float,
    var radius: Float,
    val swayAmp: Float,
    val swayFreq: Float,
    val phase: Float,
)

@Composable
private fun SnowCanvas(density: Float) {
    val flakes = remember { mutableStateListOf<FlakeMutable>() }
    val maxFlakes = (180 * density).toInt().coerceAtMost(300)
    var elapsedNs by remember { mutableLongStateOf(0L) }
    val rng = remember { Random(System.currentTimeMillis() xor 0xBEEF) }

    LaunchedEffect(density) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dtNs = now - last
                last = now
                elapsedNs += dtNs
                val dt = dtNs / 1_000_000_000f
                while (flakes.size < maxFlakes) {
                    val depth = 0.4f + rng.nextFloat() * 0.6f
                    flakes.add(
                        FlakeMutable(
                            xFrac = rng.nextFloat(),
                            yFrac = -rng.nextFloat() * 0.3f,
                            fallSpeed = (20f + rng.nextFloat() * 45f) * depth,
                            alpha = (0.55f + rng.nextFloat() * 0.4f) * (0.4f + depth * 0.6f),
                            radius = (0.8f + rng.nextFloat() * 2.4f) * depth,
                            swayAmp = 8f + rng.nextFloat() * 20f,
                            swayFreq = 0.3f + rng.nextFloat() * 0.6f,
                            phase = rng.nextFloat() * 6.28f,
                        )
                    )
                }
                val it = flakes.iterator()
                while (it.hasNext()) {
                    val f = it.next()
                    f.yFrac += f.fallSpeed * dt / 2400f
                    if (f.yFrac > 1.10f) it.remove()
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = elapsedNs / 1_000_000_000f
        val w = size.width
        val h = size.height
        flakes.forEach { f ->
            val visualX = (f.xFrac * w) + sin(t * f.swayFreq + f.phase) * f.swayAmp
            drawCircle(
                color = Color.White.copy(alpha = f.alpha.coerceIn(0f, 1f)),
                radius = f.radius,
                center = Offset(visualX, f.yFrac * h),
            )
        }
    }
}

/* ---------- Stars ---------- */

@Composable
private fun Stars(density: Float, brightness: Float) {
    val seeds = remember(density) {
        val count = (260 * density).toInt().coerceAtMost(420)
        List(count) { i ->
            val s = i * 19
            StarSeed(
                x = Random(s).nextFloat(),
                y = Random(s + 1).nextFloat() * 0.88f,
                // Larger radius range — 0.9..3.0 instead of 0.4..1.6
                r = 0.9f + Random(s + 2).nextFloat() * 2.1f,
                // Higher base alpha so stars never fully dim out
                baseAlpha = 0.55f + Random(s + 3).nextFloat() * 0.45f,
                // Wider freq spread — some twinkle fast, some slow
                freq = 0.8f + Random(s + 4).nextFloat() * 2.6f,
                phase = Random(s + 5).nextFloat() * 6.28f,
            )
        }
    }
    var elapsedNs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { elapsedNs = it }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = elapsedNs / 1_000_000_000f
        val w = size.width
        val h = size.height
        seeds.forEach { s ->
            // Sharper twinkle envelope: pow(.., 3) instead of pow(.., 2) +
            // wider dynamic range (0.3..1.0 instead of 0.4..1.0) so the
            // visible "blink" is more pronounced.
            val raw = (sin(t * s.freq + s.phase) * 0.5f + 0.5f).pow(3)
            val twinkle = raw * 0.7f + 0.30f
            val a = (s.baseAlpha * twinkle * brightness).coerceIn(0f, 1f)
            val center = Offset(s.x * w, s.y * h)
            // Big stars get a soft outer glow + a brighter inner ring
            if (s.r > 1.6f) {
                drawCircle(Color.White.copy(alpha = a * 0.18f), s.r * 4f, center)
                drawCircle(Color.White.copy(alpha = a * 0.40f), s.r * 2.2f, center)
            } else if (s.r > 1.1f) {
                drawCircle(Color.White.copy(alpha = a * 0.30f), s.r * 2.5f, center)
            }
            // Main star core
            drawCircle(Color.White.copy(alpha = a), s.r, center)
            // Diffraction-spike highlight for the biggest stars when bright
            if (s.r > 2.0f && raw > 0.55f) {
                val spikeLen = s.r * 4.5f
                val spikeAlpha = (a - 0.4f).coerceAtLeast(0f) * 0.85f
                drawLine(
                    Color.White.copy(alpha = spikeAlpha),
                    start = Offset(center.x - spikeLen, center.y),
                    end = Offset(center.x + spikeLen, center.y),
                    strokeWidth = 0.7f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    Color.White.copy(alpha = spikeAlpha),
                    start = Offset(center.x, center.y - spikeLen),
                    end = Offset(center.x, center.y + spikeLen),
                    strokeWidth = 0.7f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private data class StarSeed(
    val x: Float,
    val y: Float,
    val r: Float,
    val baseAlpha: Float,
    val freq: Float,
    val phase: Float,
)

/* ---------- Lightning ---------- */

private class Bolt(
    val startX: Float,         // fraction
    val mainPath: List<Offset>,
    val branches: List<List<Offset>>,
    val originY: Float,
    val durationSec: Float,
    var age: Float,
    val jitterSeed: Float,
)

@Composable
private fun LightningCanvas() {
    val bolts = remember { mutableStateListOf<Bolt>() }
    var elapsedNs by remember { mutableLongStateOf(0L) }
    var nextStrikeAt by remember { mutableFloatStateOf(3.5f) }
    val rng = remember { Random(System.currentTimeMillis() xor 0xCAFE) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dtNs = now - last
                last = now
                elapsedNs += dtNs
                val dt = dtNs / 1_000_000_000f
                val time = elapsedNs / 1_000_000_000f

                if (time >= nextStrikeAt) {
                    // Build a new bolt path (HANDOFF section 6)
                    val w = 1080f // unit width
                    val h = 2400f
                    val startX = 0.25f + rng.nextFloat() * 0.50f
                    val segLen = 36f + rng.nextFloat() * 12f
                    val main = mutableListOf<Offset>()
                    var px = startX * w
                    var py = -8f
                    main.add(Offset(px, py))
                    while (py < h * (0.85f + rng.nextFloat() * 0.18f)) {
                        py += 16f + rng.nextFloat() * 38f
                        px += (rng.nextFloat() - 0.5f) * segLen * 1.4f
                        main.add(Offset(px, py))
                    }
                    val branches = (0 until rng.nextInt(3)).map {
                        val anchorIdx = main.size / 4 + rng.nextInt(main.size / 2)
                        val start = main[anchorIdx]
                        val dir = if (rng.nextFloat() < 0.5f) -1f else +1f
                        val pts = mutableListOf(start)
                        var bx = start.x
                        var by = start.y
                        val segCount = 3 + rng.nextInt(4)
                        repeat(segCount) {
                            by += 14f + rng.nextFloat() * 36f
                            bx += dir * (8f + rng.nextFloat() * 22f)
                            pts.add(Offset(bx, by))
                        }
                        pts
                    }
                    bolts.add(
                        Bolt(
                            startX = startX,
                            mainPath = main,
                            branches = branches,
                            originY = -8f,
                            durationSec = 0.55f + rng.nextFloat() * 0.25f,
                            age = 0f,
                            jitterSeed = rng.nextFloat() * 100f,
                        )
                    )
                    nextStrikeAt = time + 3.5f + rng.nextFloat() * 5.5f
                }

                val it = bolts.iterator()
                while (it.hasNext()) {
                    val b = it.next()
                    b.age += dt
                    if (b.age > b.durationSec) it.remove()
                }
            }
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        val time = elapsedNs / 1_000_000_000f
        val sx = size.width / 1080f
        val sy = size.height / 2400f
        bolts.forEach { b ->
            val u = (b.age / b.durationSec).coerceIn(0f, 1f)
            val f = flashEnvelope(u)
            if (f <= 0.01f) return@forEach
            // Sky bloom
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE8F0FF).copy(alpha = f * 0.45f),
                        Color.Transparent,
                    ),
                    center = Offset(b.mainPath.first().x * sx, size.height * 0.18f),
                    radius = size.minDimension,
                ),
                radius = size.minDimension,
                center = Offset(b.mainPath.first().x * sx, size.height * 0.18f),
            )

            fun shimmer(p: Offset, i: Int): Offset {
                val jitter = sin(time * 65f + i + b.jitterSeed) * 0.6f
                return Offset(p.x * sx + jitter, p.y * sy + jitter)
            }
            // Helper to build a Path from points + shimmer
            fun buildPath(pts: List<Offset>): Path {
                val p = Path()
                pts.forEachIndexed { i, point ->
                    val s = shimmer(point, i)
                    if (i == 0) p.moveTo(s.x, s.y) else p.lineTo(s.x, s.y)
                }
                return p
            }

            val main = buildPath(b.mainPath)
            // Wide halo
            drawPath(
                main,
                color = Color(0xFFB4D2FF).copy(alpha = f * 0.55f),
                style = Stroke(width = 5f * sx, cap = StrokeCap.Round),
            )
            // Mid glow
            drawPath(
                main,
                color = Color(0xFFE6F0FF).copy(alpha = f * 0.90f),
                style = Stroke(width = 2.2f * sx, cap = StrokeCap.Round),
            )
            // White core
            drawPath(
                main,
                color = Color.White.copy(alpha = f),
                style = Stroke(width = 1f * sx, cap = StrokeCap.Round),
            )
            b.branches.forEach { bp ->
                val path = buildPath(bp)
                drawPath(
                    path,
                    color = Color(0xFFB4D2FF).copy(alpha = f * 0.45f),
                    style = Stroke(width = 3f * sx, cap = StrokeCap.Round),
                )
                drawPath(
                    path,
                    color = Color.White.copy(alpha = f * 0.85f),
                    style = Stroke(width = 0.8f * sx, cap = StrokeCap.Round),
                )
            }
        }
    }
}

private fun flashEnvelope(u: Float): Float = when {
    u < 0.04f -> u / 0.04f                                // attack 0→1
    u < 0.18f -> 1f - (u - 0.04f) / 0.14f * 0.55f         // fade to 0.45
    u < 0.26f -> 0.45f + (u - 0.18f) / 0.08f * 0.55f      // re-strike peak 1
    u < 0.55f -> 1f - (u - 0.26f) / 0.29f * 0.75f         // long fade to 0.25
    u < 1.00f -> 0.25f * (1f - (u - 0.55f) / 0.45f)       // tail
    else -> 0f
}

/* ---------- Fog / Haze / Windy / Aurora / Misty spray ---------- */

@Composable
private fun MistyBottomSpray() {
    Canvas(Modifier.fillMaxSize()) {
        val gradH = size.height * 0.30f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.12f),
                ),
                startY = size.height - gradH,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - gradH),
            size = Size(size.width, gradH),
        )
    }
}

@Composable
private fun FogBands() {
    // Continuous atmospheric fog — single soft full-screen veil + 3 very wide
    // slow-drifting density blobs that vary local thickness. No discrete
    // banded geometry; nothing reads as a "bar".
    val transition = rememberInfiniteTransition(label = "fog")
    val blobA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(90_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blob-a",
    )
    val blobB by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(130_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "blob-b",
    )
    val blobC by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(160_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blob-c",
    )

    // Base uniform mist veil — vertical gradient peaks mid-screen, fades at
    // top + bottom. Low alpha overall (~0.25 max) so cards underneath stay
    // readable; the scrim is what carries readability, not the fog itself.
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.06f),
                    Color.White.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.25f),
                    Color.White.copy(alpha = 0.18f),
                    Color.White.copy(alpha = 0.05f),
                ),
            ),
        )

        val w = size.width
        val h = size.height
        // 3 huge density blobs sliding horizontally at different speeds and y
        // positions. Each is wider than the screen by 2× so the falloff is
        // out of view → no visible edge anywhere in the viewport.
        val blobs = listOf(
            BlobDrift(blobA, 0.32f, 0.35f),
            BlobDrift(blobB, 0.56f, 0.45f),
            BlobDrift(blobC, 0.80f, 0.40f),
        )
        blobs.forEach { b ->
            val bw = w * 3.0f
            val bh = h * 1.8f
            val cx = -bw / 2f + b.drift * (w + bw)
            val cy = b.yFrac * h
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = b.alphaScale * 0.22f),
                        Color.White.copy(alpha = b.alphaScale * 0.10f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = bw * 0.45f,
                ),
                topLeft = Offset(cx - bw / 2f, cy - bh / 2f),
                size = Size(bw, bh),
            )
        }
    }
}

private data class BlobDrift(val drift: Float, val yFrac: Float, val alphaScale: Float)

@Composable
private fun HazyClouds() {
    val transition = rememberInfiniteTransition(label = "haze")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(11_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Diffused warm sun disc top-right
        val sunCx = w * 0.62f
        val sunCy = h * 0.22f
        val sunR = w * 0.35f * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFE4B5).copy(alpha = 0.50f),
                    Color(0xFFFFB76E).copy(alpha = 0.20f),
                    Color.Transparent,
                ),
                center = Offset(sunCx, sunCy),
                radius = sunR,
            ),
            radius = sunR,
            center = Offset(sunCx, sunCy),
        )
        // 4 large warm soft cloud blobs
        repeat(4) { i ->
            val phase = (drift + i * 0.31f) % 1f
            val bw = w * (0.5f + i * 0.08f)
            val cx = -bw + phase * (w + bw) + bw / 2f
            val cy = h * (0.30f + i * 0.13f)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE9C8A6).copy(alpha = 0.40f),
                        Color(0xFFB89060).copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = bw * 0.5f,
                ),
                topLeft = Offset(cx - bw / 2f, cy - h * 0.10f),
                size = Size(bw, h * 0.20f),
            )
        }
    }
}

@Composable
private fun WindyStreaks() {
    val transition = rememberInfiniteTransition(label = "windy")
    val streakConfigs = listOf(
        WindyConfig(0.22f, 0.16f, +1, 32),
        WindyConfig(0.36f, 0.20f, -1, 38),
        WindyConfig(0.48f, 0.14f, +1, 42),
        WindyConfig(0.60f, 0.18f, -1, 36),
        WindyConfig(0.72f, 0.16f, +1, 46),
        WindyConfig(0.84f, 0.20f, -1, 40),
    )
    streakConfigs.forEachIndexed { i, cfg ->
        val drift by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(cfg.durSec * 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "wind-$i",
        )
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val bandW = w * 1.4f
            val cx = if (cfg.dir > 0)
                -bandW + (drift % 1f) * (w + bandW) + bandW / 2f
            else
                w + bandW - (drift % 1f) * (w + bandW) - bandW / 2f
            val cy = cfg.yFrac * h
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.45f),
                        Color.White.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = bandW * 0.5f,
                ),
                topLeft = Offset(cx - bandW / 2f, cy - h * cfg.heightMul / 2f),
                size = Size(bandW, h * cfg.heightMul),
            )
        }
    }
}

private data class WindyConfig(val yFrac: Float, val heightMul: Float, val dir: Int, val durSec: Int)

@Composable
private fun AuroraBands() {
    val transition = rememberInfiniteTransition(label = "aurora")
    val bandColors = listOf(
        listOf(Color(0xFF6CFFAE), Color(0xFF34D2FF)),
        listOf(Color(0xFFFF7CE0), Color(0xFFFFB28A)),
        listOf(Color(0xFF8AB6FF), Color(0xFF6CFFAE)),
    )
    bandColors.forEachIndexed { i, palette ->
        val shimmer by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween((14 + i * 4) * 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "aurora-$i",
        )
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cy = h * (0.25f + i * 0.16f) + sin(shimmer * 2f * PI.toFloat()) * 20f
            val bandH = h * 0.32f
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette[0].copy(alpha = 0.30f),
                        palette[1].copy(alpha = 0.25f),
                        Color.Transparent,
                    ),
                    startY = cy - bandH / 2f,
                    endY = cy + bandH / 2f,
                ),
                topLeft = Offset(0f, cy - bandH / 2f),
                size = Size(w, bandH),
                blendMode = BlendMode.Screen,
            )
        }
    }
}
