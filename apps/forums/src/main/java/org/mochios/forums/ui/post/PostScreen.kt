package org.mochios.forums.ui.post

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.HtmlContent
import org.mochios.forums.R
import org.mochios.forums.model.ForumComment
import org.mochios.forums.model.Post
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(
    onBack: () -> Unit,
    viewModel: PostViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var draft by remember { mutableStateOf("") }
    var showDeletePostConfirm by remember { mutableStateOf(false) }
    var commentToDelete by remember { mutableStateOf<ForumComment?>(null) }
    var showEditPost by remember { mutableStateOf(false) }
    var editingComment by remember { mutableStateOf<ForumComment?>(null) }
    var showReportPost by remember { mutableStateOf(false) }
    var reportingComment by remember { mutableStateOf<ForumComment?>(null) }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.forum.name.ifBlank { stringResource(R.string.forums_loading) },
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            PostHeader(
                                post = uiState.post,
                                canVote = uiState.canVote,
                                canEdit = (uiState.post.member == uiState.identity && uiState.identity.isNotBlank())
                                    || uiState.canModerate,
                                canModerate = uiState.canModerate,
                                isAuthor = uiState.post.member == uiState.identity && uiState.identity.isNotBlank(),
                                forumId = viewModel.forumId,
                                onVote = { viewModel.votePost(it) },
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
                                onQuote = { draft = quoteText(uiState.post.bodyMarkdown.ifBlank { uiState.post.body }, draft) },
                                onAddTag = { label -> viewModel.addPostTag(label) },
                                onRemoveTag = { tagId -> viewModel.removePostTag(tagId) },
                                onTagInterest = { qid, direction -> viewModel.adjustTagInterest(qid, direction) },
                            )
                        }
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
                                onQuote = { c ->
                                    viewModel.setReplyTo(c)
                                    draft = quoteText(c.body, draft)
                                },
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PostHeader(
    post: Post,
    canVote: Boolean,
    canEdit: Boolean,
    canModerate: Boolean,
    isAuthor: Boolean,
    forumId: String,
    onVote: (String) -> Unit,
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
    onQuote: () -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onTagInterest: (qid: String, direction: String) -> Unit,
) {
    var showAddTag by remember { mutableStateOf(false) }
    val format = LocalFormat.current
    var showMenu by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            contentDescription = stringResource(MochiR.string.common_more_options)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (canEdit) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.forums_post_edit)) },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                }
                            )
                        }
                        if (canModerate) {
                            if (post.pinned) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.forums_post_unpin)) },
                                    onClick = { showMenu = false; onUnpin() }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.forums_post_pin)) },
                                    onClick = { showMenu = false; onPin() }
                                )
                            }
                            if (post.locked) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.forums_post_unlock)) },
                                    onClick = { showMenu = false; onUnlock() }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.forums_post_lock)) },
                                    onClick = { showMenu = false; onLock() }
                                )
                            }
                            if (post.status == "pending") {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.forums_post_approve)) },
                                    onClick = { showMenu = false; onApprove() }
                                )
                            }
                            if (post.status == "removed") {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.forums_post_restore)) },
                                    onClick = { showMenu = false; onRestore() }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.forums_post_remove)) },
                                    onClick = { showMenu = false; onRemove() }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.forums_post_quote)) },
                            onClick = { showMenu = false; onQuote() }
                        )
                        if (!isAuthor) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.forums_post_report)) },
                                onClick = { showMenu = false; onReport() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.forums_post_delete)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = post.name.ifBlank { stringResource(R.string.forums_post_default_author) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = format.formatRelativeTime(post.created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (post.tags.isNotEmpty() || canEdit) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    post.tags.forEach { tag ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.AssistChip(
                                onClick = { if (canEdit) onRemoveTag(tag.id) },
                                label = {
                                    Text(tag.label, style = MaterialTheme.typography.labelSmall)
                                },
                                trailingIcon = if (canEdit) {
                                    {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.forums_post_tag_remove),
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                } else null,
                            )
                            if (tag.qid.isNotBlank()) {
                                IconButton(
                                    onClick = { onTagInterest(tag.qid, "up") },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.forums_tag_interest_up),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { onTagInterest(tag.qid, "down") },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.forums_tag_interest_down),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { onTagInterest(tag.qid, "remove") },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.forums_tag_interest_remove),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (canEdit) {
                        TextButton(onClick = { showAddTag = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.forums_post_tag_add))
                        }
                    }
                }
            }
            val postMarkdown = post.bodyMarkdown.ifBlank { post.body }
            if (postMarkdown.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                HtmlContent(html = postMarkdown, modifier = Modifier.fillMaxWidth())
            }
            if (post.attachments.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                AttachmentGallery(
                    attachments = post.attachments,
                    urlBuilder = { att ->
                        att.url ?: "/forums/$forumId/-/attachments/${att.id}"
                    },
                    thumbnailUrlBuilder = { att ->
                        att.thumbnailUrl ?: "/forums/$forumId/-/attachments/${att.id}/thumbnail"
                    }
                )
            }
            if (canVote) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onVote(if (post.userVote == "up") "" else "up") }) {
                        Icon(
                            Icons.Default.ThumbUp,
                            contentDescription = stringResource(R.string.forums_post_vote_up),
                            tint = if (post.userVote == "up") MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(LocalFormat.current.formatNumber(post.up - post.down), style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { onVote(if (post.userVote == "down") "" else "down") }) {
                        Icon(
                            Icons.Default.ThumbDown,
                            contentDescription = stringResource(R.string.forums_post_vote_down),
                            tint = if (post.userVote == "down") MaterialTheme.colorScheme.error
                                  else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showAddTag) {
        AddTagDialog(
            onConfirm = { label ->
                onAddTag(label)
                showAddTag = false
            },
            onDismiss = { showAddTag = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTagDialog(
    onConfirm: (label: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forums_post_tag_add)) },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.forums_post_tag_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label.trim()) },
                enabled = label.isNotBlank(),
            ) { Text(stringResource(MochiR.string.common_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        },
    )
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
    onQuote: (ForumComment) -> Unit,
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
                onQuote = { onQuote(c) },
            )
        }
        if (c.children.isNotEmpty()) {
            commentsItems(c.children, forumId, currentIdentity, canModerate, depth + 1, onVote, onReply, onEdit, onDelete, onApprove, onRemove, onRestore, onReport, onQuote)
        }
    }
}

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
    onQuote: () -> Unit,
) {
    val format = LocalFormat.current
    var showMenu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = (depth * 12).dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.name.ifBlank { stringResource(R.string.forums_post_default_author) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = format.formatRelativeTime(comment.created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            contentDescription = stringResource(MochiR.string.common_more_options),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (canEdit) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.forums_comment_edit)) },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                }
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
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.forums_comment_quote)) },
                            onClick = { showMenu = false; onQuote() }
                        )
                        if (!isAuthor) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.forums_comment_report)) },
                                onClick = { showMenu = false; onReport() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.forums_comment_delete)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            HtmlContent(html = comment.body, modifier = Modifier.fillMaxWidth())
            if (comment.attachments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AttachmentGallery(
                    attachments = comment.attachments,
                    urlBuilder = { att ->
                        att.url ?: "/forums/$forumId/-/attachments/${att.id}"
                    },
                    thumbnailUrlBuilder = { att ->
                        att.thumbnailUrl ?: "/forums/$forumId/-/attachments/${att.id}/thumbnail"
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (comment.canVote) {
                    IconButton(
                        onClick = { onVote(if (comment.userVote == "up") "" else "up") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ThumbUp,
                            contentDescription = stringResource(R.string.forums_post_vote_up),
                            tint = if (comment.userVote == "up") MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(LocalFormat.current.formatNumber(comment.up - comment.down), style = MaterialTheme.typography.labelSmall)
                    IconButton(
                        onClick = { onVote(if (comment.userVote == "down") "" else "down") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ThumbDown,
                            contentDescription = stringResource(R.string.forums_post_vote_down),
                            tint = if (comment.userVote == "down") MaterialTheme.colorScheme.error
                                  else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (comment.canComment) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onReply) {
                        Text(
                            stringResource(R.string.forums_comment_reply),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
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
 * Quote a post or comment body into the reply composer. Mirrors web's
 * thread-detail behaviour: prefix every non-empty line with "> " and append
 * a blank line so the user can start typing immediately. When the draft is
 * already non-empty, prepend the quote above existing text.
 */
private fun quoteText(body: String, currentDraft: String): String {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return currentDraft
    val quoted = trimmed.split("\n").joinToString("\n") { line ->
        if (line.isBlank()) ">" else "> $line"
    } + "\n\n"
    return if (currentDraft.isBlank()) quoted else "$quoted$currentDraft"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    isSending: Boolean,
    enabled: Boolean,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
