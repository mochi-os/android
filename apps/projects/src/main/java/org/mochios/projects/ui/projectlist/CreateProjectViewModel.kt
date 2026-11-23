// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.projectlist

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.files.Upload
import org.mochios.android.files.UploadTracker
import org.mochios.android.util.NaturalCompare
import org.mochios.android.util.slugify
import org.mochios.projects.model.Project
import org.mochios.projects.model.Template
import org.mochios.projects.repository.ProjectsRepository
import javax.inject.Inject

private fun JsonObject.jsonString(key: String): String? =
    get(key)
        ?.takeIf { element -> element.isJsonPrimitive }
        ?.asString
        ?.takeIf { value -> value.isNotBlank() }

data class CreateProjectUiState(
    val templates: List<Template> = emptyList(),
    // Every server has templates, so none means they could not be fetched:
    // the template step shows why, with a retry.
    val templatesLoading: Boolean = true,
    val templatesError: MochiError? = null,
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    // The picked file could not be read as a backup; the screen says so once.
    val backupReadFailed: Boolean = false,
    val createdProjectId: String? = null
)

// The two steps of the create flow.
internal const val STEP_DETAILS = 0
internal const val STEP_TEMPLATE = 1

// The server caps a prefix at 20 characters, so a long name like
// "Android project Testing Name" becomes "android-project-test".
private const val PREFIX_MAX = 20

internal fun prefixFromName(name: String): String = slugify(name, PREFIX_MAX)

/** Drives the create-project screen: templates, backup import, and the create call. */
@HiltViewModel
class CreateProjectViewModel @Inject constructor(
    private val repository: ProjectsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateProjectUiState())
    val uiState: StateFlow<CreateProjectUiState> = _uiState.asStateFlow()

    // The form, held here rather than in the screen, so a rotation or a
    // change of theme or font size, which recreate the screen, keep it.

    /** The step shown: [STEP_DETAILS] or [STEP_TEMPLATE]. */
    var step by mutableIntStateOf(STEP_DETAILS)
    var name by mutableStateOf("")
    var prefix by mutableStateOf("")

    /** Whether the prefix was typed, which stops it following the name. */
    var prefixEdited by mutableStateOf(false)

    /** Public: the directory lists the project; who may view it is [readable]'s. Off at first. */
    var allowSearch by mutableStateOf(false)

    /** Anyone may view the project; off, it is for its members alone. Off at first. */
    var readable by mutableStateOf(false)

    /** The template picked; none is preselected. */
    var selectedTemplate by mutableStateOf<String?>(null)

    /** The picked backup, restored after the project is created. */
    var backupJson by mutableStateOf<String?>(null)
    var backupName by mutableStateOf<String?>(null)

    /** The picked document when it is an archive: restored whole, so the attachments it carries survive. */
    var backupArchive by mutableStateOf<Uri?>(null)

    private val tracker = UploadTracker()

    /** The backup being uploaded into the new project, while it goes. */
    val upload: StateFlow<Upload?> = tracker.state

    init {
        loadTemplates()
    }

    fun loadTemplates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(templatesLoading = true, templatesError = null)
            try {
                val templates = repository.getTemplates()
                    .sortedWith(compareBy(NaturalCompare) { template -> template.name })
                _uiState.value = _uiState.value.copy(templates = templates, templatesLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(templatesLoading = false, templatesError = e.toMochiError())
            }
        }
    }

    /**
     * Reads the backup file the user picked and fills the form's name and
     * prefix from the project it holds.
     */
    fun readBackup(uri: Uri) {
        viewModelScope.launch {
            val content = repository.readTextOrZippedFile(uri)
            val root = content
                ?.let { text -> runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() }
            if (content == null || root == null) {
                _uiState.value = _uiState.value.copy(backupReadFailed = true)
                return@launch
            }
            val project = root.getAsJsonObject("project")
            backupJson = content
            backupName = repository.fileName(uri)
            backupArchive = uri.takeIf { repository.isArchive(uri) }
            project?.jsonString("name")?.let { value -> name = value }
            project?.jsonString("prefix")?.let { value ->
                prefix = prefixFromName(value)
                prefixEdited = true
            }
        }
    }

    /** The screen has shown the unreadable-backup notice. */
    fun consumeBackupReadFailed() {
        _uiState.value = _uiState.value.copy(backupReadFailed = false)
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
        readable: Boolean,
        template: String?,
        backupJson: String?,
        backupArchive: Uri? = null
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
                    readable = readable,
                    template = if (importing) TEMPLATE_BLANK else template
                )
                val newProjectId = project.fingerprint.ifEmpty { project.id }
                if (importing) {
                    restoreBackup(project, backupJson, backupArchive)
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

    private suspend fun restoreBackup(created: Project, backupJson: String, archive: Uri?) {
        val projectId = created.fingerprint.ifEmpty { created.id }
        try {
            if (archive != null) {
                // The archive carries design, objects and attachment bytes;
                // one upload restores all three.
                tracker.track { repository.importArchive(projectId, archive, tracker) }
                return
            }
            val root = JsonParser.parseString(backupJson).asJsonObject
            val design = root.getAsJsonObject("design") ?: root
            repository.importDesign(
                projectId,
                data = design.toString(),
                template = "",
                templateVersion = 0
            )
            if (hasData(root)) {
                tracker.track { repository.importData(projectId, backupJson, tracker) }
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
