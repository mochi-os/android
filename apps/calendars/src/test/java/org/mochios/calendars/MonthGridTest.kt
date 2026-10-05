// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.model.Instance
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.storage.VisibilityStore
import org.mochios.calendars.ui.calendar.CalendarUiState
import org.mochios.calendars.ui.calendar.CalendarViewModel
import org.mochios.calendars.ui.calendar.MonthGrid
import org.mochios.calendars.ui.calendar.number
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
 * The month and multiweek views as the web's month grid draws them: each
 * row led by its week number, the days outside the month shaded, and a
 * carried chip that turns the page when it rests at the top or bottom and
 * lands on the day under the finger in the range turned to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonthGridTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val london = ZoneId.of("Europe/London")
    private lateinit var server: MockWebServer

    private val moved = mutableListOf<Pair<Instance, LocalDate>>()
    private val steps = mutableListOf<Int>()
    private val created = mutableListOf<LocalDate>()
    private val ranged = mutableListOf<Pair<LocalDate, LocalDate>>()
    private val opened = mutableListOf<Instance>()
    private val holding = mutableListOf<Boolean>()

    /** October 2026's grid: six Monday-first weeks from 28 September. */
    private fun grid(month: LocalDate): List<LocalDate> {
        val first = month.withDayOfMonth(1)
        val start = first.minusDays(((first.dayOfWeek.value + 6) % 7).toLong())
        return (0L until 6L).map { start.plusWeeks(it) }
    }

    private val shown = mutableStateOf(LocalDate.of(2026, 10, 1))

    @Before
    fun begin() {
        VisibilityStore.hidden(context, emptySet())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/calendars/").substringBefore("?")
                return when (path) {
                    "-/calendars" -> ok("""{"calendars": [{"id": "c1", "name": "Home", "default": true}]}""")
                    "-/preferences/get", "-/preferences/set" -> ok("""{"preferences": {}}""")
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
            while (model.uiState.value.isLoading) {
                shadowOf(Looper.getMainLooper()).idle()
                check(System.currentTimeMillis() < deadline) { "timed out" }
                Thread.sleep(10)
            }
        }
    }

    private fun show(instances: List<Instance>) {
        val model = model()
        val state = CalendarUiState(view = CalendarsSection.MONTH, anchor = shown.value, instances = instances, isLoading = false)
        rule.setContent {
            MonthGrid(
                weeks = grid(shown.value),
                month = shown.value.monthValue,
                state = state,
                viewModel = model,
                onOpen = { opened += it },
                onCreate = { created += it },
                onMove = { instance, day -> moved += instance to day },
                onCreateRange = { first, last -> ranged += first to last },
                onLifted = { holding += it },
                onStep = { direction ->
                    steps += direction
                    shown.value = shown.value.plusMonths(direction.toLong())
                },
            )
        }
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(100)
        rule.onRoot().captureToImage()
    }

    private fun meeting(day: LocalDate) = Instance(
        event = "e1",
        calendar = "c1",
        summary = "Stand-up",
        start = day.atTime(10, 0).atZone(london).toEpochSecond(),
        finish = day.atTime(11, 0).atZone(london).toEpochSecond(),
    )

    private fun px(dp: Dp): Float = with(rule.density) { dp.toPx() }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /**
     * A point inside the cell for [day] on the grid of [month], [side] of
     * the way across it: towards its far side unless asked otherwise.
     */
    private fun cell(day: LocalDate, month: LocalDate = shown.value, side: Float = 0.8f): Offset {
        val weeks = grid(month)
        val box = bounds("weeks")
        val row = weeks.indexOfLast { !it.isAfter(day) }
        val column = ((day.dayOfWeek.value + 6) % 7)
        val width = box.width / 7
        val height = box.height / weeks.size
        return Offset(box.left + width * (column + side), box.top + height * (row + 0.7f))
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

    // ---- week numbers ----

    @Test
    fun `each row is labelled with its ISO week number`() {
        show(emptyList())
        val numbers = rule.onAllNodesWithTag("week-number", useUnmergedTree = true).fetchSemanticsNodes().map {
            it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { text -> text.text }
        }
        assertEquals(listOf("40", "41", "42", "43", "44", "45"), numbers)
    }

    @Test
    fun `a week number sits in its row's first day, opposite the date, and the days start at the grid's edge`() {
        show(emptyList())
        val box = bounds("weeks")
        val cells = listOf("inside", "outside").flatMap { tag ->
            rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        }
        val numbers = rule.onAllNodesWithTag("week-number", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(6, numbers.size)
        for (number in numbers.map { it.boundsInRoot }) {
            val cell = cells.single { it.contains(number.center) }
            assertEquals(box.left, cell.left, 1f)
            // The date is at the cell's start, so the number takes its end.
            assertTrue(number.center.x > cell.center.x)
        }
    }

    @Test
    fun `a row starting on Sunday or Saturday takes the number of the week most of its days are in`() {
        // Sunday 4 October to Saturday 10 October: Monday to Saturday are week 41.
        assertEquals(41, number(LocalDate.of(2026, 10, 4)))
        assertEquals(41, number(LocalDate.of(2026, 10, 3)))
        assertEquals(41, number(LocalDate.of(2026, 10, 5)))
    }

    // ---- how an entry reads ----

    /** An entry's own bounds, the clickable node that holds its words. */
    private fun entry(title: String): Rect = rule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot

    /** The bounds of an entry's title text alone. */
    private fun words(title: String): Rect =
        rule.onAllNodesWithText(title, useUnmergedTree = true).fetchSemanticsNodes().last().boundsInRoot

    /** The bounds of the first text that reads as a clock, such as "10:00 AM". */
    private fun clock(): Rect = rule.onAllNodes(
        SemanticsMatcher("a clock reading") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { Regex("^\\d{1,2}[:.]\\d{2}").containsMatchIn(it.text) }
        },
        useUnmergedTree = true,
    ).fetchSemanticsNodes().first().boundsInRoot

    @Test
    fun `a timed entry puts its dot and time on the first line and its title alone on the second`() {
        show(listOf(meeting(LocalDate.of(2026, 10, 14))))
        val chip = entry("Stand-up")
        val title = words("Stand-up")
        val time = clock()
        assertTrue(time.bottom <= title.top + 1f)
        // The title starts at the entry's padding, with no dot before it;
        // the dot and its gap come before the time instead.
        assertEquals(px(4.dp), title.left - chip.left, 1.5f)
        assertEquals(px(4.dp + 8.dp + 4.dp), time.left - chip.left, 1.5f)
    }

    @Test
    fun `the dot sits on the time's line, before the time`() {
        show(listOf(meeting(LocalDate.of(2026, 10, 14))))
        val chip = entry("Stand-up")
        val row = (clock().center.y - chip.top).toInt()
        val pixels = rule.onNodeWithText("Stand-up").captureToImage().toPixelMap()
        // Where the dot is, against the empty end of the same line.
        assertNotEquals(pixels[pixels.width - 2, row], pixels[px(8.dp).toInt(), row])
    }

    @Test
    fun `a timed entry beneath a full band of bars keeps both its lines in view`() {
        val alone = LocalDate.of(2026, 10, 14)
        val crowded = LocalDate.of(2026, 10, 21)
        val midnight = crowded.atStartOfDay(london).toEpochSecond()
        val bars = (1..6).map { index ->
            Instance(event = "b$index", calendar = "c1", summary = "Bar $index", allday = true, date = crowded.toString(), start = midnight, finish = midnight + 86400)
        }
        show(bars + meeting(alone) + meeting(crowded).copy(event = "e2", summary = "Crowded"))
        assertEquals(entry("Stand-up").height, entry("Crowded").height, 1f)
    }

    @Test
    fun `an all-day entry stays one line, its dot before its title`() {
        val day = LocalDate.of(2026, 10, 14)
        val midnight = day.atStartOfDay(london).toEpochSecond()
        show(listOf(Instance(event = "a1", calendar = "c1", summary = "Holiday", allday = true, date = day.toString(), start = midnight, finish = midnight + 86400)))
        val chip = entry("Holiday")
        val title = words("Holiday")
        assertEquals(px(4.dp + 8.dp + 4.dp), title.left - chip.left, 1.5f)
        assertTrue(chip.height < px(24.dp))
    }

    // ---- days outside the month ----

    @Test
    fun `days outside the month are shaded, not only their numbers`() {
        show(emptyList())
        assertEquals(11, rule.onAllNodesWithTag("outside").fetchSemanticsNodes().size)
        val outside = rule.onAllNodesWithTag("outside")[0].captureToImage().toPixelMap()
        val inside = rule.onAllNodesWithTag("inside")[10].captureToImage().toPixelMap()
        // Low in each cell, below its number and clear of any entry.
        assertNotEquals(
            inside[inside.width / 2, inside.height - 3],
            outside[outside.width / 2, outside.height - 3],
        )
    }

    // ---- carrying a chip ----

    @Test
    fun `a carried chip lands on the day under the finger`() {
        val thursday = LocalDate.of(2026, 10, 15)
        show(listOf(meeting(thursday)))
        press(rule.onNodeWithText("Stand-up").fetchSemanticsNode().boundsInRoot.center)
        move(cell(thursday.plusDays(6)))
        move(cell(thursday.plusDays(7)))
        release()
        assertEquals(thursday.plusDays(7), moved.single().second)
        // The chip's long press is the lift's: it picks no days.
        assertTrue(ranged.isEmpty() && created.isEmpty())
    }

    @Test
    fun `a chip resting at the bottom turns to the next month and lands on its day there`() {
        val thursday = LocalDate.of(2026, 10, 15)
        show(listOf(meeting(thursday)))
        press(rule.onNodeWithText("Stand-up").fetchSemanticsNode().boundsInRoot.center)
        val box = bounds("weeks")
        val bottom = box.bottom - 6f
        move(Offset(cell(thursday).x, bottom))
        rule.mainClock.advanceTimeBy(700)
        assertEquals(listOf(1), steps)
        release()
        // November's grid ends on the week of 30 November; its Thursday is 3 December.
        assertEquals(LocalDate.of(2026, 12, 3), moved.single().second)
        assertTrue(rule.onAllNodesWithText("Stand-up").fetchSemanticsNodes().isEmpty())
    }

    // ---- picking days ----

    @Test
    fun `a long press on a day and a drag across others picks a run of days, holding the page meanwhile`() {
        show(emptyList())
        val tuesday = LocalDate.of(2026, 10, 13)
        press(cell(tuesday))
        move(cell(tuesday.plusDays(2)))
        move(cell(tuesday.plusDays(8)))
        assertEquals(listOf(false, true), holding)
        release()
        assertEquals(listOf(tuesday to tuesday.plusDays(8)), ranged)
        assertTrue(created.isEmpty())
        assertEquals(listOf(false, true, false), holding)
    }

    @Test
    fun `a run reaches the day whose near edge the finger is on`() {
        show(emptyList())
        val tuesday = LocalDate.of(2026, 10, 13)
        press(cell(tuesday))
        move(cell(tuesday.plusDays(2), side = 0.15f))
        release()
        assertEquals(listOf(tuesday to tuesday.plusDays(2)), ranged)
    }

    @Test
    fun `a run picked backwards runs from its earlier day`() {
        show(emptyList())
        val wednesday = LocalDate.of(2026, 10, 21)
        press(cell(wednesday))
        move(cell(wednesday.minusDays(3)))
        move(cell(wednesday.minusDays(9)))
        release()
        assertEquals(listOf(wednesday.minusDays(9) to wednesday), ranged)
    }

    @Test
    fun `a long press let go on its own day creates once on that day`() {
        show(emptyList())
        val tuesday = LocalDate.of(2026, 10, 13)
        press(cell(tuesday))
        release()
        assertEquals(listOf(tuesday), created)
        assertTrue(ranged.isEmpty())
    }

    @Test
    fun `the days being picked are tinted`() {
        show(emptyList())
        // The October cells in order: day n is the nth inside the month.
        fun shade(day: Int) = rule.onAllNodesWithTag("inside")[day - 1].captureToImage().toPixelMap().let { it[it.width / 2, it.height - 3] }
        val plain = shade(14)
        press(cell(LocalDate.of(2026, 10, 13)))
        move(cell(LocalDate.of(2026, 10, 15)))
        assertNotEquals(plain, shade(14))
        assertEquals(plain, shade(16))
        release()
    }

    @Test
    fun `a pick the system takes the finger from makes nothing, and the next tap creates on its own day`() {
        show(emptyList())
        val tuesday = LocalDate.of(2026, 10, 13)
        press(cell(tuesday))
        move(cell(tuesday.plusDays(2)))
        rule.onRoot().performTouchInput { cancel() }
        rule.mainClock.advanceTimeBy(100)
        assertTrue(ranged.isEmpty() && created.isEmpty())
        rule.onRoot().performTouchInput { click(cell(tuesday.plusDays(7))) }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(listOf(tuesday.plusDays(7)), created)
        assertTrue(ranged.isEmpty())
    }

    @Test
    fun `a carried chip the system takes the finger from stays where it was`() {
        val thursday = LocalDate.of(2026, 10, 15)
        show(listOf(meeting(thursday)))
        press(rule.onNodeWithText("Stand-up").fetchSemanticsNode().boundsInRoot.center)
        move(cell(thursday.plusDays(7)))
        rule.onRoot().performTouchInput { cancel() }
        rule.mainClock.advanceTimeBy(100)
        assertTrue(moved.isEmpty())
    }

    @Test
    fun `a long press on a chip that cannot be lifted picks no days, and letting go opens it`() {
        val thursday = LocalDate.of(2026, 10, 15)
        show(listOf(meeting(thursday).copy(readonly = true)))
        press(rule.onNodeWithText("Stand-up").fetchSemanticsNode().boundsInRoot.center)
        release()
        assertTrue(ranged.isEmpty())
        assertTrue(created.isEmpty())
        assertEquals(listOf("e1"), opened.map { it.event })
    }
}
