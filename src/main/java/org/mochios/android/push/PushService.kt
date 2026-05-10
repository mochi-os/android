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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mochios.android.account.MochiAccount
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

    private val store by lazy { DistributorStore(applicationContext) }
    // server → subscriptionId. Uses ConcurrentHashMap so connect() can claim
    // a slot atomically via putIfAbsent — onStartCommand can fire reentrantly
    // (Hilt re-init, system restarts) and we'd otherwise register the same
    // callback twice and dispatch each push N times.
    private val subscriptions = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        connect()
        return START_STICKY
    }

    override fun onDestroy() {
        for ((_, id) in subscriptions) {
            webSocket.unsubscribe(id)
        }
        subscriptions.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connect() {
        val account = MochiAccount.first(applicationContext) ?: run {
            Log.w(TAG, "No Mochi account; idle until one is added")
            return
        }
        // Claim the slot atomically before launching the coroutine. If a
        // concurrent connect() already claimed (returns non-null), bail out
        // so we don't end up registering two callbacks against
        // MochiWebSocket.subscribe and dispatching every push twice.
        if (subscriptions.putIfAbsent(account.server, PENDING) != null) return

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
                Log.i(TAG, "Subscribing to push channel on ${account.server}")
                sid = webSocket.subscribe(
                    serverUrl = account.server,
                    fingerprint = FINGERPRINT,
                    token = token,
                ) { event -> handleEvent(event) }
                subscriptions[account.server] = sid
            } finally {
                // If we didn't get to a real subscription id, drop the
                // placeholder so the next connect() can retry.
                if (sid == null) subscriptions.remove(account.server, PENDING)
            }
        }
    }

    private fun mintToken(server: String, sessionCookie: String): String? {
        val url = server.trimEnd('/') + "/_/token"
        val httpUrl = url.toHttpUrl()
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
        val body = JSONObject().put("app", "menu").toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        return runCatching {
            tempClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "/_/token returned ${resp.code}")
                    if (resp.code == 401) {
                        Reauth.report401(applicationContext, server)
                    }
                    return@use null
                }
                Reauth.reportSuccess(applicationContext, server)
                JSONObject(resp.body?.string().orEmpty()).optString("token").ifBlank { null }
            }
        }.getOrNull()
    }

    private fun handleEvent(event: org.mochios.android.model.WebSocketEvent) {
        val subId = event.subId ?: return
        val payload = event.payload ?: return
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

        // "Hide" action → deep-link to this channel's notification settings,
        // so the user can disable display without killing the FG service.
        val hideIntent = android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val hidePendingIntent = android.app.PendingIntent.getActivity(this, 1, hideIntent, pendingFlags)

        val builder = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(org.mochios.android.R.string.push_service_title))
            .setContentText(getString(org.mochios.android.R.string.push_service_text))
            .setSmallIcon(org.mochios.android.R.drawable.ic_mochi_notification)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
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
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(org.mochios.android.R.string.push_service_channel),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(org.mochios.android.R.string.push_service_channel_description)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "mochi_push_service"
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
    }
}
