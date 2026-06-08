package org.mochios.market.lib

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
 * Mirrors the web side's `lib/saved.ts`: saved state lives in the market
 * app's own per-user DB (`-/saved/list|add|remove|clear`) so it survives
 * reloads and logout and syncs across the user's devices via Mochi's
 * per-app replication. This store keeps a synchronous in-memory mirror
 * ([StateFlow]) so the rest of the app can read the saved set without
 * awaiting, while mutations apply optimistically and reconcile with the
 * server in the background.
 *
 * Application-scoped ([Singleton]) so every screen observes the same
 * mirror. Call [refresh] after login (and on entering a saved-aware
 * screen) to hydrate it from the server. The store holds full [Listing]
 * snapshots — the server persists one per saved row and `saved/list`
 * returns them fully hydrated, so the saved screen renders without a
 * per-id refetch.
 */
@Singleton
class SavedStore @Inject constructor(
    private val api: MarketApi,
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
