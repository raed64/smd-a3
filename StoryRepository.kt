package com.AppFlix.i220968_i228810.stories

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.data.local.AppDatabase
import com.AppFlix.i220968_i228810.data.local.PendingStoryEntity
import com.AppFlix.i220968_i228810.data.local.StoryEntity
import com.AppFlix.i220968_i228810.model.Story
import com.AppFlix.i220968_i228810.sync.SyncWorker
import com.AppFlix.i220968_i228810.utils.NetworkHelper
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

class StoryRepository(
    private val sessionManager: SessionManager,
    private val api: StoryApiService,
    private val contentResolver: ContentResolver,
    private val context: Context
) {
    private val storyDao = AppDatabase.getDatabase(context).storyDao()
    private val pendingDao = AppDatabase.getDatabase(context).pendingDao()

    suspend fun fetchStories(): List<Story> {
        // 1. Try Network
        try {
            val response = api.getStories()
            if (response.isSuccessful) {
                val apiStories = response.body().orEmpty()
                val entities = apiStories.map {
                    StoryEntity(
                        id = it.id,
                        userId = it.userId,
                        username = it.username,
                        userProfileImageUrl = it.userProfileImageUrl,
                        mediaUrl = it.mediaUrl,
                        mediaType = it.mediaType,
                        createdAt = it.createdAt,
                        expiresAt = it.expiresAt
                    )
                }
                storyDao.insertAll(entities)
            }
        } catch (e: Exception) { }

        // 2. Return from DB
        val now = System.currentTimeMillis()
        val cached = storyDao.getActiveStories(now)

        return cached.map { entity ->
            Story(
                id = entity.id,
                userId = entity.userId,
                username = entity.username,
                userProfileImageUrl = entity.userProfileImageUrl,
                mediaUrl = entity.mediaUrl,
                mediaType = entity.mediaType,
                createdAt = entity.createdAt,
                expiresAt = entity.expiresAt
            )
        }
    }

    suspend fun uploadStory(mediaUri: Uri, fileExtension: String) {
        // 1. Save to Internal Storage (Critical for Offline)
        val isVideo = fileExtension.equals("mp4", true)
        val savedPath = saveToInternalStorage(mediaUri, isVideo) ?: mediaUri.toString()
        val createdAt = System.currentTimeMillis()

        if (NetworkHelper.isOnline(context)) {
            // ONLINE: Try upload
            try {
                performOnlineUpload(Uri.parse(savedPath), fileExtension, createdAt)
            } catch (e: Exception) {
                // If fails, queue it
                queueOfflineStory(savedPath, fileExtension, createdAt)
            }
        } else {
            // OFFLINE: Queue immediately
            queueOfflineStory(savedPath, fileExtension, createdAt)
        }
    }

    private suspend fun queueOfflineStory(path: String, extension: String, createdAt: Long) {
        val mediaType = if (extension.equals("mp4", true)) "video" else "image"
        val pending = PendingStoryEntity(
            mediaUri = path,
            mediaType = mediaType,
            createdAt = createdAt
        )
        pendingDao.insertStory(pending)
        scheduleSync()
    }

    private suspend fun performOnlineUpload(mediaUri: Uri, fileExtension: String, createdAt: Long) {
        val currentProfile = sessionManager.getUserProfile() ?: return
        val expiresAt = createdAt + (24 * 60 * 60 * 1000L)

        val mimeType = when (fileExtension.lowercase()) {
            "mp4" -> "video/mp4"
            "png" -> "image/png"
            else -> "image/jpeg"
        }

        // Handle File vs ContentUri
        val bytes = if (mediaUri.scheme == "file") {
            File(mediaUri.path!!).readBytes()
        } else {
            contentResolver.openInputStream(mediaUri)?.use { it.readBytes() }
        } ?: throw IOException("Could not read file")

        val requestFile = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val mediaPart = MultipartBody.Part.createFormData("media", "story_$createdAt.$fileExtension", requestFile)

        val response = api.uploadStory(
            mediaPart,
            (currentProfile.username ?: "").toRequestBody("text/plain".toMediaTypeOrNull()),
            (currentProfile.username ?: "").toRequestBody("text/plain".toMediaTypeOrNull()),
            (if (fileExtension.equals("mp4", true)) "video" else "image").toRequestBody("text/plain".toMediaTypeOrNull()),
            createdAt.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
            expiresAt.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
            (currentProfile.profileImageUrl ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
        )

        if (!response.isSuccessful) throw IOException("Upload failed")
    }

    private fun saveToInternalStorage(uri: Uri, isVideo: Boolean): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val fileName = "story_offline_${System.currentTimeMillis()}.${if(isVideo) "mp4" else "jpg"}"
            val file = File(context.filesDir, fileName)
            file.outputStream().use { inputStream.copyTo(it) }
            Uri.fromFile(file).toString()
        } catch (e: Exception) { null }
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequest.Builder(SyncWorker::class.java)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "StorySyncWork",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}