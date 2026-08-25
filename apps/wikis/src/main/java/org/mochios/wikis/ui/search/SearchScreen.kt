// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.ErrorState
import org.mochios.wikis.R
import org.mochios.wikis.ui.components.WikiSearchTopBar
import org.mochios.wikis.model.SearchResult
import org.mochios.wikis.navigation.WikisApp
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            WikiSearchTopBar(
                query = state.query,
                placeholder = stringResource(R.string.wikis_search_pages_placeholder),
                onQueryChange = viewModel::updateQuery,
                onClose = { navController.popBackStack() },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.debouncedQuery.isBlank() -> {
                        EmptyHint(
                            text = stringResource(R.string.wikis_search_empty_query),
                        )
                    }
                    state.isLoading && state.results.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.error != null -> {
                        ErrorState(
                            error = state.error!!,
                            onRetry = viewModel::retry,
                        )
                    }
                    state.results.isEmpty() -> {
                        EmptyHint(
                            text = stringResource(
                                R.string.wikis_search_no_results,
                                state.debouncedQuery,
                            ),
                        )
                    }
                    else -> {
                        ResultsList(
                            query = state.debouncedQuery,
                            results = state.results,
                            onOpen = { page ->
                                navController.navigate(
                                    WikisApp.pageView(viewModel.wikiId, page)
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsList(
    query: String,
    results: List<SearchResult>,
    onOpen: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            val count = results.size
            Text(
                text = pluralStringResource(
                    R.plurals.wikis_search_results_count,
                    count,
                    count,
                    query,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(results, key = { it.page }) { result ->
            ResultRow(
                result = result,
                onClick = { onOpen(result.page) },
            )
        }
    }
}

@Composable
private fun ResultRow(
    result: SearchResult,
    onClick: () -> Unit,
) {
    val format = LocalFormat.current
    val updatedLabel = format.formatTimestamp(result.updated)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Default.FileCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = result.title.ifBlank { result.page },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (result.excerpt.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${result.excerpt}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.wikis_search_updated, updatedLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp, start = 24.dp, end = 24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
