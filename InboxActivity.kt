package com.AppFlix.i220968_i228810

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.messaging.ChatListAdapter
import com.AppFlix.i220968_i228810.messaging.ChatRepository
import com.AppFlix.i220968_i228810.model.Chat
import kotlinx.coroutines.launch

class InboxActivity : AppCompatActivity() {

    private lateinit var chatRepository: ChatRepository
    // DECLARATION MUST BE HERE
    private lateinit var chatListAdapter: ChatListAdapter
    private lateinit var chatsRecycler: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: View
    private lateinit var usernameHeader: TextView
    private lateinit var searchInput: EditText
    private lateinit var sessionManager: SessionManager

    private var isShareMode = false
    private var sharePostId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inbox)

        window.statusBarColor = ContextCompat.getColor(this, R.color.light_gray)

        isShareMode = intent.getBooleanExtra("SHARE_POST_MODE", false)
        sharePostId = intent.getStringExtra("SHARE_POST_ID")

        chatRepository = ChatRepository(this)
        sessionManager = SessionManager(this)

        setupViews()
        loadChats()
    }

    private fun setupViews() {
        val backArrow = findViewById<ImageView>(R.id.back_arrow)
        val addMessageIcon = findViewById<ImageView>(R.id.add_message_icon)
        chatsRecycler = findViewById(R.id.chats_recycler)
        progressBar = findViewById(R.id.progress_bar)
        emptyView = findViewById(R.id.empty_view)
        usernameHeader = findViewById(R.id.toolbar_username)
        searchInput = findViewById(R.id.search_input)

        backArrow.setOnClickListener { finish() }

        addMessageIcon.setOnClickListener {
            val intent = Intent(this, SelectUserActivity::class.java)
            startActivity(intent)
        }

        // Initialize Adapter
        chatListAdapter = ChatListAdapter { chat -> openChat(chat) }

        chatsRecycler.layoutManager = LinearLayoutManager(this)
        chatsRecycler.adapter = chatListAdapter

        val currentUser = sessionManager.getUserProfile()
        usernameHeader.text = currentUser?.username ?: "Inbox"

        // Search Listener
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // This calls the filter method in the adapter
                if (::chatListAdapter.isInitialized) {
                    chatListAdapter.filter(s.toString())
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadChats() {
        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        chatsRecycler.visibility = View.GONE

        lifecycleScope.launch {
            chatRepository.getChats().collect { chats ->
                progressBar.visibility = View.GONE
                if (chats.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    chatsRecycler.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    chatsRecycler.visibility = View.VISIBLE
                    // Pass data to adapter
                    chatListAdapter.submitList(chats)
                }
            }
        }
    }

    private fun openChat(chat: Chat) {
        val intent = Intent(this, PersonalMessageActivity::class.java).apply {
            putExtra("recipient_uid", chat.otherUserId)
            putExtra("recipient_name", chat.otherUserName)
            putExtra("recipient_profile_image", chat.otherUserProfileImage)
            if (isShareMode) putExtra("SHARE_POST_ID", sharePostId)
        }
        startActivity(intent)
        if (isShareMode) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::chatListAdapter.isInitialized) {
            chatListAdapter.cleanup()
        }
    }
}