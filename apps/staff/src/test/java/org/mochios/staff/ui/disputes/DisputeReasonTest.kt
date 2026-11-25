// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.disputes

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.staff.R
import org.mochios.staff.model.Dispute
import org.mochios.staff.ui.components.disputeReasonRes
import org.mochios.staff.ui.components.stripeReasonRes
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A dispute's reason reads in words: every reason Stripe documents for a
 * chargeback and every reason the Comptroller accepts from a buyer, in the
 * web's English, and a reason this client has no words for as "Other reason",
 * never as its stored token. Read in British English, the base the web's
 * English is written in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rGB")
class DisputeReasonTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    /** Stripe's dispute reasons, with the English `useStripeChargebackReasons` gives them on the web. */
    private val stripe = mapOf(
        "bank_cannot_process" to "Bank could not process",
        "check_returned" to "Cheque returned",
        "credit_not_processed" to "Credit not processed",
        "customer_initiated" to "Customer initiated",
        "debit_not_authorized" to "Debit not allowed",
        "duplicate" to "Duplicate charge",
        "fraudulent" to "Fraudulent",
        "general" to "General",
        "incorrect_account_details" to "Incorrect account details",
        "insufficient_funds" to "Insufficient funds",
        "noncompliant" to "Non-compliant",
        "product_not_received" to "Product not received",
        "product_unacceptable" to "Product unacceptable",
        "subscription_canceled" to "Subscription cancelled",
        "unrecognized" to "Unknown charge",
    )

    /** The buyer's reasons the Comptroller accepts (`VALID_DISPUTE_REASONS`), with the web's English. */
    private val buyer = mapOf(
        "not_received" to "Not received",
        "not_as_described" to "Not as described",
        "damaged" to "Damaged",
        "unauthorised" to "Payment not made by me",
        "other" to "Other",
    )

    private fun card(dispute: Dispute) {
        AppContext.set(context)
        rule.setContent { DisputeRow(dispute = dispute, onActionClick = {}) }
        rule.waitForIdle()
    }

    private fun dispute(opener: String, reason: String) = Dispute(
        id = "d1",
        order = "o1",
        opener = opener,
        reason = reason,
        status = "open",
        total = 2500,
        currency = "gbp",
        buyer = "u1",
        buyerName = "Duc",
        created = 100,
    )

    @Test
    fun `every reason Stripe documents for a dispute reads in the web's words`() {
        for ((reason, words) in stripe) {
            val label = stripeReasonRes(reason)
            assertNotNull(reason, label)
            assertEquals(reason, words, context.getString(label!!))
        }
    }

    @Test
    fun `every reason the Comptroller accepts from a buyer reads in the web's words`() {
        for ((reason, words) in buyer) {
            val label = disputeReasonRes(reason)
            assertNotNull(reason, label)
            assertEquals(reason, words, context.getString(label!!))
        }
    }

    @Test
    fun `a chargeback's card names its reason`() {
        card(dispute(opener = "stripe", reason = "noncompliant"))
        rule.onNodeWithText("Chargeback: Non-compliant").assertExists()
    }

    @Test
    fun `a chargeback whose reason this client does not know reads as other reason, never its code`() {
        card(dispute(opener = "stripe", reason = "something_new"))
        rule.onNodeWithText(context.getString(R.string.staff_disputes_dialog_title_chargeback, "Other reason")).assertExists()
        rule.onAllNodesWithText("something_new", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("something new", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a buyer's dispute whose reason this client does not know reads as other reason, never its token`() {
        card(dispute(opener = "u1", reason = "something_new"))
        rule.onNodeWithText(context.getString(R.string.staff_disputes_reason_unknown)).assertExists()
        assertEquals("Other reason", context.getString(R.string.staff_disputes_reason_unknown))
        rule.onAllNodesWithText("something_new", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("something new", substring = true).assertCountEquals(0)
    }
}
