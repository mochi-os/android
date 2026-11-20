// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forum

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.runBlocking
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
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.model.Post
import org.mochios.forums.repository.ForumsRepository
import org.mochios.forums.ui.forumlist.ForumListViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** What a forum's card shows of a post, and what the drawer beside the forum reads as it comes back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForumScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var server: MockWebServer

    /** The forum's name in the roster. */
    @Volatile private var name = "Forum"

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().startsWith("/forums/-/information")) {
                    MockResponse().setBody("""{"data": {"forums": [{"id": "f1", "fingerprint": "fp1", "name": "$name"}], "settings": {"sort": ""}}}""")
                } else {
                    MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    @After
    fun end() {
        server.shutdown()
    }

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

    /** The text of every platform text view on screen. */
    private fun views(view: View = rule.activity.window.decorView): List<String> = when (view) {
        is TextView -> listOf(view.text.toString().trim())
        is ViewGroup -> (0 until view.childCount).flatMap { index -> views(view.getChildAt(index)) }
        else -> emptyList()
    }

    @Test
    fun `a card draws a post's text from its markdown, not as the author typed it`() {
        val post = Post(id = "p1", body = "Some **bold** words", bodyMarkdown = "<p>Some <strong>bold</strong> words</p>")
        rule.setContent { PostPreview(post = post, onClick = {}) }
        rule.waitForIdle()
        rule.onAllNodesWithText("Some **bold** words").assertCountEquals(0)
        assertTrue("THE POINT: drawn as text: ${views()}", "Some bold words" in views())
    }

    /** A lifecycle the test moves by hand, standing in for the screen's back stack entry. */
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun `the drawer reads its forums again when the screen comes back`() {
        val context = rule.activity
        val api = Retrofit.Builder()
            .baseUrl(server.url("/forums/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ForumsApi::class.java)
        val session = SessionManager(context)
        runBlocking {
            session.setServerUrl(server.url("/").toString().trimEnd('/'))
            session.setBoundAccount("a1")
        }
        val drawer = ForumListViewModel(ForumsRepository(api, FileStore(context), context, session))
        val owner = Owner()
        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ForumScreen(
                    forumId = "",
                    onSelectForum = {},
                    onPostClick = { _, _ -> },
                    onNewPost = {},
                    onFindForums = {},
                    onCreateForum = {},
                    onSettings = {},
                    onOpenNotifications = {},
                    listViewModel = drawer,
                )
            }
        }
        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        wait { drawer.uiState.value.forums.isNotEmpty() }
        assertEquals(listOf("Forum"), drawer.uiState.value.forums.map { forum -> forum.name })
        // Renamed on another device while a post covered the forum.
        name = "Renamed"
        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        repeat(20) {
            rule.waitForIdle()
            Thread.sleep(20)
        }
        assertEquals("THE POINT: the drawer shows the new name", listOf("Renamed"), drawer.uiState.value.forums.map { forum -> forum.name })
    }
}
