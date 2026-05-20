package com.justpass.app.games.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.justpass.app.games.ui.theme.BBHot

/**
 * Three-bolt lives indicator. When [lives] drops, the lost bolt shrinks +
 * fades out (instead of instantly disappearing) and the phone vibrates.
 */
@Composable
fun LivesStrip(
    lives: Int,
    modifier: Modifier = Modifier,
    boltSize: Dp = 14.dp,
    activeColor: Color = BBHot,
    inactiveColor: Color = Color.White.copy(alpha = 0.15f)
) {
    val context = LocalContext.current
    val prevLives = remember { mutableIntStateOf(lives) }

    // Per-bolt scale + alpha for the drop animation
    val scales = remember { List(3) { Animatable(1f) } }
    val alphas = remember { List(3) { Animatable(1f) } }

    LaunchedEffect(lives) {
        if (lives < prevLives.intValue) {
            // ── Vibrate: stronger, longer, with a double-pulse pattern ──
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // 150ms strong pulse, 80ms gap, 150ms strong pulse
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0L, 150L, 80L, 150L),
                            intArrayOf(0, 255, 0, 255),
                            -1
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0L, 150L, 80L, 150L), -1)
                }
            }

            // ── Animate the bolt that was just lost ──
            val lostIndex = prevLives.intValue - 1  // index of the bolt that turned off
            if (lostIndex in 0..2) {
                // Quick pop then shrink to nothing
                scales[lostIndex].snapTo(1f)
                alphas[lostIndex].snapTo(1f)
                scales[lostIndex].animateTo(0f, tween(durationMillis = 350))
                alphas[lostIndex].animateTo(0f, tween(durationMillis = 300))
                // Reset back to 1 so the *inactive* bolt draws at normal size next frame
                scales[lostIndex].snapTo(1f)
                alphas[lostIndex].snapTo(1f)
            }
        }
        prevLives.intValue = lives
    }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            val active = i < lives
            // If this bolt is currently animating out (scale < 1), draw it in active color
            // otherwise draw active/inactive based on current lives count
            val color = if (!active && scales[i].value < 0.99f) activeColor else if (active) activeColor else inactiveColor
            Bolt(
                size = boltSize,
                color = color,
                modifier = Modifier
                    .scale(scales[i].value)
                    .alpha(alphas[i].value)
            )
        }
    }
}
