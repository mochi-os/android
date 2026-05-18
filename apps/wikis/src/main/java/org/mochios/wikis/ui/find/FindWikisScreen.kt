package org.mochios.wikis.ui.find

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.wikis.R
import org.mochios.wikis.model.DirectoryEntry
import org.mochios.wikis.model.Recommendation
import org.mochios.android.R as MochiR

/**
 * Find-and-subscribe surface for wikis. Mirrors web's `FindWikisPage`
 * (`apps/wikis/web/src/routes/_authenticated/find.tsx`):
 *
 *  - Debounced directory search field at the top.
 *  - Search results below, each row offering "Subscribe".
 *  - Below results (or as the whole body when the query is empty), a
 *    "Recommended wikis" section fed from `/-/recommendations`.
 *  - The user's already-subscribed wikis are filtered out of both lists.
 *
 * Subscribe handles the 502 retry-without-server case in the ViewModel; on
 * success the screen navigates to the new wiki's home via [onSubscribed].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindWikisScreen(
    onBack: () -> Unit,
    onSubscribed: (wikiId: String) -> Unit,
    viewModel: FindWikisViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar feedback messages — collected once before LaunchedEffect so
    // they can be passed into the coroutine, since stringResource() is only
    // valid inside a composable.
    val successMsg = stringResource(R.string.wikis_subscribe_success)
    val retryMsg = stringResource(R.string.wikis_subscribe_502_retry)
    val failedFallback = stringResource(R.string.wikis_subscribe_failed)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FindEvent.SubscribeSuccess -> {
                    snackbarHostState.showSnackbar(successMsg)
                    onSubscribed(event.wikiId)
                }
                is FindEvent.SubscribeRetried -> {
                    snackbarHostState.showSnackbar(retryMsg)
                }
                is FindEvent.SubscribeFailed -> {
                    val msg = event.error.userMessage().ifBlank { failedFallback }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wikis_find_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text(stringResource(R.string.wikis_find_search_placeholder)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (uiState.isSearching) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val showRecommendations = uiState.searchQuery.isBlank()
            val filteredResults = uiState.results.filter { entry ->
                entry.id !in uiState.subscribedIds &&
                    entry.fingerprint !in uiState.subscribedIds
            }
            val filteredRecommendations = uiState.recommendations.filter { rec ->
                rec.id !in uiState.subscribedIds &&
                    rec.fingerprint !in uiState.subscribedIds
            }

            when {
                // Empty query + still loading recommendations
                showRecommendations && uiState.isLoadingRecommendations &&
                    filteredRecommendations.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Search results
                        if (!showRecommendations) {
                            if (filteredResults.isEmpty() && !uiState.isSearching) {
                                item {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.wikis_find_no_results),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            items(filteredResults, key = { it.id }) { entry ->
                                DirectoryEntryRow(
                                    entry = entry,
                                    isPending = uiState.pendingId == entry.id,
                                    onSubscribe = { viewModel.subscribeDirectoryEntry(entry) },
                                )
                            }
                        }

                        // Recommendations
                        if (filteredRecommendations.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.wikis_find_recommended_section),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                )
                            }
                            items(filteredRecommendations, key = { it.id }) { rec ->
                                RecommendationRow(
                                    rec = rec,
                                    isPending = uiState.pendingId == rec.id,
                                    onSubscribe = { viewModel.subscribeRecommendation(rec) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryEntryRow(
    entry: DirectoryEntry,
    isPending: Boolean,
    onSubscribe: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.fingerprint.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatFingerprint(entry.fingerprint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onSubscribe, enabled = !isPending) {
                if (isPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(MochiR.string.common_subscribe))
                }
            }
        }
    }
}

@Composable
private fun RecommendationRow(
    rec: Recommendation,
    isPending: Boolean,
    onSubscribe: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rec.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (rec.blurb.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = rec.blurb,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onSubscribe, enabled = !isPending) {
                if (isPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(MochiR.string.common_subscribe))
                }
            }
        }
    }
}

/** Group a fingerprint into dash-separated triplets — matches the web style. */
private fun formatFingerprint(fp: String): String =
    fp.chunked(3).joinToString("-")
