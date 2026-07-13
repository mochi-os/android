// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receives FCM messages and posts the system notification on the matching
 * per-feature channel.
 *
 * Wire payload (set by the Mochi server in FCM v1 `message.data`) mirrors
 * the UnifiedPush envelope so the on-device side is transport-agnostic:
 *
 *     { "app": "forums", "link": "/forums/abc12def", "title": "...", "body": "...", "tag": "..." }
 *
 * `onNewToken` POSTs the refreshed token back to the notifications app's
 * `push/register/fcm` action so the server can replace any stale entry.
 *
 * The service is plumbed in the lib so all per-feature channels see the same
 * routing; channel IDs are the feature slugs ("feeds" / "chat" / "forums" /
 * "projects") shipped by the per-feature modules' channel-setup helpers.
 */
class MochiFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun deps(): PushEntryPoint =
        EntryPointAccessors.fromApplication(applicationContext, PushEntryPoint::class.java)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"].orEmpty()
        val body = data["body"].orEmpty()
        val link = data["link"].orEmpty()
        val tag = data["tag"].orEmpty()
        val app = data["app"].orEmpty()
        val id = data["id"].orEmpty()

        if (title.isBlank() && body.isBlank()) {
            Log.w(TAG, "FCM payload missing title/body; ignoring")
            return
        }
        postSystemNotification(applicationContext, title, body, link, tag, app, id)
    }

    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM token refreshed")
        scope.launch {
            val deps = deps()
            // deleteToken() during sign-out makes Firebase mint a fresh token
            // and fire this callback. Registering it would re-subscribe the
            // device we just signed out of, so skip when there's no session.
            if (!deps.sessionManager().isAuthenticated.first()) {
                Log.i(TAG, "No active session; skipping FCM token registration")
                return@launch
            }
            val server = deps.sessionManager().getServerUrlBlocking()
            if (server.isBlank()) {
                Log.w(TAG, "No server bound; skipping FCM token registration")
                return@launch
            }
            FcmRegistrar.register(applicationContext, deps.okHttpClient(), server, token)
        }
    }

    /**
     * Channel routing is identical to the UnifiedPush path's
     * MochiPushDispatcher.channelId: prefer the server-supplied `app`
     * slug, fall back to the first path segment of `link`. Both refer to
     * channels created by the per-feature `setup*NotificationChannel`
     * helpers using the slug as the channel ID.
     */
    private fun channelIdFor(app: String, link: String): String {
        val key = app.ifBlank {
            link.trimStart('/').substringBefore('/')
        }.lowercase()
        return when (key) {
            "feeds", "chat", "forums", "projects" -> key
            else -> "feeds"
        }
    }

    private fun postSystemNotification(
        context: Context,
        title: String,
        body: String,
        link: String,
        tag: String,
        app: String,
        id: String,
    ) {
        val channelId = channelIdFor(app, link)

        // mochi:notification?link=<encoded>[&id=<encoded>] — same opaque URI
        // the UnifiedPush dispatcher uses; routed by MainActivity through
        // claude/plans/mochi-uri-scheme.md. `id` lets MainActivity call the
        // notifications app's -/read endpoint so the matching web row is
        // cleared on tap.
        val ssp = buildString {
            append("notification?link=").append(Uri.encode(link))
            if (id.isNotEmpty()) append("&id=").append(Uri.encode(id))
        }
        val deepLink = Uri.parse("mochi:$ssp")
        val intent = Intent(Intent.ACTION_VIEW, deepLink).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Pin the PendingIntent to the matching launcher alias so badge-
            // capable launchers (Octopi etc.) attribute the unread dot to the
            // right Mochi-app icon. Without this every alias targeting
            // MainActivity shows the badge.
            launcherComponentFor(context, app)?.let { component = it }
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(context, 0, intent, pendingFlags)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(notificationIconFor(app))
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)

        val nm = NotificationManagerCompat.from(context)
        if (nm.areNotificationsEnabled()) {
            try {
                nm.notify(tag.ifBlank { "mochi" }, tag.hashCode(), builder.build())
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification post denied: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "MochiFcm"
    }
}
