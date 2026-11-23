// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.history

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
import org.mochios.wikis.model.Revision
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import org.mochios.wikis.ui.components.WikiInfoLoader
import javax.inject.Inject

data class PageHistoryUiState(
    val isLoading: Boolean = true,
    /** Where the next page of revisions stands: a failed one is held with its reason until retried. */
    val load: LoadState = LoadState.Idle,
    val revisions: List<Revision> = emptyList(),
    val currentVersion: Int = 0,
    val total: Int = 0,
    val offset: Int = 0,
    val wiki: WikiInfo? = null,
    val permissions: WikiPermissions = WikiPermissions(),
    /** Why the wiki's information or the first page would not load. */
    val error: MochiError? = null,
    /** A revert asked for from the list, and how it went. The failure is its
     *  own field: [error] stands for a history that would not load, and the
     *  screen reads the two differently. */
    val isReverting: Boolean = false,
    val reverted: Boolean = false,
    val revertError: MochiError? = null,
) {
    /** True when more revisions remain on the server beyond what's loaded. */
    val hasMore: Boolean get() = revisions.size < total
}

@HiltViewModel
class PageHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()
    val slug: String = savedStateHandle.get<String>("page").orEmpty()

    /** Origin of the Mochi server the session is bound to. Trimmed of trailing slash. */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(PageHistoryUiState())
    val uiState: StateFlow<PageHistoryUiState> = _uiState.asStateFlow()

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
     * Put one revision back as the page's current version.
     *
     * @param version The revision to restore.
     * @param comment The edit comment recorded against the revert; empty for
     *   the server's own note, which each reader sees in their language.
     */
    fun revert(version: Int, comment: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isReverting = true, revertError = null)
            try {
                repository.revertPage(wikiId, slug, version, comment.trim())
                _uiState.value = _uiState.value.copy(isReverting = false, reverted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isReverting = false,
                    revertError = e.toMochiError(),
                )
            }
        }
    }

    /** Clear a revert failure once the screen has said so. */
    fun clearRevertError() {
        _uiState.value = _uiState.value.copy(revertError = null)
    }

    /**
     * The wiki's information, unless it is already held, then the first page
     * of revisions: on open, and from Retry, so one retry restores both.
     */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                information.ensure()
                val response = repository.getHistory(wikiId, slug, PAGE_SIZE, 0)
                val current = response.revisions.maxOfOrNull { it.version } ?: 0
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    load = LoadState.Idle,
                    revisions = response.revisions,
                    currentVersion = current,
                    total = response.total,
                    offset = response.revisions.size,
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

    /** The next page of revisions; one that fails is held in [PageHistoryUiState.load] until retried. */
    fun loadMore() {
        val state = _uiState.value
        if (state.load == LoadState.Loading || !state.hasMore) return
        _uiState.value = state.copy(load = LoadState.Loading)
        viewModelScope.launch {
            try {
                val response = repository.getHistory(wikiId, slug, PAGE_SIZE, state.offset)
                // De-dup against already-loaded versions in case rows shifted between pages.
                val seen = state.revisions.mapTo(mutableSetOf()) { it.version }
                val merged = state.revisions + response.revisions.filter { it.version !in seen }
                _uiState.value = _uiState.value.copy(
                    load = LoadState.Idle,
                    revisions = merged,
                    currentVersion = merged.maxOfOrNull { it.version } ?: state.currentVersion,
                    total = response.total,
                    offset = state.offset + response.revisions.size,
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
