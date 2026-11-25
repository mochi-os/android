// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.market.R

/**
 * A dispute's wire `reason` as a label. A Stripe chargeback (`opener ==
 * "stripe"`) carries Stripe's reason codes; a buyer's refund request carries
 * the market's own. A code this client does not know reads as "Other reason",
 * never as the code.
 */
@Composable
fun disputeReasonLabel(reason: String, opener: String): String {
    val mapped = if (opener == "stripe") chargebackReasonRes(reason) else refundReasonRes(reason)
    return stringResource(mapped ?: R.string.market_dispute_reason_unknown)
}

/** A buyer's reason for a refund request, from the five the Comptroller accepts. */
@StringRes
internal fun refundReasonRes(reason: String): Int? = when (reason) {
    "not_received" -> R.string.market_refund_reason_not_received
    "not_as_described" -> R.string.market_refund_reason_not_as_described
    "damaged" -> R.string.market_refund_reason_damaged
    "unauthorised" -> R.string.market_refund_reason_unauthorised
    "other" -> R.string.market_refund_reason_other
    else -> null
}

/** Stripe's reason code for a chargeback: every reason Stripe documents for a dispute. */
@StringRes
internal fun chargebackReasonRes(reason: String): Int? = when (reason) {
    "fraudulent" -> R.string.market_chargeback_reason_fraudulent
    "duplicate" -> R.string.market_chargeback_reason_duplicate
    "general" -> R.string.market_chargeback_reason_general
    "subscription_canceled" -> R.string.market_chargeback_reason_subscription_canceled
    "unrecognized" -> R.string.market_chargeback_reason_unrecognized
    "product_not_received" -> R.string.market_chargeback_reason_product_not_received
    "product_unacceptable" -> R.string.market_chargeback_reason_product_unacceptable
    "credit_not_processed" -> R.string.market_chargeback_reason_credit_not_processed
    "customer_initiated" -> R.string.market_chargeback_reason_customer_initiated
    "debit_not_authorized" -> R.string.market_chargeback_reason_debit_not_authorized
    "incorrect_account_details" -> R.string.market_chargeback_reason_incorrect_account_details
    "insufficient_funds" -> R.string.market_chargeback_reason_insufficient_funds
    "noncompliant" -> R.string.market_chargeback_reason_noncompliant
    "bank_cannot_process" -> R.string.market_chargeback_reason_bank_cannot_process
    "check_returned" -> R.string.market_chargeback_reason_check_returned
    else -> null
}
