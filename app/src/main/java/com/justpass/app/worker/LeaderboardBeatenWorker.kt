package com.justpass.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.justpass.app.MainActivity
import com.justpass.app.R
import com.justpass.app.games.data.api.ScoresApi
import com.justpass.app.games.data.local.ScorePrefs
import com.justpass.app.games.data.model.Game
import java.util.concurrent.TimeUnit

/**
 * Polls the user's rank in every game and fires a notification when they
 * have been overtaken on the section leaderboard. Tracks per-game last
 * known rank in SharedPreferences. Throttled to 1 notification per game
 * per 6h to avoid spam.
 */
class LeaderboardBeatenWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val scorePrefs = ScorePrefs.getInstance(applicationContext)
        val playerId = scorePrefs.playerId
        // Anonymous (no roll) → nothing to track.
        if (playerId.startsWith("anon_")) return Result.success()

        val api = ScoresApi()
        val classId = scorePrefs.classId
        val now = System.currentTimeMillis()

        return try {
            for (game in Game.entries) {
                // Only check games the user has a personal best in — no
                // best = nothing to defend.
                val myBest = scorePrefs.getBest(game) ?: continue
                val rows = api.leaderboard(game, classId = classId).ifEmpty {
                    api.leaderboard(game, classId = null)
                }
                if (rows.isEmpty()) continue
                val myRow = rows.indexOfFirst { it.playerId == playerId }
                val myRank = if (myRow >= 0) myRow + 1 else rows.size + 1

                val lastRank = prefs.getInt("rank_${game.id}", -1)
                val lastNotifAt = prefs.getLong("notif_${game.id}", 0L)

                if (lastRank in 1..(myRank - 1) && now - lastNotifAt >= NOTIF_THROTTLE_MS) {
                    // Find the player who is now ahead of us (rank myRank-1).
                    val ahead = rows.getOrNull(myRank - 2)
                    val aheadName = ahead?.displayName?.takeIf { it.isNotBlank() }
                        ?: ahead?.playerId
                        ?: "Someone"
                    notify(game, aheadName, myRank, lastRank, myBest)
                    prefs.edit().putLong("notif_${game.id}", now).apply()
                }

                prefs.edit().putInt("rank_${game.id}", myRank).apply()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun notify(
        game: Game,
        aheadName: String,
        myRank: Int,
        previousRank: Int,
        myBest: Double
    ) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Leaderboard alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifies when someone beats your score" }
            nm.createNotificationChannel(channel)
        }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "games_leaderboard")
            putExtra("leaderboard_game_id", game.id)
        }
        val pi = PendingIntent.getActivity(
            applicationContext,
            game.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = "$aheadName just took ${game.title}. You dropped from #$previousRank to #$myRank " +
            "(your best: ${myBest.toInt()} ${game.unit})."
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("You were beaten on ${game.title}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_BASE_ID + game.ordinal, notif)
    }

    companion object {
        private const val CHANNEL_ID = "leaderboard_beaten"
        private const val NOTIF_BASE_ID = 4000
        private const val WORK_NAME = "leaderboard_beaten_check"
        private const val PREFS_NAME = "leaderboard_beaten_state"
        private const val NOTIF_THROTTLE_MS = 6L * 60L * 60L * 1000L // 6h per game

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<LeaderboardBeatenWorker>(
                30, TimeUnit.MINUTES,
                10, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
