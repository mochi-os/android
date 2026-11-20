// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.lib

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mochios.android.auth.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "market_recent",
)

/**
 * Most-recent-first listing ids the signed-in account has opened, capped at
 * [MAX] and de-duplicated on push. Each account's are its own, and dropped when
 * another account signs in or it signs out. Stored comma-separated because
 * DataStore string sets lose order.
 */
@Singleton
class RecentlyViewedStore @Inject constructor(
    @ApplicationContext context: Context,
    session: SessionManager,
) {

    private val entries = AccountEntries(context.recentDataStore, session, KEY) { name -> stringPreferencesKey(name) }

    fun observe(): Flow<List<String>> = entries.observe().map { raw -> decode(raw) }

    suspend fun push(listingId: String) {
        if (listingId.isBlank()) return
        entries.update { raw ->
            val current = decode(raw)
            encode((listOf(listingId) + current.filter { it != listingId }).take(MAX))
        }
    }

    /** Forgets [listingIds], as listings that are gone or no longer on sale. */
    suspend fun remove(listingIds: Collection<String>) {
        if (listingIds.isEmpty()) return
        entries.update { raw -> encode(decode(raw).filter { id -> id !in listingIds }) }
    }

    suspend fun clear() {
        entries.update { null }
    }

    private fun decode(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(',').filter { it.isNotBlank() }
    }

    private fun encode(ids: List<String>): String? = ids.joinToString(separator = ",").ifEmpty { null }

    companion object {
        private const val MAX = 20
        private const val KEY = "recent_listings"
    }
}
