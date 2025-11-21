package com.AppFlix.i220968_i228810

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.model.UserProfile
import com.AppFlix.i220968_i228810.search.SearchRepository
import com.AppFlix.i220968_i228810.utils.CircleTransform
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso

class SelectUserActivity : AppCompatActivity() {

    private lateinit var searchRepository: SearchRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var usersRecycler: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var userAdapter: UserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_user)

        window.statusBarColor = ContextCompat.getColor(this, R.color.light_gray)

        searchRepository = SearchRepository(this)
        sessionManager = SessionManager(this)

        setupViews()
        loadUsers()
    }

    private fun setupViews() {
        val backArrow = findViewById<ImageView>(R.id.back_arrow)
        backArrow.setOnClickListener { finish() }

        usersRecycler = findViewById(R.id.users_recycler)
        progressBar = findViewById(R.id.progress_bar)
        emptyView = findViewById(R.id.empty_view)

        userAdapter = UserAdapter { user ->
            openChatWithUser(user)
        }

        usersRecycler.layoutManager = LinearLayoutManager(this)
        usersRecycler.adapter = userAdapter
    }

    private fun loadUsers() {
        val currentUser = sessionManager.getUserProfile() ?: return

        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        usersRecycler.visibility = View.GONE

        searchRepository.getSuggestedUsers(limit = 100) { allUsers ->
            val otherUsers = allUsers.filter { it.uid != currentUser.uid }

            runOnUiThread {
                progressBar.visibility = View.GONE

                if (otherUsers.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    usersRecycler.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    usersRecycler.visibility = View.VISIBLE
                    userAdapter.submitList(otherUsers)
                }
            }
        }
    }

    private fun openChatWithUser(user: UserProfile) {
        val intent = Intent(this, PersonalMessageActivity::class.java).apply {
            putExtra("recipient_uid", user.uid)
            putExtra("recipient_name", user.username)
            putExtra("recipient_profile_image", user.profileImageUrl)
        }
        startActivity(intent)
        finish()
    }
}

class UserAdapter(
    private val onUserClick: (UserProfile) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private var users = listOf<UserProfile>()

    fun submitList(newUsers: List<UserProfile>) {
        users = newUsers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_select, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount() = users.size

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val profileImage: ImageView = view.findViewById(R.id.profile_image)
        private val nameText: TextView = view.findViewById(R.id.name_text)
        private val usernameText: TextView = view.findViewById(R.id.username_text)
        private val container: CardView = view.findViewById(R.id.user_container)

        fun bind(user: UserProfile) {
            val fullName = "${user.firstName} ${user.lastName}".trim()
            nameText.text = if (fullName.isNotEmpty()) fullName else user.username
            usernameText.text = "@${user.username}"

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

            container.setOnClickListener { onUserClick(user) }
        }
    }
}