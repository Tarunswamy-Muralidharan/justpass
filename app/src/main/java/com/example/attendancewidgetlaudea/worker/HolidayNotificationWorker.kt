package com.example.attendancewidgetlaudea.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.attendancewidgetlaudea.MainActivity
import com.example.attendancewidgetlaudea.R
import com.example.attendancewidgetlaudea.data.model.CalendarEventType
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class HolidayNotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val tomorrow = LocalDate.now().plusDays(1)
            val tomorrowStr = tomorrow.format(DateTimeFormatter.ISO_LOCAL_DATE)

            val events = withContext(Dispatchers.IO) { fetchCalendarEvents() }

            // Find holidays that fall on tomorrow
            val tomorrowHolidays = events.filter { (startDate, _, eventType) ->
                eventType == CalendarEventType.HOLIDAY && startDate == tomorrowStr
            }

            if (tomorrowHolidays.isNotEmpty()) {
                val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastNotifiedDate = prefs.getString(KEY_LAST_NOTIFIED_DATE, null)

                // Only notify once per holiday date
                if (lastNotifiedDate != tomorrowStr) {
                    val holidayNames = tomorrowHolidays.map { it.second }
                    showNotification(holidayNames, tomorrowStr)
                    prefs.edit().putString(KEY_LAST_NOTIFIED_DATE, tomorrowStr).apply()
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun fetchCalendarEvents(): List<Triple<String, String, CalendarEventType>> {
        val now = LocalDate.now()
        val timeMin = now.format(DateTimeFormatter.ISO_LOCAL_DATE) + "T00:00:00Z"
        val timeMax = now.plusDays(3).format(DateTimeFormatter.ISO_LOCAL_DATE) + "T00:00:00Z"

        val url = "https://www.googleapis.com/calendar/v3/calendars/$CALENDAR_ID/events" +
                "?key=$API_KEY&timeMin=$timeMin&timeMax=$timeMax" +
                "&singleEvents=true&orderBy=startTime&maxResults=50"

        val response = URL(url).readText()
        val json = Gson().fromJson(response, JsonObject::class.java)
        val items = json.getAsJsonArray("items") ?: return emptyList()

        return items.mapNotNull { item ->
            val obj = item.asJsonObject
            val summary = obj.get("summary")?.asString ?: return@mapNotNull null
            val start = obj.getAsJsonObject("start")
            val startDate = start?.get("date")?.asString
                ?: start?.get("dateTime")?.asString?.substring(0, 10)
                ?: return@mapNotNull null

            Triple(startDate, summary, CalendarEventType.fromSummary(summary))
        }
    }

    private fun showNotification(holidayNames: List<String>, date: String) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Holiday Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for upcoming holidays"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "calendar")
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (holidayNames.size == 1) "Tomorrow is a Holiday!" else "Holidays Tomorrow!"
        val body = holidayNames.joinToString("\n") { it.removePrefix("Holiday - ").removePrefix("Holiday- ").removePrefix("Holiday-").trim() }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CALENDAR_ID = "c_f65646ec47f509e6a093824790c28766188222d525707dfb817f80ac21e9e24c%40group.calendar.google.com"
        private const val API_KEY = "AIzaSyBNlYH01_9Hc5S1J9vuFmu2nUqBZJNAXxs"
        private const val CHANNEL_ID = "holiday_channel"
        private const val NOTIFICATION_ID = 2002
        private const val WORK_NAME = "holiday_check"
        private const val PREFS_NAME = "laudea_prefs"
        private const val KEY_LAST_NOTIFIED_DATE = "last_holiday_notified_date"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<HolidayNotificationWorker>(
                6, TimeUnit.HOURS,
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
