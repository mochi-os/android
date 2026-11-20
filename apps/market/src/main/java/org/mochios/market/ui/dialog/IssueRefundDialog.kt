// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiTextField
import org.mochios.market.R
import org.mochios.market.lib.currencyDecimals
import org.mochios.market.lib.formatPrice
import org.mochios.market.lib.toMinorUnits
import org.mochios.market.model.Currency

/** What the refund dialog's amount field asks for. */
internal sealed interface RefundEntry {
    /** The whole remainder: a blank field, or one naming all of it. Sent as no amount. */
    data object Whole : RefundEntry

    /** Part of the remainder, in minor units. */
    data class Part(val amount: Long) : RefundEntry

    /** Not an amount the order can refund. */
    data object Invalid : RefundEntry
}

/**
 * Read the refund field [text] for an order with [remaining] minor units left
 * to refund, as the web does: blank is the whole remainder; anything else must
 * be a plain amount in whole minor units of [currency], from one minor unit up
 * to the remainder. Zero, a lone mark, a fraction of a minor unit or text the
 * field cannot read is [RefundEntry.Invalid], never a full refund.
 */
internal fun refundEntry(text: String, currency: Currency, remaining: Long): RefundEntry {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return RefundEntry.Whole
    val decimals = currencyDecimals(currency)
    val shape = if (decimals == 0) {
        Regex("""^\d+$""")
    } else {
        Regex("""^(\d+([.,]\d{0,$decimals})?|[.,]\d{1,$decimals})$""")
    }
    if (!shape.matches(trimmed)) return RefundEntry.Invalid
    val amount = toMinorUnits(trimmed, currency)
    return when {
        amount < 1L || amount > remaining -> RefundEntry.Invalid
        amount == remaining -> RefundEntry.Whole
        else -> RefundEntry.Part(amount)
    }
}

/**
 * The seller's refund: an amount, blank for the whole of [remaining]. Issue
 * refund waits until the field holds an amount the order can refund, with the
 * reason under the field; [onSubmit] gets null for the whole remainder.
 */
@Composable
fun IssueRefundDialog(
    open: Boolean,
    currency: Currency,
    remaining: Long,
    priorRefundedAmount: Long = 0L,
    submitting: Boolean = false,
    errorMessage: String? = null,
    onSubmit: (amountMinor: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return

    var amountInput by remember { mutableStateOf("") }

    LaunchedEffect(open) {
        if (open) {
            amountInput = ""
        }
    }

    val entry = refundEntry(amountInput, currency, remaining)
    val invalid = entry == RefundEntry.Invalid

    MochiAlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = stringResource(R.string.market_refund_dialog_title),
        content = {
            Column {
                if (priorRefundedAmount > 0L) {
                    Text(
                        text = stringResource(R.string.market_refund_dialog_prior, formatPrice(priorRefundedAmount, currency)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                MochiTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text(stringResource(R.string.market_refund_dialog_amount_label)) },
                    supportingText = {
                        if (invalid) {
                            Text(
                                stringResource(
                                    R.string.market_refund_dialog_amount_invalid,
                                    formatPrice(1L, currency),
                                    formatPrice(remaining, currency),
                                ),
                            )
                        } else {
                            Text(
                                stringResource(
                                    R.string.market_refund_dialog_amount_remaining,
                                    formatPrice(remaining, currency),
                                ),
                            )
                        }
                    },
                    isError = invalid,
                    singleLine = true,
                    enabled = !submitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmText = if (submitting) {
            stringResource(R.string.market_refund_dialog_submitting)
        } else {
            stringResource(R.string.market_refund_dialog_submit)
        },
        onConfirm = {
            when (val chosen = refundEntry(amountInput, currency, remaining)) {
                RefundEntry.Whole -> onSubmit(null)
                is RefundEntry.Part -> onSubmit(chosen.amount)
                RefundEntry.Invalid -> Unit
            }
        },
        confirmEnabled = !invalid && remaining > 0L,
        confirmLoading = submitting,
        dismissText = stringResource(R.string.market_refund_dialog_cancel),
        dismissEnabled = !submitting,
    )
}
