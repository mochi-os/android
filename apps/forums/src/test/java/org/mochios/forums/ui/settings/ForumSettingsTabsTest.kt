// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
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
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.repository.ForumsRepository
import org.mochios.forums.ui.moderation.ModerationViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The forum settings tabs against a server: the AI tab holds the account the
 * forum has, a member's removal or block asks first, an emptied number box is
 * not saved, and a refused community name stays in its field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForumSettingsTabsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: ForumsRepository
    private val asked = ConcurrentLinkedQueue<RecordedRequest>()

    /** What `ai/prompts/get` answers. */
    @Volatile private var prompts = """{"prompts": {}, "defaults": {"tag": "T"}, "mode": "tag", "account": "a2"}"""

    /** Holds the AI tab's answer until released. */
    @Volatile private var reading: CountDownLatch? = null

    /** Whether the server takes a member's removal. */
    @Volatile private var removing = true

    /** Holds the removal's answer until released. */
    @Volatile private var removal: CountDownLatch? = null

    /** Whether the server takes a community name. */
    @Volatile private var naming = false

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked.add(request)
                return when (request.path.orEmpty().substringAfter("/forums/").substringBefore("?")) {
                    // The row as the owner is shown it: no account.
                    "f1/-/information" -> ok(
                        """{"forum": {"id": "f1", "name": "Books", "ai_mode": "tag"},
                            "permissions": {"manage": true, "post": true, "view": true}}""",
                    )
                    "-/accounts/list" -> MockResponse().setBody(
                        """[{"id": "a1", "label": "First"}, {"id": "a2", "label": "Second"}]""",
                    )
                    "f1/-/ai/prompts/get" -> {
                        reading?.await(10, TimeUnit.SECONDS)
                        ok(prompts)
                    }
                    "f1/-/ai/settings" -> ok("""{"ok": true}""")
                    "f1/-/access" -> ok("""{"access": [], "levels": ["view", "vote", "comment", "post", "moderate"]}""")
                    "f1/-/members" -> ok("""{"members": [{"id": "m1", "name": "Ann"}]}""")
                    "f1/-/members/save" -> {
                        removal?.await(10, TimeUnit.SECONDS)
                        if (removing) ok("""{"success": true}""") else refused()
                    }
                    "f1/-/access/set" -> ok("""{"success": true}""")
                    "f1/-/moderation/queue" -> ok("""{"posts": [], "comments": []}""")
                    "f1/-/moderation/settings" -> ok(
                        """{"settings": {"post_limit": 10, "comment_limit": 5, "limit_window": 3600}}""",
                    )
                    "f1/-/moderation/settings/save" -> ok("""{"success": true}""")
                    "f1/-/publish" -> ok(
                        """{"published": false, "offered": true, "domains": ["example.com"]}""",
                    )
                    "f1/-/publish/set" ->
                        if (naming) {
                            ok("""{"published": true, "offered": true, "identifier": "@books@example.com",
                                "address": "https://example.com/~f1", "domains": ["example.com"]}""")
                        } else {
                            MockResponse().setResponseCode(400).setBody("""{"error": "That name is taken"}""")
                        }
                    "f1/-/publish/clear" -> ok(
                        """{"published": false, "offered": true, "domains": ["example.com"]}""",
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
    fun end() {
        // A held answer let go, or the server waits on it as it shuts down.
        reading?.countDown()
        removal?.countDown()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun refused() = MockResponse().setResponseCode(403).setBody("""{"error": "Not allowed"}""")

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

    private fun sent(path: String) = asked.filter { it.path.orEmpty().substringBefore("?").endsWith(path) }

    private fun settings(content: @Composable (ForumSettingsViewModel) -> Unit = {}): ForumSettingsViewModel {
        val model = ForumSettingsViewModel(SavedStateHandle(mapOf("forumId" to "f1")), repository)
        rule.setContent { content(model) }
        answered { model.uiState.value.forum.id == "f1" && model.uiState.value.aiAccounts.isNotEmpty() }
        return model
    }

    @Test
    fun `groups load at once, and groups that cannot be read are said, not taken for none`() {
        val model = settings()
        model.loadGroups()
        assertTrue("THE POINT: loading from the call, before the answer", model.uiState.value.isLoadingGroups)
        answered { !model.uiState.value.isLoadingGroups }
        // The server answers no groups route here, so the read fails.
        assertTrue("THE POINT: the failure is kept for the dialog to show", model.uiState.value.groupsError != null)
        assertTrue(model.uiState.value.groups.isEmpty())
    }

    @Test
    fun `the AI tab shows the account the forum holds, and a mode change keeps it`() {
        settings { AiTab(it) }
        // THE POINT: the owner's own answer names the account; the row has none.
        assertTrue(within { shown("Second") })
        rule.onNodeWithText("Tag").performClick()
        rule.onNodeWithText("Disabled").performClick()
        answered { sent("/ai/settings").isNotEmpty() }
        val body = sent("/ai/settings").single().body.readUtf8()
        assertTrue(body, "mode=&" in "$body&" && "account=a2" in body)
    }

    @Test
    fun `the AI tab offers no choice until the forum's own settings arrive`() {
        val held = CountDownLatch(1)
        reading = held
        settings { AiTab(it) }
        rule.mainClock.advanceTimeBy(1_000)
        answered { true }
        // THE POINT: a mode chosen now would be sent with an account not yet known.
        assertFalse(shown("AI actions on posts"))
        held.countDown()
        assertTrue(within { shown("Second") })
    }

    @Test
    fun `the AI tab takes the forum row's values from a server that does not send its own`() {
        prompts = """{"prompts": {}, "defaults": {"tag": "T"}}"""
        val model = settings()
        model.loadAiPrompts()
        answered { model.uiState.value.aiSettings != null }
        assertEquals("tag", model.uiState.value.aiSettings?.mode)
        assertEquals("", model.uiState.value.aiSettings?.account)
    }

    @Test
    fun `removing a member asks first, and the dialog waits for the server`() {
        val held = CountDownLatch(1)
        removal = held
        settings { AccessTab(it) }
        answered { shown("Ann") }
        rule.onNodeWithContentDescription("Remove").performClick()
        // THE POINT: nothing is sent until the removal is confirmed.
        assertTrue(within { shown("Remove Ann?") })
        assertTrue(shown("Their votes in this forum are deleted."))
        assertTrue(sent("/members/save").isEmpty())
        rule.onNodeWithText("Remove").performClick()
        answered { sent("/members/save").isNotEmpty() }
        // Still open while the server has not answered.
        assertTrue(shown("Remove Ann?"))
        held.countDown()
        answered { !shown("Remove Ann?") }
    }

    @Test
    fun `a refused removal leaves its confirmation open`() {
        removing = false
        val model = settings { AccessTab(it) }
        answered { shown("Ann") }
        rule.onNodeWithContentDescription("Remove").performClick()
        answered { shown("Remove Ann?") }
        rule.onNodeWithText("Remove").performClick()
        answered { model.uiState.value.error != null }
        assertTrue(shown("Remove Ann?"))
    }

    @Test
    fun `blocking a member asks first, then denies them every level`() {
        settings { AccessTab(it) }
        answered { shown("Ann") }
        rule.onNodeWithContentDescription("Block").performClick()
        assertTrue(within { shown("Block Ann?") })
        assertTrue(sent("/access/set").isEmpty())
        rule.onNodeWithText("Block").performClick()
        answered { sent("/access/set").isNotEmpty() }
        val body = sent("/access/set").single().body.readUtf8()
        assertTrue(body, "level=none" in body)
        answered { !shown("Block Ann?") }
    }

    private fun moderation(): ModerationViewModel {
        val model = ModerationViewModel(SavedStateHandle(mapOf("forumId" to "f1")), repository)
        rule.setContent { ModerationTab(onMessage = {}, onError = {}, viewModel = model) }
        answered { shown("10") }
        return model
    }

    /** The post limit's box: the boxes are, in order, posts, comments and the window. */
    private fun postLimit() = rule.onAllNodes(hasSetTextAction())[0]

    @Test
    fun `an emptied number box is not saved, and a number typed into it is`() {
        moderation()
        postLimit().performTextReplacement("")
        rule.mainClock.advanceTimeBy(2_000)
        answered { true }
        // THE POINT: an empty box is no limit of 0.
        assertTrue(sent("/moderation/settings/save").isEmpty())
        postLimit().performTextInput("25")
        answered { sent("/moderation/settings/save").isNotEmpty() }
        val body = sent("/moderation/settings/save").single().body.readUtf8()
        assertTrue(body, "post_limit=25" in body)
    }

    @Test
    fun `the keyboard's Done saves a box at once`() {
        moderation()
        // The clock stands still, so the pause's save cannot be the one sent.
        rule.mainClock.autoAdvance = false
        postLimit().performTextReplacement("30")
        postLimit().performImeAction()
        assertTrue(within { sent("/moderation/settings/save").isNotEmpty() })
        rule.mainClock.autoAdvance = true
        val body = sent("/moderation/settings/save").first().body.readUtf8()
        assertTrue(body, "post_limit=30" in body)
    }

    @Test
    fun `a switch flipped while a box is empty saves the numbers as they were and leaves the box empty`() {
        moderation()
        postLimit().performTextReplacement("")
        // The first switch: new posts wait for approval.
        rule.onAllNodes(isToggleable())[0].performClick()
        answered { sent("/moderation/settings/save").isNotEmpty() }
        val body = sent("/moderation/settings/save").single().body.readUtf8()
        assertTrue(body, "post_limit=10" in body && "moderation_posts=true" in body)
        rule.mainClock.advanceTimeBy(2_000)
        answered { true }
        // THE POINT: the reseed after the save does not write under the cursor.
        assertFalse(shown("10"))
    }

    private fun community(): CommunityViewModel {
        val model = CommunityViewModel(SavedStateHandle(mapOf("forumId" to "f1")), repository)
        model.load(sensitive = false)
        rule.setContent { CommunitySection(model) }
        answered { shown("Community name") }
        return model
    }

    @Test
    fun `a refused community name stays in its field`() {
        val model = community()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("books")
        rule.onNodeWithText("Publish").performClick()
        answered { sent("/publish/set").isNotEmpty() && !model.busy.value }
        // THE POINT: the owner changes one letter rather than typing it again.
        assertTrue(shown("books"))
    }

    @Test
    fun `a published name leaves the field, so a withdrawn community offers an empty one`() {
        naming = true
        val model = community()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("books")
        rule.onNodeWithText("Publish").performClick()
        answered { shown("@books@example.com") }
        rule.onNodeWithText("Withdraw").performClick()
        answered { shown("Community name") && !model.busy.value }
        assertFalse(shown("books"))
    }
}
