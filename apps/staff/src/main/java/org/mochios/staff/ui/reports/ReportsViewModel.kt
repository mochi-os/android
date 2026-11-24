// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.reports

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
import org.mochios.staff.model.ConversationMessage
import org.mochios.staff.model.Report
import org.mochios.staff.repository.StaffRepository
import org.mochios.staff.ws.STAFF_EVENTS
import org.mochios.staff.ws.StaffEvent
import org.mochios.staff.ws.staffEvent
import javax.inject.Inject

/**
 * UI state for [ReportsScreen]. `type` is one of
 * [org.mochios.staff.ui.components.Stored.Report.type] and `status` one of its
 * statuses; null means any.
 */
data class ReportsUiState(
    val type: String? = null,
    val status: String? = null,
    val isLoading: Boolean = true,
    /** The next page, for the list's foot. */
    val load: LoadState = LoadState.Idle,
    val reports: List<Report> = emptyList(),
    val total: Long = 0,
    /** How far the list has been read. */
    val paging: Paging = Paging(),
    val error: MochiError? = null,
    val actionDialog: Report? = null,
    val submitting: Boolean = false,
    /**
     * The open report's conversation, oldest message first. Loaded only when
     * staff ask, since each read is audited; null until then, and cleared
     * whenever another report opens.
     */
    val conversation: List<ConversationMessage>? = null,
    val loadingConversation: Boolean = false,
)

/** One-shot events emitted to the screen. */
sealed interface ReportsEvent {
    data class Toast(@StringRes val messageRes: Int) : ReportsEvent
    data class Error(val error: MochiError) : ReportsEvent
}

private const val PAGE_SIZE = 20

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: StaffRepository,
    private val webSocket: MochiWebSocket,
    sessionManager: SessionManager,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ReportsUiState(
            type = savedStateHandle.get<String>(KEY_TYPE),
            status = savedStateHandle.get<String>(KEY_STATUS),
        ),
    )
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReportsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ReportsEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    /** The pending [reloadLatest]; replaced by each newer staff event. */
    private var eventJob: Job? = null
    private var moreJob: Job? = null

    /** The staff-events subscription, held while this view model lives. */
    private val subscription: String

    init {
        reload()
        // Refresh whenever a new buyer-filed report lands on the server, or
        // the socket was down and missed one — mirrors web's
        // `useStaffEvents()` invalidation for the reports queue.
        subscription = webSocket.subscribe(sessionManager.getServerUrlBlocking(), STAFF_EVENTS, app = "staff") { frame ->
            when (staffEvent(frame)) {
                StaffEvent.NewReport, is StaffEvent.Unknown -> reloadLatest()
                else -> Unit
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        webSocket.unsubscribe(subscription)
    }

    fun setType(type: String?) {
        if (_state.value.type == type) return
        _state.value = _state.value.copy(type = type)
        savedStateHandle[KEY_TYPE] = type
        reload()
    }

    fun setStatus(status: String?) {
        if (_state.value.status == status) return
        _state.value = _state.value.copy(status = status)
        savedStateHandle[KEY_STATUS] = status
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
                val r = repository.listReports(
                    type = s.type,
                    status = s.status,
                    page = 1,
                    limit = PAGE_SIZE,
                )
                _state.value = _state.value.copy(
                    isLoading = false,
                    reports = r.reports,
                    total = r.total,
                    paging = Paging().advance(r.reports.size, r.total, r.next),
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
                val r = repository.listReports(
                    type = s.type,
                    status = s.status,
                    page = s.paging.page,
                    limit = PAGE_SIZE,
                    after = s.paging.cursor,
                )
                _state.value = _state.value.copy(
                    load = LoadState.Idle,
                    reports = _state.value.reports.append(r.reports) { report -> report.id },
                    total = r.total,
                    paging = s.paging.advance(r.reports.size, r.total, r.next),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(load = LoadState.Failed(e.toMochiError()))
            }
        }
    }

    fun openAction(report: Report) {
        _state.value = _state.value.copy(
            actionDialog = report,
            conversation = null,
            loadingConversation = false,
        )
    }

    fun dismissAction() {
        _state.value = _state.value.copy(
            actionDialog = null,
            submitting = false,
            conversation = null,
            loadingConversation = false,
        )
    }

    /** Load the conversation the open report was filed from. */
    fun showConversation() {
        val report = _state.value.actionDialog ?: return
        if (report.thread.isBlank() || _state.value.loadingConversation) return
        _state.value = _state.value.copy(loadingConversation = true)
        viewModelScope.launch {
            try {
                val messages = repository.getConversation(report.thread)
                if (_state.value.actionDialog?.id == report.id) {
                    _state.value = _state.value.copy(
                        loadingConversation = false,
                        conversation = messages,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_state.value.actionDialog?.id == report.id) {
                    _state.value = _state.value.copy(loadingConversation = false)
                }
                _events.tryEmit(ReportsEvent.Error(e.toMochiError()))
            }
        }
    }

    /**
     * Submit a report action: dismiss / warn / remove / suspend / ban. The row
     * drops from the list on success.
     */
    fun actionReport(action: String, notes: String) {
        val current = _state.value.actionDialog ?: return
        if (_state.value.submitting) return
        _state.value = _state.value.copy(submitting = true)
        viewModelScope.launch {
            try {
                repository.actionReport(
                    id = current.id,
                    action = action,
                    notes = notes.takeIf { it.isNotBlank() },
                )
                val before = _state.value.reports
                val after = before.filter { it.id != current.id }
                val deltaTotal = if (before.size != after.size) 1L else 0L
                _state.value = _state.value.copy(
                    submitting = false,
                    actionDialog = null,
                    reports = after,
                    total = (_state.value.total - deltaTotal).coerceAtLeast(0L),
                )
                _events.tryEmit(ReportsEvent.Toast(R.string.staff_reports_action_taken_toast))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false)
                _events.tryEmit(ReportsEvent.Error(e.toMochiError()))
            }
        }
    }

    companion object {
        // SavedStateHandle keys for filter persistence across process death.
        private const val KEY_TYPE = "filter_type"
        private const val KEY_STATUS = "filter_status"
    }
}
