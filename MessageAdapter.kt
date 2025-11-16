package com.AppFlix.i220968_i228810.messaging

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.AppFlix.i220968_i228810.R
import com.AppFlix.i220968_i228810.model.Message
import com.AppFlix.i220968_i228810.model.MessageType
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val currentUserId: String,
    private val onImageClick: (String) -> Unit,
    private val onPostClick: (String) -> Unit,
    private val onMessageLongClick: (Message) -> Unit
) : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback) {

    companion object {
        private const val VIEW_TYPE_TEXT_SENT = 0
        private const val VIEW_TYPE_TEXT_RECEIVED = 1
        private const val VIEW_TYPE_IMAGE_SENT = 2
        private const val VIEW_TYPE_IMAGE_RECEIVED = 3
        private const val VIEW_TYPE_POST_SENT = 4
        private const val VIEW_TYPE_POST_RECEIVED = 5
        private const val VIEW_TYPE_VIDEO_SENT = 6
        private const val VIEW_TYPE_VIDEO_RECEIVED = 7
        private const val VIEW_TYPE_FILE_SENT = 8
        private const val VIEW_TYPE_FILE_RECEIVED = 9

        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        val isSent = message.senderId == currentUserId

        return when (message.type) {
            MessageType.TEXT -> if (isSent) VIEW_TYPE_TEXT_SENT else VIEW_TYPE_TEXT_RECEIVED
            MessageType.IMAGE -> if (isSent) VIEW_TYPE_IMAGE_SENT else VIEW_TYPE_IMAGE_RECEIVED
            MessageType.VIDEO -> if (isSent) VIEW_TYPE_VIDEO_SENT else VIEW_TYPE_VIDEO_RECEIVED
            MessageType.FILE -> if (isSent) VIEW_TYPE_FILE_SENT else VIEW_TYPE_FILE_RECEIVED
            MessageType.POST_SHARE -> if (isSent) VIEW_TYPE_POST_SENT else VIEW_TYPE_POST_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TEXT_SENT, VIEW_TYPE_FILE_SENT -> TextMessageViewHolder(
                inflater.inflate(R.layout.item_message_text_sent, parent, false)
            )
            VIEW_TYPE_TEXT_RECEIVED, VIEW_TYPE_FILE_RECEIVED -> TextMessageViewHolder(
                inflater.inflate(R.layout.item_message_text_received, parent, false)
            )
            VIEW_TYPE_IMAGE_SENT, VIEW_TYPE_VIDEO_SENT -> ImageMessageViewHolder(
                inflater.inflate(R.layout.item_message_image_sent, parent, false)
            )
            VIEW_TYPE_IMAGE_RECEIVED, VIEW_TYPE_VIDEO_RECEIVED -> ImageMessageViewHolder(
                inflater.inflate(R.layout.item_message_image_received, parent, false)
            )
            VIEW_TYPE_POST_SENT -> PostMessageViewHolder(
                inflater.inflate(R.layout.item_message_post_sent, parent, false)
            )
            VIEW_TYPE_POST_RECEIVED -> PostMessageViewHolder(
                inflater.inflate(R.layout.item_message_post_received, parent, false)
            )
            else -> throw IllegalStateException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is TextMessageViewHolder -> holder.bind(message)
            is ImageMessageViewHolder -> holder.bind(message)
            is PostMessageViewHolder -> holder.bind(message)
        }
    }

    inner class TextMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView = itemView.findViewById(R.id.text_message)
        private val textTime: TextView = itemView.findViewById(R.id.text_time)

        fun bind(message: Message) {
            if (message.deleted) {
                textMessage.text = "Message deleted"
                textMessage.alpha = 0.5f
            } else {
                textMessage.alpha = 1.0f
                if (message.type == MessageType.FILE) {
                    textMessage.text = "📎 Attachment (Tap to open)"
                    textMessage.setOnClickListener {
                        if (message.mediaUrl.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(message.mediaUrl))
                            itemView.context.startActivity(intent)
                        }
                    }
                } else {
                    textMessage.text = message.text
                }
            }
            textTime.text = formatTime(message.sentAt)

            itemView.setOnLongClickListener {
                onMessageLongClick(message)
                true
            }
        }
    }

    inner class ImageMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageMessage: ImageView = itemView.findViewById(R.id.image_message)
        private val textTime: TextView = itemView.findViewById(R.id.text_time)

        fun bind(message: Message) {
            if (message.deleted) {
                imageMessage.setImageResource(R.drawable.ic_image_placeholder)
                imageMessage.alpha = 0.5f
            } else {
                imageMessage.alpha = 1.0f

                if (message.type == MessageType.VIDEO) {
                    // Picasso cannot load video thumbnails. Show a video placeholder instead.
                    imageMessage.setImageResource(R.drawable.ic_video_call) // Use a video icon
                    imageMessage.scaleType = ImageView.ScaleType.CENTER_INSIDE

                    imageMessage.setOnClickListener {
                        if (message.mediaUrl.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(message.mediaUrl))
                            intent.setDataAndType(Uri.parse(message.mediaUrl), "video/*")
                            itemView.context.startActivity(intent)
                        }
                    }
                } else {
                    // Handle Image Loading with Picasso (Offline first)
                    imageMessage.scaleType = ImageView.ScaleType.CENTER_CROP

                    val picasso = Picasso.get()
                    val url = message.mediaUrl

                    if (url.isNotEmpty()) {
                        // 1. Try Offline
                        picasso.load(url)
                            .networkPolicy(NetworkPolicy.OFFLINE)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .into(imageMessage, object : Callback {
                                override fun onSuccess() {}
                                override fun onError(e: Exception?) {
                                    // 2. Try Online if cache fails
                                    picasso.load(url)
                                        .placeholder(R.drawable.ic_image_placeholder)
                                        .into(imageMessage)
                                }
                            })
                    }

                    imageMessage.setOnClickListener {
                        if (message.mediaUrl.isNotEmpty()) {
                            onImageClick(message.mediaUrl)
                        }
                    }
                }
            }
            textTime.text = formatTime(message.sentAt)

            itemView.setOnLongClickListener {
                onMessageLongClick(message)
                true
            }
        }
    }

    inner class PostMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textPostShare: TextView = itemView.findViewById(R.id.text_post_share)
        private val textTime: TextView = itemView.findViewById(R.id.text_time)

        fun bind(message: Message) {
            textPostShare.text = message.text
            textTime.text = formatTime(message.sentAt)
            itemView.setOnClickListener { onPostClick(message.postId) }
        }
    }

    private fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }

    object MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message) = oldItem.messageId == newItem.messageId
        override fun areContentsTheSame(oldItem: Message, newItem: Message) = oldItem == newItem
    }
}