// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forumlist

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HomeMax
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.EntityListRow
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiTextField
import org.mochios.forums.R
import org.mochios.forums.model.Forum
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumListScreen(
    onForumClick: (String) -> Unit,
    onFindForums: () -> Unit,
    onCreateForum: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ForumListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showOverflow by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.forums_list_title)) },
                actions = {
                    IconButton(onClick = { viewModel.toggleSearch() }) {
                        Icon(
                            if (uiState.showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (uiState.showSearch) {
                                stringResource(R.string.forums_list_close_search)
                            } else {
                                stringResource(R.string.forums_list_search)
                            }
                        )
                    }
                    IconButton(onClick = onFindForums) {
                        Icon(Icons.Default.Explore, contentDescription = stringResource(R.string.forums_list_find))
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                Icons.Default.Sort,
                                contentDescription = stringResource(R.string.forums_list_default_sort)
                            )
                        }
                        MochiDropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            // The list-level control sets the user's GLOBAL default
                            // post sort, distinct from a per-forum override.
                            listOf(
                                "interests" to R.string.forums_sort_interests,
                                "new" to R.string.forums_sort_new,
                                "hot" to R.string.forums_sort_hot,
                                "top" to R.string.forums_sort_top
                            ).forEach { (key, labelRes) ->
                                val current = uiState.defaultSort.ifEmpty { "new" }
                                MochiDropdownMenuItem(
                                    text = { Text(stringResource(labelRes)) },
                                    onClick = {
                                        showSortMenu = false
                                        viewModel.setDefaultSort(key)
                                    },
                                    selected = key == current,
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.forums_list_more))
                        }
                        MochiDropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false }
                        ) {
                            MochiDropdownMenuItem(
                                text = { Text(stringResource(R.string.forums_list_logout)) },
                                onClick = {
                                    showOverflow = false
                                    onLogout()
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateForum) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.forums_list_create))
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.showSearch) {
                    MochiTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        placeholder = { Text(stringResource(R.string.forums_list_search_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                when {
                    uiState.isLoading && uiState.forums.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.error != null && uiState.forums.isEmpty() -> {
                        ErrorState(
                            error = uiState.error!!,
                            onRetry = viewModel::load,
                        )
                    }
                    else -> {
                        val filtered = viewModel.filteredForums()
                        if (filtered.isEmpty()) {
                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                                item {
                                    Box(
                                        Modifier.fillMaxWidth().padding(top = 64.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (uiState.searchQuery.isNotBlank()) {
                                                stringResource(R.string.forums_list_no_matching)
                                            } else {
                                                stringResource(R.string.forums_list_empty)
                                            },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filtered, key = { it.fingerprint.ifEmpty { it.id } }) { forum ->
                                    ForumRow(
                                        forum = forum,
                                        onClick = {
                                            val id = forum.fingerprint.ifEmpty { forum.id }
                                            onForumClick(id)
                                        },
                                        onUnsubscribe = { viewModel.unsubscribe(forum.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForumRow(
    forum: Forum,
    onClick: () -> Unit,
    onUnsubscribe: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showUnsubscribeConfirm by remember { mutableStateOf(false) }
    val forumId = forum.fingerprint.ifEmpty { forum.id }
    val unsubscribeTitle = stringResource(R.string.forums_list_unsubscribe_title)
    val unsubscribeMessage = stringResource(R.string.forums_list_unsubscribe_message)
    val unsubscribeLabel = stringResource(R.string.forums_list_unsubscribe)
    val cancelLabel = stringResource(MochiR.string.common_cancel)

    Box {
        EntityListRow(
            name = forum.name,
            seed = forumId.ifEmpty { forum.id },
            icon = Icons.Default.Forum,
            onClick = onClick,
            onLongClick = { showMenu = true },
            trailing = {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = stringResource(MochiR.string.common_more_options)
                    )
                }
            }
        )
        MochiDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            MochiDropdownMenuItem(
                text = { Text(stringResource(R.string.forums_list_add_to_home)) },
                onClick = {
                    showMenu = false
                    // mochi:/<entity> per claude/plans/mochi-uri-scheme.md.
                    // The "app" extra is a hint the dispatcher uses to skip the
                    // entity → app directory lookup.
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mochi:/$forumId")).apply {
                        setPackage(context.packageName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("app", "forums")
                    }
                    val shortcut = ShortcutInfoCompat.Builder(context, "forum_$forumId")
                        .setShortLabel(forum.name)
                        .setLongLabel(forum.name)
                        .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_forums))
                        .setIntent(intent)
                        .build()
                    ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
                },
                leadingIcon = { Icon(Icons.Outlined.HomeMax, contentDescription = null) },
            )
            MochiDropdownMenuItem(
                text = { Text(unsubscribeLabel) },
                onClick = {
                    showMenu = false
                    showUnsubscribeConfirm = true
                },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            )
        }
    }

    if (showUnsubscribeConfirm) {
        MochiAlertDialog(
            onDismissRequest = { showUnsubscribeConfirm = false },
            title = unsubscribeTitle,
            text = unsubscribeMessage,
            confirmText = unsubscribeLabel,
            onConfirm = {
                showUnsubscribeConfirm = false
                onUnsubscribe()
            },
            destructive = true,
            dismissText = cancelLabel,
        )
    }
}
