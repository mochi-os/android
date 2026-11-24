// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.editor

import org.mochios.android.sync.CalendarsMapping
import org.mochios.android.sync.EventComponent
import org.mochios.android.sync.EventProperty
import org.mochios.android.sync.property
import org.mochios.android.util.Zones
import org.mochios.android.util.descriptionText
import org.mochios.calendars.model.Hours
import org.mochios.calendars.model.Instance
import org.mochios.calendars.model.Zone
import org.mochios.calendars.storage.Memory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

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
 * [start] and [finish] are epoch seconds and [reminders] each reminder the
 * editor can say, in minutes before the start. [zone] is the zone each end is written in: a flight is
 * 10:00 Europe/London to 13:00 America/New_York, though most events have the
 * same zone at both ends.
 *
 * [description] is the description as text, which the editor shows and
 * edits; [original] is the description as the event holds it, which may be
 * HTML, as a Google calendar's is. While [description] still reads as its
 * text, the event keeps [original] as it was, markup and all.
 *
 * [occurrence] is the occurrence the user opened, epoch seconds, 0 when the
 * event does not repeat; [series] is the master's own start. The form shows
 * the occurrence, so a change to the whole series moves the master by however
 * far the user moved that occurrence rather than onto its date.
 *
 * [floating] is the zone a floating event, one whose times name no zone, was
 * read in, and blank for any other: an end still in that zone is written
 * floating again, and one the user moved to another zone is pinned there.
 */
data class EventForm(
    val title: String = "",
    val start: Long = 0,
    val finish: Long = 0,
    val allday: Boolean = false,
    val zone: Zone = Zone(),
    val location: String = "",
    /** The event's own colour, its `COLOR`; blank draws it in its calendar's. */
    val colour: String = "",
    /** A web address the event carries, its `URL`. */
    val url: String = "",
    /** Whether the event is only tentative, its `STATUS`. */
    val tentative: Boolean = false,
    val description: String = "",
    val original: String = "",
    val recurrence: Recurrence = Recurrence(),
    val reminders: List<Int> = emptyList(),
    val occurrence: Long = 0,
    val series: Long = 0,
    val floating: String = "",
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
 * The zone a moment of the form is shown and picked in. An all-day day is
 * held as its UTC midnight and is a date, not a moment, so it reads in UTC
 * whatever the user's zone; read in a zone west of UTC it would show the
 * day before. A timed end reads in its own [zone], the device's own where
 * that names no zone it knows.
 */
fun shownIn(allday: Boolean, zone: String): ZoneId =
    if (allday) ZoneOffset.UTC else runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault())

/**
 * The end no earlier than the start as instants: [ends] as it is when it
 * already follows [begins], else moved on by as many whole days as it takes.
 */
fun following(begins: Long, ends: Long): Long {
    if (ends >= begins) return ends
    val days = ((begins - ends + 86_399) / 86_400).coerceAtLeast(1)
    return ends + days * 86_400
}

/**
 * The pair after the start zone is set to [start]: the finish zone follows
 * while the two are one zone, under whichever names, and stops once it has
 * been set apart.
 */
fun follow(zone: Zone, start: String, registry: Zones.Registry = Zones.Platform): Zone =
    Zone(start, if (Zones.same(zone.finish, zone.start, registry)) start else zone.finish)

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
    "SUMMARY", "LOCATION", "COLOR", "URL", "DESCRIPTION", "DTSTART", "DTEND", "DURATION",
    "RRULE", "RDATE", "EXDATE", "RECURRENCE-ID",
)

/**
 * The tree to send for an edit. [carried] is the event as the server last
 * sent it, whose properties the editor has no field for are kept. [user] is
 * the zone an override's end reads in when it names none.
 *
 * [Scope.ALL] rewrites the master and keeps every override but that of the
 * occurrence the form was opened on, which the form replaces; a series whose
 * start moved takes its overrides and listed dates along by the same
 * distance, by the clock where it repeats ([shifting]), as the series' start
 * moves by as far as the clock moved the occurrence the form was opened on.
 * [Scope.ONE] keeps the master as it stands and replaces just the
 * override for the occurrence the user opened, adding one when there was
 * none. [Scope.FOLLOWING] is two events, which [split] builds; here it reads
 * as the whole series, which is what it is on the series' first occurrence.
 * A floating time in [carried] is read in [user]'s zone.
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
        val start = landing(form, master, user)
        val head = component(form, master, recurrence = true, occurrence = 0, start = start, user = user)
        val first = master?.let { begins(it, user) } ?: 0L
        val kept = unopened(overrides, form, user)
        if (first == 0L || start == first) return listOf(head) + kept
        val shift = shifting(first, start, repeats(master!!, user))
        return listOf(relisted(head, master, Long.MIN_VALUE, shift, user)) +
            carried(kept, head, Long.MIN_VALUE, shift, user)
    }
    val own = overrides.firstOrNull { matches(it, form.occurrence, user) }
    // A new override's reminders are the ones the form showed, the series'.
    val replaced = component(
        form,
        own,
        recurrence = false,
        occurrence = form.occurrence,
        start = form.start,
        user = user,
        alarmed = own ?: master,
    )
    return listOf(master) + overrides.filterNot { matches(it, form.occurrence, user) } + replaced
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
    val start = starts?.let { CalendarsMapping.moment(it, user) / 1000 } ?: 0L
    val length = component.property("DTEND")?.let { CalendarsMapping.moment(it, user) / 1000 - start }
        ?: component.value("DURATION").takeIf { it.isNotBlank() }?.let { CalendarsMapping.seconds(it) }
        ?: if (allday) 86_400L else 0L
    val zone = written(component, user)
    val form = EventForm(
        title = component.value("SUMMARY"),
        start = start,
        finish = start + length,
        allday = allday,
        zone = zone,
        location = component.value("LOCATION"),
        colour = component.value("COLOR"),
        url = component.value("URL"),
        tentative = tentative(component),
        description = descriptionText(component.value("DESCRIPTION")),
        original = component.value("DESCRIPTION"),
        recurrence = recurrence(component.value("RRULE"), zone.start, if (allday) 0L else start),
        reminders = alarms(component).mapNotNull(::alarmMinutes).distinct(),
        floating = if (CalendarsMapping.floating(starts)) user else "",
    )
    return if (occurrence > 0 && start != 0L) shifted(form, occurrence - start) else form
}

/** Whether a `VEVENT` is only tentative. */
fun tentative(component: EventComponent): Boolean =
    component.value("STATUS").equals("TENTATIVE", ignoreCase = true)

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
 * override, reminders and all, or the master moved onto it, with the repeat
 * cleared. With any other scope it is the master, its repeat kept as written.
 * Null when the event has no master to copy.
 */
fun copied(carried: List<EventComponent>, occurrence: Long, scope: Scope, user: String): EventForm? {
    val events = carried.filter { it.name.equals("VEVENT", ignoreCase = true) }
    val master = events.firstOrNull { !it.exception() } ?: return null
    val whole = draft(master, user)
    if (scope != Scope.ONE || occurrence == 0L) return whole
    val override = events.firstOrNull { it.exception() && matches(it, occurrence, user) }
    val own = if (override != null) draft(override, user) else draft(master, user, occurrence)
    return own.copy(recurrence = Recurrence())
}

/**
 * The form a copy opens on when the form it is copied from holds edits, as
 * the web editor builds it: [Scope.ONE] is the form as it stands without its
 * repeat, the whole series is the form moved back onto the series' own start,
 * as saving it to the series would ([landing]), its length kept. Either is a
 * new event, of no occurrence. [carried] is the event as the server last sent
 * it, whose master says where the series repeats, and a floating time in it
 * is read in [user]'s zone.
 */
fun duplicate(
    form: EventForm,
    scope: Scope,
    carried: List<EventComponent>,
    user: String = form.zone.start.ifBlank { CalendarsMapping.UTC },
): EventForm {
    val master = carried.firstOrNull { it.name.equals("VEVENT", ignoreCase = true) && !it.exception() }
    val moved = if (scope == Scope.ONE) {
        form
    } else {
        val start = landing(form, master, user)
        form.copy(start = start, finish = start + (form.finish - form.start))
    }
    val recurrence = if (scope == Scope.ONE) Recurrence() else form.recurrence
    return moved.copy(recurrence = recurrence, occurrence = 0, series = 0)
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
    val day = instance.date?.let { runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toEpochSecond() }.getOrNull() }
    val start = if (instance.allday && day != null) day else instance.start
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
        description = descriptionText(instance.description),
        original = instance.description,
        reminders = defaultReminders(reminder),
    )
}

/**
 * The series cut to end just before the occurrence at [occurrence]: the
 * master's rule gains an `UNTIL` there and loses any `COUNT`, and the
 * overrides and listed dates from that occurrence on are dropped. A series
 * that repeats by its listed dates alone, with no rule, loses those from the
 * occurrence on. Null when the occurrence is the series' first, since nothing
 * would be left, or when the event is no series. A floating time is read in
 * the [user]'s zone.
 */
fun truncated(carried: List<EventComponent>, occurrence: Long, user: String = CalendarsMapping.UTC): List<EventComponent>? {
    val events = carried.filter { it.name.equals("VEVENT", ignoreCase = true) }
    val master = events.firstOrNull { !it.exception() } ?: return null
    val starts = master.property("DTSTART") ?: return null
    val rule = master.value("RRULE")
    val first = begins(master, user)
    if ((rule.isBlank() && master.property("RDATE") == null) || first == 0L || occurrence <= first) return null
    // UNTIL is inclusive, and takes the form of DTSTART: the day before for
    // a whole-day series, otherwise the second before, in UTC, or as the
    // clock reads for a floating one, which UNTIL must be as well.
    val allday = CalendarsMapping.date(starts)
    val loose = CalendarsMapping.floating(starts)
    val until = CalendarsMapping.stamp(
        "UNTIL",
        (occurrence - if (allday) 86_400L else 1L) * 1000,
        if (loose) user else CalendarsMapping.UTC,
        allday,
        loose,
    ).value
    val parts = rule.split(";").filterNot {
        it.uppercase().startsWith("UNTIL=") || it.uppercase().startsWith("COUNT=")
    } + "UNTIL=$until"
    val (before, _) = dates(master.properties, occurrence, { seconds -> seconds }, user)
    val properties = master.properties.filterNot { listed(it) }.map {
        if (it.name.equals("RRULE", ignoreCase = true)) it.copy(value = parts.joinToString(";")) else it
    } + before
    val kept = events.filter { it.exception() && key(it, user).let { at -> at == 0L || at < occurrence } }
    return listOf(master.copy(properties = properties)) + kept
}

/**
 * An edit of the occurrence at [occurrence] and every one after it: the
 * series is cut in two. The first of the pair is the old event, ending just
 * before the occurrence; the second is a new event whose master is [form],
 * the series as it now goes on from there, so the form's dates are where
 * this occurrence lands. The old overrides and listed dates from the cut
 * onwards move to the new event, shifted as the form shifted the occurrence,
 * by the clock where the series repeats ([shifting]), except the override of the
 * occurrence the form was opened on, which the form replaces. The new rule
 * keeps the old one's end; a `COUNT` is left for the server to shorten by the
 * occurrences the old event keeps. Null when the occurrence is the series'
 * first, which makes the edit one of the whole series.
 */
fun split(
    carried: List<EventComponent>,
    form: EventForm,
    occurrence: Long,
    user: String,
): Pair<List<EventComponent>, List<EventComponent>>? {
    val before = truncated(carried, occurrence, user) ?: return null
    val events = carried.filter { it.name.equals("VEVENT", ignoreCase = true) }
    val master = events.first { !it.exception() }
    val shift = shifting(occurrence, form.start, repeats(master, user))
    val head = component(form, master, recurrence = true, occurrence = 0, start = form.start, user = user)
    val after = listOf(relisted(head, master, occurrence, shift, user)) +
        carried(unopened(events.filter { it.exception() }, form, user), head, occurrence, shift, user)
    return before to after
}

/**
 * The overrides less the one of the occurrence the editor's form was opened
 * on, which the form replaces when it is saved to the whole series or to it
 * and the ones after it. A drag's form names no occurrence, and a dragged
 * occurrence's override moves along with it.
 */
private fun unopened(overrides: List<EventComponent>, form: EventForm, user: String): List<EventComponent> =
    if (form.occurrence > 0) overrides.filterNot { matches(it, form.occurrence, user) } else overrides

/** The instant a master begins, 0 for one without a readable `DTSTART`. */
private fun begins(master: EventComponent, user: String): Long =
    master.property("DTSTART")?.let { CalendarsMapping.moment(it, user) / 1000 } ?: 0L

/** The occurrence an override replaces, 0 when its `RECURRENCE-ID` will not read. */
private fun key(override: EventComponent, user: String): Long =
    override.property("RECURRENCE-ID")?.let { CalendarsMapping.moment(it, user) / 1000 } ?: 0L

/** Whether a property lists dates: `RDATE` or `EXDATE`. */
private fun listed(property: EventProperty): Boolean =
    property.name.equals("RDATE", ignoreCase = true) || property.name.equals("EXDATE", ignoreCase = true)

/**
 * How a date the series carries moves when the series' start moves from
 * [from] to [to], epoch seconds: by as far as the clock moved where the
 * series repeats, [zone], so a date across a clock change from the start
 * keeps its time of day there, as the occurrence it names does, rather than
 * landing an hour off as a move by the same seconds would.
 */
private fun shifting(from: Long, to: Long, zone: ZoneId): (Long) -> Long {
    val clock = { seconds: Long -> Instant.ofEpochSecond(seconds).atZone(zone) }
    val distance = ChronoUnit.SECONDS.between(clock(from).toLocalDateTime(), clock(to).toLocalDateTime())
    if (distance == 0L) return { seconds -> seconds }
    // In the hour a clock change reads twice, the reading on the same side
    // of the change as the date had.
    return { seconds ->
        val at = clock(seconds)
        ZonedDateTime.ofLocal(at.toLocalDateTime().plusSeconds(distance), zone, at.offset).toEpochSecond()
    }
}

/**
 * Where the series' own start lands, epoch seconds, once the occurrence the
 * [form] was opened on moves to the form's start: as far on as the clock moved
 * that occurrence where [master]'s series repeats ([shifting]), so an
 * occurrence moved across a clock change from its place, such as an override
 * a day after it, moves the series by the days and time of day it moved, not
 * an hour off. A form opened on no occurrence starts where it says.
 */
private fun landing(form: EventForm, master: EventComponent?, user: String): Long = when {
    form.occurrence <= 0 || form.series <= 0 -> form.start
    master != null -> shifting(form.occurrence, form.start, repeats(master, user))(form.series)
    else -> form.series + (form.start - form.occurrence)
}

/**
 * The zone [master]'s series repeats in, as its start names it: its own,
 * the [user]'s for a floating one, and UTC for one in UTC or of whole days,
 * which are held as their UTC midnights.
 */
private fun repeats(master: EventComponent, user: String): ZoneId =
    zoneOf(CalendarsMapping.zone(master.property("DTSTART"), user))

/**
 * The head with the master's listed dates from [from] on, each moved by
 * [shift], in place of any it carries. A head with neither a rule nor listed
 * dates of its own repeats no longer, and gets none back.
 */
private fun relisted(head: EventComponent, master: EventComponent, from: Long, shift: (Long) -> Long, user: String): EventComponent {
    if (head.property("RRULE") == null && head.property("RDATE") == null) return head
    val (_, after) = dates(master.properties, from, shift, user)
    return head.copy(properties = head.properties.filterNot { listed(it) } + after)
}

/**
 * The master's list-valued dates, `RDATE` and `EXDATE`, kept to one side of a
 * cut: the first of the pair keeps those naming instants before [from], the
 * second the rest, each moved by [shift] in the form it had. A property left
 * with no values is dropped. A floating value is read, and written, as the
 * clock reads in the [user]'s zone.
 */
private fun dates(
    properties: List<EventProperty>,
    from: Long,
    shift: (Long) -> Long,
    user: String,
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
            val seconds = CalendarsMapping.moment(single, user) / 1000
            if (seconds == 0L || seconds < from) {
                early.add(single.value)
            } else {
                late.add(CalendarsMapping.like(property.name, shift(seconds) * 1000, single, user))
            }
        }
        if (early.isNotEmpty()) before.add(property.copy(value = early.joinToString(",")))
        if (late.isNotEmpty()) after.add(late.first().copy(value = late.joinToString(",") { it.value }))
    }
    return before to after
}

/**
 * The overrides of occurrences at or after [from], following a master that
 * moved as [shift] moves an instant: each keeps its own title, length and
 * zones, moves by the same shift, its start and its end each, and names its
 * occurrence, moved likewise, as the new [head] does.
 */
private fun carried(
    overrides: List<EventComponent>,
    head: EventComponent,
    from: Long,
    shift: (Long) -> Long,
    user: String,
): List<EventComponent> {
    val out = mutableListOf<EventComponent>()
    for (item in overrides) {
        val at = key(item, user)
        if (at == 0L || at < from) continue
        if (shift(at) == at) {
            out.add(item)
            continue
        }
        val opened = draft(item, user)
        val own = opened.copy(start = shift(opened.start), finish = shift(opened.finish))
        val rewritten = component(
            own.copy(recurrence = Recurrence()),
            item,
            recurrence = false,
            occurrence = 0,
            start = own.start,
            user = user,
        )
        out.add(rewritten.copy(properties = rewritten.properties + identifier(head, shift(at), user)))
    }
    return out
}

/**
 * A `RECURRENCE-ID` naming the occurrence at [occurrence], in the form the
 * [head]'s own `DTSTART` uses so the two describe the same instant.
 */
private fun identifier(head: EventComponent, occurrence: Long, user: String): EventProperty =
    CalendarsMapping.like("RECURRENCE-ID", occurrence * 1000, head.property("DTSTART"), user)

/**
 * The tree with one occurrence excluded: its override dropped and its start
 * added to the master's `EXDATE`, which is how an occurrence is taken out of
 * a series. The `EXDATE` takes the form of the series' start, a floating one
 * as the clock reads in the [user]'s zone.
 */
fun excluded(carried: List<EventComponent>, occurrence: Long, user: String = CalendarsMapping.UTC): List<EventComponent> {
    val events = carried.filter { it.name.equals("VEVENT", ignoreCase = true) }
    val master = events.firstOrNull { !it.exception() } ?: return carried
    val exdate = CalendarsMapping.like("EXDATE", occurrence * 1000, master.property("DTSTART"), user)
    val kept = events.filter { it.exception() && !matches(it, occurrence, user) }
    return listOf(master.copy(properties = master.properties + exdate)) + kept
}

/**
 * One occurrence of a series as an event of its own: the form over the
 * occurrence's own override or, failing one, the master, with nothing of the
 * series on it.
 */
fun single(form: EventForm, carried: List<EventComponent>, user: String = CalendarsMapping.UTC): List<EventComponent> {
    val events = carried.filter { it.name.equals("VEVENT", ignoreCase = true) }
    val own = events.firstOrNull { it.exception() && matches(it, form.occurrence, user) }
        ?: events.firstOrNull { !it.exception() }
    return listOf(component(form, own, recurrence = false, occurrence = 0, start = form.start, user = user))
}

/**
 * One occurrence of a series moved to another calendar: [create] makes it an
 * event of its own there, then [exclude] takes it out of the series. The copy
 * is taken back if the series cannot be written, so the occurrence is never
 * left in both calendars.
 */
suspend fun <T> moveOccurrence(
    create: suspend () -> T,
    exclude: suspend () -> Unit,
    delete: suspend (T) -> Unit,
): T {
    val created = create()
    try {
        exclude()
    } catch (e: Exception) {
        runCatching { delete(created) }
        throw e
    }
    return created
}

/**
 * Whether an override replaces the occurrence starting at [occurrence], its
 * `RECURRENCE-ID` read as the tree names one, a floating one in the [user]'s
 * zone.
 */
fun matches(override: EventComponent, occurrence: Long, user: String = CalendarsMapping.UTC): Boolean =
    override.property("RECURRENCE-ID")?.let { CalendarsMapping.moment(it, user) / 1000 } == occurrence

/**
 * The occurrence the listing gives at [moment], as [carried], the event's
 * tree, names it: the same instant in a timed series, and in an all-day one,
 * whose days the listing gives at the [user]'s own midnight and the tree as
 * their dates, that day's UTC midnight.
 */
fun named(carried: List<EventComponent>, moment: Long, user: String): Long {
    val starts = carried.firstOrNull { it.name.equals("VEVENT", ignoreCase = true) && !it.exception() }
        ?.property("DTSTART")
    if (moment <= 0 || starts == null || !CalendarsMapping.date(starts)) return moment
    return Instant.ofEpochSecond(moment).atZone(zoneOf(user)).toLocalDate().atStartOfDay(ZoneOffset.UTC).toEpochSecond()
}

/**
 * Where the listing would give the occurrence that the override of
 * [carried] listed at [start], its own start, replaces: a core that does not
 * name it lists an override only where it now falls. [start] itself when no
 * override starts there.
 */
fun original(carried: List<EventComponent>, start: Long, user: String): Long {
    val override = carried.firstOrNull {
        it.name.equals("VEVENT", ignoreCase = true) && it.exception() && listing(it.property("DTSTART"), user) == start
    } ?: return start
    return listing(override.property("RECURRENCE-ID"), user) ?: start
}

/** Where the listing gives a date property's moment: a date-time's own instant, a date at the [user]'s midnight. */
private fun listing(property: EventProperty?, user: String): Long? {
    if (property == null) return null
    val seconds = CalendarsMapping.moment(property, user) / 1000
    if (seconds == 0L) return null
    if (!CalendarsMapping.date(property)) return seconds
    return Instant.ofEpochSecond(seconds).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zoneOf(user)).toEpochSecond()
}

/**
 * One `VEVENT` from the form. [carried] is the component as the server last
 * sent it, whose properties the editor has no field for are kept.
 * [recurrence] says whether this component owns the series' `RRULE`;
 * [occurrence] is non-zero for an override, whose `RECURRENCE-ID` it is.
 * [start] is the component's own start: the occurrence's for an override and
 * the master's for the series. Each end is stamped in its own zone, so
 * `DTSTART` and `DTEND` may carry different `TZID`s, and an end still in the
 * zone a floating event was read in is written floating. [alarmed] holds the
 * alarms the form's reminders were read from, [carried]'s own unless this is
 * a new override, whose are its series'.
 */
private fun component(
    form: EventForm,
    carried: EventComponent?,
    recurrence: Boolean,
    occurrence: Long,
    start: Long,
    user: String,
    alarmed: EventComponent? = carried,
): EventComponent {
    val zone = form.zone.start.ifBlank { CalendarsMapping.UTC }
    val ends = form.zone.finish.ifBlank { zone }
    val loose = form.floating.isNotBlank() && zone == form.floating
    val finish = start + (form.finish - form.start)
    // A description edited here leaves behind the rich copy another client
    // kept beside it, which would go on showing the old text there.
    val edited = form.description != descriptionText(form.original)
    val properties = mutableListOf<EventProperty>()
    // Tentative is the one status the editor sets. Turned off, it goes, and
    // a confirmed or cancelled event keeps its status.
    carried?.properties?.filterNot {
        it.name.uppercase() in MANAGED ||
            (edited && it.name.equals(CalendarsMapping.ALTERNATIVE, ignoreCase = true)) ||
            (it.name.equals("STATUS", ignoreCase = true) && (form.tentative || it.value.equals("TENTATIVE", ignoreCase = true)))
    }?.let(properties::addAll)
    properties.add(property("SUMMARY", form.title.trim()))
    properties.add(CalendarsMapping.stamp("DTSTART", start * 1000, zone, form.allday, loose))
    properties.add(
        CalendarsMapping.stamp("DTEND", finish * 1000, ends, form.allday, form.floating.isNotBlank() && ends == form.floating),
    )
    if (form.location.isNotBlank()) properties.add(property("LOCATION", form.location.trim()))
    if (form.colour.isNotBlank()) properties.add(property("COLOR", form.colour.trim()))
    if (form.url.isNotBlank()) properties.add(property("URL", form.url.trim()))
    if (form.tentative) properties.add(property("STATUS", "TENTATIVE"))
    val description = if (edited) form.description.trim() else form.original
    if (description.isNotBlank()) properties.add(property("DESCRIPTION", description))
    if (recurrence) {
        val rule = form.recurrence.rule(zone, form.allday, loose)
        if (rule != null) properties.add(property("RRULE", rule))
        // A series of listed dates alone, with no rule, keeps them, as the
        // repeat setting cannot say them; an RRULE the editor dropped takes
        // its exclusions and dates with it.
        if (rule != null || carried?.property("RRULE") == null) {
            carried?.all("EXDATE")?.let(properties::addAll)
            carried?.all("RDATE")?.let(properties::addAll)
        }
    }
    if (occurrence > 0) {
        val named = carried?.property("RECURRENCE-ID")
        val allday = named?.let { CalendarsMapping.date(it) } ?: form.allday
        // The occurrence is named as its series names it: a floating
        // series' as the clock reads in the zone it was read in.
        val floats = named?.let { CalendarsMapping.floating(it) } ?: form.floating.isNotBlank()
        properties.add(
            if (floats) CalendarsMapping.stamp("RECURRENCE-ID", occurrence * 1000, form.floating.ifBlank { user }, allday, true)
            else CalendarsMapping.stamp("RECURRENCE-ID", occurrence * 1000, zone, allday),
        )
    }
    // The alarms the reminder setting cannot say are kept as they are, and so
    // is each one it can whose reminder the form still has; a reminder no
    // alarm holds is a new one. The form shows alarms at the same time as
    // one reminder, so each it keeps keeps every one of them.
    val nested = carried?.components?.filterNot { it.name.equals("VALARM", ignoreCase = true) }.orEmpty()
    val held = alarmed?.let(::alarms).orEmpty()
    val counts = held.mapNotNull(CalendarsMapping::minutes)
    val asked = form.reminders.distinct().flatMap { minutes -> List(maxOf(1, counts.count { it == minutes })) { minutes } }
    return EventComponent("VEVENT", properties, nested + CalendarsMapping.alarms(held, asked, form.title.trim()))
}

/**
 * Where a new event with no time of its own starts on [day]: at the next
 * whole hour when [day] is [today], at the start of the working [hours] on
 * any other day, and at tomorrow's working hours once today has no whole hour
 * left. The web client starts one the same way.
 */
fun defaultStart(day: LocalDate, today: LocalDate, now: LocalTime, hours: Hours): LocalDateTime {
    val working = hours.start.coerceIn(0, 23)
    if (day != today) return day.atTime(working, 0)
    val next = now.hour + 1
    if (next > 23) return today.plusDays(1).atTime(working, 0)
    return today.atTime(next, 0)
}

/**
 * The times an all-day event starting on [day] holds beneath it, which
 * turning all day off gives it, in minutes past midnight: where a new event
 * on that day would start, and [duration] minutes later on its last day, held
 * to that day's last minute. Its days never move, so today with no whole hour
 * left starts at the working [hours]. The web client gives the same.
 */
fun beneath(day: LocalDate, today: LocalDate, now: LocalTime, hours: Hours, duration: Int): Pair<Int, Int> {
    val start = defaultStart(day, today, now, hours)
    val from = start.hour * 60 + start.minute
    return from to minOf(from + duration, 24 * 60 - 1)
}

/**
 * The span a run of days picked on the grid, [first] to [last], opens the
 * editor with, epoch seconds in [zone]: the times [beneath] the first day,
 * [now] being the user's clock, from the first day to the last. The editor
 * shows it all day across the days; the times are what turning all day off
 * gives. The web client picks a run of days the same way.
 */
fun picked(
    first: LocalDate,
    last: LocalDate,
    hours: Hours,
    duration: Int,
    zone: ZoneId,
    now: LocalDateTime = LocalDateTime.now(zone),
): Pair<Long, Long> {
    val (from, to) = beneath(first, now.toLocalDate(), now.toLocalTime(), hours, duration)
    return first.atTime(from / 60, from % 60).atZone(zone).toEpochSecond() to
        last.atTime(to / 60, to % 60).atZone(zone).toEpochSecond()
}
