// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.app.NotificationManager
import android.content.Context
import android.util.Log

/**
 * Cancels tray rows when the user has seen the content elsewhere: the server's
 * `clear/object` marks the row read but Android's tray knows nothing about it.
 * Tags are `<app>-<category>-<object>`, matched by prefix and suffix.
 */
object SystemNotifications {

    private const val TAG = "MochiSysNotifs"

    fun cancelFor(context: Context, app: String, objectId: String) {
        if (app.isBlank()) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val active = try {
            nm.activeNotifications
        } catch (e: SecurityException) {
            Log.d(TAG, "cancelFor: no permission to read active notifications: ${e.message}")
            return
        } ?: return
        for (sbn in active) {
            val tag = sbn.tag ?: continue
            if (!tagMatches(tag, app, objectId)) continue
            try {
                nm.cancel(tag, sbn.id)
            } catch (e: SecurityException) {
                Log.d(TAG, "cancelFor: cancel denied for $tag: ${e.message}")
            }
        }
    }

    private fun tagMatches(tag: String, app: String, objectId: String): Boolean {
        if (!tag.startsWith("$app-")) return false
        // Empty objectId matches any object for this app (used when a
        // feature opens its list view, not a specific entity).
        if (objectId.isBlank()) return true
        return tag.endsWith("-$objectId")
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
