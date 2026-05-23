package org.mochios.android.notifications

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.auth.SessionManager
import org.mochios.android.model.WebSocketEvent
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
    @ApplicationContext private val context: Context,
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
                "read" -> {
                    scope.launch { refresh() }
                    cancelSystemNotification(event)
                }
                "clear_object" -> {
                    scope.launch { refresh() }
                    cancelSystemNotificationsForObject(event)
                }
                "read_all", "clear_all" -> {
                    scope.launch { refresh() }
                    cancelAllSystemNotifications()
                }
            }
        }
    }

    /**
     * Cancel the system-tray notification for a single `(app, topic, object)`
     * tuple — fired when the user reads the matching row via the web bell.
     * Tag format mirrors [MochiPushReceiver.postSystemNotification] /
     * [MochiFirebaseMessagingService.postSystemNotification]:
     * `"<app>-<topic>-<object>"`.
     */
    private fun cancelSystemNotification(event: WebSocketEvent) {
        val app = event.app ?: return
        val topic = event.topic ?: return
        val obj = event.objectId ?: return
        val tag = "$app-$topic-$obj"
        NotificationManagerCompat.from(context).cancel(tag, tag.hashCode())
    }

    /**
     * Cancel every system-tray notification whose tag matches `"<app>-*-<object>"`
     * — fired when the calling app clears all of its notifications for one
     * object (e.g. the user opens a chat thread).
     */
    private fun cancelSystemNotificationsForObject(event: WebSocketEvent) {
        val app = event.app ?: return
        val obj = event.objectId ?: return
        val prefix = "$app-"
        val suffix = "-$obj"
        val nm = NotificationManagerCompat.from(context)
        try {
            for (active in nm.activeNotifications) {
                val tag = active.tag ?: continue
                if (tag.startsWith(prefix) && tag.endsWith(suffix)) {
                    nm.cancel(tag, active.id)
                }
            }
        } catch (e: SecurityException) {
            // getActiveNotifications can throw on revoked listener access.
            Log.w(TAG, "cancelSystemNotificationsForObject: ${e.message}")
        }
    }

    /**
     * Cancel every system-tray notification this app has posted. Fired on
     * read_all / clear_all — both mean the user has handled the inbox
     * elsewhere, so leaving anything in the tray would be stale.
     */
    private fun cancelAllSystemNotifications() {
        NotificationManagerCompat.from(context).cancelAll()
    }

    private companion object {
        const val TAG = "NotificationsUnread"
    }
}
