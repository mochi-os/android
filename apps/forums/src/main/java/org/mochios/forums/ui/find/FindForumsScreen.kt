// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.find

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.forums.R
import org.mochios.android.R as MochiR

/**
 * Forum discovery: search the directory as the user types, paste a URL to probe
 * a remote forum, or pick from the recommendations. Subscribing opens the forum
 * once the server confirms it, so the user lands where they just joined.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindForumsScreen(
    onBack: () -> Unit,
    onForumSubscribed: (String) -> Unit,
    viewModel: FindForumsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // The search field keeps the keyboard up, which covers the snackbar the
    // subscribe result lands in — drop it before the request goes out.
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(viewModel) {
        viewModel.navigateToForum.collect { forumId -> onForumSubscribed(forumId) }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error.userMessage())
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.forums_find_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = uiState.searchQuery,
                        onQueryChange = { query -> viewModel.updateSearchQuery(query) },
                        onSearch = { },
                        expanded = false,
                        onExpandedChange = { },
                        placeholder = {
                            Text(stringResource(R.string.forums_find_search_placeholder))
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (uiState.isSearching || uiState.isProbing) {
                            {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            null
                        }
                    )
                },
                expanded = false,
                onExpandedChange = { },
                // The Scaffold already offsets content below the status bar and
                // top app bar; the SearchBar's default insets would add a second
                // status-bar-height gap on top of that.
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { }

            Spacer(Modifier.height(8.dp))

            val probe = uiState.probeResult
            val sourceForums = when {
                probe != null -> listOf(probe)
                uiState.searchQuery.isNotBlank() -> uiState.results
                else -> uiState.recommended
            }
            // A forum the user already has belongs in their own list, not in the
            // directory, so it drops out of whichever section would have carried
            // it rather than sitting there behind a dead "Subscribed" chip.
            // Filtering the composed list — not each source — means the empty
            // branches below test what actually renders, so a search whose every
            // hit is already subscribed reads as no results, not a blank list.
            val displayForums = sourceForums.filter { item ->
                item.key !in uiState.subscribed
            }
            val sectionTitle = when {
                probe != null -> stringResource(R.string.forums_find_found_by_url)
                uiState.searchQuery.isNotBlank() -> stringResource(R.string.forums_find_results)
                else -> stringResource(R.string.forums_find_recommended)
            }

            if (displayForums.isNotEmpty() || uiState.isLoading) {
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            when {
                uiState.isLoading && uiState.searchQuery.isBlank() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                displayForums.isEmpty() && uiState.searchQuery.isNotBlank() &&
                    !uiState.isSearching && !uiState.isProbing -> {
                    EmptyMessage(stringResource(R.string.forums_find_no_results))
                }
                displayForums.isEmpty() && uiState.searchQuery.isBlank() -> {
                    EmptyMessage(stringResource(R.string.forums_find_search_hint))
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayForums, key = { item -> item.key }) { item ->
                            ForumDiscoveryCard(
                                item = item,
                                isSubscribing = uiState.subscribingKey == item.key,
                                onSubscribe = {
                                    keyboardController?.hide()
                                    viewModel.subscribe(item)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A discovery row: forum avatar, name over its fingerprint (or blurb), and a
 * Subscribe button. The screen only ever hands this forums the user has not
 * subscribed to, so there is no subscribed state to draw.
 */
@Composable
private fun ForumDiscoveryCard(
    item: ForumDirectoryItem,
    isSubscribing: Boolean,
    onSubscribe: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Forum,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // The blurb is the friendlier subtitle, with the fingerprint below it
            // rather than instead of it — a plain search hit has only the latter.
            if (item.blurb.isNotBlank()) {
                Text(
                    text = item.blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = onSubscribe, enabled = !isSubscribing) {
            if (isSubscribing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.forums_find_subscribe))
            }
        }
    }
}
