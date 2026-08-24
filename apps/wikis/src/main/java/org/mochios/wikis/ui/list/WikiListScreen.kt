// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.list

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.EntityIconCircle
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.wikis.R
import org.mochios.wikis.model.DirectoryEntry
import org.mochios.wikis.model.Recommendation
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.ui.components.WikiDrawer
import org.mochios.android.R as MochiR

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
    var showAbout by remember { mutableStateOf(false) }
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

    WikiDrawer(
        drawerState = drawerState,
        wikis = uiState.wikis,
        // This screen *is* the "All wikis" view, so the pinned row stays the
        // selected one and tapping a wiki navigates away.
        selectedId = WikisApp.HOME,
        onSelectWiki = { wikiId -> navController.navigate(WikisApp.wikiHome(wikiId)) },
        onSelectAll = { /* already here */ },
        onFind = { navController.navigate(WikisApp.FIND) },
        onCreate = { navController.navigate(WikisApp.CREATE) },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.wikis_title)) },
                    navigationIcon = {
                        MochiIconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.wikis_open_sidebar),
                            )
                        }
                    },
                    actions = {
                        Box {
                            MochiIconButton(onClick = { showOverflow = true }) {
                                Icon(
                                    Icons.Default.MoreHoriz,
                                    contentDescription = stringResource(MochiR.string.common_more_options),
                                )
                            }
                            MochiDropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = {
                                    showOverflow = false
                                    rssSubmenuOpen = false
                                },
                            ) {
                                MochiDropdownMenuItem(
                                    text = { Text(stringResource(R.string.wikis_rss_menu)) },
                                    onClick = { rssSubmenuOpen = !rssSubmenuOpen },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.RssFeed, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        Text(stringResource(R.string.wikis_rss_menu_trailing))
                                    },
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
                                MochiDropdownMenuItem(
                                    text = { Text(stringResource(MochiR.string.about_label)) },
                                    onClick = {
                                        showOverflow = false
                                        showAbout = true
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                )
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
                            onCreate = { navController.navigate(WikisApp.CREATE) },
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
        MochiAlertDialog(
            onDismissRequest = { viewModel.cancelUnsubscribe() },
            title = stringResource(R.string.wikis_unsubscribe_confirm_title),
            text = stringResource(R.string.wikis_unsubscribe_confirm_message, candidate.name),
            confirmText = stringResource(R.string.wikis_unsubscribe_action),
            onConfirm = { viewModel.confirmUnsubscribe() },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@Composable
private fun RssSubmenu(onSelect: (String) -> Unit) {
    Column(modifier = Modifier.padding(start = 16.dp)) {
        MochiDropdownMenuItem(
            text = { Text(stringResource(R.string.wikis_rss_changes)) },
            onClick = { onSelect("changes") },
        )
        MochiDropdownMenuItem(
            text = { Text(stringResource(R.string.wikis_rss_comments)) },
            onClick = { onSelect("comments") },
        )
        MochiDropdownMenuItem(
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
            MochiTextButton(onClick = onRetry) {
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WikiCard(
    wiki: WikiInfo,
    isUnsubscribing: Boolean,
    onOpen: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    val isSubscribed = wiki.source != null
    val badge = if (isSubscribed) {
        stringResource(R.string.wikis_subscribed_badge)
    } else {
        stringResource(R.string.wikis_owned_badge)
    }
    val wikiId = wiki.fingerprint ?: wiki.id
    var showMenu by remember { mutableStateOf(false) }

    MochiCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                // Only a subscribed wiki has anything in the menu, so an owned
                // one's long press does nothing rather than opening an empty one.
                onLongClick = { if (isSubscribed) showMenu = true },
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EntityIconCircle(
                seed = wikiId.ifEmpty { wiki.name },
                icon = Icons.Default.Book,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = wiki.name,
                    style = MaterialTheme.typography.titleMedium,
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
                    MochiIconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            contentDescription = stringResource(MochiR.string.common_more_options),
                        )
                    }
                    MochiDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        val unsubLabel = if (isUnsubscribing) {
                            stringResource(R.string.wikis_unsubscribing)
                        } else {
                            stringResource(R.string.wikis_unsubscribe_action)
                        }
                        MochiDropdownMenuItem(
                            text = { Text(unsubLabel) },
                            onClick = {
                                showMenu = false
                                onUnsubscribe()
                            },
                            enabled = !isUnsubscribing,
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
            MochiTextField(
                value = state.searchQuery,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.wikis_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item("create") {
            MochiOutlinedButton(
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
                    isSubscribing = state.subscribingId == entry.id.ifEmpty { entry.fingerprint },
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
                    isSubscribing = state.subscribingId == rec.id.ifEmpty { rec.fingerprint },
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
    MochiCard(
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
            MochiButton(
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
