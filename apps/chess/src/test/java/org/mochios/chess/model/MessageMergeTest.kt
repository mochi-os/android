// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.model

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.android.util.mergeMessages

/**
 * `refresh()` and `loadMoreOlder()` merge overlapping pages of
 * `:game/-/messages`. Every merged row carries the server's id, so the merge
 * has to key on it: two identical messages from one player inside one second
 * are two rows, not one.
 */
class MessageMergeTest {

    private fun merge(existing: List<GameMessage>, incoming: List<GameMessage>) =
        mergeMessages(existing, incoming, key = ::messageKey, created = { it.created })

    @Test
    fun `two identical messages in the same second both survive`() {
        val first = GameMessage(id = "m1", name = "Ada", body = "ok", created = 100)
        val second = first.copy(id = "m2")
        assertEquals(listOf("m1", "m2"), merge(listOf(first), listOf(second)).map { it.id })
    }

    @Test
    fun `an overlapping page does not duplicate a row already held`() {
        val first = GameMessage(id = "m1", body = "ok", created = 100)
        val second = GameMessage(id = "m2", body = "next", created = 101)
        val merged = merge(listOf(first, second), listOf(second, GameMessage(id = "m3", created = 102)))
        assertEquals(listOf("m1", "m2", "m3"), merged.map { it.id })
    }
}
