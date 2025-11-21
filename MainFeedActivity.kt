package com.AppFlix.i220968_i228810

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.AppFlix.i220968_i228810.calling.IncomingCallObserver
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.model.PostFeedItem
import com.AppFlix.i220968_i228810.model.Story
import com.AppFlix.i220968_i228810.posts.CommentsActivity
import com.AppFlix.i220968_i228810.posts.CreatePostActivity
import com.AppFlix.i220968_i228810.posts.PostAdapter
import com.AppFlix.i220968_i228810.posts.PostNetworkModule
import com.AppFlix.i220968_i228810.posts.PostRepository
import com.AppFlix.i220968_i228810.presence.PresenceManager
import com.AppFlix.i220968_i228810.search.UserSearchActivity
import com.AppFlix.i220968_i228810.stories.StoryAdapter
import com.AppFlix.i220968_i228810.stories.StoryNetworkModule
import com.AppFlix.i220968_i228810.stories.StoryRepository
import kotlinx.coroutines.launch

class MainFeedActivity : AppCompatActivity() {

    // Stories UI
    private lateinit var storiesRecycler: RecyclerView
    private lateinit var storiesEmptyState: TextView
    private lateinit var storyAdapter: StoryAdapter
    private lateinit var storyRepository: StoryRepository
    private lateinit var sessionManager: SessionManager

    // Posts UI
    private lateinit var postsRecycler: RecyclerView
    private lateinit var postsEmptyState: TextView
    private lateinit var postAdapter: PostAdapter
    private lateinit var postRepository: PostRepository

    // Call Observer
    private var incomingCallObserver: IncomingCallObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_feed)

        window.statusBarColor = ContextCompat.getColor(this, R.color.light_gray)

        sessionManager = SessionManager(this)

        // In onCreate(), right after sessionManager init
        val currentUid = sessionManager.getUserProfile()?.uid
        if (currentUid != null) {
            // Start Heartbeat (Keep me online)
            com.AppFlix.i220968_i228810.presence.PresenceManager.getInstance().startHeartbeat(currentUid)

            // Start Incoming Call Observer
            incomingCallObserver = com.AppFlix.i220968_i228810.calling.IncomingCallObserver(this, currentUid)
            incomingCallObserver?.start()
        }

        // Initialize Call Observer
        sessionManager.getUserProfile()?.uid?.let { uid ->
            incomingCallObserver = IncomingCallObserver(this, uid)
            incomingCallObserver?.start()
        }

        // ... rest of your existing onCreate code ...

        // Header icons
        val cameraIcon = findViewById<ImageView>(R.id.camera_icon)
        val igtvIcon = findViewById<ImageView>(R.id.igtv_icon)
        val directMessageIcon = findViewById<ImageView>(R.id.direct_message_icon)

        cameraIcon.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }

        directMessageIcon.setOnClickListener {
            val intent = Intent(this, InboxActivity::class.java)
            startActivity(intent)
        }

        setupStoriesSection()
        setupPostsSection()

        // Bottom navigation
        val searchNav = findViewById<ImageView>(R.id.nav_search)
        val addNav = findViewById<ImageView>(R.id.nav_add)
        val heartNav = findViewById<ImageView>(R.id.nav_heart)
        val profileNav = findViewById<ImageView>(R.id.nav_profile)

        searchNav.setOnClickListener {
            val intent = Intent(this, UserSearchActivity::class.java)
            startActivity(intent)
        }

        addNav.setOnClickListener {
            val intent = Intent(this, CreatePostActivity::class.java)
            startActivity(intent)
        }

        heartNav.setOnClickListener {
            val intent = Intent(this, NotificationActivity::class.java)
            startActivity(intent)
        }

        profileNav.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        // Add this check in onCreate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Optional: Stop heartbeat if you want them to go offline instantly
        // PresenceManager.getInstance().stopHeartbeat()
        // Note: It's often better to leave it running in a Service or just let it timeout on the server

        incomingCallObserver?.stop()
    }

    override fun onStart() {
        super.onStart()
        loadStories()
        loadPosts()
    }

    private fun setupStoriesSection() {
        storyRepository = StoryRepository(
            sessionManager = sessionManager,
            api = StoryNetworkModule.api,
            contentResolver = contentResolver,
            context = this
        )
        storiesRecycler = findViewById(R.id.stories_recycler)
        storiesEmptyState = findViewById(R.id.stories_empty_state)

        storyAdapter = StoryAdapter(
            onStoryClicked = { story -> openStory(story) },
            onAddStoryClicked = { openAddStoryComposer() }
        )

        storiesRecycler.apply {
            layoutManager = LinearLayoutManager(
                this@MainFeedActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = storyAdapter
        }
    }

    private fun loadStories() {
        lifecycleScope.launch {
            try {
                val stories = storyRepository.fetchStories()
                updateStoriesUi(stories)
            } catch (e: Exception) {
                updateStoriesUi(emptyList())
            }
        }
    }

    private fun updateStoriesUi(stories: List<Story>) {
        storyAdapter.submitList(stories)
        storiesEmptyState.visibility = if (stories.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupPostsSection() {
        postsRecycler = findViewById(R.id.posts_recycler)
        postsEmptyState = findViewById(R.id.posts_empty_state)

        postRepository = PostRepository(
            sessionManager = sessionManager,
            api = PostNetworkModule.api,
            contentResolver = contentResolver,
            context = this
        )

        postAdapter = PostAdapter(
            onLikeClicked = { item -> togglePostLike(item) },
            onCommentClicked = { item -> openComments(item) },
            onShareClicked = { item, _ -> sharePost(item) }
        )

        postsRecycler.apply {
            layoutManager = LinearLayoutManager(this@MainFeedActivity)
            adapter = postAdapter
        }
    }

    private fun loadPosts() {
        lifecycleScope.launch {
            try {
                val posts = postRepository.fetchPosts()
                updatePostsUi(posts)
            } catch (e: Exception) {
                updatePostsUi(emptyList())
            }
        }
    }

    private fun updatePostsUi(posts: List<PostFeedItem>) {
        postAdapter.submitList(posts)
        postsEmptyState.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun togglePostLike(item: PostFeedItem) {
        lifecycleScope.launch {
            try {
                val response = postRepository.toggleLike(
                    postId = item.post.id,
                    currentlyLiked = item.isLikedByCurrentUser
                )

                val updatedItem = item.copy(
                    isLikedByCurrentUser = response.likedByUser,
                    post = item.post.copy(likesCount = response.likesCount)
                )

                postAdapter.updateItem(updatedItem)

            } catch (e: Exception) {
                Toast.makeText(
                    this@MainFeedActivity,
                    "Failed to like post",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openComments(item: PostFeedItem) {
        val intent = Intent(this, CommentsActivity::class.java).apply {
            putExtra(CommentsActivity.EXTRA_POST_ID, item.post.id)
            putExtra(CommentsActivity.EXTRA_COMMENT_COUNT, item.post.commentsCount)
        }
        startActivity(intent)
    }

    private fun sharePost(item: PostFeedItem) {
        val intent = Intent(this, InboxActivity::class.java).apply {
            putExtra("SHARE_POST_ID", item.post.id)
            putExtra("SHARE_POST_MODE", true)
        }
        startActivity(intent)
    }

    private fun openStory(story: Story) {
        val intent = Intent(this, OtherPersonStoryActivity::class.java).apply {
            putExtra(OtherPersonStoryActivity.EXTRA_STORY_ID, story.id)
            putExtra(OtherPersonStoryActivity.EXTRA_STORY_MEDIA_URL, story.mediaUrl)
            putExtra(OtherPersonStoryActivity.EXTRA_STORY_USERNAME, story.username)
            putExtra(OtherPersonStoryActivity.EXTRA_STORY_MEDIA_TYPE, story.mediaType)
            putExtra(OtherPersonStoryActivity.EXTRA_PROFILE_IMAGE_URL, story.userProfileImageUrl)
            putExtra(OtherPersonStoryActivity.EXTRA_CREATED_AT, story.createdAt)
            putExtra(OtherPersonStoryActivity.EXTRA_USER_ID, story.userId)
        }
        startActivity(intent)
    }

    private fun openAddStoryComposer() {
        val intent = Intent(this, AddStoryActivity::class.java)
        startActivity(intent)
    }
}