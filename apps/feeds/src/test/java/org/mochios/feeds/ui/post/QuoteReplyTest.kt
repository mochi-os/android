// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.post

import android.text.Selection
import android.text.Spannable
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R as MochiR
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.ClickableLinkTextView
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.api.MenuApi
import org.mochios.feeds.repository.FeedsRepository
import org.mochios.feeds.ui.component.CommentItem
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Reply quotes the text selected in a comment into the composer, as the web's
 * does: the post's draft takes the quote ahead of what is typed, and a comment
 * row hands its selection over from the Reply icon and from the selection's
 * own menu, and offers neither to a reader who may not comment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuoteReplyTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket

    @Before
    fun begin() {
        server = MockWebServer()
        // The post is not under test: nothing the page asks for is there.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(404)
        }
        server.start()
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = server.url("/").toString().trimEnd('/')

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.shutdown()
    }

    private fun model(): PostDetailViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/feeds/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val session = SessionManager(context)
        runBlocking { session.setServerUrl(server.url("/").toString().trimEnd('/')) }
        val repository = FeedsRepository(
            retrofit.create(FeedsApi::class.java),
            retrofit.create(MenuApi::class.java),
            FileStore(context),
            context,
            session,
        )
        return PostDetailViewModel(
            SavedStateHandle(mapOf("feedId" to "f1", "postId" to "p1")),
            repository,
            socket,
            session,
        )
    }

    @Test
    fun `a reply with text selected quotes it ahead of the draft`() {
        val model = model()
        model.setCommentText("my words")
        model.startReply("c1", " quick brown ")
        assertEquals("c1" to "> quick brown\n\nmy words", model.replyingTo.value to model.commentText.value)
    }

    @Test
    fun `a second reply replaces the first's quote rather than stacking another`() {
        val model = model()
        model.setCommentText("my words")
        model.startReply("c1", "quick brown")
        model.startReply("c2", "quick brown")
        assertEquals("THE POINT: quoted once", "c2" to "> quick brown\n\nmy words", model.replyingTo.value to model.commentText.value)
        model.startReply("c2", null)
        assertEquals("with nothing selected, no quote, as the web's Reply leaves none", "my words", model.commentText.value)
    }

    private var root: View? = null
    private val replied = mutableListOf<String?>()

    private fun bodies(view: View): List<ClickableLinkTextView> = when (view) {
        is ClickableLinkTextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { bodies(view.getChildAt(it)) }
        else -> emptyList()
    }

    /** A comment row reading "The quick brown fox", and its body; [comment] is whether the reader may reply. */
    private fun row(comment: Boolean = true): ClickableLinkTextView {
        rule.setContent {
            root = LocalView.current
            CommentItem(
                comment = Comment(id = "c1", authorName = "Ann", body = "The quick brown fox"),
                depth = 0, avatarUrl = null, feedId = "f1", isEditing = false, editText = "",
                onEditTextChange = {}, onSaveEdit = {}, onCancelEdit = {},
                onReply = if (comment) ({ selected -> replied += selected }) else null,
                onEdit = {}, onDelete = {}, onReact = {}, canManage = false,
            )
        }
        rule.waitForIdle()
        return bodies(root!!).single()
    }

    @Test
    fun `the Reply icon hands over the text selected in the comment`() {
        val body = row()
        rule.runOnUiThread { Selection.setSelection(body.text as Spannable, 4, 15) }
        rule.onNodeWithContentDescription(context.getString(MochiR.string.comment_reply)).performClick()
        rule.waitForIdle()
        assertEquals(listOf("quick brown"), replied)
    }

    @Test
    fun `the Reply icon hands over text selected in another comment, as the web reads the page's selection`() {
        rule.setContent {
            root = LocalView.current
            Column {
                CommentItem(
                    comment = Comment(id = "c1", authorName = "Ann", body = "The quick brown fox"),
                    depth = 0, avatarUrl = null, feedId = "f1", isEditing = false, editText = "",
                    onEditTextChange = {}, onSaveEdit = {}, onCancelEdit = {},
                    onReply = { selected -> replied += selected },
                    onEdit = {}, onDelete = {}, onReact = {}, canManage = false,
                )
                CommentItem(
                    comment = Comment(id = "c2", authorName = "Bob", body = "Jumps over"),
                    depth = 0, avatarUrl = null, feedId = "f1", isEditing = false, editText = "",
                    onEditTextChange = {}, onSaveEdit = {}, onCancelEdit = {},
                    onReply = { selected -> replied += "c2: $selected" },
                    onEdit = {}, onDelete = {}, onReact = {}, canManage = false,
                )
            }
        }
        rule.waitForIdle()
        val first = bodies(root!!).single { body -> body.text.startsWith("The") }
        rule.runOnUiThread { Selection.setSelection(first.text as Spannable, 4, 15) }
        rule.onAllNodesWithContentDescription(context.getString(MochiR.string.comment_reply))[1].performClick()
        rule.waitForIdle()
        assertEquals("THE POINT: the second comment's Reply quotes the first's selection", listOf("c2: quick brown"), replied)
    }

    @Test
    fun `the selection's menu offers Reply, handing over the selection`() {
        val action = row().selectionAction
        assertNotNull("the body's selection menu offers Reply", action)
        rule.runOnUiThread { action!!.onSelected("quick brown") }
        assertEquals(listOf("quick brown"), replied)
    }

    @Test
    fun `a reader who may not comment is offered no Reply, on the row or in the selection's menu`() {
        val body = row(comment = false)
        assertEquals(
            0 to null,
            rule.onAllNodesWithContentDescription(context.getString(MochiR.string.comment_reply))
                .fetchSemanticsNodes().size to body.selectionAction,
        )
    }
}
