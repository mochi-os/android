package org.mochios.android.push

import android.content.Context
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mochios.android.auth.SessionManager
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Bridges UnifiedPush callbacks into Mochi-shaped events. The super-app
 * subclasses this once with a single MochiPushDispatcher that routes by
 * the deep-link's first path segment; the base class handles the wire
 * decoding, server-side bookkeeping, and notification posting.
 */
abstract class MochiPushReceiver : MessagingReceiver() {

    /**
     * Channel id for the system notification. The dispatcher picks the
     * per-app channel from [app] when the server includes it in the payload,
     * and falls back to parsing [link]'s first path segment when it doesn't
     * (older server, system notifications without a link, etc.).
     */
    abstract fun channelId(context: Context, instance: String, app: String, link: String): String

    /** Deep-link Uri for tapping the notification. */
    abstract fun deepLinkFor(context: Context, instance: String, link: String): android.net.Uri

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PushDeps {
        fun sessionManager(): SessionManager
        fun okHttpClient(): OkHttpClient
    }

    private fun deps(context: Context): PushDeps =
        EntryPointAccessors.fromApplication(context.applicationContext, PushDeps::class.java)

    override fun onNewEndpoint(context: Context, endpoint: PushEndpoint, instance: String) {
        Log.i(TAG, "onNewEndpoint instance=$instance url=${endpoint.url}")
        val keys = endpoint.pubKeySet
        if (keys == null) {
            Log.w(TAG, "Endpoint has no Web Push keys — server can't encrypt; aborting")
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deps = deps(context)
                val server = deps.sessionManager().getServerUrlBlocking()
                // When the distributor's endpoint is on our own Mochi server,
                // send the path-only form so the server can take its in-process
                // WebSocket fast-path instead of POSTing RFC 8030 back to itself
                // (which would hit the still-stubbed inbound endpoint and 501).
                val endpointToSend = collapseLocalEndpoint(endpoint.url, server)
                Log.i(TAG, "register: endpoint.url=${endpoint.url} server=$server collapsed=$endpointToSend")
                val accountId = postPushRegister(
                    deps.okHttpClient(),
                    server = server,
                    label = DeviceName.resolve(context),
                    auth = keys.auth,
                    p256dh = keys.pubKey,
                    endpoint = endpointToSend,
                )
                if (accountId != null) {
                    storeAccountId(context, instance, accountId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register endpoint with Mochi server: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * If [endpointUrl] is hosted on the same origin as [server] (the
     * Mochi-distributor case where our user's server allocated this
     * endpoint), return just the path component so the server detects
     * the local-delivery fast-path. Otherwise return the URL unchanged
     * (the third-party-distributor case — e.g. ntfy.sh — where the
     * server must POST RFC 8030 to the absolute URL).
     */
    private fun collapseLocalEndpoint(endpointUrl: String, server: String): String {
        return try {
            val ep = android.net.Uri.parse(endpointUrl)
            val sv = android.net.Uri.parse(server)
            if (ep.host != null && ep.host == sv.host) ep.encodedPath ?: endpointUrl
            else endpointUrl
        } catch (_: Exception) {
            endpointUrl
        }
    }

    override fun onUnregistered(context: Context, instance: String) {
        Log.i(TAG, "onUnregistered instance=$instance")
        val accountId = readAccountId(context, instance) ?: return
        clearAccountId(context, instance)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deps = deps(context)
                val server = deps.sessionManager().getServerUrlBlocking()
                postPushAccountsRemove(deps.okHttpClient(), server, accountId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister endpoint from Mochi server: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onRegistrationFailed(
        context: Context,
        reason: org.unifiedpush.android.connector.FailedReason,
        instance: String,
    ) {
        Log.w(TAG, "onRegistrationFailed instance=$instance reason=$reason")
    }

    override fun onMessage(context: Context, message: PushMessage, instance: String) {
        val text = message.content.toString(Charsets.UTF_8)
        val payload = try {
            JSONObject(text)
        } catch (_: Exception) {
            Log.w(TAG, "Push payload not JSON; ignoring")
            return
        }

        val title = payload.optString("title", "")
        val body = payload.optString("body", "")
        val link = payload.optString("link", "")
        val tag = payload.optString("tag", "")
        val app = payload.optString("app", "")

        if (title.isBlank() && body.isBlank()) {
            Log.w(TAG, "Push payload has no title/body; ignoring")
            return
        }

        postSystemNotification(context, instance, title, body, link, tag, app)
    }

    private fun postPushRegister(
        client: OkHttpClient,
        server: String,
        label: String,
        auth: String,
        p256dh: String,
        endpoint: String,
    ): Int? {
        val token = mintAppToken(client, server, "notifications") ?: return null
        val url = server.trimEnd('/') + "/notifications/-/push/register"
        val form = FormBody.Builder()
            .add("label", label)
            .add("auth", auth)
            .add("p256dh", p256dh)
            .add("endpoint", endpoint)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(form)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "/notifications/-/push/register returned ${resp.code}")
                return null
            }
            val body = resp.body?.string().orEmpty()
            return try {
                JSONObject(body).optJSONObject("data")?.optInt("id")?.takeIf { it > 0 }
            } catch (_: Exception) {
                Log.w(TAG, "Could not parse /notifications/-/push/register response")
                null
            }
        }
    }

    private fun postPushAccountsRemove(client: OkHttpClient, server: String, accountId: Int) {
        val token = mintAppToken(client, server, "notifications") ?: return
        val url = server.trimEnd('/') + "/notifications/-/push/accounts/remove"
        val form = FormBody.Builder().add("id", accountId.toString()).build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(form)
            .build()
        client.newCall(request).execute().close()
    }

    /**
     * Mint a JWT for the named app via POST /_/token. Authorised by the
     * session cookie (already on the OkHttpClient's CookieJar from the
     * per-app's login flow). Same pattern the web shell uses to issue
     * tokens to its iframe SPAs.
     */
    private fun mintAppToken(client: OkHttpClient, server: String, app: String): String? {
        val url = server.trimEnd('/') + "/_/token"
        val body = JSONObject().put("app", app).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "/_/token($app) returned ${resp.code}")
                return null
            }
            return try {
                JSONObject(resp.body?.string().orEmpty()).optString("token").ifBlank { null }
            } catch (_: Exception) {
                Log.w(TAG, "Could not parse /_/token response")
                null
            }
        }
    }

    private fun storeAccountId(context: Context, instance: String, accountId: Int) {
        context.applicationContext
            .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(prefKey(instance), accountId)
            .apply()
    }

    private fun readAccountId(context: Context, instance: String): Int? {
        val prefs = context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val v = prefs.getInt(prefKey(instance), -1)
        return if (v > 0) v else null
    }

    private fun clearAccountId(context: Context, instance: String) {
        context.applicationContext
            .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .remove(prefKey(instance))
            .apply()
    }

    private fun prefKey(instance: String) = "push_account_id:$instance"

    private fun postSystemNotification(
        context: Context,
        instance: String,
        title: String,
        body: String,
        link: String,
        tag: String,
        app: String,
    ) {
        val channelId = channelId(context, instance, app, link)
        val deepLink = deepLinkFor(context, instance, link)

        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, deepLink).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
        val pending = android.app.PendingIntent.getActivity(context, 0, intent, pendingFlags)

        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)

        val nm = androidx.core.app.NotificationManagerCompat.from(context)
        if (nm.areNotificationsEnabled()) {
            try {
                nm.notify(tag.ifBlank { instance }, tag.hashCode(), builder.build())
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification post denied: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "MochiPush"
        const val PREF_FILE = "mochi_push"
    }
}
