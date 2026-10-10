// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.launcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Every app's activity in the shell manifest, by app name. Each hosts one
 * Mochi app in a task of its own; only home's is a launcher entry, the
 * others are opened from the home grid, notifications, forwarded links and
 * pinned shortcuts. A missing entry hides the app from the home grid and
 * sends its notifications through the forwarding MainActivity.
 */
internal val LAUNCHER_ACTIVITIES = mapOf(
    "home" to "MochiHomeLauncher",
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
    "calendars" to "MochiCalendarsLauncher",
)

/** Whether the client has a launcher activity for [app], so it can open it. */
fun launchable(app: String?): Boolean = LAUNCHER_ACTIVITIES.containsKey(app?.lowercase())

/**
 * The activity that hosts an app, which a notification for it opens directly
 * rather than through the forwarding MainActivity. Null for an app the client
 * has no activity for.
 */
fun launcherComponentFor(context: Context, app: String?): ComponentName? {
    val name = LAUNCHER_ACTIVITIES[app?.lowercase()] ?: return null
    return ComponentName(context, "${context.packageName}.$name")
}

/**
 * The intent a home screen sends to open [app]: main and launcher, at the
 * app's own launcher activity, in a new task. That activity's task affinity
 * gives the app its own task, so the launch resumes that task or starts it,
 * as a tap on the app's icon does. Null for an app with no launcher activity.
 */
fun launcherIntentFor(context: Context, app: String?): Intent? {
    val component = launcherComponentFor(context, app) ?: return null
    return Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

/**
 * Whether [app]'s launcher activity can be started now: switched on, or left
 * at the manifest's own setting and that is on. Staff ships switched off
 * until its access check passes.
 */
fun launcherEnabled(context: Context, app: String?): Boolean {
    val component = launcherComponentFor(context, app) ?: return false
    val pm = context.packageManager
    return when (pm.getComponentEnabledSetting(component)) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> try {
            pm.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        else -> false
    }
}

/**
 * Opens [app] in its own task, switching its launcher activity on first when
 * it is off: the caller has the server's word that the user may open it.
 * Answers whether the app was started.
 */
fun openApp(context: Context, app: String): Boolean {
    val intent = launcherIntentFor(context, app) ?: return false
    if (!launcherEnabled(context, app)) {
        LauncherIconToggle.setVisible(context, LAUNCHER_ACTIVITIES.getValue(app.lowercase()), true)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
