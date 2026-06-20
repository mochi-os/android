// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.document

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.settings.api.DocumentApi
import retrofit2.Response
import javax.inject.Inject

/**
 * Read-only view-model for the legal-document viewer: it loads and exposes one
 * document (privacy / rules / terms) for display. There is intentionally no
 * edit/save path here — operator editing lives in SystemDocumentsScreen (which
 * also carries the per-language dimension). See [DocumentApi].
 */
data class DocumentUiState(
    val kind: String = "",
    val isLoading: Boolean = true,
    val body: String = "",
    val error: MochiError? = null,
)

@HiltViewModel
class DocumentViewModel @Inject constructor(
    private val api: DocumentApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val kind: String = savedStateHandle.get<String>("kind").orEmpty()

    private val _uiState = MutableStateFlow(DocumentUiState(kind = kind))
    val uiState: StateFlow<DocumentUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // `body` is the markdown source; HtmlContent renders it. (The
                // response also carries server-rendered `html` if ever needed.)
                val data = api.getDocument(kind).bodyOrThrow()
                _uiState.value = _uiState.value.copy(isLoading = false, body = data.body)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    private fun <T> Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) throw RuntimeException("HTTP ${code()}")
        return body() ?: throw RuntimeException("empty body")
    }
}
