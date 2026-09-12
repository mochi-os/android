// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feed

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The read batcher is started once and files-and-returns while it runs, so
 * every id filed during a request has to be picked up by the same run.
 */
class PendingReadsTest {

    @Test
    fun `ids filed while a batch is in flight are still sent`() = runBlocking {
        val pending = PendingReads()
        val sent = mutableListOf<List<String>>()
        pending.add("a")
        drainReads(pending, pause = 1) { batch ->
            sent.add(batch)
            // The scroll listener files another id while this request runs.
            if (batch.contains("a")) pending.add("b")
        }
        assertEquals(listOf(listOf("a"), listOf("b")), sent)
    }

    @Test
    fun `a drain empties the set, so an id is never sent twice`() = runBlocking {
        val pending = PendingReads()
        pending.add("a")
        pending.add("a")
        pending.add("b")
        val sent = mutableListOf<List<String>>()
        drainReads(pending, pause = 1) { sent.add(it.sorted()) }
        assertEquals(listOf(listOf("a", "b")), sent)
    }

    @Test
    fun `concurrent filing loses nothing`() {
        val pending = PendingReads()
        val threads = (0 until 8).map { thread ->
            Thread { repeat(500) { pending.add("$thread-$it") } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(4000, pending.drain().size)
    }
}
