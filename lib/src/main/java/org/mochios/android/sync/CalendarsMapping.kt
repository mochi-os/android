// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import android.provider.CalendarContract.Events
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

    // ---- components -> provider ----

    /**
     * The provider rows for a server event, master first. [calendar] is the
     * provider row id of the calendar the event belongs to.
     */
    fun rows(event: SyncedEvent, calendar: Long): List<EventRow> {
        val events = event.components.filter { it.name.equals("VEVENT", ignoreCase = true) }
        val master = events.firstOrNull { !it.exception() } ?: return emptyList()
        val out = mutableListOf(row(master, event, calendar, null))
        for (override in events.filter { it.exception() }) {
            out.add(row(override, event, calendar, master))
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
    ): EventRow {
        val values = mutableMapOf<String, Any?>()
        values[Events.CALENDAR_ID] = calendar
        values[Events.SYNC_DATA1] = event.etag
        values[Events.SYNC_DATA2] = event.id
        values[Events.SYNC_DATA3] = event.slug
        values[Events.DIRTY] = 0
        values[Events.TITLE] = component.value("SUMMARY")
        values[Events.EVENT_LOCATION] = component.value("LOCATION")
        values[Events.DESCRIPTION] = component.value("DESCRIPTION")
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
        val zone = zone(start)
        values[Events.EVENT_TIMEZONE] = if (allday) UTC else zone
        val began = start?.let { moment(it) } ?: 0L
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
                finish != null -> span(began, moment(finish), allday)
                allday -> "P1D"
                else -> "PT0S"
            }
        } else {
            values[Events.DURATION] = null
            values[Events.DTEND] = when {
                finish != null -> moment(finish)
                duration.isNotBlank() -> began + seconds(duration) * 1000L
                allday -> began + 86_400_000L
                else -> began
            }
            val end = component.property("DTEND")
            values[Events.EVENT_END_TIMEZONE] = if (allday || end == null) null else zone(end)
        }

        values[Events.RRULE] = component.value("RRULE").takeIf { it.isNotBlank() }
        values[Events.RDATE] = dates(component.all("RDATE"), allday)
        values[Events.EXDATE] = dates(component.all("EXDATE"), allday)

        if (master != null) {
            val recurrence = component.property("RECURRENCE-ID")
            val original = recurrence?.let { moment(it) } ?: 0L
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
            .mapNotNull { alarm -> alarm.property("TRIGGER")?.let { minutes(it) } }

    /**
     * A `TRIGGER` as minutes before the start, which is all the provider
     * holds. A trigger relative to the end, or an absolute one, has no column
     * and is left to the server, which sends the notification itself.
     */
    private fun minutes(trigger: EventProperty): Int? {
        if (trigger.parameter("RELATED").equals("END", ignoreCase = true)) return null
        if (trigger.parameter("VALUE").equals("DATE-TIME", ignoreCase = true)) return null
        val value = trigger.value.trim()
        if (!value.startsWith("-P") && !value.startsWith("P")) return null
        val total = seconds(value.removePrefix("-"))
        if (total < 0) return null
        val signed = if (value.startsWith("-")) total else -total
        return (signed / 60).toInt()
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
     */
    fun components(rows: List<EventRow>, carried: List<EventComponent> = emptyList()): List<EventComponent> {
        val master = rows.firstOrNull { it.values[Events.ORIGINAL_SYNC_ID] == null } ?: return emptyList()
        val overrides = rows.filter { it !== master }
        val previous = carried.filter { it.name.equals("VEVENT", ignoreCase = true) }
        val kept = previous.firstOrNull { !it.exception() }

        val excluded = overrides.filter { it.deleted }.mapNotNull { it.values[Events.ORIGINAL_INSTANCE_TIME] as? Long }
        val out = mutableListOf(component(master, kept, excluded))
        for (override in overrides.filterNot { it.deleted }) {
            val original = override.values[Events.ORIGINAL_INSTANCE_TIME] as? Long ?: continue
            val before = previous.firstOrNull {
                it.exception() && it.property("RECURRENCE-ID")?.let { property -> moment(property) } == original
            }
            out.add(component(override, before, emptyList()))
        }
        return out
    }

    /**
     * One component from one row. [carried] is the same component as the
     * server last sent it, if any; its unmanaged properties and its
     * components other than `VALARM` are kept, since the phone has nowhere to
     * hold them and dropping them would lose them on the first edit.
     */
    private fun component(row: EventRow, carried: EventComponent?, excluded: List<Long>): EventComponent {
        val values = row.values
        val allday = number(values[Events.ALL_DAY]) == 1L
        val zone = (values[Events.EVENT_TIMEZONE] as? String)?.takeIf { it.isNotBlank() } ?: UTC
        val began = number(values[Events.DTSTART])

        val properties = mutableListOf<EventProperty>()
        carried?.properties?.filterNot { it.name.uppercase() in MANAGED }?.let(properties::addAll)

        text(values[Events.TITLE])?.let { properties.add(property("SUMMARY", it)) }
        text(values[Events.EVENT_LOCATION])?.let { properties.add(property("LOCATION", it)) }
        text(values[Events.DESCRIPTION])?.let { properties.add(property("DESCRIPTION", it)) }
        label(values[Events.STATUS])?.let { properties.add(property("STATUS", it)) }
        if (number(values[Events.AVAILABILITY]) == Events.AVAILABILITY_FREE.toLong()) {
            properties.add(property("TRANSP", "TRANSPARENT"))
        }

        properties.add(stamp("DTSTART", began, zone, allday))
        val finish = values[Events.DTEND]
        val duration = text(values[Events.DURATION])
        when {
            finish != null -> properties.add(
                stamp("DTEND", number(finish), (values[Events.EVENT_END_TIMEZONE] as? String) ?: zone, allday),
            )
            duration != null -> properties.add(property("DURATION", duration))
        }

        text(values[Events.RRULE])?.let { properties.add(property("RRULE", it)) }
        for (value in split(values[Events.RDATE])) properties.add(stamp("RDATE", value, zone, allday))
        val exdates = split(values[Events.EXDATE]) + excluded
        for (value in exdates.distinct()) properties.add(stamp("EXDATE", value, zone, allday))

        val original = values[Events.ORIGINAL_INSTANCE_TIME] as? Long
        if (original != null) {
            val originally = number(values[Events.ORIGINAL_ALL_DAY]) == 1L
            properties.add(stamp("RECURRENCE-ID", original, zone, originally))
        }

        // VALARMs are rebuilt from the Reminders rows; every other nested
        // component the server holds is carried through.
        val nested = carried?.components?.filterNot { it.name.equals("VALARM", ignoreCase = true) }.orEmpty()
        val alarms = row.reminders.map { alarm(it, text(values[Events.TITLE]).orEmpty()) }
        return EventComponent("VEVENT", properties, nested + alarms)
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
     * stores; a floating one is read in the zone its `TZID` names, else UTC.
     */
    fun moment(property: EventProperty): Long {
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
                LocalDateTime.parse(value, STAMP).atZone(zone(property).let(::zoneOf)).toInstant().toEpochMilli()
            }
        } catch (_: DateTimeParseException) {
            0
        }
    }

    /** Whether the property carries a date rather than a date-time. */
    fun date(property: EventProperty): Boolean =
        property.parameter("VALUE").equals("DATE", ignoreCase = true) ||
            (property.value.length == 8 && property.value.all { it.isDigit() })

    /** The zone a property names, UTC when it names none. */
    fun zone(property: EventProperty?): String {
        if (property == null) return UTC
        property.parameter("TZID")?.let { return it }
        return UTC
    }

    private fun zoneOf(name: String): ZoneId = try {
        ZoneId.of(name)
    } catch (_: Exception) {
        ZoneOffset.UTC
    }

    /** A date or date-time property for an epoch millisecond. */
    fun stamp(name: String, moment: Long, zone: String, allday: Boolean): EventProperty {
        if (allday) {
            val day = Instant.ofEpochMilli(moment).atZone(ZoneOffset.UTC).toLocalDate()
            return property(name, DATE.format(day), "VALUE", "DATE")
        }
        if (zone.isBlank() || zone == UTC) {
            val universal = Instant.ofEpochMilli(moment).atZone(ZoneOffset.UTC)
            return property(name, STAMP.format(universal) + "Z")
        }
        val local = ZonedDateTime.ofInstant(Instant.ofEpochMilli(moment), zoneOf(zone))
        return property(name, STAMP.format(local), "TZID", zone)
    }

    /**
     * The provider's comma-separated list of date-times for a run of `RDATE`
     * or `EXDATE` properties, each of which may itself carry several values.
     * Written in UTC, which every value parses to and which needs no zone
     * prefix on the row.
     */
    private fun dates(properties: List<EventProperty>, allday: Boolean): String? {
        val out = mutableListOf<String>()
        for (property in properties) {
            for (value in property.value.split(",")) {
                val single = property.copy(value = value.trim())
                if (single.value.isEmpty()) continue
                out.add(listed(moment(single), allday))
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

    private fun label(value: Any?): String? = when (number(value)) {
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
