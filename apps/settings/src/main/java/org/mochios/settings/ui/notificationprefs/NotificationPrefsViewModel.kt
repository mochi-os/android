// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notificationprefs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrapEmpty
import org.mochios.android.api.unwrapRaw
import org.mochios.settings.api.DestinationsAvailable
import org.mochios.settings.api.NotifCategory
import org.mochios.settings.api.NotifTopic
import org.mochios.settings.api.NotificationPrefsApi
import org.mochios.settings.api.TestResult
import javax.inject.Inject

enum class NotifTab { CATEGORIES, TOPICS }

data class NotificationPrefsUiState(
    val isLoading: Boolean = true,
    val topicsLoaded: Boolean = false,
    val tab: NotifTab = NotifTab.CATEGORIES,
    val categories: List<NotifCategory> = emptyList(),
    val topics: List<NotifTopic> = emptyList(),
    val available: DestinationsAvailable = DestinationsAvailable(),
    val error: MochiError? = null,
)

@HiltViewModel
class NotificationPrefsViewModel @Inject constructor(
    private val api: NotificationPrefsApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationPrefsUiState())
    val uiState: StateFlow<NotificationPrefsUiState> = _uiState.asStateFlow()

    private var topicsJob: Job? = null

    // The destination count, not a finished sentence. The wording lives in the
    // Compose layer, where a <plurals> resource can inflect it for the reader's
    // language; a String built here can only ever be English.
    private val _testSent = MutableSharedFlow<TestResult>(extraBufferCapacity = 4)
    val testSent: SharedFlow<TestResult> = _testSent.asSharedFlow()

    init { refresh() }

    fun setTab(tab: NotifTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
        if (tab == NotifTab.TOPICS && !_uiState.value.topicsLoaded) {
            loadTopics()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val cats = api.getCategories().unwrapRaw()
                val dests = api.getDestinations().unwrapRaw()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    categories = cats,
                    available = dests,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    /** Fetch the topic list, which the topics tab asks for the first time it is opened. */
    fun loadTopics() {
        if (topicsJob?.isActive == true) return
        topicsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val topics = api.getTopics().unwrapRaw()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    topicsLoaded = true,
                    topics = topics,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun deleteCategory(id: String, reassignTo: String) = mutate {
        api.deleteCategory(id = id, reassign = reassignTo).unwrapEmpty()
    }

    fun setTopicCategory(topic: NotifTopic, categoryId: String?) = mutate {
        val value = categoryId ?: ""
        api.setTopicCategory(
            app = topic.app, topic = topic.topic, obj = topic.`object`, category = value,
        ).unwrapEmpty()
    }

    fun removeTopic(topic: NotifTopic) = mutate {
        api.deleteTopic(app = topic.app, topic = topic.topic, obj = topic.`object`).unwrapEmpty()
    }

    fun testCategory(category: NotifCategory) {
        viewModelScope.launch {
            try {
                val result = api.testCategory(id = category.id).unwrapRaw()
                _testSent.emit(result)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                refresh()
                if (_uiState.value.topicsLoaded) {
                    loadTopics()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /** Dismiss a surfaced error once the screen has shown it. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
