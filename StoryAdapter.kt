package com.AppFlix.i220968_i228810.stories

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.AppFlix.i220968_i228810.R
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.model.Story
import com.AppFlix.i220968_i228810.utils.CircleTransform
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso

class StoryAdapter(
    private val onStoryClicked: (Story) -> Unit,
    private val onAddStoryClicked: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Story>()

    fun submitList(stories: List<Story>) {
        items.clear()
        items.addAll(stories)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size + 1

    override fun getItemViewType(position: Int): Int =
        if (position == 0) VIEW_TYPE_ADD else VIEW_TYPE_STORY

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_ADD -> AddStoryViewHolder(
                inflater.inflate(R.layout.item_story_add, parent, false)
            )
            else -> StoryViewHolder(
                inflater.inflate(R.layout.item_story, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AddStoryViewHolder -> holder.bind(onAddStoryClicked)
            is StoryViewHolder -> holder.bind(items[position - 1], onStoryClicked)
        }
    }

    class AddStoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.add_story_image)

        fun bind(onClick: () -> Unit) {
            val session = SessionManager(itemView.context)
            val profile = session.getUserProfile()

            if (profile?.profileImageUrl?.isNotBlank() == true) {
                val picasso = Picasso.get()
                picasso.load(profile.profileImageUrl)
                    .networkPolicy(NetworkPolicy.OFFLINE)
                    .transform(CircleTransform())
                    .placeholder(R.drawable.profile_placeholder)
                    .into(avatar, object : Callback {
                        override fun onSuccess() {}
                        override fun onError(e: Exception?) {
                            picasso.load(profile.profileImageUrl)
                                .transform(CircleTransform())
                                .placeholder(R.drawable.profile_placeholder)
                                .into(avatar)
                        }
                    })
            } else {
                avatar.setImageResource(R.drawable.profile_placeholder)
            }
            itemView.setOnClickListener { onClick() }
        }
    }

    class StoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.story_image)
        private val username: TextView = itemView.findViewById(R.id.story_username)

        fun bind(story: Story, onClick: (Story) -> Unit) {
            username.text = story.username

            if (story.userProfileImageUrl.isNotBlank()) {
                val picasso = Picasso.get()
                picasso.load(story.userProfileImageUrl)
                    .networkPolicy(NetworkPolicy.OFFLINE)
                    .transform(CircleTransform())
                    .placeholder(R.drawable.profile_placeholder)
                    .into(avatar, object : Callback {
                        override fun onSuccess() {}
                        override fun onError(e: Exception?) {
                            picasso.load(story.userProfileImageUrl)
                                .transform(CircleTransform())
                                .placeholder(R.drawable.profile_placeholder)
                                .into(avatar)
                        }
                    })
            } else {
                avatar.setImageResource(R.drawable.profile_placeholder)
            }

            itemView.setOnClickListener { onClick(story) }
        }
    }

    companion object {
        private const val VIEW_TYPE_ADD = 0
        private const val VIEW_TYPE_STORY = 1
    }
}