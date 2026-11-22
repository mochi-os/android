// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.chat.R
import org.mochios.chat.model.ChatStatus
import org.mochios.chat.ui.chat.ForwardRow
import org.mochios.chat.ui.chatlist.ChatFilter
import org.mochios.chat.ui.chatlist.ChatListNoMatches
import org.mochios.chat.ui.chatlist.ChatListViewModel
import org.mochios.android.R as MochiR

/**
 * The choice of chat for what another app shares: the user's chats, as the
 * drawer lists them, that a message can be sent in, and a new chat.
 * [onChoose] opens the chosen chat with the share in its message box, and
 * [onNewChat] starts one that opens so; nothing is sent until the user sends
 * it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatShareScreen(
    onBack: () -> Unit,
    onChoose: (String) -> Unit,
    onNewChat: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val pinned by viewModel.pinned.collectAsState()
    val chats = remember(uiState, pinned) {
        viewModel.filteredChats().filter { chat -> chat.status == ChatStatus.ACTIVE }
    }
    val active = remember(uiState.chats) { uiState.chats.any { chat -> chat.status == ChatStatus.ACTIVE } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(MochiR.string.share_chat)) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(MochiR.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            MochiTextField(
                value = uiState.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.chat_forward_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNewChat)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.size(12.dp))
                Text(text = stringResource(R.string.chat_list_new), style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider()
            when {
                chats.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(chats, key = { chat -> chat.id }) { chat ->
                        ForwardRow(chat.name, busy = false, enabled = true) { onChoose(chat.id) }
                    }
                }
                uiState.error != null -> Column(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = uiState.error?.userMessage().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    MochiOutlinedButton(onClick = viewModel::load) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(MochiR.string.common_retry))
                    }
                }
                !uiState.loaded || uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                active -> ChatListNoMatches(query = uiState.query, filter = ChatFilter.ALL)
                else -> Text(
                    text = stringResource(R.string.chat_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }
}
