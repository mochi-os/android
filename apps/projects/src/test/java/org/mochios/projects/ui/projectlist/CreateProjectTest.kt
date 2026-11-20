// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.projectlist

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
 * A new project is private unless its creator says otherwise, and the
 * template it must be made from is never skipped: a failed fetch of them is
 * shown with a retry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class CreateProjectTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()
    private val asked = ConcurrentLinkedQueue<Pair<String, String>>()

    /** Whether the server fails the templates' read. */
    @Volatile private var templatesDown = false

    @Before
    fun begin() {
        AppContext.set(context)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/projects/").substringBefore("?")
                asked.add(path to request.body.readUtf8())
                if (path == "-/templates" && templatesDown) {
                    return MockResponse().setResponseCode(500).setBody("""{"error": "Down"}""")
                }
                val data = when (path) {
                    "-/templates" -> """{"templates": [{"id": "kanban", "name": "Kanban board"}]}"""
                    "-/create" -> """{"id": "p9", "fingerprint": "abc"}"""
                    else -> "{}"
                }
                return MockResponse().setBody("""{"data": $data}""")
            }
        }
        server.start()
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

    private fun show(): CreateProjectViewModel {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/projects/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProjectsApi::class.java)
        val model = CreateProjectViewModel(ProjectsRepository(api, FileStore(context), context, SessionManager(context)))
        until { !model.uiState.value.templatesLoading }
        rule.setContent { CreateProjectScreen(onBack = {}, onCreated = {}, viewModel = model) }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.projects_create_name)).performTextInput("Website")
        return model
    }

    private fun created(): List<String> = asked.filter { (path, _) -> path == "-/create" }.map { (_, body) -> body }

    @Test
    fun `a new project is private unless asked otherwise`() {
        show()
        rule.onNodeWithText(context.getString(R.string.projects_create_next)).performClick()
        rule.onNodeWithText("Kanban board").performClick()
        rule.onNodeWithText(context.getString(R.string.projects_create_action)).performClick()
        until { created().isNotEmpty() }
        val body = created().single()
        assertTrue("THE POINT: private: $body", body.contains("privacy=private"))
        assertTrue(body.contains("template=kanban"))
    }

    @Test
    fun `the switch says it lets anyone view and comment`() {
        show()
        rule.onNodeWithText(context.getString(R.string.projects_create_allow_public)).assertExists()
    }

    @Test
    fun `a failed template fetch is shown with a retry, and nothing is created without one`() {
        templatesDown = true
        show()
        // Whatever the button offers, Next or Create.
        val submit = listOf(R.string.projects_create_next, R.string.projects_create_action)
            .map { label -> context.getString(label) }
            .first { label -> rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(submit).performClick()
        rule.waitForIdle()
        // Long enough for a create the button sent to reach the server.
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
        assertEquals("THE POINT: no project is asked for without a template", emptyList<String>(), created())
        templatesDown = false
        rule.onNodeWithText(context.getString(MochiR.string.common_retry)).performClick()
        until { asked.count { (path, _) -> path == "-/templates" } == 2 }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Kanban board").fetchSemanticsNodes().isNotEmpty())
    }
}
