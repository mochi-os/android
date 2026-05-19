package org.mochios.staff.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.mochios.staff.R

const val STAFF_NOTIFICATION_CHANNEL_ID = "staff"

fun setupStaffNotificationChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        STAFF_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.staff_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT,
    )
    nm.createNotificationChannel(channel)
}
