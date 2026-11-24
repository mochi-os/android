// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.moderation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.ui.components.LoadState
import org.mochios.android.util.Paging
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.util.append
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.staff.model.ModerationEntry
import org.mochios.staff.repository.StaffRepository
import org.mochios.staff.ws.STAFF_EVENTS
import org.mochios.staff.ws.StaffEvent
import org.mochios.staff.ws.staffEvent
import javax.inject.Inject

/**
 * The moderation log, optionally narrowed to one [listing] (opened from that
 * listing's history) and to listings whose title matches [query].
 */
data class ModerationLogUiState(
    val listing: String? = null,
    val query: String = "",
    val isLoading: Boolean = true,
    /** The next page, for the list's foot. */
    val load: LoadState = LoadState.Idle,
    val entries: List<ModerationEntry> = emptyList(),
    val total: Long = 0,
    /** How far the log has been read. */
    val paging: Paging = Paging(),
    val error: MochiError? = null,
)

private const val PAGE_SIZE = 20

@HiltViewModel
class ModerationLogViewModel @Inject constructor(
    private val repository: StaffRepository,
    private val webSocket: MochiWebSocket,
    sessionManager: SessionManager,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ModerationLogUiState(
            listing = savedStateHandle.get<String>(KEY_LISTING)?.ifBlank { null },
            query = savedStateHandle.get<String>(KEY_QUERY) ?: "",
        ),
    )
    val state: StateFlow<ModerationLogUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** The pending [reloadLatest]; replaced by each newer staff event. */
    private var eventJob: Job? = null

    /** The staff-events subscription, held while this view model lives. */
    private val subscription: String

    init {
        reload()
        // Refetch whenever the moderation queue changes server-side — same
        // signal that triggers the listings page's invalidation — or the
        // socket was down and missed it.
        subscription = webSocket.subscribe(sessionManager.getServerUrlBlocking(), STAFF_EVENTS, app = "staff") { frame ->
            when (staffEvent(frame)) {
                StaffEvent.ModerationUpdated, is StaffEvent.Unknown -> reloadLatest()
                else -> Unit
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        webSocket.unsubscribe(subscription)
    }

    /** Search the log by listing title; the screen debounces. */
    fun setQuery(query: String) {
        if (_state.value.query == query) return
        _state.value = _state.value.copy(query = query)
        savedStateHandle[KEY_QUERY] = query
        reload()
    }

    /** Stop narrowing the log to the listing it was opened for. */
    fun clearListing() {
        if (_state.value.listing == null) return
        _state.value = _state.value.copy(listing = null)
        savedStateHandle[KEY_LISTING] = null
        reload()
    }

    /**
     * [reload] after [REFRESH_DEBOUNCE] for a staff event, replacing the one
     * pending, so a burst of events makes one request.
     */
    private fun reloadLatest() {
        eventJob?.cancel()
        eventJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            reload()
        }
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            _state.value = s.copy(isLoading = true, load = LoadState.Idle, error = null)
            try {
                val r = repository.getModerationLog(
                    listing = s.listing,
                    query = s.query.trim().ifBlank { null },
                    page = 1,
                    limit = PAGE_SIZE,
                )
                _state.value = _state.value.copy(
                    isLoading = false,
                    entries = r.log,
                    total = r.total,
                    paging = Paging().advance(r.log.size, r.total, r.next),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /** Reads the next page, from the list's foot or its retry. */
    fun loadMore() {
        val s = _state.value
        if (s.load == LoadState.Loading || s.isLoading || !s.paging.more) return
        // Tracked in loadJob so a reload replaces it rather than having its
        // page spliced into the new list.
        loadJob = viewModelScope.launch {
            _state.value = s.copy(load = LoadState.Loading)
            try {
                val r = repository.getModerationLog(
                    listing = s.listing,
                    query = s.query.trim().ifBlank { null },
                    page = s.paging.page,
                    limit = PAGE_SIZE,
                    after = s.paging.cursor,
                )
                _state.value = _state.value.copy(
                    load = LoadState.Idle,
                    entries = _state.value.entries.append(r.log) { entry -> entry.id },
                    total = r.total,
                    paging = s.paging.advance(r.log.size, r.total, r.next),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(load = LoadState.Failed(e.toMochiError()))
            }
        }
    }

    companion object {
        /** The route's argument naming the listing the log is opened for. */
        const val KEY_LISTING = "listing"

        // SavedStateHandle key for the search across process death.
        private const val KEY_QUERY = "filter_query"
    }
}
