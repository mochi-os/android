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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import org.mochios.android.ui.components.FeatureDrawerItem
import org.mochios.android.push.SystemNotifications
import org.mochios.android.ui.components.FeatureListDrawer
import org.mochios.android.ui.components.FlipBook
import org.mochios.feeds.ui.component.CommentItem
import org.mochios.feeds.ui.component.PostBody
import org.mochios.feeds.ui.component.PostTitle
import org.mochios.feeds.ui.component.currentReactionType
import org.mochios.feeds.ui.component.rssDisplayTitle
import org.mochios.feeds.ui.component.toReactionCounts
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.ui.components.LightboxScreen
import org.mochios.android.ui.components.MediaGrid
import org.mochios.android.ui.components.NewItemsPill
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.ui.components.ReactionBar
import org.mochios.feeds.R
import org.mochios.feeds.api.InterestSuggestion
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.Tag
import org.mochios.feeds.ui.post.AddTagDialog
import org.mochios.feeds.ui.post.CommentInputBar
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
    val hasAi by feedListViewModel.hasAi.collectAsState()

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
    val caughtUpMessage = stringResource(R.string.feeds_caught_up)
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
    val isPostRefreshing by viewModel.isPostRefreshing.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val isNotFound by viewModel.isNotFound.collectAsState()
    val currentSort by viewModel.currentSort.collectAsState()
    val unreadOnly by viewModel.unreadOnly.collectAsState()
    val savedIds by viewModel.savedIds.collectAsState()
    val newPostsCount by viewModel.newPostsCount.collectAsState()
    val commentTarget by viewModel.commentTarget.collectAsState()
    val commentDraft by viewModel.commentDraft.collectAsState()
    val commentAttachments by viewModel.commentAttachments.collectAsState()
    val isSendingComment by viewModel.isSendingComment.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val commentFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri -> viewModel.addCommentAttachment(uri) }
    }


    var showOverflowMenu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Post?>(null) }
    // Set once the viewer closes the one-shot post-subscribe suggestions prompt,
    // so it doesn't reopen while the suggestions are still being acted on.
    var suggestionsDismissed by remember { mutableStateOf(false) }
    var addTagTarget by remember { mutableStateOf<String?>(null) }
    // (feedId, postId, commentId) of a comment pending delete confirmation.
    var pendingDeleteComment by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    val pagerState = rememberPagerState(pageCount = { posts.size })

    // "You're all caught up": when the reader is on the last post with nothing
    // more to load and flips up (drags toward a next post that doesn't exist),
    // the over-scroll is unconsumed and surfaces here. Rate-limited so a sustained
    // drag shows the toast once.
    val atFeedEnd by rememberUpdatedState(
        posts.isNotEmpty() && pagerState.currentPage == posts.lastIndex && !hasMore
    )
    var lastCaughtUpToastAt by remember { mutableLongStateOf(0L) }
    val endOverscrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // available.y < 0 is an unconsumed forward (toward-next) drag.
                if (available.y < -1f && atFeedEnd) {
                    val now = System.currentTimeMillis()
                    if (now - lastCaughtUpToastAt > 1500L) {
                        lastCaughtUpToastAt = now
                        Toast.makeText(context, caughtUpMessage, Toast.LENGTH_SHORT).show()
                    }
                }
                return Offset.Zero
            }
        }
    }

    // The pager addresses pages by integer index, but the posts list is
    // replaced wholesale by background refreshes (the cache→network refresh on
    // open, and websocket-driven refreshes), and relevance / AI / hot sorts
    // re-rank server-side — so the same index can map to a different post after
    // a refresh. Without anchoring that silently swaps the post under the reader
    // about a second after it appears. To keep them on their post we capture the
    // list as it was and, after a change, look up where the post they were
    // viewing moved to — reading the LIVE currentPage against the PREVIOUS list,
    // not a separately-tracked id. (That id lagged a fresh swipe: a "1 new post"
    // re-emission landing mid-flip would see the stale id and yank the reader
    // back to the post they'd just left.) `suppressAnchorRestore` lets a manual
    // refresh opt out (it intentionally returns to the top).
    var previousPosts by remember { mutableStateOf(posts) }
    var suppressAnchorRestore by remember { mutableStateOf(false) }
    // Set by the "new posts" pill: jump to the top once the refreshed list lands.
    // Uses requestScrollToPage so it survives the keyed pager's data-change scroll
    // restoration (which would otherwise keep the reader on their current post).
    var goToTopOnRefresh by remember { mutableStateOf(false) }
    LaunchedEffect(posts) {
        val oldPosts = previousPosts
        previousPosts = posts
        if (goToTopOnRefresh) {
            goToTopOnRefresh = false
            pagerState.requestScrollToPage(0)
            return@LaunchedEffect
        }
        if (suppressAnchorRestore) {
            suppressAnchorRestore = false
            return@LaunchedEffect
        }
        // The post under the reader right now, taken from the list as it was at
        // the page they've actually swiped to — so a stale anchor can't fight a
        // swipe that the currentPage→id sync hasn't caught up with yet.
        val viewingId = oldPosts.getOrNull(pagerState.currentPage)?.id ?: return@LaunchedEffect
        val index = posts.indexOfFirst { post -> post.id == viewingId }
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
                    onNavigateToSaved()
                },
                headlineContent = { Text(stringResource(R.string.feeds_saved_menu)) },
                leadingContent = { Icon(Icons.Default.BookmarkBorder, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
            ListItem(
                modifier = Modifier.clickable {
                    drawerScope.launch { drawerState.close() }
                    onNavigateToFindFeeds()
                },
                headlineContent = { Text(stringResource(R.string.feeds_find_feeds)) },
                leadingContent = { Icon(Icons.Default.Search, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
            // TODO: re-enable Suggested interests once it's wired into the
            // subscribe-feed flow.
            // val suggestedInterests by viewModel.suggestedInterests.collectAsState()
            // if (suggestedInterests.isNotEmpty()) {
            //     ListItem(
            //         modifier = Modifier.clickable {
            //             drawerScope.launch { drawerState.close() }
            //             showSuggestedInterests = true
            //         },
            //         headlineContent = { Text(stringResource(R.string.feeds_suggested_interests)) },
            //         supportingContent = {
            //             Text(stringResource(R.string.feeds_suggested_interests_count, suggestedInterests.size))
            //         },
            //         leadingContent = { Icon(Icons.Default.Star, contentDescription = null) },
            //         colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            //     )
            // }
            ListItem(
                modifier = Modifier.clickable {
                    drawerScope.launch { drawerState.close() }
                    onLogout()
                },
                headlineContent = { Text(stringResource(R.string.feeds_logout)) },
                leadingContent = { Icon(Icons.Default.Logout, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = feedInfo?.name
                                ?: stringResource(R.string.feeds_feed_title_default),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    // Slightly shorter than the 64dp default to reclaim vertical space.
                    expandedHeight = 52.dp,
                    navigationIcon = {
                        IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.feeds_title)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                // Refresh only the post currently in view, patched in
                                // place so the pager doesn't move. Its feed comes from
                                // the post itself (cards span feeds in the aggregate).
                                posts.getOrNull(pagerState.currentPage)?.let { current ->
                                    val postFeedId = current.feedFingerprint
                                        .ifEmpty { current.feed }
                                        .ifEmpty { viewModel.feedId }
                                    viewModel.refreshPost(postFeedId, current.id)
                                }
                            },
                            enabled = !isPostRefreshing,
                        ) {
                            if (isPostRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.feeds_refresh),
                                )
                            }
                        }
                        if (permissions.manage) {
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(MochiR.string.common_more_options)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    // Edit/delete act on the post currently in view;
                                    // its feed comes from the post (cards span feeds
                                    // in the all-feeds aggregate).
                                    val currentPost = posts.getOrNull(pagerState.currentPage)
                                    if (currentPost != null) {
                                        val postFeedId = currentPost.feedFingerprint
                                            .ifEmpty { currentPost.feed }
                                            .ifEmpty { viewModel.feedId }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(MochiR.string.common_edit)) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Edit, contentDescription = null)
                                            },
                                            onClick = {
                                                onNavigateToEditPost(postFeedId, currentPost.id)
                                                showOverflowMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(MochiR.string.common_delete)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                pendingDelete = currentPost
                                                showOverflowMenu = false
                                            }
                                        )
                                        HorizontalDivider()
                                    }
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
                // Primary action: create a new post in this feed.
                if (permissions.manage) {
                    FloatingActionButton(
                        onClick = { onNavigateToCreatePost(viewModel.feedId) }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.feeds_new_post)
                        )
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                FeedFilterChips(
                    currentSort = currentSort,
                    unreadOnly = unreadOnly,
                    hasAi = hasAi,
                    onSetSort = { sort -> viewModel.setSort(sort) },
                    onSetUnreadOnly = { value -> viewModel.setUnreadOnly(value) },
                    onMarkAllRead = { viewModel.markAllRead { feedListViewModel.refreshSilently() } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp)
                )
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
                        .fillMaxWidth()
                        .weight(1f)
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
                                        val routeFeedId = post.feedFingerprint.ifEmpty { post.feed }
                                            .ifEmpty { viewModel.feedId }
                                        // post.source.url is the RSS feed (XML) URL —
                                        // not the article URL. The article URL lives
                                        // in rss.link; when present, tapping the card
                                        // opens the in-app article (PostSourceScreen).
                                        // Anything else falls through to post detail.
                                        val sourceUrl =
                                            post.data?.rss?.link?.takeIf { it.isNotEmpty() }
                                        PostCard(
                                            post = post,
                                            fallbackFeedId = viewModel.feedId,
                                            isSaved = savedIds.contains(post.id),
                                            onClick = {
                                                onNavigateToPost(
                                                    routeFeedId,
                                                    post.id,
                                                    sourceUrl,
                                                    false
                                                )
                                            },
                                            // The comment icon opens the composer
                                            // bottom sheet to add a comment.
                                            onComments = {
                                                viewModel.openCommentComposer(
                                                    routeFeedId,
                                                    post.id
                                                )
                                            },
                                            // "N more comments" opens the post with
                                            // its comments (expandComments = true).
                                            onViewComments = {
                                                onNavigateToPost(
                                                    routeFeedId,
                                                    post.id,
                                                    sourceUrl,
                                                    true
                                                )
                                            },
                                            onReplyComment = { parentId, parentName, parentBody ->
                                                viewModel.openCommentComposer(
                                                    routeFeedId,
                                                    post.id,
                                                    parentId,
                                                    parentName,
                                                    parentBody
                                                )
                                            },
                                            onEditComment = { commentId, body ->
                                                viewModel.openCommentEditor(
                                                    routeFeedId,
                                                    post.id,
                                                    commentId,
                                                    body
                                                )
                                            },
                                            onDeleteComment = { commentId ->
                                                pendingDeleteComment =
                                                    Triple(routeFeedId, post.id, commentId)
                                            },
                                            currentUserId = currentUserId,
                                            onReact = { reaction ->
                                                viewModel.reactToPost(
                                                    routeFeedId,
                                                    post.id,
                                                    reaction
                                                )
                                            },
                                            onReactComment = { commentId, reaction ->
                                                viewModel.reactToComment(
                                                    routeFeedId,
                                                    post.id,
                                                    commentId,
                                                    reaction
                                                )
                                            },
                                            onToggleSave = { viewModel.toggleSave(post) },
                                            onAddTag = { addTagTarget = post.id },
                                            onAdjustInterest = { tag, direction ->
                                                viewModel.adjustInterest(
                                                    routeFeedId,
                                                    tag,
                                                    direction
                                                )
                                            },
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .nestedScroll(endOverscrollConnection)
                                    ) {
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
                                                R.plurals.feeds_new_posts,
                                                newPostsCount,
                                                newPostsCount
                                            ),
                                            onClick = {
                                                // Refresh and jump to the top: the
                                                // anchor effect requests page 0 once
                                                // the new list lands (goToTopOnRefresh).
                                                goToTopOnRefresh = true
                                                viewModel.showNewPosts()
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
        }

        // Show the suggestions raised by a just-completed subscribe once: they
        // appear when the prompt arrives, hide when the viewer acts on them all,
        // closes the dialog, or refreshes (which clears the list in the VM).
        val suggestedInterests by viewModel.suggestedInterests.collectAsState()
        if (suggestedInterests.isNotEmpty() && !suggestionsDismissed) {
            InterestSuggestionsDialog(
                suggestions = suggestedInterests,
                onAdd = { viewModel.addInterest(it) },
                onDismissOne = { viewModel.dismissInterest(it) },
                onDismiss = { suggestionsDismissed = true },
            )
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

        pendingDeleteComment?.let { (feedId, postId, commentId) ->
            AlertDialog(
                onDismissRequest = { pendingDeleteComment = null },
                title = { Text(stringResource(R.string.feeds_delete_comment)) },
                text = { Text(stringResource(R.string.feeds_delete_comment_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteComment(feedId, postId, commentId)
                            pendingDeleteComment = null
                        }
                    ) {
                        Text(
                            stringResource(MochiR.string.common_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteComment = null }) {
                        Text(stringResource(MochiR.string.common_cancel))
                    }
                }
            )
        }

        commentTarget?.let { target ->
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeCommentComposer() },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                // Hide the drag handle and match the input bar's surface colour
                // so the sheet reads as one continuous composer.
                dragHandle = null,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Chat-style reply preview: who/what you're replying to.
                    if (target.parentId != null) {
                        CommentReplyPreview(
                            name = target.parentName.orEmpty(),
                            body = target.parentBody.orEmpty(),
                            onCancel = { viewModel.closeCommentComposer() },
                        )
                    }
                    // Reuse the post detail's comment input (text + attach + send,
                    // with @-mentions). The reply context is shown above, so the
                    // input bar's own indicator is suppressed.
                    CommentInputBar(
                        text = commentDraft,
                        onTextChange = { value -> viewModel.setCommentDraft(value) },
                        attachments = commentAttachments,
                        onAddAttachment = { commentFilePicker.launch("*/*") },
                        onRemoveAttachment = { uri -> viewModel.removeCommentAttachment(uri) },
                        onSend = { viewModel.sendComment() },
                        isSending = isSendingComment,
                        replyingTo = null,
                        onCancelReply = { viewModel.closeCommentComposer() },
                        onSearchMembers = { query -> viewModel.searchMembers(query) },
                    )
                }
            }
        }
    }
}

/**
 * A chat-style reply preview shown above the comment composer: an accent bar,
 * the author being replied to, and a one-line snippet of their comment.
 */
@Composable
private fun CommentReplyPreview(
    name: String,
    body: String,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.feeds_cancel_reply),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}


@Composable
private fun PostCard(
    post: Post,
    fallbackFeedId: String,
    isSaved: Boolean,
    onClick: () -> Unit,
    onComments: () -> Unit,
    onViewComments: () -> Unit,
    onReact: (String) -> Unit,
    onReactComment: (commentId: String, reaction: String) -> Unit,
    onReplyComment: (parentId: String, parentName: String, parentBody: String) -> Unit,
    onEditComment: (commentId: String, body: String) -> Unit,
    onDeleteComment: (commentId: String) -> Unit,
    currentUserId: String?,
    onToggleSave: () -> Unit,
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
        // One post fills at least one screen. A short post stays screen-filling
        // (the inner column has a viewport-height floor); a long post grows past
        // it and the verticalScroll lets the reader scroll through. When the
        // scroll reaches the bottom, the leftover drag hands off to the pager and
        // flips to the next post.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val viewportHeight = this.maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(min = viewportHeight)
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 4.dp,
                            top = if (heroUrl != null) 12.dp else 16.dp,
                        )
                ) {
                    // Title first (RSS posts only), so the byline sits beneath it.
                    // Hoisted out of PostBody — which renders the same line via
                    // PostTitle — so the source/time row can slot in between.
                    val displayTitle = rssDisplayTitle(post)
                    if (displayTitle != null) {
                        PostTitle(
                            title = displayTitle,
                            fontSize = 20.sp,
                            truncated = true,
                            onClick = onClick,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Byline: source/feed name + timestamp, below the title.
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

                    // Action row (react, tag, comment, save, edit/delete) directly
                    // below the byline. Moved up from the former bottom bar, so all
                    // post controls sit in the header block; it now flips and scrolls
                    // with the card rather than staying pinned at the screen bottom.
                    Spacer(modifier = Modifier.height(8.dp))
                    PostActionBar(
                        post = post,
                        isSaved = isSaved,
                        onReact = onReact,
                        onComments = onComments,
                        onToggleSave = onToggleSave,
                        onAddTag = onAddTag,
                        onAdjustInterest = onAdjustInterest,
                    )

                    // Memory badge
                    post.data?.memory?.let { memory ->
                        if (memory.yearsAgo > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = pluralStringResource(
                                    R.plurals.feeds_memory_years_ago_today,
                                    memory.yearsAgo,
                                    memory.yearsAgo
                                ),
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
                                    stringResource(
                                        R.string.feeds_travel_arrow,
                                        origin,
                                        destination
                                    )

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

                    // Post body — rendered at its full height so a long article scrolls
                    // within the page (the column's viewport-height floor keeps short
                    // posts screen-filling). passThroughTouches stays ON: the body's
                    // TextView must forward vertical drags to the page's verticalScroll
                    // (which scrolls the article) instead of consuming them — otherwise
                    // a drag over the text is swallowed and the page can neither scroll
                    // nor flip. Once the scroll bottoms out the leftover drag hands off
                    // to the pager and flips. Taps still open detail via the card's
                    // clickable. For RSS posts the first line is the article title —
                    // bolded for scannability.
                    if (post.body.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        PostBody(
                            post = post,
                            fillHeight = false,
                            passThroughTouches = true,
                            // Title is rendered above the byline, not inside the body.
                            includeTitle = false,
                            // Drop the trailing RSS link from the card preview.
                            stripTrailingLink = true,
                            // The body TextView passes touches through (returns
                            // false), so a tap on it never reaches onClick on its
                            // own. Wrap it in a clickable — like PostTitle — so
                            // tapping the body opens detail/source; vertical drags
                            // still pass through to the page scroll/flip.
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onClick),
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
                                att.thumbnailUrl
                                    ?: "/feeds/$attachmentFeed/-/attachments/${att.id}/thumbnail"
                            },
                            contentDescriptions = gridImages.map { it.name },
                            onClick = { index ->
                                lightboxState = attachmentImageUrls to (index + gridStartIndex)
                            }
                        )
                    }
                    if (otherAttachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = pluralStringResource(
                                R.plurals.feeds_attachment_count,
                                otherAttachments.size,
                                otherAttachments.size
                            ),
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


                    // Inline comments preview (top-level only, newest first).
                    // Uses the same CommentItem layout as the detail screen, but
                    // capped at 3 and view-only for management (edit/delete and
                    // threaded replies live on the post detail screen).
                    if (post.comments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val previewLimit = 3
                        val previewed = post.comments.take(previewLimit)
                        val remaining = post.comments.size - previewed.size
                        val commentFeedId = post.feedFingerprint
                            .ifEmpty { post.feed }
                            .ifEmpty { fallbackFeedId }
                        val anonymous = stringResource(R.string.feeds_anonymous)
                        Column {
                            for (comment in previewed) {
                                CommentItem(
                                    comment = comment,
                                    depth = 0,
                                    avatarUrl = "/feeds/$commentFeedId/-/${post.id}/${comment.id}/asset/avatar",
                                    feedId = commentFeedId,
                                    isEditing = false,
                                    editText = "",
                                    onEditTextChange = {},
                                    onSaveEdit = {},
                                    onCancelEdit = {},
                                    onReply = {
                                        onReplyComment(
                                            comment.id,
                                            comment.name.ifEmpty { anonymous },
                                            comment.body
                                        )
                                    },
                                    onEdit = {
                                        onEditComment(comment.id, comment.markdownSource)
                                    },
                                    onDelete = { onDeleteComment(comment.id) },
                                    onReact = { reaction ->
                                        onReactComment(
                                            comment.id,
                                            reaction
                                        )
                                    },
                                    canManage = false,
                                    isMine = currentUserId != null && comment.authorId == currentUserId,
                                    horizontalPadding = 0.dp,
                                )
                            }
                            if (remaining > 0) {
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.feeds_view_more_comments,
                                        remaining,
                                        remaining
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    // "N more comments" opens the post with its
                                    // comments to read the whole thread (the comment
                                    // icon is for composing, not viewing).
                                    modifier = Modifier.clickable(onClick = onViewComments)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    lightboxState?.let { (urls, index) ->
        LightboxScreen(
            images = urls,
            initialIndex = index,
            onDismiss = { lightboxState = null },
        )
    }
}

// Action bar: reaction bar, then tag / comment / save / edit / delete buttons.
// Rendered in the header block beneath the byline, so it flips and scrolls
// with the card. The host content column supplies the horizontal padding, so
// this only adds the inter-row vertical gap.
@Composable
private fun PostActionBar(
    post: Post,
    isSaved: Boolean,
    onReact: (String) -> Unit,
    onComments: () -> Unit,
    onToggleSave: () -> Unit,
    onAddTag: () -> Unit,
    onAdjustInterest: (Tag, String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReactionBar(
            reactions = toReactionCounts(post.reactions, post.myReaction),
            onReact = onReact,
            onRemoveReaction = { onReact(post.myReaction) },
            currentReaction = currentReactionType(post.myReaction),
        )
        // The reaction add button is a filled circle, so its padding sits inside
        // the background; this spacer makes the gap to the tag match the gaps
        // between the borderless icons (≈16dp everywhere).
        Spacer(modifier = Modifier.width(8.dp))
        PostTagsButton(
            tags = post.tags,
            onAddTag = onAddTag,
            onAdjustInterest = onAdjustInterest,
            horizontalPadding = 8.dp,
            iconSize = 24.dp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onComments)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            val hasComments = post.comments.isNotEmpty()
            val commentColor = MaterialTheme.colorScheme.onSurfaceVariant
            Icon(
                if (hasComments) Icons.Filled.ChatBubble else Icons.Default.ChatBubbleOutline,
                contentDescription = stringResource(R.string.feeds_comments),
                modifier = Modifier.size(24.dp),
                tint = commentColor
            )
            if (hasComments) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${post.comments.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = commentColor
                )
            }
        }
        Icon(
            if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            contentDescription = stringResource(
                if (isSaved) R.string.feeds_saved_remove else R.string.feeds_saved_save
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleSave)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Flexible space trails the action group, keeping reaction + tag +
        // comment + save left-aligned and adjacent rather than spread apart.
        Spacer(modifier = Modifier.weight(1f))
    }
}


@Composable
private fun InterestSuggestionsDialog(
    suggestions: List<InterestSuggestion>,
    onAdd: (InterestSuggestion) -> Unit,
    onDismissOne: (InterestSuggestion) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_suggested_interests)) },
        text = {
            if (suggestions.isEmpty()) {
                Text(stringResource(R.string.feeds_suggested_interests_empty))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(suggestions, key = { it.qid }) { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.label, style = MaterialTheme.typography.bodyMedium)
                                if (s.count > 0) {
                                    Text(
                                        stringResource(
                                            R.string.feeds_suggested_interests_count,
                                            s.count
                                        ),
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

/** A sort option for the feed sort chip: server value, label, and icon. */
private data class FeedSortOption(val value: String, val labelRes: Int, val icon: ImageVector)

/**
 * The feed's sort options in display order, each with its chip/menu icon. The
 * AI option is offered only when the server has an AI provider configured
 * ([hasAi]); it then applies to every feed.
 */
private fun feedSortOptions(hasAi: Boolean): List<FeedSortOption> = buildList {
    if (hasAi) {
        add(FeedSortOption("ai", R.string.feeds_sort_ai, Icons.Default.AutoAwesome))
    }
    add(FeedSortOption("interests", R.string.feeds_sort_interests, Icons.Default.Star))
    add(FeedSortOption("new", R.string.feeds_sort_new, Icons.Default.Schedule))
    add(FeedSortOption("hot", R.string.feeds_sort_hot, Icons.Default.LocalFireDepartment))
    add(FeedSortOption("top", R.string.feeds_sort_top, Icons.Default.EmojiEvents))
}

/**
 * The read-filter and sort chips shown at the top of the feed. Each is a compact
 * label-plus-chevron button that opens a dropdown of options.
 */
@Composable
private fun FeedFilterChips(
    currentSort: String,
    unreadOnly: Boolean,
    hasAi: Boolean,
    onSetSort: (String) -> Unit,
    onSetUnreadOnly: (Boolean) -> Unit,
    onMarkAllRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showReadMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val sortOptions = feedSortOptions(hasAi)
    val activeSort = sortOptions.firstOrNull { option -> option.value == currentSort }
        ?: sortOptions.first()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            FeedFilterChip(
                icon = if (unreadOnly) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                label = stringResource(
                    if (unreadOnly) R.string.feeds_unread else R.string.feeds_filter_all
                ),
                onClick = { showReadMenu = true },
            )
            DropdownMenu(
                expanded = showReadMenu,
                onDismissRequest = { showReadMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feeds_filter_all)) },
                    leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                    trailingIcon = {
                        if (!unreadOnly) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        onSetUnreadOnly(false)
                        showReadMenu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feeds_unread)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (unreadOnly) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        onSetUnreadOnly(true)
                        showReadMenu = false
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feeds_mark_all_read)) },
                    leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null) },
                    onClick = {
                        onMarkAllRead()
                        showReadMenu = false
                    },
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Box {
            FeedFilterChip(
                icon = activeSort.icon,
                label = stringResource(activeSort.labelRes),
                onClick = { showSortMenu = true },
            )
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
            ) {
                sortOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes)) },
                        leadingIcon = { Icon(option.icon, contentDescription = null) },
                        trailingIcon = {
                            if (option.value == currentSort) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            onSetSort(option.value)
                            showSortMenu = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * A single compact filter chip: an icon, a label, and a dropdown chevron.
 * Styled as a Material [FilterChip] (matching the market filters). Always shown
 * selected — each chip represents an active filter dimension rather than an
 * on/off toggle, so every option (e.g. All vs Unread) renders the same way.
 */
@Composable
private fun FeedFilterChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    // Drop the 48dp minimum touch target so the chip sits at its 32dp height
    // instead of reserving extra space above and below it.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        FilterChip(
            selected = true,
            onClick = onClick,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                )
            },
        )
    }
}

