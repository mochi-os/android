package org.mochios.android.push

import android.content.ComponentName
import android.content.Context
import androidx.annotation.DrawableRes
import org.mochios.android.R

/**
 * Map a Mochi-app slug to the small notification icon shown in the system
 * tray. Falls back to the generic Mochi mark when the app is unknown.
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
    val name = when (app?.lowercase()) {
        "feeds" -> "MochiFeedsLauncher"
        "chat" -> "MochiChatLauncher"
        "forums" -> "MochiForumsLauncher"
        "projects" -> "MochiProjectsLauncher"
        "settings" -> "MochiSettingsLauncher"
        else -> return null
    }
    return ComponentName(context, "${context.packageName}.$name")
}
