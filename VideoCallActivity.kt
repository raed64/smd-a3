package com.AppFlix.i220968_i228810

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.AppFlix.i220968_i228810.calling.CallManager
import com.AppFlix.i220968_i228810.utils.CircleTransform
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso

class VideoCallActivity : AppCompatActivity() {

    private lateinit var callManager: CallManager
    private var callDurationInSeconds = 0
    private lateinit var callDurationTextView: TextView
    private lateinit var callStatusTextView: TextView
    private lateinit var remoteUserNameTextView: TextView
    private lateinit var remoteUserImageView: ImageView
    private lateinit var remotePlaceholder: LinearLayout

    // Containers
    private lateinit var localVideoContainer: FrameLayout // Smaller Rectangle
    private lateinit var remoteVideoContainer: FrameLayout // Bigger Rectangle

    private lateinit var muteButton: FrameLayout
    private lateinit var muteIcon: ImageView
    private lateinit var cameraToggleButton: FrameLayout
    private lateinit var cameraIcon: ImageView

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateTimeRunnable: Runnable

    private var localSurfaceView: SurfaceView? = null
    private var remoteSurfaceView: SurfaceView? = null

    private var isMuted = false
    private var isVideoEnabled = true
    private var channelName: String = ""
    private var otherUserName: String = ""
    private var otherUserImage: String = ""
    private var otherUserId: String = ""
    private var isIncoming: Boolean = false

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_OTHER_USER_ID = "other_user_id"
        const val EXTRA_OTHER_USER_NAME = "other_user_name"
        const val EXTRA_OTHER_USER_IMAGE = "other_user_image"
        const val EXTRA_IS_INCOMING = "is_incoming"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        window.statusBarColor = android.graphics.Color.TRANSPARENT

        channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""
        otherUserId = intent.getStringExtra(EXTRA_OTHER_USER_ID) ?: ""
        otherUserName = intent.getStringExtra(EXTRA_OTHER_USER_NAME) ?: "Unknown"
        otherUserImage = intent.getStringExtra(EXTRA_OTHER_USER_IMAGE) ?: ""
        isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false)

        if (channelName.isEmpty()) {
            Toast.makeText(this, "Invalid call data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupViews()

        callManager = CallManager(this)
        callManager.setEventListener(object : CallManager.CallEventListener {
            override fun onUserJoined(uid: Int) {
                runOnUiThread {
                    remotePlaceholder.visibility = View.GONE
                    callStatusTextView.text = "Connected"
                    // Setup Bigger Rectangle (Remote)
                    setupRemoteVideo(uid)
                }
            }

            override fun onUserOffline(uid: Int) {
                runOnUiThread {
                    remotePlaceholder.visibility = View.VISIBLE
                    callStatusTextView.text = "User left"
                    remoteSurfaceView?.let { remoteVideoContainer.removeView(it) }
                    remoteSurfaceView = null
                    endCall()
                }
            }

            override fun onConnectionStateChanged(state: Int, reason: Int) {}

            override fun onError(errorCode: Int) {
                runOnUiThread {
                    Toast.makeText(this@VideoCallActivity, "Call error: $errorCode", Toast.LENGTH_SHORT).show()
                }
            }
        })

        if (checkPermissions()) {
            initializeCall()
        } else {
            requestPermissions()
        }
    }

    private fun setupViews() {
        callDurationTextView = findViewById(R.id.call_duration)
        callStatusTextView = findViewById(R.id.call_status)
        remoteUserNameTextView = findViewById(R.id.remote_user_name)
        remoteUserImageView = findViewById(R.id.remote_user_image)
        remotePlaceholder = findViewById(R.id.remote_placeholder)

        localVideoContainer = findViewById(R.id.local_video_container) // Small view
        remoteVideoContainer = findViewById(R.id.remote_video_container) // Big view

        muteButton = findViewById(R.id.mute_button)
        muteIcon = findViewById(R.id.mute_icon)
        cameraToggleButton = findViewById(R.id.camera_toggle_button)
        cameraIcon = findViewById(R.id.camera_icon)

        remoteUserNameTextView.text = otherUserName
        if (otherUserImage.isNotEmpty()) {
            val picasso = Picasso.get()
            picasso.load(otherUserImage)
                .networkPolicy(NetworkPolicy.OFFLINE)
                .transform(CircleTransform())
                .placeholder(R.drawable.profile_placeholder)
                .into(remoteUserImageView, object : Callback {
                    override fun onSuccess() {}
                    override fun onError(e: Exception?) {
                        picasso.load(otherUserImage)
                            .transform(CircleTransform())
                            .placeholder(R.drawable.profile_placeholder)
                            .into(remoteUserImageView)
                    }
                })
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        findViewById<FrameLayout>(R.id.end_call_button).setOnClickListener { endCall() }

        muteButton.setOnClickListener {
            isMuted = callManager.toggleMute()
            muteIcon.setImageResource(if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic)
        }

        cameraToggleButton.setOnClickListener {
            isVideoEnabled = callManager.toggleVideo()
            cameraIcon.setImageResource(if (isVideoEnabled) R.drawable.ic_videocam else R.drawable.ic_videocam_off)
            localSurfaceView?.visibility = if (isVideoEnabled) View.VISIBLE else View.GONE
        }

        // Switch Camera (Front/Back)
        findViewById<FrameLayout>(R.id.switch_camera_button).setOnClickListener {
            callManager.switchCamera()
        }
    }

    private fun checkPermissions(): Boolean {
        return callManager.hasRequiredPermissions(true)
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeCall()
            } else {
                Toast.makeText(this, "Permissions required.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun initializeCall() {
        if (!callManager.initialize(enableVideo = true)) {
            Toast.makeText(this, "Failed to initialize call", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Setup Local Video (Smaller Rectangle)
        localSurfaceView = SurfaceView(this)
        // IMPORTANT: This makes the local view sit ON TOP of the remote view
        localSurfaceView?.setZOrderMediaOverlay(true)

        localVideoContainer.addView(localSurfaceView)
        callManager.setupLocalVideo(localSurfaceView!!)

        if (callManager.joinChannel(channelName)) {
            startCallTimer()

            if (!isIncoming && otherUserId.isNotEmpty()) {
                callStatusTextView.text = "Calling..."
                callManager.sendCallNotification(otherUserId, isVideoCall = true)
            } else {
                callStatusTextView.text = "Connecting..."
            }
        } else {
            Toast.makeText(this, "Failed to join call", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupRemoteVideo(uid: Int) {
        if (remoteSurfaceView == null) {
            // Setup Remote Video (Bigger Rectangle)
            remoteSurfaceView = SurfaceView(this)
            // Remote view stays at the bottom (default Z order)
            remoteSurfaceView?.setZOrderMediaOverlay(false)

            remoteVideoContainer.addView(remoteSurfaceView, 0)
            callManager.setupRemoteVideo(uid, remoteSurfaceView!!)
        }
    }

    private fun startCallTimer() {
        callDurationInSeconds = 0
        callDurationTextView.visibility = View.VISIBLE
        updateTimeRunnable = object : Runnable {
            override fun run() {
                callDurationInSeconds++
                updateCallDuration()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateTimeRunnable)
    }

    private fun updateCallDuration() {
        val minutes = callDurationInSeconds / 60
        val seconds = callDurationInSeconds % 60
        callDurationTextView.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun endCall() {
        if (::updateTimeRunnable.isInitialized) handler.removeCallbacks(updateTimeRunnable)
        callManager.destroy()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::updateTimeRunnable.isInitialized) handler.removeCallbacks(updateTimeRunnable)
        callManager.destroy()
    }
}