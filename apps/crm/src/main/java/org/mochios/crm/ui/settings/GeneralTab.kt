// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.DataChip
import org.mochios.android.ui.components.DeleteSection
import org.mochios.android.ui.components.EditableIdentityRow
import org.mochios.android.ui.components.IdentityRow
import org.mochios.android.ui.components.Section
import org.mochios.android.ui.components.Truncate
import org.mochios.crm.R
import org.mochios.crm.model.Crm
import org.mochios.android.R as MochiR

@Composable
fun GeneralTab(
    uiState: CrmSettingsUiState,
    viewModel: CrmSettingsViewModel,
    onCrmDeleted: () -> Unit
) {
    val crm = uiState.crm ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Section(title = stringResource(R.string.crm_settings_section_identity)) {
            EditableIdentityRow(
                label = stringResource(R.string.crm_create_name),
                value = uiState.name,
                editLabel = stringResource(MochiR.string.common_edit),
                allowBlank = false,
                placeholder = stringResource(R.string.crm_settings_not_set),
                onSave = { value ->
                    viewModel.updateName(value)
                    viewModel.saveCrm()
                }
            )
            EditableIdentityRow(
                label = stringResource(R.string.crm_create_description),
                value = uiState.description,
                editLabel = stringResource(MochiR.string.common_edit),
                singleLine = false,
                placeholder = stringResource(R.string.crm_settings_not_set),
                onSave = { value ->
                    viewModel.updateDescription(value)
                    viewModel.saveCrm()
                }
            )
            IdentityRow(label = stringResource(R.string.crm_settings_entity_id)) {
                DataChip(value = crm.id, truncate = Truncate.MIDDLE)
            }
            if (crm.fingerprint.isNotBlank()) {
                IdentityRow(label = stringResource(R.string.crm_settings_fingerprint)) {
                    DataChip(value = crm.fingerprint, truncate = Truncate.MIDDLE)
                }
            }
            if (!crm.server.isNullOrBlank()) {
                IdentityRow(label = stringResource(R.string.crm_settings_server)) {
                    DataChip(value = crm.server, truncate = Truncate.MIDDLE)
                }
            }
        }

        DeleteSection(
            title = stringResource(R.string.crm_settings_delete_crm),
            buttonLabel = stringResource(MochiR.string.common_delete),
            confirmTitle = stringResource(R.string.crm_settings_delete_confirm_title),
            confirmMessage = stringResource(R.string.crm_settings_delete_confirm_message),
            confirmLabel = stringResource(MochiR.string.common_delete),
            isDeleting = uiState.isDeleting,
            onDelete = { viewModel.deleteCrm { onCrmDeleted() } }
        )
    }
}

/** Read-only identity card, for a viewer who cannot manage the CRM. */
@Composable
fun CrmIdentitySection(
    crm: Crm,
    modifier: Modifier = Modifier
) {
    Section(
        title = stringResource(R.string.crm_settings_section_identity),
        modifier = modifier
    ) {
        IdentityRow(label = stringResource(R.string.crm_create_name)) {
            Text(crm.name)
        }
        if (crm.description.isNotBlank()) {
            IdentityRow(label = stringResource(R.string.crm_create_description)) {
                Text(crm.description)
            }
        }
        IdentityRow(label = stringResource(R.string.crm_settings_entity_id)) {
            DataChip(value = crm.id, truncate = Truncate.MIDDLE)
        }
        if (crm.fingerprint.isNotBlank()) {
            IdentityRow(label = stringResource(R.string.crm_settings_fingerprint)) {
                DataChip(value = crm.fingerprint, truncate = Truncate.MIDDLE)
            }
        }
        if (!crm.server.isNullOrBlank()) {
            IdentityRow(label = stringResource(R.string.crm_settings_server)) {
                DataChip(value = crm.server, truncate = Truncate.MIDDLE)
            }
        }
    }
}
