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

/**
 * A list that asks for more through [onLoadMore] as its end comes into view.
 * With [key], each item is tracked by it, so a row that moves keeps its state
 * and rows that did not change are not redrawn; a page that repeats an item
 * already listed leaves the list unkeyed rather than crashing on the
 * duplicate key.
 */
@Composable
fun <T> InfiniteList(
    items: List<T>,
    isLoading: Boolean,
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
    if (items.isEmpty() && !isLoading) {
        emptyContent()
        return
    }

    // Derive only the scroll position: [hasMore] and [isLoading] are plain
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

    LaunchedEffect(reachedEnd, hasMore, isLoading) {
        if (reachedEnd && hasMore && !isLoading) {
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

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
