package org.mochios.forums.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.mochios.forums.R

const val FORUMS_NOTIFICATION_CHANNEL_ID = "forums"

fun setupForumsNotificationChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        FORUMS_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.notification_channel_forums),
        NotificationManager.IMPORTANCE_DEFAULT,
    )
    nm.createNotificationChannel(channel)
}
