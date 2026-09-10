// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.policy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.chat.R
import org.mochios.android.R as MochiR

private data class PolicyOption(val value: String, val label: String)

@Composable
fun ChatPolicyDialog(
    onDismiss: () -> Unit,
    viewModel: ChatPolicyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            viewModel.consumeSaved()
            onDismiss()
        }
    }

    val options = listOf(
        PolicyOption(value = "friends", label = stringResource(R.string.chat_policy_friends)),
        PolicyOption(value = "anyone", label = stringResource(R.string.chat_policy_anyone)),
    )

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.chat_policy_title),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in options) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !uiState.isLoading) { viewModel.select(option.value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(
                            selected = uiState.policy == option.value,
                            onClick = { viewModel.select(option.value) },
                            enabled = !uiState.isLoading,
                        )
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmText = stringResource(MochiR.string.common_save),
        onConfirm = { viewModel.save() },
        confirmEnabled = !uiState.isLoading,
        confirmLoading = uiState.isSaving,
        dismissText = stringResource(MochiR.string.common_cancel),
        onDismiss = onDismiss,
        dismissEnabled = !uiState.isSaving,
    )
}
