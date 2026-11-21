// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
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
import org.mochios.android.auth.SessionManager
import org.mochios.android.sync.ContactProperty
import org.mochios.people.R
import org.mochios.people.api.ContactsListResponse
import org.mochios.people.api.StandingResponse
import org.mochios.people.model.PersonInformation
import org.mochios.people.repository.PeopleRepository
import java.security.MessageDigest
import javax.inject.Inject

sealed class FriendState {
    object Self : FriendState()
    object Friend : FriendState()

    /** A contact of the viewer's, but not a friend. */
    object Contact : FriendState()
    data class InvitedByThem(val person: String) : FriendState()
    object InvitedThem : FriendState()
    object NotFriend : FriendState()

    /** The viewer's contacts could not be read, so where they stand is not known. */
    object Unknown : FriendState()
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
 * Where the viewer stands with [person], as their server answers for that one
 * person: a friend, an invitation waiting either way, a contact who is not
 * one, or a stranger. [me] is the viewer's own identity.
 */
fun friendState(standing: StandingResponse, person: String, me: String? = null): FriendState {
    if (person.isBlank()) return FriendState.NotFriend
    if (me != null && me == person) return FriendState.Self
    if (standing.friend) return FriendState.Friend
    if (standing.received) return FriendState.InvitedByThem(person)
    if (standing.sent) return FriendState.InvitedThem
    if (standing.contact.isNotBlank()) return FriendState.Contact
    return FriendState.NotFriend
}

/** The letters core writes an entity's fingerprint in. */
private const val FINGERPRINT_LETTERS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz"

/** The nine-letter fingerprint core derives from an entity id. */
internal fun fingerprintOf(id: String): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
    return String(CharArray(9) { index -> FINGERPRINT_LETTERS[(hash[index].toInt() and 0xff) % FINGERPRINT_LETTERS.length] })
}

/** A route that names a person by fingerprint rather than by id. */
internal fun isFingerprint(route: String): Boolean =
    route.length == 9 && route.all { it in FINGERPRINT_LETTERS }

/**
 * Whether [info] is the person [route] asked for: their id, or the
 * fingerprint derived from it. The answer is their server's, relayed, so it
 * is the one place another identity could be named.
 */
internal fun answersFor(route: String, info: PersonInformation): Boolean =
    info.id.isNotBlank() && (info.id == route || (isFingerprint(route) && fingerprintOf(info.id) == route))

/**
 * Where the viewer stands with a person, read off the contacts response: a
 * friend, a contact who is not one, an invitation waiting either way, or a
 * stranger. [me] is the viewer's own identity. For a server that predates
 * `-/friends/standing`.
 */
fun friendState(
    contacts: ContactsListResponse,
    person: String,
    me: String? = null,
): FriendState {
    if (person.isBlank()) return FriendState.NotFriend
    if (me != null && me == person) return FriendState.Self
    // A plain address-book entry carries no entity id, so matching on an empty
    // person would claim the first one of those as this profile's contact.
    val contact = contacts.contacts.firstOrNull { it.person == person }
    if (contact != null && contact.friend) return FriendState.Friend
    contacts.received.firstOrNull { it.id == person }?.let {
        return FriendState.InvitedByThem(it.id)
    }
    contacts.sent.firstOrNull { it.id == person }?.let { return FriendState.InvitedThem }
    if (contact != null) return FriendState.Contact
    return FriendState.NotFriend
}

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
            _uiState.update { state -> state.copy(isLoading = true, error = null) }
            // Where the viewer stands is asked for beside the person, not after
            // them: the person's information is a relay to their server when
            // they live elsewhere, about half a second. A page opened by
            // fingerprint learns the id to ask with from the answer. Best-effort,
            // so a failed read of it still shows the person.
            val standing = if (isFingerprint(personId)) null else async { standingOrNull(personId) }
            try {
                val info = repository.getPersonInformation(personId)
                // The answer is relayed from the person's server, which could
                // name someone else; the page is the person asked for or none.
                if (!answersFor(personId, info)) {
                    standing?.cancel()
                    _uiState.update { state ->
                        state.copy(isLoading = false, error = MochiError.Local(R.string.people_person_unavailable))
                    }
                    return@launch
                }
                val read = if (standing != null) standing.await() else standingOrNull(info.id)
                val state = friendStateOf(info.id, read)
                _uiState.update { current -> current.copy(isLoading = false, info = info, friendState = state) }
            } catch (e: Exception) {
                standing?.cancel()
                if (e is CancellationException) throw e
                _uiState.update { state -> state.copy(isLoading = false, error = e.toMochiError()) }
            }
        }
    }

    /** Where the viewer stands with [person], or null for a read that failed. */
    private suspend fun standingOrNull(person: String): FriendState? = try {
        resolveFriendState(person)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Where the viewer stands with [personEntityId], or not known for a read that failed. */
    private suspend fun friendStateOf(personEntityId: String, read: FriendState?): FriendState {
        val me = sessionManager.getBoundIdentity()
        if (me != null && me == personEntityId) return FriendState.Self
        return read ?: FriendState.Unknown
    }

    /**
     * Where the viewer stands with [personEntityId], asked for that person
     * alone; a server that predates the question is read from the whole list.
     */
    private suspend fun resolveFriendState(personEntityId: String): FriendState {
        val me = sessionManager.getBoundIdentity()
        if (me != null && me == personEntityId) return FriendState.Self
        val standing = try {
            repository.getStanding(personEntityId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return if (standing != null) {
            friendState(standing, personEntityId, me)
        } else {
            friendState(repository.listContacts(), personEntityId, me)
        }
    }

    /** Saves the person as a contact, without inviting them. */
    fun addContact() {
        val info = _uiState.value.info ?: return
        if (_uiState.value.friendState != FriendState.NotFriend) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMutating = true, error = null)
            try {
                repository.createContact(
                    properties = listOf(ContactProperty("FN", emptyMap(), info.name)),
                    person = info.id,
                )
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

    /** Sends a friendship invitation to a person already in contacts or not. */
    fun invite() {
        val info = _uiState.value.info ?: return
        val state = _uiState.value.friendState
        if (state == FriendState.Friend || state == FriendState.InvitedThem ||
            state == FriendState.Self
        ) {
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMutating = true, error = null)
            try {
                repository.inviteFriend(info.id, info.name)
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

    fun acceptInvite() {
        val state = _uiState.value.friendState
        if (state !is FriendState.InvitedByThem) return
        val info = _uiState.value.info ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMutating = true, error = null)
            try {
                repository.acceptInvite(state.person)
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
                repository.ignoreInvite(state.person)
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
