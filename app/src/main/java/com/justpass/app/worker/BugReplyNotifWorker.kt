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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.justpass.app.MainActivity
import com.justpass.app.R
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.data.model.TournamentAdmins
import com.justpass.app.data.repository.ChessRepository
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Polls Firestore every ~15 min and surfaces a local notification when a
 * new bug-report reply lands for the current user. Mirrors the
 * LeaderboardBeatenWorker pattern. Works for both reporter and admin:
 *
 * - Reporter: watches their own bug_reports where userUnread=true. New
 *   IDs since last check → "Developer replied to your bug report".
 * - Admin: watches all bug_reports where adminUnread=true. New IDs since
 *   last check → "New bug report from <reporter>".
 *
 * Per-report dedup via [PREFS_NAME] state — once we've notified for a
 * particular reportId+timestamp, the same payload doesn't fire again
 * even if the unread flag stays set.
 */
class BugReplyNotifWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val securePrefs = SecurePreferences.getInstance(applicationContext)
        val roll = securePrefs.rollNumber.orEmpty()
        if (roll.isBlank()) return Result.success() // not logged in
        val playerId = ChessRepository().getPlayerId(roll)
        val isAdmin = TournamentAdmins.isAdmin(playerId)
        val state = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        return try {
            val db = FirebaseFirestore.getInstance().collection("bug_reports")
            val query = if (isAdmin) {
                db.whereEqualTo("adminUnread", true)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(20)
            } else {
                db.whereEqualTo("reporterPlayerId", playerId)
                    .whereEqualTo("userUnread", true)
                    .limit(20)
            }
            val snap = query.get().await()
            for (doc in snap.documents) {
                val reportId = doc.id
                val title = doc.getString("title") ?: "Bug report"
                val reporter = doc.getString("reporterName") ?: "User"
                val repliedAt = doc.getLong("repliedAt") ?: 0L
                val key = "notified_${if (isAdmin) "admin" else "user"}_${reportId}_$repliedAt"
                if (state.getBoolean(key, false)) continue
                if (isAdmin) {
                    notify(
                        id = NOTIF_BASE_ID + (reportId.hashCode() and 0xFFFF),
                        title = "New bug report",
                        body = "$reporter: $title",
                        adminInbox = true
                    )
                } else {
                    notify(
                        id = NOTIF_BASE_ID + (reportId.hashCode() and 0xFFFF),
                        title = "Developer replied",
                        body = "Re: $title",
                        adminInbox = false
                    )
                }
                state.edit().putBoolean(key, true).apply()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun notify(id: Int, title: String, body: String, adminInbox: Boolean) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Bug report replies", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifies you when there's a new bug-report message" }
            nm.createNotificationChannel(channel)
        }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", if (adminInbox) "bug_inbox" else "bug_report")
        }
        val pi = PendingIntent.getActivity(
            applicationContext, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(id, notif)
    }

    companion object {
        private const val CHANNEL_ID = "bug_reply_replies"
        private const val NOTIF_BASE_ID = 5000
        private const val WORK_NAME = "bug_reply_check"
        private const val PREFS_NAME = "bug_reply_notif_state"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<BugReplyNotifWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
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
