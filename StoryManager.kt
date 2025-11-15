package com.AppFlix.i220968_i228810.stories

import android.content.Context
import android.net.Uri
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.model.Story
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StoryManager(private val context: Context) {

    private val sessionManager = SessionManager(context)
    private val repository = StoryRepository(
        sessionManager = sessionManager,
        api = StoryNetworkModule.api,
        contentResolver = context.contentResolver,
        context = context
    )

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val _storiesFlow = MutableStateFlow<List<Story>>(emptyList())
    val storiesFlow: StateFlow<List<Story>> = _storiesFlow

    private val _uploadState = MutableStateFlow<StoryUploadState>(StoryUploadState.Idle)
    val uploadState: StateFlow<StoryUploadState> = _uploadState

    init {
        refreshStories()
    }

    fun refreshStories() {
        scope.launch {
            try {
                val stories = repository.fetchStories()
                _storiesFlow.value = stories
            } catch (ex: Exception) {
                // You could expose an error state if needed
            }
        }
    }

    fun uploadStoryFromUri(uri: Uri) {
        val currentUser = sessionManager.getUserProfile()
        if (currentUser == null) {
            _uploadState.value = StoryUploadState.Error("User session missing.")
            return
        }

        scope.launch {
            try {
                _uploadState.value = StoryUploadState.Uploading(0.0)

                val extension = context.contentResolver.getType(uri)?.let { mime ->
                    when {
                        mime.contains("image") -> "jpg"
                        mime.contains("video") -> "mp4"
                        else -> "bin"
                    }
                } ?: "jpg"

                repository.uploadStory(
                    mediaUri = uri,
                    fileExtension = extension
                )

                _uploadState.value = StoryUploadState.Success
                refreshStories()
            } catch (ex: Exception) {
                _uploadState.value = StoryUploadState.Error(ex.message ?: "Failed to upload story")
            }
        }
    }

    fun clearState() {
        _uploadState.value = StoryUploadState.Idle
    }
}

sealed interface StoryUploadState {
    data object Idle : StoryUploadState
    data class Uploading(val progress: Double) : StoryUploadState
    data object Success : StoryUploadState
    data class Error(val message: String) : StoryUploadState
}
