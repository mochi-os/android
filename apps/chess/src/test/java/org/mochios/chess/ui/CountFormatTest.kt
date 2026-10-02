// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.bhlangonijr.chesslib.PieceType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.chess.ui.detail.board.CapturedPiece
import org.mochios.chess.ui.detail.board.CapturedPiecesStrip
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A captured piece's multiplier used to be written by String.format, which
 * uses the language's own digits (Arabic-Indic in Arabic). It is written as the
 * user writes numbers, digits 0 to 9, as the web writes it. A side has at most
 * eight of any piece, so the count never reaches a thousand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ar")
class CountFormatTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the pawns captured`() {
        rule.setContent { CapturedPiecesStrip(capturedByColor = 'w', pieces = listOf(CapturedPiece(PieceType.PAWN, 8))) }
        rule.onNodeWithText("×8").assertExists()
    }
}
