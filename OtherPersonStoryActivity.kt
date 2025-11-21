package com.AppFlix.i220968_i228810

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.AppFlix.i220968_i228810.utils.CircleTransform
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso

class OtherPersonStoryActivity : AppCompatActivity() {

    private lateinit var storyImage: ImageView
    private lateinit var storyVideo: VideoView
    private lateinit var usernameText: TextView
    private lateinit var timestampText: TextView
    private lateinit var profileImage: ImageView

    private var mediaType: String = MEDIA_TYPE_IMAGE
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_other_person_story)

        storyImage = findViewById(R.id.story_image)
        storyVideo = findViewById(R.id.story_video_view)
        usernameText = findViewById(R.id.username_text)
        timestampText = findViewById(R.id.story_timestamp)
        profileImage = findViewById(R.id.story_profile_image)

        bindStory()
        setupClickListeners()
    }

    override fun onPause() {
        super.onPause()
        if (::storyVideo.isInitialized && storyVideo.isPlaying) {
            storyVideo.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::storyVideo.isInitialized) {
            storyVideo.stopPlayback()
        }
    }

    private fun bindStory() {
        val mediaUrl = intent.getStringExtra(EXTRA_STORY_MEDIA_URL)
        val username = intent.getStringExtra(EXTRA_STORY_USERNAME).orEmpty()
        val profileUrl = intent.getStringExtra(EXTRA_PROFILE_IMAGE_URL).orEmpty()
        val createdAt = intent.getLongExtra(EXTRA_CREATED_AT, 0L)
        mediaType = intent.getStringExtra(EXTRA_STORY_MEDIA_TYPE) ?: MEDIA_TYPE_IMAGE
        userId = intent.getStringExtra(EXTRA_USER_ID)

        if (mediaUrl.isNullOrBlank()) {
            Toast.makeText(this, R.string.error_story_not_available, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        usernameText.text = username.ifBlank { getString(R.string.app_name) }
        if (createdAt > 0) {
            val relativeTime = DateUtils.getRelativeTimeSpanString(
                createdAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            timestampText.text = relativeTime
        }

        // 1. Load Profile Image with Offline Caching
        if (profileUrl.isNotBlank()) {
            val picasso = Picasso.get()
            picasso.load(profileUrl)
                .networkPolicy(NetworkPolicy.OFFLINE)
                .placeholder(R.drawable.profile_placeholder)
                .into(profileImage, object : Callback {
                    override fun onSuccess() {}
                    override fun onError(e: Exception?) {
                        picasso.load(profileUrl)
                            .placeholder(R.drawable.profile_placeholder)
                            .into(profileImage)
                    }
                })
        } else {
            profileImage.setImageResource(R.drawable.profile_placeholder)
        }

        // 2. Load Story Media
        if (mediaType == MEDIA_TYPE_VIDEO) {
            storyImage.visibility = View.GONE
            storyVideo.visibility = View.VISIBLE
            storyVideo.setVideoURI(Uri.parse(mediaUrl))
            storyVideo.setOnPreparedListener { player ->
                player.isLooping = true
                storyVideo.start()
            }
            storyVideo.setOnErrorListener { _, _, _ ->
                Toast.makeText(this, R.string.error_story_not_available, Toast.LENGTH_SHORT).show()
                true
            }
        } else {
            storyVideo.visibility = View.GONE
            storyImage.visibility = View.VISIBLE

            // Offline Caching for Story Image
            val picasso = Picasso.get()
            picasso.load(mediaUrl)
                .networkPolicy(NetworkPolicy.OFFLINE)
                .placeholder(R.drawable.sample_photo_3)
                .into(storyImage, object : Callback {
                    override fun onSuccess() {}
                    override fun onError(e: Exception?) {
                        picasso.load(mediaUrl)
                            .placeholder(R.drawable.sample_photo_3)
                            .into(storyImage)
                    }
                })
        }
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.close_story_button).setOnClickListener {
            finish()
        }

        findViewById<ImageView>(R.id.send_story_button).setOnClickListener {
            Toast.makeText(this, R.string.feature_coming_soon, Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageView>(R.id.more_story_button).setOnClickListener {
            Toast.makeText(this, R.string.feature_coming_soon, Toast.LENGTH_SHORT).show()
        }

        usernameText.setOnClickListener {
            val uid = userId
            if (!uid.isNullOrBlank()) {
                val intent = Intent(this, OtherPersonProfileActivity::class.java).apply {
                    putExtra(OtherPersonProfileActivity.EXTRA_USER_ID, uid)
                }
                startActivity(intent)
            }
        }
    }

    companion object {
        const val EXTRA_STORY_ID = "extra_story_id"
        const val EXTRA_STORY_MEDIA_URL = "extra_story_media_url"
        const val EXTRA_STORY_MEDIA_TYPE = "extra_story_media_type"
        const val EXTRA_STORY_USERNAME = "extra_story_username"
        const val EXTRA_PROFILE_IMAGE_URL = "extra_profile_image_url"
        const val EXTRA_CREATED_AT = "extra_created_at"
        const val EXTRA_USER_ID = "extra_story_user_id"

        private const val MEDIA_TYPE_IMAGE = "image"
        private const val MEDIA_TYPE_VIDEO = "video"
    }
}