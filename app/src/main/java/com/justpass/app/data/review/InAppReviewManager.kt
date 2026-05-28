package com.justpass.app.data.review

import android.app.Activity
import android.content.pm.ApplicationInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.justpass.app.data.local.SecurePreferences

/**
 * Thin wrapper around Google's Play In-App Review API.
 *
 * Trigger policy: ask once per install, only after the user has had a real
 * chance to form an opinion of the app.
 *   - Account-age gate: [MIN_DAYS_SINCE_FIRST_LAUNCH] days since first open.
 *   - Engagement gate: at least [MIN_OPEN_COUNT] foreground sessions.
 *   - One-shot: [SecurePreferences.ratingAsked] flips true the moment we
 *     launch the flow. We do this even if the dialog didn't actually appear
 *     (Google enforces a per-user quota and may silently no-op), because the
 *     API does not tell us whether the user saw the prompt and re-prompting
 *     would be the bad outcome.
 *
 * Call [recordAppOpen] once on every cold start and [maybeShow] from the
 * main Activity once the user is on a non-blocking screen (Dashboard).
 */
object InAppReviewManager {

    private const val MIN_DAYS_SINCE_FIRST_LAUNCH = 3L
    private const val MIN_OPEN_COUNT = 5
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /** Bump the engagement counters. Idempotent for a given process start. */
    fun recordAppOpen(prefs: SecurePreferences) {
        if (prefs.firstLaunchMillis == 0L) {
            prefs.firstLaunchMillis = System.currentTimeMillis()
        }
        prefs.appOpenCount = prefs.appOpenCount + 1
    }

    /** True if all trigger conditions are met and we haven't asked yet. */
    fun shouldAsk(prefs: SecurePreferences): Boolean {
        if (prefs.ratingAsked) return false
        val first = prefs.firstLaunchMillis
        if (first == 0L) return false
        val ageMillis = System.currentTimeMillis() - first
        if (ageMillis < MIN_DAYS_SINCE_FIRST_LAUNCH * DAY_MILLIS) return false
        if (prefs.appOpenCount < MIN_OPEN_COUNT) return false
        return true
    }

    /**
     * Launch the Play In-App Review flow if eligible. Safe to call multiple
     * times — gated by [shouldAsk] and the one-shot flag. Fails silently if
     * the device has no Play Services or the request can't be built.
     */
    fun maybeShow(activity: Activity) {
        val prefs = SecurePreferences.getInstance(activity)
        if (!shouldAsk(prefs)) return

        // Debug builds: skip the real flow but flip the flag so we exercise
        // the gate logic without spamming the dev's Play account. The real
        // dialog only ever appears for Play-installed release builds anyway.
        val isDebuggable = (activity.applicationInfo.flags and
            ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            prefs.ratingAsked = true
            return
        }

        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) return@addOnCompleteListener
            val reviewInfo = request.result
            manager.launchReviewFlow(activity, reviewInfo).addOnCompleteListener {
                // Per Google's design, we never learn whether the user
                // actually rated. Mark asked so we don't ever pester again.
                prefs.ratingAsked = true
            }
        }
    }
}
