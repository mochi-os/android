// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.lib.toMinorUnits
import org.mochios.market.model.Currency

/**
 * The five buyer-side refund reasons (mirrors `useDisputeReasons()` in
 * `apps/market/web/src/config/constants.ts`). Pairs wire value with the
 * Android string-resource id so the dropdown rows pick up the
 * translation from the locale catalog at render time.
 */
internal val REFUND_REASONS: List<Pair<String, Int>> = listOf(
    "not_received" to R.string.market_refund_reason_not_received,
    "not_as_described" to R.string.market_refund_reason_not_as_described,
    "damaged" to R.string.market_refund_reason_damaged,
    "unauthorised" to R.string.market_refund_reason_unauthorised,
    "other" to R.string.market_refund_reason_other,
)

/**
 * Buyer-facing dialog for requesting a refund.
 *
 * Reason dropdown defaults to `other`; amount defaults to the full order
 * [orderTotal] (in minor units) so the buyer gets a sane preselected
 * value but can lower it for a partial refund. The submit callback
 * receives `(amountMinor, reasonWireValue, description)` so the parent
 * VM can post to `orders/refund` via [org.mochios.market.repository.MarketRepository.refundOrder].
 *
 * Re-used by both the buyer "Request refund" trigger on
 * [org.mochios.market.ui.buying.PurchaseDetailScreen] and (with seller-
 * specific copy supplied by a sibling [IssueRefundDialog]) the seller
 * flow on the selling detail screen — the wire endpoint is the same.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestRefundDialog(
    orderTotal: Long,
    currency: Currency,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (amountMinor: Long, reason: String, description: String) -> Unit,
) {
    var reasonExpanded by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("other") }
    val initialAmount = remember(orderTotal, currency) { formatPrice(orderTotal, currency) }
    var amountInput by remember { mutableStateOf(initialAmount) }
    var description by remember { mutableStateOf("") }

    val reasonLabel = stringResource(
        REFUND_REASONS.firstOrNull { it.first == reason }?.second
            ?: R.string.market_refund_reason_other,
    )

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.market_refund_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.market_refund_body))
                ExposedDropdownMenuBox(
                    expanded = reasonExpanded,
                    onExpandedChange = { reasonExpanded = it },
                ) {
                    OutlinedTextField(
                        value = reasonLabel,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.market_refund_reason_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = reasonExpanded,
                        onDismissRequest = { reasonExpanded = false },
                    ) {
                        REFUND_REASONS.forEach { (wire, labelRes) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                onClick = {
                                    reason = wire
                                    reasonExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text(stringResource(R.string.market_refund_amount_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(0.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.market_refund_details_label)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !submitting,
                onClick = {
                    val amountMinor = toMinorUnits(amountInput, currency)
                        .takeIf { it > 0 } ?: orderTotal
                    onSubmit(amountMinor, reason, description)
                },
            ) {
                Text(stringResource(R.string.market_refund_submit))
            }
        },
        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) {
                Text(stringResource(org.mochios.android.R.string.common_cancel))
            }
        },
    )
}
