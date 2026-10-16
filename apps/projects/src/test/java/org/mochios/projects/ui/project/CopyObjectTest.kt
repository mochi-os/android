// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.project

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
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
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Copy on an object opens the new-object form filled from it, as the web's
 * does: its class, parent and values, its status among them, and its
 * attachments, which Create copies onto the new object.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class CopyObjectTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()

    /** Each request as path and body. */
    private val asked = ConcurrentLinkedQueue<Pair<String, String>>()

    @Before
    fun begin() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/projects/").substringBefore("?")
                asked.add(path to request.body.readUtf8())
                val data = when (path) {
                    "p1/-/information" -> """{"project": {"id": "p1", "prefix": "P"},
                        "classes": [{"id": "bug", "name": "Bug", "title": "title"}, {"id": "card", "name": "Card", "title": "title"}],
                        "fields": {"bug": [{"id": "title", "name": "Title", "fieldtype": "text", "rank": 1}],
                            "card": [
                            {"id": "title", "name": "Title", "fieldtype": "text", "rank": 1},
                            {"id": "status", "name": "Status", "fieldtype": "text", "rank": 2}]},
                        "options": {}, "views": [], "hierarchy": {"bug": [""], "card": [""]}}"""
                    "p1/-/objects" -> """{"objects": [{"id": "o1", "class": "card", "parent": "",
                        "values": {"title": "Original", "status": "Done"}}]}"""
                    "p1/-/people" -> """{"people": []}"""
                    "p1/-/objects/o1/attachments" -> """{"attachments": [{"id": "a1", "name": "plan.png"}]}"""
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

    private fun model(copy: String?): CreateObjectViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/projects/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val repository = ProjectsRepository(retrofit.create(ProjectsApi::class.java), FileStore(context), context)
        val route = buildMap<String, Any?> {
            put("projectId", "p1")
            if (copy != null) put("copy", copy)
        }
        return CreateObjectViewModel(SavedStateHandle(route), repository, ActiveViewStore(context))
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

    private fun show(copy: String?): CreateObjectViewModel {
        val model = model(copy)
        until { !model.uiState.value.isLoading }
        rule.setContent { CreateObjectScreen(onBack = {}, onCreated = {}, viewModel = model) }
        rule.waitForIdle()
        return model
    }

    private fun create(model: CreateObjectViewModel) {
        rule.onNodeWithText(context.getString(R.string.projects_create_action)).performClick()
        until { model.uiState.value.createdObjectId != null || sent("p1/-/objects/create").isNotEmpty() && !model.uiState.value.isCreating }
    }

    @Test
    fun `a copy opens on the original's values and attachments, and Create copies them, status and all`() {
        val model = show("o1")
        assertTrue(rule.onAllNodesWithText("Original").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("plan.png").fetchSemanticsNodes().isNotEmpty())
        assertEquals(emptyList<String>(), sent("p1/-/objects/create"))
        create(model)
        val made = sent("p1/-/objects/create").single()
        assertTrue(made, made.contains("class=card") && made.contains("title=Original"))
        assertTrue(sent("p1/-/objects/o9/values/status").single().contains("Done"))
        assertEquals(listOf("attachments=a1"), sent("p1/-/objects/o9/attachments/copy"))
    }

    @Test
    fun `an attachment taken off the copy is not copied`() {
        val model = show("o1")
        rule.onNodeWithContentDescription(context.getString(R.string.projects_attachment_remove)).performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("plan.png").fetchSemanticsNodes().isEmpty())
        create(model)
        assertEquals(1, sent("p1/-/objects/create").size)
        assertEquals(emptyList<String>(), sent("p1/-/objects/o9/attachments/copy"))
    }

    @Test
    fun `a plain new object reads no attachments and copies none`() {
        val model = model(null)
        until { !model.uiState.value.isLoading }
        assertEquals(emptyList<String>(), sent("p1/-/objects/o1/attachments"))
        assertEquals(emptyList<String>(), model.uiState.value.copied.map { attachment -> attachment.id })
    }
}
