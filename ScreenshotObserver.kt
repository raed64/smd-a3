package com.AppFlix.i220968_i228810.utils

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log

/**
 * Phase 8: Screenshot Detection Observer
 * 
 * Monitors the MediaStore for new screenshots taken by the user.
 * Uses ContentObserver to watch for changes in the Screenshots directory.
 * 
 * Note: This requires READ_MEDIA_IMAGES permission on Android 13+ or
 * READ_EXTERNAL_STORAGE on older versions.
 */
class ScreenshotObserver(
    private val context: Context,
    private val onScreenshotTaken: () -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {
    
    companion object {
        private const val TAG = "ScreenshotObserver"
        
        // MediaStore URI for images
        private val EXTERNAL_CONTENT_URI: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        
        // Projection for querying MediaStore
        private val PROJECTION = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
        )
        
        // Screenshot detection keywords
        private val SCREENSHOT_KEYWORDS = arrayOf(
            "screenshot", "screen_shot", "screen-shot", "screencapture", "screen_capture"
        )
    }
    
    private var lastScreenshotTime = 0L
    private var isObserving = false
    
    /**
     * Start observing for screenshots
     */
    fun startObserving() {
        if (!isObserving) {
            context.contentResolver.registerContentObserver(
                EXTERNAL_CONTENT_URI,
                true,
                this
            )
            isObserving = true
            lastScreenshotTime = System.currentTimeMillis()
            Log.d(TAG, "Started observing screenshots")
        }
    }
    
    /**
     * Stop observing for screenshots
     */
    fun stopObserving() {
        if (isObserving) {
            context.contentResolver.unregisterContentObserver(this)
            isObserving = false
            Log.d(TAG, "Stopped observing screenshots")
        }
    }
    
    /**
     * Called when MediaStore content changes
     */
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        
        if (uri == null) {
            return
        }
        
        Log.d(TAG, "MediaStore change detected: $uri")
        
        // Check if the new image is a screenshot
        if (isScreenshot(uri)) {
            // Prevent duplicate triggers within 2 seconds
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastScreenshotTime > 2000) {
                lastScreenshotTime = currentTime
                Log.d(TAG, "Screenshot detected!")
                onScreenshotTaken()
            }
        }
    }
    
    /**
     * Check if the URI represents a screenshot
     */
    private fun isScreenshot(uri: Uri): Boolean {
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                PROJECTION,
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
            )
            
            if (cursor != null && cursor.moveToFirst()) {
                val dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                val dateIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                
                if (dataIndex != -1 && dateIndex != -1) {
                    val path = cursor.getString(dataIndex)?.lowercase() ?: ""
                    val dateAdded = cursor.getLong(dateIndex) * 1000 // Convert to milliseconds
                    
                    // Check if the image was added recently (within last 5 seconds)
                    val isRecent = System.currentTimeMillis() - dateAdded < 5000
                    
                    // Check if path contains screenshot keywords
                    val containsKeyword = SCREENSHOT_KEYWORDS.any { keyword ->
                        path.contains(keyword)
                    }
                    
                    if (isRecent && containsKeyword) {
                        Log.d(TAG, "Screenshot confirmed: $path")
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking screenshot", e)
        } finally {
            cursor?.close()
        }
        
        return false
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stopObserving()
    }
}
