// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.friends

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
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.NaturalCompare
import org.mochios.people.model.Friend
import org.mochios.people.model.PersonInformation
import org.mochios.people.model.RelationshipStatus
import org.mochios.people.model.User
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

    // Add-friend dialog
    val addDialogOpen: Boolean = false,
    val addSearchQuery: String = "",
    val addSearchLoading: Boolean = false,
    val addSearchError: MochiError? = null,
    val addSearchResults: List<User> = emptyList(),
    val invitedUserIds: Set<String> = emptySet(),
    val addingUserId: String? = null,

    /**
     * Profile-preview substate of the add-friend dialog. Null when the dialog
     * is on the search results step; non-null after the user taps a result and
     * the second-stage "peek before inviting" view replaces the list. Mirrors
     * the web add-friend-dialog's two-stage flow.
     */
    val addPreview: AddFriendPreview? = null,

    /**
     * One-shot welcome banner shown on first visit. True only after
     * `-/welcome` reports `seen == false`; flipped back to false (and persisted
     * server-side via `-/welcome/seen`) when the user dismisses it.
     */
    val showWelcome: Boolean = false,
)

/**
 * Second stage of the add-friend dialog. Carries the user the search row was
 * built from (so the dialog still has display name / fingerprint while the
 * details fetch is in flight) plus the fetched `PersonInformation` (banner,
 * avatar, bio, accent). `isLoading` is true between tap and response; `error`
 * is set if the fetch failed so the user can either retry or go back to the
 * search list.
 */
data class AddFriendPreview(
    val targetUser: User,
    val information: PersonInformation? = null,
    val isLoading: Boolean = true,
    val error: MochiError? = null,
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

    private var searchJob: Job? = null
    private var previewJob: Job? = null

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

    // ---------------- add-friend dialog ----------------

    fun openAddDialog() {
        _uiState.value = _uiState.value.copy(
            addDialogOpen = true,
            addSearchQuery = "",
            addSearchResults = emptyList(),
            addSearchError = null,
            invitedUserIds = emptySet(),
            addingUserId = null,
            addPreview = null,
        )
    }

    fun closeAddDialog() {
        searchJob?.cancel()
        previewJob?.cancel()
        _uiState.value = _uiState.value.copy(
            addDialogOpen = false,
            addSearchQuery = "",
            addSearchResults = emptyList(),
            addSearchError = null,
            addSearchLoading = false,
            invitedUserIds = emptySet(),
            addingUserId = null,
            addPreview = null,
        )
    }

    /**
     * Tap on a search result. Transitions the dialog from the list step to
     * the profile-preview step and kicks off a [PeopleRepository.getPersonInformation]
     * fetch. Mirrors the web `startConnect` flow but without the
     * "has-profile-content" short-circuit — Android always shows the preview
     * so the user has a consistent confirmation step (the web's skip-on-empty
     * is an optimisation for keyboard-heavy desktop use).
     */
    fun openAddPreview(user: User) {
        previewJob?.cancel()
        _uiState.value = _uiState.value.copy(
            addPreview = AddFriendPreview(targetUser = user, isLoading = true),
        )
        previewJob = viewModelScope.launch {
            try {
                val info = repository.getPersonInformation(user.id)
                val current = _uiState.value.addPreview
                if (current != null && current.targetUser.id == user.id) {
                    _uiState.value = _uiState.value.copy(
                        addPreview = current.copy(information = info, isLoading = false),
                    )
                }
            } catch (e: Exception) {
                val current = _uiState.value.addPreview
                if (current != null && current.targetUser.id == user.id) {
                    _uiState.value = _uiState.value.copy(
                        addPreview = current.copy(
                            isLoading = false,
                            error = e.toMochiError(),
                        ),
                    )
                }
            }
        }
    }

    /** Back-button from the profile-preview step returns to the search list. */
    fun closeAddPreview() {
        previewJob?.cancel()
        _uiState.value = _uiState.value.copy(addPreview = null)
    }

    /** Retry the [PersonInformation] fetch after a transient failure. */
    fun retryAddPreview() {
        val current = _uiState.value.addPreview ?: return
        openAddPreview(current.targetUser)
    }

    fun updateAddSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(addSearchQuery = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                addSearchResults = emptyList(),
                addSearchLoading = false,
                addSearchError = null,
            )
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.value = _uiState.value.copy(addSearchLoading = true, addSearchError = null)
            try {
                val results = repository.searchFriends(query)
                _uiState.value = _uiState.value.copy(
                    addSearchResults = results,
                    addSearchLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    addSearchLoading = false,
                    addSearchError = e.toMochiError(),
                )
            }
        }
    }

    fun retryAddSearch() {
        val q = _uiState.value.addSearchQuery
        if (q.isBlank()) return
        updateAddSearchQuery(q)
    }

    /**
     * Send an invite (or accept an incoming invite if [user.relationshipStatus]
     * is `pending`). "self" results no-op with a toast — the user can't friend
     * themselves.
     */
    fun addFriend(user: User) {
        val status = if (user.id in _uiState.value.invitedUserIds) {
            RelationshipStatus.INVITED
        } else {
            user.relationshipStatus
        }
        if (status == RelationshipStatus.FRIEND ||
            status == RelationshipStatus.INVITED ||
            status == RelationshipStatus.SELF
        ) {
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(addingUserId = user.id)
            try {
                if (status == RelationshipStatus.PENDING) {
                    repository.acceptInvite(user.id)
                    _uiState.value = _uiState.value.copy(
                        addingUserId = null,
                        invitedUserIds = _uiState.value.invitedUserIds + user.id,
                        addPreview = null,
                    )
                    refresh()
                } else {
                    repository.createFriend(user.id, user.name)
                    _uiState.value = _uiState.value.copy(
                        addingUserId = null,
                        invitedUserIds = _uiState.value.invitedUserIds + user.id,
                        addPreview = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    addingUserId = null,
                    error = e.toMochiError(),
                )
            }
        }
    }
}
