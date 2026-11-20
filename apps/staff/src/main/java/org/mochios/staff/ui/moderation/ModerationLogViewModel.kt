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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.ui.components.LoadState
import org.mochios.android.util.append
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.staff.model.ModerationEntry
import org.mochios.staff.repository.StaffRepository
import org.mochios.staff.ws.StaffEvent
import org.mochios.staff.ws.StaffEventsBus
import javax.inject.Inject

data class ModerationLogUiState(
    val listingId: String? = null,
    val isLoading: Boolean = true,
    /** The next page, for the list's foot. */
    val load: LoadState = LoadState.Idle,
    val entries: List<ModerationEntry> = emptyList(),
    val total: Long = 0,
    val error: MochiError? = null,
)

private const val PAGE_SIZE = 20

@HiltViewModel
class ModerationLogViewModel @Inject constructor(
    private val repository: StaffRepository,
    private val eventsBus: StaffEventsBus,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ModerationLogUiState(
            listingId = savedStateHandle.get<String>(KEY_LISTING_ID),
        ),
    )
    val state: StateFlow<ModerationLogUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** Pages read since the last reload; rows a page repeats are dropped, so the count of rows cannot say. */
    private var pagesLoaded = 0

    /** The pending [reloadLatest]; replaced by each newer staff event. */
    private var eventJob: Job? = null

    init {
        reload()
        // Refetch whenever the moderation queue changes server-side — same
        // signal that triggers the listings page's invalidation.
        viewModelScope.launch {
            eventsBus.events
                .filter { it is StaffEvent.ModerationUpdated || it is StaffEvent.Unknown }
                .collect { _ -> reloadLatest() }
        }
    }

    /** Apply / clear the listing-id filter. */
    fun setListingId(listingId: String?) {
        if (_state.value.listingId == listingId) return
        _state.value = _state.value.copy(listingId = listingId)
        savedStateHandle[KEY_LISTING_ID] = listingId
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
                    listingId = s.listingId,
                    page = 1,
                    limit = PAGE_SIZE,
                )
                pagesLoaded = 1
                _state.value = _state.value.copy(
                    isLoading = false,
                    entries = r.log,
                    total = r.total,
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

    fun loadMore() {
        val s = _state.value
        if (s.load == LoadState.Loading || s.isLoading || s.entries.size >= s.total) return
        val nextPage = pagesLoaded + 1
        // Tracked in loadJob so a reload replaces it rather than having its
        // page spliced into the new list.
        loadJob = viewModelScope.launch {
            _state.value = s.copy(load = LoadState.Loading)
            try {
                val r = repository.getModerationLog(
                    listingId = s.listingId,
                    page = nextPage,
                    limit = PAGE_SIZE,
                )
                pagesLoaded = nextPage
                val listed = _state.value.entries.append(r.log) { entry -> entry.id }
                _state.value = _state.value.copy(
                    load = LoadState.Idle,
                    entries = listed,
                    // A page with no rows ends the list, whatever total it claims.
                    total = if (r.log.isEmpty()) listed.size.toLong() else r.total,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(load = LoadState.Failed(e.toMochiError()))
            }
        }
    }

    companion object {
        // SavedStateHandle keys for filter persistence across process death.
        private const val KEY_LISTING_ID = "filter_listing_id"
    }
}
