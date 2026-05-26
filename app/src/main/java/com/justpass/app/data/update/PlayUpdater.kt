package com.justpass.app.data.update

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo

/**
 * Wraps Google Play's In-App Update API.
 *
 * Two flows:
 *  - IMMEDIATE: full-screen blocking Play UI. Used when Play Console
 *    `inAppUpdatePriority >= 4` — for critical/breaking releases the user
 *    cannot dismiss. Resumes automatically across config changes via
 *    [resumeImmediateIfInProgress].
 *  - FLEXIBLE: downloads in background, then we show a gentle "Restart to
 *    install" card via [onDownloaded] callback. Priority 1-3.
 *
 * IMMEDIATE is the Play Store equivalent of the old GitHub `min_version_code`
 * remote-config gate, but enforced by Google's own UI (no custom dialog
 * needed) and only fires for users who installed from Play.
 *
 * Caller is responsible for: providing the activity-result launcher, calling
 * [check] once at start, calling [resumeImmediateIfInProgress] on every
 * onResume, and calling [unregister] when scope ends.
 */
class PlayUpdater(activity: Activity) {

    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    private var listener: InstallStateUpdatedListener? = null

    /** Decide flow based on Play Console `inAppUpdatePriority`. */
    suspend fun check(
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onDownloaded: () -> Unit,
    ) {
        val info: AppUpdateInfo = try {
            manager.requestAppUpdateInfo()
        } catch (_: Exception) {
            return
        }
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return

        val priority = info.updatePriority() // 0..5 from Play Console
        if (priority >= 4 && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            startImmediate(info, launcher)
            return
        }
        if (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
            // Register install-state listener BEFORE starting so we don't miss
            // the DOWNLOADED transition.
            registerListener(onDownloaded)
            startFlexible(info, launcher)
        }
    }

    /** Call on every onResume. Re-shows the blocking Play UI if an IMMEDIATE
     *  flow was interrupted (process killed, user backgrounded, etc.). */
    fun resumeImmediateIfInProgress(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                startImmediate(info, launcher)
            }
        }
    }

    /** Call after the user taps "Restart" in the flexible-update card. */
    fun completeFlexibleUpdate() {
        manager.completeUpdate()
    }

    fun unregister() {
        listener?.let { manager.unregisterListener(it) }
        listener = null
    }

    private fun startImmediate(
        info: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        try {
            manager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
            )
        } catch (_: Exception) {}
    }

    private fun startFlexible(
        info: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        try {
            manager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
            )
        } catch (_: Exception) {}
    }

    private fun registerListener(onDownloaded: () -> Unit) {
        if (listener != null) return
        val l = InstallStateUpdatedListener { state ->
            if (state.installStatus() == InstallStatus.DOWNLOADED) onDownloaded()
        }
        manager.registerListener(l)
        listener = l
    }
}
