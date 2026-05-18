package org.mochios.crm.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.api.userMessage
import org.mochios.crm.R
import org.mochios.crm.ui.`object`.ConfirmDeleteDialog

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

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.prefix,
            onValueChange = { viewModel.updatePrefix(it.uppercase()) },
            label = { Text(stringResource(R.string.crm_create_prefix)) },
            singleLine = true,
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
