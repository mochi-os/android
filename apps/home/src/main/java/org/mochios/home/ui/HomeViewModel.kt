// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.home.repository.Grid
import org.mochios.home.repository.HomeRepository
import javax.inject.Inject

/**
 * The home screen's state: the [grid] to show, null until one is known;
 * whether the first fetch is still [loading]; and the [error] of a fetch that
 * left nothing to show.
 */
data class HomeUiState(
    val grid: Grid? = null,
    val loading: Boolean = true,
    val error: MochiError? = null,
)

/**
 * Shows the grid kept from last time at once, then the server's. A failed
 * fetch over a kept grid leaves that grid up, so the screen still works
 * offline; one with nothing kept shows the error.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())

    /** The screen's state. */
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var fetching: Job? = null

    init {
        viewModelScope.launch {
            repository.cached()?.let { grid ->
                if (_state.value.grid == null) {
                    _state.value = HomeUiState(grid = grid, loading = false)
                }
            }
        }
        refresh()
    }

    /**
     * Fetches the grid again, as the screen does whenever it comes back into
     * view, since apps, the user's role and the theme can all change
     * elsewhere. A fetch already under way is left to finish.
     */
    fun refresh() {
        if (fetching?.isActive == true) {
            return
        }
        fetching = viewModelScope.launch {
            try {
                _state.value = HomeUiState(grid = repository.fetch(), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val shown = _state.value.grid
                _state.value = HomeUiState(
                    grid = shown,
                    loading = false,
                    error = if (shown == null) e.toMochiError() else null,
                )
            }
        }
    }
}
