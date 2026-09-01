// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.settings

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
import org.mochios.android.ui.components.ConfirmActionSection
import org.mochios.android.ui.components.EditableIdentityRow
import org.mochios.android.ui.components.IdentityRow
import org.mochios.android.ui.components.Section
import org.mochios.android.ui.components.Truncate
import org.mochios.projects.R
import org.mochios.projects.model.Project
import org.mochios.android.R as MochiR

/**
 * Owner "General" tab: editable identity plus delete. Reached only by a viewer
 * who can manage the project; others get [ProjectIdentitySection].
 */
@Composable
fun GeneralTab(
    uiState: ProjectSettingsUiState,
    viewModel: ProjectSettingsViewModel,
    onProjectDeleted: () -> Unit
) {
    val project = uiState.project ?: return
    val editLabel = stringResource(MochiR.string.common_edit)
    val notSet = stringResource(R.string.projects_settings_not_set)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Section(title = stringResource(R.string.projects_settings_section_identity)) {
            EditableIdentityRow(
                label = stringResource(R.string.projects_create_name),
                value = uiState.name,
                editLabel = editLabel,
                allowBlank = false,
                placeholder = notSet,
                onSave = { value ->
                    viewModel.updateName(value)
                    viewModel.saveProject()
                }
            )
            EditableIdentityRow(
                label = stringResource(R.string.projects_create_description),
                value = uiState.description,
                editLabel = editLabel,
                singleLine = false,
                placeholder = notSet,
                onSave = { value ->
                    viewModel.updateDescription(value)
                    viewModel.saveProject()
                }
            )
            EditableIdentityRow(
                label = stringResource(R.string.projects_create_prefix),
                value = uiState.prefix,
                editLabel = editLabel,
                allowBlank = false,
                placeholder = notSet,
                transform = { text -> text.uppercase() },
                onSave = { value ->
                    viewModel.updatePrefix(value)
                    viewModel.saveProject()
                }
            )
            IdentityRow(label = stringResource(R.string.projects_settings_field_entity_id)) {
                DataChip(value = project.id, truncate = Truncate.MIDDLE)
            }
            if (project.fingerprint.isNotBlank()) {
                IdentityRow(
                    label = stringResource(R.string.projects_settings_field_fingerprint)
                ) {
                    DataChip(value = project.fingerprint, truncate = Truncate.MIDDLE)
                }
            }
            if (!project.server.isNullOrBlank()) {
                IdentityRow(label = stringResource(R.string.projects_settings_field_server)) {
                    DataChip(value = project.server, truncate = Truncate.MIDDLE)
                }
            }
        }

        ConfirmActionSection(
            title = stringResource(R.string.projects_settings_delete_project),
            buttonLabel = stringResource(MochiR.string.common_delete),
            confirmTitle = stringResource(R.string.projects_settings_delete_confirm_title),
            confirmMessage = stringResource(R.string.projects_settings_delete_confirm_message),
            confirmLabel = stringResource(MochiR.string.common_delete),
            isBusy = uiState.isDeleting,
            onConfirm = { viewModel.deleteProject { onProjectDeleted() } }
        )
    }
}

/** Read-only identity card, for a viewer who cannot manage the project. */
@Composable
fun ProjectIdentitySection(
    project: Project,
    modifier: Modifier = Modifier
) {
    Section(
        title = stringResource(R.string.projects_settings_section_identity),
        modifier = modifier
    ) {
        IdentityRow(label = stringResource(R.string.projects_create_name)) {
            Text(project.name)
        }
        if (project.description.isNotBlank()) {
            IdentityRow(label = stringResource(R.string.projects_create_description)) {
                Text(project.description)
            }
        }
        if (project.prefix.isNotBlank()) {
            IdentityRow(label = stringResource(R.string.projects_create_prefix)) {
                Text(project.prefix)
            }
        }
        IdentityRow(label = stringResource(R.string.projects_settings_field_entity_id)) {
            DataChip(value = project.id, truncate = Truncate.MIDDLE)
        }
        if (project.fingerprint.isNotBlank()) {
            IdentityRow(label = stringResource(R.string.projects_settings_field_fingerprint)) {
                DataChip(value = project.fingerprint, truncate = Truncate.MIDDLE)
            }
        }
        if (!project.server.isNullOrBlank()) {
            IdentityRow(label = stringResource(R.string.projects_settings_field_server)) {
                DataChip(value = project.server, truncate = Truncate.MIDDLE)
            }
        }
    }
}
