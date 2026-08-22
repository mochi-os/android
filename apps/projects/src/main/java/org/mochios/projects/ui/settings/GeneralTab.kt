// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.DataChip
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiButtonTone
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.Section
import org.mochios.android.ui.components.Truncate
import org.mochios.projects.R
import org.mochios.projects.model.Project
import org.mochios.android.R as MochiR

/**
 * Owner "General" tab: an editable identity [Section] over the read-only entity
 * chips, plus a delete [Section] whose action sits in the header. Styled to match
 * the forums settings screen. Only a viewer who can manage the project reaches
 * this tab; everyone else gets the read-only [ProjectIdentitySection] and an
 * unsubscribe action from the settings screen, so there is no unsubscribe branch.
 */
@Composable
fun GeneralTab(
    uiState: ProjectSettingsUiState,
    viewModel: ProjectSettingsViewModel,
    onProjectDeleted: () -> Unit
) {
    val project = uiState.project ?: return
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                allowBlank = false,
                onSave = { value ->
                    viewModel.updateName(value)
                    viewModel.saveProject()
                }
            )
            EditableIdentityRow(
                label = stringResource(R.string.projects_create_description),
                value = uiState.description,
                singleLine = false,
                onSave = { value ->
                    viewModel.updateDescription(value)
                    viewModel.saveProject()
                }
            )
            EditableIdentityRow(
                label = stringResource(R.string.projects_create_prefix),
                value = uiState.prefix,
                allowBlank = false,
                transform = { text -> text.uppercase() },
                onSave = { value ->
                    viewModel.updatePrefix(value)
                    viewModel.saveProject()
                }
            )
            IdentityFieldRow(label = stringResource(R.string.projects_settings_field_entity_id)) {
                DataChip(value = project.id, truncate = Truncate.MIDDLE)
            }
            if (project.fingerprint.isNotBlank()) {
                IdentityFieldRow(label = stringResource(R.string.projects_settings_field_fingerprint)) {
                    DataChip(value = project.fingerprint, truncate = Truncate.MIDDLE)
                }
            }
            if (!project.server.isNullOrBlank()) {
                IdentityFieldRow(label = stringResource(R.string.projects_settings_field_server)) {
                    DataChip(value = project.server, truncate = Truncate.MIDDLE)
                }
            }
        }

        Section(
            title = stringResource(R.string.projects_settings_delete_project),
            headerAlignment = Alignment.CenterVertically,
            action = {
                MochiOutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !uiState.isDeleting,
                    tone = MochiButtonTone.Neutral,
                ) {
                    if (uiState.isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(MochiR.string.common_delete))
                    }
                }
            },
            content = {}
        )
    }

    if (showDeleteConfirm) {
        MochiAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = stringResource(R.string.projects_settings_delete_confirm_title),
            text = stringResource(R.string.projects_settings_delete_confirm_message),
            confirmText = stringResource(MochiR.string.common_delete),
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteProject { onProjectDeleted() }
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}

/**
 * Read-only identity card: the project's name, description, prefix, entity id,
 * fingerprint, and server. Shown to a viewer who cannot manage the project,
 * mirroring the forum and feed subscriber views.
 */
@Composable
fun ProjectIdentitySection(
    project: Project,
    modifier: Modifier = Modifier
) {
    Section(
        title = stringResource(R.string.projects_settings_section_identity),
        modifier = modifier
    ) {
        IdentityFieldRow(label = stringResource(R.string.projects_create_name)) {
            Text(project.name)
        }
        if (project.description.isNotBlank()) {
            IdentityFieldRow(label = stringResource(R.string.projects_create_description)) {
                Text(project.description)
            }
        }
        if (project.prefix.isNotBlank()) {
            IdentityFieldRow(label = stringResource(R.string.projects_create_prefix)) {
                Text(project.prefix)
            }
        }
        IdentityFieldRow(label = stringResource(R.string.projects_settings_field_entity_id)) {
            DataChip(value = project.id, truncate = Truncate.MIDDLE)
        }
        if (project.fingerprint.isNotBlank()) {
            IdentityFieldRow(label = stringResource(R.string.projects_settings_field_fingerprint)) {
                DataChip(value = project.fingerprint, truncate = Truncate.MIDDLE)
            }
        }
        if (!project.server.isNullOrBlank()) {
            IdentityFieldRow(label = stringResource(R.string.projects_settings_field_server)) {
                DataChip(value = project.server, truncate = Truncate.MIDDLE)
            }
        }
    }
}

/** Identity row with a fixed-width label so values align in a column. */
@Composable
private fun IdentityFieldRow(
    label: String,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        content()
    }
}

/**
 * Editable identity row: shows the value with an edit pencil on the right; the
 * pencil swaps in an inline text field with confirm/cancel that saves the field
 * immediately. Mirrors the forum title's inline editor.
 *
 * @param onSave    invoked with the trimmed value when the edit is confirmed.
 * @param allowBlank when false, the confirm action is disabled for a blank value.
 * @param transform applied to each keystroke — used to force the prefix uppercase.
 */
@Composable
private fun EditableIdentityRow(
    label: String,
    value: String,
    onSave: (String) -> Unit,
    singleLine: Boolean = true,
    allowBlank: Boolean = true,
    transform: (String) -> String = { text -> text }
) {
    var isEditing by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }

    IdentityFieldRow(label = label) {
        if (isEditing) {
            MochiTextField(
                value = draft,
                onValueChange = { text -> draft = transform(text) },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                modifier = Modifier.weight(1f)
            )
            MochiIconButton(
                onClick = {
                    onSave(draft.trim())
                    isEditing = false
                },
                enabled = allowBlank || draft.isNotBlank()
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(MochiR.string.common_save)
                )
            }
            MochiIconButton(onClick = {
                draft = value
                isEditing = false
            }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(MochiR.string.common_cancel)
                )
            }
        } else {
            // The edit pencil sits right after the value (fill = false), and a
            // blank value reads as an italic "Not set" placeholder instead.
            if (value.isBlank()) {
                Text(
                    text = stringResource(R.string.projects_settings_not_set),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false)
                )
            } else {
                Text(text = value, modifier = Modifier.weight(1f, fill = false))
            }
            MochiIconButton(
                onClick = {
                    draft = value
                    isEditing = true
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(MochiR.string.common_edit),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
