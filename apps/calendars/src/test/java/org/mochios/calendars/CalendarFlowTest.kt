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
import kotlinx.coroutines.runBlocking
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
import org.mochios.calendars.ui.editor.defaultStart
import org.mochios.calendars.ui.router.CalendarsSection
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    /** The preferences the server holds, as its JSON. */
    @Volatile private var preferences = "{}"

    /**
     * Held until released: the preferences write, and a listing that starts
     * at or after [held], which answers with a concert at its start. [last]
     * is the last event the bounds report.
     */
    @Volatile private var saving: CountDownLatch? = null
    @Volatile private var paging: CountDownLatch? = null
    @Volatile private var held = Long.MAX_VALUE

    /** Holds the calendars list's answer until released. */
    @Volatile private var hold: CountDownLatch? = null

    /** Holds the stored event's read until released, or with [lost], refuses it; [refusals] refuses only that many reads. */
    @Volatile private var reading: CountDownLatch? = null
    @Volatile private var lost = false
    @Volatile private var refusals = 0

    /** Holds every read [refusals] lets through until released. */
    @Volatile private var stall: CountDownLatch? = null

    /** Holds each write of the stored event, a move's, until released. */
    @Volatile private var writes: CountDownLatch? = null

    /** Another occurrence of the stand-up, listed in a range that holds it, or 0 for none. */
    @Volatile private var later = 0L

    /** Whether a write is kept, so the next read of the event answers what it wrote, and what it wrote. */
    @Volatile private var keeping = false
    @Volatile private var kept: String? = null

    /** Whether every events listing answers slowly, so a newer load overtakes it. */
    @Volatile private var slow = false
    @Volatile private var last = 0L

    /** More occurrences of the stand-up, listed in a range that holds their start: each start and its JSON. */
    @Volatile private var extra: List<Pair<Long, String>> = emptyList()

    /**
     * What a listing of the stand-up's calendar answers once a split has cut
     * the series, the occurrences' JSON, and [following], what a split sets
     * it to.
     */
    @Volatile private var cut: String? = null
    @Volatile private var following = ""

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
                    "-/calendars" -> {
                        hold?.await(10, TimeUnit.SECONDS)
                        ok("""{"calendars": [{"id": "c1", "name": "Home", "default": true}, {"id": "c2", "name": "Work"}]}""")
                    }
                    "-/preferences/get" -> ok("""{"preferences": $preferences}""")
                    "-/preferences/set" -> {
                        saving?.await(10, TimeUnit.SECONDS)
                        ok("""{"preferences": {"view": "list"}}""")
                    }
                    "-/events/bounds" -> ok("""{"first": 0, "last": $last, "endless": false}""")
                    "-/events/get" -> {
                        reading?.await(10, TimeUnit.SECONDS)
                        val refused = refusals > 0
                        if (refused) refusals-- else stall?.await(10, TimeUnit.SECONDS)
                        if (lost || refused) MockResponse().setResponseCode(500).setBody("""{"error": "down"}""") else ok(stored())
                    }
                    "-/calendars/rename", "-/calendars/colour", "-/calendars/delete" ->
                        if (refusing) MockResponse().setResponseCode(500).setBody("""{"error": "down"}""") else ok("{}")
                    "-/link/revoke" -> ok("""{"revoked": $revocable}""")
                    "-/events/update" -> {
                        writes?.await(10, TimeUnit.SECONDS)
                        val body = request.body.readUtf8()
                        written = body
                        if (body.contains("\"etag\":\"v$version\"")) {
                            version++
                            if (keeping) kept = com.google.gson.JsonParser.parseString(body).asJsonObject.get("components").toString()
                            ok(stored()).setBodyDelay(300, java.util.concurrent.TimeUnit.MILLISECONDS)
                        } else {
                            MockResponse().setResponseCode(412).setBody("""{"error": "changed"}""")
                        }
                    }
                    "-/events/split" -> {
                        writes?.await(10, TimeUnit.SECONDS)
                        written = request.body.readUtf8()
                        version++
                        cut = following
                        ok(
                            """{"event": {"id": "e1", "calendar": "$holding", "etag": "v$version", "recurring": true, "components": []},
                                "following": {"id": "e3", "calendar": "$holding", "etag": "w1", "recurring": true, "components": []}}""",
                        )
                    }
                    "-/events" -> when {
                        (request.requestUrl?.queryParameter("start")?.toLongOrNull() ?: 0) >= held -> {
                            paging?.await(10, TimeUnit.SECONDS)
                            val from = request.requestUrl?.queryParameter("start")?.toLongOrNull() ?: 0
                            ok(
                                """{"instances": [{"event": "e2", "calendar": "c1", "summary": "Concert",
                                    "start": $from, "finish": ${from + 3_600}}], "truncated": false}""",
                            )
                        }
                        failing -> MockResponse().setResponseCode(500).setBody("""{"error": "down"}""")
                        holding in query.split(",") && cut != null -> ok("""{"instances": [$cut], "truncated": false}""")
                        holding in query.split(",") -> {
                            val from = request.requestUrl?.queryParameter("start")?.toLongOrNull() ?: 0
                            val until = request.requestUrl?.queryParameter("finish")?.toLongOrNull() ?: 0
                            val also = if (later != 0L && later >= from && later < until) {
                                """, {"event": "e1", "calendar": "$holding", "summary": "Stand-up",
                                    "start": $later, "finish": ${later + 3_600}, "recurring": $weekly}"""
                            } else {
                                ""
                            }
                            val more = extra.filter { (at, _) -> at >= from && at < until }.joinToString("") { (_, json) -> ", $json" }
                            ok(
                                """{"instances": [{"event": "e1", "calendar": "$holding", "summary": "Stand-up",
                                    "start": $start, "finish": ${start + 3_600}, "recurring": $weekly}$also$more], "truncated": false}""",
                            )
                        }
                        else -> ok("""{"instances": [], "truncated": false}""")
                    }.apply {
                        if (slow) {
                            setBodyDelay(300, TimeUnit.MILLISECONDS)
                        }
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
            context,
        )
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun stored() = kept?.let { components ->
        """{"event": {"id": "e1", "calendar": "$holding", "etag": "v$version", "recurring": $weekly, "components": $components}}"""
    } ?: """{"event": {"id": "e1", "calendar": "$holding", "etag": "v$version", "recurring": $weekly, "components": [
        {"name": "VEVENT", "properties": [
            {"name": "UID", "params": {}, "value": "uid-1"},
            {"name": "SUMMARY", "params": {}, "value": "Stand-up"},
            ${if (whole) DAY else TIMED}${if (weekly) WEEKLY else ""}
        ], "components": []}${if (weekly) LATE else ""}
    ]}}"""

    /** Whether the stored event repeats weekly, with the 12th's occurrence moved to Tuesday the 13th at 14:00. */
    @Volatile private var weekly = false

    private val WEEKLY = """, {"name": "RRULE", "params": {}, "value": "FREQ=WEEKLY"}"""

    private val LATE = """, {"name": "VEVENT", "properties": [
            {"name": "UID", "params": {}, "value": "uid-1"},
            {"name": "SUMMARY", "params": {}, "value": "Late"},
            {"name": "RECURRENCE-ID", "params": {"TZID": ["Europe/London"]}, "value": "20261012T100000"},
            {"name": "DTSTART", "params": {"TZID": ["Europe/London"]}, "value": "20261013T140000"},
            {"name": "DTEND", "params": {"TZID": ["Europe/London"]}, "value": "20261013T150000"}
        ], "components": []}"""

    private val TIMED = """{"name": "DTSTART", "params": {"TZID": ["Europe/London"]}, "value": "20261005T100000"},
            {"name": "DTEND", "params": {"TZID": ["Europe/London"]}, "value": "20261005T110000"}"""

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

    /** Runs the main thread for [millis], for a reply that leaves nothing changed to wait on. */
    private fun settle(millis: Long = 500) {
        val deadline = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
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
        // Today's starts at the next whole hour, which from 23:00 is the
        // start of tomorrow's working hours, as the web's does.
        val expected = defaultStart(today, today, LocalTime.now(london), model.uiState.value.preferences.hours).toLocalDate()
        assertEquals(expected, Instant.ofEpochSecond(model.creation()).atZone(london).toLocalDate())
    }

    @Test
    fun `a new event in the list lands on the day the list has scrolled to, until the anchor changes, and only in the list`() {
        val model = model()
        val day = { seconds: Long -> Instant.ofEpochSecond(seconds).atZone(london).toLocalDate() }
        val today = LocalDate.now(london)
        model.view(CalendarsSection.LIST)
        model.anchor(today.minusDays(10))
        model.listed(today.plusDays(40))
        assertEquals(today.plusDays(40), day(model.creation()))
        // A new anchor lets the scrolled day go.
        model.anchor(today.minusDays(20))
        assertEquals(today.minusDays(20), day(model.creation()))
        // Another view has no day atop it, and goes on the day last chosen.
        model.listed(today.plusDays(40))
        model.view(CalendarsSection.DAY)
        assertEquals(today.minusDays(20), day(model.creation()))
    }

    @Test
    fun `a new event from the toolbar lands on today while the view shows it, whatever day was chosen, as the web's does`() {
        val model = model()
        val day = { seconds: Long -> Instant.ofEpochSecond(seconds).atZone(london).toLocalDate() }
        val today = LocalDate.now(london)
        // Today's own start may be tomorrow's working hours, late at night.
        val expected = day(model.creation(today))
        model.view(CalendarsSection.MONTH)
        model.today()
        // A day tapped on the grid yesterday is the chosen one, and today still wins.
        model.focus(today.minusDays(1))
        assertEquals(expected, day(model.creation()))
        model.view(CalendarsSection.WEEK)
        assertEquals(expected, day(model.creation()))
        // A view that does not show today goes on the day chosen in it.
        model.anchor(today.plusWeeks(3))
        model.focus(today.plusWeeks(3).plusDays(1))
        assertEquals(today.plusWeeks(3).plusDays(1), day(model.creation()))
    }

    @Test
    fun `a preference changed elsewhere applies when the screen comes back, and the first read is not doubled`() {
        val model = CalendarViewModel(context, repository, London)
        // On screen at once, while the first read is still under way.
        model.revisit()
        until { !model.uiState.value.isLoading && listings().isNotEmpty() }
        settle()
        val reads = { asked.count { it.path.orEmpty().contains("/-/preferences/get") } }
        assertEquals(1, reads())
        assertEquals("first", model.uiState.value.preferences.allday)
        // Set to last on the web, then back to the phone.
        preferences = """{"allday": "last"}"""
        model.revisit()
        until { model.uiState.value.preferences.allday == "last" }
        assertEquals(2, reads())
    }

    @Test
    fun `an event saved behind the editor is read once, as the screen comes back`() {
        val model = model()
        settle()
        val start = listings().size
        model.onScreenPaused()
        model.onScreenResumed()
        until { listings().size > start }
        settle()
        val back = listings().size - start
        val before = listings().size
        model.onScreenPaused()
        runBlocking { repository.updateEvent("e1", "v$version", null, null) }
        settle()
        assertEquals(before, listings().size)
        model.onScreenResumed()
        until { listings().size > before }
        settle()
        assertEquals(before + back, listings().size)
    }

    /** The model loaded, and how many listings one read of its range makes: the period shown and its neighbours. */
    private fun loaded(): Pair<CalendarViewModel, Int> {
        val model = model()
        settle()
        return model to listings().size
    }

    private fun preferenceReads() = asked.count { it.path.orEmpty().contains("/-/preferences/get") }

    @Test
    fun `a return asks for the preferences while the calendars are still coming`() {
        val (model, _) = loaded()
        val gate = CountDownLatch(1)
        hold = gate
        model.onScreenPaused()
        model.onScreenResumed()
        try {
            until { preferenceReads() == 2 }
        } finally {
            gate.countDown()
        }
    }

    @Test
    fun `a return reads the range beside the calendars and preferences, and once when nothing changed`() {
        val (model, round) = loaded()
        val gate = CountDownLatch(1)
        hold = gate
        model.onScreenPaused()
        model.onScreenResumed()
        try {
            // The calendars' answer is held: the range is read for those already held.
            until { listings().size == 2 * round }
        } finally {
            gate.countDown()
        }
        until { preferenceReads() == 2 }
        settle()
        assertEquals("THE POINT: nothing changed, so the range was not read again", 2 * round, listings().size)
    }

    @Test
    fun `a preference changed elsewhere reads the range again once it is in`() {
        val (model, round) = loaded()
        preferences = """{"zones": true}"""
        val gate = CountDownLatch(1)
        hold = gate
        model.onScreenPaused()
        model.onScreenResumed()
        try {
            until { listings().size == 2 * round }
        } finally {
            gate.countDown()
        }
        until { model.uiState.value.preferences.zones }
        settle()
        assertEquals("THE POINT: read for what was held, then again for the change", 3 * round, listings().size)
    }

    @Test
    fun `a view left on today moves on to the new today when the date changes, and one moved elsewhere stays`() {
        val model = model()
        val today = LocalDate.now(london)
        model.view(CalendarsSection.WEEK)
        model.revisit(today.plusDays(1))
        assertEquals(today.plusDays(1), model.uiState.value.anchor)
        assertEquals(today.plusDays(1), model.uiState.value.focus)
        // Paged away, the view stays where it was put.
        model.anchor(today.minusWeeks(2))
        model.revisit(today.plusDays(2))
        assertEquals(today.minusWeeks(2), model.uiState.value.anchor)
        // The same day again reads the range and moves nothing.
        model.revisit(today.plusDays(2))
        assertEquals(today.minusWeeks(2), model.uiState.value.anchor)
    }

    @Test
    fun `a zone or first day of the week changed elsewhere is measured again in at once`() {
        val zone = java.util.concurrent.atomic.AtomicReference("Europe/London")
        val week = java.util.concurrent.atomic.AtomicInteger(1)
        val changes = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val viewer = object : org.mochios.calendars.di.Viewer {
            override fun zone(): String = zone.get()
            override fun week(): Int = week.get()
            override fun changes(): kotlinx.coroutines.flow.Flow<Unit> = changes
            override suspend fun server(): String = "https://example.org"
        }
        val model = CalendarViewModel(context, repository, viewer)
        until { !model.uiState.value.isLoading && listings().isNotEmpty() }
        model.view(CalendarsSection.WEEK)
        settle()
        // Sunday first, in Tokyo, set on the web while the week is on screen.
        val tokyo = ZoneId.of("Asia/Tokyo")
        zone.set(tokyo.id)
        week.set(0)
        changes.tryEmit(Unit)
        until {
            val anchor = model.uiState.value.anchor
            val sunday = anchor.minusDays((anchor.dayOfWeek.value % 7).toLong())
            listings().any { listing ->
                listing.requestUrl?.queryParameter("timezone") == tokyo.id &&
                    listing.requestUrl?.queryParameter("start") == sunday.atStartOfDay(tokyo).toEpochSecond().toString()
            }
        }
        assertEquals(LocalDate.now(tokyo), model.uiState.value.anchor)
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
    fun `opening the day the day view already shows reads nothing again`() {
        val model = model()
        val day = LocalDate.of(2026, 10, 7)
        model.open(day)
        until { !model.uiState.value.isRefreshing && listings().size >= 2 }
        settle()
        val before = asked.size
        model.open(day)
        settle()
        assertEquals(before, asked.size)
        assertEquals(CalendarsSection.DAY, model.uiState.value.view)
        assertEquals(day, model.uiState.value.anchor)
    }

    @Test
    fun `days moved through faster than they load leave no error behind`() {
        val model = model()
        model.view(CalendarsSection.DAY)
        until { !model.uiState.value.isRefreshing }
        settle()
        slow = true
        val today = LocalDate.now(london)
        val before = listings().size
        for (ahead in 1L..3L) {
            model.anchor(today.plusDays(ahead))
            until { listings().size > before + ahead - 1 }
        }
        settle(1_500)
        assertEquals(today.plusDays(3), model.uiState.value.anchor)
        assertNull(model.uiState.value.stale)
        assertNull(model.uiState.value.error)
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
    fun `a dropped block shows where it landed before the move is written`() {
        val model = model()
        model.anchor(LocalDate.of(2026, 10, 5))
        until { model.uiState.value.instances.isNotEmpty() }
        val said = said(model)
        reading = CountDownLatch(1)
        val instance = model.uiState.value.instances.single()
        model.move(instance, start + 3_600, start + 7_200, org.mochios.calendars.ui.editor.Scope.ALL)
        val shown = model.uiState.value.instances.single()
        assertEquals("THE POINT: at the new time while the stored event is still being read", start + 3_600, shown.start)
        assertEquals(start + 7_200, shown.finish)
        reading?.countDown()
        until { said.isNotEmpty() }
        assertEquals(CalendarEvent.Moved, said.single())
    }

    @Test
    fun `a move that cannot be written goes back to where the server has it`() {
        val model = model()
        model.anchor(LocalDate.of(2026, 10, 5))
        until { model.uiState.value.instances.isNotEmpty() }
        val said = said(model)
        lost = true
        val instance = model.uiState.value.instances.single()
        model.move(instance, Moved.Whole(LocalDate.of(2026, 10, 7)), org.mochios.calendars.ui.editor.Scope.ALL)
        assertTrue("drawn all day where it was dropped", model.uiState.value.instances.single().allday)
        until { said.isNotEmpty() }
        assertTrue(said.single() is CalendarEvent.Failed)
        until { !model.uiState.value.instances.single().allday }
        assertEquals("THE POINT: the range is read again, which puts it back", start, model.uiState.value.instances.single().start)
    }

    /**
     * A drag of an occurrence "This event" moved before moves its override
     * again, found by the occurrence it replaces, rather than adding another.
     * A core that does not name that occurrence lists the override only where
     * it now falls, and the override is found by its start.
     */
    @Test
    fun `a moved occurrence dragged again with this event moves its own override`() {
        for (replaced in listOf<Long?>(1_791_795_600, null)) {
            weekly = true
            written = ""
            val model = model()
            val said = mutableListOf<CalendarEvent>()
            val scope = CoroutineScope(Dispatchers.Main.immediate)
            scope.launch { model.events.collect { said += it } }
            scopes += scope
            // Listed at its own start, Tuesday 13 October 14:00 London.
            val late = LocalDate.of(2026, 10, 13).atTime(14, 0).atZone(london).toEpochSecond()
            val instance = org.mochios.calendars.model.Instance(
                event = "e1", calendar = "c1", start = late, finish = late + 3_600,
                recurring = true, exception = true, replaced = replaced,
            )
            model.move(instance, late + 3_600, late + 7_200, org.mochios.calendars.ui.editor.Scope.ONE)
            until { said.isNotEmpty() }
            assertEquals(CalendarEvent.Moved, said.single())
            assertEquals(written, 1, Regex("RECURRENCE-ID").findAll(written).count())
            assertTrue(written, written.contains("\"value\":\"Late\""))
            assertTrue(written, written.contains("\"value\":\"20261012T100000\""))
            assertTrue(written, written.contains("\"value\":\"20261013T150000\""))
        }
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

    @Test
    fun `a bar dropped in the grid on the day the clocks go back is drawn where it will be written`() {
        whole = true
        // Nothing listed, so the bar is the only occurrence drawn.
        holding = "c9"
        val model = model()
        val said = said(model)
        reading = CountDownLatch(1)
        // London's clocks go back at 02:00 on Sunday 25 October.
        val midnight = LocalDate.of(2026, 10, 24).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()
        val instance = org.mochios.calendars.model.Instance(
            event = "e1", calendar = "c1", start = midnight, finish = midnight + 86_400, allday = true, date = "2026-10-24",
        )
        model.move(instance, Moved.Timed(LocalDate.of(2026, 10, 25), 11.25f), org.mochios.calendars.ui.editor.Scope.ALL)
        assertEquals(
            "THE POINT: 11:15 by the London clock, where the write puts it",
            LocalDate.of(2026, 10, 25).atTime(11, 15).atZone(london).toEpochSecond(),
            model.uiState.value.instances.single().start,
        )
        reading?.countDown()
        until { said.isNotEmpty() }
        // The stored bar is on another day; the write reads the same clock time.
        assertTrue(written, written.contains("T111500"))
    }

    @Test
    fun `all events dragged across the clock change keeps the series at its time of day`() {
        weekly = true
        val model = model()
        val said = said(model)
        // Saturday 24 October 10:00 in summer time to Sunday 25 October 10:00
        // in winter time: a day on by the clock, 25 hours by the watch.
        val saturday = LocalDate.of(2026, 10, 24).atTime(10, 0).atZone(london).toEpochSecond()
        val sunday = LocalDate.of(2026, 10, 25).atTime(10, 0).atZone(london).toEpochSecond()
        val instance = org.mochios.calendars.model.Instance(
            event = "e1", calendar = "c1", start = saturday, finish = saturday + 3_600, recurring = true,
        )
        model.move(instance, sunday, sunday + 3_600, org.mochios.calendars.ui.editor.Scope.ALL)
        until { said.isNotEmpty() }
        assertEquals(CalendarEvent.Moved, said.single())
        // The series began Monday 5 October 10:00, so it now begins Tuesday 6 October 10:00.
        assertTrue("THE POINT: a day on, still at 10:00: $written", written.contains("\"value\":\"20261006T100000\""))
        assertFalse("not 11:00 to 12:00, 25 hours on: $written", written.contains("20261006T120000"))
    }

    @Test
    fun `all events dragged across the clock change keeps a series stored in UTC in UTC, its occurrence where it was dropped`() {
        weekly = true
        // Weekly at 09:00 UTC from Monday 5 October, 10:00 in London's summer time.
        kept = """[{"name": "VEVENT", "properties": [
            {"name": "UID", "params": {}, "value": "uid-1"},
            {"name": "SUMMARY", "params": {}, "value": "Stand-up"},
            {"name": "DTSTART", "params": {}, "value": "20261005T090000Z"},
            {"name": "DTEND", "params": {}, "value": "20261005T100000Z"},
            {"name": "RRULE", "params": {}, "value": "FREQ=WEEKLY"}
        ], "components": []}]"""
        val model = model()
        val said = said(model)
        // Saturday 24 October 10:00 in summer time to Sunday 25 October 10:00
        // in winter time: 25 hours on by the clock of UTC, which the series
        // repeats on.
        val saturday = LocalDate.of(2026, 10, 24).atTime(10, 0).atZone(london).toEpochSecond()
        val sunday = LocalDate.of(2026, 10, 25).atTime(10, 0).atZone(london).toEpochSecond()
        val instance = org.mochios.calendars.model.Instance(
            event = "e1", calendar = "c1", start = saturday, finish = saturday + 3_600, recurring = true,
        )
        model.move(instance, sunday, sunday + 3_600, org.mochios.calendars.ui.editor.Scope.ALL)
        until { said.isNotEmpty() }
        assertEquals(CalendarEvent.Moved, said.single())
        // From 09:00 UTC on the 5th to 10:00 UTC on the 6th, so Sunday's
        // occurrence falls at 10:00 UTC, 10:00 in London, where it was dropped.
        assertEquals(
            "THE POINT: still in UTC and 25 hours on, not a day on to 09:00 UTC or into London's zone",
            "20261006T100000Z",
            begins(written),
        )
    }

    /** An instant, written in UTC. */
    private fun utc(text: String) = Instant.parse(text).epochSecond

    /** A model whose user is in [zone], their week starting on Monday. */
    private fun model(zone: String): CalendarViewModel {
        val viewer = object : org.mochios.calendars.di.Viewer {
            override fun zone(): String = zone
            override fun week(): Int = 1
            override suspend fun server(): String = "https://example.org"
        }
        return CalendarViewModel(context, repository, viewer).also { model ->
            until { !model.uiState.value.isLoading }
        }
    }

    @Test
    fun `a series stored in UTC that the listing names UTC is drawn on UTC's clock when dragged across the clock change`() {
        weekly = true
        // The series at 09:00 UTC, listed in UTC: Saturday 24 October 2026,
        // 10:00 in London's summer time, and Monday the 26th, 09:00 in its
        // winter time, beside the 5 October occurrence every listing holds.
        val saturday = utc("2026-10-24T09:00:00Z")
        val monday = utc("2026-10-26T09:00:00Z")
        val listed = { at: Long ->
            at to """{"event": "e1", "calendar": "c1", "summary": "Stand-up", "start": $at, "finish": ${at + 3_600},
                "recurring": true, "zone": {"start": "UTC", "finish": "UTC"}}"""
        }
        extra = listOf(listed(saturday), listed(monday))
        val model = model()
        model.view(CalendarsSection.MONTH)
        model.anchor(LocalDate.of(2026, 10, 24))
        until { model.uiState.value.instances.map { it.start }.containsAll(listOf(saturday, monday)) }
        val said = said(model)
        reading = CountDownLatch(1)
        // Saturday's dropped at 10:00 on Sunday the 25th, in winter time: 25
        // hours on by UTC's clock, a day by London's.
        val instance = model.uiState.value.instances.first { it.start == saturday }
        model.move(instance, utc("2026-10-25T10:00:00Z"), utc("2026-10-25T11:00:00Z"), org.mochios.calendars.ui.editor.Scope.ALL)
        val drawn = model.uiState.value.instances.filter { it.event == "e1" }.map { it.start }.sorted()
        reading?.countDown()
        until { said.isNotEmpty() }
        assertEquals(
            "THE POINT: each 25 hours on, at 10:00 UTC, as the write puts the series, not a day on by London's clock",
            listOf(utc("2026-10-06T10:00:00Z"), utc("2026-10-25T10:00:00Z"), utc("2026-10-27T10:00:00Z")),
            drawn,
        )
    }

    // ---- an occurrence on a day the clocks skip, dragged to the next day ----
    //
    // New York's clocks go from 02:00 to 03:00 on 10 March 2024, so the
    // server lists a daily 02:30 series that day at 01:30 in winter time.

    /**
     * What dragging the 10th's occurrence of a daily New York series from
     * [day] at 02:30 onto the 11th for [scope] writes: the DTSTART of the
     * component that replaces the 10th's occurrence, or with no [replaced]
     * the master's.
     */
    private fun dragged(day: String, scope: org.mochios.calendars.ui.editor.Scope, replaced: String? = null): String? {
        weekly = true
        kept = """[{"name": "VEVENT", "properties": [
            {"name": "UID", "params": {}, "value": "uid-1"},
            {"name": "SUMMARY", "params": {}, "value": "Stand-up"},
            {"name": "DTSTART", "params": {"TZID": ["America/New_York"]}, "value": "${day}T023000"},
            {"name": "DTEND", "params": {"TZID": ["America/New_York"]}, "value": "${day}T033000"},
            {"name": "RRULE", "params": {}, "value": "FREQ=DAILY"}
        ], "components": []}]"""
        val tenth = utc("2024-03-10T06:30:00Z")
        extra = listOf(
            tenth to """{"event": "e1", "calendar": "c1", "summary": "Stand-up", "start": $tenth, "finish": ${tenth + 3_600},
                "recurring": true, "zone": {"start": "America/New_York", "finish": "America/New_York"}}""",
        )
        val model = model("America/New_York")
        model.view(CalendarsSection.MONTH)
        model.anchor(LocalDate.of(2024, 3, 10))
        until { model.uiState.value.instances.any { it.start == tenth } }
        val said = said(model)
        model.move(model.uiState.value.instances.first { it.start == tenth }, LocalDate.of(2024, 3, 11), scope)
        until { said.isNotEmpty() }
        return begins(written, replaced)
    }

    @Test
    fun `a series dragged whole from a later occurrence on a day the clocks skip keeps its time of day`() {
        assertEquals(
            "THE POINT: the series still at 02:30, from a day on",
            "20240302T023000",
            dragged("20240301", org.mochios.calendars.ui.editor.Scope.ALL),
        )
    }

    @Test
    fun `a series dragged whole from its first occurrence on a day the clocks skip keeps its time of day`() {
        assertEquals(
            "THE POINT: the series still at 02:30, from a day on, not 01:30",
            "20240311T023000",
            dragged("20240310", org.mochios.calendars.ui.editor.Scope.ALL),
        )
    }

    @Test
    fun `a series cut by dragging its occurrence on a day the clocks skip goes on at its time of day`() {
        dragged("20240301", org.mochios.calendars.ui.editor.Scope.FOLLOWING)
        val following = com.google.gson.JsonParser.parseString(written).asJsonObject.getAsJsonArray("following")[0].asJsonObject
        val start = following.getAsJsonArray("properties").map { it.asJsonObject }
            .first { it.get("name").asString == "DTSTART" }.get("value").asString
        assertEquals("THE POINT: the series goes on at 02:30 from the 11th, not 01:30", "20240311T023000", start)
    }

    @Test
    fun `one occurrence on a day the clocks skip dragged alone lands where it is drawn`() {
        assertEquals(
            "THE POINT: at 01:30 on the 11th, where the 10th's occurrence is drawn, a day on",
            "20240311T013000",
            dragged("20240301", org.mochios.calendars.ui.editor.Scope.ONE, replaced = "20240310"),
        )
    }

    /**
     * Where the 10th's and 11th's occurrences of a daily New York series at
     * 02:30 from the 1st are drawn once the 10th's is dragged onto the 11th
     * for [scope] and the stored event is read, while the write is out.
     */
    private fun drawn(scope: org.mochios.calendars.ui.editor.Scope): List<Long> =
        dragging { model, tenth -> model.move(tenth, LocalDate.of(2024, 3, 11), scope) }.first

    /**
     * Where the 10th's and 11th's occurrences of a daily New York series at
     * 02:30 from the 1st are drawn once [move] drags the 10th's and the
     * stored event is read, while the write is out, and then what the write
     * sends.
     */
    private fun dragging(move: (CalendarViewModel, org.mochios.calendars.model.Instance) -> Unit): Pair<List<Long>, String> {
        weekly = true
        kept = """[{"name": "VEVENT", "properties": [
            {"name": "UID", "params": {}, "value": "uid-1"},
            {"name": "SUMMARY", "params": {}, "value": "Stand-up"},
            {"name": "DTSTART", "params": {"TZID": ["America/New_York"]}, "value": "20240301T023000"},
            {"name": "DTEND", "params": {"TZID": ["America/New_York"]}, "value": "20240301T033000"},
            {"name": "RRULE", "params": {}, "value": "FREQ=DAILY"}
        ], "components": []}]"""
        val days = listOf(utc("2024-03-10T06:30:00Z"), utc("2024-03-11T06:30:00Z"))
        extra = days.map { at ->
            at to """{"event": "e1", "calendar": "c1", "summary": "Stand-up", "start": $at, "finish": ${at + 3_600},
                "recurring": true, "zone": {"start": "America/New_York", "finish": "America/New_York"}}"""
        }
        val model = model("America/New_York")
        model.view(CalendarsSection.MONTH)
        model.anchor(LocalDate.of(2024, 3, 10))
        until { model.uiState.value.instances.map { it.start }.containsAll(days) }
        writes = CountDownLatch(1)
        move(model, model.uiState.value.instances.first { it.start == days[0] })
        until { asked.any { it.path.orEmpty().contains("/-/events/update") || it.path.orEmpty().contains("/-/events/split") } }
        val starts = model.uiState.value.instances.filter { it.event == "e1" && it.start < utc("2025-01-01T00:00:00Z") }.map { it.start }.sorted()
        writes?.countDown()
        until { written.isNotEmpty() }
        return starts to written
    }

    /** What [dragging] gives when the 10th's is dropped at 03:30 in summer time for [scope]. */
    private fun dropped(scope: org.mochios.calendars.ui.editor.Scope) =
        dragging { model, tenth -> model.move(tenth, utc("2024-03-10T07:30:00Z"), utc("2024-03-10T08:30:00Z"), scope) }

    /** The DTSTART of the first component in [part] of the [written] body. */
    private fun starting(written: String, part: String): String =
        com.google.gson.JsonParser.parseString(written).asJsonObject.getAsJsonArray(part)[0].asJsonObject
            .getAsJsonArray("properties").map { it.asJsonObject }
            .first { it.get("name").asString == "DTSTART" }.get("value").asString

    @Test
    fun `a series dragged whole from its occurrence the clocks skip moves an hour on from its reading, drawn so`() {
        val (starts, written) = dropped(org.mochios.calendars.ui.editor.Scope.ALL)
        assertEquals(
            "THE POINT: the series at 03:30, an hour on from 02:30, and the 11th's drawn at 03:30, not 04:30",
            listOf(listOf(utc("2024-03-10T07:30:00Z"), utc("2024-03-11T07:30:00Z")), "20240301T033000"),
            listOf(starts, starting(written, "components")),
        )
    }

    @Test
    fun `a series cut by dragging its occurrence the clocks skip moves an hour on from its reading, drawn so`() {
        val (starts, written) = dropped(org.mochios.calendars.ui.editor.Scope.FOLLOWING)
        assertEquals(
            "THE POINT: the series from the 10th at 03:30, and the 11th's drawn at 03:30, not 04:30",
            listOf(listOf(utc("2024-03-10T07:30:00Z"), utc("2024-03-11T07:30:00Z")), "20240310T033000"),
            listOf(starts, starting(written, "following")),
        )
    }

    @Test
    fun `one occurrence the clocks skip dragged alone to half past three lands where it is dropped`() {
        val (starts, written) = dropped(org.mochios.calendars.ui.editor.Scope.ONE)
        assertEquals(
            "THE POINT: at 03:30 on the 10th, the 11th's left where it is",
            listOf(listOf(utc("2024-03-10T07:30:00Z"), utc("2024-03-11T06:30:00Z")), "20240310T033000"),
            listOf(starts, begins(written, "20240310")),
        )
    }

    @Test
    fun `a series cut by dragging its occurrence on a day the clocks skip is drawn where the write puts it`() {
        assertEquals(
            "THE POINT: the 10th's at 02:30 on the 11th, where the write puts it, not 01:30",
            listOf(utc("2024-03-11T06:30:00Z"), utc("2024-03-12T06:30:00Z")),
            drawn(org.mochios.calendars.ui.editor.Scope.FOLLOWING),
        )
    }

    @Test
    fun `a series dragged whole from its occurrence on a day the clocks skip is drawn where the write puts it`() {
        assertEquals(
            "THE POINT: the 10th's at 02:30 on the 11th, where the write puts it, not 01:30",
            listOf(utc("2024-03-11T06:30:00Z"), utc("2024-03-12T06:30:00Z")),
            drawn(org.mochios.calendars.ui.editor.Scope.ALL),
        )
    }

    @Test
    fun `one occurrence on a day the clocks skip dragged alone is drawn where it is dropped`() {
        assertEquals(
            "THE POINT: at 01:30 on the 11th, beside the 11th's own, as written",
            listOf(utc("2024-03-11T05:30:00Z"), utc("2024-03-11T06:30:00Z")),
            drawn(org.mochios.calendars.ui.editor.Scope.ONE),
        )
    }

    /**
     * Where the 10th's and 11th's occurrences of a daily New York series at
     * 02:30 from the 1st are drawn the moment [move] drags the 10th's, the
     * read of the stored event held, and then once it is read, the write
     * held. The listing gives the 10th's the reading it is written at where
     * [given] says so, as a core that predates the field does not.
     */
    private fun framed(given: Boolean, move: (CalendarViewModel, org.mochios.calendars.model.Instance) -> Unit): Pair<List<Long>, List<Long>> {
        weekly = true
        kept = """[{"name": "VEVENT", "properties": [
            {"name": "UID", "params": {}, "value": "uid-1"},
            {"name": "SUMMARY", "params": {}, "value": "Stand-up"},
            {"name": "DTSTART", "params": {"TZID": ["America/New_York"]}, "value": "20240301T023000"},
            {"name": "DTEND", "params": {"TZID": ["America/New_York"]}, "value": "20240301T033000"},
            {"name": "RRULE", "params": {}, "value": "FREQ=DAILY"}
        ], "components": []}]"""
        val days = listOf(utc("2024-03-10T06:30:00Z"), utc("2024-03-11T06:30:00Z"))
        extra = days.map { at ->
            val stated = if (given && at == days[0]) """, "reading": "20240310T023000"""" else ""
            at to """{"event": "e1", "calendar": "c1", "summary": "Stand-up", "start": $at, "finish": ${at + 3_600},
                "recurring": true, "zone": {"start": "America/New_York", "finish": "America/New_York"}$stated}"""
        }
        val model = model("America/New_York")
        model.view(CalendarsSection.MONTH)
        model.anchor(LocalDate.of(2024, 3, 10))
        until { model.uiState.value.instances.map { it.start }.containsAll(days) }
        val starts = { model.uiState.value.instances.filter { it.event == "e1" && it.start < utc("2025-01-01T00:00:00Z") }.map { it.start }.sorted() }
        reading = CountDownLatch(1)
        writes = CountDownLatch(1)
        move(model, model.uiState.value.instances.first { it.start == days[0] })
        val first = starts()
        reading?.countDown()
        until { asked.any { it.path.orEmpty().contains("/-/events/update") || it.path.orEmpty().contains("/-/events/split") } }
        val then = starts()
        writes?.countDown()
        until { written.isNotEmpty() }
        return first to then
    }

    /** The 10th's occurrence dragged onto the 11th for [scope]. */
    private fun onward(scope: org.mochios.calendars.ui.editor.Scope) = { model: CalendarViewModel, tenth: org.mochios.calendars.model.Instance ->
        model.move(tenth, LocalDate.of(2024, 3, 11), scope)
    }

    /** The 10th's occurrence dropped at 03:30 in summer time for [scope]. */
    private fun later(scope: org.mochios.calendars.ui.editor.Scope) = { model: CalendarViewModel, tenth: org.mochios.calendars.model.Instance ->
        model.move(tenth, utc("2024-03-10T07:30:00Z"), utc("2024-03-10T08:30:00Z"), scope)
    }

    /** The 10th's and 11th's occurrences a day on, at 02:30, as the write puts them. */
    private val onto = listOf(utc("2024-03-11T06:30:00Z"), utc("2024-03-12T06:30:00Z"))

    /** The 10th's and 11th's occurrences at 03:30, an hour on from 02:30, as the write puts them. */
    private val half = listOf(utc("2024-03-10T07:30:00Z"), utc("2024-03-11T07:30:00Z"))

    @Test
    fun `a series dragged whole a day on from its occurrence the clocks skip is drawn at half past two from the first frame when the listing gives its reading`() {
        assertEquals("THE POINT: before the event is read", onto, framed(true, onward(org.mochios.calendars.ui.editor.Scope.ALL)).first)
    }

    @Test
    fun `a series cut a day on from its occurrence the clocks skip is drawn at half past two from the first frame when the listing gives its reading`() {
        assertEquals("THE POINT: before the event is read", onto, framed(true, onward(org.mochios.calendars.ui.editor.Scope.FOLLOWING)).first)
    }

    @Test
    fun `a series dragged whole to half past three from its occurrence the clocks skip is drawn an hour on from the first frame when the listing gives its reading`() {
        assertEquals("THE POINT: before the event is read", half, framed(true, later(org.mochios.calendars.ui.editor.Scope.ALL)).first)
    }

    @Test
    fun `a series cut at half past three from its occurrence the clocks skip is drawn an hour on from the first frame when the listing gives its reading`() {
        assertEquals("THE POINT: before the event is read", half, framed(true, later(org.mochios.calendars.ui.editor.Scope.FOLLOWING)).first)
    }

    @Test
    fun `a series cut a day on from its occurrence the clocks skip is drawn from its instant without the reading, and at half past two once the event is read`() {
        assertEquals(
            "THE POINT: at 01:30 on the 11th until the event is read, then at 02:30",
            listOf(utc("2024-03-11T05:30:00Z"), utc("2024-03-12T06:30:00Z")) to onto,
            framed(false, onward(org.mochios.calendars.ui.editor.Scope.FOLLOWING)),
        )
    }

    @Test
    fun `a series dragged whole to half past three from its occurrence the clocks skip is drawn from its instant without the reading, and an hour on once the event is read`() {
        assertEquals(
            "THE POINT: the 11th's two hours on until the event is read, then one",
            listOf(utc("2024-03-10T07:30:00Z"), utc("2024-03-11T08:30:00Z")) to half,
            framed(false, later(org.mochios.calendars.ui.editor.Scope.ALL)),
        )
    }

    /**
     * What dropping the 10th's occurrence of a daily New York series at
     * 09:00 from the 1st, which lists 02:30 on the 10th besides, at 03:30 in
     * summer time for all events writes as the series' start, the listing
     * giving that occurrence the reading it is written at where [given] says
     * so.
     */
    private fun listed(given: Boolean): String? {
        weekly = true
        kept = """[{"name": "VEVENT", "properties": [
            {"name": "UID", "params": {}, "value": "uid-1"},
            {"name": "SUMMARY", "params": {}, "value": "Stand-up"},
            {"name": "DTSTART", "params": {"TZID": ["America/New_York"]}, "value": "20240301T090000"},
            {"name": "DTEND", "params": {"TZID": ["America/New_York"]}, "value": "20240301T100000"},
            {"name": "RRULE", "params": {}, "value": "FREQ=DAILY"},
            {"name": "RDATE", "params": {"TZID": ["America/New_York"]}, "value": "20240310T023000"}
        ], "components": []}]"""
        val tenth = utc("2024-03-10T06:30:00Z")
        val stated = if (given) """, "reading": "20240310T023000"""" else ""
        extra = listOf(
            tenth to """{"event": "e1", "calendar": "c1", "summary": "Stand-up", "start": $tenth, "finish": ${tenth + 3_600},
                "recurring": true, "zone": {"start": "America/New_York", "finish": "America/New_York"}$stated}""",
        )
        val model = model("America/New_York")
        model.view(CalendarsSection.MONTH)
        model.anchor(LocalDate.of(2024, 3, 10))
        until { model.uiState.value.instances.any { it.start == tenth } }
        val said = said(model)
        later(org.mochios.calendars.ui.editor.Scope.ALL)(model, model.uiState.value.instances.first { it.start == tenth })
        until { said.isNotEmpty() }
        return begins(written)
    }

    @Test
    fun `a series moved from a date listed at another time the clocks skip is measured from the reading the listing gives it`() {
        // The series' own 09:00 does not find the listed date's reading: from
        // the listing's 02:30 the series goes an hour on, and without it two,
        // from the 01:30 its instant reads.
        assertEquals("THE POINT: an hour on from 02:30", "20240301T100000", listed(true))
        assertEquals("two hours on from 01:30, from a core that predates the reading", "20240301T110000", listed(false))
    }

    // ---- an event written in UTC, dragged across a change of clocks ----
    //
    // 14:00 UTC on Saturday 1 November 2025 is 10:00 in New York's summer
    // time. Its clocks go back on the 2nd, so 10:00 on Monday the 3rd is
    // 15:00 UTC.

    /**
     * Where the event written in UTC from 1 November, repeating daily where
     * [series] says so, is drawn the moment its occurrence that day is
     * dragged onto the 3rd for [scope], the read of the stored event held,
     * and what the write then gives as the DTSTART of the component that
     * replaces that occurrence, with no [replaced] the master's.
     */
    private fun universal(series: Boolean, scope: org.mochios.calendars.ui.editor.Scope, replaced: String? = null): Pair<List<Long>, String?> {
        weekly = series
        val rule = if (series) """, {"name": "RRULE", "params": {}, "value": "FREQ=DAILY"}""" else ""
        kept = """[{"name": "VEVENT", "properties": [
            {"name": "UID", "params": {}, "value": "uid-1"},
            {"name": "SUMMARY", "params": {}, "value": "Stand-up"},
            {"name": "DTSTART", "params": {}, "value": "20251101T140000Z"},
            {"name": "DTEND", "params": {}, "value": "20251101T150000Z"}$rule
        ], "components": []}]"""
        val days = if (series) listOf(utc("2025-11-01T14:00:00Z"), utc("2025-11-02T14:00:00Z")) else listOf(utc("2025-11-01T14:00:00Z"))
        extra = days.map { at ->
            at to """{"event": "e1", "calendar": "c1", "summary": "Stand-up", "start": $at, "finish": ${at + 3_600},
                "recurring": $series, "zone": {"start": "UTC", "finish": "UTC"}}"""
        }
        val model = model("America/New_York")
        model.view(CalendarsSection.MONTH)
        model.anchor(LocalDate.of(2025, 11, 1))
        until { model.uiState.value.instances.map { it.start }.containsAll(days) }
        val said = said(model)
        reading = CountDownLatch(1)
        model.move(model.uiState.value.instances.first { it.start == days[0] }, LocalDate.of(2025, 11, 3), scope)
        val first = model.uiState.value.instances
            .filter { it.event == "e1" && it.start < utc("2026-01-01T00:00:00Z") }.map { it.start }.sorted()
        reading?.countDown()
        until { said.isNotEmpty() }
        return first to begins(written, replaced)
    }

    @Test
    fun `one event written in UTC dragged across the clock change keeps its time on the user's clock, still written in UTC`() {
        assertEquals(
            "THE POINT: 10:00 on the 3rd in winter time, drawn and written, not 09:00",
            listOf(utc("2025-11-03T15:00:00Z")) to "20251103T150000Z",
            universal(series = false, org.mochios.calendars.ui.editor.Scope.ALL),
        )
    }

    @Test
    fun `one occurrence of a series written in UTC dragged alone across the clock change keeps its time on the user's clock`() {
        assertEquals(
            "THE POINT: 10:00 on the 3rd in winter time, not 09:00",
            "20251103T150000Z",
            universal(series = true, org.mochios.calendars.ui.editor.Scope.ONE, replaced = "20251101").second,
        )
    }

    @Test
    fun `a series written in UTC dragged whole across the clock change keeps its time on UTC's clock`() {
        assertEquals(
            "THE POINT: at 14:00 UTC, the clock it repeats on, drawn and written",
            listOf(utc("2025-11-03T14:00:00Z"), utc("2025-11-04T14:00:00Z")) to "20251103T140000Z",
            universal(series = true, org.mochios.calendars.ui.editor.Scope.ALL),
        )
    }

    @Test
    fun `an all-day occurrence on a day whose midnight the clocks skip is placed by its date, not the day its start reads`() {
        // Havana's clocks skip midnight on 10 March 2024: the server lists
        // that day's all-day occurrence from 04:00 UTC, 23:00 on the 9th
        // there, and dates it the 10th.
        val havana = ZoneId.of("America/Havana")
        val midnight = 1_710_043_200L
        val model = model(havana.id)
        val day = org.mochios.calendars.model.Instance(
            event = "e9", allday = true, date = "2024-03-10", start = midnight, finish = midnight + 86_400,
        )
        assertEquals(LocalDate.of(2024, 3, 9), Instant.ofEpochSecond(midnight).atZone(havana).toLocalDate())
        assertEquals(
            "THE POINT: on the 10th alone, not the 9th",
            listOf(LocalDate.of(2024, 3, 10), LocalDate.of(2024, 3, 10), false, true),
            listOf(model.day(day), model.finish(day), model.covers(day, LocalDate.of(2024, 3, 9)), model.covers(day, LocalDate.of(2024, 3, 10))),
        )
    }

    // ---- a midnight or an hour the clocks skip or read twice ----
    //
    // Each is placed where the server places it, Go's time.Date: Havana's
    // skipped midnight on 10 March 2024 an hour back, at 23:00 the evening
    // before; Amman's midnight on 30 October 2020, which comes round twice,
    // the second time; New York's skipped 02:00 on 8 March 2026 an hour back,
    // at 01:00 in winter time; and London's 01:00 on 25 October 2026, which
    // comes round twice, in winter time. The platform would put each the
    // other way.

    @Test
    fun `a day's range begins and ends at a midnight the clocks skip or read twice where the server places it`() {
        val havana = model("America/Havana")
        val amman = model("Asia/Amman")
        val day = { model: CalendarViewModel, date: LocalDate ->
            model.range(model.uiState.value.copy(view = CalendarsSection.DAY, anchor = date))
        }
        assertEquals(
            "THE POINT: an hour back in Havana, the second midnight in Amman, at both ends",
            listOf(utc("2024-03-10T04:00:00Z"), utc("2024-03-10T04:00:00Z"), utc("2020-10-29T22:00:00Z"), utc("2020-10-29T22:00:00Z")),
            listOf(
                day(havana, LocalDate.of(2024, 3, 10)).first,
                day(havana, LocalDate.of(2024, 3, 9)).second,
                day(amman, LocalDate.of(2020, 10, 30)).first,
                day(amman, LocalDate.of(2020, 10, 29)).second,
            ),
        )
    }

    @Test
    fun `a new event on a day whose working hours the clocks skip or read twice starts where the server places it`() {
        val york = model("America/New_York")
        val london = model()
        val hours = { model: CalendarViewModel, start: Int ->
            model.uiState.value.let { it.copy(preferences = it.preferences.copy(hours = org.mochios.calendars.model.Hours(start = start))) }
        }
        assertEquals(
            "THE POINT: 01:00 in New York's winter time, and London's 01:00 in winter time",
            listOf(utc("2026-03-08T06:00:00Z"), utc("2026-10-25T01:00:00Z")),
            listOf(
                york.creation(LocalDate.of(2026, 3, 8), hours(york, 2)),
                london.creation(LocalDate.of(2026, 10, 25), hours(london, 1)),
            ),
        )
    }

    /**
     * Where the preview draws the start of a block of 10:00 the day before
     * [day], dropped in the all-day band on [day], for a user in [zone].
     */
    private fun banded(zone: String, day: LocalDate): Long {
        val opens = day.minusDays(1).atTime(10, 0).atZone(ZoneId.of(zone)).toEpochSecond()
        extra = listOf(
            opens to """{"event": "e1", "calendar": "c1", "summary": "Stand-up", "start": $opens, "finish": ${opens + 3_600}}""",
        )
        val model = model(zone)
        model.view(CalendarsSection.MONTH)
        model.anchor(day)
        until { model.uiState.value.instances.any { it.start == opens } }
        val said = said(model)
        reading = CountDownLatch(1)
        model.move(model.uiState.value.instances.first { it.start == opens }, Moved.Whole(day), org.mochios.calendars.ui.editor.Scope.ALL)
        val drawn = model.uiState.value.instances.first { it.allday && it.date == day.toString() }.start
        reading?.countDown()
        until { said.isNotEmpty() }
        return drawn
    }

    @Test
    fun `a block dropped in the band on a day whose midnight the clocks skip or read twice is drawn from where the server places it`() {
        assertEquals(
            "THE POINT: an hour back in Havana, the second midnight in Amman",
            listOf(utc("2024-03-10T04:00:00Z"), utc("2020-10-29T22:00:00Z")),
            listOf(banded("America/Havana", LocalDate.of(2024, 3, 10)), banded("Asia/Amman", LocalDate.of(2020, 10, 30))),
        )
    }

    @Test
    fun `an occurrence moved with this event is drawn as the override of the occurrence it was`() {
        weekly = true
        val model = model()
        val said = said(model)
        reading = CountDownLatch(1)
        val listed = model.uiState.value.instances.single()
        assertTrue(listed.recurring)
        model.move(listed, start + 3_600, start + 7_200, org.mochios.calendars.ui.editor.Scope.ONE)
        val shown = model.uiState.value.instances.single()
        assertEquals(start + 3_600, shown.start)
        assertTrue("THE POINT: an override, as the write makes it", shown.exception)
        assertEquals("of the occurrence it was", start, shown.occurrence)
        assertFalse("a second drag may take it", shown.readonly)
        reading?.countDown()
        until { said.isNotEmpty() }
    }

    @Test
    fun `occurrences moved with this and following or all events are read-only until listed again`() {
        weekly = true
        val model = model()
        val said = said(model)
        val kinds = listOf(org.mochios.calendars.ui.editor.Scope.FOLLOWING, org.mochios.calendars.ui.editor.Scope.ALL)
        for ((index, scope) in kinds.withIndex()) {
            until { !model.uiState.value.instances.single().readonly }
            reading = CountDownLatch(1)
            model.move(model.uiState.value.instances.single(), start + 3_600, start + 7_200, scope)
            val shown = model.uiState.value.instances.single()
            assertEquals(start + 3_600, shown.start)
            assertTrue("THE POINT: $scope gives it a place in the series only a new listing names", shown.readonly)
            reading?.countDown()
            until { said.size == index + 1 }
        }
    }

    @Test
    fun `an occurrence moved with this event and dragged again before it is listed again moves its override`() {
        weekly = true
        keeping = true
        val model = model()
        val said = said(model)
        reading = CountDownLatch(1)
        // Monday 5 October's 10:00 to 11:00, then, before that is written, on to 12:00.
        model.move(model.uiState.value.instances.single(), start + 3_600, start + 7_200, org.mochios.calendars.ui.editor.Scope.ONE)
        model.move(model.uiState.value.instances.single(), start + 7_200, start + 10_800, org.mochios.calendars.ui.editor.Scope.ONE)
        reading?.countDown()
        until { said.size == 2 }
        assertEquals(listOf(CalendarEvent.Moved, CalendarEvent.Moved), said)
        assertTrue(written, written.contains("\"value\":\"20261005T100000\""))
        assertTrue(written, written.contains("\"value\":\"20261005T120000\""))
        assertNull("THE POINT: no override of an 11:00 the series does not have: $written", begins(written, "20261005T110000"))
    }

    @Test
    fun `a move that fails shows where the server has it, though the range cannot be read again`() {
        val model = model()
        model.anchor(LocalDate.of(2026, 10, 5))
        until { model.uiState.value.instances.isNotEmpty() }
        val said = said(model)
        lost = true
        failing = true
        val instance = model.uiState.value.instances.single()
        model.move(instance, start + 3_600, start + 7_200, org.mochios.calendars.ui.editor.Scope.ALL)
        assertEquals("drawn where it was dropped", start + 3_600, model.uiState.value.instances.single().start)
        until { said.isNotEmpty() }
        assertTrue(said.single() is CalendarEvent.Failed)
        assertEquals("THE POINT: where the server last listed it", start, model.uiState.value.instances.single().start)
    }

    /**
     * A drag made from where a first move drew the event, queued behind that
     * move, whose write is then refused: [first] moves [listed], or the
     * occurrence the range lists, and [then] drags what it drew. Answers
     * what the second move wrote.
     */
    private fun queued(
        listed: org.mochios.calendars.model.Instance? = null,
        first: (CalendarViewModel, org.mochios.calendars.model.Instance) -> Unit,
        then: (CalendarViewModel, org.mochios.calendars.model.Instance) -> Unit,
    ): String {
        val model = model()
        model.anchor(LocalDate.of(2026, 10, 5))
        until { model.uiState.value.instances.isNotEmpty() }
        val said = said(model)
        reading = CountDownLatch(1)
        refusals = 1
        first(model, listed ?: model.uiState.value.instances.single())
        then(model, model.uiState.value.instances.single())
        reading?.countDown()
        until { said.size == 2 }
        assertTrue(said.first() is CalendarEvent.Failed)
        assertEquals(CalendarEvent.Moved, said.last())
        return written
    }

    /**
     * The DTSTART [written] gives the component that replaces an occurrence
     * on [replaced], the day a RECURRENCE-ID value begins with, or with none
     * the master's.
     */
    private fun begins(written: String, replaced: String? = null): String? {
        val components = com.google.gson.JsonParser.parseString(written).asJsonObject.getAsJsonArray("components")
        val value = { component: com.google.gson.JsonObject, name: String ->
            component.getAsJsonArray("properties").map { it.asJsonObject }
                .firstOrNull { it.get("name").asString == name }?.get("value")?.asString
        }
        return components.map { it.asJsonObject }
            .firstOrNull { component ->
                val recurrence = value(component, "RECURRENCE-ID")
                if (replaced == null) recurrence == null else recurrence?.startsWith(replaced) == true
            }
            ?.let { component -> value(component, "DTSTART") }
    }

    @Test
    fun `a block dragged again behind a move that fails is written where it was dropped`() {
        val written = queued(
            first = { model, instance -> model.move(instance, start + 3_600, start + 7_200, org.mochios.calendars.ui.editor.Scope.ALL) },
            then = { model, instance -> model.move(instance, start + 7_200, start + 10_800, org.mochios.calendars.ui.editor.Scope.ALL) },
        )
        assertEquals("THE POINT: 12:00, where it was dropped, not an hour on from where the server has it", "20261005T120000", begins(written))
    }

    @Test
    fun `an occurrence dragged again with this event behind a move that fails is written where it was dropped`() {
        weekly = true
        val written = queued(
            first = { model, instance -> model.move(instance, start + 3_600, start + 7_200, org.mochios.calendars.ui.editor.Scope.ONE) },
            then = { model, instance -> model.move(instance, start + 7_200, start + 10_800, org.mochios.calendars.ui.editor.Scope.ONE) },
        )
        assertEquals("THE POINT: the override of the 5th at 12:00, where it was dropped", "20261005T120000", begins(written, "20261005"))
    }

    @Test
    fun `a chip dragged again behind a move that fails is written on the day it was dropped on`() {
        val written = queued(
            first = { model, instance -> model.move(instance, LocalDate.of(2026, 10, 6), org.mochios.calendars.ui.editor.Scope.ALL) },
            then = { model, instance -> model.move(instance, LocalDate.of(2026, 10, 8), org.mochios.calendars.ui.editor.Scope.ALL) },
        )
        assertEquals("THE POINT: Thursday the 8th, where it was dropped", "20261008T100000", begins(written))
    }

    @Test
    fun `a block dropped in the band again behind a move that fails is written from the day it was dropped on`() {
        val written = queued(
            first = { model, instance -> model.move(instance, Moved.Whole(LocalDate.of(2026, 10, 6)), org.mochios.calendars.ui.editor.Scope.ALL) },
            then = { model, instance -> model.move(instance, Moved.Whole(LocalDate.of(2026, 10, 8)), org.mochios.calendars.ui.editor.Scope.ALL) },
        )
        assertEquals("THE POINT: from Thursday the 8th", "20261008", begins(written))
    }

    @Test
    fun `a bar dropped in the grid behind a move that fails is written on the day it was dropped on`() {
        whole = true
        val midnight = LocalDate.of(2026, 10, 8).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()
        val bar = org.mochios.calendars.model.Instance(
            event = "e1", calendar = "c1", start = midnight, finish = midnight + 86_400, allday = true, date = "2026-10-08",
        )
        val written = queued(
            listed = bar,
            first = { model, instance -> model.move(instance, LocalDate.of(2026, 10, 9), org.mochios.calendars.ui.editor.Scope.ALL) },
            then = { model, instance -> model.move(instance, Moved.Timed(LocalDate.of(2026, 10, 11), 11.25f), org.mochios.calendars.ui.editor.Scope.ALL) },
        )
        assertEquals("THE POINT: 11:15 on Sunday the 11th, where it was dropped", "20261011T111500", begins(written))
    }

    @Test
    fun `a listing that lands while the move is written keeps it where it was dropped, and one after lets it go`() {
        val model = model()
        model.anchor(LocalDate.of(2026, 10, 5))
        until { model.uiState.value.instances.isNotEmpty() }
        val said = said(model)
        reading = CountDownLatch(1)
        val instance = model.uiState.value.instances.single()
        model.move(instance, start + 3_600, start + 7_200, org.mochios.calendars.ui.editor.Scope.ALL)
        val before = listings().size
        model.load(reset = false)
        until { listings().size >= before + 3 }
        settle()
        assertEquals("THE POINT: the listing from before the write does not take it back", start + 3_600, model.uiState.value.instances.single().start)
        reading?.countDown()
        until { said.isNotEmpty() }
        // This server does not move it, so the listing read since says where it is.
        until { model.uiState.value.instances.single().start == start }
    }

    @Test
    fun `a series moved with all events is drawn moved in a month read while it is written`() {
        weekly = true
        // Monday 14 December 10:00: only December's listing holds it, and
        // October's month reads no further than November.
        later = LocalDate.of(2026, 12, 14).atTime(10, 0).atZone(london).toEpochSecond()
        val model = model()
        model.anchor(LocalDate.of(2026, 10, 5))
        until { model.uiState.value.instances.any { it.start == start } }
        val said = said(model)
        reading = CountDownLatch(1)
        val listed = model.uiState.value.instances.first { it.start == start }
        assertTrue("not held at the drop", model.uiState.value.instances.none { it.start == later })
        model.move(listed, start + 3_600, start + 7_200, org.mochios.calendars.ui.editor.Scope.ALL)
        model.anchor(LocalDate.of(2026, 12, 14))
        until { listings().any { it.requestUrl?.queryParameter("finish")?.toLongOrNull()?.let { finish -> finish > later } == true } }
        settle()
        val december = model.uiState.value.instances.filter { it.event == "e1" && it.start >= later - 86_400 }
        assertEquals("THE POINT: drawn an hour on, where the write will put it", listOf(later + 3_600), december.map { it.start })
        assertTrue("and read-only until it is listed again", december.single().readonly)
        reading?.countDown()
        until { said.isNotEmpty() }
    }

    @Test
    fun `a second drag from where a failed move drew an occurrence still hides where that move took it from`() {
        weekly = true
        val model = model()
        model.anchor(LocalDate.of(2026, 10, 5))
        until { model.uiState.value.instances.isNotEmpty() }
        val said = said(model)
        reading = CountDownLatch(1)
        refusals = 1
        stall = CountDownLatch(1)
        // Monday 5 October's 10:00 to 11:00 alone, then, before that is
        // written, that one and those after it on to 12:00; the first write fails.
        model.move(model.uiState.value.instances.single(), start + 3_600, start + 7_200, org.mochios.calendars.ui.editor.Scope.ONE)
        model.move(model.uiState.value.instances.single(), start + 7_200, start + 10_800, org.mochios.calendars.ui.editor.Scope.FOLLOWING)
        reading?.countDown()
        until { said.any { it is CalendarEvent.Failed } }
        settle()
        assertEquals(
            "THE POINT: not at 10:00 as well, which the first move took it from",
            listOf(start + 7_200),
            model.uiState.value.instances.filter { it.event == "e1" }.map { it.start },
        )
        stall?.countDown()
        until { said.size == 2 }
    }

    @Test
    fun `a drag of an occurrence a failed move drew another onto leaves what that move took to show again`() {
        weekly = true
        // Monday 12 October 10:00, a week on, the series' next occurrence.
        later = start + 7 * 86_400
        val model = model()
        model.anchor(LocalDate.of(2026, 10, 5))
        until { model.uiState.value.instances.count { it.event == "e1" } == 2 }
        val said = said(model)
        reading = CountDownLatch(1)
        refusals = 1
        stall = CountDownLatch(1)
        // The 5th's occurrence alone onto the 12th's start; then, before that
        // is written, the 12th's own occurrence on two hours. The first write fails.
        model.move(model.uiState.value.instances.first { it.start == start }, later, later + 3_600, org.mochios.calendars.ui.editor.Scope.ONE)
        val own = model.uiState.value.instances.first { it.start == later && !it.exception }
        model.move(own, later + 7_200, later + 10_800, org.mochios.calendars.ui.editor.Scope.ONE)
        reading?.countDown()
        until { said.any { it is CalendarEvent.Failed } }
        settle()
        assertTrue(
            "THE POINT: the 5th's occurrence is back where the server has it: the second drag was of another occurrence at that start",
            model.uiState.value.instances.any { it.event == "e1" && it.start == start },
        )
        stall?.countDown()
        until { said.size == 2 }
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
    fun `a calendar made, subscribed to or linked is said on the calendar it returns to`() {
        assertEquals(R.string.calendars_created, CalendarsApp.said(CalendarsApp.CALENDAR))
        assertEquals(R.string.calendars_subscribed, CalendarsApp.said(CalendarsApp.SUBSCRIBED))
        assertEquals(R.string.calendars_linked, CalendarsApp.said(CalendarsApp.LINKED))
        // So is an event made, which the editor leaves the calendar to say.
        assertEquals(R.string.calendars_event_created, CalendarsApp.said(CalendarsApp.CREATED))
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

    // ---- the day it opens on ----

    @Test
    fun `it opens on today where the user is, not where the phone's clock is`() {
        // Twenty-six hours apart, so the two are never on the same date.
        val east = ZoneId.of("Etc/GMT-14")
        val default = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Etc/GMT+12"))
        try {
            val viewer = object : org.mochios.calendars.di.Viewer {
                override fun zone(): String = east.id
                override fun week(): Int = 1
                override suspend fun server(): String = "https://example.org"
            }
            val before = LocalDate.now(east)
            val model = CalendarViewModel(context, repository, viewer)
            val after = LocalDate.now(east)
            assertTrue(model.uiState.value.anchor == before || model.uiState.value.anchor == after)
            assertEquals(model.uiState.value.anchor, model.uiState.value.focus)
        } finally {
            java.util.TimeZone.setDefault(default)
        }
    }

    // ---- a reply that lands after the state has moved on ----

    @Test
    fun `saving the view keeps what changed while it was saved`() {
        val model = model()
        val saved = CountDownLatch(1)
        saving = saved
        model.view(CalendarsSection.LIST)
        until { asked.any { it.path.orEmpty().endsWith("-/preferences/set") } }
        val day = LocalDate.of(2026, 10, 5)
        model.anchor(day)
        until { model.uiState.value.fetched == day && model.uiState.value.instances.isNotEmpty() }
        saved.countDown()
        until { model.uiState.value.preferences.view == "list" }
        assertEquals(day, model.uiState.value.anchor)
        assertEquals(day, model.uiState.value.fetched)
        assertEquals(listOf("e1"), model.uiState.value.instances.map { it.event })
    }

    @Test
    fun `a later page keeps what changed while it was read`() {
        val day = LocalDate.of(2026, 10, 5)
        last = day.plusDays(200).atStartOfDay(london).toEpochSecond()
        val model = model()
        model.view(CalendarsSection.LIST)
        until { model.uiState.value.preferences.view == "list" }
        model.anchor(day)
        until { model.uiState.value.fetched == day && !model.uiState.value.isLoading }
        val read = CountDownLatch(1)
        paging = read
        held = day.plusDays(1).atStartOfDay(london).toEpochSecond()
        model.later()
        until { listings().any { (it.requestUrl?.queryParameter("start")?.toLongOrNull() ?: 0) >= held } }
        model.search("stand")
        read.countDown()
        until { model.uiState.value.latest == 1 && !model.uiState.value.paging }
        assertEquals("stand", model.uiState.value.search)
        assertEquals(listOf("e1", "e2"), model.uiState.value.instances.map { it.event })
    }

    @Test
    fun `a later page read for another day is dropped`() {
        val day = LocalDate.of(2026, 10, 5)
        last = day.plusDays(200).atStartOfDay(london).toEpochSecond()
        val model = model()
        model.view(CalendarsSection.LIST)
        until { model.uiState.value.preferences.view == "list" }
        model.anchor(day)
        until { model.uiState.value.fetched == day && !model.uiState.value.isLoading }
        val read = CountDownLatch(1)
        paging = read
        held = day.plusDays(1).atStartOfDay(london).toEpochSecond()
        model.later()
        until { listings().any { (it.requestUrl?.queryParameter("start")?.toLongOrNull() ?: 0) >= held } }
        // A day well before the held page: its own first page comes back at once.
        val earlier = day.minusYears(2)
        model.anchor(earlier)
        until { model.uiState.value.fetched == earlier && !model.uiState.value.isLoading }
        read.countDown()
        // Nothing changes when the page is dropped, so there is nothing to wait on.
        settle()
        assertEquals(earlier, model.uiState.value.anchor)
        assertEquals(0, model.uiState.value.latest)
        assertFalse(model.uiState.value.instances.any { it.event == "e2" })
    }

    // ---- a series move's preview ----

    /** The same hour as [start] on day [day] of October. */
    private fun october(day: Int, hour: Int = 10) = LocalDate.of(2026, 10, day).atTime(hour, 0).atZone(london).toEpochSecond()

    /** A listed occurrence of the stand-up from [from] for an hour; an override when it [replaces] an occurrence. */
    private fun occurrence(from: Long, replaces: Long? = null, event: String = "e1") =
        """{"event": "$event", "calendar": "c1", "summary": "Stand-up", "start": $from, "finish": ${from + 3_600},
            "recurring": true${replaces?.let { """, "exception": true, "occurrence": $it""" } ?: ""}}"""

    @Test
    fun `a series moved with this and following lets go of its preview as soon as the month read after the save lands`() {
        weekly = true
        later = october(12)
        val model = model()
        model.anchor(LocalDate.of(2026, 10, 5))
        until { model.uiState.value.instances.count { it.event == "e1" } == 2 }
        val said = said(model)
        // Once cut, the 5th's stays e1's and the 12th's on is e3's, an hour on.
        following = listOf(occurrence(start), occurrence(october(12, 11), event = "e3")).joinToString(",")
        // The next month's read waits, so only the month shown lands.
        val next = CountDownLatch(1)
        paging = next
        held = october(15)
        val waiting = { listings().count { (it.requestUrl?.queryParameter("start")?.toLongOrNull() ?: 0) >= held } }
        val before = waiting()
        val twelfth = model.uiState.value.instances.first { it.start == later }
        model.move(twelfth, october(12, 11), october(12, 12), org.mochios.calendars.ui.editor.Scope.FOLLOWING)
        until { said.isNotEmpty() }
        assertEquals(CalendarEvent.Moved, said.single())
        // The months read on the save's own announcement: the next one waits,
        // and the one shown lands.
        until { waiting() > before }
        settle()
        val drawn = model.uiState.value.instances
        assertTrue("THE POINT: the preview has gone with the month that holds the move: $drawn", drawn.none { it.readonly })
        assertEquals("drawn once, as e3's", listOf("e3"), drawn.filter { it.start == october(12, 11) }.map { it.event })
        next.countDown()
    }

    @Test
    fun `a series moved whole draws each override at the place the save moves it to`() {
        weekly = true
        // The 12th's moved to Tuesday the 13th at 14:00, and the 19th's as listed.
        extra = listOf(
            october(13, 14) to occurrence(october(13, 14), replaces = october(12)),
            october(19) to occurrence(october(19)),
        )
        val model = model()
        model.anchor(LocalDate.of(2026, 10, 5))
        until { model.uiState.value.instances.count { it.event == "e1" } == 3 }
        val said = said(model)
        reading = CountDownLatch(1)
        model.move(model.uiState.value.instances.first { it.start == start }, LocalDate.of(2026, 10, 12), org.mochios.calendars.ui.editor.Scope.ALL)
        val drawn = model.uiState.value.instances.filter { it.event == "e1" }
        assertEquals(
            "THE POINT: the 5th's on the 12th and the 13th's override on the 20th, not one of them dropped for sharing the 12th's place",
            listOf(october(12), october(20, 14), october(26)),
            drawn.map { it.start },
        )
        assertEquals("the override known by the 19th's place, where the save moves it", october(19), drawn.first { it.exception }.occurrence)
        reading?.countDown()
        until { said.isNotEmpty() }
    }

    @Test
    fun `an occurrence moved with this and following onto one the move leaves draws both`() {
        weekly = true
        later = october(12)
        val model = model()
        model.anchor(LocalDate.of(2026, 10, 5))
        until { model.uiState.value.instances.count { it.event == "e1" } == 2 }
        val said = said(model)
        reading = CountDownLatch(1)
        lost = true
        // The 12th's, and those after it, back a week onto the 5th's place.
        model.move(model.uiState.value.instances.first { it.start == later }, LocalDate.of(2026, 10, 5), org.mochios.calendars.ui.editor.Scope.FOLLOWING)
        val drawn = model.uiState.value.instances.filter { it.event == "e1" && it.start == start }
        assertEquals("THE POINT: the 5th's, which the cut leaves, and the 12th's on it, read-only", listOf(false, true), drawn.map { it.readonly }.sorted())
        reading?.countDown()
        until { said.isNotEmpty() }
    }

    @Test
    fun `a drag of the occurrence a failed this event move drew another beside does not hide what that move took`() {
        weekly = true
        later = october(12)
        val model = model()
        model.anchor(LocalDate.of(2026, 10, 5))
        until { model.uiState.value.instances.count { it.event == "e1" } == 2 }
        val said = said(model)
        reading = CountDownLatch(1)
        refusals = 1
        stall = CountDownLatch(1)
        // The 5th's alone onto the 12th's start; then, before that is written,
        // the 12th's own two hours on. The first write is refused.
        model.move(model.uiState.value.instances.first { it.start == start }, later, later + 3_600, org.mochios.calendars.ui.editor.Scope.ONE)
        val twelfth = model.uiState.value.instances.first { it.start == later && !it.exception }
        model.move(twelfth, later + 7_200, later + 10_800, org.mochios.calendars.ui.editor.Scope.ONE)
        reading?.countDown()
        until { said.any { it is CalendarEvent.Failed } }
        settle()
        assertTrue(
            "THE POINT: the 5th's is drawn where the server has it again, as the second drag took the 12th's own",
            model.uiState.value.instances.any { it.event == "e1" && it.start == start },
        )
        stall?.countDown()
        until { said.size == 2 }
    }
}
