package org.mochios.staff.ui.reports

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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.staff.model.Report
import org.mochios.staff.repository.StaffRepository
import org.mochios.staff.ws.StaffEvent
import org.mochios.staff.ws.StaffEventsBus
import javax.inject.Inject

/**
 * UI state for [ReportsScreen]. Mirrors the same axes as the web
 * `ReportsPage`:
 *
 *   - `type`   — `"listing"` / `"user"` / null for any.
 *   - `status` — `"pending"` / `"reviewed"` / `"actioned"` / `"dismissed"`
 *                or null for any.
 *
 * Pagination is page-based; the repository's `listReports` accepts
 * `page` / `limit`, so we mirror the same model on Android.
 */
data class ReportsUiState(
    val type: String? = null,
    val status: String? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val reports: List<Report> = emptyList(),
    val total: Long = 0,
    val error: MochiError? = null,
    val actionDialog: Report? = null,
    val submitting: Boolean = false,
)

/** One-shot events emitted to the screen (toasts). */
sealed class ReportsEvent {
    data class Toast(val message: String) : ReportsEvent()
}

private const val PAGE_SIZE = 20

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: StaffRepository,
    private val eventsBus: StaffEventsBus,
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

    init {
        reload()
        // Refresh whenever a new buyer-filed report lands on the server —
        // mirrors web's `useStaffEvents()` invalidation for the reports queue.
        viewModelScope.launch {
            eventsBus.events
                .filter { it is StaffEvent.NewReport }
                .collect { reload() }
        }
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

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            _state.value = s.copy(isLoading = true, error = null)
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
        if (s.isLoadingMore || s.isLoading || s.reports.size >= s.total) return
        val nextPage = (s.reports.size / PAGE_SIZE) + 1
        viewModelScope.launch {
            _state.value = s.copy(isLoadingMore = true)
            try {
                val r = repository.listReports(
                    type = s.type,
                    status = s.status,
                    page = nextPage,
                    limit = PAGE_SIZE,
                )
                _state.value = _state.value.copy(
                    isLoadingMore = false,
                    reports = _state.value.reports + r.reports,
                    total = r.total,
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    fun openAction(report: Report) {
        _state.value = _state.value.copy(actionDialog = report)
    }

    fun dismissAction() {
        _state.value = _state.value.copy(actionDialog = null, submitting = false)
    }

    /**
     * Submit a report action. `action` is one of `dismiss` / `warn` /
     * `remove` / `suspend` / `ban`. Notes are optional. After a
     * successful action, the row is optimistically dropped from the
     * visible list (status moves out of `pending`).
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
                _events.tryEmit(ReportsEvent.Toast(SUCCESS_TOAST))
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false)
                _events.tryEmit(ReportsEvent.Toast(e.toMochiError().userMessage()))
            }
        }
    }

    companion object {
        // Replaced by the screen with the localised success string before
        // the snackbar is shown — keeps the ViewModel free of Context.
        const val SUCCESS_TOAST = "@action_taken"

        // SavedStateHandle keys for filter persistence across process death.
        private const val KEY_TYPE = "filter_type"
        private const val KEY_STATUS = "filter_status"
    }
}
