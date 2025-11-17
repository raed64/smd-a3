package com.AppFlix.i220968_i228810.follow

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.AppFlix.i220968_i228810.OtherPersonProfileActivity
import com.AppFlix.i220968_i228810.R
import com.AppFlix.i220968_i228810.data.SessionManager

class FollowingListActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var titleText: TextView
    private lateinit var followingRecycler: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var progressBar: ProgressBar

    private lateinit var followRepository: FollowRepository
    private lateinit var followListAdapter: FollowListAdapter

    private var targetUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_following_list)

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)

        // 1. Use SessionManager for current user ID (matches PHP Auth flow)
        val sessionManager = SessionManager(this)
        targetUserId = intent.getStringExtra(EXTRA_USER_ID)
            ?: sessionManager.getUserProfile()?.uid

        if (targetUserId == null) {
            finish()
            return
        }

        followRepository = FollowRepository(this)

        initializeViews()
        setupRecyclerView()
        setupClickListeners()
        loadFollowing()
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.back_button)
        titleText = findViewById(R.id.title_text)
        followingRecycler = findViewById(R.id.following_recycler)
        emptyState = findViewById(R.id.empty_state)
        progressBar = findViewById(R.id.progress_bar)

        titleText.text = "Following"
    }

    private fun setupRecyclerView() {
        followListAdapter = FollowListAdapter { user ->
            val intent = Intent(this, OtherPersonProfileActivity::class.java).apply {
                putExtra(OtherPersonProfileActivity.EXTRA_USER_ID, user.uid)
            }
            startActivity(intent)
        }

        followingRecycler.apply {
            layoutManager = LinearLayoutManager(this@FollowingListActivity)
            adapter = followListAdapter
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }
    }

    private fun loadFollowing() {
        progressBar.visibility = View.VISIBLE
        targetUserId?.let { userId ->
            // 2. Call the new method that returns List<UserProfile> directly from PHP
            followRepository.getFollowing(userId) { profiles ->
                runOnUiThread {
                    progressBar.visibility = View.GONE

                    if (profiles.isEmpty()) {
                        followingRecycler.visibility = View.GONE
                        emptyState.visibility = View.VISIBLE
                    } else {
                        followingRecycler.visibility = View.VISIBLE
                        emptyState.visibility = View.GONE
                        // 3. Submit the profiles directly to the adapter
                        followListAdapter.submitList(profiles)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
    }
}