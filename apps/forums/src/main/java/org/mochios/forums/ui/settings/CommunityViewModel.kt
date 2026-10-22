// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.settings

import androidx.lifecycle.SavedStateHandle
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
import org.mochios.android.util.NaturalCompare
import org.mochios.forums.R
import org.mochios.forums.model.CommunityState
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

/** What publishing the forum or marking it sensitive came to. */
sealed interface CommunityEvent {
    data class Failure(val error: MochiError, val fallback: Int) : CommunityEvent

    data class Done(val message: Int) : CommunityEvent
}

/**
 * The forum as a community on the fediverse - published on a name the owner
 * takes on one of the domains they may take names on, or withdrawn - and
 * whether every post of the forum is sensitive.
 */
@HiltViewModel
class CommunityViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ForumsRepository,
) : ViewModel() {

    val forumId: String = savedStateHandle["forumId"] ?: ""

    private val _state = MutableStateFlow<CommunityState?>(null)
    val state: StateFlow<CommunityState?> = _state.asStateFlow()

    private val _sensitive = MutableStateFlow(false)
    val sensitive: StateFlow<Boolean> = _sensitive.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = MutableSharedFlow<CommunityEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CommunityEvent> = _events.asSharedFlow()

    fun load(sensitive: Boolean) {
        _sensitive.value = sensitive
        viewModelScope.launch {
            try {
                _state.value = repository.getCommunity(forumId)
            } catch (_: Exception) {
                // A server without the networks has no community to offer.
            }
        }
    }

    /** The domains a name may be taken on, in order. */
    fun domains(state: CommunityState?): List<String> = state?.domains.orEmpty().sortedWith(NaturalCompare)

    fun publish(name: String, domain: String) {
        if (name.isBlank() || domain.isBlank()) return
        change(R.string.forums_community_publish_failed) {
            _state.value = repository.publishCommunity(forumId, name, domain)
            _events.emit(CommunityEvent.Done(R.string.forums_community_published))
        }
    }

    fun withdraw() = change(R.string.forums_community_withdraw_failed) {
        _state.value = repository.withdrawCommunity(forumId)
        _events.emit(CommunityEvent.Done(R.string.forums_community_withdrawn))
    }

    /** Mark every post of the forum sensitive, or not; a failure puts the switch back. */
    fun setSensitive(on: Boolean) {
        val before = _sensitive.value
        _sensitive.value = on
        change(R.string.forums_save_failed, onFailure = { _sensitive.value = before }) {
            _sensitive.value = repository.setSensitive(forumId, on)
        }
    }

    private fun change(fallback: Int, onFailure: () -> Unit = {}, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                onFailure()
                _events.emit(CommunityEvent.Failure(e.toMochiError(), fallback))
            } finally {
                _busy.value = false
            }
        }
    }
}
