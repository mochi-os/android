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
import org.mochios.android.files.SavedExport
import org.mochios.projects.model.ProjectDetails
import org.mochios.projects.model.Template
import org.mochios.projects.repository.ProjectsRepository
import org.mochios.projects.util.hierarchyParameter
import javax.inject.Inject

data class DesignUiState(
    val projectDetails: ProjectDetails? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: MochiError? = null,
    val isSaving: Boolean = false,
    // Design JSON fetched and waiting for the user to pick a destination.
    val pendingExport: PendingExport? = null,
    val savedExport: SavedExport? = null,
    val exportFailed: Boolean = false,
    val templates: List<Template> = emptyList(),
    val isLoadingTemplates: Boolean = false,
    // Design JSON read from a picked file, waiting on the replace confirmation.
    val pendingImport: PendingImport? = null,
    val importSuccess: Boolean = false,
    val importFailed: Boolean = false
)

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
                        suggestedName = repository.exportFileName(name, "design"),
                        content = json.toString(),
                    )
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /**
     * Writes the pending export to the destination the user picked. A design
     * is built here rather than downloaded, so it always carries its content.
     */
    fun writeExportTo(uri: Uri) {
        val pending = _uiState.value.pendingExport ?: return
        val content = pending.content ?: return
        viewModelScope.launch {
            val ok = repository.saveTextFile(uri, content)
            _uiState.value = _uiState.value.copy(
                pendingExport = null,
                savedExport = if (ok) {
                    SavedExport(uri, pending.mimeType, pending.suggestedName)
                } else {
                    null
                },
                exportFailed = !ok
            )
        }
    }

    /** Drops the pending export when the user backs out of the save dialog. */
    fun cancelExport() {
        _uiState.value = _uiState.value.copy(pendingExport = null)
    }

    fun clearExportResult() {
        _uiState.value = _uiState.value.copy(savedExport = null, exportFailed = false)
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
