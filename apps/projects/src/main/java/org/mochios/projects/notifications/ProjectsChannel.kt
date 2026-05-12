package org.mochios.projects.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.mochios.projects.R

const val PROJECTS_NOTIFICATION_CHANNEL_ID = "projects"

fun setupProjectsNotificationChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        PROJECTS_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.notification_channel_projects),
        NotificationManager.IMPORTANCE_DEFAULT,
    )
    nm.createNotificationChannel(channel)
}
