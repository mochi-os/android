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
 * Backing DataStore for the [ReportedStore]. Kept separate from the saved
 * and recently-viewed stores because reports are an opt-in moderation
 * action — never bundle this set with the user's other browsing state.
 */
private val Context.reportedDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "market_reported",
)

/**
 * Local record of listing IDs the user has already reported. Read by the
 * listing detail and listing card overflow menus so the "Report" item is
 * hidden (and replaced with a "Reported" indicator) after submission.
 *
 * The server is the source of truth for moderation; this store is purely a
 * UX hint to stop users firing duplicate reports while their first is still
 * being reviewed. Loss of the underlying file is harmless — the worst case
 * is the Report button reappearing for a listing the user has already
 * flagged, which the server-side dedup will collapse.
 */
@Singleton
class ReportedStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val dataStore = context.reportedDataStore

    fun observe(): Flow<Set<String>> =
        dataStore.data.map { prefs -> prefs[KEY_REPORTED] ?: emptySet() }

    suspend fun markReported(listingId: String) {
        if (listingId.isBlank()) return
        dataStore.edit { prefs ->
            val current = prefs[KEY_REPORTED] ?: emptySet()
            prefs[KEY_REPORTED] = current + listingId
        }
    }

    suspend fun isReported(listingId: String): Boolean {
        val current = dataStore.data.first()[KEY_REPORTED] ?: emptySet()
        return listingId in current
    }

    companion object {
        private val KEY_REPORTED = stringSetPreferencesKey("reported_listings")
    }
}
