// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.android.websocket.Mint

/**
 * An account's channel comes up without the app being opened: a first token
 * that cannot be minted yet - the network down at boot - is asked for again
 * along a backoff ladder, which a network coming up cuts short. A session
 * that is gone, or a claim given up, stops the asking.
 */
class FirstTokenTest {

    @Test
    fun `a first token not to be had yet is asked for again until it is`() = runBlocking {
        val answers = ArrayDeque(listOf<Mint>(Mint.Later, Mint.Later, Mint.Fresh("token")))
        val pauses = mutableListOf<Int>()
        val token = firstToken(mint = { answers.removeFirst() }, held = { true }, pause = { attempt -> pauses.add(attempt) })
        assertEquals("token", token)
        assertEquals(listOf(1, 2), pauses)
    }

    @Test
    fun `a session that is gone stops the asking`() = runBlocking {
        var asked = 0
        val token = firstToken(mint = { asked++; Mint.Gone }, held = { asked < 5 }, pause = {})
        assertNull(token)
        assertEquals(1, asked)
    }

    @Test
    fun `a claim given up stops the asking`() = runBlocking {
        var asked = 0
        val token = firstToken(mint = { asked++; Mint.Later }, held = { asked < 2 }, pause = {})
        assertNull(token)
        assertEquals(2, asked)
    }

    @Test
    fun `a network coming up cuts the wait short`() = runBlocking {
        val online = Online()
        val start = System.nanoTime()
        launch {
            delay(200)
            online.available()
        }
        online.await(10_000)
        val waited = (System.nanoTime() - start) / 1_000_000
        assertTrue("cut short: $waited ms", waited < 2_000)
    }
}
