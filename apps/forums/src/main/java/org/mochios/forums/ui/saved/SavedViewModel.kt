// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.mochios.android.auth.SessionManager
import org.mochios.forums.model.SavedItem
import org.mochios.forums.repository.SavedRepository
import javax.inject.Inject

/**
 * Backs the Saved (read-later) screen. The saved list is owned by the app-wide
 * [SavedRepository] so it stays in sync with the bookmark toggles on post
 * cards; this ViewModel hydrates it on open and exposes a clear-all action.
 */
@HiltViewModel
class SavedViewModel @Inject constructor(
    private val savedRepository: SavedRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    val saved: StateFlow<List<SavedItem>> = savedRepository.saved

    /** Base URL for resolving attachment thumbnail paths to absolute URLs. */
    val serverUrl: String = sessionManager.getServerUrlBlocking()

    private val _clearFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val clearFailed: SharedFlow<Unit> = _clearFailed.asSharedFlow()

    init {
        viewModelScope.launch { savedRepository.load() }
    }

    fun clearAll() {
        viewModelScope.launch {
            try {
                savedRepository.clear()
            } catch (_: Exception) {
                _clearFailed.tryEmit(Unit)
            }
        }
    }

    /** Remove a single post from the saved list. Mirrors the bookmark toggle on
     *  post cards via the shared [SavedRepository]. */
    fun remove(id: String) {
        viewModelScope.launch {
            try {
                savedRepository.remove(id)
            } catch (_: Exception) {
                _clearFailed.tryEmit(Unit)
            }
        }
    }
}
