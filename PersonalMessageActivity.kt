package com.AppFlix.i220968_i228810

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.AppFlix.i220968_i228810.calling.CallManager
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.data.api.ApiClient
import com.AppFlix.i220968_i228810.messaging.ChatRepository
import com.AppFlix.i220968_i228810.messaging.MessageAdapter
import com.AppFlix.i220968_i228810.model.Message
import com.AppFlix.i220968_i228810.presence.PresenceManager
import com.AppFlix.i220968_i228810.utils.CircleTransform
import com.AppFlix.i220968_i228810.utils.ScreenshotObserver
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.launch

class PersonalMessageActivity : AppCompatActivity() {

    private lateinit var chatRepository: ChatRepository
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var recipientNameText: TextView
    private lateinit var recipientStatusText: TextView
    private lateinit var recipientProfileImage: ImageView
    private lateinit var rootLayout: RelativeLayout

    private var otherUserId: String? = null
    private var otherUserName: String? = null
    private var otherUserProfileImage: String? = null

    private var progressDialog: ProgressDialog? = null
    private var screenshotObserver: ScreenshotObserver? = null
    private var presenceCleaner: Runnable? = null
    private val database = FirebaseDatabase.getInstance()

    private var isVanishMode = false

    private val mediaPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                sendMedia(uri)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setupScreenshotDetection()
        } else {
            Log.w(TAG, "Screenshot detection permission denied")
        }
    }

    companion object {
        const val EXTRA_OTHER_USER_ID = "OTHER_USER_ID"
        private const val TAG = "PersonalMessageActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personal_message)

        otherUserId = intent.getStringExtra("recipient_uid")
            ?: intent.getStringExtra("OTHER_USER_ID")
        otherUserName = intent.getStringExtra("recipient_name")
        otherUserProfileImage = intent.getStringExtra("recipient_profile_image")

        if (otherUserId == null) {
            Toast.makeText(this, "Invalid user", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        rootLayout = findViewById(R.id.root_layout)
        recipientNameText = findViewById(R.id.recipient_name_text)
        recipientStatusText = findViewById(R.id.recipient_status_text)
        recipientProfileImage = findViewById(R.id.recipient_profile_image)

        recipientNameText.text = otherUserName ?: "User"

        // PICASSO IMPLEMENTATION (With Offline Support)
        if (!otherUserProfileImage.isNullOrEmpty()) {
            val picasso = Picasso.get()
            picasso.load(otherUserProfileImage)
                .networkPolicy(NetworkPolicy.OFFLINE)
                .transform(CircleTransform())
                .placeholder(R.drawable.profile_placeholder)
                .into(recipientProfileImage, object : Callback {
                    override fun onSuccess() {}
                    override fun onError(e: Exception?) {
                        picasso.load(otherUserProfileImage)
                            .transform(CircleTransform())
                            .placeholder(R.drawable.profile_placeholder)
                            .into(recipientProfileImage)
                    }
                })
        }

        chatRepository = ChatRepository(this)

        setupRecyclerView()
        setupClickListeners()
        initializeChat()
        requestScreenshotPermission()
        observeRecipientStatus()

        val sharePostId = intent.getStringExtra("SHARE_POST_ID")
        if (sharePostId != null) {
            sendPostShareMessage(sharePostId)
        }
    }

    private fun setupRecyclerView() {
        messagesRecyclerView = findViewById(R.id.messages_recycler_view)
        messageInput = findViewById(R.id.message_input)

        val currentUserId = SessionManager(this).getUserProfile()?.uid ?: ""

        messageAdapter = MessageAdapter(
            currentUserId = currentUserId,
            onImageClick = { imageUrl ->
                Toast.makeText(this, "Image viewer coming soon", Toast.LENGTH_SHORT).show()
            },
            onPostClick = { postId ->
                Toast.makeText(this, "Post viewer coming soon", Toast.LENGTH_SHORT).show()
            },
            onMessageLongClick = { message ->
                showMessageOptions(message)
            }
        )

        messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PersonalMessageActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffY = e1.y - e2.y

                if (diffY > 150 && Math.abs(velocityY) > 100) {
                    toggleVanishMode(true)
                    return true
                }
                else if (diffY < -150 && Math.abs(velocityY) > 100) {
                    toggleVanishMode(false)
                    return true
                }
                return false
            }
        })

        messagesRecyclerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun initializeChat() {
        val targetUid = otherUserId ?: return
        lifecycleScope.launch {
            chatRepository.getMessages(targetUid).collect { messages ->
                messageAdapter.submitList(messages)
                if (messages.isNotEmpty()) messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
            }
        }
        lifecycleScope.launch {
            chatRepository.vanishModeState.collect { active ->
                isVanishMode = active
                updateVanishUI(active)
            }
        }
    }

    private fun observeRecipientStatus() {
        val targetUid = otherUserId ?: return
        presenceCleaner = PresenceManager.getInstance().observeUserPresence(targetUid) { presence ->
            runOnUiThread {
                if (presence?.status == "online") {
                    recipientStatusText.text = "Active Now"
                    recipientStatusText.setTextColor(ContextCompat.getColor(this, R.color.instagram_green))
                    recipientStatusText.visibility = View.VISIBLE
                } else {
                    recipientStatusText.text = "Offline"
                    recipientStatusText.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
                    recipientStatusText.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun toggleVanishMode(enable: Boolean) {
        if (isVanishMode == enable) return
        otherUserId?.let { uid -> chatRepository.toggleVanishMode(uid, enable) }
    }

    private fun updateVanishUI(active: Boolean) {
        if (active) {
            rootLayout.setBackgroundColor(Color.BLUE)
            window.statusBarColor = Color.BLUE
            messageInput.setBackgroundColor(Color.DKGRAY)
            messageInput.setTextColor(Color.WHITE)
            messageInput.setHintTextColor(Color.LTGRAY)
            recipientNameText.setTextColor(Color.WHITE)
            recipientStatusText.setTextColor(Color.WHITE)
        } else {
            rootLayout.setBackgroundColor(Color.WHITE)
            window.statusBarColor = ContextCompat.getColor(this, R.color.light_gray)
            messageInput.setBackgroundColor(Color.WHITE)
            messageInput.setTextColor(Color.BLACK)
            messageInput.setHintTextColor(Color.GRAY)
            recipientNameText.setTextColor(Color.BLACK)
        }
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.back_button).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.video_call_button).setOnClickListener { startVideoCall() }
        findViewById<ImageView>(R.id.voice_call_button).setOnClickListener { startVoiceCall() }
        findViewById<androidx.cardview.widget.CardView>(R.id.send_button).setOnClickListener { sendTextMessage() }
        findViewById<ImageView>(R.id.gallery_button).setOnClickListener { openMediaPicker() }
    }

    private fun sendTextMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) return
        chatRepository.sendMessage(otherUserId!!, text)
        messageInput.text.clear()
    }

    private fun openMediaPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*", "application/pdf"))
        }
        mediaPicker.launch(intent)
    }

    private fun sendMedia(uri: Uri) {
        progressDialog = ProgressDialog(this).apply {
            setMessage("Sending file...")
            setCancelable(false)
            show()
        }
        chatRepository.sendMediaMessage(otherUserId!!, uri)
        rootLayout.postDelayed({ progressDialog?.dismiss() }, 1500)
    }

    private fun sendPostShareMessage(postId: String) {
        chatRepository.sendPostShareMessage(otherUserId!!, postId)
        Toast.makeText(this, "Post shared", Toast.LENGTH_SHORT).show()
    }

    private fun showMessageOptions(message: Message) {
        val now = System.currentTimeMillis()
        if (now - message.sentAt > 5 * 60 * 1000) {
            Toast.makeText(this, "Cannot edit/delete messages older than 5 minutes", Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf("Edit", "Delete")
        AlertDialog.Builder(this)
            .setTitle("Message Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditDialog(message)
                    1 -> confirmDeleteMessage(message)
                }
            }
            .show()
    }

    private fun showEditDialog(message: Message) {
        val input = EditText(this).apply {
            setText(message.text)
            hint = "Edit message"
        }
        AlertDialog.Builder(this)
            .setTitle("Edit Message")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newText = input.text.toString().trim()
                if (newText.isNotEmpty()) {
                    chatRepository.editMessage(message.messageId, newText, { Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show() }, {})
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteMessage(message: Message) {
        AlertDialog.Builder(this)
            .setTitle("Delete")
            .setMessage("Are you sure?")
            .setPositiveButton("Delete") { _, _ ->
                chatRepository.deleteMessage(message.messageId, { Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show() }, {})
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startVideoCall() {
        val currentUserId = SessionManager(this).getUserProfile()?.uid ?: return
        val channelName = CallManager.generateChannelName(currentUserId, otherUserId!!)
        val intent = Intent(this, VideoCallActivity::class.java).apply {
            putExtra(VideoCallActivity.EXTRA_CHANNEL_NAME, channelName)
            putExtra(VideoCallActivity.EXTRA_OTHER_USER_ID, otherUserId)
            putExtra(VideoCallActivity.EXTRA_OTHER_USER_NAME, otherUserName)
            putExtra(VideoCallActivity.EXTRA_OTHER_USER_IMAGE, otherUserProfileImage)
        }
        startActivity(intent)
    }

    private fun startVoiceCall() {
        val currentUserId = SessionManager(this).getUserProfile()?.uid
        val targetUid = otherUserId
        if (currentUserId == null || targetUid == null) {
            Toast.makeText(this, "Unable to start call", Toast.LENGTH_SHORT).show()
            return
        }
        val channelName = CallManager.generateChannelName(currentUserId, targetUid)
        val intent = Intent(this, VoiceCallActivity::class.java).apply { // Launch VoiceCallActivity
            putExtra(VideoCallActivity.EXTRA_CHANNEL_NAME, channelName)
            putExtra(VideoCallActivity.EXTRA_OTHER_USER_ID, targetUid)
            putExtra("other_user_name", otherUserName)
            putExtra("other_user_image", otherUserProfileImage)
        }
        startActivity(intent)

        // Send Voice Call Notification via CallManager
        val callManager = CallManager(this)
        callManager.sendCallNotification(otherUserId!!, isVideoCall = false)
    }

    private fun requestScreenshotPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            setupScreenshotDetection()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun setupScreenshotDetection() {
        screenshotObserver = ScreenshotObserver(this) { handleScreenshotDetected() }
        screenshotObserver?.startObserving()
    }

    private fun handleScreenshotDetected() {
        val currentUserId = SessionManager(this).getUserProfile()?.uid ?: return
        val currentUserName = SessionManager(this).getUserProfile()?.username ?: "Someone"
        Toast.makeText(this, "Screenshot detected! Notifying user...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                ApiClient.authApi.sendScreenshotAlert(currentUserId, otherUserId!!, currentUserName)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        progressDialog?.dismiss()
        screenshotObserver?.cleanup()
        presenceCleaner?.run()
        otherUserId?.let { chatRepository.clearVanishMessages(it) }
    }
}