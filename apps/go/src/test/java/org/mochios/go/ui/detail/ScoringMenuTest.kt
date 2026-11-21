// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.detail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.ui.components.DrawerItem
import org.mochios.go.model.Game
import org.mochios.go.ui.components.goDrawerItems
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A game being scored is live: listed with the active games, offered nothing
 * destructive, and the player who has accepted can resume play.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScoringMenuTest {

    @get:Rule
    val rule = createComposeRule()

    private fun menu(game: Game) {
        rule.setContent {
            GoActionsMenu(
                game = game,
                myIdentity = "a1",
                canPass = false,
                passing = false,
                drawOffering = false,
                resuming = false,
                rematching = false,
                onPass = {},
                onOfferDraw = {},
                onResign = {},
                onResume = {},
                onRematch = {},
                onDelete = {},
            )
        }
    }

    private val scoring = Game(id = "g1", identity = "a1", opponent = "b1", status = "scoring")

    @Test
    fun `the player who accepted the count can resume play, and nothing else`() {
        menu(scoring.copy(scoring = "a1"))
        rule.onNodeWithContentDescription("Open game actions").performClick()
        rule.onNodeWithText("Resume play").assertExists()
        rule.onNodeWithText("Delete game").assertDoesNotExist()
        rule.onNodeWithText("Rematch").assertDoesNotExist()
    }

    @Test
    fun `the player still deciding answers in the banner and has no menu`() {
        menu(scoring.copy(scoring = "b1"))
        rule.onNodeWithContentDescription("Open game actions").assertDoesNotExist()
    }

    @Test
    fun `a finished game offers a rematch and its deletion`() {
        menu(scoring.copy(status = "finished"))
        rule.onNodeWithContentDescription("Open game actions").performClick()
        rule.onNodeWithText("Rematch").assertExists()
        rule.onNodeWithText("Delete game").assertExists()
    }

    @Test
    fun `the drawer lists a game being scored with the active games`() {
        var items: List<DrawerItem> = emptyList()
        rule.setContent {
            items = goDrawerItems(listOf(scoring, scoring.copy(id = "g2", status = "finished")), "a1")
        }
        rule.waitForIdle()
        assertEquals(listOf("Active games", "Completed"), items.map { item -> item.section })
    }
}
