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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.launch
import kotlin.random.Random

private val WORDS = listOf(
    "river", "knight", "lantern", "moon", "ember", "courage", "whisper",
    "echo", "harbor", "frost", "anchor", "summit", "compass",
    "willow", "tide", "harvest", "garnet", "stellar", "voyage",
    "phantom", "vortex", "saga", "lotus", "kindle", "valor", "drift",
    "verdant", "nimbus", "embark", "myth", "horizon", "ardent",
    "candle", "luna", "ash", "spark", "raven", "tempest", "zenith",
    "obscure", "trinket", "linger", "warden", "halo", "obsidian",
    "fable", "amber", "calm", "polar", "drone", "talon", "gleam",
    "crystal", "marble", "pearl", "labyrinth", "haven", "cipher",
    "torch", "mosaic", "phoenix", "wraith", "thicket"
).distinct()

@Composable
fun VerbalMemoryScreen(onBack: () -> Unit, onLeaderboard: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { ScorePrefs.getInstance(context) }
    val api = remember { ScoresApi() }
    val scope = rememberCoroutineScope()
    val game = Game.VERBAL_MEMORY

    val seen = remember { mutableStateOf(mutableSetOf<String>()) }
    val pool = remember { WORDS.toMutableList() }
    var current by remember { mutableStateOf("") }
    var lives by remember { mutableIntStateOf(3) }
    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(prefs.getBest(game)?.toInt() ?: 0) }
    var gameOver by remember { mutableStateOf(false) }

    fun nextWord() {
        // 40% chance to repeat a previously-seen word, 60% pick fresh (not yet shown).
        val fresh = pool.filter { it !in seen.value }
        current = when {
            seen.value.isNotEmpty() && (fresh.isEmpty() || Random.nextFloat() < 0.4f) ->
                seen.value.random()
            fresh.isNotEmpty() -> fresh.random()
            else -> pool.random()
        }
    }

    fun answer(claimedSeen: Boolean) {
        if (gameOver) return
        val actuallySeen = current in seen.value
        if (claimedSeen == actuallySeen) {
            score++
            if (!actuallySeen) seen.value.add(current)
            nextWord()
        } else {
            lives--
            if (lives <= 0) {
                gameOver = true
                if (prefs.saveIfBetter(game, score.toDouble())) {
                    bestScore = score
                    scope.launch {
                        api.submit(prefs.playerId, game,
                            score.toDouble(), prefs.displayName, prefs.classId)
                    }
                }
            } else {
                if (!actuallySeen) seen.value.add(current)
                nextWord()
            }
        }
    }

    fun restart() {
        seen.value = mutableSetOf()
        score = 0; lives = 3; gameOver = false
        nextWord()
    }

    LaunchedEffect(Unit) {
        if (current.isEmpty()) nextWord()
    }

    GameScaffold(game = game, onBack = onBack) { _ ->
        StatLine(
            accent = game.accent,
            leftLabel = "Score",
            leftValue = String.format("%02d", score),
            rightLabel = "Best",
            rightValue = String.format("%02d", bestScore)
        )

        // Lives strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "LIVES",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 9.sp,
                letterSpacing = 1.8.sp,
                fontWeight = FontWeight.SemiBold
            )
            LivesStrip(lives = lives)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.06f))
        )

        if (gameOver) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                KickerText(text = "Round over", accent = game.accent)
                Spacer(Modifier.height(8.dp))
                Text(
                    "$score",
                    color = game.accent,
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DisplayFont,
                    letterSpacing = (-5).sp,
                    lineHeight = 86.sp
                )
                Text(
                    "words answered",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(36.dp))
                PrimaryBtn(
                    accent = game.accent,
                    text = "Try again",
                    big = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { restart() }
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Word poster
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "“",
                    color = game.accent.copy(alpha = 0.18f),
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DisplayFont,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        current.uppercase(),
                        color = Color.White,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DisplayFont,
                        letterSpacing = (-2.8).sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    KickerText(text = "have you seen it?", accent = game.accent)
                }
                Text(
                    "”",
                    color = game.accent.copy(alpha = 0.18f),
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DisplayFont,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrimaryBtn(
                    accent = game.accent,
                    text = "● Seen",
                    big = true,
                    modifier = Modifier.weight(1f),
                    onClick = { answer(claimedSeen = true) }
                )
                GhostBtn(
                    text = "○ New",
                    modifier = Modifier.weight(1f),
                    onClick = { answer(claimedSeen = false) }
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
