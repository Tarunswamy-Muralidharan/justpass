package com.justpass.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.data.model.ClassMarksUploadBody
import com.justpass.app.data.model.ClassStatsResponse
import com.justpass.app.data.model.ClassSubjectMark
import com.justpass.app.data.model.CourseMarks
import com.justpass.app.data.model.detectDepartment
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Class marks comparison — Android client side.
 *
 * Reads `cachedCourseMarks` (already fetched by AttendanceRepository, no
 * extra SIS call), translates to the Worker upload shape, computes a
 * payload hash, skips the upload if unchanged since last cycle. All
 * routes auth'd with a Firebase anonymous ID token (same scheme chess
 * uses).
 *
 * Server URLs share the existing chess-lobby Worker.
 */
class ClassMarksRepository private constructor(private val context: Context) {

    private val securePrefs = SecurePreferences.getInstance(context)
    private val gson = Gson()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Resolve the class key from cached biodata. Returns null if any piece
     * is missing — caller should skip the upload (we won't know which
     * bucket the user belongs in).
     */
    fun resolveClassKey(): String? {
        val batchYear = securePrefs.batchYear.takeIf { it > 0 } ?: return null
        val rawDept = securePrefs.cachedDepartment ?: securePrefs.programmeName ?: return null
        val deptShort = detectDepartment(rawDept)?.name ?: return null
        val section = (securePrefs.cachedSection ?: return null).trim().uppercase()
        if (section.isEmpty()) return null
        val sem = securePrefs.cachedCurrentSem.takeIf { it > 0 } ?: return null
        return "${batchYear}_${deptShort}_${section}_${sem}"
    }

    /**
     * Translate the SIS-shaped CA marks list into the simpler upload body.
     * Only subjects with a numeric total (and non-NOT_ENTERED status) make
     * it through. overallAvg = mean of `total / max * 100` across those.
     */
    fun buildUploadBody(courseMarks: List<CourseMarks>, classKey: String): ClassMarksUploadBody? {
        val subjects = mutableMapOf<String, ClassSubjectMark>()
        val percents = mutableListOf<Double>()
        for (course in courseMarks) {
            val totalMarks = course.testDetails.total
            val totalSecured = totalMarks.actual.getSecuredAsDouble()
            val totalMax = totalMarks.actual.getMaxAsDouble()
            if (totalSecured == null || totalMax <= 0.0) continue
            if (totalMarks.actual.isNotEntered()) continue

            // Pluck CA1/CA2/CA3 if present by name. Components vary across
            // depts; this is best-effort, not authoritative — server only
            // uses `total` for stats.
            val components = course.testDetails.components
            val ca1 = components.firstOrNull { it.name.contains("CA1", true) }
                ?.marks?.actual?.getSecuredAsDouble()
            val ca2 = components.firstOrNull { it.name.contains("CA2", true) }
                ?.marks?.actual?.getSecuredAsDouble()
            val ca3 = components.firstOrNull { it.name.contains("CA3", true) }
                ?.marks?.actual?.getSecuredAsDouble()

            subjects[course.courseCode] = ClassSubjectMark(
                ca1 = ca1,
                ca2 = ca2,
                ca3 = ca3,
                total = totalSecured,
                status = if (totalMarks.actual.isNotEntered()) "NOT_ENTERED" else "ENTERED",
            )
            // Percentage on the 0..100 scale for ranking. Some subjects have
            // max>100 (laboratory continuous assessments e.g.); we normalise.
            percents += (totalSecured / totalMax) * 100.0
        }
        if (subjects.isEmpty() || percents.isEmpty()) return null
        val overallAvg = percents.average()
        return ClassMarksUploadBody(
            classKey = classKey,
            subjects = subjects,
            overallAvg = overallAvg,
        )
    }

    /**
     * Hash payload (excluding classKey since classKey changes alone don't
     * mean the marks data changed — but we WANT a fresh upload on classKey
     * change, so include it). Used to dedup repeated identical uploads.
     */
    fun hashPayload(body: ClassMarksUploadBody): String {
        val json = gson.toJson(body)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(json.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Upload one body. Returns true on success. Skips if hash matches last
     * upload (no D1 write performed).
     */
    suspend fun uploadIfChanged(body: ClassMarksUploadBody): Boolean {
        val hash = hashPayload(body)
        if (hash == securePrefs.lastUploadedMarksHash) {
            Log.d(TAG, "Class marks unchanged, skip upload")
            return true
        }
        val token = fetchFirebaseIdToken() ?: run {
            Log.w(TAG, "No Firebase token, skip upload")
            return false
        }
        val request = Request.Builder()
            .url("$BASE_URL/class/marks")
            .header("Authorization", "Bearer $token")
            .post(gson.toJson(body).toRequestBody(JSON_MEDIA))
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    securePrefs.lastUploadedMarksHash = hash
                    Log.d(TAG, "Class marks uploaded, hash=$hash")
                    true
                } else {
                    Log.w(TAG, "Upload failed: ${resp.code} ${resp.body?.string()}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Upload exception: ${e.message}")
            false
        }
    }

    /**
     * Lightweight visibility probe used by CAMarksScreen. Hits the same
     * endpoint as [fetchClassStats] but only looks at studentCount, and
     * flips [SecurePreferences.classCompareUnlocked] on once the class
     * has crossed the anonymity floor. The Compare entry icon reads that
     * pref to decide whether to render — keeps under-populated classes
     * from ever seeing a "need N more classmates" placeholder.
     */
    suspend fun probeClassUnlocked(): Boolean {
        if (securePrefs.classCompareUnlocked) return true
        val classKey = resolveClassKey() ?: return false
        val stats = fetchClassStats(classKey) ?: return false
        return if (stats.studentCount >= 15) {
            securePrefs.classCompareUnlocked = true
            true
        } else false
    }

    /**
     * Fetch class stats for the given key. Null on any error.
     */
    suspend fun fetchClassStats(classKey: String): ClassStatsResponse? {
        val token = fetchFirebaseIdToken() ?: return null
        val request = Request.Builder()
            .url("$BASE_URL/class/${java.net.URLEncoder.encode(classKey, "UTF-8")}")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Fetch class stats failed: ${resp.code}")
                    return null
                }
                val raw = resp.body?.string() ?: return null
                gson.fromJson(raw, ClassStatsResponse::class.java)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch class stats exception: ${e.message}")
            null
        }
    }

    /**
     * Delete my row on the Worker. Returns true on success.
     */
    suspend fun deleteMyData(): Boolean {
        val token = fetchFirebaseIdToken() ?: return false
        val request = Request.Builder()
            .url("$BASE_URL/class/me")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    securePrefs.lastUploadedMarksHash = null
                    true
                } else false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Delete exception: ${e.message}")
            false
        }
    }

    /**
     * Pull a fresh Firebase ID token. Signs in anonymously if no user
     * exists (mirrors the chess-v2 helper pattern).
     */
    private suspend fun fetchFirebaseIdToken(): String? {
        return try {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser ?: auth.signInAnonymously().await().user ?: return null
            user.getIdToken(false).await().token
        } catch (e: Exception) {
            Log.w(TAG, "fetchFirebaseIdToken failed: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "ClassMarksRepo"
        private const val BASE_URL = "https://chess-lobby.tmswamy10.workers.dev"
        private val JSON_MEDIA = "application/json".toMediaType()

        @Volatile
        private var instance: ClassMarksRepository? = null

        fun getInstance(context: Context): ClassMarksRepository {
            return instance ?: synchronized(this) {
                instance ?: ClassMarksRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
