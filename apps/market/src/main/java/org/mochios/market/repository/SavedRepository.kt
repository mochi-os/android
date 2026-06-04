package org.mochios.market.repository

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrap
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
    private val api: MarketApi,
) {

    private val gson = Gson()

    private val _saved = MutableStateFlow<List<Listing>>(emptyList())

    /** Full saved listings, most-recent-first as returned by the server. */
    val saved: StateFlow<List<Listing>> = _saved.asStateFlow()

    /** Reactive saved-id set for filling the bookmark toggle on cards/grids. */
    val savedIds: Flow<Set<Long>> = saved.map { list -> list.mapTo(HashSet()) { it.id } }

    private val hydrateMutex = Mutex()

    @Volatile
    private var hydrated = false

    /** Reactive "is this listing saved", derived from the cache. */
    fun isSaved(id: Long): Flow<Boolean> = saved.map { list -> list.any { it.id == id } }

    private fun contains(id: Long): Boolean = _saved.value.any { it.id == id }

    /**
     * Pull the saved set from the server once. Cheap no-op after the first
     * successful load — call it wherever a screen needs the saved state to be
     * accurate (listing detail, home grid) without forcing a refresh.
     */
    suspend fun ensureHydrated() {
        if (hydrated) return
        hydrateMutex.withLock {
            if (hydrated) return
            refresh()
        }
    }

    /** Force a re-fetch of the saved set from the server. */
    suspend fun refresh() {
        try {
            _saved.value = api.listSaved().unwrap().saved
            hydrated = true
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** Toggle [listing]'s saved state, returning the new state (true = saved). */
    suspend fun toggle(listing: Listing): Boolean =
        if (contains(listing.id)) {
            remove(listing.id)
            false
        } else {
            add(listing)
            true
        }

    /** Save [listing]. Optimistic — the cache reverts if the call fails. */
    suspend fun add(listing: Listing) {
        if (!contains(listing.id)) {
            _saved.value = listOf(listing) + _saved.value
        }
        try {
            api.addSaved(listing.id.toString(), gson.toJson(listing)).unwrap()
        } catch (e: Exception) {
            _saved.value = _saved.value.filterNot { it.id == listing.id }
            throw e.toMochiError()
        }
    }

    /** Un-save the listing with [id]. Optimistic — reverts if the call fails. */
    suspend fun remove(id: Long) {
        val previous = _saved.value
        _saved.value = previous.filterNot { it.id == id }
        try {
            api.removeSaved(id.toString()).unwrap()
        } catch (e: Exception) {
            _saved.value = previous
            throw e.toMochiError()
        }
    }

    /**
     * Remove every saved listing. There's no bulk endpoint, so this fans the
     * per-id removes out and tolerates individual failures — the local cache
     * is cleared up front so the grid empties immediately.
     */
    suspend fun clear() {
        val ids = _saved.value.map { it.id }
        _saved.value = emptyList()
        ids.forEach { id ->
            runCatching { api.removeSaved(id.toString()).unwrap() }
        }
    }
}
