// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.find

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.projects.R
import org.mochios.projects.model.Project
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindProjectsScreen(
    onBack: () -> Unit,
    onProjectSubscribed: (String) -> Unit,
    viewModel: FindProjectsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // The search field keeps the keyboard up, which covers the snackbar the
    // subscribe result lands in — drop it before the request goes out.
    val keyboardController = LocalSoftwareKeyboardController.current

    // Errors are transient feedback, not a screen state: a failed subscribe must
    // leave the results list on screen and tappable rather than replacing it.
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
                title = { Text(stringResource(R.string.projects_find_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(MochiR.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text(stringResource(R.string.projects_find_search_placeholder)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
            // A project the user already has belongs in their own list, not in
            // the directory, so it drops out of both sections rather than
            // sitting there behind a dead "Subscribed" chip. Filtering here
            // rather than in the view model keeps the branches below testing the
            // same lists they render, so a search whose every hit is already
            // subscribed lands on the empty state instead of an empty LazyColumn.
            val searchResults = uiState.searchResults.filter { project ->
                project.id.ifEmpty { project.fingerprint } !in uiState.subscribedIds
            }
            val recommendations = uiState.recommendations.filter { project ->
                project.id.ifEmpty { project.fingerprint } !in uiState.subscribedIds
            }
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                searchResults.isNotEmpty() -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults, key = { it.fingerprint.ifEmpty { it.id } }) { project ->
                            val subscribeId = project.id.ifEmpty { project.fingerprint }
                            DiscoveredProjectCard(
                                project = project,
                                isSubscribing = uiState.subscribingId == subscribeId,
                                onSubscribe = {
                                    keyboardController?.hide()
                                    viewModel.subscribe(project) { landingId ->
                                        onProjectSubscribed(landingId)
                                    }
                                }
                            )
                        }
                    }
                }

                // A live search owns the screen. Without the blank-query guard,
                // a search whose every hit is already subscribed would fall
                // through to the recommendations and look like the search had
                // never run; the empty state below is the honest answer.
                recommendations.isNotEmpty() && uiState.searchQuery.isBlank() -> {
                    Column {
                        Text(
                            text = stringResource(R.string.projects_find_recommended),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(recommendations, key = { it.fingerprint.ifEmpty { it.id } }) { project ->
                                val subscribeId = project.id.ifEmpty { project.fingerprint }
                                DiscoveredProjectCard(
                                    project = project,
                                    isSubscribing = uiState.subscribingId == subscribeId,
                                    onSubscribe = {
                                        keyboardController?.hide()
                                        viewModel.subscribe(project) { landingId ->
                                            onProjectSubscribed(landingId)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.projects_find_search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            }
        }
    }
}

/**
 * A directory hit, with a subscribe button. The screen only ever hands this
 * projects the user has not subscribed to, so there is no subscribed state to
 * draw.
 */
@Composable
private fun DiscoveredProjectCard(
    project: Project,
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
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (project.description.isNotBlank()) {
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Owner and home server, as the card showed them before the restyle.
            // The separator only joins values that are there, so a bare hit never
            // renders a dangling middot. `location` is the host a directory entry
            // carries when it has no explicit `server`.
            val meta = listOfNotNull(
                project.ownername.takeIf { owner -> owner.isNotBlank() },
                (project.server ?: project.location)?.takeIf { host -> host.isNotBlank() }
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Fingerprint under the subtitle, as the feeds directory shows it —
            // the only stable handle for two hits with the same name.
            val fingerprint = project.fingerprintHyphens.ifEmpty { project.fingerprint }
            if (fingerprint.isNotEmpty()) {
                Text(
                    text = fingerprint,
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
                Text(stringResource(MochiR.string.common_subscribe))
            }
        }
    }
}
