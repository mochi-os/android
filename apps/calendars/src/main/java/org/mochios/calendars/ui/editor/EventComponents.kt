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
import org.mochios.calendars.model.regional
import org.mochios.calendars.storage.Memory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
 *
 * [reading] is each timed end as its clock reads in its own zone, as it was
 * typed or as the event stores it, which is what is written ([live]).
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
    val reading: Reading = Reading(),
)

/**
 * Each end of a timed event as its clock reads in its own zone, as the user
 * typed it or the event stores it, as the web editor holds a draft. A
 * reading the clocks skip, such as 02:30 on New York's 10 March 2024, has no
 * instant of its own: the end's instant is where the server places it,
 * 01:30 in winter time, but the reading is what is written, which RFC 5545
 * and the server take as the time of day of a series that starts there.
 */
data class Reading(val start: LocalDateTime? = null, val finish: LocalDateTime? = null)

/**
 * [reading] when it is still the end's own: when it places at [instant] in
 * [zone] as the server places it. An end whose instant has since moved
 * without it, as a drag or a move by seconds moves one, reads as its
 * instant does, and null is returned.
 */
fun live(reading: LocalDateTime?, instant: Long, zone: String): LocalDateTime? =
    reading?.takeIf { CalendarsMapping.placed(it, zoneOf(zone)) == instant }

/**
 * [form] with each timed end's reading the one it shows: its own while that
 * is still the end's ([live]), else its instant's, so forms that read alike
 * are alike, however each came by its readings.
 */
fun normal(form: EventForm): EventForm {
    if (form.allday) return form.copy(reading = Reading())
    fun shown(instant: Long, reading: LocalDateTime?, zone: String): LocalDateTime =
        live(reading, instant, zone) ?: Instant.ofEpochSecond(instant).atZone(zoneOf(zone)).toLocalDateTime()
    return form.copy(
        reading = Reading(shown(form.start, form.reading.start, form.zone.start), shown(form.finish, form.reading.finish, form.zone.finish)),
    )
}

/**
 * Each end of [component] as it is written, when that is the clock reading
 * of [zone], the zone the editor opens the end in: its `TZID`'s, UTC's or,
 * for a floating time, the [user]'s. An end written in another zone, such as
 * a `DTEND` in UTC beside a start with a zone, reads as its instant does.
 */
internal fun stored(component: EventComponent, zone: Zone, user: String): Reading {
    fun end(name: String, own: String): LocalDateTime? {
        val property = component.property(name) ?: return null
        return CalendarsMapping.reading(property).takeIf { CalendarsMapping.zone(property, user) == own }
    }
    return Reading(end("DTSTART", zone.start), end("DTEND", zone.finish))
}

/**
 * The zones a component was written in, which the editor opens it in:
 * `DTSTART`'s `TZID` for the start and `DTEND`'s for the finish. A start in
 * UTC keeps UTC, so a series stored in UTC goes on repeating in it unless the
 * user picks another zone; a floating or whole-day start is the [user]'s own
 * zone; and a `DTEND` without a `TZID` follows the start.
 */
fun written(component: EventComponent, user: String): Zone {
    val starts = component.property("DTSTART")
    val universal = starts != null && !CalendarsMapping.date(starts) && starts.value.trim().endsWith("Z")
    val start = starts?.parameter("TZID")?.takeIf { it.isNotBlank() }
        ?: if (universal) CalendarsMapping.UTC else user
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
 * London becomes 10:00 New York, five hours later. A reading the clocks skip
 * or read twice in [to] is placed where the server places it
 * ([CalendarsMapping.placed]). A zone the platform does not know is read as
 * the device's.
 */
fun moved(instant: Long, from: String, to: String): Long {
    if (from == to) return instant
    val local = Instant.ofEpochSecond(instant).atZone(zoneOf(from)).toLocalDateTime()
    return CalendarsMapping.placed(local, zoneOf(to))
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
 * start moved, or that now repeats in another zone, takes its overrides and
 * listed dates along by the same distance, from the clock where it repeated
 * to the one where it repeats ([shifting]), as the series' start moves by as
 * far as the clock moved the occurrence the form was opened on.
 * [Scope.ONE] keeps the master as it stands and replaces just the
 * override for the occurrence the user opened, adding one when there was
 * none. [Scope.FOLLOWING] is two events, which [split] builds; here it reads
 * as the whole series, which is what it is on the series' first occurrence.
 * A floating time in [carried] is read in [user]'s zone. [given] is the
 * reading the listing gives the occurrence the form was opened on where its
 * zone skips it ([Instance.reading]), which the move is measured from.
 */
fun components(
    form: EventForm,
    carried: List<EventComponent>,
    scope: Scope,
    user: String = form.zone.start.ifBlank { CalendarsMapping.UTC },
    given: String? = null,
): List<EventComponent> {
    val events = carried.filter { it.name.equals("VEVENT", ignoreCase = true) }
    val master = events.firstOrNull { !it.exception() }
    val overrides = events.filter { it.exception() }

    if (scope != Scope.ONE || master == null) {
        val landed = landing(form, master, user, given)
        val start = landed.start
        val head = component(landed, master, recurrence = true, occurrence = 0, start = start, user = user)
        val first = master?.let { begins(it, user) } ?: 0L
        val kept = unopened(overrides, form, user)
        if (master == null || first == 0L) return listOf(head) + kept
        val source = repeats(master, user)
        val target = repeats(form, user)
        // Measured between the starts as they are written, so a start
        // written at a time the clocks skip moves from its reading.
        val shift = shifting(
            first,
            start,
            source,
            target,
            user,
            reading(master.property("DTSTART"), first, source, user),
            reading(head.property("DTSTART"), start, target, user),
        )
        if (shift.still) return listOf(head) + kept
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
        reading = if (allday) Reading() else stored(component, zone, user),
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
 * own zone across a clock change, as the web editor moves a draft's days: a
 * reading the clocks skip or read twice is written as it reads and placed
 * where the server places it ([CalendarsMapping.placed]). An all-day form
 * moves by its dates. With [user], as one occurrence alone moves, an end
 * written in UTC moves by that zone's clock instead, the user's, keeping the
 * time of day it is drawn at there across a clock change ([regional]), and
 * stays written in UTC; the web client moves a draft so (clockedDraft).
 */
fun advanced(form: EventForm, days: Long, user: String? = null): EventForm {
    if (days == 0L) return form
    if (form.allday) return shifted(form, days * 86_400)
    fun move(instant: Long, reading: LocalDateTime?, zone: String): LocalDateTime {
        if (user != null && zone.isNotBlank() && regional(zone) == null) {
            val home = zoneOf(user)
            val moved = CalendarsMapping.placed(Instant.ofEpochSecond(instant).atZone(home).toLocalDateTime().plusDays(days), home)
            return Instant.ofEpochSecond(moved).atZone(zoneOf(zone)).toLocalDateTime()
        }
        return (live(reading, instant, zone) ?: Instant.ofEpochSecond(instant).atZone(zoneOf(zone)).toLocalDateTime()).plusDays(days)
    }
    val opens = move(form.start, form.reading.start, form.zone.start)
    val closes = move(form.finish, form.reading.finish, form.zone.finish)
    return form.copy(
        start = CalendarsMapping.placed(opens, zoneOf(form.zone.start)),
        finish = CalendarsMapping.placed(closes, zoneOf(form.zone.finish)),
        reading = Reading(opens, closes),
    )
}

/**
 * The instant the form starts. A timed form's start is one already; an
 * all-day form holds the UTC midnight of its first day, which begins for the
 * [user] at their own midnight, placed where the server places it when the
 * clocks skip or read it twice ([CalendarsMapping.placed]).
 */
fun instant(form: EventForm, user: String): Long {
    if (!form.allday) return form.start
    val day = Instant.ofEpochSecond(form.start).atZone(ZoneOffset.UTC).toLocalDate()
    return CalendarsMapping.placed(day.atStartOfDay(), zoneOf(user))
}

/**
 * The form a copy of a stored event opens on, as a new event of its own.
 * With [Scope.ONE] it is the occurrence at [occurrence] alone: its own
 * override, reminders and all, or the master moved onto it at its reading in
 * the series ([opening]), with the repeat cleared. With any other scope it is
 * the master, its repeat kept as written. Null when the event has no master
 * to copy.
 */
fun copied(carried: List<EventComponent>, occurrence: Long, scope: Scope, user: String, given: String? = null): EventForm? {
    val events = carried.filter { it.name.equals("VEVENT", ignoreCase = true) }
    val master = events.firstOrNull { !it.exception() } ?: return null
    val whole = draft(master, user)
    if (scope != Scope.ONE || occurrence == 0L) return whole
    val override = events.firstOrNull { it.exception() && matches(it, occurrence, user) }
    val own = override?.let { draft(it, user) }
        ?: draft(master, user, occurrence).let { if (it.allday) it else it.copy(reading = opening(master, occurrence, user, given)) }
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
    given: String? = null,
): EventForm {
    val master = carried.firstOrNull { it.name.equals("VEVENT", ignoreCase = true) && !it.exception() }
    val moved = if (scope == Scope.ONE) form else landing(form, master, user, given)
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
 * in, the [user]'s own where it names UTC ([regional]) and the start's
 * where it names none.
 */
fun copied(instance: Instance, user: String, reminder: Int): EventForm {
    val day = instance.date?.let { runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toEpochSecond() }.getOrNull() }
    val start = if (instance.allday && day != null) day else instance.start
    val length = if (instance.allday) {
        maxOf(1L, Math.round((instance.finish - instance.start) / 86_400.0)) * 86_400
    } else {
        (instance.finish - instance.start).coerceAtLeast(0)
    }
    val begins = regional(instance.zone?.start) ?: user
    val ends = instance.zone?.finish?.takeIf { it.isNotBlank() }?.let { regional(it) ?: user } ?: begins
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
    val (before, _) = dates(master.properties, occurrence, Shift(0, ZoneOffset.UTC, ZoneOffset.UTC, user), user)
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
 * from the clock where the series repeated to the one where it now repeats
 * ([shifting]), except the override of the occurrence the form was opened on,
 * which the form replaces. The new rule keeps the old one's end; a `COUNT` is
 * left for the server to shorten by the occurrences the old event keeps. Null
 * when the occurrence is the series' first, which makes the edit one of the
 * whole series. [given] is the reading the listing gives the occurrence
 * where its zone skips it ([occurred]).
 */
fun split(
    carried: List<EventComponent>,
    form: EventForm,
    occurrence: Long,
    user: String,
    given: String? = null,
): Pair<List<EventComponent>, List<EventComponent>>? {
    val before = truncated(carried, occurrence, user) ?: return null
    val events = carried.filter { it.name.equals("VEVENT", ignoreCase = true) }
    val master = events.first { !it.exception() }
    val source = repeats(master, user)
    val target = repeats(form, user)
    val head = component(form, master, recurrence = true, occurrence = 0, start = form.start, user = user)
    // Measured from the occurrence's reading in the series to the new
    // series' start as it is written, as a move of the whole series is.
    val shift = shifting(
        occurrence,
        form.start,
        source,
        target,
        user,
        occurred(master, occurrence, source, user, given),
        reading(head.property("DTSTART"), form.start, target, user),
    )
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
 * The reading [property], a date-time, is written at where it is written on
 * [zone]'s clock (by its `TZID`, or floating in the [user]'s zone) and still
 * names [seconds] there ([live]). Null for a date, one in UTC, one written on
 * another clock, and one whose reading no longer names its instant: each of
 * those reads as its instant does.
 */
private fun reading(property: EventProperty?, seconds: Long, zone: ZoneId, user: String): LocalDateTime? {
    if (property == null || property.value.trim().endsWith("Z")) return null
    if (!Zones.same(CalendarsMapping.zone(property, user), zone.id)) return null
    return live(CalendarsMapping.reading(property), seconds, zone.id)
}

/**
 * The reading a listing gives an occurrence, `yyyyMMdd'T'HHmmss`
 * ([Instance.reading]); null for none.
 */
internal fun stated(value: String?): LocalDateTime? =
    value?.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalDateTime.parse(it.trim(), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")) }.getOrNull()
    }

/**
 * The reading the occurrence of [master]'s series at [occurrence] is written
 * at on [zone]'s clock: the series' start's time of day on the occurrence's
 * day, where the start is written on that clock and the reading names
 * [occurrence]. An occurrence the clocks skip, such as 02:30 on New York's 10
 * March 2024 for a series at 02:30, falls on the day before or after the
 * reading its instant gives, so each is tried. Where the clocks skip a whole
 * day, as Kiritimati's did on 31 December 1994, that day's reading and the
 * next day's name the same instant, and the occurrence is on the day the
 * series' rule or listed dates give ([generates]); on the instant's own day
 * where both or neither are. Null otherwise. The listing gives the reading
 * itself where the zone skips it ([Instance.reading]), [given], which is
 * taken first: it holds where the series cannot, as for a listed date at
 * another time of day or an occurrence a later override moved.
 */
private fun occurred(master: EventComponent, occurrence: Long, zone: ZoneId, user: String, given: String? = null): LocalDateTime? {
    // The reading the listing gives the occurrence where its zone skips it,
    // the server's own word on which it is, while it names the occurrence.
    stated(given)?.takeIf { CalendarsMapping.placed(it, zone) == occurrence }?.let { return it }
    val own = reading(master.property("DTSTART"), begins(master, user), zone, user) ?: return null
    val day = LocalDateTime.ofInstant(Instant.ofEpochSecond(occurrence), zone).toLocalDate()
    val found = listOf(day, day.minusDays(1), day.plusDays(1))
        .map { it.atTime(own.toLocalTime()) }
        .filter { CalendarsMapping.placed(it, zone) == occurrence }
    if (found.size < 2) return found.firstOrNull()
    val listed = { clock: LocalDateTime ->
        master.all("RDATE").any { property ->
            property.value.split(",").any { value -> reading(property.copy(value = value.trim()), occurrence, zone, user) == clock }
        }
    }
    val rule = master.value("RRULE").takeIf { it.isNotBlank() }
    return found.firstOrNull { clock ->
        listed(clock) || (rule != null && generates(rule, own.toLocalDate(), clock.toLocalDate()) == true)
    } ?: found.first()
}

/**
 * Whether the series whose rule is [rule] and whose first day is [first] has
 * an occurrence on [day], both on the clock it repeats on: by its frequency
 * and interval counted from the first day, and by the months, days of the
 * month and weekdays the rule names, or the first day's where it names none
 * (RFC 5545 3.3.10). Null for a rule it does not read: one naming week
 * numbers, days of the year, set positions or times of day. The web client
 * reads a rule the same way.
 */
private fun generates(rule: String, first: LocalDate, day: LocalDate): Boolean? {
    val parts = rule.split(";").mapNotNull { part ->
        part.split("=", limit = 2).takeIf { it.size == 2 }?.let { (name, value) -> name.trim().uppercase() to value.trim().uppercase() }
    }.toMap()
    if (listOf("BYWEEKNO", "BYYEARDAY", "BYSETPOS", "BYHOUR", "BYMINUTE", "BYSECOND").any { it in parts }) return null
    val began = ChronoUnit.DAYS.between(first, day)
    if (began < 0) return false
    fun list(name: String) = parts[name]?.split(",")?.filter { it.isNotBlank() }
    val weekdays = list("BYDAY")?.map { Regex("^([+-]?\\d{1,2})?(SU|MO|TU|WE|TH|FR|SA)$").find(it) ?: return null }
    val months = list("BYMONTH")?.map { it.toIntOrNull() ?: return null }
    val monthdays = list("BYMONTHDAY")?.map { it.toIntOrNull() ?: return null }
    val frequency = parts["FREQ"]
    val interval = (parts["INTERVAL"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
    val weekday = day.dayOfWeek.value % 7
    val length = day.lengthOfMonth()
    if (months != null && day.monthValue !in months) return false
    if (monthdays != null && monthdays.none { it == day.dayOfMonth || (it < 0 && length + it + 1 == day.dayOfMonth) }) return false
    if (weekdays != null) {
        // Which such weekday of the month, or of the year for a yearly rule
        // naming no month, from its start and from its end.
        val yearly = frequency == "YEARLY" && months == null
        val position = if (yearly) day.dayOfYear else day.dayOfMonth
        val count = if (yearly) day.lengthOfYear() else length
        val forward = (position - 1) / 7 + 1
        val backward = -((count - position) / 7 + 1)
        val counted = frequency == "MONTHLY" || frequency == "YEARLY"
        val named = weekdays.any { match ->
            val nth = match.groupValues[1].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            WEEKDAYS.indexOf(match.groupValues[2]) == weekday && (nth == 0 || (counted && (nth == forward || nth == backward)))
        }
        if (!named) return false
    }
    return when (frequency) {
        "DAILY" -> began % interval == 0L
        "WEEKLY" -> {
            // Weeks counted from the week the first day falls in, each
            // beginning on WKST, Monday where the rule names none.
            val opening = WEEKDAYS.indexOf(parts["WKST"] ?: "MO")
            fun back(on: LocalDate) = on.minusDays(((on.dayOfWeek.value % 7 - opening + 7) % 7).toLong())
            val weeks = ChronoUnit.DAYS.between(back(first), back(day)) / 7
            weeks % interval == 0L && (weekdays != null || weekday == first.dayOfWeek.value % 7)
        }
        "MONTHLY" ->
            ((day.year - first.year) * 12 + day.monthValue - first.monthValue) % interval == 0 &&
                (weekdays != null || monthdays != null || day.dayOfMonth == first.dayOfMonth)
        "YEARLY" ->
            (day.year - first.year) % interval == 0 &&
                (weekdays != null || monthdays != null || ((months != null || day.monthValue == first.monthValue) && day.dayOfMonth == first.dayOfMonth))
        else -> null
    }
}

/**
 * Whether a timed form is written at a reading its instants do not read, at
 * either end, such as 02:30 on New York's 10 March 2024, which the server
 * places at 01:30 in winter time.
 */
internal fun skewed(form: EventForm): Boolean =
    !form.allday && normal(form).reading != normal(form.copy(reading = Reading())).reading

/**
 * Where a move that [change] makes of a form, given the occurrence dragged
 * as the editor opens it ([opened]), puts the occurrence of [master]'s
 * series at [occurrence]: the instants of its form at its reading in the
 * series ([opening]) once changed, which is where the series' own move puts
 * it. Only where its instants would place it elsewhere: when the series
 * writes it at a reading its instants do not read, such as 02:30 on New
 * York's 10 March 2024, or, with [every], for every occurrence, as when the
 * occurrence dragged is such a one and the move is measured from its
 * reading. Null otherwise, and for one the change makes all day. The web
 * client places a move's preview the same way (landed).
 */
internal fun landed(
    master: EventComponent,
    occurrence: Long,
    user: String,
    change: (EventForm, EventForm) -> EventForm,
    opened: EventForm,
    every: Boolean = false,
    given: String? = null,
): Pair<Long, Long>? {
    val drawn = draft(master, user, occurrence)
    if (drawn.allday) return null
    val own = drawn.copy(reading = opening(master, occurrence, user, given))
    if (!every && !skewed(own)) return null
    val moved = change(own, opened)
    return if (moved.allday) null else moved.start to moved.finish
}

/**
 * [form] moved by as far as the clock moved the occurrence dragged, as the
 * editor opens it ([opened]), to the reading the drop at [start] names on
 * the form's clock: by the days and time of day between, so one written at
 * a time the clocks skip, such as 02:30 on New York's 10 March 2024, which
 * the server places at 01:30 in winter time, moves from 02:30. Its end
 * follows [length] seconds after its start. The web client moves a draft the
 * same way (droppedDraft).
 */
internal fun dropped(form: EventForm, opened: EventForm, start: Long, length: Long): EventForm {
    val zone = zoneOf(form.zone.start.ifBlank { CalendarsMapping.UTC })
    val read = { seconds: Long -> LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), zone).withSecond(0) }
    val own = { shown: EventForm -> live(shown.reading.start, shown.start, shown.zone.start) ?: read(shown.start) }
    val from = if (Zones.same(opened.zone.start, form.zone.start)) own(opened) else read(opened.start)
    val opens = own(form).plusSeconds(ChronoUnit.SECONDS.between(from, read(start)))
    val begins = CalendarsMapping.placed(opens, zone)
    return form.copy(allday = false, start = begins, finish = begins + length, reading = Reading(opens, null))
}

/**
 * Each end of the occurrence of [master]'s series at [occurrence] as the
 * series writes it ([occurred]), which the editor opens it at: one the clocks
 * skip, such as 02:30 on New York's 10 March 2024 for a series at 02:30,
 * reads 02:30, as the series' first occurrence does, not the 01:30 its
 * instant reads. Its end reads as far on from the series' end as its start
 * does from the series' start. No reading where the series' start is not
 * written on its own clock, and an end it does not place reads as its
 * instant does ([live]). [given] is the reading the listing gives it where
 * its zone skips it, taken first ([occurred]). The web client opens an
 * occurrence the same way (occurrenceDraft).
 */
internal fun opening(master: EventComponent, occurrence: Long, user: String, given: String? = null): Reading {
    val own = stored(master, written(master, user), user)
    val first = own.start ?: return Reading()
    val start = occurred(master, occurrence, repeats(master, user), user, given) ?: return Reading()
    return Reading(start, own.finish?.plusSeconds(ChronoUnit.SECONDS.between(first, start)))
}

/**
 * How a date the series carries moves as the series moves ([shifting]): its
 * reading on [source]'s clock moves on by [distance] seconds and is placed on
 * [target]'s, where the server places it ([CalendarsMapping.placed]).
 */
private class Shift(
    private val distance: Long,
    private val source: ZoneId,
    private val target: ZoneId,
    private val user: String,
) {
    /** Nothing moves: no distance, and one zone under whichever names. */
    val still = distance == 0L && Zones.same(source.id, target.id)

    /** [property]'s reading on [source]'s clock as written, while it names [seconds], else [seconds]'s. */
    private fun base(seconds: Long, property: EventProperty?): LocalDateTime =
        reading(property, seconds, source, user) ?: LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), source)

    /** Where the date at [seconds], written as [property], lands. */
    operator fun invoke(seconds: Long, property: EventProperty? = null): Long =
        if (still) seconds else CalendarsMapping.placed(base(seconds, property).plusSeconds(distance), target)

    /**
     * The reading to write the date at [seconds], written as [property], at
     * once moved, where it is written on [target]'s clock: it may be one the
     * clocks skip. Null to write it as the instant it lands on reads.
     */
    fun clock(seconds: Long, property: EventProperty?): LocalDateTime? {
        if (property == null || CalendarsMapping.date(property) || property.value.trim().endsWith("Z")) return null
        val zone = CalendarsMapping.zone(property, user)
        if (Zones.same(zone, CalendarsMapping.UTC) || !Zones.same(zone, target.id)) return null
        return base(seconds, property).plusSeconds(distance)
    }
}

/**
 * How a date the series carries moves when the series' start moves from
 * [from] to [to], epoch seconds, written at the readings [before] and [after]
 * where given ([reading], [occurred]), and the zone it repeats in from
 * [source] to [target]: the date's reading on [source]'s clock moves by as
 * far as the start's reading moved, from [source]'s clock to [target]'s, and
 * is read back on [target]'s, where the server places it
 * ([CalendarsMapping.placed]). A date across a clock change from the start
 * then keeps its time of day, as the occurrence it names does, rather than
 * landing an hour off as a move by the same seconds would, and a series that
 * changes zone takes each date onto the new zone's clock, whose changes may
 * fall on other days than the old one's. A date written on [source]'s clock
 * moves from its reading as written, so one written at a time the clocks
 * skip, such as 02:30 on New York's 10 March 2024, moves from 02:30 rather
 * than the 01:30 its instant reads, and goes on naming the occurrence it
 * names; one written on [target]'s clock is written at its moved reading
 * ([Shift.clock]). The web client moves them the same way.
 */
private fun shifting(
    from: Long,
    to: Long,
    source: ZoneId,
    target: ZoneId,
    user: String,
    before: LocalDateTime? = null,
    after: LocalDateTime? = null,
): Shift {
    val clock = { seconds: Long, zone: ZoneId -> LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), zone) }
    val distance = ChronoUnit.SECONDS.between(before ?: clock(from, source), after ?: clock(to, target))
    return Shift(distance, source, target, user)
}

/**
 * The [form] moved onto the series' own start, once the occurrence it was
 * opened on moves to the form's start: as far on as the clock moved that
 * occurrence, from the clock where [master]'s series repeated to the one
 * where the form has it repeat ([shifting]), so an occurrence moved across a
 * clock change from its place, such as an override a day after it, or set to
 * a zone whose clocks change on other days, moves the series by the days and
 * time of day it moved, not an hour off. The series' start is read as it is
 * written, so one written at a time the clocks skip, such as 02:30 on New
 * York's 10 March 2024, keeps that reading where it lands, as its end does.
 * The move is measured from the occurrence's reading in the series
 * ([occurred]), which the form opens it at, to the form's own, so an
 * occurrence at 02:30 that day moved to 03:30, or only to another zone, takes
 * the series there and not an hour further. A form opened on no occurrence
 * starts where it says, and a form already on the series' start is as it is,
 * readings and all.
 */
private fun landing(form: EventForm, master: EventComponent?, user: String, given: String? = null): EventForm {
    if (form.occurrence <= 0 || form.series <= 0) return form
    if (master == null) return shifted(form, form.series - form.occurrence)
    val source = repeats(master, user)
    val target = repeats(form, user)
    val clock = { seconds: Long, zone: ZoneId -> LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), zone) }
    val own = stored(master, written(master, user), user)
    val declared = live(own.start, form.series, source.id)
    // From the occurrence's reading in the series, which the form opens it
    // at ([opening]), to the form's own as typed.
    val from = occurred(master, form.occurrence, source, user, given) ?: clock(form.occurrence, source)
    val to = live(form.reading.start, form.start, target.id) ?: clock(form.start, target)
    val distance = ChronoUnit.SECONDS.between(from, to)
    val opens = (declared ?: clock(form.series, source)).plusSeconds(distance)
    val start = CalendarsMapping.placed(opens, target)
    if (start == form.start) return form
    return form.copy(
        start = start,
        finish = start + (form.finish - form.start),
        reading = Reading(opens, own.finish?.plusSeconds(distance)),
    )
}

/**
 * The zone [master]'s series repeats in, as its start names it: its own,
 * the [user]'s for a floating one, and UTC for one in UTC or of whole days,
 * which are held as their UTC midnights.
 */
private fun repeats(master: EventComponent, user: String): ZoneId =
    zoneOf(CalendarsMapping.zone(master.property("DTSTART"), user))

/**
 * The zone the series [form] describes repeats in, read as [repeats] reads a
 * master from the start [component] writes for it: the editor may set another
 * zone in the same save as it moves the series.
 */
private fun repeats(form: EventForm, user: String): ZoneId {
    val zone = form.zone.start.ifBlank { CalendarsMapping.UTC }
    val loose = form.floating.isNotBlank() && zone == form.floating
    return zoneOf(CalendarsMapping.zone(CalendarsMapping.stamp("DTSTART", form.start * 1000, zone, form.allday, loose), user))
}

/**
 * The head with the master's listed dates from [from] on, each moved by
 * [shift], in place of any it carries. A head with neither a rule nor listed
 * dates of its own repeats no longer, and gets none back.
 */
private fun relisted(head: EventComponent, master: EventComponent, from: Long, shift: Shift, user: String): EventComponent {
    if (head.property("RRULE") == null && head.property("RDATE") == null) return head
    val (_, after) = dates(master.properties, from, shift, user)
    return head.copy(properties = head.properties.filterNot { listed(it) } + after)
}

/**
 * The master's list-valued dates, `RDATE` and `EXDATE`, kept to one side of a
 * cut: the first of the pair keeps those naming instants before [from], the
 * second the rest, each moved by [shift] in the form it had: at its moved
 * reading where it is written on the clock the series now repeats on, so one
 * written at a time the clocks skip goes on naming the occurrence it names,
 * else as the instant it lands on reads. A property left with no values is
 * dropped. A floating value is read, and written, as the clock reads in the
 * [user]'s zone.
 */
private fun dates(
    properties: List<EventProperty>,
    from: Long,
    shift: Shift,
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
                late.add(
                    shift.clock(seconds, single)?.let {
                        CalendarsMapping.stamp(property.name, it, CalendarsMapping.zone(single, user), CalendarsMapping.floating(single))
                    } ?: CalendarsMapping.like(property.name, shift(seconds, single) * 1000, single, user),
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
 * moved as [shift] moves a date: each keeps its own title, length and zones,
 * moves by the same shift, its start and its end each, and names its
 * occurrence, moved likewise, as the new [head] does. Each is moved from its
 * reading as written, so one written at a time the clocks skip goes on
 * naming the occurrence it replaces, and is written at its moved reading
 * where it is written on the clock the series now repeats on.
 */
private fun carried(
    overrides: List<EventComponent>,
    head: EventComponent,
    from: Long,
    shift: Shift,
    user: String,
): List<EventComponent> {
    val out = mutableListOf<EventComponent>()
    for (item in overrides) {
        val at = key(item, user)
        if (at == 0L || at < from) continue
        val named = item.property("RECURRENCE-ID")
        val occurrence = shift(at, named)
        val clock = shift.clock(at, named)
        // An override stays as it is while its occurrence does, by instant
        // and by reading: one moved from 01:45 to 02:45 on a day the clocks
        // skip from 02:00 to 03:00 names the same instant, at a new reading.
        if (occurrence == at && (clock == null || clock == CalendarsMapping.reading(named))) {
            out.add(item)
            continue
        }
        val opened = draft(item, user)
        val opens = item.property("DTSTART")
        val closes = item.property("DTEND")
        val own = opened.copy(
            start = shift(opened.start, opens),
            finish = shift(opened.finish, closes),
            reading = Reading(shift.clock(opened.start, opens), shift.clock(opened.finish, closes)),
        )
        val rewritten = component(
            own.copy(recurrence = Recurrence()),
            item,
            recurrence = false,
            occurrence = 0,
            start = own.start,
            user = user,
        )
        out.add(rewritten.copy(properties = rewritten.properties + identifier(head, occurrence, user, clock)))
    }
    return out
}

/**
 * A `RECURRENCE-ID` naming the occurrence at [occurrence], in the form the
 * [head]'s own `DTSTART` uses so the two describe the same instant: at the
 * reading [clock] on the head's clock where given, which may be one the
 * clocks skip, as the occurrence's own start is written.
 */
private fun identifier(head: EventComponent, occurrence: Long, user: String, clock: LocalDateTime? = null): EventProperty {
    val starts = head.property("DTSTART")
    if (clock != null && starts != null && !CalendarsMapping.date(starts) && !starts.value.trim().endsWith("Z")) {
        val zone = CalendarsMapping.zone(starts, user)
        if (!Zones.same(zone, CalendarsMapping.UTC)) {
            return CalendarsMapping.stamp("RECURRENCE-ID", clock, zone, CalendarsMapping.floating(starts))
        }
    }
    return CalendarsMapping.like("RECURRENCE-ID", occurrence * 1000, starts, user)
}

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

/**
 * Where the listing gives a date property's moment: a date-time's own
 * instant, a date at the [user]'s midnight, placed as the server places it.
 */
private fun listing(property: EventProperty?, user: String): Long? {
    if (property == null) return null
    val seconds = CalendarsMapping.moment(property, user) / 1000
    if (seconds == 0L) return null
    if (!CalendarsMapping.date(property)) return seconds
    return CalendarsMapping.placed(Instant.ofEpochSecond(seconds).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(), zoneOf(user))
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
    // Each end as its clock reads, typed or stored, while it is still where
    // the form has it (live), so a time the clocks skip is written as it
    // reads rather than as its instant does; any other as its instant reads.
    val loosely = form.floating.isNotBlank() && ends == form.floating
    val opens = if (form.allday || start != form.start) null else live(form.reading.start, start, zone)
    val closes = if (form.allday || finish != form.finish) null else live(form.reading.finish, finish, ends)
    properties.add(
        opens?.let { CalendarsMapping.stamp("DTSTART", it, zone, loose) }
            ?: CalendarsMapping.stamp("DTSTART", start * 1000, zone, form.allday, loose),
    )
    properties.add(
        closes?.let { CalendarsMapping.stamp("DTEND", it, ends, loosely) }
            ?: CalendarsMapping.stamp("DTEND", finish * 1000, ends, form.allday, loosely),
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
 * gives, each placed where the server places it ([CalendarsMapping.placed]).
 * The web client picks a run of days the same way.
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
    return CalendarsMapping.placed(first.atTime(from / 60, from % 60), zone) to
        CalendarsMapping.placed(last.atTime(to / 60, to % 60), zone)
}
