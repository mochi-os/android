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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.util.providerName
import org.mochios.chat.R
import org.mochios.chat.model.ChatAccount
import org.mochios.chat.model.Friend
import org.mochios.chat.model.addressList
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
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
    val isGroup = if (remote != null) addresses.size > 1 else uiState.selected.size > 1
    val canCreate = if (remote != null) {
        addresses.isNotEmpty() && (!isGroup || uiState.groupName.isNotBlank())
    } else {
        uiState.selected.isNotEmpty() && (!isGroup || uiState.groupName.isNotBlank())
    }

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
                        placeholder = { Text(stringResource(R.string.chat_new_search_placeholder)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    }

                    if (isGroup) {
                        MochiTextField(
                            value = uiState.groupName,
                            onValueChange = viewModel::updateGroupName,
                            label = { Text(stringResource(R.string.chat_new_name)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    val filtered = viewModel.filteredFriends()
                    if (remote != null) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                uiState.isSearchingDirectory ->
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                uiState.searchQuery.isBlank() && uiState.friends.isEmpty() ->
                                    Text(
                                        text = stringResource(R.string.chat_new_no_friends),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                                    selected = friend.id in uiState.selected,
                                    onToggle = {
                                        if (friend.chat.isNotBlank() && uiState.selected.isEmpty()) {
                                            viewModel.openExistingChat(friend.chat)
                                        } else {
                                            viewModel.toggleSelect(friend.id)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    val fallbackName = stringResource(R.string.chat_new_fallback_name)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        MochiButton(
                            onClick = {
                                if (remote != null) {
                                    viewModel.createRemote()
                                } else {
                                    viewModel.createChat(fallbackName = viewModel.selectedFallbackName(fallbackName))
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
    selected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (friend.chat.isNotBlank() && !selected) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.chat_new_existing_chat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
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
