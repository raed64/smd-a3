package com.AppFlix.i220968_i228810.calling

import android.content.Context
import android.content.Intent
import android.util.Log
import com.AppFlix.i220968_i228810.VideoCallActivity
import com.AppFlix.i220968_i228810.VoiceCallActivity
import com.google.firebase.database.*

class IncomingCallObserver(private val context: Context, private val currentUserId: String) {

    private val database = FirebaseDatabase.getInstance().reference
    private var listener: ChildEventListener? = null

    fun start() {
        if (listener != null) return

        val notificationsRef = database.child("notifications").child(currentUserId)

        listener = notificationsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val type = snapshot.child("type").value as? String
                if (type == "call") {
                    handleIncomingCall(snapshot)
                    // Remove the notification so it doesn't trigger again
                    snapshot.ref.removeValue()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun handleIncomingCall(snapshot: DataSnapshot) {
        val callType = snapshot.child("callType").value as? String ?: "video"
        val callerId = snapshot.child("fromUserId").value as? String ?: return
        val callerName = snapshot.child("fromUserName").value as? String ?: "Unknown"

        // Generate the same channel name as the sender
        val channelName = CallManager.generateChannelName(currentUserId, callerId)

        val intent = if (callType == "video") {
            Intent(context, VideoCallActivity::class.java)
        } else {
            Intent(context, VoiceCallActivity::class.java)
        }

        intent.apply {
            putExtra("channel_name", channelName)
            putExtra("other_user_id", callerId)
            putExtra("other_user_name", callerName)
            putExtra("is_incoming", true) // Flag to prevent re-sending notification
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    fun stop() {
        listener?.let {
            database.child("notifications").child(currentUserId).removeEventListener(it)
        }
        listener = null
    }
}