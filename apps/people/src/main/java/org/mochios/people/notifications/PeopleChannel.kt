package org.mochios.people.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.mochios.people.R

const val PEOPLE_NOTIFICATION_CHANNEL_ID = "people"

fun setupPeopleNotificationChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        PEOPLE_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.notification_channel_people),
        NotificationManager.IMPORTANCE_DEFAULT,
    )
    nm.createNotificationChannel(channel)
}
