// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.websocket

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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
    private val gson: Gson
) {
    private val sockets = ConcurrentHashMap<String, WebSocket>()
    private val subscribers = ConcurrentHashMap<String, MutableMap<String, (WebSocketEvent) -> Unit>>()
    private val reconnecting = ConcurrentHashMap<String, Boolean>()
    private val backoffAttempts = ConcurrentHashMap<String, Int>()
    // Bumped by reconnectNow(); a sleeping backoff thread that wakes to a
    // newer generation stands down, so a foreground return does not race a
    // timer into opening a second socket for the same key.
    private val generation = ConcurrentHashMap<String, Int>()

    // Derived client so the ping keepalive applies to WebSockets only, not the
    // shared HTTP client. 5 minutes stays inside typical carrier-NAT idle
    // timeouts (10-15 min) at negligible battery cost.
    private val wsClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .pingInterval(5, TimeUnit.MINUTES)
            .build()
    }

    // All internal maps are keyed by a composite `serverUrl::fingerprint`
    // so two subscribe calls with the same fingerprint but different servers
    // (e.g. the push distributor multiplexing across two Mochi identities)
    // get separate WebSocket connections rather than silently sharing one.
    private fun keyOf(serverUrl: String, fingerprint: String): String =
        "$serverUrl::$fingerprint"

    fun subscribe(
        serverUrl: String,
        fingerprint: String,
        token: String? = null,
        onEvent: (WebSocketEvent) -> Unit,
    ): String {
        val subscriptionId = UUID.randomUUID().toString()
        val key = keyOf(serverUrl, fingerprint)

        val callbacks = subscribers.getOrPut(key) { ConcurrentHashMap() }
        callbacks[subscriptionId] = onEvent

        if (!sockets.containsKey(key)) {
            connect(serverUrl, fingerprint, token)
        }

        return subscriptionId
    }

    fun unsubscribe(subscriptionId: String) {
        val emptyKeys = mutableListOf<String>()

        for ((key, callbacks) in subscribers) {
            callbacks.remove(subscriptionId)
            if (callbacks.isEmpty()) {
                emptyKeys.add(key)
            }
        }

        for (key in emptyKeys) {
            subscribers.remove(key)
            reconnecting[key] = false
            sockets.remove(key)?.close(1000, "No subscribers")
        }
    }

    fun disconnectAll() {
        reconnecting.keys.forEach { reconnecting[it] = false }
        for ((_, socket) in sockets) {
            socket.close(1000, "Disconnect all")
        }
        sockets.clear()
        subscribers.clear()
        reconnecting.clear()
    }

    /**
     * Reconnects every subscribed key whose socket is down, right now. Call
     * from onResume: backoff alone can be five minutes out, longer under Doze.
     * A no-op for keys whose socket is already up.
     */
    fun reconnectNow() {
        for ((key, callbacks) in subscribers) {
            if (callbacks.isEmpty() || sockets.containsKey(key)) continue
            val parts = key.split("::", limit = 2)
            if (parts.size != 2) continue
            val (serverUrl, fingerprint) = parts
            // Supersede any backoff wait in flight and start the ladder over:
            // a foreground return is a fresh attempt, not a retry.
            generation.compute(key) { _, prev -> (prev ?: 0) + 1 }
            backoffAttempts.remove(key)
            connect(serverUrl, fingerprint)
        }
    }

    private val tokens = ConcurrentHashMap<String, String>()

    private fun connect(serverUrl: String, fingerprint: String, token: String? = null) {
        val key = keyOf(serverUrl, fingerprint)
        if (token != null) {
            tokens[key] = token
        }
        val wsUrl = serverUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/')
        val storedToken = tokens[key]
        // The token goes in a header, never the query: OkHttp's WebSocket call
        // keeps the application interceptors, so the logging interceptor would
        // write the credential to logcat. The server accepts Bearer and
        // ?token=.
        val request = Request.Builder()
            .url(socketUrl(wsUrl, fingerprint))
            .apply { if (storedToken != null) header("Authorization", "Bearer $storedToken") }
            .build()

        reconnecting[key] = true

        // Create-and-store atomically: two racing connects would otherwise both
        // open a socket, and the unretained one keeps reconnecting forever.
        sockets.computeIfAbsent(key) { _ ->
            wsClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // Reset backoff on successful connect so the next failure
                    // starts at the short-end again.
                    backoffAttempts.remove(key)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val event = gson.fromJson(text, WebSocketEvent::class.java)
                        val callbacks = subscribers[key]
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
                    // Only reconnect if this socket is still the current one for
                    // the key — an orphaned socket dying must not evict a newer
                    // healthy socket or start a competing reconnect loop.
                    if (sockets.remove(key, webSocket)) {
                        scheduleReconnect(serverUrl, fingerprint)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (sockets.remove(key, webSocket)) {
                        scheduleReconnect(serverUrl, fingerprint)
                    }
                }
            })
        }
    }

    private fun scheduleReconnect(serverUrl: String, fingerprint: String) {
        val key = keyOf(serverUrl, fingerprint)
        if (reconnecting[key] != true) return
        if (subscribers[key].isNullOrEmpty()) return

        // Exponential backoff, 1s doubling to a 300s cap, with plus/minus 20%
        // jitter to spread reconnects after a server restart.
        val attempt = backoffAttempts.compute(key) { _, prev -> (prev ?: 0) + 1 }!!
        val baseMs = min(1000L shl (attempt - 1).coerceIn(0, 8), 300_000L)
        val jitterMs = (baseMs * (Math.random() * 0.4 - 0.2)).toLong()
        val delayMs = baseMs + jitterMs
        val scheduledGeneration = generation[key] ?: 0

        Thread {
            try {
                Thread.sleep(delayMs)
            } catch (e: InterruptedException) {
                return@Thread
            }
            // A reconnectNow() while this slept has already connected (or is
            // about to); this timer belongs to a superseded generation.
            if ((generation[key] ?: 0) != scheduledGeneration) return@Thread
            if (reconnecting[key] == true && !subscribers[key].isNullOrEmpty()) {
                connect(serverUrl, fingerprint)
            }
        }.start()
    }
}

/**
 * Handshake URL for a subscription. Carries no credential: the logging
 * interceptor writes the URL to logcat, so the token goes in a header.
 */
internal fun socketUrl(wsBase: String, fingerprint: String): String =
    "$wsBase/_/websocket?key=$fingerprint"

