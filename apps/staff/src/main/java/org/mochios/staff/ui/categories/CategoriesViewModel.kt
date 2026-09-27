// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.staff.model.Category
import org.mochios.staff.repository.StaffRepository
import javax.inject.Inject

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    val error: MochiError? = null,
    val deleteTarget: Category? = null,
    val submitting: Boolean = false,
)

sealed interface CategoriesEvent {
    data class Toast(val messageRes: Int) : CategoriesEvent
    data class Error(val error: MochiError) : CategoriesEvent
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repo: StaffRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesUiState())
    val state: StateFlow<CategoriesUiState> = _state.asStateFlow()

    private val _events = Channel<CategoriesEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val list = repo.listCategories()
                _state.value = _state.value.copy(categories = list, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    /** Reloads after the form screen saved a category, and says what it did. */
    fun onSaved(messageRes: Int) {
        viewModelScope.launch {
            _events.send(CategoriesEvent.Toast(messageRes))
        }
        load()
    }

    fun askDelete(category: Category) {
        _state.value = _state.value.copy(deleteTarget = category)
    }

    fun cancelDelete() {
        _state.value = _state.value.copy(deleteTarget = null)
    }

    fun confirmDelete() {
        val target = _state.value.deleteTarget ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true)
            try {
                repo.deleteCategory(target.id)
                _state.value = _state.value.copy(deleteTarget = null, submitting = false)
                _events.send(
                    CategoriesEvent.Toast(org.mochios.staff.R.string.staff_categories_toast_deleted),
                )
                load()
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false)
                _events.send(CategoriesEvent.Error(e.toMochiError()))
            }
        }
    }
}
