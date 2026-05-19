package org.mochios.market.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.mochios.market.R

const val MARKET_NOTIFICATION_CHANNEL_ID = "market"

fun setupMarketNotificationChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        MARKET_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.market_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT,
    )
    nm.createNotificationChannel(channel)
}
