// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.design

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.lifecycle.SavedStateHandle
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.android.ui.components.ViewFormState
import org.mochios.projects.R
import org.mochios.projects.api.ProjectsApi
import org.mochios.projects.repository.ProjectsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import org.mochios.android.R as MochiR

/**
 * The design screens: a setting cleared is sent as "", which the server
 * reads as cleared, and a change it refuses is shown over the loaded screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2000dp")
class DesignFormsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()
    private val asked = ConcurrentLinkedQueue<Pair<String, String>>()
    private lateinit var repository: ProjectsRepository

    /** The summary field as the server holds it. */
    @Volatile private var summary = """{"id": "summary", "name": "Summary", "fieldtype": "text", "flags": "required",
        "position": "header", "pattern": "^x", "rows": 3, "rank": 1, "minlength": 5, "maxlength": 40}"""

    /** Whether the server fails the design's read. */
    @Volatile private var unreadable = false

    @Before
    fun begin() {
        AppContext.set(context)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/projects/").substringBefore("?")
                val body = request.body.readUtf8()
                asked.add(path to body)
                when {
                    path == "p1/-/information" && unreadable ->
                        return MockResponse().setResponseCode(500).setBody("""{"error": "Down"}""")
                    path == "p1/-/classes/task/delete" ->
                        return MockResponse().setResponseCode(400).setBody("""{"error": "Class in use"}""")
                    path == "p1/-/classes/task/fields/status/options/open/delete" ->
                        return MockResponse().setResponseCode(400).setBody("""{"error": "Option in use"}""")
                    path == "p1/-/classes/task/fields/summary/update" ->
                        summary = """{"id": "summary", "name": "Summary", "fieldtype": "text", "flags": "",
                            "position": "", "pattern": "", "rows": 1, "rank": 1}"""
                }
                val data = when (path) {
                    "p1/-/information" -> """{"project": {"id": "p1", "name": "Board", "access": "owner"},
                        "classes": [{"id": "task", "name": "Task", "title": "summary", "rank": 1}],
                        "fields": {"task": [$summary, {"id": "status", "name": "Status", "fieldtype": "enumerated", "rank": 2},
                            {"id": "signed", "name": "Signed", "fieldtype": "checkbox", "rank": 3}]},
                        "options": {"task": {"status": [{"id": "open", "name": "Open", "colour": "#ff0000", "icon": "star", "rank": 1}]}},
                        "views": [{"id": "v1", "name": "Board", "viewtype": "board", "columns": "status", "rows": "status",
                            "sort": "summary", "border": "status", "filter": "status:open", "classes": ["task"]}],
                        "hierarchy": {"task": [""]}}"""
                    else -> "{}"
                }
                return MockResponse().setBody("""{"data": $data}""")
            }
        }
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/projects/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProjectsApi::class.java)
        repository = ProjectsRepository(api, FileStore(context), context, SessionManager(context))
    }

    @After
    fun end() = server.shutdown()

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun sent(path: String): JsonObject =
        JsonParser.parseString(asked.last { (asked, _) -> asked == path }.second).asJsonObject

    private fun handle(vararg pairs: Pair<String, String>) = SavedStateHandle(mapOf(*pairs))

    /** Lets a snackbar come up without the test clock running past its timeout. */
    private fun shown(text: String): Boolean {
        repeat(30) {
            rule.mainClock.advanceTimeByFrame()
            shadowOf(Looper.getMainLooper()).idle()
            if (rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return true
            Thread.sleep(10)
        }
        return false
    }

    // ---- View ----

    @Test
    fun `a view's cleared settings are sent as empty strings`() {
        val model = EditViewViewModel(handle("projectId" to "p1", "viewId" to "v1"), repository)
        until { model.uiState.value.projectDetails != null }
        val view = model.uiState.value.projectDetails!!.views.single().toListItem()
        val form = ViewFormState(view)
        form.rows = ""
        form.border = ""
        form.sort = ""
        form.filter = ""
        form.classes = emptySet()
        model.updateView(form.toDraft())
        until { model.uiState.value.saved }
        val body = sent("p1/-/views/v1/update")
        for (setting in listOf("rows", "border", "sort", "filter", "classes")) {
            assertEquals("THE POINT: $setting arrives cleared", "", body[setting]?.asString)
        }
    }

    // ---- Field ----

    private fun field(id: String): FieldDetailViewModel {
        val model = FieldDetailViewModel(handle("projectId" to "p1", "classId" to "task", "fieldId" to id), repository)
        rule.setContent { FieldDetailScreen(onBack = {}, onAddOption = {}, onEditOption = {}, viewModel = model) }
        rule.waitForIdle()
        until { model.uiState.value.projectDetails != null }
        rule.waitForIdle()
        return model
    }

    @Test
    fun `a field's last flag, position, pattern and rows are cleared, and the form takes what was saved`() {
        val model = field("summary")
        rule.onNodeWithText(context.getString(R.string.projects_field_required)).performClick()
        rule.onNodeWithText(context.getString(R.string.projects_field_position_header)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.projects_field_position_default)).performClick()
        rule.onNodeWithText("^x").performTextClearance()
        rule.onNodeWithText("3").performTextClearance()
        rule.onNodeWithText(context.getString(MochiR.string.common_save)).performScrollTo().performClick()
        until { asked.any { (path, _) -> path == "p1/-/classes/task/fields/summary/update" } }
        val body = sent("p1/-/classes/task/fields/summary/update")
        assertEquals("THE POINT: no flag on is sent as none, not left out", "", body["flags"]?.asString)
        until { model.uiState.value.saves == 1 }
        rule.waitForIdle()
        assertTrue(
            "the form shows the saved field and nothing is left to save",
            rule.onAllNodesWithText(context.getString(MochiR.string.common_save)).fetchSemanticsNodes().isEmpty(),
        )
    }

    /** Saves the summary field and returns what the update sent. */
    private fun save(): JsonObject {
        rule.onNodeWithText(context.getString(MochiR.string.common_save)).performScrollTo().performClick()
        until { asked.any { (path, _) -> path == "p1/-/classes/task/fields/summary/update" } }
        return sent("p1/-/classes/task/fields/summary/update")
    }

    @Test
    fun `a field's position put back to default is sent as an empty string`() {
        field("summary")
        rule.onNodeWithText(context.getString(R.string.projects_field_position_header)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.projects_field_position_default)).performClick()
        assertEquals("", save()["position"]?.asString)
    }

    @Test
    fun `a field's pattern cleared is sent as an empty string`() {
        field("summary")
        rule.onNodeWithText("^x").performTextClearance()
        assertEquals("", save()["pattern"]?.asString)
    }

    @Test
    fun `a field's rows cleared are sent as an empty string`() {
        field("summary")
        rule.onNodeWithText("3").performTextClearance()
        assertEquals("", save()["rows"]?.asString)
    }

    @Test
    fun `a field opens with the length limits it has, and a limit cleared is sent as none`() {
        field("summary")
        assertTrue("THE POINT: the minimum as stored", rule.onAllNodesWithText("5").fetchSemanticsNodes().isNotEmpty())
        assertTrue("the maximum as stored", rule.onAllNodesWithText("40").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("5").performScrollTo().performTextClearance()
        assertEquals(0, save()["minlength"]?.asInt)
    }

    // ---- Field types ----

    @Test
    fun `a checkbox field can be made`() {
        val model = CreateFieldViewModel(handle("projectId" to "p1", "classId" to "task"), repository)
        rule.setContent { CreateFieldScreen(onBack = {}, onCreated = {}, viewModel = model) }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.projects_field_type)).performClick()
        rule.waitForIdle()
        assertTrue(
            "THE POINT: Checkbox is offered with the other six",
            rule.onAllNodesWithText(context.getString(MochiR.string.field_type_checkbox)).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun `a class names a checkbox field's type`() {
        val model = ClassDetailViewModel(handle("projectId" to "p1", "classId" to "task"), repository)
        rule.setContent { ClassDetailScreen(onBack = {}, onAddField = {}, onFieldClick = {}, viewModel = model) }
        until { model.uiState.value.projectDetails != null }
        rule.waitForIdle()
        assertTrue(
            "THE POINT: by its name, not its key",
            rule.onAllNodesWithText(context.getString(MochiR.string.field_type_checkbox)).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(rule.onAllNodesWithText("checkbox").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a refused option delete is shown over the loaded field`() {
        field("status")
        rule.onAllNodesWithContentDescription(context.getString(MochiR.string.common_delete))[0].performScrollTo().performClick()
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        until { asked.any { (path, _) -> path.endsWith("/options/open/delete") } }
        assertTrue("THE POINT: the server's refusal is on screen", shown("Option in use"))
    }

    // ---- Class ----

    @Test
    fun `a refused class delete is shown over the loaded class`() {
        val model = ClassDetailViewModel(handle("projectId" to "p1", "classId" to "task"), repository)
        rule.setContent { ClassDetailScreen(onBack = {}, onAddField = {}, onFieldClick = {}, viewModel = model) }
        until { model.uiState.value.projectDetails != null }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.projects_class_delete)).performScrollTo().performClick()
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText(context.getString(MochiR.string.common_delete)).performClick()
        until { asked.any { (path, _) -> path == "p1/-/classes/task/delete" } }
        assertTrue("THE POINT: the server's refusal is on screen", shown("Class in use"))
    }

    @Test
    fun `a class that cannot be read keeps its retry`() {
        unreadable = true
        val model = ClassDetailViewModel(handle("projectId" to "p1", "classId" to "task"), repository)
        rule.setContent { ClassDetailScreen(onBack = {}, onAddField = {}, onFieldClick = {}, viewModel = model) }
        until { model.uiState.value.error != null }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(10_000)
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(MochiR.string.common_retry)).assertExists()
    }

    // ---- Option ----

    @Test
    fun `an option's icon cleared is sent as an empty string`() {
        val model = OptionViewModel(
            handle("projectId" to "p1", "classId" to "task", "fieldId" to "status", "optionId" to "open"),
            repository,
        )
        rule.setContent { OptionScreen(onBack = {}, onSaved = {}, viewModel = model) }
        until { model.uiState.value.option != null }
        rule.waitForIdle()
        rule.onNodeWithText("star").performTextClearance()
        rule.onNodeWithText(context.getString(MochiR.string.common_save)).performClick()
        until { asked.any { (path, _) -> path.endsWith("/options/open/update") } }
        val body = sent("p1/-/classes/task/fields/status/options/open/update")
        assertEquals("THE POINT: the icon arrives cleared", "", body["icon"]?.asString)
        assertEquals("#ff0000", body["colour"]?.asString)
    }

    @Test
    fun `an option's colour cleared is sent as an empty string`() {
        val model = OptionViewModel(
            handle("projectId" to "p1", "classId" to "task", "fieldId" to "status", "optionId" to "open"),
            repository,
        )
        until { model.uiState.value.option != null }
        model.save("Open", "", "star")
        until { model.uiState.value.saved }
        assertEquals("", sent("p1/-/classes/task/fields/status/options/open/update")["colour"]?.asString)
    }
}
