// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.mochios.chess.R

const val CHESS_NOTIFICATION_CHANNEL_ID = "chess"

/**
 * Register the chess notification channel. Called from
 * [org.mochios.mochi.MochiApplication.onCreate]; per-app channel creation is
 * idempotent so re-running after install is safe. Channel name and
 * description come from `chess` strings (translated per locale).
 */
fun setupChessNotificationChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        CHESS_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.chess_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = context.getString(R.string.chess_channel_description)
    }
    nm.createNotificationChannel(channel)
}
