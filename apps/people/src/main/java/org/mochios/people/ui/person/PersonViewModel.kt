// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.person

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
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.people.model.PersonInformation
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

/**
 * Friendship state between the current user and the person on display. Resolved
 * from [PeopleRepository.listFriends] plus the bound identity at refresh time;
 * the screen renders different action affordances per branch.
 *
 *  - [Self]            — the displayed person *is* the current user
 *  - [Friend]          — already mutual friends
 *  - [InvitedByThem]   — they have sent us an invite that's pending acceptance
 *  - [InvitedThem]     — we have sent them an outgoing invite
 *  - [NotFriend]       — no relationship; can send a new invite
 */
sealed class FriendState {
    object Self : FriendState()
    object Friend : FriendState()
    data class InvitedByThem(val inviteId: String) : FriendState()
    object InvitedThem : FriendState()
    object NotFriend : FriendState()
}

/** One-shot side-effect events emitted by [PersonViewModel] to the screen. */
sealed class PersonViewEvent {
    /** Open chat with the displayed person (host wires this through to ChatApp). */
    data class Message(val personId: String, val personName: String) : PersonViewEvent()
}

data class PersonViewUiState(
    val isLoading: Boolean = true,
    val isMutating: Boolean = false,
    val info: PersonInformation? = null,
    val friendState: FriendState = FriendState.NotFriend,
    val error: MochiError? = null,
)

/**
 * Read-only public profile of a person other than the current user. Loads the
 * `:entity/-/information` response and the friends list in parallel on init so
 * the friendship-state pill can render without a follow-up round-trip.
 */
@HiltViewModel
class PersonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PeopleRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val personId: String = savedStateHandle.get<String>("id").orEmpty()

    private val _uiState = MutableStateFlow(PersonViewUiState())
    val uiState: StateFlow<PersonViewUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PersonViewEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PersonViewEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val info = repository.getPersonInformation(personId)
                val state = resolveFriendState(info.id)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    info = info,
                    friendState = state,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    private suspend fun resolveFriendState(personEntityId: String): FriendState {
        val me = sessionManager.getBoundIdentity()
        if (me != null && me == personEntityId) return FriendState.Self
        val friends = repository.listFriends()
        friends.friends.firstOrNull { it.id == personEntityId }?.let { return FriendState.Friend }
        friends.received.firstOrNull { it.id == personEntityId }?.let {
            return FriendState.InvitedByThem(it.id)
        }
        friends.sent.firstOrNull { it.id == personEntityId }?.let { return FriendState.InvitedThem }
        return FriendState.NotFriend
    }

    fun addFriend() {
        val info = _uiState.value.info ?: return
        if (_uiState.value.friendState != FriendState.NotFriend) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMutating = true, error = null)
            try {
                repository.createFriend(info.id, info.name)
                val state = resolveFriendState(info.id)
                _uiState.value = _uiState.value.copy(
                    isMutating = false,
                    friendState = state,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isMutating = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun acceptInvite() {
        val state = _uiState.value.friendState
        if (state !is FriendState.InvitedByThem) return
        val info = _uiState.value.info ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMutating = true, error = null)
            try {
                repository.acceptInvite(state.inviteId)
                val resolved = resolveFriendState(info.id)
                _uiState.value = _uiState.value.copy(
                    isMutating = false,
                    friendState = resolved,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isMutating = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun declineInvite() {
        val state = _uiState.value.friendState
        if (state !is FriendState.InvitedByThem) return
        val info = _uiState.value.info ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMutating = true, error = null)
            try {
                repository.ignoreInvite(state.inviteId)
                val resolved = resolveFriendState(info.id)
                _uiState.value = _uiState.value.copy(
                    isMutating = false,
                    friendState = resolved,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isMutating = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun message() {
        val info = _uiState.value.info ?: return
        viewModelScope.launch {
            _events.emit(PersonViewEvent.Message(info.id, info.name))
        }
    }
}
