package com.AppFlix.i220968_i228810.follow

import com.AppFlix.i220968_i228810.data.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

/**
 * Repository for managing follow requests
 * Implements request/accept/decline flow
 */
class FollowRequestRepository(private val sessionManager: SessionManager) {

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val NODE_FOLLOW_REQUESTS = "follow_requests" // follow_requests/{userId}/{requesterId} = timestamp
        private const val NODE_FOLLOWERS = "followers"
        private const val NODE_FOLLOWING = "following"
        private const val NODE_USERS = "users"
    }

    /**
     * Send a follow request
     */
    fun sendFollowRequest(
        targetUserId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            onError("User not logged in")
            return
        }

        if (currentUserId == targetUserId) {
            onError("Cannot follow yourself")
            return
        }

        val timestamp = System.currentTimeMillis()
        
        // Get current user info to send notification
        database.child(NODE_USERS).child(currentUserId).get()
            .addOnSuccessListener { userSnapshot ->
                val firstName = userSnapshot.child("firstName").getValue(String::class.java) ?: "Someone"
                val lastName = userSnapshot.child("lastName").getValue(String::class.java) ?: ""
                val senderName = "$firstName $lastName".trim()
                
                // Add to target user's incoming requests
                database.child(NODE_FOLLOW_REQUESTS)
                    .child(targetUserId)
                    .child(currentUserId)
                    .setValue(timestamp)
                    .addOnSuccessListener {
                        // Create notification entry for Cloud Function to send push
                        val notificationData = hashMapOf<String, Any>(
                            "type" to "follow_request",
                            "fromUserId" to currentUserId,
                            "fromUserName" to senderName,
                            "timestamp" to timestamp,
                            "read" to false
                        )
                        
                        database.child("notifications")
                            .child(targetUserId)
                            .push()
                            .setValue(notificationData)
                        
                        onSuccess()
                    }
                    .addOnFailureListener { onError(it.message ?: "Failed to send follow request") }
            }
            .addOnFailureListener { onError(it.message ?: "Failed to get user info") }
    }

    /**
     * Cancel a sent follow request
     */
    fun cancelFollowRequest(
        targetUserId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            onError("User not logged in")
            return
        }

        database.child(NODE_FOLLOW_REQUESTS)
            .child(targetUserId)
            .child(currentUserId)
            .removeValue()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Failed to cancel request") }
    }

    /**
     * Accept a follow request
     */
    fun acceptFollowRequest(
        requesterId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            onError("User not logged in")
            return
        }

        val updates = hashMapOf<String, Any?>(
            // Add to current user's followers list
            "$NODE_FOLLOWERS/$currentUserId/$requesterId" to true,
            // Add to requester's following list
            "$NODE_FOLLOWING/$requesterId/$currentUserId" to true,
            // Remove the request
            "$NODE_FOLLOW_REQUESTS/$currentUserId/$requesterId" to null
        )

        database.updateChildren(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Failed to accept request") }
    }

    /**
     * Decline a follow request
     */
    fun declineFollowRequest(
        requesterId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            onError("User not logged in")
            return
        }

        database.child(NODE_FOLLOW_REQUESTS)
            .child(currentUserId)
            .child(requesterId)
            .removeValue()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Failed to decline request") }
    }

    /**
     * Get list of incoming follow request user IDs
     */
    fun getFollowRequests(
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            onError("User not logged in")
            return
        }

        database.child(NODE_FOLLOW_REQUESTS)
            .child(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val requestIds = mutableListOf<String>()
                    snapshot.children.forEach { child ->
                        requestIds.add(child.key ?: "")
                    }
                    onSuccess(requestIds)
                }

                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
    }

    /**
     * Get count of incoming follow requests
     */
    fun getFollowRequestCount(
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            onError("User not logged in")
            return
        }

        database.child(NODE_FOLLOW_REQUESTS)
            .child(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onSuccess(snapshot.childrenCount.toInt())
                }

                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
    }

    /**
     * Check if current user has sent a request to target user
     */
    fun hasRequestedToFollow(
        targetUserId: String,
        onResult: (Boolean) -> Unit
    ) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            onResult(false)
            return
        }

        database.child(NODE_FOLLOW_REQUESTS)
            .child(targetUserId)
            .child(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onResult(snapshot.exists())
                }

                override fun onCancelled(error: DatabaseError) {
                    onResult(false)
                }
            })
    }

    /**
     * Observe follow requests in real-time
     */
    fun observeFollowRequests(
        onRequestsChange: (List<String>) -> Unit
    ): ValueEventListener {
        val currentUserId = auth.currentUser?.uid ?: return object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val requestIds = mutableListOf<String>()
                snapshot.children.forEach { child ->
                    requestIds.add(child.key ?: "")
                }
                onRequestsChange(requestIds)
            }

            override fun onCancelled(error: DatabaseError) {
                onRequestsChange(emptyList())
            }
        }

        database.child(NODE_FOLLOW_REQUESTS)
            .child(currentUserId)
            .addValueEventListener(listener)

        return listener
    }

    /**
     * Remove listener
     */
    fun removeListener(userId: String, listener: ValueEventListener) {
        database.child(NODE_FOLLOW_REQUESTS)
            .child(userId)
            .removeEventListener(listener)
    }
}
