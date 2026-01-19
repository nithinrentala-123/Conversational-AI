package com.niquewrld.conversationalai.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.niquewrld.conversationalai.CancelDownloadReceiver
import com.niquewrld.conversationalai.DownloadManager
import com.niquewrld.conversationalai.MainActivity
import com.niquewrld.conversationalai.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloadService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val CHANNEL_ID_START = "download_start_channel"
        private const val CHANNEL_NAME = "Model Downloads"
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_ID_START = 1002
        private const val MODELS_FOLDER = "NiqueWrld/models"
        
        /**
         * Get the models directory in Documents folder
         * Location: /storage/emulated/0/Documents/NiqueWrld/models/
         * This allows multiple NiqueWrld apps to share the same models
         */
        fun getModelsDir(context: Context): File {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val modelsDir = File(documentsDir, MODELS_FOLDER)
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }
            return modelsDir
        }
        
        /**
         * Check if a model file exists
         */
        fun isModelDownloaded(context: Context, filename: String): Boolean {
            val modelsDir = getModelsDir(context)
            val modelFile = File(modelsDir, filename)
            return modelFile.exists()
        }
        
        /**
         * Get the path to a model file
         */
        fun getModelPath(context: Context, filename: String): String {
            return File(getModelsDir(context), filename).absolutePath
        }
    }

    private var currentModelName: String = ""
    private var currentModelFilename: String = ""
    private var currentModelUrl: String = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = createNotification("Preparing...", 0, "Downloading Model")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent == null) return START_REDELIVER_INTENT

        val modelName = intent.getStringExtra("modelName") ?: "Model"
        val modelUrl = intent.getStringExtra("modelUrl") ?: return START_REDELIVER_INTENT
        val modelFilename = intent.getStringExtra("modelFilename") ?: return START_REDELIVER_INTENT
        
        currentModelName = modelName
        currentModelFilename = modelFilename
        currentModelUrl = modelUrl

        DownloadManager.isCancelled = false
        DownloadManager.isPaused = false
        
        // Show heads-up notification that download started
        showDownloadStartedNotification(modelName)

        // Use external storage - models persist after app uninstall
        val modelsDir = getModelsDir(applicationContext)
        val targetFile = File(modelsDir, modelFilename)
        val partFile = File(modelsDir, "$modelFilename.part")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                downloadModelWithResume(modelUrl, partFile, targetFile, modelName) { progress, speed, downloaded, total ->
                    updateNotification(progress, modelName, speed, downloaded, total)
                }
                if (!DownloadManager.isCancelled && !DownloadManager.isPaused) {
                    onDownloadComplete(true, modelName)
                } else if (DownloadManager.isPaused) {
                    onDownloadPaused(modelName)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onDownloadComplete(false, modelName, e.message)
            }
        }

        return START_REDELIVER_INTENT
    }

    private fun downloadModelWithResume(
        url: String,
        partFile: File,
        targetFile: File,
        modelTitle: String,
        onProgress: (Int, String, String, String) -> Unit
    ) {
        var existingBytes = 0L
        if (partFile.exists()) {
            existingBytes = partFile.length()
        }

        val connection = URL(url).openConnection() as HttpURLConnection
        
        // Request resume from existing position
        if (existingBytes > 0) {
            connection.setRequestProperty("Range", "bytes=$existingBytes-")
        }
        
        connection.connect()
        
        val responseCode = connection.responseCode
        val contentLength: Long
        val shouldAppend: Boolean
        
        when (responseCode) {
            HttpURLConnection.HTTP_PARTIAL -> {
                // Server supports resume
                contentLength = existingBytes + connection.contentLengthLong
                shouldAppend = true
            }
            HttpURLConnection.HTTP_OK -> {
                // Server doesn't support resume or fresh download
                contentLength = connection.contentLengthLong
                existingBytes = 0
                shouldAppend = false
                if (partFile.exists()) {
                    partFile.delete()
                }
            }
            else -> {
                throw Exception("Server returned HTTP $responseCode")
            }
        }

        val inputStream = connection.inputStream
        val outputStream = if (shouldAppend) {
            FileOutputStream(partFile, true) // Append mode
        } else {
            FileOutputStream(partFile)
        }
        
        val buffer = ByteArray(8192)
        var totalBytesRead = existingBytes
        var lastUpdateTime = System.currentTimeMillis()
        var bytesAtLastUpdate = existingBytes
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            if (DownloadManager.isCancelled) {
                inputStream.close()
                outputStream.close()
                partFile.delete()
                return
            }
            
            if (DownloadManager.isPaused) {
                inputStream.close()
                outputStream.close()
                connection.disconnect()
                return
            }

            outputStream.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead

            val progress = if (contentLength > 0) ((totalBytesRead * 100) / contentLength).toInt() else 0
            val currentTime = System.currentTimeMillis()
            val timeDiff = currentTime - lastUpdateTime

            if (timeDiff >= 500) { // Update more frequently
                val bytesDiff = totalBytesRead - bytesAtLastUpdate
                val speed = formatSpeed(bytesDiff, timeDiff)
                val downloaded = formatSize(totalBytesRead)
                val total = formatSize(contentLength)
                onProgress(progress, speed, downloaded, total)
                lastUpdateTime = currentTime
                bytesAtLastUpdate = totalBytesRead
            }
        }

        outputStream.close()
        inputStream.close()
        connection.disconnect()
        
        // Rename part file to final file
        if (partFile.exists()) {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            partFile.renameTo(targetFile)
        }
    }

    private fun formatSpeed(bytes: Long, timeMs: Long): String {
        if (timeMs == 0L) return "0 B/s"
        val bps = (bytes * 1000) / timeMs
        return when {
            bps >= 1_000_000 -> String.format("%.1f MB/s", bps / 1_000_000.0)
            bps >= 1_000 -> String.format("%.1f KB/s", bps / 1_000.0)
            else -> "$bps B/s"
        }
    }
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
    
    private fun showDownloadStartedNotification(modelName: String) {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(this, 1, contentIntent, PendingIntent.FLAG_IMMUTABLE)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_START)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Downloading $modelName")
            .setContentText("Starting download...")
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setAutoCancel(true)
            .setTimeoutAfter(3000) // Auto dismiss after 3 seconds
            .build()
        
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID_START, notification)
    }

    private fun updateNotification(progress: Int, modelTitle: String, speed: String, downloaded: String, total: String) {
        val notification = createNotification("$speed • $downloaded / $total", progress, modelTitle)
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun onDownloadComplete(success: Boolean, modelName: String, errorMsg: String? = null) {
        val nm = getSystemService(NotificationManager::class.java)
        
        if (success) {
            // Show completion notification
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle("Download Complete")
                .setContentText("$modelName is ready to use")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFICATION_ID + 1, notification)
            
            // Broadcast download complete
            val intent = Intent("com.niquewrld.conversationalai.DOWNLOAD_COMPLETE").apply {
                putExtra("modelName", modelName)
                putExtra("success", true)
            }
            sendBroadcast(intent)
        } else {
            // Show failure notification with retry option
            val retryIntent = Intent(this, ModelDownloadService::class.java).apply {
                putExtra("modelName", currentModelName)
                putExtra("modelFilename", currentModelFilename)
            }
            val retryPendingIntent = PendingIntent.getService(this, 2, retryIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle("Download Failed")
                .setContentText(errorMsg ?: "Failed to download $modelName")
                .addAction(R.drawable.ic_download, "Retry", retryPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFICATION_ID + 1, notification)
        }
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun onDownloadPaused(modelName: String) {
        val nm = getSystemService(NotificationManager::class.java)
        
        // Show paused notification with resume option
        val resumeIntent = Intent(this, CancelDownloadReceiver::class.java).apply { 
            action = "RESUME_DOWNLOAD"
            putExtra("modelName", currentModelName)
            putExtra("modelFilename", currentModelFilename)
        }
        val resumePendingIntent = PendingIntent.getBroadcast(this, 3, resumeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        
        val cancelIntent = Intent(this, CancelDownloadReceiver::class.java).apply { action = "CANCEL_DOWNLOAD" }
        val cancelPendingIntent = PendingIntent.getBroadcast(this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Download Paused")
            .setContentText("$modelName - Tap Resume to continue")
            .addAction(R.drawable.ic_download, "Resume", resumePendingIntent)
            .addAction(R.drawable.ic_download, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
        
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun createNotificationChannel() {
        // Download progress channel - low importance (no sound/vibration)
        val progressChannel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows download progress for AI models"
            setShowBadge(false)
        }
        
        // Download started channel - high importance for heads-up notification
        val startChannel = NotificationChannel(CHANNEL_ID_START, "Download Started", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notifies when a download starts"
            setShowBadge(true)
        }
        
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(progressChannel)
        nm?.createNotificationChannel(startChannel)
    }

    @SuppressLint("RemoteViewLayout")
    private fun createNotification(contentText: String, progress: Int, modelTitle: String): Notification {
        val cancelIntent = Intent(this, CancelDownloadReceiver::class.java).apply { action = "CANCEL_DOWNLOAD" }
        val cancelPendingIntent = PendingIntent.getBroadcast(this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE)
        
        val pauseIntent = Intent(this, CancelDownloadReceiver::class.java).apply { action = "PAUSE_DOWNLOAD" }
        val pausePendingIntent = PendingIntent.getBroadcast(this, 4, pauseIntent, PendingIntent.FLAG_IMMUTABLE)

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(this, 1, contentIntent, PendingIntent.FLAG_IMMUTABLE)

        // Collapsed view
        val remoteViews = RemoteViews(packageName, R.layout.notification_download).apply {
            setTextViewText(R.id.notification_title, "Downloading $modelTitle")
            setTextViewText(R.id.notification_progress_text, "$progress% • $contentText")
            setProgressBar(R.id.notification_progress_bar, 100, progress, false)
            setOnClickPendingIntent(R.id.notification_cancel_button, cancelPendingIntent)
        }
        
        // Expanded view (same layout, reuse it)
        val expandedViews = RemoteViews(packageName, R.layout.notification_download).apply {
            setTextViewText(R.id.notification_title, "Downloading $modelTitle")
            setTextViewText(R.id.notification_progress_text, "$progress% • $contentText")
            setProgressBar(R.id.notification_progress_bar, 100, progress, false)
            setOnClickPendingIntent(R.id.notification_cancel_button, cancelPendingIntent)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(expandedViews)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_download, "Pause", pausePendingIntent)
            .addAction(R.drawable.ic_download, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .build()
    }
}
