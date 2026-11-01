// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.app.Notification
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mochios.android.account.MochiAccount
import org.mochios.android.api.ApiClient
import org.mochios.android.api.foreignClient
import org.mochios.android.api.ApiException
import org.mochios.android.api.unwrapRaw
import org.mochios.android.auth.TokenApi
import org.mochios.android.auth.TokenRequest
import org.mochios.android.util.isServerOrigin
import org.mochios.android.model.WebSocketEvent
import org.mochios.android.websocket.Mint
import org.mochios.android.websocket.MochiWebSocket
import javax.inject.Inject
import kotlin.math.min

/**
 * Foreground service hosting the Mochi UnifiedPush distributor. Holds one
 * WebSocket subscription per Mochi identity on the `unifiedpush` channel and
 * broadcasts each incoming `{subscription, payload}` as a MESSAGE intent to the
 * registered app.
 */
@AndroidEntryPoint
class PushService : Service() {

    @Inject
    lateinit var webSocket: MochiWebSocket
    @Inject
    lateinit var gson: Gson

    // One client for every account's server. Never the injected shared one:
    // that carries the bound-server retarget, so a drain or ack meant for
    // account B's server would arrive at account A's holding B's bearer token.
    private val client: OkHttpClient by lazy { foreignClient().build() }

    private val store by lazy { DistributorStore(applicationContext) }

    // One channel per identity, claimed before it is opened: onStartCommand
    // fires reentrantly and a double claim dispatches every push twice. Keyed
    // by identity, since two accounts can share a server.
    private val channels = Channels { channel ->
        webSocket.unsubscribe(channel.subscription)
        webSocket.unsubscribeConnected(channel.connection)
    }

    // identity -> the token its socket, drains and acks authorise with, kept
    // current when a refused one is minted again.
    private val credentials = java.util.concurrent.ConcurrentHashMap<String, String>()

    // identity -> its drain, run one at a time.
    private val drainers = java.util.concurrent.ConcurrentHashMap<String, Drainer>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var accountsJob: Job? = null

    // A network coming up cuts short the wait of a channel whose first token
    // could not be minted.
    private val online = Online()
    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = online.available()
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        runCatching { getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(network) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to the foreground first: Android raises
        // ForegroundServiceDidNotStartInTimeException unless startForeground()
        // follows every startForegroundService() within ~5s, even on paths that
        // stop immediately.
        startForegroundCompat()
        if (PushTransport.current(applicationContext) == PushTransport.TRANSPORT_FCM) {
            Log.i(TAG, "onStartCommand: transport=fcm; stopping self")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        // Initial snapshot — accountsFlow does NOT emit synchronously on
        // collection, so we have to seed from MochiAccount.all() ourselves.
        reconcile(MochiAccount.all(applicationContext))
        // Then watch for changes (new identity added, identity removed) and
        // re-reconcile on every emission. The previous job is cancelled in
        // case onStartCommand fires reentrantly so we don't accumulate
        // concurrent collectors.
        accountsJob?.cancel()
        accountsJob = MochiAccount.accountsFlow(applicationContext)
            .onEach { reconcile(it) }
            .launchIn(scope)
        return START_STICKY
    }

    override fun onDestroy() {
        // First: an in-flight connectOne would otherwise finish after destroy
        // and subscribe on a service that no longer owns the subscription, so
        // nothing ever unsubscribes it.
        scope.cancel()
        accountsJob?.cancel()
        runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(network) }
        // And a connectOne already past its suspensions registers before this
        // closes, and is torn down with the rest, or is refused after.
        channels.close()
        credentials.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Multiplex subscriptions across the current Mochi identities: connect new
     * ones, drop vanished ones, and open again those signed in afresh - a
     * channel whose session died waits for exactly that. Safe to call
     * concurrently and reentrantly.
     */
    private fun reconcile(accounts: List<MochiAccount.Snapshot>) {
        for (identity in channels.stale(accounts)) {
            Log.i(TAG, "Identity gone or signed in again; unsubscribing")
            channels.release(identity)
            credentials.remove(identity)
        }
        if (accounts.isEmpty()) {
            Log.w(TAG, "No Mochi accounts; idle until one is added")
            return
        }
        for (account in accounts) {
            connectOne(account)
        }
    }

    private fun connectOne(account: MochiAccount.Snapshot) {
        // Claim the slot atomically before launching the coroutine. If a
        // concurrent reconcile() already claimed it, bail out so we don't end
        // up registering two callbacks against MochiWebSocket.subscribe and
        // dispatching every push twice.
        val slot = channels.claim(account.identity, account.session) ?: return

        scope.launch {
            var registered = false
            try {
                // The shell process has no login, so the cookie jar is empty.
                // Mint a JWT from the cross-app MochiAccount session cookie for
                // MochiWebSocket to authorise the upgrade with - again and again
                // while it cannot be reached, so a start with the network down
                // still brings the channel up without the app being opened.
                val token = firstToken(
                    mint = { mint(account.identity, account.server, account.session) },
                    held = { channels.holds(slot) },
                    pause = { attempt -> online.await(ladder(attempt)) },
                )
                if (token == null) {
                    Log.w(
                        TAG,
                        "Could not mint token for ${account.server}; WS will not authenticate"
                    )
                    return@launch
                }
                Log.i(
                    TAG,
                    "Subscribing to push channel on ${account.server} (identity ${account.identity})"
                )
                val identity = account.identity
                credentials[identity] = token
                val drainer = drainers.getOrPut(identity) { Drainer() }
                registered = channels.register(slot) {
                    webSocket.subscribePush(
                        server = account.server,
                        account = identity,
                        token = token,
                        mint = {
                            mint(account.identity, account.server, account.session).also { minted ->
                                if (minted is Mint.Fresh) credentials[identity] = minted.token
                            }
                        },
                        // Drained events take the live dispatch path.
                        drain = {
                            scope.launch {
                                drainer.request { credentials[identity]?.let { current -> drainPending(account.server, current) } }
                            }
                        },
                        onEvent = { event -> handleEvent(event, account.server, credentials[identity] ?: token) },
                    )
                }
            } finally {
                // Without a channel the slot is given up, so the next
                // reconcile() can retry.
                if (!registered) channels.abandon(slot)
            }
        }
    }

    /**
     * A fresh notifications token for the account whose session cookie is
     * [sessionCookie], or why there is none: [Mint.Gone] once its session is
     * found dead, [Mint.Later] for anything that may pass.
     */
    private suspend fun mint(identity: String, server: String, sessionCookie: String): Mint {
        val httpUrl = (server.trimEnd('/') + "/").toHttpUrl()
        val cookie = Cookie.Builder()
            .domain(httpUrl.host)
            .path("/")
            .name("session")
            .value(sessionCookie)
            .secure()
            .build()
        // A one-shot client carrying just this cookie: the session must not
        // reach the shared cookie jar, and the jar releases it only to this
        // account's own origin. A redirect off that origin - or the
        // bound-server retarget this client deliberately does not carry -
        // would otherwise hand one server a working session for another.
        val tempClient = client.newBuilder()
            .cookieJar(object : okhttp3.CookieJar {
                override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<Cookie>) {}
                override fun loadForRequest(url: okhttp3.HttpUrl): List<Cookie> =
                    if (isServerOrigin(url, server)) listOf(cookie) else emptyList()
            })
            .build()
        val tokenApi = ApiClient.createRetrofit(server, tempClient, gson)
            .create(TokenApi::class.java)
        return try {
            val token = tokenApi.fetchToken(TokenRequest("notifications")).unwrapRaw().token
            Reauth.reportSuccess(applicationContext, identity)
            if (token.isBlank()) Mint.Later else Mint.Fresh(token)
        } catch (e: Exception) {
            Log.w(TAG, "/_/token failed: ${e.message}")
            // A session found dead is told to the user once, and its channel
            // waits for the account to sign in again rather than asking on.
            if (e is ApiException && e.code == 401 && Reauth.report401(applicationContext, identity)) {
                Mint.Gone
            } else {
                Mint.Later
            }
        }
    }

    private fun handleEvent(
        event: org.mochios.android.model.WebSocketEvent,
        server: String,
        token: String,
    ) {
        val subId = event.subId ?: return
        val payload = event.payload ?: return
        dispatchPush(subId, payload)
        // Ack so the server drops the push_pending row. `account` identifies
        // it; the subscription id is only a routing token. Without it the row waits
        // for the TTL sweep.
        val account = event.account?.takeIf { it.isNotBlank() } ?: return
        val eventId = extractTag(payload) ?: return
        scope.launch { ackEvent(server, token, account, eventId) }
    }

    private fun dispatchPush(subId: String, payload: String) {
        val entry = store.bySubId(subId)
        // Never log the subscription id: it is the unguessable segment of the
        // push endpoint, so anything that reads logcat would learn the
        // capability. DistributorStore and MochiPushReceiver hold the same
        // line.
        if (entry == null) {
            Log.w(TAG, "Received push for an unknown subscription; dropping")
            return
        }
        Log.i(TAG, "Dispatching push to ${entry.appPackage}")
        // Our own action, not the connector's. The connector decrypts every
        // MESSAGE it receives and marks a payload that fails as undecrypted,
        // and this one is cleartext by design - the server sends it that way
        // over the user's authenticated WebSocket rather than encrypting to a
        // Web Push key (core/server/accounts.go, account_deliver_unifiedpush).
        // Broadcast on the connector's action and the receiver correctly drops
        // it, which is what left self-hosted push inert.
        //
        // The PendingIntent is what replaces the decrypt as the authenticity
        // check: it carries no action of its own and is never sent, it exists
        // so the receiver can read creatorPackage and know the broadcast came
        // from this distributor rather than any app that guessed the action.
        val identity = PendingIntent.getBroadcast(
            applicationContext, 0, Intent(), PendingIntent.FLAG_IMMUTABLE,
        )
        val out = Intent(ACTION_LOCAL_MESSAGE).apply {
            setPackage(entry.appPackage)
            putExtra(EXTRA_TOKEN, entry.token)
            putExtra(EXTRA_BYTES_MESSAGE, payload.toByteArray(Charsets.UTF_8))
            putExtra(MochiDistributorReceiver.EXTRA_PI, identity)
        }
        applicationContext.sendBroadcast(out)
    }

    /**
     * Dispatch and ack everything queued at `/notifications/-/push/drain`,
     * each time the account's socket opens. The ack is a separate POST, so a
     * crash mid-drain leaves the rows for the next open.
     */
    private fun drainPending(server: String, token: String) {
        val url = server.trimEnd('/') + "/notifications/-/push/drain"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post("".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "/notifications/-/push/drain returned ${resp.code}")
                    return@use
                }
                val raw = resp.body?.string().orEmpty()
                val events = JSONObject(raw).optJSONArray("data") ?: return@use
                if (events.length() == 0) return@use
                Log.i(TAG, "Draining ${events.length()} queued event(s)")
                val acks = org.json.JSONArray()
                for (i in 0 until events.length()) {
                    val ev = events.getJSONObject(i)
                    val subId = ev.optString("subscription")
                    val payload = ev.optString("payload")
                    val account = ev.optString("account")
                    val eventId = ev.optString("event")
                    if (subId.isBlank() || payload.isBlank()) continue
                    dispatchPush(subId, payload)
                    if (account.isNotBlank() && eventId.isNotBlank()) {
                        acks.put(JSONObject().put("account", account).put("event", eventId))
                    }
                }
                if (acks.length() > 0) {
                    ackBatch(server, token, acks)
                }
            }
        }.onFailure { Log.w(TAG, "Drain failed: ${it.message}") }
    }

    private fun ackEvent(server: String, token: String, account: String, eventId: String) {
        val acks = org.json.JSONArray().put(
            JSONObject().put("account", account).put("event", eventId)
        )
        ackBatch(server, token, acks)
    }

    private fun ackBatch(server: String, token: String, acks: org.json.JSONArray) {
        val url = server.trimEnd('/') + "/notifications/-/push/ack"
        val form = okhttp3.FormBody.Builder()
            .add("events", acks.toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(form)
            .build()
        runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "/notifications/-/push/ack returned ${resp.code}")
                }
            }
        }.onFailure { Log.w(TAG, "Ack failed: ${it.message}") }
    }

    private fun extractTag(payload: String): String? = runCatching {
        JSONObject(payload).optString("tag").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun startForegroundCompat() {
        val pendingFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE

        // Tap → open the host app (whichever app is the current package's
        // launch entry — typically the Mochi shell when this service is
        // running there).
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val tapPendingIntent = launchIntent?.let {
            android.app.PendingIntent.getActivity(this, 0, it, pendingFlags)
        }

        // Hide action opens this channel's settings, so switching it off
        // silences only this notification. Swipe-to-dismiss on Samsung offers
        // to turn off all of the app's notifications, taking the per-app push
        // channels with it.
        val hideIntent =
            android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val hidePendingIntent =
            android.app.PendingIntent.getActivity(this, 1, hideIntent, pendingFlags)

        val builder = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(org.mochios.android.R.string.push_service_title))
            .setSmallIcon(org.mochios.android.R.drawable.ic_mochi_notification)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            // CATEGORY_SERVICE groups this with other background-activity
            // notices in the shade and in DND filters, apart from the per-app
            // push channels.
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .apply {
                if (tapPendingIntent != null) setContentIntent(tapPendingIntent)
            }
            .addAction(
                0,
                getString(org.mochios.android.R.string.push_service_action_hide),
                hidePendingIntent,
            )

        val notification: Notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Group first: the channel references it. Both calls are idempotent,
        // and re-creating an existing channel updates its non-user-locked
        // fields, so an install upgraded from a build without the group picks
        // it up.
        nm.createNotificationChannelGroup(
            android.app.NotificationChannelGroup(
                CHANNEL_GROUP_ID,
                getString(org.mochios.android.R.string.push_service_group),
            )
        )
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(org.mochios.android.R.string.push_service_channel),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(org.mochios.android.R.string.push_service_channel_description)
            group = CHANNEL_GROUP_ID
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "mochi_push_service"
        const val CHANNEL_GROUP_ID = "mochi_service_status"
        const val NOTIFICATION_ID = 0x4D43_0001 // 'MC' 0001
        const val FINGERPRINT = "unifiedpush"

        // UnifiedPush v3 wire constants for the App-bound MESSAGE broadcast.
        const val ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE"

        // Mochi's own delivery action. A third-party distributor still uses
        // ACTION_MESSAGE above and its payload is genuinely encrypted; this one
        // is for the local fast path, whose payload is not.
        const val ACTION_LOCAL_MESSAGE = "org.mochios.android.push.MESSAGE"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_BYTES_MESSAGE = "bytesMessage"

        const val TAG = "MochiPushService"

        fun start(context: android.content.Context) {
            val intent = Intent(context, PushService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the distributor service; [PushTransport] calls this when the
         * server has FCM configured.
         */
        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, PushService::class.java))
        }

        /**
         * Drop this device's push account server-side so delivery stops after
         * sign-out. Call before clearing the session - the request needs a
         * notifications app token. Failures are logged and swallowed.
         */
        suspend fun removeAccount(context: android.content.Context) {
            val deps = EntryPointAccessors.fromApplication(
                context.applicationContext,
                PushEntryPoint::class.java
            )
            val identity = deps.sessionManager().getBoundIdentity().orEmpty()
            if (identity.isBlank()) return
            val store = deps.pushAccountStore()
            val accountId = store.read(identity) ?: return
            runCatching { deps.notificationsRepository().removeAccount(accountId) }
                .onSuccess {
                    Log.i(TAG, "Removed push account $accountId")
                    store.clear(identity)
                }
                .onFailure { e -> Log.w(TAG, "accounts/remove failed: ${e.message}") }
        }
    }
}

/** The push channel of one account: the ids to hand back when it leaves. */
internal data class PushChannel(val subscription: String, val connection: String)

/**
 * Subscribe [account]'s push channel on [server], authorised with that
 * account's own [token]. Each account gets a socket of its own, so two on one
 * server each hear their own pushes. It stays open in the background, where
 * push is delivered and every other socket closes, and stands apart from the
 * signed-in session: the account may be another one, and it keeps its push
 * when the signed-in one logs out. [PushService] drops it as the account
 * leaves.
 *
 * @param mint a fresh token for the account, when core refuses the one held.
 * @param drain asks for the server's queue to be drained. Called each time the
 *   socket opens, first open and every reopen alike: what was queued while no
 *   socket was live reaches the phone no other way, and a drain before the
 *   socket is open would miss a push queued between the two.
 */
internal fun MochiWebSocket.subscribePush(
    server: String,
    account: String,
    token: String,
    mint: suspend () -> Mint,
    drain: () -> Unit,
    onEvent: (WebSocketEvent) -> Unit,
): PushChannel {
    val connection = subscribeConnected(server, PushService.FINGERPRINT, account) { drain() }
    val subscription = subscribe(
        serverUrl = server,
        fingerprint = PushService.FINGERPRINT,
        token = token,
        background = true,
        account = account,
        mint = mint,
        onEvent = onEvent,
    )
    // Once now as well: a socket that cannot open - refused at the cap,
    // websockets blocked on the way - would otherwise leave the queue
    // undrained. Overlapping drains are folded into one.
    drain()
    return PushChannel(subscription, connection)
}

/**
 * The push channels the service holds, one per account, with the session each
 * was opened under. Registering a channel and closing the lot are mutually
 * exclusive: a channel being opened as the service is destroyed is either
 * registered first and torn down with the rest, or never opened.
 *
 * @param drop tears a channel down.
 */
internal class Channels(private val drop: (PushChannel) -> Unit) {

    /** One account's claim on a channel. */
    internal class Slot(val identity: String, val session: String) {
        var channel: PushChannel? = null
    }

    private val slots = HashMap<String, Slot>()
    private var closed = false

    /**
     * Claim [identity]'s channel, to be opened under [session].
     *
     * @return the claim, or null when one is held or the service has gone.
     */
    @Synchronized
    fun claim(identity: String, session: String): Slot? {
        if (closed || identity in slots) return null
        return Slot(identity, session).also { slot -> slots[identity] = slot }
    }

    /**
     * Open [slot]'s channel with [open] and keep it, unless the claim was
     * released meanwhile - the service gone, the account with it - in which
     * case nothing opens.
     */
    @Synchronized
    fun register(slot: Slot, open: () -> PushChannel): Boolean {
        if (slots[slot.identity] !== slot || slot.channel != null) return false
        slot.channel = open()
        return true
    }

    /** Whether [slot] is still the claim on its account's channel. */
    @Synchronized
    fun holds(slot: Slot): Boolean = slots[slot.identity] === slot

    /** Give up [slot] if it never got a channel. */
    @Synchronized
    fun abandon(slot: Slot) {
        if (slots[slot.identity] === slot && slot.channel == null) slots.remove(slot.identity)
    }

    /** Give up [identity]'s claim, tearing its channel down. */
    fun release(identity: String) {
        val channel = synchronized(this) { slots.remove(identity)?.channel }
        channel?.let(drop)
    }

    /** The identities held that are gone from [accounts], or signed in again under another session. */
    @Synchronized
    fun stale(accounts: List<MochiAccount.Snapshot>): List<String> {
        val sessions = accounts.associate { account -> account.identity to account.session }
        return slots.values.filter { slot -> sessions[slot.identity] != slot.session }.map { slot -> slot.identity }
    }

    /** Tear every channel down, and open no more. */
    fun close() {
        val held = synchronized(this) {
            closed = true
            slots.values.mapNotNull { slot -> slot.channel }.also { slots.clear() }
        }
        held.forEach(drop)
    }
}

/**
 * Runs an account's drains one at a time. Two at once would each read the
 * queue before either acked it, and dispatch the same rows twice. A request
 * while one runs asks for one more pass once it ends, since what it read may
 * already be behind; further requests meanwhile fold into that pass.
 */
internal class Drainer {
    private val state = java.util.concurrent.atomic.AtomicInteger(IDLE)

    fun request(drain: () -> Unit) {
        while (true) {
            when (state.get()) {
                IDLE -> if (state.compareAndSet(IDLE, RUNNING)) break
                RUNNING -> if (state.compareAndSet(RUNNING, AGAIN)) return
                else -> return
            }
        }
        while (true) {
            // A drain that throws must not leave the next one locked out.
            runCatching(drain)
            if (state.compareAndSet(RUNNING, IDLE)) return
            state.set(RUNNING)
        }
    }

    private companion object {
        const val IDLE = 0
        const val RUNNING = 1
        const val AGAIN = 2
    }
}

/**
 * The first token for an account's channel. Asked for again along a backoff
 * ladder while the answer is [Mint.Later] - the network down at boot, say - so
 * the channel comes up without the app being opened.
 *
 * @param held whether the channel is still wanted; a claim released meanwhile
 *   stops the asking.
 * @param pause waits before the [attempt]th asking again.
 * @return the token, or null for a session that is gone, which only signing
 *   in again mends, or a claim given up.
 */
internal suspend fun firstToken(
    mint: suspend () -> Mint,
    held: () -> Boolean,
    pause: suspend (attempt: Int) -> Unit,
): String? {
    var attempt = 0
    while (held()) {
        when (val minted = mint()) {
            is Mint.Fresh -> return minted.token
            Mint.Gone -> return null
            Mint.Later -> pause(++attempt)
        }
    }
    return null
}

/** The wait before the [attempt]th try: 1s doubling to five minutes. */
internal fun ladder(attempt: Int): Long = min(1000L shl (attempt - 1).coerceIn(0, 8), 300_000L)

/**
 * A wait along the backoff ladder that a network coming up cuts short. A signal
 * given while nobody waits is not kept: the ladder covers it.
 */
internal class Online {
    private val signal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** A network has come up. */
    fun available() {
        signal.tryEmit(Unit)
    }

    /** Wait [millis], or until a network comes up. */
    suspend fun await(millis: Long) {
        withTimeoutOrNull(millis) { signal.first() }
    }
}
