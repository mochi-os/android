// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.mochios.chat.R

const val CHAT_NOTIFICATION_CHANNEL_ID = "chat"

fun setupChatNotificationChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        CHAT_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.notification_channel_chat),
        NotificationManager.IMPORTANCE_DEFAULT,
    )
    nm.createNotificationChannel(channel)
}
