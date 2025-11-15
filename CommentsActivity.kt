package com.AppFlix.i220968_i228810.posts

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.AppFlix.i220968_i228810.R
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.model.PostComment
import kotlinx.coroutines.launch

class CommentsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_POST_ID = "extra_post_id"
        const val EXTRA_COMMENT_COUNT = "extra_comment_count"
    }

    private lateinit var commentsAdapter: CommentsAdapter
    private lateinit var postRepository: PostRepository
    private lateinit var sessionManager: SessionManager

    private var postId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comments)

        // --- Get postId from Intent ---
        postId = intent.getStringExtra(EXTRA_POST_ID).orEmpty()
        if (postId.isBlank()) {
            Toast.makeText(this, "DEBUG: postId is blank, check openComments()", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        sessionManager = SessionManager(this)
        postRepository = PostRepository(
            sessionManager = sessionManager,
            api = PostNetworkModule.api,
            contentResolver = contentResolver,
            context = this
        )

        commentsAdapter = CommentsAdapter()

        val closeButton: ImageView = findViewById(R.id.comments_close_button)
        val commentsCount: TextView = findViewById(R.id.comments_count)
        val commentsRecycler: RecyclerView = findViewById(R.id.comments_recycler)
        val emptyState: TextView = findViewById(R.id.comments_empty_state)
        val input: EditText = findViewById(R.id.comment_input)
        val postButton: TextView = findViewById(R.id.comment_post_button)

        commentsRecycler.apply {
            layoutManager = LinearLayoutManager(this@CommentsActivity)
            adapter = commentsAdapter
        }

        // show initial count from intent
        commentsCount.text = resources.getString(
            R.string.post_comments_count,
            intent.getIntExtra(EXTRA_COMMENT_COUNT, 0)
        )

        closeButton.setOnClickListener { finish() }

        fun updateCommentsUi(list: List<PostComment>) {
            commentsAdapter.submitList(list)
            emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            commentsCount.text = resources.getString(R.string.post_comments_count, list.size)
        }

        // --- Load comments from backend ---
        lifecycleScope.launch {
            try {
                val comments = postRepository.fetchComments(postId)
                updateCommentsUi(comments)
            } catch (e: Exception) {
                Toast.makeText(this@CommentsActivity, "Failed to load comments", Toast.LENGTH_SHORT).show()
                updateCommentsUi(emptyList())
            }
        }

        // --- Post new comment ---
        postButton.setOnClickListener {
            val text = input.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) return@setOnClickListener

            lifecycleScope.launch {
                val success = try {
                    postRepository.submitComment(postId, text)
                } catch (e: Exception) {
                    false
                }

                if (success) {
                    input.text?.clear()
                    val comments = postRepository.fetchComments(postId)
                    updateCommentsUi(comments)
                } else {
                    Toast.makeText(
                        this@CommentsActivity,
                        R.string.post_comment_error,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
