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
import com.justpass.app.data.model.Component
import com.justpass.app.data.model.CourseMarks
import com.justpass.app.data.repository.AttendanceRepository
import com.justpass.app.data.repository.Result as RepoResult
import java.util.concurrent.TimeUnit

/**
 * Polls CA marks every 3h. When a previously empty / NE component becomes a
 * real score (or a new component appears), fires a notification listing the
 * affected course + component names. Deep-links into the CA Marks tab.
 *
 * Signature format per course:
 *   COURSE_CODE>>compName=secured;compName=secured
 * Joined with "||". First run only seeds the signature — no notification —
 * otherwise fresh installs would dump every existing mark.
 */
class CAMarksNotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val repo = AttendanceRepository.getInstance(applicationContext)
        if (!repo.isLoggedIn()) return Result.success()

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousSig = prefs.getString(KEY_LAST_SIG, null)

        return try {
            when (val result = repo.fetchCAMarks()) {
                is RepoResult.Success -> {
                    val courses = result.data
                    val currentSig = buildSignature(courses)

                    if (previousSig == null) {
                        // First run — seed only, never spam every existing mark.
                        prefs.edit().putString(KEY_LAST_SIG, currentSig).apply()
                        return Result.success()
                    }

                    if (currentSig != previousSig) {
                        val updates = diffNewMarks(parseSignature(previousSig), courses)
                        if (updates.isNotEmpty()) {
                            showNotification(updates)
                        }
                        prefs.edit().putString(KEY_LAST_SIG, currentSig).apply()
                    }
                    Result.success()
                }
                else -> Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun buildSignature(courses: List<CourseMarks>): String {
        return courses.joinToString("||") { course ->
            val pairs = flattenComponents(course.testDetails.components)
                .joinToString(";") { (name, secured) -> "$name=$secured" }
            "${course.courseCode}>>$pairs"
        }
    }

    private fun parseSignature(sig: String): Map<String, Map<String, String>> {
        return sig.split("||").filter { it.isNotBlank() }.associate { entry ->
            val parts = entry.split(">>", limit = 2)
            val code = parts.getOrElse(0) { "" }
            val compMap = parts.getOrElse(1) { "" }
                .split(";")
                .filter { it.isNotBlank() }
                .associate { pair ->
                    val kv = pair.split("=", limit = 2)
                    kv.getOrElse(0) { "" } to kv.getOrElse(1) { "" }
                }
            code to compMap
        }
    }

    /** Returns list of "COURSE_CODE: ComponentName" entries where the secured
     *  value transitioned from absent / NE / "-" to a real numeric score. */
    private fun diffNewMarks(
        previous: Map<String, Map<String, String>>,
        currentCourses: List<CourseMarks>
    ): List<String> {
        val updates = mutableListOf<String>()
        currentCourses.forEach { course ->
            val prevMap = previous[course.courseCode].orEmpty()
            flattenComponents(course.testDetails.components).forEach { (name, secured) ->
                val before = prevMap[name]
                if (isMarkEntered(secured) && (before == null || !isMarkEntered(before)) && before != secured) {
                    val title = course.courseTitle.takeIf { it.isNotBlank() } ?: course.courseCode
                    updates += "$title — $name"
                }
            }
        }
        return updates
    }

    private fun isMarkEntered(value: String): Boolean {
        if (value.isBlank() || value == "-" || value.equals("NE", ignoreCase = true) || value.equals("N/E", ignoreCase = true)) {
            return false
        }
        return value.toDoubleOrNull() != null
    }

    /** Flatten components into (label, securedDisplay) pairs. Sub-components
     *  get their parent name as a prefix ("CA1/Test1"). */
    private fun flattenComponents(components: List<Component>): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        components.forEach { comp ->
            if (comp.hasSubComponent && !comp.subComponents.isNullOrEmpty()) {
                comp.subComponents.forEach { sub ->
                    val secured = sub.marks?.safeScaled?.getSecuredDisplay() ?: "-"
                    out += "${comp.name}/${sub.name}" to secured
                }
            } else {
                val secured = comp.marks?.safeScaled?.getSecuredDisplay() ?: "-"
                out += comp.name to secured
            }
        }
        return out
    }

    private fun showNotification(updates: List<String>) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CA Marks Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when CA marks are entered"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "camarks")
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (updates.size == 1) "New CA Mark Entered" else "${updates.size} New CA Marks Entered"
        val body = updates.joinToString("\n")

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(updates.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "camarks_channel"
        private const val NOTIFICATION_ID = 2003
        private const val WORK_NAME = "camarks_check"
        private const val PREFS_NAME = "laudea_prefs"
        private const val KEY_LAST_SIG = "last_seen_camarks_signature"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<CAMarksNotificationWorker>(
                3, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
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
