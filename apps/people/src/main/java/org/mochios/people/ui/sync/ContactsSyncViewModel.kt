// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.sync

import android.accounts.Account
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mochios.android.auth.SessionManager
import org.mochios.android.sync.ContactsSync
import org.mochios.android.sync.ContactsSyncState
import javax.inject.Inject

/**
 * The drawer's "Sync to this phone" switch: the signed-in account's contacts
 * sync, turned on after the contacts permission is granted, off again, or
 * run now.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactsSyncViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val account = MutableStateFlow<Account?>(null)

    /** Re-read on demand, since a permission answer changes no sync setting. */
    private val refresh = MutableStateFlow(0)

    val state: StateFlow<ContactsSyncState> = refresh
        .flatMapLatest { account }
        .flatMapLatest { ContactsSync.state(context, it) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsSyncState())

    init {
        viewModelScope.launch {
            account.value = withContext(Dispatchers.IO) {
                ContactsSync.account(context, sessionManager.getBoundIdentity())
            }
        }
    }

    fun enable() {
        val target = account.value ?: return
        viewModelScope.launch(Dispatchers.IO) { ContactsSync.enable(target) }
    }

    /** Stops syncing and takes the synced contacts off the phone. */
    fun disable() {
        val target = account.value ?: return
        viewModelScope.launch(Dispatchers.IO) { ContactsSync.disable(context, target) }
    }

    fun sync() {
        val target = account.value ?: return
        viewModelScope.launch(Dispatchers.IO) { ContactsSync.request(target) }
    }

    fun refresh() {
        refresh.value++
    }
}
