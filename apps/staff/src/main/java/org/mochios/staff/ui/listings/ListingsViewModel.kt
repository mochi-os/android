// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.listings

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import org.mochios.staff.R
import org.mochios.staff.model.PendingListing
import org.mochios.staff.repository.StaffRepository
import org.mochios.staff.ws.STAFF_EVENTS
import org.mochios.staff.ws.StaffEvent
import org.mochios.staff.ws.staffEvent
import javax.inject.Inject

/**
 * UI state for [ListingsScreen]. `query` is matched server-side against title
 * and description.
 */
data class ListingsUiState(
    val status: String? = null,
    val moderation: String? = null,
    val query: String = "",
    val isLoading: Boolean = true,
    /** The next page, for the list's foot. */
    val load: LoadState = LoadState.Idle,
    val listings: List<PendingListing> = emptyList(),
    val total: Long = 0,
    /** How far the list has been read. */
    val paging: Paging = Paging(),
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

/** One-shot events emitted to the screen. */
sealed interface ListingsEvent {
    data class Toast(@StringRes val messageRes: Int) : ListingsEvent
    data class Error(val error: MochiError) : ListingsEvent
}

/** What the snackbar says once [type] has been done. */
@StringRes
internal fun listingActionDone(type: ListingActionType): Int = when (type) {
    ListingActionType.APPROVE -> R.string.staff_listings_approved_toast
    ListingActionType.REJECT -> R.string.staff_listings_rejected_toast
    ListingActionType.REMOVE -> R.string.staff_listings_removed_toast
}

private const val PAGE_SIZE = 20

@HiltViewModel
class ListingsViewModel @Inject constructor(
    private val repository: StaffRepository,
    private val webSocket: MochiWebSocket,
    sessionManager: SessionManager,
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

    /** The pending [reloadLatest]; replaced by each newer staff event. */
    private var eventJob: Job? = null
    private var moreJob: Job? = null

    /** The staff-events subscription, held while this view model lives. */
    private val subscription: String

    init {
        reload()
        // Refresh in place whenever the moderation queue changes on the
        // server — appeals decided elsewhere, listings flipped by another
        // moderator, etc. — or the socket was down and missed it. Mirrors
        // web's `useStaffEvents()` invalidation for `/_authenticated/listings`.
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
        // A load-more still in flight belongs to the filter being replaced;
        // left running, it would splice that filter's rows into the new list.
        moreJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            _state.value = s.copy(isLoading = true, load = LoadState.Idle, error = null)
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
                    paging = Paging().advance(r.listings.size, r.total, r.next),
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
        moreJob = viewModelScope.launch {
            _state.value = s.copy(load = LoadState.Loading)
            try {
                val r = repository.listPendingListings(
                    status = s.status,
                    moderation = s.moderation,
                    query = s.query.takeIf { it.isNotBlank() },
                    page = s.paging.page,
                    limit = PAGE_SIZE,
                    after = s.paging.cursor,
                )
                _state.value = _state.value.copy(
                    load = LoadState.Idle,
                    listings = _state.value.listings.append(r.listings) { listing -> listing.id },
                    total = r.total,
                    paging = s.paging.advance(r.listings.size, r.total, r.next),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(load = LoadState.Failed(e.toMochiError()))
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
     * Submit the pending action. The wire requires `reason` for REJECT and
     * REMOVE; the dialog enforces it.
     */
    fun submitAction(reason: String, notes: String) {
        val pending = _state.value.pendingAction ?: return
        if (_state.value.submitting) return
        _state.value = _state.value.copy(submitting = true)
        viewModelScope.launch {
            try {
                submitListingAction(repository, pending, reason, notes)
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
                _events.tryEmit(ListingsEvent.Toast(listingActionDone(pending.type)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false)
                _events.tryEmit(ListingsEvent.Error(e.toMochiError()))
            }
        }
    }

    companion object {
        // SavedStateHandle keys for filter persistence across process death.
        private const val KEY_STATUS = "filter_status"
        private const val KEY_MODERATION = "filter_moderation"
        private const val KEY_QUERY = "filter_query"
    }
}

/** Sends [pending] to the Comptroller, with [reason] and [notes] where given. */
internal suspend fun submitListingAction(
    repository: StaffRepository,
    pending: PendingListingAction,
    reason: String,
    notes: String,
) {
    val id = pending.listing.id
    val written = notes.takeIf { it.isNotBlank() }
    when (pending.type) {
        ListingActionType.APPROVE -> repository.approveListing(id, written)
        ListingActionType.REJECT -> repository.rejectListing(id, reason.takeIf { it.isNotBlank() }, written)
        ListingActionType.REMOVE -> repository.removeListing(id, reason.takeIf { it.isNotBlank() }, written)
    }
}
