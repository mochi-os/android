// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mochios.android.api.ApiException
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.sync.ContactProperty
import org.mochios.android.util.NaturalCompare
import org.mochios.people.api.MergeSource
import org.mochios.people.model.Book
import org.mochios.people.model.Contact
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

data class ContactEditUiState(
    val isLoading: Boolean = false,
    /** A save, a create, a copy or a merge is under way. */
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
    val deleted: Boolean = false,

    /**
     * A contact the screen goes on as: the one a create or a copy made, or the
     * one a merge left standing.
     */
    val opened: String? = null,
    /** Leaving saved what was pending, so the screen can go. */
    val left: Boolean = false,

    /** Persons with an invitation out, which is how a pending invite is known. */
    val sent: Set<String> = emptySet(),
    val unfriendRequested: Boolean = false,
    /** The friend switch is mid-handshake: inviting, cancelling or unfriending. */
    val isToggling: Boolean = false,

    /** The list of contacts to merge with is open. */
    val mergeOpen: Boolean = false,
    /** The contacts that list offers, every one but this. */
    val mergeCandidates: List<Contact> = emptyList(),
    /** The contact being merged in. */
    val mergePending: String? = null,
    /** Why the list or the merge could not be read, shown in the list. */
    val mergeError: MochiError? = null,
)

/** What the server holds, as the form writes it: the line a change is measured from. */
private data class Baseline(val properties: List<ContactProperty>, val book: String)

/** How long typing rests before it is saved. */
private const val PAUSE = 1_000L

/**
 * Drives the contact editor in both modes: create when the route carries no
 * contact id, edit when it does.
 *
 * An edit saves as it goes, as the web's side panel does: a change once typing
 * rests for a second, a field when it is left, and whatever is pending when
 * the screen is left. Saves go one at a time, each sending the etag the one
 * before it was answered with. A new contact is saved by its Create, after
 * which the screen goes on as that contact's editor.
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

    /** The etag the next save sends, and the card it was answered with. */
    private var etag: String? = null
    private var baseline: Baseline? = null
    /** Saves, and everything that writes the card, go one at a time. */
    private val writing = Mutex()
    private var pause: Job? = null
    /** Counts the saves made, so a read begun before one lands is not taken over it. */
    private var writes = 0

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

    /** Whether the form holds something the server does not. */
    private fun dirty(): Boolean {
        val base = baseline ?: return false
        val form = _uiState.value.form
        return form.properties() != base.properties || form.book != base.book
    }

    private fun apply(contact: Contact) {
        val form = contactForm(contact.card, contact.book)
        etag = contact.etag
        baseline = Baseline(form.properties(), form.book)
        _uiState.value = _uiState.value.copy(contact = contact, form = form)
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val contact = repository.getContact(contactId)
                apply(contact)
                _uiState.value = _uiState.value.copy(isLoading = false, sent = fetchSent())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
            fetchBooks()
        }
    }

    /**
     * The contact and the sent list again. A card changed elsewhere replaces
     * the form while it holds nothing unsaved; an edit waits, its save is
     * refused, and the reload that follows reads the card.
     */
    private suspend fun refresh() {
        val before = writes
        try {
            val contact = repository.getContact(contactId)
            val sent = fetchSent()
            if (writes != before) return
            if (contact.etag != etag && !dirty()) {
                apply(contact)
            } else {
                _uiState.value = _uiState.value.copy(contact = contact)
            }
            _uiState.value = _uiState.value.copy(sent = sent)
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
            // A card filed nowhere is shown in the book it falls into, which
            // is not a change of its own.
            baseline?.let { base -> if (base.book.isBlank()) baseline = base.copy(book = book) }
        } catch (_: Exception) {
            // The book picker is one field of many: without the list it shows
            // nothing and the server files the contact in the default book.
        }
    }

    /** A change to the form, saved once typing rests. */
    fun updateForm(form: ContactForm) {
        _uiState.value = _uiState.value.copy(form = form)
        if (creating) return
        pause?.cancel()
        pause = viewModelScope.launch {
            delay(PAUSE)
            save()
        }
    }

    /** Saves what the form holds now, after any save under way: a field was left. */
    fun save() {
        pause?.cancel()
        if (creating) return
        viewModelScope.launch { writing.withLock { send() } }
    }

    private suspend fun send() {
        if (creating || !dirty()) return
        val form = _uiState.value.form
        // A contact keeps its name; a change without one waits for it.
        if (!form.valid) return
        val properties = form.properties()
        _uiState.value = _uiState.value.copy(isSaving = true, error = null, conflict = null)
        try {
            val contact = repository.updateContact(
                contact = contactId,
                etag = etag,
                properties = properties,
                book = form.book.ifBlank { null },
            )
            writes++
            etag = contact.etag
            baseline = Baseline(properties, form.book)
            _uiState.value = _uiState.value.copy(isSaving = false, contact = contact)
        } catch (e: Exception) {
            // 412 is the compare-and-swap losing to another device. The
            // server's message says so; the card it now holds replaces the
            // form, since saving over it is exactly what was refused.
            if (e is ApiException && e.code == 412) {
                _uiState.value = _uiState.value.copy(isSaving = false, conflict = e.message)
                baseline = null
                load()
            } else {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    /**
     * Leaving the screen saves what is pending first. A save that fails keeps
     * the screen, saying why; a change that cannot be saved, a contact left
     * with no name, is dropped.
     */
    fun leave() {
        pause?.cancel()
        viewModelScope.launch {
            writing.withLock { send() }
            val state = _uiState.value
            if (state.error == null && state.conflict == null) {
                _uiState.value = state.copy(left = true)
            }
        }
    }

    fun clearConflict() {
        _uiState.value = _uiState.value.copy(conflict = null)
    }

    /** Saves a new contact; the screen then goes on as its editor. */
    fun create() {
        val state = _uiState.value
        if (!creating || !state.form.valid || state.isSaving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val contact = repository.createContact(
                    properties = state.form.properties(),
                    book = state.form.book.ifBlank { null },
                )
                _uiState.value = _uiState.value.copy(isSaving = false, opened = contact.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    /**
     * Makes a copy of this contact at once, from the card as it stands, so
     * what the form does not show comes with it, and opens it.
     */
    fun copy() {
        if (creating || !_uiState.value.form.valid) return
        pause?.cancel()
        viewModelScope.launch {
            writing.withLock {
                send()
                val form = _uiState.value.form
                _uiState.value = _uiState.value.copy(isSaving = true, error = null)
                try {
                    val contact = repository.createContact(
                        properties = form.properties(),
                        book = form.book.ifBlank { null },
                        source = contactId,
                    )
                    _uiState.value = _uiState.value.copy(isSaving = false, opened = contact.id)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
                }
            }
        }
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
     * Merges [source] into this contact as soon as it is picked: the server
     * combines the two cards. The survivor is whichever is linked to a Mochi
     * person, so the screen may go on as the other contact. A refusal stays
     * in the list, saying why.
     */
    fun merge(source: String) {
        if (creating || _uiState.value.mergePending != null) return
        pause?.cancel()
        viewModelScope.launch {
            writing.withLock {
                send()
                _uiState.value = _uiState.value.copy(mergePending = source, mergeError = null)
                try {
                    val merge = repository.previewMerge(contactId, source)
                    val survivor = repository.updateContact(
                        contact = merge.contact.id,
                        etag = merge.contact.etag,
                        source = MergeSource(merge.source.id, merge.source.etag),
                    )
                    writes++
                    _uiState.value = _uiState.value.copy(mergeOpen = false, mergePending = null)
                    if (survivor.id == contactId) {
                        apply(survivor)
                    } else {
                        _uiState.value = _uiState.value.copy(opened = survivor.id)
                    }
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(mergePending = null, mergeError = e.toMochiError())
                }
            }
        }
    }

    fun requestDelete() {
        _uiState.value = _uiState.value.copy(deleteRequested = true)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(deleteRequested = false)
    }

    fun confirmDelete() {
        if (creating) return
        pause?.cancel()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            try {
                repository.deleteContact(contactId)
                // Nothing is left to save.
                baseline = null
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
