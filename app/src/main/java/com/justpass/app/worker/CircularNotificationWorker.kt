package com.justpass.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.justpass.app.MainActivity
import com.justpass.app.R
import com.justpass.app.data.repository.AttendanceRepository
import java.util.concurrent.TimeUnit

class CircularNotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val repo = AttendanceRepository.getInstance(applicationContext)
        if (!repo.isLoggedIn()) return Result.success()

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSeenId = prefs.getString(KEY_LAST_SEEN_ID, null)

        return try {
            when (val result = repo.fetchCirculars()) {
                is com.justpass.app.data.repository.Result.Success -> {
                    val circulars = result.data.records
                    if (circulars.isNotEmpty()) {
                        val newest = circulars.first()
                        if (lastSeenId != null && newest.id != lastSeenId) {
                            // New circular found — show notification
                            showNotification(newest.title ?: "New Circular", newest.tag)
                        }
                        // Save newest ID
                        prefs.edit().putString(KEY_LAST_SEEN_ID, newest.id).apply()
                    }
                    Result.success()
                }
                else -> Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(title: String, tag: String?) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (required on API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "College Circulars",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new college circulars"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "circulars")
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val tagText = if (tag != null) "[$tag] " else ""
        val contentText = "$tagText$title"

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("New Circular")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "circulars_channel"
        private const val NOTIFICATION_ID = 2001
        private const val WORK_NAME = "circular_check"
        private const val PREFS_NAME = "laudea_prefs"
        private const val KEY_LAST_SEEN_ID = "last_seen_circular_id"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<CircularNotificationWorker>(
                3, TimeUnit.HOURS,    // Check every 3 hours
                30, TimeUnit.MINUTES  // Flex interval
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
