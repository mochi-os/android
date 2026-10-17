// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.market.model.Dispute

/**
 * The sale page notes beside a chargeback's fee what Stripe did with it, as
 * the web does: refunded in full or in part, kept once the buyer won, or
 * nothing while it is undecided.
 */
class FeeOutcomeTest {

    private fun chargeback(status: String = "open", fee: Long = 2000, refunded: Int = 0) =
        Dispute(opener = "stripe", status = status, fee = fee, feeRefunded = refunded)

    @Test
    fun `a buyer's dispute or a chargeback with no fee shows none`() {
        assertNull(feeOutcome(Dispute(opener = "buyer", status = "open", fee = 2000)))
        assertNull(feeOutcome(chargeback(fee = 0)))
    }

    @Test
    fun `an undecided chargeback shows the fee alone`() {
        assertEquals(FeeOutcome.PENDING, feeOutcome(chargeback()))
    }

    @Test
    fun `a won chargeback has its fee refunded in full or in part`() {
        assertEquals(FeeOutcome.REFUNDED, feeOutcome(chargeback("resolved_seller", refunded = 2000)))
        assertEquals(FeeOutcome.PARTIAL, feeOutcome(chargeback("resolved_seller", refunded = 500)))
    }

    @Test
    fun `a lost chargeback's fee is kept by Stripe`() {
        assertEquals(FeeOutcome.KEPT, feeOutcome(chargeback("resolved_buyer")))
    }
}
