// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.post

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.api.MenuApi
import org.mochios.feeds.model.Post
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

/**
 * An edit opens before the post it edits has loaded. Its form and its Save
 * wait for the post, so an early save cannot replace the post's text, and the
 * form's defaults cannot reset its visibility, warning or sensitive flag; a
 * load that fails offers a retry in place of the form; and a save sends only
 * the features the user changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditLoadTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: FeedsRepository

    /** Holds the post's answer until released. */
    @Volatile private var gate = CountDownLatch(0)

    /** How many reads of the post fail before one answers. */
    @Volatile private var failing = 0

    /** Whether the feeds a post may go to cannot be read. */
    @Volatile private var feedsFail = false

    /** Each write's body, by path. */
    private val writes = ConcurrentHashMap<String, String>()

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                if (request.method == "POST") writes[path] = request.body.readUtf8()
                return when (path) {
                    "/feeds/f1/-/post/new" -> if (feedsFail) {
                        MockResponse().setResponseCode(500).setBody("""{"error": "Broken"}""")
                    } else {
                        ok("""{"feeds": [{"id": "f1", "fingerprint": "f1", "name": "Feed", "owner": 1}]}""")
                    }
                    "/feeds/f1/-/p1" -> {
                        gate.await(5, TimeUnit.SECONDS)
                        if (failing > 0) {
                            failing--
                            MockResponse().setResponseCode(500).setBody("""{"error": "Broken"}""")
                        } else {
                            ok(
                                """{"posts": [{"id": "p1", "feed": "f1", "body": "Stored text", "visibility": "followers",""" +
                                    """ "warning": "Spoilers", "sensitive": 1}], "permissions": {"manage": true}}""",
                            )
                        }
                    }
                    "/feeds/f1/-/p1/edit" -> ok("""{"success": true}""")
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
        val session = SessionManager(context)
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

    private fun edit() = CreatePostViewModel(SavedStateHandle(mapOf("feedId" to "f1", "postId" to "p1")), repository, context)

    /** The value of the multipart [body]'s part [name], or null when it has none. */
    private fun part(body: String, name: String): String? {
        val start = body.indexOf("name=\"$name\"").takeIf { it >= 0 } ?: return null
        val value = body.indexOf("\r\n\r\n", start) + 4
        return body.substring(value, body.indexOf("\r\n--", value))
    }

    @Test
    fun `a save before the post has loaded sends nothing`() {
        gate = CountDownLatch(1)
        val model = edit()
        settle()
        assertEquals(EditState.Loading, model.edit.value)
        model.setBody("Typed early")
        model.createPost()
        settle()
        assertNull("THE POINT: nothing replaces the post before it has loaded", writes["/feeds/f1/-/p1/edit"])

        gate.countDown()
        until { model.edit.value is EditState.Loaded }
        assertEquals("Stored text", model.body.value)
    }

    @Test
    fun `the form and its Save wait for the post`() {
        gate = CountDownLatch(1)
        val model = edit()
        rule.setContent { CreatePostScreen(onNavigateBack = {}, viewModel = model) }
        rule.waitForIdle()
        rule.onNodeWithText("Save").assertDoesNotExist()

        gate.countDown()
        until { model.edit.value is EditState.Loaded }
        rule.waitForIdle()
        rule.onNodeWithText("Save").assertExists()
    }

    @Test
    fun `a load that fails is shown with a retry, which loads it`() {
        failing = 1
        val model = edit()
        until { model.edit.value !is EditState.Loading }
        assertTrue("THE POINT: the failure stands in place of the form", model.edit.value is EditState.Failed)
        model.loadExistingPost()
        until { model.edit.value is EditState.Loaded }
        assertEquals("Stored text", model.body.value)
    }

    @Test
    fun `a save sends only the features the user changed`() {
        val model = edit()
        until { model.edit.value is EditState.Loaded }
        until { model.availableFeeds.value.isNotEmpty() }
        model.setWarning("Changed")
        model.createPost()
        until { model.postSuccess.value }
        val body = writes.getValue("/feeds/f1/-/p1/edit")
        assertNull("THE POINT: an untouched visibility is not sent", part(body, "visibility"))
        assertNull("nor an untouched sensitive flag", part(body, "sensitive"))
        assertEquals("Changed", part(body, "warning"))
    }

    @Test
    fun `the features an edit sends are those changed from the loaded post`() {
        val post = Post(id = "p1", visibility = "followers", warning = "Spoilers", sensitive = 1)
        val same = editedFeatures(post, "followers", " Spoilers ", true)
        assertNull(same.visibility)
        assertNull(same.warning)
        assertNull(same.sensitive)
        val changed = editedFeatures(post, "public", "", false)
        assertEquals("public", changed.visibility)
        assertEquals("", changed.warning)
        assertEquals(false, changed.sensitive)
        assertEquals("a post with no visibility stored is public", null, editedFeatures(Post(id = "p2"), "public", "", false).visibility)
    }

    @Test
    fun `feeds a new post could go to that cannot be read are said`() {
        feedsFail = true
        val model = CreatePostViewModel(SavedStateHandle(mapOf("feedId" to "f1")), repository, context)
        until { model.error.value != null }
        assertNotNull("THE POINT: the composer says why its feed menu is empty", model.error.value)
    }
}
