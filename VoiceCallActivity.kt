package com.AppFlix.i220968_i228810

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.AppFlix.i220968_i228810.calling.CallManager
import com.AppFlix.i220968_i228810.utils.CircleTransform
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso

class VoiceCallActivity : AppCompatActivity() {

    private lateinit var callManager: CallManager
    private var callDurationInSeconds = 0
    private lateinit var callDurationTextView: TextView
    private lateinit var callStatusTextView: TextView
    private lateinit var callerNameTextView: TextView
    private lateinit var callerImageView: ImageView
    private lateinit var muteButton: FrameLayout
    private lateinit var muteIcon: ImageView
    private lateinit var speakerButton: FrameLayout
    private lateinit var speakerIcon: ImageView

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateTimeRunnable: Runnable

    private var isMuted = false
    private var isSpeakerOn = true
    private var channelName: String = ""
    private var otherUserName: String = ""
    private var otherUserImage: String = ""
    // otherUserId removed

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1002
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_OTHER_USER_ID = "other_user_id"
        const val EXTRA_OTHER_USER_NAME = "other_user_name"
        const val EXTRA_OTHER_USER_IMAGE = "other_user_image"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_call)

        window.statusBarColor = android.graphics.Color.TRANSPARENT

        channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""
        otherUserName = intent.getStringExtra(EXTRA_OTHER_USER_NAME) ?: "Unknown"
        otherUserImage = intent.getStringExtra(EXTRA_OTHER_USER_IMAGE) ?: ""

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
                    callStatusTextView.text = "Connected"
                    callDurationTextView.visibility = View.VISIBLE
                }
            }

            override fun onUserOffline(uid: Int) {
                runOnUiThread {
                    callStatusTextView.text = "Call ended"
                    endCall()
                }
            }

            override fun onConnectionStateChanged(state: Int, reason: Int) {}

            override fun onError(errorCode: Int) {
                runOnUiThread {
                    Toast.makeText(this@VoiceCallActivity, "Call error: $errorCode", Toast.LENGTH_SHORT).show()
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
        callerNameTextView = findViewById(R.id.caller_name)
        callerImageView = findViewById(R.id.caller_image)
        muteButton = findViewById(R.id.mute_button)
        muteIcon = findViewById(R.id.mute_icon)
        speakerButton = findViewById(R.id.speaker_button)
        speakerIcon = findViewById(R.id.speaker_icon)

        callerNameTextView.text = otherUserName
        if (otherUserImage.isNotEmpty()) {
            val picasso = Picasso.get()
            picasso.load(otherUserImage)
                .networkPolicy(NetworkPolicy.OFFLINE)
                .transform(CircleTransform())
                .placeholder(R.drawable.profile_placeholder)
                .into(callerImageView, object : Callback {
                    override fun onSuccess() {}
                    override fun onError(e: Exception?) {
                        picasso.load(otherUserImage)
                            .transform(CircleTransform())
                            .placeholder(R.drawable.profile_placeholder)
                            .into(callerImageView)
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

        speakerButton.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            callManager.setSpeakerPhone(isSpeakerOn)
            speakerIcon.setImageResource(if (isSpeakerOn) R.drawable.ic_volume_up else R.drawable.ic_volume_off)
        }
    }

    private fun checkPermissions(): Boolean {
        return callManager.hasRequiredPermissions(false)
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeCall()
            } else {
                Toast.makeText(this, "Microphone permission required.", Toast.LENGTH_LONG).show()
                handler.postDelayed({ finish() }, 2500)
            }
        }
    }

    private fun initializeCall() {
        if (!callManager.initialize(enableVideo = false)) {
            Toast.makeText(this, "Failed to initialize call", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        callManager.setSpeakerPhone(true)

        // Simply join the channel (removed notification sending)
        if (callManager.joinChannel(channelName)) {
            startCallTimer()
            callStatusTextView.text = "Calling..."
        } else {
            Toast.makeText(this, "Failed to join call", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun startCallTimer() {
        callDurationInSeconds = 0
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