// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.mochios.people.model.Book
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

/**
 * Feeds [PeopleDrawer] the address books, fetching them only when none are
 * held yet or a change has made them stale.
 */
@HiltViewModel
class PeopleDrawerViewModel @Inject constructor(
    private val repository: PeopleRepository,
) : ViewModel() {

    /** The address books, as last fetched by any screen. */
    val books: StateFlow<List<Book>> = repository.books

    init {
        load()
    }

    /** Fetches the address books if none are held yet or they are stale. */
    fun load() {
        viewModelScope.launch {
            try {
                repository.loadBooks()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Marks the books stale, for when the app leaves the foreground and they
     * may change elsewhere.
     */
    fun markStale() {
        repository.markBooksStale()
    }
}
