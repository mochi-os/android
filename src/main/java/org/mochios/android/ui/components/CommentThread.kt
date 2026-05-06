package org.mochi.android.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochi.android.R
import org.mochi.android.i18n.LocalFormat
import org.mochi.android.i18n.formatRelativeTime
import org.mochi.android.model.Attachment
import org.mochi.android.model.Comment
import org.mochi.android.util.toReactionCounts

private const val MAX_DEPTH = 6

@Composable
fun CommentThread(
    comments: List<Comment>,
    currentUserId: String,
    canComment: Boolean,
    canReact: Boolean,
    onReply: (parentId: String, body: String) -> Unit,
    onEdit: (commentId: String, body: String) -> Unit,
    onDelete: (commentId: String) -> Unit,
    onReact: ((commentId: String, reaction: String) -> Unit)? = null,
    attachmentUrlBuilder: ((Attachment) -> String)? = null,
    // Returns the avatar proxy URL for a commenter. Apps should construct this
    // from their own proxy action (e.g. "/feeds/<feed>/-/<post>/<comment>/asset/avatar").
    // When null, the thread falls back to an initials-only placeholder keyed on
    // the comment author's entity ID.
    avatarUrlBuilder: ((Comment) -> String?)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        for (comment in comments) {
            CommentItem(
                comment = comment,
                depth = 0,
                currentUserId = currentUserId,
                canComment = canComment,
                canReact = canReact,
                onReply = onReply,
                onEdit = onEdit,
                onDelete = onDelete,
                onReact = onReact,
                attachmentUrlBuilder = attachmentUrlBuilder,
                avatarUrlBuilder = avatarUrlBuilder
            )
        }
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    depth: Int,
    currentUserId: String,
    canComment: Boolean,
    canReact: Boolean,
    onReply: (parentId: String, body: String) -> Unit,
    onEdit: (commentId: String, body: String) -> Unit,
    onDelete: (commentId: String) -> Unit,
    onReact: ((commentId: String, reaction: String) -> Unit)?,
    attachmentUrlBuilder: ((Attachment) -> String)?,
    avatarUrlBuilder: ((Comment) -> String?)?
) {
    val startPadding = (depth * 16).coerceAtMost(MAX_DEPTH * 16).dp
    var expanded by rememberSaveable { mutableStateOf(true) }
    var showReplyField by rememberSaveable { mutableStateOf(false) }
    var replyText by rememberSaveable { mutableStateOf("") }
    var showEditField by rememberSaveable { mutableStateOf(false) }
    var editText by rememberSaveable { mutableStateOf(comment.bodyMarkdown.ifBlank { comment.text }) }
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding, top = 8.dp, bottom = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            EntityAvatar(
                name = comment.name,
                src = avatarUrlBuilder?.invoke(comment),
                seed = comment.author,
                size = 20.dp,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = comment.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
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
                    text = stringResource(R.string.comment_edited),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (comment.children.isNotEmpty()) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.common_collapse else R.string.common_expand
                    ),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { expanded = !expanded },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (comment.author == currentUserId) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = stringResource(R.string.common_more_options),
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_edit)) },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showMenu = false
                            showEditField = true
                            showReplyField = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete)) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showMenu = false
                            onDelete(comment.id)
                        }
                    )
                }
            }
        }

        if (showEditField) {
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.comment_edit_placeholder)) },
                trailingIcon = {
                    IconButton(onClick = {
                        if (editText.isNotBlank()) {
                            onEdit(comment.id, editText)
                            showEditField = false
                        }
                    }) {
                        Icon(Icons.Default.Send, contentDescription = stringResource(R.string.common_save))
                    }
                }
            )
            TextButton(onClick = {
                showEditField = false
                editText = comment.bodyMarkdown.ifBlank { comment.text }
            }) {
                Text(stringResource(R.string.common_cancel))
            }
        } else {
            HtmlContent(
                html = comment.text,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (comment.attachments.isNotEmpty() && attachmentUrlBuilder != null) {
            Spacer(modifier = Modifier.height(4.dp))
            AttachmentGallery(
                attachments = comment.attachments,
                urlBuilder = attachmentUrlBuilder
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (canComment) {
                TextButton(
                    onClick = {
                        showReplyField = !showReplyField
                        showEditField = false
                    },
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        Icons.Default.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.comment_reply), style = MaterialTheme.typography.labelSmall)
                }
            }

            if (canReact && onReact != null) {
                val reactionCounts = comment.reactions.toReactionCounts(comment.myReaction)
                if (reactionCounts.isNotEmpty()) {
                    ReactionBar(
                        reactions = reactionCounts,
                        onReact = { reaction -> onReact(comment.id, reaction) },
                        onRemoveReaction = { onReact(comment.id, "") }
                    )
                }
            }
        }

        AnimatedVisibility(visible = showReplyField) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.comment_reply_placeholder)) },
                    singleLine = true
                )
                IconButton(onClick = {
                    if (replyText.isNotBlank()) {
                        onReply(comment.id, replyText)
                        replyText = ""
                        showReplyField = false
                    }
                }) {
                    Icon(Icons.Default.Send, contentDescription = stringResource(R.string.comment_send_reply))
                }
            }
        }

        AnimatedVisibility(visible = expanded && comment.children.isNotEmpty()) {
            Column {
                if (depth < MAX_DEPTH) {
                    for (child in comment.children) {
                        CommentItem(
                            comment = child,
                            depth = depth + 1,
                            currentUserId = currentUserId,
                            canComment = canComment,
                            canReact = canReact,
                            onReply = onReply,
                            onEdit = onEdit,
                            onDelete = onDelete,
                            onReact = onReact,
                            attachmentUrlBuilder = attachmentUrlBuilder,
                            avatarUrlBuilder = avatarUrlBuilder
                        )
                    }
                } else {
                    for (child in comment.children) {
                        CommentItem(
                            comment = child,
                            depth = MAX_DEPTH,
                            currentUserId = currentUserId,
                            canComment = canComment,
                            canReact = canReact,
                            onReply = onReply,
                            onEdit = onEdit,
                            onDelete = onDelete,
                            onReact = onReact,
                            attachmentUrlBuilder = attachmentUrlBuilder,
                            avatarUrlBuilder = avatarUrlBuilder
                        )
                    }
                }
            }
        }
    }
}
