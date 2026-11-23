// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.projectlist

import android.os.Looper
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
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
 * A new project is unlisted and for its members unless its creator says
 * otherwise, one switch listing it and another letting anyone view it, and
 * the template it must be made from is never skipped: a failed fetch of them
 * is shown with a retry.
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

    // The form's two switches in the order it shows them: the listing, then
    // who may view. A row's label and switch are not one node, so the label
    // cannot pick its switch.
    private fun listing() = rule.onAllNodes(isToggleable())[0]
    private fun viewing() = rule.onAllNodes(isToggleable())[1]

    /** Creates from the shown form with the two switches as given. */
    private fun create(search: Boolean, view: Boolean): String {
        show()
        listing().assertIsOff()
        viewing().assertIsOff()
        if (search) listing().performClick()
        if (view) viewing().performClick()
        rule.onNodeWithText(context.getString(R.string.projects_create_next)).performClick()
        rule.onNodeWithText("Kanban board").performClick()
        rule.onNodeWithText(context.getString(R.string.projects_create_action)).performClick()
        until { created().isNotEmpty() }
        return created().single()
    }

    @Test
    fun `a new project is private unless asked otherwise`() {
        val body = create(search = false, view = false)
        assertTrue("THE POINT: private: $body", body.contains("privacy=private"))
        assertTrue("THE POINT: members only: $body", body.contains("readable=false"))
        assertTrue(body.contains("template=kanban"))
    }

    @Test
    fun `the search switch lists the project and lets no one in`() {
        val body = create(search = true, view = false)
        assertTrue(body, body.contains("privacy=public"))
        assertTrue("THE POINT: listing grants nothing: $body", body.contains("readable=false"))
    }

    @Test
    fun `the view switch lets anyone view the project, unlisted`() {
        val body = create(search = false, view = true)
        assertTrue(body, body.contains("privacy=private"))
        assertTrue("THE POINT: anyone may view: $body", body.contains("readable=true"))
    }

    @Test
    fun `the form keeps its step and what was typed when the screen is made again`() {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/projects/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProjectsApi::class.java)
        val model = CreateProjectViewModel(ProjectsRepository(api, FileStore(context), context, SessionManager(context)))
        until { !model.uiState.value.templatesLoading }
        val tester = StateRestorationTester(rule)
        tester.setContent { CreateProjectScreen(onBack = {}, onCreated = {}, viewModel = model) }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.projects_create_name)).performTextInput("Website")
        listing().performClick()
        viewing().performClick()
        rule.onNodeWithText(context.getString(R.string.projects_create_next)).performClick()
        rule.onNodeWithText("Kanban board").performClick()
        rule.waitForIdle()
        // As a rotation, or a change of theme or font size, does.
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.projects_create_action)).performClick()
        until { created().isNotEmpty() }
        val body = created().single()
        assertTrue("THE POINT: the name typed: $body", body.contains("name=Website"))
        assertTrue("the search switch as set", body.contains("privacy=public"))
        assertTrue("the view switch as set", body.contains("readable=true"))
        assertTrue("the template picked", body.contains("template=kanban"))
    }

    @Test
    fun `the switches say what they do`() {
        show()
        assertEquals("Allow anyone to search for project", context.getString(R.string.projects_create_allow_search))
        assertEquals("Allow anyone to view project", context.getString(R.string.projects_create_allow_public))
        rule.onNodeWithText(context.getString(R.string.projects_create_allow_search)).assertExists()
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
