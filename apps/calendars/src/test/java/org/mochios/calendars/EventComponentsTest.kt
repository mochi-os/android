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
import org.mochios.calendars.ui.editor.matches
import org.mochios.calendars.ui.editor.moved
import org.mochios.calendars.ui.editor.named
import org.mochios.calendars.ui.editor.original
import org.mochios.calendars.ui.editor.shifted
import org.mochios.calendars.ui.editor.truncated
import org.mochios.calendars.ui.editor.moveOccurrence
import org.mochios.calendars.ui.editor.single
import org.mochios.calendars.ui.editor.written
import org.mochios.calendars.ui.editor.advanced
import org.mochios.calendars.ui.editor.instant
import org.mochios.calendars.ui.editor.picked
import org.mochios.calendars.model.Hours
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

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
        val copy = duplicate(form(title = "Renamed").copy(colour = "#f87171"), Scope.ONE, listOf(master()), LONDON)
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
        val copy = duplicate(
            form(title = "Renamed", start = NEXT + 3_600, finish = NEXT_END + 3_600),
            Scope.ALL,
            listOf(master()),
            LONDON,
        )
        assertEquals("Renamed", copy.title)
        assertEquals(TEN + 3_600, copy.start)
        assertEquals(ELEVEN + 3_600, copy.finish)
        assertEquals("FREQ=WEEKLY", copy.recurrence.rule())
        assertEquals(0L, copy.occurrence)
    }

    /** How [seconds] reads on London's clock, as `yyyyMMdd'T'HHmmss`. */
    private fun reads(seconds: Long) = java.time.Instant.ofEpochSecond(seconds).atZone(java.time.ZoneId.of(LONDON))
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))

    /**
     * A daily London series at 09:00 from Wednesday 23 October 2024, whose
     * Saturday occurrence, in summer time, was moved to 09:00 on Sunday the
     * 27th, in winter time: London's clocks go back that morning.
     */
    private fun autumn(): List<EventComponent> = listOf(
        EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("UID", "uid-1@mochi"),
                property("SUMMARY", "Stand-up"),
                property("DTSTART", "20241023T090000", "TZID", LONDON),
                property("DTEND", "20241023T100000", "TZID", LONDON),
                property("RRULE", "FREQ=DAILY"),
            ),
        ),
        EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("UID", "uid-1@mochi"),
                property("SUMMARY", "Stand-up, a day late"),
                property("RECURRENCE-ID", "20241026T090000", "TZID", LONDON),
                property("DTSTART", "20241027T090000", "TZID", LONDON),
                property("DTEND", "20241027T100000", "TZID", LONDON),
            ),
        ),
    )

    @Test
    fun `a copy of all events from an override moved across a clock change lands on the series by the clock`() {
        // The override opened, renamed and copied whole: a day on from the
        // series' first occurrence, at 09:00 as the form shows.
        val tree = autumn()
        val form = draft(tree[1], LONDON).copy(
            title = "Renamed",
            recurrence = Recurrence(Frequency.DAILY),
            occurrence = london("20241026T090000"),
            series = london("20241023T090000"),
        )
        val copy = duplicate(form, Scope.ALL, tree, LONDON)
        assertEquals(
            "THE POINT: 09:00 to 10:00 on the 24th, not 10:00 to 11:00",
            listOf("20241024T090000", "20241024T100000"),
            listOf(reads(copy.start), reads(copy.finish)),
        )
        assertEquals(
            "where saving the form to the series puts the series",
            components(form, tree, Scope.ALL, LONDON)[0].value("DTSTART"),
            reads(copy.start),
        )
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

    // ---- tentative ----

    private fun status(value: String) = master().let { it.copy(properties = it.properties + property("STATUS", value)) }

    private fun statuses(component: EventComponent) =
        component.properties.filter { it.name == "STATUS" }.map { it.value }

    @Test
    fun `an event opens as tentative only when its status says so`() {
        assertTrue(draft(status("TENTATIVE"), LONDON).tentative)
        assertFalse(draft(status("CONFIRMED"), LONDON).tentative)
        assertFalse(draft(master(), LONDON).tentative)
    }

    @Test
    fun `tentative set in the editor is saved once, in place of any other status`() {
        val on = form().copy(tentative = true)
        assertEquals(listOf("TENTATIVE"), statuses(components(on, listOf(master()), Scope.ALL)[0]))
        assertEquals(listOf("TENTATIVE"), statuses(components(on, listOf(status("CONFIRMED")), Scope.ALL)[0]))
        assertEquals(listOf("TENTATIVE"), statuses(components(on, listOf(status("TENTATIVE")), Scope.ALL)[0]))
    }

    @Test
    fun `tentative turned off clears it, leaving a confirmed or cancelled status alone`() {
        assertEquals(emptyList<String>(), statuses(components(form(), listOf(status("TENTATIVE")), Scope.ALL)[0]))
        assertEquals(listOf("CANCELLED"), statuses(components(form(), listOf(status("CANCELLED")), Scope.ALL)[0]))
        assertEquals(listOf("CONFIRMED"), statuses(components(form(), listOf(status("CONFIRMED")), Scope.ALL)[0]))
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

    @Test
    fun `a series cut by this and following opens ending before the cut`() {
        // A daily 10:00 London series cut at the 29th's occurrence ends a second before it.
        val cut = truncated(listOf(master("FREQ=DAILY")), NEXT)!!.first()
        assertEquals("FREQ=DAILY;UNTIL=20260929T085959Z", cut.value("RRULE"))
        assertEquals(java.time.LocalDate.of(2026, 9, 28), draft(cut, LONDON).recurrence.until)
    }

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

    // ---- a rich copy of the description another client keeps ----

    private fun enriched() = master().let {
        it.copy(
            properties = it.properties + property("DESCRIPTION", "Gate 12") +
                property(CalendarsMapping.ALTERNATIVE, "<p>Gate <b>12</b></p>", "FMTTYPE", "text/html"),
        )
    }

    @Test
    fun `another client's rich copy of the description stays while the description is unchanged`() {
        val carried = enriched()
        val tree = components(draft(carried, LONDON).copy(title = "Renamed"), listOf(carried), Scope.ALL)
        assertEquals("<p>Gate <b>12</b></p>", tree[0].value(CalendarsMapping.ALTERNATIVE))
    }

    @Test
    fun `another client's rich copy goes once the description is edited, so it cannot show the old text`() {
        val carried = enriched()
        val tree = components(draft(carried, LONDON).copy(description = "Gate 14"), listOf(carried), Scope.ALL)
        assertEquals("Gate 14", tree[0].value("DESCRIPTION"))
        assertNull(tree[0].property(CalendarsMapping.ALTERNATIVE))
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
    fun `reading a start in UTC keeps UTC, and one with no zone at all the user's`() {
        val utc = EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("DTSTART", "20260922T090000Z"),
                property("DTEND", "20260922T100000Z"),
            ),
        )
        val floating = EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("DTSTART", "20260922T090000"),
                property("DTEND", "20260922T100000"),
            ),
        )
        assertEquals(
            listOf(Zone(CalendarsMapping.UTC, CalendarsMapping.UTC), Zone("Pacific/Auckland", "Pacific/Auckland")),
            listOf(written(utc, "Pacific/Auckland"), written(floating, "Pacific/Auckland")),
        )
    }

    @Test
    fun `the finish zone follows the start zone while the two are equal`() {
        assertEquals(Zone(NEW_YORK, NEW_YORK), follow(Zone(LONDON, LONDON), NEW_YORK))
    }

    @Test
    fun `the finish zone stops following once set apart`() {
        assertEquals(Zone("Asia/Tokyo", NEW_YORK), follow(Zone(LONDON, NEW_YORK), "Asia/Tokyo"))
    }

    /** A platform that resolves zones as ICU does, where Asia/Kolkata's own name is Asia/Calcutta. */
    private val icu = object : Zones.Registry {
        override fun canonical(zone: String): String? =
            mapOf("Asia/Kolkata" to "Asia/Calcutta", "Asia/Calcutta" to "Asia/Calcutta")[zone]

        override fun places(): Collection<String> = emptyList()
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

    // ---- an occurrence moved by "This event" ----

    /** The override of the 29th starts at 14:00, where the listing gives it. */
    private val MOVED = NEXT + 4 * 3600

    @Test
    fun `an override listed where it was moved to is found by the occurrence it replaces`() {
        val tree = listOf(master(), override())
        assertEquals(NEXT, original(tree, MOVED, LONDON))
        assertEquals("a start no override was moved to is itself", TEN, original(tree, TEN, LONDON))
        assertEquals(NEXT, named(tree, NEXT, LONDON))
    }

    @Test
    fun `an all-day occurrence the listing gives at the user's midnight is named by its date`() {
        val series = EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("DTSTART", "20260922", "VALUE", "DATE"),
                property("DTEND", "20260923", "VALUE", "DATE"),
                property("RRULE", "FREQ=WEEKLY"),
            ),
        )
        val moved = EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("RECURRENCE-ID", "20260929", "VALUE", "DATE"),
                property("DTSTART", "20261001", "VALUE", "DATE"),
                property("DTEND", "20261002", "VALUE", "DATE"),
            ),
        )
        fun midnight(day: Int, zone: String) =
            java.time.LocalDate.of(2026, 9, 1).plusDays(day - 1L).atStartOfDay(java.time.ZoneId.of(zone)).toEpochSecond()
        val listed = java.time.LocalDate.of(2026, 10, 1).atStartOfDay(java.time.ZoneId.of(NEW_YORK)).toEpochSecond()
        assertEquals(midnight(29, NEW_YORK), original(listOf(series, moved), listed, NEW_YORK))
        assertEquals(midnight(29, "UTC"), named(listOf(series, moved), midnight(29, NEW_YORK), NEW_YORK))
    }

    @Test
    fun `this event on a moved occurrence replaces its override rather than adding one`() {
        val tree = listOf(master(), override())
        val opened = named(tree, original(tree, MOVED, LONDON), LONDON)
        val saved = components(
            form(title = "Moved again", start = MOVED + 3600, finish = MOVED + 7200, occurrence = opened),
            tree,
            Scope.ONE,
            LONDON,
        )
        assertEquals(2, saved.size)
        assertEquals("Moved again", saved[1].value("SUMMARY"))
        assertEquals(NEXT, CalendarsMapping.moment(saved[1].property("RECURRENCE-ID")!!) / 1000)
        val gone = excluded(tree, opened, LONDON)
        assertEquals(1, gone.size)
        assertEquals(NEXT, CalendarsMapping.moment(gone[0].all("EXDATE").single()) / 1000)
    }

    // ---- a floating event, whose times name no zone ----

    private fun floating(rrule: String? = "FREQ=WEEKLY") = EventComponent(
        name = "VEVENT",
        properties = listOfNotNull(
            property("UID", "uid-2@mochi"),
            property("SUMMARY", "Yoga"),
            property("DTSTART", "20260922T100000"),
            property("DTEND", "20260922T110000"),
            rrule?.let { property("RRULE", it) },
        ),
    )

    /** 10:00 on the 22nd, and a week later, in New York. */
    private val YORK = 1_790_085_600L
    private val YORK_NEXT = YORK + 7 * 86_400

    @Test
    fun `a floating event reads at its clock time in the user's zone`() {
        val read = draft(floating(null), NEW_YORK)
        assertEquals(YORK, read.start)
        assertEquals(YORK + 3600, read.finish)
        assertEquals(NEW_YORK, read.floating)
    }

    @Test
    fun `a floating event stays floating through a save that kept its zone`() {
        val read = draft(floating(null), NEW_YORK)
        val tree = components(read.copy(title = "Yoga, upstairs"), listOf(floating(null)), Scope.ALL, NEW_YORK)
        assertEquals(property("DTSTART", "20260922T100000"), tree[0].property("DTSTART"))
        assertEquals(property("DTEND", "20260922T110000"), tree[0].property("DTEND"))
    }

    @Test
    fun `a floating event given a zone is pinned there`() {
        val read = draft(floating(null), NEW_YORK)
        val tree = components(read.copy(zone = Zone(LONDON, LONDON)), listOf(floating(null)), Scope.ALL, NEW_YORK)
        assertEquals(property("DTSTART", "20260922T150000", "TZID", LONDON), tree[0].property("DTSTART"))
    }

    @Test
    fun `a floating series names its occurrences floating`() {
        val read = draft(floating(), NEW_YORK, YORK_NEXT)
        val tree = components(read.copy(title = "Just this one", occurrence = YORK_NEXT), listOf(floating()), Scope.ONE, NEW_YORK)
        assertEquals(property("RECURRENCE-ID", "20260929T100000"), tree[1].property("RECURRENCE-ID"))
        assertEquals(property("DTSTART", "20260929T100000"), tree[1].property("DTSTART"))
        val gone = excluded(listOf(floating()), YORK_NEXT, NEW_YORK)
        assertEquals(property("EXDATE", "20260929T100000"), gone[0].property("EXDATE"))
    }

    @Test
    fun `a floating series cut there ends floating, as its start is`() {
        val cut = truncated(listOf(floating()), YORK_NEXT, NEW_YORK)!!.first()
        assertEquals("FREQ=WEEKLY;UNTIL=20260929T095959", cut.value("RRULE"))
    }

    @Test
    fun `a floating series' end on a day is floating`() {
        val until = Recurrence(Frequency.WEEKLY, ending = org.mochios.calendars.ui.editor.Ending.UNTIL, until = java.time.LocalDate.of(2026, 10, 20))
        assertEquals("FREQ=WEEKLY;UNTIL=20261020T235959", until.rule(NEW_YORK, floating = true))
    }

    // ---- a series of listed dates alone ----

    private fun listed() = master(rrule = null).let {
        it.copy(properties = it.properties + property("RDATE", "20260925T100000,20260929T100000,20261006T100000", "TZID", LONDON))
    }

    @Test
    fun `a series of listed dates alone keeps them through a save of all events`() {
        val read = draft(listed(), LONDON)
        val tree = components(read.copy(title = "Renamed"), listOf(listed()), Scope.ALL, LONDON)
        assertEquals("20260925T100000,20260929T100000,20261006T100000", tree[0].value("RDATE"))
        val moved = components(shifted(read, 3600), listOf(listed()), Scope.ALL, LONDON)
        assertEquals("20260925T110000,20260929T110000,20261006T110000", moved[0].value("RDATE"))
    }

    @Test
    fun `a series of listed dates alone is cut at the occurrence`() {
        val cut = truncated(listOf(listed()), NEXT, LONDON)
        assertNotNull(cut)
        assertEquals("20260925T100000", cut!!.single().value("RDATE"))
        assertNull(cut.single().property("RRULE"))
    }

    // ---- alarms the editor keeps ----

    private fun alarm(minutes: Int, action: String, description: String) = EventComponent(
        "VALARM",
        listOf(
            property("ACTION", action),
            property("DESCRIPTION", description),
            property("TRIGGER", CalendarsMapping.trigger(minutes)),
            property("X-WR-ALARMUID", "alarm-$minutes-$action"),
        ),
    )

    @Test
    fun `an alarm whose reminder the form still has is kept as the event holds it`() {
        val carried = master().copy(
            components = listOf(alarm(15, "EMAIL", "Leave now"), alarm(15, "DISPLAY", "Leave now"), alarm(30, "AUDIO", "Soon")),
        )
        val read = draft(carried, LONDON)
        assertEquals(listOf(15, 30), read.reminders)
        val tree = components(read.copy(title = "Renamed", reminders = listOf(15, 60)), listOf(carried), Scope.ALL, LONDON)
        val alarms = tree[0].components.filter { it.name == "VALARM" }
        assertEquals(alarm(15, "EMAIL", "Leave now"), alarms[0])
        assertEquals(alarm(15, "DISPLAY", "Leave now"), alarms[1])
        assertEquals(listOf("-PT60M"), alarms.drop(2).map { it.value("TRIGGER") })
        assertEquals(3, alarms.size)
    }

    @Test
    fun `a new override keeps the series' alarms the form showed`() {
        val carried = master().copy(components = listOf(alarm(15, "EMAIL", "Leave now")))
        val read = draft(carried, LONDON, NEXT)
        val tree = components(read.copy(title = "Just this one", occurrence = NEXT), listOf(carried), Scope.ONE, LONDON)
        assertEquals(listOf(alarm(15, "EMAIL", "Leave now")), tree[1].components.filter { it.name == "VALARM" })
    }

    // ---- a series moved across a clock change ----

    /** The instant [time], as `yyyyMMdd'T'HHmmss`, reads on London's clock. */
    private fun london(time: String) =
        java.time.LocalDateTime.parse(time, java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            .atZone(java.time.ZoneId.of(LONDON)).toEpochSecond()

    /**
     * A weekly London series at 10:00 from Friday [from], with the 10 April
     * occurrence moved to 14:00, the 17th's excluded and Wednesday the 22nd
     * added: every one of them in summer time, which London's clocks go on
     * to on Sunday 29 March.
     */
    private fun spring(from: String): List<EventComponent> = listOf(
        EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("UID", "uid-1@mochi"),
                property("SUMMARY", "Stand-up"),
                property("DTSTART", "${from}T100000", "TZID", LONDON),
                property("DTEND", "${from}T110000", "TZID", LONDON),
                property("RRULE", "FREQ=WEEKLY"),
                property("EXDATE", "20260417T100000", "TZID", LONDON),
                property("RDATE", "20260422T100000", "TZID", LONDON),
            ),
        ),
        EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("UID", "uid-1@mochi"),
                property("SUMMARY", "Stand-up, late"),
                property("RECURRENCE-ID", "20260410T100000", "TZID", LONDON),
                property("DTSTART", "20260410T140000", "TZID", LONDON),
                property("DTEND", "20260410T150000", "TZID", LONDON),
            ),
        ),
    )

    /** What [tree]'s series and its 10 April override, moved on three days by the clock, are written as. */
    private fun movedOnThreeDays(tree: List<EventComponent>) {
        val head = tree.first { it.property("RECURRENCE-ID") == null }
        val late = tree.single { it.property("RECURRENCE-ID") != null }
        assertEquals("THE POINT: the override still names the 13 April 10:00 the series now holds", "20260413T100000", late.value("RECURRENCE-ID"))
        assertEquals("and starts at 14:00 that day still", "20260413T140000", late.value("DTSTART"))
        assertEquals("20260413T150000", late.value("DTEND"))
        assertEquals("THE POINT: the exclusion moves to 10:00 on the 20th, not 09:00", "20260420T100000", head.value("EXDATE"))
        assertEquals("and the added date to 10:00 on the 25th", "20260425T100000", head.value("RDATE"))
    }

    @Test
    fun `a series moved whole across a clock change takes its overrides and dates along by the clock`() {
        // From Friday 27 March, in winter time, to Monday the 30th, in summer:
        // three days on the clock, an hour short of that by the watch.
        val form = form(start = london("20260330T100000"), finish = london("20260330T110000"), occurrence = 0, series = 0)
        val tree = components(form, spring("20260327"), Scope.ALL, LONDON)
        assertEquals("20260330T100000", tree[0].value("DTSTART"))
        movedOnThreeDays(tree)
    }

    @Test
    fun `a series moved from one occurrence across a clock change takes its overrides and dates along by the clock`() {
        // From the 27 March occurrence on, moved to Monday the 30th.
        val carried = spring("20260320")
        val form = draft(carried[0], LONDON, london("20260327T100000"))
            .copy(start = london("20260330T100000"), finish = london("20260330T110000"))
        val (_, after) = org.mochios.calendars.ui.editor.split(carried, form, london("20260327T100000"), LONDON)!!
        assertEquals("20260330T100000", after[0].value("DTSTART"))
        movedOnThreeDays(after)
    }

    @Test
    fun `a series moved from an occurrence opened across a clock change moves its start by the clock`() {
        // The 27 March occurrence opened in winter time and moved to Monday
        // the 30th, in summer time, then saved to the whole series from the 20th.
        val form = form(
            start = london("20260330T100000"),
            finish = london("20260330T110000"),
            occurrence = london("20260327T100000"),
            series = london("20260320T100000"),
        )
        val tree = components(form, spring("20260320"), Scope.ALL, LONDON)
        assertEquals("THE POINT: three days on at 10:00, not at 09:00", "20260323T100000", tree[0].value("DTSTART"))
        movedOnThreeDays(tree)
    }

    // ---- an hour the clocks read twice or skip ----

    /** The instant [time], as `yyyyMMdd'T'HHmmss`, reads on the clock in [zone]. */
    private fun at(time: String, zone: String) =
        java.time.LocalDateTime.parse(time, java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            .atZone(java.time.ZoneId.of(zone)).toEpochSecond()

    @Test
    fun `a date moved into the hour the clocks read twice falls where the server places the series' own occurrence`() {
        // A daily London series at 01:30 from Friday 25 October 2024, whose
        // exclusion of Saturday's, written in UTC, and a date added at the
        // same instant, written in New York, move with it a day on to Sunday
        // the 27th, when 01:30 comes round twice: the server places that
        // occurrence at the second, in winter time, 01:30 UTC.
        val tree = listOf(
            EventComponent(
                name = "VEVENT",
                properties = listOf(
                    property("UID", "uid-1@mochi"),
                    property("SUMMARY", "Night shift"),
                    property("DTSTART", "20241025T013000", "TZID", LONDON),
                    property("DTEND", "20241025T023000", "TZID", LONDON),
                    property("RRULE", "FREQ=DAILY"),
                    property("EXDATE", "20241026T003000Z"),
                    property("RDATE", "20241025T203000", "TZID", NEW_YORK),
                ),
            ),
        )
        val form = form(start = london("20241026T013000"), finish = london("20241026T023000"), occurrence = 0, series = 0)
        val head = components(form, tree, Scope.ALL, LONDON)[0]
        assertEquals(
            "THE POINT: both at 01:30 in winter time, 21:30 in New York, not in summer time",
            listOf("20241027T013000Z", "20241026T213000"),
            listOf(head.value("EXDATE"), head.value("RDATE")),
        )
    }

    @Test
    fun `a date moved into the hour the clocks skip falls where the server places the series' own occurrence`() {
        // A daily New York series at 02:30 from Friday 8 March 2024, whose
        // exclusion of Saturday's, written in UTC, moves with it a day on to
        // Sunday the 10th, whose 02:30 the clocks skip: the server places that
        // occurrence an hour before, at 01:30 in winter time, 06:30 UTC.
        val tree = listOf(
            EventComponent(
                name = "VEVENT",
                properties = listOf(
                    property("UID", "uid-1@mochi"),
                    property("SUMMARY", "Night shift"),
                    property("DTSTART", "20240308T023000", "TZID", NEW_YORK),
                    property("DTEND", "20240308T033000", "TZID", NEW_YORK),
                    property("RRULE", "FREQ=DAILY"),
                    property("EXDATE", "20240309T073000Z"),
                ),
            ),
        )
        val form = form(
            start = at("20240309T023000", NEW_YORK),
            finish = at("20240309T033000", NEW_YORK),
            occurrence = 0,
            series = 0,
        ).copy(zone = Zone(NEW_YORK, NEW_YORK))
        val head = components(form, tree, Scope.ALL, NEW_YORK)[0]
        assertEquals("THE POINT: 06:30 UTC, not 07:30", "20240310T063000Z", head.value("EXDATE"))
    }

    // ---- a series moved into another zone ----

    /**
     * A daily London series at 09:00 from [from], with Tuesday 29 October
     * 2024's occurrence moved to 14:00, Wednesday the 30th's excluded and
     * 18:00 on Thursday the 31st added. London's clocks go back on Sunday the
     * 27th and New York's on Sunday 3 November, so in the week between, where
     * each of those falls, the two cities are four hours apart, not five.
     */
    private fun window(from: String = "20241023"): List<EventComponent> = listOf(
        EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("UID", "uid-1@mochi"),
                property("SUMMARY", "Stand-up"),
                property("DTSTART", "${from}T090000", "TZID", LONDON),
                property("DTEND", "${from}T100000", "TZID", LONDON),
                property("RRULE", "FREQ=DAILY"),
                property("EXDATE", "20241030T090000", "TZID", LONDON),
                property("RDATE", "20241031T180000", "TZID", LONDON),
            ),
        ),
        EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("UID", "uid-1@mochi"),
                property("SUMMARY", "Stand-up, late"),
                property("RECURRENCE-ID", "20241029T090000", "TZID", LONDON),
                property("DTSTART", "20241029T140000", "TZID", LONDON),
                property("DTEND", "20241029T150000", "TZID", LONDON),
            ),
        ),
    )

    /**
     * What [tree] writes for its series and its one override: the series'
     * start and the zone it is in, the override's occurrence, start and end,
     * and the series' excluded and added dates.
     */
    private fun carries(tree: List<EventComponent>): List<String?> {
        val head = tree.first { it.property("RECURRENCE-ID") == null }
        val late = tree.single { it.property("RECURRENCE-ID") != null }
        return listOf(
            head.value("DTSTART"),
            head.property("DTSTART")?.parameter("TZID"),
            late.value("RECURRENCE-ID"),
            late.value("DTSTART"),
            late.value("DTEND"),
            head.value("EXDATE"),
            head.value("RDATE"),
        )
    }

    /**
     * What [carries] reads once [window]'s series is at 09:00 in New York
     * from the 23rd: the override still names its occurrence's 09:00 there
     * and starts five hours on from it, at 14:00 New York time, and the listed
     * dates keep their New York times, each as London's clock then reads it.
     * By London's clock all four land an hour later.
     */
    private val CARRIED = listOf(
        "20241023T090000",
        NEW_YORK,
        "20241029T090000",
        "20241029T180000",
        "20241029T190000",
        "20241030T130000",
        "20241031T220000",
    )

    /** The form at [start] to an hour on, as `yyyyMMdd'T'HHmmss` in New York, in that zone. */
    private fun york(start: String, occurrence: Long = 0, series: Long = 0): EventForm {
        val begins = at(start, NEW_YORK)
        return form(start = begins, finish = begins + 3_600, occurrence = occurrence, series = series, recurrence = Recurrence(Frequency.DAILY))
            .copy(zone = Zone(NEW_YORK, NEW_YORK))
    }

    @Test
    fun `a series moved into another zone takes its overrides and dates onto that zone's clock`() {
        val tree = components(york("20241023T090000"), window(), Scope.ALL, LONDON)
        assertEquals("THE POINT: each an hour before where London's clock puts it", CARRIED, carries(tree))
    }

    @Test
    fun `a series moved into another zone from an occurrence between the clock changes lands on that zone's clock`() {
        // Monday the 28th's occurrence, 09:00 in London, set to 09:00 in New
        // York and saved to the whole series: the series goes to 09:00 in New
        // York from the 23rd, not 08:00.
        val form = york("20241028T090000", occurrence = london("20241028T090000"), series = london("20241023T090000"))
        val tree = components(form, window(), Scope.ALL, LONDON)
        assertEquals("THE POINT: the series at 09:00 New York time, not 08:00", CARRIED, carries(tree))
    }

    @Test
    fun `a series moved into another zone from one occurrence on takes its overrides and dates onto that zone's clock`() {
        val carried = window("20241020")
        val cut = london("20241023T090000")
        val (_, after) = org.mochios.calendars.ui.editor.split(carried, york("20241023T090000"), cut, LONDON)!!
        assertEquals("THE POINT: each an hour before where London's clock puts it", CARRIED, carries(after))
    }

    @Test
    fun `a copy of all events from an occurrence set to another zone lands on the series' start by that zone's clock`() {
        val form = york("20241028T090000", occurrence = london("20241028T090000"), series = london("20241023T090000"))
        val copy = duplicate(form, Scope.ALL, window(), LONDON)
        assertEquals(
            "THE POINT: 09:00 to 10:00 New York time on the 23rd, not 08:00 to 09:00",
            listOf(at("20241023T090000", NEW_YORK), at("20241023T100000", NEW_YORK)),
            listOf(copy.start, copy.finish),
        )
    }

    @Test
    fun `a series set to another zone at the same instant takes its overrides and dates onto that zone's clock`() {
        // 09:00 in London on the 23rd is 04:00 in New York: the series starts
        // where it did, but in the week between the clock changes its
        // occurrences fall an hour before London's 09:00, and what it carries
        // goes with them: the override still five hours on from its own.
        val form = york("20241023T040000")
        assertEquals(london("20241023T090000"), form.start)
        assertEquals(
            "THE POINT: each an hour before where it was",
            listOf(
                "20241023T040000",
                NEW_YORK,
                "20241029T040000",
                "20241029T130000",
                "20241029T140000",
                "20241030T080000",
                "20241031T170000",
            ),
            carries(components(form, window(), Scope.ALL, LONDON)),
        )
    }

    // ---- a stored time in an hour the clocks read twice or skip ----

    /**
     * A daily series in [zone] from [from] to [until], `yyyyMMdd'T'HHmmss`,
     * with [added] added and moved two hours on by an override of its own.
     */
    private fun added(zone: String, from: String, until: String, added: String, moved: String): List<EventComponent> = listOf(
        EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("UID", "uid-1@mochi"),
                property("SUMMARY", "Night shift"),
                property("DTSTART", from, "TZID", zone),
                property("DTEND", until, "TZID", zone),
                property("RRULE", "FREQ=DAILY"),
                property("RDATE", added, "TZID", zone),
            ),
        ),
        EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("UID", "uid-1@mochi"),
                property("SUMMARY", "Night shift, late"),
                property("RECURRENCE-ID", added, "TZID", zone),
                property("DTSTART", moved, "TZID", zone),
            ),
        ),
    )

    /**
     * Where [carried] cut at [cut] by [split] puts the added date and its
     * override: the old series' added dates and overrides, then the new
     * one's, and whether the override names the added occurrence at
     * [occurrence], where the server lists it.
     */
    private fun halves(carried: List<EventComponent>, cut: Long, occurrence: Long, user: String): List<Any> {
        val form = draft(carried[0], user, cut)
        val (before, after) = org.mochios.calendars.ui.editor.split(carried, form, cut, user)!!
        fun dates(tree: List<EventComponent>) = tree[0].all("RDATE").map { it.value }
        fun overrides(tree: List<EventComponent>) = tree.drop(1).map { it.value("RECURRENCE-ID") }
        return listOf(dates(before), overrides(before), dates(after), overrides(after), matches(carried[1], occurrence, user))
    }

    @Test
    fun `a stored time London's clocks read twice is read where the server places it`() {
        // A daily series at 01:00 from Friday 25 October 2024, with 01:30 on
        // Sunday the 27th added, when London's 01:00 to 02:00 comes round
        // twice, and cut at that Sunday's 01:00: the server places both in
        // winter time, the second time round, 01:00 and 01:30 UTC, so the
        // added date falls after the cut and goes with the new series.
        val carried = added(LONDON, "20241025T010000", "20241025T020000", "20241027T013000", "20241027T033000")
        assertEquals(
            "THE POINT: 01:30 in winter time, after the cut, not in summer time before it",
            listOf(emptyList<String>(), emptyList<String>(), listOf("20241027T013000"), listOf("20241027T013000"), true),
            halves(carried, 1_729_990_800L, 1_729_992_600L, LONDON),
        )
    }

    @Test
    fun `a stored time New York's clocks skip is read where the server places it`() {
        // A daily series at 03:00 from Friday 8 March 2024, with 02:30 on
        // Sunday the 10th added, which New York's clocks skip, and cut at
        // that Sunday's 03:00: the server places 02:30 an hour back, at
        // 01:30 in winter time, 06:30 UTC, so the added date falls before
        // the cut at 07:00 UTC and stays with the old series.
        val carried = added(NEW_YORK, "20240308T030000", "20240308T040000", "20240310T023000", "20240310T043000")
        assertEquals(
            "THE POINT: an hour back, before the cut, not an hour on after it",
            listOf(listOf("20240310T023000"), listOf("20240310T023000"), emptyList<String>(), emptyList<String>(), true),
            halves(carried, 1_710_054_000L, 1_710_052_200L, NEW_YORK),
        )
    }

    // ---- a series stored in UTC ----

    /**
     * A weekly series stored in UTC, at 09:00 UTC from Tuesday 22 September
     * 2026, 10:00 in London's summer time, with 3 November, in its winter
     * time, excluded and 10 November moved to 14:00.
     */
    private fun universal(): List<EventComponent> = listOf(
        EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("UID", "uid-1@mochi"),
                property("SUMMARY", "Stand-up"),
                property("DTSTART", "20260922T090000Z"),
                property("DTEND", "20260922T100000Z"),
                property("RRULE", "FREQ=WEEKLY"),
                property("EXDATE", "20261103T090000Z"),
            ),
        ),
        EventComponent(
            name = "VEVENT",
            properties = listOf(
                property("UID", "uid-1@mochi"),
                property("SUMMARY", "Stand-up, late"),
                property("RECURRENCE-ID", "20261110T090000Z"),
                property("DTSTART", "20261110T140000Z"),
                property("DTEND", "20261110T150000Z"),
            ),
        ),
    )

    /**
     * What [tree]'s series says of when it happens: its start as written,
     * the instants of its first eight weeks less those it excludes, each a
     * week on by the clock of the zone its start is in, and its override's
     * occurrence and start.
     */
    private fun schedule(tree: List<EventComponent>): List<Any?> {
        val master = tree.first { it.property("RECURRENCE-ID") == null }
        val late = tree.single { it.property("RECURRENCE-ID") != null }
        val starts = master.property("DTSTART")!!
        val first = java.time.Instant.ofEpochMilli(CalendarsMapping.moment(starts, LONDON))
            .atZone(java.time.ZoneId.of(CalendarsMapping.zone(starts, LONDON)))
        val excluded = master.all("EXDATE").map { CalendarsMapping.moment(it, LONDON) / 1000 }.toSet()
        val weeks = (0L until 8L).map { first.plusWeeks(it).toEpochSecond() }.filterNot { it in excluded }
        return listOf(
            starts,
            weeks,
            CalendarsMapping.moment(late.property("RECURRENCE-ID")!!, LONDON) / 1000,
            CalendarsMapping.moment(late.property("DTSTART")!!, LONDON) / 1000,
        )
    }

    @Test
    fun `a series stored in UTC renamed with all events stays in UTC and keeps every occurrence`() {
        // The editor opened on 29 September and only the title changed.
        val carried = universal()
        val form = draft(carried[0], LONDON, NEXT).copy(title = "Stand-up, renamed", occurrence = NEXT, series = TEN)
        val tree = components(form, carried, Scope.ALL, LONDON)
        assertEquals(
            "THE POINT: renamed, still 09:00 UTC, so its winter weeks stay at 09:00 UTC, not 10:00",
            listOf<Any?>("Stand-up, renamed") + schedule(carried),
            listOf<Any?>(tree[0].value("SUMMARY")) + schedule(tree),
        )
    }

    // ---- a time typed or moved into an hour the clocks skip or read twice ----
    //
    // Each is placed where the server places it, Go's time.Date: New York's
    // skipped 02:30 on 10 March 2024 an hour back, at 01:30 in winter time,
    // and London's 01:30 on 27 October 2024, which comes round twice, in
    // winter time. The platform would put the first an hour on and the
    // second in summer time.

    private val HAVANA = "America/Havana"
    private val AMMAN = "Asia/Amman"

    /** An instant, written in UTC. */
    private fun utc(text: String) = java.time.Instant.parse(text).epochSecond

    @Test
    fun `a time moved to a zone whose clocks skip or read it twice lands where the server places it`() {
        // 02:30 and 01:30 by UTC's clock, moved onto New York's and London's.
        assertEquals(
            "THE POINT: 01:30 in New York's winter time, and London's 01:30 in winter time",
            listOf(utc("2024-03-10T06:30:00Z"), utc("2024-10-27T01:30:00Z")),
            listOf(
                moved(utc("2024-03-10T02:30:00Z"), CalendarsMapping.UTC, NEW_YORK),
                moved(utc("2024-10-27T01:30:00Z"), CalendarsMapping.UTC, LONDON),
            ),
        )
    }

    @Test
    fun `a form moved a day onto a time the clocks skip or read twice lands where the server places it`() {
        // An hour long, from 02:30 on 9 March in New York and from 01:30 on
        // 26 October in London, each a day on.
        val york = form(start = at("20240309T023000", NEW_YORK), finish = at("20240309T033000", NEW_YORK))
            .copy(zone = Zone(NEW_YORK, NEW_YORK))
        val london = form(start = at("20241026T013000", LONDON), finish = at("20241026T023000", LONDON))
        assertEquals(
            "THE POINT: each start where the server places it; the ends exist once",
            listOf(utc("2024-03-10T06:30:00Z"), utc("2024-03-10T07:30:00Z"), utc("2024-10-27T01:30:00Z"), utc("2024-10-27T02:30:00Z")),
            listOf(advanced(york, 1), advanced(london, 1)).flatMap { listOf(it.start, it.finish) },
        )
    }

    @Test
    fun `an all-day form begins at a midnight the clocks skip or read twice where the server places it`() {
        // Havana's clocks go from midnight to 01:00 on 10 March 2024, and
        // Amman's back from 01:00 to midnight on 30 October 2020: the server
        // places Havana's midnight an hour back, at 23:00 the evening before,
        // and Amman's the second time round.
        val day = { date: LocalDate -> EventForm(allday = true, start = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond()) }
        assertEquals(
            "THE POINT: an hour back in Havana, the second midnight in Amman",
            listOf(utc("2024-03-10T04:00:00Z"), utc("2020-10-29T22:00:00Z")),
            listOf(instant(day(LocalDate.of(2024, 3, 10)), HAVANA), instant(day(LocalDate.of(2020, 10, 30)), AMMAN)),
        )
    }

    @Test
    fun `days picked whose working hours the clocks skip or read twice begin where the server places them`() {
        // Working hours from 02:00 on New York's 10 March and from 01:00 on
        // London's 27 October.
        val now = java.time.LocalDateTime.of(2026, 1, 1, 12, 0)
        val york = picked(LocalDate.of(2024, 3, 10), LocalDate.of(2024, 3, 10), Hours(start = 2), 60, ZoneId.of(NEW_YORK), now)
        val london = picked(LocalDate.of(2024, 10, 27), LocalDate.of(2024, 10, 27), Hours(start = 1), 60, ZoneId.of(LONDON), now)
        assertEquals(
            "THE POINT: 01:00 in New York's winter time, and London's 01:00 in winter time",
            listOf(utc("2024-03-10T06:00:00Z"), utc("2024-03-10T07:00:00Z"), utc("2024-10-27T01:00:00Z"), utc("2024-10-27T02:00:00Z")),
            listOf(york.first, york.second, london.first, london.second),
        )
    }

    @Test
    fun `an all-day override is found where the server lists it at a midnight the clocks skip or read twice`() {
        // An all-day daily series whose 9 March 2024 in Havana moved to the
        // 10th, and whose 29 October 2020 in Amman moved to the 30th: each
        // listed at the server's midnight of the day it moved to.
        fun tree(from: String, replaced: String, moved: String, ends: String) = listOf(
            EventComponent(
                name = "VEVENT",
                properties = listOf(
                    property("UID", "uid-1@mochi"),
                    property("DTSTART", from, "VALUE", "DATE"),
                    property("RRULE", "FREQ=DAILY"),
                ),
            ),
            EventComponent(
                name = "VEVENT",
                properties = listOf(
                    property("UID", "uid-1@mochi"),
                    property("RECURRENCE-ID", replaced, "VALUE", "DATE"),
                    property("DTSTART", moved, "VALUE", "DATE"),
                    property("DTEND", ends, "VALUE", "DATE"),
                ),
            ),
        )
        assertEquals(
            "THE POINT: the occurrence each replaces, its own midnight, not the listed start handed back",
            listOf(
                LocalDate.of(2024, 3, 9).atStartOfDay(ZoneId.of(HAVANA)).toEpochSecond(),
                LocalDate.of(2020, 10, 29).atStartOfDay(ZoneId.of(AMMAN)).toEpochSecond(),
            ),
            listOf(
                original(tree("20240301", "20240309", "20240310", "20240311"), utc("2024-03-10T04:00:00Z"), HAVANA),
                original(tree("20201001", "20201029", "20201030", "20201031"), utc("2020-10-29T22:00:00Z"), AMMAN),
            ),
        )
    }
}
