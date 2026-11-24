// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.appeals

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
import org.mochios.android.util.append
import org.mochios.android.api.userMessage
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.staff.model.Appeal
import org.mochios.staff.repository.StaffRepository
import org.mochios.staff.ws.STAFF_EVENTS
import org.mochios.staff.ws.StaffEvent
import org.mochios.staff.ws.staffEvent
import javax.inject.Inject

/**
 * The pending appeals, newest first. `appeals/list` has no filters; it pages
 * by cursor like every staff list.
 */
data class AppealsUiState(
    val isLoading: Boolean = true,
    /** The next page, for the list's foot. */
    val load: LoadState = LoadState.Idle,
    val appeals: List<Appeal> = emptyList(),
    val total: Long = 0,
    /** How far the list has been read. */
    val paging: Paging = Paging(),
    val error: MochiError? = null,
    val decideDialog: Appeal? = null,
    val submitting: Boolean = false,
)

/** One-shot events emitted to the screen (toasts). */
sealed class AppealsEvent {
    /** Decision was successful — screen substitutes the matching localised string. */
    data class Decided(val upheld: Boolean) : AppealsEvent()

    /** Pre-localised error message. */
    data class Toast(val message: String) : AppealsEvent()
}

private const val PAGE_SIZE = 20

@HiltViewModel
class AppealsViewModel @Inject constructor(
    private val repository: StaffRepository,
    private val webSocket: MochiWebSocket,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AppealsUiState())
    val state: StateFlow<AppealsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AppealsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AppealsEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    /** The pending [reloadLatest]; replaced by each newer staff event. */
    private var eventJob: Job? = null
    private var moreJob: Job? = null

    /** The staff-events subscription, held while this view model lives. */
    private val subscription: String

    init {
        reload()
        // Appeals are triggered by moderation events (listings flipping
        // between hold / review / rejected / appealed) — refresh whenever
        // the moderation queue changes server-side, or the socket was down
        // and missed it.
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
        // A load-more still in flight belongs to the list being replaced;
        // left running, it would splice its rows into the new one.
        moreJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            _state.value = s.copy(isLoading = true, load = LoadState.Idle, error = null)
            try {
                val r = repository.listAppeals(page = 1, limit = PAGE_SIZE)
                _state.value = _state.value.copy(
                    isLoading = false,
                    appeals = r.appeals,
                    total = r.total,
                    paging = Paging().advance(r.appeals.size, r.total, r.next),
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
                val r = repository.listAppeals(page = s.paging.page, limit = PAGE_SIZE, after = s.paging.cursor)
                _state.value = _state.value.copy(
                    load = LoadState.Idle,
                    appeals = _state.value.appeals.append(r.appeals) { appeal -> appeal.id },
                    total = r.total,
                    paging = s.paging.advance(r.appeals.size, r.total, r.next),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(load = LoadState.Failed(e.toMochiError()))
            }
        }
    }

    fun openDecide(appeal: Appeal) {
        _state.value = _state.value.copy(decideDialog = appeal)
    }

    fun dismissDecide() {
        _state.value = _state.value.copy(decideDialog = null, submitting = false)
    }

    /**
     * `decision` is `upheld` or `denied`.
     */
    fun decideAppeal(decision: String, notes: String) {
        val current = _state.value.decideDialog ?: return
        if (_state.value.submitting) return
        _state.value = _state.value.copy(submitting = true)
        viewModelScope.launch {
            try {
                repository.decideAppeal(
                    listingId = current.listing,
                    decision = decision,
                    notes = notes.takeIf { it.isNotBlank() },
                )
                val before = _state.value.appeals
                val after = before.filter { it.id != current.id }
                val deltaTotal = if (before.size != after.size) 1L else 0L
                _state.value = _state.value.copy(
                    submitting = false,
                    decideDialog = null,
                    appeals = after,
                    total = (_state.value.total - deltaTotal).coerceAtLeast(0L),
                )
                _events.tryEmit(AppealsEvent.Decided(decision == "upheld"))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(submitting = false)
                _events.tryEmit(AppealsEvent.Toast(e.toMochiError().userMessage()))
            }
        }
    }
}
