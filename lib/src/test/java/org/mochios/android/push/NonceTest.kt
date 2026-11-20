// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MainActivity is exported, so a `mochi:notification` intent can come from
 * anywhere; the nonce issued when the notification was posted is what proves a
 * real tap. These cover that it is single use, and that the outstanding set
 * follows the tray: one nonce per tag.
 */
class NonceTest {

    private fun nonces(outstanding: List<Outstanding>?) = outstanding?.map { entry -> entry.nonce }

    @Test
    fun `an issued nonce can be consumed once`() {
        val outstanding = noncesAfterIssue(emptyList(), "tag", "abc")
        assertEquals(listOf(Outstanding("tag", "abc")), outstanding)

        val remaining = noncesAfterConsume(outstanding, "abc")
        assertEquals(emptyList<Outstanding>(), remaining)
    }

    /** The property the whole gate rests on: a replayed tap is refused. */
    @Test
    fun `the same nonce cannot be consumed twice`() {
        val outstanding = noncesAfterIssue(emptyList(), "tag", "abc")
        val remaining = noncesAfterConsume(outstanding, "abc")
        assertNotNull(remaining)
        assertNull(noncesAfterConsume(remaining!!, "abc"))
    }

    /** A forged intent carries a value we never issued, or none at all. */
    @Test
    fun `an unknown, empty or absent nonce is refused`() {
        val outstanding = noncesAfterIssue(emptyList(), "tag", "abc")
        assertNull(noncesAfterConsume(outstanding, "guessed"))
        assertNull(noncesAfterConsume(outstanding, ""))
        assertNull(noncesAfterConsume(outstanding, null))
        assertNull(noncesAfterConsume(emptyList(), "abc"))
        // A tag is not a nonce.
        assertNull(noncesAfterConsume(outstanding, "tag"))
    }

    @Test
    fun `consuming one nonce leaves the others outstanding`() {
        var outstanding = noncesAfterIssue(emptyList(), "first", "one")
        outstanding = noncesAfterIssue(outstanding, "second", "two")
        val remaining = noncesAfterConsume(outstanding, "one")
        assertEquals(listOf("two"), nonces(remaining))
        assertNotNull(noncesAfterConsume(remaining!!, "two"))
    }

    /**
     * Posting a tag again replaces its notification, which fires no delete
     * intent: the old nonce goes with it, so a busy thread holds one slot.
     */
    @Test
    fun `posting a tag again replaces its nonce`() {
        var outstanding = noncesAfterIssue(emptyList(), "other", "kept")
        outstanding = noncesAfterIssue(outstanding, "chat", "first")
        repeat(500) { index -> outstanding = noncesAfterIssue(outstanding, "chat", "message$index") }
        assertEquals(listOf("kept", "message499"), nonces(outstanding))
        assertNull("the replaced notification's nonce is spent", noncesAfterConsume(outstanding, "first"))
        assertNotNull(noncesAfterConsume(outstanding, "kept"))
    }

    /** A cancel the app makes itself retires the tag's nonce, and only that. */
    @Test
    fun `retiring a tag drops its nonce alone`() {
        var outstanding = noncesAfterIssue(emptyList(), "first", "one")
        outstanding = noncesAfterIssue(outstanding, "second", "two")
        outstanding = noncesAfterIssue(outstanding, "", "tapped")
        val remaining = noncesAfterRetire(outstanding, "first")
        assertEquals(listOf("two", "tapped"), nonces(remaining))
    }

    /**
     * A nonce no notification carries (a link opened in the app) has no tag;
     * neither a blank retire nor a blank issue may take the others with it.
     */
    @Test
    fun `tagless nonces are never replaced or retired by tag`() {
        var outstanding = noncesAfterIssue(emptyList(), "", "one")
        outstanding = noncesAfterIssue(outstanding, "", "two")
        assertEquals(listOf("one", "two"), nonces(outstanding))
        assertEquals(outstanding, noncesAfterRetire(outstanding, ""))
    }

    /**
     * The reported failure: a tap on a notification died once enough newer
     * ones had been posted, because issuing evicted its nonce. The store has
     * to outlast an ordinary run of notifications between a user seeing one
     * and acting on it.
     */
    @Test
    fun `a tap survives a long run of newer notifications`() {
        var outstanding = noncesAfterIssue(emptyList(), "old", "old")
        repeat(200) { index -> outstanding = noncesAfterIssue(outstanding, "later$index", "later$index") }
        assertNotNull(
            "the older notification is still tappable",
            noncesAfterConsume(outstanding, "old"),
        )
    }

    /**
     * Dismissal retires a nonce through the same consume path as a tap, so a
     * notification the user swipes away stops holding a slot.
     */
    @Test
    fun `dismissing a notification frees its slot`() {
        var outstanding = emptyList<Outstanding>()
        repeat(MAXIMUM_NONCES) { index ->
            outstanding = noncesAfterIssue(outstanding, "tag$index", "nonce$index")
        }
        // The user swipes away the newest without tapping it.
        outstanding = noncesAfterConsume(outstanding, "nonce${MAXIMUM_NONCES - 1}")!!
        assertEquals(MAXIMUM_NONCES - 1, outstanding.size)

        // The next notification therefore evicts nothing.
        outstanding = noncesAfterIssue(outstanding, "fresh", "fresh")
        assertEquals(MAXIMUM_NONCES, outstanding.size)
        assertNotNull("the oldest is still tappable", noncesAfterConsume(outstanding, "nonce0"))
        assertNotNull("and so is the new one", noncesAfterConsume(outstanding, "fresh"))
    }

    /** Untapped notifications must not grow the store without bound. */
    @Test
    fun `issuing past the cap drops the oldest`() {
        var outstanding = emptyList<Outstanding>()
        repeat(MAXIMUM_NONCES + 10) { index ->
            outstanding = noncesAfterIssue(outstanding, "tag$index", "nonce$index")
        }
        assertEquals(MAXIMUM_NONCES, outstanding.size)
        assertNull("the oldest is gone", noncesAfterConsume(outstanding, "nonce0"))
        assertNotNull(
            "the newest survives",
            noncesAfterConsume(outstanding, "nonce${MAXIMUM_NONCES + 9}"),
        )
    }

    /**
     * A list written before nonces were keyed by tag is comma-separated; the
     * notifications already in the tray must still open when tapped.
     */
    @Test
    fun `a list stored before tags is read as tagless`() {
        assertEquals(
            listOf(Outstanding("", "one"), Outstanding("", "two")),
            noncesFromStored("one,two"),
        )
    }
}

/** The stored form is JSON, which needs the Android runtime. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NonceStorageTest {

    @Test
    fun `the outstanding list survives storage`() {
        val outstanding = listOf(Outstanding("a-b-c", "one"), Outstanding("", "two"))
        assertEquals(outstanding, noncesFromStored(noncesToStored(outstanding)))
        assertEquals(emptyList<Outstanding>(), noncesFromStored(""))
    }
}
