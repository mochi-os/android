// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.detail.board

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A square says where it is and what it holds, as the web board's names do:
 * a tile read as its bare letter, or an empty square as "TW", told a screen
 * reader user nothing of the board.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SquareLabelTest {

    @get:Rule
    val rule = createComposeRule()

    private data class Square(val row: Int, val col: Int, val cell: Char, val pending: Char? = null, val lifted: Boolean = false)

    /** The labels of several squares, from one composition: a rule takes one setContent per test. */
    private fun labels(vararg squares: Square): List<String> {
        var texts = emptyList<String>()
        rule.setContent { texts = squares.map { squareLabel(it.row, it.col, it.cell, it.pending, it.lifted) } }
        rule.waitForIdle()
        return texts
    }

    private fun label(row: Int, col: Int, cell: Char, pending: Char? = null, lifted: Boolean = false): String =
        labels(Square(row, col, cell, pending, lifted)).single()

    @Test
    fun `an empty premium square names its coordinate and its premium`() {
        assertEquals("Empty square A1, Triple word score", label(0, 0, '.'))
    }

    @Test
    fun `the start square names its double word score`() {
        assertEquals("Empty square H8, Start square, double word score", label(7, 7, '.'))
    }

    @Test
    fun `a plain empty square names its coordinate alone`() {
        assertEquals("Empty square B1", label(0, 1, '.'))
    }

    @Test
    fun `a tile of this move names its premium and how to take it back`() {
        assertEquals(
            listOf("A at A1, Triple word score, click to remove", "A at B1, click to remove"),
            labels(Square(0, 0, '.', pending = 'a'), Square(0, 1, '.', pending = 'a')),
        )
    }

    @Test
    fun `a tile being dragged leaves its square empty`() {
        assertEquals("Empty square A1, Triple word score", label(0, 0, '.', pending = 'a', lifted = true))
    }

    @Test
    fun `a played tile names its letter and coordinate, its premium spent`() {
        assertEquals(listOf("Q at H8", "E at A1"), labels(Square(7, 7, 'Q'), Square(0, 0, 'e')))
    }
}
