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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MochiWebSocket @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val sessionManager: SessionManager,
    private val gson: Gson
) {
    private val sockets = ConcurrentHashMap<String, WebSocket>()
    private val subscribers = ConcurrentHashMap<String, MutableMap<String, (WebSocketEvent) -> Unit>>()
    private val reconnecting = ConcurrentHashMap<String, Boolean>()

    fun subscribe(
        serverUrl: String,
        fingerprint: String,
        onEvent: (WebSocketEvent) -> Unit
    ): String {
        val subscriptionId = UUID.randomUUID().toString()

        val callbacks = subscribers.getOrPut(fingerprint) { ConcurrentHashMap() }
        callbacks[subscriptionId] = onEvent

        if (!sockets.containsKey(fingerprint)) {
            connect(serverUrl, fingerprint)
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

    private fun connect(serverUrl: String, fingerprint: String) {
        val wsUrl = serverUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/')
        val url = "$wsUrl/_/websocket?key=$fingerprint"

        val request = Request.Builder()
            .url(url)
            .build()

        reconnecting[fingerprint] = true

        val socket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Connection established
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

        Thread {
            try {
                Thread.sleep(3000)
            } catch (e: InterruptedException) {
                return@Thread
            }
            if (reconnecting[fingerprint] == true && !subscribers[fingerprint].isNullOrEmpty()) {
                connect(serverUrl, fingerprint)
            }
        }.start()
    }
}
