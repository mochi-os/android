// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ws

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
import org.mochios.android.auth.SessionManager
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * Connection status reported by [GameWebSocketController]. FAILED is unused:
 * retries are unbounded.
 */
enum class GameWsStatus {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
    FAILED,
}

/**
 * A decoded `mochi.websocket.write` payload from chess.star / go.star /
 * words.star. [type] is "message", "move" or "system"; for a move [body] is
 * game-specific notation (SAN, coordinates, the placed word). [raw] carries
 * what the typed fields do not.
 */
data class GameWsEvent(
    val type: String,
    val created: Long,
    val member: String?,
    val name: String?,
    val body: String?,
    val event: String?,
    val raw: Map<String, Any?>,
)

/**
 * Controller for a single game's WebSocket, usually created by
 * [rememberGameWebSocket] and closed when the screen leaves the composition.
 * [events] and [status] tolerate multiple collectors.
 */
class GameWebSocketController internal constructor(
    private val gameKey: String,
    private val sessionManager: SessionManager,
    private val client: OkHttpClient,
    private val gson: Gson,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(GameWsStatus.CONNECTING)
    val status: StateFlow<GameWsStatus> = _status.asStateFlow()

    private val _retries = MutableStateFlow(0)
    val retries: StateFlow<Int> = _retries.asStateFlow()

    // replay = 0 so a late subscriber doesn't see the historical event
    // stream (it's a snapshot, not a journal); buffer 64 covers the worst
    // common case where the UI thread is briefly slow to drain after a
    // burst of moves.
    private val _events = MutableSharedFlow<GameWsEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    val events: SharedFlow<GameWsEvent> = _events.asSharedFlow()

    private val socketRef = AtomicReference<WebSocket?>(null)
    @Volatile private var closed: Boolean = false
    private var reconnectJob: Job? = null

    init {
        connect()
    }

    /** Close the socket and prevent further reconnect attempts. */
    fun close() {
        closed = true
        reconnectJob?.cancel()
        reconnectJob = null
        socketRef.getAndSet(null)?.close(1000, "Closed by caller")
        scope.cancel()
    }

    private fun connect() {
        if (closed) return
        _status.value = if (_retries.value > 0) GameWsStatus.RECONNECTING else GameWsStatus.CONNECTING

        // The subscription key rides in the query: it is the only place the
        // server reads it from. No token or cookie - the key selects the
        // stream.
        val serverUrl = sessionManager.getServerUrlBlocking().trimEnd('/')
        val wsBase = serverUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        val url = StringBuilder("$wsBase/_/websocket?key=$gameKey")

        val requestBuilder = Request.Builder().url(url.toString())
        val request = requestBuilder.build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _retries.value = 0
                _status.value = GameWsStatus.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = parseEvent(text) ?: return
                // tryEmit is non-blocking and silently drops when the buffer
                // is full — UI consumers must process events fast enough or
                // accept missed frames. The buffer is large enough for any
                // reasonable burst.
                _events.tryEmit(event)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socketRef.compareAndSet(webSocket, null)
                if (!closed) {
                    _status.value = GameWsStatus.DISCONNECTED
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socketRef.compareAndSet(webSocket, null)
                if (!closed) {
                    _status.value = GameWsStatus.DISCONNECTED
                    scheduleReconnect()
                }
            }
        }

        val socket = client.newWebSocket(request, listener)
        socketRef.set(socket)
    }

    private fun scheduleReconnect() {
        if (closed) return
        reconnectJob?.cancel()
        // 1s → 2s → 4s → 8s → 16s → 30s (cap). Same shape as the spec asks
        // for. The base of MochiWebSocket uses a 5-minute cap; for an
        // interactive game-detail screen 30s is plenty — beyond that the
        // user will navigate away anyway.
        val attempt = (_retries.value + 1)
        _retries.value = attempt
        val baseMs = min(1000L shl (attempt - 1).coerceIn(0, 5), 30_000L)
        reconnectJob = scope.launch {
            delay(baseMs)
            if (!closed) connect()
        }
    }

    private fun parseEvent(text: String): GameWsEvent? {
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
            GameWsEvent(
                type = type,
                created = created,
                member = raw["member"] as? String,
                name = raw["name"] as? String,
                body = raw["body"] as? String,
                event = raw["event"] as? String,
                raw = raw,
            )
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: ClassCastException) {
            null
        }
    }
}

class GameWebSocket(
    private val sessionManager: SessionManager,
    private val client: OkHttpClient,
    private val gson: Gson,
) {
    /** Open a controller for the given game key. Remember to [close]. */
    fun open(gameKey: String): GameWebSocketController {
        return GameWebSocketController(
            gameKey = gameKey,
            sessionManager = sessionManager,
            // Layer a ping interval on top of the shared HTTP client so the
            // WS connection has its own keepalive — pings are pointless on
            // request/response HTTP. 5 minutes matches MochiWebSocket.
            client = client.newBuilder()
                .pingInterval(5, TimeUnit.MINUTES)
                .build(),
            gson = gson,
        )
    }
}

/**
 * Hilt entry point so [rememberGameWebSocket] can resolve singletons without a
 * ViewModel.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GameWebSocketEntryPoint {
    fun sessionManager(): SessionManager
    fun okHttpClient(): OkHttpClient
    fun gson(): Gson
}

/**
 * Open a game-scoped WebSocket for as long as this composable is in the
 * composition. A null or blank `gameKey` returns null and opens nothing;
 * changing it closes the previous socket and opens a new one.
 */
@Composable
fun rememberGameWebSocket(gameKey: String?): GameWebSocketController? {
    if (gameKey.isNullOrBlank()) return null
    val context = LocalContext.current
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            GameWebSocketEntryPoint::class.java,
        )
    }
    val controller = remember(gameKey) {
        GameWebSocket(
            sessionManager = entryPoint.sessionManager(),
            client = entryPoint.okHttpClient(),
            gson = entryPoint.gson(),
        ).open(gameKey)
    }
    DisposableEffect(gameKey) {
        onDispose { controller.close() }
    }
    return controller
}
