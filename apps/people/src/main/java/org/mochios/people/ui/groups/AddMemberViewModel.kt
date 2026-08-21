// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.NaturalCompare
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.people.model.Group
import org.mochios.people.model.GroupMemberType
import org.mochios.people.model.LocalUser
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

/**
 * Add-member search over local users and groups, interleaved in one list where
 * web uses tabs.
 */
@HiltViewModel
class AddMemberViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PeopleRepository,
) : ViewModel() {

    /** The group the new member joins. */
    val groupId: String = savedStateHandle.get<String>("id").orEmpty()

    data class SearchResult(
        val id: String,
        val name: String,
        val type: GroupMemberType,
    )

    data class UiState(
        val searchQuery: String = "",
        val searchLoading: Boolean = false,
        val searchError: MochiError? = null,
        val searchResults: List<SearchResult> = emptyList(),
        val isSaving: Boolean = false,
        val error: MochiError? = null,
        val added: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Ids the results must not offer: the members the group already has, and
     * the group itself, which cannot be its own member.
     */
    private var excluded: Set<String> = setOf(groupId)

    private var searchJob: Job? = null

    init {
        loadExisting()
    }

    /**
     * Loads the members to exclude from results; on failure search still works
     * and the server rejects duplicates.
     */
    private fun loadExisting() {
        if (groupId.isBlank()) return
        viewModelScope.launch {
            val detail = runCatching { repository.getGroup(groupId) }.getOrNull() ?: return@launch
            excluded = detail.members.map { member -> member.member }.toSet() + groupId
        }
    }

    fun search(query: String) {
        _state.update { state -> state.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { state ->
                state.copy(
                    searchResults = emptyList(),
                    searchLoading = false,
                    searchError = null,
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            _state.update { state -> state.copy(searchLoading = true, searchError = null) }
            try {
                val users = runCatching { repository.searchLocalUsers(query) }
                    .getOrDefault(emptyList<LocalUser>())
                    .filter { user -> user.id !in excluded }
                    .map { user -> SearchResult(user.id, user.name, GroupMemberType.USER) }

                val needle = query.trim().lowercase()
                val groups = runCatching { repository.listGroups() }
                    .getOrDefault(emptyList<Group>())
                    .filter { group ->
                        group.id !in excluded && group.name.lowercase().contains(needle)
                    }
                    .map { group -> SearchResult(group.id, group.name, GroupMemberType.GROUP) }

                val merged = (users + groups)
                    .sortedWith(compareBy(NaturalCompare) { result -> result.name })

                _state.update { state ->
                    state.copy(searchLoading = false, searchResults = merged)
                }
            } catch (e: Exception) {
                _state.update { state ->
                    state.copy(searchLoading = false, searchError = e.toMochiError())
                }
            }
        }
    }

    /** Adds [result] to the group and reports it back through [UiState.added]. */
    fun addMember(result: SearchResult) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.update { state -> state.copy(isSaving = true, error = null) }
            try {
                repository.addGroupMember(groupId, result.id, result.type)
                _state.update { state -> state.copy(isSaving = false, added = true) }
            } catch (e: Exception) {
                _state.update { state ->
                    state.copy(isSaving = false, error = e.toMochiError())
                }
            }
        }
    }
}
