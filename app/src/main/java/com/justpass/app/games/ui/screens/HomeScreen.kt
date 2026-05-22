package com.justpass.app.games.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.justpass.app.games.data.local.ScorePrefs
import com.justpass.app.games.data.model.Game
import com.justpass.app.games.ui.components.BlurChip
import com.justpass.app.games.ui.components.CircleIcon
import com.justpass.app.games.ui.components.GameArt
import com.justpass.app.games.ui.components.HBLogo
import com.justpass.app.games.ui.theme.BBHot
import com.justpass.app.games.ui.theme.BBInk
import com.justpass.app.games.ui.theme.BBSurface
import com.justpass.app.games.ui.theme.DisplayFont

@Composable
fun HomeScreen(
    onPlay: (Game) -> Unit,
    onLeaderboard: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { ScorePrefs.getInstance(context) }
    val games = Game.entries

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BBInk)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // Top app bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HBLogo(size = 20.dp, ink = BBInk, fill = Color.White)
                CircleIcon(size = 36.dp, onClick = onLeaderboard) {
                    com.justpass.app.games.ui.components.PodiumIcon(
                        size = 18.dp,
                        accent = BBHot
                    )
                }
            }

            // Meta strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${games.size} GAMES",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DisplayFont,
                        letterSpacing = (-0.8).sp,
                        lineHeight = 26.sp
                    )
                    Text(
                        ".",
                        color = BBHot,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DisplayFont,
                        lineHeight = 26.sp
                    )
                }
                Text(
                    "GLOBAL · RANKED",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    letterSpacing = 1.1.sp
                )
            }

            // Poster grid. Each card stagger-rises after the pixel-wipe
            // curtain settles — see TRANSITION_SPEC.md / SPEC_FOR_KIMI § 6
            // (`card-rise`: opacity 0→1, translateY 20px → 0, +60 ms per card
            // starting 650 ms after composition).
            val riseDistPx = with(LocalDensity.current) { 20.dp.toPx() }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(((games.size + 1) / 2 * 230).dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = false
            ) {
                itemsIndexed(games) { idx, g ->
                    val progress = remember(g) { Animatable(0f) }
                    LaunchedEffect(g) {
                        delay(650L + idx * 60L)
                        progress.animateTo(1f, tween(500))
                    }
                    Box(
                        modifier = Modifier.graphicsLayer {
                            translationY = (1f - progress.value) * riseDistPx
                            alpha = progress.value
                        }
                    ) {
                        GamePoster(
                            game = g,
                            bestScore = prefs.getBest(g),
                            onClick = { onPlay(g) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GamePoster(
    game: Game,
    bestScore: Double?,
    onClick: () -> Unit
) {
    val isFresh = bestScore == null
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BBSurface)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().height(118.dp)) {
                GameArt(game)
                Box(modifier = Modifier.padding(8.dp).align(Alignment.TopStart)) {
                    if (isFresh) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(game.accent)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "NEW",
                                color = BBInk,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.4.sp
                            )
                        }
                    } else {
                        BlurChip(text = "—", monoText = true)
                    }
                }
                if (!isFresh) {
                    Box(modifier = Modifier.padding(8.dp).align(Alignment.TopEnd)) {
                        BlurChip(text = "BEST", accent = game.accent)
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        game.title,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.2).sp,
                        lineHeight = 16.sp
                    )
                    Text(
                        game.tagline,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (!isFresh) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                formatMetric(bestScore!!, game),
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = DisplayFont,
                                letterSpacing = (-1).sp
                            )
                            Text(
                                game.unit,
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 3.dp, bottom = 3.dp)
                            )
                        }
                        Text(
                            "BEST",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 9.sp,
                            letterSpacing = 1.6.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("▶", color = BBInk, fontSize = 9.sp)
                            Text(
                                "Play",
                                color = BBInk,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMetric(value: Double, game: Game): String = when (game.unit) {
    "ms" -> value.toInt().toString()
    "wpm" -> value.toInt().toString()
    "lvl" -> value.toInt().toString()
    "digits" -> value.toInt().toString()
    else -> value.toInt().toString()
}
