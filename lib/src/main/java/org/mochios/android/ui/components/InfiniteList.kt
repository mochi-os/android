// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mochios.android.api.MochiError

/**
 * Where a paged list's next page stands. The caller owns it: [Loading] while a
 * page is being read, [Failed] with the reason when the last read failed, and
 * [Idle] otherwise.
 */
sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data class Failed(val error: MochiError) : LoadState
}

/**
 * A list that asks for more through [onLoadMore] as its end comes into view.
 * It asks only while [load] is [LoadState.Idle]: a page that failed is shown
 * as the list's last row with its reason and a retry, and is not asked for
 * again until the user retries.
 * With [key], each item is tracked by it, so a row that moves keeps its state
 * and rows that did not change are not redrawn; a page that repeats an item
 * already listed leaves the list unkeyed rather than crashing on the
 * duplicate key.
 */
@Composable
fun <T> InfiniteList(
    items: List<T>,
    load: LoadState,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    emptyContent: @Composable () -> Unit = {},
    key: ((T) -> Any)? = null,
    itemContent: @Composable (T) -> Unit
) {
    if (items.isEmpty() && load == LoadState.Idle) {
        emptyContent()
        return
    }

    // Derive only the scroll position: [hasMore] and [load] are plain
    // parameters, and reading them inside this unkeyed remember would freeze
    // the first composition's values forever. Read them fresh as effect keys.
    val reachedEnd by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 3
        }
    }

    LaunchedEffect(reachedEnd, hasMore, load) {
        if (reachedEnd && hasMore && load == LoadState.Idle) {
            onLoadMore()
        }
    }

    val unique = remember(items, key != null) {
        key != null && items.mapTo(HashSet(items.size)) { item -> key(item) }.size == items.size
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        modifier = modifier.fillMaxSize()
    ) {
        if (unique) {
            items(items, key = key) { item ->
                itemContent(item)
            }
        } else {
            itemsIndexed(items) { _, item ->
                itemContent(item)
            }
        }

        when (load) {
            LoadState.Loading -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is LoadState.Failed -> item {
                InlineErrorState(
                    error = load.error,
                    onRetry = onLoadMore,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            LoadState.Idle -> Unit
        }
    }
}
