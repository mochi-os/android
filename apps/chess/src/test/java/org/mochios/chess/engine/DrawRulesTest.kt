// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.engine

import com.github.bhlangonijr.chesslib.Board
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dead positions match chess.js, not chesslib's broader
 * `isInsufficientMaterial`. The K+N v K+N and K+N v K+B cases also assert that
 * chesslib disagrees, so a regression to `board.isDraw` fails.
 */
class DrawRulesTest {

    private fun board(fen: String) = Board().apply { loadFromFen(fen) }

    // ---------------- dead positions ----------------

    @Test
    fun `bare kings are dead`() {
        assertTrue(isDeadPosition(board("8/8/4k3/8/8/4K3/8/8 w - - 0 1")))
    }

    @Test
    fun `king and one minor piece is dead`() {
        assertTrue(isDeadPosition(board("8/8/4k3/8/8/4K3/5N2/8 w - - 0 1")))
        assertTrue(isDeadPosition(board("8/8/4k3/8/8/4K3/5B2/8 w - - 0 1")))
        assertTrue(isDeadPosition(board("8/5n2/4k3/8/8/4K3/8/8 w - - 0 1")))
    }

    @Test
    fun `bishops on one square colour are dead however many`() {
        // c1 and f4 are both dark.
        assertTrue(isDeadPosition(board("8/8/4k3/8/5B2/4K3/8/2B5 w - - 0 1")))
    }

    // ---------------- live positions ----------------

    /**
     * The divergence. chesslib says dead, chess.js says live, and the rules
     * agree with chess.js — mate is constructible.
     */
    @Test
    fun `knight against knight is live, though chesslib disagrees`() {
        val position = board("8/5n2/4k3/8/8/4K3/5N2/8 w - - 0 1")
        assertFalse("our rule must keep this playable", isDeadPosition(position))
        assertTrue(
            "control: chesslib still calls it insufficient, which is why we do not use it",
            position.isInsufficientMaterial,
        )
    }

    @Test
    fun `knight against bishop is live, though chesslib disagrees`() {
        val position = board("8/5b2/4k3/8/8/4K3/5N2/8 w - - 0 1")
        assertFalse(isDeadPosition(position))
        assertTrue("control: chesslib disagrees here too", position.isInsufficientMaterial)
    }

    @Test
    fun `bishops on opposite square colours are live`() {
        // c1 dark, f1 light.
        assertFalse(isDeadPosition(board("8/8/4k3/8/8/4K3/8/2B2B2 w - - 0 1")))
    }

    @Test
    fun `two knights are live`() {
        assertFalse(isDeadPosition(board("8/8/4k3/8/8/4K3/5NN1/8 w - - 0 1")))
    }

    @Test
    fun `any pawn, rook or queen is live`() {
        assertFalse(isDeadPosition(board("8/8/4k3/8/8/4K3/5P2/8 w - - 0 1")))
        assertFalse(isDeadPosition(board("8/8/4k3/8/8/4K3/5R2/8 w - - 0 1")))
        assertFalse(isDeadPosition(board("8/8/4k3/8/8/4K3/5Q2/8 w - - 0 1")))
    }

    @Test
    fun `the opening position is live`() {
        assertFalse(isDeadPosition(Board()))
    }

    // ---------------- the fifty-move half ----------------

    @Test
    fun `a hundred half-moves without progress is drawn`() {
        assertTrue(isDrawnPosition(board("4k3/8/8/8/8/8/4P3/4K3 w - - 100 60")))
        assertFalse(isDrawnPosition(board("4k3/8/8/8/8/8/4P3/4K3 w - - 99 60")))
    }

    @Test
    fun `a drawn position is any dead position or the fifty-move rule`() {
        assertTrue(isDrawnPosition(board("8/8/4k3/8/8/4K3/8/8 w - - 0 1")))
        assertFalse(isDrawnPosition(board("8/5n2/4k3/8/8/4K3/5N2/8 w - - 0 1")))
    }
}
