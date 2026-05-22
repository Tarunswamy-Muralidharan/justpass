package com.justpass.app.games.ui.screens.games

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import com.justpass.app.games.ui.theme.DisplayFont
import com.justpass.app.games.ui.theme.MonoFont
import kotlinx.coroutines.launch

private val PARAGRAPHS = listOf(
    "to be or not to be that is the question whether tis nobler in the mind to suffer the slings and arrows of outrageous fortune",
    "the quick brown fox jumps over the lazy dog and runs through the meadow finding fresh streams flowing among the rocks",
    "she sells seashells by the seashore the shells she sells are surely seashells from the deep blue ocean tides at dawn",
    "all that glitters is not gold often have you heard that told many a man his life hath sold but my outside to behold",
    "in a hole in the ground there lived a hobbit not a nasty dirty wet hole filled with the ends of worms and an oozy smell"
)

@Composable
fun TypingScreen(onBack: () -> Unit, onLeaderboard: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { ScorePrefs.getInstance(context) }
    val api = remember { ScoresApi() }
    val scope = rememberCoroutineScope()
    val game = Game.TYPING

    var paragraph by remember { mutableStateOf(PARAGRAPHS.random()) }
    var typed by remember { mutableStateOf("") }
    var startedAt by remember { mutableLongStateOf(0L) }
    var done by remember { mutableStateOf(false) }
    var wpm by remember { mutableStateOf(0) }
    var bestWpm by remember { mutableStateOf(prefs.getBest(game)?.toInt() ?: 0) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    val typedWords = typed.trim().split(" ").filter { it.isNotEmpty() }.size
    val totalWords = paragraph.split(" ").size
    val errors = (0 until typed.length).count { i -> i < paragraph.length && typed[i] != paragraph[i] }
    val correctChars = (0 until typed.length).count { i -> i < paragraph.length && typed[i] == paragraph[i] }
    val accuracy = if (typed.isNotEmpty()) (correctChars * 100 / typed.length) else 100
    val elapsedMs = if (startedAt > 0) System.currentTimeMillis() - startedAt else 0L
    val liveWpm = if (elapsedMs > 0 && typedWords > 0) (typedWords / (elapsedMs / 60000.0)).toInt() else 0

    GameScaffold(game = game, onBack = onBack) { _ ->
        StatLine(
            accent = game.accent,
            leftLabel = "WPM",
            leftValue = String.format("%02d", liveWpm),
            rightLabel = "Best",
            rightValue = String.format("%02d", bestWpm),
            rightUnit = if (bestWpm > 0) "wpm" else ""
        )
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
        com.justpass.app.games.ui.components.RivalAboveStrip(
            game = game,
            currentScore = liveWpm.takeIf { it > 0 }?.toDouble(),
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 18.dp)
        )
        AdBanner()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            KickerText(text = "Prompt · ${paragraphAttribution(paragraph)}", accent = game.accent)
            Spacer(Modifier.height(10.dp))

            // Prompt block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text(
                    text = highlight(paragraph, typed, game.accent),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.4f),
                    lineHeight = 26.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // Counter strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "$typedWords/$totalWords words",
                    color = Color.White.copy(alpha = 0.55f),
                    fontFamily = MonoFont,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    formatClockTyping(elapsedMs),
                    color = Color.White.copy(alpha = 0.55f),
                    fontFamily = MonoFont,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    "ACC ${accuracy}%",
                    color = Color.White.copy(alpha = 0.55f),
                    fontFamily = MonoFont,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            // Input field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(24.dp, RoundedCornerShape(14.dp), spotColor = game.accent.copy(alpha = 0.5f), ambientColor = game.accent.copy(alpha = 0.3f))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F1014))
                    .border(1.5.dp, game.accent, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = typed,
                    onValueChange = { v ->
                        if (done) return@BasicTextField
                        if (typed.isEmpty() && v.isNotEmpty()) {
                            startedAt = System.currentTimeMillis()
                        }
                        typed = v.take(paragraph.length)
                        if (typed == paragraph) {
                            val ms = System.currentTimeMillis() - startedAt
                            val minutes = ms / 60000.0
                            val words = paragraph.length / 5.0
                            wpm = (words / minutes).toInt().coerceAtLeast(0)
                            done = true
                            if (prefs.saveIfBetter(game, wpm.toDouble())) bestWpm = wpm
                            scope.launch {
                                api.submit(prefs.playerId, game,
                                    wpm.toDouble(), prefs.displayName, prefs.classId)
                            }
                        }
                    },
                    cursorBrush = SolidColor(game.accent),
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        fontFamily = MonoFont
                    ),
                    enabled = !done,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                )
            }

            Spacer(Modifier.height(20.dp))

            // Mini stat row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiniStat(label = "Speed", value = "$liveWpm", unit = "wpm", accent = game.accent, modifier = Modifier.weight(1f))
                MiniStat(label = "Errors", value = String.format("%02d", errors), unit = "", accent = null, modifier = Modifier.weight(1f))
                MiniStat(label = "Words", value = "$typedWords", unit = "/$totalWords", accent = null, modifier = Modifier.weight(1f))
            }

            if (done) {
                Spacer(Modifier.height(20.dp))
                PrimaryBtn(
                    accent = game.accent,
                    text = "Try again",
                    big = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        paragraph = PARAGRAPHS.random()
                        typed = ""
                        done = false
                        wpm = 0
                        startedAt = 0L
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

@Composable
private fun MiniStat(
    label: String,
    value: String,
    unit: String,
    accent: Color?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                label.uppercase(),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    color = accent ?: Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DisplayFont,
                    letterSpacing = (-1).sp,
                    lineHeight = 22.sp
                )
                if (unit.isNotEmpty()) {
                    Text(
                        unit,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                    )
                }
            }
        }
    }
}

private fun paragraphAttribution(p: String): String = when {
    p.startsWith("to be") -> "Hamlet"
    p.startsWith("the quick") -> "Pangram"
    p.startsWith("she sells") -> "Tongue twister"
    p.startsWith("all that") -> "Merchant of Venice"
    else -> "Hobbit"
}

private fun formatClockTyping(ms: Long): String {
    val totalSec = ms / 1000
    val mins = totalSec / 60
    val secs = totalSec % 60
    val cs = (ms % 1000) / 10
    return String.format("%02d:%02d.%02d", mins, secs, cs)
}

private fun highlight(target: String, typed: String, accent: Color): AnnotatedString =
    buildAnnotatedString {
        for (i in target.indices) {
            val c = target[i]
            when {
                i >= typed.length -> withStyle(SpanStyle(color = Color.White.copy(alpha = 0.4f))) { append(c) }
                typed[i] == c -> withStyle(SpanStyle(color = accent,
                    fontWeight = FontWeight.Bold)) { append(c) }
                else -> withStyle(SpanStyle(color = BBHot,
                    fontWeight = FontWeight.Bold)) { append(c) }
            }
        }
    }
