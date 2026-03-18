package com.example.attendancewidgetlaudea.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.attendancewidgetlaudea.R
import com.example.attendancewidgetlaudea.data.repository.AttendanceRepository
import com.example.attendancewidgetlaudea.data.update.UpdateChecker
import com.example.attendancewidgetlaudea.widget.AttendanceWidgetReceiver
import java.util.concurrent.TimeUnit

class AttendanceRefreshWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            // Check for app updates and notify
            checkForUpdateAndNotify()

            val repository = AttendanceRepository.getInstance(context)

            // Check if user is logged in
            if (!repository.isLoggedIn()) {
                return Result.success()
            }

            // Fetch attendance data (runs on Main thread internally)
            when (val result = repository.refreshAttendance()) {
                is com.example.attendancewidgetlaudea.data.repository.Result.Success -> {
                    // Update all widgets
                    updateWidgets()
                    // Self-chain: schedule the next run in 8 minutes
                    scheduleNextRefresh(context)
                    return Result.success()
                }
                is com.example.attendancewidgetlaudea.data.repository.Result.Error -> {
                    // Update widget to hide loading state
                    updateWidgets()
                    // Retry on failure
                    return if (runAttemptCount < 3) {
                        Result.retry()
                    } else {
                        // Even on final failure, keep the chain alive
                        scheduleNextRefresh(context)
                        Result.failure()
                    }
                }
                is com.example.attendancewidgetlaudea.data.repository.Result.Loading -> {
                    scheduleNextRefresh(context)
                    return Result.success()
                }
            }
        } catch (e: Exception) {
            // Update widget to hide loading state
            updateWidgets()
            // Keep the chain alive even on exception
            scheduleNextRefresh(context)
            return if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun updateWidgets() {
        try {
            AttendanceWidgetReceiver.updateWidget(context)
        } catch (e: Exception) {
            // Widget update failed, but work still succeeded
        }
    }

    private suspend fun checkForUpdateAndNotify() {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersion = packageInfo.versionName ?: return
            val update = UpdateChecker.checkForUpdate(currentVersion) ?: return

            // Don't notify if we already notified for this version
            val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
            val lastNotifiedVersion = prefs.getString("last_notified_version", null)
            if (lastNotifiedVersion == update.versionName) return

            // Create notification channel
            val channelId = "update_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId, "App Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Notifications for new app versions" }
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            }

            // Intent to open download URL
            val downloadIntent = PendingIntent.getActivity(
                context, 0,
                Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Update Available — v${update.versionName}")
                .setContentText(update.releaseNotes?.lines()?.firstOrNull() ?: "A new version is available!")
                .setContentIntent(downloadIntent)
                .setAutoCancel(true)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(1001, notification)

            // Remember we notified for this version
            prefs.edit().putString("last_notified_version", update.versionName).apply()
        } catch (e: Exception) {
            android.util.Log.e("RefreshWorker", "Update check failed: ${e.message}")
        }
    }

    companion object {
        private const val WORK_NAME_CHAIN = "attendance_refresh_chain"
        private const val REFRESH_INTERVAL_MINUTES = 8L

        /**
         * Start the self-chaining refresh. Call once on app launch.
         * Each worker schedules the next one after completing, creating a chain
         * that runs every ~8 minutes even in background.
         */
        fun schedulePeriodicRefresh(context: Context) {
            // Start the first link in the chain
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val initialWork = OneTimeWorkRequestBuilder<AttendanceRefreshWorker>()
                .setConstraints(constraints)
                .setInitialDelay(REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME_CHAIN,
                    ExistingWorkPolicy.KEEP,
                    initialWork
                )
        }

        /**
         * Schedule the next link in the chain (called by doWork after completion).
         */
        private fun scheduleNextRefresh(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val nextWork = OneTimeWorkRequestBuilder<AttendanceRefreshWorker>()
                .setConstraints(constraints)
                .setInitialDelay(REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME_CHAIN,
                    ExistingWorkPolicy.REPLACE,
                    nextWork
                )
        }

        fun cancelPeriodicRefresh(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_CHAIN)
        }

        fun refreshNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeWork = OneTimeWorkRequestBuilder<AttendanceRefreshWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeWork)
        }
    }
}
