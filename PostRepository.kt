package com.AppFlix.i220968_i228810.posts

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.data.local.*
import com.AppFlix.i220968_i228810.model.Post
import com.AppFlix.i220968_i228810.model.PostComment
import com.AppFlix.i220968_i228810.model.PostFeedItem
import com.AppFlix.i220968_i228810.sync.SyncWorker
import com.AppFlix.i220968_i228810.utils.NetworkHelper
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
// Add Import
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest

data class LikeResponse(
    val likesCount: Int,
    val likedByUser: Boolean
)

class PostRepository(
    private val sessionManager: SessionManager,
    private val api: PostApiService,
    private val contentResolver: ContentResolver,
    private val context: Context // Added Context for DB and WorkManager
) {

    private val db = AppDatabase.getDatabase(context)
    private val postDao = db.postDao()
    private val commentDao = db.commentDao()
    private val pendingDao = db.pendingDao()

    // ---------- POSTS (FEED) ----------

    /**
     * Offline-First Strategy:
     * 1. Try to fetch from Network and save to DB (Cache).
     * 2. Always return data from DB (Single Source of Truth).
     */
    suspend fun fetchPosts(): List<PostFeedItem> {
        val profile = sessionManager.getUserProfile()
        val userId = profile?.username
            ?: profile?.email?.substringBefore("@")
            ?: "socially_user"

        // 1. Try Network (Fire and Forget)
        try {
            val response = api.getPosts(userId)
            if (response.isSuccessful) {
                val dtos = response.body().orEmpty()

                // Map API DTOs to Local Entities
                val entities = dtos.map { dto ->
                    PostEntity(
                        id = dto.id,
                        userId = dto.userId,
                        username = dto.username,
                        userProfileImageUrl = dto.userProfileImageUrl,
                        mediaUrl = dto.mediaUrl,
                        caption = dto.caption,
                        likesCount = dto.likesCount,
                        commentsCount = dto.commentsCount,
                        createdAt = dto.createdAt,
                        isLikedByCurrentUser = dto.likedByUser
                    )
                }

                // Update Cache
                postDao.clearAll() // Simple strategy: replace feed
                postDao.insertAll(entities)
            }
        } catch (e: Exception) {
            // Network failed? No problem, we will show cached data.
            e.printStackTrace()
        }

        // 2. Return from Local Database
        val cachedPosts = postDao.getAllPosts()

        return cachedPosts.map { entity ->
            PostFeedItem(
                post = Post(
                    id = entity.id,
                    userId = entity.userId,
                    username = entity.username,
                    userProfileImageUrl = entity.userProfileImageUrl,
                    mediaUrl = entity.mediaUrl,
                    caption = entity.caption,
                    likesCount = entity.likesCount,
                    commentsCount = entity.commentsCount,
                    createdAt = entity.createdAt
                ),
                isLikedByCurrentUser = entity.isLikedByCurrentUser
            )
        }
    }

    suspend fun createPost(mediaUri: Uri, caption: String) {
        // 1. Prepare Data (same as before)
        val createdAt = System.currentTimeMillis()

        if (NetworkHelper.isOnline(context)) {
            // ONLINE: Try immediate upload
            performOnlineUpload(mediaUri, caption, createdAt)
        } else {
            // OFFLINE: Queue it
            val pending = PendingPostEntity(
                caption = caption,
                mediaUri = mediaUri.toString(),
                createdAt = createdAt
            )
            pendingDao.insertPost(pending)
            scheduleSync()
        }
    }

    private suspend fun performOnlineUpload(mediaUri: Uri, caption: String, createdAt: Long) {
        val profile = sessionManager.getUserProfile()
            ?: throw IllegalStateException("User must be logged in")

        val userId = profile.username ?: "user"
        val profileImageUrl = profile.profileImageUrl ?: ""

        val mimeType = contentResolver.getType(mediaUri) ?: "image/jpeg"
        val inputStream = contentResolver.openInputStream(mediaUri)
            ?: throw IOException("Unable to open media stream")
        val bytes = inputStream.use { it.readBytes() }

        val requestFile: RequestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val mediaPart = MultipartBody.Part.createFormData(
            name = "media",
            filename = "post_${createdAt}.jpg",
            body = requestFile
        )

        val userIdBody = userId.toRequestBody("text/plain".toMediaTypeOrNull())
        val usernameBody = userId.toRequestBody("text/plain".toMediaTypeOrNull())
        val captionBody = caption.toRequestBody("text/plain".toMediaTypeOrNull())
        val createdAtBody = createdAt.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val profileUrlBody = profileImageUrl.toRequestBody("text/plain".toMediaTypeOrNull())

        val response = api.uploadPost(
            media = mediaPart,
            userId = userIdBody,
            username = usernameBody,
            caption = captionBody,
            createdAt = createdAtBody,
            userProfileImageUrl = profileUrlBody
        )

        if (!response.isSuccessful) {
            throw IOException("Upload failed: ${response.code()}")
        }
    }

    // ---------- LIKES ----------

    suspend fun toggleLike(postId: String, currentlyLiked: Boolean): LikeResponse {
        val profile = sessionManager.getUserProfile()
            ?: throw IllegalStateException("User not logged in")

        val userId = profile.username ?: "user"
        val newStatus = !currentlyLiked

        if (NetworkHelper.isOnline(context)) {
            // ONLINE: Call API
            val response = api.toggleLike(postId, userId, newStatus)
            if (response.isSuccessful) {
                val body = response.body()!!
                // Sync local cache with server response
                postDao.updateLikeStatus(postId, body.likesCount, body.likedByUser)
                return body
            } else {
                throw IOException("Like failed: ${response.code()}")
            }
        } else {
            // OFFLINE: Optimistic Update & Queue

            // 1. Queue Action
            pendingDao.insertLike(
                PendingLikeEntity(
                    postId = postId,
                    isLike = newStatus,
                    timestamp = System.currentTimeMillis()
                )
            )

            // 2. Optimistic UI Update (Fake it locally)
            val currentPost = postDao.getPostById(postId)
            val currentCount = currentPost?.likesCount ?: 0
            val newCount = if (newStatus) currentCount + 1 else (if (currentCount > 0) currentCount - 1 else 0)

            postDao.updateLikeStatus(postId, newCount, newStatus)

            // 3. Schedule Sync
            scheduleSync()

            return LikeResponse(newCount, newStatus)
        }
    }

    // ---------- COMMENTS ----------

    suspend fun fetchComments(postId: String): List<PostComment> {
        // 1. Try Network & Cache
        try {
            val response = api.getComments(postId)
            if (response.isSuccessful) {
                val apiComments = response.body().orEmpty()
                val entities = apiComments.map { dto ->
                    CommentEntity(
                        id = dto.id,
                        postId = dto.postId,
                        userId = dto.userId,
                        username = dto.username,
                        userProfileImageUrl = dto.userProfileImageUrl,
                        text = dto.text,
                        createdAt = dto.createdAt
                    )
                }
                commentDao.insertAll(entities)
            }
        } catch (e: Exception) { }

        // 2. Return from DB
        return commentDao.getCommentsForPost(postId).map { entity ->
            PostComment(
                id = entity.id,
                postId = entity.postId,
                userId = entity.userId,
                username = entity.username,
                userProfileImageUrl = entity.userProfileImageUrl,
                text = entity.text,
                createdAt = entity.createdAt
            )
        }
    }

    suspend fun submitComment(postId: String, text: String): Boolean {
        val profile = sessionManager.getUserProfile() ?: return false
        val timestamp = System.currentTimeMillis()

        if (NetworkHelper.isOnline(context)) {
            // ONLINE
            val response = api.addComment(
                postId,
                profile.uid, // or username depending on your PHP
                profile.username,
                profile.profileImageUrl,
                text,
                timestamp
            )
            return response.isSuccessful
        } else {
            // OFFLINE

            // 1. Queue
            pendingDao.insertComment(
                PendingCommentEntity(
                    postId = postId,
                    text = text,
                    timestamp = timestamp
                )
            )

            // 2. Optimistic Insert (Show immediately with temp ID)
            val tempId = "temp_${timestamp}"
            commentDao.insert(
                CommentEntity(
                    id = tempId,
                    postId = postId,
                    userId = profile.uid,
                    username = profile.username,
                    userProfileImageUrl = profile.profileImageUrl,
                    text = text,
                    createdAt = timestamp
                )
            )

            scheduleSync()
            return true
        }
    }

    // Helper to trigger background sync
    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequest.Builder(SyncWorker::class.java)
            .setConstraints(constraints)
            .build()

        // CHANGED: Use enqueueUniqueWork with KEEP
        // "KEEP" means: If a sync is already scheduled/running, ignore this new request.
        // This prevents 3 workers from starting if you click share 3 times.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "OfflineSyncWork",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}