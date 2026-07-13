// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.lib.toMinorUnits
import org.mochios.market.model.Currency
import org.mochios.market.model.Refund

/**
 * Reason options the dialog presents in the dropdown. The first element
 * is the wire value; the second is a string resource id for the
 * localised label. Stripe's `requested_by_customer` / `fraudulent` /
 * `duplicate` are accepted by the comptroller for partial-refund
 * routing.
 */
private val SELLER_REFUND_REASONS = listOf(
    "requested_by_customer" to R.string.market_refund_dialog_reason_requested,
    "fraudulent" to R.string.market_refund_dialog_reason_fraud,
    "duplicate" to R.string.market_refund_dialog_reason_duplicate,
    "other" to R.string.market_refund_dialog_reason_other,
)

/**
 * Issue-refund dialog. Reason dropdown, optional partial-amount field
 * (blank = full refund), description textarea. Submits via the host
 * screen's ViewModel.
 *
 * @param priorRefundedAmount If the order was already partially refunded,
 *                            shown as an informational note above the form.
 * @param priorRefunds Per-refund history (one row per past partial). When
 *                    non-empty a "Previous refunds" section renders above
 *                    the form with amount, kind, date and optional reason
 *                    / description for each row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueRefundDialog(
    open: Boolean,
    currency: Currency,
    priorRefundedAmount: Long = 0L,
    priorRefunds: List<Refund> = emptyList(),
    submitting: Boolean = false,
    errorMessage: String? = null,
    onSubmit: (amountMinor: Long?, reason: String, description: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return

    var reason by remember { mutableStateOf("requested_by_customer") }
    var amountInput by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var reasonOpen by remember { mutableStateOf(false) }

    LaunchedEffect(open) {
        if (open) {
            reason = "requested_by_customer"
            amountInput = ""
            description = ""
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.market_refund_dialog_title)) },
        text = {
            Column {
                if (priorRefunds.isNotEmpty()) {
                    PriorRefundsSection(
                        refunds = priorRefunds,
                        currency = currency,
                    )
                    Spacer(Modifier.height(12.dp))
                } else if (priorRefundedAmount > 0L) {
                    Text(
                        text = stringResource(R.string.market_refund_dialog_prior_title) +
                            ": " + formatPrice(priorRefundedAmount, currency),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                val reasonLabel = SELLER_REFUND_REASONS.firstOrNull { it.first == reason }?.second
                    ?: R.string.market_refund_dialog_reason_other
                ExposedDropdownMenuBox(
                    expanded = reasonOpen,
                    onExpandedChange = { reasonOpen = it },
                ) {
                    OutlinedTextField(
                        value = stringResource(reasonLabel),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.market_refund_dialog_reason_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonOpen)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = reasonOpen,
                        onDismissRequest = { reasonOpen = false },
                    ) {
                        for ((wireValue, labelRes) in SELLER_REFUND_REASONS) {
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                onClick = {
                                    reason = wireValue
                                    reasonOpen = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text(stringResource(R.string.market_refund_dialog_amount_label)) },
                    supportingText = {
                        Text(stringResource(R.string.market_refund_dialog_amount_help))
                    },
                    singleLine = true,
                    enabled = !submitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = {
                        Text(stringResource(R.string.market_refund_dialog_description_label))
                    },
                    enabled = !submitting,
                    minLines = 3,
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
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    val minor = amountInput.trim().takeIf { it.isNotEmpty() }
                        ?.let { toMinorUnits(it, currency) }
                    onSubmit(minor, reason, description.trim())
                },
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.market_refund_dialog_submitting))
                } else {
                    Text(stringResource(R.string.market_refund_dialog_submit))
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !submitting,
                onClick = { if (!submitting) onDismiss() },
            ) {
                Text(stringResource(R.string.market_refund_dialog_cancel))
            }
        },
    )
}

/**
 * "Previous refunds" section rendered above the form when prior partial
 * refunds exist on the order. One row per refund: amount + kind + date on
 * the top line, optional reason / description underneath.
 */
@Composable
private fun PriorRefundsSection(refunds: List<Refund>, currency: Currency) {
    val format = LocalFormat.current
    Text(
        text = stringResource(R.string.market_refund_dialog_prior_section_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Card(colors = CardDefaults.outlinedCardColors(), modifier = Modifier.fillMaxWidth()) {
        Column {
            refunds.forEachIndexed { index, refund ->
                if (index > 0) HorizontalDivider()
                PriorRefundRow(refund = refund, currency = currency, dateString = format.formatDate(refund.created))
            }
        }
    }
}

@Composable
private fun PriorRefundRow(refund: Refund, currency: Currency, dateString: String) {
    val displayCurrency = refund.currency ?: currency
    val kindLabel = when (refund.kind.lowercase()) {
        "full" -> stringResource(R.string.market_refund_dialog_prior_kind_full)
        "partial" -> stringResource(R.string.market_refund_dialog_prior_kind_partial)
        else -> refund.kind
    }
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = formatPrice(refund.amount, displayCurrency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (kindLabel.isNotBlank()) {
                Text(
                    text = kindLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.fillMaxWidth(0f))
            if (dateString.isNotBlank()) {
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val detail = refund.description.trim().ifBlank { refund.reason.trim() }
        if (detail.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
