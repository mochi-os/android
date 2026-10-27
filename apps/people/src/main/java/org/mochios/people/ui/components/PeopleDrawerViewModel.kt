// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.auth.SessionManager
import org.mochios.people.model.Book
import org.mochios.people.model.Group
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

/**
 * Feeds [PeopleDrawer] the address books and the groups, fetching each only
 * when none are held yet or a change has made them stale.
 */
@HiltViewModel
class PeopleDrawerViewModel @Inject constructor(
    private val repository: PeopleRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    /** The address books, as last fetched by any screen. */
    val books: StateFlow<List<Book>> = repository.books

    private val _groups = MutableStateFlow<List<Group>>(emptyList())

    /** The groups, as last fetched for the drawer. */
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    private var groupsStale = true

    private val _identity = MutableStateFlow<String?>(null)

    /** The signed-in person's id, for the Profile row's avatar. */
    val identity: StateFlow<String?> = _identity.asStateFlow()

    init {
        viewModelScope.launch {
            val identity = sessionManager.getBoundIdentity()
            _identity.value = identity
        }
        load()
        viewModelScope.launch {
            repository.groupsChanged.collect {
                groupsStale = true
                loadGroups()
            }
        }
    }

    /** Fetches the address books and the groups if either is missing or stale. */
    fun load() {
        viewModelScope.launch {
            try {
                repository.loadBooks()
            } catch (_: Exception) {
            }
        }
        viewModelScope.launch { loadGroups() }
    }

    private suspend fun loadGroups() {
        if (!groupsStale) {
            return
        }
        try {
            val groups = repository.listGroups()
            _groups.value = groups
            groupsStale = false
        } catch (_: Exception) {
        }
    }

    /**
     * Marks the books and groups stale, for when the app leaves the foreground and they
     * may change elsewhere.
     */
    fun markStale() {
        repository.markBooksStale()
        groupsStale = true
    }
}
