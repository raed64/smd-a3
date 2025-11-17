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

class FollowRequestAdapter(
    private val onAcceptClick: (UserProfile) -> Unit,
    private val onDeclineClick: (UserProfile) -> Unit,
    private val onUserClick: (UserProfile) -> Unit
) : RecyclerView.Adapter<FollowRequestAdapter.ViewHolder>() {

    private val requests = mutableListOf<UserProfile>()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.profileImage)
        val username: TextView = itemView.findViewById(R.id.username)
        val fullName: TextView = itemView.findViewById(R.id.fullName)
        // confirmButton is "Accept", deleteButton is "Reject"
        val confirmButton: TextView = itemView.findViewById(R.id.confirmButton)
        val deleteButton: TextView = itemView.findViewById(R.id.deleteButton)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) onUserClick(requests[position])
            }
            confirmButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) onAcceptClick(requests[position])
            }
            deleteButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) onDeclineClick(requests[position])
            }
        }

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
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_follow_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(requests[position])
    }

    override fun getItemCount(): Int = requests.size

    fun submitList(newRequests: List<UserProfile>) {
        requests.clear()
        requests.addAll(newRequests)
        notifyDataSetChanged()
    }

    fun removeRequest(userId: String) {
        val position = requests.indexOfFirst { it.uid == userId }
        if (position != -1) {
            requests.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}