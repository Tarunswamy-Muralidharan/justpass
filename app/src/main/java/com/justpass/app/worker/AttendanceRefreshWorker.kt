package com.justpass.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * Attendance auto-refresh has been permanently DISCONTINUED at the college's
 * request — they observed automated polling of their SIS attendance endpoint.
 *
 * This worker used to self-chain every ~8 minutes and fetch attendance in the
 * background (the source of the flagged traffic). It now does NOTHING except
 * cancel any previously-scheduled chain, so that on updated devices the old
 * background polling stops for good and never fetches attendance again.
 *
 * The schedule/refresh entry points are kept (callers still reference them) but
 * are no-ops that never enqueue a fetch. Do not re-enable.
 */
class AttendanceRefreshWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // If a legacy self-chain link fires on an updated device, kill the chain
        // and stop — never fetch, never reschedule.
        cancelPeriodicRefresh(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME_CHAIN = "attendance_refresh_chain"

        /**
         * Formerly started the ~8-min background attendance poll. Now cancels any
         * existing chain instead — so simply launching the app (or enabling the
         * widget) permanently stops the old polling on updated devices.
         */
        fun schedulePeriodicRefresh(context: Context) {
            cancelPeriodicRefresh(context)
        }

        /** No-op — attendance fetching is discontinued. */
        fun refreshNow(@Suppress("UNUSED_PARAMETER") context: Context) { /* discontinued */ }

        fun cancelPeriodicRefresh(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_CHAIN)
        }
    }
}
