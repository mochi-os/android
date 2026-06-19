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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.MentionSuggestion
import org.mochios.android.ui.components.MentionTextField
import org.mochios.crm.R
import java.io.File
import org.mochios.android.R as MochiR

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommentsTab(
    comments: List<Comment>,
    crmId: String,
    onCreateComment: (String, String?, List<File>) -> Unit,
    onUpdateComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
    onSearchUsers: (suspend (String) -> List<MentionSuggestion>)? = null,
    // Builds the avatar proxy path for a commenter. Should return a
    // server-relative path to the crm app's proxy action, e.g.
    // "/crm/<crm>/-/comment/<comment.id>/asset/avatar".
    avatarUrlBuilder: ((Comment) -> String?)? = null
) {
    val context = LocalContext.current
    var newComment by remember { mutableStateOf("") }
    var replyToId by remember { mutableStateOf<String?>(null) }
    var replyToName by remember { mutableStateOf<String?>(null) }
    val pendingFiles = remember { mutableStateListOf<File>() }
    val defaultName = stringResource(R.string.crm_attachment_default_name)

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        for (uri in uris) {
            val inputStream = context.contentResolver.openInputStream(uri) ?: continue
            val fileName = uri.lastPathSegment ?: defaultName
            val tempFile = File(context.cacheDir, fileName)
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            pendingFiles.add(tempFile)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Comment input
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
                    pendingFiles.forEach { file ->
                        AssistChip(
                            onClick = { pendingFiles.remove(file) },
                            label = { Text(file.name) },
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                MentionTextField(
                    value = newComment,
                    onValueChange = { newComment = it },
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
                IconButton(
                    onClick = {
                        if (newComment.isNotBlank() || pendingFiles.isNotEmpty()) {
                            onCreateComment(newComment, replyToId, pendingFiles.toList())
                            newComment = ""
                            replyToId = null
                            replyToName = null
                            pendingFiles.clear()
                        }
                    },
                    enabled = newComment.isNotBlank() || pendingFiles.isNotEmpty()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.crm_comment_send))
                }
            }
        }

        HorizontalDivider()

        // Comment list
        if (comments.isEmpty()) {
            Box(
                modifier = Modifier
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(comments, key = { it.id }) { comment ->
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
    val avatarUrl = avatarUrlBuilder?.invoke(comment)
    var showOverflow by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editContent by remember { mutableStateOf(comment.text) }
    val indent = (depth * 16).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent + 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityAvatar(
                name = comment.name.orEmpty(),
                src = avatarUrl,
                seed = comment.author,
                size = 20.dp,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = comment.name.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = LocalFormat.current.formatTimestamp(comment.created),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box {
                IconButton(
                    onClick = { showOverflow = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(MochiR.string.common_more_options),
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(
                    expanded = showOverflow,
                    onDismissRequest = { showOverflow = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(MochiR.string.comment_reply)) },
                        onClick = {
                            showOverflow = false
                            onReply(comment.id, comment.name)
                        },
                        leadingIcon = { Icon(Icons.Default.Reply, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MochiR.string.common_edit)) },
                        onClick = {
                            showOverflow = false
                            isEditing = true
                            editContent = comment.text
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MochiR.string.common_delete)) },
                        onClick = {
                            showOverflow = false
                            onDelete(comment.id)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    )
                }
            }
        }

        if (isEditing) {
            OutlinedTextField(
                value = editContent,
                onValueChange = { editContent = it },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 6
            )
            Row {
                IconButton(onClick = {
                    onEdit(comment.id, editContent)
                    isEditing = false
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(MochiR.string.common_save))
                }
                IconButton(onClick = { isEditing = false }) {
                    Text(stringResource(MochiR.string.common_cancel), style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            Text(
                text = comment.text.orEmpty(),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (comment.attachments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            AttachmentGallery(
                attachments = comment.attachments,
                urlBuilder = { att ->
                    att.url ?: "/crm/$crmId/-/attachments/${att.id}"
                },
                thumbnailUrlBuilder = { att ->
                    att.thumbnailUrl ?: "/crm/$crmId/-/attachments/${att.id}/thumbnail"
                }
            )
        }

        // Render children
        if (comment.children.isNotEmpty()) {
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
    }
}

