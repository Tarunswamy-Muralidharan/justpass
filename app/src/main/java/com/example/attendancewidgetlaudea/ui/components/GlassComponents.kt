package com.example.attendancewidgetlaudea.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
                        Icon(tab.icon, tab.label, Modifier.size(22.dp), tint = tint)
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
