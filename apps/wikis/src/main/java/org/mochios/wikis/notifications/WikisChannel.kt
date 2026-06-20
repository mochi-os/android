// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.mochios.wikis.R

const val WIKIS_NOTIFICATION_CHANNEL_ID = "wikis"

fun setupWikisNotificationChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        WIKIS_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.wikis_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT,
    )
    nm.createNotificationChannel(channel)
}
