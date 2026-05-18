package org.mochios.words.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.mochios.words.R

const val WORDS_NOTIFICATION_CHANNEL_ID = "words"

fun setupWordsNotificationChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        WORDS_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.words_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = context.getString(R.string.words_channel_description)
    }
    nm.createNotificationChannel(channel)
}
