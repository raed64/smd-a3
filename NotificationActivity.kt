package com.AppFlix.i220968_i228810

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.AppFlix.i220968_i228810.data.UserRepository
import com.AppFlix.i220968_i228810.follow.FollowRepository
import com.AppFlix.i220968_i228810.follow.FollowRequestAdapter
import com.AppFlix.i220968_i228810.model.UserProfile

class NotificationActivity : AppCompatActivity() {

    private lateinit var followingTab: TextView
    private lateinit var youTab: TextView
    private lateinit var followingUnderline: View
    private lateinit var youUnderline: View
    private lateinit var followingContent: NestedScrollView
    private lateinit var youContent: NestedScrollView
    private lateinit var followRequestsHeader: TextView
    private lateinit var followRequestsRecycler: RecyclerView

    private lateinit var followRepository: FollowRepository
    private lateinit var userRepository: UserRepository
    private lateinit var followRequestAdapter: FollowRequestAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        followRepository = FollowRepository(this)
        userRepository = UserRepository()

        setupViews()
        setupClickListeners()
        setupBottomNavigation()
        setupFollowRequests()

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
    }

    private fun setupViews() {
        followingTab = findViewById(R.id.following_tab)
        youTab = findViewById(R.id.you_tab)
        followingUnderline = findViewById(R.id.following_underline)
        youUnderline = findViewById(R.id.you_underline)
        followingContent = findViewById(R.id.following_content)
        youContent = findViewById(R.id.you_content)
        followRequestsHeader = findViewById(R.id.follow_requests_header)
        followRequestsRecycler = findViewById(R.id.follow_requests_recycler)
    }

    private fun setupFollowRequests() {
        followRequestAdapter = FollowRequestAdapter(
            onAcceptClick = { user -> acceptFollowRequest(user) },
            onDeclineClick = { user -> declineFollowRequest(user) },
            onUserClick = { user -> openUserProfile(user) }
        )

        followRequestsRecycler.apply {
            layoutManager = LinearLayoutManager(this@NotificationActivity)
            adapter = followRequestAdapter
        }

        loadFollowRequests()
    }

    private fun loadFollowRequests() {
        followRepository.getRequests { profiles ->
            runOnUiThread {
                if (profiles.isEmpty()) {
                    followRequestsHeader.visibility = View.GONE
                    followRequestsRecycler.visibility = View.GONE
                } else {
                    followRequestsHeader.visibility = View.VISIBLE
                    followRequestsRecycler.visibility = View.VISIBLE
                    followRequestAdapter.submitList(profiles)
                }
            }
        }
    }

    private fun acceptFollowRequest(user: UserProfile) {
        followRepository.acceptRequest(user.uid) {
            runOnUiThread {
                followRequestAdapter.removeRequest(user.uid)
                Toast.makeText(this, "Accepted request from ${user.username}", Toast.LENGTH_SHORT).show()

                if (followRequestAdapter.itemCount == 0) {
                    followRequestsHeader.visibility = View.GONE
                    followRequestsRecycler.visibility = View.GONE
                }
            }
        }
    }

    private fun declineFollowRequest(user: UserProfile) {
        followRepository.rejectRequest(user.uid) {
            runOnUiThread {
                followRequestAdapter.removeRequest(user.uid)
                Toast.makeText(this, "Declined request", Toast.LENGTH_SHORT).show()
                if (followRequestAdapter.itemCount == 0) {
                    followRequestsHeader.visibility = View.GONE
                    followRequestsRecycler.visibility = View.GONE
                }
            }
        }
    }

    private fun openUserProfile(user: UserProfile) {
        val intent = Intent(this, OtherPersonProfileActivity::class.java).apply {
            putExtra(OtherPersonProfileActivity.EXTRA_USER_ID, user.uid)
        }
        startActivity(intent)
    }

    private fun setupClickListeners() {
        followingTab.setOnClickListener { showFollowingTab() }
        youTab.setOnClickListener { showYouTab() }
    }

    private fun setupBottomNavigation() {
        findViewById<LinearLayout>(R.id.home_nav).setOnClickListener {
            finish()
        }
        findViewById<LinearLayout>(R.id.search_nav).setOnClickListener {
            startActivity(Intent(this, DiscoverFeedActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.add_nav).setOnClickListener { }
        findViewById<LinearLayout>(R.id.heart_nav).setOnClickListener { }
        findViewById<LinearLayout>(R.id.profile_nav).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun showFollowingTab() {
        followingTab.setTextColor(ContextCompat.getColor(this, R.color.instagram_red))
        followingTab.setTypeface(null, android.graphics.Typeface.BOLD)
        youTab.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
        youTab.setTypeface(null, android.graphics.Typeface.NORMAL)

        followingUnderline.setBackgroundColor(ContextCompat.getColor(this, R.color.instagram_red))
        youUnderline.setBackgroundColor(ContextCompat.getColor(this, R.color.light_gray))

        followingContent.visibility = View.VISIBLE
        youContent.visibility = View.GONE
    }

    private fun showYouTab() {
        youTab.setTextColor(ContextCompat.getColor(this, R.color.instagram_red))
        youTab.setTypeface(null, android.graphics.Typeface.BOLD)
        followingTab.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
        followingTab.setTypeface(null, android.graphics.Typeface.NORMAL)

        youUnderline.setBackgroundColor(ContextCompat.getColor(this, R.color.instagram_red))
        followingUnderline.setBackgroundColor(ContextCompat.getColor(this, R.color.light_gray))

        youContent.visibility = View.VISIBLE
        followingContent.visibility = View.GONE
    }
}