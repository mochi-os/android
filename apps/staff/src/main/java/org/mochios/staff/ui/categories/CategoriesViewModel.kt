// Copyright © 2026 Mochi OÜ
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

/**
 * Form model for [CategoryEditDialog]. Mirrors the web's `CategoryForm`
 * shape (`apps/staff/web/src/features/categories/categories-page.tsx`): all
 * fields are plain user input held as primitives so the dialog can read /
 * write each one independently. `position` is held as a string so the user
 * can clear the field — empty parses back to `null` (server default 0).
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

/** Mode for [CategoryEditDialog]. Edit carries the existing row's id so the
 *  ViewModel knows which endpoint to hit on submit. */
sealed interface CategoryDialogMode {
    data object Create : CategoryDialogMode
    data class Edit(val category: Category) : CategoryDialogMode
}

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    val error: MochiError? = null,
    val dialogMode: CategoryDialogMode? = null,
    val form: CategoryForm = CategoryForm(),
    val deleteTarget: Category? = null,
    val submitting: Boolean = false,
)

sealed interface CategoriesEvent {
    data class Toast(val messageRes: Int) : CategoriesEvent
    data class Error(val error: MochiError) : CategoriesEvent
}

/**
 * ViewModel for the staff Categories screen. Holds the categories list, the
 * create/edit dialog state, and the delete-confirmation state. All
 * mutations route through [StaffRepository] which speaks the
 * `categories/create | update | delete` actions exposed by the Comptroller.
 */
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

    fun openCreate() {
        _state.value = _state.value.copy(
            dialogMode = CategoryDialogMode.Create,
            form = CategoryForm(),
        )
    }

    fun openEdit(category: Category) {
        _state.value = _state.value.copy(
            dialogMode = CategoryDialogMode.Edit(category),
            form = CategoryForm(
                name = category.name,
                slug = category.slug,
                parent = category.parent ?: "",
                icon = category.icon,
                digital = category.digital,
                physical = category.physical,
                position = category.position.toString(),
                active = category.active,
            ),
        )
    }

    fun closeDialog() {
        _state.value = _state.value.copy(dialogMode = null)
    }

    fun setForm(form: CategoryForm) {
        _state.value = _state.value.copy(form = form)
    }

    fun submit() {
        val mode = _state.value.dialogMode ?: return
        val form = _state.value.form
        if (form.name.isBlank() || form.slug.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true)
            try {
                when (mode) {
                    is CategoryDialogMode.Create -> {
                        repo.createCategory(
                            name = form.name.trim(),
                            slug = form.slug.trim(),
                            parent = form.parent.takeIf { it.isNotBlank() },
                            icon = form.icon.takeIf { it.isNotBlank() },
                            position = form.position.toIntOrNull(),
                            digital = form.digital,
                            physical = form.physical,
                        )
                        _events.send(
                            CategoriesEvent.Toast(org.mochios.staff.R.string.staff_categories_toast_created),
                        )
                    }
                    is CategoryDialogMode.Edit -> {
                        repo.updateCategory(
                            id = mode.category.id,
                            name = form.name.trim(),
                            slug = form.slug.trim(),
                            parent = form.parent,
                            icon = form.icon.takeIf { it.isNotBlank() },
                            position = form.position.toIntOrNull(),
                            digital = form.digital,
                            physical = form.physical,
                            active = form.active,
                        )
                        _events.send(
                            CategoriesEvent.Toast(org.mochios.staff.R.string.staff_categories_toast_updated),
                        )
                    }
                }
                _state.value = _state.value.copy(dialogMode = null, submitting = false)
                load()
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false)
                _events.send(CategoriesEvent.Error(e.toMochiError()))
            }
        }
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
