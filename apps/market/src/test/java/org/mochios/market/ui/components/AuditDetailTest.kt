// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.format.formatPrice
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.market.model.AuditEvent
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * An audit entry's detail line reads in words, as web `formatAuditDetail`
 * writes it (`audit-labels.test.tsx`): the reason each action is written
 * with, the words a person wrote, a listing's moderation, a report's action,
 * how an action came about and its amounts; a token this client has no words
 * for is left out, never shown as itself. Read in British English, the base
 * the web's English is written in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rGB")
class AuditDetailTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val format = Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))

    private fun detail(action: String, data: String) = auditDetail(action, data, context.resources, format)

    @Test
    fun `has words for every reason token the Comptroller writes`() {
        val reasons = listOf(
            Triple("auction.ended_unsold", "no_bids", "No bids placed"),
            Triple("auction.ended_unsold", "reserve_not_met", "Reserve not met"),
            Triple("auction.ended_unsold", "no_winning_bid", "No winning bid"),
            Triple("auction.cancelled", "listing_removed", "Listing removed"),
            Triple("auction.cancelled", "payment_unavailable", "Payment unavailable"),
            Triple("subscription.cancelled", "checkout_expired", "Checkout expired"),
            Triple("subscription.cancelled", "period_ended", "Subscription period ended"),
            Triple("subscription.cancel_scheduled", "listing_removed", "Listing removed"),
            Triple("account.stripe_disconnected", "deauthorized", "Stripe access revoked"),
            Triple("account.stripe_disconnected", "unreachable", "Stripe account unreachable"),
            Triple("account.stripe_disconnected", "duplicate", "Stripe account linked to another account"),
            Triple("review.revealed", "timeout", "Review period ended"),
            Triple("dispute.opened", "damaged", "Damaged"),
            Triple("order.chargeback", "duplicate", "Duplicate charge"),
            Triple("subscription.chargeback", "fraudulent", "Fraudulent"),
            Triple("order.chargeback", "noncompliant", "Non-compliant"),
        )
        for ((action, reason, words) in reasons) {
            assertEquals("$action $reason", words, detail(action, """{"reason": "$reason"}"""))
        }
    }

    @Test
    fun `shows an unknown reason token as nothing, never as itself`() {
        assertNull(detail("auction.cancelled", """{"reason": "something_new"}"""))
        assertNull(detail("order.chargeback", """{"reason": "timeout"}"""))
    }

    @Test
    fun `shows the words a person wrote as their reason`() {
        assertEquals("Fake photos", detail("listing.rejected", """{"reason": "Fake photos"}"""))
    }

    @Test
    fun `names a moderation state in words, and no state it does not know`() {
        assertEquals("moderation: Held for review", detail("listing.published", """{"moderation": "hold"}"""))
        assertNull(detail("listing.published", """{"moderation": "something_new"}"""))
    }

    @Test
    fun `prints a report's action in words, and no other action value`() {
        assertEquals("Seller warned", detail("report.actioned", """{"action": "warn"}"""))
        assertNull(detail("listing.removed", """{"action": "warn"}"""))
    }

    @Test
    fun `has words for every way the Comptroller says an action came about`() {
        val vias = listOf(
            Triple("order.refunded", "seller", "Refunded by the seller"),
            Triple("order.refunded", "staff", "Refunded by staff"),
            Triple("order.refunded", "stripe", "Refund made on Stripe"),
            Triple("order.refunded", "reconciled", "Refund confirmed by Stripe"),
            Triple("dispute.resolved_buyer", "refund", "Settled by a refund"),
            Triple("order.completed", "automatic", "Completed automatically"),
            Triple("dispute.resolved_seller", "withdrawn", "Withdrawn by the buyer"),
            Triple("order.completed", "dispute_resolved_seller", "Resolved (seller)"),
            Triple("account.seller_activated", "stripe_oauth", "Stripe connected"),
            Triple("auction.ended_sold", "instant", "Instant purchase"),
        )
        for ((action, via, words) in vias) {
            assertEquals("$action $via", words, detail(action, """{"via": "$via"}"""))
        }
        assertNull(detail("order.completed", """{"via": "something_new"}"""))
    }

    @Test
    fun `a partial refund is its amount of the total, in the currency and the user's digits`() {
        val text = detail("order.refunded", """{"currency": "gbp", "partial": true, "refund_amount": 1500, "total": 3000, "via": "seller"}""")
        val expected = formatPrice(1500, "gbp", format) + " of " + formatPrice(3000, "gbp", format) + " — Refunded by the seller"
        assertEquals(expected, text)
        assertEquals(true, text!!.contains("15,00"))
    }

    @Test
    fun `the fields read in the web's order`() {
        assertEquals(
            "Not received — It never came",
            detail("dispute.opened", """{"reason": "not_received", "description": "It never came"}"""),
        )
        assertEquals(
            "Fraudulent — " + formatPrice(2500, "usd", format),
            detail("order.chargeback", """{"amount": 2500, "currency": "usd", "fee": 1500, "reason": "fraudulent"}"""),
        )
        assertEquals("Royal Mail: AB123", detail("order.shipped", """{"carrier": "Royal Mail", "tracking": "AB123"}"""))
        assertEquals("Appeal upheld", detail("listing.appeal_decided", """{"decision": "upheld"}"""))
        assertNull(detail("order.paid", ""))
        assertNull(detail("order.paid", "not json"))
    }

    @Test
    fun `the timeline shows the detail after the action's label`() {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides format) {
                AuditTimeline(
                    events = listOf(
                        AuditEvent(id = "e1", action = "auction.ended_unsold", data = """{"reason": "reserve_not_met"}""", timestamp = 100),
                        AuditEvent(id = "e2", action = "auction.cancelled", data = """{"reason": "something_new"}""", timestamp = 100),
                    ),
                )
            }
        }
        rule.onNodeWithText("Auction ended unsold: Reserve not met").assertExists()
        rule.onNodeWithText("Auction cancelled").assertExists()
        rule.onAllNodesWithText("something_new", substring = true).assertCountEquals(0)
    }
}
