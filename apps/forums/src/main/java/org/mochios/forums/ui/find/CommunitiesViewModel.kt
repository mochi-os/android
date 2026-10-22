// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.find

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.forums.R
import org.mochios.forums.model.CommunitiesAdded
import org.mochios.forums.model.CommunitiesImported
import org.mochios.forums.model.NetworkAccount
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

/** What joining or reading in communities came to, for the screen to put in words. */
sealed interface CommunitiesEvent {
    data class Failure(val error: MochiError, val fallback: Int) : CommunitiesEvent

    /** A community joined, or one already held; [forum] is where it is. */
    data class Joined(val forum: String, val already: Boolean) : CommunitiesEvent

    data class Added(val answer: CommunitiesAdded) : CommunitiesEvent

    data class Imported(val answer: CommunitiesImported) : CommunitiesEvent
}

/**
 * Communities on other networks from the Find screen: one joined as one of
 * the user's hosted fediverse accounts by its address, and a linked Lemmy
 * account's communities and settings read in.
 */
@HiltViewModel
class CommunitiesViewModel @Inject constructor(
    private val repository: ForumsRepository,
) : ViewModel() {

    private val _hosted = MutableStateFlow<List<NetworkAccount>>(emptyList())
    val hosted: StateFlow<List<NetworkAccount>> = _hosted.asStateFlow()

    private val _lemmy = MutableStateFlow<List<NetworkAccount>>(emptyList())
    val lemmy: StateFlow<List<NetworkAccount>> = _lemmy.asStateFlow()

    /** "join", or the Lemmy account whose read is on its way. */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _events = MutableSharedFlow<CommunitiesEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CommunitiesEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                _hosted.value = repository.hostedAccounts()
            } catch (_: Exception) {
                // Nobody to join as.
            }
            try {
                _lemmy.value = repository.lemmyAccounts()
            } catch (_: Exception) {
                // No linked Lemmy account.
            }
        }
    }

    /** Join a community by `!name@host` or its address, as [account] (the first hosted account where empty). */
    fun join(address: String, account: String) {
        val value = address.trim()
        val chosen = account.ifEmpty { _hosted.value.firstOrNull()?.id.orEmpty() }
        if (value.isEmpty() || chosen.isEmpty()) return
        change("join", R.string.forums_join_failed) {
            val joined = repository.joinCommunity(value, chosen)
            _events.emit(CommunitiesEvent.Joined(joined.fingerprint.ifEmpty { joined.id }, joined.alreadySubscribed))
        }
    }

    fun add(account: NetworkAccount) = change(account.id, R.string.forums_communities_add_failed) {
        _events.emit(CommunitiesEvent.Added(repository.addCommunities(account.id)))
    }

    fun import(account: NetworkAccount) = change(account.id, R.string.forums_import_failed) {
        _events.emit(CommunitiesEvent.Imported(repository.importCommunities(account.id)))
    }

    private fun change(key: String, fallback: Int, block: suspend () -> Unit) {
        if (_busy.value != null) return
        _busy.value = key
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _events.emit(CommunitiesEvent.Failure(e.toMochiError(), fallback))
            } finally {
                _busy.value = null
            }
        }
    }
}
