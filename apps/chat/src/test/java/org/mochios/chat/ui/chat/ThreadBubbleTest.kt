// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.chat.model.ChatMessage
import org.robolectric.RobolectricTestRunner
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
    fun `a thread of no more than three says nothing came before`() {
        show(root(2, (1..2).map(::reply)))
        rule.onNodeWithText("Reply 1").assertIsDisplayed()
        rule.onNodeWithText("earlier", substring = true).assertDoesNotExist()
    }
}
