// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.moderation

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
import org.mochios.android.api.MochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.model.ForumMember
import org.mochios.forums.model.ModerationReport
import org.mochios.forums.repository.ForumsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The moderation screen's actions against a server: a refused one is said over
 * the tab, which stays, and a restriction is made by choosing a member and a
 * length rather than typing an id and a count of seconds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModerationActionsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: ForumsRepository
    private val asked = ConcurrentLinkedQueue<RecordedRequest>()

    /** Whether the server takes a restriction. */
    @Volatile private var restricting = true

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked.add(request)
                return when (request.path.orEmpty().substringAfter("/forums/").substringBefore("?")) {
                    "f1/-/moderation/queue" -> ok(
                        """{"posts": [{"id": "p1", "forum": "f1", "member": "m1", "name": "Ann",
                            "title": "Waiting post", "body": "Held for review"}],
                            "comments": [], "counts": {"posts": 1, "comments": 0}}""",
                    )
                    "f1/-/moderation/reports" -> ok(
                        """{"reports": [{"id": "r1", "type": "post", "status": "pending",
                            "reporter_name": "Bob", "content_title": "Reported post"}]}""",
                    )
                    "f1/-/p1/approve" -> refused()
                    "f1/-/moderation/reports/r1/resolve" -> refused()
                    "f1/-/restrict" ->
                        if (restricting) ok("""{"success": true}""") else refused()
                    "f1/-/moderation/restrictions" -> ok("""{"restrictions": []}""")
                    "f1/-/members/search" -> ok(
                        """{"members": [{"id": "m2", "name": "Zoe"}, {"id": "m1", "name": "Ann"},
                            {"id": "", "name": "Nobody"}]}""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/forums/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = ForumsRepository(retrofit.create(ForumsApi::class.java), FileStore(context), context, SessionManager(context))
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun refused() = MockResponse().setResponseCode(403).setBody("""{"error": "Not allowed to moderate"}""")

    private fun model() = ModerationViewModel(SavedStateHandle(mapOf("forumId" to "f1")), repository)

    private fun answered(done: () -> Boolean) = rule.waitUntil(5_000) {
        rule.onAllNodes(isRoot()).fetchSemanticsNodes()
        done()
    }

    /** Whether [done] comes true within the wait, for a check a regression fails on. */
    private fun within(done: () -> Boolean): Boolean = try {
        answered(done)
        true
    } catch (_: ComposeTimeoutException) {
        false
    }

    private fun shown(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** Collects what [model] reports as failed, until [done] cancels it. */
    private fun listen(model: ModerationViewModel): Pair<List<MochiError>, () -> Unit> {
        val heard = Collections.synchronizedList(mutableListOf<MochiError>())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch { model.failures.collect { heard.add(it) } }
        return heard to { scope.cancel() }
    }

    @Test
    fun `a refused approval is reported as an event and leaves the queue in place`() {
        val model = model()
        rule.setContent { }
        answered { model.uiState.value.queue != null && !model.uiState.value.isLoading }
        val (heard, done) = listen(model)
        model.approvePost("p1")
        assertTrue(within { heard.isNotEmpty() })
        done()
        assertEquals(1, heard.size)
        // THE POINT: not the failed-load state, whose Retry reloads the tab.
        assertNull(model.uiState.value.error)
        assertEquals("p1", model.uiState.value.queue?.posts?.single()?.id)
    }

    @Test
    fun `a refused action shows over the tab, which keeps its rows`() {
        val model = model()
        rule.setContent { ForumModerationScreen(onBack = {}, viewModel = model) }
        answered { shown("Waiting post") }
        rule.onNodeWithText("Approve").performClick()
        assertTrue(within { shown("Not allowed to moderate") })
        // THE POINT: the queue stays, with no failed-load page in its place.
        assertTrue(shown("Waiting post"))
        assertTrue(!shown("Retry"))
    }

    @Test
    fun `a refused report resolution is an event, not a failed load`() {
        val model = model()
        rule.setContent { }
        model.selectTab(ModerationTab.REPORTS)
        answered { model.uiState.value.reports != null && !model.uiState.value.isLoading }
        val (heard, done) = listen(model)
        model.resolveReport("r1", "ignored")
        assertTrue(within { heard.isNotEmpty() })
        done()
        assertNull(model.uiState.value.error)
        assertEquals("r1", model.uiState.value.reports?.reports?.single()?.id)
    }

    @Test
    fun `a restriction says whether the server took it`() {
        val model = model()
        rule.setContent { }
        var took: Boolean? = null
        restricting = false
        model.addRestriction("m1", "muted", "", 3_600) { took = it }
        answered { took != null }
        assertEquals(false, took)
        took = null
        restricting = true
        model.addRestriction("m1", "muted", "", 3_600) { took = it }
        answered { took != null }
        assertEquals(true, took)
        val sent = asked.last { it.path.orEmpty().endsWith("/-/restrict") }.body.readUtf8()
        assertTrue(sent, "user=m1" in sent && "duration=3600" in sent)
    }

    @Test
    fun `the member search names the forum's members in order and drops a row with no id`() {
        val model = model()
        rule.setContent { }
        var found: List<ForumMember>? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch { found = model.searchMembers("") }
        answered { found != null }
        scope.cancel()
        assertEquals(listOf("m1", "m2"), found?.map { it.id })
    }

    @Test
    fun `each restriction length is sent as its seconds, and permanent as none`() {
        assertEquals(3_600L, RestrictionDuration.HOUR.seconds)
        assertEquals(86_400L, RestrictionDuration.DAY.seconds)
        assertEquals(604_800L, RestrictionDuration.WEEK.seconds)
        assertEquals(2_592_000L, RestrictionDuration.MONTH.seconds)
        assertNull(RestrictionDuration.PERMANENT.seconds)
    }

    @Test
    fun `a restriction is for a member chosen by name, for a length chosen from the list`() {
        var sent: List<Any?>? = null
        rule.setContent {
            AddRestrictionDialog(
                onConfirm = { user, type, reason, duration -> sent = listOf(user, type, reason, duration) },
                onDismiss = {},
                onSearch = { query ->
                    listOf(ForumMember(id = "m1", name = "Ann"), ForumMember(id = "m2", name = "Zoe"))
                        .filter { it.name.contains(query, ignoreCase = true) }
                },
            )
        }
        val add = rule.onAllNodesWithText("Add restriction")
        answered { shown("Ann") }
        // Nobody chosen yet: nothing to send.
        add[1].assertIsNotEnabled()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("zo")
        answered { !shown("Ann") }
        rule.onNodeWithText("Zoe").performClick()
        rule.onNodeWithText("Permanent").performClick()
        rule.onNodeWithText("A day").performClick()
        add[1].assertIsEnabled()
        add[1].performClick()
        // THE POINT: the member's id and the length in seconds, never typed.
        assertEquals(listOf("m2", "muted", "", 86_400L), sent)
    }

    @Test
    fun `a resolved report says how it was resolved in words, not the server's code`() {
        rule.setContent {
            ReportCard(
                report = ModerationReport(id = "r1", type = "post", status = "resolved", resolverName = "Ann", resolution = "ignored"),
                forumId = "f1",
                showStatus = false,
                onResolve = {},
            )
        }
        rule.onNodeWithText("Resolved by Ann — Dismissed").assertExists()
    }
}
