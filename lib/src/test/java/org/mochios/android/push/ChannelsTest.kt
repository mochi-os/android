// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.android.account.MochiAccount
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The push service's channels: one being opened as the service is destroyed
 * is torn down with the rest, or never opened, and an account signed in again
 * under a new session has its channel opened again.
 */
class ChannelsTest {

    private val dropped = ConcurrentLinkedQueue<PushChannel>()
    private val channels = Channels { channel -> dropped.add(channel) }

    private fun account(identity: String, session: String) =
        MochiAccount.Snapshot(identity, "Name", "https://example.org", null, session)

    @Test
    fun `a channel being opened as the service is destroyed is torn down with the rest`() {
        val slot = channels.claim("x", "s1")!!
        val channel = PushChannel("subscription", "connection")
        val opening = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val registering = Thread {
            channels.register(slot) {
                opening.countDown()
                proceed.await(5, TimeUnit.SECONDS)
                channel
            }
        }
        registering.start()
        opening.await(5, TimeUnit.SECONDS)
        val closing = Thread { channels.close() }
        closing.start()
        // Time for the close to land, were it not held off until the channel
        // is registered.
        Thread.sleep(300)
        proceed.countDown()
        registering.join(5_000)
        closing.join(5_000)
        assertEquals(listOf(channel), dropped.toList())
    }

    @Test
    fun `nothing is claimed or opened once the service is gone`() {
        val slot = channels.claim("x", "s1")!!
        channels.close()
        var opened = false
        val registered = channels.register(slot) {
            opened = true
            PushChannel("subscription", "connection")
        }
        assertFalse(registered)
        assertFalse(opened)
        assertNull("no new claim", channels.claim("y", "s1"))
    }

    @Test
    fun `an account signed in again under a new session is stale`() {
        channels.claim("x", "s1")
        channels.claim("y", "s1")
        assertEquals(listOf("x"), channels.stale(listOf(account("x", "s2"), account("y", "s1"))))
    }
}
