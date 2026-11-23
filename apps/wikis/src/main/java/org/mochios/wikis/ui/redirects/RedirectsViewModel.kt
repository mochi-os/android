// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.redirects

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
import org.mochios.android.util.NaturalCompare
import org.mochios.wikis.R
import org.mochios.wikis.model.Redirect
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

data class RedirectsUiState(
    val isLoading: Boolean = true,
    val redirects: List<Redirect> = emptyList(),
    /** Why the list could not be loaded; an action's failure goes to the snackbar instead. */
    val error: MochiError? = null,
    /** The source of the redirect being deleted; its confirmation stays open until the server answers. */
    val deleting: String? = null,
)

/** [messageRes] with [args]; for a failure, the server's reason in [error] where it sent one. */
data class RedirectsSnackbar(
    val messageRes: Int,
    val args: List<Any> = emptyList(),
    val error: MochiError? = null,
)

@HiltViewModel
class RedirectsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()

    private val _uiState = MutableStateFlow(RedirectsUiState())
    val uiState: StateFlow<RedirectsUiState> = _uiState.asStateFlow()

    private val _snackbar = MutableSharedFlow<RedirectsSnackbar>(extraBufferCapacity = 4)
    val snackbar: SharedFlow<RedirectsSnackbar> = _snackbar.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // The server leaves the order to the client, which lists them
                // by source, as the web does.
                val list = repository.getRedirects(wikiId).sortedWith(compareBy(NaturalCompare) { redirect -> redirect.source })
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    redirects = list,
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun create(source: String, target: String) {
        val s = source.trim()
        val t = target.trim()
        if (s.isEmpty() || t.isEmpty()) {
            viewModelScope.launch {
                _snackbar.emit(RedirectsSnackbar(R.string.wikis_redirect_required_fields))
            }
            return
        }
        viewModelScope.launch {
            try {
                repository.setRedirect(wikiId, s, t)
                _snackbar.emit(RedirectsSnackbar(R.string.wikis_redirect_created_success))
                load()
            } catch (e: Exception) {
                _snackbar.emit(RedirectsSnackbar(R.string.wikis_redirect_create_failed, error = e.toMochiError()))
            }
        }
    }

    fun delete(source: String, onSuccess: () -> Unit) {
        if (_uiState.value.deleting != null) return
        _uiState.value = _uiState.value.copy(deleting = source)
        viewModelScope.launch {
            try {
                repository.deleteRedirect(wikiId, source)
                _uiState.value = _uiState.value.copy(deleting = null)
                onSuccess()
                _snackbar.emit(
                    RedirectsSnackbar(
                        R.string.wikis_redirect_deleted_success,
                        listOf(source),
                    )
                )
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(deleting = null)
                _snackbar.emit(RedirectsSnackbar(R.string.wikis_redirect_delete_failed, error = e.toMochiError()))
            }
        }
    }
}
