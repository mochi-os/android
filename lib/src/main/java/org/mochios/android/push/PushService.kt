// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mochios.android.account.MochiAccount
import org.mochios.android.api.ApiClient
import org.mochios.android.api.ApiException
import org.mochios.android.api.unwrapRaw
import org.mochios.android.auth.TokenApi
import org.mochios.android.auth.TokenRequest
import org.mochios.android.websocket.MochiWebSocket
import javax.inject.Inject

/**
 * Foreground service that hosts the Mochi UnifiedPush distributor on a
 * device that has chosen "Mochi" as its distributor.
 *
 * Lifecycle:
 *   - Started by the shell app (org.mochios.mochi) on launch and after
 *     boot via [BootReceiver].
 *   - Holds one [MochiWebSocket] subscription per active Mochi identity,
 *     keyed by the well-known channel "unifiedpush" (matches the Go-side
 *     fast-path in `account_deliver_unifiedpush`).
 *   - On incoming `{subId, payload}` events, looks up the matching
 *     subscription in [DistributorStore] and broadcasts a UnifiedPush
 *     MESSAGE intent to the registered App package.
 *
 * v1 limitations:
 *   - Subscribes to a single (first) identity. Multi-identity multiplexing
 *     across multiple Mochi servers is the natural next refinement —
 *     iterate `MochiAccount.all()` and subscribe per server, with
 *     `accountsFlow` rebinding when identities change.
 *   - Foreground notification text is fixed English; should be replaced
 *     with a localised string resource once the shell's strings.xml
 *     gains a `push_service_running` entry.
 */
@AndroidEntryPoint
class PushService : Service() {

    @Inject lateinit var webSocket: MochiWebSocket
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var gson: Gson

    private val store by lazy { DistributorStore(applicationContext) }
    // identity → subscriptionId. ConcurrentHashMap so reconcile() can claim
    // a slot atomically via putIfAbsent — onStartCommand can fire reentrantly
    // (Hilt re-init, system restarts, accountsFlow emissions) and we'd
    // otherwise register the same callback twice and dispatch each push N
    // times. Keyed by identity (not server) because two accounts can live on
    // the same server with different user tokens.
    private val subscriptions = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var accountsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // System-driven STICKY restart can still bring this service back even
        // after the user has moved to FCM and our own callers have stopped
        // calling PushService.start — Android remembers the previous live
        // instance and replays it once it has resources. Bail out before
        // startForegroundCompat so the "listening for notifications" FG
        // notification never appears for FCM users. Intent is null on
        // system-driven restarts (so we can't gate off the intent); we read
        // the cached transport from PushTransport.current instead.
        if (PushTransport.current(applicationContext) == PushTransport.TRANSPORT_FCM) {
            Log.i(TAG, "onStartCommand: transport=fcm; stopping self")
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
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
        accountsJob?.cancel()
        for ((_, id) in subscriptions) {
            if (id != PENDING) webSocket.unsubscribe(id)
        }
        subscriptions.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Multiplex subscriptions across the current set of Mochi identities.
     *
     * For each account not yet subscribed → connect.
     * For each existing subscription whose identity has disappeared → drop.
     *
     * Safe to call concurrently and reentrantly: the per-identity slot
     * claim uses putIfAbsent, so a second caller observing the same account
     * set won't double-subscribe.
     */
    private fun reconcile(accounts: List<MochiAccount.Snapshot>) {
        if (accounts.isEmpty()) {
            Log.w(TAG, "No Mochi accounts; idle until one is added")
            // Drop any leftover subscriptions if the last account got removed.
            for ((identity, id) in subscriptions) {
                if (id != PENDING) webSocket.unsubscribe(id)
                subscriptions.remove(identity)
            }
            return
        }
        val current = accounts.map { it.identity }.toSet()
        // Drop subscriptions whose identity is no longer present.
        for ((identity, id) in subscriptions) {
            if (identity !in current) {
                Log.i(TAG, "Identity $identity gone; unsubscribing")
                if (id != PENDING) webSocket.unsubscribe(id)
                subscriptions.remove(identity)
            }
        }
        // Add subscriptions for newly-present identities.
        for (account in accounts) {
            connectOne(account)
        }
    }

    private fun connectOne(account: MochiAccount.Snapshot) {
        // Claim the slot atomically before launching the coroutine. If a
        // concurrent reconcile() already claimed (returns non-null), bail
        // out so we don't end up registering two callbacks against
        // MochiWebSocket.subscribe and dispatching every push twice.
        if (subscriptions.putIfAbsent(account.identity, PENDING) != null) return

        scope.launch {
            var sid: String? = null
            try {
                // The shell process has no SessionManager-backed login, so
                // the OkHttpClient cookie jar is empty. Mint a JWT against
                // the session cookie we read from the cross-app MochiAccount
                // and pass it on the WebSocket URL — the server accepts a
                // `token` query parameter as an alternative to the session
                // cookie.
                val token = mintToken(account.server, account.session)
                if (token == null) {
                    Log.w(TAG, "Could not mint token for ${account.server}; WS will not authenticate")
                    return@launch
                }
                Log.i(TAG, "Subscribing to push channel on ${account.server} (identity ${account.identity})")
                sid = webSocket.subscribe(
                    serverUrl = account.server,
                    fingerprint = FINGERPRINT,
                    token = token,
                ) { event -> handleEvent(event, account.server, token) }
                subscriptions[account.identity] = sid
                // Drain anything queued while we were offline. The server queues
                // events whenever the live WS push had no subscriber (phone
                // killed, Doze drop, transient network). Drained events go
                // through the same dispatch path as live ones so per-app
                // receivers see them as ordinary pushes.
                drainPending(account.server, token)
            } finally {
                // If we didn't get to a real subscription id, drop the
                // placeholder so the next reconcile() can retry.
                if (sid == null) subscriptions.remove(account.identity, PENDING)
            }
        }
    }

    private suspend fun mintToken(server: String, sessionCookie: String): String? {
        val httpUrl = (server.trimEnd('/') + "/").toHttpUrl()
        val cookie = Cookie.Builder()
            .domain(httpUrl.host)
            .path("/")
            .name("session")
            .value(sessionCookie)
            .secure()
            .build()
        // Build a one-shot client with just this cookie attached. We do
        // not want to persist the session cookie into the shared
        // OkHttpClient cookie jar, only use it to authenticate the mint.
        val tempClient = okHttpClient.newBuilder()
            .cookieJar(object : okhttp3.CookieJar {
                override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<Cookie>) {}
                override fun loadForRequest(url: okhttp3.HttpUrl): List<Cookie> = listOf(cookie)
            })
            .build()
        val tokenApi = ApiClient.createRetrofit(server, tempClient, gson)
            .create(TokenApi::class.java)
        return try {
            val token = tokenApi.fetchToken(TokenRequest("notifications")).unwrapRaw().token
            Reauth.reportSuccess(applicationContext, server)
            token.ifBlank { null }
        } catch (e: Exception) {
            Log.w(TAG, "/_/token failed: ${e.message}")
            if (e is ApiException && e.code == 401) {
                Reauth.report401(applicationContext, server)
            }
            null
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
        // Ack so the matching push_pending row is removed server-side. The
        // server includes `account` (the opaque accounts.id, a string uid) on
        // the WS envelope specifically for this — subId alone is the random
        // subscription token and can't identify the queue row. Without
        // account we silently skip the ack and the row stays until the
        // 7-day TTL sweep.
        val account = event.account?.takeIf { it.isNotBlank() } ?: return
        val eventId = extractTag(payload) ?: return
        scope.launch { ackEvent(server, token, account, eventId) }
    }

    private fun dispatchPush(subId: String, payload: String) {
        val entry = store.bySubId(subId)
        if (entry == null) {
            Log.w(TAG, "Received push for unknown subId=$subId; dropping")
            return
        }
        Log.i(TAG, "Dispatching push subId=$subId → ${entry.appPackage}")
        val out = Intent(ACTION_MESSAGE).apply {
            setPackage(entry.appPackage)
            putExtra(EXTRA_TOKEN, entry.token)
            putExtra(EXTRA_BYTES_MESSAGE, payload.toByteArray(Charsets.UTF_8))
        }
        applicationContext.sendBroadcast(out)
    }

    /**
     * GET /notifications/-/push/drain → dispatch each queued event and ack in one batch.
     *
     * Fires once after every successful WS subscribe. The drain itself is
     * read-only on the server, and the ack is a separate POST with the list
     * of (account, event_id) pairs we delivered — so a crash mid-drain leaves
     * the rows in place and we'll see them again on the next subscribe.
     */
    private fun drainPending(server: String, token: String) {
        val url = server.trimEnd('/') + "/notifications/-/push/drain"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post("".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        runCatching {
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "/notifications/-/push/drain returned ${resp.code}")
                    return@use
                }
                val raw = resp.body?.string().orEmpty()
                val events = JSONObject(raw).optJSONArray("data") ?: return@use
                if (events.length() == 0) return@use
                Log.i(TAG, "Draining ${events.length()} queued event(s) from $server")
                val acks = org.json.JSONArray()
                for (i in 0 until events.length()) {
                    val ev = events.getJSONObject(i)
                    val subId = ev.optString("subId")
                    val payload = ev.optString("payload")
                    val account = ev.optString("account")
                    val eventId = ev.optString("event_id")
                    if (subId.isBlank() || payload.isBlank()) continue
                    dispatchPush(subId, payload)
                    if (account.isNotBlank() && eventId.isNotBlank()) {
                        acks.put(JSONObject().put("account", account).put("event_id", eventId))
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
            JSONObject().put("account", account).put("event_id", eventId)
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
            okHttpClient.newCall(request).execute().use { resp ->
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

        // Hide action → opens this channel's notification settings (scoped to
        // mochi_push_service only), so the user toggling 'Show notifications'
        // off there silences just this listening notification and leaves the
        // per-app push channels (Feeds / Chat / Forums / Projects) alone.
        // Critical that users discover *this* path rather than swipe-to-dismiss,
        // which on Samsung pops a "Turn off ALL notifications from this app?"
        // dialog and would kill the per-app channels too.
        val hideIntent = android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val hidePendingIntent = android.app.PendingIntent.getActivity(this, 1, hideIntent, pendingFlags)

        val builder = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(org.mochios.android.R.string.push_service_title))
            .setSmallIcon(org.mochios.android.R.drawable.ic_mochi_notification)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            // Mark this as a service-status notification so Android groups it
            // with other background-activity notices in the shade and in DND
            // / per-app filters — keeps it visually and semantically distinct
            // from the per-app push channels (feeds / chat / forums / projects)
            // so hiding it via the channel toggle doesn't ripple into them.
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
        // Group goes in first — channel.group references it. The pair surfaces
        // the FG-service channel under a 'Background service' header in Android
        // settings, well away from the per-app channels (Feeds / Chat / Forums
        // / Projects) that live ungrouped. Disabling the FG channel is then
        // visually clearly distinct from disabling a feature's notifications.
        // Both calls are idempotent on (group_id, channel_id), and calling
        // createNotificationChannel on an existing channel updates the
        // non-user-locked fields (including `group`) — important for installs
        // upgraded from a build that didn't have the group yet.
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
        const val PENDING = "_pending_"

        // UnifiedPush v3 wire constants for the App-bound MESSAGE broadcast.
        const val ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE"
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
         * Stop the UP distributor service. Used by [PushTransport] when the
         * server has FCM configured — we don't want the FG service running
         * (and the "listening for notifications" status notification it
         * implies) when FCM is doing the delivery.
         */
        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, PushService::class.java))
        }
    }
}
