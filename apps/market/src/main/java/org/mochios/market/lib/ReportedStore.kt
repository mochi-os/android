// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.lib

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mochios.android.auth.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

private val Context.reportedDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "market_reported",
)

/**
 * Listing ids the signed-in account has reported, so the Report menu item can
 * be hidden. Each account's are its own, and dropped when another account
 * signs in or it signs out. A UX hint only; the server deduplicates reports.
 */
@Singleton
class ReportedStore @Inject constructor(
    @ApplicationContext context: Context,
    session: SessionManager,
) {

    private val entries = AccountEntries(context.reportedDataStore, session, KEY) { name -> stringSetPreferencesKey(name) }

    fun observe(): Flow<Set<String>> = entries.observe().map { held -> held ?: emptySet() }

    suspend fun markReported(listingId: String) {
        if (listingId.isBlank()) return
        entries.update { held -> (held ?: emptySet()) + listingId }
    }

    companion object {
        private const val KEY = "reported_listings"
    }
}
