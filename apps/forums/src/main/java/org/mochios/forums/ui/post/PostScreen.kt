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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.model.resolveAttachmentUrl
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.android.ui.components.ConfirmDialog
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
                                serverUrl = viewModel.serverUrl,
                                forumId = viewModel.forumId,
                                onVote = { viewModel.votePost(it) },
                                onDelete = { showDeletePostConfirm = true }
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
                                serverUrl = viewModel.serverUrl,
                                forumId = viewModel.forumId,
                                onVote = viewModel::voteComment,
                                onReply = { viewModel.setReplyTo(it) },
                                onDelete = { commentToDelete = it }
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
}

@Composable
private fun PostHeader(
    post: Post,
    canVote: Boolean,
    serverUrl: String,
    forumId: String,
    onVote: (String) -> Unit,
    onDelete: () -> Unit
) {
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
            if (post.body.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(text = post.body, style = MaterialTheme.typography.bodyLarge)
            }
            if (post.attachments.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                AttachmentGallery(
                    attachments = post.attachments,
                    urlBuilder = { att ->
                        resolveAttachmentUrl(serverUrl, att.url ?: "/forums/$forumId/-/attachments/${att.id}")
                    },
                    thumbnailUrlBuilder = { att ->
                        resolveAttachmentUrl(serverUrl, att.thumbnailUrl ?: "/forums/$forumId/-/attachments/${att.id}/thumbnail")
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
                    Text("${post.up - post.down}", style = MaterialTheme.typography.labelLarge)
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
}

private fun androidx.compose.foundation.lazy.LazyListScope.commentsItems(
    comments: List<ForumComment>,
    serverUrl: String,
    forumId: String,
    depth: Int = 0,
    onVote: (String, String) -> Unit,
    onReply: (ForumComment) -> Unit,
    onDelete: (ForumComment) -> Unit
) {
    comments.forEach { c ->
        item(key = c.id) {
            CommentCard(
                comment = c,
                depth = depth,
                serverUrl = serverUrl,
                forumId = forumId,
                onVote = { vote -> onVote(c.id, vote) },
                onReply = { onReply(c) },
                onDelete = { onDelete(c) }
            )
        }
        if (c.children.isNotEmpty()) {
            commentsItems(c.children, serverUrl, forumId, depth + 1, onVote, onReply, onDelete)
        }
    }
}

@Composable
private fun CommentCard(
    comment: ForumComment,
    depth: Int,
    serverUrl: String,
    forumId: String,
    onVote: (String) -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit
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
            Text(text = comment.body, style = MaterialTheme.typography.bodyMedium)
            if (comment.attachments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AttachmentGallery(
                    attachments = comment.attachments,
                    urlBuilder = { att ->
                        resolveAttachmentUrl(serverUrl, att.url ?: "/forums/$forumId/-/attachments/${att.id}")
                    },
                    thumbnailUrlBuilder = { att ->
                        resolveAttachmentUrl(serverUrl, att.thumbnailUrl ?: "/forums/$forumId/-/attachments/${att.id}/thumbnail")
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
                    Text("${comment.up - comment.down}", style = MaterialTheme.typography.labelSmall)
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
