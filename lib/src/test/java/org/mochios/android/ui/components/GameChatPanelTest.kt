// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.AppContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * The chat panel of chess, go and words: it says what went wrong when nothing
 * loaded, asks for older pages from what is true now, writes each system row
 * in the viewer's language, and the composer keeps a message that failed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameChatPanelTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun begin() {
        AppContext.set(context)
    }

    private fun row(
        id: String,
        type: String = "message",
        event: String = "",
        name: String = "Ana",
        member: String = "ana",
        body: String = "",
        created: Long = 1_753_900_000,
    ) = GameChatMessage(id, "g1", member, name, body, type, event, created)

    @Test
    fun `a chat that failed to load says what went wrong`() {
        rule.setContent {
            GameChatPanel(messages = emptyList(), currentUserIdentity = "me", error = MochiError.NetworkError())
        }
        rule.onNodeWithText(context.getString(R.string.error_network)).assertExists()
    }

    @Test
    fun `the composer stays in view under a failed chat`() {
        rule.setContent {
            Column(modifier = Modifier.height(400.dp)) {
                GameChatColumn(
                    chat = GameChatState<GameChatMessage>(error = MochiError.ServerError(503, "Chat is down")),
                    message = { it },
                    currentUserIdentity = "me",
                    onLoadMore = {},
                    onRetry = {},
                    onSend = { _, _ -> },
                )
            }
        }
        rule.onNodeWithText("Chat is down").assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.game_chat_send)).assertIsDisplayed()
    }

    @Test
    fun `older pages are asked for from what is true now`() {
        var asked = 0
        var more by mutableStateOf(false)
        rule.setContent {
            GameChatPanel(
                messages = listOf(row("m1", body = "hello")),
                currentUserIdentity = "me",
                hasMore = more,
                onLoadMore = { asked += 1 },
            )
        }
        rule.waitForIdle()
        assertEquals(0, asked)
        // The top is in view all along; the history turns out to have more.
        more = true
        rule.waitForIdle()
        assertEquals("THE POINT: the panel asks once there is more", 1, asked)
    }

    @Test
    fun `messages are bucketed into days in the zone their headers are written in`() {
        // 23:30 and 00:30 UTC: one day in Tokyo, two in UTC.
        val late = row("m1", created = 1_753_917_000)
        val early = row("m2", created = 1_753_920_600)
        val device = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val tokyo = groupMessagesByDay(listOf(late, early), TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals("THE POINT: one header", 1, tokyo.count { it is GameChatEntry.DateHeader })
            val utc = groupMessagesByDay(listOf(late, early), TimeZone.getTimeZone("UTC"))
            assertEquals(2, utc.count { it is GameChatEntry.DateHeader })
        } finally {
            TimeZone.setDefault(device)
        }
    }

    @Test
    fun `system rows are written in the viewer's language from the event`() {
        rule.setContent {
            GameChatPanel(
                messages = listOf(
                    row("s1", type = "system", event = "score_accept", body = "Ana hat die Zählung angenommen"),
                    row("s2", type = "system", event = "score_agreed", body = "Zählung vereinbart"),
                    row("s3", type = "system", event = "score_resume", body = "Ana spielt weiter"),
                    row("s4", type = "system", event = "resign", body = ""),
                ),
                currentUserIdentity = "me",
            )
        }
        rule.onNodeWithText(context.getString(R.string.game_system_score_accept, "Ana")).assertExists()
        rule.onNodeWithText(context.getString(R.string.game_system_score_agreed)).assertExists()
        rule.onNodeWithText(context.getString(R.string.game_system_score_resume, "Ana")).assertExists()
        rule.onNodeWithText(context.getString(R.string.game_system_resign, "Ana")).assertExists()
        rule.onNodeWithText("Zählung vereinbart").assertDoesNotExist()
    }

    @Test
    fun `a resignation naming nobody falls back to its stored words`() {
        rule.setContent {
            GameChatPanel(
                messages = listOf(row("s1", type = "system", event = "resign", name = "", body = "Bo resigned")),
                currentUserIdentity = "me",
            )
        }
        rule.onNodeWithText("Bo resigned").assertExists()
    }

    @Test
    fun `the viewer's own move is its own sentence`() {
        rule.setContent {
            GameChatPanel(
                messages = listOf(
                    row("m1", type = "move", member = "me", body = "e4"),
                    row("m2", type = "move", name = "Bo", member = "bo", body = "e5", created = 1_753_900_060),
                ),
                currentUserIdentity = "me",
            )
        }
        rule.onNodeWithText(context.getString(R.string.game_chat_move_played_you, "e4")).assertExists()
        rule.onNodeWithText(context.getString(R.string.game_chat_move_played, "Bo", "e5")).assertExists()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the viewer's own move is not the third person's sentence with "you" put in`() {
        // French conjugates for "vous": "Vous a joué" is wrong, whatever the catalogue holds.
        rule.setContent {
            GameChatPanel(
                messages = listOf(row("m1", type = "move", member = "me", body = "e4")),
                currentUserIdentity = "me",
            )
        }
        rule.onNodeWithText(context.getString(R.string.game_chat_move_played_you, "e4")).assertExists()
        rule.onNodeWithText(context.getString(R.string.game_chat_move_played, "Vous", "e4")).assertDoesNotExist()
    }

    @Test
    fun `a message that fails to send is kept, and one that goes is cleared`() {
        var outcome = false
        val sent = mutableListOf<String>()
        rule.setContent {
            GameChatColumn(
                chat = GameChatState<GameChatMessage>(),
                message = { it },
                currentUserIdentity = "me",
                onLoadMore = {},
                onRetry = {},
                onSend = { body, done ->
                    sent += body
                    done(outcome)
                },
            )
        }
        val placeholder = context.getString(R.string.game_chat_input_placeholder)
        rule.onNodeWithText(placeholder).performTextInput("good game ")
        rule.onNodeWithContentDescription(context.getString(R.string.game_chat_send)).performClick()
        rule.waitForIdle()
        assertEquals(listOf("good game"), sent)
        rule.onNodeWithText("good game ").assertExists()
        outcome = true
        rule.onNodeWithContentDescription(context.getString(R.string.game_chat_send)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("good game ").assertDoesNotExist()
        rule.onNodeWithText(placeholder).assertExists()
    }
}
