// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiTextField
import org.mochios.market.R
import org.mochios.market.ui.components.MarketStatusBadge

/**
 * The seller's answer to a buyer's refund request: one text box, which is all
 * the server takes, as on the web.
 */
@Composable
fun DisputeResponseDialog(
    open: Boolean,
    disputeStatus: String,
    submitting: Boolean = false,
    errorMessage: String? = null,
    onSubmit: (response: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return

    var response by remember { mutableStateOf("") }

    LaunchedEffect(open) {
        if (open) {
            response = ""
        }
    }

    MochiAlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = stringResource(R.string.market_dispute_dialog_title),
        content = {
            Column {
                if (disputeStatus.isNotBlank()) {
                    MarketStatusBadge(status = disputeStatus)
                    Spacer(Modifier.height(12.dp))
                }
                MochiTextField(
                    value = response,
                    onValueChange = { response = it },
                    label = { Text(stringResource(R.string.market_dispute_dialog_response_label)) },
                    enabled = !submitting,
                    minLines = 4,
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
            stringResource(R.string.market_dispute_dialog_submitting)
        } else {
            stringResource(R.string.market_dispute_dialog_submit)
        },
        onConfirm = { onSubmit(response.trim()) },
        confirmEnabled = response.isNotBlank(),
        confirmLoading = submitting,
        dismissText = stringResource(R.string.market_dispute_dialog_cancel),
        dismissEnabled = !submitting,
    )
}
