// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.mochios.android.auth.AuthRepository
import org.mochios.android.util.isServerOrigin
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Path only when [endpointUrl] is on [server]'s own origin, so the server takes
 * its local-delivery fast-path; any other endpoint is returned unchanged for
 * RFC 8030. Compare the whole origin, not the host: a wrong verdict either way
 * loses the push.
 */
internal fun collapseLocalEndpoint(endpointUrl: String, server: String): String {
    val ep = endpointUrl.toHttpUrlOrNull() ?: return endpointUrl
    // Our distributor never issues an endpoint carrying credentials or a
    // fragment, so refuse to treat one as local rather than reason about it.
    if (ep.encodedUsername.isNotEmpty() || ep.encodedPassword.isNotEmpty() || ep.fragment != null) {
        return endpointUrl
    }
    return if (isServerOrigin(ep, server)) ep.encodedPath else endpointUrl
}

/**
 * Bridges UnifiedPush callbacks into Mochi events; subclasses supply the
 * channel and deep-link routing.
 */
abstract class MochiPushReceiver : MessagingReceiver() {

    /**
     * Channel id for the notification: the payload's [app] slug, falling back
     * to [link]'s first path segment.
     */
    abstract fun channelId(context: Context, instance: String, app: String, link: String): String

    /**
     * Deep-link Uri for the notification tap. [id] marks the row read; [nonce]
     * must be carried on the URI - MainActivity is exported and ignores taps it
     * cannot match to an outstanding nonce (see [NonceStore]).
     */
    abstract fun deepLinkFor(
        context: Context,
        instance: String,
        link: String,
        id: String,
        nonce: String
    ): android.net.Uri

    private fun deps(context: Context): PushEntryPoint =
        EntryPointAccessors.fromApplication(context.applicationContext, PushEntryPoint::class.java)

    override fun onNewEndpoint(context: Context, endpoint: PushEndpoint, instance: String) {
        // The endpoint URL ends in the subscription id, which is the unguessable
        // capability anyone can post a push to, so log only its origin.
        Log.i(TAG, "onNewEndpoint instance=$instance host=${endpointHost(endpoint.url)}")
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
                Log.i(
                    TAG,
                    "register: endpoint host=${endpointHost(endpoint.url)} server=$server " +
                        "local=${endpointToSend != endpoint.url}"
                )
                val accountId = postPushRegister(
                    deps.okHttpClient(),
                    deps.authRepository(),
                    server = server,
                    label = DeviceName.resolve(context),
                    auth = keys.auth,
                    p256dh = keys.pubKey,
                    endpoint = endpointToSend,
                    device = deps.deviceStore().id(),
                )
                if (accountId != null) {
                    deps.pushAccountStore().store(instance, accountId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register endpoint with Mochi server: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Host of [endpointUrl], for logging that must not carry the subscription id. */
    private fun endpointHost(endpointUrl: String): String =
        runCatching { android.net.Uri.parse(endpointUrl).host }.getOrNull() ?: "unparseable"

    override fun onUnregistered(context: Context, instance: String) {
        Log.i(TAG, "onUnregistered instance=$instance")
        val store = deps(context).pushAccountStore()
        val accountId = store.read(instance) ?: return
        store.clear(instance)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deps = deps(context)
                val server = deps.sessionManager().getServerUrlBlocking()
                postPushAccountsRemove(
                    deps.okHttpClient(),
                    deps.authRepository(),
                    server,
                    accountId
                )
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

    /**
     * Deliver a MESSAGE this distributor sent us directly, bypassing the
     * connector's decrypt. The local fast path is cleartext by design, so it
     * cannot satisfy [onMessage]'s decrypted gate; the sender's identity
     * stands in for it. Every other action goes to the connector unchanged, so
     * a third-party distributor still gets the RFC 8291 treatment.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PushService.ACTION_LOCAL_MESSAGE) {
            super.onReceive(context, intent)
            return
        }
        val sender = senderPackage(intent)
        val content = intent.getByteArrayExtra(PushService.EXTRA_BYTES_MESSAGE)
        when (judgeLocalPush(sender, sender != null && mochiSigned(context, sender), content)) {
            LocalPush.UNIDENTIFIED -> {
                Log.w(TAG, "Local push carried no PendingIntent to identify the sender; ignoring")
                return
            }
            LocalPush.UNTRUSTED -> {
                Log.w(TAG, "Local push from unrelated package $sender; ignoring")
                return
            }
            LocalPush.EMPTY -> {
                Log.w(TAG, "Local push carried no payload; ignoring")
                return
            }
            LocalPush.ACCEPT -> Unit
        }
        // The connector resolves an instance name from the token through its
        // own private registration store, which we cannot read. Instance
        // reaches only the notification tag's fallback (channelId and
        // deepLinkFor both ignore it), and the token identifies the same
        // registration, so it serves the same purpose here.
        deliver(context, content!!, intent.getStringExtra(PushService.EXTRA_TOKEN).orEmpty())
    }

    override fun onMessage(context: Context, message: PushMessage, instance: String) {
        // The only authenticity gate on push content from a third-party
        // distributor: the connector calls this with decrypted = false after a
        // failed decrypt, so without the check anyone holding the endpoint URL
        // can post an arbitrary notification. Our own distributor does not
        // reach here - see onReceive.
        if (!message.decrypted) {
            Log.w(TAG, "Push payload was not decrypted; ignoring")
            return
        }
        deliver(context, message.content, instance)
    }

    /** Parse a delivered payload and raise the notification it describes. */
    private fun deliver(context: Context, content: ByteArray, instance: String) {
        val text = content.toString(Charsets.UTF_8)
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
        val id = payload.optString("id", "")

        if (title.isBlank() && body.isBlank()) {
            Log.w(TAG, "Push payload has no title/body; ignoring")
            return
        }

        postSystemNotification(context, instance, title, body, link, tag, app, id)
    }

    private suspend fun postPushRegister(
        client: OkHttpClient,
        authRepository: AuthRepository,
        server: String,
        label: String,
        auth: String,
        p256dh: String,
        endpoint: String,
        device: String,
    ): String? {
        val token =
            authRepository.fetchToken("notifications").getOrNull() ?: return null
        val url = server.trimEnd('/') + "/notifications/-/push/register"
        val form = FormBody.Builder()
            .add("label", label)
            .add("auth", auth)
            .add("p256dh", p256dh)
            .add("endpoint", endpoint)
            .build()
        // The Device header binds the push account to this device, so a later
        // registration from the same phone replaces it rather than adding to it.
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Device", device)
            .post(form)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "/notifications/-/push/register returned ${resp.code}")
                return null
            }
            val body = resp.body?.string().orEmpty()
            return try {
                JSONObject(body).optJSONObject("data")?.optString("id")?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                Log.w(TAG, "Could not parse /notifications/-/push/register response")
                null
            }
        }
    }

    private suspend fun postPushAccountsRemove(
        client: OkHttpClient,
        authRepository: AuthRepository,
        server: String,
        accountId: String,
    ) {
        val token =
            authRepository.fetchToken("notifications").getOrNull() ?: return
        val url = server.trimEnd('/') + "/notifications/-/push/accounts/remove"
        val form = FormBody.Builder().add("id", accountId).build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(form)
            .build()
        client.newCall(request).execute().close()
    }

    private fun postSystemNotification(
        context: Context,
        instance: String,
        title: String,
        body: String,
        link: String,
        tag: String,
        app: String,
        id: String,
    ) {
        val channelId = channelId(context, instance, app, link)
        val nonce = NonceStore(context).issue()
        val deepLink = deepLinkFor(context, instance, link, id, nonce)

        // launch-ok: deepLink is the mochi: URI this file builds from the push payload's link
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, deepLink).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Pin the PendingIntent to the matching launcher alias so badge-
            // capable launchers attribute the unread dot to the right Mochi-
            // app icon (every alias targets MainActivity, so without this the
            // badge stamps on every Mochi icon).
            launcherComponentFor(context, app)?.let { component = it }
        }
        val pendingFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
        val pending = android.app.PendingIntent.getActivity(context, 0, intent, pendingFlags)

        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(notificationIconFor(app))
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            // Damp bursts: a batch of server events re-posts the same tag in
            // rapid succession (e.g. an RSS poll ingesting several posts);
            // the first alerts, the rest update the tray silently.
            .setOnlyAlertOnce(!SystemNotifications.shouldAlert(tag.ifBlank { instance }))
            .setContentIntent(pending)
            // Retire the nonce when the notification is swiped away. Without
            // this only a tap spent one, so the store filled with nonces no tap
            // would ever present and evicted live ones to make room.
            .setDeleteIntent(dismissIntent(context, nonce))

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
    }
}
