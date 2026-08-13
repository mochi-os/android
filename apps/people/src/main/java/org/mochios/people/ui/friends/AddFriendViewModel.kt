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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.people.model.PersonInformation
import org.mochios.people.model.RelationshipStatus
import org.mochios.people.model.User
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

/**
 * State of the add-friend screen: a debounced people search whose results lead
 * to a profile-preview step before any invite is sent.
 *
 * @property searchQuery what the user has typed into the search field.
 * @property searchLoading true while the debounced search is in flight.
 * @property searchError what stopped the last search, if anything.
 * @property searchResults users matching [searchQuery].
 * @property invitedUserIds users invited during this visit, so their rows read
 *   as sent without waiting for a fresh search.
 * @property addingUserId the user whose invite is in flight, if any.
 * @property preview the profile-preview step; null while the search list shows.
 * @property inviteError what stopped the last invite, if anything.
 * @property friendsChanged true once an accepted invite has changed the
 *   friends list, so leaving the screen can reload it.
 */
data class AddFriendUiState(
    val searchQuery: String = "",
    val searchLoading: Boolean = false,
    val searchError: MochiError? = null,
    val searchResults: List<User> = emptyList(),
    val invitedUserIds: Set<String> = emptySet(),
    val addingUserId: String? = null,
    val preview: AddFriendPreview? = null,
    val inviteError: MochiError? = null,
    val friendsChanged: Boolean = false,
)

/**
 * Second stage of the add-friend flow. Carries the user the search row was
 * built from (so the screen still has display name / fingerprint while the
 * details fetch is in flight) plus the fetched [PersonInformation] (banner,
 * avatar, bio, accent). [isLoading] is true between tap and response; [error]
 * is set if the fetch failed so the user can either retry or go back to the
 * search list.
 *
 * @property targetUser the user the search row was built from.
 * @property information the fetched profile, once it arrives.
 * @property isLoading true between the tap and the response.
 * @property error what stopped the fetch, if anything.
 */
data class AddFriendPreview(
    val targetUser: User,
    val information: PersonInformation? = null,
    val isLoading: Boolean = true,
    val error: MochiError? = null,
)

/** Drives the add-friend screen. */
@HiltViewModel
class AddFriendViewModel @Inject constructor(
    private val repository: PeopleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddFriendUiState())
    val uiState: StateFlow<AddFriendUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var previewJob: Job? = null

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchResults = emptyList(),
                searchLoading = false,
                searchError = null,
            )
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            _uiState.value = _uiState.value.copy(searchLoading = true, searchError = null)
            try {
                val results = repository.searchFriends(query)
                _uiState.value = _uiState.value.copy(
                    searchResults = results,
                    searchLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    searchLoading = false,
                    searchError = e.toMochiError(),
                )
            }
        }
    }

    /** Runs the current query again after a transient failure. */
    fun retrySearch() {
        val query = _uiState.value.searchQuery
        if (query.isBlank()) return
        updateSearchQuery(query)
    }

    /**
     * Tap on a search result. Moves from the list step to the profile-preview
     * step and kicks off a [PeopleRepository.getPersonInformation] fetch.
     * Mirrors the web `startConnect` flow but without the "has-profile-content"
     * short-circuit — Android always shows the preview so the user has a
     * consistent confirmation step (the web's skip-on-empty is an optimisation
     * for keyboard-heavy desktop use).
     */
    fun openPreview(user: User) {
        previewJob?.cancel()
        _uiState.value = _uiState.value.copy(
            preview = AddFriendPreview(targetUser = user, isLoading = true),
            inviteError = null,
        )
        previewJob = viewModelScope.launch {
            try {
                val info = repository.getPersonInformation(user.id)
                val current = _uiState.value.preview
                if (current != null && current.targetUser.id == user.id) {
                    _uiState.value = _uiState.value.copy(
                        preview = current.copy(information = info, isLoading = false),
                    )
                }
            } catch (e: Exception) {
                val current = _uiState.value.preview
                if (current != null && current.targetUser.id == user.id) {
                    _uiState.value = _uiState.value.copy(
                        preview = current.copy(
                            isLoading = false,
                            error = e.toMochiError(),
                        ),
                    )
                }
            }
        }
    }

    /** Back from the profile-preview step returns to the search list. */
    fun closePreview() {
        previewJob?.cancel()
        _uiState.value = _uiState.value.copy(preview = null, inviteError = null)
    }

    /** Retry the [PersonInformation] fetch after a transient failure. */
    fun retryPreview() {
        val current = _uiState.value.preview ?: return
        openPreview(current.targetUser)
    }

    /**
     * Send an invite, or accept an incoming one when [user] has already invited
     * the local user. Users who are already friends, already invited, or the
     * local user themselves no-op — the buttons that reach here are disabled in
     * those states.
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
            _uiState.value = _uiState.value.copy(addingUserId = user.id, inviteError = null)
            try {
                // Accepting makes a friend straight away, so the list behind
                // this screen is now stale; a sent invitation only becomes a
                // friend once the other side accepts, and changes nothing here.
                val accepted = status == RelationshipStatus.PENDING
                if (accepted) {
                    repository.acceptInvite(user.id)
                } else {
                    repository.createFriend(user.id, user.name)
                }
                _uiState.value = _uiState.value.copy(
                    addingUserId = null,
                    invitedUserIds = _uiState.value.invitedUserIds + user.id,
                    preview = null,
                    friendsChanged = _uiState.value.friendsChanged || accepted,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    addingUserId = null,
                    inviteError = e.toMochiError(),
                )
            }
        }
    }
}
