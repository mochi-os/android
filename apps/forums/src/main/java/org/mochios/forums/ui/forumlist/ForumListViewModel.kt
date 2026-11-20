// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forumlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.util.NaturalCompare
import org.mochios.forums.model.Forum
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

data class ForumListUiState(
    val forums: List<Forum> = emptyList(),
)

@HiltViewModel
class ForumListViewModel @Inject constructor(
    private val repository: ForumsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForumListUiState())
    val uiState: StateFlow<ForumListUiState> = _uiState.asStateFlow()

    init {
        load()
        // A write, a forum's frame or the forum screen coming back has dropped
        // the held roster: what it said may have changed, so it is read again.
        viewModelScope.launch {
            repository.forgotten.collect { load() }
        }
    }

    /** Read the roster afresh, as the screen comes back: a forum may have been
     *  renamed, joined or left elsewhere since. Dropping the held one is
     *  enough, as the drawer reads it again whenever it is dropped. */
    fun refresh() = repository.forget()

    /** The drawer's roster: `-/information` lists every forum without the
     *  posts `-/list` carries, and the forum screen opening beside it shares
     *  the one answer. A read that fails leaves the drawer as it was. */
    fun load() {
        viewModelScope.launch {
            try {
                val r = repository.forumsInformation()
                _uiState.value = _uiState.value.copy(
                    forums = r.forums.sortedWith(compareBy(NaturalCompare) { it.name }),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }
}
