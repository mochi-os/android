// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.moderation

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
import org.mochios.forums.api.ModerationQueueResponse
import org.mochios.forums.api.ModerationReportsResponse
import org.mochios.forums.model.ModerationLogEntry
import org.mochios.forums.model.ModerationSettings
import org.mochios.forums.model.Restriction
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

data class ModerationUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val queue: ModerationQueueResponse? = null,
    val reports: ModerationReportsResponse? = null,
    val reportsStatus: String = "pending",
    val log: List<ModerationLogEntry> = emptyList(),
    val restrictions: List<Restriction> = emptyList(),
    val settings: ModerationSettings? = null,
    val error: MochiError? = null,
    val selectedTab: Int = 0,
)

@HiltViewModel
class ModerationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ForumsRepository,
) : ViewModel() {

    val forumId: String = savedStateHandle["forumId"] ?: ""

    private val _uiState = MutableStateFlow(ModerationUiState())
    val uiState: StateFlow<ModerationUiState> = _uiState.asStateFlow()

    init {
        loadTab(0)
    }

    fun selectTab(index: Int) {
        if (_uiState.value.selectedTab == index) return
        _uiState.value = _uiState.value.copy(selectedTab = index)
        loadTab(index)
    }

    fun refresh() {
        loadTab(_uiState.value.selectedTab, refreshing = true)
    }

    private fun loadTab(index: Int, refreshing: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = !refreshing,
                isRefreshing = refreshing,
                error = null,
            )
            try {
                // Each fetch is awaited before the state it merges into is read.
                // Inline as a copy() argument the receiver `_uiState.value` is
                // captured before the suspend, so a tab whose response lands late
                // writes back the snapshot it started from — discarding the newer
                // tab's data and the `finally` block's isLoading = false.
                when (index) {
                    0 -> {
                        val queue = repository.moderationQueue(forumId)
                        _uiState.value = _uiState.value.copy(queue = queue)
                    }
                    1 -> {
                        val reports = repository.moderationReports(
                            forumId, _uiState.value.reportsStatus
                        )
                        _uiState.value = _uiState.value.copy(reports = reports)
                    }
                    2 -> {
                        val log = repository.moderationLog(forumId).entries
                        _uiState.value = _uiState.value.copy(log = log)
                    }
                    3 -> {
                        val restrictions = repository.restrictions(forumId).restrictions
                            .sortedWith(compareBy(NaturalCompare) { it.name })
                        _uiState.value = _uiState.value.copy(restrictions = restrictions)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
            }
        }
    }

    fun setReportsStatus(status: String) {
        _uiState.value = _uiState.value.copy(reportsStatus = status)
        if (_uiState.value.selectedTab == 1) loadTab(1)
    }

    fun resolveReport(reportId: String, resolution: String) {
        viewModelScope.launch {
            try {
                repository.resolveReport(forumId, reportId, resolution)
                loadTab(1, refreshing = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun approvePost(postId: String) = mutate {
        repository.approvePost(forumId, postId)
        loadTab(0, refreshing = true)
    }

    fun removePost(postId: String) = mutate {
        repository.removePost(forumId, postId)
        loadTab(0, refreshing = true)
    }

    fun approveComment(postId: String, commentId: String) = mutate {
        repository.approveComment(forumId, postId, commentId)
        loadTab(0, refreshing = true)
    }

    fun removeComment(postId: String, commentId: String) = mutate {
        repository.removeComment(forumId, postId, commentId)
        loadTab(0, refreshing = true)
    }

    fun addRestriction(user: String, type: String, reason: String, durationSeconds: Long? = null) = mutate {
        repository.restrict(forumId, user, type, reason, durationSeconds)
        loadTab(3, refreshing = true)
    }

    fun removeRestriction(user: String) = mutate {
        repository.unrestrict(forumId, user)
        loadTab(3, refreshing = true)
    }

    fun loadSettings() {
        viewModelScope.launch {
            try {
                val settings = repository.moderationSettings(forumId)
                _uiState.value = _uiState.value.copy(settings = settings)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun saveSettings(settings: ModerationSettings) {
        viewModelScope.launch {
            try {
                repository.saveModerationSettings(forumId, settings)
                _uiState.value = _uiState.value.copy(settings = settings)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }
}
