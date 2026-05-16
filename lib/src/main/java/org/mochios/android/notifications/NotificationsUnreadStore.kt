package org.mochios.android.notifications

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.auth.SessionManager
import org.mochios.android.websocket.MochiWebSocket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide unread-notifications counter, refreshed via the server's
 * notifications WS broadcasts. Every feature's TopAppBar bell binds the
 * same singleton so taps in any feature consume the same counter.
 *
 * Loads lazily on first access. Re-syncs from the server whenever
 * `refresh()` is called (e.g. after the user opens the inbox).
 */
@Singleton
class NotificationsUnreadStore @Inject constructor(
    private val repository: NotificationsRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    private var subscriptionId: String? = null
    private var started = false

    fun ensureStarted() {
        if (started) return
        started = true
        scope.launch {
            refresh()
            subscribeWebSocket()
        }
    }

    suspend fun refresh() {
        try {
            _count.value = repository.count().count
        } catch (e: Exception) {
            Log.w(TAG, "refresh failed: ${e.message}")
        }
    }

    private fun subscribeWebSocket() {
        val server = sessionManager.getServerUrlBlocking()
        if (server.isBlank() || subscriptionId != null) return
        subscriptionId = webSocket.subscribe(server, "notifications") { event ->
            when (event.type) {
                "new" -> scope.launch { refresh() }
                "read", "read_all", "clear_all", "clear_object" -> scope.launch { refresh() }
            }
        }
    }

    private companion object {
        const val TAG = "NotificationsUnread"
    }
}
