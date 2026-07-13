// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.ui.components.NotificationBell
import org.mochios.people.R
import org.mochios.people.model.GroupMember
import org.mochios.people.model.GroupMemberType
import org.mochios.android.R as MochiR

/**
 * Group detail screen — mirrors the web `group-detail.tsx` page.
 *
 * Top section shows group name + description, both tappable to open a
 * single-field editor dialog. The overflow menu houses "Delete group".
 * The member list lives below, with an "Add member" action prominent
 * in the top bar and an inline empty state when there are no members.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    onBack: () -> Unit,
    onOpenPerson: (id: String) -> Unit,
    onOpenNotifications: () -> Unit = {},
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var overflowOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                GroupDetailViewModel.Event.NavigateBack -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.group?.name
                            ?: stringResource(R.string.people_group_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
                actions = {
                    NotificationBell(onClick = onOpenNotifications)
                    if (state.group != null) {
                        IconButton(onClick = { viewModel.openAddDialog() }) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = stringResource(R.string.people_member_add),
                            )
                        }
                        Box {
                            IconButton(onClick = { overflowOpen = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.people_group_actions),
                                )
                            }
                            DropdownMenu(
                                expanded = overflowOpen,
                                onDismissRequest = { overflowOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.people_group_delete)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                    },
                                    onClick = {
                                        overflowOpen = false
                                        viewModel.openDeleteConfirm()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && state.group == null -> LoadingContent()
                state.error is MochiError.NotFoundError && state.group == null -> {
                    NotFoundState(
                        title = stringResource(R.string.people_groups_not_found),
                        onBack = onBack,
                    )
                }
                state.error != null && state.group == null -> {
                    ErrorContent(message = state.error!!.userMessage())
                }
                state.group != null -> {
                    GroupDetailContent(
                        state = state,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    // ---- Dialogs ----

    if (state.addDialogOpen) {
        AddMemberDialog(
            state = state,
            onSearch = viewModel::search,
            onPick = viewModel::addMember,
            onDismiss = viewModel::closeAddDialog,
        )
    }

    if (state.editNameOpen) {
        SingleFieldDialog(
            title = stringResource(R.string.people_profile_edit_name),
            label = stringResource(R.string.people_group_name),
            initial = state.group?.name.orEmpty(),
            saving = state.isSaving,
            multiline = false,
            onDismiss = viewModel::closeEditName,
            onSave = viewModel::updateName,
        )
    }

    if (state.editDescOpen) {
        SingleFieldDialog(
            title = stringResource(R.string.people_group_description),
            label = stringResource(R.string.people_group_description),
            initial = state.group?.description.orEmpty(),
            saving = state.isSaving,
            multiline = true,
            onDismiss = viewModel::closeEditDescription,
            onSave = viewModel::updateDescription,
        )
    }

    if (state.deleteConfirmOpen) {
        ConfirmDialog(
            title = stringResource(R.string.people_group_delete),
            message = stringResource(
                R.string.people_group_delete_confirm,
                state.group?.name.orEmpty(),
            ),
            confirmLabel = stringResource(R.string.people_common_delete),
            isDestructive = true,
            onConfirm = { viewModel.delete() },
            onDismiss = viewModel::closeDeleteConfirm,
        )
    }

    val pendingRemoval = state.removeMemberTarget
    if (pendingRemoval != null) {
        ConfirmDialog(
            title = stringResource(R.string.people_member_remove_title),
            message = stringResource(
                R.string.people_member_remove_confirm,
                pendingRemoval.name,
            ),
            confirmLabel = stringResource(R.string.people_member_remove),
            isDestructive = true,
            onConfirm = { viewModel.removeMember(pendingRemoval) },
            onDismiss = viewModel::cancelRemoveMember,
        )
    }
}

@Composable
private fun GroupDetailContent(
    state: GroupDetailViewModel.UiState,
    viewModel: GroupDetailViewModel,
) {
    val group = state.group ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            vertical = 8.dp,
        ),
    ) {
        // Identity section
        item("identity") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.people_profile_identity),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.people_group_actions_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item("name-row") {
            EditableRow(
                label = stringResource(R.string.people_group_name),
                value = group.name,
                onClick = viewModel::openEditName,
            )
        }
        item("desc-row") {
            EditableRow(
                label = stringResource(R.string.people_group_description),
                value = group.description.ifBlank {
                    stringResource(R.string.people_group_description_optional)
                },
                placeholder = group.description.isBlank(),
                onClick = viewModel::openEditDescription,
            )
        }
        item("members-count") {
            FieldRow(
                label = stringResource(R.string.people_group_members_count),
                value = state.members.size.toString(),
            )
        }

        item("members-header-sep") { Spacer(Modifier.height(8.dp)) }

        // Members header
        item("members-header") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.people_group_members),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.people_group_members_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.members.isEmpty()) {
            item("members-empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.people_group_no_members),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.people_member_add_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::openAddDialog) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.people_member_add))
                        }
                    }
                }
            }
        } else {
            items(state.members, key = { it.member }) { member ->
                MemberRow(
                    member = member,
                    onRemove = { viewModel.requestRemoveMember(member) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EditableRow(
    label: String,
    value: String,
    placeholder: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (placeholder) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = stringResource(R.string.people_common_edit),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun MemberRow(
    member: GroupMember,
    onRemove: () -> Unit,
) {
    val typeLabel = stringResource(
        when (member.type) {
            GroupMemberType.USER -> R.string.people_member_type_user
            GroupMemberType.GROUP -> R.string.people_member_type_group
        }
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (member.type) {
            GroupMemberType.USER -> EntityAvatar(
                name = member.name,
                seed = member.member,
                size = 32.dp,
            )
            GroupMemberType.GROUP -> Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = member.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(typeLabel) },
            leadingIcon = {
                Icon(
                    imageVector = when (member.type) {
                        GroupMemberType.USER -> Icons.Default.Person
                        GroupMemberType.GROUP -> Icons.Default.Group
                    },
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(
                    R.string.people_member_remove_with_name,
                    member.name,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SingleFieldDialog(
    title: String,
    label: String,
    initial: String,
    saving: Boolean,
    multiline: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = !multiline,
                minLines = if (multiline) 3 else 1,
                maxLines = if (multiline) 6 else 1,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = !saving,
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.people_common_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text(stringResource(R.string.people_common_cancel))
            }
        },
    )
}
