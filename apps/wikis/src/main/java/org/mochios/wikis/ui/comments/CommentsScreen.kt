// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.comments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.EmptyState
import org.mochios.wikis.R
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.WikiContextValue
import org.mochios.android.R as MochiR

/**
 * Comments surface for a single wiki page. Route: `wikis/{wikiId}/{page}/comments`.
 * Mirrors web's `PageComments` (`apps/wikis/web/src/features/wiki/page-comments.tsx`)
 * — a single column with the compose form on top (when the user can edit), an
 * empty state when no comments exist, or a thread-per-root rendered as a
 * [LazyColumn] of [WikiCommentThread] composables.
 *
 * Wraps the whole body in a [LocalWikiContext] provider so [WikiCommentThread],
 * [CommentForm], and [CommentAttachments] can resolve the per-wiki `baseURL`
 * for avatar / attachment URLs without each one having to be threaded a
 * `serverUrl + wikiId` pair.
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
                    IconButton(onClick = { navController.popBackStack() }) {
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
                .padding(padding),
        ) {
            val wikiInfo = state.wiki
            when {
                state.isLoading && state.comments.isEmpty() && wikiInfo == null -> {
                    CommentsSkeleton()
                }
                wikiInfo == null && state.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.error!!.userMessage(),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
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
                            onCreate = { body, files ->
                                viewModel.createComment(body = body, parent = null, files = files)
                            },
                            onStartReply = { commentId, selectedText ->
                                viewModel.requestStartReply(commentId, selectedText)
                            },
                            onCancelReply = { viewModel.cancelReply() },
                            onReplyDraftChange = { viewModel.updateReplyDraft(it) },
                            onSubmitReply = { parentId, files ->
                                viewModel.submitReply(parentId, files)
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
    onCreate: (body: String, files: List<java.io.File>?) -> Unit,
    onStartReply: (commentId: String, selectedText: String?) -> Unit,
    onCancelReply: () -> Unit,
    onReplyDraftChange: (String) -> Unit,
    onSubmitReply: (commentId: String, files: List<java.io.File>?) -> Unit,
    onEdit: ((commentId: String, body: String) -> Unit)?,
    onDelete: ((commentId: String) -> Unit)?,
) {
    val isOwner = state.permissions.manage || state.permissions.delete

    Column(modifier = Modifier.fillMaxSize()) {
        if (canCompose) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                CommentForm(onSubmit = onCreate)
            }
            HorizontalDivider()
        }

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
                        replyingTo = state.replyingTo,
                        replyDraft = state.replyDraft,
                        onStartReply = onStartReply,
                        onCancelReply = onCancelReply,
                        onReplyDraftChange = onReplyDraftChange,
                        onSubmitReply = onSubmitReply,
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}

/**
 * Loading placeholder shown while the wiki info + comment thread are
 * fetching in parallel. Mirrors web's `<PageCommentsSkeleton />` —
 * a centred spinner keeps the surface visually quiet without flashing
 * an empty state before the data arrives.
 */
@Composable
private fun CommentsSkeleton() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
