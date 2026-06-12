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
 * Server-backed store for the user's saved (wishlisted) market listings.
 *
 * Replaces the old on-device DataStore: the Comptroller now persists the
 * saved set behind `-/saved/list|add|remove`, so this holds an in-memory
 * reactive mirror hydrated from the server. Reads come from the cache for
 * instant, reactive UI (bookmark fills, the Saved grid); mutations write
 * through to the server with an optimistic local update that reverts if the
 * call fails.
 *
 * `saved/list` returns full [Listing] rows, so the Saved screen renders
 * directly off [saved] without the per-id `listings/get` fan-out the local
 * store used to need.
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
     * Fetch the saved list from the server and populate the mirror. Errors
     * (transient network blip, or a 401 before login completes) are
     * swallowed so the bookmark UI degrades gracefully rather than throwing.
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
     * Toggle the saved state for [listing]. Applies optimistically to the
     * mirror, then calls the server; on failure the optimistic change is
     * rolled back. Returns the new saved state. Requires the full [Listing]
     * because the server persists a snapshot for the saved page to render.
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
