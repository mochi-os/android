// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

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
import org.mochios.people.model.ContactProperty
import org.mochios.people.model.PersonInformation
import org.mochios.people.model.RelationshipStatus
import org.mochios.people.model.User
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

/**
 * State of the add-contact screen. [addedUserIds] and [invitedUserIds] mark
 * rows acted on this visit, so they read as done without a fresh search.
 */
data class AddContactUiState(
    val searchQuery: String = "",
    val searchLoading: Boolean = false,
    val searchError: MochiError? = null,
    val searchResults: List<User> = emptyList(),
    val addedUserIds: Set<String> = emptySet(),
    val invitedUserIds: Set<String> = emptySet(),
    val friendedUserIds: Set<String> = emptySet(),
    val busyUserId: String? = null,
    val preview: AddContactPreview? = null,
    val actionError: MochiError? = null,
    val contactsChanged: Boolean = false,
)

/**
 * Profile-preview step; [targetUser] is the search row, shown until
 * [information] arrives.
 */
data class AddContactPreview(
    val targetUser: User,
    val information: PersonInformation? = null,
    val isLoading: Boolean = true,
    val error: MochiError? = null,
)

/** What the row's buttons offer for one directory hit. */
enum class AddContactState { NONE, IN_CONTACTS, FRIEND, INVITED, PENDING, SELF }

/**
 * The state a hit is in, given what the server said and what this visit has
 * already done to it.
 */
fun addContactState(
    user: User,
    added: Boolean = false,
    invited: Boolean = false,
    friended: Boolean = false,
): AddContactState = when {
    user.relationship == RelationshipStatus.SELF -> AddContactState.SELF
    user.relationship == RelationshipStatus.FRIEND || friended -> AddContactState.FRIEND
    user.relationship == RelationshipStatus.INVITED || invited -> AddContactState.INVITED
    user.relationship == RelationshipStatus.PENDING -> AddContactState.PENDING
    user.contact.isNotBlank() || added -> AddContactState.IN_CONTACTS
    else -> AddContactState.NONE
}

/** Drives the add-contact screen. */
@HiltViewModel
class AddContactViewModel @Inject constructor(
    private val repository: PeopleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddContactUiState())
    val uiState: StateFlow<AddContactUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var previewJob: Job? = null

    fun state(user: User): AddContactState = addContactState(
        user,
        added = user.id in _uiState.value.addedUserIds,
        invited = user.id in _uiState.value.invitedUserIds,
        friended = user.id in _uiState.value.friendedUserIds,
    )

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
                val results = repository.searchDirectory(query)
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

    /** Opens the profile preview for a tapped result and fetches its details. */
    fun openPreview(user: User) {
        previewJob?.cancel()
        _uiState.value = _uiState.value.copy(
            preview = AddContactPreview(targetUser = user, isLoading = true),
            actionError = null,
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
                        preview = current.copy(isLoading = false, error = e.toMochiError()),
                    )
                }
            }
        }
    }

    /** Back from the profile-preview step returns to the search list. */
    fun closePreview() {
        previewJob?.cancel()
        _uiState.value = _uiState.value.copy(preview = null, actionError = null)
    }

    /** Retry the [PersonInformation] fetch after a transient failure. */
    fun retryPreview() {
        val current = _uiState.value.preview ?: return
        openPreview(current.targetUser)
    }

    /** Saves the person as a contact, without inviting them. */
    fun addToContacts(user: User) {
        if (state(user) != AddContactState.NONE) return
        act(user) {
            repository.createContact(
                properties = listOf(ContactProperty("FN", emptyMap(), user.name)),
                person = user.id,
            )
            _uiState.value = _uiState.value.copy(
                addedUserIds = _uiState.value.addedUserIds + user.id,
                contactsChanged = true,
            )
        }
    }

    /** Sends a friendship invitation, or accepts one already waiting. */
    fun invite(user: User) {
        val current = state(user)
        if (current == AddContactState.FRIEND ||
            current == AddContactState.INVITED ||
            current == AddContactState.SELF
        ) {
            return
        }
        act(user) {
            // Accepting makes a friend straight away, so the list behind this
            // screen is now stale; a sent invitation only becomes a friend once
            // the other side accepts.
            if (current == AddContactState.PENDING) {
                repository.acceptInvite(user.id)
                _uiState.value = _uiState.value.copy(
                    contactsChanged = true,
                    preview = null,
                    friendedUserIds = _uiState.value.friendedUserIds + user.id,
                )
            } else {
                repository.inviteFriend(user.id, user.name)
                _uiState.value = _uiState.value.copy(
                    invitedUserIds = _uiState.value.invitedUserIds + user.id,
                    // Inviting creates or links the contact server-side, so the
                    // list behind this screen has a new row either way.
                    contactsChanged = true,
                )
            }
        }
    }

    private fun act(user: User, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyUserId = user.id, actionError = null)
            try {
                block()
                _uiState.value = _uiState.value.copy(busyUserId = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    busyUserId = null,
                    actionError = e.toMochiError(),
                )
            }
        }
    }
}
