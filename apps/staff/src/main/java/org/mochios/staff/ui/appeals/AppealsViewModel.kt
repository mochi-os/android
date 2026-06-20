// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.appeals

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
import org.mochios.staff.model.Appeal
import org.mochios.staff.repository.StaffRepository
import org.mochios.staff.ws.StaffEvent
import org.mochios.staff.ws.StaffEventsBus
import javax.inject.Inject

/**
 * UI state for [AppealsScreen].
 *
 * The appeals listing has no filter axes — it always shows pending
 * appeals (the Comptroller treats every `moderation` row with
 * `action == "appealed"` and no later `upheld` / `denied` decision as
 * pending). Once a decision is taken, the row drops out of the list.
 *
 * Pagination piggybacks on the (page-less) `/appeals/list` endpoint —
 * the staff Comptroller currently returns the full set in one
 * response, but we expose [loadMore] so a future paginated server can
 * be wired in without re-shaping the screen.
 */
data class AppealsUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val appeals: List<Appeal> = emptyList(),
    val total: Long = 0,
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

@HiltViewModel
class AppealsViewModel @Inject constructor(
    private val repository: StaffRepository,
    private val eventsBus: StaffEventsBus,
) : ViewModel() {

    private val _state = MutableStateFlow(AppealsUiState())
    val state: StateFlow<AppealsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AppealsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AppealsEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    init {
        reload()
        // Appeals are triggered by moderation events (listings flipping
        // between hold / review / rejected / appealed) — refresh whenever
        // the moderation queue changes server-side.
        viewModelScope.launch {
            eventsBus.events
                .filter { it is StaffEvent.ModerationUpdated }
                .collect { reload() }
        }
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            _state.value = s.copy(isLoading = true, error = null)
            try {
                val r = repository.listAppeals()
                _state.value = _state.value.copy(
                    isLoading = false,
                    appeals = r.appeals,
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
        // The /appeals/list endpoint doesn't paginate today; once a paginated
        // server arrives, fan out the logic from [ReportsViewModel.loadMore].
    }

    fun openDecide(appeal: Appeal) {
        _state.value = _state.value.copy(decideDialog = appeal)
    }

    fun dismissDecide() {
        _state.value = _state.value.copy(decideDialog = null, submitting = false)
    }

    /**
     * Submit a decision. `decision` is `upheld` (approve listing) or
     * `denied` (keep rejected). After a successful decide, the row
     * drops out of the visible list optimistically.
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
                _state.value = _state.value.copy(submitting = false)
                _events.tryEmit(AppealsEvent.Toast(e.toMochiError().userMessage()))
            }
        }
    }
}
