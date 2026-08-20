// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.post

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.model.Comment
import org.mochios.feeds.model.Post
import org.mochios.feeds.ui.component.CommentItem
import org.mochios.feeds.ui.component.flattenComments
import org.mochios.feeds.ui.component.stripHtml
import org.mochios.android.R as MochiR

/**
 * The comment thread for one image, shown in the lightbox's comments panel.
 *
 * Comments are one thread per post; a comment may be ANCHORED to one of the
 * post's attachments. This panel renders the post's REAL thread - the same
 * [CommentItem] the post screen draws, with replies, reactions, editing and
 * deletion intact - filtered to the comments anchored to the image being
 * viewed, offers the rest of the thread behind a toggle, and writes new
 * comments in the same [CommentInputBar] as the screen - attachments and all
 * - anchored to this image without the writer having to say so. The draft is
 * the ViewModel's, so it is one draft whether written here or below the post.
 */
@Composable
internal fun AttachmentComments(
    post: Post,
    attachmentId: String,
    viewModel: PostDetailViewModel,
    canComment: Boolean,
    onDeleteComment: (String) -> Unit,
) {
    val editingCommentId by viewModel.editingCommentId.collectAsState()
    val editCommentText by viewModel.editCommentText.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val commentText by viewModel.commentText.collectAsState()
    val commentAttachments by viewModel.commentAttachments.collectAsState()
    val isSendingComment by viewModel.isSendingComment.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()

    var showAll by rememberSaveable(attachmentId) { mutableStateOf(false) }

    // Anchors live on top-level comments; a reply inherits its parent's context.
    val anchored = post.comments.filter { it.anchor == attachmentId }
    val others = post.comments.size - anchored.size
    val shown = flattenComments(if (showAll) post.comments else anchored, 0)

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris -> uris.forEach { viewModel.addCommentAttachment(it) } }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(MochiR.string.lightbox_comments_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
            items(shown.size, key = { shown[it].first.id }) { index ->
                val (comment, depth) = shown[index]
                CommentItem(
                    comment = comment,
                    depth = depth,
                    avatarUrl = "/feeds/${viewModel.feedId}/-/${viewModel.postId}/${comment.id}/asset/avatar",
                    feedId = viewModel.feedId,
                    isEditing = editingCommentId == comment.id,
                    editText = if (editingCommentId == comment.id) editCommentText else "",
                    onEditTextChange = { viewModel.setEditCommentText(it) },
                    onSaveEdit = { viewModel.saveEditComment() },
                    onCancelEdit = { viewModel.cancelEditComment() },
                    onReply = { viewModel.setReplyingTo(comment.id) },
                    onEdit = { viewModel.startEditComment(comment.id, stripHtml(comment.body)) },
                    onDelete = { onDeleteComment(comment.id) },
                    onReact = { reaction -> viewModel.reactToComment(comment.id, reaction) },
                    canManage = permissions.manage,
                    isMine = currentUserId != null && comment.authorId == currentUserId,
                )
            }
            if (others > 0) {
                item(key = "toggle") {
                    TextButton(
                        onClick = { showAll = !showAll },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            if (showAll) stringResource(MochiR.string.lightbox_comments_only)
                            else pluralStringResource(MochiR.plurals.lightbox_comments_others, others, others)
                        )
                    }
                }
            }
        }
        if (canComment) {
            CommentInputBar(
                text = commentText,
                onTextChange = { viewModel.setCommentText(it) },
                attachments = commentAttachments,
                onAddAttachment = { filePicker.launch("*/*") },
                onRemoveAttachment = { viewModel.removeCommentAttachment(it) },
                resolveFileName = viewModel::fileName,
                // A reply keeps its parent's context; only a fresh comment is
                // anchored to the image.
                onSend = { viewModel.sendComment(anchor = attachmentId) },
                isSending = isSendingComment,
                replyingTo = replyingTo,
                onCancelReply = { viewModel.setReplyingTo(null) },
                onSearchMembers = { viewModel.searchMembers(it) },
                placeholder = stringResource(MochiR.string.lightbox_comment_placeholder),
            )
        }
    }
}

/** Every comment in the trees, replies included - what the lightbox count shows for an image. */
internal fun anchoredCommentCount(comments: List<Comment>, attachmentId: String): Int =
    org.mochios.feeds.ui.component.countComments(comments.filter { it.anchor == attachmentId })
