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

class FollowersListActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var titleText: TextView
    private lateinit var followersRecycler: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var progressBar: ProgressBar

    private lateinit var followRepository: FollowRepository
    private lateinit var followListAdapter: FollowListAdapter

    private var targetUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_followers_list)

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)

        // 1. Use SessionManager for fallback user ID (matches your PHP Auth flow)
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
        loadFollowers()
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.back_button)
        titleText = findViewById(R.id.title_text)
        followersRecycler = findViewById(R.id.followers_recycler)
        emptyState = findViewById(R.id.empty_state)
        progressBar = findViewById(R.id.progress_bar)

        titleText.text = "Followers"
    }

    private fun setupRecyclerView() {
        followListAdapter = FollowListAdapter { user ->
            val intent = Intent(this, OtherPersonProfileActivity::class.java).apply {
                putExtra(OtherPersonProfileActivity.EXTRA_USER_ID, user.uid)
            }
            startActivity(intent)
        }

        followersRecycler.apply {
            layoutManager = LinearLayoutManager(this@FollowersListActivity)
            adapter = followListAdapter
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }
    }

    private fun loadFollowers() {
        progressBar.visibility = View.VISIBLE
        targetUserId?.let { userId ->
            // 2. Call the new method that returns List<UserProfile> directly from PHP
            followRepository.getFollowers(userId) { profiles ->
                runOnUiThread {
                    progressBar.visibility = View.GONE

                    if (profiles.isEmpty()) {
                        followersRecycler.visibility = View.GONE
                        emptyState.visibility = View.VISIBLE
                    } else {
                        followersRecycler.visibility = View.VISIBLE
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