package com.justpass.app.games.ui.screens.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.justpass.app.games.ui.components.PrimaryBtn
import com.justpass.app.games.ui.components.StatLine
import com.justpass.app.games.ui.theme.BBInk
import com.justpass.app.games.ui.theme.DisplayFont
import com.justpass.app.games.ui.theme.MonoFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun SequenceMemoryScreen(onBack: () -> Unit, onLeaderboard: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ScorePrefs.getInstance(context) }
    val api = remember { ScoresApi() }
    val scope = rememberCoroutineScope()
    val game = Game.SEQUENCE_MEMORY

    var sequence by remember { mutableStateOf(listOf<Int>()) }
    var userIdx by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(1) }
    var bestLevel by remember { mutableIntStateOf(prefs.getBest(game)?.toInt() ?: 0) }
    var highlightedCell by remember { mutableIntStateOf(-1) }
    var inputEnabled by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("ready") } // ready / watch / repeat
    var watchStep by remember { mutableIntStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    var lastReached by remember { mutableIntStateOf(0) }

    suspend fun playSequence(seq: List<Int>) {
        inputEnabled = false
        phase = "watch"
        watchStep = 1
        delay(500)
        seq.forEachIndexed { idx, cell ->
            watchStep = idx + 1
            highlightedCell = cell
            delay(550)
            highlightedCell = -1
            delay(180)
        }
        inputEnabled = true
        userIdx = 0
        phase = "repeat"
    }

    fun startNextRound(fresh: List<Int>) {
        sequence = fresh
        scope.launch { playSequence(fresh) }
    }

    fun beginNewGame() {
        gameOver = false
        sequence = emptyList()
        level = 1
        userIdx = 0
        inputEnabled = false
        phase = "ready"
    }

    GameScaffold(game = game, onBack = onBack) { _ ->
        StatLine(
            accent = game.accent,
            leftLabel = "Level",
            leftValue = String.format("%02d", level),
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

        if (gameOver) {
            GameOverPanel(
                game = game,
                reached = lastReached,
                best = bestLevel,
                onTryAgain = { beginNewGame() },
                onLeaderboard = onLeaderboard
            )
            return@GameScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Phase banner
            when (phase) {
                "watch" -> {
                    KickerText(text = "▶ Watch closely", accent = game.accent)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "STEP $watchStep OF ${sequence.size}",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DisplayFont,
                        letterSpacing = (-1.5).sp
                    )
                }
                "repeat" -> {
                    KickerText(text = "✓ Your turn", accent = game.accent)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "REPEAT IT",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DisplayFont,
                        letterSpacing = (-1.5).sp
                    )
                }
                else -> {
                    KickerText(text = "→ Tap any cell to start", accent = game.accent)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "READY",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DisplayFont,
                        letterSpacing = (-1.5).sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 3x3 grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 320.dp)
                    .aspectRatio(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (row in 0..2) {
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (col in 0..2) {
                            val idx = row * 3 + col
                            val isLit = highlightedCell == idx
                            val target = if (isLit) game.accent else Color(0xFF252536)
                            val color by animateColorAsState(target, tween(120), label = "cell$idx")
                            val cellInteraction = remember(idx) { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .let {
                                        if (isLit)
                                            it.shadow(20.dp, RoundedCornerShape(14.dp), spotColor = game.accent, ambientColor = game.accent)
                                        else it
                                    }
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(color)
                                    .let {
                                        if (!isLit) it.border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                                        else it
                                    }
                                    .clickable(
                                        enabled = inputEnabled || sequence.isEmpty(),
                                        interactionSource = cellInteraction,
                                        indication = null
                                    ) {
                                        if (sequence.isEmpty()) {
                                            startNextRound(listOf(Random.nextInt(9)))
                                            return@clickable
                                        }
                                        if (!inputEnabled) return@clickable
                                        scope.launch {
                                            highlightedCell = idx
                                            delay(120)
                                            highlightedCell = -1
                                        }
                                        if (idx == sequence[userIdx]) {
                                            userIdx++
                                            if (userIdx >= sequence.size) {
                                                level = sequence.size + 1
                                                if (prefs.saveIfBetter(game, sequence.size.toDouble())) bestLevel = sequence.size
                                                scope.launch {
                                                    api.submit(prefs.playerId, game,
                                                        sequence.size.toDouble(), prefs.displayName, prefs.classId)
                                                }
                                                inputEnabled = false
                                                scope.launch {
                                                    delay(600)
                                                    startNextRound(sequence + Random.nextInt(9))
                                                }
                                            }
                                        } else {
                                            inputEnabled = false
                                            lastReached = sequence.size
                                            gameOver = true
                                        }
                                    },
                                contentAlignment = Alignment.TopStart
                            ) {
                                if (!isLit) {
                                    Text(
                                        String.format("%02d", idx + 1),
                                        color = Color.White.copy(alpha = 0.20f),
                                        fontFamily = MonoFont,
                                        fontSize = 8.sp,
                                        modifier = Modifier.padding(start = 8.dp, top = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Progress ticks (one per completed level, scales as you go)
            val ticks = (level - 1).coerceAtLeast(0).coerceAtMost(20)
            val tickW = if (ticks > 10) 14.dp else if (ticks > 6) 20.dp else 28.dp
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (ticks == 0) {
                    Box(
                        modifier = Modifier
                            .size(width = 28.dp, height = 3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                    )
                } else {
                    repeat(ticks) {
                        Box(
                            modifier = Modifier
                                .size(width = tickW, height = 3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(game.accent)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Sequence grows by one each level.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GameOverPanel(
    game: Game,
    reached: Int,
    best: Int,
    onTryAgain: () -> Unit,
    onLeaderboard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        KickerText(text = "Round over", accent = game.accent)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$reached",
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
        if (best > 0) {
            Text(
                "Best: lvl $best",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(36.dp))
        PrimaryBtn(
            accent = game.accent,
            text = "Try again",
            big = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = onTryAgain
        )
        Spacer(Modifier.height(10.dp))
        GhostBtn(
            text = "Leaderboard",
            modifier = Modifier.fillMaxWidth(),
            onClick = onLeaderboard
        )
    }
}
