// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InfiniteList
import org.mochios.market.R
import org.mochios.market.model.MarketThread
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.MarketLayout
import org.mochios.market.ui.components.MarketListNote
import org.mochios.market.ui.components.MarketListPadding
import org.mochios.market.ui.components.MarketListRow
import org.mochios.market.ui.components.MarketListSpacing
import org.mochios.market.ui.components.PersonThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesInboxScreen(
    navController: NavController,
    viewModel: MessagesInboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    MarketLayout(
        navController = navController,
        currentRoute = MarketApp.MESSAGES,
        titleRes = R.string.market_messages_inbox_title,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.error != null && state.threads.isEmpty() -> {
                    ErrorState(
                        error = state.error!!,
                        onRetry = viewModel::refresh,
                    )
                }
                state.threads.isEmpty() && !state.isLoading -> {
                    EmptyInbox()
                }
                else -> {
                    InfiniteList(
                        items = state.threads,
                        isLoading = state.isLoading,
                        hasMore = state.hasMore,
                        onLoadMore = viewModel::loadMore,
                        contentPadding = MarketListPadding,
                        verticalArrangement = MarketListSpacing,
                    ) { thread ->
                        InboxRow(
                            thread = thread,
                            onClick = {
                                navController.navigate(
                                    MarketApp.messageThread(
                                        listingId = thread.listing.toString(),
                                        threadId = thread.id.toString(),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyInbox() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.AutoMirrored.Filled.Message,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.market_messages_inbox_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.market_messages_inbox_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InboxRow(
    thread: MarketThread,
    onClick: () -> Unit,
) {
    val otherEntity = thread.otherEntityId()
    val otherName = thread.otherName.orEmpty().ifBlank { otherEntity.orEmpty() }
    val unread = (thread.unread ?: 0L).toInt()
    val timestamp = thread.lastMessageTime ?: thread.updated
    val timeLabel = LocalFormat.current.formatRelativeTime(timestamp)
    MarketListRow(
        title = otherName,
        onClick = onClick,
        leading = { PersonThumbnail(name = otherName, account = otherEntity) },
        subtitle = thread.title,
        preview = thread.lastMessage,
        bold = unread > 0,
        trailing = {
            if (timeLabel.isNotEmpty()) {
                MarketListNote(text = timeLabel)
            }
            if (unread > 0) {
                Badge { Text(text = unread.toString()) }
            }
        },
    )
}

/**
 * Avatar entity for the row. The screen has no current identity to compare
 * against, so it prefers the buyer; the worst case is the wrong avatar.
 */
private fun MarketThread.otherEntityId(): String? {
    val b = buyer.takeIf { it.isNotEmpty() }
    val s = seller.takeIf { it.isNotEmpty() }
    return b ?: s
}
