// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Extra carrying the nonce to retire; see [NonceDismissReceiver]. */
internal const val EXTRA_NONCE = "nonce"

/** Action the delete intent broadcasts; see [NonceDismissReceiver]. */
internal const val ACTION_NOTIFICATION_DISMISSED =
    "org.mochios.android.push.NOTIFICATION_DISMISSED"

/**
 * Retires a notification's nonce when the user swipes it away.
 *
 * Without this a nonce was spent only on tap, so a swiped-away notification
 * left its nonce outstanding forever and the store filled with values no tap
 * would ever present. Once it was full the oldest was evicted to make room -
 * including ones whose notification was still sitting in the tray, so tapping
 * that entry found no outstanding nonce and silently did nothing.
 *
 * Not exported: the delete intent is ours, and a forged dismissal that retired
 * someone else's nonce would be the same denial by another route.
 */
class NonceDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_NOTIFICATION_DISMISSED) return
        val nonce = intent.getStringExtra(EXTRA_NONCE) ?: return
        NonceStore(context).consume(nonce)
    }
}

/**
 * A PendingIntent that retires [nonce] when its notification is dismissed.
 *
 * The request code is the nonce's hash rather than a constant: PendingIntents
 * that differ only in their extras are otherwise considered equal, so every
 * notification would share one and a single dismissal would retire whichever
 * nonce happened to be attached last.
 */
internal fun dismissIntent(context: Context, nonce: String): android.app.PendingIntent {
    val intent = Intent(context, NonceDismissReceiver::class.java).apply {
        action = ACTION_NOTIFICATION_DISMISSED
        putExtra(EXTRA_NONCE, nonce)
        setPackage(context.packageName)
    }
    return android.app.PendingIntent.getBroadcast(
        context,
        nonce.hashCode(),
        intent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
    )
}
