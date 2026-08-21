// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.repository

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mochios.market.api.MarketApi
import org.mochios.market.model.Listing
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory mirror of the user's saved listings, hydrated from `-/saved/list`;
 * mutations write through optimistically and revert on failure. Rows are full
 * [Listing] snapshots, so the Saved screen renders straight off [saved].
 */
@Singleton
class SavedRepository @Inject constructor(
    private val api: MarketApi
) {
    private val gson = Gson()
    private val mutex = Mutex()

    private val _saved = MutableStateFlow<List<Listing>>(emptyList())

    /** The current saved listings, most recently saved first. */
    val saved: StateFlow<List<Listing>> = _saved.asStateFlow()

    /** Stream of the saved listing ids as strings (for save-toggle UI on cards). */
    fun observeIds(): Flow<Set<String>> =
        _saved.map { list -> list.mapTo(mutableSetOf()) { it.id.toString() } }

    /**
     * Hydrate the mirror from the server; failures (including a 401 before
     * login completes) leave it untouched.
     */
    suspend fun refresh() {
        try {
            val response = api.listSaved().body()?.data ?: return
            _saved.value = response.saved
        } catch (_: Exception) {
            // Leave the existing mirror untouched on failure.
        }
    }

    /** True if the given listing id is in the saved set. */
    fun isSaved(listingId: String): Boolean =
        _saved.value.any { it.id.toString() == listingId }

    /**
     * Toggle saved state optimistically, reverting on failure; returns the new
     * state. Takes the full [Listing] because the server stores a snapshot.
     */
    suspend fun toggle(listing: Listing): Boolean = mutex.withLock {
        val id = listing.id
        val idString = id.toString()
        val previous = _saved.value
        return if (previous.any { it.id == id }) {
            _saved.value = previous.filterNot { it.id == id }
            try {
                api.removeSaved(idString)
            } catch (e: Exception) {
                _saved.value = previous
                throw e
            }
            false
        } else {
            _saved.value = listOf(listing) + previous
            try {
                api.addSaved(idString, gson.toJson(listing))
            } catch (e: Exception) {
                _saved.value = previous
                throw e
            }
            true
        }
    }

    /** Remove every saved listing. Optimistic, with rollback on failure. */
    suspend fun clear() = mutex.withLock {
        val previous = _saved.value
        _saved.value = emptyList()
        try {
            api.clearSaved()
        } catch (e: Exception) {
            _saved.value = previous
            throw e
        }
    }
}
