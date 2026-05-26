package com.justpass.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Calls the chess-lobby Worker's /admin/announcement endpoint to publish
 * a Remote Config announcement update. Admin gating happens server-side —
 * the Worker checks the caller's Firebase UID against the admin_uids
 * Firestore collection before forwarding to Remote Config.
 *
 * The Android app holds no service account credentials. The only thing
 * sent up is the user's Firebase ID token (verified per-request by the
 * Worker via Google's JWKS).
 */
class AnnouncementAdminRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val auth = FirebaseAuth.getInstance()

    sealed class Result {
        object Success : Result()
        data class Error(val message: String) : Result()
    }

    /**
     * Publish or clear an announcement. Pass active=false with any id/title/message
     * to disable the dialog without losing the text (Remote Config keeps the
     * fields populated). Pass active=true with non-empty id + message to fire it.
     */
    suspend fun publish(
        active: Boolean,
        id: String,
        title: String,
        message: String,
    ): Result {
        val user = auth.currentUser ?: return Result.Error("Not signed in")
        val token = try {
            user.getIdToken(false).await().token
        } catch (e: Exception) {
            return Result.Error("Failed to get ID token: ${e.message}")
        } ?: return Result.Error("ID token was null")

        val payload = JSONObject().apply {
            put("active", active)
            put("id", id)
            put("title", title)
            put("message", message)
        }.toString()

        val request = Request.Builder()
            .url("$BASE_URL/admin/announcement")
            .post(payload.toRequestBody(JSON_MEDIA))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.Success
                } else {
                    val body = resp.body?.string().orEmpty()
                    Log.w(TAG, "publish ${resp.code}: $body")
                    val reason = try {
                        JSONObject(body).optString("reason").ifBlank {
                            JSONObject(body).optString("error")
                        }
                    } catch (_: Exception) { body.take(200) }
                    Result.Error("HTTP ${resp.code}: ${reason.ifBlank { "unknown error" }}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "publish network error: ${e.message}")
            Result.Error("Network error: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AnnouncementAdminRepo"
        private const val BASE_URL = "https://chess-lobby.tmswamy10.workers.dev"
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
