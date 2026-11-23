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
import org.mochios.wikis.model.RevisionDetail
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import org.mochios.wikis.ui.components.WikiInfoLoader
import javax.inject.Inject

data class RevisionViewUiState(
    val isLoading: Boolean = true,
    val revision: RevisionDetail? = null,
    val currentVersion: Int = 0,
    /** The revision before this one, to compare against; 0 when there is none. */
    val previous: Int = 0,
    val wiki: WikiInfo? = null,
    val permissions: WikiPermissions = WikiPermissions(),
    /** Why the wiki's information or the revision would not load. */
    val error: MochiError? = null,
    /** Compare-changes mode: when on, the body shows a line diff against the
     *  previous revision instead of the rendered markdown. Mirrors web's
     *  revision-view "Compare changes" toggle. */
    val showDiff: Boolean = false,
    val previousRevision: RevisionDetail? = null,
    val previousLoading: Boolean = false,
    /** Why the previous revision would not load for the comparison. */
    val compareError: MochiError? = null,
    /** A revert asked for from this screen, and how it went. The failure is
     *  its own field: [error] stands for a revision that would not load, and
     *  the screen reads the two differently. */
    val isReverting: Boolean = false,
    val reverted: Boolean = false,
    val revertError: MochiError? = null,
) {
    /** Whether there is an earlier revision to compare this one with. */
    val hasPrevious: Boolean get() = previous > 0
}

@HiltViewModel
class RevisionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()
    val slug: String = savedStateHandle.get<String>("page").orEmpty()
    val version: Int = savedStateHandle.get<Int>("version") ?: 0

    /** Origin of the Mochi server the session is bound to. Trimmed of trailing slash. */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(RevisionViewUiState())
    val uiState: StateFlow<RevisionViewUiState> = _uiState.asStateFlow()

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
     * The wiki's information, unless it is already held, then the revision:
     * on open, and from Retry, so one retry restores both.
     */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                information.ensure()
                val response = repository.getRevision(wikiId, slug, version)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    revision = response.revision,
                    currentVersion = response.version.current,
                    // A server that predates the field says nothing of the
                    // revision before, and the one before is then taken to be
                    // the version below.
                    previous = response.version.previous ?: (version - 1).coerceAtLeast(0),
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

    /**
     * Put this revision back as the page's current version. The page is the
     * one being viewed, so the caller sends the reader there once it lands.
     *
     * @param comment The edit comment recorded against the revert; empty for
     *   the server's own note, which each reader sees in their language.
     */
    fun revert(comment: String) {
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
     * Switch between the rendered page and a diff against the previous
     * revision, reading that revision the first time it is wanted, and again
     * after a failure.
     */
    fun toggleDiff() {
        val showing = !_uiState.value.showDiff
        _uiState.value = _uiState.value.copy(showDiff = showing)
        val state = _uiState.value
        if (showing && state.hasPrevious && state.previousRevision == null && !state.previousLoading) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(previousLoading = true, compareError = null)
                try {
                    val resp = repository.getRevision(wikiId, slug, state.previous)
                    _uiState.value = _uiState.value.copy(
                        previousRevision = resp.revision,
                        previousLoading = false,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        previousLoading = false,
                        compareError = e.toMochiError(),
                    )
                }
            }
        }
    }
}
