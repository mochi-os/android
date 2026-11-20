// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.lib

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.mochios.android.auth.SessionManager

/**
 * One account's entry in a device-wide DataStore: the entry is named
 * `<name>:<identity>`, read and written for the signed-in identity only. When
 * the identity changes, the entries of every other account are dropped, since
 * signing another account in, or signing out, forgets the previous one on this
 * device; so is the entry from before entries were kept per account, which
 * cannot be told apart.
 */
internal class AccountEntries<T>(
    private val dataStore: DataStore<Preferences>,
    private val session: SessionManager,
    private val name: String,
    private val key: (String) -> Preferences.Key<T>,
) {
    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            session.boundIdentity.distinctUntilChanged().collect { identity -> forget(identity) }
        }
    }

    /** The signed-in account's entry, null while no one is signed in or nothing is held. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<T?> = session.boundIdentity.distinctUntilChanged().flatMapLatest { identity ->
        if (identity == null) flowOf(null) else dataStore.data.map { prefs -> prefs[key(entry(identity))] }
    }

    /** Changes the signed-in account's entry; nothing while no one is signed in. */
    suspend fun update(change: (T?) -> T?) {
        val identity = session.getBoundIdentity() ?: return
        val entry = key(entry(identity))
        dataStore.edit { prefs ->
            val next = change(prefs[entry])
            if (next == null) prefs.remove(entry) else prefs[entry] = next
        }
    }

    /** Drops every entry but [identity]'s. */
    private suspend fun forget(identity: String?) {
        val kept = identity?.let { entry(it) }
        dataStore.edit { prefs ->
            for (stale in prefs.asMap().keys.filter { held -> held.name.belongs() && held.name != kept }) {
                @Suppress("UNCHECKED_CAST")
                prefs.remove(stale as Preferences.Key<Any>)
            }
        }
    }

    private fun entry(identity: String) = "$name:$identity"

    private fun String.belongs() = this == name || startsWith("$name:")
}
