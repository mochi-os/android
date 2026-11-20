// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mochios.android.launcher.launcherComponentFor
import org.mochios.android.sync.SyncSignal

private const val TAG = "MochiPush"

/**
 * The extra a push notification carries its link in. It also marks the
 * notification as a push, apart from the sign-in prompt and the service's own.
 */
internal const val EXTRA_LINK = "org.mochios.android.push.LINK"

/**
 * The extra naming the identity a push notification was delivered for. Absent
 * where the transport does not say: FCM is registered for the signed-in
 * account only.
 */
internal const val EXTRA_ACCOUNT = "org.mochios.android.push.ACCOUNT"

/** The tray tag of a push that carries none. */
internal const val FALLBACK_TAG = "mochi"

/**
 * One push as the server sends it on every transport. [account] is the
 * identity it was delivered for, when the transport says.
 */
internal data class Push(
    val title: String,
    val body: String,
    val link: String,
    val tag: String,
    val app: String,
    val id: String,
    val account: String?,
)

/**
 * `mochi:notification?link=<link>[&id=<id>]&nonce=<nonce>`, the URI a tapped
 * notification opens. Opaque, with no slashes after the scheme
 * (claude/plans/mochi-uri-scheme.md). [id] marks the row read on tap; [nonce]
 * proves the tap came from this app, since MainActivity is exported.
 */
internal fun notificationUri(link: String, id: String?, nonce: String): Uri {
    val part = buildString {
        append("notification?link=").append(Uri.encode(link))
        if (!id.isNullOrEmpty()) append("&id=").append(Uri.encode(id))
        append("&nonce=").append(Uri.encode(nonce))
    }
    return Uri.parse("mochi:$part")
}

/** A UnifiedPush payload, a JSON object, as the fields [deliverPush] reads; null when it is not one. */
internal fun pushFields(content: ByteArray): Map<String, String>? = try {
    val json = JSONObject(content.toString(Charsets.UTF_8))
    json.keys().asSequence().associateWith { key -> json.optString(key, "") }
} catch (_: Exception) {
    null
}

/** [deliverPush] for a UnifiedPush payload, a JSON object of the same fields. */
fun deliverPush(context: Context, content: ByteArray, account: String?) {
    val fields = pushFields(content)
    if (fields == null) {
        Log.w(TAG, "Push payload not JSON; ignoring")
        return
    }
    deliverPush(context, fields, account)
}

/**
 * Raise the notification a push describes, or start the sync it asks for.
 * Every transport ends here: FCM, a third-party UnifiedPush distributor once
 * the connector has decrypted the payload, and this app's own distributor,
 * which calls it in process.
 */
fun deliverPush(context: Context, fields: Map<String, String>, account: String?) {
    val deps = EntryPointAccessors.fromApplication(context.applicationContext, PushEntryPoint::class.java)
    SyncSignal.kind(fields)?.let { kind ->
        SyncSignal.run(context, kind, deps.sessionManager())
        return
    }
    val push = Push(
        title = fields["title"].orEmpty(),
        body = fields["body"].orEmpty(),
        link = fields["link"].orEmpty(),
        tag = fields["tag"].orEmpty(),
        app = fields["app"].orEmpty(),
        id = fields["id"].orEmpty(),
        account = account?.takeIf { it.isNotBlank() },
    )
    if (push.title.isBlank() && push.body.isBlank()) {
        Log.w(TAG, "Push has no title or body; ignoring")
        return
    }
    val webSocket = deps.webSocket()
    postPushNotification(context, push, webSocket::isLive) { id ->
        // Retire the row so the web bell and the user's other devices stop
        // announcing what they have already read here. Best effort; the tray
        // row is suppressed either way.
        val repository = deps.notificationsRepository()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.markRead(id)
            } catch (e: Exception) {
                Log.w(TAG, "markRead failed: ${e.message}")
            }
        }
    }
}

/**
 * Post [push] to the tray, unless a screen showing what it is about covers it.
 *
 * @param isLive whether a screen's socket is carrying events; a screen only
 *   covers a push while it is, see [VisibleEntity.coverFor].
 * @param markRead retires a row the user is reading on screen.
 */
internal fun postPushNotification(
    context: Context,
    push: Push,
    isLive: (String) -> Boolean,
    markRead: (String) -> Unit,
) {
    val cover = VisibleEntity.coverFor(push.link, isLive)
    if (cover != VisibleEntity.Cover.NONE) {
        Log.i(TAG, "Entity is on screen; not posting")
        // Only a screen that is the entity itself, a chat or a game, can say
        // the user has read this; a feed being open cannot.
        if (cover == VisibleEntity.Cover.READ && push.id.isNotEmpty()) markRead(push.id)
        return
    }

    val manager = NotificationManagerCompat.from(context)
    // Before the nonce: a notification that is never posted must not hold one.
    if (!manager.areNotificationsEnabled()) return

    val app = pushApp(push.app, push.link)
    val tag = push.tag.ifBlank { FALLBACK_TAG }
    val nonce = NonceStore(context).issue(tag)

    // launch-ok: a mochi: URI built here from the push's link, addressed to this client
    val intent = Intent(Intent.ACTION_VIEW, notificationUri(push.link, push.id, nonce)).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        setPackage(context.packageName)
        // The app's own activity class, so the tap opens in that app's task and
        // a launcher that badges per activity puts the dot on its icon. With
        // none the URI reaches MainActivity, which forwards it.
        launcherComponentFor(context, app)?.let { component = it }
    }
    val pending = PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val extras = Bundle().apply {
        putString(EXTRA_LINK, push.link)
        push.account?.let { putString(EXTRA_ACCOUNT, it) }
    }
    val builder = NotificationCompat.Builder(context, notificationChannelFor(push.app, push.link))
        .setSmallIcon(notificationIconFor(app))
        .setContentTitle(push.title)
        .setContentText(push.body)
        .setAutoCancel(true)
        // Damp bursts: a batch of server events re-posts the same tag in rapid
        // succession (an RSS poll ingesting several posts, say); the first
        // alerts, the rest update the tray silently.
        .setOnlyAlertOnce(!SystemNotifications.shouldAlert(tag))
        .setContentIntent(pending)
        // A swipe fires the delete intent, which retires the nonce; a tap
        // spends it in MainActivity.
        .setDeleteIntent(dismissIntent(context, nonce))
        .addExtras(extras)
    try {
        manager.notify(tag, tag.hashCode(), builder.build())
    } catch (e: SecurityException) {
        NonceStore(context).retire(tag)
        Log.w(TAG, "Notification post denied: ${e.message}")
    }
}
