// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.storage

import android.content.Context
import org.mochios.calendars.model.Zone

/**
 * What the last new event saved on this device was: whether it ran all day,
 * and the zones its two ends were written in, IANA names.
 */
data class Memory(
    val allday: Boolean = false,
    val zone: Zone = Zone(),
)

/**
 * The last new event's all-day setting and zones, kept per device and never
 * on the server, as the calendar it was made in is: the next new event
 * starts with them, since a device tends to make the same kind of event
 * again. Nothing else is kept - not the reminder, which is a preference, nor
 * the repeat, the title or the location.
 */
object MemoryStore {

    private const val PREFERENCES = "mochi_calendars_memory"
    private const val ALLDAY = "allday"
    private const val START = "zone.start"
    private const val FINISH = "zone.finish"

    /** The memory, or null when no new event has been saved on this device. */
    fun memory(context: Context): Memory? {
        val preferences = preferences(context)
        if (!preferences.contains(ALLDAY)) return null
        return Memory(
            allday = preferences.getBoolean(ALLDAY, false),
            zone = Zone(
                preferences.getString(START, null).orEmpty(),
                preferences.getString(FINISH, null).orEmpty(),
            ),
        )
    }

    fun memory(context: Context, value: Memory) {
        preferences(context).edit()
            .putBoolean(ALLDAY, value.allday)
            .putString(START, value.zone.start)
            .putString(FINISH, value.zone.finish)
            .apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
