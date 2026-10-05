// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.model.Hours
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.storage.VisibilityStore
import org.mochios.calendars.ui.calendar.CalendarUiState
import org.mochios.calendars.ui.calendar.CalendarViewModel
import org.mochios.calendars.ui.calendar.Page
import org.mochios.calendars.ui.calendar.rememberHourScroll
import org.mochios.calendars.ui.editor.picked
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
 * Each view's page as the screen draws it: a run of days picked across the
 * month and multiweek grids or along the day and week views' all-day band
 * opens the editor all day over the run, from the working hours' start on
 * its first day to the default length later on its last, as the web does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val london = ZoneId.of("Europe/London")
    private lateinit var server: MockWebServer
    private lateinit var model: CalendarViewModel

    /** What the page asked the editor to open with: start, all day or not, finish. */
    private val opened = mutableListOf<Triple<Long, Boolean?, Long?>>()

    /** Monday 5 October 2026. */
    private val monday = LocalDate.of(2026, 10, 5)

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

    private fun show(view: String): CalendarUiState {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/calendars/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val repository = CalendarsRepository(
            retrofit.create(CalendarsApi::class.java),
            retrofit.create(MenuApi::class.java),
            FileStore(context),
        )
        model = CalendarViewModel(context, repository, London)
        val deadline = System.currentTimeMillis() + 5_000
        while (model.uiState.value.isLoading) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
        val state = CalendarUiState(view = view, anchor = monday, focus = monday, isLoading = false)
        rule.setContent {
            Page(
                state = state,
                viewModel = model,
                selected = null,
                scroll = rememberHourScroll(state.preferences.hours.start),
                onOpen = {},
                onNewEvent = { start, allday, finish -> opened += Triple(start, allday, finish) },
                onMove = { _, _ -> },
                onMoveDay = { _, _ -> },
            )
        }
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(100)
        return state
    }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /** A point inside [day]'s cell on a grid whose rows start on [weeks]. */
    private fun cell(day: LocalDate, weeks: List<LocalDate>): Offset {
        val box = bounds("weeks")
        val numbers = with(rule.density) { 22.dp.toPx() }
        val row = weeks.indexOfLast { !it.isAfter(day) }
        val column = java.time.temporal.ChronoUnit.DAYS.between(weeks[row], day).toInt()
        val width = (box.width - numbers) / 7
        val height = box.height / weeks.size
        return Offset(box.left + numbers + width * (column + 0.6f), box.top + height * (row + 0.7f))
    }

    /** A point in the band over [day]'s column among [days]. */
    private fun band(day: LocalDate, days: List<LocalDate>): Offset {
        val box = bounds("band")
        val column = box.width / days.size
        return Offset(box.left + column * (days.indexOf(day) + 0.5f), box.top + with(rule.density) { 12.dp.toPx() })
    }

    private fun pick(from: Offset, to: Offset) {
        rule.onRoot().performTouchInput { down(from) }
        rule.mainClock.advanceTimeBy(1000)
        rule.onRoot().performTouchInput { moveTo(to) }
        rule.mainClock.advanceTimeBy(50)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(100)
    }

    /** The editor opened all day over [first] to [last], with the default preferences' times beneath. */
    private fun expected(first: LocalDate, last: LocalDate): Triple<Long, Boolean?, Long?> {
        val (start, finish) = picked(first, last, Hours(), 60, london)
        return Triple(start, true, finish)
    }

    @Test
    fun `a run picked across the month grid opens the editor all day over it, on its first day`() {
        val state = show(CalendarsSection.MONTH)
        val weeks = model.weeks(state)
        val first = LocalDate.of(2026, 10, 13)
        pick(cell(first, weeks), cell(first.plusDays(8), weeks))
        assertEquals(listOf(expected(first, first.plusDays(8))), opened)
        assertEquals(first, model.uiState.value.focus)
    }

    @Test
    fun `a run picked across the multiweek grid opens the editor all day over it`() {
        val state = show(CalendarsSection.MULTIWEEK)
        val weeks = model.weeks(state)
        val first = monday.plusDays(1)
        pick(cell(first, weeks), cell(first.plusDays(9), weeks))
        assertEquals(listOf(expected(first, first.plusDays(9))), opened)
    }

    @Test
    fun `a run picked along the week view's band opens the editor all day over it`() {
        val state = show(CalendarsSection.WEEK)
        val days = model.days(state)
        pick(band(days[1], days), band(days[3], days))
        assertEquals(listOf(expected(days[1], days[3])), opened)
    }

    @Test
    fun `a tap on the day view's band opens the editor all day on that day`() {
        show(CalendarsSection.DAY)
        rule.onRoot().performTouchInput { click(band(monday, listOf(monday))) }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(listOf(expected(monday, monday)), opened)
    }
}
