// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.buying

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.market.R
import org.mochios.market.model.AuctionStatus
import org.mochios.market.model.Bid
import org.mochios.market.model.BidStatus
import org.mochios.market.model.Currency
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A bid on an auction its seller cancelled is labelled Cancelled, with no
 * time left on it, rather than as an auction the bidder lost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BidRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    // Closes in 2100, so a running auction would still be counting down.
    private fun bid(status: BidStatus, auction: AuctionStatus) = Bid(
        id = "b1",
        auction = "a1",
        listing = "l1",
        amount = 200,
        status = status,
        created = 100,
        title = "Hunting knife",
        currency = Currency.EUR,
        closes = 4_102_444_800,
        auctionStatus = auction,
    )

    private fun show(bid: Bid) {
        AppContext.set(context)
        rule.setContent { BidRow(bid = bid, onClick = { _, _ -> }, onCompletePurchase = {}) }
        rule.waitForIdle()
    }

    private fun text(id: Int) = context.getString(id)

    private fun endsIn() = text(R.string.market_bids_ends_in).substringBefore("%")

    @Test
    fun `a bid on a cancelled auction is labelled Cancelled, not Lost`() {
        show(bid(BidStatus.LOST, AuctionStatus.CANCELLED))
        rule.onNodeWithText(text(R.string.market_status_cancelled)).assertIsDisplayed()
        rule.onAllNodesWithText(text(R.string.market_bids_tab_lost)).assertCountEquals(0)
    }

    @Test
    fun `an earlier bid on a cancelled auction is labelled Cancelled, not Outbid`() {
        show(bid(BidStatus.OUTBID, AuctionStatus.CANCELLED))
        rule.onNodeWithText(text(R.string.market_status_cancelled)).assertIsDisplayed()
        rule.onAllNodesWithText(text(R.string.market_bids_tab_outbid)).assertCountEquals(0)
    }

    @Test
    fun `a cancelled auction shows no time left`() {
        show(bid(BidStatus.LOST, AuctionStatus.CANCELLED))
        rule.onAllNodesWithText(endsIn(), substring = true).assertCountEquals(0)
    }

    @Test
    fun `a running auction still counts down and keeps its bid's status`() {
        show(bid(BidStatus.OUTBID, AuctionStatus.ACTIVE))
        rule.onNodeWithText(text(R.string.market_bids_tab_outbid)).assertIsDisplayed()
        rule.onNodeWithText(endsIn(), substring = true).assertIsDisplayed()
        rule.onAllNodesWithText(text(R.string.market_status_cancelled)).assertCountEquals(0)
    }
}
