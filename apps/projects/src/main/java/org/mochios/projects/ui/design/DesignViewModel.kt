// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.design

import android.net.Uri
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
import org.mochios.android.files.PendingExport
import org.mochios.projects.model.ProjectDetails
import org.mochios.projects.model.Template
import org.mochios.projects.repository.ProjectsRepository
import javax.inject.Inject

data class DesignUiState(
    val projectDetails: ProjectDetails? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: MochiError? = null,
    val selectedClassId: String? = null,
    val selectedFieldId: String? = null,
    val isSaving: Boolean = false,
    // Design JSON fetched and waiting for the user to pick a destination.
    val pendingExport: PendingExport? = null,
    val exportSaved: Boolean = false,
    val exportFailed: Boolean = false,
    val templates: List<Template> = emptyList(),
    val isLoadingTemplates: Boolean = false,
    // Design JSON read from a picked file, waiting on the replace confirmation.
    val pendingImport: PendingImport? = null,
    val importSuccess: Boolean = false,
    val importFailed: Boolean = false
)

/**
 * A design read off disk that may replace the current one, once confirmed.
 *
 * @property json the design payload read from the file.
 * @property label the file's name, shown in the confirmation.
 */
data class PendingImport(val json: String, val label: String)

@HiltViewModel
class DesignViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectsRepository
) : ViewModel() {

    val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    private val _uiState = MutableStateFlow(DesignUiState())
    val uiState: StateFlow<DesignUiState> = _uiState.asStateFlow()

    init {
        loadProject()
    }

    fun loadProject() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val details = repository.getProjectInfo(projectId)
                _uiState.value = _uiState.value.copy(
                    projectDetails = details,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                val details = repository.getProjectInfo(projectId)
                _uiState.value = _uiState.value.copy(
                    projectDetails = details,
                    isRefreshing = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun selectClass(classId: String?) {
        _uiState.value = _uiState.value.copy(selectedClassId = classId, selectedFieldId = null)
    }

    fun selectField(fieldId: String?) {
        _uiState.value = _uiState.value.copy(selectedFieldId = fieldId)
    }

    // ---- Classes ----

    fun createClass(name: String) {
        viewModelScope.launch {
            try {
                repository.createClass(projectId, name)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun updateClass(classId: String, name: String? = null, title: String? = null, requests: String? = null) {
        viewModelScope.launch {
            try {
                repository.updateClass(projectId, classId, name, title, requests)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteClass(classId: String) {
        viewModelScope.launch {
            try {
                repository.deleteClass(projectId, classId)
                if (_uiState.value.selectedClassId == classId) {
                    _uiState.value = _uiState.value.copy(selectedClassId = null)
                }
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---- Hierarchy ----

    fun setHierarchy(classId: String, parents: List<String>) {
        viewModelScope.launch {
            try {
                repository.setHierarchy(projectId, classId, parents.joinToString(","))
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---- Fields ----

    fun createField(classId: String, name: String, fieldtype: String, flags: String?, multi: Boolean?) {
        viewModelScope.launch {
            try {
                repository.createField(projectId, classId, name, fieldtype, flags, multi)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun updateField(
        classId: String,
        fieldId: String,
        name: String?,
        fieldtype: String?,
        flags: String?,
        multi: Boolean?,
        card: Boolean?,
        position: String?,
        rows: Int?
    ) {
        viewModelScope.launch {
            try {
                repository.updateField(projectId, classId, fieldId, name, fieldtype, flags, multi, card, position, rows)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteField(classId: String, fieldId: String) {
        viewModelScope.launch {
            try {
                repository.deleteField(projectId, classId, fieldId)
                if (_uiState.value.selectedFieldId == fieldId) {
                    _uiState.value = _uiState.value.copy(selectedFieldId = null)
                }
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun reorderFields(classId: String, order: String) {
        viewModelScope.launch {
            try {
                repository.reorderFields(projectId, classId, order)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---- Options ----

    fun createOption(classId: String, fieldId: String, name: String, colour: String?, icon: String? = null) {
        viewModelScope.launch {
            try {
                repository.createOption(projectId, classId, fieldId, name, colour, icon)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun updateOption(classId: String, fieldId: String, optionId: String, name: String?, colour: String?, icon: String?) {
        viewModelScope.launch {
            try {
                repository.updateOption(projectId, classId, fieldId, optionId, name, colour, icon)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteOption(classId: String, fieldId: String, optionId: String) {
        viewModelScope.launch {
            try {
                repository.deleteOption(projectId, classId, fieldId, optionId)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun reorderOptions(classId: String, fieldId: String, order: String) {
        viewModelScope.launch {
            try {
                repository.reorderOptions(projectId, classId, fieldId, order)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---- Views ----

    fun createView(
        name: String,
        viewtype: String,
        columns: String?,
        rows: String?,
        filter: String?,
        sort: String?,
        direction: String?,
        classes: String?,
        border: String?
    ) {
        viewModelScope.launch {
            try {
                repository.createView(projectId, name, viewtype, columns, rows, filter, sort, direction, classes, border)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun updateView(
        viewId: String,
        name: String?,
        viewtype: String?,
        columns: String?,
        rows: String?,
        filter: String?,
        sort: String?,
        direction: String?,
        classes: String?,
        border: String?
    ) {
        viewModelScope.launch {
            try {
                repository.updateView(projectId, viewId, name, viewtype, columns, rows, filter, sort, direction, classes, border)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteView(viewId: String) {
        viewModelScope.launch {
            try {
                repository.deleteView(projectId, viewId)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun reorderViews(order: String) {
        viewModelScope.launch {
            try {
                repository.reorderViews(projectId, order)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---- Design Export / Import ----

    /**
     * Fetches the design JSON and parks it in [DesignUiState.pendingExport] so
     * the screen can open the save dialog for it.
     */
    fun exportDesign() {
        viewModelScope.launch {
            try {
                val json = repository.exportDesign(projectId)
                val name = _uiState.value.projectDetails?.project?.name
                _uiState.value = _uiState.value.copy(
                    pendingExport = PendingExport(
                        json = json.toString(),
                        suggestedName = repository.exportFileName(name, "design")
                    )
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /** Writes the pending export to the destination the user picked. */
    fun writeExportTo(uri: Uri) {
        val pending = _uiState.value.pendingExport ?: return
        viewModelScope.launch {
            val ok = repository.saveTextFile(uri, pending.json)
            _uiState.value = _uiState.value.copy(
                pendingExport = null,
                exportSaved = ok,
                exportFailed = !ok
            )
        }
    }

    /** Drops the pending export when the user backs out of the save dialog. */
    fun cancelExport() {
        _uiState.value = _uiState.value.copy(pendingExport = null)
    }

    fun clearExportResult() {
        _uiState.value = _uiState.value.copy(exportSaved = false, exportFailed = false)
    }

    fun loadTemplates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingTemplates = true)
            try {
                val templates = repository.getTemplates()
                _uiState.value = _uiState.value.copy(
                    templates = templates,
                    isLoadingTemplates = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingTemplates = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun importFromTemplate(templateId: String, templateVersion: Int) {
        viewModelScope.launch {
            try {
                repository.importDesign(projectId, template = templateId, templateVersion = templateVersion)
                _uiState.value = _uiState.value.copy(importSuccess = true)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /**
     * Reads a picked design file and holds it in
     * [DesignUiState.pendingImport] until the user confirms the replacement.
     */
    fun readImportFile(uri: Uri) {
        viewModelScope.launch {
            val json = repository.readTextFile(uri)
            if (json.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(importFailed = true)
                return@launch
            }
            val label = repository.fileName(uri)
            _uiState.value = _uiState.value.copy(
                pendingImport = PendingImport(json = json, label = label)
            )
        }
    }

    /** Applies the design the user confirmed, replacing the current one. */
    fun confirmPendingImport() {
        val pending = _uiState.value.pendingImport ?: return
        _uiState.value = _uiState.value.copy(pendingImport = null)
        importFromJson(pending.json)
    }

    /** Drops the pending import when the user backs out of the confirmation. */
    fun cancelPendingImport() {
        _uiState.value = _uiState.value.copy(pendingImport = null)
    }

    fun clearImportFailed() {
        _uiState.value = _uiState.value.copy(importFailed = false)
    }

    private fun importFromJson(jsonText: String) {
        viewModelScope.launch {
            try {
                repository.importDesign(projectId, data = jsonText)
                _uiState.value = _uiState.value.copy(importSuccess = true)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun clearImportSuccess() {
        _uiState.value = _uiState.value.copy(importSuccess = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
