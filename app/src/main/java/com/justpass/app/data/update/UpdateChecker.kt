package com.justpass.app.data.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String,
    @SerializedName("body") val body: String?,
    @SerializedName("assets") val assets: List<GitHubAsset>
)

data class GitHubAsset(
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("name") val name: String
)

data class UpdateInfo(
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String?,
    val downloadUrl: String
)

object UpdateChecker {

    private const val GITHUB_API_URL =
        "https://api.github.com/repos/Tarunswamy-Muralidharan/-AttendanceWidgetLaudea/releases/latest"

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL(GITHUB_API_URL)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == 200) {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    val release = Gson().fromJson(json, GitHubRelease::class.java)

                    val latestVersion = release.tagName.removePrefix("v")
                    if (isNewerVersion(latestVersion, currentVersion)) {
                        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                        if (apkAsset != null) {
                            UpdateInfo(
                                versionName = latestVersion,
                                releaseName = release.name,
                                releaseNotes = release.body,
                                downloadUrl = apkAsset.downloadUrl
                            )
                        } else null
                    } else null
                } else null
            } catch (e: Exception) {
                android.util.Log.e("UpdateChecker", "Update check failed: ${e.message}")
                null
            }
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
