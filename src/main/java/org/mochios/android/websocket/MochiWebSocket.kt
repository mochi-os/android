package org.mochios.android.websocket

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.mochios.android.auth.SessionManager
import org.mochios.android.model.WebSocketEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class MochiWebSocket @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val sessionManager: SessionManager,
    private val gson: Gson
) {
    private val sockets = ConcurrentHashMap<String, WebSocket>()
    private val subscribers = ConcurrentHashMap<String, MutableMap<String, (WebSocketEvent) -> Unit>>()
    private val reconnecting = ConcurrentHashMap<String, Boolean>()
    private val backoffAttempts = ConcurrentHashMap<String, Int>()

    // Derived client with WebSocket ping keepalive. The injected
    // OkHttpClient is shared with regular HTTP requests where pings
    // are pointless, so we layer pingInterval on top for our WS calls
    // only. 5 minutes is a sweet spot — short enough to keep most
    // carrier-NAT translation tables alive (typical TCP idle is
    // 10-15 min) and to detect dead connections within a single
    // ping cycle, long enough that battery cost is negligible
    // (~30 bytes per ping per connection per 5min).
    private val wsClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .pingInterval(5, TimeUnit.MINUTES)
            .build()
    }

    fun subscribe(
        serverUrl: String,
        fingerprint: String,
        token: String? = null,
        onEvent: (WebSocketEvent) -> Unit,
    ): String {
        val subscriptionId = UUID.randomUUID().toString()

        val callbacks = subscribers.getOrPut(fingerprint) { ConcurrentHashMap() }
        callbacks[subscriptionId] = onEvent

        if (!sockets.containsKey(fingerprint)) {
            connect(serverUrl, fingerprint, token)
        }

        return subscriptionId
    }

    fun unsubscribe(subscriptionId: String) {
        val emptyFingerprints = mutableListOf<String>()

        for ((fingerprint, callbacks) in subscribers) {
            callbacks.remove(subscriptionId)
            if (callbacks.isEmpty()) {
                emptyFingerprints.add(fingerprint)
            }
        }

        for (fingerprint in emptyFingerprints) {
            subscribers.remove(fingerprint)
            reconnecting[fingerprint] = false
            sockets.remove(fingerprint)?.close(1000, "No subscribers")
        }
    }

    fun disconnectAll() {
        reconnecting.keys.forEach { reconnecting[it] = false }
        for ((fingerprint, socket) in sockets) {
            socket.close(1000, "Disconnect all")
        }
        sockets.clear()
        subscribers.clear()
        reconnecting.clear()
    }

    private val tokens = ConcurrentHashMap<String, String>()

    private fun connect(serverUrl: String, fingerprint: String, token: String? = null) {
        if (token != null) {
            tokens[fingerprint] = token
        }
        val wsUrl = serverUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/')
        val storedToken = tokens[fingerprint]
        val url = if (storedToken != null) {
            "$wsUrl/_/websocket?key=$fingerprint&token=$storedToken"
        } else {
            "$wsUrl/_/websocket?key=$fingerprint"
        }

        val request = Request.Builder()
            .url(url)
            .build()

        reconnecting[fingerprint] = true

        val socket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Reset backoff on successful connect so the next failure
                // starts at the short-end again.
                backoffAttempts.remove(fingerprint)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val event = gson.fromJson(text, WebSocketEvent::class.java)
                    val callbacks = subscribers[fingerprint]
                    if (callbacks != null) {
                        for ((_, callback) in callbacks) {
                            try {
                                callback(event)
                            } catch (e: Exception) {
                                // Swallow callback errors to avoid crashing the websocket
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Failed to parse message
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                sockets.remove(fingerprint)
                scheduleReconnect(serverUrl, fingerprint)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                sockets.remove(fingerprint)
                scheduleReconnect(serverUrl, fingerprint)
            }
        })

        sockets[fingerprint] = socket
    }

    private fun scheduleReconnect(serverUrl: String, fingerprint: String) {
        if (reconnecting[fingerprint] != true) return
        if (subscribers[fingerprint].isNullOrEmpty()) return

        // Exponential backoff with ±20% jitter, capped at 5 minutes.
        // 1s → 2s → 4s → 8s → 16s → 32s → 64s → 128s → 256s → 300s (cap).
        // Jitter spreads thundering herds when many clients reconnect
        // after a server restart. Backoff prevents a tight loop when the
        // server is genuinely down.
        val attempt = backoffAttempts.compute(fingerprint) { _, prev -> (prev ?: 0) + 1 }!!
        val baseMs = min(1000L shl (attempt - 1).coerceIn(0, 8), 300_000L)
        val jitterMs = (baseMs * (Math.random() * 0.4 - 0.2)).toLong()
        val delayMs = baseMs + jitterMs

        Thread {
            try {
                Thread.sleep(delayMs)
            } catch (e: InterruptedException) {
                return@Thread
            }
            if (reconnecting[fingerprint] == true && !subscribers[fingerprint].isNullOrEmpty()) {
                connect(serverUrl, fingerprint)
            }
        }.start()
    }
}
