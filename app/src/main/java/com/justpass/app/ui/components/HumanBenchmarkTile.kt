package com.justpass.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.games.ui.theme.MonoFont

@Composable
fun HumanBenchmarkTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "bench")
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "benchCycle"
    )

    val flashAlpha = when {
        cycle < 0.42f -> 0f
        cycle < 0.50f -> (cycle - 0.42f) / 0.08f
        cycle < 0.65f -> 1f - (cycle - 0.50f) / 0.15f * 0.6f
        else -> 0.4f - (cycle - 0.65f) / 0.35f * 0.4f
    }.coerceIn(0f, 1f)

    val blipAlpha = if (cycle % 0.7f < 0.35f) 1f else 0.25f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .drawBehind {
                drawRect(Color(0xFF0A0A0A).copy(alpha = 0.48f))
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.40f
                    )
                )
            }
            .border(0.5.dp, Color(0xFFFF3838).copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
            Box(modifier = Modifier.width(8.dp).height(1.dp).background(Color(0xFFFF3838).copy(alpha = 0.75f)))
            Box(modifier = Modifier.width(1.dp).height(8.dp).background(Color(0xFFFF3838).copy(alpha = 0.75f)))
        }
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
            Box(modifier = Modifier.width(8.dp).height(1.dp).background(Color(0xFFFF3838).copy(alpha = 0.75f)))
            Box(modifier = Modifier.width(1.dp).height(8.dp).background(Color(0xFFFF3838).copy(alpha = 0.75f)))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                DotMatrixBolt(
                    canvasSize = 76.dp,
                    animated = true
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color.White.copy(alpha = flashAlpha * 0.18f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp)
                    .background(Color(0xFFFF3838).copy(alpha = 0.18f))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(Color(0xFFFF3838).copy(alpha = blipAlpha), RoundedCornerShape(50))
                    )
                    Text(
                        "ACTIVE · 01",
                        fontSize = 9.sp,
                        letterSpacing = 1.98.sp,
                        fontFamily = MonoFont,
                        color = Color(0xFFFF5050).copy(alpha = 0.9f)
                    )
                }

                Text(
                    "HUMAN BENCHMARK",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.64.sp,
                    fontFamily = MonoFont,
                    color = Color.White
                )

                Text(
                    "Reflex · memory · focus tests",
                    fontSize = 10.5.sp,
                    color = Color.White.copy(alpha = 0.42f),
                    fontFamily = MonoFont
                )
            }

            Column(
                modifier = Modifier
                    .padding(end = 14.dp, top = 10.dp, bottom = 10.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    "SYS_03",
                    fontSize = 9.sp,
                    fontFamily = MonoFont,
                    color = Color.White.copy(alpha = 0.35f)
                )
                Text(
                    "→",
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}
