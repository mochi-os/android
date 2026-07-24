// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.api.userMessage
import org.mochios.crm.R
import org.mochios.crm.ui.`object`.ConfirmDeleteDialog
import org.mochios.android.R as MochiR

@Composable
fun GeneralTab(
    uiState: CrmSettingsUiState,
    viewModel: CrmSettingsViewModel,
    onCrmDeleted: () -> Unit,
    onUnsubscribed: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showUnsubscribeConfirm by remember { mutableStateOf(false) }
    val isOwner = uiState.crm?.owner == 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = viewModel::updateName,
            label = { Text(stringResource(R.string.crm_create_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.description,
            onValueChange = viewModel::updateDescription,
            label = { Text(stringResource(R.string.crm_create_description)) },
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.error.userMessage(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.saveCrm() },
            enabled = !uiState.isSaving && uiState.name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.crm_settings_save))
            }
        }

        // Entity identity: the CRM's entity ID, fingerprint, and origin server,
        // each copyable — mirrors web's settings entity-ID / fingerprint / server
        // rows.
        uiState.crm?.let { crm ->
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            CrmInfoRow(stringResource(R.string.crm_settings_entity_id), crm.id)
            crm.fingerprint.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(8.dp))
                CrmInfoRow(stringResource(R.string.crm_settings_fingerprint), it)
            }
            crm.server?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(8.dp))
                CrmInfoRow(stringResource(R.string.crm_settings_server), it)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.crm_settings_danger_zone),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (!isOwner) {
            OutlinedButton(
                onClick = { showUnsubscribeConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.crm_settings_unsubscribe))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isOwner) {
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                enabled = !uiState.isDeleting,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.crm_settings_delete_crm))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.crm_settings_delete_confirm_title),
            message = stringResource(R.string.crm_settings_delete_confirm_message),
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteCrm { onCrmDeleted() }
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (showUnsubscribeConfirm) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.crm_settings_unsubscribe_title),
            message = stringResource(R.string.crm_settings_unsubscribe_message),
            onConfirm = {
                showUnsubscribeConfirm = false
                viewModel.unsubscribe { onUnsubscribed() }
            },
            onDismiss = { showUnsubscribeConfirm = false }
        )
    }
}

/** A read-only identity row (label + monospace value) with a copy button. */
@Composable
private fun CrmInfoRow(label: String, value: String) {
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = { clipboard.setText(AnnotatedString(value)) },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = stringResource(MochiR.string.common_copy),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
