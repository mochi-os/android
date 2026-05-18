package org.mochios.go.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.mochios.go.R

/**
 * Notification channel id for Go game events (new game, move, message, draw,
 * resign, game-over). Mirrors the People / Wikis pattern — one channel per
 * app keeps OS-level toggles per feature.
 */
const val GO_NOTIFICATION_CHANNEL_ID = "go"

fun setupGoNotificationChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        GO_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.go_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = context.getString(R.string.go_channel_description)
    }
    nm.createNotificationChannel(channel)
}
