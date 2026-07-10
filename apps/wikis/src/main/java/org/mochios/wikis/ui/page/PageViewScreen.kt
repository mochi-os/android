// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.page

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.wikis.R
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPage
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.MarkdownContent
import org.mochios.wikis.ui.components.TagManager
import org.mochios.wikis.ui.components.TocHeading
import org.mochios.wikis.ui.components.WikiContextValue
import org.mochios.wikis.ui.dialog.RenamePageDialog
import org.mochios.android.R as MochiR

/** Feature key shared with the WikisRouter for [LastViewedStore.set] / .get(). */
const val WIKIS_FEATURE = "wikis"

/**
 * Central wiki page-viewing experience.
 *
 * Mirrors the web equivalent in
 * `apps/wikis/web/src/features/wiki/page-view.tsx` +
 * `wiki-page-content.tsx`:
 *
 *  - Sticky top bar with title, back arrow, overflow `MoreHoriz` menu.
 *  - Body: optional table-of-contents card (phone-only collapsible), then
 *    [MarkdownContent] for the page itself.
 *  - Footer: inline tag chips, version chip, "Updated …" timestamp.
 *  - Page-not-found empty state with a "Create this page" CTA gated on
 *    [WikiPermissions.edit].
 *  - Loading state and error state via the lib's [ErrorState] /
 *    skeleton-style placeholders.
 *
 * The screen reads `wikiId` and `page` from the [NavController]'s back-stack
 * arguments via [PageViewModel]'s [androidx.lifecycle.SavedStateHandle], so
 * the host nav-graph just routes `wikis/{wikiId}/{page}` to this composable
 * — no per-screen arg plumbing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageViewScreen(
    navController: NavController,
    viewModel: PageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboardLabelRss = stringResource(R.string.wikis_pageview_clipboard_label_rss)
    val rssCopiedMsg = stringResource(R.string.wikis_pageview_rss_copied)
    val shareSubject = state.page?.title ?: state.wiki?.name ?: ""

    // Persist last-viewed page so a fresh launch lands the user back here.
    // Mirrors web's `setLastLocation(wiki.id, slug)` in
    // `apps/wikis/web/src/routes/_authenticated/$page/index.tsx`. The router
    // composable consumes both values: the wiki id picks the entity to open,
    // the slug rides through as the page within it.
    LaunchedEffect(viewModel.wikiId, viewModel.slug) {
        if (viewModel.wikiId.isNotBlank() && viewModel.slug.isNotBlank()) {
            LastViewedStore.set(context, WIKIS_FEATURE, viewModel.wikiId)
            LastViewedStore.set(
                context,
                "${WIKIS_FEATURE}_page_${viewModel.wikiId}",
                viewModel.slug,
            )
        }
    }

    // Side-effect events from the ViewModel (clipboard, error toast).
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PageViewEvent.CopyRssUrl -> {
                    clipboard.setClip(
                        ClipData.newPlainText(clipboardLabelRss, event.url).toClipEntry(),
                    )
                    snackbar.showSnackbar(rssCopiedMsg)
                }
                is PageViewEvent.ShowError -> {
                    snackbar.showSnackbar(event.error.userMessage())
                }
            }
        }
    }

    val wikiInfo = state.wiki
    val title = state.page?.title?.takeIf { it.isNotBlank() }
        ?: viewModel.slug.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.wikis_pageview_title_fallback)

    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var unsubscribeDialogOpen by remember { mutableStateOf(false) }
    var isUnsubscribing by remember { mutableStateOf(false) }

    val canUnsubscribe = wikiInfo?.source?.isNotBlank() == true

    // Build a WikiContextValue once info is loaded, so downstream components
    // (MarkdownContent, AuthorAvatar, attachment galleries) can resolve URLs.
    // While info is still loading we degrade to a synthetic context built
    // from the wikiId alone — MarkdownContent doesn't need the real WikiInfo
    // for its attachment-URL helper, just the wikiId + serverUrl.
    val wikiContext = remember(viewModel.wikiId, viewModel.serverUrl, wikiInfo, state.permissions) {
        WikiContextValue(
            wikiId = viewModel.wikiId,
            info = wikiInfo ?: WikiInfo(id = viewModel.wikiId),
            permissions = state.permissions,
            serverUrl = viewModel.serverUrl,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreHoriz,
                                contentDescription = stringResource(R.string.wikis_pageview_page_actions),
                            )
                        }
                        PageOverflowMenu(
                            expanded = menuExpanded,
                            onDismiss = { menuExpanded = false },
                            wikiId = viewModel.wikiId,
                            slug = viewModel.slug,
                            permissions = state.permissions,
                            commentCount = state.commentCount,
                            canUnsubscribe = canUnsubscribe,
                            onEdit = {
                                menuExpanded = false
                                navController.navigate(
                                    WikisApp.pageEdit(viewModel.wikiId, viewModel.slug)
                                )
                            },
                            onRename = {
                                menuExpanded = false
                                showRenameDialog = true
                            },
                            onHistory = {
                                menuExpanded = false
                                navController.navigate(
                                    WikisApp.pageHistory(viewModel.wikiId, viewModel.slug)
                                )
                            },
                            onComments = {
                                menuExpanded = false
                                navController.navigate(
                                    WikisApp.comments(viewModel.wikiId, viewModel.slug)
                                )
                            },
                            onDelete = {
                                menuExpanded = false
                                navController.navigate(
                                    WikisApp.pageDelete(viewModel.wikiId, viewModel.slug)
                                )
                            },
                            onSearch = {
                                menuExpanded = false
                                navController.navigate(WikisApp.search(viewModel.wikiId))
                            },
                            onTags = {
                                menuExpanded = false
                                navController.navigate(WikisApp.tags(viewModel.wikiId))
                            },
                            onChanges = {
                                menuExpanded = false
                                navController.navigate(WikisApp.changes(viewModel.wikiId))
                            },
                            onNewPage = {
                                menuExpanded = false
                                navController.navigate(WikisApp.newPage(viewModel.wikiId))
                            },
                            onSettings = {
                                menuExpanded = false
                                navController.navigate(WikisApp.settings(viewModel.wikiId))
                            },
                            onShare = {
                                menuExpanded = false
                                sharePageLink(
                                    context = context,
                                    subject = shareSubject,
                                    url = viewModel.shareUrl(),
                                    chooserTitle = context.getString(
                                        R.string.wikis_pageview_share_chooser
                                    ),
                                )
                            },
                            onUnsubscribe = {
                                menuExpanded = false
                                unsubscribeDialogOpen = true
                            },
                            onRssCopy = { mode ->
                                menuExpanded = false
                                viewModel.copyRssUrl(mode)
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        CompositionLocalProvider(LocalWikiContext provides wikiContext) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when {
                    state.isLoading && state.page == null && !state.notFound -> {
                        PageSkeleton()
                    }
                    state.notFound -> {
                        PageNotFoundBody(
                            slug = viewModel.slug,
                            permissions = state.permissions,
                            onCreate = {
                                navController.navigate(
                                    WikisApp.pageEdit(viewModel.wikiId, viewModel.slug)
                                )
                            },
                        )
                    }
                    state.error != null && state.page == null -> {
                        ErrorState(
                            error = state.error!!,
                            onRetry = { viewModel.loadPage() },
                        )
                    }
                    state.page != null -> {
                        PageBody(
                            page = state.page!!,
                            wikiId = viewModel.wikiId,
                            slug = viewModel.slug,
                            canEdit = state.permissions.edit,
                            snackbarHost = snackbar,
                            missingLinks = state.missingLinks,
                            onInternalLink = { slug ->
                                navController.navigate(
                                    WikisApp.pageView(viewModel.wikiId, slug)
                                )
                            },
                            onTagTap = { tag ->
                                navController.navigate(
                                    WikisApp.tagPages(viewModel.wikiId, tag)
                                )
                            },
                            onTagsChanged = { newTags ->
                                viewModel.updatePageTags(newTags)
                            },
                        )
                    }
                }
            }
        }
    }

    if (unsubscribeDialogOpen) {
        AlertDialog(
            onDismissRequest = { if (!isUnsubscribing) unsubscribeDialogOpen = false },
            title = { Text(stringResource(R.string.wikis_unsubscribe_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.wikis_unsubscribe_confirm_message,
                        state.wiki?.name ?: "",
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isUnsubscribing,
                    onClick = {
                        isUnsubscribing = true
                        scope.launch {
                            try {
                                viewModel.unsubscribe()
                                snackbar.showSnackbar(
                                    context.getString(R.string.wikis_unsubscribe_success)
                                )
                                unsubscribeDialogOpen = false
                                navController.popBackStack(WikisApp.HOME, inclusive = false)
                            } catch (e: Exception) {
                                snackbar.showSnackbar(
                                    e.message?.takeIf { it.isNotBlank() }
                                        ?: context.getString(R.string.wikis_subscribe_failed)
                                )
                            } finally {
                                isUnsubscribing = false
                            }
                        }
                    },
                ) {
                    Text(
                        if (isUnsubscribing) stringResource(R.string.wikis_unsubscribing)
                        else stringResource(R.string.wikis_unsubscribe_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isUnsubscribing,
                    onClick = { unsubscribeDialogOpen = false },
                ) {
                    Text(stringResource(MochiR.string.common_cancel))
                }
            },
        )
    }

    // Rename-page dialog. Self-contained: it runs the rename mutation via the
    // repository and reports back so we can navigate to the new slug and toast
    // how many pages/links were updated (mirrors the web rename-page toast).
    RenamePageDialog(
        open = showRenameDialog,
        wikiId = viewModel.wikiId,
        currentSlug = viewModel.slug,
        onDismiss = { showRenameDialog = false },
        onRenamed = { newSlug, _, _ ->
            showRenameDialog = false
            // Web's toast appends a "Renamed N pages, updated M links" count via
            // plurals, but those plurals aren't localized (default catalog only);
            // use the fully localized success string so non-English users don't
            // get an English toast.
            scope.launch {
                snackbar.showSnackbar(context.getString(R.string.wikis_rename_page_success))
            }
            navController.navigate(WikisApp.pageView(viewModel.wikiId, newSlug)) {
                popUpTo(WikisApp.wikiHome(viewModel.wikiId)) { inclusive = false }
            }
        },
    )
}

/**
 * Article body, table-of-contents card, and footer. Captures the H2..H4
 * heading list pushed up by [MarkdownContent] so the [TableOfContents] card
 * can render the entries.
 *
 * Mirrors web's scroll-driven active-heading tracker in
 * `apps/wikis/web/src/features/wiki/page-view.tsx` lines 115-180.
 * [MarkdownContent] reports per-heading Y offsets (relative to its own
 * top); we compare them against the current `scrollState.value` plus a
 * 120dp activation offset to pick the topmost-visible heading and
 * highlight that TOC row.
 */
@Composable
private fun PageBody(
    page: WikiPage,
    wikiId: String,
    slug: String,
    canEdit: Boolean,
    snackbarHost: SnackbarHostState,
    missingLinks: List<String>,
    onInternalLink: (slug: String) -> Unit,
    onTagTap: (tag: String) -> Unit,
    onTagsChanged: (List<String>) -> Unit,
) {
    val headings = remember { mutableStateOf(emptyList<TocHeading>()) }
    val scrollState = rememberScrollState()
    val tocScope = rememberCoroutineScope()
    // Map of headingId -> Y in pixels, measured by MarkdownContent's
    // per-segment TextViews from their layout pass. Positions are
    // relative to MarkdownContent's own top — we add `markdownOffsetY`
    // (below) to get the absolute Y in the outer scrolling Column's
    // content space.
    var headingPositions by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    // Y offset of MarkdownContent inside the outer scrolling Column.
    // Captured via `onGloballyPositioned` on the wrapper so we can shift
    // `headingPositions` into the outer scroll's coordinate system before
    // comparing them against `scrollState.value`.
    var markdownOffsetY by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val activationOffsetPx = with(density) { 120.dp.toPx() }.toInt()

    // Derive the active heading from current scroll position. Matches the
    // web's algorithm: pick the heading whose top is just above
    // (scrollValue + activationOffset); fall back to the first heading
    // when the user is above all of them.
    val activeId = computeActiveHeading(
        headings = headings.value,
        positions = headingPositions.mapValues { it.value + markdownOffsetY },
        scrollY = scrollState.value,
        activationOffsetPx = activationOffsetPx,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (headings.value.isNotEmpty()) {
            TableOfContents(
                headings = headings.value,
                activeId = activeId,
                onHeadingTap = { id ->
                    headingPositions[id]?.let { y ->
                        tocScope.launch {
                            // The map is in MarkdownContent-relative
                            // coordinates; add the markdown wrapper's Y
                            // inside the outer scroll Column to get the
                            // absolute target. Mirrors web's
                            // `scrollIntoView({block:'start'})`.
                            scrollState.animateScrollTo(y + markdownOffsetY)
                        }
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
        }

        // The wrapper captures MarkdownContent's Y inside the outer
        // verticalScroll Column so the per-heading positions reported
        // *within* MarkdownContent can be shifted into outer-scroll
        // coordinates before comparing them against `scrollState.value`.
        Box(
            modifier = Modifier.onGloballyPositioned { coords ->
                val parentY = coords.positionInParent().y.toInt()
                if (parentY != markdownOffsetY) markdownOffsetY = parentY
            }
        ) {
            MarkdownContent(
                content = page.content,
                missingLinks = missingLinks,
                onHeadingsExtracted = { headings.value = it },
                onHeadingPositions = { positions -> headingPositions = positions },
                onInternalLink = onInternalLink,
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        PageFooter(
            page = page,
            wikiId = wikiId,
            slug = slug,
            canEdit = canEdit,
            snackbarHost = snackbarHost,
            onTagTap = onTagTap,
            onTagsChanged = onTagsChanged,
        )
    }
}

/**
 * Pure function: pick the active TOC heading given current scroll offset
 * and the per-heading Y positions inside [MarkdownContent]. Mirrors web's
 * `updateActiveHeadingFromScroll`:
 *
 *  - Choose the heading whose top is at or above `(scrollY + activation)`.
 *  - Fall back to the first heading when the user is above all of them.
 *  - Returns `null` only if there are no headings at all (in which case
 *    the TOC card wouldn't render either).
 */
internal fun computeActiveHeading(
    headings: List<TocHeading>,
    positions: Map<String, Int>,
    scrollY: Int,
    activationOffsetPx: Int,
): String? {
    if (headings.isEmpty()) return null
    val threshold = scrollY + activationOffsetPx
    var current: String? = headings.firstOrNull()?.id
    for (h in headings) {
        val y = positions[h.id] ?: continue
        if (y <= threshold) {
            current = h.id
        } else {
            break
        }
    }
    return current
}

/**
 * Footer row: tag chips on the start, version chip + relative-updated
 * timestamp on the end. Mirrors page-view.tsx lines 248-262.
 *
 * The tag block is delegated to [TagManager] so the add / remove
 * affordances and per-tag navigation stay consistent with the rest of the
 * wiki surfaces. The version chip + updated timestamp render below the
 * tags so the right-aligned cluster doesn't fight the inline "+" / "Add
 * tag" chip for horizontal space.
 */
@Composable
private fun PageFooter(
    page: WikiPage,
    wikiId: String,
    slug: String,
    canEdit: Boolean,
    snackbarHost: SnackbarHostState,
    onTagTap: (tag: String) -> Unit,
    onTagsChanged: (List<String>) -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            TagManager(
                wikiId = wikiId,
                slug = slug,
                tags = page.tags,
                canEdit = canEdit,
                onTagClick = onTagTap,
                onTagsChanged = onTagsChanged,
                snackbarHostState = snackbarHost,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = stringResource(R.string.wikis_pageview_version, page.version),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Text(
                    text = stringResource(
                        R.string.wikis_pageview_updated,
                        LocalFormat.current.formatTimestamp(page.updated),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PageNotFoundBody(
    slug: String,
    permissions: WikiPermissions,
    onCreate: () -> Unit,
) {
    EmptyState(
        icon = Icons.Default.SearchOff,
        title = stringResource(R.string.wikis_pageview_not_found_title),
        subtitle = stringResource(R.string.wikis_pageview_not_found_description, slug),
        action = {
            if (permissions.edit) {
                Button(onClick = onCreate) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(text = stringResource(R.string.wikis_pageview_create_this_page))
                }
            }
        },
    )
}

/**
 * Loading placeholder that matches the People app's pattern — a few thin
 * neutral bars in place of the eventual article. Mirrors web's
 * [PageViewSkeleton] in page-view.tsx lines 307-322.
 */
@Composable
private fun PageSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        SkeletonBar(widthFraction = 1f)
        Spacer(Modifier.height(8.dp))
        SkeletonBar(widthFraction = 1f)
        Spacer(Modifier.height(8.dp))
        SkeletonBar(widthFraction = 0.75f)
        Spacer(Modifier.height(8.dp))
        SkeletonBar(widthFraction = 1f)
        Spacer(Modifier.height(8.dp))
        SkeletonBar(widthFraction = 0.85f)
    }
}

@Composable
private fun SkeletonBar(widthFraction: Float) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(14.dp),
    ) { /* no content — just the tinted surface as a placeholder */ }
}

private fun sharePageLink(
    context: Context,
    subject: String,
    url: String,
    chooserTitle: String,
) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
