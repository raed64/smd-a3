package com.AppFlix.i220968_i228810.follow

import android.content.Context
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.data.api.ApiClient
import com.AppFlix.i220968_i228810.data.api.FollowUserDto
import com.AppFlix.i220968_i228810.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FollowRepository(private val context: Context) {

    private val api = ApiClient.followApi
    private val sessionManager = SessionManager(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    // --- 1. Send Follow Request (Used by Search/Profile) ---
    // ... inside FollowRepository ...

    fun followUser(targetId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentId = sessionManager.getUserProfile()?.uid ?: return
        scope.launch {
            try {
                val response = api.manageFollow("follow_request", currentId, targetId)
                if (response.isSuccessful && response.body()?.success == true) {
                    withContext(Dispatchers.Main) { onSuccess() }
                } else {
                    // Capture the specific error message from PHP
                    val msg = response.body()?.message ?: "Unknown Error"
                    withContext(Dispatchers.Main) { onError(msg) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError("Network: ${e.localizedMessage}") }
            }
        }
    }
    // --- 2. Unfollow User (Used by Search/Profile/Lists) ---
    fun unfollowUser(targetId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentId = sessionManager.getUserProfile()?.uid ?: return
        scope.launch {
            try {
                val response = api.manageFollow("unfollow", currentId, targetId)
                if (response.isSuccessful && response.body()?.success == true) {
                    withContext(Dispatchers.Main) { onSuccess() }
                } else {
                    withContext(Dispatchers.Main) { onError("Failed to unfollow") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Error") }
            }
        }
    }

    // --- 3. Accept Request (Used by NotificationActivity) ---
    fun acceptRequest(requesterId: String, onSuccess: () -> Unit) {
        val currentId = sessionManager.getUserProfile()?.uid ?: return
        scope.launch {
            try {
                val response = api.manageFollow("accept", currentId, requesterId)
                if (response.isSuccessful && response.body()?.success == true) {
                    withContext(Dispatchers.Main) { onSuccess() }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // --- 4. Reject Request (Used by NotificationActivity) ---
    fun rejectRequest(requesterId: String, onSuccess: () -> Unit) {
        val currentId = sessionManager.getUserProfile()?.uid ?: return
        scope.launch {
            try {
                val response = api.manageFollow("reject", currentId, requesterId)
                if (response.isSuccessful && response.body()?.success == true) {
                    withContext(Dispatchers.Main) { onSuccess() }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // --- 5. Get Pending Requests (Used by NotificationActivity) ---
    fun getRequests(onResult: (List<UserProfile>) -> Unit) {
        val currentId = sessionManager.getUserProfile()?.uid ?: return
        scope.launch {
            try {
                val response = api.getFollowData("requests", currentId)
                if (response.isSuccessful) {
                    val dtos = response.body()?.users ?: emptyList()
                    val profiles = dtos.map { it.toUserProfile() }
                    withContext(Dispatchers.Main) { onResult(profiles) }
                } else {
                    withContext(Dispatchers.Main) { onResult(emptyList()) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(emptyList()) }
            }
        }
    }

    // --- 6. Get Followers List (Used by FollowersListActivity) ---
    fun getFollowers(userId: String, onResult: (List<UserProfile>) -> Unit) {
        scope.launch {
            try {
                val response = api.getFollowData("followers", userId)
                if (response.isSuccessful) {
                    val dtos = response.body()?.users ?: emptyList()
                    val profiles = dtos.map { it.toUserProfile() }
                    withContext(Dispatchers.Main) { onResult(profiles) }
                } else {
                    withContext(Dispatchers.Main) { onResult(emptyList()) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(emptyList()) }
            }
        }
    }

    // --- 7. Get Following List (Used by FollowingListActivity) ---
    fun getFollowing(userId: String, onResult: (List<UserProfile>) -> Unit) {
        scope.launch {
            try {
                val response = api.getFollowData("following", userId)
                if (response.isSuccessful) {
                    val dtos = response.body()?.users ?: emptyList()
                    val profiles = dtos.map { it.toUserProfile() }
                    withContext(Dispatchers.Main) { onResult(profiles) }
                } else {
                    withContext(Dispatchers.Main) { onResult(emptyList()) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(emptyList()) }
            }
        }
    }

    // --- 8. List Fetching - IDs Only (Used by Search Activity Filtering) ---
    fun getFollowersList(userId: String, onResult: (List<String>) -> Unit) {
        scope.launch {
            try {
                val response = api.getFollowData("followers", userId)
                if (response.isSuccessful) {
                    val ids = response.body()?.users?.map { it.id.toString() } ?: emptyList()
                    withContext(Dispatchers.Main) { onResult(ids) }
                } else {
                    withContext(Dispatchers.Main) { onResult(emptyList()) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(emptyList()) }
            }
        }
    }

    fun getFollowingList(userId: String, onResult: (List<String>) -> Unit) {
        scope.launch {
            try {
                val response = api.getFollowData("following", userId)
                if (response.isSuccessful) {
                    val ids = response.body()?.users?.map { it.id.toString() } ?: emptyList()
                    withContext(Dispatchers.Main) { onResult(ids) }
                } else {
                    withContext(Dispatchers.Main) { onResult(emptyList()) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(emptyList()) }
            }
        }
    }

    // --- 9. Counts & Status ---
    fun checkFollowStatus(targetId: String, onResult: (String) -> Unit) {
        val currentId = sessionManager.getUserProfile()?.uid ?: return
        scope.launch {
            try {
                val response = api.checkFollowStatus(currentId, targetId)
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) { onResult(response.body()?.status ?: "none") }
                } else {
                    withContext(Dispatchers.Main) { onResult("none") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult("none") }
            }
        }
    }

    fun getFollowersCount(userId: String, onResult: (Int) -> Unit) {
        scope.launch {
            try {
                val response = api.getFollowData("followers", userId)
                val count = if (response.isSuccessful) response.body()?.users?.size ?: 0 else 0
                withContext(Dispatchers.Main) { onResult(count) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(0) }
            }
        }
    }

    fun getFollowingCount(userId: String, onResult: (Int) -> Unit) {
        scope.launch {
            try {
                val response = api.getFollowData("following", userId)
                val count = if (response.isSuccessful) response.body()?.users?.size ?: 0 else 0
                withContext(Dispatchers.Main) { onResult(count) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(0) }
            }
        }
    }

    // --- Helper ---
    private fun FollowUserDto.toUserProfile(): UserProfile {
        return UserProfile(
            uid = this.id.toString(),
            username = this.username,
            firstName = this.first_name ?: "",
            lastName = this.last_name ?: "",
            email = "", // Email is private/not returned in follow lists
            profileImageUrl = this.profile_image_url ?: ""
        )
    }
}