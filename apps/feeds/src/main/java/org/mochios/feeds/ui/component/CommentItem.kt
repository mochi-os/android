// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.util.isWebUrl
import org.mochios.android.util.openWeb
import org.mochios.feeds.R
import org.mochios.android.R as MochiR
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.CommentActions
import org.mochios.android.ui.components.CommentItem as SharedCommentItem
import org.mochios.android.ui.components.ReactionBar

/**
 * One comment row, shared by the post-detail list and the feed card's inline
 * preview. [horizontalPadding] is 0 when the host already insets.
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
    // Opens the lightbox on the image an anchored comment is about, comments
    // showing; null hides the chip's tap (the chip itself still shows).
    onOpenAttachment: ((String) -> Unit)? = null,
    // The owner's mute, block or report of the person on another network
    // behind the comment; null where the viewer may not.
    onModerate: ((String) -> Unit)? = null,
) {
    val anchor = comment.anchor
    val context = LocalContext.current
    val page = comment.page.orEmpty()
    SharedCommentItem(
        name = comment.name,
        body = comment.text,
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
        anchorThumbnailUrl = anchor.takeIf { it.isNotEmpty() }
            ?.let { "/feeds/$feedId/-/attachments/$it/thumbnail" },
        anchorCaption = comment.attachmentCaption.orEmpty(),
        onOpenAnchor = onOpenAttachment?.let { open -> { open(anchor) } },
        onName = if (isWebUrl(page)) ({ openWeb(context, page) }) else null,
    ) {
        // Reactions and the action icons share one row: the ReactionBar
        // (pills + add) followed by reply, and edit/delete when the viewer
        // manages the feed or owns the comment.
        CommentActions(
            onReply = onReply,
            onEdit = onEdit.takeIf { canManage || isMine },
            onDelete = onDelete.takeIf { canManage || isMine },
            reactions = {
                ReactionBar(
                    reactions = toReactionCounts(comment.reactions, comment.myReaction),
                    onReact = onReact,
                    onRemoveReaction = { onReact("none") },
                    currentReaction = currentReactionType(comment.myReaction),
                )
            },
        )
        if (onModerate != null && outsider(comment)) {
            CommenterMenu(author = comment.name.ifEmpty { comment.authorId }, onModerate = onModerate)
        }
    }
}

/** The owner's ways with the person on another network behind a comment. */
@Composable
private fun CommenterMenu(author: String, onModerate: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        MochiIconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Outlined.MoreHoriz,
                contentDescription = stringResource(MochiR.string.common_more_options),
                modifier = Modifier.size(18.dp),
            )
        }
        MochiDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MochiDropdownMenuItem(
                text = { Text(stringResource(R.string.feeds_mute_commenter, author)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.VolumeOff, contentDescription = null) },
                onClick = {
                    open = false
                    onModerate("mute")
                },
            )
            MochiDropdownMenuItem(
                text = { Text(stringResource(R.string.feeds_block_commenter, author)) },
                leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                onClick = {
                    open = false
                    onModerate("block")
                },
            )
            MochiDropdownMenuItem(
                text = { Text(stringResource(R.string.feeds_report_commenter, author)) },
                leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                onClick = {
                    open = false
                    onModerate("report")
                },
            )
        }
    }
}

/**
 * Depth-tagged pre-order flattening of a comment tree, for a LazyColumn.
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
 * Plain text from a comment body's limited HTML, to seed the edit field.
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

/** Every comment in the trees, replies included - what a lightbox comments count shows. */
internal fun countComments(comments: List<Comment>): Int =
    comments.sumOf { 1 + countComments(it.children) }
