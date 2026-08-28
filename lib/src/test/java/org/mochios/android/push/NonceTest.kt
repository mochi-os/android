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

    /**
     * The reported failure: a tap on a notification died once enough newer
     * ones had been posted, because issuing evicted its nonce. The store has
     * to outlast an ordinary run of notifications between a user seeing one
     * and acting on it.
     */
    @Test
    fun `a tap survives a long run of newer notifications`() {
        var outstanding = noncesAfterIssue(emptyList(), "old")
        repeat(200) { index -> outstanding = noncesAfterIssue(outstanding, "later$index") }
        assertNotNull(
            "the older notification is still tappable",
            noncesAfterConsume(outstanding, "old"),
        )
    }

    /**
     * Dismissal retires a nonce through the same consume path as a tap, so a
     * notification the user swipes away stops holding a slot. Without that,
     * only taps ever freed one and the cap was reached by ordinary use - which
     * is what made a tap on an older notification die.
     */
    @Test
    fun `dismissing a notification frees its slot`() {
        var outstanding = emptyList<String>()
        repeat(MAXIMUM_NONCES) { index ->
            outstanding = noncesAfterIssue(outstanding, "nonce$index")
        }
        // The user swipes away the newest without tapping it.
        outstanding = noncesAfterConsume(outstanding, "nonce${MAXIMUM_NONCES - 1}")!!
        assertEquals(MAXIMUM_NONCES - 1, outstanding.size)

        // The next notification therefore evicts nothing.
        outstanding = noncesAfterIssue(outstanding, "fresh")
        assertEquals(MAXIMUM_NONCES, outstanding.size)
        assertNotNull("the oldest is still tappable", noncesAfterConsume(outstanding, "nonce0"))
        assertNotNull("and so is the new one", noncesAfterConsume(outstanding, "fresh"))
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
