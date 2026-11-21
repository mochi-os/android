// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.engine

/**
 * One entry of the move record a game carries in its `sgf` field. The record
 * is our own text, not SGF: entries joined by `;`, each `B[row,col]` or
 * `W[pass]`, appended on every move. Port of
 * `apps/go/web/src/lib/move-record.ts`.
 */
private val STONE = Regex("""([BW])\[(\d+),(\d+)]""")

/**
 * The point of the record's last stone, for the last-move marker: whoever
 * played it, so the opponent's reply is marked as well as the player's own.
 * Null when the record is empty, ends in a pass or in something this does not
 * recognise, or names a point [game] does not hold that stone on - such a
 * record is not this position's history, and marking an empty point is worse
 * than marking none.
 */
fun lastStone(record: String, game: GoGame): Pair<Int, Int>? {
    val match = STONE.matchEntire(record.substringAfterLast(';')) ?: return null
    val (colour, rowText, colText) = match.destructured
    val row = rowText.toIntOrNull() ?: return null
    val col = colText.toIntOrNull() ?: return null
    if (row !in 0 until game.size || col !in 0 until game.size) return null
    if (game.getStone(row, col) != colour.single()) return null
    return row to col
}
