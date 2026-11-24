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
        "audit.unknown_kind_probe",
        "category.created", "category.deleted", "category.updated",
        "config.thresholds_updated", "config.updated",
        "dispute.opened", "dispute.resolved_buyer", "dispute.resolved_seller", "dispute.responded",
        "listing.appeal_decided", "listing.appeal_submitted", "listing.approved", "listing.cleared",
        "listing.created", "listing.deleted", "listing.published", "listing.rejected", "listing.relisted",
        "listing.removed", "listing.warning",
        "order.chargeback", "order.chargeback_lost", "order.chargeback_won", "order.completed", "order.created",
        "order.paid", "order.refunded", "order.shipped", "order.staff_read",
        "payment.orphaned",
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
