// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.di.Viewer
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.ui.editor.EventEditScreen
import org.mochios.calendars.ui.editor.EventEditViewModel
import org.mochios.calendars.ui.editor.Said
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import android.os.Looper
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
 * The event editor against a server, as the web's side panel behaves: an
 * event that will not load is a failure with Retry, an open event saves as it
 * goes and a write over one changed elsewhere is refused with Reload, a new
 * event is made by Create and the editor goes on as it, and nothing more can
 * be asked of the event while a save runs.
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
    /** The last body sent to each path. */
    private val bodies = ConcurrentHashMap<String, String>()

    /** The editor the screen went on as: event, moment, and what was said. */
    private var followed: Triple<String, Long, Said?>? = null
    private var left = false
    private var deleted = false

    @Before
    fun start() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/calendars/").substringBefore("?")
                asked.add(path)
                bodies[path] = request.body.readUtf8()
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

    private fun event(etag: String, title: String, status: String? = null): MockResponse {
        val marked = status?.let { """{"name": "STATUS", "params": {}, "value": "$it"},""" }.orEmpty()
        return ok(
            """{"event": {"id": "e1", "calendar": "c1", "etag": "$etag", "recurring": false, "components": [
                {"name": "VEVENT", "properties": [
                    {"name": "UID", "params": {}, "value": "uid-1"},
                    {"name": "SUMMARY", "params": {}, "value": "$title"},$marked
                    {"name": "DTSTART", "params": {}, "value": "20261002T100000Z"},
                    {"name": "DTEND", "params": {}, "value": "20261002T110000Z"}
                ], "components": []}
            ]}}""",
        )
    }

    private val changed = MockResponse().setResponseCode(412).setBody("""{"error": "changed"}""")

    private fun show(vararg route: Pair<String, String>): EventEditViewModel {
        val model = EventEditViewModel(context, repository, London, SavedStateHandle(mapOf(*route)))
        rule.setContent {
            EventEditScreen(
                onBack = { left = true },
                onOpen = { event, moment, said -> followed = Triple(event, moment, said) },
                onDeleted = { deleted = true },
                viewModel = model,
            )
        }
        return model
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

    /** Lets a second pass, the rest typing takes before it is saved. */
    private fun rest() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_100))
    }

    /** The title field, the first that takes text. */
    private fun title() = rule.onAllNodes(hasSetTextAction())[0]

    @Test
    fun `an event that will not load shows why and Retry, never a form to save`() {
        respond("-/events/get", MockResponse().setResponseCode(500).setBody("""{"error": "down"}"""), event("a", "Stand-up"))
        show("event" to "e1")
        waitFor(string(MochiR.string.common_retry))
        assertEquals(0, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText(string(R.string.calendars_create_submit)).fetchSemanticsNodes().size)
        rule.onNodeWithText(string(MochiR.string.common_retry)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Stand-up")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `an open event has no Save, and saves a change once typing rests`() {
        respond("-/events/get", event("a", "Stand-up"))
        respond("-/events/update", event("b", "Stand-up, moved"))
        show("event" to "e1")
        waitFor("Stand-up")
        assertEquals(0, rule.onAllNodesWithText(string(MochiR.string.common_save)).fetchSemanticsNodes().size)
        title().performTextReplacement("Stand-up, moved")
        assertFalse(asked.contains("-/events/update"))
        rest()
        answered { asked.contains("-/events/update") }
        assertTrue(bodies["-/events/update"]!!.contains("Stand-up, moved"))
        assertTrue(bodies["-/events/update"]!!.contains("\"etag\":\"a\""))
    }

    @Test
    fun `the next save sends the etag the last came back with`() {
        respond("-/events/get", event("a", "Stand-up"))
        respond("-/events/update", event("b", "Stand-up, moved"))
        val model = show("event" to "e1")
        waitFor("Stand-up")
        title().performTextReplacement("Stand-up, moved")
        model.save()
        answered { asked.count { it == "-/events/update" } == 1 && !model.uiState.value.isSaving }
        title().performTextReplacement("Stand-up, moved again")
        model.save()
        answered { asked.count { it == "-/events/update" } == 2 }
        assertTrue(bodies["-/events/update"]!!.contains("\"etag\":\"b\""))
    }

    @Test
    fun `leaving saves what is pending, then goes`() {
        respond("-/events/get", event("a", "Stand-up"))
        respond("-/events/update", event("b", "Stand-up, moved"))
        val model = show("event" to "e1")
        waitFor("Stand-up")
        title().performTextReplacement("Stand-up, moved")
        model.leave()
        answered { left }
        assertEquals(1, asked.count { it == "-/events/update" })
    }

    @Test
    fun `leaving with no title asks before the change is dropped`() {
        respond("-/events/get", event("a", "Stand-up"))
        val model = show("event" to "e1")
        waitFor("Stand-up")
        title().performTextReplacement("")
        model.leave()
        waitFor(string(R.string.calendars_discard_title))
        assertFalse(left)
        rule.onNodeWithText(string(R.string.calendars_discard)).performClick()
        answered { left }
        assertFalse(asked.contains("-/events/update"))
    }

    @Test
    fun `a save over an event changed elsewhere is refused, and Reload reads it again`() {
        respond("-/events/get", event("a", "Stand-up"), event("b", "Moved elsewhere"))
        respond("-/events/update", changed)
        val model = show("event" to "e1")
        waitFor("Stand-up")
        title().performTextReplacement("Stand-up, moved")
        model.save()
        waitFor(string(R.string.calendars_event_changed))
        // One refused write and no second one over the change.
        assertEquals(1, asked.count { it == "-/events/update" })
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
    fun `Create makes the new event, saving nothing before it, and the editor goes on as it`() {
        respond("-/events/create", event("a", "Lunch"))
        show()
        rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        title().performTextReplacement("Lunch")
        rest()
        assertFalse(asked.contains("-/events/create"))
        rule.onNodeWithText(string(R.string.calendars_create_submit)).performScrollTo().performClick()
        answered { followed != null }
        assertEquals("e1", followed!!.first)
        assertEquals(Said.CREATED, followed!!.third)
        assertTrue(bodies["-/events/create"]!!.contains("Lunch"))
    }

    @Test
    fun `an editor handed over says what the one before it did`() {
        respond("-/events/get", event("a", "Lunch"))
        show("event" to "e1", "said" to "copied")
        waitFor(string(R.string.calendars_event_copied))
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
        val model = show("event" to "e1")
        waitFor("Stand-up")
        assertEquals(java.time.LocalDate.of(2026, 10, 4), model.uiState.value.recurrence.until)
    }

    @Test
    fun `a series saves nothing as it is typed, and asks which occurrences when the field is left`() {
        respond(
            "-/events/get",
            ok(
                """{"event": {"id": "e1", "calendar": "c1", "etag": "a", "recurring": true, "components": [
                    {"name": "VEVENT", "properties": [
                        {"name": "UID", "params": {}, "value": "uid-1"},
                        {"name": "SUMMARY", "params": {}, "value": "Stand-up"},
                        {"name": "DTSTART", "params": {}, "value": "20261002T100000Z"},
                        {"name": "DTEND", "params": {}, "value": "20261002T110000Z"},
                        {"name": "RRULE", "params": {}, "value": "FREQ=DAILY"}
                    ], "components": []}
                ]}}""",
            ),
        )
        respond("-/events/update", event("b", "Stand-up"))
        val model = show("event" to "e1", "occurrence" to "1759399200")
        waitFor("Stand-up")
        title().performTextReplacement("Retro")
        rest()
        rule.waitForIdle()
        assertFalse(asked.contains("-/events/update"))
        assertEquals(null, model.uiState.value.prompt)
        assertEquals(0, rule.onAllNodesWithText(string(R.string.calendars_scope_save)).fetchSemanticsNodes().size)
        rule.onAllNodes(hasSetTextAction())[1].performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(string(R.string.calendars_scope_save)).fetchSemanticsNodes().isNotEmpty()
        }
        model.scope(org.mochios.calendars.ui.editor.Scope.ALL)
        answered { asked.contains("-/events/update") }
        assertTrue(bodies["-/events/update"]!!.contains("Retro"))
        assertEquals(null, followed)
    }

    /** The switch after the "Tentative" label, on the row it shares with All day. */
    private fun tentative(): SemanticsNodeInteraction {
        val label = rule.onNodeWithText(string(R.string.calendars_status_tentative)).performScrollTo()
        val bounds = label.fetchSemanticsNode().boundsInRoot
        val switches = rule.onAllNodes(isToggleable())
        val index = switches.fetchSemanticsNodes().indexOfFirst {
            bounds.center.y in it.boundsInRoot.top..it.boundsInRoot.bottom && it.boundsInRoot.left >= bounds.right
        }
        return switches[index].performScrollTo()
    }

    @Test
    fun `tentative shares the all-day row, after all day`() {
        respond("-/events/get", event("a", "Stand-up"))
        show("event" to "e1")
        waitFor("Stand-up")
        val allday = rule.onNodeWithText(string(R.string.calendars_event_allday)).performScrollTo()
            .fetchSemanticsNode().boundsInRoot
        val tentative = rule.onNodeWithText(string(R.string.calendars_status_tentative))
            .fetchSemanticsNode().boundsInRoot
        assertTrue("$allday $tentative", tentative.center.y in allday.top..allday.bottom)
        assertTrue("$allday $tentative", tentative.left > allday.right)
    }

    /** Every STATUS in the tree the last [path] sent. */
    private fun statuses(path: String = "-/events/update"): List<String> {
        val body = com.google.gson.JsonParser.parseString(bodies[path]).asJsonObject
        return body.getAsJsonArray("components").flatMap { component ->
            component.asJsonObject.getAsJsonArray("properties")
                .map { it.asJsonObject }
                .filter { it.get("name").asString == "STATUS" }
                .map { it.get("value").asString }
        }
    }

    @Test
    fun `the tentative switch marks an event tentative`() {
        respond("-/events/get", event("a", "Stand-up"))
        respond("-/events/update", event("b", "Stand-up"))
        show("event" to "e1")
        waitFor("Stand-up")
        tentative().assertIsOff().performClick()
        rest()
        answered { bodies.containsKey("-/events/update") }
        assertEquals(listOf("TENTATIVE"), statuses())
    }

    @Test
    fun `a tentative event opens with its switch on, and turning it off saves no status`() {
        respond("-/events/get", event("a", "Stand-up", status = "TENTATIVE"))
        respond("-/events/update", event("b", "Stand-up"))
        show("event" to "e1")
        waitFor("Stand-up")
        tentative().assertIsOn().performClick()
        rest()
        answered { bodies.containsKey("-/events/update") }
        assertEquals(emptyList<String>(), statuses())
    }

    @Test
    fun `a copy is made as soon as it is read, tentative as its original, and the editor goes on as it`() {
        respond("-/events/get", event("a", "Stand-up", status = "TENTATIVE"))
        respond("-/events/create", event("c", "Stand-up"))
        show("source" to "event", "copy" to "e1")
        answered { followed != null }
        assertEquals(Said.COPIED, followed!!.third)
        assertEquals(listOf("TENTATIVE"), statuses("-/events/create"))
    }

    @Test
    fun `Copy on an open event makes the copy at once and goes on as it`() {
        respond("-/events/get", event("a", "Stand-up"))
        respond("-/events/create", event("c", "Stand-up"))
        show("event" to "e1")
        waitFor("Stand-up")
        rule.onNodeWithContentDescription(string(R.string.calendars_event_copy)).performClick()
        answered { followed != null }
        assertEquals(Said.COPIED, followed!!.third)
        assertTrue(bodies["-/events/create"]!!.contains("Stand-up"))
        assertFalse(asked.contains("-/events/update"))
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
        val model = show("event" to "e1")
        waitFor("Stand-up")
        val copy = rule.onNodeWithContentDescription(string(R.string.calendars_event_copy))
        val delete = rule.onNodeWithContentDescription(string(R.string.calendars_delete))
        copy.assertIsEnabled()
        delete.assertIsEnabled()
        title().performTextReplacement("Stand-up, moved")
        model.save()
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
