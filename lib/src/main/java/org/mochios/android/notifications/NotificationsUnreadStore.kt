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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mochios.android.auth.SessionManager
import org.mochios.android.model.WebSocketEvent
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
    private var started = false

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
        started = false
        _count.value = 0
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
