// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.editor

import org.mochios.android.sync.CalendarsMapping
import org.mochios.android.sync.EventComponent
import org.mochios.android.sync.EventProperty
import org.mochios.android.sync.property

/** Whether an edit or a delete touches one occurrence or the whole series. */
enum class Scope {
    ONE,
    ALL,
}

/**
 * What the editor's fields say, as far as building an event's tree needs it.
 * [start] and [finish] are epoch seconds and [reminder] minutes before the
 * start, -1 for none.
 *
 * [occurrence] is the occurrence the user opened, epoch seconds, 0 when the
 * event does not repeat; [series] is the master's own start. The form shows
 * the occurrence, so a change to the whole series moves the master by however
 * far the user moved that occurrence rather than onto its date.
 */
data class EventForm(
    val title: String = "",
    val start: Long = 0,
    val finish: Long = 0,
    val allday: Boolean = false,
    val timezone: String = CalendarsMapping.UTC,
    val location: String = "",
    val description: String = "",
    val recurrence: Recurrence = Recurrence(),
    val reminder: Int = -1,
    val occurrence: Long = 0,
    val series: Long = 0,
)

/** The properties the editor owns; every other one is carried through. */
private val MANAGED = setOf(
    "SUMMARY", "LOCATION", "DESCRIPTION", "DTSTART", "DTEND", "DURATION",
    "RRULE", "RDATE", "EXDATE", "RECURRENCE-ID",
)

/**
 * The tree to send for an edit. [carried] is the event as the server last
 * sent it, whose properties the editor has no field for are kept.
 *
 * [Scope.ALL] rewrites the master and keeps every override; [Scope.ONE]
 * keeps the master as it stands and replaces just the override for the
 * occurrence the user opened, adding one when there was none.
 */
fun components(form: EventForm, carried: List<EventComponent>, scope: Scope): List<EventComponent> {
    val events = carried.filter { it.name.equals("VEVENT", ignoreCase = true) }
    val master = events.firstOrNull { !it.exception() }
    val overrides = events.filter { it.exception() }

    if (scope == Scope.ALL || master == null) {
        val start = if (form.occurrence > 0 && form.series > 0) {
            form.series + (form.start - form.occurrence)
        } else {
            form.start
        }
        return listOf(component(form, master, recurrence = true, occurrence = 0, start = start)) + overrides
    }
    val replaced = component(
        form,
        overrides.firstOrNull { matches(it, form.occurrence) },
        recurrence = false,
        occurrence = form.occurrence,
        start = form.start,
    )
    return listOf(master) + overrides.filterNot { matches(it, form.occurrence) } + replaced
}

/**
 * The tree with one occurrence excluded: its override dropped and its start
 * added to the master's `EXDATE`, which is how an occurrence is taken out of
 * a series.
 */
fun excluded(carried: List<EventComponent>, occurrence: Long): List<EventComponent> {
    val events = carried.filter { it.name.equals("VEVENT", ignoreCase = true) }
    val master = events.firstOrNull { !it.exception() } ?: return carried
    val start = master.property("DTSTART")
    val allday = start != null && CalendarsMapping.date(start)
    val exdate = CalendarsMapping.stamp("EXDATE", occurrence * 1000, CalendarsMapping.zone(start), allday)
    val kept = events.filter { it.exception() && !matches(it, occurrence) }
    return listOf(master.copy(properties = master.properties + exdate)) + kept
}

/** Whether an override replaces the occurrence starting at [occurrence]. */
private fun matches(override: EventComponent, occurrence: Long): Boolean =
    override.property("RECURRENCE-ID")?.let { CalendarsMapping.moment(it) / 1000 } == occurrence

/**
 * One `VEVENT` from the form. [carried] is the component as the server last
 * sent it, whose properties the editor has no field for are kept.
 * [recurrence] says whether this component owns the series' `RRULE`;
 * [occurrence] is non-zero for an override, whose `RECURRENCE-ID` it is.
 * [start] is the component's own start: the occurrence's for an override and
 * the master's for the series.
 */
private fun component(
    form: EventForm,
    carried: EventComponent?,
    recurrence: Boolean,
    occurrence: Long,
    start: Long,
): EventComponent {
    val zone = form.timezone.ifBlank { CalendarsMapping.UTC }
    val finish = start + (form.finish - form.start)
    val properties = mutableListOf<EventProperty>()
    carried?.properties?.filterNot { it.name.uppercase() in MANAGED }?.let(properties::addAll)
    properties.add(property("SUMMARY", form.title.trim()))
    properties.add(CalendarsMapping.stamp("DTSTART", start * 1000, zone, form.allday))
    properties.add(CalendarsMapping.stamp("DTEND", finish * 1000, zone, form.allday))
    if (form.location.isNotBlank()) properties.add(property("LOCATION", form.location.trim()))
    if (form.description.isNotBlank()) properties.add(property("DESCRIPTION", form.description.trim()))
    if (recurrence) {
        val rule = form.recurrence.rule()
        if (rule != null) {
            properties.add(property("RRULE", rule))
            // An RRULE the editor dropped takes its exclusions with it.
            carried?.all("EXDATE")?.let(properties::addAll)
            carried?.all("RDATE")?.let(properties::addAll)
        }
    }
    if (occurrence > 0) {
        val allday = carried?.property("RECURRENCE-ID")?.let { CalendarsMapping.date(it) } ?: form.allday
        properties.add(CalendarsMapping.stamp("RECURRENCE-ID", occurrence * 1000, zone, allday))
    }
    val nested = carried?.components?.filterNot { it.name.equals("VALARM", ignoreCase = true) }.orEmpty()
    val alarms = if (form.reminder >= 0) {
        listOf(CalendarsMapping.alarm(form.reminder, form.title.trim()))
    } else {
        emptyList()
    }
    return EventComponent("VEVENT", properties, nested + alarms)
}
