// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.history

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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
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
import org.mochios.wikis.model.Revision
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.ui.components.AuthorAvatar
import org.mochios.wikis.ui.components.AvatarSize
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.WikiContextValue
import org.mochios.android.R as MochiR

/**
 * History surface for a wiki page. Mirrors web's
 * `apps/wikis/web/src/features/wiki/page-history.tsx`: an `EmptyState`-friendly
 * table of revisions where each row carries view + revert affordances and
 * links straight to the per-revision viewer.
 *
 * Reads `wikiId` / `page` via the ViewModel's [SavedStateHandle] and is wired
 * by `WikisApp.PAGE_HISTORY` in the nav graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageHistoryScreen(
    navController: NavController,
    viewModel: PageHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val wikiInfo = state.wiki

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wikis_history_title)) },
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
                state.isLoading && state.revisions.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && state.revisions.isEmpty() -> {
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
                        PageHistoryBody(
                            slug = viewModel.slug,
                            wikiId = viewModel.wikiId,
                            revisions = state.revisions,
                            currentVersion = state.currentVersion,
                            hasMore = state.hasMore,
                            isLoadingMore = state.isLoadingMore,
                            onLoadMore = { viewModel.loadMore() },
                            onView = { version ->
                                navController.navigate(
                                    WikisApp.pageRevision(viewModel.wikiId, viewModel.slug, version)
                                )
                            },
                            onRevert = { version ->
                                navController.navigate(
                                    WikisApp.pageRevert(viewModel.wikiId, viewModel.slug, version)
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
private fun PageHistoryBody(
    slug: String,
    wikiId: String,
    revisions: List<Revision>,
    currentVersion: Int,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onView: (Int) -> Unit,
    onRevert: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Subtitle: "Viewing history for <slug>, current version N"
        Text(
            text = stringResource(
                R.string.wikis_history_viewing,
                slug,
                currentVersion,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        HorizontalDivider()

        if (revisions.isEmpty()) {
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
                        text = stringResource(R.string.wikis_history_empty),
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
                items(revisions, key = { it.id.ifEmpty { it.version.toString() } }) { revision ->
                    RevisionRow(
                        revision = revision,
                        isCurrent = revision.version == currentVersion,
                        onView = { onView(revision.version) },
                        onRevert = { onRevert(revision.version) },
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
private fun RevisionRow(
    revision: Revision,
    isCurrent: Boolean,
    onView: () -> Unit,
    onRevert: () -> Unit,
) {
    val format = LocalFormat.current
    val createdLabel = format.formatTimestamp(revision.created)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onView)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Version (monospace, tap to view)
        Text(
            text = "v${revision.version}",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(56.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
        ) {
            // Title (tap to view)
            Text(
                text = revision.title.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))

            // Author + date
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AuthorAvatar(
                    revisionId = revision.id,
                    authorFingerprint = revision.author,
                    authorName = revision.name,
                    size = AvatarSize.XS,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = revision.name.ifBlank { revision.author.take(8) },
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

            // Comment row (or em-dash placeholder, matching web's "-" cell)
            val comment = revision.comment.ifBlank { "-" }
            Spacer(Modifier.height(2.dp))
            Text(
                text = comment,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Actions: view + revert (hide revert for the current version)
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            IconButton(onClick = onView) {
                Icon(
                    Icons.Default.Visibility,
                    contentDescription = stringResource(R.string.wikis_history_view_action),
                )
            }
            if (!isCurrent) {
                IconButton(onClick = onRevert) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = stringResource(R.string.wikis_history_revert_action),
                    )
                }
            }
        }
    }
}
