package com.justpass.app.games.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 1-2-3 ranking podium glyph. Three vertical bars (left = 2nd, middle = 1st
 * tall, right = 3rd) topped with a small star above the centre block.
 * Drawn with Canvas so it scales cleanly and inherits a single accent
 * colour without depending on emoji rendering.
 */
@Composable
fun PodiumIcon(
    size: Dp = 18.dp,
    accent: Color = Color(0xFFFFD54F),
    base: Color = Color.White
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // Bar widths
        val barW = w * 0.28f
        val gap = w * 0.04f
        val totalW = barW * 3f + gap * 2f
        val startX = (w - totalW) / 2f
        // Bar heights as fractions of h (bottoms aligned)
        val h1 = h * 0.78f // middle (1st)
        val h2 = h * 0.55f // left (2nd)
        val h3 = h * 0.42f // right (3rd)
        // Baseline = bottom of canvas
        val baseY = h * 0.94f

        // Left (2nd) — silver
        drawRect(
            color = base.copy(alpha = 0.85f),
            topLeft = Offset(startX, baseY - h2),
            size = Size(barW, h2)
        )
        // Right (3rd) — bronze
        drawRect(
            color = Color(0xFFD08A4A),
            topLeft = Offset(startX + (barW + gap) * 2f, baseY - h3),
            size = Size(barW, h3)
        )
        // Middle (1st) — gold
        drawRect(
            color = accent,
            topLeft = Offset(startX + barW + gap, baseY - h1),
            size = Size(barW, h1)
        )
        // Crown star above middle block
        val cx = startX + barW + gap + barW / 2f
        val starTopY = baseY - h1 - h * 0.22f
        drawStar(cx, starTopY + h * 0.10f, h * 0.10f, accent)

        // Ground line
        drawLine(
            color = base.copy(alpha = 0.65f),
            start = Offset(0f, baseY),
            end = Offset(w, baseY),
            strokeWidth = 1.5f
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color
) {
    val path = Path()
    val points = 5
    val innerR = radius * 0.45f
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else innerR
        val a = Math.PI / points * i - Math.PI / 2
        val x = cx + (Math.cos(a) * r).toFloat()
        val y = cy + (Math.sin(a) * r).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color)
    drawPath(path, color = color, style = Stroke(width = 1f))
}
