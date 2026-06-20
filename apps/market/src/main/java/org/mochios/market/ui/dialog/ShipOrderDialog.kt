// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.market.R
import org.mochios.market.lib.COMMON_CARRIERS

/**
 * Ship-order dialog. Carrier picker (common carriers + free-form Other),
 * tracking number, optional tracking URL. The dialog is purely visual —
 * the network call lives in the host screen's ViewModel so that screen's
 * UI state stays the source of truth for "is this order shipped yet".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShipOrderDialog(
    open: Boolean,
    initialCarrier: String = "",
    initialTracking: String = "",
    initialUrl: String = "",
    submitting: Boolean = false,
    errorMessage: String? = null,
    onSubmit: (carrier: String, tracking: String, url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return

    var carrier by remember(initialCarrier) { mutableStateOf(initialCarrier) }
    var customCarrier by remember { mutableStateOf("") }
    var tracking by remember(initialTracking) { mutableStateOf(initialTracking) }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var carrierMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(open) {
        if (open) {
            carrier = initialCarrier
            customCarrier = ""
            tracking = initialTracking
            url = initialUrl
        }
    }

    val otherLabel = stringResource(R.string.market_sale_carrier_other)

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.market_ship_dialog_title)) },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = carrierMenuOpen,
                    onExpandedChange = { carrierMenuOpen = it },
                ) {
                    OutlinedTextField(
                        value = carrier.ifBlank { otherLabel },
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.market_sale_carrier_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = carrierMenuOpen)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = carrierMenuOpen,
                        onDismissRequest = { carrierMenuOpen = false },
                    ) {
                        for (c in COMMON_CARRIERS) {
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    carrier = c
                                    carrierMenuOpen = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(otherLabel) },
                            onClick = {
                                carrier = ""
                                carrierMenuOpen = false
                            },
                        )
                    }
                }
                if (carrier.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customCarrier,
                        onValueChange = { customCarrier = it },
                        label = {
                            Text(stringResource(R.string.market_sale_carrier_custom_label))
                        },
                        singleLine = true,
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tracking,
                    onValueChange = { tracking = it },
                    label = { Text(stringResource(R.string.market_sale_tracking_label)) },
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.market_sale_tracking_url_label)) },
                    singleLine = true,
                    enabled = !submitting,
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
                    val resolved = carrier.ifBlank { customCarrier }
                    onSubmit(resolved.trim(), tracking.trim(), url.trim())
                },
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.market_sale_ship_submitting))
                } else {
                    Text(stringResource(R.string.market_ship_dialog_submit))
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !submitting,
                onClick = { if (!submitting) onDismiss() },
            ) {
                Text(stringResource(R.string.market_ship_dialog_cancel))
            }
        },
    )
}
