// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.market.model.Dispute
import org.mochios.market.ui.buying.withdrawable

/**
 * What the sale and purchase pages show of a dispute: a chargeback's stored
 * description is not the buyer's account, a dispute the buyer withdrew says
 * so, and only the buyer's own open or answered dispute can be withdrawn.
 */
class DisputeShownTest {

    // A chargeback is stored with an empty description now; older ones carry
    // the Comptroller's English, which no client shows.
    @Test
    fun `a chargeback's description is not shown`() {
        val chargeback = Dispute(
            opener = "stripe",
            reason = "fraudulent",
            description = "Chargeback initiated by buyer's bank for 1500 gbp",
            status = "open",
        )
        assertNull(disputeDescription(chargeback))
    }

    @Test
    fun `a buyer's description is shown`() {
        assertEquals("Nothing came.", disputeDescription(Dispute(opener = "b1", description = "Nothing came.")))
        assertNull(disputeDescription(Dispute(opener = "b1", description = " ")))
    }

    @Test
    fun `a dispute the buyer withdrew says so`() {
        assertTrue(disputeWithdrawn(Dispute(status = "resolved_seller", decider = "buyer")))
        assertFalse(disputeWithdrawn(Dispute(status = "resolved_seller", decider = "staff")))
        assertFalse(disputeWithdrawn(Dispute(status = "open")))
    }

    @Test
    fun `the decider is read from the order's answer`() {
        val dispute = Gson().fromJson("""{"id": "d1", "status": "resolved_seller", "decider": "buyer"}""", Dispute::class.java)
        assertTrue(disputeWithdrawn(dispute))
    }

    @Test
    fun `the buyer withdraws only their own open or answered dispute`() {
        assertTrue(withdrawable(Dispute(opener = "b1", status = "open")))
        assertTrue(withdrawable(Dispute(opener = "b1", status = "responded")))
        assertFalse(withdrawable(Dispute(opener = "stripe", status = "open")))
        assertFalse(withdrawable(Dispute(opener = "b1", status = "resolved_buyer")))
        assertFalse(withdrawable(Dispute(opener = "b1", status = "resolved_seller")))
    }
}
