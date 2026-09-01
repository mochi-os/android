// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.saved

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.HtmlContent
import org.mochios.android.ui.components.SavedListLabels
import org.mochios.android.ui.components.SavedListScaffold
import org.mochios.android.ui.components.SavedPostCard
import org.mochios.android.ui.components.SavedPostFooter
import org.mochios.forums.R
import org.mochios.forums.model.SavedItem

@Composable
fun SavedScreen(
    onNavigateBack: () -> Unit,
    onOpenPost: (forumId: String, postId: String) -> Unit,
    viewModel: SavedViewModel = hiltViewModel(),
) {
    val saved by viewModel.saved.collectAsState()

    SavedListScaffold(
        items = saved,
        key = { item -> item.post.id },
        labels = SavedListLabels(
            title = stringResource(R.string.forums_saved_title),
            clearAll = stringResource(R.string.forums_saved_clear_all),
            emptyTitle = stringResource(R.string.forums_saved_empty_title),
            emptySubtitle = stringResource(R.string.forums_saved_empty_subtitle),
            clearConfirmTitle = stringResource(R.string.forums_saved_clear_confirm_title),
            clearConfirmBody = stringResource(R.string.forums_saved_clear_confirm_body),
            clearError = stringResource(R.string.forums_saved_error_clear),
        ),
        clearFailed = viewModel.clearFailed,
        onNavigateBack = onNavigateBack,
        onClearAll = { viewModel.clearAll() },
    ) { item ->
        SavedPostCardContent(
            item = item,
            onClick = { onOpenPost(item.post.forum, item.post.id) },
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

    SavedPostCard(onClick = onClick) {
        if (post.forumName.isNotBlank()) {
            Text(
                text = post.forumName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (post.title.isNotBlank()) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        val byline = listOfNotNull(
            post.name.takeIf { it.isNotBlank() },
            LocalFormat.current.formatTimestamp(post.created),
        ).joinToString(" · ")
        if (byline.isNotBlank()) {
            Text(
                text = byline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        val body = post.body.ifBlank { post.bodyMarkdown }
        if (body.isNotBlank()) {
            HtmlContent(
                html = body,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 4,
            )
        }

        // Unsave is implemented here in the Saved list screen (SavedScreen).
        SavedPostFooter(
            tagCount = post.tags.size,
            tagsLabel = stringResource(R.string.forums_post_tag_label),
            unsaveLabel = stringResource(R.string.forums_saved_remove),
            onUnsave = onUnsave,
        )
    }
}
