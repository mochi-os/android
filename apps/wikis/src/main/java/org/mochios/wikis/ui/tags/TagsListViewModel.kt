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
import org.mochios.wikis.model.Tag
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

/**
 * UI state for [TagsListScreen]. Holds the loaded tags + loading / error
 * state. Tags are pre-sorted by [NaturalCompare] on the tag name so the
 * surface always renders in a stable, locale-aware order regardless of how
 * the server returned them.
 */
data class TagsListUiState(
    val isLoading: Boolean = true,
    val tags: List<Tag> = emptyList(),
    val error: MochiError? = null,
)

/**
 * ViewModel for [TagsListScreen]. Reads `wikiId` from [SavedStateHandle]
 * (set by `WikisApp.TAGS`) and exposes [uiState] for the screen to observe.
 *
 * Mirrors web's `tags-list.tsx` data flow: a single `/-/tags` fetch on init,
 * sorted client-side (the server sorts by count, but we always sort by name
 * on the Android client per the CLAUDE.md sorting rule).
 */
@HiltViewModel
class TagsListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()

    private val _uiState = MutableStateFlow(TagsListUiState())
    val uiState: StateFlow<TagsListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val tags = repository.getTags(wikiId)
                    .sortedWith(compareBy(NaturalCompare) { it.tag })
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    tags = tags,
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
