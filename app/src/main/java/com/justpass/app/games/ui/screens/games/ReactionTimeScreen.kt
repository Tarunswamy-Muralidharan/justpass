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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
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
import com.justpass.app.games.ui.components.StatLine
import com.justpass.app.games.ui.theme.BBHot
import com.justpass.app.games.ui.theme.BBInk
import com.justpass.app.games.ui.theme.DisplayFont
import com.justpass.app.games.ui.theme.MonoFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private enum class ReactionStage { IDLE, LIGHTING_UP, ALL_ON, LIGHTS_OUT, RESULT, JUMP_START }

@Composable
fun ReactionTimeScreen(onBack: () -> Unit, onLeaderboard: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { ScorePrefs.getInstance(context) }
    val api = remember { ScoresApi() }
    val scope = rememberCoroutineScope()
    val game = Game.REACTION_TIME

    var stage by remember { mutableStateOf(ReactionStage.IDLE) }
    var litCount by remember { mutableIntStateOf(0) }
    var lightsOutAt by remember { mutableLongStateOf(0L) }
    var lastMs by remember { mutableLongStateOf(0L) }
    var bestMs by remember { mutableLongStateOf(prefs.getBest(game)?.toLong() ?: 0L) }
    var sequenceJob by remember { mutableStateOf<Job?>(null) }

    fun startSequence() {
        sequenceJob?.cancel()
        sequenceJob = scope.launch {
            litCount = 0
            stage = ReactionStage.LIGHTING_UP
            for (i in 1..5) {
                delay(1000L)
                litCount = i
            }
            stage = ReactionStage.ALL_ON
            delay(Random.nextLong(200L, 3000L))
            litCount = 0
            lightsOutAt = System.currentTimeMillis()
            stage = ReactionStage.LIGHTS_OUT
        }
    }

    GameScaffold(game = game, onBack = onBack) { _ ->
        StatLine(
            accent = game.accent,
            leftLabel = "Last",
            leftValue = if (lastMs > 0) "$lastMs" else "—",
            leftUnit = if (lastMs > 0) "ms" else "",
            rightLabel = "Personal best",
            rightValue = if (bestMs > 0) "$bestMs" else "—",
            rightUnit = if (bestMs > 0) "ms" else ""
        )
        Spacer(Modifier.height(8.dp))
        com.justpass.app.games.ui.components.RivalAboveStrip(
            game = game,
            currentScore = lastMs.takeIf { it > 0 }?.toDouble(),
            modifier = Modifier.padding(horizontal = 18.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = true) {
                    when (stage) {
                        ReactionStage.IDLE, ReactionStage.RESULT, ReactionStage.JUMP_START -> {
                            startSequence()
                        }
                        ReactionStage.LIGHTING_UP, ReactionStage.ALL_ON -> {
                            sequenceJob?.cancel()
                            sequenceJob = null
                            litCount = 0
                            stage = ReactionStage.JUMP_START
                        }
                        ReactionStage.LIGHTS_OUT -> {
                            val ms = System.currentTimeMillis() - lightsOutAt
                            lastMs = ms
                            // Local cache: track personal best. Server: submit
                            // every attempt — submit_score_v2 clamps + only
                            // keeps the better score, so the leaderboard sees
                            // an attempts++ even when the new time isn't a PB.
                            if (prefs.saveIfBetter(game, ms.toDouble())) {
                                bestMs = ms
                            }
                            scope.launch {
                                api.submit(prefs.playerId, game, ms.toDouble(), prefs.displayName, prefs.classId)
                            }
                            stage = ReactionStage.RESULT
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Lights gantry — F1-scale signals
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val showGreen = stage == ReactionStage.LIGHTS_OUT || stage == ReactionStage.RESULT
                    repeat(5) { i ->
                        val color = when {
                            showGreen -> Color(0xFF00E676)
                            i < litCount -> BBHot
                            else -> Color(0xFF2A0A0A)
                        }
                        val glow = showGreen || i < litCount
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(2) { LightDot(color = color, glow = glow) }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(36.dp)
                        .background(Color(0xFF222222))
                )

                Spacer(Modifier.height(26.dp))

                when (stage) {
                    ReactionStage.IDLE -> {
                        KickerText(text = "● ● ● ○ ○  Lights < go", accent = BBHot)
                        Spacer(Modifier.height(14.dp))
                        BigArmText("TAP TO\nARM", accent = BBHot)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "5 reds, then lights out — tap fast.",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    ReactionStage.LIGHTING_UP, ReactionStage.ALL_ON -> {
                        KickerText(text = "● ● ● ●  Hold steady", accent = BBHot)
                        Spacer(Modifier.height(14.dp))
                        BigArmText("HOLD\nSTEADY", accent = BBHot)
                    }
                    ReactionStage.LIGHTS_OUT -> {
                        KickerText(text = "● Lights out", accent = Color(0xFF00E676))
                        Spacer(Modifier.height(14.dp))
                        BigArmText("GO!", accent = Color(0xFF00E676))
                    }
                    ReactionStage.JUMP_START -> {
                        KickerText(text = "✕ Jumped the start", accent = BBHot)
                        Spacer(Modifier.height(14.dp))
                        BigArmText("JUMP\nSTART.", accent = BBHot)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Tap to retry.",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    ReactionStage.RESULT -> {
                        KickerText(text = verdict(lastMs).uppercase(), accent = game.accent)
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "$lastMs",
                                color = Color.White,
                                fontSize = 80.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = DisplayFont,
                                letterSpacing = (-4).sp,
                                lineHeight = 70.sp
                            )
                            Text(
                                "ms",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Tap to try again.",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        GhostBtn(
                            text = "Leaderboard",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onLeaderboard
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BigArmText(text: String, accent: Color) {
    val parts = text.split(".")
    Box(modifier = Modifier.wrapContentSize()) {
        if (parts.size > 1) {
            Row {
                Text(
                    parts[0],
                    color = Color.White,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DisplayFont,
                    letterSpacing = (-3).sp,
                    lineHeight = 50.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    ".",
                    color = accent,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DisplayFont,
                    lineHeight = 50.sp
                )
            }
        } else {
            Text(
                text,
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                fontFamily = DisplayFont,
                letterSpacing = (-3).sp,
                lineHeight = 50.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LightDot(color: Color, glow: Boolean) {
    val anim by animateColorAsState(color, tween(90), label = "lightColor")
    Box(
        modifier = Modifier
            .size(34.dp)
            .shadow(
                elevation = if (glow) 28.dp else 0.dp,
                shape = CircleShape,
                spotColor = color,
                ambientColor = color
            )
            .clip(CircleShape)
            .background(anim)
    )
}

private fun verdict(ms: Long): String = when {
    ms <= 0 -> "Tap a result"
    ms < 200 -> "Lightning"
    ms < 250 -> "Excellent"
    ms < 300 -> "Great"
    ms < 350 -> "Good"
    ms < 400 -> "Average"
    else -> "Slow"
}
