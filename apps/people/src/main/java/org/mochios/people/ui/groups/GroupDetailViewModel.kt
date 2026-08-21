// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PeopleRepository,
) : ViewModel() {

    val groupId: String = savedStateHandle.get<String>("id").orEmpty()

    data class UiState(
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val group: Group? = null,
        val members: List<GroupMember> = emptyList(),
        val error: MochiError? = null,
        val editNameOpen: Boolean = false,
        val editDescOpen: Boolean = false,
        val deleteConfirmOpen: Boolean = false,
        val removeMemberTarget: GroupMember? = null,
    )

    sealed interface Event {
        data object NavigateBack : Event
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events: SharedFlow<Event> = _events.asSharedFlow()

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
}
