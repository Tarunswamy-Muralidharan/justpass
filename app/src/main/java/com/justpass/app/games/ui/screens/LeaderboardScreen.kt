package com.justpass.app.games.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

// Medal palette for top-3 ranks.
private val Gold = Color(0xFFFFD54F)
private val Silver = Color(0xFFE0E6F0)
private val Bronze = Color(0xFFD08A4A)

private fun rankColor(rank: Int, accent: Color): Color = when (rank) {
    1 -> Gold
    2 -> Silver
    3 -> Bronze
    else -> accent
}

@Composable
fun LeaderboardScreen(
    onBack: () -> Unit,
    initialGame: Game? = null
) {
    val context = LocalContext.current
    val prefs = remember { ScorePrefs.getInstance(context) }
    val api = remember { ScoresApi() }
    val games = Game.entries
    val initialTab = remember(initialGame) {
        if (initialGame == null) 0 else games.indexOf(initialGame).let { if (it < 0) 0 else it + 1 }
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    val classId = remember { prefs.classId }
    // Scope: 0 = My Section (default if biodata loaded), 1 = Whole College.
    // If classId is null (no biodata), force college scope.
    var scope by rememberSaveable { mutableIntStateOf(if (classId != null) 0 else 1) }
    var perGame by remember { mutableStateOf<List<ScoreRow>>(emptyList()) }
    var overall by remember { mutableStateOf<List<OverallRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    val effectiveClass = if (scope == 0) classId else null

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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 18.dp)
            ) {
                Text(
                    "ISSUE 08 · COLLEGE",
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
                    DotSep(
                        text = if (scope == 0 && classId != null) "Section · $classId"
                               else "Whole college"
                    )
                    Bullet()
                    DotSep(text = "Top 100")
                }
            }

            // Scope toggle — Section first (when biodata available), College second.
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EditorialChip(
                    label = if (classId != null) "My section · $classId" else "Section (no biodata)",
                    active = scope == 0,
                    onClick = { if (classId != null) scope = 0 }
                )
                EditorialChip(
                    label = "Whole college",
                    active = scope == 1,
                    onClick = { scope = 1 }
                )
            }

            Spacer(Modifier.height(4.dp))

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
    Text(text, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
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

/**
 * Big colored rank pill. Top-3 get medal colors; rest use the row accent.
 */
@Composable
private fun BigRank(rank: Int, accent: Color) {
    val color = rankColor(rank, accent)
    val isMedal = rank in 1..3
    Box(
        modifier = Modifier
            .width(54.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isMedal) color.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
            .border(
                width = if (isMedal) 1.5.dp else 1.dp,
                color = if (isMedal) color else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (rank < 100) String.format("%02d", rank) else rank.toString(),
            color = color,
            fontSize = if (rank < 100) 26.sp else 22.sp,
            fontWeight = FontWeight.Black,
            fontFamily = DisplayFont,
            letterSpacing = (-1).sp
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
        BigRank(rank = rank, accent = game.accent)
        Spacer(Modifier.width(12.dp))
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
        BigRank(rank = rank, accent = OverallAccent)
        Spacer(Modifier.width(12.dp))
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
