// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.DataChip
import org.mochios.android.ui.components.Section
import org.mochios.android.ui.components.Truncate
import org.mochios.forums.R
import org.mochios.forums.model.Forum
import org.mochios.android.R as MochiR

/**
 * Owner "General" tab: identity, banner, AI, and delete sections. Mirrors the
 * feeds General tab layout. The AI section only appears when the account has at
 * least one AI-capable account (`/accounts/list?capability=ai`).
 */
@Composable
fun GeneralTab(
    viewModel: ForumSettingsViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    var bannerDraft by remember(uiState.forum.banner) { mutableStateOf(uiState.forum.banner) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Banner, mode, account and prompts all come from the forum-information load;
    // the AI accounts and prompt defaults are chained off it in the ViewModel.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ForumIdentitySection(
            forum = uiState.forum,
            editable = true,
            onRename = { name -> viewModel.rename(name) },
        )

        // Banner
        Section(
            title = stringResource(R.string.forums_tab_banner),
            description = stringResource(R.string.forums_banner_description),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                OutlinedTextField(
                    value = bannerDraft,
                    onValueChange = { value -> bannerDraft = value },
                    placeholder = { Text(stringResource(R.string.forums_banner_placeholder)) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Save only has something to do once the draft has moved off
                    // what is stored.
                    Button(
                        onClick = {
                            viewModel.saveBanner(bannerDraft)
                            focusManager.clearFocus()
                        },
                        enabled = bannerDraft != uiState.forum.banner,
                    ) {
                        Text(stringResource(MochiR.string.common_save))
                    }
                    // Clear only empties the box — Save is what writes it, the
                    // same as any other edit. Neutral rather than primary: it
                    // undoes typing, it does not commit anything. Absent while
                    // the box is empty: there is nothing to clear.
                    if (bannerDraft.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                bannerDraft = ""
                                focusManager.clearFocus()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Text(stringResource(R.string.forums_clear))
                        }
                    }
                }
            }
        }

        // Delete forum
        Section(
            title = stringResource(R.string.forums_settings_delete),
            description = stringResource(R.string.forums_settings_delete_description),
            headerAlignment = Alignment.CenterVertically,
            action = {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(MochiR.string.common_delete))
                }
            },
            content = {},
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.forums_settings_delete_title),
            message = stringResource(R.string.forums_settings_delete_message),
            confirmLabel = stringResource(R.string.forums_settings_delete),
            dismissLabel = stringResource(MochiR.string.common_cancel),
            isDestructive = true,
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

/**
 * Identity card: forum name (inline editable when [editable]) plus copyable
 * entity id, fingerprint, and server chips. Mirrors feeds'
 * `FeedIdentitySection` — shared by the owner General tab and the read-only
 * view a non-manager gets.
 *
 * @param onRename invoked with the new name when the owner saves an edit;
 *                 ignored when [editable] is false.
 */
@Composable
fun ForumIdentitySection(
    forum: Forum,
    editable: Boolean,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Section(
        title = stringResource(R.string.forums_settings_section_identity),
        modifier = modifier,
    ) {
        IdentityFieldRow(label = stringResource(R.string.forums_settings_field_name)) {
            if (editable) {
                NameEditor(
                    currentName = forum.name,
                    onRename = onRename,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(forum.name)
            }
        }
        IdentityFieldRow(label = stringResource(R.string.forums_settings_field_entity_id)) {
            DataChip(value = forum.id, truncate = Truncate.MIDDLE)
        }
        if (forum.fingerprint.isNotBlank()) {
            IdentityFieldRow(label = stringResource(R.string.forums_settings_field_fingerprint_label)) {
                DataChip(value = forum.fingerprint, truncate = Truncate.MIDDLE)
            }
        }
        if (forum.server.isNotBlank()) {
            IdentityFieldRow(label = stringResource(R.string.forums_settings_field_server)) {
                DataChip(value = forum.server, truncate = Truncate.MIDDLE)
            }
        }
    }
}

/** Identity row with a fixed-width label so values align in a column. */
@Composable
private fun IdentityFieldRow(
    label: String,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        content()
    }
}

/** Inline name display with a pencil that swaps in a text field + confirm/cancel. */
@Composable
private fun NameEditor(
    currentName: String,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember(currentName) { mutableStateOf(currentName) }

    if (isEditing) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = editValue,
                onValueChange = { value -> editValue = value },
                singleLine = true,
                trailingIcon = if (editValue.isNotEmpty()) {
                    {
                        IconButton(onClick = { editValue = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription =
                                    stringResource(R.string.forums_settings_name_clear_cd),
                            )
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                onRename(editValue.trim())
                isEditing = false
            }) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.forums_settings_save_name),
                )
            }
            IconButton(onClick = {
                editValue = currentName
                isEditing = false
            }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.forums_settings_name_cancel_cd),
                )
            }
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            // fill = false keeps the pencil next to the name instead of pushed to
            // the far end, while the weight still lets a long name wrap.
            Text(currentName, modifier = Modifier.weight(1f, fill = false))
            IconButton(
                onClick = {
                    editValue = currentName
                    isEditing = true
                },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.forums_settings_name_edit_cd),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
