// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bug this replaces: `list.all { runCatching { ... }.isSuccess }`, which
 * stops at the first failure. A batch of invitations containing one consumed
 * row (the server answers 400 forever) therefore never attempted anything
 * after it, on every retry.
 */
class BatchTest {

    @Test
    fun `every item is attempted even when an early one fails`() = runBlocking {
        val attempted = mutableListOf<Int>()
        val ok = attemptAll(listOf(1, 2, 3, 4)) { item ->
            attempted.add(item)
            if (item == 1) throw IllegalStateException("consumed")
        }
        assertEquals(listOf(1, 2, 3, 4), attempted)
        assertFalse(ok)
    }

    @Test
    fun `a failure anywhere is reported`() = runBlocking {
        assertFalse(attemptAll(listOf(1, 2, 3)) { if (it == 3) throw IllegalStateException() })
        assertFalse(attemptAll(listOf(1, 2, 3)) { throw IllegalStateException() })
    }

    @Test
    fun `an all-clear run reports success`() = runBlocking {
        val attempted = mutableListOf<Int>()
        assertTrue(attemptAll(listOf(1, 2, 3)) { attempted.add(it) })
        assertEquals(listOf(1, 2, 3), attempted)
    }

    @Test
    fun `an empty batch succeeds without attempting anything`() = runBlocking {
        var calls = 0
        assertTrue(attemptAll(emptyList<Int>()) { calls++ })
        assertEquals(0, calls)
    }

    /** Order matters: these calls mutate a shared list on the server. */
    @Test
    fun `items are attempted in order`() = runBlocking {
        val seen = mutableListOf<String>()
        attemptAll(listOf("a", "b", "c")) { seen.add(it) }
        assertEquals(listOf("a", "b", "c"), seen)
    }
}
