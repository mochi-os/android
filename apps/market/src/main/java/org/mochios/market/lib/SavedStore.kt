package org.mochios.market.lib

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backing DataStore for the [SavedStore]. Application-scoped, so the saved
 * listing set survives ViewModel and Activity recreation. The file is local
 * to the device — not synced to the server — mirroring the web localStorage
 * "saved listings" bucket.
 */
private val Context.savedDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "market_saved",
)

/**
 * Local store for the user's saved (wishlisted) market listing IDs.
 *
 * Mirrors the web side's `useSavedListings` localStorage helper: an opaque
 * set of listing-id strings the user has bookmarked from a listing card,
 * detail page, or search result. The set is read by the Saved screen and
 * by the Save toggle on listing cards.
 *
 * IDs are stored as strings even though [org.mochios.market.model.Listing.id]
 * is `Long` — DataStore only ships a `stringSetPreferencesKey`, and the cost
 * of `.toString()` / `.toLongOrNull()` at the boundary is trivial.
 */
@Singleton
class SavedStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val dataStore = context.savedDataStore

    fun observe(): Flow<Set<String>> =
        dataStore.data.map { prefs -> prefs[KEY_SAVED] ?: emptySet() }

    suspend fun toggle(listingId: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_SAVED] ?: emptySet()
            prefs[KEY_SAVED] = if (listingId in current) current - listingId else current + listingId
        }
    }

    suspend fun isSaved(listingId: String): Boolean {
        val current = dataStore.data.first()[KEY_SAVED] ?: emptySet()
        return listingId in current
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(KEY_SAVED) }
    }

    companion object {
        private val KEY_SAVED = stringSetPreferencesKey("saved_listings")
    }
}
