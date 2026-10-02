// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.go.engine.Stone
import org.mochios.go.ui.detail.board.GoBoard
import org.mochios.go.ui.newgame.BoardSizeRow
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A board's size used to be written by String.format, which uses the
 * language's own digits (Arabic-Indic in Arabic). It is an identifier, written
 * in plain digits 0 to 9 as the web writes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ar")
class BoardSizeTextTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the new game's board sizes`() {
        rule.setContent { BoardSizeRow(boardSize = 13, enabled = true, onSelect = {}) }
        for (size in listOf("9×9", "13×13", "19×19")) rule.onNodeWithText(size).assertExists()
    }

    @Test
    fun `the board's description for a screen reader`() {
        rule.setContent {
            GoBoard(
                fen = "",
                previousFen = null,
                boardSize = 13,
                myColor = Stone.BLACK,
                isMyTurn = false,
                gameStatus = "finished",
                onPlace = { _, _ -> },
                lastMove = null,
            )
        }
        rule.onNodeWithContentDescription("13×13", substring = true).assertExists()
    }
}
