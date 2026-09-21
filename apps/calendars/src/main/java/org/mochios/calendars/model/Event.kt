// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.model

import org.mochios.android.sync.EventComponent

/**
 * One event as the server holds it, from `-/events/get`, `-/events/batch`,
 * `-/events/create` and `-/events/update`. [components] is the master
 * `VEVENT` and any overrides beside it; [ics] is the stored iCalendar text,
 * which nothing on the phone parses. [etag] is the precondition an update or
 * delete carries.
 */
data class Event(
    val id: String = "",
    val calendar: String = "",
    val slug: String = "",
    val uid: String = "",
    val etag: String = "",
    val component: String = "",
    val summary: String = "",
    val start: Long = 0,
    val finish: Long = 0,
    val allday: Boolean = false,
    val recurring: Boolean = false,
    val created: Long = 0,
    val updated: Long = 0,
    val ics: String = "",
    val components: List<EventComponent> = emptyList(),
) {
    /** The master `VEVENT`, the one an "All events" edit changes. */
    fun master(): EventComponent? =
        components.firstOrNull { it.name.equals("VEVENT", ignoreCase = true) && !it.exception() }

    /** The overrides, each replacing one occurrence of the master. */
    fun overrides(): List<EventComponent> =
        components.filter { it.name.equals("VEVENT", ignoreCase = true) && it.exception() }
}

/** The body of every action that answers one event. */
data class EventResponse(val event: Event = Event())

/** The body of `-/events/batch`. */
data class EventsResponse(val events: List<Event> = emptyList())

/**
 * The body of `-/events/changes`. [reset] means the phone should list
 * everything again: [changed] is then every event the server holds.
 */
data class ChangesResponse(
    val version: Long = 0,
    val reset: Boolean = false,
    val changed: List<String> = emptyList(),
    val deleted: List<String> = emptyList(),
)
