// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.launcher

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri

/**
 * An app's own icon on the phone's home screen, as a pinned shortcut that
 * opens the app the way its launcher icon does, and shortcuts to one of an
 * app's items, kept in step with the items the user has.
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
            .setIntent(intent)
            .apply { icon(context, app)?.let { setIcon(it) } }
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    /**
     * Asks the launcher to place a shortcut to one of [app]'s items - a feed,
     * a forum, a chat - or to a place inside one, such as a wiki's page, named
     * [label], with the app's icon. [path] is the item's place in the app, its
     * entity and any segments after it, which the shortcut opens as
     * `mochi:/<path>` in that app, as a notification's link would. Asking
     * again for the same path updates its one shortcut, and turns it back on
     * if [reconcile] had turned it off. Answers whether the launcher took the
     * request.
     */
    fun pinItem(context: Context, app: String, path: String, label: String): Boolean {
        if (!supported(context)) {
            return false
        }
        // launch-ok: mochi: deep link this app builds from its own item, not an external URL
        val intent = Intent(Intent.ACTION_VIEW, "mochi:/$path".toUri()).apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(APP, app)
        }
        val shortcut = ShortcutInfoCompat.Builder(context, "${app}_$path")
            .setShortLabel(label)
            .setLongLabel(label)
            .setIntent(intent)
            .apply { icon(context, app)?.let { setIcon(it) } }
            .build()
        // The launcher refuses to pin over a shortcut that is turned off.
        ShortcutManagerCompat.enableShortcuts(context, listOf(shortcut))
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    /**
     * Brings [app]'s pinned item shortcuts into line with the items the user
     * has, [items] by path to name. A shortcut to an item still there takes
     * its current name, and is turned back on if it was off. A shortcut to a
     * place inside an item, a path whose first segment is one of [items],
     * stays on while the item is there and keeps the name it was pinned with,
     * since the item's name is not its own. A shortcut to an item gone -
     * unsubscribed, left or deleted, here or on another device - is turned
     * off, so the launcher greys it out rather than opening an item that is
     * not there. Pass the app's whole list as just read from the server,
     * never a page of it or a list that failed to load.
     */
    fun reconcile(context: Context, app: String, items: Map<String, String>) {
        // The list read that calls this never fails for it: on a device
        // locked since it started, or with a shortcut the launcher will not
        // take, the next read puts them right.
        try {
            update(context, app, items)
        } catch (_: IllegalStateException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    /**
     * [items] by each path that opens them, to their names, for [reconcile]:
     * an item is reached by its id and by its fingerprint alike, whichever
     * the screen it was pinned from held.
     */
    fun <T> paths(items: Iterable<T>, name: (T) -> String, keys: (T) -> List<String?>): Map<String, String> =
        items.flatMap { item ->
            keys(item).filterNotNull().filter { key -> key.isNotEmpty() }.map { key -> key to name(item) }
        }.toMap()

    private fun update(context: Context, app: String, items: Map<String, String>) {
        val pinned = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
        val gone = mutableListOf<String>()
        val kept = mutableListOf<ShortcutInfoCompat>()
        val renamed = mutableListOf<ShortcutInfoCompat>()
        for (shortcut in pinned) {
            val path = pathOf(shortcut, app) ?: continue
            val label = items[path]
            if (label == null) {
                if ('/' in path && path.substringBefore('/') in items) {
                    kept += shortcut
                } else {
                    gone += shortcut.id
                }
                continue
            }
            kept += shortcut
            if (label.isNotEmpty() && shortcut.shortLabel.toString() != label) {
                // A field left out of an update keeps what the shortcut had,
                // so its icon stays.
                renamed += ShortcutInfoCompat.Builder(context, shortcut.id)
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIntents(shortcut.intents)
                    .build()
            }
        }
        // Turning on a shortcut that is on, or off one that is off, does
        // nothing, so neither asks which it is.
        if (gone.isNotEmpty()) ShortcutManagerCompat.disableShortcuts(context, gone, null)
        if (kept.isNotEmpty()) ShortcutManagerCompat.enableShortcuts(context, kept)
        if (renamed.isNotEmpty()) ShortcutManagerCompat.updateShortcuts(context, renamed)
    }

    /** The item path an item shortcut of [app] opens; null for any other shortcut. */
    private fun pathOf(shortcut: ShortcutInfoCompat, app: String): String? {
        val intent = shortcut.intent
        val data = intent.data ?: return null
        if (data.scheme != "mochi" || intent.getStringExtra(APP) != app) return null
        return data.path?.trimStart('/')?.takeIf { it.isNotEmpty() }
    }

    /** The extra naming the app a `mochi:` URI is for, as MainActivity reads it. */
    private const val APP = "app"

    /**
     * [app]'s launcher icon, `ic_<app>`, which each app's module ships as an
     * adaptive mipmap; the Mochi icon for an app without one, and none, which
     * the launcher draws as the app's own, where neither is packaged.
     */
    @Suppress("DiscouragedApi")
    private fun icon(context: Context, app: String): IconCompat? {
        val resources = context.resources
        val id = resources.getIdentifier("ic_$app", "mipmap", context.packageName)
            .takeIf { found -> found != 0 }
            ?: resources.getIdentifier("ic_launcher", "mipmap", context.packageName)
        return if (id == 0) null else IconCompat.createWithResource(context, id)
    }
}
