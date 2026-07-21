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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.android.ui.components.LocationMapView
import org.mochios.android.ui.components.MentionSuggestion
import org.mochios.android.ui.components.MentionTextField
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.ui.components.TagItem
import org.mochios.android.ui.components.PostTagsButton as SharedPostTagsButton
import org.mochios.android.ui.components.ReactionBar
import org.mochios.android.ui.components.VideoEmbed
import org.mochios.android.ui.components.extractVideos
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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { viewModel.addCommentAttachment(it) }
    }

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
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back)
                        )
                    }
                },
                actions = {
                    if (permissions.manage) {
                        IconButton(onClick = { onEditPost(viewModel.feedId, viewModel.postId) }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(MochiR.string.common_edit)
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(MochiR.string.common_delete)
                            )
                        }
                        // Sources is manager-only, like the feed screen's entry,
                        // and only offered when the post was ingested from a
                        // source (feed-to-feed and memories posts land here;
                        // RSS posts open the source article screen instead).
                        // The overflow menu itself only appears when it has
                        // this entry to show.
                        post?.source?.url?.takeIf { it.isNotEmpty() }?.let { sourceUrl ->
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(MochiR.string.common_more_options)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.feeds_tab_sources)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Link, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigateToSources(viewModel.feedId, sourceUrl)
                                        }
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
                CommentInputBar(
                    text = commentText,
                    onTextChange = { viewModel.setCommentText(it) },
                    attachments = commentAttachments,
                    onAddAttachment = { filePickerLauncher.launch("*/*") },
                    onRemoveAttachment = { viewModel.removeCommentAttachment(it) },
                    onSend = { viewModel.sendComment() },
                    isSending = isSendingComment,
                    replyingTo = replyingTo,
                    onCancelReply = { viewModel.setReplyingTo(null) },
                    onSearchMembers = { viewModel.searchMembers(it) }
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
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.feeds_delete_post)) },
            text = { Text(stringResource(R.string.feeds_delete_post_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePost { onNavigateBack() }
                    }
                ) {
                    Text(
                        stringResource(MochiR.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(MochiR.string.common_cancel))
                }
            }
        )
    }

    // Delete comment dialog
    showDeleteCommentDialog?.let { commentId ->
        AlertDialog(
            onDismissRequest = { showDeleteCommentDialog = null },
            title = { Text(stringResource(R.string.feeds_delete_comment)) },
            text = { Text(stringResource(R.string.feeds_delete_comment_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteCommentDialog = null
                        viewModel.deleteComment(commentId)
                    }
                ) {
                    Text(
                        stringResource(MochiR.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCommentDialog = null }) {
                    Text(stringResource(MochiR.string.common_cancel))
                }
            }
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
                    TextButton(onClick = { viewModel.loadPost() }) {
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
                        showBody = showBody
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
                                    stripHtml(comment.body)
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
                        )
                    }
                }
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
    showBody: Boolean = true
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

        // Post body. For RSS-source posts, taps open the original article.
        // The body, embedded videos, RSS preview image and source link are
        // suppressed when this content renders inside the source-view sheet —
        // the WebView already shows the same article above the sheet.
        val sourceArticleUrl = post.data?.rss?.link?.takeIf { it.isNotEmpty() }
        val onBodyClick: (() -> Unit)? = sourceArticleUrl?.let { url ->
            {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) { /* invalid URL */
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
                }
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
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                // Invalid URL
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

@Composable
internal fun CommentInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    attachments: List<Uri>,
    onAddAttachment: () -> Unit,
    onRemoveAttachment: (Uri) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    replyingTo: String?,
    onCancelReply: () -> Unit,
    onSearchMembers: suspend (String) -> List<MentionSuggestion>
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (replyingTo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.feeds_replying_to_comment),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onCancelReply,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.feeds_cancel_reply),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (attachments.isNotEmpty()) {
                val fileLabel = stringResource(R.string.feeds_file)
                val removeLabel = stringResource(R.string.feeds_remove)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(attachments) { uri ->
                        AssistChip(
                            onClick = { onRemoveAttachment(uri) },
                            label = {
                                Text(
                                    uri.lastPathSegment?.takeLast(20) ?: fileLabel,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = removeLabel,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = onAddAttachment) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = stringResource(R.string.feeds_attach_file)
                    )
                }
                MentionTextField(
                    value = text,
                    onValueChange = onTextChange,
                    onSearch = onSearchMembers,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    placeholder = { Text(stringResource(R.string.feeds_write_a_comment)) },
                    maxLines = 4
                )
                IconButton(
                    onClick = onSend,
                    enabled = text.isNotBlank() && !isSending
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.feeds_send)
                        )
                    }
                }
            }
        }
    }
}



