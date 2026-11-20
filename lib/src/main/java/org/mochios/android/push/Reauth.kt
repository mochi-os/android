// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.mochios.android.launcher.launcherComponentFor
import java.util.concurrent.ConcurrentHashMap

/**
 * Posts a "sign in again" notification after [THRESHOLD] consecutive 401s from
 * /_/token for one account. Mochi sessions renew on every authenticated
 * request, so a 401 means the session is genuinely dead and only re-OAuth
 * recovers it. Counted, posted and cleared per account: two accounts can share
 * a server, and one's success says nothing of the other's session.
 */
object Reauth {

    private const val TAG = "MochiPushReauth"
    private const val NOTIFICATION_ID = 0x4D43_0002 // 'MC' 0002 (PushService FG uses 0001)
    private const val CHANNEL_ID = "mochi_reauth_required"
    private const val THRESHOLD = 3

    private val consecutiveFailures = ConcurrentHashMap<String, Int>()

    /**
     * Called by PushService when /_/token returns 401 for [identity]'s session.
     * The sign-in notification is posted once, as the session is found dead,
     * not again on each refusal after: one dismissed stays dismissed.
     *
     * @return whether the session is dead, so asking again is pointless until
     *   the user signs in.
     */
    fun report401(context: Context, identity: String): Boolean {
        val n = consecutiveFailures.compute(identity) { _, prev -> (prev ?: 0) + 1 }!!
        Log.w(TAG, "401 for an account's session (consecutive=$n)")
        if (n == THRESHOLD) {
            postSignInRequired(context, identity)
        }
        return n >= THRESHOLD
    }

    /** Called by PushService when /_/token returns 200 for [identity]'s session.
     *  Resets its counter and dismisses its sign-in notification if it was up. */
    fun reportSuccess(context: Context, identity: String) {
        if (consecutiveFailures.remove(identity) != null) {
            try {
                NotificationManagerCompat.from(context).cancel(identity, NOTIFICATION_ID)
            } catch (_: Exception) {
            }
            NonceStore(context).retire(identity)
        }
    }

    private fun postSignInRequired(context: Context, identity: String) {
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        // Before the nonce: a prompt that is never posted must not hold one.
        if (!nm.areNotificationsEnabled()) return

        // The tap is `mochi:reauth?account=<identity>&nonce=<nonce>` to the
        // home grid's activity, the app's launcher entry. The activity is
        // exported, so the nonce, kept under the prompt's tag, is what proves
        // the tap came from this prompt.
        val nonce = NonceStore(context).issue(identity)
        // launch-ok: a mochi: URI built here, addressed to this client's own activity
        val intent = Intent(Intent.ACTION_VIEW, reauthUri(identity, nonce)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            setPackage(context.packageName)
            launcherComponentFor(context, "home")?.let { component = it }
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        // One per account: a shared request code would leave every account's
        // notification opening the last one's.
        val tap = PendingIntent.getActivity(context, identity.hashCode(), intent, pendingFlags)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(org.mochios.android.R.string.reauth_signin_title))
            .setContentText(context.getString(org.mochios.android.R.string.reauth_signin_text))
            .setSmallIcon(org.mochios.android.R.drawable.ic_mochi_notification)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tap)
            // A swipe retires the nonce, as for a push.
            .setDeleteIntent(dismissIntent(context, nonce))

        try {
            nm.notify(identity, NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            NonceStore(context).retire(identity)
        }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(org.mochios.android.R.string.reauth_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(org.mochios.android.R.string.reauth_channel_description)
        }
        nm.createNotificationChannel(channel)
    }
}

/**
 * `mochi:reauth?account=<identity>&nonce=<nonce>`, the URI a tapped sign-in
 * prompt opens. Opaque, as [notificationUri] is.
 */
internal fun reauthUri(identity: String, nonce: String): Uri =
    Uri.parse("mochi:reauth?account=" + Uri.encode(identity) + "&nonce=" + Uri.encode(nonce))
