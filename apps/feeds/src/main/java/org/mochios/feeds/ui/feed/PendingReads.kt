// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feed

import kotlinx.coroutines.delay

/**
 * The post ids waiting to be marked read. Ids are filed from the scroll
 * listener and taken by the batching coroutine, so every access is guarded.
 */
internal class PendingReads {
    private val ids = mutableSetOf<String>()

    fun add(id: String) {
        synchronized(ids) { ids.add(id) }
    }

    /** Every id filed since the last drain; the set is left empty. */
    fun drain(): List<String> = synchronized(ids) {
        val batch = ids.toList()
        ids.clear()
        batch
    }
}

/**
 * Hand [send] each batch of filed ids until nothing is left, waiting [pause]
 * milliseconds before each so a fast scroll is one request.
 *
 * Draining until empty is what makes this safe to start once: a caller that
 * finds this already running files its id and returns, so a single pass would
 * strand every id filed while a request was in flight.
 */
internal suspend fun drainReads(
    pending: PendingReads,
    pause: Long = 200,
    send: suspend (List<String>) -> Unit,
) {
    while (true) {
        delay(pause)
        val batch = pending.drain()
        if (batch.isEmpty()) return
        send(batch)
    }
}
