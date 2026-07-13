// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.listings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.staff.model.PendingListing
import org.mochios.staff.repository.StaffRepository
import org.mochios.staff.ws.StaffEvent
import org.mochios.staff.ws.StaffEventsBus
import javax.inject.Inject

/**
 * UI state for [ListingsScreen]. Mirrors the same axes as the web
 * `ListingsPage`:
 *
 *   - `status`     — listing lifecycle filter (`active`, `draft`, …) or null
 *                    for "any".
 *   - `moderation` — moderation-state filter (`hold`, `review`, …) or null.
 *   - `query`      — substring searched against title + description; the
 *                    screen debounces user input by 300 ms before firing.
 *
 * Pagination is page-based; the repository's `listPendingListings` accepts
 * `page` / `limit`, so we mirror the same model on Android.
 */
data class ListingsUiState(
    val status: String? = null,
    val moderation: String? = null,
    val query: String = "",
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val listings: List<PendingListing> = emptyList(),
    val total: Long = 0,
    val error: MochiError? = null,
    val pendingAction: PendingListingAction? = null,
    val submitting: Boolean = false,
)

/** Three moderator actions the dialog can drive. */
enum class ListingActionType { APPROVE, REJECT, REMOVE }

/** Inflight action context — the dialog binds this to its inputs. */
data class PendingListingAction(
    val type: ListingActionType,
    val listing: PendingListing,
)

/** One-shot events emitted to the screen (toasts). */
sealed class ListingsEvent {
    data class Toast(val message: String) : ListingsEvent()
}

private const val PAGE_SIZE = 20

@HiltViewModel
class ListingsViewModel @Inject constructor(
    private val repository: StaffRepository,
    private val eventsBus: StaffEventsBus,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ListingsUiState(
            status = savedStateHandle.get<String>(KEY_STATUS),
            moderation = savedStateHandle.get<String>(KEY_MODERATION),
            query = savedStateHandle.get<String>(KEY_QUERY) ?: "",
        ),
    )
    val state: StateFlow<ListingsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ListingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ListingsEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    init {
        reload()
        // Refresh in place whenever the moderation queue changes on the
        // server — appeals decided elsewhere, listings flipped by another
        // moderator, etc. Mirrors web's `useStaffEvents()` invalidation
        // for `/_authenticated/listings`.
        viewModelScope.launch {
            eventsBus.events
                .filter { it is StaffEvent.ModerationUpdated }
                .collect { reload() }
        }
    }

    /** Update the status filter and refetch from page 1. */
    fun setStatus(status: String?) {
        if (_state.value.status == status) return
        _state.value = _state.value.copy(status = status)
        savedStateHandle[KEY_STATUS] = status
        reload()
    }

    /** Update the moderation filter and refetch from page 1. */
    fun setModeration(moderation: String?) {
        if (_state.value.moderation == moderation) return
        _state.value = _state.value.copy(moderation = moderation)
        savedStateHandle[KEY_MODERATION] = moderation
        reload()
    }

    /**
     * Set the search query. The caller (screen) is responsible for
     * debouncing; the ViewModel always refetches when called.
     */
    fun setQuery(query: String) {
        if (_state.value.query == query) return
        _state.value = _state.value.copy(query = query)
        savedStateHandle[KEY_QUERY] = query
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            _state.value = s.copy(isLoading = true, error = null)
            try {
                val r = repository.listPendingListings(
                    status = s.status,
                    moderation = s.moderation,
                    query = s.query.takeIf { it.isNotBlank() },
                    page = 1,
                    limit = PAGE_SIZE,
                )
                _state.value = _state.value.copy(
                    isLoading = false,
                    listings = r.listings,
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
        if (s.isLoadingMore || s.isLoading || s.listings.size >= s.total) return
        val nextPage = (s.listings.size / PAGE_SIZE) + 1
        viewModelScope.launch {
            _state.value = s.copy(isLoadingMore = true)
            try {
                val r = repository.listPendingListings(
                    status = s.status,
                    moderation = s.moderation,
                    query = s.query.takeIf { it.isNotBlank() },
                    page = nextPage,
                    limit = PAGE_SIZE,
                )
                _state.value = _state.value.copy(
                    isLoadingMore = false,
                    listings = _state.value.listings + r.listings,
                    total = r.total,
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    // ---- Dialog plumbing ----

    fun openAction(type: ListingActionType, listing: PendingListing) {
        _state.value = _state.value.copy(
            pendingAction = PendingListingAction(type, listing),
        )
    }

    fun dismissAction() {
        _state.value = _state.value.copy(pendingAction = null, submitting = false)
    }

    /**
     * Submit the currently-pending action. `reason` is required for
     * REJECT / REMOVE on the wire; the dialog enforces presence so we
     * just forward whatever was typed.
     */
    fun submitAction(reason: String, notes: String) {
        val pending = _state.value.pendingAction ?: return
        if (_state.value.submitting) return
        _state.value = _state.value.copy(submitting = true)
        viewModelScope.launch {
            try {
                val id = pending.listing.id
                when (pending.type) {
                    ListingActionType.APPROVE ->
                        repository.approveListing(id, notes.takeIf { it.isNotBlank() })
                    ListingActionType.REJECT ->
                        repository.rejectListing(
                            id,
                            reason.takeIf { it.isNotBlank() },
                            notes.takeIf { it.isNotBlank() },
                        )
                    ListingActionType.REMOVE ->
                        repository.removeListing(
                            id,
                            reason.takeIf { it.isNotBlank() },
                            notes.takeIf { it.isNotBlank() },
                        )
                }
                // Optimistically drop the row from the visible list — same
                // contract as web's `setListings(prev => prev.filter(...))`.
                val before = _state.value.listings
                val after = before.filter { it.id != pending.listing.id }
                val deltaTotal = if (before.size != after.size) 1L else 0L
                _state.value = _state.value.copy(
                    submitting = false,
                    pendingAction = null,
                    listings = after,
                    total = (_state.value.total - deltaTotal).coerceAtLeast(0L),
                )
                _events.tryEmit(ListingsEvent.Toast(successMessage(pending.type)))
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false)
                _events.tryEmit(ListingsEvent.Toast(e.toMochiError().userMessage()))
            }
        }
    }

    private fun successMessage(type: ListingActionType): String = when (type) {
        ListingActionType.APPROVE -> SUCCESS_APPROVED
        ListingActionType.REJECT -> SUCCESS_REJECTED
        ListingActionType.REMOVE -> SUCCESS_REMOVED
    }

    companion object {
        // These markers are replaced by the screen with the localised
        // string before showing the snackbar — keeping the ViewModel
        // free of Android Context.
        const val SUCCESS_APPROVED = "@approved"
        const val SUCCESS_REJECTED = "@rejected"
        const val SUCCESS_REMOVED = "@removed"

        // SavedStateHandle keys for filter persistence across process death.
        private const val KEY_STATUS = "filter_status"
        private const val KEY_MODERATION = "filter_moderation"
        private const val KEY_QUERY = "filter_query"
    }
}
