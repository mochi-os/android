// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.launcher

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * An app's own icon on the phone's home screen, as a pinned shortcut that
 * opens the app the way its launcher icon does.
 */
object Shortcuts {

    /** Whether the phone's launcher accepts pinned shortcuts at all. */
    fun supported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /**
     * Asks the launcher to place [app]'s icon, named [label], on the home
     * screen. The launcher confirms with the user and places it. The
     * shortcut's id is the app's name, so asking again for the same app
     * updates the one shortcut. Answers whether the launcher took the
     * request; false for an app with no launcher activity or a launcher that
     * refuses pinning.
     */
    fun pin(context: Context, app: String, label: String): Boolean {
        val intent = launcherIntentFor(context, app) ?: return false
        if (!supported(context)) {
            return false
        }
        if (!launcherEnabled(context, app)) {
            LauncherIconToggle.setVisible(context, LAUNCHER_ACTIVITIES.getValue(app.lowercase()), true)
        }
        val shortcut = ShortcutInfoCompat.Builder(context, app)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(icon(context, app))
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    /**
     * [app]'s launcher icon, `ic_<app>`, which each app's module ships as an
     * adaptive mipmap; the Mochi icon for an app without one.
     */
    @Suppress("DiscouragedApi")
    private fun icon(context: Context, app: String): IconCompat {
        val resources = context.resources
        val id = resources.getIdentifier("ic_$app", "mipmap", context.packageName)
            .takeIf { found -> found != 0 }
            ?: resources.getIdentifier("ic_launcher", "mipmap", context.packageName)
        return IconCompat.createWithResource(context, id)
    }
}
