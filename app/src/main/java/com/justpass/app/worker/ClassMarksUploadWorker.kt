package com.justpass.app.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.justpass.app.data.repository.AttendanceRepository
import com.justpass.app.data.repository.ClassMarksRepository
import java.util.concurrent.TimeUnit

/**
 * Periodic background upload of the user's CA marks to the class
 * comparison backend. Runs every 6h on a WorkManager periodic schedule.
 *
 * Gated by Firebase Remote Config flag `class_compare_enabled` (default
 * false). When the flag is off the worker bails before any network call,
 * so we can flip the whole feature on/off from the Firebase Console
 * without an APK update.
 */
class ClassMarksUploadWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            // Remote Config kill switch — default false in res/xml/remote_config_defaults.xml.
            val rc = FirebaseRemoteConfig.getInstance()
            val enabled = rc.getBoolean(REMOTE_FLAG)
            if (!enabled) {
                Log.d(TAG, "$REMOTE_FLAG is off, skipping upload")
                return Result.success()
            }

            val attendanceRepo = AttendanceRepository.getInstance(context)
            if (!attendanceRepo.isLoggedIn()) {
                Log.d(TAG, "Not logged in, skipping")
                return Result.success()
            }

            val courseMarks = attendanceRepo.cachedCourseMarks
                ?: attendanceRepo.fetchCAMarks().let { res ->
                    when (res) {
                        is com.justpass.app.data.repository.Result.Success -> res.data
                        else -> null
                    }
                }
            if (courseMarks.isNullOrEmpty()) {
                Log.d(TAG, "No course marks cached, skipping")
                return Result.success()
            }

            val classRepo = ClassMarksRepository.getInstance(context)
            val classKey = classRepo.resolveClassKey() ?: run {
                Log.d(TAG, "Cannot resolve classKey (missing batch/dept/section/sem), skipping")
                return Result.success()
            }
            val body = classRepo.buildUploadBody(courseMarks, classKey) ?: run {
                Log.d(TAG, "No subjects with valid marks, skipping")
                return Result.success()
            }
            val ok = classRepo.uploadIfChanged(body)
            return if (ok) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.w(TAG, "Worker failed: ${e.message}")
            return if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val TAG = "ClassMarksUploadWorker"
        private const val WORK_NAME = "class_marks_upload_worker"
        private const val REMOTE_FLAG = "class_compare_enabled"
        private const val INTERVAL_HOURS = 6L

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<ClassMarksUploadWorker>(
                INTERVAL_HOURS, TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Force a one-shot upload right now. Hook into fresh CA marks fetch. */
        fun uploadNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<ClassMarksUploadWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
