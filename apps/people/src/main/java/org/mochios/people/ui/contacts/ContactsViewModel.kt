// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

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
import org.mochios.android.util.NaturalCompare
import org.mochios.people.model.Book
import org.mochios.people.model.Contact
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

/** How the contacts list is ordered. Mirrors web's name/recent toggle. */
enum class ContactSortBy { NAME, RECENT }

data class ContactsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val contacts: List<Contact> = emptyList(),
    val books: List<Book> = emptyList(),
    val searchQuery: String = "",
    val sortBy: ContactSortBy = ContactSortBy.NAME,
    val error: MochiError? = null,

    // Confirmation dialogs, each holding the contact they act on.
    val unfriending: Contact? = null,
    val deleting: Contact? = null,
    val isMutating: Boolean = false,

    // Address-book dialogs for the book this screen is showing.
    val renamingBook: Book? = null,
    val deletingBook: Book? = null,

    /**
     * Welcome banner: set when `-/welcome` reports unseen, cleared and
     * persisted via `-/welcome/seen` on dismiss.
     */
    val showWelcome: Boolean = false,
)

sealed class ContactsEvent {
    /** Open chat with the given person id. */
    data class MessagePerson(val person: String, val name: String) : ContactsEvent()

    /** The book this screen was showing has gone; go back to all contacts. */
    object BookDeleted : ContactsEvent()
}

/**
 * Filter and order the list the same way the screen shows it: a query matches
 * the user's label or the directory name, and the order is the sort toggle.
 */
fun filterContacts(
    contacts: List<Contact>,
    query: String,
    sortBy: ContactSortBy,
): List<Contact> {
    val search = query.trim()
    val base = if (search.isBlank()) {
        contacts
    } else {
        contacts.filter {
            it.name.contains(search, ignoreCase = true) ||
                it.directory.contains(search, ignoreCase = true)
        }
    }
    return when (sortBy) {
        ContactSortBy.RECENT -> base.sortedByDescending { it.created }
        ContactSortBy.NAME -> base.sortedWith(compareBy(NaturalCompare) { it.name })
    }
}

@HiltViewModel
class ContactsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PeopleRepository,
) : ViewModel() {

    /** The address book being shown, or empty for every contact. */
    val book: String = savedStateHandle.get<String>("id").orEmpty()

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ContactsEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ContactsEvent> = _events.asSharedFlow()

    init {
        loadContacts()
        loadWelcome()
        viewModelScope.launch {
            repository.contactsChanged.collect { reload() }
        }
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

    fun loadContacts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val contacts = repository.listContacts(book.ifBlank { null }).contacts
                _uiState.value = _uiState.value.copy(contacts = contacts, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
            loadBooks()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val contacts = repository.listContacts(book.ifBlank { null }).contacts
                _uiState.value = _uiState.value.copy(
                    contacts = contacts,
                    isRefreshing = false,
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.toMochiError(),
                )
            }
            loadBooks()
        }
    }

    /** Silent reload behind a mutation made elsewhere. */
    private fun reload() {
        viewModelScope.launch {
            try {
                val contacts = repository.listContacts(book.ifBlank { null }).contacts
                _uiState.value = _uiState.value.copy(contacts = contacts)
            } catch (_: Exception) {
                // The screen already shows a list; a failed background reload
                // leaves it as it was rather than replacing it with an error.
            }
            loadBooks()
        }
    }

    private suspend fun loadBooks() {
        try {
            _uiState.value = _uiState.value.copy(books = repository.listBooks())
        } catch (_: Exception) {
            // The books are the drawer's contents, not the screen's: a failed
            // fetch leaves the drawer with "All contacts" alone.
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setSortBy(sortBy: ContactSortBy) {
        _uiState.value = _uiState.value.copy(sortBy = sortBy)
    }

    fun filteredContacts(): List<Contact> = filterContacts(
        _uiState.value.contacts,
        _uiState.value.searchQuery,
        _uiState.value.sortBy,
    )

    // ---------------- contact actions ----------------

    fun messageContact(contact: Contact) {
        if (contact.person.isBlank()) return
        viewModelScope.launch {
            _events.emit(ContactsEvent.MessagePerson(contact.person, contact.name))
        }
    }

    fun invite(contact: Contact) {
        if (contact.person.isBlank() || contact.friend) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMutating = true, error = null)
            try {
                repository.inviteFriend(contact.person, contact.name)
                _uiState.value = _uiState.value.copy(isMutating = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isMutating = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun requestUnfriend(contact: Contact) {
        _uiState.value = _uiState.value.copy(unfriending = contact)
    }

    fun cancelUnfriend() {
        _uiState.value = _uiState.value.copy(unfriending = null)
    }

    fun confirmUnfriend() {
        val contact = _uiState.value.unfriending ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMutating = true)
            try {
                repository.removeFriend(contact.person)
                _uiState.value = _uiState.value.copy(isMutating = false, unfriending = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isMutating = false,
                    unfriending = null,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun requestDelete(contact: Contact) {
        _uiState.value = _uiState.value.copy(deleting = contact)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(deleting = null)
    }

    fun confirmDelete() {
        val contact = _uiState.value.deleting ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMutating = true)
            try {
                repository.deleteContact(contact.id)
                _uiState.value = _uiState.value.copy(
                    isMutating = false,
                    deleting = null,
                    contacts = _uiState.value.contacts.filterNot { it.id == contact.id },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isMutating = false,
                    deleting = null,
                    error = e.toMochiError(),
                )
            }
        }
    }

    // ---------------- address book actions ----------------

    fun requestRenameBook(target: Book) {
        _uiState.value = _uiState.value.copy(renamingBook = target)
    }

    fun cancelRenameBook() {
        _uiState.value = _uiState.value.copy(renamingBook = null)
    }

    fun confirmRenameBook(name: String) {
        val target = _uiState.value.renamingBook ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMutating = true)
            try {
                repository.renameBook(target.id, name.trim())
                _uiState.value = _uiState.value.copy(isMutating = false, renamingBook = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isMutating = false,
                    renamingBook = null,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun requestDeleteBook(target: Book) {
        _uiState.value = _uiState.value.copy(deletingBook = target)
    }

    fun cancelDeleteBook() {
        _uiState.value = _uiState.value.copy(deletingBook = null)
    }

    fun confirmDeleteBook() {
        val target = _uiState.value.deletingBook ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMutating = true)
            try {
                repository.deleteBook(target.id)
                _uiState.value = _uiState.value.copy(isMutating = false, deletingBook = null)
                if (target.id == book) _events.emit(ContactsEvent.BookDeleted)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isMutating = false,
                    deletingBook = null,
                    error = e.toMochiError(),
                )
            }
        }
    }
}
