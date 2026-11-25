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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import android.content.Intent
import androidx.core.content.IntentCompat
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
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.UserPreferences
import org.mochios.android.share.EXTRA_ADDRESS
import org.mochios.android.share.EXTRA_WORDS
import org.mochios.calendars.ui.editor.EditorUiState
import org.mochios.calendars.ui.editor.Ending
import org.mochios.calendars.ui.editor.Frequency
import org.mochios.calendars.ui.editor.Recurrence
import org.mochios.calendars.ui.editor.eventShare
import org.mochios.calendars.ui.editor.eventWhen
import org.mochios.calendars.ui.editor.recurrence
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.di.Viewer
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.ui.editor.EventEditScreen
import org.mochios.calendars.ui.editor.EventEditViewModel
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

    /** The editor the screen went on as: event and moment. */
    private var followed: Pair<String, Long>? = null
    private var created = false
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
            context,
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

    private fun show(vararg route: Pair<String, String>, viewer: Viewer = London): EventEditViewModel {
        val model = EventEditViewModel(context, repository, viewer, SavedStateHandle(mapOf(*route)))
        rule.setContent {
            EventEditScreen(
                onBack = { left = true },
                onOpen = { event, moment -> followed = event to moment },
                onDeleted = { deleted = true },
                onCreated = { created = true },
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
    fun `a refused save keeps Reload on screen until it is answered`() {
        respond("-/events/get", event("a", "Stand-up"))
        respond("-/events/update", changed)
        val model = show("event" to "e1")
        waitFor("Stand-up")
        title().performTextReplacement("Stand-up, moved")
        model.save()
        waitFor(string(R.string.calendars_reload))
        rule.mainClock.advanceTimeBy(30_000)
        rule.onNodeWithText(string(R.string.calendars_reload)).assertExists()
    }

    @Test
    fun `after a refused save, leaving asks before the change is dropped, and then goes`() {
        respond("-/events/get", event("a", "Stand-up"))
        respond("-/events/update", changed)
        val model = show("event" to "e1")
        waitFor("Stand-up")
        title().performTextReplacement("Stand-up, moved")
        model.save()
        waitFor(string(R.string.calendars_event_changed))
        model.leave()
        waitFor(string(R.string.calendars_discard_title))
        assertFalse(left)
        rule.onNodeWithText(string(R.string.calendars_discard)).performClick()
        answered { left }
        assertEquals(1, asked.count { it == "-/events/update" })
    }

    @Test
    fun `a save leaving makes that is refused asks before the change is dropped`() {
        respond("-/events/get", event("a", "Stand-up"))
        respond("-/events/update", changed)
        val model = show("event" to "e1")
        waitFor("Stand-up")
        title().performTextReplacement("Stand-up, moved")
        model.leave()
        waitFor(string(R.string.calendars_discard_title))
        assertFalse(left)
        rule.onNodeWithText(string(R.string.calendars_discard)).performClick()
        answered { left }
        assertEquals(1, asked.count { it == "-/events/update" })
    }

    /** A daily series from the 2nd whose occurrence of the 5th, 11:00 London, was moved to the 6th at 15:00. */
    private fun late() = ok(
        """{"event": {"id": "e1", "calendar": "c1", "etag": "a", "recurring": true, "components": [
            {"name": "VEVENT", "properties": [
                {"name": "UID", "params": {}, "value": "uid-1"},
                {"name": "SUMMARY", "params": {}, "value": "Stand-up"},
                {"name": "DTSTART", "params": {"TZID": ["Europe/London"]}, "value": "20261002T110000"},
                {"name": "DTEND", "params": {"TZID": ["Europe/London"]}, "value": "20261002T120000"},
                {"name": "RRULE", "params": {}, "value": "FREQ=DAILY"}
            ], "components": []},
            {"name": "VEVENT", "properties": [
                {"name": "UID", "params": {}, "value": "uid-1"},
                {"name": "SUMMARY", "params": {}, "value": "Late"},
                {"name": "RECURRENCE-ID", "params": {"TZID": ["Europe/London"]}, "value": "20261005T110000"},
                {"name": "DTSTART", "params": {"TZID": ["Europe/London"]}, "value": "20261006T150000"},
                {"name": "DTEND", "params": {"TZID": ["Europe/London"]}, "value": "20261006T160000"}
            ], "components": []}
        ]}}""",
    )

    /**
     * A moved occurrence opens on its own override, found by the occurrence
     * it replaces, and "This event" replaces that override rather than adding
     * a second; it goes on being found after the save. A core that does not
     * name the occurrence gives the override's own start, which finds it too.
     */
    @Test
    fun `a moved occurrence opens on its own override, and this event replaces it`() =
        opensMoved("occurrence" to "1791194400")

    @Test
    fun `a moved occurrence a core lists only where it now falls opens on its override too`() =
        opensMoved("occurrence" to "1791295200", "moved" to "1")

    private fun opensMoved(vararg route: Pair<String, String>) {
        respond("-/events/get", late())
        respond("-/events/update", late())
        val model = show("event" to "e1", *route)
        waitFor("Late")
        assertEquals(1_791_194_400L, model.uiState.value.moment)
        title().performTextReplacement("Late, again")
        model.save()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(string(R.string.calendars_scope_save)).fetchSemanticsNodes().isNotEmpty()
        }
        model.scope(org.mochios.calendars.ui.editor.Scope.ONE)
        answered { asked.contains("-/events/update") && !model.uiState.value.isSaving }
        val body = bodies["-/events/update"]!!
        assertEquals(body, 1, Regex("RECURRENCE-ID").findAll(body).count())
        assertTrue(body, body.contains("Late, again"))
        assertTrue(body, body.contains("20261005T110000"))
        // Read again, the form is still the override's.
        model.reload()
        answered { model.uiState.value.title != "Late, again" }
        assertEquals("Late", model.uiState.value.title)
    }

    @Test
    fun `after a refused save undone by hand, leaving goes at once`() {
        respond("-/events/get", event("a", "Stand-up"))
        respond("-/events/update", changed)
        val model = show("event" to "e1")
        waitFor("Stand-up")
        title().performTextReplacement("Stand-up, moved")
        model.save()
        waitFor(string(R.string.calendars_event_changed))
        title().performTextReplacement("Stand-up")
        model.leave()
        answered { left }
        assertEquals(0, rule.onAllNodesWithText(string(R.string.calendars_discard_title)).fetchSemanticsNodes().size)
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
    fun `Create makes the new event, saving nothing before it, and the editor goes back to the calendar`() {
        respond("-/events/create", event("a", "Lunch"))
        show()
        rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        title().performTextReplacement("Lunch")
        rest()
        assertFalse(asked.contains("-/events/create"))
        rule.onNodeWithText(string(R.string.calendars_create_submit)).performScrollTo().performClick()
        answered { created }
        assertEquals(null, followed)
        assertTrue(bodies["-/events/create"]!!.contains("Lunch"))
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
        assertEquals("$allday $tentative", allday.height, tentative.height, 0.5f)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h800dp")
    fun `a row too narrow for both switches puts tentative on a line of its own, unbroken`() {
        respond("-/events/get", event("a", "Stand-up"))
        show("event" to "e1")
        waitFor("Stand-up")
        val allday = rule.onNodeWithText(string(R.string.calendars_event_allday)).performScrollTo()
            .fetchSemanticsNode().boundsInRoot
        val tentative = rule.onNodeWithText(string(R.string.calendars_status_tentative)).performScrollTo()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("$allday $tentative", tentative.top >= allday.bottom)
        assertEquals("$allday $tentative", allday.height, tentative.height, 0.5f)
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
    fun `a copy opens as a new event, made only by Create, tentative as its original`() {
        respond("-/events/get", event("a", "Stand-up", status = "TENTATIVE"))
        respond("-/events/create", event("c", "Stand-up"))
        show("event" to "e1")
        waitFor("Stand-up")
        rule.onNodeWithContentDescription(string(R.string.calendars_event_copy)).performClick()
        waitFor(string(R.string.calendars_create_submit))
        rule.onNodeWithText(string(R.string.calendars_event_new)).assertExists()
        rest()
        assertFalse(asked.contains("-/events/create"))
        rule.onNodeWithText(string(R.string.calendars_create_submit)).performScrollTo().performClick()
        answered { created }
        assertEquals(null, followed)
        assertEquals(listOf("TENTATIVE"), statuses("-/events/create"))
    }

    @Test
    fun `Copy on an open event opens the copy as a new event, made only by Create`() {
        respond("-/events/get", event("a", "Stand-up"))
        respond("-/events/create", event("c", "Stand-up"))
        show("event" to "e1")
        waitFor("Stand-up")
        rule.onNodeWithContentDescription(string(R.string.calendars_event_copy)).performClick()
        waitFor(string(R.string.calendars_create_submit))
        rule.onNodeWithText(string(R.string.calendars_event_new)).assertExists()
        rest()
        assertFalse(asked.contains("-/events/create"))
        assertFalse(asked.contains("-/events/update"))
        rule.onNodeWithText(string(R.string.calendars_create_submit)).performScrollTo().performClick()
        answered { created }
        assertEquals(null, followed)
        assertTrue(bodies["-/events/create"]!!.contains("Stand-up"))
        assertFalse(asked.contains("-/events/update"))
    }

    @Test
    fun `a copy carries a change not yet saved, and leaving asks before it is lost`() {
        respond("-/events/get", event("a", "Stand-up"))
        val model = show("event" to "e1")
        waitFor("Stand-up")
        title().performTextReplacement("Stand-up, moved")
        rule.onNodeWithContentDescription(string(R.string.calendars_event_copy)).performClick()
        waitFor(string(R.string.calendars_create_submit))
        assertEquals("Stand-up, moved", model.uiState.value.title)
        assertTrue(model.dirty(model.uiState.value))
        rest()
        assertFalse(asked.contains("-/events/update"))
        rule.onNodeWithContentDescription(string(MochiR.string.common_back)).performClick()
        waitFor(string(R.string.calendars_discard_title))
        assertFalse(left)
    }

    @Test
    fun `an untouched copy is left without asking, making nothing`() {
        respond("-/events/get", event("a", "Stand-up"))
        show("event" to "e1")
        waitFor("Stand-up")
        rule.onNodeWithContentDescription(string(R.string.calendars_event_copy)).performClick()
        waitFor(string(R.string.calendars_create_submit))
        rule.onNodeWithContentDescription(string(MochiR.string.common_back)).performClick()
        rule.waitForIdle()
        assertTrue(left)
        assertEquals(0, rule.onAllNodesWithText(string(R.string.calendars_discard_title)).fetchSemanticsNodes().size)
        assertFalse(asked.contains("-/events/create"))
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
    fun `an all-day event turned off comes back at the working hours and length the preferences give, on its days`() {
        respond("-/preferences/get", ok("""{"preferences": {"hours": {"start": 9, "finish": 17}, "duration": 30}}"""))
        respond(
            "-/events/get",
            ok(
                """{"event": {"id": "e1", "calendar": "c1", "etag": "a", "recurring": false, "components": [
                    {"name": "VEVENT", "properties": [
                        {"name": "UID", "params": {}, "value": "uid-1"},
                        {"name": "SUMMARY", "params": {}, "value": "Trip"},
                        {"name": "DTSTART", "params": {"VALUE": ["DATE"]}, "value": "20261006"},
                        {"name": "DTEND", "params": {"VALUE": ["DATE"]}, "value": "20261009"}
                    ], "components": []}
                ]}}""",
            ),
        )
        val model = EventEditViewModel(context, repository, London, SavedStateHandle(mapOf("event" to "e1")))
        val deadline = System.currentTimeMillis() + 5_000
        while (model.uiState.value.isLoading) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
        assertTrue(model.uiState.value.allday)
        model.allday(false)
        val london = java.time.ZoneId.of("Europe/London")
        val tuesday = java.time.LocalDate.of(2026, 10, 6)
        assertEquals(tuesday.atTime(9, 0).atZone(london).toEpochSecond(), model.uiState.value.start)
        assertEquals(tuesday.plusDays(2).atTime(9, 30).atZone(london).toEpochSecond(), model.uiState.value.finish)
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

    /**
     * What the system share sheet opened for, once it opens; the launch of
     * the test's own host activity is passed over.
     */
    private fun sheet(): Intent {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            val started = shadowOf(context).nextStartedActivity
            if (started == null) {
                Thread.sleep(10)
            } else if (started.action == Intent.ACTION_CHOOSER) {
                return IntentCompat.getParcelableExtra(started, Intent.EXTRA_INTENT, Intent::class.java)!!
            }
        }
        throw AssertionError("the share sheet did not open")
    }

    @Test
    fun `an event in a calendar of the user's own is shared by its name, time, place and description, and its address`() {
        respond(
            "-/events/get",
            ok(
                """{"event": {"id": "e1", "calendar": "c1", "etag": "a", "recurring": false, "components": [
                    {"name": "VEVENT", "properties": [
                        {"name": "UID", "params": {}, "value": "uid-1"},
                        {"name": "SUMMARY", "params": {}, "value": "Stand-up"},
                        {"name": "LOCATION", "params": {}, "value": "Room 4"},
                        {"name": "DESCRIPTION", "params": {}, "value": "Bring notes."},
                        {"name": "URL", "params": {}, "value": "https://meet.example/s"},
                        {"name": "DTSTART", "params": {}, "value": "20261002T100000Z"},
                        {"name": "DTEND", "params": {}, "value": "20261002T110000Z"}
                    ], "components": []}
                ]}}""",
            ),
        )
        val model = show("event" to "e1")
        waitFor("Stand-up")
        rule.onNodeWithContentDescription(string(MochiR.string.share_title)).performClick()
        val send = sheet()
        val time = eventWhen(model.uiState.value, London.zone(), Format(UserPreferences()))
        // Each field a paragraph of its own: a feed's post joins lines a single newline parts.
        val words = "Stand-up\n\n$time\n\nRoom 4\n\nBring notes."
        assertEquals("$words\n\nhttps://meet.example/s", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Stand-up", send.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(words, send.getStringExtra(EXTRA_WORDS))
        assertEquals("https://meet.example/s", send.getStringExtra(EXTRA_ADDRESS))
    }

    /** A weekly stand-up on Fridays at 10:00 in London, to 18 December. */
    private val weekly = EditorUiState(
        title = "Stand-up",
        start = java.time.ZonedDateTime.of(2026, 10, 2, 10, 0, 0, 0, java.time.ZoneId.of("Europe/London")).toEpochSecond(),
        finish = java.time.ZonedDateTime.of(2026, 10, 2, 11, 0, 0, 0, java.time.ZoneId.of("Europe/London")).toEpochSecond(),
        recurrence = Recurrence(Frequency.WEEKLY, days = setOf(5), ending = Ending.UNTIL, until = java.time.LocalDate.of(2026, 12, 18)),
    )

    private fun shared(state: EditorUiState): String =
        eventShare(state, "Europe/London", RuntimeEnvironment.getApplication().resources, java.util.Locale.UK, Format(UserPreferences())).text

    @Test
    fun `a series is shared with how it repeats, after when the occurrence shared is`() {
        val format = Format(UserPreferences())
        val time = eventWhen(weekly, "Europe/London", format)
        val until = format.formatDate(java.time.LocalDate.of(2026, 12, 18).atTime(12, 0).toEpochSecond(java.time.ZoneOffset.UTC), "UTC")
        assertEquals("Stand-up\n\n$time\n\nEvery week, on Friday, until $until", shared(weekly))
        assertEquals("Stand-up\n\n$time", shared(weekly.copy(recurrence = Recurrence())))
    }

    @Test
    fun `a series whose rule no summary can say is shared as one that repeats`() {
        val time = eventWhen(weekly, "Europe/London", Format(UserPreferences()))
        val unsaid = weekly.copy(recurrence = recurrence("FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1", "Europe/London"))
        assertEquals("Stand-up\n\n$time\n\n" + string(R.string.calendars_event_recurring), shared(unsaid))
    }

    @Test
    fun `an event is said by its day, or its days, and for a timed one its clock and zone`() {
        val format = Format(UserPreferences())
        val start = java.time.ZonedDateTime.of(2026, 10, 2, 10, 0, 0, 0, java.time.ZoneId.of("Europe/London")).toEpochSecond()
        val timed = EditorUiState(start = start, finish = start + 3_600)
        assertEquals(format.formatTimeRange(start, start + 3_600, "Europe/London", "Europe/London") + " (Europe/London)", eventWhen(timed, "Europe/London", format))
        // A flight across zones names both.
        val flight = timed.copy(zone = org.mochios.calendars.model.Zone(start = "Europe/London", finish = "America/New_York"))
        assertTrue(eventWhen(flight, "Europe/Paris", format).endsWith("(" + format.formatRange("Europe/London", "America/New York") + ")"))
        val day = java.time.LocalDate.of(2026, 10, 2)
        val midnight = day.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()
        val one = EditorUiState(allday = true, start = midnight, finish = midnight + 86_400)
        assertEquals(format.formatLongDate(midnight, "UTC"), eventWhen(one, "Europe/London", format))
        val three = one.copy(finish = midnight + 3 * 86_400)
        assertEquals(format.formatDayRange(day, day.plusDays(2)), eventWhen(three, "Europe/London", format))
    }

    @Test
    fun `an event in a subscribed calendar is not shared`() {
        respond("-/calendars", ok("""{"calendars": [{"id": "c1", "name": "Holidays", "kind": "subscription", "readonly": true}]}"""))
        respond("-/events/get", event("a", "Stand-up"))
        show("event" to "e1")
        waitFor("Stand-up")
        rule.onNodeWithContentDescription(string(R.string.calendars_event_copy)).assertExists()
        assertEquals(0, rule.onAllNodesWithContentDescription(string(MochiR.string.share_title)).fetchSemanticsNodes().size)
    }

    // ---- a series typed or written at a time the clocks skip or read twice ----
    //
    // No instant reads 02:30 on New York's 10 March 2024, when its clocks go
    // from 02:00 to 03:00: the server places a series written at 02:30 that
    // day at 01:30 in winter time and repeats it at 02:30 afterwards, so the
    // series is written at 02:30, as typed. London's 01:30 on 27 October
    // 2024 comes round twice, and the server takes the second, in winter time.

    private val york = "America/New_York"

    /** An instant, written in UTC. */
    private fun utc(text: String) = java.time.Instant.parse(text).epochSecond

    /** The [name] property of the first component [body] sends: its value, then its `TZID`. */
    private fun sent(body: String, name: String): List<String?> {
        val component = com.google.gson.JsonParser.parseString(body).asJsonObject.getAsJsonArray("components")[0].asJsonObject
        val found = component.getAsJsonArray("properties").map { it.asJsonObject }.first { it.get("name").asString == name }
        return listOf(found.get("value").asString, found.getAsJsonObject("params")?.getAsJsonArray("TZID")?.get(0)?.asString)
    }

    /**
     * What a new weekly series typed at [typed] in [zone] writes: its
     * `DTSTART` and `TZID`, the instant the form holds, and where the server
     * places the written reading a week on.
     */
    private fun typed(zone: String, typed: java.time.LocalDateTime): List<Any?> {
        respond("-/events/create", event("a", "Night shift"))
        val model = show()
        rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        title().performTextReplacement("Night shift")
        model.zone(org.mochios.calendars.model.Zone(zone, zone))
        model.start(typed)
        model.repeat(Frequency.WEEKLY)
        val held = model.uiState.value.start
        rule.onNodeWithText(string(R.string.calendars_create_submit)).performScrollTo().performClick()
        answered { created }
        val (value, named) = sent(bodies["-/events/create"]!!, "DTSTART")
        val week = java.time.LocalDateTime.parse(value!!, java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).plusWeeks(1)
        return listOf(value, named, held, org.mochios.android.sync.CalendarsMapping.placed(week, java.time.ZoneId.of(zone)))
    }

    @Test
    fun `a weekly series typed at a time New York's clocks skip is written as typed`() {
        assertEquals(
            "THE POINT: written at 02:30, held at 01:30 in winter time, and at 02:30 in summer time a week on",
            listOf<Any?>("20240310T023000", york, utc("2024-03-10T06:30:00Z"), utc("2024-03-17T06:30:00Z")),
            typed(york, java.time.LocalDateTime.of(2024, 3, 10, 2, 30)),
        )
    }

    @Test
    fun `a weekly series typed at a time London's clocks read twice is written as typed and held in winter time`() {
        assertEquals(
            "THE POINT: written at 01:30, held at 01:30 in winter time, and at 01:30 a week on",
            listOf<Any?>("20241027T013000", "Europe/London", utc("2024-10-27T01:30:00Z"), utc("2024-11-03T01:30:00Z")),
            typed("Europe/London", java.time.LocalDateTime.of(2024, 10, 27, 1, 30)),
        )
    }

    /** A weekly New York series written at 02:30 on 10 March 2024, an hour long. */
    private fun skipped() = ok(
        """{"event": {"id": "e1", "calendar": "c1", "etag": "a", "recurring": true, "components": [
            {"name": "VEVENT", "properties": [
                {"name": "UID", "params": {}, "value": "uid-1"},
                {"name": "SUMMARY", "params": {}, "value": "Night shift"},
                {"name": "DTSTART", "params": {"TZID": ["America/New_York"]}, "value": "20240310T023000"},
                {"name": "DTEND", "params": {"TZID": ["America/New_York"]}, "value": "20240310T033000"},
                {"name": "RRULE", "params": {}, "value": "FREQ=WEEKLY"}
            ], "components": []}
        ]}}""",
    )

    /** What saving only a new title to all events writes, from the occurrence at [occurrence]. */
    private fun renamed(occurrence: Long): List<String?> {
        respond("-/events/get", skipped())
        respond("-/events/update", event("b", "Night shift"))
        val model = show("event" to "e1", "occurrence" to occurrence.toString())
        waitFor("Night shift")
        title().performTextReplacement("Night shift, renamed")
        rest()
        rule.onAllNodes(hasSetTextAction())[1].performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(string(R.string.calendars_scope_save)).fetchSemanticsNodes().isNotEmpty()
        }
        model.scope(org.mochios.calendars.ui.editor.Scope.ALL)
        answered { asked.contains("-/events/update") }
        val body = bodies["-/events/update"]!!
        return listOf(sent(body, "SUMMARY")[0]) + sent(body, "DTSTART") + sent(body, "DTEND")
    }

    /** The series as [renamed] should write it: renamed, its ends as they were. */
    private val renaming = listOf("Night shift, renamed", "20240310T023000", york, "20240310T033000", york)

    @Test
    fun `a series written at a time the clocks skip keeps it when only its title is saved from its first occurrence`() {
        // Which the server lists at 01:30 in winter time.
        assertEquals("THE POINT: still written at 02:30, not 01:30", renaming, renamed(utc("2024-03-10T06:30:00Z")))
    }

    @Test
    fun `a series written at a time the clocks skip keeps it when only its title is saved from a later occurrence`() {
        // The 17th's, at 02:30 in summer time.
        assertEquals("THE POINT: still written at 02:30, not 01:30", renaming, renamed(utc("2024-03-17T06:30:00Z")))
    }

    // ---- a series moved with the dates it carries ----
    //
    // A daily series moved an hour on takes its listed dates and overrides
    // along by the clock. One written at 02:30 on New York's 10 March 2024
    // names the occurrence the server places at 01:30 in winter time, the
    // series' own that day, and goes on naming it once moved.

    /** A property as the server sends it, in [zone] where given. */
    private fun json(name: String, value: String, zone: String? = null): String {
        val params = zone?.let { """"TZID": ["$it"]""" }.orEmpty()
        return """{"name": "$name", "params": {$params}, "value": "$value"}"""
    }

    /** A daily series in [zone] from [start] to [end], with [extra] properties. */
    private fun daily(zone: String, start: String, end: String, vararg extra: String): String {
        val properties = listOf(
            json("UID", "uid-1"),
            json("SUMMARY", "Night shift"),
            json("DTSTART", start, zone),
            json("DTEND", end, zone),
            json("RRULE", "FREQ=DAILY"),
        ) + extra
        return """{"name": "VEVENT", "properties": [${properties.joinToString(", ")}], "components": []}"""
    }

    /** The override of the occurrence [named], moved to [start] until [end]. */
    private fun override(zone: String, named: String, start: String, end: String): String {
        val properties = listOf(
            json("UID", "uid-1"),
            json("SUMMARY", "Night shift, late"),
            json("RECURRENCE-ID", named, zone),
            json("DTSTART", start, zone),
            json("DTEND", end, zone),
        )
        return """{"name": "VEVENT", "properties": [${properties.joinToString(", ")}], "components": []}"""
    }

    /** The start set to [value] on its clock, as the start field sets it. */
    private fun starting(value: java.time.LocalDateTime): (EventEditViewModel) -> Unit = { it.start(value) }

    /** Both ends' zone set to [zone], as the zone field sets it. */
    private fun zoning(zone: String): (EventEditViewModel) -> Unit = { it.zone(org.mochios.calendars.model.Zone(zone, zone)) }

    /** Only the title changed, as the title field changes it. */
    private val retitling: (EventEditViewModel) -> Unit = { it.title("Night shift, late") }

    /**
     * What saving the stored series [components] for [scope], opened on the
     * occurrence at [occurrence] by a user in [viewer]'s zone, writes once
     * [change] is made: the new series' start and listed dates, and its
     * override's `RECURRENCE-ID`, `DTSTART` and `DTEND`, each as its value
     * and `TZID`. [reading] is the reading the listing gave the occurrence,
     * which the route carries.
     */
    private fun saved(
        occurrence: Long,
        change: (EventEditViewModel) -> Unit,
        scope: org.mochios.calendars.ui.editor.Scope,
        vararg components: String,
        viewer: Viewer = London,
        reading: String? = null,
    ): Map<String, List<List<String?>>> {
        respond(
            "-/events/get",
            ok("""{"event": {"id": "e1", "calendar": "c1", "etag": "a", "recurring": true, "components": [${components.joinToString(", ")}]}}"""),
        )
        respond("-/events/update", event("b", "Night shift"))
        respond(
            "-/events/split",
            ok(
                """{"event": {"id": "e1", "calendar": "c1", "etag": "b", "recurring": true, "components": []},
                    "following": {"id": "e7", "calendar": "c1", "etag": "w1", "recurring": true, "components": []}}""",
            ),
        )
        val route = listOfNotNull("event" to "e1", "occurrence" to occurrence.toString(), reading?.let { "reading" to it })
        val model = show(*route.toTypedArray(), viewer = viewer)
        waitFor("Night shift")
        change(model)
        model.save()
        rule.waitUntil(5_000) { model.uiState.value.prompt != null }
        model.scope(scope)
        val path = if (scope == org.mochios.calendars.ui.editor.Scope.ALL) "-/events/update" else "-/events/split"
        answered { asked.contains(path) }
        val body = com.google.gson.JsonParser.parseString(bodies[path]!!).asJsonObject
        val written = body.getAsJsonArray(if (scope == org.mochios.calendars.ui.editor.Scope.ALL) "components" else "following")
            .map { it.asJsonObject.getAsJsonArray("properties").map { property -> property.asJsonObject } }
        val head = written.first { properties -> properties.none { it.get("name").asString == "RECURRENCE-ID" } }
        val late = written.firstOrNull { properties -> properties.any { it.get("name").asString == "RECURRENCE-ID" } }.orEmpty()
        fun all(properties: List<com.google.gson.JsonObject>, name: String) = properties
            .filter { it.get("name").asString == name }
            .flatMap { property ->
                val zone = property.getAsJsonObject("params")?.getAsJsonArray("TZID")?.get(0)?.asString
                property.get("value").asString.split(",").map { listOf(it, zone) }
            }
        return mapOf(
            "start" to all(head, "DTSTART"),
            "excluded" to all(head, "EXDATE"),
            "added" to all(head, "RDATE"),
            "occurrence" to all(late, "RECURRENCE-ID"),
            "late" to all(late, "DTSTART") + all(late, "DTEND"),
        )
    }

    /**
     * Where the 10th's 02:30 of a daily series at 09:00 from 1 March that
     * lists it besides opens, which the server lists at 01:30 in winter
     * time, the route carrying the reading the listing gave it as [given];
     * and the series' start once it is moved to 03:30 for all events.
     */
    private fun listing(given: String?): List<Any?> {
        var opened: java.time.LocalDateTime? = null
        val written = saved(
            utc("2024-03-10T06:30:00Z"),
            { model ->
                opened = model.uiState.value.let { state ->
                    state.reading.start ?: java.time.Instant.ofEpochSecond(state.start).atZone(java.time.ZoneId.of(york)).toLocalDateTime()
                }
                model.start(java.time.LocalDateTime.of(2024, 3, 10, 3, 30))
            },
            org.mochios.calendars.ui.editor.Scope.ALL,
            daily(york, "20240301T090000", "20240301T100000", json("RDATE", "20240310T023000", york)),
            reading = given,
        )
        return listOf(opened, written["start"])
    }

    @Test
    fun `a date listed at another time the clocks skip opens at the reading the listing gives it, and moves the series from there`() {
        // The series' own 09:00 does not find the listed date's reading.
        assertEquals(
            "THE POINT: opened at 02:30, and the series an hour on from it",
            listOf(java.time.LocalDateTime.of(2024, 3, 10, 2, 30), listOf(listOf("20240301T100000", york))),
            listing("20240310T023000"),
        )
    }

    @Test
    fun `a date listed at another time the clocks skip opens at its instant's reading without one, as from a core that predates it`() {
        assertEquals(
            "opened at 01:30, and the series two hours on from it",
            listOf(java.time.LocalDateTime.of(2024, 3, 10, 1, 30), listOf(listOf("20240301T110000", york))),
            listing(null),
        )
    }

    /** Daily at 02:30 from 1 March: the 10th's 02:30 excluded, and 02:45 added and moved to 05:00. */
    private val skipping = arrayOf(
        daily(york, "20240301T023000", "20240301T033000", json("EXDATE", "20240310T023000", york), json("RDATE", "20240310T024500", york)),
        override(york, "20240310T024500", "20240310T050000", "20240310T060000"),
    )

    /** What moving [skipping] to 03:30 writes, the series starting at [start]: each date an hour on its clock. */
    private fun carrying(start: String) = mapOf(
        "start" to listOf(listOf(start, york)),
        "excluded" to listOf(listOf("20240310T033000", york)),
        "added" to listOf(listOf("20240310T034500", york)),
        "occurrence" to listOf(listOf("20240310T034500", york)),
        "late" to listOf(listOf("20240310T060000", york), listOf("20240310T070000", york)),
    )

    @Test
    fun `a series moved by all events carries dates written at a time the clocks skip by their readings`() {
        assertEquals(
            "THE POINT: each date an hour on its clock, not rewritten off its occurrence",
            carrying("20240301T033000"),
            saved(utc("2024-03-01T07:30:00Z"), starting(java.time.LocalDateTime.of(2024, 3, 1, 3, 30)), org.mochios.calendars.ui.editor.Scope.ALL, *skipping),
        )
    }

    @Test
    fun `a series moved by this and following carries dates written at a time the clocks skip by their readings`() {
        assertEquals(
            "THE POINT: each date an hour on its clock, not rewritten off its occurrence",
            carrying("20240305T033000"),
            saved(utc("2024-03-05T07:30:00Z"), starting(java.time.LocalDateTime.of(2024, 3, 5, 3, 30)), org.mochios.calendars.ui.editor.Scope.FOLLOWING, *skipping),
        )
    }

    @Test
    fun `a series moved keeps an override of an occurrence the clocks skip on that occurrence`() {
        // The series' own 02:30 on the 10th, moved to 05:00.
        assertEquals(
            "THE POINT: the override names the 10th's occurrence at 03:30, where it now is",
            mapOf(
                "start" to listOf(listOf("20240301T033000", york)),
                "excluded" to emptyList(),
                "added" to emptyList(),
                "occurrence" to listOf(listOf("20240310T033000", york)),
                "late" to listOf(listOf("20240310T060000", york), listOf("20240310T070000", york)),
            ),
            saved(
                utc("2024-03-01T07:30:00Z"),
                starting(java.time.LocalDateTime.of(2024, 3, 1, 3, 30)),
                org.mochios.calendars.ui.editor.Scope.ALL,
                daily(york, "20240301T023000", "20240301T033000"),
                override(york, "20240310T023000", "20240310T050000", "20240310T060000"),
            ),
        )
    }

    /** Daily at 02:30 from 1 March, with the 12th's excluded. */
    private val nightly = daily(york, "20240301T023000", "20240301T033000", json("EXDATE", "20240312T023000", york))

    /**
     * What a cut at the 10th's occurrence of [nightly], which the server
     * lists at 01:30 in winter time and the editor opens at the series'
     * 02:30, writes once the start is set to [hour]:[minute]: the new
     * series' start and the 12th's exclusion.
     */
    private fun cut(hour: Int, minute: Int): Map<String, List<List<String?>>> = saved(
        utc("2024-03-10T06:30:00Z"),
        starting(java.time.LocalDateTime.of(2024, 3, 10, hour, minute)),
        org.mochios.calendars.ui.editor.Scope.FOLLOWING,
        nightly,
    )

    /** The series from the 10th and its exclusion on the 12th, as [cut] should write them. */
    private fun cutting(start: String, excluded: String) = mapOf(
        "start" to listOf(listOf(start, york)),
        "excluded" to listOf(listOf(excluded, york)),
        "added" to emptyList(),
        "occurrence" to emptyList(),
        "late" to emptyList(),
    )

    @Test
    fun `a cut at an occurrence the clocks skip moves the dates after it by as far as its time of day moved`() {
        // Moved to 03:30: the series' 02:30 goes an hour on, and the 12th's exclusion with it.
        assertEquals(
            "THE POINT: the 12th's exclusion an hour on, as the series, not two",
            cutting("20240310T033000", "20240312T033000"),
            cut(3, 30),
        )
    }

    @Test
    fun `a cut at an occurrence the clocks skip moved onto another reading they skip moves the dates as far`() {
        // Moved to 02:45, a reading the clocks skip as well: a quarter of an
        // hour on, and the 12th's exclusion with it.
        assertEquals(
            "THE POINT: the 12th's exclusion a quarter of an hour on, as the series",
            cutting("20240310T024500", "20240312T024500"),
            cut(2, 45),
        )
    }

    /**
     * What a title-only cut at the occurrence at [occurrence] of a daily
     * series in [zone] from [first] to [end], excluding [exclusion], writes:
     * the series from there and its exclusion, both as the editor opened the
     * occurrence.
     */
    private fun opened(zone: String, first: String, end: String, exclusion: String, occurrence: Long) = saved(
        occurrence,
        retitling,
        org.mochios.calendars.ui.editor.Scope.FOLLOWING,
        daily(zone, first, end, json("EXDATE", exclusion, zone)),
    )

    @Test
    fun `an occurrence where the clocks skip midnight opens and is cut at its reading, on the day after its instant's`() {
        // Daily at 00:30 in Havana, whose clocks went from midnight to 01:00
        // on 10 March 2024: the server places the 10th's occurrence at 23:30
        // on the 9th, and the editor opens it at 00:30 on the 10th.
        val havana = "America/Havana"
        assertEquals(
            "THE POINT: the series goes on from 00:30 on the 10th, not 23:30 on the 9th",
            mapOf(
                "start" to listOf(listOf("20240310T003000", havana)),
                "excluded" to listOf(listOf("20240312T003000", havana)),
                "added" to emptyList(),
                "occurrence" to emptyList(),
                "late" to emptyList(),
            ),
            opened(havana, "20240301T003000", "20240301T013000", "20240312T003000", utc("2024-03-10T04:30:00Z")),
        )
    }

    @Test
    fun `an occurrence the clocks skip before midnight opens and is cut at its reading, on the day before its instant's`() {
        // Daily at 23:30 in Dhaka, whose clocks went from 23:00 to midnight
        // on 19 June 2009: the server places the 19th's occurrence at 00:30
        // on the 20th, and the editor opens it at 23:30 on the 19th.
        val dhaka = "Asia/Dhaka"
        assertEquals(
            "THE POINT: the series goes on from 23:30 on the 19th, not 00:30 on the 20th",
            mapOf(
                "start" to listOf(listOf("20090619T233000", dhaka)),
                "excluded" to listOf(listOf("20090621T233000", dhaka)),
                "added" to emptyList(),
                "occurrence" to emptyList(),
                "late" to emptyList(),
            ),
            opened(dhaka, "20090615T233000", "20090616T003000", "20090621T233000", utc("2009-06-19T17:30:00Z")),
        )
    }

    // Kiritimati's clocks went from 23:59 on 30 December 1994 to midnight on
    // 1 January 1995, and Apia's from 23:59 on 29 December 2011 to midnight
    // on the 31st: each skipped a whole day, so 10:00 that day and 10:00 the
    // next name one instant, which the server lists the skipped day's
    // occurrence at. Which day it is the series' rule says.

    /**
     * Where a title-only cut at the occurrence listed at [occurrence] of a
     * series at 10:00 in [zone] from [first] by [rule] starts the series
     * from there: the occurrence as the editor opened it.
     */
    private fun wholly(zone: String, first: String, rule: String, occurrence: Long): List<List<String?>> {
        val properties = listOf(
            json("UID", "uid-1"),
            json("SUMMARY", "Night shift"),
            json("DTSTART", "${first}T100000", zone),
            json("DTEND", "${first}T110000", zone),
            // A rule, or a date listed as `RDATE:` and its value.
            if (rule.startsWith("RDATE:")) json("RDATE", rule.removePrefix("RDATE:"), zone) else json("RRULE", rule),
        )
        val series = """{"name": "VEVENT", "properties": [${properties.joinToString(", ")}], "components": []}"""
        return saved(occurrence, retitling, org.mochios.calendars.ui.editor.Scope.FOLLOWING, series).getValue("start")
    }

    private val kiritimati = "Pacific/Kiritimati"
    private val apia = "Pacific/Apia"

    @Test
    fun `a weekly occurrence on a whole day the clocks skip opens on the weekday its rule names`() {
        // On Tuesdays and Saturdays from Tuesday 13 December 1994: Saturday the 31st's.
        assertEquals(
            "THE POINT: Saturday the 31st, not Sunday the 1st, whose 10:00 is the same instant",
            listOf(listOf("19941231T100000", kiritimati)),
            wholly(kiritimati, "19941213", "FREQ=WEEKLY;BYDAY=TU,SA", utc("1994-12-31T20:00:00Z")),
        )
    }

    @Test
    fun `a weekly occurrence the day after a whole day the clocks skip opens on its own day`() {
        // On Sundays from 18 December 1994: Sunday the 1st's, at the instant the 31st's 10:00 names.
        assertEquals(
            "THE POINT: Sunday the 1st, the series' own weekday",
            listOf(listOf("19950101T100000", kiritimati)),
            wholly(kiritimati, "19941218", "FREQ=WEEKLY", utc("1994-12-31T20:00:00Z")),
        )
    }

    @Test
    fun `an occurrence every other weekend on a whole day the clocks skip opens on the day its weeks give`() {
        // Saturdays and Sundays of every other week from Saturday 17 December
        // 1994, its weeks beginning on Sunday: the 31st's week is one, the
        // 1st's is not.
        assertEquals(
            "THE POINT: Saturday the 31st, not Sunday the 1st, which falls in a week the series skips",
            listOf(listOf("19941231T100000", kiritimati)),
            wholly(kiritimati, "19941217", "FREQ=WEEKLY;INTERVAL=2;BYDAY=SA,SU;WKST=SU", utc("1994-12-31T20:00:00Z")),
        )
    }

    @Test
    fun `a listed date on a whole day the clocks skip opens on that date`() {
        // From Saturday 24 December 1994, with Saturday the 31st listed.
        assertEquals(
            "THE POINT: Saturday the 31st, as listed, not Sunday the 1st",
            listOf(listOf("19941231T100000", kiritimati)),
            wholly(kiritimati, "19941224", "RDATE:19941231T100000", utc("1994-12-31T20:00:00Z")),
        )
    }

    @Test
    fun `a yearly occurrence on a whole day the clocks skip opens on its date`() {
        assertEquals(
            "THE POINT: the 31st, the series' date, not the 1st",
            listOf(listOf("19941231T100000", kiritimati)),
            wholly(kiritimati, "19931231", "FREQ=YEARLY", utc("1994-12-31T20:00:00Z")),
        )
    }

    @Test
    fun `a weekly occurrence on a whole day the clocks skip opens on the first occurrence's weekday`() {
        // Fridays from 16 December 2011: Friday the 30th's.
        assertEquals(
            "THE POINT: Friday the 30th, not Saturday the 31st",
            listOf(listOf("20111230T100000", apia)),
            wholly(apia, "20111216", "FREQ=WEEKLY", utc("2011-12-30T20:00:00Z")),
        )
    }

    @Test
    fun `a monthly occurrence on a whole day the clocks skip opens on its day of the month`() {
        // The 30th of each month from 30 November 2011.
        assertEquals(
            "THE POINT: the 30th, not the 31st",
            listOf(listOf("20111230T100000", apia)),
            wholly(apia, "20111130", "FREQ=MONTHLY", utc("2011-12-30T20:00:00Z")),
        )
    }

    @Test
    fun `an occurrence every other day on a whole day the clocks skip opens on the day its interval gives`() {
        // Every other day from 28 December 2011: the 30th, not the 31st.
        assertEquals(
            "THE POINT: the 30th, two days on from the 28th, not the 31st",
            listOf(listOf("20111230T100000", apia)),
            wholly(apia, "20111228", "FREQ=DAILY;INTERVAL=2", utc("2011-12-30T20:00:00Z")),
        )
    }

    /** [nightly] as a title-only save from its 10th's occurrence writes it, the series starting at [start]. */
    private fun unmoved(start: String) = cutting(start, "20240312T023000")

    @Test
    fun `a later occurrence the clocks skip opens at the series' reading, and saving only its title for all events leaves the series there`() {
        assertEquals(
            "THE POINT: still at 02:30, not moved to where the 10th's instant reads",
            unmoved("20240301T023000"),
            saved(
                utc("2024-03-10T06:30:00Z"),
                { model ->
                    assertEquals(java.time.LocalDateTime.of(2024, 3, 10, 2, 30), model.uiState.value.reading.start)
                    retitling(model)
                },
                org.mochios.calendars.ui.editor.Scope.ALL,
                nightly,
            ),
        )
    }

    @Test
    fun `a later occurrence the clocks skip opens at the series' reading, and saving only its title from it on leaves the series there`() {
        assertEquals(
            "THE POINT: the series goes on from 02:30 on the 10th, not 01:30",
            unmoved("20240310T023000"),
            saved(utc("2024-03-10T06:30:00Z"), retitling, org.mochios.calendars.ui.editor.Scope.FOLLOWING, nightly),
        )
    }

    @Test
    fun `a later occurrence whose start and end the clocks both skip opens at the series' readings`() {
        // Daily from 02:00 to 02:45, both readings the 10th skips: its
        // occurrence opens at 02:00 to 02:45, and the series goes on so from it.
        saved(
            utc("2024-03-10T06:00:00Z"),
            retitling,
            org.mochios.calendars.ui.editor.Scope.FOLLOWING,
            daily(york, "20240301T020000", "20240301T024500"),
        )
        val following = com.google.gson.JsonParser.parseString(bodies["-/events/split"]!!).asJsonObject
            .getAsJsonArray("following")[0].asJsonObject.getAsJsonArray("properties").map { it.asJsonObject }
        assertEquals(
            "THE POINT: from 02:00 to 02:45, not to the 01:45 the end's instant reads",
            listOf("20240310T020000", "20240310T024500"),
            listOf("DTSTART", "DTEND").map { name -> following.first { it.get("name").asString == name }.get("value").asString },
        )
    }

    @Test
    fun `a later occurrence the clocks skip is copied at the series' reading`() {
        respond("-/events/get", ok("""{"event": {"id": "e1", "calendar": "c1", "etag": "a", "recurring": true, "components": [$nightly]}}"""))
        respond("-/events/create", event("c", "Night shift"))
        val model = show("event" to "e1", "occurrence" to utc("2024-03-10T06:30:00Z").toString())
        waitFor("Night shift")
        model.copy()
        rule.waitUntil(5_000) { model.uiState.value.prompt != null }
        model.scope(org.mochios.calendars.ui.editor.Scope.ONE)
        waitFor(string(R.string.calendars_create_submit))
        rule.onNodeWithText(string(R.string.calendars_create_submit)).performScrollTo().performClick()
        answered { created }
        assertEquals("THE POINT: at 02:30, not 01:30", listOf("20240310T023000", york), sent(bodies["-/events/create"]!!, "DTSTART"))
    }

    @Test
    fun `a series moves from the reading a later occurrence the clocks skip opens at`() {
        // The 10th's occurrence, opened at 02:30 and moved to 03:30: the
        // series goes an hour on, and the 12th's exclusion with it.
        assertEquals(
            "THE POINT: an hour on, from 02:30, not two from 01:30",
            cutting("20240301T033000", "20240312T033000"),
            saved(
                utc("2024-03-10T06:30:00Z"),
                starting(java.time.LocalDateTime.of(2024, 3, 10, 3, 30)),
                org.mochios.calendars.ui.editor.Scope.ALL,
                nightly,
            ),
        )
    }

    /** A viewer in New York whose week starts on Monday, on a test server. */
    private val viewing = object : Viewer {
        override fun zone(): String = york
        override fun week(): Int = 1
        override suspend fun server(): String = "https://example.org"
    }

    @Test
    fun `a floating series opens and moves by its readings in the user's zone`() {
        // Floating daily at 02:30, read in New York, the user's zone: on the
        // 10th 01:45 is added and moved to 01:50 until 04:00, and the 12th's
        // excluded. The 10th's occurrence opens at 02:30, and moved to 03:30
        // each date goes an hour on its clock with the series, floating
        // still, those landing on readings the 10th skips written at those
        // readings.
        val properties = listOf(
            json("UID", "uid-1"),
            json("SUMMARY", "Night shift"),
            json("DTSTART", "20240301T023000"),
            json("DTEND", "20240301T033000"),
            json("RRULE", "FREQ=DAILY"),
            json("EXDATE", "20240312T023000"),
            json("RDATE", "20240310T014500"),
        )
        val late = listOf(
            json("UID", "uid-1"),
            json("SUMMARY", "Night shift, late"),
            json("RECURRENCE-ID", "20240310T014500"),
            json("DTSTART", "20240310T015000"),
            json("DTEND", "20240310T040000"),
        )
        assertEquals(
            "THE POINT: each date an hour on its clock, floating, from the 02:30 the 10th opens at",
            mapOf(
                "start" to listOf(listOf<String?>("20240301T033000", null)),
                "excluded" to listOf(listOf<String?>("20240312T033000", null)),
                "added" to listOf(listOf<String?>("20240310T024500", null)),
                "occurrence" to listOf(listOf<String?>("20240310T024500", null)),
                "late" to listOf(listOf<String?>("20240310T025000", null), listOf<String?>("20240310T050000", null)),
            ),
            saved(
                utc("2024-03-10T06:30:00Z"),
                { model ->
                    assertEquals(java.time.LocalDateTime.of(2024, 3, 10, 2, 30), model.uiState.value.reading.start)
                    model.start(java.time.LocalDateTime.of(2024, 3, 10, 3, 30))
                },
                org.mochios.calendars.ui.editor.Scope.ALL,
                """{"name": "VEVENT", "properties": [${properties.joinToString(", ")}], "components": []}""",
                """{"name": "VEVENT", "properties": [${late.joinToString(", ")}], "components": []}""",
                viewer = viewing,
            ),
        )
    }

    @Test
    fun `a series at half past one on the day the clocks skip an hour typed to half past two there takes its dates along`() {
        // Daily from 01:30 on the 10th, with the 12th's excluded. 02:30 that
        // day names the instant 01:30 does, but the series is at 02:30 from
        // the next day on, and its exclusion with it.
        assertEquals(
            "THE POINT: the 12th's exclusion at 02:30, as the series now is",
            mapOf(
                "start" to listOf(listOf("20240310T023000", york)),
                "excluded" to listOf(listOf("20240312T023000", york)),
                "added" to emptyList(),
                "occurrence" to emptyList(),
                "late" to emptyList(),
            ),
            saved(
                utc("2024-03-10T06:30:00Z"),
                starting(java.time.LocalDateTime.of(2024, 3, 10, 2, 30)),
                org.mochios.calendars.ui.editor.Scope.ALL,
                daily(york, "20240310T013000", "20240310T023000", json("EXDATE", "20240312T013000", york)),
            ),
        )
    }

    @Test
    fun `a series moved writes dates moved onto a time the clocks skip at that reading, as the series is`() {
        // Daily at 01:30 from 1 March: the 10th's 01:30 excluded, and 01:45
        // added and moved to 01:50 until 04:00. Moved to 02:30, each date
        // lands on a reading the 10th skips, which names the instant its
        // occurrence is placed at and moves with the series from there next
        // time.
        assertEquals(
            "THE POINT: written at 02:30, 02:45 and 02:50, as the series, not as the instants read",
            mapOf(
                "start" to listOf(listOf("20240301T023000", york)),
                "excluded" to listOf(listOf("20240310T023000", york)),
                "added" to listOf(listOf("20240310T024500", york)),
                "occurrence" to listOf(listOf("20240310T024500", york)),
                "late" to listOf(listOf("20240310T025000", york), listOf("20240310T050000", york)),
            ),
            saved(
                utc("2024-03-01T06:30:00Z"),
                starting(java.time.LocalDateTime.of(2024, 3, 1, 2, 30)),
                org.mochios.calendars.ui.editor.Scope.ALL,
                daily(york, "20240301T013000", "20240301T023000", json("EXDATE", "20240310T013000", york), json("RDATE", "20240310T014500", york)),
                override(york, "20240310T014500", "20240310T015000", "20240310T040000"),
            ),
        )
    }

    @Test
    fun `a series moved into another zone carries dates written at a time the clocks skip by their readings`() {
        // The 02:30 series moved to Phoenix, which keeps winter time all
        // year, at 02:30 there: each date written in New York moves from its
        // reading there to the same reading in Phoenix, and is written back
        // in New York.
        val phoenix = "America/Phoenix"
        assertEquals(
            "THE POINT: each date at its own reading in Phoenix, not an hour earlier",
            mapOf(
                "start" to listOf(listOf("20240301T023000", phoenix)),
                "excluded" to listOf(listOf("20240310T053000", york)),
                "added" to listOf(listOf("20240310T054500", york)),
                "occurrence" to listOf(listOf("20240310T024500", phoenix)),
                "late" to listOf(listOf("20240310T055000", york), listOf("20240310T065000", york)),
            ),
            saved(
                utc("2024-03-01T07:30:00Z"),
                zoning(phoenix),
                org.mochios.calendars.ui.editor.Scope.ALL,
                daily(york, "20240301T023000", "20240301T033000", json("EXDATE", "20240310T023000", york), json("RDATE", "20240310T024500", york)),
                override(york, "20240310T024500", "20240310T025000", "20240310T035000"),
            ),
        )
    }

    /** Daily from 02:30 on the 10th, which the server lists at 01:30 in winter time, with the 12th's excluded. */
    private val starts = daily(york, "20240310T023000", "20240310T033000", json("EXDATE", "20240312T023000", york))

    @Test
    fun `a series written at a time the clocks skip moved an hour on from its first occurrence goes there, its dates with it`() {
        assertEquals(
            "THE POINT: the series and the 12th's exclusion an hour on, not two",
            mapOf(
                "start" to listOf(listOf("20240310T033000", york)),
                "excluded" to listOf(listOf("20240312T033000", york)),
                "added" to emptyList(),
                "occurrence" to emptyList(),
                "late" to emptyList(),
            ),
            saved(
                utc("2024-03-10T06:30:00Z"),
                starting(java.time.LocalDateTime.of(2024, 3, 10, 3, 30)),
                org.mochios.calendars.ui.editor.Scope.ALL,
                starts,
            ),
        )
    }

    @Test
    fun `a series written at a time the clocks skip moved only to another zone keeps its reading, its dates with it`() {
        val phoenix = "America/Phoenix"
        assertEquals(
            "THE POINT: still 02:30, in Phoenix, and the 12th's exclusion at 02:30 there",
            mapOf(
                "start" to listOf(listOf("20240310T023000", phoenix)),
                "excluded" to listOf(listOf("20240312T053000", york)),
                "added" to emptyList(),
                "occurrence" to emptyList(),
                "late" to emptyList(),
            ),
            saved(utc("2024-03-10T06:30:00Z"), zoning(phoenix), org.mochios.calendars.ui.editor.Scope.ALL, starts),
        )
    }

    @Test
    fun `a series moved carries dates in the hour the clocks read twice, and those written in UTC or another zone, as before`() {
        // Daily at 00:30 in London from 20 October 2024, moved to 01:30. On
        // the 27th 01:30 is added and moved to 03:00, the 00:30 occurrence is
        // excluded in UTC, and 00:30 UTC, the first 01:30, is added written in
        // New York. Each lands where it did before readings were kept: a
        // reading the clocks read twice names the instant the server places
        // it at, and the others move by their instants.
        val london = "Europe/London"
        assertEquals(
            mapOf(
                "start" to listOf(listOf("20241020T013000", london)),
                "excluded" to listOf(listOf("20241027T013000Z", null)),
                "added" to listOf(listOf("20241027T023000", london), listOf("20241026T223000", york)),
                "occurrence" to listOf(listOf("20241027T023000", london)),
                "late" to listOf(listOf("20241027T040000", london), listOf("20241027T050000", london)),
            ),
            saved(
                utc("2024-10-19T23:30:00Z"),
                starting(java.time.LocalDateTime.of(2024, 10, 20, 1, 30)),
                org.mochios.calendars.ui.editor.Scope.ALL,
                daily(
                    london,
                    "20241020T003000",
                    "20241020T013000",
                    json("EXDATE", "20241026T233000Z"),
                    json("RDATE", "20241027T013000", london),
                    json("RDATE", "20241026T203000", york),
                ),
                override(london, "20241027T013000", "20241027T030000", "20241027T040000"),
            ),
        )
    }
}

/** A viewer in London whose week starts on Monday, on a test server. */
object London : Viewer {
    override fun zone(): String = "Europe/London"

    override fun week(): Int = 1

    override suspend fun server(): String = "https://example.org"
}
