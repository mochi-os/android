// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.app.NotificationManager
import android.content.Context
import android.util.Log

/**
 * Helpers for the on-device system-tray notification surface so feature
 * modules can dismiss tray rows when the user has otherwise seen the
 * content. Mochi's server-side `clear/object` already marks the matching
 * notification row read on the server (and propagates via WebSocket),
 * but Android's status bar doesn't know about that — without an explicit
 * cancel the tray entry sits there even after the user has opened the
 * chat / forum / post the entry was about.
 *
 * Tag format set by [MochiFirebaseMessagingService.postSystemNotification]
 * and [MochiPushReceiver]'s UnifiedPush counterpart mirrors the server's
 * `app + "-" + category + "-" + object` shape, with one tray row per tag.
 * We match prefix (`app-`) and suffix (`-object`) so any category that
 * relayed this app+object pair is included.
 */
object SystemNotifications {

    private const val TAG = "MochiSysNotifs"

    /**
     * Cancel every active system-tray notification whose tag identifies
     * the same (app, object) pair. Safe to call even when nothing is
     * shown; no-ops on permission failures.
     */
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
}
