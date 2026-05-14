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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.model.Reaction
import org.mochios.android.model.ReactionCount
import org.mochios.android.model.ReactionType
import org.mochios.android.model.resolveAttachmentUrl
import org.mochios.android.ui.components.FeatureDrawerItem
import org.mochios.android.ui.components.FeatureListDrawer
import org.mochios.android.ui.components.FlipboardPage
import org.mochios.android.ui.components.HtmlContent
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.ui.components.MediaGrid
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.ui.components.ReactionBar
import org.mochios.feeds.R
import org.mochios.feeds.model.Post
import org.mochios.feeds.ui.feedlist.FeedListViewModel
import org.mochios.feeds.ui.router.FEEDS_FEATURE
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onNavigateToPost: (feedId: String, postId: String, sourceUrl: String?) -> Unit,
    onNavigateToCreatePost: (String) -> Unit,
    onNavigateToEditPost: (String, String) -> Unit,
    onNavigateToSettings: (String) -> Unit,
    onSelectFeed: (String) -> Unit,
    onNavigateToFindFeeds: () -> Unit,
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
    LaunchedEffect(viewModel.feedId) {
        if (viewModel.feedId.isNotBlank()) {
            LastViewedStore.set(context, FEEDS_FEATURE, viewModel.feedId)
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
    val currentTag by viewModel.currentTag.collectAsState()
    val unreadOnly by viewModel.unreadOnly.collectAsState()


    var showOverflowMenu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Post?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { posts.size })

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
                navigationIcon = {
                    IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.feeds_title))
                    }
                },
                actions = {
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
                            if (permissions.manage) {
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
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
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
                                text = error!!,
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
                        SortDropdown(
                            currentSort = currentSort,
                            onSortChange = { viewModel.setSort(it) },
                            unreadOnly = unreadOnly,
                            onUnreadOnlyChange = { viewModel.setUnreadOnly(it) },
                            onMarkAllRead = { viewModel.markAllRead() }
                        )

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
                            // Magazine-style vertical pager: one post per page,
                            // with a 3D tilt + alpha falloff driven off the
                            // page's offset from the current page (the "fold"
                            // visual at the page edge). Not the full
                            // Flipboard two-half book-fold but feels page-flippy
                            // and is one composable rather than a custom
                            // graphics-layer split.
                            VerticalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                key = { page -> posts[page].id },
                            ) { page ->
                                val post = posts[page]
                                val routeFeedId = post.feedFingerprint.ifEmpty { viewModel.feedId }
                                // post.source.url is the RSS feed (XML) URL —
                                // not the article URL. The article URL lives
                                // in rss.link. Anything else falls through to
                                // the standard post detail screen.
                                val sourceUrl = post.data?.rss?.link?.takeIf { it.isNotEmpty() }
                                FlipboardPage(pagerState = pagerState, page = page) {
                                    PostCard(
                                        post = post,
                                        serverUrl = viewModel.serverUrl,
                                        fallbackFeedId = viewModel.feedId,
                                        canManage = permissions.manage,
                                        onClick = { onNavigateToPost(routeFeedId, post.id, sourceUrl) },
                                        onReact = { reaction -> viewModel.reactToPost(post.id, reaction) },
                                        onEdit = { onNavigateToEditPost(routeFeedId, post.id) },
                                        onDelete = { pendingDelete = post }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
    }
}

@Composable
private fun SortDropdown(
    currentSort: String,
    onSortChange: (String) -> Unit,
    unreadOnly: Boolean,
    onUnreadOnlyChange: (Boolean) -> Unit,
    onMarkAllRead: () -> Unit
) {
    val sorts = listOf(
        "ai" to stringResource(R.string.feeds_sort_ai),
        "interests" to stringResource(R.string.feeds_sort_interests),
        "new" to stringResource(R.string.feeds_sort_new),
        "hot" to stringResource(R.string.feeds_sort_hot),
        "top" to stringResource(R.string.feeds_sort_top),
    )
    val currentLabel = sorts.firstOrNull { it.first == currentSort }?.second
        ?: stringResource(R.string.feeds_sort_interests)
    var sortExpanded by remember { mutableStateOf(false) }
    var readExpanded by remember { mutableStateOf(false) }
    val readLabel = stringResource(if (unreadOnly) R.string.feeds_unread else R.string.feeds_filter_all)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box {
            FilterChip(
                selected = true,
                onClick = { sortExpanded = true },
                label = { Text(currentLabel) },
                trailingIcon = {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false }
            ) {
                sorts.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSortChange(value)
                            sortExpanded = false
                        }
                    )
                }
            }
        }
        Box {
            FilterChip(
                selected = true,
                onClick = { readExpanded = true },
                label = { Text(readLabel) },
                trailingIcon = {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
            DropdownMenu(
                expanded = readExpanded,
                onDismissRequest = { readExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feeds_filter_all)) },
                    onClick = {
                        onUnreadOnlyChange(false)
                        readExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feeds_unread)) },
                    onClick = {
                        onUnreadOnlyChange(true)
                        readExpanded = false
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feeds_mark_all_read)) },
                    onClick = {
                        onMarkAllRead()
                        readExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PostCard(
    post: Post,
    serverUrl: String,
    fallbackFeedId: String,
    canManage: Boolean,
    onClick: () -> Unit,
    onReact: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // Magazine-style page: full-screen surface, no card chrome. The
    // VerticalPager wrapper handles the 3D flip; the page itself is just
    // content on the theme background. The action row is hoisted out of
    // the scrollable content area below so it stays pinned at the bottom
    // of the screen — easier thumb reach on tall phones, and matches the
    // bottom-action-bar pattern Flipboard / Apple News use on full-screen
    // article pages.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 16.dp)
    ) {
      Column(
          modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .clickable(onClick = onClick)
      ) {
            // Header: source/feed name + time + overflow menu
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
                    text = LocalFormat.current.formatRelativeTime(post.created),
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
                HtmlContent(
                    html = boldRssTitle(post),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onClick
                )
            }

            // Attachment preview
            if (post.attachments.isNotEmpty()) {
                val images = post.attachments.filter { it.isImage }
                val others = post.attachments.filter { !it.isImage }
                val attachmentFeed = post.feed.ifEmpty { fallbackFeedId }
                if (images.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MediaGrid(
                        urls = images.map { att ->
                            resolveAttachmentUrl(serverUrl, att.url ?: "/feeds/$attachmentFeed/-/attachments/${att.id}")
                        },
                        thumbnailUrls = images.map { att ->
                            resolveAttachmentUrl(serverUrl, att.thumbnailUrl ?: "/feeds/$attachmentFeed/-/attachments/${att.id}/thumbnail")
                        },
                        contentDescriptions = images.map { it.name },
                        onClick = { onClick() }
                    )
                }
                if (others.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = pluralStringResource(R.plurals.feeds_attachment_count, others.size, others.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // RSS preview image. Tapping opens detail.
            post.data?.rss?.image?.takeIf { it.isNotEmpty() }?.let { imageUrl ->
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = imageUrl,
                    contentDescription = stringResource(R.string.feeds_image_preview),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClick),
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
                            modifier = Modifier.clickable(onClick = onClick)
                        )
                    }
                }
            }
        }

        // Bottom action bar: reaction bar, then comment / edit / delete
        // icon buttons. Pinned at the bottom of the full-screen page so
        // the user always knows where the menu is regardless of post
        // length. Spacer adds a thin top divider-ish gap above the row.
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReactionBar(
                reactions = toReactionCounts(post.reactions, post.myReaction),
                onReact = onReact,
                onRemoveReaction = { onReact(post.myReaction) },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = stringResource(R.string.feeds_comments),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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

/** For RSS-source posts the body is `title \n\n description \n\n link`.
 *  Wrap the leading title in `**…**` so Markwon renders it bold. */
private fun boldRssTitle(post: Post): String {
    val title = post.data?.rss?.title.orEmpty()
    val body = post.body
    if (title.isEmpty() || !body.startsWith(title)) return body
    return "**${title}**" + body.substring(title.length)
}

