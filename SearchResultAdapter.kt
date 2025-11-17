package com.AppFlix.i220968_i228810.search

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.AppFlix.i220968_i228810.R
import com.AppFlix.i220968_i228810.model.UserProfile
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop

class SearchResultAdapter(
    private val onUserClick: (UserProfile) -> Unit,
    private val onFollowClick: (UserProfile) -> Unit,
    private val onDeleteClick: (UserProfile) -> Unit,
    private val isFollowingCheck: (String) -> Boolean,
    private val isRequestedCheck: (String) -> Boolean
) : RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder>() {

    private val users = mutableListOf<UserProfile>()
    private val followingStatus = mutableMapOf<String, Boolean>()
    private val requestedStatus = mutableMapOf<String, Boolean>()
    private val incomingRequests = mutableSetOf<String>()

    fun submitList(newUsers: List<UserProfile>) {
        users.clear()
        users.addAll(newUsers)
        notifyDataSetChanged()
    }

    fun setIncomingRequests(requestIds: List<String>) {
        incomingRequests.clear()
        incomingRequests.addAll(requestIds)
        notifyDataSetChanged()
    }

    fun removeIncomingRequest(userId: String) {
        incomingRequests.remove(userId)
        val position = users.indexOfFirst { it.uid == userId }
        if (position != -1) notifyItemChanged(position)
    }

    fun updateFollowStatus(userId: String, isFollowing: Boolean) {
        followingStatus[userId] = isFollowing
        requestedStatus[userId] = false
        refreshUserItem(userId)
    }

    fun updateRequestedStatus(userId: String, isRequested: Boolean) {
        requestedStatus[userId] = isRequested
        followingStatus[userId] = false
        refreshUserItem(userId)
    }

    private fun refreshUserItem(userId: String) {
        val position = users.indexOfFirst { it.uid == userId }
        if (position != -1) notifyItemChanged(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return SearchResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    inner class SearchResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profileImage: ImageView = itemView.findViewById(R.id.search_profile_image)
        private val username: TextView = itemView.findViewById(R.id.search_username)
        private val fullName: TextView = itemView.findViewById(R.id.search_full_name)
        private val followButton: Button = itemView.findViewById(R.id.search_follow_button)
        private val deleteButton: Button = itemView.findViewById(R.id.search_delete_button)

        fun bind(user: UserProfile) {
            username.text = user.username
            fullName.text = "${user.firstName} ${user.lastName}"

            Glide.with(itemView.context)
                .load(user.profileImageUrl)
                .transform(CircleCrop())
                .placeholder(R.drawable.profile_placeholder)
                .into(profileImage)

            val isIncoming = incomingRequests.contains(user.uid)
            val isFollowing = followingStatus[user.uid] ?: isFollowingCheck(user.uid)
            val isRequested = requestedStatus[user.uid] ?: isRequestedCheck(user.uid)

            if (isIncoming) {
                // SHOW ACCEPT / REJECT
                deleteButton.visibility = View.VISIBLE
                deleteButton.text = "Reject"
                deleteButton.setTextColor(Color.WHITE)
                deleteButton.setBackgroundResource(R.drawable.follow_button_inactive) // Blue

                followButton.text = "Accept"
                followButton.setTextColor(Color.WHITE)
                followButton.setBackgroundResource(R.drawable.follow_button_inactive) // Blue

                followButton.setOnClickListener { onFollowClick(user) }
                deleteButton.setOnClickListener { onDeleteClick(user) }

            } else {
                // SHOW NORMAL STATES
                deleteButton.visibility = View.GONE

                when {
                    isFollowing -> {
                        followButton.text = "Following"
                        followButton.setTextColor(Color.BLACK)
                        followButton.setBackgroundColor(Color.LTGRAY)
                    }
                    isRequested -> {
                        followButton.text = "Requested"
                        followButton.setTextColor(Color.GRAY)
                        followButton.setBackgroundColor(Color.parseColor("#F0F0F0"))
                    }
                    else -> {
                        followButton.text = "Follow"
                        followButton.setTextColor(Color.WHITE)
                        followButton.setBackgroundResource(R.drawable.follow_button_inactive) // Blue
                    }
                }
                followButton.setOnClickListener { onFollowClick(user) }
            }

            itemView.setOnClickListener { onUserClick(user) }
        }
    }
}