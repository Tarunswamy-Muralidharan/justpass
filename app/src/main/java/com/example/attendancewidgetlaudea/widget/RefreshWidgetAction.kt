package com.example.attendancewidgetlaudea.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.attendancewidgetlaudea.worker.AttendanceRefreshWorker

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Trigger immediate refresh via WorkManager
        val refreshWork = OneTimeWorkRequestBuilder<AttendanceRefreshWorker>()
            .build()

        WorkManager.getInstance(context).enqueue(refreshWork)
    }
}
