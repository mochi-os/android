// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forumlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.NaturalCompare
import org.mochios.forums.model.Forum
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

data class ForumListUiState(
    val forums: List<Forum> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val searchQuery: String = "",
    val showSearch: Boolean = false,
    val showCreateDialog: Boolean = false,
    // The user's global default post sort, from the list response's settings.
    // "" means no explicit default (server falls back to "new").
    val defaultSort: String = ""
)

@HiltViewModel
class ForumListViewModel @Inject constructor(
    private val repository: ForumsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForumListUiState())
    val uiState: StateFlow<ForumListUiState> = _uiState.asStateFlow()

    /** Emits the id of a just-created forum so the host screen can open it. */
    private val _forumCreated = MutableSharedFlow<String>()
    val forumCreated: SharedFlow<String> = _forumCreated.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val r = repository.listForums()
                val sorted = r.forums.sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(
                    forums = sorted, isLoading = false, defaultSort = r.settings.sort,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val r = repository.listForums()
                val sorted = r.forums.sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(
                    forums = sorted, isRefreshing = false, error = null,
                    defaultSort = r.settings.sort,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false, error = e.toMochiError())
            }
        }
    }

    /**
     * Set the user's global default post sort (applied to forums with no
     * per-forum override). Distinct from the per-forum override on the forum
     * screen. Mirrors web's forums-list `setDefaultSort`.
     */
    fun setDefaultSort(sort: String) {
        _uiState.value = _uiState.value.copy(defaultSort = sort)
        viewModelScope.launch {
            try {
                repository.setDefaultSort(sort)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun toggleSearch() {
        val current = _uiState.value
        _uiState.value = current.copy(
            showSearch = !current.showSearch,
            searchQuery = if (current.showSearch) "" else current.searchQuery
        )
    }

    fun updateSearchQuery(q: String) {
        _uiState.value = _uiState.value.copy(searchQuery = q)
    }

    fun filteredForums(): List<Forum> {
        val q = _uiState.value.searchQuery.lowercase().trim()
        if (q.isEmpty()) return _uiState.value.forums
        return _uiState.value.forums.filter { it.name.lowercase().contains(q) }
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
    }

    fun hideCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
    }

    /**
     * Create a forum, refresh the list so the drawer shows it, then emit its id
     * on [forumCreated] for the host screen to navigate into.
     */
    fun createForum(name: String, privacy: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true)
            try {
                val forumId = repository.createForum(name, privacy)
                _uiState.value = _uiState.value.copy(isCreating = false, showCreateDialog = false)
                load()
                _forumCreated.emit(forumId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isCreating = false, error = e.toMochiError())
            }
        }
    }

    fun unsubscribe(forumId: String) {
        viewModelScope.launch {
            try {
                repository.unsubscribe(forumId)
                _uiState.value = _uiState.value.copy(
                    forums = _uiState.value.forums.filterNot { it.id == forumId }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }
}
