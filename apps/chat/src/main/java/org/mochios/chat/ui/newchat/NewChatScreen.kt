// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.newchat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.util.providerName
import org.mochios.chat.R
import org.mochios.chat.model.ChatAccount
import org.mochios.chat.model.Friend
import org.mochios.chat.model.addressList
import org.mochios.chat.model.personAvatarUrl
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewChatScreen(
    onBack: () -> Unit,
    onChatCreated: (String) -> Unit,
    viewModel: NewChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.createdChatId) {
        uiState.createdChatId?.let {
            onChatCreated(it)
            viewModel.consumeCreatedChat()
        }
    }

    val remote = uiState.chosen
    val addresses = addressList(uiState.addresses)
    val isGroup = if (remote != null) addresses.size > 1 else uiState.picked.size > 1
    val canCreate = if (remote != null) {
        addresses.isNotEmpty() && (!isGroup || uiState.groupName.isNotBlank())
    } else {
        uiState.picked.isNotEmpty() && !uiState.duplicate && !uiState.isCheckingExisting
    }
    val fallbackName = stringResource(R.string.chat_new_fallback_name)
    val defaultName = viewModel.selectedFallbackName(fallbackName)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_new_title)) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null && uiState.friends.isEmpty() -> {
                    ErrorState(
                        error = uiState.error!!,
                        onRetry = viewModel::load,
                    )
                }
                else -> {
                    uiState.error?.let { error ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.medium
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = error.userMessage(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if (uiState.accounts.isNotEmpty()) {
                        NetworkPicker(
                            accounts = uiState.accounts,
                            chosen = uiState.account,
                            onChoose = viewModel::selectAccount,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    if (remote != null) {
                        MochiTextField(
                            value = uiState.addresses,
                            onValueChange = viewModel::updateAddresses,
                            label = { Text(stringResource(R.string.chat_new_addresses)) },
                            placeholder = { Text(addressHint(remote.network)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    } else {
                        MochiTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            placeholder = { Text(stringResource(R.string.chat_settings_add_member_search)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    if (uiState.picked.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            uiState.picked.forEach { friend ->
                                InputChip(
                                    selected = false,
                                    onClick = { viewModel.toggleSelect(friend) },
                                    label = { Text(friend.name) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(
                                                R.string.chat_new_remove_person,
                                                friend.name,
                                            ),
                                            modifier = Modifier.size(InputChipDefaults.IconSize),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    if (uiState.existing.isNotEmpty()) {
                        ExistingChats(
                            chats = uiState.existing,
                            onOpen = viewModel::openExistingChat,
                        )
                    }

                    if (isGroup) {
                        MochiTextField(
                            value = if (remote != null || uiState.groupNameEdited) {
                                uiState.groupName
                            } else {
                                defaultName
                            },
                            onValueChange = viewModel::updateGroupName,
                            label = { Text(stringResource(R.string.chat_new_name)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    val filtered = viewModel.filteredFriends()
                        .filterNot { friend -> friend.id in uiState.selected }
                    if (remote != null) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(top = 32.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            when {
                                uiState.searchQuery.isBlank() && uiState.friends.isEmpty() ->
                                    Text(
                                        text = stringResource(R.string.chat_settings_add_member_no_friends),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                uiState.searchQuery.isBlank() -> Unit
                                else ->
                                    Text(
                                        text = stringResource(R.string.chat_new_no_matches),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            items(filtered, key = { it.id }) { friend ->
                                FriendRow(
                                    friend = friend,
                                    onToggle = { viewModel.toggleSelect(friend) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        MochiButton(
                            onClick = {
                                if (remote != null) {
                                    viewModel.createRemote()
                                } else {
                                    viewModel.createChat(fallbackName = defaultName)
                                }
                            },
                            enabled = canCreate && !uiState.isCreating
                        ) {
                            if (uiState.isCreating) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    if (isGroup) {
                                        stringResource(R.string.chat_new_start_group)
                                    } else {
                                        stringResource(R.string.chat_new_start)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRow(
    friend: Friend,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EntityAvatar(
            name = friend.name,
            src = personAvatarUrl(friend.id),
            seed = friend.id,
            size = 32.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = friend.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ExistingChats(
    chats: List<ExistingChat>,
    onOpen: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.chat_new_existing_with),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        chats.forEach { chat ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                MochiTextButton(onClick = { onOpen(chat.id) }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.chat_new_open))
                }
            }
        }
    }
}

/**
 * An account as the network choice names it: a linked account's network and
 * address, or a hosted fediverse account's address alone.
 */
internal fun accountLabel(account: ChatAccount): String =
    if (account.network in setOf("xmpp", "matrix", "mastodon")) {
        "${providerName(account.network)} · ${account.identifier}"
    } else {
        account.identifier
    }

/** The form of an address on a network, as the addresses field's hint. */
@Composable
private fun addressHint(network: String): String = stringResource(
    when (network) {
        "xmpp" -> R.string.chat_new_address_xmpp
        "matrix" -> R.string.chat_new_address_matrix
        else -> R.string.chat_new_address_fediverse
    },
)

/** Mochi, or one of the user's accounts on another network. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkPicker(
    accounts: List<ChatAccount>,
    chosen: String,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val mochi = stringResource(R.string.chat_new_network_mochi)
    val label = accounts.firstOrNull { it.id == chosen }?.let { accountLabel(it) } ?: mochi
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        MochiTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.chat_new_network)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MochiDropdownMenuItem(
                text = { Text(mochi) },
                onClick = {
                    open = false
                    onChoose("")
                },
            )
            accounts.forEach { account ->
                MochiDropdownMenuItem(
                    text = { Text(accountLabel(account)) },
                    onClick = {
                        open = false
                        onChoose(account.id)
                    },
                )
            }
        }
    }
}
