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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.games.data.api.ScoresApi
import com.justpass.app.games.data.local.ScorePrefs
import com.justpass.app.games.data.model.Game
import com.justpass.app.games.data.model.ScoreRow
import kotlin.math.abs

/**
 * Strip shown during gameplay that names the next player above the user
 * on the section leaderboard + the points needed to overtake. As the user
 * climbs past a rival mid-game, the strip automatically advances to the
 * next one. When the user has overtaken every other player in their
 * section, switches to the "TOP — beat your record" chip.
 *
 * Scopes to the player's section via biodata; falls back to college if
 * biodata isn't loaded. Scope label uses the programme name (e.g.
 * "Computer Science and Business Systems") instead of the literal word
 * "SECTION".
 *
 * @param currentScore caller's live in-run score. null = haven't scored
 *                     yet (show the bottom of the ladder as the first
 *                     target).
 */
@Composable
fun RivalAboveStrip(
    game: Game,
    currentScore: Double?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { ScorePrefs.getInstance(context) }
    val securePrefs = remember { SecurePreferences.getInstance(context) }
    val api = remember { ScoresApi() }
    val classId = remember { prefs.classId }
    val selfId = remember { prefs.playerId }
    // Programme name (e.g. "Computer Science and Business Systems") for
    // the scope label. Falls back to dept code or "MY GROUP" if biodata
    // hasn't hydrated.
    val scopeLabel = remember {
        (securePrefs.programmeName?.takeIf { it.isNotBlank() }
            ?: securePrefs.cachedDepartment?.takeIf { it.isNotBlank() }
            ?: "MY GROUP").uppercase()
    }
    // True if the underlying rows came from the section query (not the
    // college fallback). Affects whether the LeadingChip references the
    // programme or the whole college.
    var sourcedFromSection by remember { mutableStateOf(false) }

    var rows by remember(game, classId) { mutableStateOf<List<ScoreRow>>(emptyList()) }
    LaunchedEffect(game, classId) {
        val section = if (classId != null) api.leaderboard(game, classId = classId) else emptyList()
        if (section.isNotEmpty()) {
            rows = section
            sourcedFromSection = true
        } else {
            rows = api.leaderboard(game, classId = null)
            sourcedFromSection = false
        }
    }

    if (rows.isEmpty()) return
    val others = rows.filter { it.playerId != selfId }
    if (others.isEmpty()) return

    // Recompute on every recomposition so the rival advances as the user's
    // in-run score climbs past leaderboard entries.
    val rival: ScoreRow? = when {
        currentScore == null -> {
            // Not yet on board — first target is the bottom of the ladder.
            if (game.lowerIsBetter) others.maxByOrNull { it.bestScore }
            else others.minByOrNull { it.bestScore }
        }
        game.lowerIsBetter -> {
            // Lower is better → "above me" = faster (lower) than current.
            // Nearest lower neighbour = the next one to beat.
            others.filter { it.bestScore < currentScore }
                .maxByOrNull { it.bestScore }
        }
        else -> {
            // Higher is better → "above me" = scored more than current.
            // Nearest higher neighbour = the next one to beat.
            others.filter { it.bestScore > currentScore }
                .minByOrNull { it.bestScore }
        }
    }

    if (rival == null) {
        // Surpassed everyone in the chosen scope.
        LeadingChip(game = game, scopeLabel = scopeLabel, modifier = modifier)
        return
    }

    val diff = abs(rival.bestScore - (currentScore ?: 0.0))
    val rivalName = rival.displayName?.takeIf { it.isNotBlank() } ?: rival.playerId
    val rivalScore = formatScore(rival.bestScore, game)
    val gap = formatScore(diff, game)
    val verb = if (currentScore == null) "first target" else "to beat"
    val effectiveLabel = if (sourcedFromSection) scopeLabel else "WHOLE COLLEGE"

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
                "RIVAL · $effectiveLabel",
                color = Color(0xFF0A0A1A),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                maxLines = 1
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
private fun LeadingChip(game: Game, scopeLabel: String, modifier: Modifier = Modifier) {
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
            "TOP OF $scopeLabel · BEAT YOUR RECORD",
            color = game.accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.4.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun formatScore(value: Double, game: Game): String = when (game.unit) {
    "ms", "wpm", "lvl", "digits", "score" -> value.toInt().toString()
    else -> value.toInt().toString()
}
