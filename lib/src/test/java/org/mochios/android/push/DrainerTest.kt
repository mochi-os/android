// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * An account's drains run one at a time: two at once would each read the queue
 * before either acked it, and dispatch the same rows twice. Requests while one
 * runs make one more pass after it, since what it read may already be behind.
 */
class DrainerTest {

    @Test
    fun `drains run one at a time, and requests meanwhile make one more pass`() {
        val drainer = Drainer()
        val running = AtomicInteger()
        val most = AtomicInteger()
        val passes = AtomicInteger()
        val first = CountDownLatch(1)
        val started = CountDownLatch(1)
        val drain = {
            val now = running.incrementAndGet()
            most.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            if (passes.incrementAndGet() == 1) {
                started.countDown()
                first.await(5, TimeUnit.SECONDS)
            } else {
                Thread.sleep(50)
            }
            running.decrementAndGet()
            Unit
        }
        val pool = Executors.newFixedThreadPool(4)
        try {
            val leading = pool.submit { drainer.request(drain) }
            started.await(5, TimeUnit.SECONDS)
            val more = (1..3).map { pool.submit { drainer.request(drain) } }
            more.forEach { request -> request.get(5, TimeUnit.SECONDS) }
            first.countDown()
            leading.get(5, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }
        assertEquals("never two at once", 1, most.get())
        assertEquals("the first, and one more for the requests meanwhile", 2, passes.get())
    }
}
