package com.justpass.app.games.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquid
import io.github.fletchmckee.liquid.rememberLiquidState

/**
 * Top-level scaffold with glass background gradient. Owns its own
 * LiquidState so callers can pass it down to child cards for refraction.
 */
@Composable
fun LiquidGlassScaffold(
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (cardState: LiquidState, padding: androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    val cardState = rememberLiquidState()
    Scaffold(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0A1628),
                    Color(0xFF0F1B2E),
                    Color(0xFF142340)
                )
            )
        ),
        containerColor = Color.Transparent,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        bottomBar = bottomBar
    ) { padding ->
        content(cardState, padding)
    }
}

/**
 * Liquid glass card — uses the GPU shader for a refracting frosted look.
 * Use for hero cards / interactive surfaces.
 */
@Composable
fun LiquidGlassCard(
    cardState: LiquidState,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(shape)
            .liquid(cardState)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .let { m ->
                if (onClick != null) m.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ) else m
            },
        content = content
    )
}

/**
 * Frosted-but-readable list/header card. No GPU shader — uses a tinted
 * translucent background so text stays legible against any backdrop.
 */
@Composable
fun GlassListCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    tintColor: Color = Color.White.copy(alpha = 0.06f),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(shape)
            .background(tintColor)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .let { m ->
                if (onClick != null) m.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ) else m
            },
        content = content
    )
}
