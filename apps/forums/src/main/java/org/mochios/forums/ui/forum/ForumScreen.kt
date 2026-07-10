// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forum

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import android.content.Context
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.FeatureDrawerItem
import org.mochios.android.ui.components.FeatureListDrawer
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.ui.components.HtmlContent
import org.mochios.android.ui.components.NewItemsPill
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.ui.components.NotificationBell
import org.mochios.forums.R
import org.mochios.forums.model.Post
import org.mochios.forums.ui.forumlist.CreateForumDialog
import org.mochios.forums.ui.forumlist.ForumListViewModel
import org.mochios.forums.ui.router.FORUMS_FEATURE
import org.mochios.android.R as MochiR

/** A post-sort choice as rendered in the forum overflow menu. */
private data class SortOption(
    val key: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val SORT_OPTIONS = listOf(
    SortOption("interests", R.string.forums_sort_interests, Icons.Outlined.StarOutline),
    SortOption("new", R.string.forums_sort_new, Icons.Outlined.Schedule),
    SortOption("hot", R.string.forums_sort_hot, Icons.Outlined.Whatshot),
    SortOption("top", R.string.forums_sort_top, Icons.Outlined.EmojiEvents),
)

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

    // Opening the forum the user just created saves them hunting for it in the
    // freshly reloaded drawer.
    LaunchedEffect(listViewModel) {
        listViewModel.forumCreated.collect { newForumId ->
            if (newForumId != forumId) onSelectForum(newForumId)
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
    val drawerAll = FeatureDrawerItem(
        id = LastViewedStore.ALL,
        title = stringResource(R.string.forums_all_forums),
        icon = Icons.Default.Forum,
    )

    FeatureListDrawer(
        drawerState = drawerState,
        items = drawerItems,
        allItem = drawerAll,
        selectedId = forumId,
        onItemClick = { item ->
            drawerScope.launch { drawerState.close() }
            if (item.id != forumId) onSelectForum(item.id)
        },
        actions = {
            DrawerActionRow(
                title = stringResource(R.string.forums_saved_title),
                icon = Icons.Default.BookmarkBorder,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onNavigateToSaved()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.forums_list_find),
                icon = Icons.Default.Search,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onFindForums()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.forums_create_title),
                icon = Icons.Default.Add,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    listViewModel.showCreateDialog()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.forums_list_logout),
                icon = Icons.AutoMirrored.Filled.Logout,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onLogout()
                },
            )
            DrawerActionRow(
                title = stringResource(MochiR.string.about_label),
                icon = Icons.Default.Info,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    showAbout = true
                },
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
                onOpenNotifications = onOpenNotifications,
                onUnsubscribed = {
                    // The forum just left the user's list — drop it from the
                    // drawer and fall back to the aggregate view.
                    listViewModel.load()
                    onSelectForum(LastViewedStore.ALL)
                },
            )
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    if (listUiState.showCreateDialog) {
        CreateForumDialog(
            isCreating = listUiState.isCreating,
            onDismiss = { listViewModel.hideCreateDialog() },
            onCreate = { name, privacy -> listViewModel.createForum(name, privacy) },
        )
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
    onOpenNotifications: () -> Unit,
    onUnsubscribed: () -> Unit,
    viewModel: ForumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val savedIds by viewModel.savedIds.collectAsState()
    val newPostsCount by viewModel.newPostsCount.collectAsState()
    val isAll = viewModel.isAll
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showRssSubmenu by remember { mutableStateOf(false) }
    var showUnsubscribeConfirm by remember { mutableStateOf(false) }
    val forumIdForCallbacks = uiState.forum.fingerprint.ifEmpty { uiState.forum.id }

    val snackbar = remember { SnackbarHostState() }
    val rssClipboardLabel = stringResource(R.string.forums_rss_clipboard_label)
    val rssCopiedMessage = stringResource(R.string.forums_rss_copied)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ForumEvent.CopyRssUrl -> {
                    copyToClipboard(context, rssClipboardLabel, event.url)
                    snackbar.showSnackbar(rssCopiedMessage)
                }
                is ForumEvent.Unsubscribed -> onUnsubscribed()
                is ForumEvent.ShowError -> snackbar.showSnackbar(event.error.userMessage())
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isAll) {
                            stringResource(R.string.forums_all_forums)
                        } else {
                            uiState.forum.name.ifBlank { stringResource(R.string.forums_loading) }
                        },
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
                    // Both actions render unconditionally so the row keeps its
                    // width while the forum loads — the rows that depend on the
                    // response are gated inside the menu instead.
                    NotificationBell(onClick = onOpenNotifications)
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(
                                    MochiR.string.common_more_options
                                )
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = {
                                showOverflowMenu = false
                                showRssSubmenu = false
                            }
                        ) {
                            if (showRssSubmenu) {
                                // Header row taps back to the main menu.
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.forums_rss_feed)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = { showRssSubmenu = false }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.forums_rss_mode_posts))
                                    },
                                    onClick = {
                                        viewModel.copyRssUrl("posts")
                                        showRssSubmenu = false
                                        showOverflowMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                R.string.forums_rss_mode_posts_comments
                                            )
                                        )
                                    },
                                    onClick = {
                                        viewModel.copyRssUrl("all")
                                        showRssSubmenu = false
                                        showOverflowMenu = false
                                    }
                                )
                            } else {
                                // Sort options listed inline, each with its own
                                // icon and a trailing check on the active one.
                                Text(
                                    text = stringResource(R.string.forums_sort_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        start = 16.dp, top = 8.dp, bottom = 4.dp
                                    ),
                                )
                                SORT_OPTIONS.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(option.labelRes)) },
                                        leadingIcon = {
                                            Icon(option.icon, contentDescription = null)
                                        },
                                        trailingIcon = {
                                            if (uiState.sort == option.key) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                )
                                            }
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.setSort(option.key)
                                        }
                                    )
                                }

                                HorizontalDivider()

                                // The aggregate exports a class-level RSS feed
                                // but has no single forum to unsubscribe from.
                                if (isAll || uiState.forum.id.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.forums_rss_feed))
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.RssFeed,
                                                contentDescription = null
                                            )
                                        },
                                        trailingIcon = {
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = { showRssSubmenu = true }
                                    )
                                }
                                if (uiState.canManage && uiState.forum.id.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.forums_settings))
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Settings,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            onSettings(forumIdForCallbacks)
                                        }
                                    )
                                }
                                // Managers delete the forum from settings rather
                                // than unsubscribing from something they own.
                                if (!isAll && !uiState.canManage && uiState.forum.id.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    R.string.forums_list_unsubscribe
                                                )
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Logout,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            showUnsubscribeConfirm = true
                                        }
                                    )
                                }
                            }
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
                                showForumName = isAll,
                                onClick = {
                                    // Aggregate posts each belong to their own forum.
                                    val targetForum = if (isAll) post.forum else forumIdForCallbacks
                                    onPostClick(targetForum, post.id)
                                },
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

    if (showUnsubscribeConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.forums_list_unsubscribe_title),
            message = stringResource(R.string.forums_list_unsubscribe_message),
            confirmLabel = stringResource(R.string.forums_list_unsubscribe),
            dismissLabel = stringResource(MochiR.string.common_cancel),
            isDestructive = true,
            onConfirm = {
                showUnsubscribeConfirm = false
                viewModel.unsubscribe()
            },
            onDismiss = { showUnsubscribeConfirm = false },
        )
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

@Composable
private fun PostCard(
    post: Post,
    isSaved: Boolean,
    onClick: () -> Unit,
    onVote: (String) -> Unit,
    onToggleSave: () -> Unit,
    showForumName: Boolean = false,
) {
    val format = LocalFormat.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // In the aggregate "All forums" view, label which forum each post
            // came from since the top bar no longer identifies a single one.
            if (showForumName && post.forumName.isNotBlank()) {
                Text(
                    text = post.forumName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
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
