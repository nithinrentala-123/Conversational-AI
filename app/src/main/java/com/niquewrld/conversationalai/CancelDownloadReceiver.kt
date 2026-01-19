package com.niquewrld.conversationalai

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.niquewrld.conversationalai.service.ModelDownloadService

class CancelDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        try {
            when (intent?.action) {
                ACTION_CANCEL_DOWNLOAD -> {
                    handleCancelDownload(context)
                }
                ACTION_PAUSE_DOWNLOAD -> {
                    handlePauseDownload(context)
                }
                ACTION_RESUME_DOWNLOAD -> {
                    handleResumeDownload(context, intent)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun handleCancelDownload(context: Context) {
        // Cancel the active download
        DownloadManager.isCancelled = true
        DownloadManager.isPaused = false
        DownloadManager.currentRequest?.cancel()
        DownloadManager.currentRequest = null
        DownloadManager.resetProgress()
        
        // Stop the download service
        val serviceIntent = Intent(context, ModelDownloadService::class.java)
        context.stopService(serviceIntent)
        
        // Cancel notifications
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        notificationManager.cancel(NOTIFICATION_ID + 1)
    }
    
    private fun handlePauseDownload(context: Context) {
        // Set paused flag - download loop will exit gracefully
        DownloadManager.isPaused = true
        DownloadManager.isCancelled = false
    }
    
    private fun handleResumeDownload(context: Context, intent: Intent) {
        // Clear flags
        DownloadManager.isPaused = false
        DownloadManager.isCancelled = false
        
        // Get model info from intent or stored values
        val modelName = intent.getStringExtra("modelName") ?: DownloadManager.lastModelName
        val modelFilename = intent.getStringExtra("modelFilename") ?: DownloadManager.lastModelFilename
        val modelUrl = intent.getStringExtra("modelUrl") ?: DownloadManager.lastModelUrl
        
        if (modelName != null && modelFilename != null && modelUrl != null) {
            // Cancel the paused notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
            
            // Restart the download service - it will resume from where it left off
            val serviceIntent = Intent(context, ModelDownloadService::class.java).apply {
                putExtra("modelName", modelName)
                putExtra("modelUrl", modelUrl)
                putExtra("modelFilename", modelFilename)
            }
            context.startForegroundService(serviceIntent)
        }
    }

    companion object {
        const val ACTION_CANCEL_DOWNLOAD = "CANCEL_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "RESUME_DOWNLOAD"
        const val NOTIFICATION_ID = 1001
    }
}

/**
 * Download manager singleton to track download state
 */
object DownloadManager {
    var currentRequest: CancellableRequest? = null
    var isCancelled: Boolean = false
    var isPaused: Boolean = false
    
    // Store last download info for resume
    var lastModelName: String? = null
    var lastModelFilename: String? = null
    var lastModelUrl: String? = null
    
    fun resetProgress() {
        // Reset download progress state
    }
    
    fun storeDownloadInfo(name: String, filename: String, url: String) {
        lastModelName = name
        lastModelFilename = filename
        lastModelUrl = url
    }
}

/**
 * Interface for cancellable network requests
 */
interface CancellableRequest {
    fun cancel()
}
