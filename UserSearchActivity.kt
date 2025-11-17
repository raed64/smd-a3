package com.AppFlix.i220968_i228810.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.AppFlix.i220968_i228810.OtherPersonProfileActivity
import com.AppFlix.i220968_i228810.R
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.follow.FollowRepository
import com.AppFlix.i220968_i228810.model.UserProfile

class UserSearchActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var searchRecycler: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyStateText: TextView
    private lateinit var searchProgress: ProgressBar
    private lateinit var backButton: ImageView

    private lateinit var btnFilterAll: Button
    private lateinit var btnFilterFollowers: Button
    private lateinit var btnFilterFollowing: Button

    private lateinit var searchRepository: SearchRepository
    private lateinit var followRepository: FollowRepository
    private lateinit var searchAdapter: SearchResultAdapter
    private lateinit var sessionManager: SessionManager

    private var currentFilter = FilterType.ALL
    private var cachedFollowerIds = mutableSetOf<String>()
    private var cachedFollowingIds = mutableSetOf<String>()
    private val incomingRequestIds = mutableSetOf<String>()

    enum class FilterType { ALL, FOLLOWERS, FOLLOWING }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_search)

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)

        searchRepository = SearchRepository(this)
        followRepository = FollowRepository(this)
        sessionManager = SessionManager(this)

        initializeViews()
        setupRecyclerView()
        setupSearchInput()
        setupClickListeners()

        updateFilterUi(FilterType.ALL)
    }

    override fun onResume() {
        super.onResume()
        // Refresh data to ensure "Following" status is accurate
        preloadSocialData()
        loadIncomingRequests()

        val query = searchInput.text.toString().trim()
        handleSearch(query)
    }

    private fun initializeViews() {
        searchInput = findViewById(R.id.search_input)
        searchRecycler = findViewById(R.id.search_results_recycler)
        emptyState = findViewById(R.id.empty_state)
        emptyStateText = findViewById(R.id.empty_state_text)
        searchProgress = findViewById(R.id.search_progress)
        backButton = findViewById(R.id.back_button)

        btnFilterAll = findViewById(R.id.filter_all)
        btnFilterFollowers = findViewById(R.id.filter_followers)
        btnFilterFollowing = findViewById(R.id.filter_following)
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchResultAdapter(
            onUserClick = { user -> openUserProfile(user) },
            onFollowClick = { user -> handleMainButtonClick(user) },
            onDeleteClick = { user -> rejectRequest(user) },
            isFollowingCheck = { uid -> cachedFollowingIds.contains(uid) },
            isRequestedCheck = { false }
        )

        searchRecycler.apply {
            layoutManager = LinearLayoutManager(this@UserSearchActivity)
            adapter = searchAdapter
        }
    }

    private fun setupSearchInput() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                handleSearch(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }

        btnFilterAll.setOnClickListener {
            updateFilterUi(FilterType.ALL)
            handleSearch(searchInput.text.toString())
        }
        btnFilterFollowers.setOnClickListener {
            updateFilterUi(FilterType.FOLLOWERS)
            handleSearch(searchInput.text.toString())
        }
        btnFilterFollowing.setOnClickListener {
            updateFilterUi(FilterType.FOLLOWING)
            handleSearch(searchInput.text.toString())
        }
    }

    // --- DATA LOADING & CACHING ---

    private fun preloadSocialData() {
        val currentUserId = sessionManager.getUserProfile()?.uid ?: return

        // 1. Update Following Cache
        followRepository.getFollowingList(currentUserId) { ids ->
            cachedFollowingIds.clear()
            cachedFollowingIds.addAll(ids)
            // If we are on "Following" tab, refresh UI instantly
            if (currentFilter == FilterType.FOLLOWING) handleSearch(searchInput.text.toString())
        }
        // 2. Update Followers Cache
        followRepository.getFollowersList(currentUserId) { ids ->
            cachedFollowerIds.clear()
            cachedFollowerIds.addAll(ids)
            if (currentFilter == FilterType.FOLLOWERS) handleSearch(searchInput.text.toString())
        }
    }

    private fun loadIncomingRequests() {
        followRepository.getRequests { users ->
            runOnUiThread {
                incomingRequestIds.clear()
                incomingRequestIds.addAll(users.map { it.uid })
                searchAdapter.setIncomingRequests(users.map { it.uid })
            }
        }
    }

    // --- SEARCH LOGIC ---

    private fun handleSearch(query: String) {
        if (query.isEmpty()) {
            when (currentFilter) {
                FilterType.ALL -> loadSuggestedUsers()
                FilterType.FOLLOWERS -> loadFollowers()
                FilterType.FOLLOWING -> loadFollowing()
            }
        } else {
            performSearchAndFilter(query)
        }
    }

    private fun loadFollowers() {
        val currentUserId = sessionManager.getUserProfile()?.uid ?: return
        searchProgress.visibility = View.VISIBLE
        searchRecycler.visibility = View.GONE
        emptyState.visibility = View.GONE

        followRepository.getFollowers(currentUserId) { users ->
            runOnUiThread {
                searchProgress.visibility = View.GONE
                cachedFollowerIds.clear()
                cachedFollowerIds.addAll(users.map { it.uid })
                showList(users, "No followers yet")
            }
        }
    }

    private fun loadFollowing() {
        val currentUserId = sessionManager.getUserProfile()?.uid ?: return
        searchProgress.visibility = View.VISIBLE
        searchRecycler.visibility = View.GONE
        emptyState.visibility = View.GONE

        followRepository.getFollowing(currentUserId) { users ->
            runOnUiThread {
                searchProgress.visibility = View.GONE
                cachedFollowingIds.clear()
                cachedFollowingIds.addAll(users.map { it.uid })
                showList(users, "Not following anyone")
            }
        }
    }

    private fun performSearchAndFilter(query: String) {
        searchProgress.visibility = View.VISIBLE
        searchRecycler.visibility = View.GONE
        emptyState.visibility = View.GONE

        searchRepository.searchUsers(query) { users ->
            runOnUiThread {
                searchProgress.visibility = View.GONE

                val filteredUsers = when (currentFilter) {
                    FilterType.ALL -> users
                    FilterType.FOLLOWERS -> users.filter { cachedFollowerIds.contains(it.uid) }
                    FilterType.FOLLOWING -> users.filter { cachedFollowingIds.contains(it.uid) }
                }

                val emptyMsg = if (currentFilter == FilterType.ALL)
                    "No users found matching \"$query\""
                else
                    "No results in ${currentFilter.name.lowercase()}"

                showList(filteredUsers, emptyMsg)
            }
        }
    }

    private fun loadSuggestedUsers() {
        searchProgress.visibility = View.VISIBLE
        searchRecycler.visibility = View.GONE
        emptyState.visibility = View.GONE

        searchRepository.getSuggestedUsers(30) { users ->
            runOnUiThread {
                searchProgress.visibility = View.GONE
                if (users.isEmpty()) {
                    showList(emptyList(), "No users available")
                } else {
                    emptyStateText.text = "Suggested users"
                    showList(users)
                }
            }
        }
    }

    private fun showList(users: List<UserProfile>, emptyMessage: String = "No users found") {
        if (users.isEmpty()) {
            searchRecycler.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            emptyStateText.text = emptyMessage
        } else {
            searchRecycler.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            searchAdapter.submitList(users)
            checkFollowStatusForUsers(users)
        }
    }

    // --- STATUS CHECK & CACHE SYNC (CRITICAL) ---

    private fun checkFollowStatusForUsers(users: List<UserProfile>) {
        users.forEach { user ->
            followRepository.checkFollowStatus(user.uid) { status ->
                runOnUiThread {
                    when (status) {
                        "following" -> {
                            // Update UI
                            searchAdapter.updateFollowStatus(user.uid, true)
                            searchAdapter.updateRequestedStatus(user.uid, false)
                            // Sync Cache
                            cachedFollowingIds.add(user.uid)
                        }
                        "pending" -> {
                            searchAdapter.updateFollowStatus(user.uid, false)
                            searchAdapter.updateRequestedStatus(user.uid, true)
                            cachedFollowingIds.remove(user.uid)
                        }
                        else -> {
                            searchAdapter.updateFollowStatus(user.uid, false)
                            searchAdapter.updateRequestedStatus(user.uid, false)
                            cachedFollowingIds.remove(user.uid)
                        }
                    }
                }
            }
        }
    }

    // --- ACTIONS ---

    private fun handleMainButtonClick(user: UserProfile) {
        if (incomingRequestIds.contains(user.uid)) {
            acceptRequest(user)
        } else {
            toggleFollow(user)
        }
    }

    private fun acceptRequest(user: UserProfile) {
        followRepository.acceptRequest(user.uid) {
            runOnUiThread {
                Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show()
                incomingRequestIds.remove(user.uid)
                searchAdapter.removeIncomingRequest(user.uid)
                cachedFollowerIds.add(user.uid)
                checkFollowStatusForUsers(listOf(user))
            }
        }
    }

    private fun rejectRequest(user: UserProfile) {
        followRepository.rejectRequest(user.uid) {
            runOnUiThread {
                Toast.makeText(this, "Rejected", Toast.LENGTH_SHORT).show()
                incomingRequestIds.remove(user.uid)
                searchAdapter.removeIncomingRequest(user.uid)
                checkFollowStatusForUsers(listOf(user))
            }
        }
    }

    private fun toggleFollow(user: UserProfile) {
        followRepository.checkFollowStatus(user.uid) { currentStatus ->
            runOnUiThread {
                when (currentStatus) {
                    "following" -> {
                        // Unfollow
                        searchAdapter.updateFollowStatus(user.uid, false)
                        cachedFollowingIds.remove(user.uid) // Update Cache immediately

                        followRepository.unfollowUser(user.uid, {
                            // Success logic handled by cache removal above
                            if (currentFilter == FilterType.FOLLOWING) handleSearch(searchInput.text.toString())
                        }, {
                            // On Failure, revert
                            runOnUiThread { searchAdapter.updateFollowStatus(user.uid, true) }
                        })
                    }
                    "pending" -> {
                        searchAdapter.updateRequestedStatus(user.uid, false)
                        followRepository.unfollowUser(user.uid, {}, {
                            runOnUiThread { searchAdapter.updateRequestedStatus(user.uid, true) }
                        })
                    }
                    else -> {
                        // Follow (-> Requested)
                        searchAdapter.updateRequestedStatus(user.uid, true)
                        followRepository.followUser(user.uid, {}, {
                            runOnUiThread { searchAdapter.updateRequestedStatus(user.uid, false) }
                        })
                    }
                }
            }
        }
    }

    private fun updateFilterUi(type: FilterType) {
        currentFilter = type
        val activeColor = ContextCompat.getColor(this, R.color.instagram_blue)
        val inactiveColor = ContextCompat.getColor(this, R.color.light_gray)
        val activeText = ContextCompat.getColor(this, R.color.white)
        val inactiveText = ContextCompat.getColor(this, R.color.black)

        listOf(btnFilterAll, btnFilterFollowers, btnFilterFollowing).forEach {
            it.setBackgroundColor(inactiveColor)
            it.setTextColor(inactiveText)
        }

        val selectedBtn = when (type) {
            FilterType.ALL -> btnFilterAll
            FilterType.FOLLOWERS -> btnFilterFollowers
            FilterType.FOLLOWING -> btnFilterFollowing
        }
        selectedBtn.setBackgroundColor(activeColor)
        selectedBtn.setTextColor(activeText)
    }

    private fun openUserProfile(user: UserProfile) {
        val intent = Intent(this, OtherPersonProfileActivity::class.java).apply {
            putExtra(OtherPersonProfileActivity.EXTRA_USER_ID, user.uid)
        }
        startActivity(intent)
    }
}