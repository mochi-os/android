// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.comments

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.R as MochiR
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ComposeBarDefaults
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.ReplyComposerBanner
import org.mochios.wikis.R
import org.mochios.wikis.model.WikiComment
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.WikiContextValue

/**
 * Comments for a single wiki page. Provides [LocalWikiContext] so children can
 * build avatar and attachment URLs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(
    navController: NavController,
    viewModel: CommentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Forward ViewModel events (errors, toasts) onto the snackbar host.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CommentsEvent.Toast -> snackbarHostState.showSnackbar(event.message)
                is CommentsEvent.Error -> snackbarHostState.showSnackbar(event.error.userMessage())
            }
        }
    }

    val title = if (state.pageTitle.isBlank()) {
        stringResource(R.string.wikis_comments_title_template, viewModel.slug)
    } else {
        stringResource(R.string.wikis_comments_title_template, state.pageTitle)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    MochiIconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            val wikiInfo = state.wiki
            when {
                state.isLoading && state.comments.isEmpty() && wikiInfo == null -> {
                    CommentsSkeleton()
                }
                wikiInfo == null && state.error != null -> {
                    ErrorState(
                        error = state.error!!,
                        onRetry = viewModel::loadComments,
                    )
                }
                wikiInfo == null -> {
                    CommentsSkeleton()
                }
                else -> {
                    val wikiCtx = WikiContextValue(
                        wikiId = viewModel.wikiId,
                        info = wikiInfo,
                        permissions = state.permissions,
                        serverUrl = viewModel.serverUrl,
                    )
                    CompositionLocalProvider(LocalWikiContext provides wikiCtx) {
                        CommentsBody(
                            state = state,
                            canCompose = state.permissions.edit,
                            onCreate = { body, uris ->
                                viewModel.createComment(body = body, parent = null, uris = uris)
                            },
                            resolveFileName = viewModel::fileName,
                            onStartReply = { commentId, selectedText ->
                                viewModel.requestStartReply(commentId, selectedText)
                            },
                            onCancelReply = { viewModel.cancelReply() },
                            onReplyDraftChange = { viewModel.updateReplyDraft(it) },
                            onSubmitReply = { parentId, uris ->
                                viewModel.submitReply(parentId, uris)
                            },
                            onEdit = if (state.permissions.edit) {
                                { id, body -> viewModel.editComment(id, body) }
                            } else null,
                            onDelete = if (state.permissions.edit || state.permissions.delete) {
                                { id -> viewModel.deleteComment(id) }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Body content rendered once the wiki context is available. Pulled out of
 * [CommentsScreen] to keep the scaffold body and the loading branches small.
 */
@Composable
private fun CommentsBody(
    state: CommentsUiState,
    canCompose: Boolean,
    onCreate: (body: String, files: List<Uri>?) -> Unit,
    resolveFileName: suspend (Uri) -> String,
    onStartReply: (commentId: String, selectedText: String?) -> Unit,
    onCancelReply: () -> Unit,
    onReplyDraftChange: (String) -> Unit,
    onSubmitReply: (commentId: String, files: List<Uri>?) -> Unit,
    onEdit: ((commentId: String, body: String) -> Unit)?,
    onDelete: ((commentId: String) -> Unit)?,
) {
    val isOwner = state.permissions.manage || state.permissions.delete

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            if (state.comments.isEmpty() && !state.isLoading) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Message,
                    title = stringResource(R.string.wikis_comments_empty_title),
                    subtitle = stringResource(R.string.wikis_comments_empty_subtitle),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(state.comments, key = { it.id }) { comment ->
                        WikiCommentThread(
                            comment = comment,
                            slug = state.pageTitle.ifBlank { comment.page },
                            currentUserId = state.currentUserId,
                            isOwner = isOwner,
                            onStartReply = onStartReply,
                            onEdit = onEdit,
                            onDelete = onDelete,
                        )
                    }
                }
            }
        }

        if (canCompose) {
            val replyingTo = state.replyingTo
            val target = replyingTo?.let { id -> findComment(state.comments, id) }
            key(replyingTo) {
                CommentForm(
                    onSubmit = { body, files ->
                        if (replyingTo != null) {
                            onSubmitReply(replyingTo, files)
                        } else {
                            onCreate(body, files)
                        }
                    },
                    resolveFileName = resolveFileName,
                    initialText = if (replyingTo != null) state.replyDraft else "",
                    onTextChange = if (replyingTo != null) onReplyDraftChange else null,
                    placeholder = stringResource(
                        if (replyingTo != null) R.string.wikis_comment_form_placeholder_reply
                        else R.string.wikis_comment_form_placeholder_new
                    ),
                    autoFocus = replyingTo != null,
                    banner = target?.let { comment ->
                        {
                            ReplyComposerBanner(
                                label = stringResource(
                                    R.string.wikis_comment_replying_to,
                                    comment.name.ifBlank { comment.author },
                                ),
                                preview = comment.bodyMarkdown.ifBlank { comment.body },
                                cancelLabel = stringResource(
                                    R.string.wikis_comment_clear_reply
                                ),
                                onCancel = onCancelReply,
                            )
                        }
                    },
                    windowInsets = ComposeBarDefaults.WindowInsets,
                )
            }
        }
    }
}

/**
 * The comment with this id, wherever it sits in the thread.
 *
 * @param comments The thread roots to search.
 * @param id The comment being looked for.
 * @return The comment, or null when it is no longer in the thread.
 */
private fun findComment(comments: List<WikiComment>, id: String): WikiComment? {
    for (comment in comments) {
        if (comment.id == id) return comment
        val found = findComment(comment.children, id)
        if (found != null) return found
    }
    return null
}

@Composable
private fun CommentsSkeleton() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
