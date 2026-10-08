// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
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
import org.mochios.android.i18n.AppContext
import org.mochios.android.i18n.DateFormat
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.storage.VisibilityStore
import org.mochios.calendars.ui.calendar.AgendaList
import org.mochios.calendars.ui.calendar.CalendarViewModel
import org.mochios.calendars.ui.router.CalendarsSection
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The list view as the web's draws it: a first page with nothing on it
 * still reaches what comes after, a page that fails stops and offers Retry,
 * "Earlier events" reaches back past empty pages, as far as before 1970,
 * each day is headed across the width by its weekday and its date in the
 * user's date format, the heading staying at the top, its rows open with
 * their dots on no fill of their colour, and a wide screen names each row's
 * calendar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ListViewTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val london = ZoneId.of("Europe/London")
    private lateinit var server: MockWebServer
    private val asked = ConcurrentLinkedQueue<RecordedRequest>()

    /** The anchor day: Monday 5 October 2026. */
    private val anchor = LocalDate.of(2026, 10, 5)

    /** The events there are, by day and title, and the bounds the server reports. */
    @Volatile private var events = listOf<Pair<LocalDate, String>>()
    @Volatile private var first = 0L
    @Volatile private var last = 0L

    /** How many more listings that reach past [failFrom] fail. */
    @Volatile private var failures = 0
    @Volatile private var failFrom = Long.MAX_VALUE

    private fun noon(day: LocalDate) = day.atTime(12, 0).atZone(london).toEpochSecond()

    @Before
    fun begin() {
        AppContext.set(context)
        VisibilityStore.hidden(context, emptySet())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked.add(request)
                val path = request.path.orEmpty().substringAfter("/calendars/").substringBefore("?")
                val url = request.requestUrl
                return when (path) {
                    "-/calendars" -> ok("""{"calendars": [{"id": "c1", "name": "Home", "default": true}]}""")
                    "-/preferences/get", "-/preferences/set" -> ok("""{"preferences": {}}""")
                    "-/events/bounds" -> ok("""{"first": $first, "last": $last, "endless": false}""")
                    "-/events" -> {
                        val start = url?.queryParameter("start")?.toLongOrNull() ?: 0
                        val finish = url?.queryParameter("finish")?.toLongOrNull() ?: 0
                        if (finish > failFrom && failures > 0) {
                            failures--
                            return MockResponse().setResponseCode(500).setBody("""{"error": "down"}""")
                        }
                        val listed = events.filter { noon(it.first) in start until finish }.joinToString(",") { (day, title) ->
                            """{"event": "e-$title", "calendar": "c1", "colour": "#ff0000", "summary": "$title", "start": ${noon(day)}, "finish": ${noon(day) + 3_600}}"""
                        }
                        ok("""{"instances": [$listed], "truncated": false}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun listings() = asked.filter { it.path.orEmpty().contains("/-/events?") }

    private fun show(open: LocalDate = anchor, format: Format = Format(UserPreferences())): CalendarViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/calendars/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val repository = CalendarsRepository(
            retrofit.create(CalendarsApi::class.java),
            retrofit.create(MenuApi::class.java),
            FileStore(context),
        )
        val model = CalendarViewModel(context, repository, London)
        model.view(CalendarsSection.LIST)
        model.anchor(open)
        rule.setContent {
            val state by model.uiState.collectAsState()
            CompositionLocalProvider(LocalFormat provides format) {
                AgendaList(state, model, onOpen = {})
            }
        }
        return model
    }

    private fun waitFor(text: String) =
        rule.waitUntil(10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `a first page with nothing on it still reaches the events after it`() {
        events = listOf(anchor.plusDays(150) to "Concert")
        first = noon(anchor.plusDays(150))
        last = noon(anchor.plusDays(150))
        show()
        waitFor("Concert")
        assertEquals(0, rule.onAllNodesWithText(context.getString(R.string.calendars_list_empty)).fetchSemanticsNodes().size)
    }

    @Test
    fun `a page that fails stops the paging and offers Retry, which loads it`() {
        events = listOf(anchor.plusDays(150) to "Concert")
        first = noon(anchor)
        last = noon(anchor.plusDays(150))
        // The second page, which holds the concert, fails once.
        failFrom = anchor.plusDays(100).atStartOfDay(london).toEpochSecond()
        failures = 1
        show()
        val retry = context.getString(org.mochios.android.R.string.common_retry)
        waitFor(retry)
        val tries = listings().size
        // Nothing asks again on its own while the failure stands.
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertEquals(tries, listings().size)
        rule.onNodeWithText(retry).performClick()
        waitFor("Concert")
    }

    @Test
    fun `Earlier events reaches back past empty pages until something lands`() {
        events = listOf(anchor to "Today", anchor.minusDays(250) to "Spring")
        first = noon(anchor.minusDays(250))
        last = noon(anchor)
        show()
        waitFor("Today")
        val before = listings().size
        rule.onNodeWithText(context.getString(R.string.calendars_list_earlier)).performClick()
        waitFor("Spring")
        // Three pages back: two with nothing on them, then the one with the spring event.
        assertEquals(before + 3, listings().size)
    }

    @Test
    fun `a first event before 1970 leaves earlier events to reach back to`() {
        events = listOf(anchor to "Today")
        first = LocalDate.of(1965, 5, 1).atStartOfDay(london).toEpochSecond()
        last = noon(anchor)
        show()
        waitFor("Today")
        rule.onNodeWithText(context.getString(R.string.calendars_list_earlier)).assertExists()
    }

    /** [day]'s heading as the list writes it, its date read in [format]. */
    private fun heading(day: LocalDate, format: Format): String = context.getString(
        R.string.calendars_list_heading,
        day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
        format.formatDate(day.atTime(12, 0).toEpochSecond(java.time.ZoneOffset.UTC), "UTC"),
    )

    @Test
    fun `a day is headed by its weekday and its date in the user's date format, once, with no date column`() {
        events = (1..3).map { anchor to "Item $it" }
        first = noon(anchor)
        last = noon(anchor)
        val format = Format(UserPreferences(dateFormat = DateFormat.DD_SLASH_MM_YYYY))
        show(format = format)
        waitFor("Item 1")
        val text = heading(anchor, format)
        assertTrue(text, text.contains("05/10/2026"))
        assertEquals(1, rule.onAllNodesWithText(text).fetchSemanticsNodes().size)
        // The date column wrote the day's number on its own beside the rows.
        val number = Format(UserPreferences()).formatNumber(anchor.dayOfMonth)
        assertEquals(0, rule.onAllNodesWithText(number).fetchSemanticsNodes().size)
    }

    @Test
    fun `a day's heading stays at the top while its rows scroll beneath it`() {
        events = (1..30).map { anchor to "Item $it" }
        first = noon(anchor)
        last = noon(anchor)
        show()
        waitFor("Item 1")
        rule.onNode(androidx.compose.ui.test.hasScrollAction()).performScrollToNode(hasText("Item 30"))
        rule.waitForIdle()
        rule.onNodeWithText("Item 30").assertIsDisplayed()
        rule.onNodeWithTag("heading").assertIsDisplayed()
        val top = rule.onNodeWithTag("heading").fetchSemanticsNode().boundsInRoot.top
        assertEquals(0f, top, 1f)
    }

    /** How many of [pixels] are the events' red. */
    private fun reds(pixels: androidx.compose.ui.graphics.PixelMap): Int {
        var count = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val colour = pixels[x, y]
                if (colour.red > 0.85f && colour.green < 0.3f && colour.blue < 0.3f) count++
            }
        }
        return count
    }

    @Test
    fun `a row opens with its dot in its colour and stands on no fill of it`() {
        // A day still to come, so the row is not faded as past.
        val day = LocalDate.now(london).plusDays(2)
        events = listOf(day to "Item 1")
        first = noon(day)
        last = noon(day)
        show(open = day)
        waitFor("Item 1")
        val red = reds(rule.onNodeWithTag("row").captureToImage().toPixelMap())
        // An eight-point dot is some dozens of pixels; a card filled with
        // the colour would be thousands.
        assertTrue("red pixels: $red", red in 20..200)
    }

    @Test
    fun `rows of a day are parted by a hairline, and the day's last row has none`() {
        val day = LocalDate.now(london).plusDays(2)
        events = listOf(day to "Item 1", day to "Item 2")
        first = noon(day)
        last = noon(day)
        show(open = day)
        waitFor("Item 2")
        val rows = rule.onAllNodesWithTag("row")
        fun bottom(index: Int): Color = rows[index].captureToImage().toPixelMap().let { it[it.width / 2, it.height - 1] }
        val background = rule.onRoot().captureToImage().toPixelMap().let { it[it.width - 2, it.height - 2] }
        assertTrue("first row's bottom: ${bottom(0)}", bottom(0).toArgb() != background.toArgb())
        assertEquals(background.toArgb(), bottom(1).toArgb())
    }

    @Test
    fun `a day's heading has a rule along its top, darker than its band and the hairline between rows`() {
        val day = LocalDate.now(london).plusDays(2)
        events = listOf(day to "Item 1", day to "Item 2")
        first = noon(day)
        last = noon(day)
        val format = Format(UserPreferences())
        show(open = day, format = format)
        waitFor("Item 2")
        val heading = rule.onNodeWithText(heading(day, format)).captureToImage().toPixelMap()
        val edge = heading[heading.width - 2, 0]
        val band = heading[heading.width - 2, heading.height / 2]
        val row = rule.onAllNodesWithTag("row")[0].captureToImage().toPixelMap()
        val hairline = row[row.width / 2, row.height - 1]
        assertTrue("rule $edge, band $band", edge.luminance() < band.luminance())
        assertTrue("rule $edge, hairline $hairline", edge.luminance() < hairline.luminance())
    }

    @Test
    fun `today's heading is a band in the primary colour, and another day's is not`() {
        val today = LocalDate.now(london)
        events = listOf(today to "Now", today.plusDays(1) to "Later")
        first = noon(today)
        last = noon(today.plusDays(1))
        val format = Format(UserPreferences())
        show(open = today, format = format)
        waitFor("Later")
        val primary = lightColorScheme().primary.toArgb()
        fun band(day: LocalDate): Int =
            rule.onNodeWithText(heading(day, format)).captureToImage().toPixelMap().let { it[it.width - 2, 2] }.toArgb()
        assertEquals(primary, band(today))
        assertTrue(band(today.plusDays(1)) != primary)
    }

    @Test
    fun `today carries no line at the present moment`() {
        val today = LocalDate.now(london)
        events = listOf(today to "Now", today.plusDays(1) to "Later")
        first = noon(today)
        last = noon(today.plusDays(1))
        show(open = today)
        waitFor("Later")
        val error = lightColorScheme().error.toArgb()
        val pixels = rule.onRoot().captureToImage().toPixelMap()
        var found = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                if (pixels[x, y].toArgb() == error) found++
            }
        }
        assertEquals(0, found)
    }

    @Test
    fun `a narrow screen leaves out each row's calendar`() {
        events = listOf(anchor to "Today")
        first = noon(anchor)
        last = noon(anchor)
        show()
        waitFor("Today")
        assertEquals(0, rule.onAllNodesWithText("Home").fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = "w800dp-h800dp")
    fun `a wide screen names each row's calendar`() {
        events = listOf(anchor to "Today")
        first = noon(anchor)
        last = noon(anchor)
        show()
        waitFor("Today")
        waitFor("Home")
    }
}
