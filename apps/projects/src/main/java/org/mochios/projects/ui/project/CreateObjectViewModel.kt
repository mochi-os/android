// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.project

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
import org.mochios.projects.lib.ActiveViewStore
import org.mochios.projects.model.FieldOption
import org.mochios.projects.model.ProjectDetails
import org.mochios.projects.model.ProjectObject
import org.mochios.projects.model.ProjectView
import org.mochios.projects.repository.ProjectsRepository
import javax.inject.Inject

data class CreateObjectUiState(
    val details: ProjectDetails? = null,
    val objects: List<ProjectObject> = emptyList(),
    val activeView: ProjectView? = null,
    val isLoading: Boolean = true,
    val loadError: MochiError? = null,
    val isCreating: Boolean = false,
    val createError: MochiError? = null,
    val createdObjectId: String? = null,
)

/** Drives the create-object screen. */
@HiltViewModel
class CreateObjectViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectsRepository,
    private val activeViewStore: ActiveViewStore
) : ViewModel() {

    /** The project the new object belongs to. */
    val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    /**
     * Parent pre-selected by "Add child"; its class is the one the form starts
     * on. Null from the FAB.
     */
    val presetParent: String? = savedStateHandle.get<String>("parent")
        ?.takeIf { id -> id.isNotBlank() }

    /**
     * Field values the form opens with, e.g. the board column the user tapped
     * "+" on. They are applied to the created object as-is.
     */
    val presetValues: Map<String, String> = run {
        val field = savedStateHandle.get<String>("field").orEmpty()
        val value = savedStateHandle.get<String>("value").orEmpty()
        if (field.isBlank() || value.isBlank()) emptyMap() else mapOf(field to value)
    }

    private val _uiState = MutableStateFlow(CreateObjectUiState())
    val uiState: StateFlow<CreateObjectUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Fetches the project's design and objects. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)
            try {
                val details = repository.getProjectInfo(projectId)
                val objects = repository.getObjects(projectId)
                val remembered = activeViewStore.get(projectId)
                val activeView = details.views.firstOrNull { view -> view.id == remembered }
                    ?: details.views.firstOrNull()
                _uiState.value = _uiState.value.copy(
                    details = details,
                    objects = objects,
                    activeView = activeView,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadError = e.toMochiError(),
                )
            }
        }
    }

    /** Clears the pending-navigation id once the screen has opened the object. */
    fun consumeCreatedObject() {
        _uiState.value = _uiState.value.copy(createdObjectId = null)
    }

    /** Every option [fieldId] can take on [classId]. */
    fun optionsForField(classId: String, fieldId: String): List<FieldOption> {
        val details = _uiState.value.details ?: return emptyList()
        return details.options[classId]?.get(fieldId) ?: emptyList()
    }

    fun usableValue(classId: String, fieldId: String, value: String): Boolean {
        val details = _uiState.value.details ?: return false
        val field = details.fields[classId]?.firstOrNull { candidate -> candidate.id == fieldId }
            ?: return false
        if (field.fieldtype != "enumerated") return true
        return optionsForField(classId, fieldId).any { option -> option.id == value }
    }

    /** Creates an object and reports its id back through [CreateObjectUiState]. */
    fun createObject(
        classId: String,
        title: String,
        parent: String? = null,
        initialValues: Map<String, String> = emptyMap(),
    ) {
        if (classId.isBlank() || title.isBlank() || _uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, createError = null)
            try {
                val objectId = repository.createObject(projectId, classId, parent, title)
                if (initialValues.isNotEmpty()) {
                    repository.setValues(projectId, objectId, initialValues)
                }
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createdObjectId = objectId.takeIf { id -> id.isNotBlank() },
                )
            } catch (e: Exception) {
                // The form stays put so the entered values survive a retry; the
                // message below Create is what says why it did nothing.
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createError = e.toMochiError(),
                )
            }
        }
    }
}
