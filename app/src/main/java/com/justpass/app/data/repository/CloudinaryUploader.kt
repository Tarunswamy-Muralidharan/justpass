package com.justpass.app.data.repository

import android.util.Log
import com.google.gson.JsonParser
import com.justpass.app.BuildConfig
import com.justpass.app.data.model.CloudinaryUploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.Result as KResult

/**
 * Unsigned uploader for Cloudinary "raw" assets (used for PDF question
 * papers). Uses an unsigned upload preset configured in the Cloudinary
 * dashboard — see [BuildConfig.CLOUDINARY_UPLOAD_PRESET]. The preset
 * carries the security restrictions (allowed format=pdf, max size 10 MB,
 * folder=qpapers); the app only needs the cloud name + preset name to
 * complete an upload.
 *
 * No API secret is bundled — the unsigned preset is the entire auth
 * surface. Worst-case abuse (someone scrapes the preset name from the
 * APK and uploads garbage) is bounded by the preset restrictions and
 * Cloudinary's free-tier storage ceiling; admin verification on the app
 * side prevents abuse uploads from ever surfacing to readers.
 */
class CloudinaryUploader {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS) // PDFs can be a few MB
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Returns true if Cloudinary config is present. If false, the feature
     * should be disabled in the UI rather than crashing on upload.
     */
    val isConfigured: Boolean
        get() = BuildConfig.CLOUDINARY_CLOUD_NAME.isNotBlank() &&
            BuildConfig.CLOUDINARY_UPLOAD_PRESET.isNotBlank()

    /**
     * Upload a PDF byte array as a raw asset. The cloudinary public_id
     * is server-generated; we don't pass one so two simultaneous uploads
     * can never collide on the same name.
     */
    suspend fun uploadPdf(bytes: ByteArray): KResult<CloudinaryUploadResult> =
        withContext(Dispatchers.IO) {
            if (!isConfigured) {
                return@withContext KResult.failure(
                    IllegalStateException("Cloudinary not configured — set CLOUDINARY_CLOUD_NAME + CLOUDINARY_UPLOAD_PRESET in local.properties")
                )
            }

            val url = "https://api.cloudinary.com/v1_1/" +
                "${BuildConfig.CLOUDINARY_CLOUD_NAME}/raw/upload"

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file", "paper.pdf",
                    bytes.toRequestBody("application/pdf".toMediaType())
                )
                .addFormDataPart("upload_preset", BuildConfig.CLOUDINARY_UPLOAD_PRESET)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Log.e(TAG, "upload failed code=${response.code} body=$rawBody")
                        return@withContext KResult.failure(
                            RuntimeException("Cloudinary upload failed: ${response.code}")
                        )
                    }
                    val json = JsonParser.parseString(rawBody).asJsonObject
                    KResult.success(
                        CloudinaryUploadResult(
                            secureUrl = json.get("secure_url").asString,
                            publicId = json.get("public_id").asString,
                            bytes = json.get("bytes").asLong,
                            format = json.get("format")?.asString ?: "pdf",
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "upload exception: ${e.message}", e)
                KResult.failure(e)
            }
        }

    /**
     * Download PDF bytes from Cloudinary by public URL. Used by the
     * in-app viewer. Streams the response straight into memory because
     * we never want the file on disk — defeats the in-app-only goal.
     */
    suspend fun downloadPdf(secureUrl: String): KResult<ByteArray> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(secureUrl).get().build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext KResult.failure(
                            RuntimeException("Cloudinary download failed: ${response.code}")
                        )
                    }
                    val bytes = response.body?.bytes() ?: byteArrayOf()
                    KResult.success(bytes)
                }
            } catch (e: Exception) {
                Log.e(TAG, "download exception: ${e.message}", e)
                KResult.failure(e)
            }
        }

    companion object {
        private const val TAG = "CloudinaryUploader"
    }
}
