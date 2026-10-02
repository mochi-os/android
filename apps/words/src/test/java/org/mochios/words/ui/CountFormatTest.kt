// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.words.model.Game
import org.mochios.words.ui.detail.WordsHeaderModel
import org.mochios.words.ui.detail.buildHeaderModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Numbers inside sentences used to be written by String.format, which uses the
 * language's own digits (Arabic-Indic in Arabic). Counts are written as the
 * user writes numbers and a seat's number as plain digits, 0 to 9, as the web
 * writes them. A bag holds at most a hundred tiles and a game four seats, so
 * no count here reaches a thousand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ar")
class CountFormatTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the header's tiles left, seats and an unnamed player`() {
        var model: WordsHeaderModel? = null
        val game = Game(
            id = "g",
            player_count = 3,
            player1 = "me",
            player1_name = "Me",
            player2 = "you",
            player2_name = "You",
            player3 = "them",
            bag_count = 86,
        )
        rule.setContent { model = buildHeaderModel(game, "me") }
        rule.waitForIdle()
        val header = model!!
        assertEquals("86 قطعة متبقية", header.tilesLeftLabel)
        assertEquals("3 لاعبون", header.playersLabel)
        assertEquals("لاعب 3", header.players.first { player -> player.playerNumber == 3 }.label)
    }
}
