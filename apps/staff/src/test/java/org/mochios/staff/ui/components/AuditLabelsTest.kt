// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.format.formatPrice
import org.mochios.android.i18n.AppContext
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.staff.R
import org.mochios.staff.model.AuditEntry
import org.mochios.staff.ui.dashboard.AuditCard
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * An audit entry reads in words: its action by its label, what it concerns by
 * kind and name, who made it, and its data through the module's tables with
 * amounts in their currency and the user's number format.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuditLabelsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val format = Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))

    @Before
    fun begin() = AppContext.set(context)

    /** Every action the Comptroller's `log_audit` calls write, copied from them. */
    private val written = listOf(
        "account.banned", "account.seller_activated", "account.stripe_connected", "account.stripe_disconnected",
        "account.stripe_onboarded", "account.stripe_restricted", "account.suspended", "account.unbanned",
        "account.unsuspended",
        "auction.bid_placed", "auction.cancelled", "auction.created", "auction.ended_sold", "auction.ended_unsold",
        "auction.opened", "auction.payment_overdue",
        "category.created", "category.deleted", "category.updated",
        "config.thresholds_updated", "config.updated",
        "dispute.opened", "dispute.resolved_buyer", "dispute.resolved_seller", "dispute.responded",
        "listing.appeal_decided", "listing.appeal_submitted", "listing.approved", "listing.cleared",
        "listing.created", "listing.deleted", "listing.published", "listing.rejected", "listing.relisted",
        "listing.removed", "listing.warning",
        "order.chargeback", "order.chargeback_lost", "order.chargeback_won", "order.completed", "order.created",
        "order.paid", "order.refund_failed", "order.refunded", "order.shipped", "order.staff_read",
        "payment.orphaned", "payment.refund_failed", "payment.refunded",
        "report.actioned", "report.created",
        "review.created", "review.remove", "review.responded", "review.restore", "review.revealed",
        "staff.team_added", "staff.team_removed", "staff.team_role_changed",
        "subscription.activated", "subscription.active", "subscription.cancel_scheduled", "subscription.cancelled",
        "subscription.chargeback", "subscription.chargeback_lost", "subscription.chargeback_won",
        "subscription.created", "subscription.past_due", "subscription.paused", "subscription.reactivated",
        "subscription.resumed",
        "thread.staff_read",
    )

    private fun detail(action: String, data: String) = auditDetail(action, data, context.resources, format)

    @Test
    fun `every action the Comptroller writes has a label`() {
        for (action in written) {
            assertNotNull("no label for $action", auditActionRes(action))
        }
        assertNull(auditActionRes("listing.renamed"))
    }

    @Test
    fun `an action the Comptroller no longer writes has no label`() {
        val retired = listOf(
            "audit.unknown_kind_probe", "listing.expired", "listing.updated", "order.cancelled",
            "order.payment_failed", "review.hide",
        )
        for (action in retired) {
            assertNull(action, auditActionRes(action))
        }
    }

    @Test
    fun `every reason token the Comptroller writes reads in words`() {
        val reasons = listOf(
            Triple("auction.ended_unsold", "no_bids", R.string.staff_audit_reason_no_bids),
            Triple("auction.ended_unsold", "reserve_not_met", R.string.staff_audit_reason_reserve_not_met),
            Triple("auction.ended_unsold", "no_winning_bid", R.string.staff_audit_reason_no_winning_bid),
            Triple("auction.cancelled", "listing_removed", R.string.staff_audit_reason_listing_removed),
            Triple("auction.cancelled", "payment_unavailable", R.string.staff_audit_reason_payment_unavailable),
            Triple("subscription.cancelled", "checkout_expired", R.string.staff_audit_reason_checkout_expired),
            Triple("subscription.cancelled", "period_ended", R.string.staff_audit_reason_period_ended),
            Triple("subscription.cancel_scheduled", "listing_removed", R.string.staff_audit_reason_listing_removed),
            Triple("account.stripe_disconnected", "deauthorized", R.string.staff_audit_reason_deauthorized),
            Triple("account.stripe_disconnected", "unreachable", R.string.staff_audit_reason_unreachable),
            Triple("account.stripe_disconnected", "duplicate", R.string.staff_audit_reason_duplicate),
            Triple("review.revealed", "timeout", R.string.staff_audit_reason_timeout),
            Triple("subscription.chargeback", "fraudulent", R.string.staff_disputes_stripe_fraudulent),
        )
        for ((action, reason, label) in reasons) {
            assertEquals("$action $reason", context.getString(label), detail(action, """{"reason": "$reason"}"""))
        }
    }

    @Test
    fun `a reason token this client has no words for shows nothing, but a person's words show`() {
        assertEquals("", detail("auction.cancelled", """{"reason": "something_new"}"""))
        assertEquals("", detail("order.chargeback", """{"reason": "timeout"}"""))
        assertEquals("Fake photos", detail("listing.rejected", """{"reason": "Fake photos"}"""))
        assertEquals(context.getString(R.string.staff_reports_reason_spam), detail("account.suspended", """{"reason": "spam"}"""))
    }

    @Test
    fun `every way the Comptroller says an action came about reads in words`() {
        val vias = listOf(
            Triple("order.refunded", "seller", R.string.staff_audit_via_seller),
            Triple("order.refunded", "staff", R.string.staff_audit_via_staff),
            Triple("order.refunded", "stripe", R.string.staff_audit_via_stripe),
            Triple("order.refunded", "reconciled", R.string.staff_audit_via_reconciled),
            Triple("dispute.resolved_buyer", "refund", R.string.staff_audit_via_refund),
            Triple("order.completed", "automatic", R.string.staff_audit_via_automatic),
            Triple("dispute.resolved_seller", "withdrawn", R.string.staff_disputes_withdrawn),
            Triple("order.completed", "dispute_resolved_seller", R.string.staff_status_resolved_seller),
            Triple("account.seller_activated", "stripe_oauth", R.string.staff_audit_action_account_stripe_connected),
            Triple("auction.ended_sold", "instant", R.string.staff_audit_via_instant),
        )
        for ((action, via, label) in vias) {
            assertEquals("$action $via", context.getString(label), detail(action, """{"via": "$via"}"""))
        }
        assertEquals("", detail("order.completed", """{"via": "something_new"}"""))
    }

    @Test
    fun `the new configuration keys are named in a config change`() {
        assertEquals(
            context.getString(R.string.staff_config_field_completion_timeout) + " · 21",
            detail("config.updated", """{"key": "completion_timeout_days", "value": "21"}"""),
        )
        assertEquals(
            context.getString(R.string.staff_config_field_seller_storage) + " · 2048",
            detail("config.updated", """{"key": "seller_storage_megabytes", "value": "2048"}"""),
        )
    }

    @Test
    fun `a staff member reading an order and reading a conversation read apart`() {
        val order = context.getString(auditActionRes("order.staff_read")!!)
        val thread = context.getString(auditActionRes("thread.staff_read")!!)
        assertEquals("Order read by staff", order)
        assertEquals("Conversation read by staff", thread)
        assertNotEquals(order, thread)
    }

    @Test
    fun `a partial refund is its amount of the total, in the currency and the user's digits`() {
        val text = detail("order.refunded", """{"currency": "gbp", "partial": true, "refund_amount": 1500, "total": 3000, "via": "seller_refund"}""")
        val expected = formatPrice(1500, "gbp", format) + " " +
            context.getString(R.string.staff_disputes_meta_refunded_of, formatPrice(3000, "gbp", format))
        assertEquals(expected, text)
        assertTrue(text, text.contains("15,00"))
    }

    @Test
    fun `reasons, report actions and decisions read through the module's tables`() {
        assertEquals(
            context.getString(R.string.staff_disputes_reason_not_received) + " · It never came",
            detail("dispute.opened", """{"reason": "not_received", "description": "It never came"}"""),
        )
        assertEquals(
            "Seen · " + context.getString(R.string.staff_reports_action_warn),
            detail("report.actioned", """{"action": "warn", "notes": "Seen"}"""),
        )
        assertEquals(context.getString(R.string.staff_appeals_upheld_toast), detail("listing.appeal_decided", """{"decision": "upheld"}"""))
        assertEquals(context.getString(R.string.staff_audit_reason_no_bids), detail("auction.ended_unsold", """{"reason": "no_bids"}"""))
        assertEquals(context.getString(R.string.staff_status_hold), detail("listing.cleared", """{"moderation": "hold"}"""))
        assertEquals("Support → Admin", detail("staff.team_role_changed", """{"previous_role": "support", "role": "admin"}"""))
        assertEquals(
            context.getString(R.string.staff_disputes_stripe_fraudulent) + " · " + formatPrice(2500, "usd", format),
            detail("order.chargeback", """{"amount": 2500, "currency": "usd", "fee": 1500, "reason": "fraudulent"}"""),
        )
    }

    @Test
    fun `a resolution is shown as the server resolved it, and a label key not at all`() {
        assertEquals("Refunded by the seller", detail("dispute.resolved_buyer", """{"resolution": "Refunded by the seller", "via": "seller_refund"}"""))
        assertEquals("", detail("dispute.resolved_buyer", """{"resolution": "labels.resolution.seller"}"""))
    }

    private fun entry(
        action: String = "order.staff_read",
        kind: String = "order",
        objectId: String = "2aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456789abcdefghijkl",
        objectName: String = "Teapot",
        objectFingerprint: String = "",
        actor: String = "staff",
        actorName: String = "",
    ) = AuditEntry(
        id = "a1", kind = kind, `object` = objectId, objectName = objectName, objectFingerprint = objectFingerprint,
        actor = actor, actorName = actorName, action = action, timestamp = 100,
    )

    @Test
    fun `a dashboard audit card is titled with the label and names its object by kind and name`() {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides format) { AuditCard(entry = entry()) }
        }
        rule.onNodeWithText("Order read by staff").assertExists()
        rule.onNodeWithText(context.getString(R.string.staff_audit_kind_order) + " · Teapot").assertExists()
        rule.onNodeWithText(context.getString(R.string.staff_audit_actor_staff)).assertExists()
        rule.onAllNodesWithText("order.staff_read", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("2aBcDeFg", substring = true).assertCountEquals(0)
    }

    @Test
    fun `an account is named by its fingerprint when it has no name`() {
        rule.setContent {
            AuditCard(entry = entry(action = "account.banned", kind = "account", objectName = "", objectFingerprint = "abc123defXYZ", actor = "system"))
        }
        rule.onNodeWithText(context.getString(R.string.staff_audit_kind_account) + " · abc-123-def").assertExists()
        rule.onNodeWithText(context.getString(R.string.staff_audit_actor_system)).assertExists()
    }

    @Test
    fun `the timeline names a hidden staff member as staff`() {
        rule.setContent {
            AuditTimelineBody(state = AuditTimelineUiState(isLoading = false, entries = listOf(entry())), onMore = {})
        }
        rule.onNodeWithText(context.getString(R.string.staff_audit_actor_staff), substring = true).assertExists()
    }
}
