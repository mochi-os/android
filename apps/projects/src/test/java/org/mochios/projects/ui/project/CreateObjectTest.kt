// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.project

import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import com.google.gson.JsonParser
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.projects.R
import org.mochios.projects.api.ProjectsApi
import org.mochios.projects.lib.ActiveViewStore
import org.mochios.projects.repository.ProjectsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import org.mochios.android.R as MochiR

/**
 * A new object goes up with its values in one request, which the server
 * refuses whole. An attachment that fails afterwards is named, and the retry
 * uploads it to the object already made rather than making another.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class CreateObjectTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()

    /** Each request as path and body. */
    private val asked = ConcurrentLinkedQueue<Pair<String, String>>()

    /** Files whose upload the server refuses, by name. */
    @Volatile private var failing = setOf<String>()

    /** Whether the server refuses to copy attachments. */
    @Volatile private var copyFails = false

    @Before
    fun begin() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/projects/").substringBefore("?")
                val body = request.body.readUtf8()
                asked.add(path to body)
                if (path == "p1/-/objects/o9/attachments/create" && failing.any { name -> body.contains("filename=\"$name\"") }) {
                    return MockResponse().setResponseCode(500).setBody("""{"error": "Too large"}""")
                }
                if (path == "p1/-/objects/o9/attachments/copy" && copyFails) {
                    return MockResponse().setResponseCode(500).setBody("""{"error": "Unreachable"}""")
                }
                val data = when (path) {
                    "p1/-/information" -> """{"project": {"id": "p1", "prefix": "P"},
                        "classes": [{"id": "card", "name": "Card", "title": "title"}],
                        "fields": {"card": [
                            {"id": "title", "name": "Title", "fieldtype": "text", "rank": 1},
                            {"id": "status", "name": "Status", "fieldtype": "text", "rank": 2}]},
                        "options": {}, "views": [], "hierarchy": {"card": [""]}}"""
                    "p1/-/objects" -> """{"objects": [{"id": "o1", "class": "card", "parent": "", "values": {"title": "Original"}}]}"""
                    "p1/-/objects/o1/attachments" -> """{"attachments": [{"id": "a1", "name": "plan.png"}]}"""
                    "p1/-/people" -> """{"people": []}"""
                    "p1/-/objects/create" -> """{"id": "o9", "number": 2}"""
                    else -> """{}"""
                }
                return MockResponse().setBody("""{"data": $data}""")
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun model(copy: String? = null, preset: Pair<String, String>? = null): CreateObjectViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/projects/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val repository = ProjectsRepository(retrofit.create(ProjectsApi::class.java), FileStore(context), context, SessionManager(context))
        val route = buildMap<String, Any?> {
            put("projectId", "p1")
            if (copy != null) put("copy", copy)
            if (preset != null) {
                put("field", preset.first)
                put("value", preset.second)
            }
        }
        val model = CreateObjectViewModel(SavedStateHandle(route), repository, ActiveViewStore(context))
        until { !model.uiState.value.isLoading }
        return model
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun sent(path: String) = asked.filter { (asked, _) -> asked == path }.map { (_, body) -> body }

    private fun picked(name: String): Uri {
        val file = File(context.filesDir, name)
        file.writeText("bytes of $name")
        return Uri.fromFile(file)
    }

    private fun create(model: CreateObjectViewModel, uris: List<Uri> = emptyList(), copies: List<String> = emptyList()) {
        val before = asked.size
        model.createObject("card", "First", initialValues = mapOf("status" to "Doing"), uris = uris, copies = copies)
        until { asked.size > before && !model.uiState.value.isCreating }
    }

    @Test
    fun `the values go in the create itself`() {
        val model = model()
        create(model)
        val made = JsonParser.parseString(sent("p1/-/objects/create").single()).asJsonObject
        assertEquals("First", made["title"].asString)
        assertEquals("THE POINT: the server sees every value before it makes the object", "Doing", made["values"].asJsonObject["status"].asString)
        assertEquals("no value is written after the object is made", emptyList<String>(), sent("p1/-/objects/o9/values/status"))
        assertEquals("o9", model.uiState.value.createdObjectId)
    }

    @Test
    fun `a file that does not attach is named and the form stays`() {
        failing = setOf("plan.png")
        val model = model()
        create(model, uris = listOf(picked("plan.png"), picked("notes.txt")))
        assertEquals("THE POINT: the file left behind is named", listOf("plan.png"), model.uiState.value.unattached)
        assertNull("the form does not close as a success", model.uiState.value.createdObjectId)
        assertEquals("o9", model.uiState.value.created)
    }

    @Test
    fun `the retry uploads to the object already made, and only what failed`() {
        failing = setOf("plan.png")
        val model = model()
        val uris = listOf(picked("plan.png"), picked("notes.txt"))
        create(model, uris = uris)
        failing = emptySet()
        create(model, uris = uris)
        assertEquals("THE POINT: one object, however many tries", 1, sent("p1/-/objects/create").size)
        val uploads = sent("p1/-/objects/o9/attachments/create")
        assertEquals("the file already up is not sent again", 1, uploads.count { body -> body.contains("filename=\"notes.txt\"") })
        assertEquals(2, uploads.count { body -> body.contains("filename=\"plan.png\"") })
        assertEquals("o9", model.uiState.value.createdObjectId)
        assertEquals(emptyList<String>(), model.uiState.value.unattached)
    }

    @Test
    fun `a copied attachment that does not copy is named`() {
        copyFails = true
        val model = model(copy = "o1")
        create(model, copies = listOf("a1"))
        assertEquals(listOf("plan.png"), model.uiState.value.unattached)
        assertNull(model.uiState.value.createdObjectId)
    }

    @Test
    fun `the form keeps what was typed when the screen is made again`() {
        val model = model(preset = "status" to "Doing")
        val tester = StateRestorationTester(rule)
        tester.setContent { CreateObjectScreen(onBack = {}, onCreated = {}, viewModel = model) }
        rule.waitForIdle()
        rule.onNodeWithText("Title").performTextReplacement("Typed")
        rule.onNodeWithText("Status").performTextReplacement("Done")
        rule.waitForIdle()
        // As a rotation, or a change of theme or font size, does.
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        assertEquals("THE POINT: the title typed stays", "Typed", model.values["title"])
        assertEquals("an edited preset is not put back", "Done", model.values["status"])
        assertTrue(rule.onAllNodesWithText("Typed").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `the form names what did not attach and offers a retry`() {
        failing = setOf("plan.png")
        val model = model()
        rule.setContent { CreateObjectScreen(onBack = {}, onCreated = {}, viewModel = model) }
        rule.waitForIdle()
        create(model, uris = listOf(picked("plan.png")))
        rule.waitForIdle()
        val message = context.getString(R.string.projects_create_attachments_failed, "plan.png")
        assertTrue(rule.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText(context.getString(MochiR.string.common_retry)).fetchSemanticsNodes().isNotEmpty())
    }
}
