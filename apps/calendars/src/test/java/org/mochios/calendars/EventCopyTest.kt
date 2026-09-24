// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.android.sync.CalendarsMapping
import org.mochios.android.sync.EventComponent
import org.mochios.android.sync.property
import org.mochios.calendars.model.Instance
import org.mochios.calendars.model.Zone
import org.mochios.calendars.ui.editor.Frequency
import org.mochios.calendars.ui.editor.Scope
import org.mochios.calendars.ui.editor.components
import org.mochios.calendars.ui.editor.copied
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * The form a copy opens on: one occurrence as its own event, the whole
 * series as a new one, and an occurrence with no stored event to load.
 */
class EventCopyTest {

    private val LONDON = "Europe/London"
    private val SYDNEY = "Australia/Sydney"

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0, zone: String = LONDON): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of(zone)).toEpochSecond()

    /** A daily stand-up from the 16th, 09:00 to 10:00 London. */
    private val FIRST = at(2026, 9, 16, 9)
    private val SECOND = at(2026, 9, 17, 9)
    private val FIFTH = at(2026, 9, 20, 9)

    private fun master(rule: String = "FREQ=DAILY") = EventComponent(
        name = "VEVENT",
        properties = listOf(
            property("UID", "uid-1@mochi"),
            property("SUMMARY", "Standup"),
            property("LOCATION", "Room 1"),
            property("DESCRIPTION", "Notes"),
            property("DTSTART", "20260916T090000", "TZID", LONDON),
            property("DTEND", "20260916T100000", "TZID", LONDON),
            property("RRULE", rule),
        ),
        components = listOf(CalendarsMapping.alarm(15, "Standup")),
    )

    /** An override of the 20th, moved to 14:00 to 15:00 and renamed, with no reminder of its own. */
    private fun later() = EventComponent(
        name = "VEVENT",
        properties = listOf(
            property("UID", "uid-1@mochi"),
            property("SUMMARY", "Later"),
            property("RECURRENCE-ID", "20260920T090000", "TZID", LONDON),
            property("DTSTART", "20260920T140000", "TZID", LONDON),
            property("DTEND", "20260920T150000", "TZID", LONDON),
        ),
    )

    @Test
    fun `a copy of one occurrence without an override is the master moved onto it, with no repeat`() {
        val form = copied(listOf(master(), later()), SECOND, Scope.ONE, LONDON)!!
        assertEquals("Standup", form.title)
        assertEquals(SECOND, form.start)
        assertEquals(SECOND + 3_600, form.finish)
        assertEquals("Room 1", form.location)
        assertEquals("Notes", form.description)
        assertEquals(Zone(LONDON, LONDON), form.zone)
        assertEquals(15, form.reminder)
        assertEquals(Frequency.NEVER, form.recurrence.frequency)
        assertNull(form.recurrence.rule)

        // What the editor then sends is one fresh event: no rule, no
        // occurrence named, and none of the original's identity.
        val sent = components(form, emptyList(), Scope.ALL)
        assertEquals(1, sent.size)
        assertNull(sent[0].property("RRULE"))
        assertNull(sent[0].property("RECURRENCE-ID"))
        assertNull(sent[0].property("UID"))
        assertEquals("20260917T090000", sent[0].value("DTSTART"))
    }

    @Test
    fun `a copy of one overridden occurrence is the override, sounding the master's reminder`() {
        val form = copied(listOf(master(), later()), FIFTH, Scope.ONE, LONDON)!!
        assertEquals("Later", form.title)
        assertEquals(at(2026, 9, 20, 14), form.start)
        assertEquals(at(2026, 9, 20, 15), form.finish)
        assertEquals(15, form.reminder)
        assertEquals(Frequency.NEVER, form.recurrence.frequency)
        assertNull(form.recurrence.rule)
    }

    @Test
    fun `a copy of all events is the master on its own dates with its rule kept as written`() {
        val rule = "FREQ=MONTHLY;BYDAY=2TU"
        val form = copied(listOf(master(rule), later()), FIFTH, Scope.ALL, LONDON)!!
        assertEquals("Standup", form.title)
        assertEquals(FIRST, form.start)
        assertEquals(FIRST + 3_600, form.finish)
        // The rule is kept as read, and the settings say they cannot say all of it.
        assertEquals(rule, form.recurrence.rule)
        assertEquals(Frequency.MONTHLY, form.recurrence.frequency)
        assertFalse(form.recurrence.expressible)
        assertEquals(15, form.reminder)
        assertEquals(rule, components(form, emptyList(), Scope.ALL).single().value("RRULE"))
    }

    @Test
    fun `an event with no master has nothing to copy`() {
        assertNull(copied(listOf(later()), FIFTH, Scope.ALL, LONDON))
    }

    @Test
    fun `a read-only timed occurrence copies from the listing, in the zones it was written in`() {
        val instance = Instance(
            event = "birthday-1",
            readonly = true,
            summary = "Flight",
            location = "LHR",
            description = "BA 117",
            start = at(2026, 9, 16, 10),
            finish = at(2026, 9, 16, 15),
            zone = Zone(LONDON, "America/New_York"),
        )
        val form = copied(instance, SYDNEY, 30)
        assertEquals("Flight", form.title)
        assertEquals(at(2026, 9, 16, 10), form.start)
        assertEquals(at(2026, 9, 16, 15), form.finish)
        assertFalse(form.allday)
        assertEquals("LHR", form.location)
        assertEquals("BA 117", form.description)
        assertEquals(Zone(LONDON, "America/New_York"), form.zone)
        assertEquals(30, form.reminder)
        assertEquals(Frequency.NEVER, form.recurrence.frequency)
    }

    @Test
    fun `a read-only occurrence naming no zones reads in the user's own, the finish following the start`() {
        val bare = Instance(summary = "Call", start = FIRST, finish = FIRST + 1_800)
        assertEquals(Zone(SYDNEY, SYDNEY), copied(bare, SYDNEY, 15).zone)
        val blank = bare.copy(zone = Zone("", ""))
        assertEquals(Zone(SYDNEY, SYDNEY), copied(blank, SYDNEY, 15).zone)
        val half = bare.copy(zone = Zone(LONDON, ""))
        assertEquals(Zone(LONDON, LONDON), copied(half, SYDNEY, 15).zone)
    }

    @Test
    fun `a read-only all-day occurrence keeps its dates and its whole days`() {
        // Two days, the 16th and 17th, listed at the user's own midnights.
        val instance = Instance(
            summary = "Offsite",
            allday = true,
            date = "2026-09-16",
            start = at(2026, 9, 16, 0, zone = SYDNEY),
            finish = at(2026, 9, 18, 0, zone = SYDNEY),
        )
        val form = copied(instance, SYDNEY, 15)
        assertTrue(form.allday)
        val midnight = LocalDate.of(2026, 9, 16).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        assertEquals(midnight, form.start)
        assertEquals(midnight + 2 * 86_400, form.finish)
        assertEquals("20260916", components(form, emptyList(), Scope.ALL).single().value("DTSTART"))
        assertEquals("20260918", components(form, emptyList(), Scope.ALL).single().value("DTEND"))
    }
}
