package com.justpass.app.games.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.games.data.api.ScoresApi
import com.justpass.app.games.data.local.ScorePrefs
import com.justpass.app.games.data.model.Game
import com.justpass.app.games.data.model.ScoreRow
import kotlin.math.abs

/**
 * Strip shown during gameplay that names the classmate one step above you
 * on the section leaderboard, with the points needed to overtake. Scopes
 * to the player's section by default; falls back to college if biodata
 * isn't loaded.
 *
 * The leaderboard is fetched once when the strip enters composition. If
 * the user has no biodata + no scores yet, the strip renders nothing.
 *
 * @param currentScore caller's live in-run score. null = haven't scored
 *                     yet (show the bottom of the section ladder as the
 *                     first target).
 */
@Composable
fun RivalAboveStrip(
    game: Game,
    currentScore: Double?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { ScorePrefs.getInstance(context) }
    val api = remember { ScoresApi() }
    val classId = remember { prefs.classId }
    val selfId = remember { prefs.playerId }

    var rows by remember(game, classId) { mutableStateOf<List<ScoreRow>>(emptyList()) }
    LaunchedEffect(game, classId) {
        // Prefer section. If biodata absent, fall back to college.
        val section = if (classId != null) api.leaderboard(game, classId = classId) else emptyList()
        rows = if (section.isNotEmpty()) section else api.leaderboard(game, classId = null)
    }

    if (rows.isEmpty()) return
    val others = rows.filter { it.playerId != selfId }
    if (others.isEmpty()) return

    val rival: ScoreRow? = when {
        currentScore == null -> {
            // Not yet on board — first target is the bottom of the ladder.
            if (game.lowerIsBetter) others.maxByOrNull { it.bestScore }
            else others.minByOrNull { it.bestScore }
        }
        game.lowerIsBetter -> {
            // Lower is better → "above me" = faster (lower) than current.
            // Closest among those = nearest lower neighbour.
            others.filter { it.bestScore < currentScore }
                .maxByOrNull { it.bestScore }
        }
        else -> {
            // Higher is better → "above me" = scored more than current.
            others.filter { it.bestScore > currentScore }
                .minByOrNull { it.bestScore }
        }
    }

    if (rival == null) {
        // Leading the board — nobody above.
        LeadingChip(game = game, modifier = modifier)
        return
    }

    val diff = abs(rival.bestScore - (currentScore ?: 0.0))
    val rivalName = rival.displayName?.takeIf { it.isNotBlank() } ?: rival.playerId
    val rivalScore = formatScore(rival.bestScore, game)
    val gap = formatScore(diff, game)
    val verb = if (currentScore == null) "first target" else "to beat"
    val scopeLabel = if (classId != null && rows === rows /* section */) "SECTION" else "COLLEGE"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, game.accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(game.accent)
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                "RIVAL · $scopeLabel",
                color = Color(0xFF0A0A1A),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
        }
        Column(
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f)
        ) {
            Text(
                rivalName,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Text(
                "$rivalScore ${game.unit} · $gap ${game.unit} $verb",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LeadingChip(game: Game, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(game.accent.copy(alpha = 0.15f))
            .border(1.dp, game.accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            "LEADING THIS SECTION",
            color = game.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.6.sp
        )
    }
}

private fun formatScore(value: Double, game: Game): String = when (game.unit) {
    "ms", "wpm", "lvl", "digits", "score" -> value.toInt().toString()
    else -> value.toInt().toString()
}
