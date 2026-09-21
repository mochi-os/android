// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

/**
 * One iCalendar property of an event: the property [name] (a protocol token
 * such as `DTSTART` or `RRULE`, exempt from the single-word rule), its
 * parameters — `TZID` and `VALUE` chiefly — and its [value].
 *
 * The calendars app's editor and the calendar sync adapter share this shape;
 * the server parses the stored iCalendar into a list of these and formats it
 * back, so neither the editor nor the adapter reads or writes iCalendar text.
 */
data class EventProperty(
    val name: String = "",
    val params: Map<String, List<String>> = emptyMap(),
    val value: String = "",
) {

    /** The first value of [parameter], or null when the property carries none. */
    fun parameter(parameter: String): String? =
        params.entries.firstOrNull { it.key.equals(parameter, ignoreCase = true) }
            ?.value?.firstOrNull()?.takeIf { it.isNotBlank() }
}

/**
 * One component of an event's tree: a `VEVENT` with its properties, or a
 * `VALARM` nested inside one. A recurring event's overrides are further
 * `VEVENT`s beside the master, each carrying a `RECURRENCE-ID`.
 */
data class EventComponent(
    val name: String = "",
    val properties: List<EventProperty> = emptyList(),
    val components: List<EventComponent> = emptyList(),
) {

    /** The first property called [name], or null. */
    fun property(name: String): EventProperty? =
        properties.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Every property called [name], in the order the server holds them. */
    fun all(name: String): List<EventProperty> =
        properties.filter { it.name.equals(name, ignoreCase = true) }

    /** The first property's value, or an empty string. */
    fun value(name: String): String = property(name)?.value.orEmpty()

    /** Whether this component is an override of one occurrence of its master. */
    fun exception(): Boolean = property("RECURRENCE-ID") != null
}

/** A property with one parameter, the common case when building a component. */
fun property(name: String, value: String, parameter: String? = null, argument: String? = null): EventProperty =
    EventProperty(
        name = name,
        params = if (parameter != null && argument != null) mapOf(parameter to listOf(argument)) else emptyMap(),
        value = value,
    )
