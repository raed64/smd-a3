package com.AppFlix.i220968_i228810.posts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.AppFlix.i220968_i228810.R
import com.AppFlix.i220968_i228810.model.PostFeedItem
import com.AppFlix.i220968_i228810.utils.CircleTransform
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PostAdapter(
    private val onLikeClicked: (PostFeedItem) -> Unit,
    private val onCommentClicked: (PostFeedItem) -> Unit,
    private val onShareClicked: (PostFeedItem, View) -> Unit
) : ListAdapter<PostFeedItem, PostAdapter.PostViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profileImage: ImageView = itemView.findViewById(R.id.post_profile_image)
        private val username: TextView = itemView.findViewById(R.id.post_username)
        private val location: TextView = itemView.findViewById(R.id.post_location)
        private val postImage: ImageView = itemView.findViewById(R.id.post_image)
        private val likeButton: ImageButton = itemView.findViewById(R.id.post_like_button)
        private val commentButton: ImageButton = itemView.findViewById(R.id.post_comment_button)
        private val shareButton: ImageButton = itemView.findViewById(R.id.post_share_button)
        private val bookmarkButton: ImageButton = itemView.findViewById(R.id.post_bookmark_button)
        private val likeSummary: TextView = itemView.findViewById(R.id.post_like_summary)
        private val caption: TextView = itemView.findViewById(R.id.post_caption)
        private val timestamp: TextView = itemView.findViewById(R.id.post_timestamp)

        private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        fun bind(item: PostFeedItem) {
            val post = item.post

            username.text = post.username
            caption.text = itemView.context.getString(R.string.post_caption_format, post.username, post.caption)
            likeSummary.text = itemView.context.resources.getQuantityString(
                R.plurals.post_likes,
                post.likesCount,
                post.likesCount
            )
            timestamp.text = dateFormat.format(Date(post.createdAt))

            location.visibility = View.GONE

            // --- OFFLINE CACHING WITH PICASSO ---
            val picasso = Picasso.get()

            // 1. Profile Image
            if (post.userProfileImageUrl.isNotEmpty()) {
                picasso.load(post.userProfileImageUrl)
                    .networkPolicy(NetworkPolicy.OFFLINE) // Try cache first
                    .placeholder(R.drawable.profile_placeholder)
                    .transform(CircleTransform())
                    .into(profileImage, object : Callback {
                        override fun onSuccess() {}
                        override fun onError(e: Exception?) {
                            // If cache fails, try network
                            picasso.load(post.userProfileImageUrl)
                                .placeholder(R.drawable.profile_placeholder)
                                .transform(CircleTransform())
                                .into(profileImage)
                        }
                    })
            } else {
                profileImage.setImageResource(R.drawable.profile_placeholder)
            }

            // 2. Post Image
            if (post.mediaUrl.isNotEmpty()) {
                picasso.load(post.mediaUrl)
                    .networkPolicy(NetworkPolicy.OFFLINE) // Try cache first
                    .placeholder(R.drawable.sample_photo_1)
                    .fit()
                    .centerCrop()
                    .into(postImage, object : Callback {
                        override fun onSuccess() {}
                        override fun onError(e: Exception?) {
                            // If cache fails, try network
                            picasso.load(post.mediaUrl)
                                .placeholder(R.drawable.sample_photo_1)
                                .fit()
                                .centerCrop()
                                .into(postImage)
                        }
                    })
            } else {
                // Fallback if no URL
                postImage.setImageResource(R.drawable.sample_photo_1)
            }

            likeButton.setImageResource(R.drawable.ic_heart)
            likeButton.setColorFilter(
                if (item.isLikedByCurrentUser) itemView.context.getColor(R.color.instagram_red)
                else itemView.context.getColor(android.R.color.black)
            )

            likeButton.setOnClickListener { onLikeClicked(item) }
            commentButton.setOnClickListener { onCommentClicked(item) }
            shareButton.setOnClickListener { onShareClicked(item, shareButton) }

            bookmarkButton.setOnClickListener {
                // Placeholder for future save functionality
            }
        }
    }

    fun updateItem(updated: PostFeedItem) {
        val index = currentList.indexOfFirst { it.post.id == updated.post.id }
        if (index != -1) {
            val mutable = currentList.toMutableList()
            mutable[index] = updated
            submitList(mutable)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<PostFeedItem>() {
        override fun areItemsTheSame(oldItem: PostFeedItem, newItem: PostFeedItem): Boolean =
            oldItem.post.id == newItem.post.id

        override fun areContentsTheSame(oldItem: PostFeedItem, newItem: PostFeedItem): Boolean =
            oldItem == newItem
    }
}