// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.editor

import org.mochios.android.sync.CalendarsMapping
import org.mochios.android.sync.EventComponent
import org.mochios.android.sync.EventProperty
import org.mochios.android.sync.property
import org.mochios.calendars.model.Instance
import org.mochios.calendars.model.Zone
import org.mochios.calendars.storage.Memory
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Whether an edit or a delete touches one occurrence, that occurrence and
 * every one after it, or the whole series.
 */
enum class Scope {
    ONE,
    FOLLOWING,
    ALL,
}

/**
 * What the editor's fields say, as far as building an event's tree needs it.
 * [start] and [finish] are epoch seconds and [reminder] minutes before the
 * start, -1 for none. [zone] is the zone each end is written in: a flight is
 * 10:00 Europe/London to 13:00 America/New_York, though most events have the
 * same zone at both ends.
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
    val zone: Zone = Zone(),
    val location: String = "",
    val description: String = "",
    val recurrence: Recurrence = Recurrence(),
    val reminder: Int = -1,
    val occurrence: Long = 0,
    val series: Long = 0,
)

/**
 * The zones a component was written in, which the editor opens it in:
 * `DTSTART`'s `TZID` for the start and `DTEND`'s for the finish. A `DTEND`
 * without one follows the start, and no `TZID` at all - UTC or floating -
 * means the [user]'s own zone.
 */
fun written(component: EventComponent, user: String): Zone {
    val start = component.property("DTSTART")?.parameter("TZID")?.takeIf { it.isNotBlank() } ?: user
    val finish = component.property("DTEND")?.parameter("TZID")?.takeIf { it.isNotBlank() } ?: start
    return Zone(start, finish)
}

/**
 * Whether either end reads in another zone than the [user]'s own, which is
 * when the editor shows the zones without being asked. A blank end reads in
 * the user's zone.
 */
fun foreign(zone: Zone, user: String): Boolean =
    zone.start.ifBlank { user } != user || zone.finish.ifBlank { user } != user

/**
 * The pair after the start zone is set to [start]: the finish zone follows
 * while the two are still equal, and stops once it has been set apart.
 */
/**
 * The end no earlier than the start as instants: [ends] as it is when it
 * already follows [begins], else moved on by as many whole days as it takes.
 */
fun following(begins: Long, ends: Long): Long {
    if (ends >= begins) return ends
    val days = ((begins - ends + 86_399) / 86_400).coerceAtLeast(1)
    return ends + days * 86_400
}

fun follow(zone: Zone, start: String): Zone =
    Zone(start, if (zone.finish == zone.start) start else zone.finish)

/**
 * What a blank new form starts with, from the [memory] of the last new event
 * saved on this device. The zones are the memory's, the [user]'s own where
 * it holds none or an end is blank. All-day is what the route says when it
 * says anything - [allday] set - since a tap on an hour of the time grid has
 * already chosen timed; a tap on a day cell of the month views, which names a
 * [start] without saying, and the screen's own "new event" action both take
 * the memory's, as a day says which day and not which kind.
 */
fun remembered(memory: Memory?, user: String, start: Long, allday: Boolean?): Memory = Memory(
    allday = allday ?: memory?.allday ?: false,
    zone = Zone(
        memory?.zone?.start?.ifBlank { user } ?: user,
        memory?.zone?.finish?.ifBlank { user } ?: user,
    ),
)

/**
 * The instant that reads on the clock in [to] as [instant] does in [from],
 * for a time the user typed in one zone and then moved to another: 10:00
 * London becomes 10:00 New York, five hours later. A zone the platform does
 * not know is read as the device's.
 */
fun moved(instant: Long, from: String, to: String): Long {
    if (from == to) return instant
    val local = Instant.ofEpochSecond(instant).atZone(zoneOf(from)).toLocalDateTime()
    return local.atZone(zoneOf(to)).toEpochSecond()
}

private fun zoneOf(name: String): ZoneId =
    runCatching { ZoneId.of(name) }.getOrDefault(ZoneId.systemDefault())

/** The properties the editor owns; every other one is carried through. */
private val MANAGED = setOf(
    "SUMMARY", "LOCATION", "DESCRIPTION", "DTSTART", "DTEND", "DURATION",
    "RRULE", "RDATE", "EXDATE", "RECURRENCE-ID",
)

/**
 * The tree to send for an edit. [carried] is the event as the server last
 * sent it, whose properties the editor has no field for are kept. [user] is
 * the zone an override's end reads in when it names none.
 *
 * [Scope.ALL] rewrites the master and keeps every override; a series whose
 * start moved takes its overrides and listed dates along by the same
 * distance. [Scope.ONE] keeps the master as it stands and replaces just the
 * override for the occurrence the user opened, adding one when there was
 * none. [Scope.FOLLOWING] is two events, which [split] builds; here it reads
 * as the whole series, which is what it is on the series' first occurrence.
 */
fun components(
    form: EventForm,
    carried: List<EventComponent>,
    scope: Scope,
    user: String = form.zone.start.ifBlank { CalendarsMapping.UTC },
): List<EventComponent> {
    val events = carried.filter { it.name.equals("VEVENT", ignoreCase = true) }
    val master = events.firstOrNull { !it.exception() }
    val overrides = events.filter { it.exception() }

    if (scope != Scope.ONE || master == null) {
        val start = if (form.occurrence > 0 && form.series > 0) {
            form.series + (form.start - form.occurrence)
        } else {
            form.start
        }
        val head = component(form, master, recurrence = true, occurrence = 0, start = start)
        val first = master?.let { begins(it) } ?: 0L
        val shift = if (first == 0L) 0L else start - first
        if (shift == 0L) return listOf(head) + overrides
        return listOf(relisted(head, master!!, Long.MIN_VALUE, shift)) +
            carried(overrides, head, Long.MIN_VALUE, shift, user)
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
 * The editor's form for a stored `VEVENT`, read in the [user]'s zone where
 * it names none. With [occurrence] set, the form is moved onto that
 * occurrence of the series: the master's own form, shifted by the distance
 * from the series' start, which is what a change to this occurrence and the
 * ones after it starts from. An event with neither `DTEND` nor `DURATION`
 * ends where it starts, as the server reads it.
 */
fun draft(component: EventComponent, user: String, occurrence: Long = 0): EventForm {
    val starts = component.property("DTSTART")
    val allday = starts != null && CalendarsMapping.date(starts)
    val start = starts?.let { CalendarsMapping.moment(it) / 1000 } ?: 0L
    val length = component.property("DTEND")?.let { CalendarsMapping.moment(it) / 1000 - start }
        ?: component.value("DURATION").takeIf { it.isNotBlank() }?.let { CalendarsMapping.seconds(it) }
        ?: if (allday) 86_400L else 0L
    val alarm = component.components.firstOrNull { it.name.equals("VALARM", ignoreCase = true) }
    val form = EventForm(
        title = component.value("SUMMARY"),
        start = start,
        finish = start + length,
        allday = allday,
        zone = written(component, user),
        location = component.value("LOCATION"),
        description = component.value("DESCRIPTION"),
        recurrence = recurrence(component.value("RRULE")),
        reminder = alarm?.property("TRIGGER")?.let { minutes(it.value) } ?: -1,
    )
    return if (occurrence > 0 && start != 0L) shifted(form, occurrence - start) else form
}

/** The form with both ends moved by [seconds], its length and zones kept. */
fun shifted(form: EventForm, seconds: Long): EventForm =
    if (seconds == 0L) form else form.copy(start = form.start + seconds, finish = form.finish + seconds)

/**
 * The form moved by whole [days], each end keeping its clock reading in its
 * own zone across a clock change; an all-day form moves by its dates.
 */
fun advanced(form: EventForm, days: Long): EventForm {
    if (days == 0L) return form
    if (form.allday) return shifted(form, days * 86_400)
    fun move(instant: Long, zone: String): Long =
        Instant.ofEpochSecond(instant).atZone(zoneOf(zone)).plusDays(days).toEpochSecond()
    return form.copy(start = move(form.start, form.zone.start), finish = move(form.finish, form.zone.finish))
}

/**
 * The instant the form starts. A timed form's start is one already; an
 * all-day form holds the UTC midnight of its first day, which begins for the
 * [user] at their own midnight.
 */
fun instant(form: EventForm, user: String): Long {
    if (!form.allday) return form.start
    val day = Instant.ofEpochSecond(form.start).atZone(ZoneOffset.UTC).toLocalDate()
    return day.atStartOfDay(zoneOf(user)).toEpochSecond()
}

/**
 * The form a copy of a stored event opens on, as a new event of its own.
 * With [Scope.ONE] it is the occurrence at [occurrence] alone: its own
 * override, or the master moved onto it, with the repeat cleared, and an
 * override without a reminder of its own sounding the master's, as the
 * editor shows it. With any other scope it is the master, its repeat kept
 * as written. Null when the event has no master to copy.
 */
fun copied(carried: List<EventComponent>, occurrence: Long, scope: Scope, user: String): EventForm? {
    val events = carried.filter { it.name.equals("VEVENT", ignoreCase = true) }
    val master = events.firstOrNull { !it.exception() } ?: return null
    val whole = draft(master, user)
    if (scope != Scope.ONE || occurrence == 0L) return whole
    val override = events.firstOrNull { it.exception() && matches(it, occurrence) }
    val own = if (override != null) draft(override, user) else draft(master, user, occurrence)
    return own.copy(
        recurrence = Recurrence(),
        reminder = if (own.reminder >= 0) own.reminder else whole.reminder,
    )
}

/**
 * The form a copy of an occurrence with no stored event to load opens on -
 * a subscription's or a birthday - built from the occurrence as listed: one
 * event of its title, span, zones, location and description, with the
 * [reminder] a new event gets. An all-day occurrence holds its dates as the
 * editor does, from the UTC midnight of its first day, and keeps its whole
 * days; a timed one keeps its ends, each read in the zone it was written
 * in, the [user]'s own where it names none.
 */
fun copied(instance: Instance, user: String, reminder: Int): EventForm {
    val start = if (instance.allday) instance.occurrence else instance.start
    val length = if (instance.allday) {
        maxOf(1L, Math.round((instance.finish - instance.start) / 86_400.0)) * 86_400
    } else {
        (instance.finish - instance.start).coerceAtLeast(0)
    }
    val begins = instance.zone?.start?.takeIf { it.isNotBlank() } ?: user
    val ends = instance.zone?.finish?.takeIf { it.isNotBlank() } ?: begins
    return EventForm(
        title = instance.summary,
        start = start,
        finish = start + length,
        allday = instance.allday,
        zone = Zone(begins, ends),
        location = instance.location,
        description = instance.description,
        reminder = reminder,
    )
}

/**
 * The series cut to end just before the occurrence at [occurrence]: the
 * master's rule gains an `UNTIL` there and loses any `COUNT`, and the
 * overrides and listed dates from that occurrence on are dropped. Null when
 * the occurrence is the series' first, since nothing would be left, or when
 * the event is no series.
 */
fun truncated(carried: List<EventComponent>, occurrence: Long): List<EventComponent>? {
    val events = carried.filter { it.name.equals("VEVENT", ignoreCase = true) }
    val master = events.firstOrNull { !it.exception() } ?: return null
    val starts = master.property("DTSTART") ?: return null
    val rule = master.value("RRULE")
    val first = begins(master)
    if (rule.isBlank() || first == 0L || occurrence <= first) return null
    // UNTIL is inclusive, and takes the form of DTSTART: the day before for
    // a whole-day series, otherwise the second before, in UTC.
    val allday = CalendarsMapping.date(starts)
    val until = CalendarsMapping.stamp(
        "UNTIL",
        (occurrence - if (allday) 86_400L else 1L) * 1000,
        CalendarsMapping.UTC,
        allday,
    ).value
    val parts = rule.split(";").filterNot {
        it.uppercase().startsWith("UNTIL=") || it.uppercase().startsWith("COUNT=")
    } + "UNTIL=$until"
    val (before, _) = dates(master.properties, occurrence, 0)
    val properties = master.properties.filterNot { listed(it) }.map {
        if (it.name.equals("RRULE", ignoreCase = true)) it.copy(value = parts.joinToString(";")) else it
    } + before
    val kept = events.filter { it.exception() && key(it).let { at -> at == 0L || at < occurrence } }
    return listOf(master.copy(properties = properties)) + kept
}

/**
 * An edit of the occurrence at [occurrence] and every one after it: the
 * series is cut in two. The first of the pair is the old event, ending just
 * before the occurrence; the second is a new event whose master is [form],
 * the series as it now goes on from there, so the form's dates are where
 * this occurrence lands. The old overrides and listed dates from the cut
 * onwards move to the new event, shifted as the form shifted the occurrence,
 * and its rule keeps the old one's end; a `COUNT` is left for the server to
 * shorten by the occurrences the old event keeps. Null when the occurrence
 * is the series' first, which makes the edit one of the whole series.
 */
fun split(
    carried: List<EventComponent>,
    form: EventForm,
    occurrence: Long,
    user: String,
): Pair<List<EventComponent>, List<EventComponent>>? {
    val before = truncated(carried, occurrence) ?: return null
    val events = carried.filter { it.name.equals("VEVENT", ignoreCase = true) }
    val master = events.first { !it.exception() }
    val shift = form.start - occurrence
    val head = component(form, master, recurrence = true, occurrence = 0, start = form.start)
    val after = listOf(relisted(head, master, occurrence, shift)) +
        carried(events.filter { it.exception() }, head, occurrence, shift, user)
    return before to after
}

/** The instant a master begins, 0 for one without a readable `DTSTART`. */
private fun begins(master: EventComponent): Long =
    master.property("DTSTART")?.let { CalendarsMapping.moment(it) / 1000 } ?: 0L

/** The occurrence an override replaces, 0 when its `RECURRENCE-ID` will not read. */
private fun key(override: EventComponent): Long =
    override.property("RECURRENCE-ID")?.let { CalendarsMapping.moment(it) / 1000 } ?: 0L

/** Whether a property lists dates: `RDATE` or `EXDATE`. */
private fun listed(property: EventProperty): Boolean =
    property.name.equals("RDATE", ignoreCase = true) || property.name.equals("EXDATE", ignoreCase = true)

/**
 * The head with the master's listed dates from [from] on, each moved by
 * [shift], in place of any it carries. A rule the form dropped took the
 * dates with it, and gets none back.
 */
private fun relisted(head: EventComponent, master: EventComponent, from: Long, shift: Long): EventComponent {
    if (head.property("RRULE") == null) return head
    val (_, after) = dates(master.properties, from, shift)
    return head.copy(properties = head.properties.filterNot { listed(it) } + after)
}

/**
 * The master's list-valued dates, `RDATE` and `EXDATE`, kept to one side of a
 * cut: the first of the pair keeps those naming instants before [from], the
 * second the rest, each moved by [shift] seconds in the form it had. A
 * property left with no values is dropped.
 */
private fun dates(
    properties: List<EventProperty>,
    from: Long,
    shift: Long,
): Pair<List<EventProperty>, List<EventProperty>> {
    val before = mutableListOf<EventProperty>()
    val after = mutableListOf<EventProperty>()
    for (property in properties) {
        if (!listed(property)) continue
        val early = mutableListOf<String>()
        val late = mutableListOf<EventProperty>()
        for (value in property.value.split(",")) {
            val single = property.copy(value = value.trim())
            if (single.value.isEmpty()) continue
            val seconds = CalendarsMapping.moment(single) / 1000
            if (seconds == 0L || seconds < from) {
                early.add(single.value)
            } else {
                late.add(
                    CalendarsMapping.stamp(
                        property.name,
                        (seconds + shift) * 1000,
                        CalendarsMapping.zone(single),
                        CalendarsMapping.date(single),
                    ),
                )
            }
        }
        if (early.isNotEmpty()) before.add(property.copy(value = early.joinToString(",")))
        if (late.isNotEmpty()) after.add(late.first().copy(value = late.joinToString(",") { it.value }))
    }
    return before to after
}

/**
 * The overrides of occurrences at or after [from], following a master that
 * moved by [shift] seconds: each keeps its own title, length and zones,
 * moves by the same shift, and names its occurrence as the new [head] does.
 */
private fun carried(
    overrides: List<EventComponent>,
    head: EventComponent,
    from: Long,
    shift: Long,
    user: String,
): List<EventComponent> {
    val out = mutableListOf<EventComponent>()
    for (item in overrides) {
        val at = key(item)
        if (at == 0L || at < from) continue
        if (shift == 0L) {
            out.add(item)
            continue
        }
        val own = shifted(draft(item, user), shift)
        val rewritten = component(
            own.copy(recurrence = Recurrence()),
            item,
            recurrence = false,
            occurrence = 0,
            start = own.start,
        )
        out.add(rewritten.copy(properties = rewritten.properties + identifier(head, at + shift)))
    }
    return out
}

/**
 * A `RECURRENCE-ID` naming the occurrence at [occurrence], in the form the
 * [head]'s own `DTSTART` uses so the two describe the same instant.
 */
private fun identifier(head: EventComponent, occurrence: Long): EventProperty {
    val starts = head.property("DTSTART")
    return CalendarsMapping.stamp(
        "RECURRENCE-ID",
        occurrence * 1000,
        CalendarsMapping.zone(starts),
        starts != null && CalendarsMapping.date(starts),
    )
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
fun matches(override: EventComponent, occurrence: Long): Boolean =
    override.property("RECURRENCE-ID")?.let { CalendarsMapping.moment(it) / 1000 } == occurrence

/**
 * One `VEVENT` from the form. [carried] is the component as the server last
 * sent it, whose properties the editor has no field for are kept.
 * [recurrence] says whether this component owns the series' `RRULE`;
 * [occurrence] is non-zero for an override, whose `RECURRENCE-ID` it is.
 * [start] is the component's own start: the occurrence's for an override and
 * the master's for the series. Each end is stamped in its own zone, so
 * `DTSTART` and `DTEND` may carry different `TZID`s.
 */
private fun component(
    form: EventForm,
    carried: EventComponent?,
    recurrence: Boolean,
    occurrence: Long,
    start: Long,
): EventComponent {
    val zone = form.zone.start.ifBlank { CalendarsMapping.UTC }
    val ends = form.zone.finish.ifBlank { zone }
    val finish = start + (form.finish - form.start)
    val properties = mutableListOf<EventProperty>()
    carried?.properties?.filterNot { it.name.uppercase() in MANAGED }?.let(properties::addAll)
    properties.add(property("SUMMARY", form.title.trim()))
    properties.add(CalendarsMapping.stamp("DTSTART", start * 1000, zone, form.allday))
    properties.add(CalendarsMapping.stamp("DTEND", finish * 1000, ends, form.allday))
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
