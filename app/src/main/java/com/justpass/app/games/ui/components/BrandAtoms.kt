package com.justpass.app.games.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.games.ui.theme.BBInk
import com.justpass.app.games.ui.theme.DisplayFont
import com.justpass.app.games.ui.theme.MonoFont

// Bolt SVG path: "M14.5 1.5L3 14h6.5L8 22.5 21 9h-7l.5-7.5z" inside 0..24 viewBox.
@Composable
fun Bolt(size: Dp = 24.dp, color: Color = Color.White, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val sx = this.size.width / 24f
        val sy = this.size.height / 24f
        val path = Path().apply {
            moveTo(14.5f * sx, 1.5f * sy)
            lineTo(3f * sx, 14f * sy)
            lineTo(9.5f * sx, 14f * sy)
            lineTo(8f * sx, 22.5f * sy)
            lineTo(21f * sx, 9f * sy)
            lineTo(14f * sx, 9f * sy)
            lineTo(14.5f * sx, 1.5f * sy)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun HBLogo(size: Dp = 18.dp, ink: Color = BBInk, fill: Color = Color.White) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(fill)
            .padding(horizontal = size * 0.55f, vertical = size * 0.42f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(size * 0.32f)
    ) {
        Bolt(size = size * 1.15f, color = ink)
        Text(
            "HB",
            color = ink,
            fontSize = (size.value * 1.05f).sp,
            fontWeight = FontWeight.Black,
            fontFamily = DisplayFont,
            letterSpacing = (-0.04f * size.value * 1.05f).sp
        )
    }
}

@Composable
fun Sparkle(size: Dp = 14.dp, color: Color = Color.White) {
    Canvas(modifier = Modifier.size(size)) {
        val sx = this.size.width / 16f
        val sy = this.size.height / 16f
        val path = Path().apply {
            moveTo(8f * sx, 0f * sy)
            lineTo(9.5f * sx, 6.5f * sy)
            lineTo(16f * sx, 8f * sy)
            lineTo(9.5f * sx, 9.5f * sy)
            lineTo(8f * sx, 16f * sy)
            lineTo(6.5f * sx, 9.5f * sy)
            lineTo(0f * sx, 8f * sy)
            lineTo(6.5f * sx, 6.5f * sy)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun Barcode(width: Dp = 130.dp, height: Dp = 26.dp, color: Color = Color.White) {
    val seed = listOf(3,1,2,1,1,3,2,1,1,2,3,1,2,1,1,3,1,2,2,1,3,1,2,1,1,3,2,1,3,1,1,2)
    val total = seed.sum().toFloat()
    Canvas(modifier = Modifier.width(width).height(height)) {
        val unit = this.size.width / total
        var x = 0f
        seed.forEachIndexed { i, w ->
            if (i % 2 == 0) {
                drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(w * unit, this.size.height)
                )
            }
            x += w * unit
        }
    }
}

@Composable
fun CircleIcon(
    size: Dp = 32.dp,
    bg: Color = Color.White.copy(alpha = 0.08f),
    border: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .let { m ->
                if (border) m.border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape) else m
            }
            .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun BlurChip(
    text: String,
    accent: Color? = null,
    bg: Color = Color.Black.copy(alpha = 0.55f),
    monoText: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (accent != null) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        Text(
            text,
            color = Color.White,
            fontSize = if (monoText) 9.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = if (monoText) MonoFont else null,
            letterSpacing = if (monoText) 0.7.sp else 0.sp
        )
    }
}
