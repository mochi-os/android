// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.history

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.runBlocking
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
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.android.ui.components.LoadState
import org.mochios.wikis.api.WikisApi
import org.mochios.wikis.model.RevisionDetail
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import org.mochios.wikis.ui.changes.ChangesBody
import org.mochios.wikis.ui.changes.ChangesViewModel
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.WikiContextValue
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A page's history and the wiki's recent changes: a further page that fails
 * says so at the end of the list, with Retry; a revision compares against the
 * revision before it, which after a delete and restore is not one version
 * below; failures carry the server's reason, and the fixed line only when it
 * gave none; and a revert records the server's own note unless the reader
 * writes one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var repository: WikisRepository

    /** How many more times a further page fails. */
    private val failing = AtomicInteger(0)

    /** What the revision answer says of the one before, as its `version` object. */
    @Volatile private var version = """{"current": 7, "previous": 3}"""

    /** Each revision asked for, by version. */
    private val revisions = ConcurrentLinkedQueue<String>()

    /** What a revert is answered with. */
    @Volatile private var revert: () -> MockResponse = { ok("{}") }

    /** How many revisions and changes the server holds. */
    @Volatile private var held = 60

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when (val path = url.encodedPath) {
                    "/wikis/w1/-/info" -> ok("""{"wiki": {"id": "w1", "fingerprint": "wfp", "name": "Wiki"}, "permissions": {"view": true, "edit": true}}""")
                    "/wikis/w1/-/pages/home/history" -> page(url.queryParameter("offset")) { index ->
                        """{"id": "r$index", "version": ${held - index}, "title": "Home", "author": "a", "name": "Ann"}"""
                    }?.let { rows -> ok("""{"revisions": $rows, "total": $held}""") } ?: failure()
                    "/wikis/w1/-/changes" -> page(url.queryParameter("offset")) { index ->
                        """{"id": "c$index", "slug": "home", "version": ${held - index}, "title": "Home", "author": "a", "name": "Ann"}"""
                    }?.let { rows -> ok("""{"changes": $rows, "total": $held}""") } ?: failure()
                    "/wikis/w1/-/pages/home/revert" -> revert()
                    else -> {
                        val asked = path.removePrefix("/wikis/w1/-/pages/home/history/")
                        if (asked != path) {
                            revisions += asked
                            when (asked) {
                                "7" -> ok("""{"revision": {"id": "r7", "version": 7, "title": "Home", "content": "Seventh"}, "version": $version}""")
                                "3" -> ok("""{"revision": {"id": "r3", "version": 3, "title": "Home", "content": "Third"}, "version": {"current": 7}}""")
                                else -> MockResponse().setResponseCode(404).setBody("""{"error": "Revision not found"}""")
                            }
                        } else {
                            MockResponse().setResponseCode(404)
                        }
                    }
                }
            }
        }
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/wikis/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WikisApi::class.java)
        session = SessionManager(context)
        runBlocking { session.setServerUrl(server.url("/").toString().trimEnd('/')) }
        repository = WikisRepository(api, FileStore(context), context)
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun failure() = MockResponse().setResponseCode(429).setBody("""{"error": "Too many requests"}""")

    /** The fifty rows a page starting at [offset] holds, or null when the page is to fail. */
    private fun page(offset: String?, row: (Int) -> String): String? {
        val start = offset?.toIntOrNull() ?: 0
        if (start > 0 && failing.getAndUpdate { left -> if (left > 0) left - 1 else 0 } > 0) return null
        return (start until minOf(start + 50, held)).joinToString(",", "[", "]") { index -> row(index) }
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun handle(version: Int = 7) = SavedStateHandle(mapOf("wikiId" to "w1", "page" to "home", "version" to version))

    @Test
    fun `a failed further page of history is held with its reason until retried`() {
        val model = PageHistoryViewModel(handle(), repository, session)
        until { !model.uiState.value.isLoading }
        failing.set(1)
        model.loadMore()
        until { model.uiState.value.load is LoadState.Failed }
        assertEquals("Too many requests", (model.uiState.value.load as LoadState.Failed).error.userMessage())
        assertEquals(50, model.uiState.value.revisions.size)
        model.loadMore()
        until { model.uiState.value.load == LoadState.Idle && model.uiState.value.revisions.size == 60 }
    }

    @Test
    fun `a failed further page of changes is held with its reason until retried`() {
        val model = ChangesViewModel(SavedStateHandle(mapOf("wikiId" to "w1")), repository, session)
        until { !model.uiState.value.isLoading }
        failing.set(1)
        model.loadMore()
        until { model.uiState.value.load is LoadState.Failed }
        assertEquals("Too many requests", (model.uiState.value.load as LoadState.Failed).error.userMessage())
        model.loadMore()
        until { model.uiState.value.load == LoadState.Idle && model.uiState.value.changes.size == 60 }
    }

    private val wiki = WikiContextValue("w1", WikiInfo(id = "w1"), WikiPermissions(view = true), "https://server")

    @Test
    fun `the history says a failed further page at the end of the list, with Retry`() {
        var retried = 0
        rule.setContent {
            CompositionLocalProvider(LocalWikiContext provides wiki) {
                PageHistoryBody(
                    slug = "home", wikiId = "w1", revisions = emptyList(), currentVersion = 0, hasMore = true,
                    load = LoadState.Failed(MochiError.ServerError(429, "Too many requests")),
                    onLoadMore = { retried++ }, onView = {}, onRevert = {},
                )
            }
        }
        rule.onNodeWithText("Too many requests").assertExists()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `a revision compares against the revision before it, as the server names it`() {
        val model = RevisionViewModel(handle(), repository, session)
        until { !model.uiState.value.isLoading }
        assertTrue(model.uiState.value.hasPrevious)
        model.toggleDiff()
        until { model.uiState.value.previousRevision != null }
        assertEquals("THE POINT: version 3, not 6", listOf("7", "3"), revisions.toList())
    }

    @Test
    fun `a revision from a server that names no previous compares against the version below`() {
        version = """{"current": 7}"""
        val model = RevisionViewModel(handle(), repository, session)
        until { !model.uiState.value.isLoading }
        model.toggleDiff()
        until { !model.uiState.value.previousLoading && model.uiState.value.compareError != null }
        assertEquals(listOf("7", "6"), revisions.toList())
        assertEquals("Revision not found", model.uiState.value.compareError?.userMessage())
    }

    @Test
    fun `a first revision has nothing to compare`() {
        version = """{"current": 7, "previous": 0}"""
        val model = RevisionViewModel(handle(), repository, session)
        until { !model.uiState.value.isLoading }
        assertFalse(model.uiState.value.hasPrevious)
        model.toggleDiff()
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
        assertEquals(listOf("7"), revisions.toList())
    }

    private fun revision(compareError: MochiError?) {
        rule.setContent {
            CompositionLocalProvider(LocalWikiContext provides wiki) {
                RevisionBody(
                    slug = "home",
                    revision = RevisionDetail(id = "r7", version = 7, title = "Home", content = "Seventh"),
                    currentVersion = 7, hasPrevious = true, showDiff = true, previousRevision = null,
                    previousLoading = false, compareError = compareError,
                    onToggleDiff = {}, onRevert = {}, onInternalLink = {},
                )
            }
        }
    }

    @Test
    fun `a failed comparison says the server's reason`() {
        revision(MochiError.NotFoundError("Revision not found"))
        rule.onNodeWithText("Revision not found").assertExists()
        rule.onNodeWithText("Could not load previous version for comparison.").assertDoesNotExist()
    }

    @Test
    fun `a failed comparison with no reason says the fixed line`() {
        revision(MochiError.ServerError(500, null))
        rule.onNodeWithText("Could not load previous version for comparison.").assertExists()
    }

    @Test
    fun `a refused revert with no reason says the revert failed`() {
        // A server failure that gives no reason.
        revert = { MockResponse().setResponseCode(500).setBody("{}") }
        held = 2
        val model = PageHistoryViewModel(handle(), repository, session)
        rule.setContent { PageHistoryScreen(navController = rememberNavController(), viewModel = model) }
        until { !model.uiState.value.isLoading }
        model.revert(1, "")
        until {
            rule.waitForIdle()
            ShadowToast.getLatestToast() != null
        }
        assertEquals("THE POINT: the fixed line, not a bare server error", "Failed to revert page", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a refused revert of the revision shown, with no reason, says the revert failed`() {
        revert = { MockResponse().setResponseCode(500).setBody("{}") }
        val model = RevisionViewModel(handle(), repository, session)
        rule.setContent { RevisionViewScreen(navController = rememberNavController(), viewModel = model) }
        until { !model.uiState.value.isLoading }
        model.revert("")
        until {
            rule.waitForIdle()
            ShadowToast.getLatestToast() != null
        }
        assertEquals("THE POINT: the fixed line, not a bare server error", "Failed to revert page", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `recent changes say a failed further page at the end of the list, with Retry`() {
        var retried = 0
        rule.setContent {
            CompositionLocalProvider(LocalWikiContext provides wiki) {
                ChangesBody(
                    changes = emptyList(), hasMore = true,
                    load = LoadState.Failed(MochiError.ServerError(429, "Too many requests")),
                    onLoadMore = { retried++ }, onOpenPage = {},
                )
            }
        }
        rule.onNodeWithText("Too many requests").assertExists()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `a revert records the server's note unless the reader writes one`() {
        var confirmed: String? = null
        rule.setContent {
            RevertDialog(slug = "home", version = 3, isReverting = false, onConfirm = { comment -> confirmed = comment }, onDismiss = {})
        }
        rule.onNodeWithText("Revert").performClick()
        assertEquals("THE POINT: an empty comment, for the server's own note", "", confirmed)
    }
}
