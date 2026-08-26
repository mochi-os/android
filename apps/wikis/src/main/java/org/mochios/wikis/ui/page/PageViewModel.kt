// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.page

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import org.mochios.wikis.model.PageFetchResponse
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPage
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

sealed class PageViewEvent {
    /** Copy this URL to the clipboard and show the "RSS URL copied" toast. */
    data class CopyRssUrl(val url: String) : PageViewEvent()

    /** Display a transient error toast (already localised). */
    data class ShowError(val error: MochiError) : PageViewEvent()

    /** The page is gone; the screen showing it has to leave. */
    object Deleted : PageViewEvent()
}

data class PageViewUiState(
    val isLoading: Boolean = true,
    val page: WikiPage? = null,
    val missingLinks: List<String> = emptyList(),
    val commentCount: Int = 0,
    val wiki: WikiInfo? = null,
    val permissions: WikiPermissions = WikiPermissions(),
    val error: MochiError? = null,
    val notFound: Boolean = false,
    val isDeleting: Boolean = false,
)

@HiltViewModel
class PageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()
    val slug: String = savedStateHandle.get<String>("page").orEmpty()

    /** Origin of the Mochi server the session is bound to. Trimmed of trailing slash. */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(PageViewUiState())
    val uiState: StateFlow<PageViewUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PageViewEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PageViewEvent> = _events.asSharedFlow()

    init {
        loadInfo()
        loadPage()
    }

    fun loadInfo() {
        viewModelScope.launch {
            try {
                val response = repository.getInfo(wikiId)
                _uiState.value = _uiState.value.copy(
                    wiki = response.wiki,
                    permissions = response.permissions ?: WikiPermissions(),
                )
            } catch (e: Exception) {
                // Don't surface as a hard error — the page-body load will
                // surface the real error if there is one. Capture for
                // diagnostics only.
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun loadPage() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, notFound = false, error = null)
            try {
                when (val response = repository.getPage(wikiId, slug)) {
                    is PageFetchResponse.Page -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            page = response.page,
                            missingLinks = response.missingLinks ?: emptyList(),
                            commentCount = response.commentCount ?: 0,
                            notFound = false,
                            error = null,
                        )
                    }
                    is PageFetchResponse.NotFound -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            page = null,
                            missingLinks = emptyList(),
                            commentCount = 0,
                            notFound = true,
                            error = null,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /**
     * Delete the page this screen is showing. Confirmed by the dialog first —
     * this is the yes, not the question.
     */
    fun delete() {
        if (_uiState.value.isDeleting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            try {
                repository.deletePage(wikiId, slug)
                _uiState.value = _uiState.value.copy(isDeleting = false)
                _events.emit(PageViewEvent.Deleted)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isDeleting = false)
                _events.emit(PageViewEvent.ShowError(e.toMochiError()))
            }
        }
    }

    suspend fun unsubscribe() {
        repository.unsubscribeWiki(wikiId)
    }

    fun copyRssUrl(mode: String) {
        viewModelScope.launch {
            try {
                val token = repository.wikiRssToken(wikiId, mode)
                val url = "$serverUrl/wikis/$wikiId/-/rss?token=$token"
                _events.emit(PageViewEvent.CopyRssUrl(url))
            } catch (e: Exception) {
                _events.emit(PageViewEvent.ShowError(e.toMochiError()))
            }
        }
    }

    /** Build the canonical share URL for this page on the bound server. */
    fun shareUrl(): String = "$serverUrl/wikis/$wikiId/$slug"

    fun updatePageTags(tags: List<String>) {
        val current = _uiState.value.page ?: return
        _uiState.value = _uiState.value.copy(page = current.copy(tags = tags))
    }
}
