// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.saved

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import org.mochios.android.ui.components.HtmlContent
import org.mochios.android.ui.components.SavedListLabels
import org.mochios.android.ui.components.SavedListScaffold
import org.mochios.android.ui.components.SavedPostCard
import org.mochios.android.ui.components.SavedPostFooter
import org.mochios.feeds.R
import org.mochios.feeds.model.SavedItem
import org.mochios.feeds.ui.component.PostTitle

@Composable
fun SavedScreen(
    onNavigateBack: () -> Unit,
    onOpenPost: (feedId: String, postId: String) -> Unit,
    viewModel: SavedViewModel = hiltViewModel(),
) {
    val saved by viewModel.saved.collectAsState()

    SavedListScaffold(
        items = saved,
        key = { item -> item.post.id },
        labels = SavedListLabels(
            title = stringResource(R.string.feeds_saved_title),
            clearAll = stringResource(R.string.feeds_saved_clear_all),
            emptyTitle = stringResource(R.string.feeds_saved_empty_title),
            emptySubtitle = stringResource(R.string.feeds_saved_empty_subtitle),
            clearConfirmTitle = stringResource(R.string.feeds_saved_clear_confirm_title),
            clearConfirmBody = stringResource(R.string.feeds_saved_clear_confirm_body),
            clearError = stringResource(R.string.feeds_saved_error_clear),
        ),
        clearFailed = viewModel.clearFailed,
        onNavigateBack = onNavigateBack,
        onClearAll = { viewModel.clearAll() },
    ) { item ->
        SavedPostCardContent(
            item = item,
            onClick = { onOpenPost(item.post.feedId, item.post.id) },
            onUnsave = { viewModel.remove(item.post.id) },
        )
    }
}

@Composable
private fun SavedPostCardContent(
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
        .map { attachment ->
            attachment.url ?: "/feeds/${post.feedId}/-/attachments/${attachment.id}"
        }
    val heroUrl = attachmentImageUrls.firstOrNull()
        ?: post.data?.rss?.image?.takeIf { it.isNotEmpty() }
    val previewBody = post.bodyHtml.ifBlank { post.body }

    SavedPostCard(onClick = onClick) {
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

        // The tag icon and count mirror the feed post's footer, minus reactions
        // and comments.
        SavedPostFooter(
            tagCount = post.tags.size,
            tagsLabel = stringResource(R.string.feeds_tags),
            unsaveLabel = stringResource(R.string.feeds_saved_remove),
            onUnsave = onUnsave,
        )
    }
}
