package org.mochios.android.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Surfaces a "sign in again" system notification when the WebSocket /
 * token-mint path keeps getting 401s — the rare but real case where a
 * user's session has been invalidated server-side (admin revoke, DB
 * corruption, manual cleanup).
 *
 * No server-side refresh endpoint is needed: Mochi sessions are 1 year
 * with rolling renewal on every authenticated request (`web_auth` in
 * core/server/web.go re-sets the cookie), so an active user's session
 * never naturally expires. 401 means the session is genuinely dead and
 * only re-OAuth via a per-app app can recover it.
 *
 * Strategy:
 *   - PushService.mintToken calls [report401] on each 401 from /_/token
 *     and [reportSuccess] on each success.
 *   - After [THRESHOLD] consecutive 401s for a given server, post a
 *     dismissable system notification asking the user to sign in. Tap
 *     opens the shell's launchpad so they can choose any per-app app
 *     and re-OAuth there. The cross-app MochiAccount sync then flows
 *     the new session back to the shell's PushService automatically.
 */
object Reauth {

    private const val TAG = "MochiPushReauth"
    private const val NOTIFICATION_ID = 0x4D43_0002 // 'MC' 0002 (PushService FG uses 0001)
    private const val CHANNEL_ID = "mochi_reauth_required"
    private const val THRESHOLD = 3

    private val consecutiveFailures = ConcurrentHashMap<String, Int>()

    /** Called by PushService when /_/token returns 401 for the given server. */
    fun report401(context: Context, server: String) {
        val n = consecutiveFailures.compute(server) { _, prev -> (prev ?: 0) + 1 }!!
        Log.w(TAG, "401 from $server (consecutive=$n)")
        if (n >= THRESHOLD) {
            postSignInRequired(context)
        }
    }

    /** Called by PushService when /_/token returns 200. Resets the counter
     *  and dismisses the sign-in notification if it was up. */
    fun reportSuccess(context: Context, server: String) {
        if (consecutiveFailures.remove(server) != null) {
            try {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            } catch (_: Exception) {
            }
        }
    }

    private fun postSignInRequired(context: Context) {
        ensureChannel(context)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val tap = launchIntent?.let {
            PendingIntent.getActivity(context, 0, it, pendingFlags)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(org.mochios.android.R.string.reauth_signin_title))
            .setContentText(context.getString(org.mochios.android.R.string.reauth_signin_text))
            .setSmallIcon(org.mochios.android.R.drawable.ic_mochi_notification)
            .setAutoCancel(true)
            .apply { if (tap != null) setContentIntent(tap) }

        val nm = NotificationManagerCompat.from(context)
        if (nm.areNotificationsEnabled()) {
            try {
                nm.notify(NOTIFICATION_ID, builder.build())
            } catch (_: SecurityException) {
            }
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
