// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.comments

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.HtmlContent
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.wikis.R
import org.mochios.wikis.model.WikiComment
import org.mochios.wikis.ui.components.LocalWikiContext
import java.io.File
import org.mochios.android.R as MochiR

/**
 * Renders one [WikiComment] and its descendants, indenting each child by 16dp.
 * Reply always seeds blank: Android has no equivalent of
 * `window.getSelection()` over the Markwon-rendered body, so `selectedText` is
 * always null.
 */
@Composable
fun WikiCommentThread(
    comment: WikiComment,
    slug: String,
    currentUserId: String?,
    isOwner: Boolean,
    replyingTo: String?,
    replyDraft: String,
    onStartReply: (commentId: String, selectedText: String?) -> Unit,
    onCancelReply: () -> Unit,
    onReplyDraftChange: (String) -> Unit,
    onSubmitReply: (commentId: String, files: List<Uri>?) -> Unit,
    resolveFileName: suspend (Uri) -> String,
    onEdit: ((commentId: String, body: String) -> Unit)?,
    onDelete: ((commentId: String) -> Unit)?,
    depth: Int = 0,
) {
    val format = LocalFormat.current
    val wiki = LocalWikiContext.current

    var collapsed by rememberSaveable(comment.id) { mutableStateOf(false) }
    var editing by rememberSaveable(comment.id) { mutableStateOf(false) }
    var editBody by rememberSaveable(comment.id) { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    // Captured ref to the rendered comment-body TextView so the Reply
    // button can read the user's active selection at tap time.
    var bodyTextView by remember(comment.id) { mutableStateOf<TextView?>(null) }

    val canEdit = currentUserId != null && comment.author == currentUserId
    val canDelete = (currentUserId != null && comment.author == currentUserId) || isOwner
    val hasChildren = comment.children.isNotEmpty()
    val isReplying = replyingTo == comment.id

    val timeAgo = format.formatTimestamp(comment.created)
    val displayName = comment.name.ifBlank { comment.author.orEmpty() }

    val avatarSrc = wiki?.let { ctx ->
        "/wikis/${ctx.wikiId}/-/comment/${comment.id}/asset/avatar"
    }

    val totalDescendants = remember(comment) { countDescendants(comment) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp, top = 6.dp),
    ) {
        if (collapsed) {
            // ----- Collapsed: single-row summary -----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { collapsed = false }
                    .padding(vertical = 4.dp),
            ) {
                EntityAvatar(
                    name = displayName,
                    src = avatarSrc,
                    seed = comment.author,
                    size = 20.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = timeAgo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                if (totalDescendants > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.wikis_comment_collapsed_replies,
                            totalDescendants,
                            totalDescendants,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.wikis_comment_expand),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            // ----- Expanded -----
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Avatar — tap collapses the thread, matching web's collapse
                // toggle (the tap surface is the avatar, not a separate icon).
                Column {
                    EntityAvatar(
                        name = displayName,
                        src = avatarSrc,
                        seed = comment.author,
                        size = 28.dp,
                        modifier = Modifier.clickable { collapsed = true },
                    )
                }
                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = timeAgo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (comment.edited > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.wikis_comment_edited),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    // Body — prefer server-rendered HTML, fall back to plain
                    // text. Compose's Text preserves whitespace so newlines in
                    // raw `body` render the same as web's `whitespace-pre-wrap`.
                    if (editing) {
                        MochiTextField(
                            value = editBody,
                            onValueChange = { editBody = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 8,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            MochiTextButton(onClick = { editing = false }) {
                                Text(stringResource(R.string.wikis_comment_action_cancel))
                            }
                            MochiTextButton(
                                onClick = {
                                    if (editBody.isNotBlank()) {
                                        onEdit?.invoke(comment.id, editBody.trim())
                                        editing = false
                                    }
                                },
                                enabled = editBody.isNotBlank(),
                            ) {
                                Text(stringResource(R.string.wikis_comment_action_save))
                            }
                        }
                    } else if (!comment.bodyMarkdown.isNullOrBlank()) {
                        HtmlContent(
                            html = comment.bodyMarkdown,
                            modifier = Modifier.fillMaxWidth(),
                            onTextViewReady = { tv -> bodyTextView = tv },
                        )
                    } else if (comment.body.isNotBlank()) {
                        Text(
                            text = comment.body,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    if (comment.attachments.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        CommentAttachments(attachments = comment.attachments)
                    }

                    // Action chips
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        ActionChip(
                            icon = Icons.AutoMirrored.Filled.Reply,
                            label = stringResource(R.string.wikis_comment_action_reply),
                            onClick = {
                                // Read the active text selection from the rendered comment
                                // body so we can seed the reply with `> `-quoted lines.
                                // Mirrors web's `window.getSelection()` quote-on-select.
                                val tv = bodyTextView
                                val sel = tv?.let {
                                    val start = it.selectionStart.coerceAtLeast(0)
                                    val end = it.selectionEnd.coerceAtLeast(0)
                                    if (end > start) it.text.subSequence(start, end).toString() else null
                                }
                                onStartReply(comment.id, sel)
                            },
                        )
                        if (canEdit && onEdit != null) {
                            Spacer(Modifier.width(4.dp))
                            ActionChip(
                                icon = Icons.Default.Edit,
                                label = stringResource(R.string.wikis_comment_action_edit),
                                onClick = {
                                    editing = true
                                    editBody = comment.body
                                },
                            )
                        }
                        if (canDelete && onDelete != null) {
                            Spacer(Modifier.width(4.dp))
                            ActionChip(
                                icon = Icons.Default.Delete,
                                label = stringResource(R.string.wikis_comment_action_delete),
                                onClick = { deleting = true },
                            )
                        }
                    }

                    if (isReplying) {
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(6.dp))
                        CommentForm(
                            showDivider = false,
                            resolveFileName = resolveFileName,
                            initialText = replyDraft,
                            onSubmit = { _, files ->
                                onSubmitReply(comment.id, files)
                            },
                            onCancel = onCancelReply,
                            placeholder = stringResource(R.string.wikis_comment_form_placeholder_reply),
                            autoFocus = true,
                            onTextChange = onReplyDraftChange,
                        )
                    }
                }
            }

            // Children — render recursively with increased depth.
            if (hasChildren) {
                comment.children.forEach { child ->
                    WikiCommentThread(
                        comment = child,
                        slug = slug,
                        currentUserId = currentUserId,
                        isOwner = isOwner,
                        replyingTo = replyingTo,
                        replyDraft = replyDraft,
                        onStartReply = onStartReply,
                        onCancelReply = onCancelReply,
                        onReplyDraftChange = onReplyDraftChange,
                        onSubmitReply = onSubmitReply,
                        resolveFileName = resolveFileName,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        depth = depth + 1,
                    )
                }
            }
        }

        if (deleting) {
            MochiAlertDialog(
                onDismissRequest = { deleting = false },
                title = stringResource(R.string.wikis_comment_delete_confirm_title),
                text = stringResource(R.string.wikis_comment_delete_confirm_message),
                confirmText = stringResource(R.string.wikis_comment_action_delete),
                onConfirm = {
                    deleting = false
                    onDelete?.invoke(comment.id)
                },
                destructive = true,
                dismissText = stringResource(MochiR.string.common_cancel),
            )
        }
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    MochiTextButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 8.dp,
            vertical = 0.dp,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Count of all descendants (children, grandchildren, ...) of [comment].
 * Used by the collapsed-state "+N more replies" pluralised label.
 */
private fun countDescendants(comment: WikiComment): Int {
    if (comment.children.isEmpty()) return 0
    return comment.children.size + comment.children.sumOf { countDescendants(it) }
}
