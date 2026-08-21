// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crmlist

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
import org.mochios.crm.model.Crm
import org.mochios.crm.repository.CrmsRepository
import javax.inject.Inject

private fun JsonObject.jsonString(key: String): String? =
    get(key)
        ?.takeIf { element -> element.isJsonPrimitive }
        ?.asString
        ?.takeIf { value -> value.isNotBlank() }

data class CreateCrmUiState(
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val backupPrefill: BackupPrefill? = null,
    val createdCrmId: String? = null
)

/**
 * A picked backup file, ready to seed the create screen; [json] is restored
 * after the CRM is made.
 */
data class BackupPrefill(
    val json: String,
    val fileName: String,
    val name: String?
)

/** Drives the create-CRM screen: backup import and the create call. */
@HiltViewModel
class CreateCrmViewModel @Inject constructor(
    private val repository: CrmsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateCrmUiState())
    val uiState: StateFlow<CreateCrmUiState> = _uiState.asStateFlow()

    /**
     * Reads the backup file the user picked and pulls the CRM's name out of it,
     * so the screen can seed itself.
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
            // Exports name the top-level object after the app that wrote them;
            // older project-shaped backups still say "project".
            val crm = root.getAsJsonObject("crm") ?: root.getAsJsonObject("project")
            _uiState.value = _uiState.value.copy(
                backupPrefill = BackupPrefill(
                    json = content,
                    fileName = fileName,
                    name = crm?.jsonString("name")
                )
            )
        }
    }

    /** Clears the pending-navigation id once the screen has opened the CRM. */
    fun consumeCreatedCrm() {
        _uiState.value = _uiState.value.copy(createdCrmId = null)
    }

    /**
     * Creates a CRM, then restores [backupJson] into it when one was picked.
     */
    fun createCrm(name: String, privacy: String, backupJson: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            val importing = !backupJson.isNullOrBlank()
            try {
                val created = repository.createCrm(
                    name = name,
                    description = null,
                    privacy = privacy
                )
                val newCrmId = created.fingerprint.ifEmpty { created.id }
                if (importing) {
                    restoreBackup(created, backupJson)
                }
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    // Open the new CRM straight after creation. Only when the
                    // backend returned an id — navigating to a blank id would
                    // land on the empty-CRM placeholder.
                    createdCrmId = newCrmId.takeIf { id -> id.isNotBlank() }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    /**
     * Restores a backup into the new CRM: design, then objects. On failure the
     * CRM is deleted and the error rethrown.
     */
    private suspend fun restoreBackup(created: Crm, backupJson: String) {
        val crmId = created.fingerprint.ifEmpty { created.id }
        try {
            val root = JsonParser.parseString(backupJson).asJsonObject
            val design = root.getAsJsonObject("design") ?: root
            repository.importDesign(
                crmId,
                data = design.toString(),
                template = "",
                templateVersion = 0
            )
            if (hasData(root)) {
                repository.importData(crmId, backupJson)
            }
        } catch (e: Exception) {
            // delete rejects a fingerprint, so roll back with the canonical id
            // — see CrmSettingsViewModel.entityId().
            runCatching { repository.deleteCrm(created.id.ifEmpty { crmId }) }
            throw e
        }
    }

    private fun hasData(root: JsonObject): Boolean {
        val objects = root.getAsJsonArray("objects")?.size() ?: 0
        val links = root.getAsJsonArray("links")?.size() ?: 0
        return objects > 0 || links > 0
    }
}
