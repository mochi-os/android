// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.chat.model.ChatMessage
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A thread's root shows its newest three replies in its bubble, below how
 * many came before them, which opens the thread. An older server sends every
 * reply, of which the newest three are shown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThreadBubbleTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun begin() {
        AppContext.set(RuntimeEnvironment.getApplication())
    }

    private fun reply(index: Int) = ChatMessage(
        id = "r$index", chat = "c1", member = "b1", name = "Bea", body = "Reply $index",
        created = 100L + index, thread = "m1",
    )

    private fun root(replies: Int, answers: List<ChatMessage>) = ChatMessage(
        id = "m1", chat = "c1", member = "a1", name = "Al", body = "Root", created = 100,
        replies = replies, latest = answers.lastOrNull()?.created ?: 0, answers = answers,
    )

    private var opened = 0

    private fun show(message: ChatMessage) = rule.setContent {
        MessageBubble(
            message = message,
            isOwn = false,
            receipt = null,
            seen = null,
            onThread = { opened++ },
            isGroup = true,
            chatId = "c1",
            selectionMode = false,
            isSelected = false,
            isSearchMatch = false,
            searchQuery = "",
            replyToMessage = null,
            onStartSelect = {},
            onToggleSelect = {},
            onReply = {},
            onDelete = {},
            onReact = {},
            onForward = {},
            onEdit = {},
        )
    }

    @Test
    fun `the newest replies show below how many came before them, which opens the thread`() {
        show(root(5, (3..5).map(::reply)))
        (3..5).forEach { index -> rule.onNodeWithText("Reply $index").assertIsDisplayed() }
        rule.onNodeWithText("2 earlier replies").assertIsDisplayed().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `an older server's every reply shows only the newest three`() {
        show(root(5, (1..5).map(::reply)))
        (3..5).forEach { index -> rule.onNodeWithText("Reply $index").assertIsDisplayed() }
        rule.onNodeWithText("Reply 1").assertDoesNotExist()
        rule.onNodeWithText("Reply 2").assertDoesNotExist()
        rule.onNodeWithText("2 earlier replies").assertIsDisplayed()
    }

    @Test
    fun `a deleted message keeps its thread, still opening it`() {
        show(root(4, (2..4).map(::reply)).tombstone())
        rule.onNodeWithText("This message was deleted").assertIsDisplayed()
        (2..4).forEach { index -> rule.onNodeWithText("Reply $index").assertIsDisplayed() }
        rule.onNodeWithText("1 earlier reply").assertIsDisplayed().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `a message read from a device the user distrusted, or one whose keys do not match, says so`() {
        show(root(0, emptyList()).copy(warning = "distrusted"))
        rule.onNodeWithText("Sent from a device you distrusted").assertIsDisplayed()
    }

    @Test
    fun `a message whose keys do not match says so`() {
        show(root(0, emptyList()).copy(warning = "mismatched"))
        rule.onNodeWithText("Sent from a device whose keys do not match").assertIsDisplayed()
    }

    @Test
    fun `a reply in a thread offers its writer edit and delete, and anyone a reaction and forward`() {
        var edited: ChatMessage? = null
        val mine = reply(1).copy(member = "a1", name = "Al", body = "My reply")
        rule.setContent {
            Column {
                ThreadContent(
                    thread = ThreadState(root = root(2, emptyList()), replies = listOf(mine, reply(2)), isLoading = false),
                    chatId = "c1",
                    canWrite = true,
                    onDraft = {},
                    onSend = {},
                    onEarlier = {},
                    replies = ReplyActions(identity = "a1", canWrite = true, onEdit = { edited = it }),
                )
            }
        }
        rule.onNodeWithText("My reply").performTouchInput { longClick() }
        rule.onNodeWithText("Edit").assertIsDisplayed()
        rule.onNodeWithText("Delete").assertIsDisplayed()
        rule.onNodeWithText("Forward").assertIsDisplayed().performClick()
        rule.onNodeWithText("Reply 2").performTouchInput { longClick() }
        rule.onNodeWithText("Forward").assertIsDisplayed()
        rule.onNodeWithText("Edit").assertDoesNotExist()
        rule.onNodeWithText("Delete").assertDoesNotExist()
        rule.onNodeWithText("Forward").performClick()
        rule.onNodeWithText("My reply").performTouchInput { longClick() }
        rule.onNodeWithText("Edit").performClick()
        assertEquals(mine, edited)
    }

    @Test
    fun `a message whose send failed says why, with Retry, Copy and Discard`() {
        val taps = mutableListOf<String>()
        rule.setContent {
            UnsentRow(
                message = Unsent(key = 1, text = "Did not go", error = org.mochios.android.api.MochiError.Unknown("The server could not take it", 400)),
                onRetry = { taps += "retry" },
                onUnencrypted = { taps += "unencrypted" },
                onCopy = { taps += "copy" },
                onDiscard = { taps += "discard" },
            )
        }
        rule.onNodeWithText("Did not go").assertIsDisplayed()
        rule.onNodeWithText("The server could not take it").assertIsDisplayed()
        rule.onNodeWithText("Send unencrypted").assertDoesNotExist()
        rule.onNodeWithText("Retry").performClick()
        rule.onNodeWithText("Copy").performClick()
        rule.onNodeWithText("Discard").performClick()
        assertEquals(listOf("retry", "copy", "discard"), taps)
    }

    @Test
    fun `a message refused for want of encryption offers to send it unencrypted`() {
        val taps = mutableListOf<String>()
        rule.setContent {
            UnsentRow(
                message = Unsent(
                    key = 1,
                    text = "Secret",
                    error = org.mochios.android.api.MochiError.Unknown("The message can no longer be encrypted for everyone in this conversation", 409),
                ),
                onRetry = { taps += "retry" },
                onUnencrypted = { taps += "unencrypted" },
                onCopy = {},
                onDiscard = {},
            )
        }
        rule.onNodeWithText("The message can no longer be encrypted for everyone in this conversation").assertIsDisplayed()
        rule.onNodeWithText("Send unencrypted").performClick()
        assertEquals(listOf("unencrypted"), taps)
    }

    @Test
    fun `the composer's refused message says why and offers to send it unencrypted`() {
        var sent = 0
        rule.setContent {
            RefusalLine(
                refusal = org.mochios.android.api.MochiError.Unknown("The message can no longer be encrypted for everyone in this conversation", 409),
                onUnencrypted = { sent++ },
            )
        }
        rule.onNodeWithText("The message can no longer be encrypted for everyone in this conversation").assertIsDisplayed()
        rule.onNodeWithText("Send unencrypted").performClick()
        assertEquals(1, sent)
    }

    @Test
    fun `a message's length shows as it nears the limit, and by how much it is over past it`() {
        var text by mutableStateOf("a".repeat(MESSAGE_COUNTED - 1))
        rule.setContent { LengthLine(text) }
        rule.onNodeWithText("/10,000", substring = true).assertDoesNotExist()
        rule.runOnIdle { text = "a".repeat(MESSAGE_COUNTED) }
        rule.onNodeWithText("9,500/10,000").assertIsDisplayed()
        rule.runOnIdle { text = "😀".repeat(MESSAGE_MAXIMUM + 1) }
        rule.onNodeWithText("Message is 1 character too long").assertIsDisplayed()
        rule.runOnIdle { text = "😀".repeat(MESSAGE_MAXIMUM + 3) }
        rule.onNodeWithText("Message is 3 characters too long").assertIsDisplayed()
    }

    @Test
    fun `a forward under way leaves every destination off, the tapped one busy`() {
        var taps = 0
        rule.setContent {
            Column {
                ForwardRow("Busy chat", busy = true, enabled = false) { taps++ }
                ForwardRow("Other chat", busy = false, enabled = false) { taps++ }
            }
        }
        rule.onNodeWithText("Other chat").performClick()
        rule.onNodeWithText("Busy chat").performClick()
        assertEquals(0, taps)
    }

    @Test
    fun `a thread of no more than three says nothing came before`() {
        show(root(2, (1..2).map(::reply)))
        rule.onNodeWithText("Reply 1").assertIsDisplayed()
        rule.onNodeWithText("earlier", substring = true).assertDoesNotExist()
    }
}
