// Copyright © 2026 Mochi OÜ
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

@HiltViewModel
class NewPostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val repository: ForumsRepository
) : ViewModel() {

    val forumId: String = savedStateHandle["forumId"] ?: ""

    private val _uiState = MutableStateFlow(NewPostUiState())
    val uiState: StateFlow<NewPostUiState> = _uiState.asStateFlow()

    /** Files picked for upload, in the order they will be attached. */
    private val _attachments = MutableStateFlow<List<Uri>>(emptyList())
    val attachments: StateFlow<List<Uri>> = _attachments.asStateFlow()

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

    fun submit(title: String, body: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPosting = true, error = null)
            try {
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
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isPosting = false, error = e.toMochiError())
            }
        }
    }
}
