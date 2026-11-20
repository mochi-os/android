// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.newpost

import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 * Editing a post: the form is the post's only once the post has been read,
 * and saving sends only what the editor changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewPostEditTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: ForumsRepository

    /** Whether the server refuses to read the post. */
    @Volatile private var down = false

    /** Each request's path and body. */
    private val requests = ConcurrentLinkedQueue<Pair<String, String>>()

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                requests.add(path to request.body.readUtf8())
                return when (path) {
                    "/forums/f1/-/p1" ->
                        if (down) {
                            MockResponse().setResponseCode(500).setBody("""{"error": "down"}""")
                        } else {
                            ok(
                                """{"forum": {"id": "f1"}, "post": {"id": "p1", "forum": "f1", "title": "Old title", "body": "Body",""" +
                                    """ "url": "https://there.example/a", "sensitive": 1, "language": "fr",""" +
                                    """ "attachments": [{"id": "a1", "name": "one.png", "type": "image/png", "caption": ""},""" +
                                    """ {"id": "a2", "name": "two.png", "type": "image/png", "caption": ""}]}, "comments": []}""",
                            )
                        }
                    "/forums/f1/-/p1/edit" -> ok("{}")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/forums/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ForumsApi::class.java)
        repository = ForumsRepository(api, FileStore(context), context, SessionManager(context))
    }

    @After
    fun end() {
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    /** Runs the screen and the main thread until [done]; the main thread's clock moves on each turn. */
    private fun wait(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!done()) {
            rule.waitForIdle()
            shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out; requests ${requests.map { it.first }}" }
            Thread.sleep(10)
        }
    }

    private fun settle() {
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    private fun sent(path: String): String? = requests.lastOrNull { it.first == path }?.second

    private fun part(body: String, name: String): String? =
        Regex("name=\"$name\"\\r\\n(?:[^\\r\\n]+\\r\\n)*\\r\\n([^\\r]*)\\r\\n").find(body)?.groupValues?.get(1)

    private fun edit() = NewPostViewModel(SavedStateHandle(mapOf("forumId" to "f1", "postId" to "p1")), repository)

    @Test
    fun `an edit whose post failed to come sends nothing`() {
        down = true
        val model = edit()
        until { model.uiState.value.original is Original.Failed }
        model.setTitle("New title")
        model.setBody("New body")
        model.submit()
        settle()
        assertNull("THE POINT: no edit goes over a post never read", sent("/forums/f1/-/p1/edit"))
        assertFalse(model.uiState.value.postSuccess)
    }

    @Test
    fun `an edit whose post came on a retry is sent`() {
        down = true
        val model = edit()
        until { model.uiState.value.original is Original.Failed }
        down = false
        model.retry()
        until { model.uiState.value.original is Original.Loaded }
        model.setTitle("New title")
        model.submit()
        until { model.uiState.value.postSuccess }
        val body = sent("/forums/f1/-/p1/edit")!!
        assertEquals("New title", part(body, "title"))
        assertEquals("""["a1","a2"]""", part(body, "order"))
    }

    @Test
    fun `an edit sends only the marks the editor changed`() {
        val model = edit()
        until { model.uiState.value.original is Original.Loaded }
        assertTrue(model.sensitive.value)
        model.setTitle("New title")
        model.setLanguage("de")
        model.submit()
        until { model.uiState.value.postSuccess }
        val body = sent("/forums/f1/-/p1/edit")!!
        assertEquals("de", part(body, "language"))
        assertNull("THE POINT: the link as it was is not sent", part(body, "url"))
        assertNull("nor the sensitive mark", part(body, "sensitive"))
    }

    @Test
    fun `an edit whose post failed to come shows the failure with a retry in place of the form`() {
        down = true
        val model = edit()
        rule.setContent { NewPostScreen(onBack = {}, onPostCreated = { _, _ -> }, viewModel = model) }
        wait { model.uiState.value.original is Original.Failed }
        rule.waitForIdle()
        rule.onAllNodesWithText(context.getString(org.mochios.forums.R.string.forums_post_title_field)).assertCountEquals(0)
        model.setTitle("New title")
        model.setBody("New body")
        rule.waitForIdle()
        rule.onNode(hasText(context.getString(MochiR.string.common_save)) and hasClickAction()).assertIsNotEnabled()
        down = false
        rule.onNodeWithText(context.getString(MochiR.string.common_retry)).performClick()
        wait { model.uiState.value.original is Original.Loaded }
        rule.waitForIdle()
        rule.onAllNodesWithText(context.getString(org.mochios.forums.R.string.forums_post_title_field)).assertCountEquals(1)
    }
}
