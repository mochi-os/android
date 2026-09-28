// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Which calendars the views show, kept per device and never on the server: it
 * is a viewing choice, like a window's width, not something another device
 * should inherit. A calendar nothing has been said about is shown, so a new
 * device starts with everything on and a calendar made elsewhere appears.
 *
 * Only the hidden ones are stored, for that reason.
 */
object VisibilityStore {

    private const val PREFERENCES = "mochi_calendars_visibility"
    private const val HIDDEN = "hidden"

    /** The calendars the user has hidden. */
    fun hidden(context: Context): Set<String> =
        preferences(context).getStringSet(HIDDEN, emptySet())?.toSet().orEmpty()

    fun hidden(context: Context, value: Set<String>) {
        preferences(context).edit().putStringSet(HIDDEN, value).apply()
    }

    /** Whether a calendar is shown. */
    fun shown(context: Context, calendar: String): Boolean = calendar !in hidden(context)

    /** Shows a hidden calendar or hides a shown one. */
    fun toggle(context: Context, calendar: String) {
        val current = hidden(context).toMutableSet()
        if (!current.remove(calendar)) current.add(calendar)
        hidden(context, current)
    }

    /** Shows [calendar] and hides every other calendar in [all]. */
    fun only(context: Context, calendar: String, all: Collection<String>) {
        hidden(context, all.filterNot { it == calendar }.toSet())
    }

    /**
     * Shows [calendar] if it is hidden, as when an event was just saved into
     * it, so the event does not vanish from view as if it had not saved.
     */
    fun reveal(context: Context, calendar: String) {
        val current = hidden(context)
        if (calendar in current) hidden(context, current - calendar)
    }

    /**
     * Calls [listener] each time the hidden calendars change, from anywhere
     * on the device, until the function returned is called.
     */
    fun watch(context: Context, listener: () -> Unit): () -> Unit {
        val preferences = preferences(context)
        // Held here, since the preferences hold their listeners weakly.
        val callback = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == HIDDEN) listener()
        }
        preferences.registerOnSharedPreferenceChangeListener(callback)
        return { preferences.unregisterOnSharedPreferenceChangeListener(callback) }
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
