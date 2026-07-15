// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.newpost

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.model.Attachment
import org.mochios.android.ui.components.MentionSuggestion
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

data class NewPostUiState(
    val isPosting: Boolean = false,

    /** True once the server accepted the post; the screen then closes itself.
     *  Distinct from [createdPost], which some servers return blank. */
    val postSuccess: Boolean = false,

    val createdForum: String = "",
    val createdPost: String = "",
    val error: MochiError? = null
)

/**
 * Backs the shared compose-a-post screen for both new posts and edits. The
 * screen is in edit mode when a `postId` is present in the back-stack args, in
 * which case the existing title, body and attachments are loaded up front and
 * [submit] routes to an edit rather than a create. Mirrors feeds'
 * `CreatePostViewModel`.
 */
@HiltViewModel
class NewPostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val repository: ForumsRepository
) : ViewModel() {

    val forumId: String = savedStateHandle["forumId"] ?: ""

    private val editingPostId: String? = savedStateHandle["postId"]
    val isEditing: Boolean = editingPostId != null

    private val _uiState = MutableStateFlow(NewPostUiState())
    val uiState: StateFlow<NewPostUiState> = _uiState.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _body = MutableStateFlow("")
    val body: StateFlow<String> = _body.asStateFlow()

    /** Files picked for upload, in the order they will be attached. */
    private val _attachments = MutableStateFlow<List<Uri>>(emptyList())
    val attachments: StateFlow<List<Uri>> = _attachments.asStateFlow()

    /** The post's current attachments when editing, in display order. */
    private val _existingAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val existingAttachments: StateFlow<List<Attachment>> = _existingAttachments.asStateFlow()

    /** Ids of existing attachments the user has marked for removal. */
    private val _removedExistingIds = MutableStateFlow<Set<String>>(emptySet())
    val removedExistingIds: StateFlow<Set<String>> = _removedExistingIds.asStateFlow()

    init {
        if (isEditing) {
            loadExistingPost()
        }
    }

    private fun loadExistingPost() {
        val postId = editingPostId ?: return
        viewModelScope.launch {
            try {
                val r = repository.viewPost(forumId, postId)
                _title.value = r.post.title
                _body.value = r.post.body
                _existingAttachments.value = r.post.attachments
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun setTitle(value: String) {
        _title.value = value
    }

    fun setBody(value: String) {
        _body.value = value
    }

    fun addAttachments(uris: List<Uri>) {
        _attachments.value = _attachments.value + uris.filterNot { uri -> uri in _attachments.value }
    }

    fun removeAttachment(uri: Uri) {
        _attachments.value = _attachments.value.filterNot { existing -> existing == uri }
    }

    /** Move [uri] one slot towards the head (-1) or tail (+1) of the list. */
    fun moveAttachment(uri: Uri, direction: Int) {
        val current = _attachments.value.toMutableList()
        val from = current.indexOf(uri)
        val to = from + direction
        if (from < 0 || to !in current.indices) return
        current[from] = current[to]
        current[to] = uri
        _attachments.value = current
    }

    /** Toggle whether an existing attachment is kept or dropped on save. */
    fun toggleRemoveExistingAttachment(id: String) {
        val current = _removedExistingIds.value
        _removedExistingIds.value = if (id in current) current - id else current + id
    }

    /** Move an existing attachment one slot towards the head (-1) or tail (+1). */
    fun moveExistingAttachment(attachmentId: String, direction: Int) {
        val current = _existingAttachments.value.toMutableList()
        val from = current.indexOfFirst { attachment -> attachment.id == attachmentId }
        val to = from + direction
        if (from < 0 || to !in current.indices) return
        val moved = current[from]
        current[from] = current[to]
        current[to] = moved
        _existingAttachments.value = current
    }

    /** Backs the body field's `@mention` autocomplete with this forum's members. */
    suspend fun searchMembers(query: String): List<MentionSuggestion> {
        if (forumId.isBlank()) return emptyList()
        return try {
            repository.searchMembers(forumId, query).members.map { member ->
                MentionSuggestion(id = member.id, name = member.name)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun submit() {
        val title = _title.value
        val body = _body.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPosting = true, error = null)
            try {
                if (isEditing) {
                    val postId = editingPostId!!
                    val keptExisting = _existingAttachments.value
                        .filterNot { attachment -> attachment.id in _removedExistingIds.value }
                        .map { attachment -> attachment.id }
                    repository.editPostFromUris(
                        forumId = forumId,
                        postId = postId,
                        title = title,
                        body = body,
                        keptAttachmentIds = keptExisting,
                        newFileUris = _attachments.value,
                        contentResolver = application.contentResolver,
                    )
                    _uiState.value = _uiState.value.copy(
                        isPosting = false,
                        postSuccess = true,
                        createdForum = forumId,
                        createdPost = postId
                    )
                } else {
                    val r = repository.createPostFromUris(
                        forumId = forumId,
                        title = title,
                        body = body,
                        uris = _attachments.value,
                        contentResolver = application.contentResolver,
                    )
                    _uiState.value = _uiState.value.copy(
                        isPosting = false,
                        postSuccess = true,
                        createdForum = r.forum,
                        createdPost = r.post
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isPosting = false, error = e.toMochiError())
            }
        }
    }
}
