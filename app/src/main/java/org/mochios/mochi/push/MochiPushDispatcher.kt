package org.mochios.mochi.push

import android.content.Context
import android.net.Uri
import org.mochios.android.push.MochiPushReceiver
import org.mochios.chat.notifications.CHAT_NOTIFICATION_CHANNEL_ID
import org.mochios.feeds.notifications.FEEDS_NOTIFICATION_CHANNEL_ID
import org.mochios.forums.notifications.FORUMS_NOTIFICATION_CHANNEL_ID
import org.mochios.projects.notifications.PROJECTS_NOTIFICATION_CHANNEL_ID
import org.mochios.wikis.notifications.WIKIS_NOTIFICATION_CHANNEL_ID
import org.mochios.chess.notifications.CHESS_NOTIFICATION_CHANNEL_ID
import org.mochios.go.notifications.GO_NOTIFICATION_CHANNEL_ID
import org.mochios.words.notifications.WORDS_NOTIFICATION_CHANNEL_ID

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
            "wikis" -> WIKIS_NOTIFICATION_CHANNEL_ID
            "chess" -> CHESS_NOTIFICATION_CHANNEL_ID
            "go" -> GO_NOTIFICATION_CHANNEL_ID
            "words" -> WORDS_NOTIFICATION_CHANNEL_ID
            else -> FEEDS_NOTIFICATION_CHANNEL_ID
        }
    }

    override fun deepLinkFor(context: Context, instance: String, link: String): Uri {
        // Per claude/plans/mochi-uri-scheme.md, system intents use the opaque
        // shape (0 slashes): mochi:notification?link=…
        // Hand-construct via the opaque-URI builder to avoid the //authority/path
        // shape Uri.parse + buildUpon would otherwise emit.
        val encoded = Uri.encode(link)
        return Uri.parse("mochi:notification?link=$encoded")
    }
}
