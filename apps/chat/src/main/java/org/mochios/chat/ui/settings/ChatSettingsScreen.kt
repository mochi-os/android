// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.CompactTextField
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.DataChip
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.Truncate
import org.mochios.chat.R
import org.mochios.chat.model.ChatMember
import org.mochios.chat.model.ChatStatus
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsScreen(
    onBack: () -> Unit,
    onChatLeft: () -> Unit,
    viewModel: ChatSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(MochiR.string.common_copied)
    var nameDraft by remember(uiState.chat.id) { mutableStateOf(uiState.chat.name) }
    var memberToRemove by remember { mutableStateOf<ChatMember?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddMember by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.chat.name) {
        if (nameDraft.isEmpty()) nameDraft = uiState.chat.name
    }

    LaunchedEffect(uiState.leftOrDeleted) {
        if (uiState.leftOrDeleted) onChatLeft()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.chat.name.takeIf { it.isNotBlank() }
                            ?.let { name ->
                                stringResource(
                                    R.string.chat_settings_screen_title,
                                    name
                                )
                            }
                            ?: stringResource(R.string.chat_settings_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null && uiState.chat.id.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error!!.userMessage(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        Column {
                            SettingsSectionHeader(
                                title = stringResource(R.string.chat_settings_general),
                                description = stringResource(R.string.chat_settings_general_desc),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsFieldRow(label = stringResource(R.string.chat_settings_rename_label)) {
                                if (editingName) {
                                    CompactTextField(
                                        value = nameDraft,
                                        onValueChange = { draft -> nameDraft = draft },
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = {
                                            if (nameDraft.isNotBlank() && nameDraft != uiState.chat.name) {
                                                viewModel.rename(nameDraft)
                                            }
                                            editingName = false
                                        },
                                        enabled = nameDraft.isNotBlank() && !uiState.isSaving,
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = stringResource(R.string.chat_settings_save),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            nameDraft = uiState.chat.name
                                            editingName = false
                                        },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(MochiR.string.common_cancel),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                } else {
                                    Text(
                                        text = uiState.chat.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (uiState.chat.status == ChatStatus.ACTIVE) {
                                        IconButton(
                                            onClick = {
                                                nameDraft = uiState.chat.name
                                                editingName = true
                                            },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = stringResource(R.string.chat_settings_rename),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            SettingsFieldRow(label = stringResource(R.string.chat_settings_id)) {
                                DataChip(value = uiState.chat.id, truncate = Truncate.MIDDLE)
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(uiState.chat.id))
                                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT)
                                            .show()
                                    },
                                    enabled = uiState.chat.id.isNotBlank(),
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
                    }

                    item {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SettingsSectionHeader(
                                title = stringResource(R.string.chat_settings_members),
                                description = stringResource(R.string.chat_settings_members_desc),
                                action = if (uiState.chat.status == ChatStatus.ACTIVE) {
                                    {
                                        OutlinedButton(onClick = {
                                            viewModel.loadFriends()
                                            showAddMember = true
                                        }) {
                                            Text(stringResource(R.string.chat_settings_add_member))
                                        }
                                    }
                                } else {
                                    null
                                },
                            )
                            if (uiState.chat.members.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.chat_members_empty),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    uiState.chat.members.forEach { member ->
                                        MemberRow(
                                            member = member,
                                            isMe = member.id == uiState.identity,
                                            canRemove = uiState.chat.status == ChatStatus.ACTIVE &&
                                                    member.id != uiState.identity,
                                            onRemove = { memberToRemove = member }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.chat.status != ChatStatus.ACTIVE) {
                        item {
                            Button(
                                onClick = { showDeleteDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.chat_settings_delete))
                            }
                        }
                    }
                }
            }
        }
    }

    val removeTitle = stringResource(R.string.chat_settings_remove_member_title)
    val removeMessageTemplate = stringResource(R.string.chat_settings_remove_member_message)
    val removeLabel = stringResource(R.string.chat_settings_remove_member)
    val cancelLabel = stringResource(MochiR.string.common_cancel)

    memberToRemove?.let { member ->
        ConfirmDialog(
            title = removeTitle,
            message = removeMessageTemplate.format(member.name),
            confirmLabel = removeLabel,
            dismissLabel = cancelLabel,
            isDestructive = true,
            onConfirm = {
                viewModel.removeMember(member)
                memberToRemove = null
            },
            onDismiss = { memberToRemove = null }
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.chat_settings_delete_title),
            message = stringResource(R.string.chat_settings_delete_message),
            confirmLabel = stringResource(R.string.chat_settings_delete),
            dismissLabel = cancelLabel,
            isDestructive = true,
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteLocally()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showAddMember) {
        val existingMemberIds = uiState.chat.members.map { it.id }.toSet()
        val candidates = uiState.friends.filter { it.id !in existingMemberIds }
        AddMemberDialog(
            friends = candidates,
            onConfirm = { friendId ->
                viewModel.addMember(friendId)
                showAddMember = false
            },
            onDismiss = { showAddMember = false },
        )
    }
}

@Composable
private fun AddMemberDialog(
    friends: List<org.mochios.chat.model.Friend>,
    onConfirm: (friendId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) friends
    else friends.filter { it.name.contains(query, ignoreCase = true) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_settings_add_member)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.chat_settings_add_member_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Text(
                        stringResource(R.string.chat_settings_add_member_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filtered, key = { it.id }) { f ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable { onConfirm(f.id) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                EntityAvatar(
                                    name = f.name,
                                    src = "/people/${f.id}/-/avatar",
                                    seed = f.id,
                                    size = 24.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(f.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        },
    )
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    description: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (action != null) {
            Spacer(modifier = Modifier.width(8.dp))
            action()
        }
    }
}

@Composable
private fun SettingsFieldRow(
    label: String,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun MemberRow(
    member: ChatMember,
    isMe: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EntityAvatar(
            name = member.name,
            src = "/people/${member.id}/-/avatar",
            seed = member.id,
            size = 32.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = member.name, style = MaterialTheme.typography.bodyLarge)
            if (isMe) {
                Text(
                    text = stringResource(R.string.chat_members_you),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.PersonRemove,
                    contentDescription = stringResource(R.string.chat_settings_remove_member),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
