// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.model.Reaction
import org.mochios.android.model.ReactionCount
import org.mochios.android.model.ReactionType
import org.mochios.android.ui.components.FeatureDrawerItem
import org.mochios.android.push.SystemNotifications
import org.mochios.android.ui.components.FeatureListDrawer
import org.mochios.android.ui.components.FlipBook
import org.mochios.feeds.ui.component.PostBody
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.ui.components.LightboxScreen
import org.mochios.android.ui.components.MediaGrid
import org.mochios.android.ui.components.NewItemsPill
import org.mochios.android.ui.components.NotificationBell
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.ui.components.ReactionBar
import org.mochios.feeds.R
import org.mochios.feeds.api.InterestSuggestion
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.Tag
import org.mochios.feeds.ui.post.AddTagDialog
import org.mochios.feeds.ui.post.PostTagsButton
import org.mochios.feeds.ui.feedlist.FeedListViewModel
import org.mochios.feeds.ui.router.FEEDS_FEATURE
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onNavigateToPost: (feedId: String, postId: String, sourceUrl: String?, expandComments: Boolean) -> Unit,
    onNavigateToCreatePost: (String) -> Unit,
    onNavigateToEditPost: (String, String) -> Unit,
    onNavigateToSettings: (String) -> Unit,
    onNavigateToSources: (feedId: String, sourceUrl: String?) -> Unit = { _, _ -> },
    onNavigateToSaved: () -> Unit = {},
    onSelectFeed: (String) -> Unit,
    onNavigateToFindFeeds: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onLogout: () -> Unit,
    viewModel: FeedViewModel = hiltViewModel(),
    feedListViewModel: FeedListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val drawerFeeds by feedListViewModel.feeds.collectAsState()

    // Persist the last-viewed feed so the next cold start lands here. The
    // router composable reads this back via [LastViewedStore.get].
    // Also dismiss any system-tray pushes for this feed — server-side
    // clear/object marks the bell row read, but Android's tray only
    // clears via AUTO_CANCEL on tap; opening the feed directly without
    // tapping the push needs a manual cancel.

    // Interest-thumb feedback: confirm boosted/reduced/removed, or surface the
    // actual error — previously the result was silently swallowed, so taps
    // looked dead even when the call failed.
    val interestBoosted = stringResource(R.string.feeds_interest_boosted)
    val interestReduced = stringResource(R.string.feeds_interest_reduced)
    val interestRemoved = stringResource(R.string.feeds_interest_removed)
    val interestFailed = stringResource(R.string.feeds_interest_failed)
    LaunchedEffect(Unit) {
        viewModel.interestFeedback.collectLatest { fb ->
            val msg = when (fb) {
                is InterestFeedback.Success -> when (fb.direction) {
                    "up" -> interestBoosted
                    "down" -> interestReduced
                    else -> interestRemoved
                }
                is InterestFeedback.Failure -> fb.error?.message ?: interestFailed
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(viewModel.feedId) {
        if (viewModel.feedId.isNotBlank()) {
            LastViewedStore.set(context, FEEDS_FEATURE, viewModel.feedId)
            SystemNotifications.cancelFor(context, "feeds", viewModel.feedId)
            // Mark the feed's notifications read on the server so the bell
            // clears on web / other devices, not just the local tray.
            viewModel.clearNotifications()
        }
    }

    val posts by viewModel.posts.collectAsState()
    val feedInfo by viewModel.feedInfo.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val isNotFound by viewModel.isNotFound.collectAsState()
    val currentSort by viewModel.currentSort.collectAsState()
    val unreadOnly by viewModel.unreadOnly.collectAsState()
    val savedIds by viewModel.savedIds.collectAsState()
    val newPostsCount by viewModel.newPostsCount.collectAsState()


    var showOverflowMenu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Post?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var showSuggestedInterests by remember { mutableStateOf(false) }
    var showDefaultSortDialog by remember { mutableStateOf(false) }
    var addTagTarget by remember { mutableStateOf<String?>(null) }
    val pagerState = rememberPagerState(pageCount = { posts.size })

    // The pager addresses pages by integer index, but the posts list is
    // replaced wholesale by background refreshes (the cache→network refresh on
    // open, and websocket-driven refreshes), and relevance / AI / hot sorts
    // re-rank server-side — so the same index can map to a different post after
    // a refresh. Without anchoring that silently swaps the post under the
    // reader about a second after it appears. We track the id of the post in
    // view and, after any posts-list change, restore the pager to that post's
    // new index. `suppressAnchorRestore` lets a manual refresh opt out (it
    // intentionally returns to the top).
    var anchorPostId by remember { mutableStateOf<String?>(null) }
    var suppressAnchorRestore by remember { mutableStateOf(false) }
    LaunchedEffect(posts) {
        if (suppressAnchorRestore) {
            suppressAnchorRestore = false
            return@LaunchedEffect
        }
        val id = anchorPostId ?: return@LaunchedEffect
        val index = posts.indexOfFirst { it.id == id }
        if (index >= 0 && index != pagerState.currentPage) {
            pagerState.scrollToPage(index)
        }
    }

    // Freeze guard for the page-flip overlay: if the pager ever comes to rest
    // at a fractional offset (an interrupted/incomplete settle), snap it to the
    // nearest page so the fold can't stay frozen mid-fold. Only fires once
    // scrolling has stopped with a residual offset — a normal settle ends at
    // ~0 and is left untouched.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .collectLatest { inProgress ->
                if (!inProgress &&
                    kotlin.math.abs(pagerState.currentPageOffsetFraction) > 0.01f
                ) {
                    pagerState.scrollToPage(pagerState.currentPage)
                }
            }
    }

    // Mark the current page's post as read as soon as the swipe settles on
    // it — no debounce. The pager's currentPage only flips once the user
    // has committed to that page (mid-swipe doesn't tick currentPage), so
    // dropping the 1s delay still avoids spuriously marking pages the
    // user flipped past.
    LaunchedEffect(pagerState, posts.size) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collectLatest { page ->
                val current = posts.getOrNull(page) ?: return@collectLatest
                // Remember which post the reader has settled on so a background
                // refresh that reorders the list can keep them on it.
                anchorPostId = current.id
                viewModel.onPostBottomViewed(current.id)
                // Prefetch the lazy og:image for the current page and its
                // immediate neighbours so the picture is ready when the
                // user flips. Server caches per post, so duplicate calls
                // on subsequent flips are cheap.
                viewModel.loadPostImageIfMissing(current.id)
                posts.getOrNull(page + 1)?.let { viewModel.loadPostImageIfMissing(it.id) }
                posts.getOrNull(page - 1)?.let { viewModel.loadPostImageIfMissing(it.id) }
            }
    }

    // Load more when within 3 pages of the end. Pager state's currentPage
    // updates as the user flips, so the fetch fires while there's still
    // headroom — by the time they reach the end of the current batch the
    // next page has already loaded.
    LaunchedEffect(pagerState.currentPage, posts.size, hasMore, isLoadingMore) {
        if (hasMore && !isLoadingMore && pagerState.currentPage >= posts.size - 3) {
            viewModel.loadMore()
        }
    }

    val totalUnread = drawerFeeds.sumOf { it.unread }
    val allId = LastViewedStore.ALL
    val allLabel = stringResource(R.string.feeds_all_feeds)
    val drawerItems = remember(drawerFeeds) {
        drawerFeeds.map { feed ->
            FeatureDrawerItem(
                id = feed.fingerprint.ifEmpty { feed.id },
                title = feed.name,
                unread = feed.unread,
                icon = Icons.Default.RssFeed,
            )
        }
    }
    val drawerAll = FeatureDrawerItem(
        id = allId,
        title = allLabel,
        unread = totalUnread,
        icon = Icons.Default.RssFeed,
    )
    val currentDrawerId = if (viewModel.feedId == allId) allId else viewModel.feedId

    FeatureListDrawer(
        drawerState = drawerState,
        items = drawerItems,
        allItem = drawerAll,
        selectedId = currentDrawerId,
        onItemClick = { item ->
            drawerScope.launch { drawerState.close() }
            if (item.id != currentDrawerId) onSelectFeed(item.id)
        },
        actions = {
            ListItem(
                modifier = Modifier.clickable {
                    drawerScope.launch { drawerState.close() }
                    onNavigateToFindFeeds()
                },
                headlineContent = { Text(stringResource(R.string.feeds_find_feeds)) },
                leadingContent = { Icon(Icons.Default.Search, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
            ListItem(
                modifier = Modifier.clickable {
                    drawerScope.launch { drawerState.close() }
                    showDefaultSortDialog = true
                },
                headlineContent = { Text(stringResource(R.string.feeds_default_sort)) },
                leadingContent = { Icon(Icons.Default.ImportExport, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
            val suggestedInterests by viewModel.suggestedInterests.collectAsState()
            if (suggestedInterests.isNotEmpty()) {
                ListItem(
                    modifier = Modifier.clickable {
                        drawerScope.launch { drawerState.close() }
                        showSuggestedInterests = true
                    },
                    headlineContent = { Text(stringResource(R.string.feeds_suggested_interests)) },
                    supportingContent = {
                        Text(stringResource(R.string.feeds_suggested_interests_count, suggestedInterests.size))
                    },
                    leadingContent = { Icon(Icons.Default.Star, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
            }
            ListItem(
                modifier = Modifier.clickable {
                    drawerScope.launch { drawerState.close() }
                    onLogout()
                },
                headlineContent = { Text(stringResource(R.string.feeds_logout)) },
                leadingContent = { Icon(Icons.Default.Logout, contentDescription = null) },
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = feedInfo?.name ?: stringResource(R.string.feeds_feed_title_default),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                // Slightly shorter than the 64dp default to reclaim vertical space.
                expandedHeight = 52.dp,
                navigationIcon = {
                    IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.feeds_title))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // A manual refresh intentionally returns to the top, so
                        // don't let the anchor-restore effect pull the reader
                        // back to the post they were on when the new list lands.
                        suppressAnchorRestore = true
                        viewModel.refresh()
                        // Also jump back to the first post, so refresh both
                        // reloads and returns the user to the top of the feed.
                        drawerScope.launch { pagerState.animateScrollToPage(0) }
                    }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.feeds_refresh)
                        )
                    }
                    NotificationBell(onClick = onOpenNotifications)
                    if (permissions.manage) {
                        IconButton(onClick = { onNavigateToCreatePost(viewModel.feedId) }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.feeds_new_post))
                        }
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(MochiR.string.common_more_options))
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            // Sort options — listed inline (no nested menu)
                            // with a check next to the active one. Each tap
                            // picks the sort and dismisses the menu.
                            Text(
                                text = stringResource(R.string.feeds_sort_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                            )
                            val sortOptions = listOf(
                                "ai" to R.string.feeds_sort_ai,
                                "interests" to R.string.feeds_sort_interests,
                                "new" to R.string.feeds_sort_new,
                                "hot" to R.string.feeds_sort_hot,
                                "top" to R.string.feeds_sort_top,
                            )
                            sortOptions.forEach { (value, labelRes) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(labelRes)) },
                                    leadingIcon = {
                                        if (currentSort == value) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        } else {
                                            Spacer(Modifier.size(24.dp))
                                        }
                                    },
                                    onClick = {
                                        viewModel.setSort(value)
                                        showOverflowMenu = false
                                    }
                                )
                            }

                            HorizontalDivider()

                            // Unread-only toggle. Tapping flips state; the
                            // leading checkmark indicates the current value.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.feeds_unread_only)) },
                                leadingIcon = {
                                    if (unreadOnly) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    } else {
                                        Spacer(Modifier.size(24.dp))
                                    }
                                },
                                onClick = {
                                    viewModel.setUnreadOnly(!unreadOnly)
                                    showOverflowMenu = false
                                }
                            )

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.feeds_mark_all_read)) },
                                leadingIcon = {
                                    Icon(Icons.Default.DoneAll, contentDescription = null)
                                },
                                onClick = {
                                    viewModel.markAllRead()
                                    showOverflowMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.feeds_saved_menu)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Bookmark, contentDescription = null)
                                },
                                onClick = {
                                    onNavigateToSaved()
                                    showOverflowMenu = false
                                }
                            )
                            if (permissions.manage) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.feeds_tab_sources)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.RssFeed, contentDescription = null)
                                    },
                                    onClick = {
                                        // Pass the source of the post the user is
                                        // currently on so the Sources list opens
                                        // scrolled to it.
                                        onNavigateToSources(
                                            viewModel.feedId,
                                            posts.getOrNull(pagerState.currentPage)?.source?.url,
                                        )
                                        showOverflowMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.feeds_settings)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                    },
                                    onClick = {
                                        onNavigateToSettings(viewModel.feedId)
                                        showOverflowMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            // Reachable refresh/jump deep in the feed: a scroll-to-top button
            // appears once a few posts in (pull-to-refresh only works at page 0).
            if (pagerState.currentPage > 2) {
                SmallFloatingActionButton(
                    onClick = { drawerScope.launch { pagerState.animateScrollToPage(0) } }
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.feeds_scroll_to_top)
                    )
                }
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                // Pull-to-refresh is a deliberate "show me the latest" gesture
                // from the top — let the new list settle at the top rather than
                // anchor-restoring back to the previously-current post.
                suppressAnchorRestore = true
                viewModel.refresh()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && posts.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                isNotFound && posts.isEmpty() -> {
                    NotFoundState(
                        title = stringResource(R.string.feeds_feed_not_found),
                        onBack = { drawerScope.launch { drawerState.open() } },
                    )
                }
                error != null && posts.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = error!!.userMessage(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = { viewModel.loadFeed() }) {
                                Text(stringResource(MochiR.string.common_retry))
                            }
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (posts.isEmpty() && !isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.feeds_no_posts_yet),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // Magazine-style vertical pager: one post per page.
                            // The pager itself handles gestures/fling and
                            // renders the live (interactive) post. A FlipBook
                            // overlay paints the Flipboard book-fold during a
                            // transition (it draws nothing at rest, so the live
                            // page shows through). The fold is hinged at the
                            // mid-screen crease: the outgoing top half stays
                            // put while a single leaf turns toward the viewer.
                            val renderPost: @Composable (Int) -> Unit = { index ->
                                val post = posts[index]
                                // Prefer the fingerprint, but fall back to the feed's
                                // entity id (always present on a post) before the
                                // "__all__" aggregate sentinel — so react / interest /
                                // open-post all reach a real entity in the aggregate view.
                                val routeFeedId = post.feedFingerprint.ifEmpty { post.feed }.ifEmpty { viewModel.feedId }
                                // post.source.url is the RSS feed (XML) URL —
                                // not the article URL. The article URL lives
                                // in rss.link. Anything else falls through to
                                // the standard post detail screen.
                                val sourceUrl = post.data?.rss?.link?.takeIf { it.isNotEmpty() }
                                PostCard(
                                    post = post,
                                    fallbackFeedId = viewModel.feedId,
                                    canManage = permissions.manage,
                                    isSaved = savedIds.contains(post.id),
                                    onClick = { onNavigateToPost(routeFeedId, post.id, sourceUrl, false) },
                                    onComments = { onNavigateToPost(routeFeedId, post.id, sourceUrl, true) },
                                    onReact = { reaction -> viewModel.reactToPost(routeFeedId, post.id, reaction) },
                                    onToggleSave = { viewModel.toggleSave(post) },
                                    onEdit = { onNavigateToEditPost(routeFeedId, post.id) },
                                    onDelete = { pendingDelete = post },
                                    onAddTag = { addTagTarget = post.id },
                                    onAdjustInterest = { tag, direction -> viewModel.adjustInterest(routeFeedId, tag, direction) },
                                )
                            }
                            Box(modifier = Modifier.fillMaxSize()) {
                                VerticalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    key = { page -> posts[page].id },
                                    // Default snap threshold is 50% of page;
                                    // even 20% still felt like work on a tall
                                    // phone where the thumb naturally moves
                                    // 60-80dp. Drop to 8% so any deliberate
                                    // upward gesture commits to the next post
                                    // and the fold only rubber-bands back on a
                                    // clear flick-and-release-back. Velocity-
                                    // based fling continues to handle flicks.
                                    //
                                    // snapAnimationSpec: default spring micro-
                                    // overshoots near the end and reads as a
                                    // bounce. A short FastOutSlowIn tween
                                    // settles cleanly, matching the eased fold.
                                    flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                                        state = pagerState,
                                        snapPositionalThreshold = 0.08f,
                                        snapAnimationSpec = androidx.compose.animation.core.tween(
                                            durationMillis = 220,
                                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                                        ),
                                    ),
                                ) { page ->
                                    renderPost(page)
                                }
                                // Drawn after the pager → on top; no pointer
                                // input of its own, so drags pass through.
                                FlipBook(
                                    pagerState = pagerState,
                                    pageCount = posts.size,
                                    page = renderPost,
                                )
                                NewItemsPill(
                                    count = newPostsCount,
                                    label = pluralStringResource(
                                        R.plurals.feeds_new_posts, newPostsCount, newPostsCount
                                    ),
                                    onClick = {
                                        viewModel.showNewPosts()
                                        drawerScope.launch { pagerState.animateScrollToPage(0) }
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

    if (showDefaultSortDialog) {
        val sortOptions = listOf(
            "ai" to R.string.feeds_sort_ai,
            "interests" to R.string.feeds_sort_interests,
            "new" to R.string.feeds_sort_new,
            "hot" to R.string.feeds_sort_hot,
            "top" to R.string.feeds_sort_top,
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDefaultSortDialog = false },
            title = { Text(stringResource(R.string.feeds_default_sort)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    sortOptions.forEach { (value, labelRes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setGlobalDefaultSort(value)
                                    showDefaultSortDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(labelRes))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDefaultSortDialog = false }) {
                    Text(stringResource(MochiR.string.common_cancel))
                }
            },
        )
    }

    if (showSuggestedInterests) {
        val suggestions by viewModel.suggestedInterests.collectAsState()
        InterestSuggestionsDialog(
            suggestions = suggestions,
            onAdd = { viewModel.addInterest(it) },
            onDismissOne = { viewModel.dismissInterest(it) },
            onDismiss = { showSuggestedInterests = false },
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.feeds_delete_post)) },
            text = { Text(stringResource(R.string.feeds_delete_post_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePost(target.id)
                        pendingDelete = null
                    }
                ) {
                    Text(
                        stringResource(MochiR.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(MochiR.string.common_cancel))
                }
            }
        )
    }

    addTagTarget?.let { postId ->
        AddTagDialog(
            onDismiss = { addTagTarget = null },
            onAdd = { label, qid ->
                viewModel.addTag(postId, label, qid)
                addTagTarget = null
            }
        )
    }
    }
}

@Composable
private fun PostCard(
    post: Post,
    fallbackFeedId: String,
    canManage: Boolean,
    isSaved: Boolean,
    onClick: () -> Unit,
    onComments: () -> Unit,
    onReact: (String) -> Unit,
    onToggleSave: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddTag: () -> Unit,
    onAdjustInterest: (Tag, String) -> Unit,
) {
    // Lightbox open-state: (image urls list, starting index). null = closed.
    // Tapping an image populates this; the lightbox dialog renders above the
    // page. Matches web's per-card image-tap behaviour (open lightbox, not
    // navigate to post detail).
    var lightboxState by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    // Magazine-style page: full-screen surface, no card chrome. The
    // VerticalPager wrapper handles the 3D flip; the page itself is just
    // content on the theme background. The action row is hoisted out of
    // the scrollable content area below so it stays pinned at the bottom
    // of the screen — easier thumb reach on tall phones, and matches the
    // bottom-action-bar pattern Flipboard / Apple News use on full-screen
    // article pages.
    // Primary ("hero") image for this post: the first attachment image, else
    // the RSS preview image. When present it's lifted out of the scrolling
    // text column and shown full-bleed across the top half of the page; the
    // text follows below. When absent, the text stays at the top as usual.
    val attachmentImages = post.attachments.filter { it.isImage }
    val otherAttachments = post.attachments.filter { !it.isImage }
    val attachmentFeed = post.feed.ifEmpty { fallbackFeedId }
    val attachmentImageUrls = attachmentImages.map { att ->
        att.url ?: "/feeds/$attachmentFeed/-/attachments/${att.id}"
    }
    val rssImageUrl = post.data?.rss?.image?.takeIf { it.isNotEmpty() }
    val heroFromAttachment = attachmentImageUrls.isNotEmpty()
    val heroUrl = attachmentImageUrls.firstOrNull() ?: rssImageUrl

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
      if (heroUrl != null) {
          // Square corners; height follows the image's ratio up to a half-screen
          // cap. ContentScale.Fit means a landscape image fills the width
          // edge-to-edge, while a very tall image (e.g. a web comic) is shown
          // whole, contained within the cap (with side margins), rather than
          // cropped top and bottom. Tap opens the full-screen lightbox.
          val maxHeroHeight = (LocalConfiguration.current.screenHeightDp / 2).dp
          AsyncImage(
              model = heroUrl,
              contentDescription = stringResource(R.string.feeds_image_preview),
              modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(max = maxHeroHeight)
                  .clickable {
                      lightboxState = if (heroFromAttachment) {
                          attachmentImageUrls to 0
                      } else {
                          listOf(heroUrl) to 0
                      }
                  },
              contentScale = ContentScale.Fit,
          )
      }
      Column(
          modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .clickable(onClick = onClick)
              .padding(
                  start = 16.dp,
                  end = 4.dp,
                  top = if (heroUrl != null) 12.dp else 16.dp,
              )
      ) {
            // Header: source/feed name + timestamp, above the title.
            // formatTimestamp obeys every timestamp preference (relative /
            // absolute / auto, and the date+time+timezone format).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val defaultAuthor = stringResource(R.string.feeds_post_default_author)
                val authorName = post.source?.name?.takeIf { it.isNotEmpty() }
                    ?: post.feedName.takeIf { it.isNotEmpty() }
                    ?: defaultAuthor
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = LocalFormat.current.formatTimestamp(post.created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Memory badge
            post.data?.memory?.let { memory ->
                if (memory.yearsAgo > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pluralStringResource(R.plurals.feeds_memory_years_ago_today, memory.yearsAgo, memory.yearsAgo),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Location info
            post.data?.checkin?.let { checkin ->
                if (checkin.name.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.feeds_location_at, checkin.name),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            post.data?.travelling?.let { travelling ->
                val origin = travelling.origin?.name ?: ""
                val destination = travelling.destination?.name ?: ""
                if (origin.isNotEmpty() || destination.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val text = when {
                        origin.isNotEmpty() && destination.isNotEmpty() ->
                            stringResource(R.string.feeds_travel_arrow, origin, destination)
                        origin.isNotEmpty() ->
                            stringResource(R.string.feeds_travel_from, origin)
                        else ->
                            stringResource(R.string.feeds_travel_to, destination)
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Post body (truncated). Taps open detail. For RSS posts the
            // first line is the article title — bolded for scannability.
            if (post.body.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                PostBody(
                    post = post,
                    maxLines = 6,
                    titleFontSize = 20.sp,
                    titleBodyGap = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onClick
                )
            }

            // Remaining attachment images (the first is the hero on top).
            // Indices are kept aligned to the full list so the lightbox still
            // spans every image.
            val gridStartIndex = if (heroFromAttachment) 1 else 0
            val gridImages = attachmentImages.drop(gridStartIndex)
            if (gridImages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                MediaGrid(
                    urls = attachmentImageUrls.drop(gridStartIndex),
                    thumbnailUrls = gridImages.map { att ->
                        att.thumbnailUrl ?: "/feeds/$attachmentFeed/-/attachments/${att.id}/thumbnail"
                    },
                    contentDescriptions = gridImages.map { it.name },
                    onClick = { index -> lightboxState = attachmentImageUrls to (index + gridStartIndex) }
                )
            }
            if (otherAttachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = pluralStringResource(R.plurals.feeds_attachment_count, otherAttachments.size, otherAttachments.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // RSS preview image — only when it isn't already the hero on top.
            // Tapping opens the lightbox (web parity).
            if (rssImageUrl != null && rssImageUrl != heroUrl) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = rssImageUrl,
                    contentDescription = stringResource(R.string.feeds_image_preview),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { lightboxState = listOf(rssImageUrl) to 0 },
                    contentScale = ContentScale.Fit
                )
            }


            // Inline comments preview (top-level only, newest first)
            if (post.comments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                val previewLimit = 3
                val previewed = post.comments.take(previewLimit)
                val remaining = post.comments.size - previewed.size
                val anonymous = stringResource(R.string.feeds_anonymous)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (comment in previewed) {
                        CommentPreviewLine(
                            comment = comment,
                            anonymous = anonymous
                        )
                    }
                    if (remaining > 0) {
                        Text(
                            text = pluralStringResource(R.plurals.feeds_view_more_comments, remaining, remaining),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable(onClick = onComments)
                        )
                    }
                }
            }
        }

        PostActionBar(
            post = post,
            canManage = canManage,
            isSaved = isSaved,
            onReact = onReact,
            onComments = onComments,
            onToggleSave = onToggleSave,
            onEdit = onEdit,
            onDelete = onDelete,
            onAddTag = onAddTag,
            onAdjustInterest = onAdjustInterest,
        )
    }

    lightboxState?.let { (urls, index) ->
        LightboxScreen(
            images = urls,
            initialIndex = index,
            onDismiss = { lightboxState = null },
        )
    }
}

// Bottom action bar: reaction bar, then comment / edit / delete icon buttons.
// Lives outside the flipping page content so it stays put during the page-flip
// and simply reflects whichever post is current (updating on swipe-commit),
// rather than flipping along with the card.
@Composable
private fun PostActionBar(
    post: Post,
    canManage: Boolean,
    isSaved: Boolean,
    onReact: (String) -> Unit,
    onComments: () -> Unit,
    onToggleSave: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddTag: () -> Unit,
    onAdjustInterest: (Tag, String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReactionBar(
            reactions = toReactionCounts(post.reactions, post.myReaction),
            onReact = onReact,
            onRemoveReaction = { onReact(post.myReaction) },
            modifier = Modifier.weight(1f)
        )
        PostTagsButton(
            tags = post.tags,
            onAddTag = onAddTag,
            onAdjustInterest = onAdjustInterest,
        )
        IconButton(onClick = onComments, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.ChatBubbleOutline,
                contentDescription = stringResource(R.string.feeds_comments),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onToggleSave, modifier = Modifier.size(32.dp)) {
            Icon(
                if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = stringResource(
                    if (isSaved) R.string.feeds_saved_remove else R.string.feeds_saved_save
                ),
                modifier = Modifier.size(18.dp),
                tint = if (isSaved) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (canManage) {
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(MochiR.string.common_edit),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(MochiR.string.common_delete),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CommentPreviewLine(
    comment: Comment,
    anonymous: String
) {
    val displayName = comment.name.ifEmpty { anonymous }
    val plain = stripCommentHtml(comment.body)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = plain,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = LocalFormat.current.formatRelativeTime(comment.created),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


private fun stripCommentHtml(html: String): String =
    html
        .replace(Regex("<br\\s*/?>"), " ")
        .replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun toReactionCounts(reactions: List<Reaction>, myReaction: String): List<ReactionCount> =
    reactions.groupBy { it.reaction }.mapNotNull { (reaction, list) ->
        val type = ReactionType.fromString(reaction) ?: return@mapNotNull null
        ReactionCount(type, list.size, reaction.equals(myReaction, ignoreCase = true))
    }

@Composable
private fun InterestSuggestionsDialog(
    suggestions: List<InterestSuggestion>,
    onAdd: (InterestSuggestion) -> Unit,
    onDismissOne: (InterestSuggestion) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_suggested_interests)) },
        text = {
            if (suggestions.isEmpty()) {
                Text(stringResource(R.string.feeds_suggested_interests_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                ) {
                    items(suggestions, key = { it.qid }) { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                                Text(s.label, style = MaterialTheme.typography.bodyMedium)
                                if (s.count > 0) {
                                    Text(
                                        stringResource(R.string.feeds_suggested_interests_count, s.count),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            TextButton(onClick = { onAdd(s) }) {
                                Text(stringResource(R.string.feeds_suggested_interests_add))
                            }
                            TextButton(onClick = { onDismissOne(s) }) {
                                Text(stringResource(R.string.feeds_suggested_interests_dismiss))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_close))
            }
        },
    )
}

