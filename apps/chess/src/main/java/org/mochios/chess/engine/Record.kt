// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.engine

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import kotlin.math.abs

/** The standard starting position, as chess.js writes it. */
const val START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

/**
 * One move of a record: its SAN, its squares ("e2", "e4"), the side that
 * played it and the type of the piece it took, if any.
 */
data class Played(
    val san: String,
    val from: String,
    val to: String,
    val side: Side,
    val captured: PieceType?,
)

/**
 * A game's record: the position it starts from, its PGN tags and its moves,
 * replayed onto [board].
 *
 * The web client keeps the record with chess.js, so this reads chess.js's PGN
 * (tags, numbered moves, a result marker), the tag-less form this client wrote
 * before, and that form appended past a marker ("1. e4 * e5"); and it writes
 * the PGN and FEN exactly as chess.js would, so each client extends the
 * other's record rather than replacing it.
 */
class Record private constructor(
    /** The position the moves start from: the record's FEN tag, or [START]. */
    val start: String,
    private val tags: LinkedHashMap<String, String>,
    private val played: MutableList<Played>,
    /** The position after the moves. Its history answers repetition. */
    val board: Board,
) {
    val moves: List<Played> get() = played

    /** The move played last, for the board's highlight. */
    val last: Played? get() = played.lastOrNull()

    /** The current position, written as chess.js writes it. */
    val fen: String get() = fen(board)

    /** The record, written as chess.js's `pgn()` writes it. */
    val pgn: String get() = write()

    /**
     * Play [from]-[to] ("e7", "e8"), promoting to [promotion] ("q", "r", "b",
     * "n") when the move reaches the last rank. Null, and nothing played, when
     * the move is not legal here.
     */
    fun play(from: String, to: String, promotion: String? = null): Played? {
        val wanted = promotion?.takeIf { it.isNotBlank() }?.let { kind(it.first().uppercaseChar()) }
        val move = board.legalMoves().firstOrNull { candidate ->
            square(candidate.from) == from.lowercase() &&
                square(candidate.to) == to.lowercase() &&
                promoted(candidate) == wanted
        } ?: return null
        val made = apply(board, move)
        played += made
        return made
    }

    private fun write(): String {
        val header = LinkedHashMap<String, String?>()
        for (tag in TEMPLATE) header[tag] = ROSTER[tag]
        for ((tag, value) in tags) header[tag] = value
        val lines = header.entries
            .filter { (_, value) -> value != null }
            .joinToString("") { (tag, value) -> "[$tag \"$value\"]\n" }
        val result = header["Result"] ?: "*"
        if (played.isEmpty()) return "$lines $result"
        return "$lines\n${movetext()} $result"
    }

    /** "1. e4 e5 2. Nf3", or "1. ... e5 2. Nf3" from a position with Black to move. */
    private fun movetext(): String {
        val fields = start.trim().split(Regex("\\s+"))
        var number = fields.getOrNull(5)?.toIntOrNull() ?: 1
        val groups = mutableListOf<String>()
        var group = ""
        played.forEachIndexed { index, move ->
            if (index == 0 && move.side == Side.BLACK) {
                group = "$number. ... ${move.san}"
            } else if (move.side == Side.WHITE) {
                if (group.isNotEmpty()) groups += group
                group = "$number. ${move.san}"
            } else {
                group += " ${move.san}"
            }
            if (move.side == Side.BLACK) number += 1
        }
        if (group.isNotEmpty()) groups += group
        return groups.joinToString(" ")
    }

    companion object {
        /**
         * The record of a game the server stores as [fen] and [pgn]. [pgn] is
         * trusted only when it replays to [fen], as the web's `resume()`
         * decides; any other record - lost to an older client, or not this
         * game's - gives one that starts at [fen] with no moves. Null when
         * [fen] is not a playable position.
         */
        fun resume(fen: String, pgn: String): Record? {
            val position = loadPosition(fen) ?: return null
            if (pgn.isNotBlank()) {
                val record = read(pgn)
                if (record != null && record.fen == fen(position)) return record
            }
            return starting(fen, position)
        }

        /**
         * [pgn] alone, replayed from its FEN tag or the standard position.
         * Null when it names a position that cannot be played, or a move that
         * is not legal where it stands.
         */
        fun read(pgn: String): Record? {
            val tags = LinkedHashMap<String, String>()
            for (match in TAG.findAll(pgn)) tags[match.groupValues[1]] = match.groupValues[2]
            val key = tags.keys.firstOrNull { tag -> tag == "FEN" } ?: tags.keys.firstOrNull { tag -> tag.equals("fen", ignoreCase = true) }
            val start = key?.let { tag -> tags[tag] } ?: START
            if (key != null) {
                // As chess.js loads it: the position decides SetUp and FEN.
                tags.remove("SetUp")
                tags.remove("FEN")
                if (start != START) {
                    tags["SetUp"] = "1"
                    tags["FEN"] = start
                }
            }
            val board = loadPosition(start) ?: return null
            val played = mutableListOf<Played>()
            val text = COMMENT.replace(TAG.replace(pgn, " "), " ")
            for (raw in text.split(Regex("\\s+"))) {
                val token = raw.replace(NUMBER, "")
                if (token.isEmpty() || token == "..." || token.startsWith("$")) continue
                if (token in RESULTS) {
                    tags["Result"] = token
                    continue
                }
                val move = resolve(board, token) ?: return null
                played += apply(board, move)
            }
            return Record(start, tags, played, board)
        }

        /** A record starting at [fen] with no moves, as chess.js's `load()` makes one. */
        private fun starting(fen: String, position: Board): Record {
            val tags = LinkedHashMap<String, String>()
            if (fen != START) {
                tags["SetUp"] = "1"
                tags["FEN"] = fen
            }
            return Record(fen, tags, mutableListOf(), position)
        }

        private val TAG = Regex("\\[\\s*([A-Za-z0-9_]+)\\s+\"([^\"]*)\"\\s*]")
        private val COMMENT = Regex("\\{[^}]*\\}")
        private val NUMBER = Regex("^\\d+\\.+")
        private val RESULTS = setOf("*", "1-0", "0-1", "1/2-1/2")

        /** The seven required tags and their defaults, as chess.js writes them. */
        private val ROSTER = mapOf(
            "Event" to "?",
            "Site" to "?",
            "Date" to "????.??.??",
            "Round" to "?",
            "White" to "?",
            "Black" to "?",
            "Result" to "*",
        )

        /** chess.js's tag order: the roster, then the supplemental tags. */
        private val TEMPLATE = ROSTER.keys + listOf(
            "WhiteTitle", "BlackTitle", "WhiteElo", "BlackElo", "WhiteUSCF", "BlackUSCF",
            "WhiteNA", "BlackNA", "WhiteType", "BlackType", "EventDate", "EventSponsor",
            "Section", "Stage", "Board", "Opening", "Variation", "SubVariation", "ECO", "NIC",
            "Time", "UTCTime", "UTCDate", "TimeControl", "SetUp", "FEN", "Termination",
            "Annotator", "Mode", "PlyCount",
        )
    }
}

/**
 * [board] as chess.js writes a FEN: the en passant square only when a capture
 * there is legal. chesslib writes it after every two-square pawn move.
 */
fun fen(board: Board): String {
    val fields = board.getFen(true, false).split(" ").toMutableList()
    if (fields.size > 3 && fields[3] != "-") {
        val target = fields[3].lowercase()
        val capturable = board.legalMoves().any { move ->
            square(move.to) == target &&
                board.getPiece(move.from).pieceType == PieceType.PAWN &&
                square(move.from)[0] != target[0]
        }
        if (!capturable) fields[3] = "-"
    }
    return fields.joinToString(" ")
}

/** "e4" for E4. */
private fun square(square: Square): String = square.value().lowercase()

private fun kind(letter: Char): PieceType? = when (letter) {
    'N' -> PieceType.KNIGHT
    'B' -> PieceType.BISHOP
    'R' -> PieceType.ROOK
    'Q' -> PieceType.QUEEN
    'K' -> PieceType.KING
    else -> null
}

private fun letter(type: PieceType): String = when (type) {
    PieceType.KNIGHT -> "N"
    PieceType.BISHOP -> "B"
    PieceType.ROOK -> "R"
    PieceType.QUEEN -> "Q"
    PieceType.KING -> "K"
    else -> ""
}

private fun promoted(move: Move): PieceType? =
    move.promotion?.takeIf { it != Piece.NONE }?.pieceType

/**
 * The legal move [token] names. Read by its parts rather than matched against
 * a written SAN, so a move written with more disambiguation than it needs, or
 * without its check sign, still reads; one that fits two moves does not.
 */
private fun resolve(board: Board, token: String): Move? {
    var text = token.trimEnd('+', '#', '!', '?').removeSuffix("e.p.")
    if (text == "0-0") text = "O-O"
    if (text == "0-0-0") text = "O-O-O"
    val legal = board.legalMoves()
    if (text == "O-O" || text == "O-O-O") {
        val file = if (text == "O-O") 'g' else 'c'
        return legal.singleOrNull { move ->
            board.getPiece(move.from).pieceType == PieceType.KING &&
                abs(square(move.from)[0] - square(move.to)[0]) == 2 &&
                square(move.to)[0] == file
        }
    }
    val parts = ALGEBRAIC.matchEntire(text) ?: return null
    val type = parts.groupValues[1].firstOrNull()?.let(::kind) ?: PieceType.PAWN
    val file = parts.groupValues[2].firstOrNull()
    val rank = parts.groupValues[3].firstOrNull()
    val to = parts.groupValues[4]
    val promotion = parts.groupValues[5].firstOrNull()?.let(::kind)
    return legal.singleOrNull { move ->
        val from = square(move.from)
        board.getPiece(move.from).pieceType == type &&
            square(move.to) == to &&
            (file == null || from[0] == file) &&
            (rank == null || from[1] == rank) &&
            promoted(move) == promotion
    }
}

private val ALGEBRAIC = Regex("^([NBRQK])?([a-h])?([1-8])?x?([a-h][1-8])(?:=?([NBRQ]))?$")

/** Play [move] on [board], which must allow it, and say what it was. */
private fun apply(board: Board, move: Move): Played {
    val piece = board.getPiece(move.from)
    val from = square(move.from)
    val to = square(move.to)
    val san = san(board, move)
    val taken = board.getPiece(move.to)
    val captured = when {
        taken != Piece.NONE -> taken.pieceType
        // A pawn moving across a file onto an empty square takes en passant.
        piece.pieceType == PieceType.PAWN && from[0] != to[0] -> PieceType.PAWN
        else -> null
    }
    val side = board.sideToMove
    board.doMove(move)
    return Played(san, from, to, side, captured)
}

/** [move]'s SAN on [board], as chess.js's `_moveToSan` writes it. */
private fun san(board: Board, move: Move): String {
    val piece = board.getPiece(move.from)
    val type = piece.pieceType
    val from = square(move.from)
    val to = square(move.to)
    val text = if (type == PieceType.KING && abs(from[0] - to[0]) == 2) {
        if (to[0] == 'g') "O-O" else "O-O-O"
    } else {
        buildString {
            val capture = board.getPiece(move.to) != Piece.NONE ||
                (type == PieceType.PAWN && from[0] != to[0])
            if (type != PieceType.PAWN) {
                append(letter(type))
                append(disambiguator(board, move, piece))
            }
            if (capture) {
                if (type == PieceType.PAWN) append(from[0])
                append('x')
            }
            append(to)
            promoted(move)?.let { promoted ->
                append('=')
                append(letter(promoted))
            }
        }
    }
    board.doMove(move)
    val suffix = when {
        board.isMated -> "#"
        board.isKingAttacked -> "+"
        else -> ""
    }
    board.undoMove()
    return text + suffix
}

/** chess.js's `getDisambiguator`: the file, the rank or the square that tells [move] from its twins. */
private fun disambiguator(board: Board, move: Move, piece: Piece): String {
    val from = square(move.from)
    var twins = 0
    var ranks = 0
    var files = 0
    for (other in board.legalMoves()) {
        if (other.to != move.to || other.from == move.from) continue
        if (board.getPiece(other.from) != piece) continue
        twins += 1
        val place = square(other.from)
        if (place[1] == from[1]) ranks += 1
        if (place[0] == from[0]) files += 1
    }
    return when {
        twins == 0 -> ""
        ranks > 0 && files > 0 -> from
        files > 0 -> from[1].toString()
        else -> from[0].toString()
    }
}
