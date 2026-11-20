// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Cancels tray rows when the user has seen the content elsewhere: the server's
 * `clear/object` marks the row read but Android's tray knows nothing about it.
 * Every cancel retires the row's tap nonce, since Android tells the app
 * nothing of a notification it cancels itself.
 */
object SystemNotifications {

    private const val TAG = "MochiSysNotifs"

    /**
     * Cancel the push rows about what a screen shows: those whose link lies
     * under `/<app>/<path>`, the test [VisibleEntity.coverFor] applies to a
     * push arriving while the screen is open. The server's tag cannot answer
     * this: its app is the sending app's id, and its object is the post or
     * page rather than the feed or wiki holding it.
     */
    fun cancelFor(context: Context, app: String, path: String) {
        val shown = path.split('/').filter { segment -> segment.isNotEmpty() }
        if (app.isBlank() || shown.isEmpty()) return
        cancelWhere(context) { row -> linkUnder(row.notification.extras.getString(EXTRA_LINK).orEmpty(), app, shown) }
    }

    /**
     * Cancel the push rows delivered for [account], with those whose
     * transport names no account (FCM, registered for the signed-in account
     * alone). The sign-in prompt, the service's own notice and other
     * accounts' rows stay.
     */
    fun cancelPushes(context: Context, account: String?) {
        cancelWhere(context) { row ->
            val extras = row.notification.extras
            extras.containsKey(EXTRA_LINK) && extras.getString(EXTRA_ACCOUNT).let { it == null || it == account }
        }
    }

    /** Cancel the row posted under [tag]. */
    fun cancel(context: Context, tag: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        try {
            manager.cancel(tag, tag.hashCode())
        } catch (e: SecurityException) {
            Log.d(TAG, "cancel denied: ${e.message}")
        }
        NonceStore(context).retire(tag)
    }

    /** Cancel each active row [matches] picks. */
    fun cancelWhere(context: Context, matches: (StatusBarNotification) -> Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val active = try {
            manager.activeNotifications
        } catch (e: SecurityException) {
            Log.d(TAG, "no permission to read active notifications: ${e.message}")
            return
        } ?: return
        val nonces = NonceStore(context)
        for (row in active) {
            if (!matches(row)) continue
            try {
                manager.cancel(row.tag, row.id)
            } catch (e: SecurityException) {
                Log.d(TAG, "cancel denied: ${e.message}")
            }
            row.tag?.let(nonces::retire)
        }
    }

    // Last time each tray tag was allowed to alert. In-memory only: a
    // process restart forgetting the burst window just means one extra
    // alert, which is the safe direction.
    private val alerted = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private const val ALERT_WINDOW_MILLIS = 5_000L

    /**
     * Whether a post of [tag] should alert. The server re-sends one coalesced
     * notification per event, so a batch arrives as a run of re-posts; the
     * first alerts and the rest should set `setOnlyAlertOnce(true)`.
     */
    fun shouldAlert(tag: String): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        // Record only allowed alerts, so the window measures from the last
        // audible alert — a steady stream of sub-window re-posts still
        // alerts once per window rather than sliding into permanent silence.
        val last = alerted[tag]
        if (last != null && now - last <= ALERT_WINDOW_MILLIS) return false
        alerted[tag] = now
        return true
    }
}
