// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.find

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
 *  - Wikis the user already has stay in both lists, showing a disabled
 *    "Subscribed" chip instead of the button; tapping such a row opens the
 *    wiki. This matches the other directories (feeds, forums, projects, CRM) —
 *    hiding a wiki you searched for by name reads as "not found".
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
    // The search field keeps the keyboard up, which covers the snackbar the
    // subscribe result lands in — drop it before the request goes out.
    val keyboardController = LocalSoftwareKeyboardController.current

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

    // Directory-search failures live in the state rather than the event channel,
    // so they need their own trip to the snackbar — otherwise a failed search
    // just shows an empty list with no explanation.
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

            when {
                // Empty query + still loading recommendations
                showRecommendations && uiState.isLoadingRecommendations &&
                    uiState.recommendations.isEmpty() -> {
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
                            if (uiState.results.isEmpty() && !uiState.isSearching) {
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
                            items(uiState.results, key = { it.id }) { entry ->
                                val target = entry.id.ifEmpty { entry.fingerprint }
                                DirectoryEntryRow(
                                    entry = entry,
                                    isSubscribed = target in uiState.subscribedIds,
                                    isPending = uiState.pendingId == target,
                                    onSubscribe = {
                                        keyboardController?.hide()
                                        viewModel.subscribeDirectoryEntry(entry)
                                    },
                                    onOpen = { onSubscribed(entry.fingerprint.ifEmpty { entry.id }) },
                                )
                            }
                        }

                        // Recommendations
                        if (uiState.recommendations.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.wikis_find_recommended_section),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                )
                            }
                            items(uiState.recommendations, key = { it.id }) { rec ->
                                val target = rec.id.ifEmpty { rec.fingerprint }
                                RecommendationRow(
                                    rec = rec,
                                    isSubscribed = target in uiState.subscribedIds,
                                    isPending = uiState.pendingId == target,
                                    onSubscribe = {
                                        keyboardController?.hide()
                                        viewModel.subscribeRecommendation(rec)
                                    },
                                    onOpen = { onSubscribed(rec.fingerprint.ifEmpty { rec.id }) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A directory hit, with a subscribe button that becomes a disabled "Subscribed"
 * chip once the user has the wiki. Tapping a subscribed row opens it.
 */
@Composable
private fun DirectoryEntryRow(
    entry: DirectoryEntry,
    isSubscribed: Boolean,
    isPending: Boolean,
    onSubscribe: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isSubscribed, onClick = onOpen),
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
                        text = entry.fingerprintHyphens
                            .ifEmpty { formatFingerprint(entry.fingerprint) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            SubscribeControl(
                isSubscribed = isSubscribed,
                isPending = isPending,
                onSubscribe = onSubscribe,
            )
        }
    }
}

/**
 * A recommended wiki, with the same subscribe/subscribed control as a search
 * hit. Tapping a subscribed row opens it.
 */
@Composable
private fun RecommendationRow(
    rec: Recommendation,
    isSubscribed: Boolean,
    isPending: Boolean,
    onSubscribe: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isSubscribed, onClick = onOpen),
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
                if (rec.fingerprint.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = rec.fingerprintHyphens
                            .ifEmpty { formatFingerprint(rec.fingerprint) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            SubscribeControl(
                isSubscribed = isSubscribed,
                isPending = isPending,
                onSubscribe = onSubscribe,
            )
        }
    }
}

/** The trailing control both rows share: Subscribe, spinner, or Subscribed. */
@Composable
private fun SubscribeControl(
    isSubscribed: Boolean,
    isPending: Boolean,
    onSubscribe: () -> Unit,
) {
    if (isSubscribed) {
        FilledTonalButton(onClick = {}, enabled = false) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(MochiR.string.discovery_subscribed))
        }
    } else {
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

/** Group a fingerprint into dash-separated triplets — matches the web style. */
private fun formatFingerprint(fp: String): String =
    fp.chunked(3).joinToString("-")
