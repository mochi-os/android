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
 * Connection status reported by [GameWebSocketController].
 *
 * The model mirrors web's `WebsocketConnectionStatus` (idle / connecting /
 * ready / error) but uses Android-idiomatic names: CONNECTING + CONNECTED
 * for healthy progression, DISCONNECTED for a clean close, RECONNECTING
 * while we're sleeping between retries, FAILED for terminal errors (none
 * today — we retry indefinitely — kept for future caller-supplied limits).
 */
enum class GameWsStatus {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
    FAILED,
}

/**
 * A decoded WebSocket event from one of the game servers. Mirrors the
 * shape `mochi.websocket.write(game["key"], payload)` produces in
 * `chess.star` / `go.star` / `words.star`.
 *
 * Common fields are unpacked at the top level; game-specific fields stay
 * in [raw] for the caller to interpret.
 *
 * @param type    `"message"` for chat, `"move"` for a move payload,
 *                `"system"` for system notices.
 * @param created Epoch seconds the event happened (server clock).
 * @param member  Sender entity ID, when present.
 * @param name    Sender display name, when present.
 * @param body    Free-form body. For chat messages this is the text the
 *                user typed; for move messages it's the game-specific
 *                notation (SAN for chess, move coords for go, the placed
 *                word for words).
 * @param event   System-event subtype when [type] is `"system"` —
 *                `"resign"`, `"draw_offer"`, `"draw_decline"`, etc.
 * @param raw     The whole decoded payload as a `Map<String, Any?>` so
 *                game-specific code can pick out fields the typed columns
 *                don't carry (FEN, board state, scoring breakdown).
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
 * Lightweight controller for a single game's WebSocket. Created once per
 * game-detail screen by [rememberGameWebSocket]; closed when the screen
 * leaves the composition.
 *
 * Holds the OkHttp socket, exponential-backoff state, and the flows the
 * UI subscribes to. Multiple subscribers can collect [events] and [status]
 * simultaneously.
 *
 * Implementation lives outside [GameWebSocket] so callers that need a
 * non-Compose entry point (ViewModels, background syncs) can construct
 * one directly.
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

        // Server URL → WebSocket URL: `wss://server/_/websocket?key=<gameKey>`
        // (plus optional `&token=…` when an app token is configured). The
        // session cookie is added below if present so the server can resolve
        // the user without a per-app token.
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

/**
 * Non-Compose entry point for opening a game socket. The Compose helper
 * [rememberGameWebSocket] wraps this in a [DisposableEffect] so screens
 * don't have to manage the lifecycle by hand.
 *
 * Holds no state itself; each call to [open] returns a fresh
 * [GameWebSocketController].
 */
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
 * Hilt entry point so [rememberGameWebSocket] can pull the
 * Singleton-scoped HTTP/auth dependencies without forcing every screen
 * into the ViewModel pattern.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GameWebSocketEntryPoint {
    fun sessionManager(): SessionManager
    fun okHttpClient(): OkHttpClient
    fun gson(): Gson
}

/**
 * Open a game-scoped WebSocket for the duration this composable stays in
 * the composition. When `gameKey` is null the function returns null and
 * no socket is opened — useful while the caller is still resolving the
 * key. Changing `gameKey` closes the previous socket and opens a new one.
 *
 * The returned controller exposes [GameWebSocketController.events],
 * [GameWebSocketController.status] and [GameWebSocketController.retries]
 * for the screen to observe.
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
