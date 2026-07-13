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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.NaturalCompare
import org.mochios.people.model.Group
import org.mochios.people.model.GroupMember
import org.mochios.people.model.GroupMemberType
import org.mochios.people.model.LocalUser
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

/**
 * State + actions for the Group detail screen. Mirrors the web
 * `group-detail.tsx` page: top section with name/description + edit
 * affordances, member list with add/remove, overflow → delete.
 *
 * The search field in [AddMemberDialog] is debounced 300 ms before hitting
 * `repository.searchLocalUsers(query)` to match the web behaviour. We also
 * mine `repository.listGroups()` so groups can be added as nested members
 * — the web exposes this via tabs; on mobile we interleave both kinds in
 * a single results list and tag them with the member-type pill.
 */
@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PeopleRepository,
) : ViewModel() {

    val groupId: String = savedStateHandle.get<String>("id").orEmpty()

    /**
     * One row in the add-member results list. We unify the two web tabs
     * (local users + groups) into a single list, distinguished by [type].
     */
    data class SearchResult(
        val id: String,
        val name: String,
        val type: GroupMemberType,
    )

    data class UiState(
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val group: Group? = null,
        val members: List<GroupMember> = emptyList(),
        val error: MochiError? = null,
        val addDialogOpen: Boolean = false,
        val editNameOpen: Boolean = false,
        val editDescOpen: Boolean = false,
        val deleteConfirmOpen: Boolean = false,
        val removeMemberTarget: GroupMember? = null,
        val searchQuery: String = "",
        val searchLoading: Boolean = false,
        val searchError: MochiError? = null,
        val searchResults: List<SearchResult> = emptyList(),
    )

    sealed interface Event {
        data object NavigateBack : Event
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private var searchJob: Job? = null

    init {
        if (groupId.isNotBlank()) refresh()
    }

    fun refresh() {
        if (groupId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = repository.getGroup(groupId)
                _state.update {
                    it.copy(
                        isLoading = false,
                        group = detail.group,
                        members = detail.members.sortedWith(
                            compareBy(NaturalCompare) { m -> m.name }
                        ),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.toMochiError()) }
            }
        }
    }

    // ---- Dialog visibility ----

    fun openAddDialog() {
        _state.update {
            it.copy(
                addDialogOpen = true,
                searchQuery = "",
                searchResults = emptyList(),
                searchError = null,
                searchLoading = false,
            )
        }
    }

    fun closeAddDialog() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                addDialogOpen = false,
                searchQuery = "",
                searchResults = emptyList(),
                searchError = null,
                searchLoading = false,
            )
        }
    }

    fun openEditName() { _state.update { it.copy(editNameOpen = true) } }
    fun closeEditName() { _state.update { it.copy(editNameOpen = false) } }

    fun openEditDescription() { _state.update { it.copy(editDescOpen = true) } }
    fun closeEditDescription() { _state.update { it.copy(editDescOpen = false) } }

    fun openDeleteConfirm() { _state.update { it.copy(deleteConfirmOpen = true) } }
    fun closeDeleteConfirm() { _state.update { it.copy(deleteConfirmOpen = false) } }

    fun requestRemoveMember(member: GroupMember) {
        _state.update { it.copy(removeMemberTarget = member) }
    }

    fun cancelRemoveMember() {
        _state.update { it.copy(removeMemberTarget = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    // ---- Mutations ----

    fun updateName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(error = MochiError.Local(org.mochios.people.R.string.people_group_name_required)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                repository.updateGroup(groupId, name = trimmed)
                _state.update {
                    it.copy(
                        isSaving = false,
                        editNameOpen = false,
                        group = it.group?.copy(name = trimmed),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.toMochiError()) }
            }
        }
    }

    fun updateDescription(newDescription: String) {
        val trimmed = newDescription.trim()
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                repository.updateGroup(groupId, description = trimmed)
                _state.update {
                    it.copy(
                        isSaving = false,
                        editDescOpen = false,
                        group = it.group?.copy(description = trimmed),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.toMochiError()) }
            }
        }
    }

    fun addMember(result: SearchResult) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                repository.addGroupMember(groupId, result.id, result.type)
                _state.update { it.copy(isSaving = false, addDialogOpen = false) }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.toMochiError()) }
            }
        }
    }

    fun removeMember(member: GroupMember) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                repository.removeGroupMember(groupId, member.member)
                _state.update {
                    it.copy(
                        isSaving = false,
                        removeMemberTarget = null,
                        members = it.members.filterNot { row -> row.member == member.member },
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.toMochiError()) }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                repository.deleteGroup(groupId)
                _state.update { it.copy(isSaving = false, deleteConfirmOpen = false) }
                _events.tryEmit(Event.NavigateBack)
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.toMochiError()) }
            }
        }
    }

    // ---- Search (debounced) ----

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update {
                it.copy(
                    searchResults = emptyList(),
                    searchLoading = false,
                    searchError = null,
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(searchLoading = true, searchError = null) }
            try {
                val excluded = (_state.value.members.map { it.member } + groupId).toSet()

                val users = runCatching { repository.searchLocalUsers(query) }
                    .getOrDefault(emptyList<LocalUser>())
                    .filter { it.id !in excluded }
                    .map { SearchResult(it.id, it.name, GroupMemberType.USER) }

                val needle = query.trim().lowercase()
                val groups = runCatching { repository.listGroups() }
                    .getOrDefault(emptyList<Group>())
                    .filter { g ->
                        g.id !in excluded &&
                            g.name.lowercase().contains(needle)
                    }
                    .map { SearchResult(it.id, it.name, GroupMemberType.GROUP) }

                val merged = (users + groups)
                    .sortedWith(compareBy(NaturalCompare) { it.name })

                _state.update {
                    it.copy(searchLoading = false, searchResults = merged)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(searchLoading = false, searchError = e.toMochiError())
                }
            }
        }
    }
}
