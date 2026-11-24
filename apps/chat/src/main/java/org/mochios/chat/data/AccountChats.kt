// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Chat ids kept on this device for each account, in a preferences [file]
 * with a set per account: [ids] is the signed-in account's, and follows a
 * sign-in as another, so one account never sees the ids another kept. Set
 * aside an account's own key, an id under [legacy] is a set kept before sets
 * were per account, which the first account to read it takes over, its chats
 * the list no longer has then dropped by [prune].
 *
 * @param account the account signed in as the store is made.
 * @param accounts the account signed in, as it changes.
 */
internal class AccountChats(
    context: Context,
    file: String,
    private val legacy: String,
    account: String?,
    accounts: Flow<String?>,
) {
    private val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
    private val lock = Any()
    private var current: String? = account
    private val _ids = MutableStateFlow(read(account))
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            accounts.distinctUntilChanged().collect { next ->
                synchronized(lock) {
                    if (next == current) return@collect
                    current = next
                    _ids.value = read(next)
                }
            }
        }
    }

    /** [change] applied to the signed-in account's ids, and kept. */
    fun change(change: (Set<String>) -> Set<String>) {
        synchronized(lock) {
            val next = change(_ids.value)
            if (next == _ids.value) return
            _ids.value = next
            val account = current ?: return
            prefs.edit { putStringSet(account, next) }
        }
    }

    /**
     * Drop the ids of chats [listed], [account]'s whole list, no longer has.
     * Nothing is dropped once another account is signed in: the list is not
     * its.
     */
    fun prune(listed: Set<String>, account: String?) {
        synchronized(lock) {
            if (account != current) return
            change { ids -> ids intersect listed }
        }
    }

    private fun read(account: String?): Set<String> {
        if (account == null) return emptySet()
        prefs.getStringSet(account, null)?.let { kept -> return kept.toSet() }
        val old = prefs.getStringSet(legacy, null)?.toSet() ?: return emptySet()
        prefs.edit {
            putStringSet(account, old)
            remove(legacy)
        }
        return old
    }
}
