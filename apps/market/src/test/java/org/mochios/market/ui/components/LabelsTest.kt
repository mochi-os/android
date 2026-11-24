// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.AuditEvent
import org.mochios.market.model.Currency
import org.mochios.market.model.Listing
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Wire values reach the screen as words: a dispute's state, an audit action
 * the Comptroller writes, a status or action this client does not know yet;
 * counts and prices are written in the user's number format, and a count
 * agrees with its noun.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LabelsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private val european = Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))

    private fun text(id: Int) = context.getString(id)

    @Test
    fun `a dispute's state is labelled`() {
        rule.setContent {
            MarketStatusBadge(status = "open")
            MarketStatusBadge(status = "responded")
            MarketStatusBadge(status = "resolved_buyer")
            MarketStatusBadge(status = "resolved_seller")
        }
        rule.onNodeWithText(text(R.string.market_dispute_status_open)).assertExists()
        rule.onNodeWithText(text(R.string.market_dispute_status_responded)).assertExists()
        rule.onNodeWithText(text(R.string.market_dispute_status_resolved_buyer)).assertExists()
        rule.onNodeWithText(text(R.string.market_dispute_status_resolved_seller)).assertExists()
        rule.onAllNodesWithText("resolved_buyer").assertCountEquals(0)
    }

    @Test
    fun `a status this client does not know is Unknown, not its key`() {
        rule.setContent { MarketStatusBadge(status = "on_hold") }
        rule.onNodeWithText(text(R.string.market_status_unknown)).assertExists()
        rule.onAllNodesWithText("on_hold").assertCountEquals(0)
    }

    /** Every action the Comptroller writes on a listing, order, dispute, subscription or auction (2026-11-23). */
    private val written = listOf(
        "listing.appeal_decided", "listing.appeal_submitted", "listing.approved", "listing.cleared",
        "listing.created", "listing.deleted", "listing.published", "listing.rejected", "listing.relisted",
        "listing.removed", "listing.warning",
        "order.chargeback", "order.chargeback_lost", "order.chargeback_won", "order.completed",
        "order.created", "order.paid", "order.refund_failed", "order.refunded", "order.shipped", "order.staff_read",
        "dispute.opened", "dispute.responded", "dispute.resolved_buyer", "dispute.resolved_seller",
        "auction.bid_placed", "auction.cancelled", "auction.created", "auction.ended_sold",
        "auction.ended_unsold", "auction.opened", "auction.payment_overdue",
        "subscription.activated", "subscription.active", "subscription.cancel_scheduled",
        "subscription.cancelled", "subscription.chargeback", "subscription.chargeback_lost",
        "subscription.chargeback_won", "subscription.created", "subscription.past_due",
        "subscription.paused", "subscription.reactivated", "subscription.resumed",
    )

    @Test
    fun `every action the Comptroller writes on a timeline is labelled`() {
        for (action in written) {
            assertNotNull(action, auditActionResource(action))
        }
    }

    @Test
    fun `an action the Comptroller no longer writes has no label`() {
        for (action in listOf("listing.expired", "listing.updated", "order.cancelled", "order.payment_failed", "review.hide")) {
            assertNull(action, auditActionResource(action))
        }
    }

    @Test
    fun `an action this client does not know reads as other activity`() {
        rule.setContent {
            AuditTimeline(events = listOf(AuditEvent(id = "e1", action = "listing.renamed", timestamp = 100)))
        }
        rule.onNodeWithText(text(R.string.market_audit_other)).assertExists()
        rule.onAllNodesWithText("listing.renamed").assertCountEquals(0)
    }

    // The Comptroller names no staff member to a party: such an entry comes
    // with the actor "staff" and no name, and reads as the staff's.
    @Test
    fun `an entry a hidden staff member made reads as staff`() {
        rule.setContent {
            AuditTimeline(events = listOf(AuditEvent(id = "e1", action = "order.refunded", actor = "staff", actorName = "", timestamp = 0)))
        }
        rule.onNodeWithText(text(R.string.market_audit_actor_staff)).assertExists()
    }

    @Test
    fun `one review is a review`() {
        rule.setContent { RatingStars(rating = 4f, count = 1) }
        rule.onNodeWithText("(1 review)").assertExists()
    }

    @Test
    fun `a price and a review count are in the user's number format`() {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides european) {
                Text(formatPrice(123_456, Currency.EUR))
                ListingCard(
                    listing = Listing(id = "l1", title = "Teapot", sellerReviews = 1_234),
                    sellerReviews = 1_234,
                    sellerRating = 4f,
                )
            }
        }
        rule.onNodeWithText("1.234,56", substring = true).assertExists()
        rule.onNodeWithText("(1.234)").assertExists()
    }

    @Test
    fun `a prior refund is one sentence in the user's number format`() {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides european) {
                org.mochios.market.ui.dialog.IssueRefundDialog(
                    open = true,
                    currency = Currency.EUR,
                    remaining = 500,
                    priorRefundedAmount = 123_456,
                    onSubmit = {},
                    onDismiss = {},
                )
            }
        }
        val expected = context.getString(R.string.market_refund_dialog_prior, "€1.234,56")
        rule.onNodeWithText(expected).assertExists()
    }

    @Test
    fun `a bidder is told whether the reserve is met`() {
        rule.setContent {
            PriceDisplay(
                listing = Listing(id = "l1", pricing = org.mochios.market.model.PricingModel.AUCTION, price = 100),
                auction = org.mochios.market.model.Auction(id = "a1", hasReserve = true, reserveMet = false),
            )
        }
        rule.onNodeWithText(text(R.string.market_price_reserve_unmet)).assertExists()
    }

    @Test
    fun `files are listed without a download for someone who cannot take them`() {
        val files = listOf(org.mochios.market.model.Asset(id = "f1", filename = "manual.pdf", size = 2_048))
        rule.setContent { DigitalAssetsList(assets = files) }
        rule.onNodeWithText("manual.pdf").assertExists()
        rule.onAllNodesWithContentDescription(text(R.string.market_asset_download)).assertCountEquals(0)
    }
}
