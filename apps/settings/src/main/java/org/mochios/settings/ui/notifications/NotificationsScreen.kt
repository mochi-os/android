// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.R
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.notifications.MochiNotification
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.EntityAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showOverflow by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (uiState.unreadCount > 0) {
                        IconButton(onClick = { viewModel.markAllRead() }) {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = stringResource(R.string.notifications_mark_all_read),
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.common_more_options),
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notifications_clear_all)) },
                                onClick = {
                                    showOverflow = false
                                    showClearConfirm = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        // Badge count is derived from the items so it stays accurate as
        // individual notifications are read (the server unreadCount only
        // refreshes on load / mark-all-read).
        val unreadCount = uiState.items.count { it.isUnread }
        val displayItems = when (uiState.tab) {
            NotificationsTab.UNREAD -> uiState.items.filter { it.isUnread }
            NotificationsTab.ALL -> uiState.items
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = uiState.tab.ordinal) {
                Tab(
                    selected = uiState.tab == NotificationsTab.UNREAD,
                    onClick = { viewModel.setTab(NotificationsTab.UNREAD) },
                    text = {
                        Text(
                            if (unreadCount > 0) {
                                stringResource(R.string.notifications_tab_unread_count, unreadCount)
                            } else {
                                stringResource(R.string.notifications_tab_unread)
                            },
                        )
                    },
                )
                Tab(
                    selected = uiState.tab == NotificationsTab.ALL,
                    onClick = { viewModel.setTab(NotificationsTab.ALL) },
                    text = { Text(stringResource(R.string.notifications_tab_all)) },
                )
            }

            when {
                uiState.isLoading && uiState.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                displayItems.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(
                                if (uiState.tab == NotificationsTab.UNREAD) {
                                    R.string.notifications_empty_unread
                                } else {
                                    R.string.notifications_empty
                                },
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(displayItems, key = { it.id }) { n ->
                                NotificationCard(
                                    notification = n,
                                    onClick = {
                                        if (n.read == 0L) viewModel.markRead(n.id)
                                        if (n.link.isNotBlank()) onOpenLink(n.link)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.notifications_clear_all_title),
            message = stringResource(R.string.notifications_clear_all_message),
            confirmLabel = stringResource(R.string.notifications_clear_all),
            dismissLabel = stringResource(R.string.common_cancel),
            isDestructive = true,
            onConfirm = {
                showClearConfirm = false
                viewModel.clearAll()
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

@Composable
private fun NotificationCard(
    notification: MochiNotification,
    onClick: () -> Unit,
) {
    val format = LocalFormat.current
    val container = if (notification.read == 0L) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val avatarSrc = if (notification.sender.isNotBlank()) {
                "/people/${notification.sender}/-/avatar"
            } else null
            EntityAvatar(
                name = notification.sender.ifBlank { notification.app },
                src = avatarSrc,
                seed = notification.sender.ifBlank { notification.app },
                size = 36.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (notification.read == 0L) FontWeight.SemiBold else FontWeight.Normal,
                )
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (notification.app.isNotBlank()) {
                        Text(
                            text = notification.app,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = format.formatRelativeTime(notification.created),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (notification.count > 1) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 6.dp),
                        ) {
                            Text(
                                text = "×${notification.count}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}
