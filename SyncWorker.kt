package com.AppFlix.i220968_i228810.sync

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.data.api.ApiClient
import com.AppFlix.i220968_i228810.data.local.AppDatabase // FIXED: Added Import
import com.AppFlix.i220968_i228810.data.local.MessageEntity // FIXED: Added Import
import com.AppFlix.i220968_i228810.data.local.PendingPostEntity
import com.AppFlix.i220968_i228810.data.local.PendingStoryEntity
import com.AppFlix.i220968_i228810.data.local.PendingLikeEntity
import com.AppFlix.i220968_i228810.data.local.PendingCommentEntity
import androidx.work.ListenableWorker.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File // Added

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    // FIXED: Explicitly call getDatabase from the imported class
    private val database = AppDatabase.getDatabase(context)
    private val pendingDao = database.pendingDao()
    private val messageDao = database.messageDao()
    private val sessionManager = SessionManager(context)

    private val postApi = ApiClient.postApi
    private val storyApi = ApiClient.storyApi
    private val messageApi = ApiClient.messageApi

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            syncMessages()
            syncPosts()
            syncStories()
            syncLikes()
            syncComments()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun syncMessages() {
        val pendingMessages: List<MessageEntity> = pendingDao.getPendingMessages()
        pendingMessages.forEach { msg ->
            try {
                val textPart = msg.text.toRequestBody("text/plain".toMediaTypeOrNull())
                val senderPart = msg.senderId.toRequestBody("text/plain".toMediaTypeOrNull())
                val receiverPart = msg.receiverId.toRequestBody("text/plain".toMediaTypeOrNull())
                val typePart = msg.type.toRequestBody("text/plain".toMediaTypeOrNull())
                val timePart = msg.createdAt.toString().toRequestBody("text/plain".toMediaTypeOrNull())

                // --- FIX: Handle Media Upload ---
                var mediaPart: MultipartBody.Part? = null
                if (msg.mediaUrl.isNotEmpty()) {
                    val uri = Uri.parse(msg.mediaUrl)
                    // Try opening as File first (Internal Storage), fall back to Content Resolver
                    val bytes = try {
                        if (uri.scheme == "file") {
                            File(uri.path!!).readBytes()
                        } else {
                            applicationContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        }
                    } catch (e: Exception) { null }

                    if (bytes != null) {
                        val mimeType = if (msg.type == "VIDEO") "video/mp4" else "image/jpeg"
                        val ext = if (msg.type == "VIDEO") "mp4" else "jpg"
                        val requestFile = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                        mediaPart = MultipartBody.Part.createFormData("media", "msg_${msg.createdAt}.$ext", requestFile)
                    }
                }

                val response = messageApi.sendMessage(
                    mediaPart, // <--- Now passing the actual media
                    senderPart, receiverPart, textPart, typePart, timePart
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    // Update local DB with real Server URL and ID
                    val serverUrl = body.mediaUrl ?: msg.mediaUrl
                    body.id?.let { serverId ->
                        messageDao.updateMessageSynced(msg.localId, serverId, serverUrl)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun syncPosts() {
        val pendingPosts = pendingDao.getAllPendingPosts()
        val profile = sessionManager.getUserProfile() ?: return

        pendingPosts.forEach { post ->
            try {
                val uri = Uri.parse(post.mediaUri)
                val inputStream = applicationContext.contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() } ?: return@forEach

                val requestFile = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val mediaPart = MultipartBody.Part.createFormData("media", "post_offline_${post.createdAt}.jpg", requestFile)

                val userIdBody = profile.uid.toRequestBody("text/plain".toMediaTypeOrNull())
                val usernameBody = profile.username.toRequestBody("text/plain".toMediaTypeOrNull())
                val captionBody = post.caption.toRequestBody("text/plain".toMediaTypeOrNull())
                val timeBody = post.createdAt.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val profileImgBody = profile.profileImageUrl.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = postApi.uploadPost(mediaPart, userIdBody, usernameBody, captionBody, timeBody, profileImgBody)

                if (response.isSuccessful) {
                    pendingDao.deletePost(post)
                }
            } catch (e: Exception) { }
        }
    }

    // ... inside SyncWorker class ...

    private suspend fun syncStories() {
        val pendingStories = pendingDao.getAllPendingStories()
        val profile = sessionManager.getUserProfile() ?: return

        pendingStories.forEach { story ->
            try {
                val uri = Uri.parse(story.mediaUri)

                // --- FIX: Handle File URI vs Content URI ---
                val bytes = if (uri.scheme == "file") {
                    File(uri.path!!).readBytes()
                } else {
                    applicationContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }

                if (bytes == null) return@forEach

                val mimeType = if (story.mediaType == "video") "video/mp4" else "image/jpeg"
                val ext = if (story.mediaType == "video") "mp4" else "jpg"

                val requestFile = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val mediaPart = MultipartBody.Part.createFormData("media", "story_offline_${story.createdAt}.$ext", requestFile)

                // ... (Prepare other parts as before) ...
                val userIdBody = profile.uid.toRequestBody("text/plain".toMediaTypeOrNull())
                // ... (rest of the body parts) ...
                val usernameBody = profile.username.toRequestBody("text/plain".toMediaTypeOrNull())
                val typeBody = story.mediaType.toRequestBody("text/plain".toMediaTypeOrNull())
                val timeBody = story.createdAt.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val expireBody = (story.createdAt + 86400000).toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val profileImgBody = profile.profileImageUrl.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = storyApi.uploadStory(mediaPart, userIdBody, usernameBody, typeBody, timeBody, expireBody, profileImgBody)

                if (response.isSuccessful) {
                    pendingDao.deleteStory(story)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    private suspend fun syncLikes() {
        val pendingLikes = pendingDao.getAllPendingLikes()
        val profile = sessionManager.getUserProfile() ?: return

        pendingLikes.forEach { action ->
            try {
                val response = postApi.toggleLike(action.postId, profile.username, action.isLike)
                if (response.isSuccessful) {
                    pendingDao.deleteLike(action)
                }
            } catch (e: Exception) { }
        }
    }

    private suspend fun syncComments() {
        val pendingComments = pendingDao.getAllPendingComments()
        val profile = sessionManager.getUserProfile() ?: return

        pendingComments.forEach { action ->
            try {
                val response = postApi.addComment(
                    postId = action.postId,
                    userId = profile.uid,
                    username = profile.username,
                    userProfileImageUrl = profile.profileImageUrl,
                    text = action.text,
                    createdAt = action.timestamp
                )

                if (response.isSuccessful) {
                    pendingDao.deleteComment(action)
                }
            } catch (e: Exception) { }
        }
    }
}