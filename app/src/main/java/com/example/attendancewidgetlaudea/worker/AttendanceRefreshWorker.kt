package com.example.attendancewidgetlaudea.worker

import android.content.Context
import androidx.work.*
import com.example.attendancewidgetlaudea.data.repository.AttendanceRepository
import com.example.attendancewidgetlaudea.widget.AttendanceWidgetReceiver
import java.util.concurrent.TimeUnit

class AttendanceRefreshWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
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
                    return Result.success()
                }
                is com.example.attendancewidgetlaudea.data.repository.Result.Error -> {
                    // Update widget to hide loading state
                    updateWidgets()
                    // Retry on failure
                    return if (runAttemptCount < 3) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
                is com.example.attendancewidgetlaudea.data.repository.Result.Loading -> {
                    return Result.success()
                }
            }
        } catch (e: Exception) {
            // Update widget to hide loading state
            updateWidgets()
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

    companion object {
        private const val WORK_NAME = "attendance_refresh_work"

        fun schedulePeriodicRefresh(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicWork = PeriodicWorkRequestBuilder<AttendanceRefreshWorker>(
                repeatInterval = 4,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 30,
                flexTimeIntervalUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicWork
                )
        }

        fun cancelPeriodicRefresh(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
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
