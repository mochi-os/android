// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

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
    "wikis", "chess", "go", "words", "market", "staff", "calendars",
)

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
 * Small tray icon for a Mochi-app slug, falling back to the generic Mochi mark.
 * Deliberately narrower than [NOTIFICATION_CHANNELS]: only these apps ship a
 * drawable.
 */
@DrawableRes
fun notificationIconFor(app: String?): Int = when (app?.lowercase()) {
    "feeds" -> R.drawable.ic_notification_feeds
    "chat" -> R.drawable.ic_notification_chat
    "forums" -> R.drawable.ic_notification_forums
    "projects" -> R.drawable.ic_notification_projects
    "settings" -> R.drawable.ic_notification_settings
    else -> R.drawable.ic_mochi_notification
}
