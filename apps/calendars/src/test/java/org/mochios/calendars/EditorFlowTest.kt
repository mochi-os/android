// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.di.Viewer
import org.mochios.calendars.navigation.CalendarsApp
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.ui.editor.EventEditScreen
import org.mochios.calendars.ui.editor.EventEditViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import org.mochios.android.R as MochiR

/**
 * The event editor against a server, as the web editor behaves: an event
 * that will not load is a failure with Retry, a write over an event changed
 * elsewhere is refused with Reload, a save says whether it created, and
 * nothing more can be asked of the event while a save runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorFlowTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: CalendarsRepository

    /** Answers by path, in order, the last one repeating. */
    private val answers = ConcurrentHashMap<String, ConcurrentLinkedQueue<MockResponse>>()
    private val asked = ConcurrentLinkedQueue<String>()

    private var saved: Boolean? = null
    private var deleted = false

    @Before
    fun start() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/calendars/").substringBefore("?")
                asked.add(path)
                val queue = answers[path] ?: return MockResponse().setResponseCode(404)
                return if (queue.size > 1) queue.poll()!! else queue.peek()!!
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
        respond("-/calendars", ok("""{"calendars": [{"id": "c1", "name": "Home", "default": true}]}"""))
        respond("-/preferences/get", ok("""{"preferences": {}}"""))
    }

    @After
    fun stop() = server.shutdown()

    private fun respond(path: String, vararg responses: MockResponse) {
        answers[path] = ConcurrentLinkedQueue(responses.toList())
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun event(etag: String, title: String) = ok(
        """{"event": {"id": "e1", "calendar": "c1", "etag": "$etag", "recurring": false, "components": [
            {"name": "VEVENT", "properties": [
                {"name": "UID", "params": {}, "value": "uid-1"},
                {"name": "SUMMARY", "params": {}, "value": "$title"},
                {"name": "DTSTART", "params": {}, "value": "20261002T100000Z"},
                {"name": "DTEND", "params": {}, "value": "20261002T110000Z"}
            ], "components": []}
        ]}}""",
    )

    private val changed = MockResponse().setResponseCode(412).setBody("""{"error": "changed"}""")

    private fun show(vararg route: Pair<String, String>) {
        val model = EventEditViewModel(context, repository, London, SavedStateHandle(mapOf(*route)))
        rule.setContent {
            EventEditScreen(
                onBack = {},
                onSaved = { saved = it },
                onCopied = {},
                onDeleted = { deleted = true },
                onCopy = { _, _, _ -> },
                viewModel = model,
            )
        }
    }

    private fun waitFor(text: String) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun string(id: Int) = context.getString(id)

    /**
     * Waits for [done], a flag rather than a node: reading the nodes is what
     * lets the main thread run the response the flag is waiting on.
     */
    private fun answered(done: () -> Boolean) = rule.waitUntil(5_000) {
        rule.onAllNodes(isRoot()).fetchSemanticsNodes()
        done()
    }

    @Test
    fun `an event that will not load shows why and Retry, never a form to save`() {
        respond("-/events/get", MockResponse().setResponseCode(500).setBody("""{"error": "down"}"""), event("a", "Stand-up"))
        show("event" to "e1")
        waitFor(string(MochiR.string.common_retry))
        assertEquals(0, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText(string(MochiR.string.common_save)).fetchSemanticsNodes().size)
        rule.onNodeWithText(string(MochiR.string.common_retry)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Stand-up")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a save over an event changed elsewhere is refused, and Reload reads it again`() {
        respond("-/events/get", event("a", "Stand-up"), event("b", "Moved elsewhere"))
        respond("-/events/update", changed)
        show("event" to "e1")
        waitFor("Stand-up")
        rule.onNodeWithText(string(MochiR.string.common_save)).performScrollTo().performClick()
        waitFor(string(R.string.calendars_event_changed))
        // One refused write and no second one over the change.
        assertEquals(1, asked.count { it == "-/events/update" })
        assertEquals(null, saved)
        rule.onNodeWithText(string(R.string.calendars_reload)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Moved elsewhere")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, asked.count { it == "-/events/update" })
    }

    @Test
    fun `a delete of an event changed elsewhere is refused`() {
        respond("-/events/get", event("a", "Stand-up"))
        respond("-/events/delete", changed)
        show("event" to "e1")
        waitFor("Stand-up")
        rule.onNodeWithContentDescription(string(R.string.calendars_delete)).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(string(R.string.calendars_scope_delete)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasText(string(R.string.calendars_delete)) and hasClickAction()).performClick()
        waitFor(string(R.string.calendars_event_changed))
        assertFalse(deleted)
        // Deleted outright from the copy read, never read again and deleted anyway.
        assertEquals(1, asked.count { it == "-/events/get" })
    }

    @Test
    fun `saving says whether it created the event or changed one`() {
        respond("-/events/create", event("a", "Lunch"))
        show()
        rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasSetTextAction())[0].performTextReplacement("Lunch")
        rule.onNodeWithText(string(MochiR.string.common_save)).performScrollTo().performClick()
        answered { saved != null }
        assertEquals(true, saved)
        assertEquals(R.string.calendars_event_created, CalendarsApp.said(CalendarsApp.CREATED))
        assertEquals(R.string.calendars_event_saved, CalendarsApp.said(CalendarsApp.CHANGED))
        assertEquals(null, CalendarsApp.said(""))
    }

    @Test
    fun `a series cut by this and following opens in the editor ending before the cut`() {
        // 11:00 London daily from the 2nd, cut at the 5th's occurrence a second before it.
        respond(
            "-/events/get",
            ok(
                """{"event": {"id": "e1", "calendar": "c1", "etag": "a", "recurring": true, "components": [
                    {"name": "VEVENT", "properties": [
                        {"name": "UID", "params": {}, "value": "uid-1"},
                        {"name": "SUMMARY", "params": {}, "value": "Stand-up"},
                        {"name": "DTSTART", "params": {}, "value": "20261002T100000Z"},
                        {"name": "DTEND", "params": {}, "value": "20261002T110000Z"},
                        {"name": "RRULE", "params": {}, "value": "FREQ=DAILY;UNTIL=20261005T095959Z"}
                    ], "components": []}
                ]}}""",
            ),
        )
        val model = EventEditViewModel(context, repository, London, SavedStateHandle(mapOf("event" to "e1")))
        rule.setContent {
            EventEditScreen(onBack = {}, onSaved = {}, onCopied = {}, onDeleted = {}, onCopy = { _, _, _ -> }, viewModel = model)
        }
        waitFor("Stand-up")
        assertEquals(java.time.LocalDate.of(2026, 10, 4), model.uiState.value.recurrence.until)
    }

    @Test
    fun `saving a stored event says it saved rather than created`() {
        respond("-/events/get", event("a", "Stand-up"))
        respond("-/events/update", event("b", "Stand-up"))
        show("event" to "e1")
        waitFor("Stand-up")
        rule.onNodeWithText(string(MochiR.string.common_save)).performScrollTo().performClick()
        answered { saved != null }
        assertEquals(false, saved)
    }

    @Test
    fun `a span marked out on the grid opens as the new event's start and end`() {
        val start = java.time.ZonedDateTime.of(2026, 10, 6, 10, 0, 0, 0, java.time.ZoneId.of("Europe/London")).toEpochSecond()
        val model = EventEditViewModel(
            context,
            repository,
            London,
            SavedStateHandle(mapOf("start" to "$start", "finish" to "${start + 7_200}", "allday" to "0")),
        )
        val deadline = System.currentTimeMillis() + 5_000
        while (model.uiState.value.isLoading) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
        assertEquals(start, model.uiState.value.start)
        assertEquals(start + 7_200, model.uiState.value.finish)
    }

    @Test
    fun `a run of days picked on the grid opens all day across them, with its times beneath`() {
        val london = java.time.ZoneId.of("Europe/London")
        val tuesday = java.time.LocalDate.of(2026, 10, 6)
        val start = tuesday.atTime(8, 0).atZone(london).toEpochSecond()
        val finish = tuesday.plusDays(2).atTime(9, 0).atZone(london).toEpochSecond()
        val model = EventEditViewModel(
            context,
            repository,
            London,
            SavedStateHandle(mapOf("start" to "$start", "finish" to "$finish", "allday" to "1")),
        )
        val deadline = System.currentTimeMillis() + 5_000
        while (model.uiState.value.isLoading) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
        val opened = model.uiState.value
        assertEquals(true, opened.allday)
        // An all-day form holds its first day's UTC midnight, and the day after its last.
        assertEquals(tuesday.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond(), opened.start)
        assertEquals(tuesday.plusDays(3).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond(), opened.finish)
        model.allday(false)
        assertEquals(start, model.uiState.value.start)
        assertEquals(finish, model.uiState.value.finish)
    }

    @Test
    fun `copy and delete wait while a save runs`() {
        respond("-/events/get", event("a", "Stand-up"))
        respond("-/events/update", event("b", "Stand-up").setBodyDelay(3, TimeUnit.SECONDS))
        show("event" to "e1")
        waitFor("Stand-up")
        val copy = rule.onNodeWithContentDescription(string(R.string.calendars_event_copy))
        val delete = rule.onNodeWithContentDescription(string(R.string.calendars_delete))
        copy.assertIsEnabled()
        delete.assertIsEnabled()
        rule.onNodeWithText(string(MochiR.string.common_save)).performScrollTo().performClick()
        rule.waitForIdle()
        copy.assertIsNotEnabled()
        delete.assertIsNotEnabled()
    }
}

/** A viewer in London whose week starts on Monday, on a test server. */
object London : Viewer {
    override fun zone(): String = "Europe/London"

    override fun week(): Int = 1

    override suspend fun server(): String = "https://example.org"
}
