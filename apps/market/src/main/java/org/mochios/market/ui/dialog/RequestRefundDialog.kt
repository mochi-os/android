// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.market.R

/**
 * Mirrors `useDisputeReasons()` in `apps/market/web/src/config/constants.ts`.
 */
internal val REFUND_REASONS: List<Pair<String, Int>> = listOf(
    "not_received" to R.string.market_refund_reason_not_received,
    "not_as_described" to R.string.market_refund_reason_not_as_described,
    "damaged" to R.string.market_refund_reason_damaged,
    "unauthorised" to R.string.market_refund_reason_unauthorised,
    "other" to R.string.market_refund_reason_other,
)

/**
 * Reason and description only: `orders/dispute` forwards nothing else. The
 * amount belongs to the seller's `orders/refund` ([IssueRefundDialog]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestRefundDialog(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, description: String) -> Unit,
) {
    var reasonExpanded by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("other") }
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
                            MochiDropdownMenuItem(
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
                onClick = { onSubmit(reason, description) },
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
