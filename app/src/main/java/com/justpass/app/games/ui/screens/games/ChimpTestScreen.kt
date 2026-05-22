package com.justpass.app.games.ui.screens.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.games.data.api.ScoresApi
import com.justpass.app.games.data.local.ScorePrefs
import com.justpass.app.games.data.model.Game
import com.justpass.app.games.ui.components.GameScaffold
import com.justpass.app.games.ui.components.GhostBtn
import com.justpass.app.games.ui.components.KickerText
import com.justpass.app.games.ui.components.LivesStrip
import com.justpass.app.games.ui.components.PrimaryBtn
import com.justpass.app.games.ui.components.StatLine
import com.justpass.app.games.ui.theme.BBHot
import com.justpass.app.games.ui.theme.DisplayFont
import com.justpass.app.games.ui.theme.MonoFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Tile(val number: Int, val xCells: Int, val yCells: Int)

private enum class ChimpStage { IDLE, SHOWING, INPUT, RESULT }

@Composable
fun ChimpTestScreen(onBack: () -> Unit, onLeaderboard: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { ScorePrefs.getInstance(context) }
    val api = remember { ScoresApi() }
    val scope = rememberCoroutineScope()
    val game = Game.CHIMP_TEST
    val cols = 5
    val rows = 7
    val density = LocalDensity.current

    var level by remember { mutableIntStateOf(4) }
    var lives by remember { mutableIntStateOf(3) }
    var stage by remember { mutableStateOf(ChimpStage.IDLE) }
    var tiles by remember { mutableStateOf<List<Tile>>(emptyList()) }
    var nextNum by remember { mutableIntStateOf(1) }
    var hidden by remember { mutableStateOf(false) }
    var bestLevel by remember { mutableIntStateOf(prefs.getBest(game)?.toInt() ?: 0) }

    fun startRound() {
        val taken = mutableSetOf<Pair<Int, Int>>()
        val newTiles = mutableListOf<Tile>()
        var n = 1
        while (newTiles.size < level) {
            val x = (0 until cols).random()
            val y = (0 until rows).random()
            if (taken.add(x to y)) {
                newTiles.add(Tile(n++, x, y))
            }
        }
        tiles = newTiles
        nextNum = 1
        hidden = false
        stage = ChimpStage.SHOWING
    }

    GameScaffold(game = game, onBack = onBack) { _ ->
        StatLine(
            accent = game.accent,
            leftLabel = "Numbers",
            leftValue = String.format("%02d", level),
            leftUnit = if (stage != ChimpStage.IDLE) "· next $nextNum" else "",
            rightLabel = "Best",
            rightValue = String.format("%02d", bestLevel)
        )
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
        com.justpass.app.games.ui.components.RivalAboveStrip(
            game = game,
            currentScore = level.takeIf { it > 4 }?.toDouble(),
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 18.dp)
        )

        // Phase + lives
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "▶ TAP IN ORDER",
                color = game.accent,
                fontFamily = MonoFont,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            )
            LivesStrip(lives = lives)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.06f))
        )

        if (stage == ChimpStage.IDLE) {
            Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                KickerText(text = "Tap to begin", accent = game.accent)
                Spacer(Modifier.height(10.dp))
                Text(
                    "TAP IN\nORDER",
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DisplayFont,
                    letterSpacing = (-2.2).sp,
                    lineHeight = 42.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "After tapping 1, the rest hide.",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))
                PrimaryBtn(
                    accent = game.accent,
                    text = "Start ($level numbers)",
                    big = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startRound() }
                )
            }
            return@GameScaffold
        }

        if (stage == ChimpStage.RESULT) {
            Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                KickerText(text = "Reached", accent = game.accent)
                Spacer(Modifier.height(8.dp))
                Text(
                    "$level",
                    color = game.accent,
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DisplayFont,
                    letterSpacing = (-5).sp,
                    lineHeight = 86.sp
                )
                Text(
                    "numbers",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(28.dp))
                PrimaryBtn(
                    accent = game.accent,
                    text = "Try again",
                    big = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { level = 4; lives = 3; stage = ChimpStage.IDLE }
                )
                Spacer(Modifier.height(10.dp))
                GhostBtn(
                    text = "Leaderboard",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onLeaderboard
                )
            }
            return@GameScaffold
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF15151E))
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
        ) {
            val cellW = maxWidth / cols
            val cellH = maxHeight / rows
            val tileSize = 52.dp

            // Faint grid
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (i in 1 until cols) {
                    val x = (size.width / cols) * i
                    drawLine(
                        Color.White.copy(alpha = 0.10f),
                        Offset(x, 0f), Offset(x, size.height),
                        strokeWidth = 0.5f
                    )
                }
                for (i in 1 until rows) {
                    val y = (size.height / rows) * i
                    drawLine(
                        Color.White.copy(alpha = 0.10f),
                        Offset(0f, y), Offset(size.width, y),
                        strokeWidth = 0.5f
                    )
                }
            }

            tiles.forEach { tile ->
                val showNumber = !hidden || tile.number < nextNum
                val isTapped = tile.number < nextNum
                val isNext = tile.number == nextNum && !hidden
                val xPx = with(density) {
                    (cellW * tile.xCells).toPx() + (cellW.toPx() - tileSize.toPx()) / 2f
                }
                val yPx = with(density) {
                    (cellH * tile.yCells).toPx() + (cellH.toPx() - tileSize.toPx()) / 2f
                }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(xPx.toInt(), yPx.toInt()) }
                        .let {
                            if (isNext)
                                it.shadow(20.dp, RoundedCornerShape(10.dp), spotColor = game.accent, ambientColor = game.accent)
                            else it
                        }
                        .size(tileSize)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                isTapped -> Color.White
                                isNext -> game.accent.copy(alpha = 0.18f)
                                else -> Color.White.copy(alpha = 0.05f)
                            }
                        )
                        .let {
                            when {
                                isTapped -> it
                                isNext -> it.border(1.5.dp, game.accent, RoundedCornerShape(10.dp))
                                else -> it.border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                            }
                        }
                        .clickable(enabled = tile.number >= nextNum) {
                            if (tile.number == nextNum) {
                                if (nextNum == 1) hidden = true
                                nextNum++
                                if (nextNum > level) {
                                    if (prefs.saveIfBetter(game, level.toDouble())) bestLevel = level
                                    scope.launch {
                                        api.submit(prefs.playerId, game,
                                            level.toDouble(), prefs.displayName, prefs.classId)
                                    }
                                    level++
                                    scope.launch {
                                        delay(500)
                                        startRound()
                                    }
                                }
                            } else {
                                lives--
                                if (lives <= 0) {
                                    stage = ChimpStage.RESULT
                                } else {
                                    scope.launch {
                                        delay(300)
                                        startRound()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (showNumber && !isTapped) {
                        Text(
                            "${tile.number}",
                            color = if (isNext) game.accent else Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = DisplayFont,
                            letterSpacing = (-1).sp
                        )
                    }
                }
            }
        }
    }
}
