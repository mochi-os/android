// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.moderation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import org.mochios.forums.R
import org.mochios.forums.api.ModerationQueueResponse
import org.mochios.forums.api.ModerationReportsResponse
import org.mochios.forums.model.ForumMember
import org.mochios.forums.model.ModerationLogEntry
import org.mochios.forums.model.ModerationSettings
import org.mochios.forums.model.Restriction
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

/** The moderation tabs, in the order the web client presents them. */
enum class ModerationTab {
    QUEUE,
    REPORTS,
    RESTRICTIONS,
    LOG,
}

data class ModerationUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val queue: ModerationQueueResponse? = null,
    val reports: ModerationReportsResponse? = null,
    val reportsStatus: String = "pending",
    val log: List<ModerationLogEntry> = emptyList(),
    val restrictions: List<Restriction> = emptyList(),
    val settings: ModerationSettings? = null,
    /** Snackbar/toast message resource for a finished action. */
    val actionMessage: Int? = null,
    /** A tab or the settings failed to load; a refused action is a [ModerationViewModel.failures] event. */
    val error: MochiError? = null,
    val selectedTab: ModerationTab = ModerationTab.QUEUE,
)

@HiltViewModel
class ModerationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ForumsRepository,
) : ViewModel() {

    val forumId: String = savedStateHandle["forumId"] ?: ""

    private val _uiState = MutableStateFlow(ModerationUiState())
    val uiState: StateFlow<ModerationUiState> = _uiState.asStateFlow()

    /**
     * An action the server refused, said over the tab, which stays as it was:
     * as [ModerationUiState.error] it put the failed-load page in the tab's
     * place, whose Retry reloads the tab and never repeats the action.
     */
    private val _failures = MutableSharedFlow<MochiError>(extraBufferCapacity = 1)
    val failures: SharedFlow<MochiError> = _failures.asSharedFlow()

    init {
        loadTab(ModerationTab.QUEUE)
    }

    fun selectTab(tab: ModerationTab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        loadTab(tab)
    }

    fun refresh() {
        loadTab(_uiState.value.selectedTab, refreshing = true)
    }

    /**
     * Retry for the error state. Not refresh(): that reports through the
     * pull-to-refresh indicator, which has no content to sit over after a
     * failed load.
     */
    fun retry() {
        loadTab(_uiState.value.selectedTab)
    }

    private fun loadTab(tab: ModerationTab, refreshing: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = !refreshing,
                isRefreshing = refreshing,
                error = null,
            )
            try {
                // Await each fetch before reading the state to copy from: as a
                // copy() argument the receiver `_uiState.value` is captured
                // before the suspend, and a late response would write back a
                // stale snapshot.
                when (tab) {
                    ModerationTab.QUEUE -> {
                        val queue = repository.moderationQueue(forumId)
                        _uiState.value = _uiState.value.copy(queue = queue)
                    }
                    ModerationTab.REPORTS -> {
                        val reports = repository.moderationReports(
                            forumId, _uiState.value.reportsStatus
                        )
                        _uiState.value = _uiState.value.copy(reports = reports)
                    }
                    ModerationTab.LOG -> {
                        val log = repository.moderationLog(forumId).entries
                        _uiState.value = _uiState.value.copy(log = log)
                    }
                    ModerationTab.RESTRICTIONS -> {
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
        // Refresh rather than load: `isLoading` would swap the whole tab for a
        // spinner, so the filter chips would blink out and back on every switch.
        if (_uiState.value.selectedTab == ModerationTab.REPORTS) {
            loadTab(ModerationTab.REPORTS, refreshing = true)
        }
    }

    fun resolveReport(reportId: String, resolution: String) = mutate {
        repository.resolveReport(forumId, reportId, resolution)
        loadTab(ModerationTab.REPORTS, refreshing = true)
    }

    fun approvePost(postId: String) = mutate {
        repository.approvePost(forumId, postId)
        loadTab(ModerationTab.QUEUE, refreshing = true)
    }

    fun removePost(postId: String, reason: String) = mutate {
        repository.removePost(forumId, postId, reason)
        loadTab(ModerationTab.QUEUE, refreshing = true)
    }

    fun approveComment(postId: String, commentId: String) = mutate {
        repository.approveComment(forumId, postId, commentId)
        loadTab(ModerationTab.QUEUE, refreshing = true)
    }

    fun removeComment(postId: String, commentId: String, reason: String) = mutate {
        repository.removeComment(forumId, postId, commentId, reason)
        loadTab(ModerationTab.QUEUE, refreshing = true)
    }

    /**
     * [onDone] says whether the server took the restriction, so the add dialog
     * closes on it and keeps what was chosen when it is refused.
     */
    fun addRestriction(
        user: String,
        type: String,
        reason: String,
        durationSeconds: Long? = null,
        onDone: (Boolean) -> Unit = {},
    ) = mutate(onDone) {
        repository.restrict(forumId, user, type, reason, durationSeconds)
        loadTab(ModerationTab.RESTRICTIONS, refreshing = true)
    }

    /**
     * The forum's members whose names hold [query], for the add dialog to
     * choose from; the first the forum lists when [query] is empty. None when
     * the search fails: the field then simply offers nobody.
     */
    suspend fun searchMembers(query: String): List<ForumMember> = try {
        repository.searchMembers(forumId, query.trim()).members
            .filter { member -> member.id.isNotEmpty() }
            .sortedWith(compareBy(NaturalCompare) { member -> member.name })
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    fun removeRestriction(user: String) = mutate {
        repository.unrestrict(forumId, user)
        loadTab(ModerationTab.RESTRICTIONS, refreshing = true)
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
                _uiState.value = _uiState.value.copy(
                    settings = settings,
                    actionMessage = R.string.forums_moderation_settings_updated,
                )
            } catch (e: Exception) {
                // Re-read so the controls fall back to what the forum holds
                // rather than standing on a value the server refused.
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
                try {
                    // Awaited first, as loadTab does: the error shown above may
                    // be cleared while this read is on its way.
                    val held = repository.moderationSettings(forumId)
                    _uiState.value = _uiState.value.copy(settings = held)
                } catch (_: Exception) {
                }
            }
        }
    }

    /** The error snackbar has been shown; don't repeat it on recomposition. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearActionMessage() {
        _uiState.value = _uiState.value.copy(actionMessage = null)
    }

    private fun mutate(onDone: (Boolean) -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                onDone(true)
            } catch (e: Exception) {
                onDone(false)
                _failures.emit(e.toMochiError())
            }
        }
    }
}
