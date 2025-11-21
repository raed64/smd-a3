package com.AppFlix.i220968_i228810

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.AppFlix.i220968_i228810.data.UserRepository
import com.AppFlix.i220968_i228810.follow.FollowRepository
import com.AppFlix.i220968_i228810.utils.CircleTransform
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso

class OtherPersonProfileActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var followButton: Button
    private lateinit var messageButton: Button
    private lateinit var emailButton: Button
    private lateinit var moreActionsButton: Button

    private lateinit var usernameHeader: TextView
    private lateinit var profileImage: ImageView
    private lateinit var fullNameText: TextView
    private lateinit var postsCountText: TextView
    private lateinit var followersCountText: TextView
    private lateinit var followingCountText: TextView

    private lateinit var followRepository: FollowRepository
    private lateinit var userRepository: UserRepository

    private var targetUserId: String? = null

    // States
    private var isFollowing = false
    private var isRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_other_person_profile)

        targetUserId = intent.getStringExtra(EXTRA_USER_ID)
        if (targetUserId == null) {
            Toast.makeText(this, "Invalid user", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        followRepository = FollowRepository(this)
        userRepository = UserRepository()

        initializeViews()
        loadUserProfile()
        setupClickListeners()

        checkFollowStatus()
        loadCounts()
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.back_button)
        followButton = findViewById(R.id.follow_button)
        messageButton = findViewById(R.id.message_button)
        emailButton = findViewById(R.id.email_button)
        moreActionsButton = findViewById(R.id.more_actions_button)

        usernameHeader = findViewById(R.id.username_header)
        profileImage = findViewById(R.id.profile_image)
        fullNameText = findViewById(R.id.full_name)
        postsCountText = findViewById(R.id.posts_count)
        followersCountText = findViewById(R.id.followers_count)
        followingCountText = findViewById(R.id.following_count)
    }

    private fun loadUserProfile() {
        targetUserId?.let { userId ->
            userRepository.getUserProfileOnce(userId) { profile ->
                if (profile != null) {
                    runOnUiThread {
                        usernameHeader.text = profile.username
                        fullNameText.text = "${profile.firstName} ${profile.lastName}"

                        if (profile.profileImageUrl.isNotEmpty()) {
                            val picasso = Picasso.get()
                            picasso.load(profile.profileImageUrl)
                                .networkPolicy(NetworkPolicy.OFFLINE)
                                .transform(CircleTransform())
                                .placeholder(R.drawable.profile_placeholder)
                                .into(profileImage, object : Callback {
                                    override fun onSuccess() {}
                                    override fun onError(e: Exception?) {
                                        picasso.load(profile.profileImageUrl)
                                            .transform(CircleTransform())
                                            .placeholder(R.drawable.profile_placeholder)
                                            .into(profileImage)
                                    }
                                })
                        }
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }

        followButton.setOnClickListener { toggleFollow() }

        messageButton.setOnClickListener { startChat() }

        emailButton.setOnClickListener {
            Toast.makeText(this, "Email feature coming soon", Toast.LENGTH_SHORT).show()
        }

        moreActionsButton.setOnClickListener {
            Toast.makeText(this, "More actions coming soon", Toast.LENGTH_SHORT).show()
        }

        followersCountText.setOnClickListener {
            if (!targetUserId.isNullOrEmpty()) {
                val intent = Intent(this, com.AppFlix.i220968_i228810.follow.FollowersListActivity::class.java)
                intent.putExtra(com.AppFlix.i220968_i228810.follow.FollowersListActivity.EXTRA_USER_ID, targetUserId)
                startActivity(intent)
            }
        }

        followingCountText.setOnClickListener {
            if (!targetUserId.isNullOrEmpty()) {
                val intent = Intent(this, com.AppFlix.i220968_i228810.follow.FollowingListActivity::class.java)
                intent.putExtra(com.AppFlix.i220968_i228810.follow.FollowingListActivity.EXTRA_USER_ID, targetUserId)
                startActivity(intent)
            }
        }
    }

    private fun checkFollowStatus() {
        targetUserId?.let { userId ->
            followRepository.checkFollowStatus(userId) { status ->
                runOnUiThread {
                    // 'status' comes from PHP: "following", "pending", or "none"
                    isFollowing = (status == "following")
                    isRequested = (status == "pending")
                    updateFollowButton()
                }
            }
        }
    }

    private fun loadCounts() {
        targetUserId?.let { userId ->
            followRepository.getFollowersCount(userId) { count ->
                runOnUiThread { followersCountText.text = formatCount(count) }
            }

            followRepository.getFollowingCount(userId) { count ->
                runOnUiThread { followingCountText.text = formatCount(count) }
            }

            postsCountText.text = "0"
        }
    }

    private fun toggleFollow() {
        val userId = targetUserId ?: return

        followButton.isEnabled = false

        if (isFollowing) {
            // Unfollow
            followRepository.unfollowUser(
                targetId = userId,
                onSuccess = {
                    runOnUiThread {
                        followButton.isEnabled = true
                        isFollowing = false
                        isRequested = false
                        updateFollowButton()
                        loadCounts()
                        Toast.makeText(this, "Unfollowed", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        followButton.isEnabled = true
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } else if (isRequested) {
            // Cancel Request (Technically same as unfollow/delete request)
            followRepository.unfollowUser(
                targetId = userId,
                onSuccess = {
                    runOnUiThread {
                        followButton.isEnabled = true
                        isFollowing = false
                        isRequested = false
                        updateFollowButton()
                        Toast.makeText(this, "Request Cancelled", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        followButton.isEnabled = true
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } else {
            // Follow (Send Request)
            followRepository.followUser(
                targetId = userId,
                onSuccess = {
                    runOnUiThread {
                        followButton.isEnabled = true
                        // Since backend logic puts it in 'status=0' (pending) for requests
                        isFollowing = false
                        isRequested = true
                        updateFollowButton()
                        Toast.makeText(this, "Requested", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        followButton.isEnabled = true
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    private fun updateFollowButton() {
        when {
            isFollowing -> {
                followButton.text = "Following"
                followButton.setTextColor(getColor(R.color.white))
                followButton.setBackgroundResource(R.drawable.follow_button_active) // Dark background
            }
            isRequested -> {
                followButton.text = "Requested"
                followButton.setTextColor(Color.BLACK)
                followButton.setBackgroundColor(Color.LTGRAY) // Gray background
            }
            else -> {
                followButton.text = "Follow"
                followButton.setTextColor(getColor(R.color.white))
                followButton.setBackgroundResource(R.drawable.follow_button_inactive) // Blue background
            }
        }
    }

    private fun startChat() {
        val userId = targetUserId ?: return
        val username = usernameHeader.text.toString()

        val intent = Intent(this, PersonalMessageActivity::class.java).apply {
            putExtra("recipient_uid", userId)
            putExtra("recipient_name", username)
        }
        startActivity(intent)
    }

    private fun formatCount(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
    }
}