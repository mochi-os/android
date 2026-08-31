// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notificationprefs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
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
import org.mochios.android.api.unwrapEmpty
import org.mochios.android.api.unwrapRaw
import org.mochios.settings.api.TestResult
import org.mochios.settings.api.DestinationRow
import org.mochios.settings.api.DestinationsAvailable
import org.mochios.settings.api.NotifCategory
import org.mochios.settings.api.NotifTopic
import org.mochios.settings.api.NotificationPrefsApi
import javax.inject.Inject

enum class NotifTab { CATEGORIES, TOPICS }

data class NotificationPrefsUiState(
    val isLoading: Boolean = true,
    val tab: NotifTab = NotifTab.CATEGORIES,
    val categories: List<NotifCategory> = emptyList(),
    val topics: List<NotifTopic> = emptyList(),
    val available: DestinationsAvailable = DestinationsAvailable(),
    val error: MochiError? = null,
)

@HiltViewModel
class NotificationPrefsViewModel @Inject constructor(
    private val api: NotificationPrefsApi,
    private val gson: Gson,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationPrefsUiState())
    val uiState: StateFlow<NotificationPrefsUiState> = _uiState.asStateFlow()

    // The destination count, not a finished sentence. The wording lives in the
    // Compose layer, where a <plurals> resource can inflect it for the reader's
    // language; a String built here can only ever be English.
    private val _testSent = MutableSharedFlow<TestResult>(extraBufferCapacity = 4)
    val testSent: SharedFlow<TestResult> = _testSent.asSharedFlow()

    init { refresh() }

    fun setTab(tab: NotifTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val cats = api.getCategories().unwrapRaw()
                val topics = api.getTopics().unwrapRaw()
                val dests = api.getDestinations().unwrapRaw()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    categories = cats,
                    topics = topics,
                    available = dests,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun createCategory(label: String) = mutate {
        api.createCategory(label = label.trim(), destinations = null, default = null).unwrapEmpty()
    }

    fun renameCategory(category: NotifCategory, label: String) = mutate {
        api.updateCategory(id = category.id, label = label.trim()).unwrapEmpty()
    }

    fun deleteCategory(id: String, reassignTo: String) = mutate {
        api.deleteCategory(id = id, reassign = reassignTo).unwrapEmpty()
    }

    fun toggleDestination(category: NotifCategory, row: DestinationRow, checked: Boolean) = mutate {
        val current = category.destinations.toMutableList()
        val matches: (DestinationRow) -> Boolean = { it.type == row.type && it.target == row.target }
        if (checked) {
            if (current.none(matches)) current.add(row)
        } else {
            current.removeAll(matches)
        }
        api.updateCategory(
            id = category.id,
            destinations = gson.toJson(current),
        ).unwrapEmpty()
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
