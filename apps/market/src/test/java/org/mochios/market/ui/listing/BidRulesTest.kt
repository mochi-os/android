// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.listing

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.market.model.Auction
import org.mochios.market.ui.dialog.reachesInstant

/**
 * The bid rules the listing page applies as the Comptroller does
 * (auctions.star): the minimum follows the current bid, and a bid or maximum
 * that reaches the buy-now price is the purchase.
 */
class BidRulesTest {

    // A Comptroller that sends no count of bids left it at 0: the page read a
    // live auction as unbid, showed the starting price, and offered a minimum
    // the server refused.
    @Test
    fun `the minimum follows the current bid, whatever the count says`() {
        val bidden = Auction(bid = 1500, bids = 0)
        assertTrue(bidden.bidden)
        assertEquals(1501L, bidden.minimum(startingPrice = 1000))
        val unbid = Auction(bid = 0, bids = 0)
        assertFalse(unbid.bidden)
        assertEquals(1000L, unbid.minimum(startingPrice = 1000))
    }

    @Test
    fun `the count of bids is read from the listing's answer`() {
        val auction = Gson().fromJson("""{"id": "a1", "bid": 1500, "bids": 3}""", Auction::class.java)
        assertEquals(3L, auction.bids)
        assertEquals(1501L, auction.minimum(startingPrice = 1000))
    }

    @Test
    fun `a bid or a maximum at the buy-now price is the purchase`() {
        val auction = Auction(instant = 5000)
        assertTrue(reachesInstant(auction, amount = 5000, ceiling = null))
        assertTrue(reachesInstant(auction, amount = 6000, ceiling = null))
        assertTrue(reachesInstant(auction, amount = 2000, ceiling = 5000))
        assertFalse(reachesInstant(auction, amount = 2000, ceiling = 4999))
        assertFalse(reachesInstant(auction, amount = 4999, ceiling = null))
    }

    @Test
    fun `an auction with no buy-now price has no purchase to reach`() {
        assertFalse(reachesInstant(Auction(instant = 0), amount = 1_000_000, ceiling = 2_000_000))
    }
}
