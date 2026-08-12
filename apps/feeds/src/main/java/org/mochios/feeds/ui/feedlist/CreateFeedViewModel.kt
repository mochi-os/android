// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feedlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.feeds.repository.FeedsRepository
import javax.inject.Inject

/**
 * State of the create-feed screen.
 *
 * @property isCreating true while the create request is in flight.
 * @property error what went wrong on the last create attempt, if anything.
 * @property createdFeedId set to the new feed's id after a successful create so
 *   the screen can navigate into it; cleared once consumed.
 */
data class CreateFeedUiState(
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val createdFeedId: String? = null
)

/** Drives the create-feed screen. */
@HiltViewModel
class CreateFeedViewModel @Inject constructor(
    private val repository: FeedsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateFeedUiState())
    val uiState: StateFlow<CreateFeedUiState> = _uiState.asStateFlow()

    /** Clears the pending-navigation id once the screen has opened the feed. */
    fun consumeCreatedFeed() {
        _uiState.value = _uiState.value.copy(createdFeedId = null)
    }

    /** Creates a feed and reports its id back through [CreateFeedUiState]. */
    fun createFeed(name: String, privacy: String, memories: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            try {
                val feed = repository.createFeed(name, privacy, memories)
                val newFeedId = feed.fingerprint.ifEmpty { feed.id }
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createdFeedId = newFeedId.takeIf { id -> id.isNotEmpty() }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = e.toMochiError()
                )
            }
        }
    }
}
