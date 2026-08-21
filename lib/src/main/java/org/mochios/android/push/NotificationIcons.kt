// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.ComponentName
import android.content.Context
import androidx.annotation.DrawableRes
import org.mochios.android.R

/**
 * Mochi-app slugs that create a notification channel at startup; each channel's
 * id is its slug. Keep in step with MochiApplication - NotificationRoutingTest
 * fails on a gap.
 */
internal val NOTIFICATION_CHANNELS = setOf(
    "feeds", "chat", "forums", "projects", "people", "crm",
    "wikis", "chess", "go", "words", "market", "staff",
)

/** Channel used when the payload names an app we have no channel for. */
internal const val FALLBACK_NOTIFICATION_CHANNEL = "feeds"

/**
 * Channel id for a notification: the server-supplied `app` slug, falling back
 * to `link`'s first path segment. Single source of truth for both transports.
 */
fun notificationChannelFor(app: String?, link: String?): String {
    val key = app.orEmpty().ifBlank {
        link.orEmpty().trimStart('/').substringBefore('/')
    }.lowercase()
    return if (key in NOTIFICATION_CHANNELS) key else FALLBACK_NOTIFICATION_CHANNEL
}

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

/**
 * Activity-alias for a slug, so a notification's badge lands on that app's
 * launcher icon only; the implicit `mochi:` intent resolves to MainActivity and
 * badges every Mochi icon. Null for unknown apps.
 */
fun launcherComponentFor(context: Context, app: String?): ComponentName? {
    val name = LAUNCHER_ALIASES[app?.lowercase()] ?: return null
    return ComponentName(context, "${context.packageName}.$name")
}

/**
 * Every activity-alias in the shell manifest, by slug. A missing entry silently
 * reverts that app to the badge-everything fallback.
 */
internal val LAUNCHER_ALIASES = mapOf(
    "feeds" to "MochiFeedsLauncher",
    "chat" to "MochiChatLauncher",
    "forums" to "MochiForumsLauncher",
    "projects" to "MochiProjectsLauncher",
    "crm" to "MochiCrmLauncher",
    "people" to "MochiPeopleLauncher",
    "settings" to "MochiSettingsLauncher",
    "wikis" to "MochiWikisLauncher",
    "chess" to "MochiChessLauncher",
    "go" to "MochiGoLauncher",
    "words" to "MochiWordsLauncher",
    "market" to "MochiMarketLauncher",
    "staff" to "MochiStaffLauncher",
)
