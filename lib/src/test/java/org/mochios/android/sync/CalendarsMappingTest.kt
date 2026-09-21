// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import android.provider.CalendarContract.Events
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The calendar mapping in both directions: what the server sends becomes the
 * provider's rows, and what the provider holds becomes the tree the server
 * stores. The epoch values are worked out with `java.time` rather than by the
 * mapping, so a parser that drifts is caught rather than confirmed.
 */
class CalendarsMappingTest {

    private val LONDON = "Europe/London"

    /** 2026-09-22 10:00 in London, which is British Summer Time. */
    private val TEN = 1_790_067_600_000L
    private val ELEVEN = 1_790_071_200_000L

    /** 2026-09-29 10:00 in London, the week after. */
    private val NEXT = 1_790_672_400_000L

    /** 2026-09-25 and 2026-09-27, UTC midnight, as an all-day row is stored. */
    private val FRIDAY = 1_790_294_400_000L
    private val SUNDAY = 1_790_467_200_000L

    private fun timed(
        rrule: String? = null,
        alarm: Int? = null,
        extra: List<EventProperty> = emptyList(),
    ) = EventComponent(
        name = "VEVENT",
        properties = listOfNotNull(
            property("UID", "uid-1@mochi"),
            property("SUMMARY", "Stand-up"),
            property("LOCATION", "The kitchen"),
            property("DESCRIPTION", "Every Tuesday"),
            property("DTSTART", "20260922T100000", "TZID", LONDON),
            property("DTEND", "20260922T110000", "TZID", LONDON),
            rrule?.let { property("RRULE", it) },
        ) + extra,
        components = alarm?.let { listOf(CalendarsMapping.alarm(it, "Stand-up")) }.orEmpty(),
    )

    private fun allday() = EventComponent(
        name = "VEVENT",
        properties = listOf(
            property("UID", "uid-2@mochi"),
            property("SUMMARY", "Conference"),
            property("DTSTART", "20260925", "VALUE", "DATE"),
            property("DTEND", "20260927", "VALUE", "DATE"),
        ),
    )

    private fun event(vararg components: EventComponent) =
        SyncedEvent("event-1", "calendar-1", "etag-1", components.toList(), "slug-1")

    // ---- components -> provider ----

    @Test
    fun `a timed event keeps its zone and its ends`() {
        val row = CalendarsMapping.rows(event(timed()), 7).single()
        assertEquals(7L, row.values[Events.CALENDAR_ID])
        assertEquals("event-1", row.values[Events._SYNC_ID])
        assertEquals("etag-1", row.values[Events.SYNC_DATA1])
        assertEquals("event-1", row.values[Events.SYNC_DATA2])
        assertEquals("slug-1", row.values[Events.SYNC_DATA3])
        assertEquals("Stand-up", row.values[Events.TITLE])
        assertEquals("The kitchen", row.values[Events.EVENT_LOCATION])
        assertEquals("Every Tuesday", row.values[Events.DESCRIPTION])
        assertEquals(0, row.values[Events.ALL_DAY])
        assertEquals(LONDON, row.values[Events.EVENT_TIMEZONE])
        assertEquals(TEN, row.values[Events.DTSTART])
        assertEquals(ELEVEN, row.values[Events.DTEND])
        assertNull(row.values[Events.DURATION])
        assertNull(row.values[Events.ORIGINAL_SYNC_ID])
    }

    @Test
    fun `an all-day event is stored at UTC midnight`() {
        val row = CalendarsMapping.rows(event(allday()), 7).single()
        assertEquals(1, row.values[Events.ALL_DAY])
        assertEquals(CalendarsMapping.UTC, row.values[Events.EVENT_TIMEZONE])
        assertEquals(FRIDAY, row.values[Events.DTSTART])
        assertEquals(SUNDAY, row.values[Events.DTEND])
    }

    @Test
    fun `a recurring event travels as a duration, which the provider insists on`() {
        val row = CalendarsMapping.rows(event(timed(rrule = "FREQ=WEEKLY;BYDAY=TU")), 7).single()
        assertEquals("FREQ=WEEKLY;BYDAY=TU", row.values[Events.RRULE])
        assertNull(row.values[Events.DTEND])
        assertEquals("PT3600S", row.values[Events.DURATION])
    }

    @Test
    fun `an override becomes its own row pointing at the occurrence it replaces`() {
        val override = EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("UID", "uid-1@mochi"),
                property("SUMMARY", "Stand-up, moved"),
                property("RECURRENCE-ID", "20260929T100000", "TZID", LONDON),
                property("DTSTART", "20260929T140000", "TZID", LONDON),
                property("DTEND", "20260929T150000", "TZID", LONDON),
            ),
        )
        val rows = CalendarsMapping.rows(event(timed(rrule = "FREQ=WEEKLY"), override), 7)
        assertEquals(2, rows.size)
        val second = rows[1]
        assertEquals("event-1", second.values[Events.ORIGINAL_SYNC_ID])
        assertEquals(NEXT, second.values[Events.ORIGINAL_INSTANCE_TIME])
        assertEquals(CalendarsMapping.name("event-1", NEXT), second.values[Events._SYNC_ID])
        assertEquals("Stand-up, moved", second.values[Events.TITLE])
        assertEquals(0, second.values[Events.ORIGINAL_ALL_DAY])
    }

    @Test
    fun `a VALARM becomes the minutes the provider counts back`() {
        val row = CalendarsMapping.rows(event(timed(alarm = 15)), 7).single()
        assertEquals(listOf(15), row.reminders)
    }

    @Test
    fun `an alarm after the start is a negative count, and one at the time is zero`() {
        assertEquals("-PT15M", CalendarsMapping.trigger(15))
        assertEquals("PT0S", CalendarsMapping.trigger(0))
        assertEquals("PT10M", CalendarsMapping.trigger(-10))
    }

    @Test
    fun `exclusions travel as a list the provider parses`() {
        val component = timed(rrule = "FREQ=WEEKLY").let {
            it.copy(properties = it.properties + property("EXDATE", "20260929T100000", "TZID", LONDON))
        }
        val row = CalendarsMapping.rows(event(component), 7).single()
        assertEquals("20260929T090000Z", row.values[Events.EXDATE])
    }

    // ---- provider -> components ----

    @Test
    fun `a timed row comes back as the properties it came from`() {
        val row = CalendarsMapping.rows(event(timed(alarm = 15)), 7).single()
        val back = CalendarsMapping.components(listOf(row), listOf(timed(alarm = 15))).single()
        assertEquals("Stand-up", back.value("SUMMARY"))
        assertEquals("The kitchen", back.value("LOCATION"))
        assertEquals("Every Tuesday", back.value("DESCRIPTION"))
        assertEquals(TEN, CalendarsMapping.moment(back.property("DTSTART")!!))
        assertEquals(ELEVEN, CalendarsMapping.moment(back.property("DTEND")!!))
        assertEquals(LONDON, back.property("DTSTART")!!.parameter("TZID"))
        val alarm = back.components.single { it.name == "VALARM" }
        assertEquals("-PT15M", alarm.value("TRIGGER"))
    }

    @Test
    fun `an all-day row comes back as dates, not date-times`() {
        val row = CalendarsMapping.rows(event(allday()), 7).single()
        val back = CalendarsMapping.components(listOf(row), listOf(allday())).single()
        val start = back.property("DTSTART")!!
        assertTrue(CalendarsMapping.date(start))
        assertEquals("20260925", start.value)
        assertEquals("20260927", back.property("DTEND")!!.value)
    }

    @Test
    fun `properties the phone has no column for survive an upload`() {
        val carried = timed(extra = listOf(property("ORGANIZER", "mailto:a@b.c"), property("CLASS", "PRIVATE")))
        val row = CalendarsMapping.rows(event(carried), 7).single()
        val back = CalendarsMapping.components(listOf(row), listOf(carried)).single()
        assertEquals("mailto:a@b.c", back.value("ORGANIZER"))
        assertEquals("PRIVATE", back.value("CLASS"))
        assertEquals("uid-1@mochi", back.value("UID"))
    }

    @Test
    fun `an override comes back beside its master, with its recurrence id`() {
        val master = timed(rrule = "FREQ=WEEKLY")
        val override = EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("SUMMARY", "Stand-up, moved"),
                property("RECURRENCE-ID", "20260929T100000", "TZID", LONDON),
                property("DTSTART", "20260929T140000", "TZID", LONDON),
                property("DTEND", "20260929T150000", "TZID", LONDON),
            ),
        )
        val rows = CalendarsMapping.rows(event(master, override), 7)
        val back = CalendarsMapping.components(rows, listOf(master, override))
        assertEquals(2, back.size)
        assertEquals("FREQ=WEEKLY", back[0].value("RRULE"))
        assertNotNull(back[1].property("RECURRENCE-ID"))
        assertEquals(NEXT, CalendarsMapping.moment(back[1].property("RECURRENCE-ID")!!))
    }

    @Test
    fun `an occurrence the phone cancelled becomes an exclusion on the master`() {
        val master = timed(rrule = "FREQ=WEEKLY")
        val override = EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("SUMMARY", "Stand-up"),
                property("RECURRENCE-ID", "20260929T100000", "TZID", LONDON),
                property("DTSTART", "20260929T100000", "TZID", LONDON),
            ),
        )
        val rows = CalendarsMapping.rows(event(master, override), 7)
        val cancelled = listOf(rows[0], EventRow(rows[1].values, rows[1].reminders, deleted = true))
        val back = CalendarsMapping.components(cancelled, listOf(master, override))
        assertEquals(1, back.size)
        // Written in the master's own zone, which is what the rest of the
        // event is in; the moment is what has to match the occurrence.
        assertEquals(NEXT, CalendarsMapping.moment(back[0].all("EXDATE").single()))
    }

    @Test
    fun `a duration reads as the seconds it names`() {
        assertEquals(3_600L, CalendarsMapping.seconds("PT1H"))
        assertEquals(900L, CalendarsMapping.seconds("PT15M"))
        assertEquals(86_400L, CalendarsMapping.seconds("P1D"))
        assertEquals(604_800L, CalendarsMapping.seconds("P1W"))
        assertEquals(0L, CalendarsMapping.seconds("nonsense"))
    }

    @Test
    fun `a UTC date-time needs no zone and reads back the same`() {
        val stamped = CalendarsMapping.stamp("DTSTART", TEN, CalendarsMapping.UTC, allday = false)
        assertEquals("20260922T090000Z", stamped.value)
        assertEquals(TEN, CalendarsMapping.moment(stamped))
    }
}
