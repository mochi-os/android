// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.friends

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
import org.mochios.people.model.Friend
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

/**
 * UI state for the Friends list. Mirrors the web `Friends` page state — a
 * searchable list of confirmed friends with overlays for the add-friend
 * dialog (with its own search), the remove-friend confirm, and the optional
 * welcome banner shown on first visit.
 *
 * Errors are kept as typed [MochiError] so the composable can resolve them
 * to localised strings via `userMessage()`. Toast messages already pre-formatted
 * are emitted through [toasts] so the screen can render them via Snackbar
 * without owning more state.
 */
/** How the friends list is ordered. Mirrors web's name/recent toggle. */
enum class FriendSortBy { NAME, RECENT }

data class FriendsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val friends: List<Friend> = emptyList(),
    val searchQuery: String = "",
    val sortBy: FriendSortBy = FriendSortBy.NAME,
    val error: MochiError? = null,

    // Remove-friend confirmation dialog
    val removingFriend: Friend? = null,
    val isRemoving: Boolean = false,

    /**
     * One-shot welcome banner shown on first visit. True only after
     * `-/welcome` reports `seen == false`; flipped back to false (and persisted
     * server-side via `-/welcome/seen`) when the user dismisses it.
     */
    val showWelcome: Boolean = false,
)

/**
 * Side-effect events emitted by the ViewModel. The screen collects these
 * and routes them to navigation / Toast / Intent helpers without putting
 * one-shot data into the persistent UI state.
 */
sealed class FriendsEvent {
    /** Open chat with the given friend id (deep-link `mochi://chat/with?friend=X`). */
    data class MessageFriend(val friendId: String, val friendName: String) : FriendsEvent()
    /** Show a transient string (already localised) in a snackbar. */
    data class Toast(val message: String) : FriendsEvent()
}

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val repository: PeopleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FriendsEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<FriendsEvent> = _events.asSharedFlow()

    init {
        loadFriends()
        loadWelcome()
    }

    // ---------------- welcome banner ----------------

    private fun loadWelcome() {
        viewModelScope.launch {
            try {
                val welcome = repository.getWelcome()
                if (!welcome.seen) {
                    _uiState.value = _uiState.value.copy(showWelcome = true)
                }
            } catch (_: Exception) {
                // Welcome is non-essential chrome; failing to fetch it just
                // means we don't show the banner this session.
            }
        }
    }

    fun dismissWelcome() {
        if (!_uiState.value.showWelcome) return
        _uiState.value = _uiState.value.copy(showWelcome = false)
        viewModelScope.launch {
            try {
                repository.markWelcomeSeen()
            } catch (_: Exception) {
                // Best-effort persistence; the banner is already hidden locally.
            }
        }
    }

    // ---------------- list ----------------

    fun loadFriends() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val list = repository.listFriends().friends
                _uiState.value = _uiState.value.copy(friends = list, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val list = repository.listFriends().friends
                _uiState.value = _uiState.value.copy(
                    friends = list,
                    isRefreshing = false,
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setSortBy(sortBy: FriendSortBy) {
        _uiState.value = _uiState.value.copy(sortBy = sortBy)
    }

    fun filteredFriends(): List<Friend> {
        val q = _uiState.value.searchQuery.trim()
        val base = if (q.isBlank()) {
            _uiState.value.friends
        } else {
            _uiState.value.friends.filter { it.name.contains(q, ignoreCase = true) }
        }
        return when (_uiState.value.sortBy) {
            FriendSortBy.RECENT -> base.sortedByDescending { it.created }
            FriendSortBy.NAME -> base.sortedWith(compareBy(NaturalCompare) { it.name })
        }
    }

    // ---------------- remove ----------------

    fun requestRemoveFriend(friend: Friend) {
        _uiState.value = _uiState.value.copy(removingFriend = friend)
    }

    fun cancelRemoveFriend() {
        _uiState.value = _uiState.value.copy(removingFriend = null)
    }

    fun confirmRemoveFriend() {
        val friend = _uiState.value.removingFriend ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRemoving = true)
            try {
                repository.deleteFriend(friend.id)
                _uiState.value = _uiState.value.copy(
                    isRemoving = false,
                    removingFriend = null,
                    friends = _uiState.value.friends.filterNot { it.id == friend.id },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRemoving = false,
                    removingFriend = null,
                    error = e.toMochiError(),
                )
            }
        }
    }

    // ---------------- message (deep-link to chat) ----------------

    fun messageFriend(friend: Friend) {
        viewModelScope.launch {
            _events.emit(FriendsEvent.MessageFriend(friend.id, friend.name))
        }
    }
}
