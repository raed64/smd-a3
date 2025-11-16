package com.AppFlix.i220968_i228810.messaging

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.data.api.*
import com.AppFlix.i220968_i228810.data.local.AppDatabase
import com.AppFlix.i220968_i228810.data.local.ChatEntity
import com.AppFlix.i220968_i228810.data.local.MessageEntity
import com.AppFlix.i220968_i228810.model.Chat
import com.AppFlix.i220968_i228810.model.Message
import com.AppFlix.i220968_i228810.model.MessageType
import com.AppFlix.i220968_i228810.sync.SyncWorker
import com.AppFlix.i220968_i228810.utils.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class ChatRepository(private val context: Context) {

    private val sessionManager = SessionManager(context)
    private val api = ApiClient.messageApi
    private val dao = AppDatabase.getDatabase(context).messageDao()
    private val contentResolver = context.contentResolver
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _vanishModeState = MutableStateFlow(false)
    val vanishModeState: StateFlow<Boolean> = _vanishModeState

    // 1. Chat List (Offline First)
    fun getChats(): Flow<List<Chat>> {
        val currentUid = sessionManager.getUserProfile()?.uid ?: ""

        // Background Sync
        scope.launch {
            // Only try to sync if network is available
            if (NetworkHelper.isOnline(context)) {
                syncChats(currentUid)
            }
            while(isActive && NetworkHelper.isOnline(context)) {
                delay(5000)
                syncChats(currentUid)
            }
        }

        // Always return local DB data immediately
        return dao.getChats().map { entities ->
            entities.map { entity ->
                Chat(
                    chatId = entity.chatId,
                    userIds = listOf(currentUid, entity.otherUserId),
                    lastMessage = entity.lastMessage,
                    lastMessageTime = entity.lastMessageTime,
                    updatedAt = entity.lastMessageTime,
                    otherUserId = entity.otherUserId,
                    otherUserName = entity.otherUserName,
                    otherUserProfileImage = entity.otherProfileImage
                )
            }
        }
    }

    private suspend fun syncChats(userId: String) {
        try {
            val response = api.getUserChats(userId)
            if (response.isSuccessful) {
                val dtos = response.body() ?: emptyList()
                val entities = dtos.map { dto ->
                    ChatEntity(
                        chatId = dto.localChatId,
                        serverChatId = dto.serverChatId,
                        otherUserId = dto.otherUserId,
                        otherUserName = dto.otherUserName,
                        otherProfileImage = dto.otherProfileImage ?: "",
                        lastMessage = dto.lastMessage ?: "",
                        lastMessageTime = dto.lastMessageTime
                    )
                }
                dao.insertChats(entities)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // 2. Messaging
    fun getChatId(user1: String, user2: String): String {
        return if (user1 < user2) "${user1}_${user2}" else "${user2}_${user1}"
    }

    fun getMessages(otherUserId: String): Flow<List<Message>> {
        val currentUid = sessionManager.getUserProfile()?.uid ?: ""
        val chatId = getChatId(currentUid, otherUserId)

        scope.launch {
            while (isActive) {
                if (NetworkHelper.isOnline(context)) {
                    syncMessages(currentUid, otherUserId, chatId)
                }
                delay(2000)
            }
        }

        return dao.getMessages(chatId).map { entities ->
            entities.map { entity ->
                val msgType = try {
                    if (entity.type == "VANISH") MessageType.TEXT
                    else if (entity.type == "VANISH_IMAGE") MessageType.IMAGE
                    else MessageType.valueOf(entity.type)
                } catch (e: Exception) { MessageType.TEXT }

                Message(
                    messageId = entity.serverId?.toString() ?: entity.localId.toString(),
                    chatId = chatId,
                    senderId = entity.senderId,
                    type = msgType,
                    text = entity.text,
                    mediaUrl = entity.mediaUrl,
                    sentAt = entity.createdAt,
                    deleted = entity.isDeleted
                )
            }
        }
    }

    private suspend fun syncMessages(myId: String, otherId: String, chatId: String) {
        try {
            val response = api.getMessages(myId, otherId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    _vanishModeState.value = body.vanish_mode

                    body.messages.forEach { dto ->
                        val vanishClearedTime = sessionManager.getLastVanishClearTime(chatId)
                        if ((dto.type == "VANISH" || dto.type == "VANISH_IMAGE") && dto.created_at < vanishClearedTime) {
                            return@forEach
                        }

                        val exists = dao.hasMessageWithServerId(dto.id)
                        if (exists) {
                            dao.updateSyncedMessage(dto.id, dto.text_content ?: "", dto.media_url ?: "", dto.is_deleted == 1)
                        } else {
                            // Check if this matches a pending message (deduplication)
                            val pendingMatch = dao.findPendingMessage(chatId, dto.sender_id, dto.text_content ?: "", dto.created_at)
                            if (pendingMatch != null) {
                                dao.updateServerId(pendingMatch.localId, dto.id)
                            } else {
                                val entity = MessageEntity(
                                    serverId = dto.id,
                                    chatId = chatId,
                                    senderId = dto.sender_id,
                                    receiverId = if(dto.sender_id == myId) otherId else myId,
                                    type = dto.type,
                                    text = dto.text_content ?: "",
                                    mediaUrl = dto.media_url ?: "",
                                    postId = "",
                                    createdAt = dto.created_at,
                                    isDeleted = dto.is_deleted == 1,
                                    syncStatus = 0
                                )
                                dao.insertMessage(entity)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun sendMediaMessage(otherUserId: String, uri: Uri) {
        val type = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val msgType = when {
            type.startsWith("image/") -> "IMAGE"
            type.startsWith("video/") -> "VIDEO"
            else -> "FILE"
        }
        sendMessage(otherUserId, "", uri, msgType)
    }

    // --- UPDATED: Send Message with Offline Support ---
    // ... (Keep existing code) ...

    // --- UPDATED: Send Message with Fixes ---
    // ... (Keep existing code) ...

    // --- UPDATED: Send Message with Fixes ---
    // ... (Inside ChatRepository Class) ...

    // --- NEW HELPER: Copy file to app storage ---
    private fun saveToInternalStorage(uri: Uri, isVideo: Boolean): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val fileName = "msg_local_${System.currentTimeMillis()}.${if(isVideo) "mp4" else "jpg"}"
            val file = java.io.File(context.filesDir, fileName)

            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            // Return the file URI string (file:///...)
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- UPDATED: Send Message ---
    fun sendMessage(otherUserId: String, text: String, mediaUri: Uri? = null, manualType: String = "TEXT") {
        val currentUser = sessionManager.getUserProfile() ?: return
        val chatId = getChatId(currentUser.uid, otherUserId)
        val timestamp = System.currentTimeMillis()

        val isVanish = _vanishModeState.value
        val typeStr = if (isVanish) {
            if (mediaUri != null) "VANISH_IMAGE" else "VANISH"
        } else {
            if (mediaUri != null) manualType else "TEXT"
        }

        scope.launch {
            // 1. COPY FILE INTERNALLY (Critical for Offline Support)
            var localFilePath = ""
            if (mediaUri != null) {
                val isVideo = typeStr == "VIDEO"
                // Save to internal storage so we don't lose permission after restart
                localFilePath = saveToInternalStorage(mediaUri, isVideo) ?: mediaUri.toString()
            }

            // 2. SAVE LOCAL with Internal File Path
            val localId = dao.insertMessage(
                MessageEntity(
                    chatId = chatId,
                    senderId = currentUser.uid,
                    receiverId = otherUserId,
                    type = typeStr,
                    text = text,
                    mediaUrl = localFilePath, // Save the file:// path, not content://
                    postId = "",
                    createdAt = timestamp,
                    syncStatus = 2
                )
            )

            // 3. Check Network
            if (NetworkHelper.isOnline(context)) {
                try {
                    // ... (Prepare parts as before) ...
                    val textPart = text.toRequestBody("text/plain".toMediaTypeOrNull())
                    val senderPart = currentUser.uid.toRequestBody("text/plain".toMediaTypeOrNull())
                    val receiverPart = otherUserId.toRequestBody("text/plain".toMediaTypeOrNull())
                    val typePart = typeStr.toRequestBody("text/plain".toMediaTypeOrNull())
                    val timePart = timestamp.toString().toRequestBody("text/plain".toMediaTypeOrNull())

                    var mediaPart: MultipartBody.Part? = null
                    if (localFilePath.isNotEmpty()) {
                        // Read from the LOCAL COPIED FILE
                        val fileUri = Uri.parse(localFilePath)
                        val bytes = if (fileUri.scheme == "file") {
                            java.io.File(fileUri.path!!).readBytes()
                        } else {
                            contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                        }

                        if (bytes != null) {
                            val mimeType = if (typeStr == "VIDEO") "video/mp4" else "image/jpeg"
                            val ext = if (typeStr == "VIDEO") "mp4" else "jpg"
                            val requestFile = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                            mediaPart = MultipartBody.Part.createFormData("media", "msg_$timestamp.$ext", requestFile)
                        }
                    }

                    val response = api.sendMessage(mediaPart, senderPart, receiverPart, textPart, typePart, timePart)

                    if (response.isSuccessful && response.body()?.success == true) {
                        val body = response.body()!!
                        // Update with SERVER URL (http://...)
                        val serverUrl = body.mediaUrl ?: localFilePath
                        body.id?.let { serverId ->
                            dao.updateMessageSynced(localId, serverId, serverUrl)
                        }
                    } else {
                        dao.markAsPending(localId)
                        scheduleSync()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    dao.markAsPending(localId)
                    scheduleSync()
                }
            } else {
                // OFFLINE: Just mark pending. Worker will find the file at localFilePath later.
                dao.markAsPending(localId)
                scheduleSync()
            }
        }
    }

    // ... (Keep existing code) ...
    // ... (Keep existing code) ...

    // --- Background Sync Trigger ---
    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequest.Builder(SyncWorker::class.java)
            .setConstraints(constraints)
            .build()

        // Use Unique Work to avoid stacking duplicates
        WorkManager.getInstance(context).enqueueUniqueWork(
            "ChatOfflineSync",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun sendPostShareMessage(otherUserId: String, postId: String) {
        sendMessage(otherUserId, "Shared Post: $postId")
    }

    fun clearVanishMessages(otherUserId: String) {
        val currentUid = sessionManager.getUserProfile()?.uid ?: return
        val chatId = getChatId(currentUid, otherUserId)
        scope.launch {
            sessionManager.setLastVanishClearTime(chatId, System.currentTimeMillis())
            dao.deleteVanishMessages(chatId)
        }
    }

    fun editMessage(messageId: String, newText: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val id = messageId.toIntOrNull() ?: return
        val senderId = sessionManager.getUserProfile()?.uid ?: return
        scope.launch {
            try {
                val res = api.editMessage(EditMessageRequest(id, newText, senderId))
                if (res.isSuccessful && res.body()?.success == true) {
                    withContext(Dispatchers.Main) { onSuccess() }
                } else { withContext(Dispatchers.Main) { onError("Error") } }
            } catch(e: Exception) { withContext(Dispatchers.Main) { onError("Network Error") } }
        }
    }

    fun deleteMessage(messageId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val id = messageId.toIntOrNull() ?: return
        val senderId = sessionManager.getUserProfile()?.uid ?: return
        scope.launch {
            try {
                val res = api.deleteMessage(DeleteMessageRequest(id, senderId))
                if (res.isSuccessful && res.body()?.success == true) {
                    withContext(Dispatchers.Main) { onSuccess() }
                } else { withContext(Dispatchers.Main) { onError("Error") } }
            } catch(e: Exception) { withContext(Dispatchers.Main) { onError("Network Error") } }
        }
    }

    fun toggleVanishMode(otherUserId: String, enable: Boolean) {
        val currentUid = sessionManager.getUserProfile()?.uid ?: return
        _vanishModeState.value = enable
        scope.launch {
            try {
                api.toggleVanishMode(user1 = currentUid, user2 = otherUserId, enable = enable)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}