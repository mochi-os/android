// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.lib.toMinorUnits
import org.mochios.market.model.Auction
import org.mochios.market.model.Currency

/**
 * The optional maximum is a proxy-bid ceiling: the Comptroller raises the bid
 * by the smallest increment needed to stay ahead, up to it. Blank sends no
 * ceiling.
 */
@Composable
fun PlaceBidDialog(
    open: Boolean,
    auction: Auction?,
    startingPrice: Long,
    currency: Currency,
    submitting: Boolean = false,
    errorMessage: String? = null,
    onSubmit: (amount: Long, ceiling: Long?, currency: Currency) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open || auction == null) return

    var amountInput by remember { mutableStateOf("") }
    var ceilingInput by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val hasBids = auction.bids > 0
    // Not auction.reserve: the server redacts it to 0 for anyone but the
    // seller, so it showed "starting at £0.00" and set the floor to nothing.
    // Mirror the server's own rule (auctions.star): the minimum is the
    // listing price until there are bids, then one unit above the high bid.
    val currentHigh = if (hasBids) auction.bid else startingPrice
    val minimum = if (hasBids) auction.bid + 1 else startingPrice

    LaunchedEffect(open, auction.id) {
        if (open) {
            amountInput = ""
            ceilingInput = ""
            validationError = null
        }
    }

    val invalidAmountText = stringResource(R.string.market_bid_dialog_invalid_amount)
    val invalidCeilingText = stringResource(R.string.market_bid_dialog_invalid_ceiling)

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.market_bid_dialog_title)) },
        text = {
            Column {
                val high = formatPrice(currentHigh, currency)
                val label = if (hasBids) {
                    stringResource(R.string.market_bid_dialog_current_high, high)
                } else {
                    stringResource(R.string.market_bid_dialog_no_bids_yet, high)
                }
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = {
                        amountInput = it
                        validationError = null
                    },
                    label = { Text(stringResource(R.string.market_bid_dialog_amount_label)) },
                    singleLine = true,
                    enabled = !submitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = validationError != null || errorMessage != null,
                    supportingText = {
                        val msg = validationError ?: errorMessage
                        if (!msg.isNullOrBlank()) {
                            Text(text = msg, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = ceilingInput,
                    onValueChange = {
                        ceilingInput = it
                        validationError = null
                    },
                    label = { Text(stringResource(R.string.market_bid_dialog_ceiling_label)) },
                    singleLine = true,
                    enabled = !submitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = validationError != null,
                    supportingText = {
                        Text(stringResource(R.string.market_bid_dialog_ceiling_help))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    val minor = toMinorUnits(amountInput, currency)
                    if (minor <= 0L || minor < minimum) {
                        validationError = invalidAmountText
                        return@TextButton
                    }
                    val ceiling = if (ceilingInput.isBlank()) {
                        null
                    } else {
                        val c = toMinorUnits(ceilingInput, currency)
                        if (c < minor) {
                            validationError = invalidCeilingText
                            return@TextButton
                        }
                        c
                    }
                    onSubmit(minor, ceiling, currency)
                },
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.market_bid_dialog_submitting))
                } else {
                    Text(stringResource(R.string.market_bid_dialog_submit))
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !submitting,
                onClick = { if (!submitting) onDismiss() },
            ) {
                Text(stringResource(R.string.market_bid_dialog_cancel))
            }
        },
    )
}
