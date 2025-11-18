package com.AppFlix.i220968_i228810.presence

import com.AppFlix.i220968_i228810.data.api.ApiClient
import com.AppFlix.i220968_i228810.model.PresenceSnapshot
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class PresenceManager {

    companion object {
        @Volatile private var INSTANCE: PresenceManager? = null
        fun getInstance() = INSTANCE ?: synchronized(this) {
            INSTANCE ?: PresenceManager().also { INSTANCE = it }
        }
    }

    private val api = ApiClient.presenceApi
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var heartbeatJob: Job? = null
    private var currentUserId: String? = null

    private var observerJob: Job? = null
    private val observers = ConcurrentHashMap<String, MutableList<(PresenceSnapshot) -> Unit>>()

    fun startHeartbeat(userId: String) {
        if (currentUserId == userId && heartbeatJob?.isActive == true) return
        currentUserId = userId

        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                try { api.sendHeartbeat(userId = userId) } catch (_: Exception) { }
                delay(1000) // Send heartbeat every 10s
            }
        }
        startObserverLoop()
    }

    fun stopHeartbeat() {
        // Instant Offline Call
        currentUserId?.let { uid ->
            scope.launch {
                try { api.goOffline(userId = uid) } catch (_: Exception) { }
            }
        }

        heartbeatJob?.cancel()
        observerJob?.cancel()
        observers.clear()
        currentUserId = null
    }

    fun observeUserPresence(targetUserId: String, callback: (PresenceSnapshot?) -> Unit): Runnable {
        val list = observers.getOrPut(targetUserId) { mutableListOf() }
        synchronized(list) { list.add(callback) }

        fetchStatusesNow(listOf(targetUserId))

        return Runnable {
            val currentList = observers[targetUserId]
            if (currentList != null) {
                synchronized(currentList) {
                    currentList.remove(callback)
                    if (currentList.isEmpty()) observers.remove(targetUserId)
                }
            }
        }
    }

    private fun startObserverLoop() {
        if (observerJob?.isActive == true) return

        observerJob = scope.launch {
            while (isActive) {
                if (observers.isNotEmpty()) {
                    fetchStatusesNow(observers.keys.toList())
                }
                // FASTER POLLING for "Immediate" feel
                delay(1000)
            }
        }
    }

    private fun fetchStatusesNow(ids: List<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            try {
                val response = api.getUserStatuses(ids.joinToString(","))
                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.statuses?.forEach { dto ->
                        val snapshot = PresenceSnapshot(dto.uid, dto.status, dto.lastActive)
                        observers[dto.uid]?.let { callbacks ->
                            synchronized(callbacks) { callbacks.forEach { it(snapshot) } }
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }
}