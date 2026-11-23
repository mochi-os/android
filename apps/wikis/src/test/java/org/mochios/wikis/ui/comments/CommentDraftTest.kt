// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.comments

import android.net.Uri
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
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
import org.mochios.android.api.userMessage
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.format.formatFingerprint
import org.mochios.android.i18n.AppContext
import org.mochios.android.util.fingerprint
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.wikis.api.WikisApi
import org.mochios.wikis.model.WikiComment
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.WikiContextValue
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A comment, and its edit, keep what was written until the server has them:
 * a refused send leaves the text and the picked files in the composer, and a
 * refused edit leaves the edit box open. While a comment is on its way the bar
 * takes no second send, and Send asks for text whatever files are picked, as
 * the server requires a body.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CommentDraftTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var repository: WikisRepository

    /** Whether the server refuses the comments and edits it is sent. */
    @Volatile private var refuse = true

    /** Holds each write until released, so a test sees the bar while it is on its way. */
    @Volatile private var gate = CountDownLatch(0)

    /** An identity's entity id, which means nothing to a reader. */
    private val author = "1aBcDeFgHiJkLmNoPqRsTuVwXyZ23456789aBcDeFgHiJkLmNo"

    /** The page's thread: one comment, by an author the server could not name. */
    private val thread = """{"comments": [{"id": "c1", "author": "$author", "name": "", "body": "First"}]}"""

    private val jobs = mutableListOf<Job>()

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?")) {
                    "/wikis/w1/-/info" -> ok("""{"wiki": {"id": "w1", "fingerprint": "wfp", "name": "Wiki"}, "permissions": {"edit": true}}""")
                    "/wikis/w1/-/pages/home" -> ok("""{"page": {"id": "p1", "page": "home", "title": "Home", "content": "Text"}}""")
                    "/wikis/w1/-/pages/home/comments" -> ok(thread)
                    "/wikis/w1/-/pages/home/comment/create", "/wikis/w1/-/pages/home/comment/edit" -> {
                        gate.await(5, TimeUnit.SECONDS)
                        if (refuse) {
                            MockResponse().setResponseCode(413).setBody("""{"error": "Files too large"}""")
                        } else {
                            ok("""{"id": "c1", "body": "Hello"}""")
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val api = Retrofit.Builder()
            .baseUrl(server.url("/wikis/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WikisApi::class.java)
        session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        repository = WikisRepository(api, FileStore(context), context)
    }

    @After
    fun end() {
        gate.countDown()
        jobs.forEach { job -> job.cancel() }
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun picked(name: String): Uri {
        val file = File(context.filesDir, name)
        file.writeText("bytes of $name")
        return Uri.fromFile(file)
    }

    private fun model(): Pair<CommentsViewModel, MutableList<CommentsEvent>> {
        val model = CommentsViewModel(SavedStateHandle(mapOf("wikiId" to "w1", "page" to "home")), repository, session, socket)
        val events = mutableListOf<CommentsEvent>()
        jobs += CoroutineScope(Dispatchers.Main).launch { model.events.collect { event -> events += event } }
        until { !model.uiState.value.isLoading }
        return model to events
    }

    @Test
    fun `a refused comment keeps its picked files and is not cleared, and an accepted one is`() {
        val (model, events) = model()
        val file = picked("plan.pdf")
        model.addFiles(listOf(file))
        var sent = 0

        model.createComment("Hello", onSent = { sent++ })
        until { events.isNotEmpty() && !model.uiState.value.isSending }
        assertEquals("Files too large", (events.single() as CommentsEvent.Error).error.userMessage())
        assertEquals("THE POINT: the refused comment's files are still picked", listOf(file), model.uiState.value.files)
        assertEquals("THE POINT: the composer is not told to clear", 0, sent)

        refuse = false
        model.createComment("Hello", onSent = { sent++ })
        until { sent == 1 && !model.uiState.value.isSending }
        assertTrue(model.uiState.value.files.isEmpty())
    }

    @Test
    fun `a comment on its way marks the bar as sending and takes no second send`() {
        val (model, _) = model()
        refuse = false
        gate = CountDownLatch(1)
        var sent = 0
        model.createComment("Hello", onSent = { sent++ })
        assertTrue("THE POINT: sending from the tap", model.uiState.value.isSending)
        model.createComment("Hello", onSent = { sent++ })
        gate.countDown()
        until { !model.uiState.value.isSending }
        until { sent == 1 }
        assertEquals("one comment went, not two", 1, sentRequests())
    }

    /** How many comments the server was sent. */
    private fun sentRequests(): Int {
        var sent = 0
        while (true) {
            val request = server.takeRequest(0, TimeUnit.MILLISECONDS) ?: return sent
            if (request.path.orEmpty().endsWith("/comment/create")) sent++
        }
    }

    @Test
    fun `a refused edit keeps the edit box open, and an accepted one closes it`() {
        val (model, events) = model()
        var saved = 0
        model.editComment("c1", "Better", onSaved = { saved++ })
        until { events.isNotEmpty() }
        assertEquals("THE POINT: a refused edit is not saved", 0, saved)
        refuse = false
        model.editComment("c1", "Better", onSaved = { saved++ })
        until { saved == 1 }
    }

    @Test
    fun `a reply to an author with no name names them by their fingerprint`() {
        val (model, _) = model()
        rule.setContent { CommentsScreen(navController = rememberNavController(), viewModel = model) }
        model.requestStartReply("c1")
        rule.waitForIdle()
        rule.onNodeWithText("Replying to ${formatFingerprint(fingerprint(author))}").assertExists()
        assertTrue(rule.onAllNodesWithText(author.take(8), substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `an edit keeps its box open until the server has it`() {
        var saved: (() -> Unit)? = null
        val wiki = WikiContextValue("w1", WikiInfo(id = "w1"), WikiPermissions(view = true, edit = true), "https://server")
        rule.setContent {
            CompositionLocalProvider(LocalWikiContext provides wiki) {
                WikiCommentThread(
                    comment = WikiComment(id = "c1", author = "me", name = "Me", body = "Old"),
                    currentUserId = "me",
                    isOwner = false,
                    onStartReply = { _, _ -> },
                    onEdit = { _, _, done -> saved = done },
                    onDelete = null,
                )
            }
        }
        rule.onNodeWithContentDescription("Edit").performClick()
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()
        assertTrue(saved != null)
        rule.onNodeWithText("Save").assertExists()
        saved?.invoke()
        rule.waitForIdle()
        rule.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun `the composer keeps its text until the comment is in`() {
        var pending: (() -> Unit)? = null
        rule.setContent {
            CommentForm(
                onSubmit = { _, sent -> pending = sent },
                resolveFileName = { "plan.pdf" },
                files = emptyList(),
                onAddFiles = {},
                onRemoveFile = {},
            )
        }
        rule.onNode(hasSetTextAction()).performTextInput("Hello")
        rule.onNodeWithContentDescription("Send").performClick()
        rule.waitForIdle()
        assertTrue(pending != null)
        rule.onNodeWithText("Hello").assertExists()
        pending?.invoke()
        rule.waitForIdle()
        rule.onNodeWithText("Hello").assertDoesNotExist()
    }

    @Test
    fun `Send needs text, whatever files are picked`() {
        rule.setContent {
            CommentForm(
                onSubmit = { _, _ -> },
                resolveFileName = { "plan.pdf" },
                files = listOf(picked("plan.pdf")),
                onAddFiles = {},
                onRemoveFile = {},
            )
        }
        rule.onNodeWithContentDescription("Send").assertIsNotEnabled()
        rule.onNode(hasSetTextAction()).performTextInput("Hello")
        rule.onNodeWithContentDescription("Send").assertIsEnabled()
    }

    @Test
    fun `while a comment is on its way the bar offers no Send`() {
        var sending by mutableStateOf(true)
        rule.setContent {
            CommentForm(
                onSubmit = { _, _ -> },
                resolveFileName = { "plan.pdf" },
                files = emptyList(),
                onAddFiles = {},
                onRemoveFile = {},
                initialText = "Hello",
                isSending = sending,
            )
        }
        rule.onAllNodesWithContentDescription("Send").fetchSemanticsNodes().let { nodes ->
            assertTrue("THE POINT: no Send while sending: $nodes", nodes.isEmpty())
        }
        sending = false
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Send").assertIsEnabled()
    }
}
