package com.AppFlix.i220968_i228810.calling

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.data.api.ApiClient
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages Agora RTC Engine lifecycle and call operations.
 * Uses PHP API + FCM for signaling (calling notifications).
 */
class CallManager(private val context: Context) {

    private var rtcEngine: RtcEngine? = null
    private var isJoined = false
    private var isMuted = false
    private var isVideoEnabled = true

    private val sessionManager = SessionManager(context)
    // Use AuthApi because it contains the initiateCall endpoint
    private val api = ApiClient.authApi

    companion object {
        private const val TAG = "CallManager"
        // Your Agora App ID
        private const val APP_ID = "c026c1fa456343d1b0b7dcaaf6de1562"

        // Generate consistent channel name for 1-on-1 calls
        fun generateChannelName(userId1: String, userId2: String): String {
            val sorted = listOf(userId1, userId2).sorted()
            return "chat_${sorted[0]}_${sorted[1]}"
        }
    }

    interface CallEventListener {
        fun onUserJoined(uid: Int)
        fun onUserOffline(uid: Int)
        fun onConnectionStateChanged(state: Int, reason: Int)
        fun onError(errorCode: Int)
    }

    private var eventListener: CallEventListener? = null

    fun setEventListener(listener: CallEventListener) {
        this.eventListener = listener
    }

    /**
     * Initialize Agora RTC Engine
     */
    fun initialize(enableVideo: Boolean = true): Boolean {
        if (APP_ID.isEmpty() || APP_ID == "YOUR_AGORA_APP_ID") {
            Log.e(TAG, "Agora App ID is missing.")
            return false
        }

        try {
            // Create Configuration
            val config = RtcEngineConfig()
            config.mContext = context
            config.mAppId = APP_ID
            config.mEventHandler = object : IRtcEngineEventHandler() {
                override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                    Log.d(TAG, "Join channel success: $channel, uid: $uid")
                    isJoined = true
                }

                override fun onUserJoined(uid: Int, elapsed: Int) {
                    Log.d(TAG, "Remote user joined: $uid")
                    eventListener?.onUserJoined(uid)
                }

                override fun onUserOffline(uid: Int, reason: Int) {
                    Log.d(TAG, "Remote user offline: $uid")
                    eventListener?.onUserOffline(uid)
                }

                override fun onError(err: Int) {
                    Log.e(TAG, "Agora Error: $err")
                    eventListener?.onError(err)
                }
            }

            // Initialize Engine
            rtcEngine = RtcEngine.create(config)

            if (rtcEngine == null) {
                Log.e(TAG, "Failed to create RTC Engine")
                return false
            }

            // Set Profile
            rtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)

            // Configure Video/Audio
            if (enableVideo) {
                rtcEngine?.enableVideo()
            } else {
                rtcEngine?.disableVideo()
                rtcEngine?.enableAudio()
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Agora: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Join a call channel
     */
    fun joinChannel(channelName: String, uid: Int = 0): Boolean {
        if (rtcEngine == null) {
            Log.e(TAG, "RTC Engine not initialized")
            return false
        }

        try {
            val token: String? = null // Use null for testing mode
            Log.d(TAG, "Joining channel: $channelName with uid: $uid")
            val result = rtcEngine?.joinChannel(token, channelName, "", uid)

            // Presence is now handled by Heartbeat in MainFeedActivity,
            // so we don't need manual setUserOnCall here.

            return result == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join channel: ${e.message}", e)
            return false
        }
    }

    /**
     * Leave the call channel
     */
    fun leaveChannel() {
        rtcEngine?.leaveChannel()
        isJoined = false
        // Presence automatically reverts to "Online" via Heartbeat logic
    }

    /**
     * Send call notification via PHP API (Signaling)
     */
    fun sendCallNotification(recipientUserId: String, isVideoCall: Boolean) {
        val currentUser = sessionManager.getUserProfile() ?: return
        val callType = if (isVideoCall) "video" else "voice"

        // Generate channel name
        val channelName = generateChannelName(currentUser.uid, recipientUserId)

        // Call the API endpoint
        CoroutineScope(Dispatchers.IO).launch {
            try {
                api.initiateCall(
                    callerId = currentUser.uid,
                    targetId = recipientUserId,
                    callerName = currentUser.username,
                    callType = callType,
                    channelName = channelName
                )
                Log.d(TAG, "Call notification sent to API for user: $recipientUserId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send call notification via API", e)
            }
        }
    }

    // --- Video / Audio Controls ---

    fun setupLocalVideo(view: android.view.SurfaceView) {
        rtcEngine?.setupLocalVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0))
        rtcEngine?.startPreview()
    }

    fun setupRemoteVideo(uid: Int, view: android.view.SurfaceView) {
        rtcEngine?.setupRemoteVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, uid))
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        rtcEngine?.muteLocalAudioStream(isMuted)
        return isMuted
    }

    fun toggleVideo(): Boolean {
        isVideoEnabled = !isVideoEnabled
        rtcEngine?.muteLocalVideoStream(!isVideoEnabled)
        return isVideoEnabled
    }

    fun switchCamera() {
        rtcEngine?.switchCamera()
    }

    fun setSpeakerPhone(enabled: Boolean) {
        rtcEngine?.setEnableSpeakerphone(enabled)
    }

    /**
     * Cleanup resources
     */
    fun destroy() {
        rtcEngine?.leaveChannel()
        rtcEngine?.stopPreview()
        RtcEngine.destroy()
        rtcEngine = null
        isJoined = false
    }

    /**
     * Check Permissions
     */
    fun hasRequiredPermissions(enableVideo: Boolean = true): Boolean {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (enableVideo) {
            permissions.add(Manifest.permission.CAMERA)
        }

        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Getters for UI state
    fun getMutedState() = isMuted
    fun getVideoState() = isVideoEnabled
    fun getJoinedState() = isJoined
}