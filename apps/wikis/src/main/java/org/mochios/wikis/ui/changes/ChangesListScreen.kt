// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.changes

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.LoadMoreButton
import org.mochios.wikis.R
import org.mochios.wikis.model.Change
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.ui.components.AuthorAvatar
import org.mochios.wikis.ui.components.AvatarSize
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.WikiContextValue
import org.mochios.android.R as MochiR

/**
 * Recent-changes surface for a wiki. Mirrors web's
 * `apps/wikis/web/src/features/wiki/changes-list.tsx`: a flat list of edits
 * across every page in the wiki, newest first, with author avatars and tap-
 * to-open-page rows.
 *
 * Reads `wikiId` via the ViewModel's [SavedStateHandle] and is wired by
 * `WikisApp.CHANGES` in the nav graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesListScreen(
    navController: NavController,
    viewModel: ChangesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val wikiInfo = state.wiki

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wikis_changes_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && state.changes.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && state.changes.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.error!!.userMessage(),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                wikiInfo == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    val wikiCtx = WikiContextValue(
                        wikiId = viewModel.wikiId,
                        info = wikiInfo,
                        permissions = state.permissions,
                        serverUrl = viewModel.serverUrl,
                    )
                    CompositionLocalProvider(LocalWikiContext provides wikiCtx) {
                        ChangesBody(
                            changes = state.changes,
                            hasMore = state.hasMore,
                            isLoadingMore = state.isLoadingMore,
                            onLoadMore = { viewModel.loadMore() },
                            onOpenPage = { slug ->
                                navController.navigate(
                                    WikisApp.pageView(viewModel.wikiId, slug)
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
private fun ChangesBody(
    changes: List<Change>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenPage: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Subtitle
        Text(
            text = stringResource(R.string.wikis_changes_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        HorizontalDivider()

        if (changes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.wikis_changes_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(changes, key = { it.id.ifEmpty { it.slug + "/" + it.version } }) { change ->
                    ChangeRow(
                        change = change,
                        onClick = { onOpenPage(change.slug) },
                    )
                    HorizontalDivider()
                }
                if (hasMore) {
                    item(key = "load-more") {
                        LoadMoreButton(
                            label = stringResource(R.string.wikis_load_more),
                            isLoading = isLoadingMore,
                            onClick = onLoadMore,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(
    change: Change,
    onClick: () -> Unit,
) {
    val format = LocalFormat.current
    val createdLabel = format.formatTimestamp(change.created)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Page title (tap to open)
            Text(
                text = change.title.ifBlank { change.slug },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(4.dp))

            // Version + author + date row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "v${change.version}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                AuthorAvatar(
                    revisionId = change.id,
                    authorFingerprint = change.author,
                    authorName = change.name,
                    size = AvatarSize.XS,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = change.name.ifBlank { change.author.take(8) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = createdLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Comment (or em-dash placeholder)
            val comment = change.comment.ifBlank { "-" }
            Spacer(Modifier.height(2.dp))
            Text(
                text = comment,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
