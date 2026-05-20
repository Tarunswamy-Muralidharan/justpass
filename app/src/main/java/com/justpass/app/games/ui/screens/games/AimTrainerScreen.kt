package com.justpass.app.games.ui.screens.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.games.data.api.ScoresApi
import com.justpass.app.games.data.local.ScorePrefs
import com.justpass.app.games.data.model.Game
import com.justpass.app.games.ui.components.GameScaffold
import com.justpass.app.games.ui.components.GhostBtn
import com.justpass.app.games.ui.components.KickerText
import com.justpass.app.games.ui.components.StatLine
import com.justpass.app.games.ui.theme.DisplayFont
import com.justpass.app.games.ui.theme.MonoFont
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.random.Random

@Composable
fun AimTrainerScreen(onBack: () -> Unit, onLeaderboard: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { ScorePrefs.getInstance(context) }
    val api = remember { ScoresApi() }
    val scope = rememberCoroutineScope()
    val game = Game.AIM_TRAINER
    val targetTotal = 30
    val density = LocalDensity.current

    var hits by remember { mutableIntStateOf(0) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var totalMs by remember { mutableLongStateOf(0L) }
    var inProgress by remember { mutableStateOf(false) }
    var targetX by remember { mutableStateOf(0.5f) }
    var targetY by remember { mutableStateOf(0.5f) }
    var tickNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val bestMs = prefs.getBest(game)?.toLong()

    LaunchedEffect(inProgress) {
        if (!inProgress) return@LaunchedEffect
        while (inProgress) {
            withFrameMillis { tickNow = System.currentTimeMillis() }
        }
    }

    GameScaffold(game = game, onBack = onBack) { _ ->
        StatLine(
            accent = game.accent,
            leftLabel = "Hits",
            leftValue = String.format("%02d", hits),
            leftUnit = "/$targetTotal",
            rightLabel = "Best",
            rightValue = if (bestMs != null) "${bestMs / 1000.0}".take(4) else "—",
            rightUnit = if (bestMs != null) "s" else ""
        )

        // Progress bar
        Column(modifier = Modifier.padding(horizontal = 18.dp).padding(top = 14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (targetTotal > 0) hits / targetTotal.toFloat() else 0f)
                        .fillMaxSize()
                        .background(game.accent)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val elapsed = if (inProgress) (tickNow - startedAt) else totalMs
                Text(
                    formatClock(elapsed),
                    color = Color.White.copy(alpha = 0.45f),
                    fontFamily = MonoFont,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp
                )
                Text(
                    "${(targetTotal - hits).coerceAtLeast(0)} LEFT",
                    color = Color.White.copy(alpha = 0.45f),
                    fontFamily = MonoFont,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp
                )
            }
        }

        // Arena card
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF15151E))
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
        ) {
            val areaW = maxWidth
            val areaH = maxHeight

            // Crosshair grid
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rows = 5
                val cols = 4
                val rH = size.height / rows
                val cW = size.width / cols
                for (i in 0..rows) {
                    drawLine(
                        Color.White.copy(alpha = 0.18f),
                        Offset(0f, i * rH), Offset(size.width, i * rH),
                        strokeWidth = 0.5f
                    )
                }
                for (i in 0..cols) {
                    drawLine(
                        Color.White.copy(alpha = 0.18f),
                        Offset(i * cW, 0f), Offset(i * cW, size.height),
                        strokeWidth = 0.5f
                    )
                }
                drawLine(
                    Color.White.copy(alpha = 0.6f),
                    Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height),
                    strokeWidth = 0.6f
                )
                drawLine(
                    Color.White.copy(alpha = 0.6f),
                    Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f),
                    strokeWidth = 0.6f
                )
            }

            if (!inProgress) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectFirstTap { _, _ ->
                                hits = 0
                                totalMs = 0
                                targetX = Random.nextFloat() * 0.8f + 0.1f
                                targetY = Random.nextFloat() * 0.8f + 0.1f
                                startedAt = System.currentTimeMillis()
                                inProgress = true
                            }
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    KickerText(
                        text = if (hits == targetTotal && totalMs > 0) "Run complete" else "Tap to start",
                        accent = game.accent
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            if (totalMs > 0) "${totalMs / 1000.0}".take(4) else "—",
                            color = Color.White,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = DisplayFont,
                            letterSpacing = (-3).sp,
                            lineHeight = 56.sp
                        )
                        if (totalMs > 0) {
                            Text(
                                "s",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (hits == targetTotal) "Tap to retry"
                        else "Hit $targetTotal targets as fast as you can",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp
                    )
                    if (hits == targetTotal) {
                        Spacer(Modifier.height(10.dp))
                        GhostBtn(
                            text = "Leaderboard",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onLeaderboard
                        )
                    }
                }
            } else {
                val targetSize = 64.dp
                val xPx = with(density) { (areaW.toPx() - targetSize.toPx()) * targetX }
                val yPx = with(density) { (areaH.toPx() - targetSize.toPx()) * targetY }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(xPx.toInt(), yPx.toInt()) }
                        .size(targetSize)
                        .pointerInput(hits) {
                            detectFirstTap { _, _ ->
                                hits++
                                if (hits >= targetTotal) {
                                    totalMs = System.currentTimeMillis() - startedAt
                                    prefs.saveIfBetter(game, totalMs.toDouble())
                                    scope.launch {
                                        api.submit(prefs.playerId, game,
                                            totalMs.toDouble(), prefs.displayName, prefs.classId)
                                    }
                                    inProgress = false
                                } else {
                                    var nx: Float; var ny: Float
                                    do {
                                        nx = Random.nextFloat() * 0.8f + 0.1f
                                        ny = Random.nextFloat() * 0.8f + 0.1f
                                    } while (hypot((nx - targetX).toDouble(),
                                                   (ny - targetY).toDouble()) < 0.2)
                                    targetX = nx
                                    targetY = ny
                                }
                            }
                        }
                ) {
                    // Bullseye target
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        drawCircle(game.accent.copy(alpha = 0.30f),
                            radius = size.width * 0.47f, center = Offset(cx, cy),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                        drawCircle(game.accent.copy(alpha = 0.55f),
                            radius = size.width * 0.36f, center = Offset(cx, cy),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                        drawCircle(game.accent.copy(alpha = 0.85f),
                            radius = size.width * 0.24f, center = Offset(cx, cy),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                        drawCircle(game.accent, radius = size.width * 0.12f, center = Offset(cx, cy))
                        drawCircle(Color.White, radius = size.width * 0.04f, center = Offset(cx, cy))
                    }
                }

                Text(
                    "AVG · ${if (hits > 0) ((tickNow - startedAt) / hits) else 0}ms",
                    color = game.accent,
                    fontFamily = MonoFont,
                    fontSize = 10.sp,
                    letterSpacing = 1.0.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 14.dp)
                )
            }
        }
    }
}

private fun formatClock(ms: Long): String {
    val totalSec = ms / 1000
    val mins = totalSec / 60
    val secs = totalSec % 60
    val cs = (ms % 1000) / 10
    return String.format("%02d:%02d.%02d", mins, secs, cs)
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectFirstTap(
    block: (Offset, Long) -> Unit
) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            if (change.pressed && change.previousPressed.not()) {
                block(change.position, System.currentTimeMillis())
                change.consume()
            }
        }
    }
}
