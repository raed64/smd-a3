package com.AppFlix.i220968_i228810

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.data.api.ApiClient
import com.AppFlix.i220968_i228810.follow.FollowRepository
import com.AppFlix.i220968_i228810.posts.CreatePostActivity
import com.AppFlix.i220968_i228810.utils.CircleTransform
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private val sessionManager by lazy { SessionManager(this) }
    private val followRepository by lazy { FollowRepository(this) }

    private lateinit var profileImage: ImageView
    private lateinit var usernameText: TextView
    private lateinit var fullNameText: TextView
    private lateinit var bioText: TextView

    private lateinit var myFollowersCountText: TextView
    private lateinit var myFollowingCountText: TextView
    private lateinit var myPostsCountText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)

        initializeViews()
        setupClickListeners()
        setupTabSwitching()
    }

    override fun onResume() {
        super.onResume()
        loadUserInfo()
        loadCounts()
    }

    private fun initializeViews() {
        profileImage = findViewById(R.id.profile_image)
        usernameText = findViewById(R.id.username_text)
        fullNameText = findViewById(R.id.full_name_text)
        bioText = findViewById(R.id.bio_text)

        myFollowersCountText = findViewById(R.id.my_followers_count)
        myFollowingCountText = findViewById(R.id.my_following_count)
        myPostsCountText = findViewById(R.id.my_posts_count)
    }

    private fun loadUserInfo() {
        val user = sessionManager.getUserProfile()
        if (user != null) {
            usernameText.text = user.username
            fullNameText.text = "${user.firstName} ${user.lastName}".trim()

            if (user.bio.isNotEmpty()) {
                bioText.text = user.bio
                bioText.visibility = View.VISIBLE
            } else {
                bioText.visibility = View.GONE
            }

            if (user.profileImageUrl.isNotEmpty()) {
                // Picasso Offline Implementation
                val picasso = Picasso.get()
                picasso.load(user.profileImageUrl)
                    .networkPolicy(NetworkPolicy.OFFLINE)
                    .transform(CircleTransform())
                    .placeholder(R.drawable.profile_placeholder)
                    .into(profileImage, object : Callback {
                        override fun onSuccess() {}
                        override fun onError(e: Exception?) {
                            picasso.load(user.profileImageUrl)
                                .transform(CircleTransform())
                                .placeholder(R.drawable.profile_placeholder)
                                .into(profileImage)
                        }
                    })
            } else {
                profileImage.setImageResource(R.drawable.profile_placeholder)
            }
        }
    }

    // ... (Remaining methods: loadCounts, formatCount, setupClickListeners, setupHighlightCircles, setupBottomNavigation, setupTabSwitching, signOut) ...
    // I am omitting the rest of the file methods for brevity as they remain unchanged,
    // but please retain them in your actual implementation.

    private fun loadCounts() {
        val currentUserId = sessionManager.getUserProfile()?.uid ?: return
        followRepository.getFollowersCount(currentUserId) { count ->
            runOnUiThread { myFollowersCountText.text = formatCount(count) }
        }
        followRepository.getFollowingCount(currentUserId) { count ->
            runOnUiThread { myFollowingCountText.text = formatCount(count) }
        }
        myPostsCountText.text = "0"
    }

    private fun formatCount(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    private fun setupClickListeners() {
        findViewById<TextView>(R.id.edit_profile_button).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        findViewById<TextView>(R.id.sign_out_button).setOnClickListener {
            signOut()
        }
        findViewById<LinearLayout>(R.id.followers_stat).setOnClickListener {
            val intent = Intent(this, com.AppFlix.i220968_i228810.follow.FollowersListActivity::class.java)
            intent.putExtra("extra_user_id", sessionManager.getUserProfile()?.uid)
            startActivity(intent)
        }
        findViewById<LinearLayout>(R.id.following_stat).setOnClickListener {
            val intent = Intent(this, com.AppFlix.i220968_i228810.follow.FollowingListActivity::class.java)
            intent.putExtra("extra_user_id", sessionManager.getUserProfile()?.uid)
            startActivity(intent)
        }
        setupHighlightCircles()
        setupBottomNavigation()
    }

    private fun setupHighlightCircles() {}

    private fun setupBottomNavigation() {
        findViewById<LinearLayout>(R.id.home_nav).setOnClickListener {
            startActivity(Intent(this, MainFeedActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.search_nav).setOnClickListener {
            startActivity(Intent(this, DiscoverFeedActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.add_nav).setOnClickListener {
            startActivity(Intent(this, CreatePostActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.heart_nav).setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }
    }

    private fun setupTabSwitching() {
        val gridTab = findViewById<LinearLayout>(R.id.grid_tab)
        val taggedTab = findViewById<LinearLayout>(R.id.tagged_tab)
        gridTab.setOnClickListener { showGridTab() }
        taggedTab.setOnClickListener { showTaggedTab() }
        showGridTab()
    }

    private fun showGridTab() {
        findViewById<ImageView>(R.id.grid_icon).setColorFilter(ContextCompat.getColor(this, R.color.instagram_red))
        findViewById<View>(R.id.grid_underline).setBackgroundColor(ContextCompat.getColor(this, R.color.instagram_red))
        findViewById<ImageView>(R.id.tagged_icon).setColorFilter(ContextCompat.getColor(this, R.color.text_gray))
        findViewById<View>(R.id.tagged_underline).setBackgroundColor(ContextCompat.getColor(this, R.color.light_gray))
    }

    private fun showTaggedTab() {
        findViewById<ImageView>(R.id.tagged_icon).setColorFilter(ContextCompat.getColor(this, R.color.instagram_red))
        findViewById<View>(R.id.tagged_underline).setBackgroundColor(ContextCompat.getColor(this, R.color.instagram_red))
        findViewById<ImageView>(R.id.grid_icon).setColorFilter(ContextCompat.getColor(this, R.color.text_gray))
        findViewById<View>(R.id.grid_underline).setBackgroundColor(ContextCompat.getColor(this, R.color.light_gray))
    }

    private fun signOut() {
        lifecycleScope.launch {
            try { ApiClient.authApi.logout() } catch (_: Exception) { }
            finally {
                sessionManager.clear()
                Toast.makeText(this@ProfileActivity, R.string.sign_out_toast, Toast.LENGTH_SHORT).show()
                val intent = Intent(this@ProfileActivity, QuickLoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }
    }
}