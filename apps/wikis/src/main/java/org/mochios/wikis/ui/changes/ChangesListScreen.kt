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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.format.personName
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InfiniteList
import org.mochios.android.ui.components.LoadState
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.util.fingerprint
import org.mochios.wikis.R
import org.mochios.wikis.model.Change
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.ui.components.AuthorAvatar
import org.mochios.wikis.ui.components.AvatarSize
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.WikiContextValue
import org.mochios.android.R as MochiR
import androidx.compose.runtime.remember
import org.mochios.android.ui.components.addressByAuthor

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
                    MochiIconButton(onClick = { navController.popBackStack() }) {
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
            val error = state.error
            when {
                // The wiki's information or the first page failed: one retry
                // asks for whichever is missing.
                error != null && (wikiInfo == null || state.changes.isEmpty()) -> {
                    ErrorState(
                        error = error,
                        onRetry = viewModel::load,
                    )
                }
                wikiInfo == null || state.isLoading && state.changes.isEmpty() -> {
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
                            load = state.load,
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
internal fun ChangesBody(
    changes: List<Change>,
    hasMore: Boolean,
    load: LoadState,
    onLoadMore: () -> Unit,
    onOpenPage: (String) -> Unit,
) {
    // One revision per author across the list, the first each has: the
    // avatar route names a revision, so naming each row's own had the image
    // cache fetch and store the same picture once per row.
    val avatars = remember(changes) { addressByAuthor(changes, { it.author }, { it.id }) }
    Column(modifier = Modifier.fillMaxSize()) {
        // Subtitle
        Text(
            text = stringResource(R.string.wikis_changes_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        HorizontalDivider()

        // The next page is asked for as the end of the list comes into view;
        // one that fails stands as the list's last row, with Retry.
        InfiniteList(
            items = changes,
            load = load,
            hasMore = hasMore,
            onLoadMore = onLoadMore,
            contentPadding = PaddingValues(vertical = 4.dp),
            key = { change -> change.id.ifEmpty { change.slug + "/" + change.version } },
            emptyContent = {
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
            },
        ) { change ->
            ChangeRow(
                change = change,
                avatar = avatars[change.author] ?: change.id,
                onClick = { onOpenPage(change.slug) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ChangeRow(
    change: Change,
    // The revision the author's avatar is served under (see ChangesBody).
    avatar: String,
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
                    revisionId = avatar,
                    authorFingerprint = change.author,
                    authorName = change.name,
                    size = AvatarSize.XS,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = personName(change.name, fingerprint(change.author)),
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
