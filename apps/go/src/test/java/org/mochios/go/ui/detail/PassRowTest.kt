// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.detail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.ui.components.GameChatMessage
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A pass is its own sentence in the move log, never "played Pass": the
 * server's marker decides it, and rows stored before the marker still read as
 * a pass from their body.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PassRowTest {

    @get:Rule
    val rule = createComposeRule()

    private fun row(body: String, event: String = "") = GameChatMessage(
        id = "m1",
        game = "g1",
        member = "b1",
        name = "Bob",
        body = body,
        type = "move",
        event = event,
        created = 0,
    )

    @Test
    fun `a marked pass by the opponent`() {
        rule.setContent { GoMoveMessageRow(row("Pass", "pass"), isSent = false) }
        rule.onNodeWithText("Bob passed").assertExists()
    }

    @Test
    fun `the player's own pass`() {
        rule.setContent { GoMoveMessageRow(row("Pass", "pass"), isSent = true) }
        rule.onNodeWithText("You passed").assertExists()
    }

    @Test
    fun `the marker decides, not the stored word`() {
        rule.setContent { GoMoveMessageRow(row("", "pass"), isSent = false) }
        rule.onNodeWithText("Bob passed").assertExists()
    }

    @Test
    fun `a pass stored before the marker`() {
        rule.setContent { GoMoveMessageRow(row("Pass"), isSent = false) }
        rule.onNodeWithText("Bob passed").assertExists()
    }

    @Test
    fun `a stone is still played at its point`() {
        rule.setContent { GoMoveMessageRow(row("D4"), isSent = false) }
        rule.onNodeWithText("Bob played D4").assertExists()
    }
}
