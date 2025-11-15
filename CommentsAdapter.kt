package com.AppFlix.i220968_i228810.posts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.AppFlix.i220968_i228810.R
import com.AppFlix.i220968_i228810.model.PostComment
import com.AppFlix.i220968_i228810.utils.CircleTransform
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommentsAdapter : ListAdapter<PostComment, CommentsAdapter.CommentViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profileImage: ImageView = itemView.findViewById(R.id.comment_profile_image)
        private val username: TextView = itemView.findViewById(R.id.comment_username)
        private val commentText: TextView = itemView.findViewById(R.id.comment_text)
        private val timestamp: TextView = itemView.findViewById(R.id.comment_timestamp)

        private val timeFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        fun bind(comment: PostComment) {
            username.text = comment.username
            commentText.text = comment.text
            timestamp.text = timeFormat.format(Date(comment.createdAt))

            if (comment.userProfileImageUrl.isNotEmpty()) {
                val picasso = Picasso.get()
                picasso.load(comment.userProfileImageUrl)
                    .networkPolicy(NetworkPolicy.OFFLINE)
                    .placeholder(R.drawable.profile_placeholder)
                    .transform(CircleTransform())
                    .into(profileImage, object : Callback {
                        override fun onSuccess() {}
                        override fun onError(e: Exception?) {
                            picasso.load(comment.userProfileImageUrl)
                                .placeholder(R.drawable.profile_placeholder)
                                .transform(CircleTransform())
                                .into(profileImage)
                        }
                    })
            } else {
                profileImage.setImageResource(R.drawable.profile_placeholder)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<PostComment>() {
        override fun areItemsTheSame(oldItem: PostComment, newItem: PostComment): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: PostComment, newItem: PostComment): Boolean =
            oldItem == newItem
    }
}