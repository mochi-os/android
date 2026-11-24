// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.listing

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.staff.model.ListingView
import org.mochios.staff.repository.StaffRepository
import org.mochios.staff.ui.listings.ListingActionType
import org.mochios.staff.ui.listings.PendingListingAction
import org.mochios.staff.ui.listings.listingActionDone
import org.mochios.staff.ui.listings.submitListingAction
import javax.inject.Inject

data class ListingUiState(
    val isLoading: Boolean = true,
    val view: ListingView? = null,
    val error: MochiError? = null,
    val pendingAction: PendingListingAction? = null,
    val submitting: Boolean = false,
)

/** One-shot events emitted to the screen. */
sealed interface ListingEvent {
    data class Toast(@StringRes val messageRes: Int) : ListingEvent
    data class Error(val error: MochiError) : ListingEvent
}

/**
 * Backs [ListingScreen]: one listing as staff see it, whatever its status,
 * read through `listings/get`, and the moderator actions on it.
 */
@HiltViewModel
class ListingViewModel @Inject constructor(
    private val repository: StaffRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** The listing's id, from the route. */
    val id: String = savedStateHandle.get<String>("id").orEmpty()

    private val _state = MutableStateFlow(ListingUiState())
    val state: StateFlow<ListingUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ListingEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ListingEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    /** Reads the listing again, as the error state's retry and a finished action do. */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val view = repository.getListing(id)
                _state.value = _state.value.copy(isLoading = false, view = view)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun openAction(type: ListingActionType) {
        val listing = _state.value.view?.listing ?: return
        _state.value = _state.value.copy(pendingAction = PendingListingAction(type, listing))
    }

    fun dismissAction() {
        _state.value = _state.value.copy(pendingAction = null, submitting = false)
    }

    /** Submits the open action, then reads the listing again for its new state and history. */
    fun submitAction(reason: String, notes: String) {
        val pending = _state.value.pendingAction ?: return
        if (_state.value.submitting) return
        _state.value = _state.value.copy(submitting = true)
        viewModelScope.launch {
            try {
                submitListingAction(repository, pending, reason, notes)
                _state.value = _state.value.copy(submitting = false, pendingAction = null)
                _events.tryEmit(ListingEvent.Toast(listingActionDone(pending.type)))
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false)
                _events.tryEmit(ListingEvent.Error(e.toMochiError()))
            }
        }
    }
}
