// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * MainActivity is exported, so a `mochi:notification` intent can come from
 * anywhere; the nonce issued when the notification was posted is what proves a
 * real tap. These cover that it is single use.
 */
class NonceTest {

    @Test
    fun `an issued nonce can be consumed once`() {
        val outstanding = noncesAfterIssue(emptyList(), "abc")
        assertEquals(listOf("abc"), outstanding)

        val remaining = noncesAfterConsume(outstanding, "abc")
        assertEquals(emptyList<String>(), remaining)
    }

    /** The property the whole gate rests on: a replayed tap is refused. */
    @Test
    fun `the same nonce cannot be consumed twice`() {
        val outstanding = noncesAfterIssue(emptyList(), "abc")
        val remaining = noncesAfterConsume(outstanding, "abc")
        assertNotNull(remaining)
        assertNull(noncesAfterConsume(remaining!!, "abc"))
    }

    /** A forged intent carries a value we never issued, or none at all. */
    @Test
    fun `an unknown, empty or absent nonce is refused`() {
        val outstanding = noncesAfterIssue(emptyList(), "abc")
        assertNull(noncesAfterConsume(outstanding, "guessed"))
        assertNull(noncesAfterConsume(outstanding, ""))
        assertNull(noncesAfterConsume(outstanding, null))
        assertNull(noncesAfterConsume(emptyList(), "abc"))
    }

    @Test
    fun `consuming one nonce leaves the others outstanding`() {
        var outstanding = noncesAfterIssue(emptyList(), "one")
        outstanding = noncesAfterIssue(outstanding, "two")
        val remaining = noncesAfterConsume(outstanding, "one")
        assertEquals(listOf("two"), remaining)
        assertNotNull(noncesAfterConsume(remaining!!, "two"))
    }

    /** Untapped notifications must not grow the store without bound. */
    @Test
    fun `issuing past the cap drops the oldest`() {
        var outstanding = emptyList<String>()
        repeat(MAXIMUM_NONCES + 10) { index ->
            outstanding = noncesAfterIssue(outstanding, "nonce$index")
        }
        assertEquals(MAXIMUM_NONCES, outstanding.size)
        assertNull("the oldest is gone", noncesAfterConsume(outstanding, "nonce0"))
        assertNotNull(
            "the newest survives",
            noncesAfterConsume(outstanding, "nonce${MAXIMUM_NONCES + 9}"),
        )
    }
}
