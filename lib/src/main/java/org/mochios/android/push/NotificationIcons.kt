// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.DrawableRes
import org.mochios.android.R
import org.mochios.android.launcher.LAUNCHER_ACTIVITIES

/**
 * Mochi-app slugs that create a notification channel at startup; each channel's
 * id is its slug. Keep in step with MochiApplication - NotificationRoutingTest
 * fails on a gap.
 */
internal val NOTIFICATION_CHANNELS = setOf(
    "feeds", "chat", "forums", "projects", "people", "crm",
    "wikis", "chess", "go", "words", "market", "staff", "calendars", "settings",
)

/**
 * Each app's tray icon: its web icon in white, written by
 * claude/scripts/android-icons.py --tray, which also checks it.
 */
internal val NOTIFICATION_ICONS: Map<String, Int> = mapOf(
    "calendars" to R.drawable.ic_notification_calendars,
    "chat" to R.drawable.ic_notification_chat,
    "chess" to R.drawable.ic_notification_chess,
    "crm" to R.drawable.ic_notification_crm,
    "feeds" to R.drawable.ic_notification_feeds,
    "forums" to R.drawable.ic_notification_forums,
    "go" to R.drawable.ic_notification_go,
    "market" to R.drawable.ic_notification_market,
    "people" to R.drawable.ic_notification_people,
    "projects" to R.drawable.ic_notification_projects,
    "settings" to R.drawable.ic_notification_settings,
    "staff" to R.drawable.ic_notification_staff,
    "wikis" to R.drawable.ic_notification_wikis,
    "words" to R.drawable.ic_notification_words,
)

/**
 * The channel of notifications that open Settings, such as the test a
 * notification category sends. The Settings module posts none itself, so
 * the channel and its name live here.
 */
fun setupSettingsNotificationChannel(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(
        NotificationChannel(
            "settings",
            context.getString(R.string.notification_channel_settings),
            NotificationManager.IMPORTANCE_DEFAULT,
        ),
    )
}

/** Channel used when the payload names an app we have no channel for. */
internal const val FALLBACK_NOTIFICATION_CHANNEL = "feeds"

/**
 * The client app a push belongs to, or null when neither field names one.
 *
 * The payload's `app` is the sending app's id. That is its slug only where an
 * app runs from its directory, as on a development server; a published app's
 * id is an entity id. So `app` is taken when it names an app this client
 * hosts, and otherwise the first segment of `link`, which always starts with
 * the app's path. The one resolver for the channel, the icon and the activity
 * a tap opens.
 */
fun pushApp(app: String?, link: String?): String? {
    val named = app.orEmpty().lowercase()
    if (named in LAUNCHER_ACTIVITIES) return named
    val linked = link.orEmpty().trimStart('/').substringBefore('/').substringBefore('?').lowercase()
    return linked.takeIf { it in LAUNCHER_ACTIVITIES }
}

/** Channel id for a notification; see [pushApp]. */
fun notificationChannelFor(app: String?, link: String?): String =
    pushApp(app, link)?.takeIf { it in NOTIFICATION_CHANNELS } ?: FALLBACK_NOTIFICATION_CHANNEL

/**
 * Small tray icon for a Mochi-app slug, falling back to the generic Mochi mark
 * for an app this client does not host.
 */
@DrawableRes
fun notificationIconFor(app: String?): Int =
    NOTIFICATION_ICONS[app?.lowercase()] ?: R.drawable.ic_mochi_notification
