// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.post

import android.os.Looper
import android.text.Selection
import android.text.Spannable
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.android.ui.components.ClickableLinkTextView
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.forums.R
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
 * A forum post on screen against its server: what a refused action, a sent
 * comment and an edit leave behind, and what asks before it acts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PostScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var repository: ForumsRepository

    /** Whether the server refuses every write. */
    @Volatile private var refuse = false

    /** The thread's comments, as the server writes them. */
    @Volatile private var comments = """[{"id": "c1", "member": "m2", "name": "Ann", "body": "First comment", "can_vote": true, "can_comment": true}]"""

    /** The post's attachments, as the server writes them. */
    @Volatile private var attachments = "[]"

    /** Each write the server has been sent, by path. */
    private val writes = ConcurrentLinkedQueue<String>()

    /** Every request the server has been sent, as its method and path. */
    private val requests = ConcurrentLinkedQueue<String>()

    /** The server's end of the forum's websocket, once the client has connected. */
    @Volatile private var live: WebSocket? = null

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                requests.add("${request.method} $path")
                if (path == "/_/websocket") {
                    return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            live = webSocket
                        }

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }
                    })
                }
                if (path == "/forums/f1/-/p1") {
                    return ok(
                        """{"forum": {"id": "f1", "fingerprint": "fp1", "name": "Forum"},""" +
                            """ "post": {"id": "p1", "forum": "f1", "member": "m1", "name": "Bo", "title": "A thread", "body": "Words", "status": "approved", "attachments": $attachments},""" +
                            """ "comments": $comments, "can_vote": true, "can_comment": true, "can_manage": true, "can_moderate": true}""",
                    )
                }
                if (request.method == "POST") {
                    writes.add(path)
                    return if (refuse) {
                        MockResponse().setResponseCode(400).setBody("""{"error": "Refused by the forum"}""")
                    } else {
                        ok("""{"id": "c9", "forum": "f1", "post": "p1"}""")
                    }
                }
                return MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val api = Retrofit.Builder()
            .baseUrl(server.url("/forums/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ForumsApi::class.java)
        val session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        repository = ForumsRepository(api, FileStore(context), context, session)
        this.session = session
    }

    private lateinit var session: SessionManager

    @After
    fun end() {
        socket.disconnectAll()
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

    private fun text(id: Int) = context.getString(id)

    private fun model(comment: String? = null) = PostViewModel(
        SavedStateHandle(mapOf("forumId" to "f1", "postId" to "p1", "comment" to comment)),
        repository,
        session,
        socket,
    )

    /** The post on screen, its thread read. */
    private fun show(model: PostViewModel = model()): PostViewModel {
        rule.setContent { PostScreen(onBack = {}, onEditPost = { _, _ -> }, viewModel = model) }
        wait { model.uiState.value.post.id == "p1" }
        rule.waitForIdle()
        return model
    }

    /** Waits for [path] to reach the server, then for its answer to be acted on. */
    private fun sent(path: String) {
        val deadline = System.currentTimeMillis() + 15_000
        while (path !in writes) {
            rule.waitForIdle()
            shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            if (System.currentTimeMillis() > deadline) throw AssertionError("$path never came; the server had $requests")
            Thread.sleep(10)
        }
        repeat(10) {
            rule.waitForIdle()
            shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    /** Opens a menu: [COMMENT]'s, or the post's in the top bar, [POST]. */
    private fun overflow(index: Int) {
        rule.onAllNodesWithContentDescription(context.getString(MochiR.string.common_more_options))[index].performClick()
        rule.waitForIdle()
    }

    private fun compose(words: String) {
        rule.onNode(hasSetTextAction()).performTextInput(words)
        rule.onNodeWithContentDescription(text(R.string.forums_comment_send)).performClick()
    }

    @Test
    fun `a comment the server refuses keeps its words, and says why over the post`() {
        val model = show()
        refuse = true
        compose("My reply")
        sent("/forums/f1/-/p1/create")
        wait { !model.uiState.value.isSending }
        rule.waitForIdle()
        rule.onAllNodesWithText("My reply").assertCountEquals(1)
        rule.onAllNodesWithText("Refused by the forum").assertCountEquals(1)
        rule.onAllNodesWithText("A thread").assertCountEquals(1)
    }

    @Test
    fun `a comment the server takes empties the composer`() {
        val model = show()
        compose("My reply")
        sent("/forums/f1/-/p1/create")
        wait { !model.uiState.value.isSending }
        rule.waitForIdle()
        rule.onAllNodesWithText("My reply").assertCountEquals(0)
    }

    /** The thread's one comment is about the post's image, and opens it. */
    private fun anchored() {
        attachments = """[{"id": "a1", "name": "photo.png", "type": "image/png", "caption": ""}]"""
        comments = """[{"id": "c1", "member": "m2", "name": "Ann", "body": "About the photo", "attachment": "a1", "can_vote": true, "can_comment": true}]"""
    }

    /** Opens the image's lightbox from the comment about it, and sends [words] from its panel. */
    private fun lightbox(words: String) {
        // The image fills the screen above the thread: the comment is below it.
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(MochiR.string.comment_anchor_open)).performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasSetTextAction()).onLast().performTextInput(words)
        rule.onAllNodesWithContentDescription(text(R.string.forums_comment_send)).onLast().performClick()
        sent("/forums/f1/-/p1/create")
    }

    @Test
    fun `a comment on an image the server refuses keeps its words in the lightbox`() {
        anchored()
        val model = show()
        refuse = true
        lightbox("On the photo")
        wait { !model.uiState.value.isSending }
        rule.waitForIdle()
        rule.onAllNodesWithText("On the photo").assertCountEquals(1)
    }

    @Test
    fun `a comment on an image the server takes empties the lightbox's composer`() {
        anchored()
        val model = show()
        lightbox("On the photo")
        wait { !model.uiState.value.isSending }
        rule.waitForIdle()
        rule.onAllNodesWithText("On the photo").assertCountEquals(0)
    }

    /** Every window's views: the screen's, and a dialog's such as the lightbox. */
    private fun windows(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val views = global.getDeclaredField("mViews").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return (views.get(instance) as List<View>).toList()
    }

    private fun bodies(view: View): List<ClickableLinkTextView> = when (view) {
        is ClickableLinkTextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { bodies(view.getChildAt(it)) }
        else -> emptyList()
    }

    /** The bodies reading [words], in the order their windows opened. */
    private fun bodies(words: String): List<ClickableLinkTextView> =
        windows().flatMap { window -> bodies(window) }.filter { body -> body.text.toString() == words }

    @Test
    fun `Reply with text selected in a comment quotes it in the composer`() {
        show()
        val body = bodies("First comment").single()
        rule.runOnUiThread { Selection.setSelection(body.text as Spannable, 0, 5) }
        rule.onNodeWithContentDescription(text(R.string.forums_comment_reply)).performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).assert(hasText("> First\n\n"))
    }

    @Test
    fun `Reply quotes text selected anywhere on the page, the post's body included`() {
        show()
        val body = bodies("Words").single()
        rule.runOnUiThread { Selection.setSelection(body.text as Spannable, 0, 5) }
        rule.onNodeWithContentDescription(text(R.string.forums_comment_reply)).performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).assert(hasText("> Words\n\n"))
    }

    @Test
    fun `a second Reply replaces the first's quote rather than stacking another`() {
        show()
        val body = bodies("First comment").single()
        rule.runOnUiThread { Selection.setSelection(body.text as Spannable, 0, 5) }
        rule.onNodeWithContentDescription(text(R.string.forums_comment_reply)).performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(text(R.string.forums_comment_reply)).performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).assert(hasText("> First\n\n"))
    }

    @Test
    fun `Reply in a comment's selection menu quotes the selection in the composer`() {
        show()
        val action = bodies("First comment").single().selectionAction
        assertNotNull("the body's selection menu offers Reply", action)
        rule.runOnUiThread { action!!.onSelected("First") }
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).assert(hasText("> First\n\n"))
    }

    @Test
    fun `Reply in the lightbox quotes the selection in the lightbox's composer`() {
        anchored()
        show()
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(MochiR.string.comment_anchor_open)).performClick()
        rule.waitForIdle()
        val action = bodies("About the photo").last().selectionAction
        assertNotNull("the lightbox comment's selection menu offers Reply", action)
        rule.runOnUiThread { action!!.onSelected("About") }
        rule.waitForIdle()
        rule.onAllNodes(hasSetTextAction()).onLast().assert(hasText("> About\n\n"))
    }

    /** Opens the comment's edit box and saves [words] in it. */
    private fun edit(words: String) {
        overflow(COMMENT)
        rule.onNodeWithText(text(R.string.forums_comment_edit)).performClick()
        rule.onNode(hasSetTextAction() and hasText("First comment")).performTextReplacement(words)
        rule.onNodeWithText(context.getString(MochiR.string.common_save)).performClick()
        sent("/forums/f1/-/p1/c1/edit")
    }

    @Test
    fun `an edit the server refuses leaves the box open with what was typed`() {
        show()
        refuse = true
        edit("Edited words")
        rule.onAllNodesWithText("Edited words").assertCountEquals(1)
        rule.onAllNodesWithText(context.getString(MochiR.string.common_save)).assertCountEquals(1)
        rule.onAllNodesWithText("Refused by the forum").assertCountEquals(1)
    }

    @Test
    fun `an edit the server takes closes the box`() {
        show()
        edit("Edited words")
        rule.onAllNodesWithText(context.getString(MochiR.string.common_save)).assertCountEquals(0)
    }

    @Test
    fun `removing the post asks first`() {
        show()
        overflow(POST)
        rule.onNodeWithText(text(R.string.forums_post_remove)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(text(R.string.forums_post_remove_title)).assertCountEquals(1)
        assertFalse("nothing is sent before the answer", "/forums/f1/-/p1/remove" in writes)
        rule.onNodeWithText(text(R.string.forums_post_remove)).performClick()
        sent("/forums/f1/-/p1/remove")
    }

    @Test
    fun `removing a comment asks first`() {
        show()
        overflow(COMMENT)
        rule.onNodeWithText(text(R.string.forums_comment_remove)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(text(R.string.forums_comment_remove_title)).assertCountEquals(1)
        assertFalse("nothing is sent before the answer", "/forums/f1/-/p1/c1/remove" in writes)
        rule.onNodeWithText(text(R.string.forums_comment_remove)).performClick()
        sent("/forums/f1/-/p1/c1/remove")
    }

    @Test
    fun `a report the server takes says so`() {
        show()
        overflow(POST)
        rule.onNodeWithText(text(R.string.forums_post_report)).performClick()
        rule.onNodeWithText(text(R.string.forums_report_submit)).performClick()
        sent("/forums/f1/-/p1/report")
        rule.onAllNodesWithText(text(R.string.forums_report_submitted)).assertCountEquals(1)
    }

    @Test
    fun `a refused vote is said over the post`() {
        val model = show()
        refuse = true
        model.votePost("up")
        sent("/forums/f1/-/p1/vote/up")
        rule.onAllNodesWithText("Refused by the forum").assertCountEquals(1)
        assertNull("the post stays", model.uiState.value.error)
    }

    /** Runs the main thread until [done], without a screen. */
    private fun idle(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `a thread repeating a comment's id, or naming none, shows each comment once`() {
        comments = """[{"id": "c1", "body": "One"}, {"id": "c1", "body": "Again"}, {"id": "", "body": "None"},""" +
            """ {"id": "c2", "body": "Two", "children": [{"id": "c1", "body": "Nested"}]}]"""
        val model = model()
        idle { model.uiState.value.post.id == "p1" }
        assertEquals(listOf("c1", "c2"), model.uiState.value.comments.map { comment -> comment.id })
        assertTrue(model.uiState.value.comments[1].children.isEmpty())
    }

    @Test
    fun `a comment named like the screen's own rows is drawn beside them`() {
        comments = """[{"id": "all_comments", "member": "m2", "name": "Ann", "body": "Named oddly"}, {"id": "c2", "name": "Cy", "body": "Two"}]"""
        try {
            show(model(comment = "all_comments"))
        } catch (e: IllegalArgumentException) {
            fail("the thread did not draw: ${e.message}")
        }
        rule.onAllNodesWithText("Ann").assertCountEquals(1)
    }

    @Test
    fun `a frame is handled on the main thread, not the socket's`() {
        val model = model()
        idle { model.uiState.value.post.id == "p1" && live != null }
        live!!.send("""{"type": "forum/removed", "forum": "f1"}""")
        // The frame reaches the socket's thread meanwhile; the main one is not run.
        Thread.sleep(500)
        assertNull("THE POINT: nothing changed off the main thread", model.uiState.value.gone)
        idle { model.uiState.value.gone != null }
    }

    private companion object {
        /** The menus in the order the screen's tree lists them: the thread's, then the top bar's. */
        const val COMMENT = 0
        const val POST = 1
    }
}
