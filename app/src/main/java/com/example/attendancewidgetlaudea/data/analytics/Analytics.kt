package com.example.attendancewidgetlaudea.data.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Lightweight wrapper around Firebase Analytics for tracking app usage.
 */
object Analytics {

    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
    }

    /** Set the logged-in user's roll number as the user ID */
    fun setUser(rollNumber: String) {
        firebaseAnalytics?.setUserId(rollNumber)
        firebaseAnalytics?.setUserProperty("roll_number", rollNumber)
    }

    fun clearUser() {
        firebaseAnalytics?.setUserId(null)
        firebaseAnalytics?.setUserProperty("roll_number", null)
    }

    fun logLogin(rollNumber: String) {
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.LOGIN, Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, "keycloak")
            putString("roll_number", rollNumber)
        })
    }

    fun logScreenView(screenName: String) {
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
        })
    }

    fun logRefresh(success: Boolean, method: String = "unknown") {
        firebaseAnalytics?.logEvent("attendance_refresh", Bundle().apply {
            putBoolean("success", success)
            putString("method", method)
        })
    }

    fun logLogout() {
        firebaseAnalytics?.logEvent("logout", null)
    }

    fun logFeatureUsed(feature: String) {
        firebaseAnalytics?.logEvent("feature_used", Bundle().apply {
            putString("feature", feature)
        })
    }
}
