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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import coil3.compose.AsyncImage
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.model.Comment
import org.mochios.android.model.Attachment
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
import org.mochios.android.util.webUri
import org.mochios.feeds.R
import org.mochios.feeds.model.Permissions
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.Tag
import org.mochios.feeds.ui.component.CommentItem
import org.mochios.feeds.ui.component.PostBody
import org.mochios.feeds.ui.component.currentReactionType
import org.mochios.feeds.ui.component.flattenComments
import org.mochios.feeds.ui.component.stripHtml
import org.mochios.feeds.ui.component.toReactionCounts
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostDetailScreen(
    onNavigateBack: () -> Unit,
    onEditPost: (feedId: String, postId: String) -> Unit,
    onNavigateToSources: (feedId: String, sourceUrl: String) -> Unit,
    viewModel: PostDetailViewModel = hiltViewModel()
) {
    val post by viewModel.post.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val commentText by viewModel.commentText.collectAsState()
    val commentAttachments by viewModel.commentAttachments.collectAsState()
    val isSendingComment by viewModel.isSendingComment.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val editingCommentId by viewModel.editingCommentId.collectAsState()
    val editCommentText by viewModel.editCommentText.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val actionError by viewModel.actionError.collectAsState()

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
                        // The overflow menu's only entry is Sources, so it
                        // appears only for posts ingested from a source; RSS
                        // posts open the article screen instead.
                        post?.source?.url?.takeIf { it.isNotEmpty() }?.let { sourceUrl ->
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
                                    MochiDropdownMenuItem(
                                        text = { Text(stringResource(R.string.feeds_tab_sources)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigateToSources(viewModel.feedId, sourceUrl)
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                                    )
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
                    banner = feedsReplyBanner(
                        findComment(post?.comments.orEmpty(), replyingTo),
                        { viewModel.setReplyingTo(null) },
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

    // A comment's chip opens the lightbox on the image it is about, comments
    // showing. Hosted here rather than in the gallery, whose lazy item may
    // have scrolled out of composition by the time a chip far down is tapped.
    var openAttachment by remember { mutableStateOf<String?>(null) }

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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error!!.userMessage(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    MochiTextButton(onClick = { viewModel.loadPost() }) {
                        Text(stringResource(MochiR.string.common_retry))
                    }
                }
            }
        }

        post != null -> {
            val currentPost = post!!
            LazyColumn(
                modifier = modifier,
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
                        onRemoveTag = { viewModel.removeTag(it) },
                        onAdjustInterest = { tag, direction ->
                            viewModel.adjustInterest(
                                tag,
                                direction
                            )
                        },
                        showBody = showBody,
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

                    val flatComments = flattenComments(currentPost.comments, 0)
                    items(flatComments.size, key = { flatComments[it].first.id }) { index ->
                        val (comment, depth) = flatComments[index]
                        CommentItem(
                            comment = comment,
                            depth = depth,
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
    onRemoveTag: (String) -> Unit,
    onAdjustInterest: (Tag, String) -> Unit,
    showBody: Boolean = true,
    // The lightbox comments slot, per image attachment.
    commentCount: ((Attachment) -> Int)? = null,
    comments: (@Composable (Attachment) -> Unit)? = null,
) {
    val context = LocalContext.current

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
                        memory.yearsAgo
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
        if (showBody && post.body.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            PostBody(
                post = post,
                modifier = Modifier.fillMaxWidth(),
                onClick = onBodyClick
            )
        }

        if (showBody) {
            val videos = remember(post.body) { extractVideos(post.body) }
            videos.forEach { video ->
                Spacer(modifier = Modifier.height(8.dp))
                VideoEmbed(video = video)
            }
        }

        // Attachments
        if (post.attachments.isNotEmpty()) {
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
                AsyncImage(
                    model = imageUrl,
                    contentDescription = stringResource(R.string.feeds_image_preview),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .let { mod -> if (onBodyClick != null) mod.clickable(onClick = onBodyClick) else mod },
                    contentScale = ContentScale.Fit
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

