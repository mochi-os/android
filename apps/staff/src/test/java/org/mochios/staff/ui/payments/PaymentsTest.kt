// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.payments

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.staff.R
import org.mochios.staff.StaffServer
import org.mochios.staff.StaffServer.Companion.ok
import org.mochios.staff.model.Dispute
import org.mochios.staff.model.OrphanedPayment
import org.mochios.staff.model.Report
import org.mochios.staff.model.ReportListing
import org.mochios.staff.ui.dialog.reportActions
import org.mochios.staff.ui.disputes.DisputeStatus
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Payments that reached no order: listed by seller, amount and refunded state,
 * never by their Stripe id, and refunded only once confirmed and only by an
 * admin. Also the console's other decisions on what it offers: Remove only on
 * a listing report, and a dispute the buyer withdrew reading as withdrawn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PaymentsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private val intent = "pi_3NqZ0000000000000000000"

    private val server = StaffServer { request ->
        val path = request.requestUrl!!.encodedPath
        when {
            path.endsWith("-/payments/orphaned") -> ok(
                """{"payments": [{"id": "$intent", "amount": 1500, "currency": "gbp", "seller": "s1",
                   "seller_name": "Sam", "seller_fingerprint": "abcdefghi", "created": 100, "refunded": false}],
                   "total": 1, "next": ""}""",
            )
            path.endsWith("-/payments/refund") -> ok(
                """{"payment": "$intent", "amount": 1500, "currency": "gbp", "refunds": 1, "failed": 0}""",
            )
            else -> null
        }
    }

    @Before
    fun begin() {
        AppContext.set(context)
        server.start()
    }

    @After
    fun end() = server.stop()

    @Test
    fun `a payment is refunded only once the refund is confirmed`() {
        val model = PaymentsViewModel(server.repository)
        server.until { !model.state.value.isLoading }
        assertEquals(listOf(intent), model.state.value.payments.map { payment -> payment.id })

        model.askRefund(model.state.value.payments.single())
        server.idle()
        assertTrue(server.made("-/payments/refund").isEmpty())

        model.confirmRefund()
        server.until { model.state.value.payments.single().refunded }
        val refund = server.made("-/payments/refund").single()
        assertEquals("POST", refund.method)
        assertEquals("id=$intent", refund.body.readUtf8())
        assertEquals(null, model.state.value.pendingRefund)
    }

    private val payment = OrphanedPayment(
        id = intent, amount = 1500, currency = "gbp", seller = "s1", sellerName = "Sam", created = 100,
    )

    private fun body(admin: Boolean, payments: List<OrphanedPayment>) {
        rule.setContent {
            PaymentsBody(
                state = PaymentsUiState(payments = payments, isLoading = false),
                admin = admin,
                onRefund = {},
                onLoadMore = {},
                onRetry = {},
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun `an admin is offered a refund of a payment not yet refunded, never its Stripe id`() {
        body(admin = true, payments = listOf(payment, payment.copy(id = "pi_2", sellerName = "Ana", refunded = true)))
        rule.onNodeWithText("Sam").assertExists()
        rule.onNodeWithText(context.getString(R.string.staff_payments_not_refunded)).assertExists()
        rule.onNodeWithText(context.getString(R.string.staff_status_refunded)).assertExists()
        rule.onAllNodesWithText(context.getString(R.string.staff_payments_refund)).assertCountEquals(1)
        rule.onAllNodesWithText(intent, substring = true).assertCountEquals(0)
    }

    @Test
    fun `staff who are not admins are offered no refund`() {
        body(admin = false, payments = listOf(payment))
        rule.onNodeWithText("Sam").assertExists()
        rule.onAllNodesWithText(context.getString(R.string.staff_payments_refund)).assertCountEquals(0)
    }

    @Test
    fun `remove is offered on a listing report only`() {
        val user = Report(id = "r1", type = "user", target = "u1")
        val listing = Report(id = "r2", type = "listing", target = "l1", listing = ReportListing(id = "l1"))
        assertEquals(listOf("dismiss", "warn", "suspend", "ban"), reportActions(user))
        assertEquals(listOf("dismiss", "warn", "remove", "suspend", "ban"), reportActions(listing))
    }

    @Test
    fun `a dispute the buyer withdrew reads as withdrawn, not decided for the seller`() {
        rule.setContent {
            DisputeStatus(Dispute(id = "d1", status = "resolved_seller", decider = "buyer"))
        }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.staff_disputes_withdrawn)).assertExists()
        rule.onAllNodesWithText(context.getString(R.string.staff_status_resolved_seller)).assertCountEquals(0)
    }

    @Test
    fun `configuration offers the order completion timeout and the seller storage allowance`() {
        val stored = mapOf("completion_timeout_days" to "30", "seller_storage_megabytes" to "1024", "dispute_window_days" to "120")
        rule.setContent {
            org.mochios.staff.ui.config.ConfigBody(
                state = org.mochios.staff.ui.config.ConfigUiState(server = stored, local = stored, loaded = true),
                onLocalChange = { _, _ -> },
                onSave = {},
            )
        }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.staff_config_field_completion_timeout)).assertExists()
        rule.onNodeWithText("30").assertExists()
        rule.onNodeWithText(context.getString(R.string.staff_config_field_seller_storage)).assertExists()
        rule.onNodeWithText("1024").assertExists()
        rule.onNodeWithText(context.getString(R.string.staff_config_field_dispute_window)).assertExists()
    }

    @Test
    fun `a dispute staff decided for the seller reads as resolved for the seller`() {
        rule.setContent {
            DisputeStatus(Dispute(id = "d1", status = "resolved_seller", decider = "staff"))
        }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.staff_status_resolved_seller)).assertExists()
    }
}
