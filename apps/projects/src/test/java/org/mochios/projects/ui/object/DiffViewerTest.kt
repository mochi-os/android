// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.SavedStateHandle
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.projects.R
import org.mochios.projects.api.ProjectsApi
import org.mochios.projects.repository.ProjectsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * A request's diff: branches with no changes say so, and the changed files
 * and their counts scroll with the diff, in the user's number format.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class DiffViewerTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()

    @Before
    fun begin() {
        AppContext.set(context)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path.orEmpty().substringBefore("?")) {
                "/projects/-/repositories/repo1/diff" -> MockResponse().setBody("""{"data": ""}""")
                "/projects/-/diff/preference" -> MockResponse().setBody("""{"data": {"style": "unified"}}""")
                else -> MockResponse().setBody("""{"data": {}}""")
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

    @Test
    fun `branches with no changes between them say so`() {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/projects/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProjectsApi::class.java)
        val model = DiffViewerViewModel(
            SavedStateHandle(mapOf("repo" to "repo1", "source" to "fix", "target" to "main")),
            ProjectsRepository(api, FileStore(context), context, SessionManager(context)),
        )
        until { model.uiState.value.diff != null }
        rule.setContent { DiffViewerScreen(onBack = {}, viewModel = model) }
        rule.waitForIdle()
        assertTrue(
            "THE POINT: not a blank page",
            rule.onAllNodesWithText(context.getString(R.string.projects_diff_empty)).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun `the changed files lead the page the diff scrolls in, counted as the user writes numbers`() {
        val format = Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))
        val diff = "diff --git a/<x>.kt b/<x>.kt\n+added\n-removed"
        val page = diffPage(diff, unified = true, stats = listOf(FileStats("<x>.kt", 1234, 5)), format = format)
        val summary = page.indexOf("class=\"summary\"")
        assertTrue("THE POINT: the summary is in the page", summary >= 0)
        assertTrue("before the diff", summary < page.indexOf("class=\"file\""))
        assertTrue("the counts in the user's format", page.contains("+1.234") && page.contains("-5"))
        assertFalse("a file name is text, not markup", page.contains("<x>"))
    }
}
