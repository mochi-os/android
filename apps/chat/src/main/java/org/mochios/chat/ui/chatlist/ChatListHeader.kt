// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chatlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.DrawerTitle
import org.mochios.android.ui.components.MochiTextField
import org.mochios.chat.R
import org.mochios.android.R as MochiR

/**
 * The top of the chat drawer as the web has it: the title, a search box that
 * matches chats by name, and chips for all, unread and group chats.
 */
@Composable
fun ChatListHeader(
    query: String,
    filter: ChatFilter,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ChatFilter) -> Unit,
) {
    Column {
        DrawerTitle(stringResource(R.string.chat_list_title))
        MochiTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.chat_forward_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChatFilter.entries.forEach { option ->
                FilterChip(
                    selected = option == filter,
                    onClick = { onFilterChange(option) },
                    label = { Text(filterLabel(option)) },
                )
            }
        }
    }
}

/** The message under the chips when the search and filter leave no chats. */
@Composable
fun ChatListNoMatches(query: String, filter: ChatFilter) {
    val message = when {
        query.isNotBlank() -> stringResource(R.string.chat_settings_add_member_no_match)
        filter == ChatFilter.UNREAD -> stringResource(MochiR.string.notifications_empty_unread)
        filter == ChatFilter.GROUPS -> stringResource(R.string.chat_list_no_groups)
        else -> stringResource(R.string.chat_list_empty)
    }
    Text(
        text = message,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

@Composable
private fun filterLabel(filter: ChatFilter): String = when (filter) {
    ChatFilter.ALL -> stringResource(MochiR.string.notifications_tab_all)
    ChatFilter.UNREAD -> stringResource(MochiR.string.notifications_tab_unread)
    ChatFilter.GROUPS -> stringResource(R.string.chat_list_filter_groups)
}
