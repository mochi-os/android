// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.changes

import androidx.lifecycle.SavedStateHandle
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
import org.mochios.android.auth.SessionManager
import org.mochios.android.ui.components.LoadState
import org.mochios.wikis.model.Change
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import org.mochios.wikis.ui.components.WikiInfoLoader
import javax.inject.Inject

data class ChangesUiState(
    val isLoading: Boolean = true,
    /** Where the next page of changes stands: a failed one is held with its reason until retried. */
    val load: LoadState = LoadState.Idle,
    val changes: List<Change> = emptyList(),
    val total: Int = 0,
    val offset: Int = 0,
    val wiki: WikiInfo? = null,
    val permissions: WikiPermissions = WikiPermissions(),
    /** Why the wiki's information or the first page would not load. */
    val error: MochiError? = null,
) {
    /** True when more changes remain on the server beyond what's loaded. */
    val hasMore: Boolean get() = changes.size < total
}

@HiltViewModel
class ChangesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()

    /** Origin of the Mochi server the session is bound to. Trimmed of trailing slash. */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(ChangesUiState())
    val uiState: StateFlow<ChangesUiState> = _uiState.asStateFlow()

    private val information = WikiInfoLoader(
        fetch = { repository.getInfo(wikiId) },
        loaded = { response ->
            _uiState.value = _uiState.value.copy(
                wiki = response.wiki,
                permissions = response.permissions ?: WikiPermissions(),
            )
        },
    )

    /** The pending or in-flight [load]; cancelled when a newer one starts. */
    private var loadJob: Job? = null

    init {
        load()
    }

    /**
     * The wiki's information, unless it is already held, then the first page
     * of changes: on open, and from Retry, so one retry restores both.
     */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                information.ensure()
                val response = repository.getChanges(wikiId, PAGE_SIZE, 0)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    load = LoadState.Idle,
                    changes = response.changes,
                    total = response.total,
                    offset = response.changes.size,
                    error = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /** The next page of changes; one that fails is held in [ChangesUiState.load] until retried. */
    fun loadMore() {
        val current = _uiState.value
        if (current.load == LoadState.Loading || !current.hasMore) return
        _uiState.value = current.copy(load = LoadState.Loading)
        viewModelScope.launch {
            try {
                val response = repository.getChanges(wikiId, PAGE_SIZE, current.offset)
                // De-dup against already-loaded ids in case rows shifted between pages.
                val seen = current.changes.mapTo(mutableSetOf()) { it.id }
                val merged = current.changes + response.changes.filter { it.id !in seen }
                _uiState.value = _uiState.value.copy(
                    load = LoadState.Idle,
                    changes = merged,
                    total = response.total,
                    offset = current.offset + response.changes.size,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(load = LoadState.Failed(e.toMochiError()))
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 50
    }
}
