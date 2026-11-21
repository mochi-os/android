// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.ui.components.DrawerItem
import org.mochios.words.model.Game
import org.mochios.words.model.GameListItem
import org.mochios.words.ui.components.wordsDrawerItems
import org.mochios.words.ui.detail.WordsHeaderModel
import org.mochios.words.ui.detail.buildHeaderModel
import org.mochios.words.ui.detail.board.MoveActions
import org.mochios.words.ui.detail.board.MoveFeedback
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Counts and scores are worded by the catalogue, not glued in code: tiles left
 * is a label beside its number, the player counts are plurals read with the
 * count, and a score keeps its sign and the user's number format.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LabelsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `tiles left is a label beside its number, and seats are counted by a plural`() {
        var model: WordsHeaderModel? = null
        val game = Game(id = "g", player_count = 3, player1 = "me", player2 = "you", player3 = "them", bag_count = 1)
        rule.setContent { model = buildHeaderModel(game, "me") }
        rule.waitForIdle()
        val header = model!!
        assertEquals("Tiles left", header.tilesLeftLabel)
        assertEquals("1", header.tilesLeft)
        assertEquals("3 players", header.playersLabel)
    }

    @Test
    fun `the drawer counts the seats of a game with no names yet by a plural`() {
        var items: List<DrawerItem> = emptyList()
        rule.setContent { items = wordsDrawerItems(listOf(GameListItem(id = "g", player_count = 2)), "me") }
        rule.waitForIdle()
        assertEquals("2 players (0 · 0)", items.single().title)
    }

    @Test
    fun `the submit button carries the score in the user's number format`() {
        rule.setContent {
            MoveActions(
                pendingPlacements = 2,
                exchangeMode = false,
                exchangeSelected = 0,
                draftScore = 1234,
                onRecall = {},
                onSubmit = {},
                onExchangeConfirm = {},
                onExchangeCancel = {},
                canSubmit = true,
                canRecallMove = true,
                isSubmitting = false,
                isExchanging = false,
            )
        }
        rule.onNodeWithText("Submit +1,234").assertExists()
    }

    @Test
    fun `a word's score keeps its sign and the user's number format`() {
        rule.setContent {
            MoveFeedback(
                pendingPlacements = 2,
                exchangeMode = false,
                moveDraft = null,
                draftWords = listOf("QUIZ" to 1234),
                wordValidationState = emptyMap(),
            )
        }
        rule.onNodeWithText("+1,234").assertExists()
    }
}
