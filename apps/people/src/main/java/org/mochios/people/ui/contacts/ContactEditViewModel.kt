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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mochios.android.api.ApiException
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.sync.ContactProperty
import org.mochios.android.util.NaturalCompare
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.ui.components.SaveStatus
import org.mochios.people.api.HandleLookup
import org.mochios.people.api.MergeSource
import org.mochios.people.model.Book
import org.mochios.people.model.Contact
import org.mochios.people.model.XmppContact
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

data class ContactEditUiState(
    val isLoading: Boolean = false,
    /** A save, a create, a copy or a merge is under way. */
    val isSaving: Boolean = false,
    /** How an edit's saving stands, shown beside the title: nothing while a change waits for typing to rest. */
    val save: SaveStatus = SaveStatus.Idle,
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

    /** A contact the screen goes on as: the one a merge left standing. */
    val opened: String? = null,
    /** A copy of this contact to open as a new one: the contact it copies. */
    val copied: String? = null,
    /** Leaving saved what was pending, or Create made the contact, so the screen can go. */
    val left: Boolean = false,
    /**
     * Why the save leaving made failed: the screen stays, offering to leave
     * without the edit.
     */
    val unsaved: MochiError? = null,

    /** Persons with an invitation out, which is how a pending invite is known. */
    val sent: Set<String> = emptySet(),
    val unfriendRequested: Boolean = false,
    /** The friend switch is mid-handshake: inviting, cancelling or unfriending. */
    val isToggling: Boolean = false,
    /**
     * Where a switch was moved to while its change is on its way, by
     * [FRIEND_SWITCH] or an XMPP contact's [key]: it moves when tapped, and
     * goes back to what the server holds once it answers.
     */
    val switching: Map<String, Boolean> = emptyMap(),

    /** The list of contacts to merge with is open. */
    val mergeOpen: Boolean = false,
    /** The contacts that list offers, every one but this. */
    val mergeCandidates: List<Contact> = emptyList(),
    /** The contact being merged in. */
    val mergePending: String? = null,
    /** Why the list or the merge could not be read, shown in the list. */
    val mergeError: MochiError? = null,

    /**
     * What the networks show of the handles on the card, by handle as typed;
     * one the network does not answer for is absent.
     */
    val handles: Map<String, HandleLookup> = emptyMap(),
)

/** What the server holds, as the form writes it: the line a change is measured from. */
private data class Baseline(val properties: List<ContactProperty>, val book: String)

/** What a save of the form came to. */
private enum class Outcome {
    /** The form held nothing the server does not. */
    NOTHING,

    /** A change that cannot be saved, a contact left with no name, went unsent. */
    DROPPED,
    SAVED,
    FAILED,

    /** Another device wrote the card first: the form is read again. */
    CONFLICTED,
}

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

    /**
     * The contact a new one copies, whose card it opens on and starts from,
     * so what the form does not show comes with it; blank for none.
     */
    private val source: String = savedStateHandle.get<String>("copy").orEmpty()

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
    /** The handles already asked of their networks. */
    private val asked = mutableSetOf<String>()
    /** The pending or in-flight [refreshLatest]; cancelled when a newer one starts. */
    private var refreshJob: Job? = null
    /** Set by a save of this screen's own, whose change signal has nothing to read. */
    private var saved = false

    init {
        if (creating && source.isNotBlank()) {
            copying()
        } else if (creating) {
            loadBooks()
        } else {
            load()
            // The friend switch's handshake lands through other screens too - the
            // search that links the card, an accept arriving - so follow the
            // repository's change signal rather than the user's own actions only.
            viewModelScope.launch { repository.contactsChanged.collect { refreshLatest() } }
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
        _uiState.update { state -> state.copy(contact = contact, form = form) }
    }

    /**
     * Opens the form on the card a copy is made from, as it is stored. The
     * form shows once that card is read, so nothing typed before is lost.
     */
    private fun copying() {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(isLoading = true, error = null) }
            try {
                val contact = repository.getContact(source)
                val form = contactForm(contact.card, contact.book)
                _uiState.update { state -> state.copy(isLoading = false, form = form) }
            } catch (e: Exception) {
                _uiState.update { state -> state.copy(isLoading = false, error = e.toMochiError()) }
            }
            fetchBooks()
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(isLoading = true, error = null) }
            try {
                val contact = repository.getContact(contactId)
                // Read before the form is filled and written over the state as
                // it then is, so nothing typed once the form shows is lost.
                val sent = sentOf(contact)
                apply(contact)
                _uiState.update { state -> state.copy(isLoading = false, sent = sent) }
            } catch (e: Exception) {
                _uiState.update { state -> state.copy(isLoading = false, error = e.toMochiError()) }
            }
            fetchBooks()
        }
    }

    /**
     * The contact and its invitation again. A card changed elsewhere replaces
     * the form while it holds nothing unsaved; an edit waits, its save is
     * refused, and the reload that follows reads the card. A card the server
     * only restamped - a friend's photo, a directory name, the friend switch -
     * still holds what the edit was made from, so the edit goes on against
     * the new etag.
     */
    private suspend fun refresh() {
        val before = writes
        try {
            val contact = repository.getContact(contactId)
            val sent = sentOf(contact)
            if (writes != before) return
            val base = baseline
            when {
                contact.etag == etag -> Unit
                !dirty() -> apply(contact)
                base != null && restamped(contact, base) -> etag = contact.etag
            }
            _uiState.update { state -> state.copy(contact = contact, sent = sent) }
        } catch (_: Exception) {
            // A failed refresh leaves the last state showing; the next action reloads.
        }
    }

    /** Whether [contact], under a new etag, holds the card and book [base] was read from. */
    private fun restamped(contact: Contact, base: Baseline): Boolean {
        val fetched = contactForm(contact.card, contact.book)
        return fetched.properties() == base.properties && contact.book.ifBlank { base.book } == base.book
    }

    /**
     * [refresh] after [REFRESH_DEBOUNCE], replacing the one pending, so a
     * change signal and the action that sent it make one read. A save of this
     * screen's own already holds the card, and a deleted contact has none.
     */
    private fun refreshLatest(): Job {
        refreshJob?.cancel()
        val job = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            val state = _uiState.value
            if (state.isDeleting || state.deleted) {
                return@launch
            }
            if (saved) {
                saved = false
                return@launch
            }
            refresh()
        }
        refreshJob = job
        return job
    }

    /** Refreshes, and waits for the refresh that replaced it, if one did. */
    private suspend fun awaitRefresh() {
        saved = false
        var job = refreshLatest()
        while (true) {
            job.join()
            val latest = refreshJob
            if (latest == null || latest === job) {
                return
            }
            job = latest
        }
    }

    /**
     * The persons with the user's invitation out, as far as this contact
     * goes: its own answer says, and a server that predates that is asked for
     * the whole list.
     */
    private suspend fun sentOf(contact: Contact): Set<String> {
        val invited = contact.invited ?: return fetchSent()
        return if (invited && contact.person.isNotBlank()) setOf(contact.person) else emptySet()
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
        toggle(FRIEND_SWITCH, true) { repository.inviteFriend(contact.person, contact.directory.ifBlank { contact.name }) }
    }

    /**
     * An XMPP switch: on invites the address through the account, or accepts
     * its request; off withdraws the invitation or ends the contact there.
     */
    fun toggleXmpp(entry: XmppContact, on: Boolean) {
        val contact = _uiState.value.contact ?: return
        val person = "xmpp:" + entry.address
        toggle(entry.key, on) {
            if (on) {
                repository.inviteFriend(person, contact.name, account = entry.account)
            } else {
                repository.removeFriend(person, entry.account)
            }
        }
    }

    /**
     * Asks the network what it shows of [handle], once each: a typo shows as
     * no one before the card is saved. Too short a handle, or one with a space
     * in it, is not asked.
     */
    fun lookup(handle: String) {
        val key = handle.trim()
        if (key.length <= 3 || ' ' in key || !asked.add(key)) return
        viewModelScope.launch {
            try {
                val found = repository.lookupHandle(key)
                if (found.name.isNotBlank() || found.address.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(handles = _uiState.value.handles + (key to found))
                }
            } catch (_: Exception) {
                // Asked again the next time it is typed.
                asked.remove(key)
            }
        }
    }

    /** The friend switch turned off while the invitation is out: cancel it. */
    fun cancelInvite() {
        val contact = _uiState.value.contact ?: return
        if (contact.person.isBlank() || contact.friend) return
        toggle(FRIEND_SWITCH, false) { repository.removeFriend(contact.person) }
    }

    fun requestUnfriend() {
        _uiState.value = _uiState.value.copy(unfriendRequested = true, error = null)
    }

    fun cancelUnfriend() {
        _uiState.value = _uiState.value.copy(unfriendRequested = false)
    }

    fun confirmUnfriend() {
        val contact = _uiState.value.contact ?: return
        toggle(FRIEND_SWITCH, false, ContactEditUiState::unfriended) { repository.removeFriend(contact.person) }
    }

    /**
     * One switch's change: the switch named [switch] shows [to] at once, and
     * a tap on any switch while it is on its way is not taken.
     */
    private fun toggle(
        switch: String,
        to: Boolean,
        settle: ContactEditUiState.(MochiError?) -> ContactEditUiState = ContactEditUiState::toggled,
        block: suspend () -> Unit,
    ) {
        if (_uiState.value.isToggling) return
        _uiState.value = _uiState.value.copy(isToggling = true, error = null, switching = mapOf(switch to to))
        viewModelScope.launch {
            val failure = try {
                block()
                awaitRefresh()
                null
            } catch (e: Exception) {
                e.toMochiError()
            }
            _uiState.update { state -> state.settle(failure).copy(switching = emptyMap()) }
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
        val changed = form != _uiState.value.form
        _uiState.value = _uiState.value.copy(form = form)
        if (creating) return
        // A change not yet saved: "Saved" no longer holds.
        if (changed) _uiState.value = _uiState.value.copy(save = SaveStatus.Idle)
        pause?.cancel()
        pause = viewModelScope.launch {
            delay(PAUSE)
            save()
        }
    }

    /** Saves what the form holds now, after any save under way: a field was left. */
    fun save() {
        pause?.cancel()
        // Nothing is saved once the screen is going: an edit left unsaved
        // stays so, as a field losing its focus on the way out would save it.
        if (creating || _uiState.value.left) return
        viewModelScope.launch { writing.withLock { send() } }
    }

    private suspend fun send(): Outcome {
        if (creating || !dirty()) return Outcome.NOTHING
        val form = _uiState.value.form
        // A contact keeps its name; a change without one waits for it.
        if (!form.valid) return Outcome.DROPPED
        val properties = form.properties()
        _uiState.update { state -> state.copy(isSaving = true, save = SaveStatus.Saving, error = null, conflict = null) }
        return try {
            val contact = repository.updateContact(
                contact = contactId,
                etag = etag,
                properties = properties,
                book = form.book.ifBlank { null },
                managed = MANAGED,
            )
            writes++
            saved = true
            etag = contact.etag
            baseline = Baseline(properties, form.book)
            _uiState.update { state ->
                // Saved, unless the form changed while this was on its way.
                val current = if (state.form == form) SaveStatus.Saved else SaveStatus.Idle
                state.copy(isSaving = false, save = current, contact = contact)
            }
            Outcome.SAVED
        } catch (e: Exception) {
            // 412 is the compare-and-swap losing to another device. The
            // server's message says so; the card it now holds replaces the
            // form, since saving over it is exactly what was refused.
            if (e is ApiException && e.code == 412) {
                _uiState.update { state -> state.copy(isSaving = false, save = SaveStatus.Error, conflict = e.message) }
                baseline = null
                load()
                Outcome.CONFLICTED
            } else {
                _uiState.update { state -> state.copy(isSaving = false, save = SaveStatus.Error, error = e.toMochiError()) }
                Outcome.FAILED
            }
        }
    }

    /**
     * Leaving the screen saves what is pending first, and depends on that
     * save alone: a failure from before - a failed load, a refused switch -
     * never keeps the screen. A save that fails keeps it, saying why and
     * offering to leave without the edit; a change that cannot be saved, a
     * contact left with no name, is dropped.
     */
    fun leave() {
        pause?.cancel()
        viewModelScope.launch {
            when (writing.withLock { send() }) {
                Outcome.FAILED -> _uiState.update { state -> state.copy(unsaved = state.error) }
                Outcome.CONFLICTED -> Unit
                else -> _uiState.update { state -> state.copy(left = true) }
            }
        }
    }

    /** Leaves without the edit whose save failed. */
    fun discard() {
        pause?.cancel()
        _uiState.update { state -> state.copy(unsaved = null, left = true) }
    }

    /** Stays on the edit whose save failed, to try again. */
    fun stay() {
        _uiState.update { state -> state.copy(unsaved = null) }
    }

    fun clearConflict() {
        _uiState.value = _uiState.value.copy(conflict = null)
    }

    /**
     * Saves a new contact, a copy as any other; the screen then goes back to
     * where it was started, as the web's panel closes.
     */
    fun create() {
        val state = _uiState.value
        if (!creating || !state.form.valid || state.isSaving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                repository.createContact(
                    properties = state.form.properties(),
                    book = state.form.book.ifBlank { null },
                    source = source.ifBlank { null },
                    managed = MANAGED,
                )
                _uiState.value = _uiState.value.copy(isSaving = false, left = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    /**
     * Saves what is pending, then opens a copy of this contact as a new one,
     * on the card as it now stands, which Create makes, as the web's panel
     * does. A save that fails or loses to another device keeps the editor,
     * saying why, rather than copy a card without the edit.
     */
    fun copy() {
        if (creating || !_uiState.value.form.valid) return
        pause?.cancel()
        viewModelScope.launch {
            writing.withLock {
                val outcome = send()
                if (outcome == Outcome.FAILED || outcome == Outcome.CONFLICTED) return@withLock
                _uiState.update { state -> state.copy(copied = contactId) }
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
     * in the list, saying why, as does a failed save of what was pending; one
     * that lost to another device closes the list over the card read again.
     */
    fun merge(source: String) {
        if (creating || _uiState.value.mergePending != null) return
        pause?.cancel()
        viewModelScope.launch {
            writing.withLock {
                when (send()) {
                    Outcome.FAILED -> {
                        _uiState.update { state -> state.copy(mergeError = state.error) }
                        return@withLock
                    }
                    Outcome.CONFLICTED -> {
                        _uiState.update { state -> state.copy(mergeOpen = false, mergePending = null, mergeError = null) }
                        return@withLock
                    }
                    else -> Unit
                }
                _uiState.value = _uiState.value.copy(mergePending = source, mergeError = null)
                try {
                    val merge = repository.previewMerge(contactId, source)
                    val survivor = repository.updateContact(
                        contact = merge.contact.id,
                        etag = merge.contact.etag,
                        source = MergeSource(merge.source.id, merge.source.etag),
                    )
                    writes++
                    saved = true
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
                refreshJob?.cancel()
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
/** The friend switch's name among [ContactEditUiState.switching]. */
internal const val FRIEND_SWITCH = "friend"

/** An XMPP contact's switch's name among [ContactEditUiState.switching]. */
internal val XmppContact.key: String get() = "xmpp $account $address"

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
