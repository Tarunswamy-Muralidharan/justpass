package com.example.attendancewidgetlaudea.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.attendancewidgetlaudea.R
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
                val selected = index == selectedIndex
                val isCenter = index == centerIndex
                val selectedColor = MaterialTheme.colorScheme.primary
                val unselectedColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
                val tint = if (selected) selectedColor else unselectedColor

                val bounceScale = remember { Animatable(1f) }
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
                                onTabSelected(index)
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
                            AnimatedChessIcon(selected = selected, tint = tint, size = 70.dp)
                            Spacer(Modifier.height(1.dp))
                            Text(tab.label, fontSize = 16.sp,
                                fontFamily = FontFamily(Font(R.font.great_vibes)),
                                fontWeight = FontWeight.Bold,
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
