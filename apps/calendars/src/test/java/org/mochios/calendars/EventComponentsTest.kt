// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.android.sync.CalendarsMapping
import org.mochios.android.sync.EventComponent
import org.mochios.android.sync.property
import org.mochios.calendars.ui.editor.EventForm
import org.mochios.calendars.ui.editor.Frequency
import org.mochios.calendars.ui.editor.Recurrence
import org.mochios.calendars.ui.editor.Scope
import org.mochios.calendars.ui.editor.components
import org.mochios.calendars.ui.editor.excluded

/**
 * The tree the editor sends: what "This event" and "All events" each change,
 * and what survives either.
 */
class EventComponentsTest {

    private val LONDON = "Europe/London"

    /** 2026-09-22 10:00 and 11:00 in London, and the same hour a week later. */
    private val TEN = 1_790_067_600L
    private val ELEVEN = 1_790_071_200L
    private val NEXT = 1_790_672_400L
    private val NEXT_END = 1_790_676_000L

    private fun master(rrule: String? = "FREQ=WEEKLY") = EventComponent(
        name = "VEVENT",
        properties = listOfNotNull(
            property("UID", "uid-1@mochi"),
            property("ORGANIZER", "mailto:a@b.c"),
            property("SUMMARY", "Stand-up"),
            property("DTSTART", "20260922T100000", "TZID", LONDON),
            property("DTEND", "20260922T110000", "TZID", LONDON),
            rrule?.let { property("RRULE", it) },
        ),
    )

    private fun override(summary: String = "Stand-up, moved") = EventComponent(
        name = "VEVENT",
        properties = listOf(
            property("UID", "uid-1@mochi"),
            property("SUMMARY", summary),
            property("RECURRENCE-ID", "20260929T100000", "TZID", LONDON),
            property("DTSTART", "20260929T140000", "TZID", LONDON),
            property("DTEND", "20260929T150000", "TZID", LONDON),
        ),
    )

    private fun form(
        title: String = "Stand-up",
        start: Long = NEXT,
        finish: Long = NEXT_END,
        occurrence: Long = NEXT,
        series: Long = TEN,
        recurrence: Recurrence = Recurrence(Frequency.WEEKLY),
        reminder: Int = -1,
    ) = EventForm(
        title = title,
        start = start,
        finish = finish,
        timezone = LONDON,
        recurrence = recurrence,
        reminder = reminder,
        occurrence = occurrence,
        series = series,
    )

    private fun start(component: EventComponent) =
        CalendarsMapping.moment(component.property("DTSTART")!!) / 1000

    private fun finish(component: EventComponent) =
        CalendarsMapping.moment(component.property("DTEND")!!) / 1000

    // ---- this event ----

    @Test
    fun `this event adds an override and leaves the master alone`() {
        val tree = components(form(title = "Just this one"), listOf(master()), Scope.ONE)
        assertEquals(2, tree.size)
        assertEquals("Stand-up", tree[0].value("SUMMARY"))
        assertEquals(TEN, start(tree[0]))
        assertEquals("FREQ=WEEKLY", tree[0].value("RRULE"))
        assertEquals("Just this one", tree[1].value("SUMMARY"))
        assertEquals(NEXT, CalendarsMapping.moment(tree[1].property("RECURRENCE-ID")!!) / 1000)
        assertNull("an override owns no rule of its own", tree[1].property("RRULE"))
    }

    @Test
    fun `this event replaces an override that was already there`() {
        val tree = components(
            form(title = "Moved again", start = NEXT + 7200, finish = NEXT_END + 7200),
            listOf(master(), override()),
            Scope.ONE,
        )
        assertEquals(2, tree.size)
        assertEquals("Moved again", tree[1].value("SUMMARY"))
        assertEquals(NEXT + 7200, start(tree[1]))
        assertEquals(NEXT, CalendarsMapping.moment(tree[1].property("RECURRENCE-ID")!!) / 1000)
    }

    @Test
    fun `this event keeps the other overrides`() {
        val other = override("Another week").let {
            it.copy(
                properties = it.properties.map { property ->
                    if (property.name == "RECURRENCE-ID") {
                        property.copy(value = "20261006T100000")
                    } else {
                        property
                    }
                },
            )
        }
        val tree = components(form(), listOf(master(), other), Scope.ONE)
        assertEquals(3, tree.size)
        assertTrue(tree.any { it.value("SUMMARY") == "Another week" })
    }

    // ---- all events ----

    @Test
    fun `all events moves the series by however far the occurrence moved`() {
        // The user opened the 29th and pushed it two hours later.
        val tree = components(
            form(start = NEXT + 7200, finish = NEXT_END + 7200),
            listOf(master()),
            Scope.ALL,
        )
        assertEquals("the master moves by two hours, not onto the 29th", TEN + 7200, start(tree[0]))
        assertEquals(ELEVEN + 7200, finish(tree[0]))
    }

    @Test
    fun `all events leaves the series where it was when only the title changed`() {
        val tree = components(form(title = "Renamed"), listOf(master()), Scope.ALL)
        assertEquals(TEN, start(tree[0]))
        assertEquals(ELEVEN, finish(tree[0]))
        assertEquals("Renamed", tree[0].value("SUMMARY"))
    }

    @Test
    fun `all events keeps every override`() {
        val tree = components(form(), listOf(master(), override()), Scope.ALL)
        assertEquals(2, tree.size)
        assertEquals("Stand-up, moved", tree[1].value("SUMMARY"))
    }

    @Test
    fun `a new event is built from the form as it stands`() {
        val tree = components(
            EventForm(title = "One off", start = TEN, finish = ELEVEN, timezone = LONDON),
            emptyList(),
            Scope.ALL,
        )
        assertEquals(1, tree.size)
        assertEquals(TEN, start(tree[0]))
        assertEquals(ELEVEN, finish(tree[0]))
        assertNull(tree[0].property("RRULE"))
    }

    // ---- what survives ----

    @Test
    fun `properties the editor has no field for survive the edit`() {
        val tree = components(form(title = "Renamed"), listOf(master()), Scope.ALL)
        assertEquals("mailto:a@b.c", tree[0].value("ORGANIZER"))
        assertEquals("uid-1@mochi", tree[0].value("UID"))
    }

    @Test
    fun `exclusions survive while the rule does, and go with it`() {
        val excluded = master().let {
            it.copy(properties = it.properties + property("EXDATE", "20261006T100000", "TZID", LONDON))
        }
        val kept = components(form(), listOf(excluded), Scope.ALL)
        assertEquals(1, kept[0].all("EXDATE").size)

        val dropped = components(
            form(recurrence = Recurrence(Frequency.NEVER)),
            listOf(excluded),
            Scope.ALL,
        )
        assertNull("a series that no longer repeats has nothing to exclude", dropped[0].property("RRULE"))
        assertEquals(0, dropped[0].all("EXDATE").size)
    }

    @Test
    fun `a reminder becomes the event's only VALARM`() {
        val carried = master().let { it.copy(components = listOf(CalendarsMapping.alarm(60, "Stand-up"))) }
        val tree = components(form(reminder = 15), listOf(carried), Scope.ALL)
        val alarms = tree[0].components.filter { it.name == "VALARM" }
        assertEquals(1, alarms.size)
        assertEquals("-PT15M", alarms.single().value("TRIGGER"))
    }

    @Test
    fun `no reminder leaves no VALARM`() {
        val carried = master().let { it.copy(components = listOf(CalendarsMapping.alarm(60, "Stand-up"))) }
        val tree = components(form(reminder = -1), listOf(carried), Scope.ALL)
        assertTrue(tree[0].components.none { it.name == "VALARM" })
    }

    // ---- removing one occurrence ----

    @Test
    fun `excluding an occurrence adds an EXDATE and drops its override`() {
        val tree = excluded(listOf(master(), override()), NEXT)
        assertEquals(1, tree.size)
        assertEquals(NEXT, CalendarsMapping.moment(tree[0].all("EXDATE").single()) / 1000)
        assertNotNull(tree[0].property("RRULE"))
    }

    @Test
    fun `excluding an occurrence keeps the overrides of the others`() {
        val other = override("Another week").let {
            it.copy(
                properties = it.properties.map { property ->
                    if (property.name == "RECURRENCE-ID") property.copy(value = "20261006T100000") else property
                },
            )
        }
        val tree = excluded(listOf(master(), override(), other), NEXT)
        assertEquals(2, tree.size)
        assertEquals("Another week", tree[1].value("SUMMARY"))
    }
}
