// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.ApiException
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.NaturalCompare
import org.mochios.people.api.MergeSource
import org.mochios.people.model.Book
import org.mochios.people.model.Contact
import org.mochios.people.model.ContactMerge
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

data class ContactEditUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val form: ContactForm = ContactForm(),
    val contact: Contact? = null,
    val books: List<Book> = emptyList(),
    val error: MochiError? = null,

    /**
     * The server's message when a save lost the compare-and-swap: another
     * device wrote the card first, so the form has been reloaded from it.
     */
    val conflict: String? = null,

    val deleteRequested: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,

    /** Persons with an invitation out, which is how a pending invite is known. */
    val sent: Set<String> = emptySet(),
    val unfriendRequested: Boolean = false,
    /** The friend switch is mid-handshake: inviting, cancelling or unfriending. */
    val isToggling: Boolean = false,

    /**
     * The editor has turned into one for a new contact filled from this one,
     * the edits not yet saved included; saving creates it from this card.
     */
    val copying: Boolean = false,

    /**
     * A merge read from the server: the editor then shows the contact that
     * survives, holding both cards, and saving writes it and deletes the other.
     */
    val merge: ContactMerge? = null,
    /** The list of contacts to merge with is open. */
    val mergeOpen: Boolean = false,
    /** The contacts that list offers, every one but this. */
    val mergeCandidates: List<Contact> = emptyList(),
    /** The contact whose merge is being read. */
    val mergePending: String? = null,
    /** Why the list or the merge could not be read, shown in the list. */
    val mergeError: MochiError? = null,
)

/**
 * Drives the contact editor in both modes: create when the route carries no
 * contact id, edit when it does.
 *
 * The form is the managed half of the card. Everything else the card holds is
 * left alone by never being sent, and inside a managed property the components
 * with no field — `N`'s prefix, `ADR`'s post-office box — ride on the form and
 * are written back unchanged.
 */
@HiltViewModel
class ContactEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PeopleRepository,
) : ViewModel() {

    /** The contact being edited; empty in create mode. */
    val contactId: String = savedStateHandle.get<String>("id").orEmpty()

    val creating: Boolean = contactId.isBlank()

    /** The address book a new contact was started from; blank for the default. */
    private val start: String = savedStateHandle.get<String>("book").orEmpty()

    private val _uiState = MutableStateFlow(ContactEditUiState())
    val uiState: StateFlow<ContactEditUiState> = _uiState.asStateFlow()

    init {
        if (creating) {
            loadBooks()
        } else {
            load()
            // The friend switch's handshake lands through other screens too - the
            // search that links the card, an accept arriving - so follow the
            // repository's change signal rather than the user's own actions only.
            viewModelScope.launch { repository.contactsChanged.collect { refresh() } }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val contact = repository.getContact(contactId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    contact = contact,
                    form = contactForm(contact.card, contact.book),
                    sent = fetchSent(),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
            fetchBooks()
        }
    }

    /** The contact and the sent list again, leaving the form the user may be typing in alone. */
    private suspend fun refresh() {
        try {
            val contact = repository.getContact(contactId)
            _uiState.value = _uiState.value.copy(contact = contact, sent = fetchSent())
        } catch (_: Exception) {
            // A failed refresh leaves the last state showing; the next action reloads.
        }
    }

    private suspend fun fetchSent(): Set<String> = try {
        repository.listContacts().sent.map { it.id }.toSet()
    } catch (_: Exception) {
        emptySet()
    }

    /** The friend switch turned on for a linked contact: invite. */
    fun invite() {
        val contact = _uiState.value.contact ?: return
        if (contact.person.isBlank() || contact.friend) return
        toggle { repository.inviteFriend(contact.person, contact.directory.ifBlank { contact.name }) }
    }

    /** The friend switch turned off while the invitation is out: cancel it. */
    fun cancelInvite() {
        val contact = _uiState.value.contact ?: return
        if (contact.person.isBlank() || contact.friend) return
        toggle { repository.removeFriend(contact.person) }
    }

    fun requestUnfriend() {
        _uiState.value = _uiState.value.copy(unfriendRequested = true, error = null)
    }

    fun cancelUnfriend() {
        _uiState.value = _uiState.value.copy(unfriendRequested = false)
    }

    fun confirmUnfriend() {
        val contact = _uiState.value.contact ?: return
        if (_uiState.value.isToggling) return
        toggle(settle = ContactEditUiState::unfriended) { repository.removeFriend(contact.person) }
    }

    private fun toggle(
        settle: ContactEditUiState.(MochiError?) -> ContactEditUiState = ContactEditUiState::toggled,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isToggling = true, error = null)
            val failure = try {
                block()
                refresh()
                null
            } catch (e: Exception) {
                e.toMochiError()
            }
            _uiState.value = _uiState.value.settle(failure)
        }
    }

    private fun loadBooks() {
        viewModelScope.launch { fetchBooks() }
    }

    private suspend fun fetchBooks() {
        try {
            val books = repository.loadBooks()
            val form = _uiState.value.form
            // A new contact lands in the book it was started from, else the
            // default one, unless the user picks another; the field shows where
            // it is going from the start.
            val book = form.book.ifBlank { startBook(books, start) }
            _uiState.value = _uiState.value.copy(books = books, form = form.copy(book = book))
        } catch (_: Exception) {
            // The book picker is one field of many: without the list it shows
            // nothing and the server files the contact in the default book.
        }
    }

    fun updateForm(form: ContactForm) {
        _uiState.value = _uiState.value.copy(form = form)
    }

    fun clearConflict() {
        _uiState.value = _uiState.value.copy(conflict = null)
    }

    /** Turns the editor into one for a copy of this contact, keeping what has been typed. */
    fun copy() {
        if (creating) return
        _uiState.value = _uiState.value.copy(copying = true, error = null)
    }

    /** Opens the list of contacts to merge this one with. */
    fun openMerge() {
        if (creating) return
        _uiState.value = _uiState.value.copy(mergeOpen = true, mergeError = null)
        viewModelScope.launch {
            try {
                val contacts = repository.listContacts().contacts
                    .filter { it.id != contactId }
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(mergeCandidates = contacts)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(mergeError = e.toMochiError())
            }
        }
    }

    fun closeMerge() {
        _uiState.value = _uiState.value.copy(mergeOpen = false, mergePending = null, mergeError = null)
    }

    /**
     * Reads the merge of this contact with [source]. The form then shows the
     * contact that survives, which is whichever is linked to a Mochi person,
     * filled with both cards' details; a refusal stays in the list, saying why.
     */
    fun merge(source: String) {
        if (creating || _uiState.value.mergePending != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(mergePending = source, mergeError = null)
            try {
                val merge = repository.previewMerge(contactId, source)
                _uiState.value = _uiState.value.copy(
                    merge = merge,
                    mergeOpen = false,
                    mergePending = null,
                    form = contactForm(merge.contact.card, merge.contact.book),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(mergePending = null, mergeError = e.toMochiError())
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.form.valid || state.isSaving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, conflict = null)
            try {
                val merge = state.merge
                if (merge != null) {
                    repository.updateContact(
                        contact = merge.contact.id,
                        etag = merge.contact.etag,
                        properties = state.form.properties(),
                        book = state.form.book.ifBlank { null },
                        source = MergeSource(merge.source.id, merge.source.etag),
                    )
                    _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
                } else if (creating || state.copying) {
                    repository.createContact(
                        properties = state.form.properties(),
                        book = state.form.book.ifBlank { null },
                        source = contactId.takeIf { state.copying },
                    )
                    _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
                } else {
                    val contact = repository.updateContact(
                        contact = contactId,
                        etag = state.contact?.etag,
                        properties = state.form.properties(),
                        book = state.form.book.ifBlank { null },
                    )
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saved = true,
                        contact = contact,
                        form = contactForm(contact.card, contact.book),
                    )
                }
            } catch (e: Exception) {
                // 412 is the compare-and-swap losing to another device. The
                // server's message says so; the card it now holds replaces the
                // form, since saving over it is exactly what was refused. A
                // merge read from cards since changed is dropped with it.
                if (e is ApiException && e.code == 412) {
                    _uiState.value = _uiState.value.copy(isSaving = false, conflict = e.message, merge = null)
                    load()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = e.toMochiError(),
                    )
                }
            }
        }
    }

    /** Clears the flag the screen navigates on, so a later edit can set it again. */
    fun consumeSaved() {
        _uiState.value = _uiState.value.copy(saved = false)
    }

    fun requestDelete() {
        _uiState.value = _uiState.value.copy(deleteRequested = true)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(deleteRequested = false)
    }

    fun confirmDelete() {
        if (creating || _uiState.value.copying || _uiState.value.merge != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            try {
                repository.deleteContact(contactId)
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    deleteRequested = false,
                    deleted = true,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    deleteRequested = false,
                    error = e.toMochiError(),
                )
            }
        }
    }
}

/** The state a friend-switch request leaves: its failure, if it had one. */
internal fun ContactEditUiState.toggled(failure: MochiError?): ContactEditUiState =
    copy(isToggling = false, error = failure)

/**
 * The state an unfriend leaves. The confirmation closes once it is done, and
 * stays up after a failure, saying why, so it can be tried again.
 */
internal fun ContactEditUiState.unfriended(failure: MochiError?): ContactEditUiState =
    if (failure == null) {
        copy(isToggling = false, error = null, unfriendRequested = false)
    } else {
        copy(isToggling = false, error = failure)
    }

/** The book a new contact goes in: the one it was started from, else the default one. */
internal fun startBook(books: List<Book>, start: String): String =
    (books.firstOrNull { it.id == start } ?: books.firstOrNull { it.isDefault })?.id.orEmpty()
