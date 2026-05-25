package com.justpass.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.justpass.app.data.model.BugReport
import com.justpass.app.data.model.BugReportMessage
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FieldValue
import java.io.ByteArrayOutputStream

/**
 * CRUD for bug reports + image uploads to Cloud Storage.
 *
 * Free-tier budget: Cloud Storage 5GB/month total + 50k downloads/day.
 * At ~300KB compressed JPEG per report, that's ~17k reports before hitting
 * 5GB. Plenty for current 1.4k DAU app.
 */
class BugReportRepository(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val reports = db.collection("bug_reports")
    private val storage = FirebaseStorage.getInstance()

    /**
     * Submit a bug report. If [imageUri] is non-null, the image is
     * compressed to JPEG @ 80% (max 1280px wide) and uploaded to
     * Storage at bug_reports/{requestId}/img.jpg. Returns the request id
     * on success, null on failure.
     */
    suspend fun submitReport(
        report: BugReport,
        imageUri: Uri?
    ): String? {
        return try {
            // Reserve the doc id first so we can use it as the storage path.
            val docRef = reports.document()
            val requestId = docRef.id

            var imageUrl = ""
            if (imageUri != null) {
                imageUrl = uploadImageOrEmpty(imageUri, requestId)
            }

            val now = System.currentTimeMillis()
            val payload = hashMapOf(
                "reporterPlayerId" to report.reporterPlayerId,
                "reporterName" to report.reporterName,
                "reporterRollNumber" to report.reporterRollNumber,
                "reporterDepartment" to report.reporterDepartment,
                "title" to report.title,
                "description" to report.description,
                "imageUrl" to imageUrl,
                "deviceModel" to report.deviceModel,
                "osVersion" to report.osVersion,
                "appVersion" to report.appVersion,
                "status" to "open",
                "resolution" to "",
                "createdAt" to now,
                "resolvedAt" to 0L,
                "messages" to emptyList<Map<String, Any>>(),
                // New report → admin has unread, user doesn't (they sent it).
                "adminUnread" to true,
                "userUnread" to false
            )
            docRef.set(payload).await()
            requestId
        } catch (e: Exception) {
            Log.e(TAG, "submitReport failed: ${e.message}")
            null
        }
    }

    /**
     * Compress + upload a local image. Returns the publicly-resolvable
     * download URL, or empty string on failure (caller proceeds without
     * the image rather than blocking the whole report).
     */
    private suspend fun uploadImageOrEmpty(uri: Uri, requestId: String): String {
        return try {
            val resolver = context.contentResolver
            val input = resolver.openInputStream(uri) ?: return ""
            val raw = input.use { BitmapFactory.decodeStream(it) } ?: return ""
            // Cap longest side at 1280px so we don't upload 12-megapixel
            // photos when an OCR-readable size is plenty.
            val scaled = if (maxOf(raw.width, raw.height) > 1280) {
                val ratio = 1280f / maxOf(raw.width, raw.height)
                Bitmap.createScaledBitmap(
                    raw,
                    (raw.width * ratio).toInt(),
                    (raw.height * ratio).toInt(),
                    true
                )
            } else raw
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            val bytes = out.toByteArray()
            if (scaled !== raw) raw.recycle()
            scaled.recycle()

            val ref = storage.reference.child("bug_reports/$requestId/img.jpg")
            ref.putBytes(bytes).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.w(TAG, "uploadImage failed (continuing without image): ${e.message}")
            ""
        }
    }

    /**
     * Listen to a single reporter's own reports. Used by the user-facing
     * "My Reports" tab so they can see admin replies + status updates.
     * Requires composite index: reporterPlayerId ASC + createdAt DESC.
     */
    fun listenMyReports(playerId: String, onUpdate: (List<BugReport>) -> Unit): ListenerRegistration {
        return reports
            .whereEqualTo("reporterPlayerId", playerId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "listenMyReports err: ${err.message}")
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.map { doc -> doc.toBugReport() } ?: emptyList()
                onUpdate(list)
            }
    }

    /** Listen to all reports — admin only consumer. */
    fun listenAllReports(onUpdate: (List<BugReport>) -> Unit): ListenerRegistration {
        return reports
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "listenAllReports err: ${err.message}")
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.map { doc -> doc.toBugReport() } ?: emptyList()
                onUpdate(list)
            }
    }

    suspend fun setStatus(reportId: String, status: String, resolution: String): Boolean {
        return try {
            reports.document(reportId).update(mapOf(
                "status" to status,
                "resolution" to resolution,
                "resolvedAt" to System.currentTimeMillis()
            )).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "setStatus failed: ${e.message}")
            false
        }
    }

    suspend fun setReply(reportId: String, message: String): Boolean {
        // Kept for backwards compat with any callers still using the old
        // single-reply path. New flows should use [appendMessage].
        return appendMessage(reportId, from = "admin", text = message)
    }

    /**
     * Append a new message to the conversation thread + flip the
     * opposite side's unread flag. [from] is `"user"` or `"admin"`.
     */
    suspend fun appendMessage(reportId: String, from: String, text: String): Boolean {
        if (text.isBlank()) return false
        return try {
            val now = System.currentTimeMillis()
            val msg = mapOf("from" to from, "text" to text, "timestamp" to now)
            val unreadField = if (from == "admin") "userUnread" else "adminUnread"
            val updates = mutableMapOf<String, Any>(
                "messages" to FieldValue.arrayUnion(msg),
                unreadField to true
            )
            if (from == "admin") {
                // Keep legacy single-reply fields in sync for older clients.
                updates["adminReply"] = text
                updates["repliedAt"] = now
            } else {
                updates["lastUserMessageAt"] = now
            }
            reports.document(reportId).update(updates).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "appendMessage failed: ${e.message}")
            false
        }
    }

    /**
     * Mark the [side]'s ("user" or "admin") unread flag false. Called
     * when that side opens the thread for that report.
     */
    suspend fun markRead(reportId: String, side: String): Boolean {
        val field = if (side == "user") "userUnread" else "adminUnread"
        return try {
            reports.document(reportId).update(field, false).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "markRead failed: ${e.message}")
            false
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toBugReport(): BugReport {
        @Suppress("UNCHECKED_CAST")
        val rawMsgs = get("messages") as? List<Map<String, Any?>>
        val msgs = rawMsgs?.map { m ->
            BugReportMessage(
                from = (m["from"] as? String) ?: "user",
                text = (m["text"] as? String) ?: "",
                timestamp = (m["timestamp"] as? Number)?.toLong() ?: 0L
            )
        } ?: emptyList()
        return BugReport(
            id = id,
            reporterPlayerId = getString("reporterPlayerId") ?: "",
            reporterName = getString("reporterName") ?: "",
            reporterRollNumber = getString("reporterRollNumber") ?: "",
            reporterDepartment = getString("reporterDepartment") ?: "",
            title = getString("title") ?: "",
            description = getString("description") ?: "",
            imageUrl = getString("imageUrl") ?: "",
            deviceModel = getString("deviceModel") ?: "",
            osVersion = getString("osVersion") ?: "",
            appVersion = getString("appVersion") ?: "",
            status = getString("status") ?: "open",
            resolution = getString("resolution") ?: "",
            createdAt = getLong("createdAt") ?: 0L,
            resolvedAt = getLong("resolvedAt") ?: 0L,
            messages = msgs,
            userUnread = getBoolean("userUnread") ?: false,
            adminUnread = getBoolean("adminUnread") ?: false,
            adminReply = getString("adminReply") ?: "",
            repliedAt = getLong("repliedAt") ?: 0L
        )
    }

    companion object { private const val TAG = "BugReportRepo" }
}
