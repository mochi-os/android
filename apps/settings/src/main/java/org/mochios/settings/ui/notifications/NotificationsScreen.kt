// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notifications

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.R
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.notifications.MochiNotification
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.settings.api.NotifCategory
import org.mochios.settings.api.NotifTopic

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
    val snackbar = remember { SnackbarHostState() }

    // A failed recategorisation is reported over the list rather than replacing
    // it: the notifications loaded fine, and only the picker's own call failed.
    LaunchedEffect(uiState.error) {
        val failure = uiState.error
        if (failure != null && uiState.items.isNotEmpty()) {
            snackbar.showSnackbar(failure.userMessage())
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
                        MochiDropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                        ) {
                            MochiDropdownMenuItem(
                                text = { Text(stringResource(R.string.notifications_clear_all)) },
                                onClick = {
                                    showOverflow = false
                                    showClearConfirm = true
                                },
                                leadingIcon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
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

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                                    topic = uiState.topicFor(n),
                                    categories = uiState.categories,
                                    onSetCategory = { topic, id -> viewModel.setCategory(topic, id) },
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
        MochiAlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = stringResource(R.string.notifications_clear_all_title),
            text = stringResource(R.string.notifications_clear_all_message),
            confirmText = stringResource(R.string.notifications_clear_all),
            onConfirm = {
                showClearConfirm = false
                viewModel.clearAll()
            },
            destructive = true,
            dismissText = stringResource(R.string.common_cancel),
        )
    }
}

@Composable
private fun NotificationCard(
    notification: MochiNotification,
    topic: NotifTopic?,
    categories: List<NotifCategory>,
    onSetCategory: (NotifTopic, String?) -> Unit,
    onClick: () -> Unit,
) {
    val format = LocalFormat.current
    MochiCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Always render a leading avatar so rows align: a person photo
                // when there's a sender, otherwise an app-seeded monogram so the
                // circle still signals which app the notification came from.
                val hasSender = notification.sender.isNotBlank()
                EntityAvatar(
                    name = if (hasSender) notification.sender else notification.topic,
                    src = if (hasSender) "/people/${notification.sender}/-/avatar" else null,
                    seed = if (hasSender) notification.sender else notification.topic,
                    size = 36.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = notification.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = format.formatRelativeTime(notification.created),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                // Only offered when the server already holds a topic row: the
                // set-category call requires one and does not create it, so
                // without a row the control could not do anything.
                if (topic != null && categories.isNotEmpty()) {
                    CategoryPicker(
                        topic = topic,
                        categories = categories,
                        onSetCategory = onSetCategory,
                    )
                }
            }
        }
    }
}

/**
 * Moves every notification on this topic to another category, the same change
 * the Topics tab of notification preferences makes - this is the shortcut from
 * a notification you have just received.
 */
@Composable
private fun CategoryPicker(
    topic: NotifTopic,
    categories: List<NotifCategory>,
    onSetCategory: (NotifTopic, String?) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    // "No notifications" is the seeded id "0" and belongs at the end as the
    // opt-out; the rest read alphabetically, which is where a reader looks.
    val ordered = remember(categories) {
        categories.sortedWith(
            compareBy<NotifCategory> { it.id == "0" }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
        )
    }
    Box {
        IconButton(onClick = { menu = true }) {
            Icon(
                Icons.Default.Tune,
                contentDescription = stringResource(R.string.notifications_change_category),
            )
        }
        MochiDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            MochiDropdownMenuItem(
                text = { Text(stringResource(R.string.notifications_category_unassigned)) },
                onClick = {
                    menu = false
                    onSetCategory(topic, null)
                },
                selected = topic.category == null,
            )
            for (category in ordered) {
                MochiDropdownMenuItem(
                    text = { Text(category.label) },
                    onClick = {
                        menu = false
                        onSetCategory(topic, category.id)
                    },
                    selected = topic.category == category.id,
                )
            }
        }
    }
}
