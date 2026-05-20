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
import kotlinx.coroutines.tasks.await
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
                "createdAt" to System.currentTimeMillis(),
                "resolvedAt" to 0L
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
                val list = snap?.documents?.map { doc ->
                    BugReport(
                        id = doc.id,
                        reporterPlayerId = doc.getString("reporterPlayerId") ?: "",
                        reporterName = doc.getString("reporterName") ?: "",
                        reporterRollNumber = doc.getString("reporterRollNumber") ?: "",
                        reporterDepartment = doc.getString("reporterDepartment") ?: "",
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        deviceModel = doc.getString("deviceModel") ?: "",
                        osVersion = doc.getString("osVersion") ?: "",
                        appVersion = doc.getString("appVersion") ?: "",
                        status = doc.getString("status") ?: "open",
                        resolution = doc.getString("resolution") ?: "",
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        resolvedAt = doc.getLong("resolvedAt") ?: 0L,
                        adminReply = doc.getString("adminReply") ?: "",
                        repliedAt = doc.getLong("repliedAt") ?: 0L
                    )
                } ?: emptyList()
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
                val list = snap?.documents?.map { doc ->
                    BugReport(
                        id = doc.id,
                        reporterPlayerId = doc.getString("reporterPlayerId") ?: "",
                        reporterName = doc.getString("reporterName") ?: "",
                        reporterRollNumber = doc.getString("reporterRollNumber") ?: "",
                        reporterDepartment = doc.getString("reporterDepartment") ?: "",
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        deviceModel = doc.getString("deviceModel") ?: "",
                        osVersion = doc.getString("osVersion") ?: "",
                        appVersion = doc.getString("appVersion") ?: "",
                        status = doc.getString("status") ?: "open",
                        resolution = doc.getString("resolution") ?: "",
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        resolvedAt = doc.getLong("resolvedAt") ?: 0L,
                        adminReply = doc.getString("adminReply") ?: "",
                        repliedAt = doc.getLong("repliedAt") ?: 0L
                    )
                } ?: emptyList()
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
        return try {
            reports.document(reportId).update(mapOf(
                "adminReply" to message,
                "repliedAt" to System.currentTimeMillis()
            )).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "setReply failed: ${e.message}")
            false
        }
    }

    companion object { private const val TAG = "BugReportRepo" }
}
