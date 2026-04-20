package com.justpass.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.justpass.app.MainActivity
import com.justpass.app.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class DownloadProgress(
    val isDownloading: Boolean = false,
    val progress: Int = 0, // 0-100
    val downloadedMb: Long = 0,
    val totalMb: Long = 0,
    val speedKbps: Long = 0,
    val etaSeconds: Long = 0,
    val error: String? = null,
    val completed: Boolean = false
)

class ModelDownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "model_download_channel"
        private const val NOTIFICATION_ID = 3001

        private const val EXTRA_URL = "download_url"
        private const val EXTRA_FILE_PATH = "file_path"
        private const val EXTRA_MODEL_NAME = "model_name"
        private const val ACTION_CANCEL = "cancel_download"

        private val _downloadState = MutableStateFlow(DownloadProgress())
        val downloadState: StateFlow<DownloadProgress> = _downloadState.asStateFlow()

        /** Check if a download is currently in progress */
        val isDownloading: Boolean get() = _downloadState.value.isDownloading

        fun start(context: Context, url: String, filePath: String, modelName: String) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILE_PATH, filePath)
                putExtra(EXTRA_MODEL_NAME, modelName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Cancel a running download */
        fun cancel(context: Context) {
            _downloadState.value = _downloadState.value.copy(isDownloading = false)
            context.stopService(Intent(context, ModelDownloadService::class.java))
        }

        /** Reset state after ViewModel has consumed the completion */
        fun resetState() {
            _downloadState.value = DownloadProgress()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle cancel action from notification
        if (intent?.action == ACTION_CANCEL) {
            _downloadState.value = _downloadState.value.copy(isDownloading = false)
            return START_NOT_STICKY
        }

        val url = intent?.getStringExtra(EXTRA_URL)
        val filePath = intent?.getStringExtra(EXTRA_FILE_PATH)
        val modelName = intent?.getStringExtra(EXTRA_MODEL_NAME) ?: "AI Model"

        if (url == null || filePath == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Don't start if already downloading
        if (_downloadState.value.isDownloading) {
            return START_NOT_STICKY
        }

        val notification = buildNotification(modelName, 0, "Starting download...")
        startForeground(NOTIFICATION_ID, notification)

        _downloadState.value = DownloadProgress(isDownloading = true)

        downloadJob = scope.launch {
            try {
                downloadFile(url, File(filePath), modelName)
                _downloadState.value = _downloadState.value.copy(
                    isDownloading = false,
                    completed = true,
                    progress = 100
                )
                showCompletionNotification(modelName)
            } catch (e: Exception) {
                _downloadState.value = _downloadState.value.copy(
                    isDownloading = false,
                    error = e.message ?: "Download failed"
                )
                showErrorNotification(modelName, e.message ?: "Download failed")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun downloadFile(urlStr: String, target: File, modelName: String) {
        val tempFile = File(target.parent, "${target.name}.tmp")
        target.parentFile?.mkdirs()
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true

        try {
            conn.connect()
            val totalBytes = conn.contentLengthLong
            var downloaded = 0L
            var lastUpdateTime = System.currentTimeMillis()
            var lastUpdateBytes = 0L
            var lastNotificationTime = 0L

            conn.inputStream.buffered().use { input ->
                tempFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(8192)
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        if (!_downloadState.value.isDownloading) {
                            // Cancelled
                            tempFile.delete()
                            return
                        }
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        val now = System.currentTimeMillis()
                        val elapsed = now - lastUpdateTime
                        if (totalBytes > 0 && elapsed >= 500) {
                            val pct = ((downloaded * 100) / totalBytes).toInt()
                            val bytesInInterval = downloaded - lastUpdateBytes
                            val speedBps = if (elapsed > 0) bytesInInterval * 1000 / elapsed else 0
                            val remaining = totalBytes - downloaded
                            val eta = if (speedBps > 0) remaining / speedBps else 0

                            _downloadState.value = DownloadProgress(
                                isDownloading = true,
                                progress = pct,
                                downloadedMb = downloaded / (1024 * 1024),
                                totalMb = totalBytes / (1024 * 1024),
                                speedKbps = speedBps / 1024,
                                etaSeconds = eta
                            )

                            // Update notification at most every 2 seconds to avoid throttling
                            if (now - lastNotificationTime >= 2000) {
                                val speedText = if (speedBps / 1024 >= 1024)
                                    "${"%.1f".format(speedBps / 1024f / 1024f)} MB/s"
                                else "${speedBps / 1024} KB/s"
                                val etaText = when {
                                    eta >= 3600 -> "${eta / 3600}h ${(eta % 3600) / 60}m left"
                                    eta >= 60 -> "${eta / 60}m ${eta % 60}s left"
                                    eta > 0 -> "${eta}s left"
                                    else -> ""
                                }
                                updateNotification(modelName, pct, "$speedText • $etaText")
                                lastNotificationTime = now
                            }

                            lastUpdateTime = now
                            lastUpdateBytes = downloaded
                        }
                    }
                }
            }
            tempFile.renameTo(target)
        } finally {
            conn.disconnect()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while downloading AI models"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        modelName: String,
        progress: Int,
        subText: String
    ): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "ai_chat")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, ModelDownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Downloading $modelName")
            .setContentText(subText)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(modelName: String, progress: Int, subText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(modelName, progress, subText))
    }

    private fun showCompletionNotification(modelName: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "ai_chat")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$modelName ready")
            .setContentText("Download complete. Tap to start chatting.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        nm.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showErrorNotification(modelName: String, error: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$modelName download failed")
            .setContentText(error)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID + 1, notification)
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
