package com.justpass.app.games.ui.components

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.games.data.local.ScorePrefs
import com.justpass.app.games.data.model.Game
import com.justpass.app.games.ui.theme.BBInk
import com.justpass.app.games.ui.theme.DisplayFont
import com.justpass.app.games.ui.theme.MonoFont

@Composable
fun GameScaffold(
    game: Game,
    onBack: () -> Unit,
    content: @Composable (paddingTop: Dp) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { ScorePrefs.getInstance(context) }
    var showInstructions by remember { mutableStateOf(false) }

    LaunchedEffect(game) {
        if (!prefs.hasSeenInstructions(game)) {
            showInstructions = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BBInk)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            GameShellTopBar(
                game = game,
                onBack = onBack,
                onHow = { showInstructions = true }
            )
            content(0.dp)
        }
    }

    if (showInstructions) {
        InstructionsDialog(
            game = game,
            onDismiss = {
                showInstructions = false
                prefs.markInstructionsSeen(game)
            }
        )
    }
}

@Composable
private fun GameShellTopBar(game: Game, onBack: () -> Unit, onHow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Back circle
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    game.code.uppercase(),
                    color = Color.White.copy(alpha = 0.5f),
                    fontFamily = MonoFont,
                    fontSize = 9.sp,
                    letterSpacing = 1.8.sp
                )
                Row {
                    Text(
                        game.shortName,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DisplayFont,
                        letterSpacing = (-0.6).sp,
                        lineHeight = 18.sp
                    )
                    Text(
                        ".",
                        color = game.accent,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DisplayFont,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        // How pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, game.accent.copy(alpha = 0.30f), RoundedCornerShape(50))
                .clickable(onClick = onHow)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, game.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("?", color = game.accent, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
            Text(
                "HOW",
                color = game.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.7.sp
            )
        }
    }
}

@Composable
fun StatLine(
    accent: Color,
    leftLabel: String,
    leftValue: String,
    leftUnit: String = "",
    rightLabel: String,
    rightValue: String,
    rightUnit: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        StatBlock(label = leftLabel, value = leftValue, unit = leftUnit, color = Color.White)
        StatBlock(label = rightLabel, value = rightValue, unit = rightUnit, color = accent, alignEnd = true)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f))
    )
}

@Composable
private fun StatBlock(label: String, value: String, unit: String, color: Color, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            label.uppercase(),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 9.sp,
            letterSpacing = 1.8.sp
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = color,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                fontFamily = DisplayFont,
                letterSpacing = (-0.7).sp,
                lineHeight = 22.sp
            )
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 3.dp, bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
fun PrimaryBtn(
    accent: Color,
    text: String,
    big: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(accent)
            .clickable(onClick = onClick)
            .padding(
                horizontal = 20.dp,
                vertical = if (big) 18.dp else 14.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.uppercase(),
            color = BBInk,
            fontSize = if (big) 16.sp else 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.85.sp
        )
    }
}

@Composable
fun GhostBtn(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.uppercase(),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.85.sp
        )
    }
}

@Composable
fun KickerText(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text.uppercase(),
        color = accent,
        fontFamily = MonoFont,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.4.sp,
        modifier = modifier
    )
}

@Composable
private fun InstructionsDialog(game: Game, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF15151E),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCFD8DC),
        title = {
            Column {
                Text(
                    game.code.uppercase(),
                    color = Color.White.copy(alpha = 0.5f),
                    fontFamily = MonoFont,
                    fontSize = 9.sp,
                    letterSpacing = 1.8.sp
                )
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(
                        game.shortName,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DisplayFont,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        ".",
                        color = game.accent,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DisplayFont
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                game.instructions.forEachIndexed { idx, line ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(game.accent.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${idx + 1}",
                                color = game.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = DisplayFont
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(line, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = game.accent)
            ) {
                Text(
                    "GOT IT",
                    color = BBInk,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.85.sp
                )
            }
        }
    )
}
