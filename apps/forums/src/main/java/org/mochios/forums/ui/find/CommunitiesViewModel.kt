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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** What changes are waiting or running for: an account, or "join". */
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

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

    /** Join a community by `!name@host` or its address, as [account] (the first hosted account where empty); [onJoined] runs once it is joined. */
    fun join(address: String, account: String, onJoined: () -> Unit = {}) {
        val value = address.trim()
        val chosen = account.ifEmpty { _hosted.value.firstOrNull()?.id.orEmpty() }
        if (value.isEmpty() || chosen.isEmpty()) return
        change("join", R.string.forums_join_failed) {
            val joined = repository.joinCommunity(value, chosen)
            onJoined()
            _events.emit(CommunitiesEvent.Joined(joined.fingerprint.ifEmpty { joined.id }, joined.alreadySubscribed))
        }
    }

    fun add(account: NetworkAccount) = change(lemmyKey("add", account.id), R.string.forums_communities_add_failed) {
        _events.emit(CommunitiesEvent.Added(repository.addCommunities(account.id)))
    }

    fun import(account: NetworkAccount) = change(lemmyKey("import", account.id), R.string.forums_import_failed) {
        _events.emit(CommunitiesEvent.Imported(repository.importCommunities(account.id)))
    }

    // Actions run one at a time, in the order tapped: one tapped while another
    // runs waits here instead of being dropped, and shows as busy meanwhile.
    private val queue = Mutex()

    private fun change(key: String, fallback: Int, block: suspend () -> Unit) {
        // The same action tapped again while it waits or runs is the one tap.
        if (key in _busy.value) return
        _busy.update { it + key }
        viewModelScope.launch {
            queue.withLock {
                try {
                    block()
                } catch (e: Exception) {
                    _events.emit(CommunitiesEvent.Failure(e.toMochiError(), fallback))
                } finally {
                    _busy.update { it - key }
                }
            }
        }
    }
}

/** The busy key of a linked Lemmy account's action, add or import: each its own, so the button tapped is the one that spins. */
internal fun lemmyKey(action: String, account: String) = "$action/$account"
