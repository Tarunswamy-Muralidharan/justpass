package com.example.attendancewidgetlaudea.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    bottomBar: @Composable BoxScope.(barState: LiquidState) -> Unit = {},
    content: @Composable BoxScope.(cardState: LiquidState) -> Unit
) {
    val barState = rememberLiquidState()   // For bottom bar: blurs everything
    val cardState = rememberLiquidState()  // For cards: refracts the gradient

    val isDark = isSystemInDarkTheme()
    val gradientColors = getGradientColors(variant, isDark)

    Box(modifier = modifier.fillMaxSize()) {
        // barState liquefiable: captures gradient + cards + everything for the frosted bar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquefiable(barState)
        ) {
            // cardState liquefiable: captures just the gradient for card refraction
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
            )

            // Screen content — cards use liquid(cardState), list items use lightweight
            content(cardState)
        }

        // Floating glass bottom bar — uses liquid(barState) to blur everything
        bottomBar(barState)
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
                frost = 0.dp             // No blur — see-through with distortion
                refraction = 0.25f       // Strong light bending
                curve = 0.5f             // Visible curvature/lensing
                edge = 0.08f             // Bright edge reflections
                this.shape = shape
                tint = defaultTint
                saturation = 1.5f        // Vivid refracted colors
                contrast = 1.4f          // High contrast for clarity
                dispersion = 0.06f       // Chromatic aberration at edges
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
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val barShape = RoundedCornerShape(28.dp)
    val barTint = if (isDark) Color(0xFF0D1117).copy(alpha = 0.45f)
                  else Color.White.copy(alpha = 0.55f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.12f)
                      else Color.White.copy(alpha = 0.50f)

    val indicatorSpec = spring<Float>(dampingRatio = 0.7f, stiffness = 400f)
    val animatedTabIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = indicatorSpec,
        label = "tabIndicator"
    )

    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .navigationBarsPadding()
            .fillMaxWidth()
    ) {
        // Frosted glass bar pill — blurs scrolling content behind it
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(barShape)
                .liquid(barState) {
                    frost = 28.dp
                    refraction = 0.15f
                    curve = 0.4f
                    shape = barShape
                    edge = 0.08f
                    tint = barTint
                    saturation = 1.3f
                    contrast = 1.15f
                    dispersion = 0.04f
                }
                .border(width = 0.5.dp, color = borderColor, shape = barShape)
        )

        // Sliding glass bubble indicator
        val indicatorShape = RoundedCornerShape(20.dp)
        val bubbleTint = if (isDark) Color(0xFFe5e6ea).copy(alpha = 0.12f)
                         else Color(0xFFe5e6ea).copy(alpha = 0.45f)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            val tabWidth = maxWidth / tabs.size
            val indicatorOffset = tabWidth * animatedTabIndex + tabWidth * 0.05f
            val indicatorWidth = tabWidth * 0.9f

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .fillMaxHeight()
                    .clip(indicatorShape)
                    .liquid(barState) {
                        frost = 10.dp
                        refraction = 0.20f
                        curve = 0.7f
                        edge = 0.04f
                        shape = indicatorShape
                        tint = bubbleTint
                        saturation = 1.2f
                        contrast = 1.10f
                        dispersion = 0.03f
                    }
            )
        }

        // Tab icons and labels with bounce animation
        val haptic = LocalHapticFeedback.current
        Row(
            modifier = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val selectedColor = MaterialTheme.colorScheme.primary
                val unselectedColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
                val tint = if (selected) selectedColor else unselectedColor

                // Bouncy scale animation on tap (like iOS liquid glass tab bar)
                val bounceScale = remember { Animatable(1f) }
                val coroutineScope = rememberCoroutineScope()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                // Trigger bounce: scale up to 1.3x then spring back to 1.0
                                coroutineScope.launch {
                                    bounceScale.snapTo(1.3f)
                                    bounceScale.animateTo(
                                        1f,
                                        spring(dampingRatio = 0.4f, stiffness = 400f)
                                    )
                                }
                                onTabSelected(index)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.graphicsLayer {
                            scaleX = bounceScale.value
                            scaleY = bounceScale.value
                        }
                    ) {
                        when (index) {
                            0 -> AnimatedHomeIcon(selected = selected, tint = tint)
                            1 -> AnimatedCalendarIcon(selected = selected, tint = tint)
                            2 -> AnimatedCalculatorIcon(selected = selected, tint = tint)
                            3 -> AnimatedStarIcon(selected = selected, tint = tint)
                            else -> Icon(tab.icon, tab.label, Modifier.size(22.dp), tint = tint)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(tab.label, fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = tint, textAlign = TextAlign.Center)
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
 * Animated Calendar/Timetable icon — multiple pages flip in rapid succession.
 */
@Composable
private fun AnimatedCalendarIcon(selected: Boolean, tint: Color) {
    // 3 pages flip in sequence when selected
    val page1 by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "page1"
    )
    val page2 by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 120, easing = FastOutSlowInEasing),
        label = "page2"
    )
    val page3 by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 350, delayMillis = 250, easing = FastOutSlowInEasing),
        label = "page3"
    )

    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.08f

        val bodyTop = h * 0.20f
        val bodyLeft = w * 0.08f
        val bodyRight = w * 0.92f
        val bodyBottom = h * 0.95f
        val headerBottom = bodyTop + (bodyBottom - bodyTop) * 0.22f

        // Calendar body
        drawRoundRect(tint, Offset(bodyLeft, bodyTop),
            Size(bodyRight - bodyLeft, bodyBottom - bodyTop),
            cornerRadius = CornerRadius(w * 0.08f), style = Stroke(width = stroke))

        // Binding rings
        val ringH = h * 0.11f
        drawLine(tint, Offset(w * 0.30f, bodyTop - ringH), Offset(w * 0.30f, bodyTop + ringH * 0.3f), strokeWidth = stroke)
        drawLine(tint, Offset(w * 0.70f, bodyTop - ringH), Offset(w * 0.70f, bodyTop + ringH * 0.3f), strokeWidth = stroke)

        // Header bar
        drawLine(tint, Offset(bodyLeft, headerBottom), Offset(bodyRight, headerBottom), strokeWidth = stroke * 0.6f)

        // Grid dots
        val gridLeft = bodyLeft + w * 0.08f
        val gridTop = headerBottom + h * 0.05f
        val cellW = (bodyRight - bodyLeft - w * 0.16f) / 4f
        val cellH = (bodyBottom - headerBottom - h * 0.10f) / 3f
        for (row in 0..2) {
            for (col in 0..3) {
                drawCircle(tint.copy(alpha = 0.7f), radius = w * 0.028f,
                    center = Offset(gridLeft + cellW * col + cellW * 0.5f, gridTop + cellH * row + cellH * 0.5f))
            }
        }

        // ── Flipping pages (3 in staggered sequence) ──
        fun drawFlippingPage(progress: Float, pageIndex: Int) {
            if (progress < 0.05f) return
            val p = progress
            // Each page flips at a slightly different angle
            val curlX = w * 0.04f * p * (1f + pageIndex * 0.3f)
            val liftY = (bodyBottom - headerBottom) * p * 0.7f * (1f - pageIndex * 0.15f)
            val pageBottom = bodyBottom - liftY
            val alpha = p * (0.7f - pageIndex * 0.15f)

            // Page shadow
            drawRect(Color.Black.copy(alpha = 0.10f * p),
                Offset(bodyLeft + curlX * 0.5f, headerBottom),
                Size(bodyRight - bodyLeft - curlX, pageBottom - headerBottom))

            // Page shape with curl
            val pagePath = Path().apply {
                moveTo(bodyLeft + curlX * 0.3f, headerBottom)
                lineTo(bodyRight, headerBottom)
                lineTo(bodyRight - curlX, pageBottom)
                quadraticTo(
                    (bodyLeft + bodyRight) * 0.5f, pageBottom + h * 0.03f * p,
                    bodyLeft + curlX * 0.3f, pageBottom
                )
                close()
            }
            drawPath(pagePath, color = tint.copy(alpha = alpha * 0.35f))
            drawPath(pagePath, color = tint.copy(alpha = alpha), style = Stroke(width = stroke * 0.4f))

            // Curl edge highlight
            drawLine(
                color = tint.copy(alpha = alpha * 0.6f),
                start = Offset(bodyRight - curlX, pageBottom),
                end = Offset(bodyRight - curlX * 0.5f, headerBottom + (pageBottom - headerBottom) * 0.3f),
                strokeWidth = stroke * 0.3f
            )
        }

        drawFlippingPage(page1, 0)
        drawFlippingPage(page2, 1)
        drawFlippingPage(page3, 2)
    }
}

/**
 * Animated Calculator/GPA icon — math symbols cycle through with a
 * spinning twist effect and electric glow when selected.
 */
@Composable
private fun AnimatedCalculatorIcon(selected: Boolean, tint: Color) {
    // Symbols rotate through when selected
    val symbolRotation by animateFloatAsState(
        targetValue = if (selected) 360f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "symbolRotation"
    )
    val glowPulse by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "glowPulse"
    )
    // Staggered symbol animations
    val sym1 by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "sym1"
    )
    val sym2 by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 100),
        label = "sym2"
    )
    val sym3 by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 200),
        label = "sym3"
    )
    val sym4 by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 300),
        label = "sym4"
    )

    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.08f

        // ── Calculator body (rounded rect) ──
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.05f, w * 0.05f),
            size = Size(w * 0.9f, h * 0.9f),
            cornerRadius = CornerRadius(w * 0.15f),
            style = Stroke(width = stroke)
        )

        // Electric glow behind body when selected
        if (glowPulse > 0f) {
            drawRoundRect(
                color = Color(0xFF448AFF).copy(alpha = glowPulse * 0.2f),
                topLeft = Offset(w * 0.05f, w * 0.05f),
                size = Size(w * 0.9f, h * 0.9f),
                cornerRadius = CornerRadius(w * 0.15f)
            )
        }

        // ── 4 math symbols in a 2x2 grid ──
        // Each symbol scales in with stagger and has a slight rotation
        val cx1 = w * 0.32f; val cy1 = h * 0.32f  // top-left: minus
        val cx2 = w * 0.68f; val cy2 = h * 0.32f  // top-right: multiply
        val cx3 = w * 0.32f; val cy3 = h * 0.68f  // bottom-left: plus
        val cx4 = w * 0.68f; val cy4 = h * 0.68f  // bottom-right: equals

        val symSize = w * 0.16f
        val symStroke = stroke * 1.2f

        // Symbol scale for animation (pop in effect)
        fun symScale(anim: Float) = 0.6f + anim * 0.4f

        // ── Minus ─ (top-left)
        val s1 = symScale(sym1)
        drawLine(tint, Offset(cx1 - symSize * s1, cy1), Offset(cx1 + symSize * s1, cy1),
            strokeWidth = symStroke, cap = StrokeCap.Round)

        // ── Multiply × (top-right) — rotates when selected
        val s2 = symScale(sym2)
        val xRot = symbolRotation * 0.5f // half rotation for X
        val xRad = Math.toRadians(xRot.toDouble()).toFloat()
        val xCos = kotlin.math.cos(xRad)
        val xSin = kotlin.math.sin(xRad)
        val xLen = symSize * s2
        // Line 1 of X (rotated)
        drawLine(tint,
            Offset(cx2 - xLen * xCos, cy2 - xLen * xSin),
            Offset(cx2 + xLen * xCos, cy2 + xLen * xSin),
            strokeWidth = symStroke, cap = StrokeCap.Round)
        // Line 2 of X (perpendicular, rotated)
        drawLine(tint,
            Offset(cx2 + xLen * xSin, cy2 - xLen * xCos),
            Offset(cx2 - xLen * xSin, cy2 + xLen * xCos),
            strokeWidth = symStroke, cap = StrokeCap.Round)

        // ── Plus + (bottom-left)
        val s3 = symScale(sym3)
        drawLine(tint, Offset(cx3 - symSize * s3, cy3), Offset(cx3 + symSize * s3, cy3),
            strokeWidth = symStroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(cx3, cy3 - symSize * s3), Offset(cx3, cy3 + symSize * s3),
            strokeWidth = symStroke, cap = StrokeCap.Round)

        // ── Equals = (bottom-right)
        val s4 = symScale(sym4)
        val eqGap = h * 0.05f
        drawLine(tint, Offset(cx4 - symSize * s4, cy4 - eqGap), Offset(cx4 + symSize * s4, cy4 - eqGap),
            strokeWidth = symStroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(cx4 - symSize * s4, cy4 + eqGap), Offset(cx4 + symSize * s4, cy4 + eqGap),
            strokeWidth = symStroke, cap = StrokeCap.Round)

        // ── Divider lines (subtle grid) ──
        val divAlpha = 0.3f
        drawLine(tint.copy(alpha = divAlpha), Offset(w * 0.5f, h * 0.15f), Offset(w * 0.5f, h * 0.85f),
            strokeWidth = stroke * 0.4f)
        drawLine(tint.copy(alpha = divAlpha), Offset(w * 0.15f, h * 0.5f), Offset(w * 0.85f, h * 0.5f),
            strokeWidth = stroke * 0.4f)

        // ── Electric sparks during transition ──
        if (glowPulse > 0.3f && glowPulse < 0.95f) {
            val sparkColor = Color(0xFF82B1FF).copy(alpha = (glowPulse - 0.3f) * 0.7f)
            val sparkLen = w * 0.08f * glowPulse
            drawLine(sparkColor, Offset(w * 0.85f, h * 0.10f), Offset(w * 0.85f + sparkLen, h * 0.10f - sparkLen),
                strokeWidth = stroke * 0.5f, cap = StrokeCap.Round)
            drawLine(sparkColor, Offset(w * 0.15f, h * 0.90f), Offset(w * 0.15f - sparkLen, h * 0.90f + sparkLen),
                strokeWidth = stroke * 0.5f, cap = StrokeCap.Round)
            drawLine(sparkColor, Offset(w * 0.10f, h * 0.15f), Offset(w * 0.10f - sparkLen, h * 0.15f - sparkLen * 0.7f),
                strokeWidth = stroke * 0.5f, cap = StrokeCap.Round)
        }

        // ── Settled state: warm "screen on" display glow ──
        if (glowPulse > 0.8f) {
            val settled = (glowPulse - 0.8f) / 0.2f // 0→1 as it settles
            // Inner display glow — like a calculator screen lit up
            drawRoundRect(
                color = Color(0xFF1A237E).copy(alpha = settled * 0.25f),
                topLeft = Offset(w * 0.12f, h * 0.12f),
                size = Size(w * 0.76f, h * 0.76f),
                cornerRadius = CornerRadius(w * 0.10f)
            )
            // Subtle highlight at top of "screen" (specular reflection)
            drawLine(
                color = Color.White.copy(alpha = settled * 0.2f),
                start = Offset(w * 0.20f, h * 0.14f),
                end = Offset(w * 0.80f, h * 0.14f),
                strokeWidth = stroke * 0.5f,
                cap = StrokeCap.Round
            )
        }
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

    // Comet orbiting — loops while selected
    val cometAngle = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (selected) {
            cometAngle.snapTo(0f)
            cometAngle.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
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

        // ── Star shape ──
        val scale = starScale
        val outerR = w * 0.32f * scale
        val innerR = w * 0.14f * scale
        val starColor = if (starGlow > 0.5f) {
            // Lerp from tint to gold
            Color(
                red = tint.red + (Color(0xFFFFD54F).red - tint.red) * starGlow,
                green = tint.green + (Color(0xFFFFD54F).green - tint.green) * starGlow,
                blue = tint.blue + (Color(0xFFFFD54F).blue - tint.blue) * starGlow,
                alpha = 1f
            )
        } else tint

        val starPath = Path().apply {
            val startAngle = -Math.PI / 2 // start from top
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
        // Filled star
        drawPath(starPath, color = starColor)
        // Star outline
        drawPath(starPath, color = tint, style = Stroke(width = w * 0.05f, join = StrokeJoin.Round))

        // ── Comet orbiting around the star ──
        if (cometAngle.value > 0f) {
            val angle = cometAngle.value
            val orbitR = w * 0.42f
            val rad = Math.toRadians((angle - 90).toDouble()) // start from top

            // Comet head position
            val cometX = cx + orbitR * kotlin.math.cos(rad).toFloat()
            val cometY = cy + orbitR * kotlin.math.sin(rad).toFloat()

            // Draw trail (arc behind the comet)
            val trailLength = 80f // degrees of trail
            val trailSteps = 12
            for (i in 0 until trailSteps) {
                val trailAngle = angle - (trailLength * i / trailSteps)
                val trailRad = Math.toRadians((trailAngle - 90).toDouble())
                val tx = cx + orbitR * kotlin.math.cos(trailRad).toFloat()
                val ty = cy + orbitR * kotlin.math.sin(trailRad).toFloat()
                val trailAlpha = (1f - i.toFloat() / trailSteps) * 0.6f
                val trailRadius = w * 0.035f * (1f - i.toFloat() / trailSteps)
                drawCircle(
                    color = Color(0xFF4FC3F7).copy(alpha = trailAlpha),
                    radius = trailRadius,
                    center = Offset(tx, ty)
                )
            }

            // Comet head — bright blue-white
            drawCircle(
                color = Color(0xFF81D4FA),
                radius = w * 0.05f,
                center = Offset(cometX, cometY)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = w * 0.025f,
                center = Offset(cometX, cometY)
            )
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
