// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A resignation has three outcomes, not two: the resigner, the winner, and the
 * players who did neither. The header used to ask only "did I win", so every
 * remaining player was told they had resigned.
 */
class ResignOutcomeTest {

    /** Four seats, Carol (seat 3) resigned, Bob (seat 2) leads and wins. */
    private fun game(
        writer: String? = "id3",
        myPlayer: Int = 0,
    ) = Game(
        id = "g1",
        player_count = 4,
        player1 = "id1",
        player1_name = "Alice",
        player2 = "id2",
        player2_name = "Bob",
        player3 = "id3",
        player3_name = "Carol",
        player4 = "id4",
        player4_name = "Dan",
        status = "resigned",
        winner = "id2",
        writer = writer,
        my_player_number = myPlayer,
    )

    @Test
    fun `the resigner is recognised`() {
        assertTrue(isResigner(game(), "id3"))
    }

    @Test
    fun `a player who neither resigned nor won is not the resigner`() {
        assertFalse(isResigner(game(), "id1"))
        assertFalse(isResigner(game(), "id4"))
    }

    @Test
    fun `the winner is not the resigner`() {
        assertFalse(isResigner(game(), "id2"))
    }

    @Test
    fun `the resigner's seat is found`() {
        assertEquals(3, resignerSlot(game()))
    }

    @Test
    fun `a game with no writer names no resigner`() {
        assertNull(resignerSlot(game(writer = null)))
        assertFalse(isResigner(game(writer = null), "id3"))
        assertFalse(isResigner(game(writer = ""), "id3"))
    }

    @Test
    fun `a writer holding no seat names no resigner`() {
        assertNull(resignerSlot(game(writer = "someoneElse")))
    }

    /** Before the identity loads, the seat number is the only handle. */
    @Test
    fun `the seat number decides while the identity is unknown`() {
        assertTrue(isResigner(game(myPlayer = 3), ""))
        assertFalse(isResigner(game(myPlayer = 1), ""))
    }

    @Test
    fun `heads-up still tells the two sides apart`() {
        val headsUp = Game(
            id = "g2",
            player_count = 2,
            player1 = "id1",
            player2 = "id2",
            status = "resigned",
            winner = "id1",
            writer = "id2",
        )
        assertTrue(isResigner(headsUp, "id2"))
        assertFalse(isResigner(headsUp, "id1"))
    }
}
