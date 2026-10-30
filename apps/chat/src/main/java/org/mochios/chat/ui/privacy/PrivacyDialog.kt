// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.privacy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.chat.R
import org.mochios.chat.model.ChatPreferences
import org.mochios.android.R as MochiR

@Composable
fun PrivacyDialog(
    onDismiss: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            viewModel.consumeSaved()
            onDismiss()
        }
    }

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.chat_privacy_title),
        content = {
            PrivacyFields(
                state = uiState,
                onChange = viewModel::change,
            )
        },
        confirmText = stringResource(MochiR.string.common_save),
        onConfirm = { viewModel.save() },
        confirmEnabled = uiState.loaded != null && uiState.changed && !uiState.isLoading,
        confirmLoading = uiState.isSaving,
        dismissText = stringResource(MochiR.string.common_cancel),
        onDismiss = onDismiss,
        dismissEnabled = !uiState.isSaving,
    )
}

/** Who may start a chat with the user, and the three things they share. */
@Composable
internal fun PrivacyFields(state: PrivacyUiState, onChange: (ChatPreferences) -> Unit) {
    when {
        state.isLoading -> CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        state.failure != null -> Text(
            text = state.failure.userMessage(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val value = state.value
            Text(
                text = stringResource(R.string.chat_policy_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            listOf(
                "friends" to stringResource(R.string.chat_policy_friends),
                "anyone" to stringResource(R.string.chat_policy_anyone),
            ).forEach { (policy, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.isSaving) { onChange(value.copy(policy = policy)) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(
                        selected = value.policy == policy,
                        onClick = { onChange(value.copy(policy = policy)) },
                        enabled = !state.isSaving,
                    )
                    Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
            SwitchRow(stringResource(R.string.chat_privacy_presence), value.presence) { onChange(value.copy(presence = it)) }
            SwitchRow(stringResource(R.string.chat_privacy_receipts), value.receipts) { onChange(value.copy(receipts = it)) }
            SwitchRow(stringResource(R.string.chat_privacy_typing), value.typing) { onChange(value.copy(typing = it)) }
            state.error?.let { error ->
                Text(
                    text = error.userMessage(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
