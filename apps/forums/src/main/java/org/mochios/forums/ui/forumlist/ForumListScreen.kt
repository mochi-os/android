package org.mochios.forums.ui.forumlist

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HomeMax
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.EntityListRow
import org.mochios.forums.R
import org.mochios.forums.model.Forum
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumListScreen(
    onForumClick: (String) -> Unit,
    onFindForums: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ForumListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showOverflow by remember { mutableStateOf(false) }

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
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.forums_list_more))
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.forums_list_logout)) },
                                onClick = {
                                    showOverflow = false
                                    onLogout()
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showCreateDialog() }) {
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
                    OutlinedTextField(
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
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = uiState.error!!.userMessage(),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
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

    if (uiState.showCreateDialog) {
        CreateForumDialog(
            isCreating = uiState.isCreating,
            onDismiss = { viewModel.hideCreateDialog() },
            onCreate = { name, privacy -> viewModel.createForum(name, privacy) }
        )
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
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.forums_list_add_to_home)) },
                leadingIcon = { Icon(Icons.Default.HomeMax, contentDescription = null) },
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
                }
            )
            DropdownMenuItem(
                text = { Text(unsubscribeLabel) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    showMenu = false
                    showUnsubscribeConfirm = true
                }
            )
        }
    }

    if (showUnsubscribeConfirm) {
        ConfirmDialog(
            title = unsubscribeTitle,
            message = unsubscribeMessage,
            confirmLabel = unsubscribeLabel,
            dismissLabel = cancelLabel,
            isDestructive = true,
            onConfirm = {
                showUnsubscribeConfirm = false
                onUnsubscribe()
            },
            onDismiss = { showUnsubscribeConfirm = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateForumDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf("public") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forums_create_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.forums_create_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.forums_create_privacy),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    FilterChip(
                        selected = privacy == "public",
                        onClick = { privacy = "public" },
                        label = { Text(stringResource(R.string.forums_create_public)) }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = privacy == "private",
                        onClick = { privacy = "private" },
                        label = { Text(stringResource(R.string.forums_create_private)) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, privacy) },
                enabled = name.isNotBlank() && !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(stringResource(R.string.forums_create_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}
