// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.mochios.android.auth.SessionManager
import java.util.concurrent.atomic.AtomicInteger
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mochios.android.api.unwrap
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
    private val api: MarketApi,
    session: SessionManager,
) {
    private val gson = Gson()
    private val mutex = Mutex()

    private val _saved = MutableStateFlow<List<Listing>>(emptyList())

    // The mirror holds one account's saved listings, so it is emptied whenever
    // the signed-in identity changes; the next account on the phone was
    // otherwise shown them until a read succeeded. A read or a revert begun
    // before the change does not write them back after it.
    private val generation = AtomicInteger()

    /** The identity the mirror holds saved listings for. */
    @Volatile
    private var holder: String? = null

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            session.boundIdentity.collect { identity ->
                // From no account to one there is nothing to clear: signing
                // out cleared it. Clearing then would also drop the first read
                // of a cold start.
                if (identity != holder) {
                    if (holder != null) {
                        generation.incrementAndGet()
                        _saved.value = emptyList()
                    }
                    holder = identity
                }
            }
        }
    }

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
        val started = generation.get()
        try {
            val response = api.listSaved().body()?.data ?: return
            _saved.value = response.saved
            // An account change that landed meanwhile takes it back out.
            if (generation.get() != started) _saved.value = emptyList()
        } catch (_: Exception) {
            // Leave the existing mirror untouched on failure.
        }
    }

    /**
     * Toggle saved state optimistically, reverting on failure; returns the new
     * state. Takes the full [Listing] because the server stores a snapshot.
     */
    suspend fun toggle(listing: Listing): Boolean = mutex.withLock {
        val id = listing.id
        val idString = id.toString()
        val started = generation.get()
        val previous = _saved.value
        return if (previous.any { it.id == id }) {
            _saved.value = previous.filterNot { it.id == id }
            try {
                // unwrap() turns a non-2xx answer into an exception, so a refused
                // write reverts the mirror instead of reporting success.
                api.removeSaved(idString).unwrap()
            } catch (e: Exception) {
                if (generation.get() == started) _saved.value = previous
                throw e
            }
            false
        } else {
            _saved.value = listOf(listing) + previous
            try {
                api.addSaved(idString, gson.toJson(listing)).unwrap()
            } catch (e: Exception) {
                if (generation.get() == started) _saved.value = previous
                throw e
            }
            true
        }
    }

    /** Remove every saved listing. Optimistic, with rollback on failure. */
    suspend fun clear() = mutex.withLock {
        val started = generation.get()
        val previous = _saved.value
        _saved.value = emptyList()
        try {
            api.clearSaved().unwrap()
        } catch (e: Exception) {
            if (generation.get() == started) _saved.value = previous
            throw e
        }
    }
}
