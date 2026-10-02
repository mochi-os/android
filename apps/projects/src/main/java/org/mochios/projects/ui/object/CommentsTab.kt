// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.R as MochiR
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.CommentActions
import org.mochios.android.ui.components.CommentItem as SharedCommentItem
import org.mochios.android.ui.components.ComposeBar
import org.mochios.android.ui.components.ComposeBarAttachments
import org.mochios.android.ui.components.ComposeBarDefaults
import org.mochios.android.ui.components.MentionSuggestion
import org.mochios.android.ui.components.CommentDrafts
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.ReplyComposerBanner
import org.mochios.projects.R

@Composable
fun CommentsTab(
    comments: List<Comment>,
    projectId: String,
    /** Holds the unsent comment and any edit under way, past this tab's own life. */
    drafts: CommentDrafts,
    /** The object commented on, which the unsent comment is kept under. */
    target: String,
    onCreateComment: (String, String?, List<Uri>) -> Unit,
    resolveFileName: suspend (Uri) -> String,
    onUpdateComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
    onSearchUsers: (suspend (String) -> List<MentionSuggestion>)? = null,
    // Builds the avatar proxy path for a commenter. Should return a
    // server-relative path to the projects app's proxy action, e.g.
    // "/projects/<project>/-/comment/<comment.id>/asset/avatar".
    avatarUrlBuilder: ((Comment) -> String?)? = null
) {
    val context = LocalContext.current
    val draft = drafts.draft(target)
    val defaultName = stringResource(R.string.projects_attachment_default_name)

    Column(modifier = Modifier.fillMaxSize()) {
        // Comment list — takes the remaining height so the composer stays pinned
        // to the bottom, matching the feeds comment composer.
        if (comments.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.projects_comment_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(comments, key = { comment -> comment.id }) { comment ->
                    CommentItem(
                        comment = comment,
                        depth = 0,
                        projectId = projectId,
                        avatarUrlBuilder = avatarUrlBuilder,
                        drafts = drafts,
                        onReply = { id, _ -> drafts.update(target) { it.copy(reply = id) } },
                        onEdit = onUpdateComment,
                        onDelete = onDeleteComment
                    )
                }
            }
        }

        val replyTarget = draft.reply?.let { id -> findComment(comments, id) }
        ComposeBar(
            value = draft.text,
            onValueChange = { value -> drafts.update(target) { it.copy(text = value) } },
            onSend = {
                val sending = drafts.draft(target)
                onCreateComment(sending.text, sending.reply, sending.files)
                drafts.clear(target)
            },
            placeholder = stringResource(R.string.projects_comment_placeholder),
            sendLabel = stringResource(R.string.projects_comment_send),
            requireText = true,
            attachments = ComposeBarAttachments(
                pending = draft.files,
                onAdd = { uris -> drafts.update(target) { it.copy(files = it.files + uris) } },
                onRemove = { uri -> drafts.update(target) { it.copy(files = it.files - uri) } },
                resolveFileName = resolveFileName,
                addLabel = stringResource(R.string.projects_comment_attach),
                fallbackLabel = defaultName,
                removeLabel = stringResource(R.string.projects_comment_remove_attachment),
            ),
            onSearchMentions = onSearchUsers,
            banner = replyTarget?.let { comment ->
                {
                    ReplyComposerBanner(
                        label = stringResource(
                            R.string.projects_comment_replying_to,
                            comment.name.ifBlank { comment.authorId },
                        ),
                        preview = comment.markdownSource.ifBlank { comment.text },
                        cancelLabel = stringResource(R.string.projects_comment_clear_reply),
                        onCancel = { drafts.update(target) { it.copy(reply = null) } },
                    )
                }
            },
            windowInsets = ComposeBarDefaults.NoWindowInsets,
        )
    }
}

/**
 * The comment with this id, wherever it sits in the thread.
 *
 * @param comments The thread roots to search.
 * @param id The comment being looked for.
 * @return The comment, or null when it is no longer in the thread.
 */
private fun findComment(comments: List<Comment>, id: String): Comment? {
    for (comment in comments) {
        if (comment.id == id) return comment
        val found = findComment(comment.children, id)
        if (found != null) return found
    }
    return null
}

@Composable
private fun CommentItem(
    comment: Comment,
    depth: Int,
    projectId: String,
    avatarUrlBuilder: ((Comment) -> String?)?,
    drafts: CommentDrafts,
    onReply: (String, String) -> Unit,
    onEdit: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    val edit = drafts.edit(comment.id)

    SharedCommentItem(
        name = comment.name,
        body = comment.text,
        created = comment.created,
        edited = comment.edited,
        depth = depth,
        seed = comment.authorId,
        avatarUrl = avatarUrlBuilder?.invoke(comment),
        attachments = comment.attachments,
        attachmentUrl = { att ->
            att.url ?: "/projects/$projectId/-/attachments/${att.id}"
        },
        attachmentThumbnailUrl = { att ->
            att.thumbnailUrl ?: "/projects/$projectId/-/attachments/${att.id}/thumbnail"
        },
        isEditing = edit != null,
        editText = edit ?: comment.text,
        onEditTextChange = { value -> drafts.edit(comment.id, value) },
        onSaveEdit = {
            drafts.edit(comment.id)?.let { text -> onEdit(comment.id, text) }
            drafts.edit(comment.id, null)
        },
        onCancelEdit = { drafts.edit(comment.id, null) }
    ) {
        CommentActions(
            onReply = { onReply(comment.id, comment.name) },
            onEdit = { drafts.edit(comment.id, comment.text) },
            onDelete = { onDelete(comment.id) },
        )
    }

    comment.children.forEach { child ->
        CommentItem(
            comment = child,
            depth = depth + 1,
            projectId = projectId,
            avatarUrlBuilder = avatarUrlBuilder,
            drafts = drafts,
            onReply = onReply,
            onEdit = onEdit,
            onDelete = onDelete
        )
    }
}

