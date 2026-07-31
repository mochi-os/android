// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.mochios.crm.R

// Was PROJECTS_NOTIFICATION_CHANNEL_ID — the value was always "crm", but the
// name collided with the projects module's constant, so routing crm alongside
// projects in the dispatcher would have been an import clash. That is the most
// likely reason crm was never given an arm there.
const val CRM_NOTIFICATION_CHANNEL_ID = "crm"

fun setupCrmsNotificationChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        CRM_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.notification_channel_crm),
        NotificationManager.IMPORTANCE_DEFAULT,
    )
    nm.createNotificationChannel(channel)
}
