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
 * Foreground service hosting the Mochi UnifiedPush distributor. Holds one
 * WebSocket subscription per Mochi identity on the `unifiedpush` channel and
 * broadcasts each incoming `{subId, payload}` as a MESSAGE intent to the
 * registered app.
 */
@AndroidEntryPoint
class PushService : Service() {

    @Inject
    lateinit var webSocket: MochiWebSocket
    @Inject
    lateinit var okHttpClient: OkHttpClient
    @Inject
    lateinit var gson: Gson

    private val store by lazy { DistributorStore(applicationContext) }

    // identity -> subscriptionId. ConcurrentHashMap so reconcile() can claim a
    // slot with putIfAbsent: onStartCommand fires reentrantly and a double
    // claim dispatches every push twice. Keyed by identity, since two accounts
    // can share a server.
    private val subscriptions = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var accountsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
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
        accountsJob?.cancel()
        for ((_, id) in subscriptions) {
            if (id != PENDING) webSocket.unsubscribe(id)
        }
        subscriptions.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Multiplex subscriptions across the current Mochi identities: connect new
     * ones, drop vanished ones. Safe to call concurrently and reentrantly.
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
                Log.i(TAG, "Identity gone; unsubscribing")
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
                // The shell process has no login, so the cookie jar is empty.
                // Mint a JWT from the cross-app MochiAccount session cookie.
                // MochiWebSocket sends it as a header, not a query parameter,
                // to keep it out of anything that logs URLs.
                val token = mintToken(account.server, account.session)
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
                sid = webSocket.subscribe(
                    serverUrl = account.server,
                    fingerprint = FINGERPRINT,
                    token = token,
                ) { event -> handleEvent(event, account.server, token) }
                subscriptions[account.identity] = sid
                // Drain what the server queued while no subscriber was live
                // (killed phone, Doze, network). Drained events take the live
                // dispatch path.
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
        // Ack so the server drops the push_pending row. `account` identifies
        // it; subId is only the subscription token. Without it the row waits
        // for the TTL sweep.
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
     * Dispatch and ack everything queued at `/notifications/-/push/drain`, once
     * per successful subscribe. The ack is a separate POST, so a crash
     * mid-drain leaves the rows for the next subscribe.
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
                Log.i(TAG, "Draining ${events.length()} queued event(s)")
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
        const val PENDING = "_pending_"

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
