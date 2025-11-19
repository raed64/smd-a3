package com.AppFlix.i220968_i228810.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.AppFlix.i220968_i228810.MainActivity
import com.AppFlix.i220968_i228810.R
import com.AppFlix.i220968_i228810.VideoCallActivity
import com.AppFlix.i220968_i228810.VoiceCallActivity
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.data.UserRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val session = SessionManager(this)
        val uid = session.getUserProfile()?.uid
        if (uid != null) {
            val repo = UserRepository()
            repo.updateFCMToken(uid, token) { }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val type = data["type"]

        if (type == "call") {
            handleIncomingCall(data)
        } else {
            // Handle standard notifications (messages, follows, screenshots)
            val title = message.notification?.title ?: "New Notification"
            val body = message.notification?.body ?: ""
            showNotification(title, body)
        }
    }

    private fun handleIncomingCall(data: Map<String, String>) {
        val callType = data["callType"] ?: "video"
        val channelName = data["channelName"] ?: return
        val callerId = data["fromUserId"] ?: return
        val callerName = data["fromUserName"] ?: "Unknown"

        val intent = if (callType == "video") {
            Intent(this, VideoCallActivity::class.java)
        } else {
            Intent(this, VoiceCallActivity::class.java)
        }

        intent.apply {
            putExtra("channel_name", channelName)
            putExtra("other_user_id", callerId)
            putExtra("other_user_name", callerName)
            putExtra("is_incoming", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        startActivity(intent)
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "default_channel"
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_chat)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Default Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}