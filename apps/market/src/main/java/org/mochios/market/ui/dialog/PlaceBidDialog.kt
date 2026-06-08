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
 * Place-bid dialog. Shows the current high bid (or starting price when
 * no bids exist yet), accepts a free-text amount, validates that it
 * exceeds the current high, then calls back with the minor-unit value.
 *
 * An optional "Maximum bid" field sets the proxy-bid ceiling: the
 * Comptroller automatically raises the bid by the smallest increment
 * needed to stay ahead, up to this maximum. When left blank, no ceiling
 * is sent and the bid is a plain one-shot bid.
 *
 * The dialog owns its own validation and submitting state; the network
 * call lives in [org.mochios.market.ui.listing.ListingDetailViewModel.placeBid].
 * An external error message ([errorMessage]) is rendered below the input
 * when the server rejects the bid (e.g. proxy-bid outbid, auction closed).
 */
@Composable
fun PlaceBidDialog(
    open: Boolean,
    auction: Auction?,
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
    val currentHigh = if (hasBids) auction.bid else auction.reserve

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
                    if (minor <= 0L || minor <= currentHigh) {
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
