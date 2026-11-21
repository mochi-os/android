// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import android.provider.CalendarContract.Events
import org.mochios.android.util.descriptionText
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * The mapping between an event's component tree and the `Events` rows of the
 * calendar provider, in both directions.
 *
 * One server event is one row when it happens once, and a master row plus one
 * row per override when it recurs. Every row of an event carries the event's
 * id in `SYNC_DATA2` and its etag in `SYNC_DATA1`, so an upload gathers the
 * rows back into the one tree the server stores; an override also carries the
 * master's `_SYNC_ID` in `ORIGINAL_SYNC_ID` and its original start in
 * `ORIGINAL_INSTANCE_TIME`, which is what the phone's calendar app reads.
 *
 * Properties the mapping has no column for survive because an upload replaces
 * only the properties it produces and carries the rest of the master's tree
 * through untouched.
 *
 * Column names are the framework's constants, so the rows this builds are the
 * ones the provider stores, and a row read back from the provider maps with
 * the same keys.
 */
object CalendarsMapping {

    /** The properties the phone holds; every other one is carried through. */
    val MANAGED = setOf(
        "SUMMARY", "LOCATION", "DESCRIPTION", "STATUS", "TRANSP",
        "DTSTART", "DTEND", "DURATION", "RRULE", "RDATE", "EXDATE", "RECURRENCE-ID",
    )

    /** The columns a download writes and an upload reads. */
    val COLUMNS = arrayOf(
        Events._ID,
        Events.CALENDAR_ID,
        Events._SYNC_ID,
        Events.SYNC_DATA1,
        Events.SYNC_DATA2,
        Events.SYNC_DATA3,
        Events.TITLE,
        Events.EVENT_LOCATION,
        Events.DESCRIPTION,
        Events.STATUS,
        Events.AVAILABILITY,
        Events.DTSTART,
        Events.DTEND,
        Events.DURATION,
        Events.ALL_DAY,
        Events.EVENT_TIMEZONE,
        Events.EVENT_END_TIMEZONE,
        Events.RRULE,
        Events.RDATE,
        Events.EXDATE,
        Events.ORIGINAL_SYNC_ID,
        Events.ORIGINAL_INSTANCE_TIME,
        Events.ORIGINAL_ALL_DAY,
        Events.DIRTY,
        Events.DELETED,
    )

    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /** What the provider means by "no timezone", and what an all-day row carries. */
    const val UTC = "UTC"

    /**
     * The property another client keeps a rich copy of the description in,
     * which Thunderbird and Outlook show in preference to `DESCRIPTION`.
     */
    const val ALTERNATIVE = "X-ALT-DESC"

    // ---- components -> provider ----

    /**
     * The provider rows for a server event, master first. [calendar] is the
     * provider row id of the calendar the event belongs to. A floating time,
     * one that names no zone, is read in [floating], the phone's own zone,
     * which the row then carries as its zone.
     */
    fun rows(event: SyncedEvent, calendar: Long, floating: String = UTC): List<EventRow> {
        val events = event.components.filter { it.name.equals("VEVENT", ignoreCase = true) }
        val master = events.firstOrNull { !it.exception() } ?: return emptyList()
        val out = mutableListOf(row(master, event, calendar, null, floating))
        for (override in events.filter { it.exception() }) {
            out.add(row(override, event, calendar, master, floating))
        }
        return out
    }

    /**
     * One row. [master] is null for the event's own row and the master
     * component for an override, whose original start the provider indexes by.
     */
    private fun row(
        component: EventComponent,
        event: SyncedEvent,
        calendar: Long,
        master: EventComponent?,
        floating: String,
    ): EventRow {
        val values = mutableMapOf<String, Any?>()
        values[Events.CALENDAR_ID] = calendar
        values[Events.SYNC_DATA1] = event.etag
        values[Events.SYNC_DATA2] = event.id
        values[Events.SYNC_DATA3] = event.slug
        values[Events.DIRTY] = 0
        values[Events.TITLE] = component.value("SUMMARY")
        values[Events.EVENT_LOCATION] = component.value("LOCATION")
        // The phone's calendar app shows a description as plain text, so one
        // written as HTML, as a Google calendar writes it, goes in as its text.
        values[Events.DESCRIPTION] = descriptionText(component.value("DESCRIPTION"))
        values[Events.STATUS] = status(component.value("STATUS"))
        values[Events.AVAILABILITY] =
            if (component.value("TRANSP").equals("TRANSPARENT", ignoreCase = true)) {
                Events.AVAILABILITY_FREE
            } else {
                Events.AVAILABILITY_BUSY
            }

        val start = component.property("DTSTART")
        val allday = start != null && date(start)
        values[Events.ALL_DAY] = if (allday) 1 else 0
        val zone = zone(start, floating)
        values[Events.EVENT_TIMEZONE] = if (allday) UTC else zone
        val began = start?.let { moment(it, floating) } ?: 0L
        values[Events.DTSTART] = began

        // The provider refuses DTEND on a recurring event and wants its span
        // as a DURATION; a single event takes either and DTEND is exact.
        val recurring = component.property("RRULE") != null || component.property("RDATE") != null
        val finish = component.property("DTEND")
        val duration = component.value("DURATION")
        if (recurring) {
            values[Events.DTEND] = null
            values[Events.DURATION] = when {
                duration.isNotBlank() -> duration
                finish != null -> span(began, moment(finish, floating), allday)
                allday -> "P1D"
                else -> "PT0S"
            }
        } else {
            values[Events.DURATION] = null
            values[Events.DTEND] = when {
                finish != null -> moment(finish, floating)
                duration.isNotBlank() -> began + seconds(duration) * 1000L
                allday -> began + 86_400_000L
                else -> began
            }
            val end = component.property("DTEND")
            values[Events.EVENT_END_TIMEZONE] = if (allday || end == null) null else zone(end, floating)
        }

        values[Events.RRULE] = component.value("RRULE").takeIf { it.isNotBlank() }
        values[Events.RDATE] = dates(component.all("RDATE"), allday, floating)
        values[Events.EXDATE] = dates(component.all("EXDATE"), allday, floating)

        if (master != null) {
            val recurrence = component.property("RECURRENCE-ID")
            val original = recurrence?.let { moment(it, floating) } ?: 0L
            values[Events._SYNC_ID] = name(event.id, original)
            values[Events.ORIGINAL_SYNC_ID] = event.id
            values[Events.ORIGINAL_INSTANCE_TIME] = original
            values[Events.ORIGINAL_ALL_DAY] =
                if (master.property("DTSTART")?.let { date(it) } == true) 1 else 0
        } else {
            values[Events._SYNC_ID] = event.id
            values[Events.ORIGINAL_SYNC_ID] = null
            values[Events.ORIGINAL_INSTANCE_TIME] = null
            values[Events.ORIGINAL_ALL_DAY] = null
        }
        return EventRow(values, alarms(component))
    }

    /** The `_SYNC_ID` of an override: the event's id and the occurrence it replaces. */
    fun name(event: String, original: Long): String = "$event#$original"

    /** The minutes before the start each `VALARM` fires, the ones the phone can show. */
    private fun alarms(component: EventComponent): List<Int> =
        component.components
            .filter { it.name.equals("VALARM", ignoreCase = true) }
            .mapNotNull { minutes(it) }

    /**
     * How many minutes before the start an alarm fires, which is all the
     * provider holds and all a reminder can say. An alarm relative to the
     * end, at a fixed time, after the start or with no trigger is null: it
     * has no column, and is left to the server, which sends the notification
     * itself.
     */
    fun minutes(alarm: EventComponent): Int? {
        val trigger = alarm.property("TRIGGER") ?: return null
        if (trigger.parameter("RELATED").equals("END", ignoreCase = true)) return null
        if (trigger.parameter("VALUE").equals("DATE-TIME", ignoreCase = true)) return null
        val value = trigger.value.trim()
        if (!value.startsWith("-P") && !value.startsWith("P")) return null
        val total = seconds(value.removePrefix("-"))
        if (total < 0) return null
        val signed = if (value.startsWith("-")) total else -total
        return (signed / 60).toInt().takeIf { it >= 0 }
    }

    /**
     * The `VALARM`s an event holds once its reminders are [minutes], each how
     * long before the start one fires, over the [carried] alarms the server
     * holds. An alarm a reminder cannot say ([minutes] of it is null) is kept
     * as it is, and one with no trigger, which fires at nothing, goes. Each
     * one a reminder can say is paired with a reminder at its minutes and
     * kept whole - its action, description and whatever another client put
     * on it - or dropped once no reminder is left for it; a reminder no alarm
     * pairs with becomes a new one, which [summary] describes.
     */
    fun alarms(carried: List<EventComponent>, minutes: List<Int>, summary: String): List<EventComponent> {
        val left = minutes.toMutableList()
        val out = mutableListOf<EventComponent>()
        for (alarm in carried) {
            if (alarm.property("TRIGGER") == null) continue
            val at = minutes(alarm)
            if (at == null || left.remove(at)) out.add(alarm)
        }
        return out + left.map { alarm(it, summary) }
    }

    // ---- provider -> components ----

    /**
     * The component tree the phone's rows express, master first. [carried] is
     * the master's tree as the server last sent it, whose properties outside
     * [MANAGED] — the `UID`, an organiser, a class, anything a desktop client
     * put there — are kept.
     *
     * A row the phone marked deleted contributes an `EXDATE` to the master
     * rather than a component, which is how the phone's calendar app says one
     * occurrence of a recurring event is off.
     *
     * A floating event, whose times name no zone, was read in [floating], the
     * phone's own zone, and stays floating while its row is still in that
     * zone; a zone picked on the phone pins it there.
     */
    fun components(
        rows: List<EventRow>,
        carried: List<EventComponent> = emptyList(),
        floating: String = UTC,
    ): List<EventComponent> {
        val master = rows.firstOrNull { it.values[Events.ORIGINAL_SYNC_ID] == null } ?: return emptyList()
        val overrides = rows.filter { it !== master }
        val previous = carried.filter { it.name.equals("VEVENT", ignoreCase = true) }
        val kept = previous.firstOrNull { !it.exception() }
        // An override names its occurrence as the series' start does.
        val series = floating(kept?.property("DTSTART"))

        val excluded = overrides.filter { it.deleted }.mapNotNull { it.values[Events.ORIGINAL_INSTANCE_TIME] as? Long }
        val out = mutableListOf(component(master, kept, excluded, floating, series))
        for (override in overrides.filterNot { it.deleted }) {
            val original = override.values[Events.ORIGINAL_INSTANCE_TIME] as? Long ?: continue
            val before = previous.firstOrNull {
                it.exception() && it.property("RECURRENCE-ID")?.let { property -> moment(property, floating) } == original
            }
            out.add(component(override, before, emptyList(), floating, series))
        }
        return out
    }

    /**
     * One component from one row. [carried] is the same component as the
     * server last sent it, if any; its unmanaged properties and its
     * components other than `VALARM` are kept, since the phone has nowhere to
     * hold them and dropping them would lose them on the first edit. Its
     * times stay floating where [carried]'s were and the row is still in the
     * [floating] zone they were read in; [series] says the series' own start
     * floats, which an override's `RECURRENCE-ID` follows, and a new
     * override's times.
     */
    private fun component(
        row: EventRow,
        carried: EventComponent?,
        excluded: List<Long>,
        floating: String,
        series: Boolean,
    ): EventComponent {
        val values = row.values
        val allday = number(values[Events.ALL_DAY]) == 1L
        val zone = (values[Events.EVENT_TIMEZONE] as? String)?.takeIf { it.isNotBlank() } ?: UTC
        val began = number(values[Events.DTSTART])
        // A new override of a floating series floats as the series does.
        val loose = zone == floating && (carried?.property("DTSTART")?.let { floating(it) } ?: series)

        // A description edited on the phone leaves behind the rich copy
        // another client kept beside it, which would go on showing the old
        // text there.
        val described = text(values[Events.DESCRIPTION])
        val edited = described.orEmpty() != descriptionText(carried?.value("DESCRIPTION").orEmpty())
        val properties = mutableListOf<EventProperty>()
        carried?.properties?.filterNot {
            it.name.uppercase() in MANAGED || (edited && it.name.equals(ALTERNATIVE, ignoreCase = true))
        }?.let(properties::addAll)

        text(values[Events.TITLE])?.let { properties.add(property("SUMMARY", it)) }
        text(values[Events.EVENT_LOCATION])?.let { properties.add(property("LOCATION", it)) }
        described?.let { properties.add(description(it, carried)) }
        label(values[Events.STATUS])?.let { properties.add(property("STATUS", it)) }
        if (number(values[Events.AVAILABILITY]) == Events.AVAILABILITY_FREE.toLong()) {
            properties.add(property("TRANSP", "TRANSPARENT"))
        }

        properties.add(stamp("DTSTART", began, zone, allday, loose))
        val finish = values[Events.DTEND]
        val duration = text(values[Events.DURATION])
        when {
            finish != null -> {
                val ends = (values[Events.EVENT_END_TIMEZONE] as? String) ?: zone
                properties.add(stamp("DTEND", number(finish), ends, allday, loose && ends == floating))
            }
            duration != null -> properties.add(property("DURATION", standard(duration)))
        }

        text(values[Events.RRULE])?.let { properties.add(property("RRULE", it)) }
        for (value in split(values[Events.RDATE])) properties.add(stamp("RDATE", value, zone, allday, loose))
        val exdates = split(values[Events.EXDATE]) + excluded
        for (value in exdates.distinct()) properties.add(stamp("EXDATE", value, zone, allday, loose))

        val original = values[Events.ORIGINAL_INSTANCE_TIME] as? Long
        if (original != null) {
            val originally = number(values[Events.ORIGINAL_ALL_DAY]) == 1L
            properties.add(
                if (series) stamp("RECURRENCE-ID", original, floating, originally, true)
                else stamp("RECURRENCE-ID", original, zone, originally),
            )
        }

        // The alarms the server holds are paired with the phone's reminders:
        // those the phone cannot hold, and those whose reminder it still
        // has, go back as they were. Every other nested component the server
        // holds is carried through.
        val nested = carried?.components.orEmpty()
        val alarms = alarms(
            nested.filter { it.name.equals("VALARM", ignoreCase = true) },
            row.reminders,
            text(values[Events.TITLE]).orEmpty(),
        )
        val others = nested.filterNot { it.name.equals("VALARM", ignoreCase = true) }
        return EventComponent("VEVENT", properties, others + alarms)
    }

    /**
     * The description an upload writes for the phone's [text]. A description
     * the server holds as HTML reaches the phone as its text, so while that
     * text is unchanged the event keeps its HTML, and an edit to its time or
     * title on the phone does not strip the markup from the event.
     */
    private fun description(text: String, carried: EventComponent?): EventProperty {
        val original = carried?.property("DESCRIPTION")
        return if (original != null && descriptionText(original.value) == text) original else property("DESCRIPTION", text)
    }

    /** A `VALARM` that fires [minutes] before the start, as the editor builds one. */
    fun alarm(minutes: Int, summary: String): EventComponent = EventComponent(
        name = "VALARM",
        properties = listOf(
            property("ACTION", "DISPLAY"),
            property("DESCRIPTION", summary),
            property("TRIGGER", trigger(minutes)),
        ),
    )

    /** `-PT15M` for fifteen minutes before, `PT0S` at the time, `PT10M` after. */
    fun trigger(minutes: Int): String = when {
        minutes == 0 -> "PT0S"
        minutes > 0 -> "-PT${minutes}M"
        else -> "PT${-minutes}M"
    }

    // ---- times ----

    /**
     * The epoch millisecond a date or date-time property names. An all-day
     * value is the UTC midnight of its date, which is what the provider
     * stores; a date-time is read in the zone its `TZID` names, and a
     * floating one, which names none, in [floating]: the viewer's own zone,
     * as the server lists it.
     */
    fun moment(property: EventProperty, floating: String = UTC): Long {
        val value = property.value.trim()
        if (value.isEmpty()) return 0
        if (date(property)) {
            return try {
                LocalDate.parse(value.take(8), DATE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                0
            }
        }
        return try {
            if (value.endsWith("Z")) {
                LocalDateTime.parse(value.dropLast(1), STAMP).toInstant(ZoneOffset.UTC).toEpochMilli()
            } else {
                LocalDateTime.parse(value, STAMP).atZone(zone(property, floating).let(::zoneOf)).toInstant().toEpochMilli()
            }
        } catch (_: DateTimeParseException) {
            0
        }
    }

    /** Whether the property carries a date rather than a date-time. */
    fun date(property: EventProperty): Boolean =
        property.parameter("VALUE").equals("DATE", ignoreCase = true) ||
            (property.value.length == 8 && property.value.all { it.isDigit() })

    /**
     * Whether the property is a floating date-time: one that names no zone
     * and is not in UTC, so it reads the same on the clock wherever it is
     * read.
     */
    fun floating(property: EventProperty?): Boolean =
        property != null && property.value.isNotBlank() && !date(property) &&
            property.parameter("TZID").isNullOrBlank() && !property.value.trim().endsWith("Z")

    /** The zone a property names: [floating] for a floating date-time, UTC when it names none. */
    fun zone(property: EventProperty?, floating: String = UTC): String {
        if (property == null) return UTC
        property.parameter("TZID")?.takeIf { it.isNotBlank() }?.let { return it }
        return if (floating(property)) floating else UTC
    }

    private fun zoneOf(name: String): ZoneId = try {
        ZoneId.of(name)
    } catch (_: Exception) {
        ZoneOffset.UTC
    }

    /**
     * A date or date-time property for an epoch millisecond, in [zone]; with
     * [floating] set, it is written as the clock reads in [zone] but names no
     * zone, so it stays floating.
     */
    fun stamp(name: String, moment: Long, zone: String, allday: Boolean, floating: Boolean = false): EventProperty {
        if (allday) {
            val day = Instant.ofEpochMilli(moment).atZone(ZoneOffset.UTC).toLocalDate()
            return property(name, DATE.format(day), "VALUE", "DATE")
        }
        if (floating) return property(name, STAMP.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(moment), zoneOf(zone))))
        if (zone.isBlank() || zone == UTC) {
            val universal = Instant.ofEpochMilli(moment).atZone(ZoneOffset.UTC)
            return property(name, STAMP.format(universal) + "Z")
        }
        val local = ZonedDateTime.ofInstant(Instant.ofEpochMilli(moment), zoneOf(zone))
        return property(name, STAMP.format(local), "TZID", zone)
    }

    /**
     * A date or date-time property for an epoch millisecond, in the form
     * [sample] takes: a date, a time in its zone, one in UTC, or a floating
     * one, written as the clock reads in [floating], the zone floating times
     * are read in. With no [sample] it is in UTC.
     */
    fun like(name: String, moment: Long, sample: EventProperty?, floating: String = UTC): EventProperty {
        val loose = floating(sample)
        return stamp(name, moment, if (loose) floating else zone(sample), sample != null && date(sample), loose)
    }

    /**
     * The provider's comma-separated list of date-times for a run of `RDATE`
     * or `EXDATE` properties, each of which may itself carry several values.
     * Written in UTC, which every value parses to and which needs no zone
     * prefix on the row; a floating value is read in [floating].
     */
    private fun dates(properties: List<EventProperty>, allday: Boolean, floating: String): String? {
        val out = mutableListOf<String>()
        for (property in properties) {
            for (value in property.value.split(",")) {
                val single = property.copy(value = value.trim())
                if (single.value.isEmpty()) continue
                out.add(listed(moment(single, floating), allday))
            }
        }
        return out.takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    /** One value of a provider date list, in UTC so it needs no zone prefix. */
    private fun listed(moment: Long, allday: Boolean): String =
        if (allday) {
            DATE.format(Instant.ofEpochMilli(moment).atZone(ZoneOffset.UTC).toLocalDate())
        } else {
            STAMP.format(Instant.ofEpochMilli(moment).atZone(ZoneOffset.UTC)) + "Z"
        }

    /**
     * The epoch milliseconds a provider date list names. A list may carry a
     * `TZID=zone;` prefix, which the phone's calendar app writes, and its
     * values may be dates rather than date-times.
     */
    private fun split(value: Any?): List<Long> {
        val raw = text(value) ?: return emptyList()
        var zone = UTC
        var body = raw
        if (raw.startsWith("TZID=", ignoreCase = true)) {
            val separator = raw.indexOf(';')
            if (separator > 0) {
                zone = raw.substring(5, separator)
                body = raw.substring(separator + 1)
            }
        }
        return body.split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { moment(property("RDATE", it, "TZID", zone)) }
    }

    /** Seconds in an iCalendar duration, 0 when it will not parse. */
    fun seconds(duration: String): Long {
        val value = duration.trim().removePrefix("+")
        val negative = value.startsWith("-")
        val body = value.removePrefix("-")
        if (!body.startsWith("P")) return 0
        var total = 0L
        var number = StringBuilder()
        var time = false
        for (character in body.drop(1)) {
            when {
                character.isDigit() -> number.append(character)
                character == 'T' -> time = true
                else -> {
                    val count = number.toString().toLongOrNull() ?: return 0
                    number = StringBuilder()
                    total += when (character) {
                        'W' -> count * 604_800
                        'D' -> count * 86_400
                        'H' -> count * 3_600
                        'M' -> if (time) count * 60 else return 0
                        'S' -> count
                        else -> return 0
                    }
                }
            }
        }
        return if (negative) -total else total
    }

    /**
     * A duration in the form RFC 5545 writes it, which is all the server
     * reads. The provider can hold one without the T that comes before a
     * count of hours, minutes or seconds - "P3600S" - as phone calendar apps
     * write them, so such a value gains its T. An M is minutes, since a
     * duration has no months. Days, weeks and a value already in form pass
     * through unchanged.
     */
    private fun standard(duration: String): String {
        val match = Regex("^([+-]?)P((?:\\d+[WD])*)((?:\\d+[HMS])+)$", RegexOption.IGNORE_CASE)
            .find(duration.trim()) ?: return duration
        val (sign, days, time) = match.destructured
        return "${sign}P${days}T$time".uppercase()
    }

    /** The span between two moments as a duration the provider parses. */
    private fun span(start: Long, finish: Long, allday: Boolean): String {
        val seconds = ((finish - start) / 1000).coerceAtLeast(0)
        if (allday) return "P${(seconds / 86_400).coerceAtLeast(1)}D"
        return "PT${seconds}S"
    }

    // ---- small conversions ----

    private fun status(value: String): Int? = when (value.uppercase()) {
        "TENTATIVE" -> Events.STATUS_TENTATIVE
        "CONFIRMED" -> Events.STATUS_CONFIRMED
        "CANCELLED", "CANCELED" -> Events.STATUS_CANCELED
        else -> null
    }

    // A row with no status writes none. Read as a number it would be 0,
    // which is STATUS_TENTATIVE, and every event a phone app made without a
    // status would go up as tentative.
    private fun label(value: Any?): String? = if (value == null) null else when (number(value)) {
        Events.STATUS_TENTATIVE.toLong() -> "TENTATIVE"
        Events.STATUS_CONFIRMED.toLong() -> "CONFIRMED"
        Events.STATUS_CANCELED.toLong() -> "CANCELLED"
        else -> null
    }

    private fun text(value: Any?): String? = (value as? String)?.takeIf { it.isNotBlank() }

    private fun number(value: Any?): Long = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is String -> value.toLongOrNull() ?: 0
        else -> 0
    }
}
