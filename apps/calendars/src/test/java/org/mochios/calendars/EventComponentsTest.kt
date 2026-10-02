// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.mochios.android.util.Zones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import org.mochios.android.sync.CalendarsMapping
import org.mochios.android.sync.EventComponent
import org.mochios.android.sync.property
import org.mochios.calendars.model.Zone
import org.mochios.calendars.ui.editor.EventForm
import org.mochios.calendars.ui.editor.Frequency
import org.mochios.calendars.ui.editor.Recurrence
import org.mochios.calendars.ui.editor.Scope
import org.mochios.calendars.ui.editor.components
import org.mochios.calendars.ui.editor.draft
import org.mochios.calendars.ui.editor.duplicate
import org.mochios.calendars.ui.editor.excluded
import org.mochios.calendars.ui.editor.follow
import org.mochios.calendars.ui.editor.foreign
import org.mochios.calendars.ui.editor.moved
import org.mochios.calendars.ui.editor.moveOccurrence
import org.mochios.calendars.ui.editor.single
import org.mochios.calendars.ui.editor.written

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
        reminders: List<Int> = emptyList(),
    ) = EventForm(
        title = title,
        start = start,
        finish = finish,
        zone = Zone(LONDON, LONDON),
        recurrence = recurrence,
        reminders = reminders,
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
    fun `all events keeps the overrides of other occurrences`() {
        // Opened on the first occurrence; the 29th's override stays.
        val tree = components(
            form(start = TEN, finish = ELEVEN, occurrence = TEN),
            listOf(master(), override()),
            Scope.ALL,
        )
        assertEquals(2, tree.size)
        assertEquals("Stand-up, moved", tree[1].value("SUMMARY"))
    }

    @Test
    fun `all events from a changed occurrence makes the form the series and replaces its override`() {
        // The 29th, moved to 14:00, opened as the editor shows it: its own
        // values and the series' rule. Given a location, then saved to all.
        val opened = form(title = "Stand-up, moved", start = NEXT + 14_400, finish = NEXT_END + 14_400)
        val tree = components(
            opened.copy(location = "Room 2"),
            listOf(master(), override()),
            Scope.ALL,
        )
        assertEquals(1, tree.size)
        assertEquals("FREQ=WEEKLY", tree[0].value("RRULE"))
        assertEquals("the series moves to 14:00, not onto the 29th", TEN + 14_400, start(tree[0]))
        assertEquals("Stand-up, moved", tree[0].value("SUMMARY"))
        assertEquals("Room 2", tree[0].value("LOCATION"))
    }

    @Test
    fun `a new event is built from the form as it stands`() {
        val tree = components(
            EventForm(title = "One off", start = TEN, finish = ELEVEN, zone = Zone(LONDON, LONDON)),
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

    // ---- a copy of an edited form ----

    @Test
    fun `a copy of this event carries the edits and drops the repeat`() {
        val copy = duplicate(form(title = "Renamed").copy(colour = "#f87171"), Scope.ONE)
        assertEquals("Renamed", copy.title)
        assertEquals("#f87171", copy.colour)
        assertEquals(NEXT, copy.start)
        assertEquals(NEXT_END, copy.finish)
        assertNull(copy.recurrence.rule())
        assertEquals(0L, copy.occurrence)
        assertEquals(0L, copy.series)
    }

    @Test
    fun `a copy of all events carries the edits onto the series' own start`() {
        // The occurrence a week on, moved by an hour: the series copy starts an hour after the series did.
        val copy = duplicate(form(title = "Renamed", start = NEXT + 3_600, finish = NEXT_END + 3_600), Scope.ALL)
        assertEquals("Renamed", copy.title)
        assertEquals(TEN + 3_600, copy.start)
        assertEquals(ELEVEN + 3_600, copy.finish)
        assertEquals("FREQ=WEEKLY", copy.recurrence.rule())
        assertEquals(0L, copy.occurrence)
    }

    // ---- colour and link ----

    private fun coloured() = master().let {
        it.copy(properties = it.properties + property("COLOR", "#22c55e") + property("URL", "https://example.org/a"))
    }

    @Test
    fun `an event's colour and link open in the editor`() {
        val opened = draft(coloured(), LONDON)
        assertEquals("#22c55e", opened.colour)
        assertEquals("https://example.org/a", opened.url)
    }

    @Test
    fun `a colour and link set in the editor are saved`() {
        val tree = components(form().copy(colour = "#f87171", url = "https://example.org/b"), listOf(master()), Scope.ALL)
        assertEquals("#f87171", tree[0].value("COLOR"))
        assertEquals("https://example.org/b", tree[0].value("URL"))
    }

    @Test
    fun `a cleared colour and link are dropped`() {
        val tree = components(form(), listOf(coloured()), Scope.ALL)
        assertNull(tree[0].property("COLOR"))
        assertNull(tree[0].property("URL"))
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
    fun `each reminder becomes a VALARM, replacing the ones the event had`() {
        val carried = master().let { it.copy(components = listOf(CalendarsMapping.alarm(60, "Stand-up"))) }
        val tree = components(form(reminders = listOf(15, 1440)), listOf(carried), Scope.ALL)
        val triggers = tree[0].components.filter { it.name == "VALARM" }.map { it.value("TRIGGER") }
        assertEquals(listOf("-PT15M", "-PT1440M"), triggers)
    }

    @Test
    fun `an event's every reminder survives a save that changed something else`() {
        val carried = master().copy(
            components = listOf(CalendarsMapping.alarm(10, "Stand-up"), CalendarsMapping.alarm(60, "Stand-up")),
        )
        val read = draft(carried, LONDON)
        assertEquals(listOf(10, 60), read.reminders)
        val tree = components(read.copy(title = "Renamed"), listOf(carried), Scope.ALL)
        val triggers = tree[0].components.filter { it.name == "VALARM" }.map { it.value("TRIGGER") }
        assertEquals(listOf("-PT10M", "-PT60M"), triggers)
    }

    // ---- a description written as HTML ----

    private val HTML = "PNR: 2YHEIJ<br>Class: <b>Business</b> &amp; lounge"
    private val TEXT = "PNR: 2YHEIJ\nClass: Business & lounge"

    private fun described() = master().let { it.copy(properties = it.properties + property("DESCRIPTION", HTML)) }

    @Test
    fun `an HTML description opens in the editor as its text`() {
        val read = draft(described(), LONDON)
        assertEquals(TEXT, read.description)
        assertEquals(HTML, read.original)
    }

    @Test
    fun `an HTML description keeps its markup through a save that changed something else`() {
        val carried = described()
        val tree = components(draft(carried, LONDON).copy(title = "Renamed"), listOf(carried), Scope.ALL)
        assertEquals(HTML, tree[0].value("DESCRIPTION"))
    }

    @Test
    fun `an HTML description keeps its markup on one occurrence's override`() {
        val carried = described()
        val read = draft(carried, LONDON, NEXT).copy(title = "Just once", occurrence = NEXT, series = TEN)
        val tree = components(read, listOf(carried), Scope.ONE)
        val override = tree.single { it.property("RECURRENCE-ID") != null }
        assertEquals(HTML, override.value("DESCRIPTION"))
    }

    @Test
    fun `an edited HTML description is saved as the text typed`() {
        val carried = described()
        val read = draft(carried, LONDON)
        val tree = components(read.copy(description = read.description + "\nSeat 2A"), listOf(carried), Scope.ALL)
        assertEquals("$TEXT\nSeat 2A", tree[0].value("DESCRIPTION"))
    }

    @Test
    fun `a cleared HTML description is dropped`() {
        val carried = described()
        val tree = components(draft(carried, LONDON).copy(description = ""), listOf(carried), Scope.ALL)
        assertNull(tree[0].property("DESCRIPTION"))
    }

    @Test
    fun `alarms the reminder setting cannot say are kept`() {
        fun alarm(trigger: String, parameter: String? = null, argument: String? = null) =
            EventComponent("VALARM", listOf(property("TRIGGER", trigger, parameter, argument)))
        val carried = master().copy(
            components = listOf(
                alarm("-PT1H"),
                alarm("-PT30M", "RELATED", "END"),
                alarm("20260922T090000Z", "VALUE", "DATE-TIME"),
                alarm("PT10M"),
            ),
        )
        val tree = components(form(reminders = listOf(15)), listOf(carried), Scope.ALL)
        val triggers = tree[0].components.filter { it.name == "VALARM" }.map { it.value("TRIGGER") }
        assertEquals(listOf("-PT30M", "20260922T090000Z", "PT10M", "-PT15M"), triggers)
    }

    @Test
    fun `every alarm the setting can say is read, once each`() {
        fun alarm(trigger: String, parameter: String? = null, argument: String? = null) =
            EventComponent("VALARM", listOf(property("TRIGGER", trigger, parameter, argument)))
        val all = master().copy(
            components = listOf(alarm("-PT15M", "RELATED", "END"), alarm("-PT30M"), alarm("-P1D"), alarm("-PT30M")),
        )
        assertEquals(listOf(30, 1440), draft(all, LONDON).reminders)
        val end = master().copy(components = listOf(alarm("-PT15M", "RELATED", "END")))
        assertEquals(emptyList<Int>(), draft(end, LONDON).reminders)
    }

    @Test
    fun `no reminder leaves no VALARM`() {
        val carried = master().let { it.copy(components = listOf(CalendarsMapping.alarm(60, "Stand-up"))) }
        val tree = components(form(reminders = emptyList()), listOf(carried), Scope.ALL)
        assertTrue(tree[0].components.none { it.name == "VALARM" })
    }

    // ---- removing one occurrence ----

    // ---- one occurrence to another calendar ----

    @Test
    fun `an occurrence alone is the form with nothing of the series on it`() {
        val tree = single(form(title = "Elsewhere"), listOf(master(), override()))
        assertEquals(1, tree.size)
        assertEquals("Elsewhere", tree[0].value("SUMMARY"))
        assertEquals(NEXT, start(tree[0]))
        assertNull(tree[0].property("RRULE"))
        assertNull(tree[0].property("RECURRENCE-ID"))
        assertNull(tree[0].property("EXDATE"))
    }

    @Test
    fun `an occurrence with no override of its own carries the master's other properties`() {
        val tree = single(form(), listOf(master()))
        assertEquals("mailto:a@b.c", tree[0].value("ORGANIZER"))
        assertNull(tree[0].property("RRULE"))
    }

    @Test
    fun `a moved occurrence is created, then excluded from the series`() = runBlocking {
        val steps = mutableListOf<String>()
        val created = moveOccurrence(
            create = { steps.add("create"); "copy" },
            exclude = { steps.add("exclude") },
            delete = { steps.add("delete $it") },
        )
        assertEquals("copy", created)
        assertEquals(listOf("create", "exclude"), steps)
    }

    @Test
    fun `a series that cannot be written takes the copy back`() = runBlocking {
        val steps = mutableListOf<String>()
        val failure = runCatching {
            moveOccurrence(
                create = { steps.add("create"); "copy" },
                exclude = { throw IllegalStateException("changed") },
                delete = { steps.add("delete $it") },
            )
        }
        assertTrue(failure.isFailure)
        assertEquals("changed", failure.exceptionOrNull()?.message)
        assertEquals(listOf("create", "delete copy"), steps)
    }

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

    // ---- zones ----

    private val NEW_YORK = "America/New_York"

    @Test
    fun `each end is stamped in its own zone`() {
        val tree = components(
            EventForm(title = "Flight", start = TEN, finish = ELEVEN, zone = Zone(LONDON, NEW_YORK)),
            emptyList(),
            Scope.ALL,
        )
        val starts = tree[0].property("DTSTART")!!
        val finishes = tree[0].property("DTEND")!!
        assertEquals(LONDON, starts.parameter("TZID"))
        assertEquals(NEW_YORK, finishes.parameter("TZID"))
        // 10:00 London is 05:00 New York; the instant is the same either way.
        assertEquals("20260922T100000", starts.value)
        assertEquals("20260922T060000", finishes.value)
        assertEquals(TEN, start(tree[0]))
        assertEquals(ELEVEN, finish(tree[0]))
    }

    @Test
    fun `a finish zone left blank follows the start zone`() {
        val tree = components(
            EventForm(title = "One off", start = TEN, finish = ELEVEN, zone = Zone(LONDON, "")),
            emptyList(),
            Scope.ALL,
        )
        assertEquals(LONDON, tree[0].property("DTEND")!!.parameter("TZID"))
    }

    @Test
    fun `reading takes each end's own TZID`() {
        val component = EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("DTSTART", "20260922T100000", "TZID", LONDON),
                property("DTEND", "20260922T060000", "TZID", NEW_YORK),
            ),
        )
        assertEquals(Zone(LONDON, NEW_YORK), written(component, "Pacific/Auckland"))
    }

    @Test
    fun `reading a DTEND without a TZID follows the start`() {
        val component = EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("DTSTART", "20260922T100000", "TZID", LONDON),
                property("DTEND", "20260922T100000Z"),
            ),
        )
        assertEquals(Zone(LONDON, LONDON), written(component, "Pacific/Auckland"))
    }

    @Test
    fun `reading no TZID at all means the user's zone`() {
        val component = EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("DTSTART", "20260922T090000Z"),
                property("DTEND", "20260922T100000Z"),
            ),
        )
        assertEquals(Zone("Pacific/Auckland", "Pacific/Auckland"), written(component, "Pacific/Auckland"))
    }

    @Test
    fun `the finish zone follows the start zone while the two are equal`() {
        assertEquals(Zone(NEW_YORK, NEW_YORK), follow(Zone(LONDON, LONDON), NEW_YORK))
    }

    @Test
    fun `the finish zone stops following once set apart`() {
        assertEquals(Zone("Asia/Tokyo", NEW_YORK), follow(Zone(LONDON, NEW_YORK), "Asia/Tokyo"))
    }

    @Test
    fun `both ends in the user's zone need no zones shown`() {
        assertFalse(foreign(Zone(LONDON, LONDON), LONDON))
        // An end with no zone reads in the user's.
        assertFalse(foreign(Zone("", ""), LONDON))
        assertFalse(foreign(Zone(LONDON, ""), LONDON))
    }

    @Test
    fun `either end in another zone shows the zones`() {
        assertTrue(foreign(Zone(NEW_YORK, LONDON), LONDON))
        assertTrue(foreign(Zone(LONDON, NEW_YORK), LONDON))
        assertTrue(foreign(Zone(NEW_YORK, NEW_YORK), LONDON))
    }

    /** A platform that resolves zones as ICU does, where Asia/Kolkata's own name is Asia/Calcutta. */
    private val icu = object : Zones.Registry {
        override fun canonical(zone: String): String? =
            mapOf("Asia/Kolkata" to "Asia/Calcutta", "Asia/Calcutta" to "Asia/Calcutta")[zone]

        override fun places(): Collection<String> = emptyList()
    }

    @Test
    fun `an end under another name of the user's own zone needs no zones shown`() {
        assertFalse(foreign(Zone("Asia/Calcutta", "Asia/Calcutta"), "Asia/Kolkata", icu))
        assertFalse(foreign(Zone("Asia/Kolkata", ""), "Asia/Calcutta", icu))
        assertTrue(foreign(Zone("Asia/Calcutta", NEW_YORK), "Asia/Kolkata", icu))
    }

    @Test
    fun `the finish zone follows the start zone while the two are one zone under two names`() {
        assertEquals(Zone(NEW_YORK, NEW_YORK), follow(Zone("Asia/Calcutta", "Asia/Kolkata"), NEW_YORK, icu))
    }

    @Test
    fun `a time moved to another zone keeps its clock reading`() {
        // 10:00 London on the 22nd reads 10:00 New York five hours later.
        assertEquals(TEN + 5 * 3600, moved(TEN, LONDON, NEW_YORK))
        assertEquals(TEN, moved(TEN, LONDON, LONDON))
    }
}
