package com.AppFlix.i220968_i228810.search

import android.content.Context
import android.util.Log
import com.AppFlix.i220968_i228810.data.SessionManager
import com.AppFlix.i220968_i228810.data.api.ApiClient
import com.AppFlix.i220968_i228810.data.api.UserDto
import com.AppFlix.i220968_i228810.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// CHANGED: Now takes Context to access SessionManager
class SearchRepository(context: Context) {

    private val sessionManager = SessionManager(context)
    private val api = ApiClient.authApi // Using AuthApi which contains the search endpoints

    // Get current user ID from session (default to "0" if not logged in)
    private val currentUserId: String
        get() = sessionManager.getUserProfile()?.uid ?: "0"

    /**
     * Search users via PHP API
     */
    fun searchUsers(query: String, onResult: (List<UserProfile>) -> Unit) {
        if (query.isBlank()) {
            onResult(emptyList())
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // CHANGED: Passes real currentUserId to exclude self
                val response = api.searchUsers(query, currentUserId)

                if (response.isSuccessful && response.body()?.success == true) {
                    val userDtos = response.body()?.users ?: emptyList()
                    val userProfiles = userDtos.map { dto -> mapDtoToProfile(dto) }

                    withContext(Dispatchers.Main) {
                        onResult(userProfiles)
                    }
                } else {
                    withContext(Dispatchers.Main) { onResult(emptyList()) }
                }
            } catch (e: Exception) {
                Log.e("SearchRepo", "Search error", e)
                withContext(Dispatchers.Main) { onResult(emptyList()) }
            }
        }
    }

    /**
     * Get all/suggested users via PHP API
     */
    fun getSuggestedUsers(limit: Int = 20, onResult: (List<UserProfile>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // CHANGED: Passes real currentUserId to exclude self
                val response = api.getAllUsers(currentUserId)

                if (response.isSuccessful && response.body()?.success == true) {
                    val userDtos = response.body()?.users ?: emptyList()
                    val userProfiles = userDtos.map { dto -> mapDtoToProfile(dto) }

                    withContext(Dispatchers.Main) {
                        onResult(userProfiles)
                    }
                } else {
                    withContext(Dispatchers.Main) { onResult(emptyList()) }
                }
            } catch (e: Exception) {
                Log.e("SearchRepo", "Get all users error", e)
                withContext(Dispatchers.Main) { onResult(emptyList()) }
            }
        }
    }

    private fun mapDtoToProfile(dto: UserDto): UserProfile {
        return UserProfile(
            uid = dto.id.toString(),
            username = dto.username,
            firstName = dto.first_name ?: "",
            lastName = dto.last_name ?: "",
            email = dto.email,
            dateOfBirth = dto.dob ?: "",
            profileImageUrl = dto.profile_image_url ?: "",
            createdAt = 0L
        )
    }
}