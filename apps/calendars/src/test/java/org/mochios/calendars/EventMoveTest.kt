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
import org.mochios.calendars.ui.editor.Scope
import org.mochios.calendars.ui.editor.advanced
import org.mochios.calendars.ui.editor.components
import org.mochios.calendars.ui.editor.draft
import org.mochios.calendars.ui.editor.instant
import org.mochios.calendars.ui.editor.shifted
import org.mochios.calendars.ui.editor.split
import org.mochios.calendars.ui.editor.truncated
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * What a drag writes: the draft it starts from, how far it moves, and the
 * two events a series cut at an occurrence becomes.
 */
class EventMoveTest {

    private val LONDON = "Europe/London"

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of(LONDON)).toEpochSecond()

    /** A daily stand-up from the 16th, 09:00 to 10:00 London. */
    private val FIRST = at(2026, 9, 16, 9)
    private val SECOND = at(2026, 9, 17, 9)
    private val THIRD = at(2026, 9, 18, 9)

    private fun master(rule: String = "FREQ=DAILY", vararg extra: org.mochios.android.sync.EventProperty) = EventComponent(
        name = "VEVENT",
        properties = listOf(
            property("UID", "uid-1@mochi"),
            property("SUMMARY", "Standup"),
            property("DTSTART", "20260916T090000", "TZID", LONDON),
            property("DTEND", "20260916T100000", "TZID", LONDON),
            property("RRULE", rule),
        ) + extra,
        components = listOf(CalendarsMapping.alarm(15, "Standup")),
    )

    /** An override of the 20th, moved to 14:00 to 15:00 and renamed. */
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

    /** An override of the 17th, renamed but at its time. */
    private fun earlier() = EventComponent(
        name = "VEVENT",
        properties = listOf(
            property("UID", "uid-1@mochi"),
            property("SUMMARY", "Earlier"),
            property("RECURRENCE-ID", "20260917T090000", "TZID", LONDON),
            property("DTSTART", "20260917T090000", "TZID", LONDON),
            property("DTEND", "20260917T100000", "TZID", LONDON),
        ),
    )

    private fun start(component: EventComponent) = CalendarsMapping.moment(component.property("DTSTART")!!) / 1000

    private fun finish(component: EventComponent) = CalendarsMapping.moment(component.property("DTEND")!!) / 1000

    // ---- the draft ----

    @Test
    fun `a draft reads the master as it stands`() {
        val form = draft(master(), LONDON)
        assertEquals("Standup", form.title)
        assertEquals(FIRST, form.start)
        assertEquals(FIRST + 3600, form.finish)
        assertEquals(LONDON, form.zone.start)
        assertEquals(15, form.reminder)
        assertEquals("FREQ=DAILY", form.recurrence.rule())
    }

    @Test
    fun `a draft moved onto an occurrence reads the series as it stands there`() {
        val form = draft(master(), LONDON, THIRD)
        assertEquals(THIRD, form.start)
        assertEquals(THIRD + 3600, form.finish)
        assertEquals("FREQ=DAILY", form.recurrence.rule())
    }

    @Test
    fun `a shift moves both ends and keeps the length`() {
        val form = shifted(draft(master(), LONDON), 7200)
        assertEquals(FIRST + 7200, form.start)
        assertEquals(FIRST + 3600 + 7200, form.finish)
    }

    @Test
    fun `a move by days keeps the clock reading across a clock change`() {
        // London leaves summer time on 25 October 2026; 09:00 stays 09:00.
        val form = advanced(draft(master(), LONDON), 40)
        assertEquals(at(2026, 10, 26, 9), form.start)
        assertEquals(at(2026, 10, 26, 10), form.finish)
    }

    @Test
    fun `an all-day form begins for the user at their own midnight`() {
        val allday = EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("DTSTART", "20260916", "VALUE", "DATE"),
                property("DTEND", "20260917", "VALUE", "DATE"),
            ),
        )
        val form = draft(allday, LONDON)
        assertTrue(form.allday)
        assertEquals(LocalDate.of(2026, 9, 16).atStartOfDay(ZoneOffset.UTC).toEpochSecond(), form.start)
        assertEquals(LocalDate.of(2026, 9, 16).atStartOfDay(ZoneId.of(LONDON)).toEpochSecond(), instant(form, LONDON))
        // Moved by a day, it is still whole days.
        assertEquals(form.start + 86_400, advanced(form, 1).start)
    }

    // ---- this event, all events ----

    @Test
    fun `this event lands the override on the occurrence`() {
        // The third occurrence dragged an hour later.
        val form = shifted(draft(master(), LONDON, THIRD), 3600).copy(occurrence = THIRD)
        val tree = components(form, listOf(master()), Scope.ONE, LONDON)
        assertEquals(2, tree.size)
        assertEquals(FIRST, start(tree[0]))
        assertEquals(THIRD + 3600, start(tree[1]))
        assertEquals(THIRD, CalendarsMapping.moment(tree[1].property("RECURRENCE-ID")!!) / 1000)
    }

    @Test
    fun `all events shifts the whole series rather than jumping onto the occurrence`() {
        val form = shifted(draft(master(), LONDON), 3600)
        val tree = components(form, listOf(master()), Scope.ALL, LONDON)
        assertEquals(FIRST + 3600, start(tree[0]))
        assertEquals("FREQ=DAILY", tree[0].value("RRULE"))
    }

    @Test
    fun `all events carries the overrides and listed dates along when the series moves`() {
        val carried = listOf(master("FREQ=DAILY", property("EXDATE", "20260919T090000", "TZID", LONDON)), later())
        val tree = components(shifted(draft(carried[0], LONDON), 3600), carried, Scope.ALL, LONDON)
        assertEquals(2, tree.size)
        assertEquals("20260919T100000", tree[0].value("EXDATE"))
        val moved = tree[1]
        assertEquals("Later", moved.value("SUMMARY"))
        assertEquals(at(2026, 9, 20, 15), start(moved))
        assertEquals(at(2026, 9, 20, 16), finish(moved))
        assertEquals("20260920T100000", moved.value("RECURRENCE-ID"))
        assertEquals(LONDON, moved.property("RECURRENCE-ID")!!.parameter("TZID"))
    }

    @Test
    fun `all events leaves the overrides alone when only the title changed`() {
        val carried = listOf(master(), later())
        val tree = components(draft(carried[0], LONDON).copy(title = "Renamed"), carried, Scope.ALL, LONDON)
        assertEquals("Renamed", tree[0].value("SUMMARY"))
        assertEquals(later(), tree[1])
    }

    // ---- cutting the series ----

    @Test
    fun `the old series ends just before the occurrence in UTC and loses its COUNT`() {
        val before = truncated(listOf(master("FREQ=DAILY;COUNT=10")), THIRD)!!
        // 09:00 BST is 08:00 UTC; the last moment before it.
        assertEquals("FREQ=DAILY;UNTIL=20260918T075959Z", before[0].value("RRULE"))
    }

    @Test
    fun `an all-day series ends on the day before`() {
        val allday = EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("DTSTART", "20260916", "VALUE", "DATE"),
                property("DTEND", "20260917", "VALUE", "DATE"),
                property("RRULE", "FREQ=DAILY"),
            ),
        )
        val day = LocalDate.of(2026, 9, 18).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        assertEquals("FREQ=DAILY;UNTIL=20260917", truncated(listOf(allday), day)!![0].value("RRULE"))
    }

    @Test
    fun `the old series keeps what came before the cut and drops the rest`() {
        val carried = listOf(master("FREQ=DAILY", property("EXDATE", "20260919T090000", "TZID", LONDON)), earlier(), later())
        val before = truncated(carried, THIRD)!!
        assertEquals(listOf("Standup", "Earlier"), before.map { it.value("SUMMARY") })
        assertNull(before[0].property("EXDATE"))
    }

    @Test
    fun `nothing is cut at the first occurrence or off an event that does not repeat`() {
        assertNull(truncated(listOf(master()), FIRST))
        val once = master().let { it.copy(properties = it.properties.filterNot { property -> property.name == "RRULE" }) }
        assertNull(truncated(listOf(once), THIRD))
    }

    @Test
    fun `the new series starts where the occurrence landed and carries the later overrides along`() {
        val carried = listOf(master("FREQ=DAILY", property("EXDATE", "20260919T090000", "TZID", LONDON)), earlier(), later())
        // The third occurrence dragged an hour later: 10:00 on the 18th.
        val moved = shifted(draft(carried[0], LONDON, THIRD), 3600)
        val (before, after) = split(carried, moved, THIRD, LONDON)!!
        assertEquals("FREQ=DAILY;UNTIL=20260918T075959Z", before[0].value("RRULE"))
        assertEquals(2, before.size)

        assertEquals("20260918T100000", after[0].value("DTSTART"))
        assertEquals("FREQ=DAILY", after[0].value("RRULE"))
        assertEquals("20260919T100000", after[0].value("EXDATE"))
        assertEquals(2, after.size)
        val following = after[1]
        assertEquals("Later", following.value("SUMMARY"))
        assertEquals("20260920T150000", following.value("DTSTART"))
        assertEquals("20260920T160000", following.value("DTEND"))
        assertEquals("20260920T100000", following.value("RECURRENCE-ID"))
        assertEquals(LONDON, following.property("RECURRENCE-ID")!!.parameter("TZID"))
        assertNull(following.property("RRULE"))
    }

    @Test
    fun `the new series keeps the reminder and what the editor has no field for`() {
        val (_, after) = split(listOf(master()), draft(master(), LONDON, THIRD), THIRD, LONDON)!!
        assertEquals("uid-1@mochi", after[0].value("UID"))
        assertEquals("-PT15M", after[0].components.single { it.name == "VALARM" }.value("TRIGGER"))
    }

    @Test
    fun `this and following on the first occurrence is the whole series`() {
        val moved = shifted(draft(master(), LONDON, FIRST), 3600)
        assertNull(split(listOf(master()), moved, FIRST, LONDON))
        val tree = components(moved, listOf(master()), Scope.FOLLOWING, LONDON)
        assertEquals(1, tree.size)
        assertEquals("20260916T100000", tree[0].value("DTSTART"))
        assertEquals("FREQ=DAILY", tree[0].value("RRULE"))
    }

    // ---- the occurrence an override is matched by ----

    @Test
    fun `a timed occurrence is matched by its start and an all-day one by its date`() {
        assertEquals(THIRD, Instance(start = THIRD).occurrence)
        val allday = Instance(
            start = LocalDate.of(2026, 9, 18).atStartOfDay(ZoneId.of(LONDON)).toEpochSecond(),
            allday = true,
            date = "2026-09-18",
        )
        assertEquals(LocalDate.of(2026, 9, 18).atStartOfDay(ZoneOffset.UTC).toEpochSecond(), allday.occurrence)
        assertFalse(allday.occurrence == allday.start)
    }
}
