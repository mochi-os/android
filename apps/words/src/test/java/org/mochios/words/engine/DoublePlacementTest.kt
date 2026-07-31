// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The drop handler used to accept a tile onto a square that already held one,
 * on the board or as a pending placement. These pin what the engine does with
 * such a draft, which is why that had to be rejected at the drop rather than
 * tidied up afterwards: the damage is in the scoring and the rack, and the
 * board renders placements keyed by cell so the player sees a single tile.
 */
class DoublePlacementTest {

    private fun emptyRow() = ".".repeat(BOARD_SIZE)
    private fun blankBoardString() = (0 until BOARD_SIZE).joinToString("/") { emptyRow() }

    /** Centre-crossing first move: C A T across the middle row. */
    private fun firstMove() = listOf(
        Placement(7, 6, 'C', 'C'),
        Placement(7, 7, 'A', 'A'),
        Placement(7, 8, 'T', 'T'),
    )

    @Test
    fun `a normal first move scores once per tile`() {
        val draft = deriveMoveDraft(parseBoard(blankBoardString()), firstMove())
        assertEquals(DraftStatus.READY, draft.status)
        assertEquals(3, draft.result!!.tilesUsed.length)
    }

    /**
     * Two placements on one square. tilesUsed counts the placement list while
     * the board keeps one letter, so the rack pays twice for one tile — and
     * the count is what the submit path spends.
     */
    @Test
    fun `two placements on one square consume two rack tiles for one letter`() {
        val doubled = firstMove() + Placement(7, 7, 'A', 'A')
        val draft = deriveMoveDraft(parseBoard(blankBoardString()), doubled)
        assertEquals(DraftStatus.INVALID_LOCAL, draft.status)
        assertEquals(MoveError.SQUARE_OCCUPIED, draft.error)
    }

    /**
     * Seven placements is a bingo, but only if they are seven distinct squares.
     * Six squares with one duplicated must not earn the fifty.
     */
    @Test
    fun `a duplicated square must not manufacture a bingo`() {
        val sixDistinct = listOf(
            Placement(7, 4, 'S', 'S'),
            Placement(7, 5, 'T', 'T'),
            Placement(7, 6, 'A', 'A'),
            Placement(7, 7, 'R', 'R'),
            Placement(7, 8, 'T', 'T'),
            Placement(7, 9, 'S', 'S'),
        )
        val sixPlusDuplicate = sixDistinct + Placement(7, 9, 'S', 'S')
        val six = deriveMoveDraft(parseBoard(blankBoardString()), sixDistinct)
        assertEquals(DraftStatus.READY, six.status)
        val seven = deriveMoveDraft(parseBoard(blankBoardString()), sixPlusDuplicate)
        // Before the engine rejected duplicates this scored exactly the bingo:
        // six.totalScore + 50, from six squares.
        assertEquals(DraftStatus.INVALID_LOCAL, seven.status)
        assertEquals(MoveError.SQUARE_OCCUPIED, seven.error)
    }

    /** A placement onto an occupied board square is rejected by the engine. */
    @Test
    fun `placing onto an occupied board square is invalid`() {
        val rows = (0 until BOARD_SIZE).map { r ->
            if (r == 7) "......CAT......" else emptyRow()
        }
        val board = parseBoard(rows.joinToString("/"))
        val draft = deriveMoveDraft(board, listOf(Placement(7, 7, 'X', 'X')))
        assertEquals(DraftStatus.INVALID_LOCAL, draft.status)
        assertEquals(MoveError.SQUARE_OCCUPIED, draft.error)
    }
}
