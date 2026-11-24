// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.listing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.market.model.Auction
import org.mochios.market.model.AuctionStatus
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingDetailResponse
import org.mochios.market.model.ListingStatus
import org.mochios.market.model.MyOrder
import org.mochios.market.model.MyReservation
import org.mochios.market.model.MySubscription
import org.mochios.market.model.PricingModel

/**
 * The listing page offers only what the listing can take from this viewer, as
 * the web's does, and its files only to someone who can download them.
 */
class OfferTest {

    private fun detail(
        status: ListingStatus = ListingStatus.ACTIVE,
        pricing: PricingModel = PricingModel.FIXED,
        auction: Auction? = null,
        order: MyOrder? = null,
        reservation: MyReservation? = null,
        subscription: MySubscription? = null,
    ) = ListingDetailResponse(
        listing = Listing(id = "l1", seller = "seller", status = status, pricing = pricing),
        auction = auction,
        myOrder = order,
        myReservation = reservation,
        mySubscription = subscription,
    )

    @Test
    fun `an active listing is bought`() {
        assertEquals(Offer.Buy(resume = false), detail().offer("buyer"))
    }

    @Test
    fun `a sold listing offers no purchase, and the viewer's order when they have one`() {
        assertEquals(Offer.None, detail(status = ListingStatus.SOLD).offer("buyer"))
        assertEquals(Offer.Order("o1"), detail(status = ListingStatus.SOLD, order = MyOrder("o1", "paid")).offer("buyer"))
    }

    @Test
    fun `the viewer's own checkout is completed, even once the listing reads sold`() {
        val reserved = detail(status = ListingStatus.SOLD, reservation = MyReservation("r1"))
        assertEquals(Offer.Buy(resume = true), reserved.offer("buyer"))
    }

    @Test
    fun `a listing already subscribed to is managed, not subscribed to again`() {
        val subscription = detail(pricing = PricingModel.SUBSCRIPTION)
        assertEquals(Offer.Subscribe, subscription.offer("buyer"))
        val subscribed = detail(pricing = PricingModel.SUBSCRIPTION, subscription = MySubscription("s1", "active"))
        assertEquals(Offer.Subscribed, subscribed.offer("buyer"))
    }

    @Test
    fun `an auction is bid on only while it runs`() {
        fun auction(status: AuctionStatus, mine: Boolean? = null) =
            detail(pricing = PricingModel.AUCTION, auction = Auction(id = "a1", status = status, opens = 50, mine = mine))
        assertEquals(Offer.Bid, auction(AuctionStatus.ACTIVE).offer("buyer"))
        assertEquals(Offer.Opens(50), auction(AuctionStatus.SCHEDULED).offer("buyer"))
        assertEquals(Offer.Ended, auction(AuctionStatus.ENDED_UNSOLD).offer("buyer"))
        assertEquals(Offer.Ended, auction(AuctionStatus.ENDED_SOLD, mine = false).offer("buyer"))
        assertEquals(Offer.Pay, auction(AuctionStatus.ENDED_SOLD, mine = true).offer("buyer"))
        assertEquals(Offer.Ended, auction(AuctionStatus.CANCELLED).offer("buyer"))
    }

    @Test
    fun `the seller is offered nothing to buy`() {
        assertEquals(Offer.None, detail().offer("seller"))
    }

    @Test
    fun `files are downloadable by the seller, a paying buyer and an entitled subscriber only`() {
        assertTrue(detail().downloadable("seller"))
        assertFalse(detail().downloadable("buyer"))
        assertFalse(detail(order = MyOrder("o1", "pending")).downloadable("buyer"))
        assertTrue(detail(order = MyOrder("o1", "paid")).downloadable("buyer"))
        assertTrue(detail(subscription = MySubscription("s1", "active", entitled = true)).downloadable("buyer"))
        assertFalse(detail(subscription = MySubscription("s1", "paused")).downloadable("buyer"))
    }

    // The Comptroller grants a subscription's files while it is live and paid
    // through a date still to come, and answers that as entitled.
    @Test
    fun `a subscriber's files follow the Comptroller's entitlement, not the status`() {
        assertFalse(detail(subscription = MySubscription("s1", "active", entitled = false)).downloadable("buyer"))
        assertTrue(detail(subscription = MySubscription("s1", "past_due", entitled = true)).downloadable("buyer"))
    }

    @Test
    fun `entitled is read from the listing's answer`() {
        val response = com.google.gson.Gson().fromJson(
            """{"listing": {"id": "l1"}, "my_subscription": {"id": "s1", "status": "past_due", "entitled": true}}""",
            ListingDetailResponse::class.java,
        )
        assertTrue(response.mySubscription!!.entitled)
        assertTrue(response.downloadable("buyer"))
    }
}
