package com.justpass.app.games.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.games.data.model.Game
import com.justpass.app.games.ui.theme.BBButter
import com.justpass.app.games.ui.theme.BBHot
import com.justpass.app.games.ui.theme.BBInk
import com.justpass.app.games.ui.theme.BBLilac
import com.justpass.app.games.ui.theme.BBLime
import com.justpass.app.games.ui.theme.BBMint
import com.justpass.app.games.ui.theme.DisplayFont
import com.justpass.app.games.ui.theme.MonoFont

@Composable
fun GameArt(game: Game) {
    when (game) {
        Game.REACTION_TIME -> ArtReaction()
        Game.SEQUENCE_MEMORY -> ArtSequence()
        Game.AIM_TRAINER -> ArtAim()
        Game.NUMBER_MEMORY -> ArtNumber()
        Game.VERBAL_MEMORY -> ArtVerbal()
        Game.VISUAL_MEMORY -> ArtVisual()
        Game.TYPING -> ArtTyping()
        Game.CHIMP_TEST -> ArtChimp()
    }
}

@Composable
private fun ArtReaction() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0710))
    ) {
        // perspective track
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val track = Path().apply {
                moveTo(0f, h)
                lineTo(w * 0.425f, h * 0.37f)
                lineTo(w * 0.575f, h * 0.37f)
                lineTo(w, h)
                close()
            }
            drawPath(track, Color(0xFF150305))
            // perspective dashes
            val cx = w / 2f
            drawRect(Color.White.copy(alpha = 0.7f), Offset(cx - 1f, h * 0.40f), Size(2f, 4f))
            drawRect(Color.White.copy(alpha = 0.6f), Offset(cx - 2f, h * 0.50f), Size(4f, 5f))
            drawRect(Color.White.copy(alpha = 0.5f), Offset(cx - 3f, h * 0.63f), Size(6f, 6f))
            drawRect(Color.White.copy(alpha = 0.4f), Offset(cx - 5f, h * 0.82f), Size(10f, 8f))
        }
        // 5-light gantry centered upper third
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black)
                .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(5) { i ->
                    val lit = i < 3
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (lit) Color(0xFFFF1500) else Color(0xFF3A0808))
                            )
                        }
                    }
                }
            }
        }
        // gantry pole
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
                .width(2.dp)
                .height(24.dp)
                .background(Color(0xFF222222))
        )
        // checker flag fragment top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 0.dp)
                .offset(x = 4.dp)
                .rotate(8f)
                .size(width = 26.dp, height = 20.dp)
                .clip(RoundedCornerShape(2.dp))
        ) {
            CheckerPattern()
        }
    }
}

@Composable
private fun CheckerPattern() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cell = 6f
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val on = (r + c) % 2 == 0
                drawRect(
                    if (on) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f),
                    Offset(c * cell, r * cell),
                    Size(cell, cell)
                )
            }
        }
    }
}

@Composable
private fun ArtSequence() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E20)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
        ) {
            val pads = listOf(
                Triple(Color(0xFFFF3366), false, RoundedCornerShape(14.dp, 4.dp, 4.dp, 4.dp)),
                Triple(BBButter, true, RoundedCornerShape(4.dp, 14.dp, 4.dp, 4.dp)),
                Triple(BBMint, false, RoundedCornerShape(4.dp, 4.dp, 4.dp, 14.dp)),
                Triple(Color(0xFF1E1EFF), false, RoundedCornerShape(4.dp, 4.dp, 14.dp, 4.dp))
            )
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pads.subList(0, 2).forEach { (c, lit, shape) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(shape)
                                .background(if (lit) c else c.copy(alpha = 0.28f))
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pads.subList(2, 4).forEach { (c, lit, shape) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(shape)
                                .background(if (lit) c else c.copy(alpha = 0.28f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtAim() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF180605))
    ) {
        // radial-ish glow via Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(BBHot.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(size.width * 0.3f, size.height * 0.6f),
                    radius = size.width * 0.55f
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.3f, size.height * 0.6f)
            )
            // big bullseye top-right (off-canvas)
            val bx = size.width * 0.95f
            val by = size.height * 0.30f
            val maxR = size.width * 0.55f / 2f
            drawCircle(BBHot.copy(alpha = 0.4f), maxR * 0.92f, Offset(bx, by), style = Stroke(2f))
            drawCircle(BBHot.copy(alpha = 0.6f), maxR * 0.68f, Offset(bx, by), style = Stroke(2f))
            drawCircle(BBHot.copy(alpha = 0.85f), maxR * 0.44f, Offset(bx, by), style = Stroke(2f))
            drawCircle(BBHot, maxR * 0.20f, Offset(bx, by))
            drawCircle(Color.White, maxR * 0.07f, Offset(bx, by))
            // dashed crosshair line
            val y = size.height * 0.5f
            val dashLen = 6f
            val gap = 4f
            var x = 0f
            while (x < size.width) {
                drawLine(
                    BBHot.copy(alpha = 0.35f),
                    Offset(x, y),
                    Offset((x + dashLen).coerceAtMost(size.width), y),
                    strokeWidth = 1f
                )
                x += dashLen + gap
            }
        }
        // small scattered targets
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 18.dp, start = 14.dp)
                .size(14.dp)
                .clip(CircleShape)
                .border(2.dp, BBHot, CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 14.dp, start = 30.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(BBHot.copy(alpha = 0.7f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp, top = 20.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(BBHot.copy(alpha = 0.4f))
        )
    }
}

@Composable
private fun ArtNumber() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF150C24))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(BBLilac.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.7f, size.height * 0.5f),
                    radius = size.width * 0.55f
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.7f, size.height * 0.5f)
            )
        }
        Text(
            "4 · 9 · 1 · 7 · 3",
            color = Color.White.copy(alpha = 0.4f),
            fontFamily = MonoFont,
            fontSize = 10.sp,
            letterSpacing = 4.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
        )
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "4",
                color = BBLilac.copy(alpha = 0.35f),
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                fontFamily = DisplayFont,
                letterSpacing = (-2).sp
            )
            Text(
                "9",
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                fontFamily = DisplayFont,
                letterSpacing = (-3).sp
            )
            Text(
                "1",
                color = BBLilac.copy(alpha = 0.35f),
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                fontFamily = DisplayFont,
                letterSpacing = (-2).sp
            )
            Text(
                "7",
                color = BBLilac.copy(alpha = 0.20f),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                fontFamily = DisplayFont
            )
        }
    }
}

@Composable
private fun ArtVerbal() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1F1A05))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(BBButter.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.4f),
                    radius = size.width * 0.6f
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.5f, size.height * 0.4f)
            )
        }
        Text(
            "“ECLIPSE”",
            color = BBButter,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            fontFamily = DisplayFont,
            letterSpacing = (-1).sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp, start = 14.dp, end = 14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, BBButter.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("SEEN", color = Color.White, fontSize = 11.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BBButter),
                contentAlignment = Alignment.Center
            ) {
                Text("NEW", color = BBInk, fontSize = 11.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            }
        }
    }
}

@Composable
private fun ArtVisual() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06201A)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(BBMint.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    radius = size.width * 0.6f
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.5f, size.height * 0.5f)
            )
        }
        val lit = setOf(1, 2, 5, 8, 11, 14)
        Column(
            modifier = Modifier.rotate(-6f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (r in 0..3) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0..3) {
                        val idx = r * 4 + c
                        val on = lit.contains(idx)
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (on) BBMint else BBMint.copy(alpha = 0.18f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtTyping() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1F0A))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(BBLime.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.75f),
                    radius = size.width * 0.6f
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.5f, size.height * 0.75f)
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp, start = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "the quick",
                color = BBLime,
                fontSize = 12.sp,
                fontFamily = MonoFont
            )
            Spacer(Modifier.size(2.dp))
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 12.dp)
                    .background(BBLime)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            listOf(
                listOf("Q","W","E","R","T","Y","U","I","O","P"),
                listOf("A","S","D","F","G","H","J","K","L"),
                listOf("Z","X","C","V","B","N","M")
            ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { k ->
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(BBLime.copy(alpha = 0.12f))
                                .border(1.dp, BBLime.copy(alpha = 0.25f), RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                k,
                                color = BBLime.copy(alpha = 0.7f),
                                fontSize = 7.sp,
                                fontFamily = MonoFont
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtChimp() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1F0908))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(BBHot.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    radius = size.width * 0.6f
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.5f, size.height * 0.5f)
            )
            val gridCols = 7
            val gridRows = 5
            val w = size.width / gridCols
            val h = size.height / gridRows
            for (i in 0..gridCols) {
                drawLine(
                    Color.White.copy(alpha = 0.08f),
                    Offset(i * w, 0f), Offset(i * w, size.height),
                    strokeWidth = 1f
                )
            }
            for (i in 0..gridRows) {
                drawLine(
                    Color.White.copy(alpha = 0.08f),
                    Offset(0f, i * h), Offset(size.width, i * h),
                    strokeWidth = 1f
                )
            }
        }
        val nums = listOf(
            Triple(1, 0.18f, 0.20f),
            Triple(2, 0.62f, 0.14f),
            Triple(3, 0.38f, 0.54f),
            Triple(4, 0.78f, 0.62f),
            Triple(5, 0.14f, 0.78f)
        )
        nums.forEach { (n, fx, fy) ->
            ChimpNumber(n, fx, fy)
        }
    }
}

@Composable
private fun ChimpNumber(n: Int, fx: Float, fy: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopStart)
    ) {
        Box(
            modifier = Modifier
                .offsetFraction(fx, fy)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (n == 1) Color.White else Color.White.copy(alpha = 0.08f))
                .border(
                    if (n == 1) 0.dp else 1.5.dp,
                    if (n == 1) Color.Transparent else BBHot.copy(alpha = 0.6f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$n",
                color = if (n == 1) BBInk else BBHot,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = DisplayFont
            )
        }
    }
}

private fun Modifier.offsetFraction(fx: Float, fy: Float): Modifier =
    this.then(
        Modifier.offset {
            // use the parent Box's measured size via layout — but we don't have that
            // here. Use a placeholder offset; ChimpNumber wraps in Box so caller
            // controls layout. Keep a simple translation in dp via a fixed small
            // canvas size assumption (~150x100 tile).
            androidx.compose.ui.unit.IntOffset(
                x = (fx * 150f).toInt(),
                y = (fy * 100f).toInt()
            )
        }
    )
