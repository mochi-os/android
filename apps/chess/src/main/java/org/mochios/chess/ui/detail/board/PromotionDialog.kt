// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.detail.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.chess.R

/**
 * Pawn-promotion picker. [onSelect] receives `q | r | b | n` for
 * [org.mochios.chess.model.MoveRequest.promotion]; dismissing submits no move.
 */
@Composable
fun PromotionDialog(
    myColor: Char,
    onSelect: (promotion: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val side = if (myColor == 'w') Side.WHITE else Side.BLACK
    val pieces: List<Pair<PieceType, String>> = listOf(
        PieceType.QUEEN to "q",
        PieceType.ROOK to "r",
        PieceType.BISHOP to "b",
        PieceType.KNIGHT to "n",
    )

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.chess_promotion_title),
        content = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pieces.forEach { (type, code) ->
                    PromotionButton(
                        piece = Piece.make(side, type),
                        type = type,
                        onClick = { onSelect(code) },
                    )
                }
            }
        },
        dismissText = stringResource(org.mochios.android.R.string.common_cancel),
    )
}

/**
 * A single 56-dp piece button. Tapped → [onClick]. The icon shows the
 * correct side's piece (white pawn promoting → white queen/rook/etc.).
 */
@Composable
private fun PromotionButton(
    piece: Piece,
    type: PieceType,
    onClick: () -> Unit,
) {
    val border = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val label = pieceLabel(type)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .border(width = 1.dp, color = border, shape = RoundedCornerShape(12.dp))
                .pointerInput(piece, onClick) {
                    detectTapGestures(onTap = { onClick() })
                },
            contentAlignment = Alignment.Center,
        ) {
            ChessPieceIcon(
                piece = piece,
                fontSize = pieceGlyphSize(48f),
                contentDescription = label,
            )
        }
        Box(modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
