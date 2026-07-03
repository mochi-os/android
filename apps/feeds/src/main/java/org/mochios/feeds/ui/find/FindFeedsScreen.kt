// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.find

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.feeds.R
import org.mochios.feeds.model.Feed
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindFeedsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFeed: (String) -> Unit,
    viewModel: FindFeedsViewModel = hiltViewModel()
) {
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it.userMessage())
            viewModel.clearError()
        }
    }

    // After a successful subscribe, open the new feed (its screen reloads and
    // shows the interest-suggestions prompt).
    LaunchedEffect(Unit) {
        viewModel.navigateToFeed.collect { feedId ->
            onNavigateToFeed(feedId)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feeds_find_feeds)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(MochiR.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        FindFeedsContent(
            viewModel = viewModel,
            onNavigateToFeed = onNavigateToFeed,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

// Shared body of the Find Feeds experience. Used by FindFeedsScreen and by the
// FeedListScreen onboarding empty state so first-run users can search and
// subscribe without an extra navigation step.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindFeedsContent(
    viewModel: FindFeedsViewModel,
    onNavigateToFeed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isLoadingRecommendations by viewModel.isLoadingRecommendations.collectAsState()
    val subscribingFeed by viewModel.subscribingFeed.collectAsState()
    val subscribedFeeds by viewModel.subscribedFeeds.collectAsState()
    val probeResult by viewModel.probeResult.collectAsState()
    val isProbing by viewModel.isProbing.collectAsState()

    Column(
        modifier = modifier
    ) {
        // Search bar
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onSearch = { },
                    expanded = false,
                    onExpandedChange = { },
                    placeholder = { Text(stringResource(R.string.feeds_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (isSearching || isProbing) {
                        { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                    } else null
                )
            },
            expanded = false,
            onExpandedChange = { },
            // The hosting Scaffold already offsets content below the status bar
            // and top app bar; the SearchBar's default insets would add a second
            // status-bar-height gap on top of that.
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) { }

        Spacer(modifier = Modifier.height(8.dp))

        val probe = probeResult
        val displayFeeds = if (probe != null) {
            listOf(probe)
        } else if (searchQuery.isNotBlank()) {
            searchResults
        } else {
            recommendations
        }
        val sectionTitle = when {
            probe != null -> stringResource(R.string.feeds_url_result)
            searchQuery.isNotBlank() -> stringResource(R.string.feeds_search_results)
            else -> stringResource(MochiR.string.discovery_recommended)
        }

        if (displayFeeds.isNotEmpty() || isLoadingRecommendations) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (isLoadingRecommendations && searchQuery.isBlank()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (displayFeeds.isEmpty() && searchQuery.isNotBlank() && !isSearching && !isProbing) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.feeds_no_feeds_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayFeeds, key = { it.fingerprint.ifEmpty { it.id } }) { feed ->
                    val feedId = feed.fingerprint.ifEmpty { feed.id }
                    val isSubscribed = feedId in subscribedFeeds
                    val isSubscribing = subscribingFeed == feedId

                    FeedDiscoveryCard(
                        feed = feed,
                        isSubscribed = isSubscribed,
                        isSubscribing = isSubscribing,
                        onSubscribe = { viewModel.subscribe(feed) },
                        onClick = {
                            if (isSubscribed) {
                                onNavigateToFeed(feedId)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedDiscoveryCard(
    feed: Feed,
    isSubscribed: Boolean,
    isSubscribing: Boolean,
    onSubscribe: () -> Unit,
    onClick: () -> Unit
) {
    val rssOrange = Color(0xFFF26B21)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(rssOrange.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.RssFeed,
                contentDescription = null,
                tint = rssOrange,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feed.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = feed.fingerprintHyphens.ifEmpty { feed.fingerprint }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (isSubscribed) {
            FilledTonalButton(onClick = {}, enabled = false) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(MochiR.string.discovery_subscribed))
            }
        } else {
            Button(
                onClick = onSubscribe,
                enabled = !isSubscribing
            ) {
                if (isSubscribing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(MochiR.string.common_subscribe))
                }
            }
        }
    }
}
