// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.moderation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.staff.model.ModerationEntry
import org.mochios.staff.repository.StaffRepository
import org.mochios.staff.ws.StaffEvent
import org.mochios.staff.ws.StaffEventsBus
import javax.inject.Inject

/**
 * UI state for [ModerationLogScreen].
 *
 *   - `listingId` — when non-null, scopes the feed to a single listing
 *                   (matches the web `?listing=<id>` URL query).
 *
 * Pagination uses the same page-based contract as listings; the entry
 * count and page size mirror the web `useLoadMore` behaviour.
 */
data class ModerationLogUiState(
    val listingId: String? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
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

    init {
        reload()
        // Refetch whenever the moderation queue changes server-side — same
        // signal that triggers the listings page's invalidation.
        viewModelScope.launch {
            eventsBus.events
                .filter { it is StaffEvent.ModerationUpdated }
                .collect { reload() }
        }
    }

    /** Apply / clear the listing-id filter. */
    fun setListingId(listingId: String?) {
        if (_state.value.listingId == listingId) return
        _state.value = _state.value.copy(listingId = listingId)
        savedStateHandle[KEY_LISTING_ID] = listingId
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            _state.value = s.copy(isLoading = true, error = null)
            try {
                val r = repository.getModerationLog(
                    listingId = s.listingId,
                    page = 1,
                    limit = PAGE_SIZE,
                )
                _state.value = _state.value.copy(
                    isLoading = false,
                    entries = r.log,
                    total = r.total,
                )
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
        if (s.isLoadingMore || s.isLoading || s.entries.size >= s.total) return
        val nextPage = (s.entries.size / PAGE_SIZE) + 1
        viewModelScope.launch {
            _state.value = s.copy(isLoadingMore = true)
            try {
                val r = repository.getModerationLog(
                    listingId = s.listingId,
                    page = nextPage,
                    limit = PAGE_SIZE,
                )
                _state.value = _state.value.copy(
                    isLoadingMore = false,
                    entries = _state.value.entries + r.log,
                    total = r.total,
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    companion object {
        // SavedStateHandle keys for filter persistence across process death.
        private const val KEY_LISTING_ID = "filter_listing_id"
    }
}
