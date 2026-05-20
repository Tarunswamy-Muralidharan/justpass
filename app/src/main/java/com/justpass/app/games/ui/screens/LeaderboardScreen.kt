package com.justpass.app.games.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.games.data.api.ScoresApi
import com.justpass.app.games.data.local.ScorePrefs
import com.justpass.app.games.data.model.Game
import com.justpass.app.games.data.model.OverallRow
import com.justpass.app.games.data.model.ScoreRow
import com.justpass.app.games.ui.theme.BBBlueDeep
import com.justpass.app.games.ui.theme.BBHot
import com.justpass.app.games.ui.theme.BBInk
import com.justpass.app.games.ui.theme.DisplayFont
import com.justpass.app.games.ui.theme.MonoFont

private val OverallAccent = BBHot

@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ScorePrefs.getInstance(context) }
    val api = remember { ScoresApi() }
    val games = Game.entries
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // Scope toggle: 0 = Global, 1 = Your class. Class scope only meaningful
    // when biodata supplies a class id.
    val classId = remember { prefs.classId }
    var scope by rememberSaveable { mutableIntStateOf(0) }
    var perGame by remember { mutableStateOf<List<ScoreRow>>(emptyList()) }
    var overall by remember { mutableStateOf<List<OverallRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    val effectiveClass = if (scope == 1) classId else null

    LaunchedEffect(selectedTab, scope) {
        loading = true
        if (selectedTab == 0) {
            overall = api.overall(classId = effectiveClass)
            perGame = emptyList()
        } else {
            perGame = api.leaderboard(games[selectedTab - 1], classId = effectiveClass)
            overall = emptyList()
        }
        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to BBInk,
                    0.6f to Color(0xFF0A0A30),
                    1.0f to BBBlueDeep
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            }

            // Editorial header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 18.dp)
            ) {
                Text(
                    "ISSUE 08 · GLOBAL",
                    color = BBHot,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.4.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "HUMAN",
                    color = Color.White,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DisplayFont,
                    letterSpacing = (-3.0).sp,
                    lineHeight = 50.sp,
                    maxLines = 1
                )
                Text(
                    text = buildAnnotatedString {
                        append("BENCH")
                        withStyle(SpanStyle(color = BBHot)) { append("·") }
                        append("MARK")
                        withStyle(SpanStyle(color = BBHot)) { append(".") }
                    },
                    color = Color.White,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DisplayFont,
                    letterSpacing = (-3.0).sp,
                    lineHeight = 50.sp,
                    maxLines = 1
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DotSep(text = "${games.size} games")
                    Bullet()
                    DotSep(text = if (scope == 1 && classId != null) classId else "Global leaderboard")
                    Bullet()
                    DotSep(text = "Top 100")
                }
            }

            // Scope toggle (Global / Your class)
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EditorialChip(
                    label = "Global",
                    active = scope == 0,
                    onClick = { scope = 0 }
                )
                EditorialChip(
                    label = if (classId != null) "Class · $classId" else "Class (no biodata)",
                    active = scope == 1,
                    onClick = { if (classId != null) scope = 1 }
                )
            }

            Spacer(Modifier.height(4.dp))

            // Tab pills (segmented look)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    EditorialChip(
                        label = "Overall",
                        active = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                }
                itemsIndexed(games) { idx, game ->
                    val tabIdx = idx + 1
                    EditorialChip(
                        label = game.title,
                        active = selectedTab == tabIdx,
                        onClick = { selectedTab = tabIdx }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (selectedTab == 0) {
                OverallList(rows = overall, loading = loading, selfId = prefs.playerId)
            } else {
                val game = games[selectedTab - 1]
                PerGameList(
                    rows = perGame,
                    loading = loading,
                    game = game,
                    selfId = prefs.playerId
                )
            }
        }
    }
}

@Composable
private fun Bullet() {
    Box(
        modifier = Modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.4f))
    )
}

@Composable
private fun DotSep(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 12.sp
    )
}

@Composable
private fun EditorialChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Color.White else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (active) BBInk else Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PerGameList(rows: List<ScoreRow>, loading: Boolean, game: Game, selfId: String) {
    if (rows.isEmpty()) {
        EmptyState(
            text = if (loading) "Loading..."
                else "No scores yet for ${game.title}.\nBe first to set a record."
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        itemsIndexed(rows) { idx, row ->
            val rank = idx + 1
            val isMe = row.playerId == selfId
            EditorialPerGameRow(rank, row, game, isMe)
        }
    }
}

@Composable
private fun OverallList(rows: List<OverallRow>, loading: Boolean, selfId: String) {
    if (rows.isEmpty()) {
        EmptyState(
            text = if (loading) "Crunching all 8 leaderboards..."
                else "No scores yet across any game.\nBe first to set a record."
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        itemsIndexed(rows) { idx, row ->
            val rank = idx + 1
            val isMe = row.playerId == selfId
            EditorialOverallRow(rank, row, isMe)
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EditorialPerGameRow(rank: Int, row: ScoreRow, game: Game, isMe: Boolean) {
    // Name = biodata-supplied displayName. Roll = playerId (now a roll
    // number for logged-in users; "anon_XXX" for offline / pre-login).
    val nameDisplay = row.displayName?.takeIf { it.isNotBlank() } ?: row.playerId
    val rollSubtitle = if (
        !row.displayName.isNullOrBlank() &&
        !row.playerId.startsWith("anon_") &&
        row.playerId != nameDisplay
    ) row.playerId else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            String.format("%02d", rank),
            color = Color.White.copy(alpha = 0.5f),
            fontFamily = MonoFont,
            fontSize = 11.sp,
            modifier = Modifier.width(28.dp)
        )
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    nameDisplay,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DisplayFont,
                    letterSpacing = (-1).sp,
                    maxLines = 1
                )
                if (isMe) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(game.accent)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "YOU",
                            color = BBInk,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.4.sp
                        )
                    }
                }
            }
            val subtitle = if (rollSubtitle != null) "$rollSubtitle · ${row.attempts} attempts"
                           else "${row.attempts} attempts"
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatLbValue(row.bestScore, game),
                    color = game.accent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DisplayFont,
                    letterSpacing = (-1.2).sp
                )
                Text(
                    game.unit,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 3.dp, bottom = 4.dp)
                )
            }
            Text(
                "RANK #$rank",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 9.sp,
                letterSpacing = 1.6.sp
            )
        }
    }
    Divider()
}

@Composable
private fun EditorialOverallRow(rank: Int, row: OverallRow, isMe: Boolean) {
    val nameDisplay = row.displayName?.takeIf { it.isNotBlank() } ?: row.playerId
    val rollSubtitle = if (
        !row.displayName.isNullOrBlank() &&
        !row.playerId.startsWith("anon_") &&
        row.playerId != nameDisplay
    ) row.playerId else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            String.format("%02d", rank),
            color = Color.White.copy(alpha = 0.5f),
            fontFamily = MonoFont,
            fontSize = 11.sp,
            modifier = Modifier.width(28.dp)
        )
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    nameDisplay,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DisplayFont,
                    letterSpacing = (-1).sp,
                    maxLines = 1
                )
                if (isMe) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(BBHot)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "YOU",
                            color = BBInk,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.4.sp
                        )
                    }
                }
            }
            val subtitle = if (rollSubtitle != null) "$rollSubtitle · ${row.gamesPlayed}/8 games"
                           else "${row.gamesPlayed}/8 games entered"
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${row.totalPoints}",
                    color = OverallAccent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DisplayFont,
                    letterSpacing = (-1.2).sp
                )
                Text(
                    "pts",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 3.dp, bottom = 4.dp)
                )
            }
            Text(
                "RANK #$rank",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 9.sp,
                letterSpacing = 1.6.sp
            )
        }
    }
    Divider()
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.12f))
    )
}

private fun formatLbValue(value: Double, game: Game): String = when (game.unit) {
    "ms" -> value.toInt().toString()
    "wpm" -> value.toInt().toString()
    "lvl" -> value.toInt().toString()
    "digits" -> value.toInt().toString()
    else -> value.toInt().toString()
}
