package org.mochios.crm.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.mochios.crm.R

const val PROJECTS_NOTIFICATION_CHANNEL_ID = "crm"

fun setupCrmsNotificationChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        PROJECTS_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.notification_channel_crm),
        NotificationManager.IMPORTANCE_DEFAULT,
    )
    nm.createNotificationChannel(channel)
}
