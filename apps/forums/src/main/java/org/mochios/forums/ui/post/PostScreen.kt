// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.post

import org.mochios.android.ui.components.CommentItem
import org.mochios.android.ui.components.TagItem
import org.mochios.android.ui.components.PostTagsButton
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.ui.platform.LocalContext
import android.provider.OpenableColumns
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.HtmlContent
import org.mochios.forums.R
import org.mochios.forums.model.ForumComment
import org.mochios.forums.model.Post
import org.mochios.forums.model.Tag
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(
    onBack: () -> Unit,
    viewModel: PostViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val commentAttachments by viewModel.commentAttachments.collectAsState()
    var draft by remember { mutableStateOf("") }
    var showDeletePostConfirm by remember { mutableStateOf(false) }
    var commentToDelete by remember { mutableStateOf<ForumComment?>(null) }
    var showEditPost by remember { mutableStateOf(false) }
    var editingComment by remember { mutableStateOf<ForumComment?>(null) }
    var showReportPost by remember { mutableStateOf(false) }
    var reportingComment by remember { mutableStateOf<ForumComment?>(null) }
    var showPostMenu by remember { mutableStateOf(false) }
    val isPostAuthor = uiState.post.member == uiState.identity && uiState.identity.isNotBlank()

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // The post's own title — the forum name is already implied by
                    // where the user came from.
                    Text(
                        text = uiState.post.title.ifBlank { stringResource(R.string.forums_loading) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back)
                        )
                    }
                },
                actions = {
                    // The post's own actions live in the bar rather than beside
                    // the title, leaving the card as pure content.
                    if (uiState.post.id.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showPostMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(
                                        MochiR.string.common_more_options
                                    )
                                )
                            }
                            PostActionsMenu(
                                expanded = showPostMenu,
                                onDismiss = { showPostMenu = false },
                                post = uiState.post,
                                canEdit = isPostAuthor || uiState.canModerate,
                                canModerate = uiState.canModerate,
                                isAuthor = isPostAuthor,
                                onEdit = { showEditPost = true },
                                onDelete = { showDeletePostConfirm = true },
                                onPin = viewModel::pinPost,
                                onUnpin = viewModel::unpinPost,
                                onLock = viewModel::lockPost,
                                onUnlock = viewModel::unlockPost,
                                onApprove = viewModel::approvePost,
                                onRemove = viewModel::removePost,
                                onRestore = viewModel::restorePost,
                                onReport = { showReportPost = true },
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading && uiState.post.id.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error is MochiError.NotFoundError && uiState.post.id.isEmpty() -> {
                    NotFoundState(
                        title = stringResource(R.string.forums_post_not_found),
                        onBack = onBack,
                    )
                }
                uiState.error != null && uiState.post.id.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(uiState.error!!.userMessage(), color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            PostHeader(
                                post = uiState.post,
                                canVote = uiState.canVote,
                                canEdit = isPostAuthor || uiState.canModerate,
                                forumId = viewModel.forumId,
                                onVote = { viewModel.votePost(it) },
                                onAddTag = { label -> viewModel.addPostTag(label) },
                                onRemoveTag = { tagId -> viewModel.removePostTag(tagId) },
                                onTagInterest = { qid, direction -> viewModel.adjustTagInterest(qid, direction) },
                            )
                        }
                        // Separates the post from the conversation below it.
                        item { HorizontalDivider() }
                        if (uiState.comments.isEmpty()) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(top = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        stringResource(R.string.forums_no_comments),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            commentsItems(
                                comments = uiState.comments,
                                forumId = viewModel.forumId,
                                currentIdentity = uiState.identity,
                                canModerate = uiState.canModerate,
                                onVote = viewModel::voteComment,
                                onReply = { viewModel.setReplyTo(it) },
                                onEdit = { editingComment = it },
                                onDelete = { commentToDelete = it },
                                onApprove = { viewModel.approveComment(it.id) },
                                onRemove = { viewModel.removeComment(it.id) },
                                onRestore = { viewModel.restoreComment(it.id) },
                                onReport = { reportingComment = it },
                            )
                        }
                    }
                    if (uiState.canComment) {
                        ReplyBanner(uiState.replyTo, onClear = { viewModel.setReplyTo(null) })
                        ComposerBar(
                            value = draft,
                            onValueChange = { draft = it },
                            isSending = uiState.isSending,
                            enabled = !uiState.post.locked,
                            attachments = commentAttachments,
                            onAddAttachments = { uris -> viewModel.addCommentAttachments(uris) },
                            onRemoveAttachment = { uri -> viewModel.removeCommentAttachment(uri) },
                            onSend = {
                                viewModel.submitComment(draft)
                                draft = ""
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeletePostConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.forums_post_delete_title),
            message = stringResource(R.string.forums_post_delete_message),
            confirmLabel = stringResource(R.string.forums_post_delete),
            dismissLabel = stringResource(MochiR.string.common_cancel),
            isDestructive = true,
            onConfirm = {
                showDeletePostConfirm = false
                viewModel.deletePost()
            },
            onDismiss = { showDeletePostConfirm = false }
        )
    }

    commentToDelete?.let { c ->
        ConfirmDialog(
            title = stringResource(R.string.forums_comment_delete_title),
            message = stringResource(R.string.forums_comment_delete_message),
            confirmLabel = stringResource(R.string.forums_comment_delete),
            dismissLabel = stringResource(MochiR.string.common_cancel),
            isDestructive = true,
            onConfirm = {
                viewModel.deleteComment(c.id)
                commentToDelete = null
            },
            onDismiss = { commentToDelete = null }
        )
    }

    if (showEditPost) {
        EditPostDialog(
            initialTitle = uiState.post.title,
            initialBody = uiState.post.body,
            onConfirm = { title, body ->
                viewModel.editPost(title, body)
                showEditPost = false
            },
            onDismiss = { showEditPost = false }
        )
    }

    editingComment?.let { c ->
        val ctx = androidx.compose.ui.platform.LocalContext.current
        EditCommentDialog(
            comment = c,
            onConfirm = { body, keptIds, newUris ->
                viewModel.editCommentWithAttachments(
                    c.id, body, keptIds, newUris, ctx.contentResolver
                )
                editingComment = null
            },
            onDismiss = { editingComment = null }
        )
    }

    if (showReportPost) {
        ReportDialog(
            title = stringResource(R.string.forums_post_report),
            onConfirm = { reason, details ->
                viewModel.reportPost(reason, details)
                showReportPost = false
            },
            onDismiss = { showReportPost = false }
        )
    }

    reportingComment?.let { c ->
        ReportDialog(
            title = stringResource(R.string.forums_comment_report),
            onConfirm = { reason, details ->
                viewModel.reportComment(c.id, reason, details)
                reportingComment = null
            },
            onDismiss = { reportingComment = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPostDialog(
    initialTitle: String,
    initialBody: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var body by remember { mutableStateOf(initialBody) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forums_post_edit_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.forums_post_edit_title_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.forums_post_edit_body_field)) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, body) },
                enabled = title.isNotBlank()
            ) {
                Text(stringResource(MochiR.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EditCommentDialog(
    comment: ForumComment,
    onConfirm: (body: String, keptAttachmentIds: List<String>, newUris: List<android.net.Uri>) -> Unit,
    onDismiss: () -> Unit,
) {
    var body by remember { mutableStateOf(comment.body) }
    val keptIds = remember { androidx.compose.runtime.mutableStateListOf<String>().apply {
        addAll(comment.attachments.map { it.id })
    } }
    val newUris = remember { androidx.compose.runtime.mutableStateListOf<android.net.Uri>() }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents(),
    ) { uris -> newUris.addAll(uris) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forums_comment_edit_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.forums_comment_edit_body_field)) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                    Icon(
                        androidx.compose.material.icons.Icons.Default.MoreHoriz,
                        contentDescription = null,
                    )
                    Text(stringResource(R.string.forums_comment_edit_attach))
                }
                if (comment.attachments.isNotEmpty() || newUris.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        comment.attachments.forEach { att ->
                            val isKept = att.id in keptIds
                            androidx.compose.material3.FilterChip(
                                selected = isKept,
                                onClick = {
                                    if (isKept) keptIds.remove(att.id) else keptIds.add(att.id)
                                },
                                label = {
                                    Text(
                                        att.name.ifBlank { att.id }.takeLast(20),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                            )
                        }
                        newUris.forEach { uri ->
                            androidx.compose.material3.AssistChip(
                                onClick = { newUris.remove(uri) },
                                label = {
                                    Text(
                                        uri.lastPathSegment?.takeLast(20)
                                            ?: stringResource(R.string.forums_comment_edit_attach),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(body, keptIds.toList(), newUris.toList()) },
                enabled = body.isNotBlank()
            ) {
                Text(stringResource(MochiR.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDialog(
    title: String,
    onConfirm: (reason: String, details: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val reasons = listOf(
        "spam" to stringResource(R.string.forums_report_reason_spam),
        "harassment" to stringResource(R.string.forums_report_reason_harassment),
        "hate" to stringResource(R.string.forums_report_reason_hate),
        "violence" to stringResource(R.string.forums_report_reason_violence),
        "misinformation" to stringResource(R.string.forums_report_reason_misinformation),
        "offtopic" to stringResource(R.string.forums_report_reason_offtopic),
        "other" to stringResource(R.string.forums_report_reason_other),
    )
    var selectedReason by remember { mutableStateOf("spam") }
    var details by remember { mutableStateOf("") }
    var reasonExpanded by remember { mutableStateOf(false) }
    val selectedLabel = reasons.firstOrNull { it.first == selectedReason }?.second ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = reasonExpanded,
                    onExpandedChange = { reasonExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.forums_report_reason)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = reasonExpanded,
                        onDismissRequest = { reasonExpanded = false }
                    ) {
                        reasons.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedReason = code
                                    reasonExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text(stringResource(R.string.forums_report_details)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedReason, details) },
                enabled = selectedReason != "other" || details.isNotBlank()
            ) {
                Text(stringResource(R.string.forums_report_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}

/**
 * One reaction counter on the post detail: icon plus its count, always shown
 * even at zero. Tappable when [onClick] is given — only the votes are, and only
 * when the viewer has vote rights.
 */
@Composable
private fun PostReaction(
    icon: ImageVector,
    contentDescription: String,
    count: Int,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) {
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = clickable.padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = LocalFormat.current.formatNumber(count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The post's moderation and authoring actions, hosted by the screen's top bar.
 * "Quote in reply" is deliberately absent — the composer is the place to quote.
 */
@Composable
private fun PostActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    post: Post,
    canEdit: Boolean,
    canModerate: Boolean,
    isAuthor: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onApprove: () -> Unit,
    onRemove: () -> Unit,
    onRestore: () -> Unit,
    onReport: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (canEdit) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.forums_post_edit)) },
                onClick = { onDismiss(); onEdit() }
            )
        }
        if (canModerate) {
            if (post.pinned) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.forums_post_unpin)) },
                    onClick = { onDismiss(); onUnpin() }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.forums_post_pin)) },
                    onClick = { onDismiss(); onPin() }
                )
            }
            if (post.locked) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.forums_post_unlock)) },
                    onClick = { onDismiss(); onUnlock() }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.forums_post_lock)) },
                    onClick = { onDismiss(); onLock() }
                )
            }
            if (post.status == "pending") {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.forums_post_approve)) },
                    onClick = { onDismiss(); onApprove() }
                )
            }
            if (post.status == "removed") {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.forums_post_restore)) },
                    onClick = { onDismiss(); onRestore() }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.forums_post_remove)) },
                    onClick = { onDismiss(); onRemove() }
                )
            }
        }
        if (!isAuthor) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.forums_post_report)) },
                onClick = { onDismiss(); onReport() }
            )
        }
        if (canEdit) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.forums_post_delete)) },
                onClick = { onDismiss(); onDelete() }
            )
        }
    }
}

@Composable
private fun PostHeader(
    post: Post,
    canVote: Boolean,
    canEdit: Boolean,
    forumId: String,
    onVote: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onTagInterest: (qid: String, direction: String) -> Unit,
) {
    val format = LocalFormat.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
    ) {
        val postMarkdown = post.bodyMarkdown.ifBlank { post.body }
        if (postMarkdown.isNotBlank()) {
            HtmlContent(html = postMarkdown, modifier = Modifier.fillMaxWidth())
        }
        if (post.attachments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            AttachmentGallery(
                attachments = post.attachments,
                urlBuilder = { att ->
                    att.url ?: "/forums/$forumId/-/attachments/${att.id}"
                },
                thumbnailUrlBuilder = { att ->
                    att.thumbnailUrl ?: "/forums/$forumId/-/attachments/${att.id}/thumbnail"
                }
            )
            Spacer(Modifier.height(6.dp))
        }
        // Author on the left, timestamp flush to the trailing edge. The avatar
        // matches the name's line height so the two sit on one baseline.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val authorName = post.name.ifBlank {
                stringResource(R.string.forums_post_default_author)
            }
            EntityAvatar(
                name = authorName,
                src = "/forums/$forumId/-/${post.id}/asset/avatar",
                seed = post.member.ifEmpty { authorName },
                size = 24.dp,
            )
            Spacer(Modifier.width(8.dp))
            // The name takes the slack so the timestamp lands on the end edge.
            Text(
                text = authorName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            // The fuller timestamp — absolute date/time or "5m ago" per the
            // user's preference — rather than the terse relative form.
            Text(
                text = format.formatTimestamp(post.created),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        // Every counter is shown here, unlike the list card which hides the
        // empty ones: tag · like · dislike · comment. Votes are tappable only
        // when the viewer may vote; the rest are indicators.
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Shared with feeds: tag icon + count opening a popup that adds,
            // deletes and tunes interest. Delete only when the viewer may edit.
            PostTagsButton(
                tags = post.tags.map { tag ->
                    val qid = tag.qid.takeIf { value -> value.isNotBlank() }
                    TagItem(
                        id = tag.id,
                        label = tag.label,
                        qid = qid,
                        // Only entity-backed tags carry an interest weight; the
                        // model's 0f default would otherwise tint every label.
                        interest = tag.interest.takeIf { qid != null },
                    )
                },
                onAddTag = if (canEdit) onAddTag else null,
                onRemoveTag = if (canEdit) onRemoveTag else null,
                onAdjustInterest = onTagInterest,
                horizontalPadding = 4.dp,
                countStyle = MaterialTheme.typography.labelSmall,
            )
            PostReaction(
                icon = if (post.userVote == "up") Icons.Filled.ThumbUp
                       else Icons.Outlined.ThumbUp,
                contentDescription = stringResource(R.string.forums_post_vote_up),
                count = post.up,
                onClick = if (canVote) {
                    { onVote(if (post.userVote == "up") "" else "up") }
                } else {
                    null
                },
            )
            PostReaction(
                icon = if (post.userVote == "down") Icons.Filled.ThumbDown
                       else Icons.Outlined.ThumbDown,
                contentDescription = stringResource(R.string.forums_post_vote_down),
                count = post.down,
                onClick = if (canVote) {
                    { onVote(if (post.userVote == "down") "" else "down") }
                } else {
                    null
                },
            )
            PostReaction(
                icon = if (post.comments == 0) Icons.Filled.ChatBubbleOutline
                       else Icons.Filled.ChatBubble,
                contentDescription = stringResource(R.string.forums_post_comments),
                count = post.comments,
            )
        }
    }
}



private fun androidx.compose.foundation.lazy.LazyListScope.commentsItems(
    comments: List<ForumComment>,
    forumId: String,
    currentIdentity: String,
    canModerate: Boolean,
    depth: Int = 0,
    onVote: (String, String) -> Unit,
    onReply: (ForumComment) -> Unit,
    onEdit: (ForumComment) -> Unit,
    onDelete: (ForumComment) -> Unit,
    onApprove: (ForumComment) -> Unit,
    onRemove: (ForumComment) -> Unit,
    onRestore: (ForumComment) -> Unit,
    onReport: (ForumComment) -> Unit,
) {
    comments.forEach { c ->
        item(key = c.id) {
            val isAuthor = c.member == currentIdentity && currentIdentity.isNotBlank()
            val canEditThis = isAuthor || canModerate
            CommentCard(
                comment = c,
                depth = depth,
                forumId = forumId,
                canEdit = canEditThis,
                canModerate = canModerate,
                isAuthor = isAuthor,
                onVote = { vote -> onVote(c.id, vote) },
                onReply = { onReply(c) },
                onEdit = { onEdit(c) },
                onDelete = { onDelete(c) },
                onApprove = { onApprove(c) },
                onRemove = { onRemove(c) },
                onRestore = { onRestore(c) },
                onReport = { onReport(c) },
            )
        }
        if (c.children.isNotEmpty()) {
            commentsItems(c.children, forumId, currentIdentity, canModerate, depth + 1, onVote, onReply, onEdit, onDelete, onApprove, onRemove, onRestore, onReport)
        }
    }
}

/**
 * A forum comment, rendered by the shared [CommentItem] so the header, threading
 * and attachments match feeds. Only the action row differs: up/down votes, a
 * reply button, and the moderation overflow.
 */
@Composable
private fun CommentCard(
    comment: ForumComment,
    depth: Int,
    forumId: String,
    canEdit: Boolean,
    canModerate: Boolean,
    isAuthor: Boolean,
    onVote: (String) -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onApprove: () -> Unit,
    onRemove: () -> Unit,
    onRestore: () -> Unit,
    onReport: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    CommentItem(
        name = comment.name,
        body = comment.body,
        created = comment.created,
        edited = comment.edited,
        depth = depth,
        seed = comment.member,
        avatarUrl = "/forums/$forumId/-/${comment.post}/${comment.id}/asset/avatar",
        attachments = comment.attachments,
        attachmentUrl = { att -> att.url ?: "/forums/$forumId/-/attachments/${att.id}" },
        attachmentThumbnailUrl = { att ->
            att.thumbnailUrl ?: "/forums/$forumId/-/attachments/${att.id}/thumbnail"
        },
        horizontalPadding = 12.dp,
    ) {
        // Same reaction row as the post: like · dislike, filled for the viewer's
        // own vote, tappable only with vote rights.
        PostReaction(
            icon = if (comment.userVote == "up") Icons.Filled.ThumbUp
                   else Icons.Outlined.ThumbUp,
            contentDescription = stringResource(R.string.forums_post_vote_up),
            count = comment.up,
            onClick = if (comment.canVote) {
                { onVote(if (comment.userVote == "up") "" else "up") }
            } else {
                null
            },
        )
        PostReaction(
            icon = if (comment.userVote == "down") Icons.Filled.ThumbDown
                   else Icons.Outlined.ThumbDown,
            contentDescription = stringResource(R.string.forums_post_vote_down),
            count = comment.down,
            onClick = if (comment.canVote) {
                { onVote(if (comment.userVote == "down") "" else "down") }
            } else {
                null
            },
        )
        if (comment.canComment) {
            IconButton(onClick = onReply, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.Reply,
                    contentDescription = stringResource(R.string.forums_comment_reply),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = stringResource(MochiR.string.common_more_options),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (canEdit) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.forums_comment_edit)) },
                        onClick = { showMenu = false; onEdit() }
                    )
                }
                if (canModerate) {
                    if (comment.status == "pending") {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.forums_comment_approve)) },
                            onClick = { showMenu = false; onApprove() }
                        )
                    }
                    if (comment.status == "removed") {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.forums_comment_restore)) },
                            onClick = { showMenu = false; onRestore() }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.forums_comment_remove)) },
                            onClick = { showMenu = false; onRemove() }
                        )
                    }
                }
                if (!isAuthor) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.forums_comment_report)) },
                        onClick = { showMenu = false; onReport() }
                    )
                }
                if (canEdit) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.forums_comment_delete)) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplyBanner(replyTo: ForumComment?, onClear: () -> Unit) {
    if (replyTo == null) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.forums_comment_replying_to, replyTo.name),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.forums_comment_clear_reply),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}


/**
 * The provider's display name for [uri]; a `content://` path segment is an
 * opaque id, so the chip would otherwise read "image:59".
 */
@Composable
private fun rememberFileName(uri: Uri, fallback: String): String {
    val context = LocalContext.current
    return remember(uri) {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)?.let { name -> return@remember name }
            }
        }
        uri.lastPathSegment?.substringAfterLast('/') ?: fallback
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    isSending: Boolean,
    enabled: Boolean,
    attachments: List<Uri>,
    onAddAttachments: (List<Uri>) -> Unit,
    onRemoveAttachment: (Uri) -> Unit,
    onSend: () -> Unit
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris -> onAddAttachments(uris) }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (attachments.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEach { uri ->
                    val fileLabel = stringResource(R.string.forums_attachment_file)
                    AssistChip(
                        onClick = { onRemoveAttachment(uri) },
                        label = {
                            Text(
                                rememberFileName(uri, fileLabel).takeLast(20),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(
                                    R.string.forums_attachment_remove
                                ),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { filePickerLauncher.launch("*/*") },
                enabled = enabled && !isSending,
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = stringResource(R.string.forums_post_attach)
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.forums_write_comment)) },
                enabled = enabled,
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                // A body is required even when files are attached (server 400s).
                enabled = enabled && !isSending && value.isNotBlank()
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.forums_comment_send)
                    )
                }
            }
        }
    }
}
