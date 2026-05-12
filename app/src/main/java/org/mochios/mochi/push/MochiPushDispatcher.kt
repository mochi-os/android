package org.mochios.mochi.push

import android.content.Context
import android.net.Uri
import org.mochios.android.push.MochiPushReceiver
import org.mochios.chat.notifications.CHAT_NOTIFICATION_CHANNEL_ID
import org.mochios.feeds.notifications.FEEDS_NOTIFICATION_CHANNEL_ID
import org.mochios.forums.notifications.FORUMS_NOTIFICATION_CHANNEL_ID
import org.mochios.projects.notifications.PROJECTS_NOTIFICATION_CHANNEL_ID

class MochiPushDispatcher : MochiPushReceiver() {

    override fun channelId(context: Context, instance: String, app: String, link: String): String {
        // Prefer the server-supplied `app` slug; fall back to the link's first
        // path segment when older servers don't include it or the notification
        // has no link at all.
        val key = app.ifBlank {
            link.trimStart('/').substringBefore('/')
        }.lowercase()
        return when (key) {
            "feeds" -> FEEDS_NOTIFICATION_CHANNEL_ID
            "chat" -> CHAT_NOTIFICATION_CHANNEL_ID
            "forums" -> FORUMS_NOTIFICATION_CHANNEL_ID
            "projects" -> PROJECTS_NOTIFICATION_CHANNEL_ID
            else -> FEEDS_NOTIFICATION_CHANNEL_ID
        }
    }

    override fun deepLinkFor(context: Context, instance: String, link: String): Uri =
        Uri.parse("mochi://notification")
            .buildUpon()
            .appendQueryParameter("link", link)
            .build()
}
