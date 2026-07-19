// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feed

import android.content.ClipData
import android.content.Intent
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.model.Attachment
import org.mochios.android.push.SystemNotifications
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.FeatureDrawerItem
import org.mochios.android.ui.components.FeatureListDrawer
import org.mochios.android.ui.components.FlipBook
import org.mochios.android.ui.components.HtmlContent
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.ui.components.LightboxScreen
import org.mochios.android.ui.components.LocationMapView
import org.mochios.android.ui.components.MediaGrid
import org.mochios.android.ui.components.VideoFrame
import org.mochios.android.ui.components.VideoPlayer
import org.mochios.android.ui.components.rememberServerUrl
import org.mochios.android.ui.components.NewItemsPill
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.ui.components.NotificationBell
import org.mochios.android.ui.components.ReactionBar
import org.mochios.feeds.R
import org.mochios.feeds.api.InterestSuggestion
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.Tag
import org.mochios.feeds.ui.component.PostBody
import org.mochios.feeds.ui.component.PostTitle
import org.mochios.feeds.ui.component.currentReactionType
import org.mochios.feeds.ui.component.rssDisplayTitle
import org.mochios.feeds.ui.component.stripHtml
import org.mochios.feeds.ui.component.toReactionCounts
import org.mochios.feeds.ui.feedlist.CreateFeedDialog
import org.mochios.feeds.ui.feedlist.FeedListViewModel
import org.mochios.feeds.ui.post.CommentInputBar
import org.mochios.feeds.ui.post.PostTagsButton
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
    val clipboard = LocalClipboardManager.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val drawerFeeds by feedListViewModel.feeds.collectAsState()
    val showCreateFeedDialog by feedListViewModel.showCreateDialog.collectAsState()

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
    val rssCopiedMessage = stringResource(R.string.feeds_rss_url_copied)
    val rssClipboardLabel = stringResource(R.string.feeds_clipboard_label_rss)
    val unsubscribedMessage = stringResource(R.string.feeds_unsubscribed)
    val shareLinkTitle = stringResource(R.string.feeds_share_link_title)
    // Turn one-shot overflow-menu actions into a clipboard write, a toast, or a
    // jump back to the "All feeds" aggregate after unsubscribing.
    LaunchedEffect(Unit) {
        viewModel.actionEvents.collect { event ->
            when (event) {
                is FeedActionEvent.RssUrlReady -> {
                    clipboard.setClip(
                        ClipData.newPlainText(rssClipboardLabel, event.url).toClipEntry(),
                    )
                    Toast.makeText(context, rssCopiedMessage, Toast.LENGTH_SHORT).show()
                }

                is FeedActionEvent.ShareLinkReady -> {
                    shareLink(context, event.link, shareLinkTitle)
                }

                is FeedActionEvent.Unsubscribed -> {
                    Toast.makeText(context, unsubscribedMessage, Toast.LENGTH_SHORT).show()
                    onSelectFeed(LastViewedStore.ALL)
                }

                is FeedActionEvent.Failure -> {
                    event.error?.userMessage()?.let { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
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
    // Creating a feed from the drawer opens it, landing on its empty state.
    LaunchedEffect(Unit) {
        feedListViewModel.feedCreated.collect { newFeedId ->
            if (newFeedId.isNotEmpty()) onSelectFeed(newFeedId)
        }
    }
    // Reload when the screen returns to the foreground — most importantly after
    // creating a post here, so the new (or first) post shows without a manual
    // pull-to-refresh.
    val feedLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(feedLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reloadOnForeground()
            }
        }
        feedLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { feedLifecycleOwner.lifecycle.removeObserver(observer) }
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
    val postImages by viewModel.postImages.collectAsState()
    val newPostsCount by viewModel.newPostsCount.collectAsState()
    val commentTarget by viewModel.commentTarget.collectAsState()
    val commentDraft by viewModel.commentDraft.collectAsState()
    val commentAttachments by viewModel.commentAttachments.collectAsState()
    val isSendingComment by viewModel.isSendingComment.collectAsState()
    val commentFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri -> viewModel.addCommentAttachment(uri) }
    }


    var showOverflowMenu by remember { mutableStateOf(false) }
    // Whether the overflow menu is showing its nested "RSS feed" submenu.
    var showRssSubmenu by remember { mutableStateOf(false) }
    // True while the unsubscribe confirmation dialog is open.
    var pendingUnsubscribe by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Post?>(null) }
    // Set once the viewer closes the one-shot post-subscribe suggestions prompt,
    // so it doesn't reopen while the suggestions are still being acted on.
    var suggestionsDismissed by remember { mutableStateOf(false) }
    // (feedId, postId, commentId) of a comment pending delete confirmation.
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
            DrawerActionRow(
                title = stringResource(R.string.feeds_saved_menu),
                icon = Icons.Default.BookmarkBorder,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onNavigateToSaved()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.feeds_find_feeds),
                icon = Icons.Default.Search,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onNavigateToFindFeeds()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.feeds_create_feed),
                icon = Icons.Default.Add,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    feedListViewModel.showCreateDialog()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.feeds_logout),
                icon = Icons.AutoMirrored.Filled.Logout,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onLogout()
                },
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
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.feeds_new_post)
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(MochiR.string.common_more_options)
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
                                        text = { Text(stringResource(R.string.feeds_rss_feed)) },
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
                                            Text(stringResource(R.string.feeds_rss_mode_posts))
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
                                                    R.string.feeds_rss_mode_posts_comments
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
                                            viewModel.markAllRead { feedListViewModel.refreshSilently() }
                                            showOverflowMenu = false
                                        }
                                    )
                                    // Sources are only editable by managers, so
                                    // hide the entry for plain subscribers. In the
                                    // "All feeds" aggregate ("__all__" isn't a real
                                    // feed) route through the feed of the post
                                    // currently in view instead — the standard
                                    // aggregate per-post routing — shown when the
                                    // user owns that feed.
                                    val sourcesPost = posts.getOrNull(pagerState.currentPage)
                                    val sourcesFeedId = if (viewModel.isAllFeeds) {
                                        sourcesPost?.let { it.feedFingerprint.ifEmpty { it.feed } } ?: ""
                                    } else {
                                        viewModel.feedId
                                    }
                                    val sourcesAllowed = if (viewModel.isAllFeeds) {
                                        drawerFeeds.any { f ->
                                            f.owner == 1 && (f.id == sourcesPost?.feed ||
                                                (f.fingerprint.isNotEmpty() && f.fingerprint == sourcesPost?.feedFingerprint))
                                        }
                                    } else {
                                        permissions.manage
                                    }
                                    if (sourcesAllowed && sourcesFeedId.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.feeds_tab_sources)) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Link, contentDescription = null)
                                            },
                                            onClick = {
                                                // Open the Sources list scrolled to the
                                                // source of the post currently in view.
                                                onNavigateToSources(
                                                    sourcesFeedId,
                                                    sourcesPost?.source?.url,
                                                )
                                                showOverflowMenu = false
                                            }
                                        )
                                    }
                                    // Per-feed settings stay hidden on the
                                    // aggregate, which has no feed to configure.
                                    if (!viewModel.isAllFeeds && permissions.manage) {
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
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.feeds_rss_feed)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.RssFeed, contentDescription = null)
                                        },
                                        trailingIcon = {
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = { showRssSubmenu = true }
                                    )
                                    // Sharing a feed is an owner's call, so this
                                    // sits behind the same gate as Settings. The
                                    // aggregate has no single feed to share.
                                    if (!viewModel.isAllFeeds && permissions.manage) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.feeds_link)) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Link, contentDescription = null)
                                            },
                                            onClick = {
                                                viewModel.shareLink()
                                                showOverflowMenu = false
                                            }
                                        )
                                    }
                                    // Unsubscribe only for member feeds — owners/admins
                                    // manage (and delete) the feed instead.
                                    if (!viewModel.isAllFeeds && !permissions.manage) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.feeds_unsubscribe)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Logout,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                pendingUnsubscribe = true
                                                showOverflowMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                feedInfo?.banner?.takeIf { banner -> banner.isNotBlank() }?.let { banner ->
                    FeedBanner(banner = banner, feedId = viewModel.feedId)
                }
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
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(48.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(
                                            16.dp,
                                            Alignment.CenterVertically
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (unreadOnly) Icons.Default.DoneAll else Icons.Default.RssFeed,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Text(
                                            text = stringResource(
                                                if (unreadOnly) R.string.feeds_all_caught_up
                                                else R.string.feeds_no_posts_yet
                                            ),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (unreadOnly) {
                                            // Mirror the web's "All caught up" state: when the
                                            // unread filter has emptied the list, offer a way
                                            // back to every post instead of dead-ending.
                                            OutlinedButton(onClick = { viewModel.setUnreadOnly(false) }) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                                )
                                                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                                Text(stringResource(R.string.feeds_view_all_posts))
                                            }
                                        } else if (permissions.manage) {
                                            // An owner of a genuinely empty feed can create
                                            // the first post straight from here.
                                            Button(
                                                onClick = {
                                                    onNavigateToCreatePost(viewModel.feedId)
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(stringResource(R.string.feeds_create_first_post))
                                            }
                                        }
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
                                        // Upgrade the RSS thumbnail to the article's
                                        // og:image for pages the pager composes
                                        // (current + neighbours); resolves once per
                                        // post, server-cached after the first fetch.
                                        LaunchedEffect(post.id) {
                                            viewModel.resolvePostImage(post)
                                        }
                                        PostCard(
                                            post = post,
                                            fallbackFeedId = viewModel.feedId,
                                            upgradedRssImage = postImages[post.id],
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
                                            onReact = { reaction ->
                                                viewModel.reactToPost(
                                                    routeFeedId,
                                                    post.id,
                                                    reaction
                                                )
                                            },
                                            onToggleSave = { viewModel.toggleSave(post) },
                                            onAddTag = { label ->
                                                viewModel.addTag(post.id, label, null)
                                            },
                                            onAdjustInterest = { tag, direction ->
                                                viewModel.adjustInterest(
                                                    routeFeedId,
                                                    tag,
                                                    direction
                                                )
                                            },
                                            canManage = permissions.manage,
                                            onEdit = {
                                                onNavigateToEditPost(routeFeedId, post.id)
                                            },
                                            onDelete = { pendingDelete = post },
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
                feedName = feedInfo?.name.orEmpty(),
                suggestions = suggestedInterests,
                onAdd = { selected ->
                    viewModel.addInterests(selected)
                    suggestionsDismissed = true
                },
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

        if (pendingUnsubscribe) {
            AlertDialog(
                onDismissRequest = { pendingUnsubscribe = false },
                title = { Text(stringResource(R.string.feeds_unsubscribe_confirm)) },
                text = { Text(stringResource(R.string.feeds_unsubscribe_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.unsubscribe()
                            pendingUnsubscribe = false
                        }
                    ) {
                        Text(
                            stringResource(R.string.feeds_unsubscribe),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingUnsubscribe = false }) {
                        Text(stringResource(MochiR.string.common_cancel))
                    }
                }
            )
        }

        if (showCreateFeedDialog) {
            CreateFeedDialog(
                onDismiss = { feedListViewModel.hideCreateDialog() },
                onCreate = { name, privacy, memories ->
                    feedListViewModel.createFeed(name, privacy, memories)
                },
                viewModel = feedListViewModel,
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


// A click with no ripple / press indication. The post card's title and body
// open detail (or the source article) on tap, but shouldn't flash a highlight
// background while doing so — the page is full-bleed content, not a list row.
@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}

@Composable
private fun PostImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    onClick: (() -> Unit)? = null,
) {
    // Layout-stable image: the caller fixes the container's size up front
    // (height or aspect ratio) because posts carry no image dimensions, so
    // the space is reserved before the bitmap arrives and the surrounding
    // layout never shifts. A neutral tint marks the reserved space while
    // loading; a broken-image icon takes it over if loading fails. The
    // lightbox tap only arms once the image has actually loaded — there's
    // nothing to show full-screen for a pending or failed URL.
    var state by remember(url) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    // Bumped by tapping the broken-image glyph: recreating the painter under a
    // new key is what makes Coil re-attempt the fetch — flaky mobile links
    // stall image loads that a later tap would recover.
    var retry by remember(url) { mutableIntStateOf(0) }
    val loaded = state is AsyncImagePainter.State.Success
    Box(
        modifier = modifier.then(
            if (onClick != null && loaded) Modifier.clickable { onClick() } else Modifier
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            )
        }
        if (state is AsyncImagePainter.State.Error) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                        state = AsyncImagePainter.State.Empty
                        retry++
                    }
            )
        } else {
            key(retry) {
                AsyncImage(
                    model = url,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    alignment = alignment,
                    onState = { state = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (!loaded) {
                // A bare tint reads as "nothing is coming"; show that the
                // fetch is still in flight.
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

// A post is a "gallery" when its media is the content: no RSS article behind
// it, and at most a caption of text alongside the images/videos.
private const val GALLERY_CAPTION_LIMIT = 200

// Mosaic tiles shown before the last one collapses into a "+N" overlay.
private const val GALLERY_TILE_LIMIT = 6

// Byline (source/feed name + timestamp) and the compact metadata lines
// (memory, check-in, travelling), shared by the article and gallery layouts.
@Composable
private fun PostByline(post: Post) {
    // formatTimestamp obeys every timestamp preference (relative / absolute /
    // auto, and the date+time+timezone format).
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
}

// Full-page gallery layout: byline on top, a media mosaic filling the space
// down to the action bar, then the caption. Replaces the hero + article
// column when the post's media is its content.
@Composable
private fun GalleryContent(
    post: Post,
    media: List<Attachment>,
    caption: String,
    fallbackFeedId: String,
    onOpenImage: (imageIndex: Int) -> Unit,
    onPlayVideo: (url: String) -> Unit,
    onOpenPost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val attachmentFeed = post.feed.ifEmpty { fallbackFeedId }
    // Image requests go through Coil, whose mapper resolves relative URLs, but
    // the video frame decoder and ExoPlayer don't — resolve video URLs against
    // the server here. Absolute URLs pass through unchanged.
    val serverUrl = rememberServerUrl().trimEnd('/')
    val resolve: (String) -> String = { path ->
        when {
            path.startsWith("http://") || path.startsWith("https://") -> path
            path.startsWith("/") -> "$serverUrl$path"
            else -> "$serverUrl/$path"
        }
    }
    val fullUrl: (Attachment) -> String = { attachment ->
        attachment.url ?: "/feeds/$attachmentFeed/-/attachments/${attachment.id}"
    }

    // Same zero-range verticalScroll wrapper as the article column: its only
    // job is the nested-scroll handoff to the pager, so a swipe starting on a
    // mosaic tile forwards to the page flip instead of being fought over —
    // without it, gallery pages were noticeably harder to flip than articles.
    // The inner column is pinned to the viewport height, so the scroll range
    // stays zero and every drag delegates upward.
    BoxWithConstraints(modifier = modifier) {
        val viewportHeight = this.maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .height(viewportHeight)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 16.dp)
                ) {
                    PostByline(post)
                }
                // Check-in / travelling map, as the web card shows for located
                // posts. Guards match the detail screen: zero coordinates mean
                // the location carries no mappable point.
                val checkinWithCoordinates = post.data?.checkin?.takeIf { place ->
                    place.lat != 0.0 || place.lon != 0.0
                }
                val travellingWithCoordinates = post.data?.travelling?.takeIf { travelling ->
                    (travelling.origin?.lat != 0.0 || travelling.origin?.lon != 0.0) &&
                        (travelling.destination?.lat != 0.0 || travelling.destination?.lon != 0.0)
                }
                if (checkinWithCoordinates != null || travellingWithCoordinates != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        LocationMapView(
                            checkin = checkinWithCoordinates,
                            origin = travellingWithCoordinates?.origin,
                            destination = travellingWithCoordinates?.destination
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                GalleryMosaic(
                    media = media,
                    tileModel = { attachment ->
                        if (attachment.isVideo) {
                            // No server video-thumbnail route: decode the opening
                            // frame from the video itself (VideoFrameFetcher).
                            VideoFrame(resolve(fullUrl(attachment)))
                        } else {
                            attachment.thumbnailUrl
                                ?: "/feeds/$attachmentFeed/-/attachments/${attachment.id}/thumbnail"
                        }
                    },
                    onTap = { index ->
                        val attachment = media[index]
                        if (attachment.isVideo) {
                            onPlayVideo(resolve(fullUrl(attachment)))
                        } else {
                            // The lightbox spans images only, so map the mosaic
                            // index to the attachment's position among the images.
                            onOpenImage(media.take(index + 1).count { it.isImage } - 1)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                if (caption.isNotEmpty()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 8.dp)
                            .noRippleClickable(onOpenPost),
                    )
                }
                val files = post.attachments.filter { attachment ->
                    !attachment.isImage && !attachment.isVideo
                }
                if (files.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.feeds_attachment_count,
                            files.size,
                            files.size
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    )
                }
            }
        }
    }
}

// Count-adaptive fixed templates rather than a measured justified grid: the
// geometry is final on the first frame (no reflow as bitmaps arrive), the
// same layout-stability rule the hero image follows. Full-bleed, 2dp gaps.
@Composable
private fun GalleryMosaic(
    media: List<Attachment>,
    tileModel: (Attachment) -> Any,
    onTap: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = media.take(GALLERY_TILE_LIMIT)
    val overflow = media.size - shown.size

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val tile: @Composable (Int, Modifier, ContentScale) -> Unit = { index, tileModifier, scale ->
            GalleryTile(
                attachment = shown[index],
                model = tileModel(shown[index]),
                contentScale = scale,
                more = if (index == shown.lastIndex && overflow > 0) overflow else 0,
                onClick = { onTap(index) },
                modifier = tileModifier,
            )
        }
        when (shown.size) {
            // A lone photo keeps its natural framing; grids crop to fill.
            1 -> tile(0, Modifier.weight(1f).fillMaxWidth(), ContentScale.Fit)
            2 -> {
                tile(0, Modifier.weight(1f).fillMaxWidth(), ContentScale.Crop)
                tile(1, Modifier.weight(1f).fillMaxWidth(), ContentScale.Crop)
            }
            3 -> {
                tile(0, Modifier.weight(3f).fillMaxWidth(), ContentScale.Crop)
                Row(
                    modifier = Modifier.weight(2f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    tile(1, Modifier.weight(1f).fillMaxHeight(), ContentScale.Crop)
                    tile(2, Modifier.weight(1f).fillMaxHeight(), ContentScale.Crop)
                }
            }
            4 -> {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    tile(0, Modifier.weight(1f).fillMaxHeight(), ContentScale.Crop)
                    tile(1, Modifier.weight(1f).fillMaxHeight(), ContentScale.Crop)
                }
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    tile(2, Modifier.weight(1f).fillMaxHeight(), ContentScale.Crop)
                    tile(3, Modifier.weight(1f).fillMaxHeight(), ContentScale.Crop)
                }
            }
            5 -> {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    tile(0, Modifier.weight(1f).fillMaxHeight(), ContentScale.Crop)
                    tile(1, Modifier.weight(1f).fillMaxHeight(), ContentScale.Crop)
                }
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    tile(2, Modifier.weight(1f).fillMaxHeight(), ContentScale.Crop)
                    tile(3, Modifier.weight(1f).fillMaxHeight(), ContentScale.Crop)
                }
                tile(4, Modifier.weight(1f).fillMaxWidth(), ContentScale.Crop)
            }
            else -> {
                for (row in 0 until 3) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        tile(row * 2, Modifier.weight(1f).fillMaxHeight(), ContentScale.Crop)
                        tile(row * 2 + 1, Modifier.weight(1f).fillMaxHeight(), ContentScale.Crop)
                    }
                }
            }
        }
    }
}

// One mosaic cell: an image thumbnail (layout-stable PostImage) or a decoded
// video frame with a play glyph, with a "+N" overlay on the last tile when
// the post has more media than the mosaic shows.
@Composable
private fun GalleryTile(
    attachment: Attachment,
    model: Any,
    contentScale: ContentScale,
    more: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (attachment.isVideo) {
            Box(modifier = Modifier.matchParentSize().background(Color.Black))
            AsyncImage(
                model = model,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (more == 0) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = stringResource(MochiR.string.common_play_video),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(48.dp),
                )
            }
        } else {
            PostImage(
                url = model as String,
                contentDescription = attachment.name,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (more > 0) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(MochiR.string.media_grid_more_count, more),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

@Composable
private fun PostCard(
    post: Post,
    fallbackFeedId: String,
    // Article og:image resolved by the ViewModel, upgrading the RSS item's
    // (often tiny) thumbnail. Null = not resolved yet, "" = nothing better.
    upgradedRssImage: String?,
    isSaved: Boolean,
    onClick: () -> Unit,
    onComments: () -> Unit,
    onViewComments: () -> Unit,
    onReact: (String) -> Unit,
    onToggleSave: () -> Unit,
    onAddTag: (String) -> Unit,
    onAdjustInterest: (Tag, String) -> Unit,
    canManage: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
    val rssImageUrl = upgradedRssImage?.takeIf { it.isNotEmpty() }
        ?: post.data?.rss?.image?.takeIf { it.isNotEmpty() }
    val heroFromAttachment = attachmentImageUrls.isNotEmpty()
    val heroUrl = attachmentImageUrls.firstOrNull() ?: rssImageUrl
    // Gallery posts — the media is the content (no RSS article, at most a
    // caption of text) — take the full-page mosaic layout instead of the
    // hero + article column below.
    val mediaAttachments = post.attachments.filter { it.isImage || it.isVideo }
    val captionText = remember(post.body) { stripHtml(post.body).trim() }
    val isGallery = post.data?.rss == null && mediaAttachments.isNotEmpty() &&
        captionText.length <= GALLERY_CAPTION_LIMIT
    // Full-screen playback for a tapped gallery video tile. null = closed.
    var playingVideoUrl by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (isGallery) {
            GalleryContent(
                post = post,
                media = mediaAttachments,
                caption = captionText,
                fallbackFeedId = fallbackFeedId,
                onOpenImage = { imageIndex ->
                    lightboxState = attachmentImageUrls to imageIndex
                },
                onPlayVideo = { url -> playingVideoUrl = url },
                onOpenPost = onClick,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
        if (!isGallery && heroUrl != null) {
            // Square corners; the hero region has a FIXED height reserved
            // from the first frame — the post data carries no image
            // dimensions, so this is the only way a slow image load can't
            // shift (and re-truncate) the text below it. The height depends
            // only on screen geometry, never the bitmap: RSS heroes get a
            // width-driven 16:9 region (news images are almost always
            // landscape; reserving more just left a dead band between image
            // and text on tall screens), while attachment heroes keep the
            // half-screen region since user photos are often portrait.
            // ContentScale.Fit shows a mismatched image whole with margins
            // rather than cropped. Top-aligned so the image stays full-bleed
            // against the screen edge. Tap opens the full-screen lightbox.
            val configuration = LocalConfiguration.current
            val heroHeight = if (heroFromAttachment) {
                (configuration.screenHeightDp / 2).dp
            } else {
                minOf(configuration.screenWidthDp * 9 / 16, configuration.screenHeightDp / 2).dp
            }
            PostImage(
                url = heroUrl,
                contentDescription = stringResource(R.string.feeds_image_preview),
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight),
                onClick = {
                    lightboxState = if (heroFromAttachment) {
                        attachmentImageUrls to 0
                    } else {
                        listOf(heroUrl) to 0
                    }
                },
            )
        }
        // One post = one screen. The inner column is pinned to the viewport
        // height, so its content fills the screen and the overflow is ellipsised
        // rather than scrolled. The verticalScroll is kept ONLY for its
        // nested-scroll handoff to the pager (its scroll range is zero) — a swipe
        // forwards to the pager and flips to the next post.
        if (!isGallery) BoxWithConstraints(
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
                        .height(viewportHeight)
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
                            // No internal ripple; the no-indication modifier below
                            // carries the tap-to-open without a highlight background.
                            modifier = Modifier.noRippleClickable(onClick),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Byline: source/feed name + timestamp, below the title,
                    // then the compact metadata lines.
                    PostByline(post)

                    // Post body — fillHeight gives it the weighted remainder of the
                    // viewport-height column, so it fills the space under the header
                    // and ellipsises the overflow ("enough to fill the screen", no
                    // scroll through the whole article). passThroughTouches stays ON:
                    // the body's TextView forwards vertical drags to the page's
                    // (zero-range) verticalScroll, which hands off to the pager and
                    // flips immediately, instead of swallowing them. Taps still open
                    // detail via the card's clickable. For RSS posts the first line
                    // is the article title — bolded for scannability.
                    if (post.body.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        PostBody(
                            post = post,
                            fillHeight = true,
                            passThroughTouches = true,
                            // Title is rendered above the byline, not inside the body.
                            includeTitle = false,
                            // Drop the trailing RSS link from the card preview.
                            stripTrailingLink = true,
                            // The body TextView passes touches through (returns
                            // false), so a tap on it never reaches onClick on its
                            // own. Wrap it in a no-ripple clickable — so tapping the
                            // body opens detail/source without a highlight background;
                            // vertical drags still pass through to the page scroll/flip.
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .noRippleClickable(onClick),
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
                    // Fixed 16:9 region for the same no-layout-shift reason as the
                    // hero. Tapping opens the lightbox (web parity).
                    if (rssImageUrl != null && rssImageUrl != heroUrl) {
                        Spacer(modifier = Modifier.height(8.dp))
                        PostImage(
                            url = rssImageUrl,
                            contentDescription = stringResource(R.string.feeds_image_preview),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp)),
                            onClick = { lightboxState = listOf(rssImageUrl) to 0 },
                        )
                    }


                    // Inline comments preview (top-level only, newest first).
                    // Each is a single compact line — avatar, name, message text
                    // (taking the free width), then the time — capped at 3.
                    // Edit / delete / replies live on the post detail screen.
                    if (post.comments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val previewLimit = 3
                        val previewed = post.comments.take(previewLimit)
                        val remaining = post.comments.size - previewed.size
                        val commentFeedId = post.feedFingerprint
                            .ifEmpty { post.feed }
                            .ifEmpty { fallbackFeedId }
                        val anonymous = stringResource(R.string.feeds_anonymous)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (comment in previewed) {
                                val displayName = comment.name.ifEmpty { anonymous }
                                Row(
                                    // Tapping a comment opens the post (detail or the
                                    // source article) with its comments expanded — no
                                    // ripple, matching the title and body.
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .noRippleClickable(onViewComments),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    EntityAvatar(
                                        name = displayName,
                                        src = "/feeds/$commentFeedId/-/${post.id}/${comment.id}/asset/avatar",
                                        seed = comment.authorId.ifEmpty { displayName },
                                        size = 20.dp,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
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
                                        text = stripHtml(comment.body),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = LocalFormat.current
                                            .formatRelativeTime(comment.created),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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

        // Bottom action bar: react, tag, comment, save. Lives outside the
        // flipping page content so it stays pinned at the screen bottom and
        // simply reflects the current post, rather than scrolling with the card.
        PostActionBar(
            post = post,
            isSaved = isSaved,
            canManage = canManage,
            onReact = onReact,
            onComments = onComments,
            onToggleSave = onToggleSave,
            onAddTag = onAddTag,
            onAdjustInterest = onAdjustInterest,
            onEdit = onEdit,
            onDelete = onDelete,
        )
    }

    lightboxState?.let { (urls, index) ->
        LightboxScreen(
            images = urls,
            initialIndex = index,
            onDismiss = { lightboxState = null },
        )
    }

    // Full-screen playback for gallery video tiles, matching the attachment
    // gallery's player dialog.
    playingVideoUrl?.let { url ->
        Dialog(
            onDismissRequest = { playingVideoUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
            ) {
                VideoPlayer(url = url, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

// Bottom action bar: reaction bar, then tag / comment / save buttons. Lives
// outside the flipping page content so it stays pinned at the screen bottom and
// simply reflects whichever post is current (updating on swipe-commit), rather
// than flipping or scrolling along with the card.
@Composable
private fun PostActionBar(
    post: Post,
    isSaved: Boolean,
    canManage: Boolean,
    onReact: (String) -> Unit,
    onComments: () -> Unit,
    onToggleSave: () -> Unit,
    onAddTag: (String) -> Unit,
    onAdjustInterest: (Tag, String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
        // Flexible space pushes the manager actions to the right edge, keeping
        // the react / tag / comment / save group left-aligned and adjacent.
        Spacer(modifier = Modifier.weight(1f))
        // Edit / delete — managers only — styled like the comment and save
        // icons above, sitting at the right end of the bar.
        if (canManage) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(MochiR.string.common_edit),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(MochiR.string.common_delete),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


/**
 * Post-subscribe prompt offering the feed's topics as interests. Every topic
 * starts selected; the viewer unchecks any they don't want, then "Add N
 * interests" commits the selection in one pass or "Skip" dismisses the prompt.
 */
@Composable
private fun InterestSuggestionsDialog(
    feedName: String,
    suggestions: List<InterestSuggestion>,
    onAdd: (List<InterestSuggestion>) -> Unit,
    onDismiss: () -> Unit,
) {
    // qid -> selected. Seeded to all-on so the common "take everything" path is
    // a single tap; the map is keyed by qid so it survives recomposition.
    val selected = remember(suggestions) {
        mutableStateMapOf<String, Boolean>().apply {
            suggestions.forEach { put(it.qid, true) }
        }
    }
    val selectedCount = selected.count { it.value }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (feedName.isNotEmpty()) {
                    stringResource(R.string.feeds_suggested_interests_title, feedName)
                } else {
                    stringResource(R.string.feeds_suggested_interests)
                }
            )
        },
        text = {
            if (suggestions.isEmpty()) {
                Text(stringResource(R.string.feeds_suggested_interests_empty))
            } else {
                Column {
                    Text(
                        stringResource(R.string.feeds_suggested_interests_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                    ) {
                        items(suggestions, key = { it.qid }) { s ->
                            val checked = selected[s.qid] ?: false
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selected[s.qid] = !checked }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { selected[s.qid] = it },
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    s.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                if (s.count > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        pluralStringResource(
                                            R.plurals.feeds_suggested_interests_post_count,
                                            s.count,
                                            s.count,
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(suggestions.filter { selected[it.qid] == true }) },
                enabled = selectedCount > 0,
            ) {
                Text(
                    pluralStringResource(
                        R.plurals.feeds_suggested_interests_add_count,
                        selectedCount,
                        selectedCount,
                    )
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.feeds_suggested_interests_skip))
            }
        },
    )
}

private fun bannerContentHash(content: String): String {
    var hash = 5381
    for (c in content) hash += (hash shl 5) + c.code
    return Integer.toHexString(hash)
}

/**
 * Dismissible markdown banner shown at the top of a feed, mirroring the forums
 * banner. Dismissal is persisted per feed keyed by a content hash, so a new
 * banner (different text) reappears even after a previous one was dismissed.
 */
@Composable
private fun FeedBanner(banner: String, feedId: String) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("feeds_banner_dismissed", Context.MODE_PRIVATE)
    }
    val prefKey = remember(feedId) { "feed_$feedId" }
    val contentHash = remember(banner) { bannerContentHash(banner) }
    var dismissed by remember(prefKey, contentHash) {
        mutableStateOf(prefs.getString(prefKey, null) == contentHash)
    }
    if (dismissed) return

    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HtmlContent(html = banner, modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    prefs.edit { putString(prefKey, contentHash) }
                    dismissed = true
                },
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.feeds_banner_dismiss),
                )
            }
        }
    }
}

/**
 * Hand [link] to the system share sheet, whose target list already includes
 * "Copy" — so there is no in-app copy affordance to maintain.
 */
private fun shareLink(context: Context, link: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link)
        // Names the sheet's content preview. Android 10+ ignores the
        // createChooser title, so without this the sheet reads "Sharing text".
        putExtra(Intent.EXTRA_TITLE, title)
    }
    val chooser = Intent.createChooser(intent, title)
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
