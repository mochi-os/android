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
import org.mochios.android.util.NaturalCompare
import org.mochios.crm.model.Crm
import org.mochios.crm.model.Template
import org.mochios.crm.repository.CrmsRepository
import javax.inject.Inject

private fun JsonObject.jsonString(key: String): String? =
    get(key)
        ?.takeIf { element -> element.isJsonPrimitive }
        ?.asString
        ?.takeIf { value -> value.isNotBlank() }

data class CrmListUiState(
    val crm: List<Crm> = emptyList(),
    val templates: List<Template> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val searchQuery: String = "",
    val showCreateDialog: Boolean = false,
    val showSearch: Boolean = false,
    // What the create dialog reads back out of a picked backup file.
    val backupPrefill: BackupPrefill? = null,
    // Set to the new CRM's id after a successful create so the screen can
    // navigate into it; cleared once consumed.
    val createdCrmId: String? = null
)

/**
 * A backup file the user picked, ready to seed the create dialog.
 *
 * @property json the whole backup payload, restored after the CRM is made.
 * @property fileName shown on the picker button so the choice is visible.
 * @property name CRM name recorded in the backup, if any.
 */
data class BackupPrefill(
    val json: String,
    val fileName: String,
    val name: String?
)

@HiltViewModel
class CrmListViewModel @Inject constructor(
    private val repository: CrmsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CrmListUiState())
    val uiState: StateFlow<CrmListUiState> = _uiState.asStateFlow()

    init {
        loadCrms()
    }

    fun loadCrms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val crm = repository.listCrms()
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(
                    crm = crm,
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
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val crm = repository.listCrms()
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(
                    crm = crm,
                    isRefreshing = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleSearch() {
        val current = _uiState.value
        _uiState.value = current.copy(
            showSearch = !current.showSearch,
            searchQuery = if (current.showSearch) "" else current.searchQuery
        )
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
        viewModelScope.launch {
            try {
                val templates = repository.getTemplates()
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(templates = templates)
            } catch (_: Exception) {
            }
        }
    }

    fun hideCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false, backupPrefill = null)
    }

    /**
     * Reads the backup file the user picked and pulls the CRM's name out of it,
     * so the create dialog can seed itself.
     */
    fun readBackup(uri: Uri) {
        viewModelScope.launch {
            val content = repository.readTextFile(uri)
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
     * Creates a CRM, restoring the design held in [backupJson] when the user
     * picked a backup to import. Without one the server's own CRM design is
     * left as it is — every CRM is created with it.
     */
    fun createCrm(name: String, privacy: String, backupJson: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true)
            val importing = !backupJson.isNullOrBlank()
            try {
                val created = repository.createCrm(
                    name = name,
                    description = null,
                    privacy = privacy
                )
                val newCrmId = created.fingerprint.ifEmpty { created.id }
                if (importing) {
                    restoreBackup(newCrmId, backupJson)
                }
                // The create response only carries id/fingerprint, so reload the
                // full list before opening the CRM — this way the drawer and
                // list already contain it (with its name) when we navigate in.
                val crm = runCatching {
                    repository.listCrms().sortedWith(compareBy(NaturalCompare) { item -> item.name })
                }.getOrDefault(_uiState.value.crm)
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    showCreateDialog = false,
                    backupPrefill = null,
                    crm = crm,
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
     * Restores a picked backup into the new CRM: the design first, then the
     * objects when the file carries any. A half-restored CRM is worse than
     * none, so a failure deletes it and reports the error.
     */
    private suspend fun restoreBackup(crmId: String, backupJson: String) {
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
            runCatching { repository.deleteCrm(crmId) }
            throw e
        }
    }

    private fun hasData(root: JsonObject): Boolean {
        val objects = root.getAsJsonArray("objects")?.size() ?: 0
        val links = root.getAsJsonArray("links")?.size() ?: 0
        return objects > 0 || links > 0
    }

    fun filteredCrm(): List<Crm> {
        val query = _uiState.value.searchQuery.lowercase()
        if (query.isBlank()) return _uiState.value.crm
        return _uiState.value.crm.filter {
            it.name.lowercase().contains(query) ||
                it.description.lowercase().contains(query)
        }
    }

    fun unsubscribe(crmId: String) {
        viewModelScope.launch {
            try {
                repository.unsubscribe(crmId)
                _uiState.value = _uiState.value.copy(
                    crm = _uiState.value.crm.filterNot { it.id == crmId }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

}
