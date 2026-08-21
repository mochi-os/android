// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.engine

/**
 * Kotlin port of `apps/words/web/src/lib/words-engine.ts` — every function
 * here mirrors the TS engine character-for-character so the move-composer
 * draft preview agrees with the web client on what a placement scores
 * before either calls the server.
 *
 * Board encoding:
 *   - 15x15 grid of single characters
 *   - '.' = empty
 *   - 'A'..'Z' = a regular letter tile
 *   - 'a'..'z' = a blank tile played as that letter (worth 0 points)
 *   - The serialised form joins rows with '/' (`"./..../A.B./..."` etc.)
 *
 * Rack encoding (separate from board):
 *   - Sequence of characters drawn from the bag
 *   - 'A'..'Z' = a normal lettered tile
 *   - '_' = a blank tile (the player picks the letter on placement)
 *
 * Letter values match standard English Scrabble; blanks always score 0.
 */

const val BOARD_SIZE = 15

/**
 * `ST` is the centre star: scores as a `DW`, and like every premium only for
 * newly placed tiles.
 */
enum class PremiumType { NONE, DL, TL, DW, TW, ST }

private val PREMIUM_MAP: Array<Array<PremiumType>> = arrayOf(
    arrayOf(PremiumType.TW, PremiumType.NONE, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.TW, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.NONE, PremiumType.TW),
    arrayOf(PremiumType.NONE, PremiumType.DW, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.TL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.TL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DW, PremiumType.NONE),
    arrayOf(PremiumType.NONE, PremiumType.NONE, PremiumType.DW, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DW, PremiumType.NONE, PremiumType.NONE),
    arrayOf(PremiumType.DL, PremiumType.NONE, PremiumType.NONE, PremiumType.DW, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DW, PremiumType.NONE, PremiumType.NONE, PremiumType.DL),
    arrayOf(PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DW, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DW, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE),
    arrayOf(PremiumType.NONE, PremiumType.TL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.TL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.TL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.TL, PremiumType.NONE),
    arrayOf(PremiumType.NONE, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.NONE),
    arrayOf(PremiumType.TW, PremiumType.NONE, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.ST, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.NONE, PremiumType.TW),
    arrayOf(PremiumType.NONE, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.NONE),
    arrayOf(PremiumType.NONE, PremiumType.TL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.TL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.TL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.TL, PremiumType.NONE),
    arrayOf(PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DW, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DW, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE),
    arrayOf(PremiumType.DL, PremiumType.NONE, PremiumType.NONE, PremiumType.DW, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DW, PremiumType.NONE, PremiumType.NONE, PremiumType.DL),
    arrayOf(PremiumType.NONE, PremiumType.NONE, PremiumType.DW, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DW, PremiumType.NONE, PremiumType.NONE),
    arrayOf(PremiumType.NONE, PremiumType.DW, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.TL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.TL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DW, PremiumType.NONE),
    arrayOf(PremiumType.TW, PremiumType.NONE, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.TW, PremiumType.NONE, PremiumType.NONE, PremiumType.NONE, PremiumType.DL, PremiumType.NONE, PremiumType.NONE, PremiumType.TW),
)

fun getPremium(row: Int, col: Int): PremiumType = PREMIUM_MAP[row][col]

private val LETTER_VALUES: Map<Char, Int> = mapOf(
    'A' to 1, 'B' to 3, 'C' to 3, 'D' to 2, 'E' to 1, 'F' to 4, 'G' to 2, 'H' to 4,
    'I' to 1, 'J' to 8, 'K' to 5, 'L' to 1, 'M' to 3, 'N' to 1, 'O' to 1, 'P' to 3,
    'Q' to 10, 'R' to 1, 'S' to 1, 'T' to 1, 'U' to 1, 'V' to 4, 'W' to 4, 'X' to 8,
    'Y' to 4, 'Z' to 10,
)

/** 0 for blanks (lowercase), the standard Scrabble value otherwise. */
fun getLetterValue(letter: Char): Int {
    if (letter in 'a'..'z') return 0
    return LETTER_VALUES[letter.uppercaseChar()] ?: 0
}

// ─── Board representation ─────────────────────────────────────────────

/**
 * 15x15 grid in the encoding above, mutated in place during draft scoring.
 * [equals] / [hashCode] compare cell contents.
 */
class Board(val cells: Array<CharArray>) {
    init {
        require(cells.size == BOARD_SIZE) { "Board must have $BOARD_SIZE rows" }
        for (row in cells) require(row.size == BOARD_SIZE) { "Each row must have $BOARD_SIZE cells" }
    }

    operator fun get(row: Int, col: Int): Char = cells[row][col]

    fun copy(): Board = Board(Array(BOARD_SIZE) { cells[it].copyOf() })

    fun isEmpty(): Boolean = cells.all { row -> row.all { it == '.' } }

    fun serialise(): String = cells.joinToString("/") { String(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Board) return false
        for (r in 0 until BOARD_SIZE) {
            if (!cells[r].contentEquals(other.cells[r])) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var h = 0
        for (row in cells) h = 31 * h + row.contentHashCode()
        return h
    }
}

fun emptyBoard(): Board = Board(Array(BOARD_SIZE) { CharArray(BOARD_SIZE) { '.' } })

/**
 * Parse the wire form (rows joined by `/`). Malformed input yields an empty
 * board, as in the TS engine.
 */
fun parseBoard(boardStr: String): Board {
    if (boardStr.isEmpty()) return emptyBoard()
    val rows = boardStr.split('/')
    if (rows.size != BOARD_SIZE) return emptyBoard()
    val out = Array(BOARD_SIZE) { CharArray(BOARD_SIZE) }
    for (r in 0 until BOARD_SIZE) {
        if (rows[r].length != BOARD_SIZE) return emptyBoard()
        for (c in 0 until BOARD_SIZE) out[r][c] = rows[r][c]
    }
    return Board(out)
}

fun serializeBoard(board: Board): String = board.serialise()

fun isBoardEmpty(board: Board): Boolean = board.isEmpty()

/** Returns the user-facing uppercase letter for a cell, or empty for empty cells. */
fun getDisplayLetter(cell: Char): String = if (cell == '.') "" else cell.uppercaseChar().toString()

/** True iff the cell is a blank tile played as a specific letter (stored lowercase). */
fun isBlankTile(cell: Char): Boolean = cell in 'a'..'z'

// ─── Placement + result types ─────────────────────────────────────────

/**
 * [letter] is what shows on the board; [rackTile] is the tile spent - '_' for a
 * blank played as [letter], otherwise the same letter.
 */
data class Placement(
    val row: Int,
    val col: Int,
    val letter: Char,
    val rackTile: Char,
)

/**
 * One word formed by a draft move. [tiles] is in word-reading order; [score]
 * includes premium multipliers.
 */
data class WordResult(
    val word: String,
    val score: Int,
    val tiles: List<Pair<Int, Int>>,
)

data class DraftResult(
    val newBoard: Board,
    val wordsFormed: List<WordResult>,
    val totalScore: Int,
    val tilesUsed: String,
)

enum class DraftStatus { INVALID_LOCAL, READY }

/**
 * Why a draft is invalid. The composable maps these to localised strings; the
 * engine holds no prose.
 */
enum class MoveError {
    NO_TILES_PLACED,
    OUT_OF_BOUNDS,
    SQUARE_OCCUPIED,
    NOT_IN_LINE,
    NOT_CONTIGUOUS,
    FIRST_MOVE_MUST_COVER_CENTRE,
    FIRST_MOVE_NEEDS_TWO_TILES,
    NOT_CONNECTED,
    NO_VALID_WORDS,
}

data class MoveDraft(
    val status: DraftStatus,
    val error: MoveError?,
    val result: DraftResult?,
)

// ─── Validation + scoring ─────────────────────────────────────────────

private class MoveValidationException(val error: MoveError) : RuntimeException(error.name)

/**
 * Entry point for the move composer: returns a [MoveDraft] rather than
 * throwing.
 */
fun deriveMoveDraft(
    board: Board,
    placements: List<Placement>,
): MoveDraft {
    if (placements.isEmpty()) {
        return MoveDraft(DraftStatus.INVALID_LOCAL, MoveError.NO_TILES_PLACED, null)
    }
    return try {
        val result = validateAndScoreMove(board, placements)
        MoveDraft(DraftStatus.READY, null, result)
    } catch (e: MoveValidationException) {
        MoveDraft(DraftStatus.INVALID_LOCAL, e.error, null)
    }
}

/**
 * Validate and score a draft, throwing [MoveValidationException] on a rule
 * violation. Premiums count only for newly placed tiles; all seven tiles in one
 * move earn 50 points.
 */
fun validateAndScoreMove(board: Board, placements: List<Placement>): DraftResult {
    if (placements.isEmpty()) throw MoveValidationException(MoveError.NO_TILES_PLACED)

    // Reject duplicate squares here rather than only in the drop handler: the
    // board dedupes them but tilesUsed counts the list, so a duplicate
    // double-scores the cross-word and can fake a bingo.
    if (placements.distinctBy { it.row to it.col }.size != placements.size) {
        throw MoveValidationException(MoveError.SQUARE_OCCUPIED)
    }

    // Bounds + occupancy check.
    for (p in placements) {
        if (p.row < 0 || p.row >= BOARD_SIZE || p.col < 0 || p.col >= BOARD_SIZE) {
            throw MoveValidationException(MoveError.OUT_OF_BOUNDS)
        }
        if (board[p.row, p.col] != '.') {
            throw MoveValidationException(MoveError.SQUARE_OCCUPIED)
        }
    }

    // All placements must share a single row or column.
    val rows = placements.map { it.row }.toSet()
    val cols = placements.map { it.col }.toSet()
    if (rows.size > 1 && cols.size > 1) {
        throw MoveValidationException(MoveError.NOT_IN_LINE)
    }
    val isHorizontal = rows.size == 1

    // Sort placements by position along the placement axis.
    val sorted = placements.sortedWith(
        if (isHorizontal) compareBy { it.col } else compareBy { it.row }
    )

    // Apply placements to a working copy of the board.
    val newBoard = board.copy()
    val newlyPlaced = HashSet<Long>()
    for (p in placements) {
        newBoard.cells[p.row][p.col] =
            if (p.rackTile == '_') p.letter.lowercaseChar() else p.letter.uppercaseChar()
        newlyPlaced.add(cellKey(p.row, p.col))
    }

    // Continuity check: no gaps between placed tiles along the line.
    if (sorted.size > 1) {
        val start = if (isHorizontal) sorted.first().col else sorted.first().row
        val end = if (isHorizontal) sorted.last().col else sorted.last().row
        val fixedAxis = if (isHorizontal) sorted.first().row else sorted.first().col
        for (i in start..end) {
            val r = if (isHorizontal) fixedAxis else i
            val c = if (isHorizontal) i else fixedAxis
            if (newBoard[r, c] == '.') {
                throw MoveValidationException(MoveError.NOT_CONTIGUOUS)
            }
        }
    }

    // Connectivity check: first move covers centre + ≥2 tiles; else connects.
    if (isBoardEmpty(board)) {
        val coversCenter = placements.any { it.row == 7 && it.col == 7 }
        if (!coversCenter) throw MoveValidationException(MoveError.FIRST_MOVE_MUST_COVER_CENTRE)
        if (placements.size < 2) throw MoveValidationException(MoveError.FIRST_MOVE_NEEDS_TWO_TILES)
    } else {
        var connected = false
        outer@ for (p in placements) {
            val neighbours = arrayOf(
                p.row - 1 to p.col,
                p.row + 1 to p.col,
                p.row to p.col - 1,
                p.row to p.col + 1,
            )
            for ((nr, nc) in neighbours) {
                if (nr in 0 until BOARD_SIZE && nc in 0 until BOARD_SIZE) {
                    if (board[nr, nc] != '.') {
                        connected = true
                        break@outer
                    }
                }
            }
        }
        if (!connected) throw MoveValidationException(MoveError.NOT_CONNECTED)
    }

    // Find the main word along the placement axis, anchored at the first
    // sorted placement. findWord walks back to the start of the run before
    // scoring forward, so any starting cell on the word does the job.
    val wordsFormed = mutableListOf<WordResult>()
    findWord(newBoard, sorted.first().row, sorted.first().col, isHorizontal, newlyPlaced)
        ?.let { if (it.word.length >= 2) wordsFormed.add(it) }

    // Cross-words: each placed tile may form a perpendicular word too.
    for (p in placements) {
        findWord(newBoard, p.row, p.col, !isHorizontal, newlyPlaced)
            ?.let { if (it.word.length >= 2) wordsFormed.add(it) }
    }

    if (wordsFormed.isEmpty()) throw MoveValidationException(MoveError.NO_VALID_WORDS)

    var totalScore = wordsFormed.sumOf { it.score }
    if (placements.size == 7) totalScore += 50

    val tilesUsed = placements.map { it.rackTile }.joinToString("")
    return DraftResult(newBoard, wordsFormed, totalScore, tilesUsed)
}

private fun findWord(
    board: Board,
    row: Int,
    col: Int,
    horizontal: Boolean,
    newlyPlaced: Set<Long>,
): WordResult? {
    // Walk back to the start of the word.
    var r = row
    var c = col
    if (horizontal) {
        while (c > 0 && board[r, c - 1] != '.') c--
    } else {
        while (r > 0 && board[r - 1, c] != '.') r--
    }

    val tiles = mutableListOf<Pair<Int, Int>>()
    val wordBuilder = StringBuilder()
    var wordScore = 0
    var wordMultiplier = 1
    var cr = r
    var cc = c

    while (cr < BOARD_SIZE && cc < BOARD_SIZE && board[cr, cc] != '.') {
        val cellLetter = board[cr, cc]
        wordBuilder.append(cellLetter.uppercaseChar())
        tiles.add(cr to cc)

        val isNew = newlyPlaced.contains(cellKey(cr, cc))
        val letterValue = getLetterValue(cellLetter)

        if (isNew) {
            when (getPremium(cr, cc)) {
                PremiumType.DL -> wordScore += letterValue * 2
                PremiumType.TL -> wordScore += letterValue * 3
                PremiumType.DW, PremiumType.ST -> {
                    wordScore += letterValue
                    wordMultiplier *= 2
                }
                PremiumType.TW -> {
                    wordScore += letterValue
                    wordMultiplier *= 3
                }
                PremiumType.NONE -> wordScore += letterValue
            }
        } else {
            wordScore += letterValue
        }

        if (horizontal) cc++ else cr++
    }

    if (wordBuilder.length < 2) return null
    return WordResult(wordBuilder.toString(), wordScore * wordMultiplier, tiles)
}

private fun cellKey(row: Int, col: Int): Long = row.toLong() * BOARD_SIZE + col

// ─── Helpers used by the composer ─────────────────────────────────────

fun getUniqueDraftWords(wordsFormed: List<WordResult>): List<String> {
    val seen = LinkedHashSet<String>()
    for (entry in wordsFormed) {
        if (entry.word.isNotEmpty()) seen.add(entry.word.uppercase())
    }
    return seen.toList()
}

/**
 * Stable signature for a (board, placements) pair; the composer drops debounced
 * validation results whose signature no longer matches. Order-independent.
 */
fun createDraftSignature(boardSerialised: String, placements: List<Placement>): String {
    val ordered = placements.sortedWith(
        compareBy<Placement> { it.row }
            .thenBy { it.col }
            .thenBy { it.letter }
            .thenBy { it.rackTile }
    ).joinToString("|") { "${it.row},${it.col},${it.letter},${it.rackTile}" }
    return "$boardSerialised::$ordered"
}
