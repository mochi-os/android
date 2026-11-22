// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.api.MenuApi
import org.mochios.feeds.model.Feed
import org.mochios.feeds.repository.FeedsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A feed's settings when the server cannot be read or refuses: a feed that
 * cannot be read offers a retry in place of every setting, an owner's
 * included; an AI choice the server refuses goes back to what it holds; a
 * failed prompt reset or prompt read is said; and removing or blocking a
 * member asks first and waits for the server's answer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsFailuresTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: FeedsRepository
    private lateinit var session: SessionManager

    /** Paths the server refuses with a 403. */
    @Volatile private var refused = setOf<String>()

    /** Whether the feed's information cannot be read. */
    @Volatile private var infoFails = false

    /** Holds the member removal's answer until released. */
    @Volatile private var gate = CountDownLatch(0)

    /** How many times each path was asked for. */
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                counts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
                if (path == "/feeds/f1/-/members/remove") gate.await(5, TimeUnit.SECONDS)
                if (path in refused) return MockResponse().setResponseCode(403).setBody("""{"error": "Not allowed"}""")
                return when (path) {
                    "/feeds/f1/-/info" -> if (infoFails) {
                        MockResponse().setResponseCode(500).setBody("""{"error": "Broken"}""")
                    } else {
                        ok(
                            """{"feed": {"id": "f1", "fingerprint": "f1", "name": "Feed", "ai_mode": "tag",""" +
                                """ "ai_account": "a1"}, "permissions": {"manage": true}}""",
                        )
                    }
                    "/feeds/-/accounts/list" -> MockResponse().setBody("""[{"id": "1", "label": "Model"}]""")
                    "/feeds/f1/-/members" -> ok("""{"members": [{"id": "m1", "name": "Alice"}]}""")
                    "/feeds/f1/-/access" -> ok("""{"rules": []}""")
                    else -> if (request.method == "POST") ok("{}") else MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/feeds/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        session = SessionManager(context)
        runBlocking {
            session.setServerUrl(base)
            session.setBoundAccount("a1")
        }
        repository = FeedsRepository(
            retrofit.create(FeedsApi::class.java),
            retrofit.create(MenuApi::class.java),
            FileStore(context),
            context,
            session,
        )
    }

    @After
    fun end() {
        gate.countDown()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun count(path: String): Int = counts[path]?.get() ?: 0

    /** Runs the main thread until [done]; an assertion fails if it never is. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out")
            Thread.sleep(10)
        }
    }

    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    private fun settings(): FeedSettingsViewModel {
        val model = FeedSettingsViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, session)
        until { !model.isLoading.value && (model.feedInfo.value != null || model.loadError.value != null) }
        return model
    }

    @Test
    fun `a feed that cannot be read keeps why, and the retry reads it`() {
        infoFails = true
        val model = settings()
        assertNotNull("THE POINT: the failure is kept to stand in place of the settings", model.loadError.value)
        assertNull(model.feedInfo.value)

        infoFails = false
        model.loadFeedInfo()
        until { model.feedInfo.value != null }
        assertNull(model.loadError.value)
    }

    @Test
    fun `the screen shows a retry, not the owner's settings, for a feed it cannot read`() {
        val failure = MochiError.ServerError(500, "Broken")
        assertEquals(
            "THE POINT: no tabs, Delete feed among them, for a feed that could not be read",
            SettingsBody.Failed,
            settingsBody(loading = false, feed = null, failure = failure, manage = false, offered = null),
        )
        assertEquals(SettingsBody.Loading, settingsBody(loading = true, feed = null, failure = null, manage = false, offered = null))
        assertEquals(SettingsBody.Loading, settingsBody(loading = false, feed = null, failure = null, manage = false, offered = null))
        val feed = Feed(id = "f1", name = "Feed")
        assertEquals(SettingsBody.Subscriber, settingsBody(loading = false, feed = feed, failure = null, manage = false, offered = null))
        assertEquals(SettingsBody.Loading, settingsBody(loading = false, feed = feed, failure = null, manage = true, offered = null))
        assertEquals(SettingsBody.Tabs, settingsBody(loading = false, feed = feed, failure = null, manage = true, offered = false))
    }

    @Test
    fun `an AI mode the server refuses goes back`() {
        val model = settings()
        refused = setOf("/feeds/f1/-/ai/settings")
        model.setAiMode("off")
        until { model.error.value != null }
        assertEquals("THE POINT: the mode the server holds shows again", "tag", model.aiMode.value)
    }

    @Test
    fun `an AI account the server refuses goes back`() {
        val model = settings()
        refused = setOf("/feeds/f1/-/ai/settings")
        model.setAiAccount("2")
        until { model.error.value != null }
        assertEquals("THE POINT: the account the server holds shows again", "a1", model.aiAccount.value)
    }

    @Test
    fun `a prompt reset that fails is said`() {
        val model = settings()
        refused = setOf("/feeds/f1/-/ai/prompts/set")
        model.resetAiPrompt("tag")
        until { model.error.value != null }
        assertNotNull("THE POINT: the reset's failure is said", model.error.value)
    }

    @Test
    fun `prompts that cannot be read are said`() {
        val model = settings()
        model.loadAiPrompts()
        until { model.error.value != null }
        assertNotNull("THE POINT: the prompts' failure is said", model.error.value)
    }

    @Test
    fun `groups load at once, and groups that cannot be read are said, not taken for none`() {
        val model = settings()
        refused = setOf("/feeds/-/groups")
        model.loadGroups()
        assertTrue("THE POINT: loading from the call, before the answer", model.isLoadingGroups.value)
        until { !model.isLoadingGroups.value }
        assertNotNull("THE POINT: the failure is kept for the dialog to show", model.groupsError.value)
        assertTrue(model.groups.value.isEmpty())
    }

    @Test
    fun `removing a member asks first, and the confirmation waits for the server`() {
        val model = settings()
        gate = CountDownLatch(1)
        rule.setContent { AccessTab(viewModel = model) }
        until { model.members.value.isNotEmpty() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Remove").performClick()
        rule.onNodeWithText("Remove Alice?").assertExists()
        settle()
        assertEquals("THE POINT: nothing is removed until the owner confirms", 0, count("/feeds/f1/-/members/remove"))

        rule.onNodeWithText("Remove").performClick()
        until { count("/feeds/f1/-/members/remove") == 1 }
        rule.onNodeWithText("Remove Alice?").assertExists()
        gate.countDown()
        until { model.actionMessage.value != null }
        rule.waitForIdle()
        rule.onNodeWithText("Remove Alice?").assertDoesNotExist()
    }

    @Test
    fun `a refused block leaves the confirmation open`() {
        val model = settings()
        refused = setOf("/feeds/f1/-/access/set")
        rule.setContent { AccessTab(viewModel = model) }
        until { model.members.value.isNotEmpty() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Block").performClick()
        rule.onNodeWithText("Block Alice?").assertExists()
        rule.onNodeWithText("Block").performClick()
        until { model.error.value != null }
        rule.waitForIdle()
        rule.onNodeWithText("Block Alice?").assertExists()
    }
}
