package com.example.attendancewidgetlaudea.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.attendancewidgetlaudea.MainActivity
import com.example.attendancewidgetlaudea.R
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceWidgetReceiver : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.example.attendancewidgetlaudea.ACTION_REFRESH"

        fun updateWidget(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, AttendanceWidgetReceiver::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }

        fun showLoadingState(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, AttendanceWidgetReceiver::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_attendance)
                views.setViewVisibility(R.id.btn_refresh, View.GONE)
                views.setViewVisibility(R.id.progress_loading, View.VISIBLE)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = SecurePreferences.getInstance(context)
            val attendanceData = prefs.getAttendanceData()
            val isLoggedIn = prefs.isLoggedIn()

            val views = RemoteViews(context.packageName, R.layout.widget_attendance)

            // Set click listener to open app
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, openAppPendingIntent)

            // Set refresh button click listener
            val refreshIntent = Intent(context, AttendanceWidgetReceiver::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

            if (isLoggedIn) {
                // Show attendance data
                views.setViewVisibility(R.id.dot_matrix_container, View.VISIBLE)
                views.setViewVisibility(R.id.text_percentage, View.VISIBLE)
                views.setViewVisibility(R.id.text_decimal, View.VISIBLE)
                views.setViewVisibility(R.id.text_percent_sign, View.VISIBLE)
                views.setViewVisibility(R.id.text_label, View.VISIBLE)
                views.setViewVisibility(R.id.stats_container, View.VISIBLE)
                views.setViewVisibility(R.id.btn_refresh, View.VISIBLE)
                views.setViewVisibility(R.id.progress_loading, View.GONE)
                views.setViewVisibility(R.id.text_last_refreshed, View.VISIBLE)
                views.setViewVisibility(R.id.text_not_logged_in, View.GONE)

                // Update percentage with decimal - use attendance WITH exemption as main
                val percentage = attendanceData.attendanceWithExemption
                val formatted = String.format(Locale.US, "%.1f", percentage)
                val parts = formatted.split(".")
                views.setTextViewText(R.id.text_percentage, parts[0])
                views.setTextViewText(R.id.text_decimal, ".${parts[1]}")

                // Set color based on attendance (red if below 75%)
                val textColor = if (percentage < 75) {
                    android.graphics.Color.parseColor("#FF5252") // Red
                } else {
                    android.graphics.Color.parseColor("#00E676") // Green
                }
                views.setTextColor(R.id.text_percentage, textColor)
                views.setTextColor(R.id.text_decimal, textColor)
                views.setTextColor(R.id.text_percent_sign, textColor)

                // Update present/absent/exemption
                views.setTextViewText(R.id.text_present, "P: ${attendanceData.presentCount}")
                views.setTextViewText(R.id.text_absent, "A: ${attendanceData.absentCount}")

                // Update last refreshed time
                val lastRefreshedText = if (attendanceData.lastUpdated > 0) {
                    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    "Updated: ${dateFormat.format(Date(attendanceData.lastUpdated))}"
                } else {
                    "Updated: --:--"
                }
                views.setTextViewText(R.id.text_last_refreshed, lastRefreshedText)

                // Update dot matrix - use attendance with exemption
                updateDotMatrix(context, views, attendanceData.attendanceWithExemption)
            } else {
                // Show login prompt
                views.setViewVisibility(R.id.dot_matrix_container, View.GONE)
                views.setViewVisibility(R.id.text_percentage, View.GONE)
                views.setViewVisibility(R.id.text_decimal, View.GONE)
                views.setViewVisibility(R.id.text_percent_sign, View.GONE)
                views.setViewVisibility(R.id.text_label, View.GONE)
                views.setViewVisibility(R.id.stats_container, View.GONE)
                views.setViewVisibility(R.id.btn_refresh, View.GONE)
                views.setViewVisibility(R.id.progress_loading, View.GONE)
                views.setViewVisibility(R.id.text_last_refreshed, View.GONE)
                views.setViewVisibility(R.id.text_not_logged_in, View.VISIBLE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun updateDotMatrix(context: Context, views: RemoteViews, percentage: Double) {
            val totalDots = 30
            val filledDots = ((percentage / 100.0) * totalDots).toInt().coerceIn(0, totalDots)

            // Clear and rebuild dot rows
            views.removeAllViews(R.id.dot_row_1)
            views.removeAllViews(R.id.dot_row_2)
            views.removeAllViews(R.id.dot_row_3)

            // Row 1 (dots 0-9)
            for (i in 0 until 10) {
                val dotView = RemoteViews(context.packageName, R.layout.widget_dot)
                val isActive = i < filledDots
                dotView.setImageViewResource(R.id.dot,
                    if (isActive) R.drawable.dot_active else R.drawable.dot_inactive)
                views.addView(R.id.dot_row_1, dotView)
            }

            // Row 2 (dots 10-19)
            for (i in 10 until 20) {
                val dotView = RemoteViews(context.packageName, R.layout.widget_dot)
                val isActive = i < filledDots
                dotView.setImageViewResource(R.id.dot,
                    if (isActive) R.drawable.dot_active else R.drawable.dot_inactive)
                views.addView(R.id.dot_row_2, dotView)
            }

            // Row 3 (dots 20-29)
            for (i in 20 until 30) {
                val dotView = RemoteViews(context.packageName, R.layout.widget_dot)
                val isActive = i < filledDots
                dotView.setImageViewResource(R.id.dot,
                    if (isActive) R.drawable.dot_active else R.drawable.dot_inactive)
                views.addView(R.id.dot_row_3, dotView)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_REFRESH) {
            // Show loading state immediately
            showLoadingState(context)
            // Trigger refresh via WorkManager
            com.example.attendancewidgetlaudea.worker.AttendanceRefreshWorker.refreshNow(context)
        }
    }

    override fun onEnabled(context: Context) {
        // Start periodic refresh when first widget is added
        com.example.attendancewidgetlaudea.worker.AttendanceRefreshWorker.schedulePeriodicRefresh(context)
    }

    override fun onDisabled(context: Context) {
        // Cancel refresh when last widget is removed
        com.example.attendancewidgetlaudea.worker.AttendanceRefreshWorker.cancelPeriodicRefresh(context)
    }
}
