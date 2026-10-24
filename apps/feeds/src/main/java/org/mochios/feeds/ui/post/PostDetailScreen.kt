// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.post

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.model.Comment
import org.mochios.android.model.Attachment
import org.mochios.android.push.VisibleEntityEffect
import org.mochios.android.ui.components.CommentThreadBack
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.findInThread
import org.mochios.android.ui.components.rememberCollapsedComments
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.android.ui.components.AttachmentLightbox
import org.mochios.android.ui.components.ComposeBar
import org.mochios.android.ui.components.ComposeBarAttachments
import org.mochios.android.ui.components.ComposeBarDefaults
import org.mochios.android.ui.components.LocationMapView
import org.mochios.android.ui.components.MentionSuggestion
import org.mochios.android.ui.components.MentionTextField
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.ui.components.PostTagsButton as SharedPostTagsButton
import org.mochios.android.ui.components.ReactionBar
import org.mochios.android.ui.components.TagItem
import org.mochios.android.ui.components.VideoEmbed
import org.mochios.android.ui.components.extractVideos
import org.mochios.android.files.rememberFileLabel
import org.mochios.android.ui.components.MochiImage
import org.mochios.android.util.shareLink
import org.mochios.android.util.webUri
import org.mochios.feeds.R
import org.mochios.feeds.model.Permissions
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.Tag
import org.mochios.feeds.ui.component.CommentItem
import org.mochios.feeds.ui.component.PostBody
import org.mochios.feeds.ui.component.PostCopies
import org.mochios.feeds.ui.component.PostHeadline
import org.mochios.feeds.ui.component.PostMediaGrid
import org.mochios.feeds.ui.component.PostPollView
import org.mochios.feeds.ui.component.PostWarning
import org.mochios.feeds.ui.component.QuoteCard
import org.mochios.feeds.ui.component.boostedFrom
import org.mochios.feeds.ui.component.hasBadges
import org.mochios.feeds.ui.component.repliesElsewhere
import org.mochios.feeds.ui.component.currentReactionType
import org.mochios.feeds.ui.component.threadRows
import org.mochios.feeds.ui.component.stripHtml
import org.mochios.feeds.ui.component.toReactionCounts
import org.mochios.android.R as MochiR
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostDetailScreen(
    onNavigateBack: () -> Unit,
    onEditPost: (feedId: String, postId: String) -> Unit,
    onNavigateToSources: (feedId: String, sourceUrl: String) -> Unit,
    viewModel: PostDetailViewModel = hiltViewModel()
) {
    // A comment on the post being read arrives on the socket; the tray
    // row for it would only repeat what is on screen.
    VisibleEntityEffect("feeds", viewModel.feedId, socketKey = viewModel.feedId)

    val post by viewModel.post.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val commentText by viewModel.commentText.collectAsState()
    val commentAttachments by viewModel.commentAttachments.collectAsState()
    val isSendingComment by viewModel.isSendingComment.collectAsState()
    val commentUpload by viewModel.commentUpload.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val editingCommentId by viewModel.editingCommentId.collectAsState()
    val editCommentText by viewModel.editCommentText.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val hosted by viewModel.hosted.collectAsState()
    val replyFrom by viewModel.replyFrom.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteCommentDialog by remember { mutableStateOf<String?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionError) {
        actionError?.let {
            snackbarHostState.showSnackbar(it.userMessage())
            viewModel.clearActionError()
        }
    }

    val resources = LocalResources.current
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(resources.getString(it))
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feeds_post), maxLines = 1) },
                navigationIcon = {
                    MochiIconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back)
                        )
                    }
                },
                actions = {
                    // Every reply and reaction in a followed post notifies; a
                    // post in a feed the user does not hold cannot be followed.
                    post?.following?.let { following ->
                        MochiIconButton(onClick = { viewModel.setFollowing(!following) }) {
                            Icon(
                                if (following) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone,
                                contentDescription = stringResource(
                                    if (following) R.string.feeds_unfollow_thread else R.string.feeds_follow_thread
                                ),
                            )
                        }
                    }
                    if (permissions.manage) {
                        MochiIconButton(onClick = { onEditPost(viewModel.feedId, viewModel.postId) }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(MochiR.string.common_edit)
                            )
                        }
                        MochiIconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(MochiR.string.common_delete)
                            )
                        }
                        // The post's source, pinning, and a boost of a post
                        // from another network from the first hosted account.
                        post?.let { current ->
                            val sourceUrl = current.source?.url?.takeIf { it.isNotEmpty() }
                            val booster = hosted.firstOrNull()?.takeIf { current.elsewhere }
                            Box {
                                MochiIconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(MochiR.string.common_more_options)
                                    )
                                }
                                MochiDropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    if (sourceUrl != null) {
                                        MochiDropdownMenuItem(
                                            text = { Text(stringResource(R.string.feeds_tab_sources)) },
                                            onClick = {
                                                showOverflowMenu = false
                                                onNavigateToSources(viewModel.feedId, sourceUrl)
                                            },
                                            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                                        )
                                    }
                                    MochiDropdownMenuItem(
                                        text = {
                                            Text(stringResource(if (current.pinned > 0) R.string.feeds_unpin_post else R.string.feeds_pin_post))
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.togglePin()
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                                    )
                                    if (booster != null) {
                                        MochiDropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (boostedFrom(current, booster)) {
                                                        stringResource(R.string.feeds_take_boost_back)
                                                    } else {
                                                        stringResource(R.string.feeds_boost_from, booster.identifier)
                                                    }
                                                )
                                            },
                                            onClick = {
                                                showOverflowMenu = false
                                                viewModel.toggleBoost()
                                            },
                                            leadingIcon = { Icon(Icons.Outlined.Repeat, contentDescription = null) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (permissions.comment) {
                ComposeBar(
                    value = commentText,
                    onValueChange = { viewModel.setCommentText(it) },
                    onSend = { viewModel.sendComment() },
                    isSending = isSendingComment,
                    upload = commentUpload,
                    sendLabel = stringResource(R.string.feeds_send),
                    windowInsets = ComposeBarDefaults.WindowInsets,
                    attachments = feedsCommentAttachments(
                        attachments = commentAttachments,
                        onAdd = { uris -> uris.forEach { viewModel.addCommentAttachment(it) } },
                        onRemove = { viewModel.removeCommentAttachment(it) },
                        resolveFileName = viewModel::fileName,
                    ),
                    // A comment needs a body; attachments alone will not do.
                    requireText = true,
                    onSearchMentions = { viewModel.searchMembers(it) },
                    banner = feedsComposerBanner(
                        replyFromBanner(
                            accounts = hosted,
                            shown = post?.let(::repliesElsewhere) == true,
                            chosen = replyFrom,
                            onChoose = { viewModel.setReplyFrom(it) },
                        ),
                        feedsReplyBanner(
                            findComment(post?.comments.orEmpty(), replyingTo),
                            { viewModel.setReplyingTo(null) },
                        ),
                    ),
                )
            }
        }
    ) { paddingValues ->
        PostDetailContent(
            viewModel = viewModel,
            onAddTag = { label -> viewModel.addTag(label, null) },
            showDeleteCommentDialog = { showDeleteCommentDialog = it },
            onBack = onNavigateBack,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }

    // Delete post dialog
    if (showDeleteDialog) {
        MochiAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = stringResource(R.string.feeds_delete_post),
            text = stringResource(R.string.feeds_delete_post_confirm),
            confirmText = stringResource(MochiR.string.common_delete),
            onConfirm = {
                showDeleteDialog = false
                viewModel.deletePost { onNavigateBack() }
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }

    // Delete comment dialog
    showDeleteCommentDialog?.let { commentId ->
        MochiAlertDialog(
            onDismissRequest = { showDeleteCommentDialog = null },
            title = stringResource(R.string.feeds_delete_comment),
            text = stringResource(R.string.feeds_delete_comment_confirm),
            confirmText = stringResource(MochiR.string.common_delete),
            onConfirm = {
                showDeleteCommentDialog = null
                viewModel.deleteComment(commentId)
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PostDetailContent(
    viewModel: PostDetailViewModel,
    onAddTag: (String) -> Unit,
    showDeleteCommentDialog: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 16.dp),
    // When false, suppress the post body and any other content the source
    // WebView already shows (RSS preview image, source link). Used by the
    // source-view sheet so the pull-up only adds value over the webview.
    showBody: Boolean = true
) {
    val post by viewModel.post.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isNotFound by viewModel.isNotFound.collectAsState()
    val editingCommentId by viewModel.editingCommentId.collectAsState()
    val editCommentText by viewModel.editCommentText.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val voting by viewModel.voting.collectAsState()
    // The comment whose author the owner is muting, blocking or reporting, and which.
    var moderating by remember { mutableStateOf<Pair<org.mochios.android.model.Comment, String>?>(null) }

    // A comment's chip opens the lightbox on the image it is about, comments
    // showing. Hosted here rather than in the gallery, whose lazy item may
    // have scrolled out of composition by the time a chip far down is tapped.
    var openAttachment by remember { mutableStateOf<String?>(null) }
    val focusedId by viewModel.focusedComment.collectAsState()
    val collapsed = rememberCollapsedComments()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val shareTitle = stringResource(MochiR.string.comment_share)

    when {
        isLoading && post == null -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        isNotFound && post == null -> {
            Box(modifier = modifier) {
                NotFoundState(
                    title = stringResource(R.string.feeds_post_not_found),
                    onBack = onBack,
                )
            }
        }

        error != null && post == null -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                ErrorState(error = error!!, onRetry = { viewModel.loadPost() })
            }
        }

        post != null -> {
            val currentPost = post!!
            // A post opened on one comment shows it alone with its replies,
            // under a way back to them all; one deleted since shows them all.
            val focused = focusedId?.let { id ->
                findInThread(currentPost.comments, id, { it.id }, { it.children })
            }
            val rows = threadRows(focused?.let { listOf(it) } ?: currentPost.comments, collapsed.ids)
            // Land on the comment rather than the top of the post: the
            // post, then the comments heading, then the way back.
            LaunchedEffect(focused?.id) {
                if (focused != null) listState.scrollToItem(2)
            }
            LazyColumn(
                modifier = modifier,
                state = listState,
                contentPadding = contentPadding
            ) {
                item(key = "post_content") {
                    PostContent(
                        post = currentPost,
                        tags = tags,
                        permissions = permissions,
                        serverUrl = viewModel.serverUrl,
                        feedId = viewModel.feedId,
                        onReact = { viewModel.reactToPost(it) },
                        onAddTag = onAddTag,
                        onAdjustInterest = { tag, direction ->
                            viewModel.adjustInterest(
                                tag,
                                direction
                            )
                        },
                        showBody = showBody,
                        voting = voting,
                        onVote = { viewModel.vote(it) },
                        commentCount = { att -> anchoredCommentCount(currentPost.comments, att.id) },
                        comments = { att ->
                            AttachmentComments(
                                post = currentPost,
                                attachmentId = att.id,
                                viewModel = viewModel,
                                canComment = permissions.comment,
                                onDeleteComment = showDeleteCommentDialog,
                            )
                        },
                    )
                }

                if (currentPost.comments.isNotEmpty()) {
                    item(key = "comments_header") {
                        Text(
                            text = stringResource(R.string.feeds_comments),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }

                    if (focused != null) {
                        item(key = "all_comments") {
                            CommentThreadBack(
                                onClick = {
                                    viewModel.openComment(null)
                                    scope.launch { listState.scrollToItem(1) }
                                },
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                    items(rows.size, key = { rows[it].comment.id }) { index ->
                        val row = rows[index]
                        val comment = row.comment
                        CommentItem(
                            comment = comment,
                            depth = row.depth,
                            avatarUrl = "/feeds/${viewModel.feedId}/-/${viewModel.postId}/${comment.id}/asset/avatar",
                            feedId = viewModel.feedId,
                            isEditing = editingCommentId == comment.id,
                            editText = if (editingCommentId == comment.id) editCommentText else "",
                            onEditTextChange = { viewModel.setEditCommentText(it) },
                            onSaveEdit = { viewModel.saveEditComment() },
                            onCancelEdit = { viewModel.cancelEditComment() },
                            onReply = { viewModel.setReplyingTo(comment.id) },
                            onEdit = {
                                viewModel.startEditComment(
                                    comment.id,
                                    stripHtml(comment.text)
                                )
                            },
                            onDelete = { showDeleteCommentDialog(comment.id) },
                            onReact = { reaction ->
                                viewModel.reactToComment(
                                    comment.id,
                                    reaction
                                )
                            },
                            canManage = permissions.manage,
                            isMine = currentUserId != null && comment.authorId == currentUserId,
                            onOpenAttachment = { openAttachment = it },
                            onModerate = if (permissions.manage) ({ action -> moderating = comment to action }) else null,
                            onShare = {
                                shareLink(
                                    context,
                                    viewModel.commentLink(comment.id),
                                    currentPost.feedName.ifEmpty { shareTitle },
                                )
                            },
                            replies = row.replies,
                            collapsed = comment.id in collapsed,
                            onToggleCollapse = if (row.replies > 0) ({ collapsed.toggle(comment.id) }) else null,
                            onCollapseParent = row.parent?.let { parent -> { collapsed.collapse(parent) } },
                            highlighted = comment.id == focused?.id,
                        )
                    }
                }
            }

            val images = currentPost.attachments.filter { it.isImage }
            val openIndex = images.indexOfFirst { it.id == openAttachment }
            if (openIndex >= 0) {
                val attachmentFeed = currentPost.feed.ifEmpty { viewModel.feedId }
                AttachmentLightbox(
                    images = images,
                    urlBuilder = { att -> att.url ?: "/feeds/$attachmentFeed/-/attachments/${att.id}" },
                    initialIndex = openIndex,
                    onDismiss = { openAttachment = null },
                    commentCount = { att -> anchoredCommentCount(currentPost.comments, att.id) },
                    comments = { att ->
                        AttachmentComments(
                            post = currentPost,
                            attachmentId = att.id,
                            viewModel = viewModel,
                            canComment = permissions.comment,
                            onDeleteComment = showDeleteCommentDialog,
                        )
                    },
                    commentsInitiallyOpen = true,
                )
            }

            moderating?.let { (comment, action) ->
                CommenterDialog(
                    action = action,
                    author = comment.name.ifEmpty { comment.authorId },
                    onDismiss = { moderating = null },
                    onConfirm = { reason ->
                        moderating = null
                        viewModel.moderate(comment.id, action, reason)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostContent(
    post: Post,
    tags: List<Tag>,
    permissions: Permissions,
    serverUrl: String,
    feedId: String,
    onReact: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onAdjustInterest: (Tag, String) -> Unit,
    showBody: Boolean = true,
    voting: Boolean = false,
    onVote: (List<Int>) -> Unit = {},
    // The lightbox comments slot, per image attachment.
    commentCount: ((Attachment) -> Int)? = null,
    comments: (@Composable (Attachment) -> Unit)? = null,
) {
    val context = LocalContext.current
    // A post behind a content warning shows its warning until the reader asks
    // for the rest.
    var revealed by rememberSaveable(post.id) { mutableStateOf(false) }
    val hidden = post.warning.isNotEmpty() && !revealed

    Column(modifier = Modifier.padding(16.dp)) {
        // Author/source + time
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val defaultAuthor = stringResource(R.string.feeds_post_default_author)
            val authorName = post.source?.name?.takeIf { it.isNotEmpty() }
                ?: post.feedName.takeIf { it.isNotEmpty() }
                ?: defaultAuthor
            Text(
                text = authorName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = LocalFormat.current.formatTimestamp(post.created),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Memory badge
        post.data?.memory?.let { memory ->
            if (memory.yearsAgo > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.feeds_memory_years_ago_today,
                        memory.yearsAgo,
                        LocalFormat.current.formatNumber(memory.yearsAgo)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Location
        post.data?.checkin?.let { checkin ->
            if (checkin.name.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.feeds_location_at, checkin.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (checkin.city.isNotEmpty() || checkin.country.isNotEmpty()) {
                    Text(
                        text = listOfNotNull(
                            checkin.city.takeIf { it.isNotEmpty() },
                            checkin.state.takeIf { it.isNotEmpty() },
                            checkin.country.takeIf { it.isNotEmpty() }
                        ).joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        post.data?.travelling?.let { travelling ->
            val origin = travelling.origin
            val destination = travelling.destination
            if (origin != null || destination != null) {
                Spacer(modifier = Modifier.height(4.dp))
                if (origin != null && destination != null) {
                    Text(
                        text = stringResource(
                            R.string.feeds_travel_arrow,
                            origin.name,
                            destination.name
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (origin != null) {
                    Text(
                        text = stringResource(R.string.feeds_travel_from, origin.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (destination != null) {
                    Text(
                        text = stringResource(R.string.feeds_travel_to, destination.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Location map
        val checkinWithCoords = post.data?.checkin?.takeIf { it.lat != 0.0 || it.lon != 0.0 }
        val travellingWithCoords = post.data?.travelling?.takeIf {
            (it.origin?.lat != 0.0 || it.origin?.lon != 0.0) &&
                    (it.destination?.lat != 0.0 || it.destination?.lon != 0.0)
        }
        if (checkinWithCoords != null || travellingWithCoords != null) {
            Spacer(modifier = Modifier.height(8.dp))
            LocationMapView(
                checkin = checkinWithCoords,
                origin = travellingWithCoords?.origin,
                destination = travellingWithCoords?.destination
            )
        }

        if (showBody) {
            if (post.data?.person != null || post.data?.boost != null || hasBadges(post)) {
                Spacer(modifier = Modifier.height(8.dp))
                PostHeadline(post)
            }
            if (post.warning.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                PostWarning(warning = post.warning, revealed = revealed, onToggle = { revealed = !revealed })
            }
            if (!hidden && (post.copies.isNotEmpty() || post.boosts > 0)) {
                Spacer(modifier = Modifier.height(4.dp))
                PostCopies(copies = post.copies, boosts = post.boosts)
            }
            post.data?.quote?.takeIf { !hidden }?.let { quote ->
                Spacer(modifier = Modifier.height(8.dp))
                QuoteCard(quote)
            }
        }

        // RSS-source posts: a body tap opens the article. Body, videos, preview
        // image and source link are hidden inside the source-view sheet, where
        // the WebView already shows the article.
        val sourceArticleUrl = post.data?.rss?.link?.let { webUri(it) }
        val onBodyClick: (() -> Unit)? = sourceArticleUrl?.let { uri ->
            {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: Exception) { /* no browser */
                }
            }
        }
        if (showBody && !hidden && post.body.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            PostBody(
                post = post,
                modifier = Modifier.fillMaxWidth(),
                onClick = onBodyClick
            )
        }

        if (showBody && !hidden) {
            val videos = remember(post.body) { extractVideos(post.body) }
            videos.forEach { video ->
                Spacer(modifier = Modifier.height(8.dp))
                VideoEmbed(video = video)
            }
            post.data?.media?.takeIf { it.isNotEmpty() }?.let { media ->
                Spacer(modifier = Modifier.height(8.dp))
                PostMediaGrid(media = media, sensitive = post.sensitive != 0)
            }
            post.data?.poll?.let { poll ->
                Spacer(modifier = Modifier.height(8.dp))
                PostPollView(
                    poll = poll,
                    voted = post.voted,
                    canVote = permissions.react || permissions.comment || permissions.manage,
                    busy = voting,
                    onVote = onVote,
                )
            }
        }

        // Attachments
        if (!hidden && post.attachments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            val attachmentFeed = post.feed.ifEmpty { feedId }
            AttachmentGallery(
                attachments = post.attachments,
                urlBuilder = { att ->
                    att.url ?: "/feeds/$attachmentFeed/-/attachments/${att.id}"
                },
                thumbnailUrlBuilder = { att ->
                    att.thumbnailUrl ?: "/feeds/$attachmentFeed/-/attachments/${att.id}/thumbnail"
                },
                // previewUrl is absent on servers that predate the preview
                // variant; falling back to the thumbnail chain keeps working.
                previewUrlBuilder = { att ->
                    att.previewUrl
                        ?: att.thumbnailUrl
                        ?: "/feeds/$attachmentFeed/-/attachments/${att.id}/thumbnail"
                },
                commentCount = commentCount,
                comments = comments,
            )
        }

        if (showBody) {
            // RSS preview image. Tapping opens the source article when present.
            // Use ContentScale.Fit so the full image shows at natural aspect
            // ratio — Crop with a fixed max-height was clipping landscape and
            // tall portraits alike.
            post.data?.rss?.image?.takeIf { it.isNotEmpty() }?.let { imageUrl ->
                Spacer(modifier = Modifier.height(12.dp))
                MochiImage(
                    model = imageUrl,
                    contentDescription = stringResource(R.string.feeds_image_preview),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                    shape = RoundedCornerShape(8.dp),
                    onClick = onBodyClick,
                )
            }
        }

        if (showBody) {
            post.source?.let { source ->
                if (source.url.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = source.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            webUri(source.url)?.let { uri ->
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                } catch (_: Exception) {
                                    // no browser
                                }
                            }
                        }
                    )
                }
            }
        }

        // Reaction + tag grouped on the leading edge, matching the feed card's
        // action bar. In the source sheet (showBody = false) the tags button
        // lives in the sheet header instead, so it stays reachable from the peek
        // without expanding.
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReactionBar(
                reactions = toReactionCounts(post.reactions, post.myReaction),
                onReact = onReact,
                onRemoveReaction = { onReact(post.myReaction) },
                currentReaction = currentReactionType(post.myReaction),
            )
            if (showBody) {
                // The reaction add button is a filled circle, so its padding
                // sits inside the background; this spacer makes the gap to the
                // tag match the feed's action bar (≈16dp).
                Spacer(modifier = Modifier.width(8.dp))
                PostTagsButton(
                    tags = tags,
                    onAddTag = onAddTag,
                    onAdjustInterest = onAdjustInterest,
                    horizontalPadding = 8.dp,
                    iconSize = 24.dp,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
    }
}

// Web-parity tags affordance, rendered by the shared [SharedPostTagsButton]:
// a tag icon + count opening a popup with per-tag interest tuning and an
// "Add tag" action. Feeds has no per-tag delete, so none is passed.
@Composable
internal fun PostTagsButton(
    tags: List<Tag>,
    onAddTag: (String) -> Unit,
    onAdjustInterest: (Tag, String) -> Unit,
    horizontalPadding: Dp = 6.dp,
    iconSize: Dp = 18.dp,
) {
    val items = tags.map { tag ->
        TagItem(
            id = tag.id,
            label = tag.label,
            qid = tag.qid,
            interest = tag.interest?.toFloat(),
        )
    }
    SharedPostTagsButton(
        tags = items,
        onAddTag = onAddTag,
        onAdjustInterest = { qid, direction ->
            tags.firstOrNull { tag -> tag.qid == qid }?.let { tag ->
                onAdjustInterest(tag, direction)
            }
        },
        horizontalPadding = horizontalPadding,
        iconSize = iconSize,
    )
}

