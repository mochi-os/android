// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forum

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
import androidx.compose.material.icons.automirrored.filled.Logout
import android.content.Context
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.push.SystemNotifications
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.FeatureDrawerItem
import org.mochios.android.ui.components.FeatureListDrawer
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.ui.components.HtmlContent
import org.mochios.android.ui.components.NewItemsPill
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.ui.components.NotificationBell
import org.mochios.forums.R
import org.mochios.forums.model.Post
import org.mochios.forums.ui.forumlist.ForumListViewModel
import org.mochios.forums.ui.router.FORUMS_FEATURE
import org.mochios.android.R as MochiR

/**
 * Forum detail screen wrapped in a [FeatureListDrawer]. The drawer holds
 * the user's forum list (so swiping in from the left switches forums
 * directly without an intervening list page) plus actions (Find forums,
 * Logout). When [forumId] is empty (first launch with no recorded
 * last-viewed), the drawer auto-opens over a "pick a forum" placeholder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumScreen(
    forumId: String,
    onSelectForum: (String) -> Unit,
    onPostClick: (String, String) -> Unit,
    onNewPost: (String) -> Unit,
    onFindForums: () -> Unit,
    onSettings: (String) -> Unit,
    onNavigateToSaved: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onLogout: () -> Unit,
    listViewModel: ForumListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(
        if (forumId.isEmpty()) DrawerValue.Open else DrawerValue.Closed
    )
    val drawerScope = rememberCoroutineScope()
    val listUiState by listViewModel.uiState.collectAsState()
    var showAbout by remember { mutableStateOf(false) }

    LaunchedEffect(forumId) {
        if (forumId.isNotBlank()) {
            LastViewedStore.set(context, FORUMS_FEATURE, forumId)
            SystemNotifications.cancelFor(context, "forums", forumId)
        }
    }

    val drawerItems = remember(listUiState.forums) {
        listViewModel.filteredForums().map { forum ->
            FeatureDrawerItem(
                id = forum.fingerprint.ifEmpty { forum.id },
                title = forum.name,
                icon = Icons.Default.Forum,
            )
        }
    }

    FeatureListDrawer(
        drawerState = drawerState,
        items = drawerItems,
        selectedId = forumId,
        onItemClick = { item ->
            drawerScope.launch { drawerState.close() }
            if (item.id != forumId) onSelectForum(item.id)
        },
        actions = {
            ListItem(
                modifier = Modifier.clickable {
                    drawerScope.launch { drawerState.close() }
                    onFindForums()
                },
                headlineContent = { Text(stringResource(R.string.forums_list_find)) },
                leadingContent = { Icon(Icons.Default.Search, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
            ListItem(
                modifier = Modifier.clickable {
                    drawerScope.launch { drawerState.close() }
                    onLogout()
                },
                headlineContent = { Text(stringResource(R.string.forums_list_logout)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
            ListItem(
                modifier = Modifier.clickable {
                    drawerScope.launch { drawerState.close() }
                    showAbout = true
                },
                headlineContent = { Text(stringResource(MochiR.string.about_label)) },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        },
    ) {
        if (forumId.isEmpty()) {
            ForumDrawerPlaceholder(
                onOpenDrawer = { drawerScope.launch { drawerState.open() } },
            )
        } else {
            ForumContent(
                onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                onPostClick = onPostClick,
                onNewPost = onNewPost,
                onSettings = onSettings,
                onNavigateToSaved = onNavigateToSaved,
                onOpenNotifications = onOpenNotifications,
            )
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForumDrawerPlaceholder(onOpenDrawer: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.forums_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.forums_list_title))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.forums_list_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForumContent(
    onOpenDrawer: () -> Unit,
    onPostClick: (String, String) -> Unit,
    onNewPost: (String) -> Unit,
    onSettings: (String) -> Unit,
    onNavigateToSaved: () -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: ForumViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val savedIds by viewModel.savedIds.collectAsState()
    val newPostsCount by viewModel.newPostsCount.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    val forumIdForCallbacks = uiState.forum.fingerprint.ifEmpty { uiState.forum.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.forum.name.ifBlank { stringResource(R.string.forums_loading) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = stringResource(R.string.forums_list_title)
                        )
                    }
                },
                actions = {
                    NotificationBell(onClick = onOpenNotifications)
                    IconButton(onClick = onNavigateToSaved) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = stringResource(R.string.forums_saved_title)
                        )
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.forums_sort_label))
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            listOf(
                                "new" to R.string.forums_sort_new,
                                "hot" to R.string.forums_sort_hot,
                                "top" to R.string.forums_sort_top,
                                "interests" to R.string.forums_sort_interests
                            ).forEach { (key, labelRes) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(labelRes)) },
                                    onClick = {
                                        showSortMenu = false
                                        viewModel.setSort(key)
                                    }
                                )
                            }
                        }
                    }
                    if (uiState.canManage && uiState.forum.id.isNotEmpty()) {
                        IconButton(onClick = { onSettings(forumIdForCallbacks) }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.forums_settings)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.forum.id.isNotEmpty()) {
                FloatingActionButton(onClick = { onNewPost(forumIdForCallbacks) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.forums_new_post))
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
          Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.forum.bannerHtml.isNotBlank()) {
                ForumBanner(
                    bannerHtml = uiState.forum.bannerHtml,
                    forumId = uiState.forum.id,
                )
            }
            if (uiState.tags.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (uiState.currentTag != null) {
                        item(key = "clear") {
                            androidx.compose.material3.FilterChip(
                                selected = false,
                                onClick = { viewModel.setTagFilter(null) },
                                label = { Text(stringResource(R.string.forums_tag_clear)) },
                            )
                        }
                    }
                    items(uiState.tags, key = { it.label }) { tag ->
                        androidx.compose.material3.FilterChip(
                            selected = uiState.currentTag == tag.label,
                            onClick = {
                                viewModel.setTagFilter(
                                    if (uiState.currentTag == tag.label) null else tag.label
                                )
                            },
                            label = { Text(tag.label) },
                        )
                    }
                }
            }
            when {
                uiState.isLoading && uiState.posts.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error is MochiError.NotFoundError && uiState.posts.isEmpty() -> {
                    NotFoundState(
                        title = stringResource(R.string.forums_forum_not_found),
                        onBack = onOpenDrawer,
                    )
                }
                uiState.error != null && uiState.posts.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(uiState.error!!.userMessage(), color = MaterialTheme.colorScheme.error)
                    }
                }
                uiState.posts.isEmpty() -> {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(top = 64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(R.string.forums_no_posts),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                else -> {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    val pillScope = rememberCoroutineScope()
                    Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.posts, key = { it.id }) { post ->
                            PostCard(
                                post = post,
                                isSaved = savedIds.contains(post.id),
                                onClick = { onPostClick(forumIdForCallbacks, post.id) },
                                onVote = { vote -> viewModel.votePost(post.id, vote) },
                                onToggleSave = { viewModel.toggleSave(post) },
                            )
                        }
                        if (uiState.hasMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                    if (uiState.isLoadingMore) {
                                        CircularProgressIndicator()
                                    } else {
                                        TextButton(onClick = { viewModel.loadMore() }) {
                                            Text(stringResource(R.string.forums_load_more))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    NewItemsPill(
                        count = newPostsCount,
                        label = pluralStringResource(
                            R.plurals.forums_new_posts, newPostsCount, newPostsCount
                        ),
                        onClick = {
                            viewModel.showNewPosts()
                            pillScope.launch { listState.animateScrollToItem(0) }
                        },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    }
                }
            }
          }
        }
    }
}

@Composable
private fun PostCard(
    post: Post,
    isSaved: Boolean,
    onClick: () -> Unit,
    onVote: (String) -> Unit,
    onToggleSave: () -> Unit,
) {
    val format = LocalFormat.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (post.pinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.forums_post_pinned),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (post.locked) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = stringResource(R.string.forums_post_locked),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (post.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = post.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onVote(if (post.userVote == "up") "" else "up") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ThumbUp,
                        contentDescription = stringResource(R.string.forums_post_vote_up),
                        tint = if (post.userVote == "up") MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(LocalFormat.current.formatNumber(post.up - post.down), style = MaterialTheme.typography.labelMedium)
                IconButton(
                    onClick = { onVote(if (post.userVote == "down") "" else "down") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ThumbDown,
                        contentDescription = stringResource(R.string.forums_post_vote_down),
                        tint = if (post.userVote == "down") MaterialTheme.colorScheme.error
                              else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = post.name.ifBlank { stringResource(R.string.forums_post_default_author) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = format.formatRelativeTime(post.created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${post.comments}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onToggleSave, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = stringResource(
                            if (isSaved) R.string.forums_saved_remove else R.string.forums_saved_save
                        ),
                        tint = if (isSaved) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun bannerContentHash(content: String): String {
    var hash = 5381
    for (c in content) hash = (hash shl 5) + hash + c.code
    return Integer.toHexString(hash)
}

@Composable
private fun ForumBanner(bannerHtml: String, forumId: String) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("forums_banner_dismissed", Context.MODE_PRIVATE)
    }
    val prefKey = remember(forumId) { "forum_$forumId" }
    val contentHash = remember(bannerHtml) { bannerContentHash(bannerHtml) }
    var dismissed by remember(prefKey, contentHash) {
        mutableStateOf(prefs.getString(prefKey, null) == contentHash)
    }
    if (dismissed) return

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            HtmlContent(html = bannerHtml, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                prefs.edit().putString(prefKey, contentHash).apply()
                dismissed = true
            }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.forums_banner_dismiss),
                )
            }
        }
    }
}
