// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.websocket

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.mochios.android.api.WebSocketClient
import org.mochios.android.model.WebSocketEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Connection state of one stream, for screens that surface it. FAILED is
 * unused: retries are unbounded.
 */
enum class StreamWsStatus {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
    FAILED,
}

/**
 * A decoded `mochi.websocket.write` payload. The game apps send [type]
 * "message", "move" or "system", and for a move [body] is that game's notation
 * (SAN, coordinates, the placed word); other apps send their own shapes, which
 * [raw] carries along with anything the typed fields miss.
 */
data class StreamWsEvent(
    val type: String,
    val created: Long,
    val member: String?,
    val name: String?,
    val body: String?,
    val event: String?,
    val raw: Map<String, Any?>,
)

/**
 * Every WebSocket the app holds open. One connection per `server::key`, shared
 * by every subscriber of that key, whether it reads typed [WebSocketEvent]s
 * ([subscribe], for the feature ViewModels) or raw [StreamWsEvent]s
 * ([openStream], for the game boards and other screen-scoped streams).
 */
@Singleton
class MochiWebSocket @Inject constructor(
    @param:WebSocketClient private val wsClient: OkHttpClient,
    private val gson: Gson,
    private val session: SocketSession,
) {
    private val sockets = ConcurrentHashMap<String, WebSocket>()
    private val subscribers = ConcurrentHashMap<String, MutableMap<String, (WebSocketEvent) -> Unit>>()
    private val streamSubscribers =
        ConcurrentHashMap<String, MutableMap<String, (StreamWsEvent) -> Unit>>()
    // Invoked whenever a key's socket opens, first connect and reconnects
    // alike. A reconnect replays nothing - whatever was broadcast while the
    // socket was down is gone - so a subscriber whose state mirrors server
    // data re-fetches from here to absorb what it missed.
    private val connectListeners = ConcurrentHashMap<String, MutableMap<String, () -> Unit>>()
    private val reconnecting = ConcurrentHashMap<String, Boolean>()
    private val backoffAttempts = ConcurrentHashMap<String, Int>()
    private val backoffCaps = ConcurrentHashMap<String, Long>()
    private val statuses = ConcurrentHashMap<String, MutableStateFlow<StreamWsStatus>>()
    private val tokens = ConcurrentHashMap<String, String>()
    // The app a key's token is minted for, when the caller named one. A 401
    // then drops that token so the next attempt re-mints rather than
    // reconnecting forever with a credential the server has stopped taking.
    private val apps = ConcurrentHashMap<String, String>()
    // Bumped by reconnectNow(); a sleeping backoff thread that wakes to a
    // newer generation stands down, so a foreground return does not race a
    // timer into opening a second socket for the same key.
    private val generation = ConcurrentHashMap<String, Int>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // All internal maps are keyed by a composite `serverUrl::fingerprint`
    // so two subscribe calls with the same fingerprint but different servers
    // (e.g. the push distributor multiplexing across two Mochi identities)
    // get separate WebSocket connections rather than silently sharing one.
    private fun keyOf(serverUrl: String, fingerprint: String): String =
        "$serverUrl::$fingerprint"

    /**
     * Subscribe to [fingerprint]'s typed events on [serverUrl].
     *
     * @param token JWT for callers that mint their own (the push shell, which
     *   has no signed-in session). Callers with a session name their [app]
     *   instead and let the socket mint.
     * @param app the app whose token authorises the handshake.
     * @return the id to hand [unsubscribe].
     */
    fun subscribe(
        serverUrl: String,
        fingerprint: String,
        token: String? = null,
        app: String? = null,
        onEvent: (WebSocketEvent) -> Unit,
    ): String {
        val subscriptionId = UUID.randomUUID().toString()
        val key = keyOf(serverUrl, fingerprint)

        val callbacks = subscribers.getOrPut(key) { ConcurrentHashMap() }
        callbacks[subscriptionId] = onEvent

        if (app != null) {
            apps[key] = app
        }
        if (!sockets.containsKey(key)) {
            connect(serverUrl, fingerprint, token)
        }

        return subscriptionId
    }

    /**
     * Open a screen-scoped stream on [streamKey], authorised with [app]'s
     * token. The caller closes it when the screen leaves the composition;
     * [rememberStreamWebSocket] does that for you.
     *
     * @param streamKey the key the server multiplexes the stream by: a game
     *   record's key, "staff-events", "market-thread-<id>".
     * @param app the app the key belongs to ("chess", "go", "words", "staff",
     *   "market"), which names the token the connection authorises with.
     * @return the stream, already connecting.
     */
    fun openStream(streamKey: String, app: String): Stream {
        val stream = Stream(streamKey, app)
        stream.attach()
        return stream
    }

    /** Register for socket-open notifications on this key; see [connectListeners]. */
    fun subscribeConnected(
        serverUrl: String,
        fingerprint: String,
        onConnected: () -> Unit,
    ): String {
        val subscriptionId = UUID.randomUUID().toString()
        val listeners = connectListeners.getOrPut(keyOf(serverUrl, fingerprint)) { ConcurrentHashMap() }
        listeners[subscriptionId] = onConnected
        return subscriptionId
    }

    fun unsubscribeConnected(subscriptionId: String) {
        for ((_, listeners) in connectListeners) {
            listeners.remove(subscriptionId)
        }
    }

    fun unsubscribe(subscriptionId: String) {
        val touched = mutableSetOf<String>()

        for ((key, callbacks) in subscribers) {
            if (callbacks.remove(subscriptionId) != null) {
                touched.add(key)
            }
        }
        for ((key, callbacks) in streamSubscribers) {
            if (callbacks.remove(subscriptionId) != null) {
                touched.add(key)
            }
        }

        for (key in touched) {
            if (hasSubscribers(key)) continue
            subscribers.remove(key)
            streamSubscribers.remove(key)
            reconnecting[key] = false
            sockets.remove(key)?.close(1000, "No subscribers")
            statuses.remove(key)?.value = StreamWsStatus.DISCONNECTED
            backoffAttempts.remove(key)
            backoffCaps.remove(key)
            tokens.remove(key)
            apps.remove(key)
        }
    }

    fun disconnectAll() {
        reconnecting.keys.forEach { key -> reconnecting[key] = false }
        for ((_, socket) in sockets) {
            socket.close(1000, "Disconnect all")
        }
        for ((_, status) in statuses) {
            status.value = StreamWsStatus.DISCONNECTED
        }
        sockets.clear()
        subscribers.clear()
        streamSubscribers.clear()
        reconnecting.clear()
        // A logout is the one moment every stored token is certain to be
        // worthless; keeping them would reconnect the next account's sockets
        // with the previous one's credentials.
        tokens.clear()
        apps.clear()
    }

    /**
     * Reconnects every subscribed key whose socket is down, right now. Call
     * from onResume: backoff alone can be five minutes out, longer under Doze.
     * A no-op for keys whose socket is already up.
     */
    fun reconnectNow() {
        for (key in subscribers.keys + streamSubscribers.keys) {
            if (!hasSubscribers(key) || sockets.containsKey(key)) continue
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

    /**
     * Whether [fingerprint] has a connected socket with someone listening on
     * it, on any server this process holds.
     *
     * The push receivers ask before dropping a notification: a screen being on
     * display only justifies suppressing the tray row if the socket behind it
     * is up, otherwise the user is told nothing at all and the content never
     * arrives.
     *
     * @param fingerprint the key the screen subscribed with, which is not
     *   always the entity in the notification's link - chat and the games
     *   subscribe with a record key, market with `market-thread-<id>`.
     */
    fun isLive(fingerprint: String): Boolean {
        if (fingerprint.isEmpty()) return false
        for (key in sockets.keys) {
            if (key.substringAfterLast("::") != fingerprint) continue
            if (!hasSubscribers(key)) continue
            if (statuses[key]?.value != StreamWsStatus.CONNECTED) continue
            return true
        }
        return false
    }

    private fun hasSubscribers(key: String): Boolean =
        subscribers[key]?.isNotEmpty() == true || streamSubscribers[key]?.isNotEmpty() == true

    private fun statusOf(key: String): MutableStateFlow<StreamWsStatus> =
        statuses.getOrPut(key) { MutableStateFlow(StreamWsStatus.CONNECTING) }

    private fun connect(serverUrl: String, fingerprint: String, token: String? = null) {
        val key = keyOf(serverUrl, fingerprint)
        if (token != null) {
            tokens[key] = token
        }
        val app = apps[key]
        if (tokens[key] == null && app != null) {
            scope.launch {
                val minted = session.token(app)
                if (minted != null) {
                    tokens[key] = minted
                }
                if (hasSubscribers(key)) {
                    openSocket(serverUrl, fingerprint)
                }
            }
            return
        }
        openSocket(serverUrl, fingerprint)
    }

    private fun openSocket(serverUrl: String, fingerprint: String) {
        val key = keyOf(serverUrl, fingerprint)
        val wsUrl = serverUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/')
        val storedToken = tokens[key]
        // The token travels in the Bearer header only, which core reads first.
        // A query string lands in every access log between here and there, and
        // the token is good for a year.
        val request = Request.Builder()
            .url(socketUrl(wsUrl, fingerprint))
            .apply { if (storedToken != null) header("Authorization", "Bearer $storedToken") }
            .build()

        reconnecting[key] = true
        statusOf(key).value = if ((backoffAttempts[key] ?: 0) > 0) {
            StreamWsStatus.RECONNECTING
        } else {
            StreamWsStatus.CONNECTING
        }

        // Create-and-store atomically: two racing connects would otherwise both
        // open a socket, and the unretained one keeps reconnecting forever.
        sockets.computeIfAbsent(key) { _ ->
            wsClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // Reset backoff on successful connect so the next failure
                    // starts at the short-end again.
                    backoffAttempts.remove(key)
                    statusOf(key).value = StreamWsStatus.CONNECTED
                    connectListeners[key]?.let { listeners ->
                        for ((_, listener) in listeners) {
                            try {
                                listener()
                            } catch (e: Exception) {
                                // Swallow listener errors to avoid crashing the websocket
                            }
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    dispatch(key, text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    // Only reconnect if this socket is still the current one for
                    // the key — an orphaned socket dying must not evict a newer
                    // healthy socket or start a competing reconnect loop.
                    if (sockets.remove(key, webSocket)) {
                        statusOf(key).value = StreamWsStatus.DISCONNECTED
                        scheduleReconnect(serverUrl, fingerprint)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (sockets.remove(key, webSocket)) {
                        if (response?.code == 401 || response?.code == 403) {
                            invalidateToken(key)
                        }
                        statusOf(key).value = StreamWsStatus.DISCONNECTED
                        scheduleReconnect(serverUrl, fingerprint)
                    }
                }
            })
        }
    }

    private fun dispatch(key: String, text: String) {
        val callbacks = subscribers[key]
        if (!callbacks.isNullOrEmpty()) {
            val event = try {
                gson.fromJson(text, WebSocketEvent::class.java)
            } catch (e: Exception) {
                null
            }
            if (event != null) {
                for ((_, callback) in callbacks) {
                    try {
                        callback(event)
                    } catch (e: Exception) {
                        // Swallow callback errors to avoid crashing the websocket
                    }
                }
            }
        }

        val streamCallbacks = streamSubscribers[key]
        if (!streamCallbacks.isNullOrEmpty()) {
            val event = parseStreamEvent(text)
            if (event != null) {
                for ((_, callback) in streamCallbacks) {
                    try {
                        callback(event)
                    } catch (e: Exception) {
                        // Swallow callback errors to avoid crashing the websocket
                    }
                }
            }
        }
    }

    private fun invalidateToken(key: String) {
        tokens.remove(key)
        val app = apps[key] ?: return
        scope.launch { session.invalidate(app) }
    }

    private fun parseStreamEvent(text: String): StreamWsEvent? {
        return try {
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val raw: Map<String, Any?> = gson.fromJson(text, mapType) ?: return null
            // Game payloads always carry "type" ("message"/"move"/"system");
            // non-game payloads (e.g. staff-events sending {topic, object})
            // don't. Default to "" so the raw map still reaches the
            // subscriber, who can pick out whatever keyed fields apply.
            val type = (raw["type"] as? String) ?: ""
            val created = (raw["created"] as? Number)?.toLong()
                ?: (System.currentTimeMillis() / 1000L)
            StreamWsEvent(
                type = type,
                created = created,
                member = raw["member"] as? String,
                name = raw["name"] as? String,
                body = raw["body"] as? String,
                event = raw["event"] as? String,
                raw = raw,
            )
        } catch (e: JsonSyntaxException) {
            null
        } catch (e: ClassCastException) {
            null
        }
    }

    private fun scheduleReconnect(serverUrl: String, fingerprint: String) {
        val key = keyOf(serverUrl, fingerprint)
        if (reconnecting[key] != true) return
        if (!hasSubscribers(key)) return

        // Exponential backoff, 1s doubling to the key's cap, with plus/minus
        // 20% jitter to spread reconnects after a server restart.
        val attempt = backoffAttempts.compute(key) { _, prev -> (prev ?: 0) + 1 }!!
        val cap = backoffCaps[key] ?: BACKOFF_CAP_MS
        val baseMs = min(1000L shl (attempt - 1).coerceIn(0, 8), cap)
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
            if (reconnecting[key] == true && hasSubscribers(key)) {
                connect(serverUrl, fingerprint)
            }
        }.start()
    }

    /**
     * One screen's view of a stream: the raw events it carries and the state of
     * the connection underneath. The socket itself is still shared, so a second
     * screen on the same key joins rather than dials again.
     */
    inner class Stream internal constructor(
        private val streamKey: String,
        private val app: String,
    ) {
        private val _events = MutableSharedFlow<StreamWsEvent>(
            replay = 0,
            extraBufferCapacity = 64,
        )

        /**
         * Events on this stream. replay = 0 so a late collector doesn't see the
         * historical stream (it's a snapshot, not a journal); the buffer covers
         * a burst the UI is briefly slow to drain, and drops beyond it.
         */
        val events: SharedFlow<StreamWsEvent> = _events.asSharedFlow()

        private val _status = MutableStateFlow(StreamWsStatus.CONNECTING)

        /** Connection state, for screens that show a "reconnecting" hint. */
        val status: StateFlow<StreamWsStatus> = _status.asStateFlow()

        @Volatile private var closed = false
        private var subscriptionId: String? = null
        private var mirror: Job? = null

        internal fun attach() {
            scope.launch {
                val serverUrl = session.serverUrl()
                if (closed) return@launch
                val key = keyOf(serverUrl, streamKey)
                apps[key] = app
                // A board the user is looking at cannot wait out the 5-minute
                // ladder the background subscriptions use.
                backoffCaps[key] = STREAM_BACKOFF_CAP_MS
                val id = subscribeStream(serverUrl, streamKey) { event -> _events.tryEmit(event) }
                if (closed) {
                    unsubscribe(id)
                    return@launch
                }
                subscriptionId = id
                mirror = scope.launch {
                    statusOf(key).collect { value -> _status.value = value }
                }
            }
        }

        /** Close the stream: the socket goes with it if nothing else holds it. */
        fun close() {
            closed = true
            mirror?.cancel()
            subscriptionId?.let { id -> unsubscribe(id) }
        }
    }

    private fun subscribeStream(
        serverUrl: String,
        streamKey: String,
        onEvent: (StreamWsEvent) -> Unit,
    ): String {
        val subscriptionId = UUID.randomUUID().toString()
        val key = keyOf(serverUrl, streamKey)
        streamSubscribers.getOrPut(key) { ConcurrentHashMap() }[subscriptionId] = onEvent
        if (!sockets.containsKey(key)) {
            connect(serverUrl, streamKey)
        }
        return subscriptionId
    }

    private companion object {

        /** Backoff ceiling for a background subscription. */
        const val BACKOFF_CAP_MS = 300_000L

        /** Backoff ceiling for a stream a screen is watching. */
        const val STREAM_BACKOFF_CAP_MS = 30_000L
    }
}

/**
 * Handshake URL for a subscription. It carries the key and no credential: the
 * token goes in the `Authorization: Bearer` header, so a handshake sent without
 * one connects and then hears nothing. Core also accepts `?token=` for browsers,
 * which cannot set a header on a handshake; putting it back here would write the
 * token into every access log on the way. See [SocketUrlTest].
 */
internal fun socketUrl(wsBase: String, fingerprint: String): String =
    "$wsBase/_/websocket?key=$fingerprint"
