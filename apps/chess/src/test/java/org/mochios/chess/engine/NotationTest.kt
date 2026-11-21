// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.engine

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The record is written as chess.js writes it, so the web client extends this
 * client's record and this one extends the web's. `notation.json` is generated
 * with chess.js by the web's `pgn.test.ts`, which also holds it byte-identical
 * to its own copy: every ply's FEN and PGN here is chess.js's.
 */
class NotationTest {

    private class Ply(
        val from: String = "",
        val to: String = "",
        val promotion: String? = null,
        val san: String = "",
        val captured: String? = null,
        val fen: String = "",
        val pgn: String = "",
        val repetition: Boolean = false,
    )

    private class Game(
        val name: String = "",
        val start: String = "",
        val empty: String = "",
        val plies: List<Ply> = emptyList(),
    )

    private class Fixture(val games: List<Game> = emptyList())

    private val games: List<Game> = javaClass.getResourceAsStream("/notation.json")!!
        .reader()
        .use { reader -> Gson().fromJson(reader, Fixture::class.java).games }

    private fun game(name: String): Game = games.single { game -> game.name == name }

    @Test
    fun `the fixture holds every game`() {
        assertEquals(9, games.size)
    }

    @Test
    fun `every move is written as chess js writes it`() {
        for (game in games) {
            val record = Record.resume(game.start, "")!!
            assertEquals(game.name, game.empty, record.pgn)
            game.plies.forEachIndexed { index, ply ->
                val played = record.play(ply.from, ply.to, ply.promotion)
                assertNotNull("${game.name} ply $index", played)
                assertEquals("${game.name} ply $index", ply.san, played!!.san)
                assertEquals("THE POINT: ${game.name} ply $index FEN", ply.fen, record.fen)
                assertEquals("THE POINT: ${game.name} ply $index PGN", ply.pgn, record.pgn)
                assertEquals("${game.name} ply $index capture", ply.captured, played.captured?.let(::letter))
            }
        }
    }

    @Test
    fun `each move extends the record the server holds`() {
        // As the view model plays: the stored FEN and PGN, read afresh each move.
        for (game in games) {
            var fen = game.start
            var pgn = ""
            for (ply in game.plies) {
                val record = Record.resume(fen, pgn)!!
                record.play(ply.from, ply.to, ply.promotion)!!
                assertEquals(game.name, ply.pgn, record.pgn)
                fen = record.fen
                pgn = record.pgn
            }
        }
    }

    @Test
    fun `every record chess js wrote is read with its whole history`() {
        for (game in games) {
            game.plies.forEachIndexed { index, ply ->
                val record = Record.resume(ply.fen, ply.pgn)!!
                assertEquals("${game.name} ply $index", index + 1, record.moves.size)
                assertEquals(ply.from, record.last?.from)
                assertEquals(ply.to, record.last?.to)
            }
        }
    }

    @Test
    fun `this client's old records are read`() {
        val opening = game("en passant")
        // Moves only, no tags or marker, and an en passant square after every
        // two-square pawn move, as chesslib writes it.
        val played = Record.resume(opening.start, "")!!
        opening.plies.forEachIndexed { index, ply ->
            played.play(ply.from, ply.to, ply.promotion)
            val tagless = opening.plies.take(index + 1)
                .mapIndexed { at, move -> if (at % 2 == 0) "${at / 2 + 1}. ${move.san}" else move.san }
                .joinToString(" ")
            val record = Record.resume(played.board.fen, tagless)!!
            assertEquals(index + 1, record.moves.size)
        }
        assertTrue(Record.resume(opening.plies[0].fen.replace(" - ", " e3 "), "1. e4")!!.moves.size == 1)
    }

    @Test
    fun `a move this client appended after the web's marker is read, and the record mended`() {
        val opening = game("en passant")
        val (first, second, third) = opening.plies
        val appended = "${first.pgn} ${second.san}"
        val record = Record.resume(second.fen, appended)!!
        assertEquals(listOf(first.san, second.san), record.moves.map { move -> move.san })
        record.play(third.from, third.to, third.promotion)
        assertEquals(third.pgn, record.pgn)
    }

    @Test
    fun `a record that is not this position's starts again from the position`() {
        val opening = game("en passant")
        val truncated = opening.plies[0].pgn
        val record = Record.resume(opening.plies[2].fen, truncated)!!
        assertTrue(record.moves.isEmpty())
        assertEquals(opening.plies[2].fen, record.start)
        assertTrue(record.pgn.contains("[SetUp \"1\"]\n[FEN \"${opening.plies[2].fen}\"]"))
    }

    @Test
    fun `threefold repetition is seen from the record's history`() {
        for (game in games) {
            game.plies.forEachIndexed { index, ply ->
                val record = Record.resume(ply.fen, ply.pgn)!!
                assertEquals("THE POINT: ${game.name} ply $index", ply.repetition, record.board.isRepetition(3))
            }
        }
        val repeated = game("repetition").plies.last()
        assertTrue(isDrawnPosition(Record.resume(repeated.fen, repeated.pgn)!!.board))
    }

    @Test
    fun `an illegal move is refused and the record kept`() {
        val record = Record.resume(START, "")!!
        assertNull(record.play("e2", "e5"))
        assertTrue(record.moves.isEmpty())
        assertEquals(START, record.fen)
    }

    private fun letter(type: com.github.bhlangonijr.chesslib.PieceType): String = when (type) {
        com.github.bhlangonijr.chesslib.PieceType.PAWN -> "p"
        com.github.bhlangonijr.chesslib.PieceType.KNIGHT -> "n"
        com.github.bhlangonijr.chesslib.PieceType.BISHOP -> "b"
        com.github.bhlangonijr.chesslib.PieceType.ROOK -> "r"
        com.github.bhlangonijr.chesslib.PieceType.QUEEN -> "q"
        else -> "k"
    }
}
