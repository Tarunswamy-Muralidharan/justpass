package com.justpass.app.games.ui.screens.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.games.data.api.ScoresApi
import com.justpass.app.games.data.local.ScorePrefs
import com.justpass.app.games.data.model.Game
import com.justpass.app.ui.components.AdBanner
import com.justpass.app.games.ui.components.GameScaffold
import com.justpass.app.games.ui.components.GhostBtn
import com.justpass.app.games.ui.components.KickerText
import com.justpass.app.games.ui.components.PrimaryBtn
import com.justpass.app.games.ui.components.StatLine
import com.justpass.app.games.ui.theme.BBHot
import com.justpass.app.games.ui.theme.BBInk
import com.justpass.app.games.ui.theme.DisplayFont
import com.justpass.app.games.ui.theme.MonoFont
import kotlinx.coroutines.launch
import kotlin.random.Random

private enum class NumberStage { IDLE, SHOWING, INPUT, RESULT }

@Composable
fun NumberMemoryScreen(onBack: () -> Unit, onLeaderboard: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { ScorePrefs.getInstance(context) }
    val api = remember { ScoresApi() }
    val scope = rememberCoroutineScope()
    val game = Game.NUMBER_MEMORY

    var stage by remember { mutableStateOf(NumberStage.IDLE) }
    var digits by remember { mutableIntStateOf(1) }
    var current by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var bestDigits by remember { mutableIntStateOf(prefs.getBest(game)?.toInt() ?: 0) }
    var lastReached by remember { mutableIntStateOf(0) }
    var lastWasRight by remember { mutableStateOf(true) }
    var showDurationMs by remember { mutableLongStateOf(0L) }
    val progress = remember { Animatable(1f) }
    val focus = remember { FocusRequester() }

    fun startRound() {
        current = (1..digits).map { Random.nextInt(0, 10) }.joinToString("")
        input = ""
        showDurationMs = 800L + digits * 250L
        stage = NumberStage.SHOWING
    }

    fun submitInput() {
        if (stage != NumberStage.INPUT) return
        if (input.length != digits) return
        val correct = input == current
        lastWasRight = correct
        lastReached = digits
        if (correct) {
            if (prefs.saveIfBetter(game, digits.toDouble())) bestDigits = digits
            scope.launch {
                api.submit(prefs.playerId, game,
                    digits.toDouble(), prefs.displayName, prefs.classId)
            }
            digits++
        }
        stage = NumberStage.RESULT
    }

    LaunchedEffect(stage, current) {
        if (stage != NumberStage.SHOWING) return@LaunchedEffect
        progress.snapTo(1f)
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = showDurationMs.toInt(), easing = LinearEasing)
        )
        if (stage == NumberStage.SHOWING) stage = NumberStage.INPUT
    }

    LaunchedEffect(stage) {
        if (stage == NumberStage.INPUT) focus.requestFocus()
    }

    GameScaffold(game = game, onBack = onBack) { _ ->
        StatLine(
            accent = game.accent,
            leftLabel = "Length",
            leftValue = String.format("%02d", digits),
            leftUnit = "digits",
            rightLabel = "Best",
            rightValue = String.format("%02d", bestDigits),
            rightUnit = if (bestDigits > 0) "digits" else ""
        )
        AdBanner()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (stage) {
                NumberStage.IDLE -> {
                    KickerText(text = "Tap to begin", accent = game.accent)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "MEMORISE\nTHE NUMBER",
                        color = Color.White,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DisplayFont,
                        letterSpacing = (-2.2).sp,
                        lineHeight = 42.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(28.dp))
                    PrimaryBtn(
                        accent = game.accent,
                        text = "Start",
                        big = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { digits = 1; startRound() }
                    )
                }
                NumberStage.SHOWING -> {
                    val pct = progress.value
                    val remainingSec = ((pct * showDurationMs) / 100f).toInt() / 10f
                    KickerText(text = "Memorise · ${remainingSec}s", accent = game.accent)
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(pct)
                                .fillMaxSize()
                                .background(game.accent)
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        current,
                        color = Color.White,
                        fontSize = if (digits > 7) 56.sp else 88.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DisplayFont,
                        letterSpacing = (-4.5).sp,
                        modifier = Modifier
                            .shadow(60.dp, spotColor = game.accent, ambientColor = game.accent)
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Look. Don't write.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
                NumberStage.INPUT -> {
                    KickerText(text = "Type what you saw", accent = game.accent)
                    Spacer(Modifier.height(18.dp))
                    // Hidden text field for keyboard
                    BasicTextField(
                        value = input,
                        onValueChange = { v -> input = v.filter { it.isDigit() }.take(digits) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitInput() }),
                        cursorBrush = SolidColor(game.accent),
                        textStyle = TextStyle(color = Color.Transparent),
                        modifier = Modifier
                            .size(1.dp)
                            .focusRequester(focus)
                    )
                    // Digit slots
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(digits) { i ->
                            val ch = input.getOrNull(i)?.toString() ?: ""
                            val isCurrent = ch.isEmpty() && i == input.length
                            Box(
                                modifier = Modifier
                                    .size(width = 56.dp, height = 76.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (ch.isNotEmpty()) game.accent.copy(alpha = 0.12f)
                                        else Color.White.copy(alpha = 0.04f)
                                    )
                                    .border(
                                        if (isCurrent) 2.dp else 1.dp,
                                        if (isCurrent) game.accent else Color.White.copy(alpha = 0.10f),
                                        RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    ch,
                                    color = if (ch.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.20f),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = DisplayFont,
                                    letterSpacing = (-1.6).sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    PrimaryBtn(
                        accent = game.accent,
                        text = "Submit",
                        big = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { submitInput() }
                    )
                }
                NumberStage.RESULT -> {
                    KickerText(
                        text = if (lastWasRight) "Correct" else "Wrong",
                        accent = if (lastWasRight) game.accent else BBHot
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (lastWasRight) "NICE" else "MISS",
                        color = if (lastWasRight) game.accent else BBHot,
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DisplayFont,
                        letterSpacing = (-4).sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Number was: $current",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 14.sp
                    )
                    if (!lastWasRight) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Reached $lastReached digits",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    PrimaryBtn(
                        accent = game.accent,
                        text = if (lastWasRight) "Next round" else "Try again",
                        big = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (lastWasRight) startRound()
                            else { digits = 1; stage = NumberStage.IDLE }
                        }
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
