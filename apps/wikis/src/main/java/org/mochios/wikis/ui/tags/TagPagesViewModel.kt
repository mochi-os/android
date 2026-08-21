// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.tags

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
import org.mochios.android.util.NaturalCompare
import org.mochios.wikis.model.TagPage
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

data class TagPagesUiState(
    val isLoading: Boolean = true,
    val pages: List<TagPage> = emptyList(),
    val error: MochiError? = null,
)

@HiltViewModel
class TagPagesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()
    val tag: String = savedStateHandle.get<String>("tag").orEmpty()

    private val _uiState = MutableStateFlow(TagPagesUiState())
    val uiState: StateFlow<TagPagesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = repository.getPagesForTag(wikiId, tag)
                val pages = response.pages.sortedWith(
                    compareBy(NaturalCompare) { it.title.ifBlank { it.page } }
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pages = pages,
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
}
