// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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

private val Context.reportedDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "market_reported",
)

/**
 * Listing ids the user has reported, so the Report menu item can be hidden. A
 * UX hint only; the server deduplicates reports.
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
