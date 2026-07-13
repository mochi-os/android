// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.CommentItem as SharedCommentItem
import org.mochios.android.ui.components.ReactionBar
import org.mochios.android.R as MochiR

/**
 * A single comment row, shared by the post-detail comment list and the feed
 * card's inline preview: header (avatar · name · time · edited), body,
 * attachments, a [ReactionBar], and an icon action row (reply, plus edit/delete
 * when [canManage]). Nested replies ([depth] > 0) are indented and marked with a
 * colored vertical thread bar whose colour cycles by depth.
 *
 * @param horizontalPadding leading/trailing inset; pass 0 when the host already
 *   provides horizontal padding (e.g. the feed card content column).
 */
@Composable
internal fun CommentItem(
    comment: Comment,
    depth: Int,
    avatarUrl: String,
    feedId: String,
    isEditing: Boolean,
    editText: String,
    onEditTextChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReact: (String) -> Unit,
    canManage: Boolean,
    isMine: Boolean = false,
    horizontalPadding: Dp = 16.dp,
) {
    SharedCommentItem(
        name = comment.name,
        body = comment.body,
        created = comment.created,
        edited = comment.edited,
        depth = depth,
        seed = comment.authorId,
        avatarUrl = avatarUrl,
        attachments = comment.attachments,
        attachmentUrl = { att -> att.url ?: "/feeds/$feedId/-/attachments/${att.id}" },
        attachmentThumbnailUrl = { att ->
            att.thumbnailUrl ?: "/feeds/$feedId/-/attachments/${att.id}/thumbnail"
        },
        isEditing = isEditing,
        editText = editText,
        onEditTextChange = onEditTextChange,
        onSaveEdit = onSaveEdit,
        onCancelEdit = onCancelEdit,
        horizontalPadding = horizontalPadding,
    ) {
        // Reactions and the action icons share one row: the ReactionBar
        // (pills + add) followed by reply, and edit/delete when the viewer
        // manages the feed or owns the comment.
        ReactionBar(
            reactions = toReactionCounts(comment.reactions, comment.myReaction),
            onReact = onReact,
            onRemoveReaction = { onReact(comment.myReaction) },
            currentReaction = currentReactionType(comment.myReaction)
        )
        IconButton(onClick = onReply, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.AutoMirrored.Outlined.Reply,
                contentDescription = stringResource(MochiR.string.comment_reply),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (canManage || isMine) {
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(MochiR.string.common_edit),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(MochiR.string.common_delete),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Flattens a nested comment tree into a depth-tagged list for a flat
 * [androidx.compose.foundation.lazy.LazyColumn], preserving order: each comment
 * is immediately followed by its (deeper) replies.
 */
internal fun flattenComments(comments: List<Comment>, depth: Int): List<Pair<Comment, Int>> {
    val result = mutableListOf<Pair<Comment, Int>>()
    for (comment in comments) {
        result.add(comment to depth)
        result.addAll(flattenComments(comment.children, depth + 1))
    }
    return result
}

/**
 * Strips the limited HTML a comment body carries (line breaks and common
 * entities) back to plain text — used to seed the edit field from the rendered
 * body.
 */
internal fun stripHtml(html: String): String {
    return html
        .replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .trim()
}
