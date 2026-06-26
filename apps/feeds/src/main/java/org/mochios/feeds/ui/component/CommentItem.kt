// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.HtmlContent
import org.mochios.android.ui.components.ReactionBar
import org.mochios.feeds.R
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
    // Replies are indented and marked with a colored vertical bar so nested
    // conversations read as connected threads; the colour cycles by depth.
    val threadPalette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        if (depth > 0) {
            Spacer(
                modifier = Modifier.width(
                    horizontalPadding + 16.dp * (depth - 1).coerceAtMost(4)
                )
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(threadPalette[(depth - 1) % threadPalette.size])
            )
            Spacer(modifier = Modifier.width(10.dp))
        } else if (horizontalPadding > 0.dp) {
            Spacer(modifier = Modifier.width(horizontalPadding))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = horizontalPadding, top = 8.dp, bottom = 8.dp)
        ) {
            val anonymous = stringResource(R.string.feeds_anonymous)
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntityAvatar(
                    name = comment.name.ifEmpty { anonymous },
                    src = avatarUrl,
                    seed = comment.authorId,
                    size = 20.dp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = comment.name.ifEmpty { anonymous },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = LocalFormat.current.formatRelativeTime(comment.created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (comment.edited > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(MochiR.string.comment_edited),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = onEditTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancelEdit) {
                        Text(stringResource(MochiR.string.common_cancel))
                    }
                    TextButton(onClick = onSaveEdit) {
                        Text(stringResource(MochiR.string.common_save))
                    }
                }
            } else {
                HtmlContent(
                    html = comment.body,
                    modifier = Modifier.fillMaxWidth()
                )

                if (comment.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AttachmentGallery(
                        attachments = comment.attachments,
                        urlBuilder = { att ->
                            att.url ?: "/feeds/$feedId/-/attachments/${att.id}"
                        },
                        thumbnailUrlBuilder = { att ->
                            att.thumbnailUrl ?: "/feeds/$feedId/-/attachments/${att.id}/thumbnail"
                        }
                    )
                }

                // Reactions and the action icons share one row: the ReactionBar
                // (pills + add) followed by reply, and edit/delete when the
                // viewer manages the feed or owns the comment.
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
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
