// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.files.rememberFileLabel
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.CommentItem as SharedCommentItem
import org.mochios.android.ui.components.MentionSuggestion
import org.mochios.android.ui.components.MentionTextField
import org.mochios.crm.R
import org.mochios.android.R as MochiR

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommentsTab(
    comments: List<Comment>,
    crmId: String,
    onCreateComment: (String, String?, List<Uri>) -> Unit,
    resolveFileName: suspend (Uri) -> String,
    onUpdateComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
    onSearchUsers: (suspend (String) -> List<MentionSuggestion>)? = null,
    // Builds the avatar proxy path for a commenter. Should return a
    // server-relative path to the crm app's proxy action, e.g.
    // "/crm/<crm>/-/comment/<comment.id>/asset/avatar".
    avatarUrlBuilder: ((Comment) -> String?)? = null
) {
    var newComment by remember { mutableStateOf("") }
    var replyToId by remember { mutableStateOf<String?>(null) }
    var replyToName by remember { mutableStateOf<String?>(null) }
    val pendingFiles = remember { mutableStateListOf<Uri>() }
    val defaultName = stringResource(R.string.crm_attachment_default_name)

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        pendingFiles.addAll(uris)
    }

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
                    text = stringResource(R.string.crm_comment_empty),
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
                        crmId = crmId,
                        avatarUrlBuilder = avatarUrlBuilder,
                        onReply = { id, name ->
                            replyToId = id
                            replyToName = name
                        },
                        onEdit = onUpdateComment,
                        onDelete = onDeleteComment
                    )
                }
            }
        }

        HorizontalDivider()

        // Comment input, pinned below the list
        Column(modifier = Modifier.padding(16.dp)) {
            if (replyToName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.crm_comment_replying_to, replyToName!!),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            replyToId = null
                            replyToName = null
                        },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Text(
                            "x",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (pendingFiles.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    pendingFiles.forEach { uri ->
                        AssistChip(
                            onClick = { pendingFiles.remove(uri) },
                            label = {
                                Text(rememberFileLabel(uri, resolveFileName, defaultName))
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.crm_comment_remove_attachment),
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            // Attachments ride along with a comment rather than standing in for
            // one, so text is what makes a comment sendable.
            val canSend = newComment.isNotBlank()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                MentionTextField(
                    value = newComment,
                    onValueChange = { value -> newComment = value },
                    onSearch = onSearchUsers ?: { emptyList() },
                    placeholder = { Text(stringResource(R.string.crm_comment_placeholder)) },
                    maxLines = 4,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { filePicker.launch("*/*") }
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = stringResource(R.string.crm_comment_attach)
                    )
                }
                // Send appears only once there is something to send — an empty
                // composer shows no button rather than a dead greyed-out one.
                if (canSend) {
                    IconButton(
                        onClick = {
                            onCreateComment(newComment, replyToId, pendingFiles.toList())
                            newComment = ""
                            replyToId = null
                            replyToName = null
                            pendingFiles.clear()
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.crm_comment_send)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    depth: Int,
    crmId: String,
    avatarUrlBuilder: ((Comment) -> String?)?,
    onReply: (String, String) -> Unit,
    onEdit: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(comment.id) { mutableStateOf(comment.text) }

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
            att.url ?: "/crm/$crmId/-/attachments/${att.id}"
        },
        attachmentThumbnailUrl = { att ->
            att.thumbnailUrl ?: "/crm/$crmId/-/attachments/${att.id}/thumbnail"
        },
        isEditing = isEditing,
        editText = editText,
        onEditTextChange = { value -> editText = value },
        onSaveEdit = {
            onEdit(comment.id, editText)
            isEditing = false
        },
        onCancelEdit = { isEditing = false }
    ) {
        IconButton(
            onClick = { onReply(comment.id, comment.name) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Reply,
                contentDescription = stringResource(MochiR.string.comment_reply),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = {
                editText = comment.text
                isEditing = true
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = stringResource(MochiR.string.common_edit),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = { onDelete(comment.id) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(MochiR.string.common_delete),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    comment.children.forEach { child ->
        CommentItem(
            comment = child,
            depth = depth + 1,
            crmId = crmId,
            avatarUrlBuilder = avatarUrlBuilder,
            onReply = onReply,
            onEdit = onEdit,
            onDelete = onDelete
        )
    }
}
