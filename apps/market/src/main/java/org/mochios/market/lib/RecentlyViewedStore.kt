package org.mochios.market.lib

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backing DataStore for the [RecentlyViewedStore]. Separate file from the
 * saved store so a "clear recently viewed" never touches the user's saved
 * set, and so the two stores can evolve their schemas independently.
 */
private val Context.recentDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "market_recent",
)

/**
 * Most-recent-first list of listing IDs the user has opened. Powers the
 * "Recently viewed" strip on the market home screen.
 *
 * The list is capped at [MAX] entries and de-duplicated on push: opening a
 * listing already in the list moves it to the head rather than producing a
 * second copy. Mirrors the web side's `useRecentListings` localStorage
 * helper — IDs are stored as comma-separated strings because DataStore has
 * no ordered-list preferences key (sets lose order).
 */
@Singleton
class RecentlyViewedStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val dataStore = context.recentDataStore

    fun observe(): Flow<List<String>> =
        dataStore.data.map { prefs -> decode(prefs[KEY_RECENT]) }

    suspend fun push(listingId: String) {
        if (listingId.isBlank()) return
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_RECENT])
            val next = (listOf(listingId) + current.filter { it != listingId }).take(MAX)
            prefs[KEY_RECENT] = encode(next)
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(KEY_RECENT) }
    }

    private fun decode(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(',').filter { it.isNotBlank() }
    }

    private fun encode(ids: List<String>): String = ids.joinToString(separator = ",")

    companion object {
        private const val MAX = 20
        private val KEY_RECENT = stringPreferencesKey("recent_listings")
    }
}
