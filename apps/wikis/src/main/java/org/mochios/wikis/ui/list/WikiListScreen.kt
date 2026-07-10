// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.list

import android.content.ClipData
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.wikis.R
import org.mochios.wikis.model.DirectoryEntry
import org.mochios.wikis.model.Recommendation
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.ui.components.WikisSidebar
import org.mochios.wikis.ui.dialog.CreateWikiDialog
import org.mochios.android.R as MochiR

/**
 * Wikis app landing list — the surface the launcher icon opens. Mirrors
 * `apps/wikis/web/src/routes/_authenticated/index.tsx` `WikisListPage`:
 *
 *  - Top bar: hamburger opens the [WikisSidebar] drawer, RSS-feed overflow
 *    submenu copies a class-level RSS URL to the clipboard.
 *  - Card grid of owned + subscribed wikis, sorted naturally by name. Each
 *    card uses the [Icons.Default.Book] icon for owned wikis and
 *    [Icons.Default.Link] for subscribed wikis (the latter has a non-null
 *    `source`). Subscribed cards have a per-row overflow with a single
 *    Unsubscribe item that opens a [ConfirmDialog].
 *  - Empty state when the user has no wikis — a hint, a Create wiki button
 *    (opens [CreateWikiDialog]), an inline debounced directory search, and a
 *    "Recommended wikis" rail. Search results and recommendations both
 *    expose a Subscribe button; the ViewModel handles the 502
 *    retry-without-server-hint fallback.
 *  - Pull-to-refresh re-fetches both info and recommendations.
 *  - Snackbar host shows toast events from the ViewModel (subscribe /
 *    unsubscribe failures, RSS-copy confirmation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiListScreen(
    navController: NavController,
    viewModel: WikiListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var showOverflow by remember { mutableStateOf(false) }
    var rssSubmenuOpen by remember { mutableStateOf(false) }

    val rssCopiedMessage = stringResource(R.string.wikis_rss_copied)
    val rssFailedMessage = stringResource(R.string.wikis_rss_failed)
    val clipboardLabel = stringResource(R.string.wikis_clipboard_label_rss)

    // Side-effect events from the ViewModel: toast strings + open-wiki
    // navigation. Kept here so the NavController stays out of ViewModel scope.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WikiListEvent.Toast -> snackbarHostState.showSnackbar(event.message)
                is WikiListEvent.OpenWiki -> {
                    navController.navigate(WikisApp.wikiHome(event.wikiId))
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            WikisSidebar(
                currentRoute = WikisApp.HOME,
                onNavigate = { route ->
                    drawerScope.launch { drawerState.close() }
                    if (route != WikisApp.HOME) {
                        navController.navigate(route)
                    }
                },
                onCreateWiki = {
                    drawerScope.launch { drawerState.close() }
                    viewModel.openCreateDialog()
                },
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.wikis_title)) },
                    navigationIcon = {
                        IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.wikis_open_sidebar),
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(
                                    Icons.Default.MoreHoriz,
                                    contentDescription = stringResource(MochiR.string.common_more_options),
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = {
                                    showOverflow = false
                                    rssSubmenuOpen = false
                                },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.wikis_rss_menu)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.RssFeed, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        Text(stringResource(R.string.wikis_rss_menu_trailing))
                                    },
                                    onClick = { rssSubmenuOpen = !rssSubmenuOpen },
                                )
                                if (rssSubmenuOpen) {
                                    RssSubmenu(
                                        onSelect = { mode ->
                                            showOverflow = false
                                            rssSubmenuOpen = false
                                            drawerScope.launch {
                                                val result = viewModel.makeRssUrl(mode)
                                                result.fold(
                                                    onSuccess = { url ->
                                                        clipboard.setClip(
                                                            ClipData.newPlainText(clipboardLabel, url)
                                                                .toClipEntry(),
                                                        )
                                                        snackbarHostState.showSnackbar(rssCopiedMessage)
                                                    },
                                                    onFailure = {
                                                        snackbarHostState.showSnackbar(rssFailedMessage)
                                                    },
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when {
                    uiState.isLoading && uiState.wikis.isEmpty() -> LoadingState()
                    uiState.error != null && uiState.wikis.isEmpty() -> {
                        ErrorState(
                            message = uiState.error?.userMessage()
                                ?: stringResource(MochiR.string.error_unexpected),
                            onRetry = { viewModel.loadInfo() },
                        )
                    }
                    uiState.wikis.isEmpty() -> {
                        EmptyWikis(
                            state = uiState,
                            subscribedIds = viewModel.subscribedWikiIds(),
                            onQueryChange = viewModel::setSearchQuery,
                            onSubscribeEntry = viewModel::subscribeFromSearch,
                            onSubscribeRecommendation = viewModel::subscribeFromRecommendation,
                            onCreate = { viewModel.openCreateDialog() },
                        )
                    }
                    else -> {
                        WikiCardGrid(
                            wikis = uiState.wikis,
                            unsubscribingId = uiState.unsubscribingId,
                            onOpen = { wiki ->
                                navController.navigate(WikisApp.wikiHome(wiki.fingerprint ?: wiki.id))
                            },
                            onUnsubscribe = viewModel::requestUnsubscribe,
                        )
                    }
                }
            }
        }
    }

    val candidate = uiState.unsubscribeCandidate
    if (candidate != null) {
        ConfirmDialog(
            title = stringResource(R.string.wikis_unsubscribe_confirm_title),
            message = stringResource(R.string.wikis_unsubscribe_confirm_message, candidate.name),
            confirmLabel = stringResource(R.string.wikis_unsubscribe_action),
            isDestructive = true,
            onConfirm = { viewModel.confirmUnsubscribe() },
            onDismiss = { viewModel.cancelUnsubscribe() },
        )
    }

    // Create-wiki dialog. Pure-input; onSubmit calls the ViewModel, which talks
    // to the repository and emits OpenWiki to navigate to the new wiki on
    // success. Stays open showing a spinner while the request is in flight.
    if (uiState.createDialogOpen) {
        CreateWikiDialog(
            open = true,
            onDismiss = { viewModel.closeCreateDialog() },
            onSubmit = { name, privacy -> viewModel.createWiki(name, privacy) },
            isPending = uiState.createPending,
        )
    }
}

@Composable
private fun RssSubmenu(onSelect: (String) -> Unit) {
    Column(modifier = Modifier.padding(start = 16.dp)) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.wikis_rss_changes)) },
            onClick = { onSelect("changes") },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.wikis_rss_comments)) },
            onClick = { onSelect("comments") },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.wikis_rss_both)) },
            onClick = { onSelect("all") },
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(MochiR.string.common_retry))
            }
        }
    }
}

@Composable
private fun WikiCardGrid(
    wikis: List<WikiInfo>,
    unsubscribingId: String?,
    onOpen: (WikiInfo) -> Unit,
    onUnsubscribe: (WikiInfo) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(wikis, key = { it.id.ifEmpty { it.fingerprint ?: it.name } }) { wiki ->
            WikiCard(
                wiki = wiki,
                isUnsubscribing = unsubscribingId == wiki.id,
                onOpen = { onOpen(wiki) },
                onUnsubscribe = { onUnsubscribe(wiki) },
            )
        }
    }
}

@Composable
private fun WikiCard(
    wiki: WikiInfo,
    isUnsubscribing: Boolean,
    onOpen: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    val isSubscribed = wiki.source != null
    val icon: ImageVector = if (isSubscribed) Icons.Default.Link else Icons.Default.Book
    val badge = if (isSubscribed) {
        stringResource(R.string.wikis_subscribed_badge)
    } else {
        stringResource(R.string.wikis_owned_badge)
    }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = badge,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = wiki.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = badge,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isSubscribed) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            contentDescription = stringResource(MochiR.string.common_more_options),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        val unsubLabel = if (isUnsubscribing) {
                            stringResource(R.string.wikis_unsubscribing)
                        } else {
                            stringResource(R.string.wikis_unsubscribe_action)
                        }
                        DropdownMenuItem(
                            text = { Text(unsubLabel) },
                            enabled = !isUnsubscribing,
                            onClick = {
                                showMenu = false
                                onUnsubscribe()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyWikis(
    state: WikiListUiState,
    subscribedIds: Set<String>,
    onQueryChange: (String) -> Unit,
    onSubscribeEntry: (DirectoryEntry) -> Unit,
    onSubscribeRecommendation: (Recommendation) -> Unit,
    onCreate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("header") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.wikis_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.wikis_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item("search") {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.wikis_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item("create") {
            OutlinedButton(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.wikis_create_button))
            }
        }

        if (state.searchLoading) {
            item("search-loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        if (state.searchError != null) {
            item("search-error") {
                Text(
                    text = state.searchError.userMessage(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val filteredSearch = state.searchResults.filter { entry ->
            entry.id !in subscribedIds && entry.fingerprint !in subscribedIds
        }
        if (filteredSearch.isNotEmpty()) {
            items(filteredSearch, key = { "search-${it.id}" }) { entry ->
                SubscribableRow(
                    name = entry.name,
                    subtitle = null,
                    isSubscribing = state.subscribingId == entry.id,
                    onSubscribe = { onSubscribeEntry(entry) },
                )
            }
        }

        val filteredRecs = state.recommendations.filter { rec ->
            rec.id !in subscribedIds && rec.fingerprint !in subscribedIds
        }
        if (filteredRecs.isNotEmpty()) {
            item("rec-divider") {
                HorizontalDivider()
            }
            item("rec-header") {
                Text(
                    text = stringResource(R.string.wikis_recommended_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(filteredRecs, key = { "rec-${it.id}" }) { rec ->
                SubscribableRow(
                    name = rec.name,
                    subtitle = rec.blurb.ifBlank { null },
                    isSubscribing = state.subscribingId == rec.id,
                    onSubscribe = { onSubscribeRecommendation(rec) },
                )
            }
        }
    }
}

@Composable
private fun SubscribableRow(
    name: String,
    subtitle: String?,
    isSubscribing: Boolean,
    onSubscribe: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Book,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Button(
                onClick = onSubscribe,
                enabled = !isSubscribing,
            ) {
                if (isSubscribing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.wikis_subscribe_button))
                }
            }
        }
    }
}
