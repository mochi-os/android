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
 * Mochi-app slugs that create a notification channel at startup (see the
 * `setup*NotificationChannel` calls in MochiApplication). Every channel's id
 * *is* its slug, so routing needs only this set, not the per-module channel
 * constants — which is what lets both transports share one mapping even though
 * `lib` cannot see the app modules.
 *
 * Keep in step with MochiApplication. NotificationRoutingTest fails if a slug
 * here has no channel or a created channel is missing here.
 */
internal val NOTIFICATION_CHANNELS = setOf(
    "feeds", "chat", "forums", "projects", "people", "crm",
    "wikis", "chess", "go", "words", "market", "staff",
)

/** Channel used when the payload names an app we have no channel for. */
internal const val FALLBACK_NOTIFICATION_CHANNEL = "feeds"

/**
 * Channel id for a notification, preferring the server-supplied `app` slug and
 * falling back to the first path segment of `link` when the server is older
 * than that field.
 *
 * Single source of truth for both transports. It previously existed twice —
 * ten apps in the UnifiedPush dispatcher, four in the FCM service — and the two
 * drifted, so on FCM every app beyond the first four posted on the Feeds
 * channel with the wrong name, importance and sound.
 */
fun notificationChannelFor(app: String?, link: String?): String {
    val key = app.orEmpty().ifBlank {
        link.orEmpty().trimStart('/').substringBefore('/')
    }.lowercase()
    return if (key in NOTIFICATION_CHANNELS) key else FALLBACK_NOTIFICATION_CHANNEL
}

/**
 * Map a Mochi-app slug to the small notification icon shown in the system
 * tray. Falls back to the generic Mochi mark when the app is unknown.
 *
 * Deliberately narrower than [NOTIFICATION_CHANNELS]: only these apps ship a
 * per-app tray drawable, and the rest correctly show the Mochi mark. Adding
 * more is an asset change, not a routing one.
 *
 * Used by every notification post path — FCM, UnifiedPush dispatcher, and
 * the foreground-service status notification — so all surfaces show the
 * same per-app branding.
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
 * Activity-alias component for a Mochi-app slug. Notifications whose
 * PendingIntent targets this component show their unread-badge dot/counter
 * on the matching launcher icon only — without it, launchers (e.g. Octopi)
 * see the implicit `mochi:` intent resolve to MainActivity and stamp the
 * badge on every alias that targets MainActivity (i.e. every Mochi-app
 * icon). Returns null for unknown apps so callers can fall back to the
 * implicit URI form.
 */
fun launcherComponentFor(context: Context, app: String?): ComponentName? {
    val name = LAUNCHER_ALIASES[app?.lowercase()] ?: return null
    return ComponentName(context, "${context.packageName}.$name")
}

/**
 * Every activity-alias declared in the shell manifest, by slug. All thirteen,
 * because a missing entry is not a cosmetic gap: the notification falls back to
 * the implicit `mochi:` intent, which resolves to MainActivity, and the
 * launcher then stamps the unread badge on every Mochi icon — the exact
 * behaviour [launcherComponentFor] exists to prevent. Eight were missing.
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
