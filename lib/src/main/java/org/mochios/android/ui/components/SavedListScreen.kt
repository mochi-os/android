// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.mochios.android.R

/**
 * Wording for [SavedListScaffold]. Each feature keeps its own translated
 * strings, so the caller resolves them and passes them in rather than the
 * library owning a second copy of every locale.
 *
 * @property title Screen title.
 * @property clearAll Label of the clear-everything action, reused as the
 *   confirmation's confirm button.
 * @property emptyTitle Heading of the empty state.
 * @property emptySubtitle Second line of the empty state.
 * @property clearConfirmTitle Title of the clear confirmation.
 * @property clearConfirmBody Body of the clear confirmation.
 * @property clearError Snackbar shown when clearing fails.
 */
data class SavedListLabels(
    val title: String,
    val clearAll: String,
    val emptyTitle: String,
    val emptySubtitle: String,
    val clearConfirmTitle: String,
    val clearConfirmBody: String,
    val clearError: String
)

/**
 * The saved-posts screen every feature wears the same way: a back arrow, a
 * "clear all" action that appears only when there is something to clear, an
 * empty state, and the saved items as a spaced list of cards. What a card shows
 * is the feature's business, so [card] renders each item.
 *
 * @param items Saved items, in the order they should appear.
 * @param key Stable identity of an item, for the list.
 * @param labels Feature-specific wording.
 * @param clearFailed Emits when a clear attempt failed, raising a snackbar.
 * @param onNavigateBack Called by the back arrow.
 * @param onClearAll Called once clearing everything is confirmed.
 * @param card Renders one saved item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SavedListScaffold(
    items: List<T>,
    key: (T) -> Any,
    labels: SavedListLabels,
    clearFailed: Flow<*>,
    onNavigateBack: () -> Unit,
    onClearAll: () -> Unit,
    card: @Composable (T) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        clearFailed.collect { snackbarHostState.showSnackbar(labels.clearError) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(labels.title) },
                navigationIcon = {
                    MochiIconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        MochiTextButton(onClick = { showClearConfirm = true }) {
                            Text(labels.clearAll)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Filled.Bookmark,
                    title = labels.emptyTitle,
                    subtitle = labels.emptySubtitle,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                    start = 12.dp,
                    end = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { item -> key(item) }) { item ->
                    card(item)
                }
            }
        }
    }

    if (showClearConfirm) {
        MochiAlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = labels.clearConfirmTitle,
            text = labels.clearConfirmBody,
            confirmText = labels.clearAll,
            onConfirm = {
                showClearConfirm = false
                onClearAll()
            },
            dismissText = stringResource(R.string.common_cancel),
        )
    }
}

/**
 * The card a saved item sits in: the whole card opens the post.
 *
 * @param onClick Called when the card is tapped.
 * @param content What the card shows.
 */
@Composable
fun SavedPostCard(
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    MochiCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

/**
 * The row that closes a saved card: how many tags the post carries, and a
 * filled bookmark that takes it back out of the saved list.
 *
 * @param tagCount Number of tags on the post; zero hollows the tag icon and
 *   hides the count.
 * @param tagsLabel Content description of the tag icon.
 * @param unsaveLabel Content description of the bookmark.
 * @param onUnsave Called when the bookmark is pressed.
 */
@Composable
fun SavedPostFooter(
    tagCount: Int,
    tagsLabel: String,
    unsaveLabel: String,
    onUnsave: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        val hasTags = tagCount > 0
        val tagColor = MaterialTheme.colorScheme.onSurfaceVariant
        Icon(
            if (hasTags) Icons.Filled.LocalOffer else Icons.Outlined.LocalOffer,
            contentDescription = tagsLabel,
            tint = tagColor,
            modifier = Modifier.size(18.dp),
        )
        if (hasTags) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$tagCount",
                style = MaterialTheme.typography.labelMedium,
                color = tagColor,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        MochiIconButton(onClick = onUnsave, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.Bookmark,
                contentDescription = unsaveLabel,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
