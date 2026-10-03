// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.navigation.CalendarsApp
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.storage.VisibilityStore
import org.mochios.calendars.ui.calendar.CalendarEvent
import org.mochios.calendars.ui.calendar.CalendarViewModel
import org.mochios.calendars.ui.calendar.Moved
import org.mochios.calendars.ui.calendar.step
import org.mochios.calendars.ui.router.CalendarsSection
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The calendar screen's state against a server, as the web's: only the
 * calendars shown are fetched, a month's step lands on its 1st, search
 * reaches the list from anywhere, a failed refresh keeps what is on screen,
 * and a reminder opens the day of its occurrence and then the occurrence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarFlowTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: CalendarsRepository
    private val asked = ConcurrentLinkedQueue<RecordedRequest>()
    private val london = ZoneId.of("Europe/London")

    /** 2026-10-05 10:00 in London, a Monday. */
    private val start = LocalDate.of(2026, 10, 5).atTime(10, 0).atZone(london).toEpochSecond()

    /** Which calendars hold the stand-up, and whether the events listing fails. */
    @Volatile private var holding = "c1"
    @Volatile private var failing = false

    /** The stored event's version: a write must name it, as the server's etags do. */
    @Volatile private var version = 1

    /** Whether calendar changes fail, and whether there is an address to revoke. */
    @Volatile private var refusing = false
    @Volatile private var revocable = true

    /** Whether the stored event is all day on Thursday 8 October, and the last write's body. */
    @Volatile private var whole = false
    @Volatile private var written = ""

    @Before
    fun begin() {
        VisibilityStore.hidden(context, emptySet())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked.add(request)
                val path = request.path.orEmpty().substringAfter("/calendars/").substringBefore("?")
                val query = request.requestUrl?.queryParameter("calendars").orEmpty()
                return when (path) {
                    "-/calendars" -> ok(
                        """{"calendars": [{"id": "c1", "name": "Home", "default": true}, {"id": "c2", "name": "Work"}]}""",
                    )
                    "-/preferences/get", "-/preferences/set" -> ok("""{"preferences": {}}""")
                    "-/events/bounds" -> ok("""{"first": 0, "last": 0, "endless": false}""")
                    "-/events/get" -> ok(stored())
                    "-/calendars/rename", "-/calendars/colour", "-/calendars/delete" ->
                        if (refusing) MockResponse().setResponseCode(500).setBody("""{"error": "down"}""") else ok("{}")
                    "-/link/revoke" -> ok("""{"revoked": $revocable}""")
                    "-/events/update" -> {
                        val body = request.body.readUtf8()
                        written = body
                        if (body.contains("\"etag\":\"v$version\"")) {
                            version++
                            ok(stored()).setBodyDelay(300, java.util.concurrent.TimeUnit.MILLISECONDS)
                        } else {
                            MockResponse().setResponseCode(412).setBody("""{"error": "changed"}""")
                        }
                    }
                    "-/events" -> when {
                        failing -> MockResponse().setResponseCode(500).setBody("""{"error": "down"}""")
                        holding in query.split(",") -> ok(
                            """{"instances": [{"event": "e1", "calendar": "$holding", "summary": "Stand-up",
                                "start": $start, "finish": ${start + 3_600}}], "truncated": false}""",
                        )
                        else -> ok("""{"instances": [], "truncated": false}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/calendars/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = CalendarsRepository(
            retrofit.create(CalendarsApi::class.java),
            retrofit.create(MenuApi::class.java),
            FileStore(context),
        )
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun stored() = """{"event": {"id": "e1", "calendar": "$holding", "etag": "v$version", "components": [
        {"name": "VEVENT", "properties": [
            {"name": "UID", "params": {}, "value": "uid-1"},
            {"name": "SUMMARY", "params": {}, "value": "Stand-up"},
            ${if (whole) DAY else TIMED}
        ], "components": []}
    ]}}"""

    private val TIMED = """{"name": "DTSTART", "params": {}, "value": "20261005T090000Z"},
            {"name": "DTEND", "params": {}, "value": "20261005T100000Z"}"""

    private val DAY = """{"name": "DTSTART", "params": {"VALUE": ["DATE"]}, "value": "20261008"},
            {"name": "DTEND", "params": {"VALUE": ["DATE"]}, "value": "20261009"}"""

    /** Runs the main thread until [done], the server answering on its own. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun listings() = asked.filter { it.path.orEmpty().contains("/-/events?") }

    private fun model(): CalendarViewModel = CalendarViewModel(context, repository, London).also { model ->
        until { !model.uiState.value.isLoading && listings().isNotEmpty() }
    }

    // ---- only the calendars shown ----

    @Test
    fun `only the calendars shown are fetched, and nothing once every one is hidden`() {
        VisibilityStore.hidden(context, setOf("c2"))
        val model = model()
        assertEquals("c1", listings().last().requestUrl?.queryParameter("calendars"))
        val before = listings().size
        VisibilityStore.hidden(context, setOf("c1", "c2"))
        until { model.uiState.value.instances.isEmpty() && !model.uiState.value.isRefreshing }
        assertEquals(before, listings().size)
        assertFalse(model.uiState.value.truncated)
    }

    @Test
    fun `a calendar shown again is fetched again`() {
        VisibilityStore.hidden(context, setOf("c2"))
        val model = model()
        VisibilityStore.reveal(context, "c2")
        until { listings().last().requestUrl?.queryParameter("calendars") == "c1,c2" }
        assertTrue(model.uiState.value.hidden.isEmpty())
    }

    // ---- stepping and new events ----

    @Test
    fun `a month's step and the list's land on the 1st`() {
        assertEquals(LocalDate.of(2026, 2, 1), step(CalendarsSection.MONTH, LocalDate.of(2026, 1, 31), 1))
        assertEquals(LocalDate.of(2026, 2, 1), step(CalendarsSection.LIST, LocalDate.of(2026, 3, 15), -1))
        assertEquals(LocalDate.of(2026, 3, 16), step(CalendarsSection.DAY, LocalDate.of(2026, 3, 15), 1))
        assertEquals(LocalDate.of(2026, 3, 22), step(CalendarsSection.WEEK, LocalDate.of(2026, 3, 15), 1))
    }

    @Test
    fun `a new event in the list lands on the day the list is on, unless that is today`() {
        val model = model()
        model.view(CalendarsSection.LIST)
        val today = LocalDate.now(london)
        // Ten days back, today is well inside the list's page, which reaches
        // months on; the list is on the earlier day all the same.
        model.anchor(today.minusDays(10))
        assertEquals(today.minusDays(10), Instant.ofEpochSecond(model.creation()).atZone(london).toLocalDate())
        model.anchor(today)
        assertEquals(today, Instant.ofEpochSecond(model.creation()).atZone(london).toLocalDate())
    }

    // ---- search, views and refreshing ----

    @Test
    fun `search from the toolbar opens the list, and leaving the list lets the search go`() {
        val model = model()
        model.seek()
        assertEquals(CalendarsSection.LIST, model.uiState.value.view)
        assertEquals(1, model.uiState.value.seeking)
        model.search("lunch")
        model.view(CalendarsSection.MONTH)
        assertEquals("", model.uiState.value.search)
    }

    @Test
    fun `opening a day from a heading saves Day as the view`() {
        val model = model()
        model.open(LocalDate.of(2026, 10, 7))
        until { asked.any { it.path.orEmpty().endsWith("-/preferences/set") } }
        val set = asked.first { it.path.orEmpty().endsWith("-/preferences/set") }
        assertTrue(set.body.readUtf8().contains("day"))
        assertEquals(CalendarsSection.DAY, model.uiState.value.view)
    }

    @Test
    fun `a failed refresh keeps the events on screen and says so once`() {
        val model = model()
        model.anchor(LocalDate.of(2026, 10, 5))
        until { model.uiState.value.instances.isNotEmpty() }
        failing = true
        model.load(refreshing = true, reset = false)
        until { model.uiState.value.stale != null }
        assertEquals(1, model.uiState.value.instances.size)
        assertNull(model.uiState.value.error)
        model.told()
        assertNull(model.uiState.value.stale)
    }

    // ---- moving ----

    @Test
    fun `two quick moves both land, the second after the first`() {
        val model = model()
        val said = mutableListOf<CalendarEvent>()
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        scope.launch { model.events.collect { said += it } }
        scopes += scope
        val instance = org.mochios.calendars.model.Instance(event = "e1", calendar = "c1", start = start, finish = start + 3_600)
        model.move(instance, start + 3_600, start + 7_200, org.mochios.calendars.ui.editor.Scope.ALL)
        model.move(instance, start + 7_200, start + 10_800, org.mochios.calendars.ui.editor.Scope.ALL)
        until { said.count { it is CalendarEvent.Moved || it is CalendarEvent.Changed } == 2 }
        assertEquals(listOf(CalendarEvent.Moved, CalendarEvent.Moved), said.filter { it is CalendarEvent.Moved || it is CalendarEvent.Changed })
        assertEquals(3, version)
    }

    @Test
    fun `a block dropped in the band is written all day from the day it landed on`() {
        val model = model()
        val said = mutableListOf<CalendarEvent>()
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        scope.launch { model.events.collect { said += it } }
        scopes += scope
        val instance = org.mochios.calendars.model.Instance(event = "e1", calendar = "c1", start = start, finish = start + 3_600)
        model.move(instance, Moved.Whole(LocalDate.of(2026, 10, 7)), org.mochios.calendars.ui.editor.Scope.ALL)
        until { said.isNotEmpty() }
        assertEquals(CalendarEvent.Moved, said.single())
        assertTrue(written, written.contains("\"value\":\"20261007\""))
        assertTrue(written, written.contains("\"value\":\"20261008\""))
        assertTrue(written, written.contains("DATE"))
    }

    @Test
    fun `a bar dropped in the grid is written timed on the day it landed on, as long as a new event`() {
        whole = true
        val model = model()
        val said = mutableListOf<CalendarEvent>()
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        scope.launch { model.events.collect { said += it } }
        scopes += scope
        val midnight = LocalDate.of(2026, 10, 8).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()
        val instance = org.mochios.calendars.model.Instance(
            event = "e1", calendar = "c1", start = midnight, finish = midnight + 86_400, allday = true, date = "2026-10-08",
        )
        model.move(instance, Moved.Timed(LocalDate.of(2026, 10, 9), 11.25f), org.mochios.calendars.ui.editor.Scope.ALL)
        until { said.isNotEmpty() }
        assertEquals(CalendarEvent.Moved, said.single())
        // 11:15 to 12:15 on Friday, the default hour long, by the London clock.
        assertTrue(written, written.contains("20261009T111500"))
        assertTrue(written, written.contains("20261009T121500"))
    }

    // ---- calendar actions ----

    private fun said(model: CalendarViewModel): MutableList<CalendarEvent> {
        val said = mutableListOf<CalendarEvent>()
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        scope.launch { model.events.collect { said += it } }
        scopes += scope
        return said
    }

    @Test
    fun `a rename says it is done and only then closes its dialog`() {
        val model = model()
        val said = said(model)
        var closed = false
        model.rename("c1", "Family") { closed = true }
        assertTrue(model.working.value)
        until { said.isNotEmpty() }
        assertEquals(CalendarEvent.Done(R.string.calendars_renamed), said.single())
        assertTrue(closed)
        assertFalse(model.working.value)
    }

    @Test
    fun `a rename that fails says why and leaves its dialog open`() {
        refusing = true
        val model = model()
        val said = said(model)
        var closed = false
        model.rename("c1", "Family") { closed = true }
        until { said.isNotEmpty() }
        assertTrue(said.single() is CalendarEvent.Failed)
        assertFalse(closed)
    }

    @Test
    fun `removing a subscription says it is removed, and deleting a calendar says deleted`() {
        val model = model()
        val said = said(model)
        model.remove(org.mochios.calendars.model.Calendar(id = "c2", name = "Holidays", kind = org.mochios.calendars.model.Calendar.KIND_SUBSCRIPTION))
        until { said.size == 1 }
        model.remove(org.mochios.calendars.model.Calendar(id = "c2", name = "Work"))
        until { said.size == 2 }
        assertEquals(
            listOf(CalendarEvent.Done(R.string.calendars_removed), CalendarEvent.Done(R.string.calendars_deleted)),
            said,
        )
    }

    @Test
    fun `revoking says whether there was an address to revoke`() {
        val model = model()
        val said = said(model)
        model.revokeLink("c1")
        until { said.size == 1 }
        revocable = false
        model.revokeLink("c1")
        until { said.size == 2 }
        assertEquals(
            listOf(CalendarEvent.Done(R.string.calendars_link_revoked), CalendarEvent.Done(R.string.calendars_link_none)),
            said,
        )
    }

    @Test
    fun `a calendar made, subscribed to or linked is said on the calendar it returns to`() {
        assertEquals(R.string.calendars_created, CalendarsApp.said(CalendarsApp.CALENDAR))
        assertEquals(R.string.calendars_subscribed, CalendarsApp.said(CalendarsApp.SUBSCRIBED))
        assertEquals(R.string.calendars_linked, CalendarsApp.said(CalendarsApp.LINKED))
    }

    // ---- reminders ----

    private fun opened(model: CalendarViewModel): MutableList<CalendarEvent.Open> {
        val seen = mutableListOf<CalendarEvent.Open>()
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        scope.launch { model.events.collect { if (it is CalendarEvent.Open) seen += it } }
        scopes += scope
        return seen
    }

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun quiet() = scopes.forEach { it.cancel() }

    @Test
    fun `a reminder opens the day it falls on, then its occurrence`() {
        val model = model()
        val seen = opened(model)
        model.remind("e1", start, LocalDate.of(2026, 10, 5))
        assertEquals(CalendarsSection.DAY, model.uiState.value.view)
        assertEquals(LocalDate.of(2026, 10, 5), model.uiState.value.anchor)
        until { seen.isNotEmpty() }
        assertEquals("e1", seen.single().instance.event)
    }

    @Test
    fun `a reminder whose calendar is hidden shows that calendar, then opens the occurrence`() {
        holding = "c2"
        VisibilityStore.hidden(context, setOf("c2"))
        val model = model()
        val seen = opened(model)
        model.remind("e1", start, LocalDate.of(2026, 10, 5))
        until { seen.isNotEmpty() }
        assertNotNull(seen.single().instance)
        assertFalse("c2" in VisibilityStore.hidden(context))
    }
}
