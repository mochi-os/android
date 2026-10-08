// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import android.os.Looper
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.UserPreferences
import org.mochios.android.util.Zones
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.model.Instance
import org.mochios.calendars.model.Preferences
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.storage.VisibilityStore
import org.mochios.calendars.ui.calendar.CalendarUiState
import org.mochios.calendars.ui.calendar.CalendarViewModel
import org.mochios.calendars.ui.calendar.HourGutter
import org.mochios.calendars.ui.calendar.Moved
import org.mochios.calendars.ui.calendar.TimeGrid
import org.mochios.calendars.ui.calendar.rememberHourScroll
import org.mochios.calendars.ui.router.CalendarsSection
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.time.ZoneId

/**
 * The day and week views as the web's time grid draws them: a band that is
 * always there and holds every all-day event as one bar across its days, a
 * current-time line that moves on, a new event at the quarter hour tapped or
 * over the span marked out, occurrences moved into and out of the band, and
 * the page turned while a lifted one rests at the side.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimeGridTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val london = ZoneId.of("Europe/London")
    private lateinit var server: MockWebServer
    private lateinit var model: CalendarViewModel

    /** Whether the server's preferences show events in their own zones. */
    private var zoning = false

    /** The week of Monday 5 October 2026. */
    private val monday = LocalDate.of(2026, 10, 5)
    private val week = (0L until 7L).map { monday.plusDays(it) }

    /** Monday 10:00 in London. */
    private var now = at(monday, 10, 0)

    private val created = mutableListOf<Pair<Long, Long?>>()
    private val ranged = mutableListOf<Pair<LocalDate, LocalDate>>()
    private val opened = mutableListOf<Instance>()
    private lateinit var hours: androidx.compose.foundation.ScrollState
    private val moved = mutableListOf<Pair<Instance, Moved>>()
    private val steps = mutableListOf<Int>()

    private val HOUR = 56.dp
    private val ROW = 24.dp

    private fun at(day: LocalDate, hour: Int, minute: Int): Long = day.atTime(hour, minute).atZone(london).toEpochSecond()

    private fun utc(day: LocalDate): Long = day.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()

    private fun whole(id: String, title: String, first: LocalDate, days: Long = 1) = Instance(
        event = id,
        calendar = "c1",
        summary = title,
        start = utc(first),
        finish = utc(first.plusDays(days)),
        allday = true,
        date = first.toString(),
    )

    private fun timed(id: String, title: String, day: LocalDate, hour: Int, hours: Int = 1) = Instance(
        event = id,
        calendar = "c1",
        summary = title,
        start = at(day, hour, 0),
        finish = at(day, hour + hours, 0),
    )

    @Before
    fun begin() {
        VisibilityStore.hidden(context, emptySet())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/calendars/").substringBefore("?")
                return when (path) {
                    "-/calendars" -> ok("""{"calendars": [{"id": "c1", "name": "Home", "default": true}]}""")
                    "-/preferences/get", "-/preferences/set" -> ok("""{"preferences": {"zones": $zoning}}""")
                    "-/events/bounds" -> ok("""{"first": 0, "last": 0, "endless": false}""")
                    "-/events" -> ok("""{"instances": [], "truncated": false}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun model(): CalendarViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/calendars/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val repository = CalendarsRepository(
            retrofit.create(CalendarsApi::class.java),
            retrofit.create(MenuApi::class.java),
            FileStore(context),
        )
        return CalendarViewModel(context, repository, London).also { model ->
            val deadline = System.currentTimeMillis() + 5_000
            while (model.uiState.value.isLoading || model.uiState.value.preferences.zones != zoning) {
                shadowOf(Looper.getMainLooper()).idle()
                check(System.currentTimeMillis() < deadline) { "timed out" }
                Thread.sleep(10)
            }
        }
    }

    private val shown = mutableStateOf(week)

    private fun show(instances: List<Instance>, days: List<LocalDate> = week) {
        model = model()
        shown.value = days
        val state = CalendarUiState(
            view = if (days.size == 1) CalendarsSection.DAY else CalendarsSection.WEEK,
            anchor = days.first(),
            instances = instances,
            preferences = Preferences(zones = zoning),
            isLoading = false,
        )
        // As the screen lays them out: the hour gutter, then the grid.
        rule.setContent {
            val scroll = rememberHourScroll(state.preferences.hours.start)
            hours = scroll
            var top by remember { mutableStateOf(0.dp) }
            Row(modifier = Modifier.fillMaxSize()) {
                HourGutter(top, scroll, zone = if (zoning) Zones.offset(london.id) else null)
                TimeGrid(
                    days = shown.value,
                    state = state,
                    viewModel = model,
                    onOpen = { opened += it },
                    onCreate = { start, finish -> created += start to finish },
                    onCreateRange = { first, last -> ranged += first to last },
                    onMove = { instance, move -> moved += instance to move },
                    scroll = scroll,
                    stacked = shown.value.size > 1,
                    onStep = { direction ->
                        steps += direction
                        shown.value = shown.value.map { it.plusDays(7L * direction) }
                    },
                    onTop = { offset -> top = offset },
                    clock = { now },
                )
            }
        }
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(100)
    }

    private fun px(dp: Dp): Float = with(rule.density) { dp.toPx() }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /** The point in the root over [day]'s column, [hours] past midnight, through the grid's scroll. */
    private fun grid(day: LocalDate, hours: Float, days: List<LocalDate> = week): Offset {
        val hoursBox = bounds("hours")
        val column = hoursBox.width / days.size
        val x = hoursBox.left + column * (days.indexOf(day) + 0.5f)
        // The grid opens scrolled to the first working hour, 08:00.
        val y = hoursBox.top + (hours - 8f) * px(HOUR)
        return Offset(x, y)
    }

    /** The point in the root over [day]'s column in the band's first row. */
    private fun band(day: LocalDate, row: Int = 0): Offset {
        val box = bounds("band")
        val column = box.width / week.size
        return Offset(box.left + column * (week.indexOf(day) + 0.5f), box.top + px(ROW) * (row + 0.5f))
    }

    private fun press(at: Offset) {
        rule.onRoot().performTouchInput { down(at) }
        rule.mainClock.advanceTimeBy(1000)
    }

    private fun move(to: Offset) {
        rule.onRoot().performTouchInput { moveTo(to) }
        rule.mainClock.advanceTimeBy(50)
    }

    private fun release() {
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(100)
    }

    // ---- the band ----

    @Test
    fun `the band is there and labelled with no all-day events, and names the zone when events show in theirs`() {
        show(emptyList())
        assertEquals(1, rule.onAllNodesWithTag("band").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText(context.getString(R.string.calendars_event_allday)).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("gutter-zone").fetchSemanticsNodes().size)
    }

    @Test
    fun `with events in their own zones the band names the zone the hours read in`() {
        zoning = true
        show(emptyList())
        val zone = rule.onNodeWithTag("gutter-zone").fetchSemanticsNode()
        assertEquals(
            Zones.offset("Europe/London"),
            zone.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text },
        )
    }

    @Test
    fun `a multi-day event is one bar across its days, and no all-day event is left out`() {
        val wednesday = monday.plusDays(2)
        val many = (1..6).map { whole("w$it", "Item $it", wednesday) }
        show(many + whole("trip", "Trip", monday.plusDays(1), days = 3))
        val trip = rule.onAllNodesWithText("Trip").fetchSemanticsNodes()
        assertEquals(1, trip.size)
        val column = (bounds("band").width) / 7
        assertTrue("three columns wide", trip.single().boundsInRoot.width > column * 2.5f)
        for (index in 1..6) {
            assertEquals("Item $index", 1, rule.onAllNodesWithText("Item $index").fetchSemanticsNodes().size)
        }
    }

    // ---- headings and gutter ----

    @Test
    fun `a single day is headed on one line, and midnight has no hour label`() {
        val format = Format(UserPreferences())
        show(emptyList(), days = listOf(monday))
        assertEquals(0, rule.onAllNodesWithText(format.formatHour(0)).fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText(format.formatHour(1)).fetchSemanticsNodes().size)
        val locale = context.resources.configuration.locales[0]
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEd")
        val heading = org.mochios.calendars.ui.calendar.heading(monday, pattern, locale)
        assertEquals(1, rule.onAllNodesWithText(heading).fetchSemanticsNodes().size)
    }

    @Test
    fun `a week heads each column with its weekday above its day`() {
        show(emptyList())
        val locale = context.resources.configuration.locales[0]
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEd")
        val heading = org.mochios.calendars.ui.calendar.heading(monday, pattern, locale)
        assertEquals(0, rule.onAllNodesWithText(heading).fetchSemanticsNodes().size)
        // The heading is clickable, which merges its two lines into one node.
        val number = rule.onAllNodesWithText(monday.dayOfMonth.toString(), useUnmergedTree = true)
            .fetchSemanticsNodes()
        val short = monday.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale)
        val weekday = rule.onAllNodesWithText(short, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(number.isNotEmpty() && weekday.isNotEmpty())
        assertTrue(weekday.first().boundsInRoot.bottom <= number.first().boundsInRoot.top + 1f)
    }

    // ---- now ----

    @Test
    fun `today carries no line at the present moment`() {
        show(emptyList())
        val error = lightColorScheme().error
        val pixels = rule.onRoot().captureToImage().toPixelMap()
        var found = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                if (pixels[x, y].toArgb() == error.toArgb()) found++
            }
        }
        assertEquals(0, found)
    }

    // ---- new events ----

    @Test
    fun `a tap on empty grid starts an event at the quarter hour, for the default length`() {
        show(emptyList())
        val tuesday = monday.plusDays(1)
        rule.onRoot().performTouchInput { click(grid(tuesday, 9f + 20f / 60f)) }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(listOf(at(tuesday, 9, 15) to null), created)
    }

    @Test
    fun `a long press and a drag across empty grid marks out the new event's span`() {
        show(emptyList())
        val tuesday = monday.plusDays(1)
        press(grid(tuesday, 10f))
        move(grid(tuesday, 11f))
        move(grid(tuesday, 12f))
        rule.onNodeWithTag("creating").assertExists()
        release()
        assertEquals(listOf(at(tuesday, 10, 0) to at(tuesday, 12, 0)), created)
    }

    @Test
    fun `a long press on empty grid that marks nothing out starts an event of the default length there`() {
        show(emptyList())
        val tuesday = monday.plusDays(1)
        press(grid(tuesday, 14f))
        release()
        assertEquals(listOf(at(tuesday, 14, 0) to null), created)
    }

    // ---- picking days in the band ----

    @Test
    fun `a tap on an empty stretch of the band makes an all-day event on that day`() {
        show(emptyList())
        val wednesday = monday.plusDays(2)
        rule.onRoot().performTouchInput { click(band(wednesday)) }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(listOf(wednesday to wednesday), ranged)
        assertTrue(created.isEmpty())
    }

    @Test
    fun `a tap on a bar in the band opens it and makes no event`() {
        val wednesday = monday.plusDays(2)
        show(listOf(whole("e2", "Party", wednesday)))
        rule.onRoot().performTouchInput { click(band(wednesday)) }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(listOf("e2"), opened.map { it.event })
        assertTrue(ranged.isEmpty())
    }

    @Test
    fun `a long press on the band and a drag along it picks a run of days`() {
        show(emptyList())
        val tuesday = monday.plusDays(1)
        press(band(tuesday))
        move(band(tuesday.plusDays(1)))
        move(band(tuesday.plusDays(2)))
        rule.onNodeWithTag("picked").assertExists()
        release()
        assertEquals(listOf(tuesday to tuesday.plusDays(2)), ranged)
        assertTrue(created.isEmpty())
    }

    @Test
    fun `a run picked backwards along the band runs from its earlier day`() {
        show(emptyList())
        val friday = monday.plusDays(4)
        press(band(friday))
        move(band(friday.minusDays(1)))
        move(band(friday.minusDays(3)))
        release()
        assertEquals(listOf(monday.plusDays(1) to friday), ranged)
    }

    @Test
    fun `a band pick the system takes the finger from makes nothing`() {
        show(emptyList())
        press(band(monday.plusDays(1)))
        move(band(monday.plusDays(3)))
        rule.onRoot().performTouchInput { cancel() }
        rule.mainClock.advanceTimeBy(100)
        assertTrue(ranged.isEmpty())
        assertTrue(rule.onAllNodesWithTag("picked").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a carried block the system takes the finger from stays where it was`() {
        val tuesday = monday.plusDays(1)
        show(listOf(timed("e1", "Standup", tuesday, 9)))
        press(grid(tuesday, 9.5f))
        move(grid(tuesday.plusDays(1), 11.5f))
        rule.onRoot().performTouchInput { cancel() }
        rule.mainClock.advanceTimeBy(100)
        assertTrue(moved.isEmpty())
    }

    @Test
    fun `picking days along the band leaves the hours where they are`() {
        show(emptyList())
        val before = hours.value
        assertTrue(before > 0)
        press(band(monday.plusDays(1)))
        move(band(monday.plusDays(3)))
        rule.mainClock.advanceTimeBy(1_500)
        assertEquals(before, hours.value)
        release()
    }

    // ---- the band and the grid ----

    @Test
    fun `a block dropped in the band becomes all day on the day it is dropped on`() {
        val tuesday = monday.plusDays(1)
        show(listOf(timed("e1", "Standup", tuesday, 9)))
        press(grid(tuesday, 9.5f))
        move(grid(tuesday.plusDays(1), 9.5f))
        move(band(tuesday.plusDays(1)))
        release()
        assertEquals(Moved.Whole(tuesday.plusDays(1)), moved.single().second)
    }

    @Test
    fun `a bar dropped in the grid becomes timed at the quarter hour it lands on`() {
        val thursday = monday.plusDays(3)
        show(listOf(whole("e2", "Party", thursday)))
        press(band(thursday))
        move(grid(thursday, 11f))
        move(grid(monday.plusDays(1), 11f))
        release()
        assertEquals(Moved.Timed(monday.plusDays(1), 11f), moved.single().second)
    }

    @Test
    fun `a bar grabbed by a later day moves by as many days as the finger, not to the finger`() {
        val tuesday = monday.plusDays(1)
        show(listOf(whole("trip", "Trip", tuesday, days = 3)))
        // Taken by Thursday, its last day, and carried to Friday.
        press(band(tuesday.plusDays(2)))
        move(band(tuesday.plusDays(2)).copy(x = band(tuesday.plusDays(2)).x + 4f))
        move(band(tuesday.plusDays(3)))
        release()
        assertEquals(Moved.Days(tuesday.plusDays(1)), moved.single().second)
    }

    @Test
    fun `a bar that never leaves its day goes nowhere`() {
        val tuesday = monday.plusDays(1)
        show(listOf(whole("e2", "Party", tuesday)))
        press(band(tuesday))
        move(band(tuesday).copy(x = band(tuesday).x + 6f))
        release()
        assertTrue(moved.isEmpty())
    }

    // ---- turning the page ----

    @Test
    fun `a lifted block resting at the side turns to the next week and lands there`() {
        val friday = monday.plusDays(4)
        show(listOf(timed("e1", "Standup", friday, 9)))
        press(grid(friday, 9.5f))
        val side = bounds("hours").right - 4f
        move(Offset(side, grid(friday, 9.5f).y))
        rule.mainClock.advanceTimeBy(700)
        assertEquals(listOf(1), steps)
        release()
        val landed = moved.single().second as Moved.Time
        // Dropped on the next week's Sunday, at the same time of day.
        assertEquals(at(monday.plusDays(13), 9, 0), landed.start)
    }

    @Test
    fun `a new event's span never turns the page`() {
        show(emptyList())
        val friday = monday.plusDays(4)
        press(grid(friday, 9f))
        move(Offset(bounds("hours").right - 4f, grid(friday, 10f).y))
        rule.mainClock.advanceTimeBy(1_500)
        release()
        assertTrue(steps.isEmpty())
    }

    // ---- how a block looks ----

    /** How many of the root's pixels are the blocks' red. */
    private fun reds(): Int {
        val pixels = rule.onRoot().captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val colour = pixels[x, y]
                if (colour.red > 0.85f && colour.green < 0.3f && colour.blue < 0.3f) count++
            }
        }
        return count
    }

    /** [title]'s block from 10:00 for [minutes] on Monday, in red. */
    private fun red(title: String, minutes: Int) = Instance(
        event = title,
        calendar = "c1",
        colour = "#ff0000",
        summary = title,
        start = at(monday, 10, 0),
        finish = at(monday, 10, 0) + minutes * 60L,
    )

    /** The block's time as it writes it from [start] for [minutes], "09:00 to 11:00", in the user's zone. */
    private fun span(start: Long, minutes: Int): String {
        val format = org.mochios.android.i18n.Format(org.mochios.android.i18n.UserPreferences())
        return context.getString(R.string.calendars_range, format.formatTime(start), format.formatTime(start + minutes * 60L))
    }

    @Test
    fun `a block stands on the cards' own tone, its colour only in its dot`() {
        // Today, with its red line, is another week; the block is still to come.
        now = at(monday.minusDays(14), 10, 0)
        show(listOf(red("Meeting", 120)), days = listOf(monday))
        val red = reds()
        // An eight-point dot is some dozens of pixels; a card filled with
        // the colour would be thousands.
        assertTrue("red pixels: $red", red in 20..200)
    }

    @Test
    fun `a tall block opens with its dot and time, its title beneath`() {
        now = at(monday.minusDays(14), 10, 0)
        show(listOf(red("Meeting", 120)), days = listOf(monday))
        val title = rule.onNodeWithText("Meeting", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val time = rule.onNodeWithText(span(at(monday, 10, 0), 120), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("time $time, title $title", time.bottom <= title.top + 1f)
    }

    @Test
    fun `a short block reads its dot, its title and its time on one line`() {
        now = at(monday.minusDays(14), 10, 0)
        show(listOf(red("Call", 15)), days = listOf(monday))
        val title = rule.onNodeWithText("Call", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val time = rule.onNodeWithText(span(at(monday, 10, 0), 15), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("time $time, title $title", time.left >= title.right - 1f && time.top < title.bottom)
        val red = reds()
        assertTrue("red pixels: $red", red in 20..200)
    }

    @Test
    fun `a week's block puts its dot and time above its title`() {
        now = at(monday.minusDays(14), 10, 0)
        show(listOf(red("Meeting", 120)))
        val title = rule.onNodeWithText("Meeting", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val time = rule.onNodeWithText(span(at(monday, 10, 0), 120), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("time $time, title $title", time.bottom <= title.top + 1f)
        val red = reds()
        assertTrue("red pixels: $red", red in 20..200)
    }

    @Test
    fun `a week's block too short for its time reads its dot beside its title`() {
        now = at(monday.minusDays(14), 10, 0)
        show(listOf(red("Call", 15)))
        rule.onNodeWithText("Call", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(0, rule.onAllNodesWithText(span(at(monday, 10, 0), 15), useUnmergedTree = true).fetchSemanticsNodes().size)
        val red = reds()
        assertTrue("red pixels: $red", red in 20..200)
    }
}
