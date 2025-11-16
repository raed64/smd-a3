package com.AppFlix.i220968_i228810.messaging

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.AppFlix.i220968_i228810.R
import com.AppFlix.i220968_i228810.model.Chat
import com.AppFlix.i220968_i228810.presence.PresenceManager
import com.AppFlix.i220968_i228810.utils.CircleTransform
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.*

class ChatListAdapter(
    private val onChatClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>() {

    private var originalChats = listOf<Chat>()
    private var displayChats = listOf<Chat>()
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    private val presenceManager = PresenceManager.getInstance()
    private val presenceCleaners = mutableMapOf<String, Runnable>()

    fun submitList(newChats: List<Chat>) {
        originalChats = newChats.sortedByDescending { it.updatedAt }
        displayChats = originalChats
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        displayChats = if (query.isEmpty()) {
            originalChats
        } else {
            originalChats.filter { chat ->
                chat.otherUserName.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }

    fun cleanup() {
        presenceCleaners.values.forEach { it.run() }
        presenceCleaners.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_list, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(displayChats[position])
    }

    override fun getItemCount() = displayChats.size

    inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val profileImage: ImageView = view.findViewById(R.id.profile_image)
        private val usernameText: TextView = view.findViewById(R.id.username_text)
        private val lastMessageText: TextView = view.findViewById(R.id.last_message_text)
        private val timeText: TextView = view.findViewById(R.id.time_text)
        private val onlineIndicator: View = view.findViewById(R.id.online_indicator)

        fun bind(chat: Chat) {
            usernameText.text = chat.otherUserName.ifEmpty { "User" }
            lastMessageText.text = chat.lastMessage.ifEmpty { "Start chatting" }
            timeText.text = formatTime(chat.updatedAt)

            if (chat.otherUserProfileImage.isNotEmpty()) {
                val picasso = Picasso.get()
                picasso.load(chat.otherUserProfileImage)
                    .networkPolicy(NetworkPolicy.OFFLINE)
                    .transform(CircleTransform())
                    .placeholder(R.drawable.profile_placeholder)
                    .into(profileImage, object : Callback {
                        override fun onSuccess() {}
                        override fun onError(e: Exception?) {
                            picasso.load(chat.otherUserProfileImage)
                                .transform(CircleTransform())
                                .placeholder(R.drawable.profile_placeholder)
                                .into(profileImage)
                        }
                    })
            } else {
                profileImage.setImageResource(R.drawable.profile_placeholder)
            }

            observePresence(chat.otherUserId)
            itemView.setOnClickListener { onChatClick(chat) }
        }

        private fun observePresence(userId: String) {
            presenceCleaners[userId]?.run()
            val cleaner = presenceManager.observeUserPresence(userId) { presence ->
                itemView.post {
                    onlineIndicator.visibility = if (presence?.status == "online") View.VISIBLE else View.GONE
                    if (presence?.status == "online") {
                        onlineIndicator.setBackgroundResource(R.drawable.online_indicator)
                    }
                }
            }
            presenceCleaners[userId] = cleaner
        }

        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            return when {
                diff < 60000 -> "now"
                diff < 86400000 -> dateFormat.format(Date(timestamp))
                else -> dayFormat.format(Date(timestamp))
            }
        }
    }
}