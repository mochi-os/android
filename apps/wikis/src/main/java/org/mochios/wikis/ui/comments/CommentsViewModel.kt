// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.comments

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.wikis.model.WikiComment
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import java.io.File
import javax.inject.Inject

/**
 * UI state for [CommentsScreen]. `replyingTo` / `replyDraft` live here so the
 * recursive thread composables read one source.
 */
data class CommentsUiState(
    val isLoading: Boolean = true,
    val comments: List<WikiComment> = emptyList(),
    val pageTitle: String = "",
    val wiki: WikiInfo? = null,
    val permissions: WikiPermissions = WikiPermissions(),
    /** Identity of the signed-in account (used to gate Edit). */
    val currentUserId: String? = null,
    val error: MochiError? = null,

    /** ID of the comment whose reply textarea is currently active, or null. */
    val replyingTo: String? = null,
    /** Working draft for the active reply textarea. */
    val replyDraft: String = "",
)

sealed interface CommentsEvent {
    data class Toast(val message: String) : CommentsEvent
    data class Error(val error: MochiError) : CommentsEvent
}

/**
 * ViewModel for [CommentsScreen]. Reads `wikiId` and `page` from
 * [SavedStateHandle] and loads wiki info, page and comments in parallel.
 */
@HiltViewModel
class CommentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()
    val slug: String = savedStateHandle.get<String>("page").orEmpty()

    /** Origin of the Mochi server the session is bound to. Trimmed of trailing slash. */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    private val _events = Channel<CommentsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadIdentity()
        loadInfo()
        loadPage()
        loadComments()
    }

    private fun loadIdentity() {
        viewModelScope.launch {
            // Capture the signed-in entity so Edit affordances can gate on
            // ownership of each comment — `comment.author == currentUserId`.
            val identity = sessionManager.getBoundIdentity()
            _uiState.value = _uiState.value.copy(currentUserId = identity)
        }
    }

    private fun loadInfo() {
        viewModelScope.launch {
            try {
                val response = repository.getInfo(wikiId)
                _uiState.value = _uiState.value.copy(
                    wiki = response.wiki,
                    permissions = response.permissions ?: WikiPermissions(),
                )
            } catch (e: Exception) {
                // Non-fatal: the screen still renders, just without a fully
                // populated WikiContextValue — the comment refresh below will
                // surface the real error.
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    private fun loadPage() {
        viewModelScope.launch {
            try {
                val response = repository.getPage(wikiId, slug)
                val title = when (response) {
                    is org.mochios.wikis.model.PageFetchResponse.Page -> response.page.title
                    is org.mochios.wikis.model.PageFetchResponse.NotFound -> slug
                }
                _uiState.value = _uiState.value.copy(pageTitle = title)
            } catch (_: Exception) {
                // Title fallback to slug — the comments thread is the main
                // content and is independently fetched below.
                _uiState.value = _uiState.value.copy(pageTitle = slug)
            }
        }
    }

    fun loadComments() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = repository.getComments(wikiId, slug)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    comments = response.comments,
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /** Stages any picked attachments, then posts the comment. */
    fun createComment(body: String, parent: String? = null, uris: List<Uri>? = null) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val files = repository.stageFiles(uris.orEmpty())
            try {
                repository.createComment(wikiId, slug, trimmed, parent, files.ifEmpty { null })
                if (parent != null) cancelReply()
                loadComments()
            } catch (e: Exception) {
                _events.send(CommentsEvent.Error(e.toMochiError()))
            } finally {
                repository.discardStaged(files)
            }
        }
    }

    /** The picked file's real name, for labelling a draft attachment. */
    suspend fun fileName(uri: Uri): String = repository.fileName(uri)

    fun editComment(id: String, body: String) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.editComment(wikiId, slug, id, trimmed)
                loadComments()
            } catch (e: Exception) {
                _events.send(CommentsEvent.Error(e.toMochiError()))
            }
        }
    }

    fun deleteComment(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteComment(wikiId, slug, id)
                loadComments()
            } catch (e: Exception) {
                _events.send(CommentsEvent.Error(e.toMochiError()))
            }
        }
    }

    // ---------------- reply textarea state ----------------

    fun requestStartReply(commentId: String, selectedText: String? = null) {
        val draft = selectedText?.trim()?.takeIf { it.isNotEmpty() }?.let { sel ->
            sel.lineSequence().joinToString("\n") { "> $it" } + "\n\n"
        } ?: ""
        _uiState.value = _uiState.value.copy(
            replyingTo = commentId,
            replyDraft = draft,
        )
    }

    fun cancelReply() {
        _uiState.value = _uiState.value.copy(replyingTo = null, replyDraft = "")
    }

    fun updateReplyDraft(text: String) {
        _uiState.value = _uiState.value.copy(replyDraft = text)
    }

    /**
     * Submit the current reply draft as a reply under [parentId]. Files are
     * the optional list of attachments picked through the reply form.
     */
    fun submitReply(parentId: String, uris: List<Uri>? = null) {
        val draft = _uiState.value.replyDraft
        createComment(body = draft, parent = parentId, uris = uris)
    }
}
