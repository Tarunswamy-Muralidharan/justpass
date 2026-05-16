package com.justpass.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquid
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

val GlassCardShape = RoundedCornerShape(20.dp)
val GlassCardShapeSmall = RoundedCornerShape(14.dp)

/**
 * Dual-state glass scaffold:
 * - cardState: cards sample the gradient background (refraction, no blur)
 * - barState: bottom bar samples everything including cards (frosted blur)
 *
 * Content lambda receives cardState for use with LiquidGlassCard.
 * bottomBar lambda receives barState for the frosted bottom bar.
 */
@Composable
fun LiquidGlassScaffold(
    modifier: Modifier = Modifier,
    variant: BackgroundVariant = BackgroundVariant.Default,
    weatherScene: WeatherScene = WeatherScene.OFF,
    bottomBar: @Composable BoxScope.(barState: LiquidState) -> Unit = {},
    content: @Composable BoxScope.(cardState: LiquidState) -> Unit
) {
    val barState = rememberLiquidState()   // For bottom bar: blurs everything
    val cardState = rememberLiquidState()  // For cards: refracts the gradient

    val isDark = isSystemInDarkTheme()
    val gradientColors = getGradientColors(variant, isDark)

    // Per-screen splash zone registry — LiquidGlassCard registers its top-edge
    // bounds here via Modifier.registerAsSplashTarget. SplashCanvas reads these
    // to spawn rain splatters only where there's actual glass to bounce off.
    val splashZones = remember { mutableStateListOf<androidx.compose.ui.geometry.Rect>() }

    CompositionLocalProvider(LocalSplashZones provides splashZones) {
        Box(modifier = modifier.fillMaxSize()) {
            // barState liquefiable: captures gradient + cards + everything for the frosted bar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .liquefiable(barState)
            ) {
                // cardState liquefiable: captures the gradient AND the weather effects
                // for card refraction.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .liquefiable(cardState)
                        .background(
                            brush = Brush.linearGradient(
                                colors = gradientColors,
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            )
                        )
                ) {
                    // Weather effects sit on top of the gradient but inside both
                    // liquefiable layers so glass tiles refract them. Rain drops
                    // fall *behind* cards from inside this Box; splash particles
                    // are drawn separately on top of content().
                    WeatherBackgroundLayer(weatherScene, drawSplashes = false)
                }

                // Screen content — cards use liquid(cardState), list items use lightweight
                content(cardState)

                // Splash particles draw on top of glass tiles (drops bouncing off rim).
                WeatherBackgroundLayer(weatherScene, drawSplashes = true)
            }

            // Floating glass bottom bar — uses liquid(barState) to blur everything
            bottomBar(barState)
        }
    }
}

/**
 * Real liquid glass card with visible refraction, edge reflections, and color dispersion.
 * Like Apple's Liquid Glass: transparent, bends light, refracts colors, live edge reflections.
 * Use for static/prominent cards (headers, main stats). NOT for scrolling list items.
 */
@Composable
fun LiquidGlassCard(
    cardState: LiquidState,
    modifier: Modifier = Modifier,
    shape: Shape = GlassCardShape,
    tintColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val defaultTint = if (tintColor == Color.Unspecified) {
        if (isDark) Color.White.copy(alpha = 0.04f)
        else Color.White.copy(alpha = 0.25f)
    } else tintColor

    Column(
        modifier = modifier
            .liquid(cardState) {
                frost = 2.dp             // Tiny blur softens hard refraction
                refraction = 0.14f       // Reduced — less warping of rain/effects
                curve = 0.35f            // Gentler lens curvature
                edge = 0.08f             // Bright edge reflections
                this.shape = shape
                tint = defaultTint
                saturation = 1.35f       // Slightly less vivid
                contrast = 1.25f
                dispersion = 0.025f      // Reduced chromatic aberration
            },
        content = content
    )
}

/**
 * Real liquid glass surface (Box layout) with visible refraction.
 */
@Composable
fun LiquidGlassSurface(
    cardState: LiquidState,
    modifier: Modifier = Modifier,
    shape: Shape = GlassCardShape,
    tintColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val defaultTint = if (tintColor == Color.Unspecified) {
        if (isDark) Color.White.copy(alpha = 0.04f)
        else Color.White.copy(alpha = 0.25f)
    } else tintColor

    Box(
        modifier = modifier
            .liquid(cardState) {
                frost = 0.dp
                refraction = 0.20f
                curve = 0.4f
                edge = 0.06f
                this.shape = shape
                tint = defaultTint
                saturation = 1.4f
                contrast = 1.3f
                dispersion = 0.04f
            },
        content = content
    )
}

// ─── Floating glass bottom bar ───────────────────────────────────────────────

@Composable
fun LiquidGlassBottomBar(
    barState: LiquidState,
    tabs: List<TabItemData>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onCenterTap: (() -> Unit)? = null,
    centerSelected: Boolean = false
) {
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val barTint = if (isDark) Color(0xFF0D1117).copy(alpha = 0.45f)
                  else Color.White.copy(alpha = 0.55f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.12f)
                      else Color.White.copy(alpha = 0.50f)

    val barHeight = 68.dp
    val bumpHeight = 30.dp
    val bumpWidth = 82.dp
    val cornerRadius = 28.dp
    val totalHeight = barHeight + bumpHeight

    // Custom shape: pill bar with a smooth bump at center top
    val bumpBarShape = remember {
        GenericShape { size, _ ->
            val w = size.width
            val h = size.height
            with(density) {
                val r = cornerRadius.toPx()
                val bH = bumpHeight.toPx()
                val bW = bumpWidth.toPx()
                val barTop = bH

                val bumpCx = w / 2f
                val bumpLeft = bumpCx - bW / 2f
                val bumpRight = bumpCx + bW / 2f
                val ctrl = bW * 0.28f

                // Start top-left corner
                moveTo(0f, barTop + r)
                arcTo(Rect(0f, barTop, 2 * r, barTop + 2 * r), 180f, 90f, false)

                // Top edge → bump up
                lineTo(bumpLeft - ctrl, barTop)
                cubicTo(
                    bumpLeft, barTop,
                    bumpLeft + ctrl * 0.3f, 0f,
                    bumpCx, 0f
                )
                // Bump down → top edge
                cubicTo(
                    bumpRight - ctrl * 0.3f, 0f,
                    bumpRight, barTop,
                    bumpRight + ctrl, barTop
                )

                // Top-right corner
                lineTo(w - r, barTop)
                arcTo(Rect(w - 2 * r, barTop, w, barTop + 2 * r), -90f, 90f, false)

                // Right side → bottom-right corner
                lineTo(w, h - r)
                arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)

                // Bottom → bottom-left corner
                lineTo(r, h)
                arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)

                close()
            }
        }
    }

    val indicatorSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 180f)
    val animatedTabIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = indicatorSpec,
        label = "tabIndicator"
    )

    Box(
        modifier = modifier
            // displayCutoutPadding handles side notches (edge cutouts on
            // certain Samsungs / curved Realmes) so the bar doesn't get
            // visually shifted by an asymmetric safe area.
            .displayCutoutPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .navigationBarsPadding()
            .fillMaxWidth()
    ) {
        // Frosted glass bar with bump
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight)
                .clip(bumpBarShape)
                .liquid(barState) {
                    frost = 28.dp
                    refraction = 0.15f
                    curve = 0.4f
                    shape = bumpBarShape
                    edge = 0.08f
                    tint = barTint
                    saturation = 1.3f
                    contrast = 1.15f
                    dispersion = 0.04f
                }
                .border(width = 0.5.dp, color = borderColor, shape = bumpBarShape)
        )

        // Sliding glass bubble — morphs from pill to circle near center tab
        val centerIdx = tabs.size / 2
        val bubbleTint = if (isDark) Color(0xFFe5e6ea).copy(alpha = 0.12f)
                         else Color(0xFFe5e6ea).copy(alpha = 0.45f)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight)
                .padding(horizontal = 6.dp)
        ) {
            val tabWidth = maxWidth / tabs.size
            val pillWidth = tabWidth * 0.9f
            val pillHeight = barHeight - 12.dp
            val circleD = 88.dp

            // How close to center: 1 = at center, 0 = 1+ tabs away
            // Smooth ease-in-out curve for buttery morph
            val distFromCenter = kotlin.math.abs(animatedTabIndex - centerIdx.toFloat())
            val rawMorph = (1f - distFromCenter).coerceIn(0f, 1f)
            val morphAmount = rawMorph * rawMorph * (3f - 2f * rawMorph) // smoothstep

            // Interpolate dimensions: pill → circle
            val morphW = pillWidth + (circleD - pillWidth) * morphAmount
            val morphH = pillHeight + (circleD - pillHeight) * morphAmount
            val morphCorner = 20.dp * (1f - morphAmount) + (morphH / 2) * morphAmount
            val morphShape = RoundedCornerShape(morphCorner)

            // X: centered within the animated tab position
            val morphX = tabWidth * animatedTabIndex + (tabWidth - morphW) / 2

            // Y: from bar center → bump center
            val barCenterY = bumpHeight + (barHeight - morphH) / 2
            val bumpCenterY = (totalHeight - circleD) / 2 - 2.dp
            val morphY = barCenterY + (bumpCenterY - barCenterY) * morphAmount

            Box(
                modifier = Modifier
                    .offset(x = morphX, y = morphY)
                    .width(morphW)
                    .height(morphH)
                    .clip(morphShape)
                    .liquid(barState) {
                        frost = 10.dp + 6.dp * morphAmount
                        refraction = 0.20f
                        curve = 0.7f
                        edge = 0.04f
                        shape = morphShape
                        tint = bubbleTint
                        saturation = 1.2f
                        contrast = 1.10f
                        dispersion = 0.03f
                    }
            )
        }

        // Tab icons and labels
        val haptic = LocalHapticFeedback.current
        val centerIndex = tabs.size / 2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Bottom
        ) {
            tabs.forEachIndexed { index, tab ->
                val isCenter = index == centerIndex
                val selected = if (isCenter) centerSelected else (index == selectedIndex)
                val selectedColor = MaterialTheme.colorScheme.primary
                val unselectedColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
                val tint = if (selected) selectedColor else unselectedColor

                val bounceScale = remember { Animatable(1f) }
                var centerTapCount by remember { mutableStateOf(0) }
                val coroutineScope = rememberCoroutineScope()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (isCenter) Modifier.height(totalHeight) else Modifier.height(barHeight))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    bounceScale.snapTo(1.3f)
                                    bounceScale.animateTo(
                                        1f,
                                        spring(dampingRatio = 0.4f, stiffness = 400f)
                                    )
                                }
                                if (isCenter && onCenterTap != null) {
                                    centerTapCount++
                                    onCenterTap()
                                } else {
                                    onTabSelected(index)
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCenter) {
                        // Center tab — sits in the bump, much bigger icon
                        // Levitating float animation
                        val levitateTransition = rememberInfiniteTransition(label = "levitate")
                        val levitateY by levitateTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1800, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "levY"
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.offset(y = (-12).dp + (-3 * levitateY).dp).graphicsLayer {
                                scaleX = bounceScale.value
                                scaleY = bounceScale.value
                            }
                        ) {
                            val chessLabelColor by androidx.compose.animation.animateColorAsState(
                                targetValue = if (selected) Color(0xFF4CAF50) else tint,
                                animationSpec = tween(500),
                                label = "chessLabel"
                            )
                            AnimatedControllerIcon(
                                selected = selected,
                                tint = tint,
                                size = 70.dp,
                                tapCounter = centerTapCount
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(tab.label, fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = chessLabelColor, textAlign = TextAlign.Center)
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.graphicsLayer {
                                scaleX = bounceScale.value
                                scaleY = bounceScale.value
                            }
                        ) {
                            when (index) {
                                0 -> AnimatedHomeIcon(selected = selected, tint = tint)
                                1 -> AnimatedStarIcon(selected = selected, tint = tint)
                                3 -> AnimatedCalculatorIcon(selected = selected, tint = tint)
                                4 -> AnimatedCalendarIcon(selected = selected, tint = tint)
                                else -> Icon(tab.icon, tab.label, Modifier.size(20.dp), tint = tint)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(tab.label, fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = tint, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

data class TabItemData(val label: String, val icon: ImageVector)

// ─── Animated bottom nav icons ──────────────────────────────────────────────

/**
 * Animated Home icon — door swings open, warm light spills out,
 * window lights up, chimney puffs smoke.
 */
@Composable
private fun AnimatedHomeIcon(selected: Boolean, tint: Color) {
    // Staggered animations for a richer sequence
    val doorOpen by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 200f),
        label = "doorOpen"
    )
    val windowGlow by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 150),
        label = "windowGlow"
    )
    val lightSpill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 100),
        label = "lightSpill"
    )

    // Looping smoke puff when selected
    val smokeOffset = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (selected) {
            while (true) {
                smokeOffset.animateTo(1f, tween(1200))
                smokeOffset.snapTo(0f)
            }
        } else {
            smokeOffset.snapTo(0f)
        }
    }

    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.08f

        // ── Roof triangle ──
        val roofPath = Path().apply {
            moveTo(w * 0.5f, h * 0.02f)
            lineTo(w * 0.0f, h * 0.40f)
            lineTo(w * 1.0f, h * 0.40f)
            close()
        }
        drawPath(roofPath, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // ── House body ──
        val bodyLeft = w * 0.14f
        val bodyRight = w * 0.86f
        val bodyTop = h * 0.40f
        val bodyBottom = h * 0.96f
        drawRect(tint, Offset(bodyLeft, bodyTop), Size(bodyRight - bodyLeft, bodyBottom - bodyTop), style = Stroke(width = stroke))

        // ── Window (left side) ── lights up with warm glow
        val winLeft = w * 0.20f
        val winTop = h * 0.50f
        val winSize = w * 0.18f
        if (windowGlow > 0f) {
            drawRect(Color(0xFFFFF176).copy(alpha = windowGlow * 0.6f),
                Offset(winLeft, winTop), Size(winSize, winSize))
        }
        drawRect(tint, Offset(winLeft, winTop), Size(winSize, winSize), style = Stroke(width = stroke * 0.7f))
        // Window cross
        drawLine(tint, Offset(winLeft + winSize * 0.5f, winTop), Offset(winLeft + winSize * 0.5f, winTop + winSize), strokeWidth = stroke * 0.5f)
        drawLine(tint, Offset(winLeft, winTop + winSize * 0.5f), Offset(winLeft + winSize, winTop + winSize * 0.5f), strokeWidth = stroke * 0.5f)

        // ── Door ── swings open from left hinge
        val doorLeft = w * 0.48f
        val doorRight = w * 0.72f
        val doorTop = h * 0.52f
        val doorW = doorRight - doorLeft
        val openW = doorW * (1f - doorOpen * 0.7f)

        // Light spilling out from door opening
        if (lightSpill > 0f) {
            // Warm trapezoid of light on the "ground"
            val lightPath = Path().apply {
                moveTo(doorLeft, doorTop)
                lineTo(doorRight, doorTop)
                lineTo(doorRight + w * 0.08f * lightSpill, bodyBottom)
                lineTo(doorLeft, bodyBottom)
                close()
            }
            drawPath(lightPath, Color(0xFFFFA726).copy(alpha = lightSpill * 0.45f))
        }

        // Door panel
        drawRect(tint, Offset(doorLeft, doorTop), Size(openW, bodyBottom - doorTop), style = Stroke(width = stroke * 0.7f))

        // Door knob
        drawCircle(tint, radius = w * 0.025f, center = Offset(doorLeft + openW * 0.78f, (doorTop + bodyBottom) * 0.52f))

        // ── Chimney ──
        val chimX = w * 0.72f
        val chimW = w * 0.10f
        val chimTop = h * 0.06f
        val chimBottom = h * 0.28f
        drawRect(tint, Offset(chimX, chimTop), Size(chimW, chimBottom - chimTop), style = Stroke(width = stroke * 0.7f))

        // Smoke puffs (3 circles rising & fading)
        if (selected && smokeOffset.value > 0f) {
            val s = smokeOffset.value
            val smokeColor = tint.copy(alpha = (1f - s) * 0.5f)
            val cx = chimX + chimW * 0.5f
            drawCircle(smokeColor, radius = w * 0.04f * (0.5f + s * 0.5f),
                center = Offset(cx - w * 0.02f * s, chimTop - h * 0.06f * s))
            if (s > 0.2f) {
                val s2 = (s - 0.2f) / 0.8f
                drawCircle(smokeColor.copy(alpha = (1f - s2) * 0.35f), radius = w * 0.035f * (0.5f + s2 * 0.5f),
                    center = Offset(cx + w * 0.03f * s2, chimTop - h * 0.10f * s2 - h * 0.02f))
            }
            if (s > 0.4f) {
                val s3 = (s - 0.4f) / 0.6f
                drawCircle(smokeColor.copy(alpha = (1f - s3) * 0.25f), radius = w * 0.03f * (0.5f + s3 * 0.5f),
                    center = Offset(cx - w * 0.01f * s3, chimTop - h * 0.14f * s3 - h * 0.04f))
            }
        }
    }
}

/**
 * Animated Calendar/Timetable icon — when selected, pages tear off in
 * randomised succession for ~2 seconds. Each page peels from the
 * top-right corner, drifts off the icon, fades, and shows a faint date
 * number behind it. Deselecting the tab snaps everything back to the
 * idle state with no reverse animation.
 */
@Composable
private fun AnimatedCalendarIcon(selected: Boolean, tint: Color) {
    val redTop = Color(0xFFE53935)
    val bindingColor by animateColorAsState(
        targetValue = if (selected) redTop else tint,
        animationSpec = tween(durationMillis = if (selected) 200 else 0),
        label = "calBinding"
    )
    val bindingFill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = if (selected) 200 else 0),
        label = "calBindFill"
    )

    val tear1 = remember { Animatable(0f) }
    val tear2 = remember { Animatable(0f) }
    val tear3 = remember { Animatable(0f) }
    val tear4 = remember { Animatable(0f) }

    // Per-tap random parameters: rotation, drift X/Y magnitudes, curl
    // size, displayed date number. Regenerated every time the tab
    // becomes selected so each tap looks different.
    var pageParams by remember { mutableStateOf(generateCalendarPageParams()) }

    LaunchedEffect(selected) {
        if (selected) {
            pageParams = generateCalendarPageParams()
            tear1.snapTo(0f); tear2.snapTo(0f); tear3.snapTo(0f); tear4.snapTo(0f)
            launch { tear1.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
            launch { delay(300); tear2.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
            launch { delay(620); tear3.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
            launch { delay(960); tear4.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        } else {
            // Snap to idle — no reverse animation
            tear1.snapTo(0f); tear2.snapTo(0f); tear3.snapTo(0f); tear4.snapTo(0f)
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.08f
        val cornerR = w * 0.13f

        val bodyTop = h * 0.22f
        val bodyLeft = w * 0.08f
        val bodyRight = w * 0.92f
        val bodyBottom = h * 0.94f
        val bindingH = (bodyBottom - bodyTop) * 0.26f
        val bindingBottom = bodyTop + bindingH

        // Red top binding — only when selected, no white face fill below
        if (bindingFill > 0.01f) {
            val bindingPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = bodyLeft,
                        top = bodyTop,
                        right = bodyRight,
                        bottom = bindingBottom,
                        topLeftCornerRadius = CornerRadius(cornerR),
                        topRightCornerRadius = CornerRadius(cornerR),
                        bottomLeftCornerRadius = CornerRadius.Zero,
                        bottomRightCornerRadius = CornerRadius.Zero
                    )
                )
            }
            drawPath(bindingPath, color = bindingColor.copy(alpha = bindingFill))
        }

        // Body outline — always visible
        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyRight - bodyLeft, bodyBottom - bodyTop),
            cornerRadius = CornerRadius(cornerR),
            style = Stroke(width = stroke)
        )

        // Two binding rings on top
        val ringR = w * 0.05f
        val ringY = bodyTop - ringR * 0.6f
        for (cx in listOf(w * 0.32f, w * 0.68f)) {
            drawCircle(
                color = tint,
                radius = ringR,
                center = Offset(cx, ringY),
                style = Stroke(width = stroke * 0.7f)
            )
        }

        // ── Tearing pages — peel-off-the-top desk-calendar style ──
        // Each tap regenerates random per-page parameters: rotation,
        // drift X/Y magnitudes, curl size, and the date number printed
        // faintly on the page as it tears away.
        fun drawTearingPage(progress: Float, params: CalendarPageParam) {
            if (progress <= 0.001f || progress >= 0.999f) return
            val p = progress
            val pageLeft = bodyLeft + stroke * 0.5f
            val pageRight = bodyRight - stroke * 0.5f
            val pageTop = bindingBottom + stroke * 0.2f
            val pageBottom = bodyBottom - stroke * 0.5f
            val pageW = pageRight - pageLeft
            val pageH = pageBottom - pageTop

            val rotDeg = p * params.rotMagnitude
            val driftX = p * pageW * params.driftXMag
            val driftY = -p * pageH * params.driftYMag
            val alpha = (1f - (p - 0.45f).coerceAtLeast(0f) / 0.55f).coerceIn(0f, 1f)
            val curlPhase = if (p < 0.5f) p * 2f else (1f - (p - 0.5f) * 2f)
            val curlSize = pageW * params.curlSizeMag * curlPhase

            // Date number drawn at page centre, tinted faint
            val pageCenterX = (pageLeft + pageRight) / 2f
            val pageCenterY = (pageTop + pageBottom) / 2f

            withTransform({
                translate(left = driftX, top = driftY)
                rotate(degrees = rotDeg, pivot = Offset(pageLeft, pageTop))
            }) {
                val pagePath = Path().apply {
                    moveTo(pageLeft, pageTop)
                    lineTo(pageRight - curlSize, pageTop)
                    quadraticTo(
                        pageRight - curlSize * 0.4f, pageTop + curlSize * 0.25f,
                        pageRight - curlSize * 0.55f, pageTop + curlSize * 0.55f
                    )
                    quadraticTo(
                        pageRight - curlSize * 0.7f, pageTop + curlSize * 0.85f,
                        pageRight, pageTop + curlSize
                    )
                    lineTo(pageRight, pageBottom)
                    lineTo(pageLeft, pageBottom)
                    close()
                }
                drawPath(pagePath, color = tint.copy(alpha = alpha * 0.18f))
                drawPath(
                    pagePath,
                    color = tint.copy(alpha = alpha * 0.9f),
                    style = Stroke(width = stroke * 0.55f, join = StrokeJoin.Round)
                )
                drawLine(
                    color = tint.copy(alpha = alpha * 0.6f),
                    start = Offset(pageRight - curlSize, pageTop),
                    end = Offset(pageRight, pageTop + curlSize),
                    strokeWidth = stroke * 0.4f,
                    cap = StrokeCap.Round
                )
                // Faint date number on the page
                val numStyle = TextStyle(
                    color = tint.copy(alpha = alpha * 0.55f),
                    fontSize = with(density) { (h * 0.30f).toSp() },
                    fontWeight = FontWeight.ExtraBold
                )
                val numLayout = textMeasurer.measure(params.number, numStyle)
                drawText(
                    textLayoutResult = numLayout,
                    topLeft = Offset(
                        pageCenterX - numLayout.size.width / 2f,
                        pageCenterY - numLayout.size.height / 2f
                    )
                )
            }
        }

        drawTearingPage(tear1.value, pageParams[0])
        drawTearingPage(tear2.value, pageParams[1])
        drawTearingPage(tear3.value, pageParams[2])
        drawTearingPage(tear4.value, pageParams[3])
    }
}

/** Per-page random tear parameters, regenerated on each calendar tap. */
private data class CalendarPageParam(
    val rotMagnitude: Float,
    val driftXMag: Float,
    val driftYMag: Float,
    val curlSizeMag: Float,
    val number: String
)

private fun generateCalendarPageParams(): List<CalendarPageParam> {
    val nums = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9",
        "10", "11", "12", "13", "14", "15", "20", "21", "22", "30", "31")
    val rng = java.util.Random()
    return List(4) {
        // Rotation: -180..-90 (mostly counter-clockwise upward)
        val rot = -90f - rng.nextFloat() * 90f
        // X drift: 0.2..0.8 (positive = up-right; negative half = up-left)
        val xMag = (0.2f + rng.nextFloat() * 0.6f) * (if (rng.nextBoolean()) 1f else -0.6f)
        val yMag = 0.55f + rng.nextFloat() * 0.6f
        val curl = 0.30f + rng.nextFloat() * 0.25f
        CalendarPageParam(rot, xMag, yMag, curl, nums[rng.nextInt(nums.size)])
    }
}

/**
 * Animated Calculator/GPA icon — grade cards (A+, A, O, S) drop into the
 * calculator's display in succession over ~2s when selected, with
 * sparkles around the top. Idle state shows a clean calculator outline
 * with a display strip and 2x3 button grid.
 */
@Composable
private fun AnimatedCalculatorIcon(selected: Boolean, tint: Color) {
    val card1 = remember { Animatable(0f) }
    val card2 = remember { Animatable(0f) }
    val card3 = remember { Animatable(0f) }
    val card4 = remember { Animatable(0f) }
    val sparkle by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = if (selected) 250 else 0),
        label = "gradeSparkle"
    )

    var cardParams by remember { mutableStateOf(generateGradeCardParams()) }

    LaunchedEffect(selected) {
        if (selected) {
            cardParams = generateGradeCardParams()
            card1.snapTo(0f); card2.snapTo(0f); card3.snapTo(0f); card4.snapTo(0f)
            launch { card1.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
            launch { delay(300); card2.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
            launch { delay(620); card3.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
            launch { delay(960); card4.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        } else {
            card1.snapTo(0f); card2.snapTo(0f); card3.snapTo(0f); card4.snapTo(0f)
        }
    }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val gradeGreen = Color(0xFF00C853)
    val orangeBtn = Color(0xFFFF6D00)

    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.08f
        val cornerR = w * 0.13f

        // Calculator body
        val bodyTop = h * 0.18f
        val bodyLeft = w * 0.14f
        val bodyRight = w * 0.86f
        val bodyBottom = h * 0.94f
        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyRight - bodyLeft, bodyBottom - bodyTop),
            cornerRadius = CornerRadius(cornerR),
            style = Stroke(width = stroke)
        )

        // Display strip (top quarter of calculator)
        val displayTop = bodyTop + h * 0.06f
        val displayBottom = bodyTop + (bodyBottom - bodyTop) * 0.32f
        val displayLeft = bodyLeft + w * 0.07f
        val displayRight = bodyRight - w * 0.07f
        drawRoundRect(
            color = tint.copy(alpha = 0.45f),
            topLeft = Offset(displayLeft, displayTop),
            size = Size(displayRight - displayLeft, displayBottom - displayTop),
            cornerRadius = CornerRadius(w * 0.04f),
            style = Stroke(width = stroke * 0.5f)
        )

        // 2x3 button grid below the display
        val gridTop = displayBottom + h * 0.04f
        val gridBottom = bodyBottom - h * 0.05f
        val btnW = (bodyRight - bodyLeft - w * 0.16f) / 3f
        val btnH = (gridBottom - gridTop - h * 0.04f) / 2f
        for (row in 0..1) {
            for (col in 0..2) {
                val cx = bodyLeft + w * 0.08f + btnW * col + btnW * 0.5f
                val cy = gridTop + btnH * row + btnH * 0.5f + (if (row == 1) h * 0.04f else 0f)
                val isOrange = row == 1 && col == 2
                if (isOrange) {
                    drawRoundRect(
                        color = orangeBtn,
                        topLeft = Offset(cx - btnW * 0.36f, cy - btnH * 0.36f),
                        size = Size(btnW * 0.72f, btnH * 0.72f),
                        cornerRadius = CornerRadius(w * 0.025f)
                    )
                } else {
                    drawRoundRect(
                        color = tint.copy(alpha = 0.55f),
                        topLeft = Offset(cx - btnW * 0.36f, cy - btnH * 0.36f),
                        size = Size(btnW * 0.72f, btnH * 0.72f),
                        cornerRadius = CornerRadius(w * 0.025f),
                        style = Stroke(width = stroke * 0.5f)
                    )
                }
            }
        }

        // ── Falling grade cards ──
        // Each card flies in from above, lands on the display, fades out.
        // Per-tap random params: starting X offset, drop rotation, grade
        // letter chosen from a pool.
        val displayCenterX = (displayLeft + displayRight) / 2f
        val displayCenterY = (displayTop + displayBottom) / 2f

        fun drawGradeCard(progress: Float, params: GradeCardParam) {
            if (progress <= 0.001f || progress >= 0.999f) return
            val p = progress
            val flyT = (p / 0.55f).coerceAtMost(1f)
            val fadeT = ((p - 0.55f) / 0.45f).coerceIn(0f, 1f)
            val alpha = 1f - fadeT
            val startX = displayCenterX + params.startXOffsetMag * w
            val startY = -h * (0.4f + params.startYOffsetMag)
            val cx = startX + (displayCenterX - startX) * flyT
            val cy = startY + (displayCenterY - startY) * flyT
            val rotDeg = (1f - flyT) * params.rotMagnitude
            val cardW = w * 0.36f
            val cardH = h * 0.26f

            withTransform({
                rotate(degrees = rotDeg, pivot = Offset(cx, cy))
            }) {
                drawRoundRect(
                    color = Color.White.copy(alpha = alpha * 0.95f),
                    topLeft = Offset(cx - cardW / 2f, cy - cardH / 2f),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(w * 0.04f)
                )
                drawRoundRect(
                    color = tint.copy(alpha = alpha * 0.55f),
                    topLeft = Offset(cx - cardW / 2f, cy - cardH / 2f),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(w * 0.04f),
                    style = Stroke(width = stroke * 0.4f)
                )
                val letterStyle = TextStyle(
                    color = gradeGreen.copy(alpha = alpha),
                    fontSize = with(density) { (h * 0.18f).toSp() },
                    fontWeight = FontWeight.ExtraBold
                )
                val layout = textMeasurer.measure(params.grade, letterStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        cx - layout.size.width / 2f,
                        cy - layout.size.height / 2f
                    )
                )
            }
        }

        drawGradeCard(card1.value, cardParams[0])
        drawGradeCard(card2.value, cardParams[1])
        drawGradeCard(card3.value, cardParams[2])
        drawGradeCard(card4.value, cardParams[3])

        // Sparkles around top corners while cards drop
        if (sparkle > 0.1f) {
            val sCol = Color(0xFFFFD600).copy(alpha = sparkle * 0.85f)
            val sparks = listOf(
                Offset(w * 0.10f, h * 0.10f),
                Offset(w * 0.88f, h * 0.08f),
                Offset(w * 0.92f, h * 0.30f),
                Offset(w * 0.06f, h * 0.30f)
            )
            for (sp in sparks) {
                val sl = w * 0.05f * sparkle
                drawLine(
                    color = sCol,
                    start = Offset(sp.x - sl, sp.y),
                    end = Offset(sp.x + sl, sp.y),
                    strokeWidth = stroke * 0.45f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = sCol,
                    start = Offset(sp.x, sp.y - sl),
                    end = Offset(sp.x, sp.y + sl),
                    strokeWidth = stroke * 0.45f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/** Per-card random grade-card parameters, regenerated on each GPA tap. */
private data class GradeCardParam(
    val startXOffsetMag: Float,
    val startYOffsetMag: Float,
    val rotMagnitude: Float,
    val grade: String
)

private fun generateGradeCardParams(): List<GradeCardParam> {
    val grades = listOf("A+", "A", "O", "B+", "B", "S")
    val rng = java.util.Random()
    return List(4) {
        val xOff = -0.25f + rng.nextFloat() * 0.5f
        val yOff = rng.nextFloat() * 0.3f
        val rot = (-30f + rng.nextFloat() * 60f)
        GradeCardParam(xOff, yOff, rot, grades[rng.nextInt(grades.size)])
    }
}

/**
 * Animated Star/CA Marks icon — a comet orbits around the star
 * leaving a glowing trail, and the star pulses with warm light.
 */
@Composable
private fun AnimatedStarIcon(selected: Boolean, tint: Color) {
    // Star glow
    val starGlow by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "starGlow"
    )
    val starScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "starScale"
    )

    // Comet — diagonal orbit, goes behind then in front of star
    val cometAngle = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (selected) {
            cometAngle.snapTo(0f)
            cometAngle.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing)
            )
        } else {
            cometAngle.snapTo(0f)
        }
    }

    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f

        // ── Warm glow behind star ──
        if (starGlow > 0f) {
            drawCircle(
                color = Color(0xFFFFD54F).copy(alpha = starGlow * 0.35f),
                radius = w * 0.45f * starGlow,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color(0xFFFFF176).copy(alpha = starGlow * 0.2f),
                radius = w * 0.55f * starGlow,
                center = Offset(cx, cy)
            )
        }

        // ── Comet orbit parameters — tilted ellipse for diagonal path ──
        val angle = cometAngle.value
        val showComet = angle > 0f
        val tiltRad = Math.toRadians(35.0) // diagonal tilt
        val orbitA = w * 0.48f  // semi-major axis (elongated)
        val orbitB = w * 0.16f  // semi-minor axis (narrow — elliptical)
        val rawRad = Math.toRadians((angle - 90).toDouble())
        val baseX = orbitA * kotlin.math.cos(rawRad).toFloat()
        val baseY = orbitB * kotlin.math.sin(rawRad).toFloat()
        val cometX = cx + (baseX * kotlin.math.cos(tiltRad) - baseY * kotlin.math.sin(tiltRad)).toFloat()
        val cometY = cy + (baseX * kotlin.math.sin(tiltRad) + baseY * kotlin.math.cos(tiltRad)).toFloat()
        // Behind star when on far side of ellipse (back half of orbit)
        val isBehind = angle in 50f..230f

        // ── Draw comet BEHIND star ──
        if (showComet && isBehind) {
            val trailLength = 80f
            val trailSteps = 12
            for (i in 0 until trailSteps) {
                val ta = angle - (trailLength * i / trailSteps)
                val tr = Math.toRadians((ta - 90).toDouble())
                val tbx = orbitA * kotlin.math.cos(tr).toFloat()
                val tby = orbitB * kotlin.math.sin(tr).toFloat()
                val tx = cx + (tbx * kotlin.math.cos(tiltRad) - tby * kotlin.math.sin(tiltRad)).toFloat()
                val ty = cy + (tbx * kotlin.math.sin(tiltRad) + tby * kotlin.math.cos(tiltRad)).toFloat()
                val trailAlpha = (1f - i.toFloat() / trailSteps) * 0.4f // dimmer behind
                val trailRadius = w * 0.03f * (1f - i.toFloat() / trailSteps)
                drawCircle(color = Color(0xFF4FC3F7).copy(alpha = trailAlpha), radius = trailRadius, center = Offset(tx, ty))
            }
            drawCircle(color = Color(0xFF81D4FA).copy(alpha = 0.5f), radius = w * 0.04f, center = Offset(cometX, cometY))
            drawCircle(color = Color.White.copy(alpha = 0.4f), radius = w * 0.02f, center = Offset(cometX, cometY))
        }

        // ── Star shape ──
        val scale = starScale
        val outerR = w * 0.32f * scale
        val innerR = w * 0.14f * scale
        val starColor = if (starGlow > 0.5f) {
            Color(
                red = tint.red + (Color(0xFFFFD54F).red - tint.red) * starGlow,
                green = tint.green + (Color(0xFFFFD54F).green - tint.green) * starGlow,
                blue = tint.blue + (Color(0xFFFFD54F).blue - tint.blue) * starGlow,
                alpha = 1f
            )
        } else tint

        val starPath = Path().apply {
            val startAngle = -Math.PI / 2
            for (i in 0 until 5) {
                val outerAngle = startAngle + i * 2 * Math.PI / 5
                val innerAngle = startAngle + (i + 0.5) * 2 * Math.PI / 5
                val ox = cx + outerR * kotlin.math.cos(outerAngle).toFloat()
                val oy = cy + outerR * kotlin.math.sin(outerAngle).toFloat()
                val ix = cx + innerR * kotlin.math.cos(innerAngle).toFloat()
                val iy = cy + innerR * kotlin.math.sin(innerAngle).toFloat()
                if (i == 0) moveTo(ox, oy) else lineTo(ox, oy)
                lineTo(ix, iy)
            }
            close()
        }
        drawPath(starPath, color = starColor)
        drawPath(starPath, color = tint, style = Stroke(width = w * 0.05f, join = StrokeJoin.Round))

        // ── Draw comet IN FRONT of star ──
        if (showComet && !isBehind) {
            val trailLength = 80f
            val trailSteps = 12
            for (i in 0 until trailSteps) {
                val ta = angle - (trailLength * i / trailSteps)
                val tr = Math.toRadians((ta - 90).toDouble())
                val tbx = orbitA * kotlin.math.cos(tr).toFloat()
                val tby = orbitB * kotlin.math.sin(tr).toFloat()
                val tx = cx + (tbx * kotlin.math.cos(tiltRad) - tby * kotlin.math.sin(tiltRad)).toFloat()
                val ty = cy + (tbx * kotlin.math.sin(tiltRad) + tby * kotlin.math.cos(tiltRad)).toFloat()
                val trailAlpha = (1f - i.toFloat() / trailSteps) * 0.6f
                val trailRadius = w * 0.035f * (1f - i.toFloat() / trailSteps)
                drawCircle(color = Color(0xFF4FC3F7).copy(alpha = trailAlpha), radius = trailRadius, center = Offset(tx, ty))
            }
            drawCircle(color = Color(0xFF81D4FA), radius = w * 0.05f, center = Offset(cometX, cometY))
            drawCircle(color = Color.White.copy(alpha = 0.8f), radius = w * 0.025f, center = Offset(cometX, cometY))
        }

        // ── Small sparkle dots when glowing ──
        if (starGlow > 0.5f) {
            val sparkAlpha = (starGlow - 0.5f) * 2f * 0.5f
            drawCircle(Color.White.copy(alpha = sparkAlpha), w * 0.02f, Offset(w * 0.15f, h * 0.20f))
            drawCircle(Color.White.copy(alpha = sparkAlpha * 0.7f), w * 0.015f, Offset(w * 0.82f, h * 0.15f))
            drawCircle(Color.White.copy(alpha = sparkAlpha * 0.5f), w * 0.018f, Offset(w * 0.88f, h * 0.75f))
            drawCircle(Color.White.copy(alpha = sparkAlpha * 0.6f), w * 0.015f, Offset(w * 0.12f, h * 0.80f))
        }
    }
}

/**
 * Animated game controller icon — gamepad silhouette with two grips,
 * D-pad cross, four face buttons, twin thumbsticks. Tapping triggers
 * a quick wiggle (rotate -8° → +8° → 0) + scale pulse on the right
 * action-buttons cluster. When [selected] (popup open) → controller
 * tilts slightly + buttons glow.
 */
@Composable
private fun AnimatedControllerIcon(
    selected: Boolean,
    tint: Color,
    size: Dp = 70.dp,
    tapCounter: Int
) {
    // Trace-in sweep that plays once when the bottom bar first composes
    // (i.e. when the app is opened). A clipRect grows left→right exposing
    // each stroke segment as it passes, with a glowing leading edge.
    val trace = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        trace.snapTo(0f)
        trace.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }

    // Tap response: wiggle rotation + scale-down "press" + buttons flash.
    val wiggle = remember { Animatable(0f) }
    val press = remember { Animatable(0f) }
    LaunchedEffect(tapCounter) {
        if (tapCounter == 0) return@LaunchedEffect
        kotlinx.coroutines.coroutineScope {
            launch {
                wiggle.snapTo(-1f)
                wiggle.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
                wiggle.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 600f))
            }
            launch {
                press.snapTo(1f)
                press.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
            }
        }
    }

    // Idle tilt when popup is open
    val tilt by animateFloatAsState(
        targetValue = if (selected) 6f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "ctrlTilt"
    )

    Canvas(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                rotationZ = wiggle.value * 10f + tilt
                val s = 1f - press.value * 0.07f
                scaleX = s
                scaleY = s
            }
    ) {
        val w = this.size.width
        val h = this.size.height
        val reveal = trace.value.coerceIn(0f, 1f)
        val pressV = press.value

        // ── PS5 DualSense geometry ──
        // Sony DualSense is 160 × 106 mm (≈1.5 : 1). Inside a square the
        // body occupies a wide central band; grip lobes droop below to fill
        // the remaining vertical space. Layout (front view, left → right):
        //
        //   shoulder-L   touchpad   shoulder-R
        //      D-pad        PS         △
        //                              □ ○
        //                              ✕
        //          L-stick      R-stick
        //         /  grip lobe (left)  \         (right grip lobe)
        //
        // Thumbsticks are at the SAME vertical height (symmetric DualShock
        // layout, not Xbox/Switch staggered).

        val outlineStroke = w * 0.038f
        val detailStroke = w * 0.028f

        // Body extents — used for the status pip + grip-bottom reference.
        val bodyTop = h * 0.30f
        val gripBottom = h * 0.88f
        val cxBody = w * 0.50f

        // Touchpad — large, central, upper half.
        val tpRect = Rect(w * 0.395f, h * 0.36f, w * 0.605f, h * 0.49f)

        // D-pad cluster (upper-left of body, level with touchpad).
        val dpadCx = w * 0.20f
        val dpadCy = h * 0.43f
        val dpadArmL = w * 0.072f
        val dpadArmT = w * 0.023f

        // Face buttons (upper-right, diamond layout — △ ○ ✕ □).
        val faceCx = w * 0.80f
        val faceCy = h * 0.43f
        val faceR = w * 0.028f
        val faceOff = w * 0.062f
        val faceCenters = listOf(
            Offset(faceCx, faceCy - faceOff),
            Offset(faceCx + faceOff, faceCy),
            Offset(faceCx, faceCy + faceOff),
            Offset(faceCx - faceOff, faceCy),
        )

        // Thumbsticks — both at the same height in the lower-centre body.
        val stickR = w * 0.078f
        val stickInnerR = w * 0.030f
        val stickY = h * 0.65f
        val leftStick = Offset(w * 0.34f, stickY)
        val rightStick = Offset(w * 0.66f, stickY)

        // ── DualSense body silhouette ──
        // Top edge: L1/R1 shoulder bumps poke up at the outside, then a long
        // arc sweeps down into chunky grip lobes that angle outward more than
        // the DualShock 4. Centre bottom has a small dip where the palms rest.
        val bodyOutline = Path().apply {
            // Outer edge of left shoulder bump
            moveTo(w * 0.08f, h * 0.38f)
            // Up & over left shoulder
            cubicTo(
                w * 0.07f, h * 0.31f,
                w * 0.16f, h * 0.28f,
                w * 0.22f, h * 0.305f,
            )
            // Inner top edge between shoulder and centre
            cubicTo(
                w * 0.28f, h * 0.325f,
                w * 0.34f, h * 0.345f,
                w * 0.42f, h * 0.35f,
            )
            // Flat centre top (under touchpad)
            cubicTo(
                w * 0.46f, h * 0.355f,
                w * 0.54f, h * 0.355f,
                w * 0.58f, h * 0.35f,
            )
            // Inner top edge on the right
            cubicTo(
                w * 0.66f, h * 0.345f,
                w * 0.72f, h * 0.325f,
                w * 0.78f, h * 0.305f,
            )
            // Up & over right shoulder (mirror of left)
            cubicTo(
                w * 0.84f, h * 0.28f,
                w * 0.93f, h * 0.31f,
                w * 0.92f, h * 0.38f,
            )
            // Right side curving outward toward right grip
            cubicTo(
                w * 0.99f, h * 0.52f,
                w * 0.96f, h * 0.66f,
                w * 0.88f, h * 0.73f,
            )
            // Right grip lobe — chunky, angled outward.
            cubicTo(
                w * 0.92f, h * 0.83f,
                w * 0.82f, gripBottom,
                w * 0.70f, h * 0.86f,
            )
            // Underside of right grip toward centre dip
            cubicTo(
                w * 0.62f, h * 0.82f,
                w * 0.56f, h * 0.78f,
                w * 0.50f, h * 0.78f,
            )
            // Underside of left grip
            cubicTo(
                w * 0.44f, h * 0.78f,
                w * 0.38f, h * 0.82f,
                w * 0.30f, h * 0.86f,
            )
            // Left grip lobe
            cubicTo(
                w * 0.18f, gripBottom,
                w * 0.08f, h * 0.83f,
                w * 0.12f, h * 0.73f,
            )
            // Left side back up
            cubicTo(
                w * 0.04f, h * 0.66f,
                w * 0.01f, h * 0.52f,
                w * 0.08f, h * 0.38f,
            )
            close()
        }

        // Subtle "white shade" fill — very low alpha so it reads as a hint
        // of glass behind the outline, not a solid silhouette.
        val innerShade = Color.White.copy(alpha = 0.08f)

        clipRect(left = 0f, top = 0f, right = w * reveal, bottom = h) {
            // 1) Thin interior fill — barely visible glass tint.
            drawPath(bodyOutline, color = innerShade)

            // 2) Body outline (the highlight border).
            drawPath(
                bodyOutline,
                color = tint,
                style = Stroke(width = outlineStroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // 3) Touchpad — outline only, slightly lighter shade behind.
            drawRoundRect(
                color = innerShade,
                topLeft = Offset(tpRect.left, tpRect.top),
                size = Size(tpRect.width, tpRect.height),
                cornerRadius = CornerRadius(w * 0.015f, w * 0.015f),
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(tpRect.left, tpRect.top),
                size = Size(tpRect.width, tpRect.height),
                cornerRadius = CornerRadius(w * 0.015f, w * 0.015f),
                style = Stroke(width = detailStroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // 4) D-pad — plus rendered as two thin lines, rounded caps.
            drawLine(
                tint,
                Offset(dpadCx - dpadArmL, dpadCy), Offset(dpadCx + dpadArmL, dpadCy),
                strokeWidth = dpadArmT * 2f, cap = StrokeCap.Round,
            )
            drawLine(
                tint,
                Offset(dpadCx, dpadCy - dpadArmL), Offset(dpadCx, dpadCy + dpadArmL),
                strokeWidth = dpadArmT * 2f, cap = StrokeCap.Round,
            )

            // 5) Face buttons — hollow rings. On tap they fill briefly.
            for (c in faceCenters) {
                if (pressV > 0.05f) {
                    drawCircle(tint.copy(alpha = pressV), faceR * 0.85f, c)
                }
                drawCircle(tint, faceR, c, style = Stroke(width = detailStroke))
            }

            // 6) Thumbsticks — outer ring + inner nub.
            drawCircle(tint, stickR, leftStick, style = Stroke(width = detailStroke))
            drawCircle(tint, stickR, rightStick, style = Stroke(width = detailStroke))
            drawCircle(tint, stickInnerR, leftStick)
            drawCircle(tint, stickInnerR, rightStick)

            // 7) PS button — small dot below touchpad centre.
            drawCircle(
                tint,
                radius = w * 0.012f,
                center = Offset(cxBody, tpRect.bottom + h * 0.04f),
            )

            // Status pip when popup is open.
            if (selected) {
                drawCircle(
                    tint.copy(alpha = 0.9f),
                    radius = h * 0.016f,
                    center = Offset(cxBody, bodyTop - h * 0.04f),
                )
            }
        }

        // Glowing leading edge during trace-in sweep.
        if (reveal < 1f) {
            val edgeX = w * reveal
            val edgeHalfW = w * 0.045f
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, tint.copy(alpha = 0.85f), Color.Transparent),
                    startX = edgeX - edgeHalfW,
                    endX = edgeX + edgeHalfW,
                ),
                topLeft = Offset(edgeX - edgeHalfW, h * 0.10f),
                size = Size(edgeHalfW * 2f, h * 0.80f),
            )
        }
    }
}

/**
 * Animated Chess icon — true isometric 3D chessboard with thick rounded base,
 * raised diamond tiles, knight L-hops + bishop diagonal slides. Infinite loop.
 */
@Composable
private fun AnimatedChessIcon(selected: Boolean, tint: Color, size: Dp = 24.dp) {
    // ── Randomized piece movement — no repeating pattern ──
    // Knight valid L-moves on 4x4 grid
    fun knightTargets(r: Int, c: Int): List<Pair<Int, Int>> {
        val deltas = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
        return deltas.map { (dr, dc) -> r + dr to c + dc }
            .filter { (nr, nc) -> nr in 0..3 && nc in 0..3 }
    }
    // Bishop valid diagonal moves on 4x4 grid
    fun bishopTargets(r: Int, c: Int): List<Pair<Int, Int>> {
        val targets = mutableListOf<Pair<Int, Int>>()
        for ((dr, dc) in listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)) {
            var nr = r + dr; var nc = c + dc
            while (nr in 0..3 && nc in 0..3) {
                targets.add(nr to nc)
                nr += dr; nc += dc
            }
        }
        return targets
    }

    val knightPos = remember { Animatable(0f, ) }
    val knightPosY = remember { Animatable(0f, ) }
    val bishopPos = remember { Animatable(0f, ) }
    val bishopPosY = remember { Animatable(0f, ) }

    // Store current grid positions
    val knightRow = remember { mutableIntStateOf(0) }
    val knightCol = remember { mutableIntStateOf(0) }
    val bishopRow = remember { mutableIntStateOf(3) }
    val bishopCol = remember { mutableIntStateOf(3) }

    // Knight random movement loop
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay((800L..1400L).random())
            val targets = knightTargets(knightRow.intValue, knightCol.intValue)
            if (targets.isNotEmpty()) {
                val (nr, nc) = targets.random()
                val spec = tween<Float>(durationMillis = (500..900).random(), easing = FastOutSlowInEasing)
                kotlinx.coroutines.coroutineScope {
                    launch { knightPos.animateTo(nc.toFloat(), spec) }
                    launch { knightPosY.animateTo(nr.toFloat(), spec) }
                }
                knightRow.intValue = nr
                knightCol.intValue = nc
            }
        }
    }

    // Bishop random movement loop
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500L)
        while (true) {
            kotlinx.coroutines.delay((900L..1600L).random())
            val targets = bishopTargets(bishopRow.intValue, bishopCol.intValue)
            if (targets.isNotEmpty()) {
                val (nr, nc) = targets.random()
                val spec = tween<Float>(durationMillis = (400..800).random(), easing = FastOutSlowInEasing)
                kotlinx.coroutines.coroutineScope {
                    launch { bishopPos.animateTo(nc.toFloat(), spec) }
                    launch { bishopPosY.animateTo(nr.toFloat(), spec) }
                }
                bishopRow.intValue = nr
                bishopCol.intValue = nc
            }
        }
    }

    val selectGlow by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(500),
        label = "chessGlow"
    )

    val colorSpec = tween<Color>(500)
    // ── Animate tile colors: slate → chess.com green/cream on select ──
    val lightTile by androidx.compose.animation.animateColorAsState(
        if (selected) Color(0xFFEBECD0) else Color(0xFFB8BCC8), colorSpec, label = "lt")
    val darkTile by androidx.compose.animation.animateColorAsState(
        if (selected) Color(0xFF739552) else Color(0xFF4A5568), colorSpec, label = "dt")
    val lightSideR by androidx.compose.animation.animateColorAsState(
        if (selected) Color(0xFFD4D4B8) else Color(0xFF9BA0AC), colorSpec, label = "lsr")
    val lightSideF by androidx.compose.animation.animateColorAsState(
        if (selected) Color(0xFFDCDCC2) else Color(0xFFA8ADBA), colorSpec, label = "lsf")
    val darkSideR by androidx.compose.animation.animateColorAsState(
        if (selected) Color(0xFF5D7A3A) else Color(0xFF3A4358), colorSpec, label = "dsr")
    val darkSideF by androidx.compose.animation.animateColorAsState(
        if (selected) Color(0xFF678442) else Color(0xFF414B60), colorSpec, label = "dsf")
    val baseTopC by androidx.compose.animation.animateColorAsState(
        if (selected) Color(0xFF4A6130) else Color(0xFF3D4A5C), colorSpec, label = "bt")
    val baseFrontC by androidx.compose.animation.animateColorAsState(
        if (selected) Color(0xFF3B5128) else Color(0xFF2D3748), colorSpec, label = "bf")
    val baseShadowC by androidx.compose.animation.animateColorAsState(
        if (selected) Color(0xFF2D3E1E) else Color(0xFF1A2332), colorSpec, label = "bs")

    Canvas(modifier = Modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()
        val cx = w * 0.5f

        // ── Isometric projection ──
        // Grid center, tile half-sizes for isometric diamond layout
        val gridN = 4
        val tileHW = w * 0.105f   // half-width of a tile diamond
        val tileHH = tileHW * 0.55f // half-height (foreshortened)
        val tileD = w * 0.035f     // tile thickness (3D depth)
        val boardCx = cx
        val boardCy = h * 0.38f    // board center on screen

        // Convert grid (row, col) to isometric screen position (top of tile diamond)
        fun isoPos(row: Float, col: Float): Offset {
            val sx = boardCx + (col - row) * tileHW
            val sy = boardCy + (col + row) * tileHH
            return Offset(sx, sy)
        }

        // 4 corners of a tile's top face (diamond shape)
        fun tileDiamond(row: Int, col: Int): List<Offset> {
            val top = isoPos(row.toFloat(), col.toFloat())          // top vertex
            val right = isoPos(row.toFloat(), col + 1f)             // right vertex
            val bottom = isoPos(row + 1f, col + 1f)                 // bottom vertex
            val left = isoPos(row + 1f, col.toFloat())              // left vertex
            return listOf(top, right, bottom, left)
        }

        // ── Draw thick rounded platform base ──
        // The base sits below all tiles. We draw it as an isometric box.
        val baseD = w * 0.07f // base thickness
        val pad = 0.3f // extra padding in grid units

        val bTopL = isoPos(gridN + pad, -pad)          // bottom-left of board + padding
        val bTopR = isoPos(-pad, gridN + pad)           // bottom-right
        val bTopT = isoPos(-pad, -pad)                  // top
        val bTopB = isoPos(gridN + pad, gridN + pad)    // bottom

        // Base top face (visible rim around tiles, slightly below tile bottoms)
        val baseTopY = tileD * 0.8f
        val baseTopPath = Path().apply {
            moveTo(bTopT.x, bTopT.y + baseTopY)
            lineTo(bTopR.x, bTopR.y + baseTopY)
            lineTo(bTopB.x, bTopB.y + baseTopY)
            lineTo(bTopL.x, bTopL.y + baseTopY)
            close()
        }
        drawPath(baseTopPath, baseTopC)
        // Glossy shine on base top
        val baseGlossPath = Path().apply {
            moveTo(bTopT.x, bTopT.y + baseTopY)
            lineTo(bTopR.x, bTopR.y + baseTopY)
            lineTo((bTopR.x + bTopB.x) * 0.5f, (bTopR.y + bTopB.y) * 0.5f + baseTopY)
            lineTo((bTopT.x + bTopL.x) * 0.5f, (bTopT.y + bTopL.y) * 0.5f + baseTopY)
            close()
        }
        drawPath(baseGlossPath, Color.White.copy(alpha = 0.08f))

        // Base front-left face
        val baseFrontLeftPath = Path().apply {
            moveTo(bTopL.x, bTopL.y + baseTopY)
            lineTo(bTopB.x, bTopB.y + baseTopY)
            lineTo(bTopB.x, bTopB.y + baseTopY + baseD)
            lineTo(bTopL.x, bTopL.y + baseTopY + baseD)
            close()
        }
        drawPath(baseFrontLeftPath, baseFrontC)
        // Glossy highlight strip on front-left
        val bflMidY1 = (bTopL.y + baseTopY) * 0.7f + (bTopL.y + baseTopY + baseD) * 0.3f
        val bflMidY2 = (bTopB.y + baseTopY) * 0.7f + (bTopB.y + baseTopY + baseD) * 0.3f
        drawLine(Color.White.copy(alpha = 0.12f), Offset(bTopL.x, bflMidY1), Offset(bTopB.x, bflMidY2), strokeWidth = w * 0.008f)

        // Base front-right face
        val baseFrontRightPath = Path().apply {
            moveTo(bTopR.x, bTopR.y + baseTopY)
            lineTo(bTopB.x, bTopB.y + baseTopY)
            lineTo(bTopB.x, bTopB.y + baseTopY + baseD)
            lineTo(bTopR.x, bTopR.y + baseTopY + baseD)
            close()
        }
        drawPath(baseFrontRightPath, baseShadowC)

        // ── Draw tiles back-to-front ──
        for (row in 0 until gridN) {
            for (col in 0 until gridN) {
                val isLight = (row + col) % 2 == 0
                val topColor = if (isLight) lightTile else darkTile
                val sideRColor = if (isLight) lightSideR else darkSideR
                val sideFColor = if (isLight) lightSideF else darkSideF
                val d = tileDiamond(row, col) // top, right, bottom, left

                // Right side face (bottom-right edge, visible)
                val rightFace = Path().apply {
                    moveTo(d[1].x, d[1].y)       // right
                    lineTo(d[2].x, d[2].y)       // bottom
                    lineTo(d[2].x, d[2].y + tileD) // bottom + depth
                    lineTo(d[1].x, d[1].y + tileD) // right + depth
                    close()
                }
                drawPath(rightFace, sideRColor)

                // Front side face (bottom-left edge, visible)
                val frontFace = Path().apply {
                    moveTo(d[3].x, d[3].y)       // left
                    lineTo(d[2].x, d[2].y)       // bottom
                    lineTo(d[2].x, d[2].y + tileD)
                    lineTo(d[3].x, d[3].y + tileD)
                    close()
                }
                drawPath(frontFace, sideFColor)

                // Top face (diamond)
                val topFace = Path().apply {
                    moveTo(d[0].x, d[0].y)
                    lineTo(d[1].x, d[1].y)
                    lineTo(d[2].x, d[2].y)
                    lineTo(d[3].x, d[3].y)
                    close()
                }
                drawPath(topFace, topColor)

                // ── Ultra glossy: gradient highlight on upper half of each tile ──
                // Top-left triangle highlight (specular band)
                val glossTop = Path().apply {
                    moveTo(d[0].x, d[0].y)
                    lineTo(d[1].x, d[1].y)
                    lineTo((d[1].x + d[2].x) * 0.5f, (d[1].y + d[2].y) * 0.5f)
                    lineTo((d[0].x + d[3].x) * 0.5f, (d[0].y + d[3].y) * 0.5f)
                    close()
                }
                drawPath(glossTop, Color.White.copy(alpha = if (isLight) 0.28f else 0.15f))

                // Specular hotspot — small bright spot near top vertex
                val specX = d[0].x * 0.55f + d[1].x * 0.3f + d[3].x * 0.15f
                val specY = d[0].y * 0.55f + d[1].y * 0.3f + d[3].y * 0.15f
                drawCircle(
                    Color.White.copy(alpha = if (isLight) 0.35f else 0.2f),
                    radius = tileHW * 0.18f,
                    center = Offset(specX, specY)
                )

                // Bottom-right darken for depth contrast
                val glossBottom = Path().apply {
                    moveTo(d[2].x, d[2].y)
                    lineTo(d[3].x, d[3].y)
                    lineTo((d[0].x + d[3].x) * 0.5f, (d[0].y + d[3].y) * 0.5f)
                    lineTo((d[1].x + d[2].x) * 0.5f, (d[1].y + d[2].y) * 0.5f)
                    close()
                }
                drawPath(glossBottom, Color.Black.copy(alpha = if (isLight) 0.06f else 0.1f))

                // Bright edge highlight along top-left edges (like polished surface catching light)
                drawLine(Color.White.copy(alpha = if (isLight) 0.5f else 0.3f),
                    d[0], d[1], strokeWidth = w * 0.006f)
                drawLine(Color.White.copy(alpha = if (isLight) 0.35f else 0.2f),
                    d[0], d[3], strokeWidth = w * 0.005f)

                // Dark edge along bottom-right edges
                drawLine(Color.Black.copy(alpha = 0.15f), d[1], d[2], strokeWidth = w * 0.005f)
                drawLine(Color.Black.copy(alpha = 0.12f), d[2], d[3], strokeWidth = w * 0.005f)

                // Side face glossy highlight strip
                val sideMidY = (d[1].y + d[1].y + tileD) * 0.5f
                drawLine(Color.White.copy(alpha = 0.12f),
                    Offset(d[1].x, sideMidY), Offset(d[2].x, (d[2].y + d[2].y + tileD) * 0.5f),
                    strokeWidth = w * 0.004f)
            }
        }

        // ── Piece positions from animated values ──
        fun cellCenterF(row: Float, col: Float): Offset {
            // Interpolate isometric position for fractional grid coords
            val sx = boardCx + (col - row) * tileHW
            val sy = boardCy + (col + row) * tileHH + tileHH // offset to center of tile
            return Offset(sx, sy)
        }

        val knightScreenPos = cellCenterF(knightPosY.value, knightPos.value)
        val bishopScreenPos = cellCenterF(bishopPosY.value, bishopPos.value)

        // ── Highlight square under knight ──
        val kSnapRow = knightRow.intValue
        val kSnapCol = knightCol.intValue
        val sd = tileDiamond(kSnapRow, kSnapCol)
        val hlPath = Path().apply {
            moveTo(sd[0].x, sd[0].y); lineTo(sd[1].x, sd[1].y)
            lineTo(sd[2].x, sd[2].y); lineTo(sd[3].x, sd[3].y); close()
        }
        drawPath(hlPath, Color(0xFFF6F669).copy(alpha = 0.3f))

        // ── Draw Knight (white piece) ──
        val pr = tileHW * 0.6f
        val kx = knightScreenPos.x
        val ky = knightScreenPos.y

        // Shadow on board
        drawOval(Color.Black.copy(alpha = 0.2f),
            Offset(knightScreenPos.x - pr * 0.4f, knightScreenPos.y - pr * 0.12f),
            Size(pr * 0.8f, pr * 0.25f))
        // Base
        drawCircle(Color(0xFFD0D4DC), pr * 0.42f, Offset(kx, ky + pr * 0.04f))
        // Horse head
        val knightBody = Path().apply {
            moveTo(kx - pr * 0.25f, ky + pr * 0.04f)
            lineTo(kx - pr * 0.2f, ky - pr * 0.35f)
            lineTo(kx - pr * 0.06f, ky - pr * 0.58f)
            lineTo(kx + pr * 0.14f, ky - pr * 0.65f)
            lineTo(kx + pr * 0.25f, ky - pr * 0.42f)
            lineTo(kx + pr * 0.18f, ky - pr * 0.2f)
            lineTo(kx + pr * 0.25f, ky + pr * 0.04f)
            close()
        }
        drawPath(knightBody, Color(0xFFF0F0F0))
        drawPath(knightBody, Color(0xFFB0B4BC), style = Stroke(width = w * 0.01f))
        drawCircle(Color(0xFF3A3A3A), pr * 0.035f, Offset(kx + pr * 0.05f, ky - pr * 0.46f))

        // ── Draw Bishop (dark piece) ──
        val bx = bishopScreenPos.x
        val by = bishopScreenPos.y

        drawOval(Color.Black.copy(alpha = 0.2f),
            Offset(bishopScreenPos.x - pr * 0.35f, bishopScreenPos.y - pr * 0.1f),
            Size(pr * 0.7f, pr * 0.22f))
        drawCircle(Color(0xFF4A4E58), pr * 0.38f, Offset(bx, by + pr * 0.03f))
        val bishopBody = Path().apply {
            moveTo(bx - pr * 0.28f, by + pr * 0.03f)
            lineTo(bx - pr * 0.16f, by - pr * 0.28f)
            lineTo(bx - pr * 0.05f, by - pr * 0.5f)
            lineTo(bx, by - pr * 0.65f)
            lineTo(bx + pr * 0.05f, by - pr * 0.5f)
            lineTo(bx + pr * 0.16f, by - pr * 0.28f)
            lineTo(bx + pr * 0.28f, by + pr * 0.03f)
            close()
        }
        drawPath(bishopBody, Color(0xFF2D3142))
        drawPath(bishopBody, Color(0xFF1A1E2E), style = Stroke(width = w * 0.01f))
        drawLine(Color(0xFF555B6E), Offset(bx - pr * 0.08f, by - pr * 0.2f),
            Offset(bx + pr * 0.05f, by - pr * 0.45f), strokeWidth = w * 0.008f)
        drawCircle(Color.White.copy(alpha = 0.18f), pr * 0.04f, Offset(bx - pr * 0.06f, by - pr * 0.4f))

    }
}

// ─── Lightweight cards for scrolling lists (no GPU cost) ─────────────────────

@Composable
fun GlassListCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassCardShapeSmall,
    tintColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val fillColor = if (isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.50f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.55f)
    val highlightColor = if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.20f)

    Column(
        modifier = modifier
            .clip(shape)
            .border(0.5.dp, borderColor, shape)
            .drawBehind {
                drawRect(fillColor)
                if (tintColor != Color.Transparent) drawRect(tintColor)
                drawRect(Brush.verticalGradient(listOf(highlightColor, Color.Transparent), startY = 0f, endY = size.height * 0.4f))
            },
        content = content
    )
}

@Composable
fun GlassListSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassCardShapeSmall,
    tintColor: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val fillColor = if (isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.50f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.55f)

    Box(
        modifier = modifier.clip(shape).border(0.5.dp, borderColor, shape)
            .drawBehind { drawRect(fillColor); if (tintColor != Color.Transparent) drawRect(tintColor) },
        content = content
    )
}

@Composable
fun GlassCardFallback(
    modifier: Modifier = Modifier,
    shape: Shape = GlassCardShape,
    tintColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val fillColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.55f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.60f)
    val highlightColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.25f)

    Column(
        modifier = modifier.clip(shape).border(0.5.dp, borderColor, shape)
            .drawBehind {
                drawRect(fillColor)
                if (tintColor != Color.Transparent) drawRect(tintColor)
                drawRect(Brush.verticalGradient(listOf(highlightColor, Color.Transparent), startY = 0f, endY = size.height * 0.5f))
            },
        content = content
    )
}

// ─── Background helpers ──────────────────────────────────────────────────────

enum class BackgroundVariant { Default, Login, Attendance }

private fun getGradientColors(variant: BackgroundVariant, isDark: Boolean): List<Color> = when (variant) {
    BackgroundVariant.Default -> if (isDark) listOf(Color(0xFF0A1628), Color(0xFF0D1B2A), Color(0xFF1B2838))
        else listOf(Color(0xFFE8F0FE), Color(0xFFD4E4FA), Color(0xFFC2D9F7))
    BackgroundVariant.Login -> if (isDark) listOf(Color(0xFF0A1628), Color(0xFF0F1D30), Color(0xFF162544))
        else listOf(Color(0xFFE0ECFF), Color(0xFFCCDDFF), Color(0xFFB8D0FF))
    BackgroundVariant.Attendance -> if (isDark) listOf(Color(0xFF0A1628), Color(0xFF0B1A2E), Color(0xFF0F2137))
        else listOf(Color(0xFFE8F4FD), Color(0xFFDCEEFA), Color(0xFFCEE5F6))
}

// ─── Rose-4 loading animation ───────────────────────────────────────────────

@Composable
fun RoseFourLoader(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "loader")

    val progress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 5400, easing = LinearEasing), RepeatMode.Restart),
        label = "progress"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 4500, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 28000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )

    Canvas(modifier = modifier.size(200.dp).graphicsLayer { rotationZ = rotation }) {
        val roseA = 9.2f
        val roseABoost = 0.60f
        val breathBase = 0.72f
        val breathBoost = 0.28f
        val k = 4
        val roseScale = 3.25f
        val particleCount = 78
        val trailSpan = 0.32f
        val TWO_PI = 2f * Math.PI.toFloat()

        val pulseAngle = pulse * TWO_PI + 0.55f
        val detailScale = 0.52f + ((sin(pulseAngle) + 1f) / 2f) * 0.48f
        val a = roseA + detailScale * roseABoost
        val cs = size.width / 100f

        val path = Path()
        for (i in 0..480) {
            val t = (i / 480f) * TWO_PI
            val r = a * (breathBase + detailScale * breathBoost) * cos(k * t)
            val x = (50f + cos(t) * r * roseScale) * cs
            val y = (50f + sin(t) * r * roseScale) * cs
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color.White.copy(alpha = 0.1f), style = Stroke(4.6f.dp.toPx(), cap = StrokeCap.Round))

        for (i in 0 until particleCount) {
            val tailOffset = i.toFloat() / (particleCount - 1)
            val p = ((progress - tailOffset * trailSpan) % 1f + 1f) % 1f
            val t = p * TWO_PI
            val r = a * (breathBase + detailScale * breathBoost) * cos(k * t)
            val px = (50f + cos(t) * r * roseScale) * cs
            val py = (50f + sin(t) * r * roseScale) * cs
            val fade = (1f - tailOffset).pow(0.56f)
            drawCircle(Color.White.copy(alpha = 0.04f + fade * 0.96f), radius = (0.9f + fade * 2.7f).dp.toPx(), center = Offset(px, py))
        }
    }
}

/**
 * Horizontally scrollable row of glass pill tabs that drift in from the right
 * with a staggered, slow "out of mist" entry. All pills share the widest pill's
 * width so they look uniform, even when [subText] returns null for some items.
 *
 * Pass [liquidState] (the same cardState the screen uses) to make pills sample
 * the gradient + weather behind them like the bottom dashboard tray.
 *
 * [subText] returns secondary text (label + color) shown below the main label.
 * Return ("n/e", muted) to mark a sem with no data — every pill stays the same
 * height so the row never jumps.
 */
@Composable
fun AnimatedSlideInTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    animationKey: Any = Unit,
    liquidState: LiquidState? = null,
    showSubTextRow: Boolean = false,
    subText: (@Composable (Int) -> Pair<String, Color>?)? = null,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val labelStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val subStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)

    val sizing = remember(items, showSubTextRow) {
        val labelMax = items.maxOfOrNull { textMeasurer.measure(it, labelStyle).size.width } ?: 0
        // For sub text row, use a sane fixed allowance instead of measuring per
        // composition (caller-provided values vary per recompose).
        val subMax = if (showSubTextRow) textMeasurer.measure("9.99", subStyle).size.width else 0
        val widthPx = maxOf(labelMax, subMax)
        with(density) { widthPx.toDp() }
    }
    // 28dp horizontal padding total inside pill, plus a hair of breathing room
    val pillWidth = sizing + 32.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, label ->
            SlideInPill(
                label = label,
                selected = index == selectedIndex,
                index = index,
                animationKey = animationKey,
                liquidState = liquidState,
                showSubTextRow = showSubTextRow,
                subText = subText?.invoke(index),
                fixedWidth = pillWidth,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun SlideInPill(
    label: String,
    selected: Boolean,
    index: Int,
    animationKey: Any,
    liquidState: LiquidState?,
    showSubTextRow: Boolean,
    subText: Pair<String, Color>?,
    fixedWidth: Dp,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()

    // Mist entry: drift from right + soft fade + slight scale + Modifier.blur.
    // Using a long, low-stiffness spring so it eases in slowly without overshoot.
    val translateXPx = remember { Animatable(with(density) { 96.dp.toPx() }) }
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.86f) }
    val blur = remember { Animatable(10f) }

    LaunchedEffect(animationKey) {
        translateXPx.snapTo(with(density) { 96.dp.toPx() })
        alpha.snapTo(0f)
        scale.snapTo(0.86f)
        blur.snapTo(10f)
        delay(index * 80L)
        launch {
            translateXPx.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 70f,
                ),
            )
        }
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 90f,
                ),
            )
        }
        launch {
            alpha.animateTo(1f, tween(durationMillis = 760))
        }
        blur.animateTo(0f, tween(durationMillis = 720))
    }

    val haptic = LocalHapticFeedback.current
    val pillShape = RoundedCornerShape(percent = 50)

    val targetTint = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else if (isDark) {
        Color(0xFF0D1117).copy(alpha = 0.40f)
    } else {
        Color.White.copy(alpha = 0.50f)
    }
    val targetBorder = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else if (isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.White.copy(alpha = 0.45f)
    }
    val animatedTint by animateColorAsState(targetTint, tween(220), label = "pill-tint")
    val animatedBorder by animateColorAsState(targetBorder, tween(220), label = "pill-border")
    val textColor by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(220),
        label = "pill-text",
    )

    val glassMod: Modifier = if (liquidState != null) {
        Modifier.liquid(liquidState) {
            frost = 22.dp
            refraction = 0.12f
            curve = 0.5f
            shape = pillShape
            edge = 0.06f
            tint = animatedTint
            saturation = 1.25f
            contrast = 1.10f
            dispersion = 0.03f
        }
    } else {
        Modifier.background(animatedTint, pillShape)
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = translateXPx.value
                this.alpha = alpha.value
                scaleX = scale.value
                scaleY = scale.value
            }
            .blur(blur.value.dp)
            .width(fixedWidth)
            .clip(pillShape)
            .then(glassMod)
            .border(0.7.dp, animatedBorder, pillShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
            )
            if (showSubTextRow) {
                val resolved = subText ?: ("n/e" to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
                Text(
                    text = resolved.first,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = resolved.second,
                    maxLines = 1,
                )
            }
        }
    }
}

// ─── Games popup (Chess / HumanBenchmark) ────────────────────────────────────

/**
 * Floating popup that emerges above the bottom bar when the controller
 * (center tab) is tapped. Two circular cards float up: Brain (left) +
 * Chess (right). Stagger fade + slide-in. Tap scrim to dismiss.
 *
 * [open] toggles visibility. [onDismiss] called on scrim tap.
 * [onChess] / [onBrain] = navigation actions.
 */
@Composable
fun GamesPopup(
    open: Boolean,
    onDismiss: () -> Unit,
    onChess: () -> Unit,
    onBrain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scrimAlpha by animateFloatAsState(
        targetValue = if (open) 0.55f else 0f,
        animationSpec = tween(220),
        label = "popupScrim"
    )
    val brainProgress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 280f),
        label = "brainProg"
    )
    val chessProgress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 220f),
        label = "chessProg"
    )

    if (!open && scrimAlpha < 0.01f && brainProgress < 0.01f && chessProgress < 0.01f) return

    // Full-screen scrim + button row
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // Floating buttons aligned just above the bottom bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 132.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            GamePopupButton(
                progress = brainProgress,
                label = "Brain",
                accent = Color(0xFFD8FF3C),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBrain()
                },
                icon = { iconTint, iconSize ->
                    PopupBrainIcon(tint = iconTint, size = iconSize)
                }
            )
            GamePopupButton(
                progress = chessProgress,
                label = "Chess",
                accent = Color(0xFFB68CFF),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onChess()
                },
                framed = false,
                icon = { iconTint, iconSize ->
                    PopupChessIcon(tint = iconTint, size = iconSize)
                }
            )
        }
    }
}

@Composable
private fun GamePopupButton(
    progress: Float,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    icon: @Composable (tint: Color, size: Dp) -> Unit,
    framed: Boolean = true,
) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF1B1F2A).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.92f)
    val textColor = if (isDark) Color.White else Color(0xFF1B1F2A)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 60f
                scaleX = 0.7f + progress * 0.3f
                scaleY = 0.7f + progress * 0.3f
            }
    ) {
        // Framed: circular pill with accent border (default — used by Brain).
        // Unframed: bare icon floating on the scrim (used by Chess so the
        // isometric board stands alone without a circle around it).
        val containerModifier = if (framed) {
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(bg)
                .border(2.dp, accent, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
        } else {
            Modifier
                .size(72.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
        }
        Box(modifier = containerModifier, contentAlignment = Alignment.Center) {
            icon(accent, if (framed) 40.dp else 64.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label.uppercase(),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.6.sp,
        )
    }
}

/** Brain icon for the HumanBenchmark popup button. */
@Composable
private fun PopupBrainIcon(tint: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        // Two brain hemispheres
        drawCircle(tint.copy(alpha = 0.35f), radius = w * 0.30f, center = Offset(cx - w * 0.13f, cy))
        drawCircle(tint.copy(alpha = 0.35f), radius = w * 0.30f, center = Offset(cx + w * 0.13f, cy))
        drawCircle(tint, radius = w * 0.30f, center = Offset(cx - w * 0.13f, cy), style = Stroke(width = w * 0.05f))
        drawCircle(tint, radius = w * 0.30f, center = Offset(cx + w * 0.13f, cy), style = Stroke(width = w * 0.05f))
        // Center divider
        drawLine(tint, Offset(cx, cy - h * 0.25f), Offset(cx, cy + h * 0.25f), strokeWidth = w * 0.04f)
        // Sparkle/synapse dots
        drawCircle(tint, radius = w * 0.04f, center = Offset(cx - w * 0.20f, cy - h * 0.08f))
        drawCircle(tint, radius = w * 0.04f, center = Offset(cx + w * 0.20f, cy + h * 0.10f))
    }
}

/**
 * Chess icon for the Games popup — reuses the bottom-bar's old isometric
 * 3D chessboard with knight L-hops + bishop diagonal slides. `selected=true`
 * so the loop runs while the popup is visible (the popup unmounts once
 * dismissed, naturally stopping the animation).
 */
@Composable
private fun PopupChessIcon(tint: Color, size: Dp) {
    AnimatedChessIcon(selected = true, tint = tint, size = size)
}
