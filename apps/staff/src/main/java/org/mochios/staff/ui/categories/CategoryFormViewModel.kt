// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.categories

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
import org.mochios.staff.R
import org.mochios.staff.model.Category
import org.mochios.staff.repository.StaffRepository
import javax.inject.Inject

/**
 * The editable fields of a category. `position` is a string so the field can
 * be cleared; empty submits null (server default 0).
 */
data class CategoryForm(
    val name: String = "",
    val slug: String = "",
    val parent: String = "",
    val icon: String = "",
    val digital: Boolean = false,
    val physical: Boolean = false,
    val position: String = "0",
    val active: Boolean = true,
)

/**
 * State of the category form screen.
 *
 * @property categories Every category, offered as a parent.
 * @property editing The category being edited, or null when creating one.
 * @property missing Whether the category to edit no longer exists.
 * @property saved The message to show on the list once a save succeeded.
 */
data class CategoryFormUiState(
    val categories: List<Category> = emptyList(),
    val editing: Category? = null,
    val form: CategoryForm = CategoryForm(),
    val isLoading: Boolean = true,
    val loadError: MochiError? = null,
    val missing: Boolean = false,
    val submitting: Boolean = false,
    val saveError: MochiError? = null,
    val saved: Int? = null,
)

/** Drives the category form screen, which both creates and edits a category. */
@HiltViewModel
class CategoryFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: StaffRepository,
) : ViewModel() {

    private val categoryId: String? = savedStateHandle.get<String>("id")

    private val _state = MutableStateFlow(CategoryFormUiState())
    val state: StateFlow<CategoryFormUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Loads the parent options and, when editing, the category's current values. */
    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, loadError = null)
            try {
                val categories = repo.listCategories()
                val editing = categoryId?.let { id ->
                    categories.firstOrNull { category -> category.id == id }
                }
                _state.value = _state.value.copy(
                    categories = categories,
                    editing = editing,
                    form = editing?.toForm() ?: _state.value.form,
                    missing = categoryId != null && editing == null,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, loadError = e.toMochiError())
            }
        }
    }

    /** Replaces the form with the user's latest input. */
    fun setForm(form: CategoryForm) {
        _state.value = _state.value.copy(form = form)
    }

    /**
     * Creates or updates the category, then reports the message through
     * [CategoryFormUiState.saved].
     */
    fun submit() {
        val current = _state.value
        val form = current.form
        if (form.name.isBlank() || form.slug.isBlank() || current.submitting) {
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, saveError = null)
            try {
                val editing = current.editing
                if (editing == null) {
                    repo.createCategory(
                        name = form.name.trim(),
                        slug = form.slug.trim(),
                        parent = form.parent.takeIf { parent -> parent.isNotBlank() },
                        icon = form.icon.takeIf { icon -> icon.isNotBlank() },
                        position = form.position.toIntOrNull(),
                        digital = form.digital,
                        physical = form.physical,
                    )
                } else {
                    repo.updateCategory(
                        id = editing.id,
                        name = form.name.trim(),
                        slug = form.slug.trim(),
                        parent = form.parent,
                        icon = form.icon.takeIf { icon -> icon.isNotBlank() },
                        position = form.position.toIntOrNull(),
                        digital = form.digital,
                        physical = form.physical,
                        active = form.active,
                    )
                }
                val message = if (editing == null) {
                    R.string.staff_categories_toast_created
                } else {
                    R.string.staff_categories_toast_updated
                }
                _state.value = _state.value.copy(submitting = false, saved = message)
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false, saveError = e.toMochiError())
            }
        }
    }

    private fun Category.toForm() = CategoryForm(
        name = name,
        slug = slug,
        parent = parent ?: "",
        icon = icon,
        digital = digital,
        physical = physical,
        position = position.toString(),
        active = active,
    )
}
