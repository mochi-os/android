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
 * Premium-square enum. `ST` is the centre star — acts as a `DW` on the
 * first move (and only on the first move, like every other premium it
 * only fires for newly placed tiles). Matches the `PremiumType` union in
 * the TS engine.
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
 * A 15x15 grid of single-character cells. Wraps a `Array<CharArray>` so
 * mutation in place during draft scoring is allocation-free, with [serialise]
 * + [parseBoard] handling the wire form.
 *
 * This intentionally mirrors the TS engine's `string[][]` shape — every
 * cell is one of:
 *   - '.' (empty)
 *   - 'A'..'Z' (regular letter)
 *   - 'a'..'z' (blank played as that letter)
 *
 * Callers reach into `cells[row][col]` directly the same way the TS code
 * does `board[row][col]`. Two `Board` instances comparing structurally
 * is intentional — `equals`/`hashCode` walk the underlying CharArrays.
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
 * Parse the server's wire form (rows joined by `/`) into a Board.
 * Malformed input (wrong row count, wrong row length) falls back to an
 * empty board — matches the TS engine's behaviour so callers never have
 * to special-case the boot state.
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
 * A single tile being placed in the current draft. [letter] is the display
 * letter on the board (uppercase 'A'..'Z'). [rackTile] is the corresponding
 * tile drawn from the rack: '_' for a blank (the player picked [letter] in
 * the blank-letter prompt) or an uppercase letter for a regular tile (in
 * which case `letter == rackTile`).
 */
data class Placement(
    val row: Int,
    val col: Int,
    val letter: Char,
    val rackTile: Char,
)

/**
 * One word formed by a draft move. [tiles] lists the (row, col) coordinates
 * the word covers, in word-reading order; [score] is that single word's
 * contribution to the total (including any premium-square multipliers).
 */
data class WordResult(
    val word: String,
    val score: Int,
    val tiles: List<Pair<Int, Int>>,
)

/**
 * Full result of validating + scoring a draft move. The TS engine throws
 * on invalid moves; the Kotlin port wraps that in [MoveDraft] (see below)
 * so composables never have to try/catch.
 */
data class DraftResult(
    val newBoard: Board,
    val wordsFormed: List<WordResult>,
    val totalScore: Int,
    val tilesUsed: String,
)

enum class DraftStatus { INVALID_LOCAL, READY }

/**
 * Top-level draft state surfaced to the composer. When the placements
 * make a legal move ([status] == [DraftStatus.READY]) the [result] is
 * populated. When they don't ([INVALID_LOCAL]) the [errorMessage] holds
 * the human-readable reason and [result] is null.
 *
 * Empty placements list yields READY+null result (the composer treats
 * empty as a non-error neutral state; the TS engine throws but the
 * `deriveMoveDraft` wrapper in the TS lib reshapes that to an `empty`
 * status — Kotlin collapses to `INVALID_LOCAL` for consistency, and the
 * caller's empty-check happens before we get here).
 */
data class MoveDraft(
    val status: DraftStatus,
    val errorMessage: String?,
    val result: DraftResult?,
)

// ─── Validation + scoring ─────────────────────────────────────────────

private class MoveValidationException(message: String) : RuntimeException(message)

/**
 * Public entry point used by the move composer. Returns a [MoveDraft] —
 * never throws — so the composer can render preview score / words while
 * the user is mid-placement.
 *
 * [invalidMoveFallback] is the generic error string shown when scoring
 * fails for a reason that doesn't have a more specific message (matches
 * the TS `deriveMoveDraft` second arg).
 */
fun deriveMoveDraft(
    board: Board,
    placements: List<Placement>,
    invalidMoveFallback: String,
): MoveDraft {
    if (placements.isEmpty()) {
        return MoveDraft(DraftStatus.INVALID_LOCAL, invalidMoveFallback, null)
    }
    return try {
        val result = validateAndScoreMove(board, placements)
        MoveDraft(DraftStatus.READY, null, result)
    } catch (e: MoveValidationException) {
        MoveDraft(DraftStatus.INVALID_LOCAL, e.message ?: invalidMoveFallback, null)
    }
}

/**
 * Inner validate + score routine. Throws [MoveValidationException] with a
 * specific message on rule violations — only called by [deriveMoveDraft]
 * directly, which converts the throws into [MoveDraft] state.
 *
 * Rules (mirrors TS `validateAndScoreMove`):
 *  1. All placements in bounds + on empty squares.
 *  2. All placements share a single row or column.
 *  3. No gaps between placed tiles (existing tiles on the same line may
 *     fill the gap).
 *  4. First move: must cover (7,7) and place ≥2 tiles.
 *  5. Later moves: must connect orthogonally to ≥1 existing tile.
 *  6. Premium squares apply only to newly placed tiles.
 *  7. 50-point bingo bonus when all 7 tiles are used in one move.
 */
fun validateAndScoreMove(board: Board, placements: List<Placement>): DraftResult {
    if (placements.isEmpty()) throw MoveValidationException("No tiles placed")

    // Bounds + occupancy check.
    for (p in placements) {
        if (p.row < 0 || p.row >= BOARD_SIZE || p.col < 0 || p.col >= BOARD_SIZE) {
            throw MoveValidationException("Placement out of bounds")
        }
        if (board[p.row, p.col] != '.') {
            throw MoveValidationException("Square already occupied")
        }
    }

    // All placements must share a single row or column.
    val rows = placements.map { it.row }.toSet()
    val cols = placements.map { it.col }.toSet()
    if (rows.size > 1 && cols.size > 1) {
        throw MoveValidationException("Tiles must be placed in a single row or column")
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
                throw MoveValidationException("Tiles must be contiguous (no gaps)")
            }
        }
    }

    // Connectivity check: first move covers centre + ≥2 tiles; else connects.
    if (isBoardEmpty(board)) {
        val coversCenter = placements.any { it.row == 7 && it.col == 7 }
        if (!coversCenter) throw MoveValidationException("First move must cover the center square")
        if (placements.size < 2) throw MoveValidationException("First move must place at least 2 tiles")
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
        if (!connected) throw MoveValidationException("Tiles must connect to existing tiles on the board")
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

    if (wordsFormed.isEmpty()) throw MoveValidationException("No valid words formed")

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

/**
 * Unique uppercase set of every word the draft would form, in the order
 * they first appear. Used by the validation-debounce dedup so we don't
 * fire `validateWord` for the same string twice in one keystroke.
 */
fun getUniqueDraftWords(wordsFormed: List<WordResult>): List<String> {
    val seen = LinkedHashSet<String>()
    for (entry in wordsFormed) {
        if (entry.word.isNotEmpty()) seen.add(entry.word.uppercase())
    }
    return seen.toList()
}

/**
 * Stable signature for a (board, placement-set) pair, used by the
 * composer's debounced word-validation hook to drop late results whose
 * input no longer matches the current draft. Order-independent — the
 * placements are sorted by (row, col, letter, rackTile) before joining,
 * so [Placement] reordering by drag-and-drop doesn't invalidate.
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
