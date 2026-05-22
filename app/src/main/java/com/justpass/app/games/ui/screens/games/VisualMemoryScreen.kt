package com.justpass.app.games.ui.screens.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private enum class VisualStage { IDLE, FLASH, INPUT, RESULT }

@Composable
fun VisualMemoryScreen(onBack: () -> Unit, onLeaderboard: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { ScorePrefs.getInstance(context) }
    val api = remember { ScoresApi() }
    val scope = rememberCoroutineScope()
    val game = Game.VISUAL_MEMORY

    var level by remember { mutableIntStateOf(1) }
    var lives by remember { mutableIntStateOf(3) }
    var stage by remember { mutableStateOf(VisualStage.IDLE) }
    var bestLevel by remember { mutableIntStateOf(prefs.getBest(game)?.toInt() ?: 0) }
    val gridSize = (3 + (level - 1) / 2).coerceAtMost(6)
    var litCells by remember { mutableStateOf(setOf<Int>()) }
    var revealed by remember { mutableStateOf(false) }
    var tapped by remember { mutableStateOf(setOf<Int>()) }
    var missed by remember { mutableStateOf(setOf<Int>()) }

    fun pickPattern(): Set<Int> {
        val numLit = (3 + level).coerceAtMost(gridSize * gridSize - 2)
        return (0 until gridSize * gridSize).shuffled().take(numLit).toSet()
    }

    fun startRound() {
        litCells = pickPattern()
        tapped = emptySet()
        missed = emptySet()
        revealed = true
        stage = VisualStage.FLASH
        scope.launch {
            delay(900L + level * 60L)
            revealed = false
            stage = VisualStage.INPUT
        }
    }

    val foundCount = (tapped intersect litCells).size

    GameScaffold(game = game, onBack = onBack) { _ ->
        StatLine(
            accent = game.accent,
            leftLabel = "Level",
            leftValue = String.format("%02d", level),
            leftUnit = "· ${gridSize}x$gridSize",
            rightLabel = "Best",
            rightValue = String.format("%02d", bestLevel),
            rightUnit = "lvl"
        )
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
        com.justpass.app.games.ui.components.RivalAboveStrip(
            game = game,
            currentScore = level.takeIf { it > 1 }?.toDouble(),
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 18.dp)
        )

        // Phase + lives strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                when (stage) {
                    VisualStage.FLASH -> "▶ MEMORISE"
                    VisualStage.INPUT -> "✓ $foundCount OF ${litCells.size} FOUND"
                    VisualStage.RESULT -> "✕ ROUND OVER"
                    VisualStage.IDLE -> "→ TAP START"
                },
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

        when (stage) {
            VisualStage.IDLE -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    KickerText(text = "Tap to begin", accent = game.accent)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "REMEMBER\nTHE SQUARES",
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DisplayFont,
                        letterSpacing = (-1.8).sp,
                        lineHeight = 38.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(28.dp))
                    PrimaryBtn(
                        accent = game.accent,
                        text = "Start",
                        big = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { startRound() }
                    )
                }
            }
            VisualStage.RESULT -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    KickerText(text = "Reached", accent = game.accent)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
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
                            "lvl",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp, bottom = 12.dp)
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    PrimaryBtn(
                        accent = game.accent,
                        text = "Try again",
                        big = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { level = 1; lives = 3; stage = VisualStage.IDLE }
                    )
                    Spacer(Modifier.height(10.dp))
                    GhostBtn(
                        text = "Leaderboard",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onLeaderboard
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 360.dp)
                            .aspectRatio(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (row in 0 until gridSize) {
                            Row(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0 until gridSize) {
                                    val idx = row * gridSize + col
                                    val isLit = idx in litCells
                                    val isTapped = idx in tapped
                                    val isMiss = idx in missed
                                    val showLit = revealed && isLit
                                    val correctTap = isTapped && isLit
                                    val target = when {
                                        showLit -> game.accent
                                        correctTap -> game.accent.copy(alpha = 0.18f)
                                        isMiss -> BBHot.copy(alpha = 0.18f)
                                        else -> Color.White.copy(alpha = 0.05f)
                                    }
                                    val color by animateColorAsState(target, tween(150),
                                        label = "vm$idx")
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxSize()
                                            .let {
                                                if (showLit) it.shadow(20.dp, RoundedCornerShape(12.dp), spotColor = game.accent, ambientColor = game.accent)
                                                else it
                                            }
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(color)
                                            .let {
                                                when {
                                                    showLit -> it
                                                    correctTap -> it.border(1.dp, game.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                                    isMiss -> it.border(1.dp, BBHot.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                                    else -> it.border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                                }
                                            }
                                            .clickable(
                                                enabled = stage == VisualStage.INPUT && idx !in tapped && idx !in missed
                                            ) {
                                                if (idx in litCells) {
                                                    tapped = tapped + idx
                                                    if (litCells.all { it in tapped }) {
                                                        litCells = emptySet()
                                                        tapped = emptySet()
                                                        missed = emptySet()
                                                        revealed = false
                                                        level++
                                                        scope.launch {
                                                            delay(450)
                                                            startRound()
                                                        }
                                                    }
                                                } else {
                                                    missed = missed + idx
                                                    lives--
                                                    if (lives <= 0) {
                                                        stage = VisualStage.RESULT
                                                        if (prefs.saveIfBetter(game, level.toDouble())) bestLevel = level
                                                        scope.launch {
                                                            api.submit(prefs.playerId, game,
                                                                level.toDouble(), prefs.displayName, prefs.classId)
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (correctTap && !showLit) {
                                            Text(
                                                "✓",
                                                color = game.accent,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = DisplayFont
                                            )
                                        }
                                        if (isMiss) {
                                            Text(
                                                "×",
                                                color = BBHot,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = DisplayFont
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Tap every square that just flashed.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
