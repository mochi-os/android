// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.post

import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Restore
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.model.Attachment
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.android.ui.components.AttachmentLightbox
import org.mochios.android.ui.components.CommentItem
import org.mochios.android.ui.components.ComposeBar
import org.mochios.android.ui.components.ComposeBarAttachments
import org.mochios.android.ui.components.ComposeBarDefaults
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.HtmlContent
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.ui.components.PostTagsButton
import org.mochios.android.ui.components.StatusBadgeSize
import org.mochios.android.ui.components.TagItem
import org.mochios.android.files.rememberFileLabel
import org.mochios.forums.R
import org.mochios.forums.model.ForumComment
import org.mochios.forums.model.countComments
import org.mochios.forums.model.Post
import org.mochios.forums.ui.components.PostBadges
import org.mochios.forums.model.Tag
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(
    onBack: () -> Unit,
    onEditPost: (forumId: String, postId: String) -> Unit,
    viewModel: PostViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val commentAttachments by viewModel.commentAttachments.collectAsState()
    // A TextFieldValue rather than a String: quoting has to place the caret, and
    // only the value carries a selection.
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var showDeletePostConfirm by remember { mutableStateOf(false) }
    var commentToDelete by remember { mutableStateOf<ForumComment?>(null) }
    var editingComment by remember { mutableStateOf<ForumComment?>(null) }
    var showReportPost by remember { mutableStateOf(false) }
    var reportingComment by remember { mutableStateOf<ForumComment?>(null) }
    var showPostMenu by remember { mutableStateOf(false) }
    val isPostAuthor = uiState.post.member == uiState.identity && uiState.identity.isNotBlank()

    val composerFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Quote and Reply both hand the conversation to the composer, so put the
    // cursor in it and raise the keyboard. Guarded on canComment: without it the
    // composer is not in the tree and the requester has nothing to focus.
    val focusComposer = {
        if (uiState.canComment && !uiState.post.locked) {
            composerFocus.requestFocus()
            keyboard?.show()
        }
    }

    // Quote the given body into the draft and park the caret at the end — past the
    // quote block, on the blank line the user is meant to type on.
    val quoteIntoDraft = { body: String ->
        val text = quoteText(body, draft.text)
        draft = TextFieldValue(text = text, selection = TextRange(text.length))
        focusComposer()
    }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBack()
    }

    // A comment's chip opens the lightbox on the image it is about, comments
    // showing. Hosted at screen level rather than in the gallery, whose lazy
    // item may have scrolled out of composition by the time a chip is tapped.
    var openAttachment by remember { mutableStateOf<String?>(null) }

    // The per-comment actions, shared by the thread below the post and by the
    // lightbox panel, so a comment behaves the same wherever it is drawn.
    val commentActions = CommentActions(
        onVote = viewModel::voteComment,
        onReply = { comment ->
            viewModel.setReplyTo(comment)
            focusComposer()
        },
        onEdit = { editingComment = it },
        onDelete = { commentToDelete = it },
        onApprove = { viewModel.approveComment(it.id) },
        onRemove = { viewModel.removeComment(it.id) },
        onRestore = { viewModel.restoreComment(it.id) },
        onReport = { reportingComment = it },
        // Quoting cites a comment; it does not thread under it. Only Reply sets
        // the parent, so the viewer can reply to one comment while quoting
        // another. Cite the author's own words, not the quote they were
        // themselves replying to.
        onQuote = { comment -> quoteIntoDraft(withoutQuote(comment.body)) },
        onOpenAttachment = { openAttachment = it },
    )
    val attachmentPanel: @Composable (Attachment) -> Unit = { att ->
        AttachmentComments(
            attachmentId = att.id,
            comments = uiState.comments,
            forumId = viewModel.forumId,
            currentIdentity = uiState.identity,
            canModerate = uiState.canModerate,
            actions = commentActions,
            canComment = uiState.canComment && !uiState.post.locked,
            replyTo = uiState.replyTo,
            onClearReply = { viewModel.setReplyTo(null) },
            isSending = uiState.isSending,
            attachments = commentAttachments,
            onAddAttachments = { uris -> viewModel.addCommentAttachments(uris) },
            onRemoveAttachment = { uri -> viewModel.removeCommentAttachment(uri) },
            resolveFileName = viewModel::fileName,
            onSend = { text -> viewModel.submitComment(text, anchor = att.id) },
        )
    }
    val attachmentCommentCount: (Attachment) -> Int = { att ->
        countComments(uiState.comments.filter { it.anchor == att.id })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // The post's own title — the forum name is already implied by
                    // where the user came from. Pinned and locked lead it, the
                    // same pairing the list card uses; locked is also why the
                    // composer below is disabled.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.post.pinned) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = stringResource(
                                    R.string.forums_post_pinned
                                ),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        if (uiState.post.locked) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = stringResource(
                                    R.string.forums_post_locked
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = uiState.post.title.ifBlank {
                                stringResource(R.string.forums_loading)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
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
                            MochiIconButton(onClick = { showPostMenu = true }) {
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
                                onEdit = { onEditPost(viewModel.forumId, viewModel.postId) },
                                onDelete = { showDeletePostConfirm = true },
                                onPin = viewModel::pinPost,
                                onUnpin = viewModel::unpinPost,
                                onLock = viewModel::lockPost,
                                onUnlock = viewModel::unlockPost,
                                onApprove = viewModel::approvePost,
                                onRemove = viewModel::removePost,
                                onRestore = viewModel::restorePost,
                                onReport = { showReportPost = true },
                                onQuote = {
                                    quoteIntoDraft(
                                        uiState.post.bodyMarkdown.ifBlank { uiState.post.body }
                                    )
                                },
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        // Consume as well as pad: the composer at the foot of this Column
        // consumes the navigation-bar inset itself, and would double it if
        // this padding did not mark it spent.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding)
                .padding(padding),
        ) {
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
                    ErrorState(
                        error = uiState.error!!,
                        onRetry = viewModel::load,
                    )
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
                                onComment = if (uiState.canComment && !uiState.post.locked) {
                                    focusComposer
                                } else {
                                    null
                                },
                                onAddTag = { label -> viewModel.addPostTag(label) },
                                onRemoveTag = { tagId -> viewModel.removePostTag(tagId) },
                                onTagInterest = { qid, direction -> viewModel.adjustTagInterest(qid, direction) },
                                commentCount = attachmentCommentCount,
                                comments = attachmentPanel,
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
                                actions = commentActions,
                            )
                        }
                    }
                    if (uiState.canComment) {
                        ReplyBanner(uiState.replyTo, onClear = { viewModel.setReplyTo(null) })
                        ComposeBar(
                            value = draft,
                            onValueChange = { value -> draft = value },
                            onSend = {
                                viewModel.submitComment(draft.text)
                                draft = TextFieldValue("")
                            },
                            placeholder = stringResource(R.string.forums_write_comment),
                            enabled = !uiState.post.locked,
                            isSending = uiState.isSending,
                            sendLabel = stringResource(R.string.forums_comment_send),
                            windowInsets = ComposeBarDefaults.WindowInsets,
                            attachments = ComposeBarAttachments(
                                pending = commentAttachments,
                                onAdd = { uris -> viewModel.addCommentAttachments(uris) },
                                onRemove = { uri -> viewModel.removeCommentAttachment(uri) },
                                resolveFileName = viewModel::fileName,
                                addLabel = stringResource(R.string.forums_post_attach),
                                fallbackLabel = stringResource(R.string.forums_attachment_file),
                                removeLabel = stringResource(R.string.forums_attachment_remove),
                            ),
                            requireText = true,
                            focusRequester = composerFocus,
                        )
                    }
                }
            }
        }
    }

    val images = uiState.post.attachments.filter { it.isImage }
    val openIndex = images.indexOfFirst { it.id == openAttachment }
    if (openIndex >= 0) {
        AttachmentLightbox(
            images = images,
            urlBuilder = { att -> att.url ?: "/forums/${viewModel.forumId}/-/attachments/${att.id}" },
            initialIndex = openIndex,
            onDismiss = { openAttachment = null },
            commentCount = attachmentCommentCount,
            comments = attachmentPanel,
            commentsInitiallyOpen = true,
        )
    }

    if (showDeletePostConfirm) {
        MochiAlertDialog(
            onDismissRequest = { showDeletePostConfirm = false },
            title = stringResource(R.string.forums_post_delete_title),
            text = stringResource(R.string.forums_post_delete_message),
            confirmText = stringResource(R.string.forums_post_delete),
            onConfirm = {
                showDeletePostConfirm = false
                viewModel.deletePost()
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }

    commentToDelete?.let { c ->
        MochiAlertDialog(
            onDismissRequest = { commentToDelete = null },
            title = stringResource(R.string.forums_comment_delete_title),
            text = stringResource(R.string.forums_comment_delete_message),
            confirmText = stringResource(R.string.forums_comment_delete),
            onConfirm = {
                viewModel.deleteComment(c.id)
                commentToDelete = null
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }

    editingComment?.let { c ->
        val ctx = androidx.compose.ui.platform.LocalContext.current
        EditCommentDialog(
            comment = c,
            onConfirm = { body, keptIds, newUris ->
                viewModel.editCommentWithAttachments(
                    c.id, body, keptIds, newUris, ctx
                )
                editingComment = null
            },
            onDismiss = { editingComment = null },
            resolveFileName = viewModel::fileName,
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EditCommentDialog(
    comment: ForumComment,
    onConfirm: (body: String, keptAttachmentIds: List<String>, newUris: List<android.net.Uri>) -> Unit,
    onDismiss: () -> Unit,
    resolveFileName: suspend (Uri) -> String,
) {
    var body by remember { mutableStateOf(comment.body) }
    val keptIds = remember { androidx.compose.runtime.mutableStateListOf<String>().apply {
        addAll(comment.attachments.map { it.id })
    } }
    val newUris = remember { androidx.compose.runtime.mutableStateListOf<android.net.Uri>() }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents(),
    ) { uris -> newUris.addAll(uris) }

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.forums_comment_edit_title),
        content = {
            Column {
                MochiTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.forums_comment_edit_body_field)) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                MochiIconButton(onClick = { filePickerLauncher.launch("*/*") }) {
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
                            val label = rememberFileName(
                                uri,
                                stringResource(R.string.forums_comment_edit_attach),
                                resolveFileName,
                            )
                            androidx.compose.material3.AssistChip(
                                onClick = { newUris.remove(uri) },
                                label = {
                                    Text(
                                        label,
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
        confirmText = stringResource(MochiR.string.common_save),
        onConfirm = { onConfirm(body, keptIds.toList(), newUris.toList()) },
        confirmEnabled = body.isNotBlank(),
        dismissText = stringResource(MochiR.string.common_cancel),
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

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = reasonExpanded,
                    onExpandedChange = { reasonExpanded = it }
                ) {
                    MochiTextField(
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
                            MochiDropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedReason = code
                                    reasonExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                MochiTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text(stringResource(R.string.forums_report_details)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmText = stringResource(R.string.forums_report_submit),
        onConfirm = { onConfirm(selectedReason, details) },
        confirmEnabled = selectedReason != "other" || details.isNotBlank(),
        dismissText = stringResource(MochiR.string.common_cancel),
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
    onQuote: () -> Unit,
) {
    MochiDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (canEdit) {
            MochiDropdownMenuItem(
                text = { Text(stringResource(R.string.forums_post_edit)) },
                onClick = { onDismiss(); onEdit() },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            )
        }
        if (canModerate) {
            if (post.pinned) {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.forums_post_unpin)) },
                    onClick = { onDismiss(); onUnpin() },
                    leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                )
            } else {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.forums_post_pin)) },
                    onClick = { onDismiss(); onPin() },
                    leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                )
            }
            if (post.locked) {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.forums_post_unlock)) },
                    onClick = { onDismiss(); onUnlock() },
                    leadingIcon = { Icon(Icons.Outlined.LockOpen, contentDescription = null) },
                )
            } else {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.forums_post_lock)) },
                    onClick = { onDismiss(); onLock() },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                )
            }
            if (post.status == "pending") {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.forums_post_approve)) },
                    onClick = { onDismiss(); onApprove() },
                    leadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                )
            }
            if (post.status == "removed") {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.forums_post_restore)) },
                    onClick = { onDismiss(); onRestore() },
                    leadingIcon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                )
            } else {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.forums_post_remove)) },
                    onClick = { onDismiss(); onRemove() },
                    leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                )
            }
        }
        MochiDropdownMenuItem(
            text = { Text(stringResource(R.string.forums_post_quote)) },
            onClick = { onDismiss(); onQuote() },
            leadingIcon = { Icon(Icons.Outlined.FormatQuote, contentDescription = null) },
        )
        if (!isAuthor) {
            MochiDropdownMenuItem(
                text = { Text(stringResource(R.string.forums_post_report)) },
                onClick = { onDismiss(); onReport() },
                leadingIcon = { Icon(Icons.Outlined.Report, contentDescription = null) },
            )
        }
        if (canEdit) {
            MochiDropdownMenuItem(
                text = { Text(stringResource(R.string.forums_post_delete)) },
                onClick = { onDismiss(); onDelete() },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
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
    onComment: (() -> Unit)?,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onTagInterest: (qid: String, direction: String) -> Unit,
    // The lightbox comments slot, per image attachment.
    commentCount: ((Attachment) -> Int)? = null,
    comments: (@Composable (Attachment) -> Unit)? = null,
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
                },
                // previewUrl is absent on servers that predate the preview
                // variant; falling back to the thumbnail chain keeps working.
                previewUrlBuilder = { att ->
                    att.previewUrl
                        ?: att.thumbnailUrl
                        ?: "/forums/$forumId/-/attachments/${att.id}/thumbnail"
                },
                commentCount = commentCount,
                comments = comments,
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
            // Unlike the votes, this counter is a way in: tapping it puts the
            // cursor in the composer, the way the comment icon does elsewhere.
            // Tappable only when the viewer may actually comment.
            PostReaction(
                icon = if (post.comments == 0) Icons.Filled.ChatBubbleOutline
                       else Icons.Filled.ChatBubble,
                contentDescription = stringResource(R.string.forums_post_comments),
                count = post.comments,
                onClick = onComment,
            )
            // Status closes the row on the trailing edge, a size up from the
            // list card since this is the post's own screen.
            Spacer(Modifier.weight(1f))
            PostBadges(status = post.status, size = StatusBadgeSize.Regular)
        }
    }
}

/** What a comment can do, wherever it is drawn: the thread and the lightbox panel share one set. */
private data class CommentActions(
    val onVote: (String, String) -> Unit,
    val onReply: (ForumComment) -> Unit,
    val onEdit: (ForumComment) -> Unit,
    val onDelete: (ForumComment) -> Unit,
    val onApprove: (ForumComment) -> Unit,
    val onRemove: (ForumComment) -> Unit,
    val onRestore: (ForumComment) -> Unit,
    val onReport: (ForumComment) -> Unit,
    val onQuote: (ForumComment) -> Unit,
    val onOpenAttachment: (String) -> Unit,
)

private fun androidx.compose.foundation.lazy.LazyListScope.commentsItems(
    comments: List<ForumComment>,
    forumId: String,
    currentIdentity: String,
    canModerate: Boolean,
    actions: CommentActions,
    depth: Int = 0,
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
                onVote = { vote -> actions.onVote(c.id, vote) },
                onReply = { actions.onReply(c) },
                onEdit = { actions.onEdit(c) },
                onDelete = { actions.onDelete(c) },
                onApprove = { actions.onApprove(c) },
                onRemove = { actions.onRemove(c) },
                onRestore = { actions.onRestore(c) },
                onReport = { actions.onReport(c) },
                onQuote = { actions.onQuote(c) },
                onOpenAttachment = actions.onOpenAttachment,
            )
        }
        if (c.children.isNotEmpty()) {
            commentsItems(c.children, forumId, currentIdentity, canModerate, actions, depth + 1)
        }
    }
}

/**
 * The comment thread for one image, shown in the lightbox's comments panel.
 *
 * Comments are one thread per post; a comment may be ANCHORED to one of the
 * post's attachments. This panel renders the post's REAL comments - the same
 * [CommentCard] the post screen draws, with replies, votes, editing, deletion
 * and moderation intact - filtered to the ones anchored to the image being
 * viewed, offers the rest of the thread behind a toggle, and writes new
 * comments in the same [ComposerBar] as the screen - attachments and all -
 * anchored to this image without the writer having to say so.
 */
@Composable
private fun AttachmentComments(
    attachmentId: String,
    comments: List<ForumComment>,
    forumId: String,
    currentIdentity: String,
    canModerate: Boolean,
    actions: CommentActions,
    canComment: Boolean,
    replyTo: ForumComment?,
    onClearReply: () -> Unit,
    isSending: Boolean,
    attachments: List<Uri>,
    onAddAttachments: (List<Uri>) -> Unit,
    onRemoveAttachment: (Uri) -> Unit,
    resolveFileName: suspend (Uri) -> String,
    onSend: (String) -> Unit,
) {
    var showAll by rememberSaveable(attachmentId) { mutableStateOf(false) }
    // The panel keeps its own draft. It used to share the post screen's, so a
    // half-typed comment below the post reappeared in the lightbox and the two
    // fields fought over one string. Keyed on the attachment: moving to another
    // image starts a new comment, which is what the anchor means.
    var draft by remember(attachmentId) { mutableStateOf(TextFieldValue("")) }
    // Anchors live on top-level comments; a reply inherits its parent's context.
    val anchored = comments.filter { it.anchor == attachmentId }
    val others = comments.size - anchored.size
    val shown = if (showAll) comments else anchored
    val composerFocus = remember { FocusRequester() }
    // Everything else a comment can do is the same here as below the post;
    // only Quote differs, because it writes into a draft and this panel's is
    // no longer the post screen's.
    val panelActions = actions.copy(
        onQuote = { comment ->
            val text = quoteText(withoutQuote(comment.body), draft.text)
            draft = TextFieldValue(text = text, selection = TextRange(text.length))
            composerFocus.requestFocus()
        },
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(MochiR.string.lightbox_comments_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            commentsItems(
                comments = shown,
                forumId = forumId,
                currentIdentity = currentIdentity,
                canModerate = canModerate,
                actions = panelActions,
            )
            if (others > 0) {
                item(key = "toggle") {
                    MochiTextButton(onClick = { showAll = !showAll }) {
                        Text(
                            if (showAll) stringResource(MochiR.string.lightbox_comments_only)
                            else pluralStringResource(MochiR.plurals.lightbox_comments_others, others, others)
                        )
                    }
                }
            }
        }
        if (canComment) {
            ReplyBanner(replyTo, onClear = onClearReply)
            ComposeBar(
                value = draft,
                onValueChange = { draft = it },
                onSend = {
                    onSend(draft.text)
                    draft = TextFieldValue("")
                },
                placeholder = stringResource(MochiR.string.lightbox_comment_placeholder),
                isSending = isSending,
                sendLabel = stringResource(R.string.forums_comment_send),
                attachments = ComposeBarAttachments(
                    pending = attachments,
                    onAdd = onAddAttachments,
                    onRemove = onRemoveAttachment,
                    resolveFileName = resolveFileName,
                    addLabel = stringResource(R.string.forums_post_attach),
                    fallbackLabel = stringResource(R.string.forums_attachment_file),
                    removeLabel = stringResource(R.string.forums_attachment_remove),
                ),
                requireText = true,
                focusRequester = composerFocus,
                windowInsets = ComposeBarDefaults.NoWindowInsets,
            )
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
    onQuote: () -> Unit,
    onOpenAttachment: (String) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val anchor = comment.anchor
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
        anchorThumbnailUrl = anchor.takeIf { it.isNotEmpty() }
            ?.let { "/forums/$forumId/-/attachments/$it/thumbnail" },
        anchorCaption = comment.attachmentCaption.orEmpty(),
        onOpenAnchor = anchor.takeIf { it.isNotEmpty() }?.let { { onOpenAttachment(it) } },
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
            MochiIconButton(onClick = onReply, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.Reply,
                    contentDescription = stringResource(R.string.forums_comment_reply),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            MochiIconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = stringResource(MochiR.string.common_more_options),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MochiDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (canEdit) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.forums_comment_edit)) },
                        onClick = { showMenu = false; onEdit() },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    )
                }
                if (canModerate) {
                    if (comment.status == "pending") {
                        MochiDropdownMenuItem(
                            text = { Text(stringResource(R.string.forums_comment_approve)) },
                            onClick = { showMenu = false; onApprove() },
                            leadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                        )
                    }
                    if (comment.status == "removed") {
                        MochiDropdownMenuItem(
                            text = { Text(stringResource(R.string.forums_comment_restore)) },
                            onClick = { showMenu = false; onRestore() },
                            leadingIcon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                        )
                    } else {
                        MochiDropdownMenuItem(
                            text = { Text(stringResource(R.string.forums_comment_remove)) },
                            onClick = { showMenu = false; onRemove() },
                            leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                        )
                    }
                }
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.forums_comment_quote)) },
                    onClick = { showMenu = false; onQuote() },
                    leadingIcon = { Icon(Icons.Outlined.FormatQuote, contentDescription = null) },
                )
                if (!isAuthor) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.forums_comment_report)) },
                        onClick = { showMenu = false; onReport() },
                        leadingIcon = { Icon(Icons.Outlined.Report, contentDescription = null) },
                    )
                }
                if (canEdit) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.forums_comment_delete)) },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
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
        MochiIconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.forums_comment_clear_reply),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * A comment's own words, with any quote it was itself citing dropped.
 *
 * A quote is stored as a leading "> " block in the body, so quoting such a
 * comment verbatim would nest the older quote inside the new one — and every
 * further round would drag the whole chain along. A post never needs this: its
 * body is the root of the thread and cites nothing.
 *
 * Only a *leading* block is dropped. A blockquote further down is something the
 * author wrote into their own text, and it stays.
 *
 * Falls back to the untouched body when the comment is nothing but a quote —
 * there are no original words to cite, so citing what they cited beats a menu
 * item that silently does nothing.
 */
private fun withoutQuote(body: String): String {
    val lines = body.split("\n")
    if (lines.firstOrNull()?.startsWith(">") != true) return body
    val own = lines.dropWhile { line -> line.startsWith(">") }
        .dropWhile { line -> line.isBlank() }
        .joinToString("\n")
    return own.ifBlank { body }
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

/**
 * The provider's display name for [uri]. A `content://` path segment is an
 * opaque id, so the picker's chip would otherwise read "1000000042".
 */
@Composable
private fun rememberFileName(
    uri: Uri,
    fallback: String,
    resolve: suspend (Uri) -> String,
): String = rememberFileLabel(uri, resolve, fallback)

