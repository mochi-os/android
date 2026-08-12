// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.projectlist

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.NaturalCompare
import org.mochios.projects.model.Project
import org.mochios.projects.model.Template
import org.mochios.projects.repository.ProjectsRepository
import javax.inject.Inject

private fun JsonObject.jsonString(key: String): String? =
    get(key)
        ?.takeIf { element -> element.isJsonPrimitive }
        ?.asString
        ?.takeIf { value -> value.isNotBlank() }

/**
 * State of the create-project screen.
 *
 * @property templates templates offered by the server, empty until they load.
 * @property isCreating true while the create request is in flight.
 * @property error what went wrong on the last create attempt, if anything.
 * @property backupPrefill what the screen reads back out of a picked backup file.
 * @property createdProjectId set to the new project's id after a successful
 *   create so the screen can navigate into it; cleared once consumed.
 */
data class CreateProjectUiState(
    val templates: List<Template> = emptyList(),
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val backupPrefill: BackupPrefill? = null,
    val createdProjectId: String? = null
)

/**
 * A backup file the user picked, ready to seed the create screen.
 *
 * @property json the whole backup payload, restored after the project is made.
 * @property fileName shown on the picker button so the choice is visible.
 * @property name project name recorded in the backup, if any.
 * @property prefix project prefix recorded in the backup, if any.
 */
data class BackupPrefill(
    val json: String,
    val fileName: String,
    val name: String?,
    val prefix: String?
)

/** Drives the create-project screen: templates, backup import, and the create call. */
@HiltViewModel
class CreateProjectViewModel @Inject constructor(
    private val repository: ProjectsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateProjectUiState())
    val uiState: StateFlow<CreateProjectUiState> = _uiState.asStateFlow()

    init {
        loadTemplates()
    }

    private fun loadTemplates() {
        viewModelScope.launch {
            try {
                val templates = repository.getTemplates()
                    .sortedWith(compareBy(NaturalCompare) { template -> template.name })
                _uiState.value = _uiState.value.copy(templates = templates)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Reads the backup file the user picked and pulls the project's name and
     * prefix out of it, so the screen can seed itself.
     */
    fun readBackup(uri: Uri) {
        viewModelScope.launch {
            val content = repository.readTextOrZippedFile(uri)
            val root = content
                ?.let { text -> runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() }
            if (content == null || root == null) {
                return@launch
            }
            val fileName = repository.fileName(uri)
            val project = root.getAsJsonObject("project")
            _uiState.value = _uiState.value.copy(
                backupPrefill = BackupPrefill(
                    json = content,
                    fileName = fileName,
                    name = project?.jsonString("name"),
                    prefix = project?.jsonString("prefix")
                )
            )
        }
    }

    /** Clears the pending-navigation id once the screen has opened the project. */
    fun consumeCreatedProject() {
        _uiState.value = _uiState.value.copy(createdProjectId = null)
    }

    /** Creates the project, restoring [backupJson] into it when one was picked. */
    fun createProject(
        name: String,
        prefix: String,
        privacy: String,
        template: String?,
        backupJson: String?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            val importing = !backupJson.isNullOrBlank()
            try {
                val project = repository.createProject(
                    name = name,
                    description = null,
                    prefix = prefix.ifBlank { null },
                    privacy = privacy,
                    template = if (importing) TEMPLATE_BLANK else template
                )
                val newProjectId = project.fingerprint.ifEmpty { project.id }
                if (importing) {
                    restoreBackup(project, backupJson)
                }
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    // Open the new project straight after creation. Only when the
                    // backend returned an id — navigating to a blank id would land
                    // on the empty-project placeholder.
                    createdProjectId = newProjectId.takeIf { id -> id.isNotBlank() }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    private suspend fun restoreBackup(created: Project, backupJson: String) {
        val projectId = created.fingerprint.ifEmpty { created.id }
        try {
            val root = JsonParser.parseString(backupJson).asJsonObject
            val design = root.getAsJsonObject("design") ?: root
            repository.importDesign(
                projectId,
                data = design.toString(),
                template = "",
                templateVersion = 0
            )
            if (hasData(root)) {
                repository.importData(projectId, backupJson)
            }
        } catch (e: Exception) {
            // delete rejects a fingerprint, so roll back with the canonical id
            // — see ProjectSettingsViewModel.entityId().
            runCatching { repository.deleteProject(created.id.ifEmpty { projectId }) }
            throw e
        }
    }

    private fun hasData(root: JsonObject): Boolean {
        val objects = root.getAsJsonArray("objects")?.size() ?: 0
        val links = root.getAsJsonArray("links")?.size() ?: 0
        return objects > 0 || links > 0
    }

    private companion object {
        const val TEMPLATE_BLANK = "blank"
    }
}
