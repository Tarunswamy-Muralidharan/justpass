package com.example.attendancewidgetlaudea.data.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Lightweight wrapper around Firebase Analytics for tracking app usage.
 */
object Analytics {

    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
    }

    /** Set the logged-in user's roll number and name */
    fun setUser(rollNumber: String, displayName: String? = null) {
        firebaseAnalytics?.setUserId(rollNumber)
        firebaseAnalytics?.setUserProperty("roll_number", rollNumber)
        displayName?.let {
            firebaseAnalytics?.setUserProperty("display_name", it)
        }
    }

    /** Extract display name from a Keycloak JWT token */
    fun extractNameFromToken(token: String): String? {
        return try {
            // JWT is 3 base64 parts separated by dots — payload is the 2nd part
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = String(
                android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING),
                Charsets.UTF_8
            )
            val json = org.json.JSONObject(payload)
            // Keycloak puts the name in "name" or "preferred_username"
            json.optString("name").ifEmpty {
                json.optString("preferred_username").ifEmpty { null }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun clearUser() {
        firebaseAnalytics?.setUserId(null)
        firebaseAnalytics?.setUserProperty("roll_number", null)
        firebaseAnalytics?.setUserProperty("display_name", null)
    }

    fun logLogin(rollNumber: String, displayName: String? = null) {
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.LOGIN, Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, "keycloak")
            putString("roll_number", rollNumber)
            displayName?.let { putString("display_name", it) }
        })
    }

    fun logAppOpen() {
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.APP_OPEN, null)
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

    fun logResultViewed(semester: Int) {
        firebaseAnalytics?.logEvent("result_viewed", Bundle().apply {
            putInt("semester", semester)
        })
    }

    fun logPullToRefresh() {
        firebaseAnalytics?.logEvent("pull_to_refresh", null)
    }

    fun logEasterEggTriggered(egg: String) {
        firebaseAnalytics?.logEvent("easter_egg_triggered", Bundle().apply {
            putString("egg", egg)
        })
    }

    fun logAppVersion(version: String) {
        firebaseAnalytics?.setUserProperty("app_version", version)
    }

    fun logSessionDuration(durationMs: Long) {
        firebaseAnalytics?.logEvent("session_duration", Bundle().apply {
            putLong("duration_ms", durationMs)
        })
    }

    fun logInstallSource() {
        val source = try {
            appContext?.let { ctx ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    ctx.packageManager.getInstallerPackageName(ctx.packageName)
                }
            } ?: "unknown"
        } catch (_: Exception) { "unknown" }
        firebaseAnalytics?.setUserProperty("install_source", source)
    }
}
