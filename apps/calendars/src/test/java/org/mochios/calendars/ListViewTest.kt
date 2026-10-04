// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.mochios.android.i18n.Format
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
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The list view as the web's draws it: a first page with nothing on it
 * still reaches what comes after, a page that fails stops and offers Retry,
 * "Earlier events" reaches back past empty pages, as far as before 1970,
 * the day headings are long dates that stay at the top, and a wide screen
 * names each row's calendar.
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
                            """{"event": "e-$title", "calendar": "c1", "summary": "$title", "start": ${noon(day)}, "finish": ${noon(day) + 3_600}}"""
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

    private fun show(): CalendarViewModel {
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
        model.anchor(anchor)
        rule.setContent {
            val state by model.uiState.collectAsState()
            AgendaList(state, model, onOpen = {})
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

    @Test
    fun `a day's date leads its first event in the date column, once`() {
        events = (1..3).map { anchor to "Item $it" }
        first = noon(anchor)
        last = noon(anchor)
        show()
        waitFor("Item 1")
        val number = Format(UserPreferences()).formatNumber(anchor.dayOfMonth)
        assertEquals(1, rule.onAllNodesWithText(number).fetchSemanticsNodes().size)
        val weekday = anchor.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        assertEquals(1, rule.onAllNodesWithText(weekday).fetchSemanticsNodes().size)
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
