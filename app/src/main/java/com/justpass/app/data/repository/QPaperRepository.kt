package com.justpass.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.data.model.DeclineKind
import com.justpass.app.data.model.PaperCategory
import com.justpass.app.data.model.QPaper
import com.justpass.app.data.model.QPaperContributor
import com.justpass.app.data.model.UploadIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.Result as KResult

/**
 * Source of truth for the previous-year question papers feature.
 *
 * Wraps Firestore (metadata + contributor records) and CloudinaryUploader
 * (PDF bytes). All paper bytes live on Cloudinary; the app NEVER persists
 * a downloaded PDF to disk. Each viewer fetch hits Cloudinary fresh.
 *
 * The two-collection privacy model:
 *   qpapers/{paperId}              — public-readable when approved, no
 *                                    contributor identity at all.
 *   qpapers_contributors/{paperId} — admin-only OR self-readable. The
 *                                    only place uid + name + roll are
 *                                    linked to a paper.
 *
 * See firebase/firestore.rules for the enforced policy.
 */
class QPaperRepository private constructor(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val securePrefs = SecurePreferences.getInstance(context)
    private val uploader = CloudinaryUploader()

    private val papersCol = db.collection("qpapers")
    private val contributorsCol = db.collection("qpapers_contributors")

    val isStorageConfigured: Boolean get() = uploader.isConfigured

    // ─────────────────────── reads ─────────────────────────────────────

    /**
     * All approved papers for one (department, subject, regulation),
     * grouped client-side into [PaperCategory]. Used by the subject detail
     * screen (3 tabs: CA1, CA2, Sem).
     */
    suspend fun listApprovedForSubject(
        department: String,
        subjectCode: String,
        regulation: String,
    ): Map<PaperCategory, List<QPaper>> {
        return try {
            val snap = papersCol
                .whereEqualTo("department", department)
                .whereEqualTo("subjectCode", subjectCode)
                .whereEqualTo("regulation", regulation)
                .whereEqualTo("status", "approved")
                .orderBy("examYear", Query.Direction.DESCENDING)
                .get().await()
            val papers = snap.documents.mapNotNull { d ->
                d.toObject(QPaper::class.java)?.copy(id = d.id)
            }
            PaperCategory.all.associateWith { cat ->
                papers.filter { it.categoryEnum == cat }
            }
        } catch (e: Exception) {
            Log.e(TAG, "listApprovedForSubject err: ${e.message}", e)
            PaperCategory.all.associateWith { emptyList() }
        }
    }

    /**
     * Has the current user already contributed for this exact slot?
     * Returns the contributor record if so, null otherwise. The upload
     * screen calls this on mount to swap the Contribute button for an
     * "Already contributed — thanks!" message.
     */
    suspend fun findMyContributionForSlot(intent: UploadIntent): QPaperContributor? {
        val uid = auth.currentUser?.uid
            ?: runCatching { auth.signInAnonymously().await().user?.uid }.getOrNull()
            ?: return null
        return try {
            val snap = contributorsCol
                .whereEqualTo("uid", uid)
                .whereEqualTo("subjectCode", intent.subjectCode)
                .whereEqualTo("category", intent.category.key)
                .whereEqualTo("examYear", intent.examYear)
                .whereEqualTo("regulation", intent.regulation)
                .limit(1)
                .get().await()
            snap.documents.firstOrNull()?.let { d ->
                d.toObject(QPaperContributor::class.java)?.copy(paperId = d.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "findMyContributionForSlot err: ${e.message}", e)
            null
        }
    }

    /** All of my contributions — for the "thank you" follow-up screen. */
    suspend fun listMyContributions(): List<QPaperContributor> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snap = contributorsCol
                .whereEqualTo("uid", uid)
                .orderBy("submittedAt", Query.Direction.DESCENDING)
                .get().await()
            snap.documents.mapNotNull { d ->
                d.toObject(QPaperContributor::class.java)?.copy(paperId = d.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "listMyContributions err: ${e.message}", e)
            emptyList()
        }
    }

    /** Stream PDF bytes from Cloudinary. Never written to disk. */
    suspend fun downloadPdfBytes(paper: QPaper): KResult<ByteArray> {
        return uploader.downloadPdf(paper.cloudinaryUrl)
    }

    /**
     * Admin-only: save a paper's PDF to the public Downloads folder so the
     * admin can open it in any reader and examine (or edit) it before
     * deciding. This deliberately bypasses the app's usual never-persist
     * rule — it's gated to the admin review flow only.
     *
     * Returns a human-readable destination path for a confirmation toast.
     */
    suspend fun downloadToDownloads(paper: QPaper): KResult<String> {
        val bytes = uploader.downloadPdf(paper.cloudinaryUrl).getOrElse {
            return KResult.failure(it)
        }
        return withContext(Dispatchers.IO) {
            try {
                val safeName = buildString {
                    append(paper.subjectCode.ifBlank { "paper" })
                    append("_").append(paper.categoryEnum.label)
                    append("_").append(paper.examYear)
                    append(".pdf")
                }.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val subDir = "JustPass QPapers"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                        put(
                            MediaStore.Downloads.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/" + subDir,
                        )
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext KResult.failure(
                            IllegalStateException("Couldn't create file in Downloads"),
                        )
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: return@withContext KResult.failure(
                            IllegalStateException("Couldn't open output stream"),
                        )
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    KResult.success("Download/$subDir/$safeName")
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        subDir,
                    )
                    if (!dir.exists()) dir.mkdirs()
                    val outFile = File(dir, safeName)
                    outFile.writeBytes(bytes)
                    KResult.success("Download/$subDir/$safeName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "downloadToDownloads err: ${e.message}", e)
                KResult.failure(e)
            }
        }
    }

    // ─────────────────────── contribute ────────────────────────────────

    /**
     * The full upload flow:
     *   1. Upload PDF bytes to Cloudinary (unsigned preset).
     *   2. Create qpapers doc (status=pending, no contributor fields).
     *   3. Create qpapers_contributors doc (admin-only, holds identity).
     *
     * Both Firestore writes are best-effort sequential — if step 3 fails
     * we still have the paper doc but no contributor record. Admin can
     * still see and approve it; the contributor just won't see it in
     * "My Contributions". Acceptable failure mode for v1.
     */
    suspend fun upload(
        bytes: ByteArray,
        intent: UploadIntent,
    ): KResult<QPaper> {
        val uid = auth.currentUser?.uid
            ?: runCatching { auth.signInAnonymously().await().user?.uid }.getOrNull()
            ?: return KResult.failure(IllegalStateException("Not signed in"))
        val displayName = securePrefs.displayName.orEmpty()
        val rollNumber = securePrefs.rollNumber.orEmpty()
        if (rollNumber.isBlank()) {
            return KResult.failure(IllegalStateException("Missing roll number — log in first"))
        }

        // 1. Cloudinary upload
        val upload = uploader.uploadPdf(bytes).getOrElse {
            return KResult.failure(it)
        }

        // 2. Firestore qpapers doc
        val paperDoc = papersCol.document() // server-generated ID
        val now = System.currentTimeMillis()
        val paperData = mapOf(
            "department" to intent.department,
            "subjectCode" to intent.subjectCode,
            "subjectName" to intent.subjectName,
            "category" to intent.category.key,
            "examYear" to intent.examYear,
            "semester" to intent.semester,
            "regulation" to intent.regulation,
            "cloudinaryUrl" to upload.secureUrl,
            "cloudinaryPublicId" to upload.publicId,
            "status" to "pending",
            "contributedAt" to now,
            "viewCount" to 0,
        )
        try {
            paperDoc.set(paperData).await()
        } catch (e: Exception) {
            Log.e(TAG, "create qpapers doc failed: ${e.message}", e)
            return KResult.failure(e)
        }

        // 3. Firestore qpapers_contributors doc — same ID for easy join
        val contributorData = mapOf(
            "paperId" to paperDoc.id,
            "uid" to uid,
            "displayName" to displayName,
            "rollNumber" to rollNumber,
            "submittedAt" to now,
            "subjectCode" to intent.subjectCode,
            "category" to intent.category.key,
            "examYear" to intent.examYear,
            "regulation" to intent.regulation,
        )
        try {
            contributorsCol.document(paperDoc.id).set(contributorData).await()
        } catch (e: Exception) {
            // Non-fatal: paper doc exists, admin can still see/approve.
            // User just won't see it in their "My Contributions" list.
            Log.w(TAG, "create contributors doc failed (paper still uploaded): ${e.message}")
        }

        return KResult.success(
            QPaper(
                id = paperDoc.id,
                department = intent.department,
                subjectCode = intent.subjectCode,
                subjectName = intent.subjectName,
                category = intent.category.key,
                examYear = intent.examYear,
                semester = intent.semester,
                regulation = intent.regulation,
                cloudinaryUrl = upload.secureUrl,
                cloudinaryPublicId = upload.publicId,
                status = "pending",
                contributedAt = now,
                viewCount = 0,
            )
        )
    }

    /** Fire-and-forget atomic view counter bump. UI shouldn't block on this. */
    suspend fun incrementViewCount(paperId: String) {
        try {
            papersCol.document(paperId)
                .update("viewCount", FieldValue.increment(1))
                .await()
        } catch (e: Exception) {
            // Counters are best-effort, don't surface to UI
            Log.d(TAG, "viewCount increment failed: ${e.message}")
        }
    }

    // ─────────────────────── admin ─────────────────────────────────────

    /** Admin queue. Rules enforce admin gate. */
    suspend fun listPending(): List<QPaper> {
        return try {
            val snap = papersCol
                .whereEqualTo("status", "pending")
                .orderBy("contributedAt", Query.Direction.ASCENDING)
                .get().await()
            snap.documents.mapNotNull { d ->
                d.toObject(QPaper::class.java)?.copy(id = d.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "listPending err: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Admin history — every paper the admin has already acted on, newest
     * first.
     *
     * Sorting happens in memory (not via Firestore `orderBy`) because legacy
     * docs whose status was flipped directly in the Firestore Console don't
     * have an `approvedAt` field, and `orderBy("approvedAt")` would silently
     * drop them from the result set. Falls back to `contributedAt` when the
     * processed timestamp is missing so old docs still show up at the bottom.
     *
     * Two parallel queries instead of `whereIn(status, ["approved",
     * "rejected"])` to avoid an extra composite index.
     */
    suspend fun listProcessed(limit: Long = 100): List<QPaper> {
        return try {
            val approvedTask = papersCol
                .whereEqualTo("status", "approved")
                .limit(limit)
                .get().await()
            val rejectedTask = papersCol
                .whereEqualTo("status", "rejected")
                .limit(limit)
                .get().await()
            val approved = approvedTask.documents.mapNotNull { d ->
                d.toObject(QPaper::class.java)?.copy(id = d.id)
            }
            val rejected = rejectedTask.documents.mapNotNull { d ->
                d.toObject(QPaper::class.java)?.copy(id = d.id)
            }
            (approved + rejected)
                .sortedByDescending { it.approvedAt ?: it.contributedAt }
                .take(limit.toInt())
        } catch (e: Exception) {
            Log.e(TAG, "listProcessed err: ${e.message}", e)
            emptyList()
        }
    }

    /** Admin: get contributor identity for verification UI. */
    suspend fun getContributor(paperId: String): QPaperContributor? {
        return try {
            val d = contributorsCol.document(paperId).get().await()
            if (!d.exists()) null
            else d.toObject(QPaperContributor::class.java)?.copy(paperId = d.id)
        } catch (e: Exception) {
            Log.e(TAG, "getContributor err: ${e.message}", e)
            null
        }
    }

    suspend fun approve(paperId: String): KResult<Unit> {
        val uid = auth.currentUser?.uid
            ?: return KResult.failure(IllegalStateException("Not signed in"))
        return try {
            papersCol.document(paperId).update(
                mapOf(
                    "status" to "approved",
                    "approvedAt" to System.currentTimeMillis(),
                    "approvedBy" to uid,
                )
            ).await()
            KResult.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "approve err: ${e.message}", e)
            KResult.failure(e)
        }
    }

    /**
     * Approve-and-place: re-home the SAME pending doc into the admin-chosen
     * slot (department/subject/category/year) and flip it to "approved" in
     * one write, so the paper goes live exactly where it belongs and the
     * queue clears — no orphan/duplicate doc, contributor keeps their credit.
     *
     * If [editedBytes] is non-null the admin edited the PDF after downloading
     * it; we upload the new asset and swap cloudinaryUrl/publicId too.
     *
     * The contributor doc is mirrored with decision="approved" (best-effort)
     * so "My Contributions" reflects the outcome.
     */
    suspend fun approveIntoSlot(
        paperId: String,
        intent: UploadIntent,
        editedBytes: ByteArray? = null,
    ): KResult<Unit> {
        val uid = auth.currentUser?.uid
            ?: return KResult.failure(IllegalStateException("Not signed in"))
        val now = System.currentTimeMillis()

        val swap: Pair<String, String>? = if (editedBytes != null) {
            val up = uploader.uploadPdf(editedBytes).getOrElse { return KResult.failure(it) }
            up.secureUrl to up.publicId
        } else null

        return try {
            val updates = mutableMapOf<String, Any?>(
                "department" to intent.department,
                "subjectCode" to intent.subjectCode,
                "subjectName" to intent.subjectName,
                "semester" to intent.semester,
                "regulation" to intent.regulation,
                "category" to intent.category.key,
                "examYear" to intent.examYear,
                "status" to "approved",
                "approvedAt" to now,
                "approvedBy" to uid,
            )
            swap?.let {
                updates["cloudinaryUrl"] = it.first
                updates["cloudinaryPublicId"] = it.second
                updates["replacedAt"] = now
            }
            papersCol.document(paperId).update(updates).await()
            // Mirror onto the contributor-readable doc (best-effort).
            runCatching {
                contributorsCol.document(paperId)
                    .update(mapOf("decision" to "approved", "decidedAt" to now))
                    .await()
            }
            KResult.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "approveIntoSlot err: ${e.message}", e)
            KResult.failure(e)
        }
    }

    /**
     * Decline a pending paper with a reason. Writes status="rejected" plus
     * the decline kind + human-readable reason on the qpapers doc (admin-
     * readable), and mirrors decision="declined" + reason onto the
     * contributor doc so the uploader sees the feedback in "My Contributions".
     */
    suspend fun decline(paperId: String, kind: DeclineKind, reason: String): KResult<Unit> {
        val uid = auth.currentUser?.uid
            ?: return KResult.failure(IllegalStateException("Not signed in"))
        val now = System.currentTimeMillis()
        return try {
            papersCol.document(paperId).update(
                mapOf(
                    "status" to "rejected",
                    "approvedAt" to now,
                    "approvedBy" to uid,
                    "declineKind" to kind.key,
                    "declineReason" to reason,
                )
            ).await()
            runCatching {
                contributorsCol.document(paperId).update(
                    mapOf(
                        "decision" to "declined",
                        "declineKind" to kind.key,
                        "declineReason" to reason,
                        "decidedAt" to now,
                    )
                ).await()
            }
            KResult.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "decline err: ${e.message}", e)
            KResult.failure(e)
        }
    }

    /**
     * Admin: replace the underlying Cloudinary PDF and approve in one shot.
     *
     * Used when the contributor's file was almost-right but needed a tweak
     * (rotated page, cover sheet, redacted name, etc.) — the admin edits
     * the PDF externally, picks the fixed file, and we swap the asset.
     *
     * Implementation notes:
     *   - Cloudinary unsigned presets can't reliably overwrite an existing
     *     public_id (depends on preset config we don't fully control), so
     *     we always upload as a NEW asset. The Firestore doc swaps to the
     *     new URL + publicId; the old Cloudinary file is orphaned and the
     *     admin can clean it up from the Cloudinary dashboard later.
     *   - Status flips to "approved" in the same write so we don't need a
     *     separate Approve tap. Sets approvedBy/approvedAt + replacedAt so
     *     audit logs show that this paper went through an admin edit.
     *   - The contributor doc is left untouched — the contributor is still
     *     credited for the original submission. Replacing the bytes doesn't
     *     change who contributed.
     */
    suspend fun replaceAndApprove(paperId: String, bytes: ByteArray): KResult<Unit> {
        val uid = auth.currentUser?.uid
            ?: return KResult.failure(IllegalStateException("Not signed in"))

        val upload = uploader.uploadPdf(bytes).getOrElse {
            return KResult.failure(it)
        }

        val now = System.currentTimeMillis()
        return try {
            papersCol.document(paperId).update(
                mapOf(
                    "cloudinaryUrl" to upload.secureUrl,
                    "cloudinaryPublicId" to upload.publicId,
                    "status" to "approved",
                    "approvedAt" to now,
                    "approvedBy" to uid,
                    "replacedAt" to now,
                )
            ).await()
            KResult.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "replaceAndApprove err: ${e.message}", e)
            KResult.failure(e)
        }
    }

    /**
     * Admin direct upload — bypasses the pending queue. Used by the
     * "Re-upload elsewhere" flow where the admin is moving (or cloning) an
     * already-verified paper to a different slot. Status is "approved" from
     * the start so it's immediately browseable by students.
     *
     * Contributor doc is intentionally NOT written here — the original
     * contributor still owns the source paper; this is an admin action,
     * not a new student contribution.
     */
    suspend fun uploadApproved(bytes: ByteArray, intent: UploadIntent): KResult<QPaper> {
        val uid = auth.currentUser?.uid
            ?: runCatching { auth.signInAnonymously().await().user?.uid }.getOrNull()
            ?: return KResult.failure(IllegalStateException("Not signed in"))

        val upload = uploader.uploadPdf(bytes).getOrElse {
            return KResult.failure(it)
        }

        val now = System.currentTimeMillis()
        val paperRef = papersCol.document()
        val paper = QPaper(
            department = intent.department,
            subjectCode = intent.subjectCode,
            subjectName = intent.subjectName,
            category = intent.category.key,
            examYear = intent.examYear,
            semester = intent.semester,
            regulation = intent.regulation,
            cloudinaryUrl = upload.secureUrl,
            cloudinaryPublicId = upload.publicId,
            status = "approved",
            contributedAt = now,
            approvedAt = now,
            approvedBy = uid,
        )
        return try {
            paperRef.set(paper).await()
            KResult.success(paper.copy(id = paperRef.id))
        } catch (e: Exception) {
            Log.e(TAG, "uploadApproved err: ${e.message}", e)
            KResult.failure(e)
        }
    }

    suspend fun reject(paperId: String): KResult<Unit> {
        val uid = auth.currentUser?.uid
            ?: return KResult.failure(IllegalStateException("Not signed in"))
        return try {
            papersCol.document(paperId).update(
                mapOf(
                    "status" to "rejected",
                    "approvedAt" to System.currentTimeMillis(),
                    "approvedBy" to uid,
                )
            ).await()
            // Note: Cloudinary file is NOT deleted here (would need API
            // secret). Admin can clean up via the Cloudinary dashboard if
            // storage starts to fill up. Rejected papers are invisible to
            // the app regardless.
            KResult.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "reject err: ${e.message}", e)
            KResult.failure(e)
        }
    }

    companion object {
        private const val TAG = "QPaperRepository"

        @Volatile private var instance: QPaperRepository? = null

        fun getInstance(context: Context): QPaperRepository {
            return instance ?: synchronized(this) {
                instance ?: QPaperRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
