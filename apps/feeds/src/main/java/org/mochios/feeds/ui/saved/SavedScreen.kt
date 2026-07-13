// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.saved

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.HtmlContent
import org.mochios.feeds.R
import org.mochios.feeds.ui.component.PostTitle
import org.mochios.feeds.model.SavedItem
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    onNavigateBack: () -> Unit,
    onOpenPost: (feedId: String, postId: String) -> Unit,
    viewModel: SavedViewModel = hiltViewModel(),
) {
    val saved by viewModel.saved.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearConfirm by remember { mutableStateOf(false) }

    val clearError = stringResource(R.string.feeds_saved_error_clear)
    LaunchedEffect(Unit) {
        viewModel.clearFailed.collect { snackbarHostState.showSnackbar(clearError) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feeds_saved_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back)
                        )
                    }
                },
                actions = {
                    if (saved.isNotEmpty()) {
                        TextButton(onClick = { showClearConfirm = true }) {
                            Text(stringResource(R.string.feeds_saved_clear_all))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (saved.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Filled.Bookmark,
                    title = stringResource(R.string.feeds_saved_empty_title),
                    subtitle = stringResource(R.string.feeds_saved_empty_subtitle),
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
                items(saved, key = { it.post.id }) { item ->
                    SavedPostCard(
                        item = item,
                        onClick = { onOpenPost(item.post.feedId, item.post.id) },
                        onUnsave = { viewModel.remove(item.post.id) },
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.feeds_saved_clear_confirm_title)) },
            text = { Text(stringResource(R.string.feeds_saved_clear_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clearAll()
                }) {
                    Text(stringResource(R.string.feeds_saved_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(MochiR.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun SavedPostCard(
    item: SavedItem,
    onClick: () -> Unit,
    onUnsave: () -> Unit,
) {
    val post = item.post

    // Resolve the title and hero image exactly as the feed's PostCard does: the
    // RSS title (shown only when the body starts with it), and the first image
    // attachment's full URL, else the RSS preview image.
    val rawTitle = post.data?.rss?.title.orEmpty()
    val displayTitle = rawTitle.trim().takeIf { it.isNotEmpty() && post.body.startsWith(rawTitle) }
    val attachmentImageUrls = post.attachments
        .filter { attachment -> attachment.isImage }
        .map { attachment -> attachment.url ?: "/feeds/${post.feedId}/-/attachments/${attachment.id}" }
    val heroUrl = attachmentImageUrls.firstOrNull()
        ?: post.data?.rss?.image?.takeIf { it.isNotEmpty() }
    val previewBody = post.bodyHtml.ifBlank { post.body }

    OutlinedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (displayTitle != null) {
                PostTitle(
                    title = displayTitle,
                    fontSize = 20.sp,
                    truncated = true,
                )
            }
            if (heroUrl != null) {
                AsyncImage(
                    model = heroUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
            if (previewBody.isNotBlank()) {
                HtmlContent(
                    html = previewBody,
                    modifier = Modifier.padding(top = 8.dp),
                    maxLines = 6,
                )
            }

            // Tag count + a bookmark to remove from saved — the tag icon/count
            // mirror the feed post's footer, minus reactions and comments.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                val hasTags = post.tags.isNotEmpty()
                val tagColor = MaterialTheme.colorScheme.onSurfaceVariant
                Icon(
                    if (hasTags) Icons.Filled.LocalOffer else Icons.Outlined.LocalOffer,
                    contentDescription = stringResource(R.string.feeds_tags),
                    tint = tagColor,
                    modifier = Modifier.size(18.dp),
                )
                if (hasTags) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${post.tags.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = tagColor,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(onClick = onUnsave, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = stringResource(R.string.feeds_saved_remove),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
