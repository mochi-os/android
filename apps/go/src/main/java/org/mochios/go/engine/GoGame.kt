// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.engine

/**
 * Kotlin port of `apps/go/web/src/lib/go-engine.ts`.
 *
 * Models a Go (Weiqi) position as an immutable value class — [place] and
 * [pass] return new instances rather than mutating in place, so callers can
 * branch on speculative moves (e.g. "what would the board look like if I
 * played here?") without copying defensively.
 *
 * ## Board encoding
 *
 * The board is serialised in a FEN-like form: rows joined by `/`, each cell
 * one of `.` (empty), `B` (black), `W` (white). After the board string
 * follows space-separated metadata:
 *
 *   `<rows> <turn> <capturesBlack> <capturesWhite> <koPoint> <consecutivePasses>`
 *
 * - `<turn>` is `b` or `w`
 * - `<koPoint>` is `r,c` if a ko point is set, `-` otherwise
 *
 * Example empty 9x9 board: `.../.../... b 0 0 - 0` (with full row strings).
 *
 * Mirrors the TS implementation exactly so the server-stored FEN can be
 * round-tripped between web (TS) and Android (Kotlin) clients.
 */
class GoGame private constructor(
    val size: Int,
    private val grid: Array<CharArray>,
    val turn: Stone,
    val capturesBlack: Int,
    val capturesWhite: Int,
    private val koPoint: Pair<Int, Int>?,
    val consecutivePasses: Int,
    private val previousGrid: Array<CharArray>?,
    val lastMove: Pair<Int, Int>?,
) {

    /** Captures recorded so far for each colour. */
    val captures: Captures
        get() = Captures(black = capturesBlack, white = capturesWhite)

    /**
     * Current board serialised as a FEN-like string (see class doc for
     * format). This is the value the server stores in `games.fen`.
     */
    val board: String
        get() = serialize()

    /**
     * Previous-position board string (rows joined by `/`, no metadata) used
     * for ko detection. Returned without metadata to match the TS getter —
     * the metadata of a "previous" position isn't meaningful to consumers.
     */
    val previousBoard: String?
        get() = previousGrid?.let { rowsToString(it) }

    /**
     * Read-only view of the grid. Indexed `[row][col]`; the returned arrays
     * are defensive copies so callers can't mutate internal state.
     */
    val gridView: Array<CharArray>
        get() = Array(size) { r -> grid[r].copyOf() }

    /** True after two consecutive passes — the game has ended. */
    val isOver: Boolean
        get() = consecutivePasses >= 2

    // ------------------------------------------------------------------
    // Public constructors
    // ------------------------------------------------------------------

    /** Empty board of [size] x [size] with Black to play. */
    constructor(size: Int) : this(
        size = size,
        grid = emptyGrid(size),
        turn = Stone.BLACK,
        capturesBlack = 0,
        capturesWhite = 0,
        koPoint = null,
        consecutivePasses = 0,
        previousGrid = null,
        lastMove = null,
    )

    /**
     * Parse a FEN-like [board] string back into a game. [previousBoardFen]
     * is an optional plain `rows-joined-by-slash` previous-position string
     * used for ko-rule enforcement.
     */
    constructor(board: String, previousBoardFen: String? = null) : this(
        parseToTriple(board, previousBoardFen),
    )

    private constructor(parsed: ParseResult) : this(
        size = parsed.size,
        grid = parsed.grid,
        turn = parsed.turn,
        capturesBlack = parsed.capturesBlack,
        capturesWhite = parsed.capturesWhite,
        koPoint = parsed.koPoint,
        consecutivePasses = parsed.consecutivePasses,
        previousGrid = parsed.previousGrid,
        lastMove = null,
    )

    // ------------------------------------------------------------------
    // Engine API
    // ------------------------------------------------------------------

    /**
     * Place a stone of the current turn at ([row], [col]).
     *
     * Throws [IllegalMoveException] when the move is illegal:
     *   - the intersection is occupied
     *   - the move repeats the previous position (simple ko)
     *   - the move would leave the placed stone's group with no liberties
     *     (suicide — Chinese / area-scoring rules forbid it)
     *
     * Returns a new [GoGame] reflecting the placement, any captured
     * opponent stones removed, the capture counters incremented, the turn
     * flipped, and `consecutivePasses` reset to 0.
     */
    fun place(row: Int, col: Int): GoGame {
        if (!isLegal(row, col)) {
            throw IllegalMoveException("Illegal move at $row,$col")
        }

        val stone = turn.stoneChar
        val opponent = turn.opposite().stoneChar

        // Clone grid for the new state, save current grid as previous (for ko).
        val newGrid = cloneGrid(grid)
        val savedPrev = cloneGrid(grid)

        // Place stone.
        newGrid[row][col] = stone

        // Capture opponent groups with no liberties.
        var captured = 0
        var capturedSinglePos: Pair<Int, Int>? = null
        for ((nr, nc) in neighbors(row, col, size)) {
            if (newGrid[nr][nc] == opponent) {
                val group = findGroup(newGrid, nr, nc)
                if (group.liberties.isEmpty()) {
                    if (group.stones.size == 1) {
                        capturedSinglePos = group.stones.first()
                    }
                    captured += removeGroup(newGrid, group.stones)
                }
            }
        }

        // Set ko point: exactly 1 captured stone, placed stone has exactly
        // 1 liberty pointing back to the captured position.
        var newKo: Pair<Int, Int>? = null
        if (captured == 1 && capturedSinglePos != null) {
            val placedGroup = findGroup(newGrid, row, col)
            if (placedGroup.stones.size == 1 && placedGroup.liberties.size == 1) {
                newKo = capturedSinglePos
            }
        }

        val newCapturesBlack = if (turn == Stone.BLACK) capturesBlack + captured else capturesBlack
        val newCapturesWhite = if (turn == Stone.WHITE) capturesWhite + captured else capturesWhite

        return GoGame(
            size = size,
            grid = newGrid,
            turn = turn.opposite(),
            capturesBlack = newCapturesBlack,
            capturesWhite = newCapturesWhite,
            koPoint = newKo,
            consecutivePasses = 0,
            previousGrid = savedPrev,
            lastMove = row to col,
        )
    }

    /**
     * Pass turn. Returns a new game with the turn flipped,
     * `consecutivePasses` incremented, ko point cleared, and `lastMove`
     * cleared (so the UI can stop drawing the last-move marker).
     */
    fun pass(): GoGame =
        GoGame(
            size = size,
            grid = cloneGrid(grid),
            turn = turn.opposite(),
            capturesBlack = capturesBlack,
            capturesWhite = capturesWhite,
            koPoint = null,
            consecutivePasses = consecutivePasses + 1,
            previousGrid = cloneGrid(grid),
            lastMove = null,
        )

    /**
     * True if placing a stone of the current turn at ([row], [col]) is
     * legal. Implements bounds, occupancy, ko, and suicide checks.
     */
    fun isLegal(row: Int, col: Int): Boolean {
        // Out of bounds.
        if (row < 0 || row >= size || col < 0 || col >= size) return false
        // Already occupied.
        if (grid[row][col] != EMPTY) return false
        // Ko rule.
        if (koPoint != null && koPoint == row to col) return false

        // Speculative placement.
        val test = cloneGrid(grid)
        val stone = turn.stoneChar
        val opponent = turn.opposite().stoneChar
        test[row][col] = stone

        // Remove captured opponent groups.
        for ((nr, nc) in neighbors(row, col, size)) {
            if (test[nr][nc] == opponent) {
                val group = findGroup(test, nr, nc)
                if (group.liberties.isEmpty()) {
                    removeGroup(test, group.stones)
                }
            }
        }

        // Suicide check: placed stone's group must have at least one liberty.
        val placedGroup = findGroup(test, row, col)
        return placedGroup.liberties.isNotEmpty()
    }

    /**
     * Area-scoring result. `winner` is `Stone.BLACK` if Black's total
     * (stones + black-only territory) strictly exceeds White's total
     * (stones + white-only territory + [komi]); otherwise `Stone.WHITE`
     * (i.e. ties go to White, matching the TS engine's `black > white`
     * test). [komi] defaults to 6.5 to keep parity with the web engine.
     */
    fun score(komi: Double = 6.5): Score {
        val territory = scoreTerritory(grid)
        val black = territory.first.toDouble()
        val white = territory.second.toDouble() + komi
        return Score(
            black = black,
            white = white,
            winner = if (black > white) Stone.BLACK else Stone.WHITE,
        )
    }

    /**
     * Per-cell territory ownership for finished-game overlay rendering.
     *
     * Returns a [size]x[size] array. Each cell is one of:
     *  - [Territory.BLACK] / [Territory.WHITE] — empty intersection surrounded
     *    solely by that colour (counts toward area score)
     *  - [Territory.NEUTRAL] — dame: empty intersection touching both colours
     *  - [Territory.OCCUPIED] — a stone is on the intersection
     */
    fun territory(): Array<Array<Territory>> {
        val result = Array(size) { Array(size) { Territory.OCCUPIED } }
        val visited = HashSet<Int>(size * size)

        for (r in 0 until size) {
            for (c in 0 until size) {
                if (grid[r][c] != EMPTY) continue
                val key = r * size + c
                if (visited.contains(key)) continue

                val region = ArrayList<Pair<Int, Int>>()
                val stack = ArrayDeque<Pair<Int, Int>>()
                stack.addLast(r to c)
                var touchesBlack = false
                var touchesWhite = false

                while (stack.isNotEmpty()) {
                    val (cr, cc) = stack.removeLast()
                    val ck = cr * size + cc
                    if (visited.contains(ck)) continue
                    visited.add(ck)

                    if (grid[cr][cc] == EMPTY) {
                        region.add(cr to cc)
                        for ((nr, nc) in neighbors(cr, cc, size)) {
                            val nk = nr * size + nc
                            if (!visited.contains(nk)) {
                                when (grid[nr][nc]) {
                                    EMPTY -> stack.addLast(nr to nc)
                                    BLACK -> touchesBlack = true
                                    WHITE -> touchesWhite = true
                                }
                            }
                        }
                    }
                }

                val owner = when {
                    touchesBlack && !touchesWhite -> Territory.BLACK
                    touchesWhite && !touchesBlack -> Territory.WHITE
                    else -> Territory.NEUTRAL
                }
                for ((tr, tc) in region) {
                    result[tr][tc] = owner
                }
            }
        }
        return result
    }

    /** Single-cell read access; returns the raw stone char (`.`/`B`/`W`). */
    fun getStone(row: Int, col: Int): Char = grid[row][col]

    // ------------------------------------------------------------------
    // FEN serialisation
    // ------------------------------------------------------------------

    private fun serialize(): String {
        val board = rowsToString(grid)
        val turnChar = if (turn == Stone.BLACK) "b" else "w"
        val koStr = koPoint?.let { "${it.first},${it.second}" } ?: "-"
        return "$board $turnChar $capturesBlack $capturesWhite $koStr $consecutivePasses"
    }

    // ------------------------------------------------------------------
    // Companion: parsing + coord helpers
    // ------------------------------------------------------------------

    companion object {
        const val EMPTY: Char = '.'
        const val BLACK: Char = 'B'
        const val WHITE: Char = 'W'

        // Letters skip 'I' in Go notation. Up to 19x19 only needs A-T.
        private const val COORD_LETTERS = "ABCDEFGHJKLMNOPQRST"

        /**
         * SGF / Q16-style coordinate label for ([row], [col]) on a board of
         * the given [size]. Letters skip 'I'; rows are numbered from bottom
         * (1 at row `size-1`) to top (`size` at row 0), matching the TS
         * implementation.
         */
        fun coordToLabel(row: Int, col: Int, size: Int): String {
            val letter = COORD_LETTERS.getOrElse(col) { '?' }
            val number = size - row
            return "$letter$number"
        }

        // Internal helpers (also used by constructor).

        private fun emptyGrid(size: Int): Array<CharArray> =
            Array(size) { CharArray(size) { EMPTY } }

        private fun cloneGrid(grid: Array<CharArray>): Array<CharArray> =
            Array(grid.size) { grid[it].copyOf() }

        private fun rowsToString(grid: Array<CharArray>): String =
            grid.joinToString("/") { String(it) }

        private fun parseToTriple(board: String, previousBoardFen: String?): ParseResult {
            val parts = board.split(' ')
            val rows = parts[0].split('/')
            val size = rows.size
            val grid = Array(size) { CharArray(size) }
            for (r in 0 until size) {
                val row = rows[r]
                for (c in 0 until size) {
                    grid[r][c] = when (row[c]) {
                        BLACK -> BLACK
                        WHITE -> WHITE
                        else -> EMPTY
                    }
                }
            }
            val turn = if (parts.getOrNull(1) == "w") Stone.WHITE else Stone.BLACK
            val capturesBlack = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val capturesWhite = parts.getOrNull(3)?.toIntOrNull() ?: 0
            val koPoint: Pair<Int, Int>? = parts.getOrNull(4)?.let { token ->
                if (token == "-" || token.isEmpty()) null else {
                    val coords = token.split(',')
                    val kr = coords.getOrNull(0)?.toIntOrNull()
                    val kc = coords.getOrNull(1)?.toIntOrNull()
                    if (kr != null && kc != null) kr to kc else null
                }
            }
            val consecutivePasses = parts.getOrNull(5)?.toIntOrNull() ?: 0

            val prev: Array<CharArray>? = previousBoardFen?.let { parsePreviousGrid(it, size) }

            return ParseResult(
                size = size,
                grid = grid,
                turn = turn,
                capturesBlack = capturesBlack,
                capturesWhite = capturesWhite,
                koPoint = koPoint,
                consecutivePasses = consecutivePasses,
                previousGrid = prev,
            )
        }

        private fun parsePreviousGrid(prevFen: String, size: Int): Array<CharArray>? {
            // The "previous" FEN may be either a board-only `rows/joined`
            // string (what previousBoard() returns) or a full FEN. Take
            // whichever the caller passed and just read the rows segment.
            val rowsPart = prevFen.split(' ').first()
            val rows = rowsPart.split('/')
            if (rows.size != size) return null
            val grid = Array(size) { CharArray(size) }
            for (r in 0 until size) {
                val row = rows[r]
                if (row.length != size) return null
                for (c in 0 until size) {
                    grid[r][c] = when (row[c]) {
                        BLACK -> BLACK
                        WHITE -> WHITE
                        else -> EMPTY
                    }
                }
            }
            return grid
        }

        // BFS over a connected group of [color] stones starting at (row, col).
        // Returns the group's stones and the set of empty-intersection liberties.
        private fun findGroup(grid: Array<CharArray>, row: Int, col: Int): Group {
            val size = grid.size
            val color = grid[row][col]
            if (color == EMPTY) return Group(emptyList(), emptySet())

            val visited = HashSet<Int>()
            val stones = ArrayList<Pair<Int, Int>>()
            val liberties = HashSet<Int>()
            val stack = ArrayDeque<Pair<Int, Int>>()
            stack.addLast(row to col)

            while (stack.isNotEmpty()) {
                val (r, c) = stack.removeLast()
                val key = r * size + c
                if (visited.contains(key)) continue
                visited.add(key)
                if (grid[r][c] == color) {
                    stones.add(r to c)
                    for ((nr, nc) in neighbors(r, c, size)) {
                        val nkey = nr * size + nc
                        if (visited.contains(nkey)) continue
                        when (grid[nr][nc]) {
                            EMPTY -> liberties.add(nkey)
                            color -> stack.addLast(nr to nc)
                        }
                    }
                }
            }
            return Group(stones, liberties)
        }

        private fun removeGroup(grid: Array<CharArray>, stones: List<Pair<Int, Int>>): Int {
            for ((r, c) in stones) grid[r][c] = EMPTY
            return stones.size
        }

        private fun neighbors(row: Int, col: Int, size: Int): List<Pair<Int, Int>> {
            val out = ArrayList<Pair<Int, Int>>(4)
            if (row > 0) out.add(row - 1 to col)
            if (row < size - 1) out.add(row + 1 to col)
            if (col > 0) out.add(row to col - 1)
            if (col < size - 1) out.add(row to col + 1)
            return out
        }

        /**
         * Chinese / area scoring: stones on board + surrounded empty
         * intersections. Returns `(blackTotal, whiteTotal)`.
         */
        private fun scoreTerritory(grid: Array<CharArray>): Pair<Int, Int> {
            val size = grid.size
            val visited = HashSet<Int>()
            var black = 0
            var white = 0

            // Stones on board count for their owner.
            for (r in 0 until size) {
                for (c in 0 until size) {
                    when (grid[r][c]) {
                        BLACK -> black++
                        WHITE -> white++
                    }
                }
            }

            // Flood-fill empty regions; assign to a colour only if surrounded
            // solely by that colour.
            for (r in 0 until size) {
                for (c in 0 until size) {
                    val key = r * size + c
                    if (grid[r][c] != EMPTY || visited.contains(key)) continue

                    val region = ArrayList<Pair<Int, Int>>()
                    val stack = ArrayDeque<Pair<Int, Int>>()
                    stack.addLast(r to c)
                    var touchesBlack = false
                    var touchesWhite = false

                    while (stack.isNotEmpty()) {
                        val (cr, cc) = stack.removeLast()
                        val ck = cr * size + cc
                        if (visited.contains(ck)) continue
                        visited.add(ck)

                        if (grid[cr][cc] == EMPTY) {
                            region.add(cr to cc)
                            for ((nr, nc) in neighbors(cr, cc, size)) {
                                val nk = nr * size + nc
                                if (!visited.contains(nk)) {
                                    when (grid[nr][nc]) {
                                        EMPTY -> stack.addLast(nr to nc)
                                        BLACK -> touchesBlack = true
                                        WHITE -> touchesWhite = true
                                    }
                                }
                            }
                        }
                    }

                    if (touchesBlack && !touchesWhite) black += region.size
                    else if (touchesWhite && !touchesBlack) white += region.size
                }
            }
            return black to white
        }
    }
}

/**
 * Stone colour. Two playing colours only — empty intersections are
 * represented by `Char '.'` inside the engine, not by a third [Stone] value.
 */
enum class Stone {
    BLACK,
    WHITE;

    fun opposite(): Stone = if (this == BLACK) WHITE else BLACK

    internal val stoneChar: Char
        get() = if (this == BLACK) GoGame.BLACK else GoGame.WHITE
}

/** Captures recorded so far (number of opponent stones each colour removed). */
data class Captures(val black: Int, val white: Int)

/** Area-scoring result. */
data class Score(val black: Double, val white: Double, val winner: Stone)

/** Territory ownership labels for [GoGame.territory]. */
enum class Territory { BLACK, WHITE, NEUTRAL, OCCUPIED }

/** Thrown by [GoGame.place] when the move violates a rule. */
class IllegalMoveException(message: String) : IllegalArgumentException(message)

// Internal helper class for the parsing constructor chain.
private data class ParseResult(
    val size: Int,
    val grid: Array<CharArray>,
    val turn: Stone,
    val capturesBlack: Int,
    val capturesWhite: Int,
    val koPoint: Pair<Int, Int>?,
    val consecutivePasses: Int,
    val previousGrid: Array<CharArray>?,
)

// Internal helper for findGroup.
private data class Group(val stones: List<Pair<Int, Int>>, val liberties: Set<Int>)
