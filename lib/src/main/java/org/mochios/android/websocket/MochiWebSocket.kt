// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.websocket

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
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
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.mochios.android.api.WebSocketClient
import org.mochios.android.model.WebSocketEvent
import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
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
 *
 * Core allows a user 32 sockets across all their apps, tabs and devices. So
 * that a phone does not hold its share while nobody looks at it, [background]
 * closes every socket but those subscribed to stay open in the background, and
 * [foreground] opens them again; [watch] ties the two to the process lifecycle.
 *
 * A socket replays nothing: whatever was sent while it was down is gone. When a
 * key's socket opens again after being down while subscribed - closed in the
 * background, dropped, refused, or reconnecting with a fresh token - its
 * subscribers hear a [REOPEN] frame, once the socket is open, and read again
 * what they show.
 */
@Singleton
class MochiWebSocket @Inject constructor(
    @param:WebSocketClient private val wsClient: OkHttpClient,
    private val gson: Gson,
    private val session: SocketSession,
) {
    /**
     * Everything held for one key: its subscriptions, its socket and the state
     * of the connection, guarded by the entry's own monitor. [entries] maps a
     * key to its one live entry. Subscribing to a key and dropping its entry
     * both run inside a `compute` on that key, so neither loses the other's
     * work; an entry dropped from [entries] is dead, and a timer, a mint or a
     * callback still holding it opens nothing.
     */
    private class Entry(
        val key: String,
        val server: String,
        val fingerprint: String,
        val account: String?,
    ) {
        val subscribers = LinkedHashMap<String, (WebSocketEvent) -> Unit>()
        val streams = LinkedHashMap<String, (StreamWsEvent) -> Unit>()

        /** The subscriptions that keep the socket open in the background. */
        val residents = HashSet<String>()

        /** The app whose token the session mints for the handshake. */
        var app: String? = null
        var token: String? = null

        /** Mints a fresh token for a key with no [app], after core refuses one. */
        var mint: (suspend () -> Mint)? = null

        /** Its minter said the session is gone; it waits to be subscribed afresh. */
        var parked = false

        /** The user its socket counted against under core's cap, as it opened. */
        var user: String? = null
        var socket: WebSocket? = null
        var attempts = 0
        var cap = BACKOFF_CAP_MS

        /** The backoff timer in flight, cancelled when a reconnect supersedes it. */
        var timer: ScheduledFuture<*>? = null

        /** Core refused the last handshake with 429, the user's sockets being at their cap. */
        var refused = false

        /** The attempt in flight was started by a place coming free; a refusal keeps its rung. */
        var freed = false

        /** The subscribers may have missed frames; the next open sends them [REOPEN]. */
        var missed = false

        /** Frames came in the background that only the resident subscriptions heard. */
        var muted = false

        /** When the key was last subscribed, so a return asks for the newest first. */
        var recency = 0L
        val status = MutableStateFlow(StreamWsStatus.CONNECTING)

        val empty: Boolean get() = subscribers.isEmpty() && streams.isEmpty()
        val resident: Boolean get() = residents.isNotEmpty()
    }

    private val entries = ConcurrentHashMap<String, Entry>()
    // Invoked whenever a key's socket opens, first connect and reconnects
    // alike. A reconnect replays nothing - whatever was broadcast while the
    // socket was down is gone - so a subscriber whose state mirrors server
    // data re-fetches from here to absorb what it missed.
    private val connectListeners = ConcurrentHashMap<String, MutableMap<String, () -> Unit>>()
    private val sequence = AtomicLong()
    // Sockets that have opened, and sockets closed on purpose. Weak, so a
    // socket OkHttp has finished with leaves both whatever order its callbacks
    // and our close ran in.
    private val opened: MutableSet<WebSocket> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))
    @VisibleForTesting
    internal val retired: MutableSet<WebSocket> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))
    // Whether the app is in the foreground: false between [background] and
    // [foreground].
    @Volatile private var visible = true
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // The client for a socket authorised as a device account: it carries that
    // account's token and nothing else. The shared client's cookie jar hands
    // the signed-in session to its own server, and core takes the cookie's
    // user before any token, so a second account's push socket there would
    // register as the signed-in account.
    private val bare: OkHttpClient by lazy { wsClient.newBuilder().cookieJar(CookieJar.NO_COOKIES).build() }

    // Backoff timers, all on one thread. A superseded timer is cancelled and
    // leaves the queue, rather than sleeping on in a thread of its own.
    private val timers = ScheduledThreadPoolExecutor(1) { runnable ->
        Thread(runnable, "MochiWebSocket timers").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    // Keys are a composite `serverUrl::fingerprint`, and `::account` after it
    // for a socket authorised as one device account: two subscriptions to the
    // same key on different servers, or for two accounts on the same server,
    // each get a connection of their own rather than one riding the other's -
    // authenticated as the other account, and hearing only what is sent to it.
    // Keys are never parsed back; a server address may hold "::" itself.
    private fun keyOf(serverUrl: String, fingerprint: String, account: String? = null): String =
        if (account == null) "$serverUrl::$fingerprint" else "$serverUrl::$fingerprint::$account"

    /**
     * Subscribe to [fingerprint]'s typed events on [serverUrl].
     *
     * @param token JWT for callers that mint their own (the push distributor,
     *   which has no signed-in session). Callers with a session name their
     *   [app] instead and let the socket mint.
     * @param app the app whose token authorises the handshake.
     * @param background whether this subscription keeps the socket open while
     *   the app is in the background, for one that delivers what the user is
     *   told about there. Every other socket closes until the app returns, and
     *   the other subscriptions to a socket kept open hear nothing meanwhile
     *   but a [REOPEN] as it returns.
     * @param account the device account the subscription belongs to, for one
     *   authorised with that account's own [token] rather than the signed-in
     *   session's. It gets a socket of its own, apart from any other account's
     *   on the same server, and stands apart from the session: [disconnectAll]
     *   leaves it, and its caller unsubscribes it.
     * @param mint mints a fresh token for a subscription with no [app], when
     *   core refuses the one it has; see [Mint].
     * @return the id to hand [unsubscribe].
     */
    fun subscribe(
        serverUrl: String,
        fingerprint: String,
        token: String? = null,
        app: String? = null,
        background: Boolean = false,
        account: String? = null,
        mint: (suspend () -> Mint)? = null,
        onEvent: (WebSocketEvent) -> Unit,
    ): String {
        val subscriptionId = UUID.randomUUID().toString()
        val entry = join(keyOf(serverUrl, fingerprint, account), serverUrl, fingerprint, account) { entry ->
            entry.subscribers[subscriptionId] = onEvent
            if (background) entry.residents.add(subscriptionId)
            if (app != null) entry.app = app
            if (token != null) entry.token = token
            if (mint != null) entry.mint = mint
        }
        connect(entry)
        return subscriptionId
    }

    /**
     * Add to [key]'s entry, making it if there is none, under its monitor and
     * inside a `compute` on the key: atomic with a drop of the entry, which
     * would otherwise take the new subscription with it.
     */
    private fun join(
        key: String,
        server: String,
        fingerprint: String,
        account: String?,
        add: (Entry) -> Unit,
    ): Entry {
        var joined: Entry? = null
        entries.compute(key) { _, existing ->
            val entry = existing ?: Entry(key, server, fingerprint, account)
            synchronized(entry) {
                add(entry)
                entry.recency = sequence.incrementAndGet()
            }
            joined = entry
            entry
        }
        return joined!!
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
        account: String? = null,
        onConnected: () -> Unit,
    ): String {
        val subscriptionId = UUID.randomUUID().toString()
        val listeners = connectListeners.getOrPut(keyOf(serverUrl, fingerprint, account)) { ConcurrentHashMap() }
        listeners[subscriptionId] = onConnected
        return subscriptionId
    }

    fun unsubscribeConnected(subscriptionId: String) {
        for ((_, listeners) in connectListeners) {
            listeners.remove(subscriptionId)
        }
    }

    fun unsubscribe(subscriptionId: String) {
        for (entry in entries.values) {
            val left = synchronized(entry) {
                val removed = entry.subscribers.remove(subscriptionId) != null ||
                    entry.streams.remove(subscriptionId) != null
                if (removed) entry.residents.remove(subscriptionId)
                removed
            }
            if (!left) continue
            if (!leave(entry, "No subscribers")) settle(entry)
        }
    }

    /**
     * Drop [entry] if nothing subscribes to it any more, closing its socket.
     * Atomic with a subscribe joining it; see [join].
     *
     * @return whether it was dropped.
     */
    private fun leave(entry: Entry, reason: String): Boolean {
        var socket: WebSocket? = null
        var dropped = false
        entries.computeIfPresent(entry.key) { _, current ->
            if (current !== entry) return@computeIfPresent current
            synchronized(entry) {
                if (!entry.empty) return@computeIfPresent current
                socket = entry.socket
                entry.socket = null
                entry.timer?.cancel(false)
                entry.status.value = StreamWsStatus.DISCONNECTED
            }
            dropped = true
            null
        }
        socket?.let { closing -> retire(closing, reason) }
        return dropped
    }

    /**
     * Close [entry]'s socket if the app is in the background and nothing
     * subscribed to keep it open there remains, as [background] would have.
     */
    private fun settle(entry: Entry) {
        val socket = synchronized(entry) {
            if (visible || entry.resident) return
            val current = entry.socket ?: return
            entry.socket = null
            entry.missed = true
            entry.status.value = StreamWsStatus.DISCONNECTED
            current
        }
        retire(socket, "Background")
    }

    /**
     * Drop every subscription of the signed-in session, at logout. Those that
     * belong to a device account stay: the push channels of the accounts still
     * on the device, which the push distributor drops itself as each account
     * leaves. A logout is the one moment every stored token is certain to be
     * worthless; keeping them would reconnect the next account's sockets with
     * the previous one's credentials, and a token still being minted for one
     * lands on an entry that is gone.
     */
    fun disconnectAll() {
        for (entry in entries.values) {
            if (entry.account != null) continue
            var socket: WebSocket? = null
            entries.computeIfPresent(entry.key) { _, current ->
                if (current !== entry) return@computeIfPresent current
                synchronized(entry) {
                    socket = entry.socket
                    entry.socket = null
                    entry.subscribers.clear()
                    entry.streams.clear()
                    entry.residents.clear()
                    entry.token = null
                    entry.timer?.cancel(false)
                    entry.status.value = StreamWsStatus.DISCONNECTED
                }
                null
            }
            socket?.let { closing -> retire(closing, "Disconnect all") }
        }
    }

    /**
     * Close every socket but the ones subscribed to stay open in the
     * background. The subscriptions stay, so [foreground] reopens each key as
     * it was, and a key subscribed meanwhile waits for it too. What is sent
     * while they are closed is lost to them; their subscribers hear [REOPEN]
     * as they open again.
     */
    fun background() {
        visible = false
        for (entry in entries.values) {
            val socket = synchronized(entry) {
                if (entry.resident) return@synchronized null
                entry.socket?.also {
                    entry.socket = null
                    entry.missed = true
                    entry.status.value = StreamWsStatus.DISCONNECTED
                }
            }
            socket?.let { closing -> retire(closing, "Background") }
        }
    }

    /**
     * Reopen what [background] closed. The subscriptions that shared a socket
     * kept open in the background, and heard none of its frames meanwhile,
     * hear [REOPEN] now.
     */
    fun foreground() {
        visible = true
        for (entry in entries.values) {
            val muted = synchronized(entry) {
                val muted = entry.muted && entry.socket != null && entry.status.value == StreamWsStatus.CONNECTED
                entry.muted = false
                muted
            }
            if (muted) reopen(entry, residents = false, others = true)
        }
        reconnectNow()
    }

    /**
     * Follow [lifecycle], the process's: [background] when the app stops,
     * [foreground] when it starts again.
     */
    fun watch(lifecycle: Lifecycle) {
        visible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = foreground()

            override fun onStop(owner: LifecycleOwner) = background()
        })
    }

    /**
     * Reconnects every subscribed key whose socket is down, right now, the
     * most recently subscribed first. Call from onResume: backoff alone can be
     * five minutes out, longer under Doze. A no-op for keys whose socket is
     * already up, and for keys that wait for the next [foreground].
     */
    fun reconnectNow() {
        for (entry in ordered()) {
            val down = synchronized(entry) {
                if (entry.socket != null || entry.empty || entry.parked) return@synchronized false
                // Supersede any backoff wait in flight and start the ladder
                // over: a foreground return is a fresh attempt, not a retry.
                entry.timer?.cancel(false)
                entry.timer = null
                entry.attempts = 0
                entry.freed = false
                true
            }
            if (down) connect(entry)
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
        return entries.values.any { entry ->
            entry.fingerprint == fingerprint && synchronized(entry) {
                entry.socket != null && !entry.empty && entry.status.value == StreamWsStatus.CONNECTED
            }
        }
    }

    /** The live entries, the most recently subscribed first. */
    private fun ordered(): List<Entry> =
        // Each entry's recency read once: a subscription moving it mid-sort
        // would break the sort's contract, and the sort would throw.
        entries.values.map { entry -> entry to synchronized(entry) { entry.recency } }
            .sortedByDescending { (_, recency) -> recency }
            .map { (entry, _) -> entry }

    /**
     * Whether a socket for [fingerprint] is opening again after being down. It
     * will tell its subscribers so with a [REOPEN] once it is open, unless core
     * refuses it, so a screen coming back can leave its catch-up to that for a
     * little while.
     */
    fun reopening(fingerprint: String): Boolean =
        entries.values.any { entry ->
            entry.fingerprint == fingerprint && synchronized(entry) { entry.missed && entry.socket != null && !entry.empty }
        }

    /** How many backoff timers wait to fire. */
    @VisibleForTesting
    internal fun waiting(): Int = timers.queue.size

    /** The subscribed keys, the most recently subscribed first. */
    @VisibleForTesting
    internal fun newest(): List<String> =
        ordered().filter { entry -> synchronized(entry) { !entry.empty } }.map { entry -> entry.key }

    /** Whether [subscriptionId] is held by a live entry, whose socket it hears. */
    @VisibleForTesting
    internal fun holds(subscriptionId: String): Boolean =
        entries.values.any { entry ->
            synchronized(entry) { subscriptionId in entry.subscribers || subscriptionId in entry.streams }
        }

    /**
     * Close [socket] on purpose. One still connecting is cancelled: closing it
     * would let the upgrade finish on the server, holding a place under the
     * cap until the close went through.
     */
    @VisibleForTesting
    internal fun retire(socket: WebSocket, reason: String) {
        retired.add(socket)
        if (socket in opened) socket.close(1000, reason) else socket.cancel()
    }

    /**
     * Try at once a key core refused for the user [freed] counted against: one
     * of this process's own sockets of theirs has closed, freeing a place
     * under their cap. One place, one key - the newest. A refusal again keeps
     * the rung it was on.
     */
    private fun retryRefused(freed: Entry) {
        val server = freed.server.trimEnd('/')
        // The user a key's socket counts against under core's cap: its device
        // account, or the signed-in one, which a device account may also be.
        val signedIn = lazy { session.identity() }
        val userOf = { entry: Entry -> entry.account ?: signedIn.value }
        // The place freed is that of the user the socket opened as, who may
        // have signed out since; one that never opened counts as now.
        val user = synchronized(freed) { freed.user } ?: userOf(freed)
        for (entry in ordered()) {
            if (entry.server.trimEnd('/') != server || userOf(entry) != user) continue
            val retry = synchronized(entry) {
                if (!entry.refused || entry.socket != null || entry.empty || entry.parked || !(visible || entry.resident)) {
                    return@synchronized false
                }
                entry.timer?.cancel(false)
                entry.timer = null
                entry.freed = true
                true
            }
            if (retry) {
                connect(entry)
                return
            }
        }
    }


    /**
     * Give up the token core refused, for a key that can get a fresh one: from
     * the session for its app, or from its [Entry.mint]. One that cannot keeps
     * it, rather than reconnecting with no credential at all.
     */
    private fun invalidate(entry: Entry) {
        val app = synchronized(entry) {
            if (entry.app == null && entry.mint == null) return
            entry.token = null
            entry.app
        }
        if (app != null) scope.launch { session.invalidate(app) }
    }

    private fun connect(entry: Entry) {
        var app: String? = null
        var mint: (suspend () -> Mint)? = null
        synchronized(entry) {
            if (entry.socket != null || entry.empty || entry.parked) return
            if (entry.token == null) {
                app = entry.app
                mint = entry.mint
            }
        }
        val minter = mint
        val named = app
        if (minter != null && named == null) {
            mint(entry, minter)
            return
        }
        if (named == null) {
            openSocket(entry)
            return
        }
        scope.launch {
            val minted = session.token(named)
            if (minted != null) {
                synchronized(entry) { if (entry.token == null) entry.token = minted }
            }
            openSocket(entry)
        }
    }

    /** Get [entry] a fresh token from its own [minter], then open it. */
    private fun mint(entry: Entry, minter: suspend () -> Mint) {
        scope.launch {
            when (val minted = minter()) {
                is Mint.Fresh -> {
                    synchronized(entry) { if (entry.token == null) entry.token = minted.token }
                    openSocket(entry)
                }
                // Never a socket without its own credential: on the signed-in
                // server it would pass as the signed-in account.
                Mint.Later -> scheduleReconnect(entry)
                Mint.Gone -> synchronized(entry) {
                    entry.parked = true
                    entry.status.value = StreamWsStatus.DISCONNECTED
                }
            }
        }
    }

    /**
     * Every way to a socket comes through here: a subscription, a backoff
     * timer waking, a reconnect, a token minted. Under the entry's monitor, so
     * the status written is the status of the socket that becomes the key's.
     */
    private fun openSocket(entry: Entry) {
        val minter = synchronized(entry) {
            // A dead entry - dropped at logout, say, while its token was
            // minted - opens nothing.
            if (entries[entry.key] !== entry || entry.empty || entry.socket != null) return
            if (!visible && !entry.resident) {
                entry.missed = true
                entry.status.value = StreamWsStatus.DISCONNECTED
                return
            }
            // Never a device account's socket without its own credential: a
            // refusal on another thread may have given the token up since it
            // was minted. It is minted again instead.
            if (entry.account != null && entry.token == null) {
                entry.mint ?: run {
                    entry.parked = true
                    entry.status.value = StreamWsStatus.DISCONNECTED
                    return
                }
            } else {
                val wsUrl = entry.server
                    .replace("https://", "wss://")
                    .replace("http://", "ws://")
                    .trimEnd('/')
                val token = entry.token
                // The token travels in the Bearer header only, which core reads
                // first. A query string lands in every access log between here
                // and there, and the token is good for a year.
                val request = Request.Builder()
                    .url(socketUrl(wsUrl, entry.fingerprint))
                    .apply { if (token != null) header("Authorization", "Bearer $token") }
                    .build()
                entry.status.value = if (entry.attempts > 0) StreamWsStatus.RECONNECTING else StreamWsStatus.CONNECTING
                val client = if (entry.account != null) bare else wsClient
                entry.socket = client.newWebSocket(request, Listener(entry))
                return
            }
        }
        mint(entry, minter)
    }

    /** The callbacks of one socket of [entry]'s; only the key's current socket is heard. */
    private inner class Listener(private val entry: Entry) : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened.add(webSocket)
            // Read before the lock: the signed-in identity is a disk read.
            val user = entry.account ?: session.identity()
            var shown = true
            val reopened = synchronized(entry) {
                // Closed on purpose since, or no longer the key's socket: it
                // reports nothing, and what was missed waits for the next one.
                if (webSocket in retired || entry.socket !== webSocket) return
                entry.attempts = 0
                entry.refused = false
                entry.freed = false
                entry.user = user
                entry.status.value = StreamWsStatus.CONNECTED
                val missed = entry.missed
                entry.missed = false
                // In the background only the resident subscriptions hear it
                // now; the rest hear it once, as the app returns.
                shown = visible
                if (missed && !shown && entry.subscribers.size + entry.streams.size > entry.residents.size) {
                    entry.muted = true
                }
                missed
            }
            connectListeners[entry.key]?.let { listeners ->
                for ((_, listener) in listeners) {
                    try {
                        listener()
                    } catch (e: Exception) {
                        // Swallow listener errors to avoid crashing the websocket
                    }
                }
            }
            // Only now, with the socket open: a read the subscribers start on
            // hearing it leaves nothing sent before it unseen.
            if (reopened) reopen(entry, residents = true, others = shown)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            frame(entry, webSocket, text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            ended(webSocket, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            ended(webSocket, response?.code)
        }

        private fun ended(webSocket: WebSocket, code: Int?) {
            var unauthorised = false
            val current = synchronized(entry) {
                // An orphaned socket dying must not evict a newer healthy one
                // or start a competing reconnect loop.
                if (entry.socket !== webSocket) return@synchronized false
                entry.socket = null
                entry.missed = true
                entry.status.value = StreamWsStatus.DISCONNECTED
                unauthorised = code == 401 || code == 403
                // Core is at the user's socket cap: the ladder goes on, and a
                // socket of ours closing there tries again sooner.
                if (code == 429) entry.refused = true
                true
            }
            if (current) {
                if (unauthorised) invalidate(entry)
                scheduleReconnect(entry)
            }
            if (webSocket in retired) retryRefused(entry)
        }
    }

    /**
     * A frame on [webSocket], for [entry]'s subscribers. In the background
     * only the subscriptions that keep the socket open there hear it; the rest
     * are told on return that they missed some.
     */
    private fun frame(entry: Entry, webSocket: WebSocket, text: String) {
        val typed: List<(WebSocketEvent) -> Unit>
        val raw: List<(StreamWsEvent) -> Unit>
        synchronized(entry) {
            if (entry.socket !== webSocket) return
            if (visible) {
                typed = entry.subscribers.values.toList()
                raw = entry.streams.values.toList()
            } else {
                typed = entry.subscribers.filterKeys { id -> id in entry.residents }.values.toList()
                raw = entry.streams.filterKeys { id -> id in entry.residents }.values.toList()
                if (typed.size + raw.size < entry.subscribers.size + entry.streams.size) entry.muted = true
            }
        }
        hand(text, typed, raw)
    }

    /**
     * Tell [entry]'s subscribers its socket is open again after being down:
     * those that keep it open in the background with [residents], the rest
     * with [others].
     */
    private fun reopen(entry: Entry, residents: Boolean, others: Boolean) {
        val typed: List<(WebSocketEvent) -> Unit>
        val raw: List<(StreamWsEvent) -> Unit>
        synchronized(entry) {
            val told = { id: String -> if (id in entry.residents) residents else others }
            typed = entry.subscribers.filterKeys(told).values.toList()
            raw = entry.streams.filterKeys(told).values.toList()
        }
        hand(gson.toJson(mapOf("type" to REOPEN)), typed, raw)
    }

    private fun hand(
        text: String,
        typed: List<(WebSocketEvent) -> Unit>,
        raw: List<(StreamWsEvent) -> Unit>,
    ) {
        if (typed.isNotEmpty()) {
            val event = try {
                gson.fromJson(text, WebSocketEvent::class.java)
            } catch (e: Exception) {
                null
            }
            if (event != null) {
                for (callback in typed) {
                    try {
                        callback(event)
                    } catch (e: Exception) {
                        // Swallow callback errors to avoid crashing the websocket
                    }
                }
            }
        }
        if (raw.isNotEmpty()) {
            val event = parseStreamEvent(text)
            if (event != null) {
                for (callback in raw) {
                    try {
                        callback(event)
                    } catch (e: Exception) {
                        // Swallow callback errors to avoid crashing the websocket
                    }
                }
            }
        }
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

    private fun scheduleReconnect(entry: Entry) {
        synchronized(entry) {
            if (entries[entry.key] !== entry || entry.empty) return
            // Exponential backoff, 1s doubling to the key's cap, with plus or
            // minus 20% jitter to spread reconnects after a server restart. A
            // retry a place coming free started keeps the rung it was on.
            if (!entry.freed) entry.attempts++
            entry.freed = false
            val attempt = entry.attempts.coerceAtLeast(1)
            val baseMs = min(1000L shl (attempt - 1).coerceIn(0, 8), entry.cap)
            val delayMs = baseMs + (baseMs * (Math.random() * 0.4 - 0.2)).toLong()
            entry.timer?.cancel(false)
            entry.timer = timers.schedule({ connect(entry) }, delayMs, TimeUnit.MILLISECONDS)
        }
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

        // Guards [closed], [subscriptionId] and [mirror]: attach starts the
        // mirror and close stops it, and neither may land in the middle of the
        // other, or a close between attach's check and its start leaves the
        // mirror running for good.
        private val lock = Any()
        private var closed = false
        private var subscriptionId: String? = null
        private var mirror: Job? = null

        /** Whether the stream still mirrors its socket's status. */
        @VisibleForTesting
        internal val mirrored: Boolean
            get() = synchronized(lock) { mirror?.isActive == true }

        internal fun attach() {
            scope.launch {
                val serverUrl = session.serverUrl()
                if (synchronized(lock) { closed }) return@launch
                val id = UUID.randomUUID().toString()
                val entry = join(keyOf(serverUrl, streamKey), serverUrl, streamKey, null) { entry ->
                    entry.streams[id] = { event -> _events.tryEmit(event) }
                    entry.app = app
                    // A board the user is looking at cannot wait out the
                    // 5-minute ladder the background subscriptions use.
                    entry.cap = STREAM_BACKOFF_CAP_MS
                }
                val started = synchronized(lock) {
                    if (closed) return@synchronized false
                    subscriptionId = id
                    stall?.invoke()
                    mirror = scope.launch {
                        entry.status.collect { value -> _status.value = value }
                    }
                    true
                }
                if (!started) {
                    unsubscribe(id)
                    return@launch
                }
                connect(entry)
            }
        }

        /** Close the stream: the socket goes with it if nothing else holds it. */
        fun close() {
            val (id, running) = synchronized(lock) {
                closed = true
                subscriptionId to mirror
            }
            running?.cancel()
            id?.let { subscription -> unsubscribe(subscription) }
        }
    }

    /**
     * Run by a stream just before it starts mirroring its socket's status,
     * where a test lands a close.
     */
    @VisibleForTesting
    @Volatile
    internal var stall: (() -> Unit)? = null

    private companion object {

        /** Backoff ceiling for a background subscription. */
        const val BACKOFF_CAP_MS = 300_000L

        /** Backoff ceiling for a stream a screen is watching. */
        const val STREAM_BACKOFF_CAP_MS = 30_000L
    }
}

/**
 * What a subscription's own minter answers when core refuses its token: a
 * fresh one; nothing for now, when the minter could not be reached, and the
 * key tries again along its backoff ladder; or a session that is gone, which
 * nothing short of signing in again mends, and the key then waits to be
 * subscribed afresh.
 */
sealed interface Mint {
    data class Fresh(val token: String) : Mint

    data object Later : Mint

    data object Gone : Mint
}

/**
 * The type of the frame [MochiWebSocket] hands a key's subscribers when its
 * socket opens again after being down: whatever was sent meanwhile is gone, so
 * they read again what they show. The web client's socket manager sends the
 * same frame.
 */
const val REOPEN = "socket/reopen"

/**
 * Handshake URL for a subscription. It carries the key and no credential: the
 * token goes in the `Authorization: Bearer` header, so a handshake sent without
 * one connects and then hears nothing. Core also accepts `?token=` for browsers,
 * which cannot set a header on a handshake; putting it back here would write the
 * token into every access log on the way. See [SocketUrlTest].
 */
internal fun socketUrl(wsBase: String, fingerprint: String): String =
    "$wsBase/_/websocket?key=$fingerprint"
