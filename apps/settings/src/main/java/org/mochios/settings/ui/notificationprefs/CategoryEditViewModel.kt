// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notificationprefs

import androidx.lifecycle.SavedStateHandle
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
import org.mochios.settings.api.DestinationRow
import org.mochios.settings.api.DestinationsAvailable
import org.mochios.settings.api.NotificationPrefsApi
import javax.inject.Inject

/** Form state for one notification category, new or existing. */
data class CategoryEditUiState(
    val isLoading: Boolean = true,
    val isLoaded: Boolean = false,
    val isSaving: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val isDefault: Boolean = false,
    val selected: Set<Pair<String, String>> = emptySet(),
    val available: DestinationsAvailable = DestinationsAvailable(),
    val error: MochiError? = null,
)

/** Backs the create and edit screens for a notification category. */
@HiltViewModel
class CategoryEditViewModel @Inject constructor(
    private val api: NotificationPrefsApi,
    private val gson: Gson,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val categoryId: String = savedStateHandle.get<String>("id").orEmpty()

    private val _uiState = MutableStateFlow(CategoryEditUiState(isNew = categoryId.isBlank()))
    val uiState: StateFlow<CategoryEditUiState> = _uiState.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    init { load() }

    /** Fetch the destinations on offer, plus the category being edited. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val dests = api.getDestinations().unwrapRaw()
                val category = if (categoryId.isBlank()) {
                    null
                } else {
                    api.getCategories().unwrapRaw().firstOrNull { cat -> cat.id == categoryId }
                }
                val selected = category?.destinations
                    ?.map { row -> row.type to row.target }
                    ?.toSet()
                    ?: everyDestination(dests)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoaded = true,
                    name = category?.label.orEmpty(),
                    isDefault = category?.default == 1,
                    selected = selected,
                    available = dests,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun setName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun setDefault(isDefault: Boolean) {
        _uiState.value = _uiState.value.copy(isDefault = isDefault)
    }

    fun toggleDestination(row: DestinationRow, checked: Boolean) {
        val key = row.type to row.target
        val selected = _uiState.value.selected
        _uiState.value = _uiState.value.copy(
            selected = if (checked) selected + key else selected - key,
        )
    }

    /** Write the form back, creating the category when this screen opened without an id. */
    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.name.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val rows = state.selected.map { (type, target) ->
                    DestinationRow(type = type, target = target)
                }
                val destinations = gson.toJson(rows)
                val default = if (state.isDefault) "1" else "0"
                if (categoryId.isBlank()) {
                    api.createCategory(
                        label = state.name.trim(),
                        destinations = destinations,
                        default = default,
                    ).unwrapEmpty()
                } else {
                    api.updateCategory(
                        id = categoryId,
                        label = state.name.trim(),
                        destinations = destinations,
                        default = default,
                    ).unwrapEmpty()
                }
                _uiState.value = _uiState.value.copy(isSaving = false)
                _saved.emit(Unit)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    /**
     * Every destination row this user has, keyed the way [destinationOptions]
     * keys them. A new category opens with all of them on.
     */
    private fun everyDestination(
        available: DestinationsAvailable,
    ): Set<Pair<String, String>> = buildSet {
        add("web" to "")
        for (device in available.devices) {
            add("device" to device.id)
        }
        for (account in available.accounts) {
            add("account" to account.id)
        }
        for (feed in available.feeds) {
            add("rss" to feed.id)
        }
    }

    /** Dismiss a surfaced error once the screen has shown it. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
