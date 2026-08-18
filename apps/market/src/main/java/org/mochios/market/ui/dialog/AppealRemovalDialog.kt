// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.dialog

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.market.R
import org.mochios.market.repository.MarketRepository

/**
 * Appeal-removal dialog. The seller types a reason explaining why the
 * removed listing should be reinstated. The dialog owns its own
 * submission flow (entry-point Hilt resolution → repository call →
 * toast on success/error) so the host My-listings screen doesn't need
 * a dedicated ViewModel slot just for this one mutation.
 */
@Composable
fun AppealRemovalDialog(
    open: Boolean,
    listingId: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit = {},
) {
    if (!open) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val repo = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, AppealRemovalEntryPoint::class.java)
            .marketRepository()
    }

    var reason by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(open) {
        if (open) {
            reason = ""
            submitting = false
            errorMessage = null
        }
    }

    val successText = stringResource(R.string.market_appeal_dialog_success)
    val failureFallback = stringResource(R.string.market_appeal_dialog_failed)

    MochiAlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = stringResource(R.string.market_appeal_dialog_title),
        content = {
            Column {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = {
                        Text(stringResource(R.string.market_appeal_dialog_reason_label))
                    },
                    enabled = !submitting,
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                val err = errorMessage
                if (!err.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmText = if (submitting) {
            stringResource(R.string.market_appeal_dialog_submitting)
        } else {
            stringResource(R.string.market_appeal_dialog_submit)
        },
        onConfirm = {
            scope.launch {
                submitting = true
                errorMessage = null
                try {
                    repo.appealListing(listingId, reason.trim())
                    Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
                    onSuccess()
                    onDismiss()
                } catch (e: Exception) {
                    errorMessage = e.toMochiError().userMessage().ifEmpty { failureFallback }
                } finally {
                    submitting = false
                }
            }
        },
        confirmEnabled = reason.isNotBlank(),
        confirmLoading = submitting,
        dismissText = stringResource(R.string.market_appeal_dialog_cancel),
        dismissEnabled = !submitting,
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppealRemovalEntryPoint {
    fun marketRepository(): MarketRepository
}
