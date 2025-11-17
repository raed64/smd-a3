package com.AppFlix.i220968_i228810.follow

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.AppFlix.i220968_i228810.R
import com.AppFlix.i220968_i228810.model.UserProfile
import com.AppFlix.i220968_i228810.utils.CircleTransform
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso

class FollowListAdapter(
    private val onUserClick: (UserProfile) -> Unit
) : RecyclerView.Adapter<FollowListAdapter.FollowListViewHolder>() {

    private val users = mutableListOf<UserProfile>()

    fun submitList(newUsers: List<UserProfile>) {
        users.clear()
        users.addAll(newUsers)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowListViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_follow_list, parent, false)
        return FollowListViewHolder(view)
    }

    override fun onBindViewHolder(holder: FollowListViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    inner class FollowListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profileImage: ImageView = itemView.findViewById(R.id.follow_list_profile_image)
        private val username: TextView = itemView.findViewById(R.id.follow_list_username)
        private val fullName: TextView = itemView.findViewById(R.id.follow_list_full_name)

        fun bind(user: UserProfile) {
            username.text = user.username
            fullName.text = "${user.firstName} ${user.lastName}"

            if (user.profileImageUrl.isNotEmpty()) {
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

            itemView.setOnClickListener { onUserClick(user) }
        }
    }
}