package com.justpass.app.games.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.justpass.app.games.data.model.Game
import com.justpass.app.games.ui.screens.games.AimTrainerScreen
import com.justpass.app.games.ui.screens.games.ChimpTestScreen
import com.justpass.app.games.ui.screens.games.NumberMemoryScreen
import com.justpass.app.games.ui.screens.games.ReactionTimeScreen
import com.justpass.app.games.ui.screens.games.SequenceMemoryScreen
import com.justpass.app.games.ui.screens.games.TypingScreen
import com.justpass.app.games.ui.screens.games.VerbalMemoryScreen
import com.justpass.app.games.ui.screens.games.VisualMemoryScreen
import com.justpass.app.games.ui.theme.BBInk

private sealed class GameRoute {
    data object Home : GameRoute()
    data class Play(val game: Game) : GameRoute()
}

/**
 * Top-level entry for the HumanBenchmark games subsystem inside JustPass.
 * Owns internal route state (Home grid ↔ single-game screen). External
 * leaderboard navigation hands off to JustPass via [onLeaderboard].
 *
 * Forces dark theme background so the games keep their brand look even
 * when JustPass is rendering in light mode.
 */
@Composable
fun GamesNav(
    onBack: () -> Unit,
    onLeaderboard: (Game?) -> Unit
) {
    var route by remember { mutableStateOf<GameRoute>(GameRoute.Home) }

    // Internal back: exit a single-game back to home grid; from home → onBack.
    BackHandler {
        when (val r = route) {
            is GameRoute.Play -> route = GameRoute.Home
            GameRoute.Home -> onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BBInk)
            .padding(bottom = 110.dp)
    ) {
        Crossfade(
            targetState = route,
            animationSpec = tween(200),
            label = "gamesRouteFade"
        ) { current ->
            when (current) {
                GameRoute.Home -> HomeScreen(
                    onPlay = { game -> route = GameRoute.Play(game) },
                    onLeaderboard = { onLeaderboard(null) }
                )
                is GameRoute.Play -> {
                    val gameOnBack: () -> Unit = { route = GameRoute.Home }
                    val gameOnLb: () -> Unit = { onLeaderboard(current.game) }
                    when (current.game) {
                        Game.REACTION_TIME -> ReactionTimeScreen(gameOnBack, gameOnLb)
                        Game.SEQUENCE_MEMORY -> SequenceMemoryScreen(gameOnBack, gameOnLb)
                        Game.AIM_TRAINER -> AimTrainerScreen(gameOnBack, gameOnLb)
                        Game.NUMBER_MEMORY -> NumberMemoryScreen(gameOnBack, gameOnLb)
                        Game.VERBAL_MEMORY -> VerbalMemoryScreen(gameOnBack, gameOnLb)
                        Game.VISUAL_MEMORY -> VisualMemoryScreen(gameOnBack, gameOnLb)
                        Game.TYPING -> TypingScreen(gameOnBack, gameOnLb)
                        Game.CHIMP_TEST -> ChimpTestScreen(gameOnBack, gameOnLb)
                    }
                }
            }
        }
    }
}
