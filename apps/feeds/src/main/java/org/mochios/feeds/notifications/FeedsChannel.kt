package org.mochios.feeds.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.mochios.feeds.R

const val FEEDS_NOTIFICATION_CHANNEL_ID = "feeds"

fun setupFeedsNotificationChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        FEEDS_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.notification_channel_feeds),
        NotificationManager.IMPORTANCE_DEFAULT,
    )
    nm.createNotificationChannel(channel)
}
