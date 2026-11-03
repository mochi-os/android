// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.notifications

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mochios.android.auth.SessionManager
import org.mochios.android.model.WebSocketEvent
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.websocket.MochiWebSocket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide unread-notifications counter, refreshed by the notifications WS
 * broadcasts. Every feature's bell binds this singleton, so they share one
 * count.
 */
@Singleton
class NotificationsUnreadStore @Inject constructor(
    private val repository: NotificationsRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    private var subscriptionId: String? = null
    private var connectionId: String? = null
    private var started = false

    /** The pending or in-flight socket refresh; cancelled when a newer one starts. */
    private var refreshJob: Job? = null

    fun ensureStarted() {
        if (started) return
        started = true
        scope.launch {
            // Don't poll or open the notifications socket while signed out —
            // the count call 401s and the socket reconnect-loops forever.
            if (sessionManager.currentToken.first() == null) {
                started = false
                return@launch
            }
            refresh()
            subscribeWebSocket()
        }
    }

    fun stop() {
        subscriptionId?.let { id -> webSocket.unsubscribe(id) }
        subscriptionId = null
        connectionId?.let { id -> webSocket.unsubscribeConnected(id) }
        connectionId = null
        started = false
        _count.value = 0
    }

    suspend fun refresh() {
        try {
            _count.value = repository.count().count
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "refresh failed: ${e.message}")
        }
    }

    /**
     * Refresh after a socket event or the user's own read or clear. Each call
     * cancels the previous one and waits [REFRESH_DEBOUNCE] first: one
     * notification arrives as a burst of frames (`new`, then `clear_object` and
     * `read` once it is marked), and an action's echo lands with its response,
     * so only the latest fetches. Use [refresh] where the count must be read now.
     */
    fun refreshLatest() {
        synchronized(this) {
            refreshJob?.cancel()
            refreshJob = scope.launch {
                delay(REFRESH_DEBOUNCE)
                refresh()
            }
        }
    }

    private suspend fun subscribeWebSocket() {
        val server = sessionManager.getServerUrlBlocking()
        if (server.isBlank() || subscriptionId != null) return
        // A reconnect replays nothing: whatever was broadcast while the socket
        // was down is gone. Re-fetch the count every time the socket comes up -
        // MainActivity's onResume forces the reconnect on a foreground return,
        // which makes this the foreground refresh as well.
        connectionId = webSocket.subscribeConnected(server, "notifications") {
            refreshLatest()
        }
        subscriptionId = webSocket.subscribeBadge(server) { event ->
            when (event.type) {
                "new" -> refreshLatest()
                "read" -> {
                    refreshLatest()
                    cancelSystemNotification(event)
                }
                "clear_object" -> {
                    refreshLatest()
                    cancelSystemNotificationsForObject(event)
                }
                "read_all", "clear_all" -> {
                    refreshLatest()
                    cancelAllSystemNotifications()
                }
            }
        }
    }

    /**
     * Cancel the tray notification for one `(app, topic, object)` tuple. The
     * tag must stay in step with what the push receivers post.
     */
    private fun cancelSystemNotification(event: WebSocketEvent) {
        val app = event.app ?: return
        val topic = event.topic ?: return
        val obj = event.objectId ?: return
        val tag = "$app-$topic-$obj"
        NotificationManagerCompat.from(context).cancel(tag, tag.hashCode())
    }

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

    private fun cancelAllSystemNotifications() {
        NotificationManagerCompat.from(context).cancelAll()
    }

    private companion object {
        const val TAG = "NotificationsUnread"
    }
}

/**
 * Subscribe the badge to [server]'s notifications channel. The socket must
 * carry a notifications-app token: delivery is scoped by the sending app, so
 * only a socket tagged with that app hears its events. Naming the app lets the
 * socket mint the token from the session, and mint it again when core refuses
 * one that has expired; a token handed in once would be all it had after a
 * refusal, and the badge would hear nothing again. It stays open in the
 * background as well: a notification read elsewhere clears its tray row from
 * here, and the tray is what the user sees while the app is not.
 *
 * @return the id to hand [MochiWebSocket.unsubscribe].
 */
internal fun MochiWebSocket.subscribeBadge(server: String, onEvent: (WebSocketEvent) -> Unit): String =
    subscribe(server, "notifications", app = "notifications", background = true, onEvent = onEvent)
