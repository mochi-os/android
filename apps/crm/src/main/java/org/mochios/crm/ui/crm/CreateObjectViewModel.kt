// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crm

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
import org.mochios.android.model.User
import org.mochios.crm.lib.ActiveViewStore
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmObject
import org.mochios.crm.model.CrmView
import org.mochios.crm.model.FieldOption
import org.mochios.crm.model.Person
import org.mochios.crm.repository.CrmsRepository
import javax.inject.Inject

data class CreateObjectUiState(
    val details: CrmDetails? = null,
    val objects: List<CrmObject> = emptyList(),
    val people: List<Person> = emptyList(),
    val activeView: CrmView? = null,
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
    private val repository: CrmsRepository,
    private val activeViewStore: ActiveViewStore
) : ViewModel() {

    /** The CRM the new object belongs to. */
    val crmId: String = savedStateHandle.get<String>("crmId") ?: ""

    /**
     * Field values the form opens with: the column a board's "+" was tapped in,
     * empty from the FAB.
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

    /** Fetches the CRM's design, objects and members. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)
            try {
                val details = repository.getCrmInfo(crmId)
                val objects = repository.getObjects(crmId)
                val people = runCatching { repository.getPeople(crmId) }
                    .getOrDefault(emptyList())
                val remembered = activeViewStore.get(crmId)
                val activeView = details.views.firstOrNull { view -> view.id == remembered }
                _uiState.value = _uiState.value.copy(
                    details = details,
                    objects = objects,
                    people = people,
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

    private fun optionsForField(classId: String, fieldId: String): List<FieldOption> {
        val details = _uiState.value.details ?: return emptyList()
        return details.options[classId]?.get(fieldId) ?: emptyList()
    }

    /**
     * Whether a preset value applies to [classId]: the field must exist on it
     * and an enumerated value must be one of its own options - a mixed-class
     * board can hand over another class's.
     */
    fun usableValue(classId: String, fieldId: String, value: String): Boolean {
        if (value.isBlank()) return false
        val details = _uiState.value.details ?: return false
        val field = details.fields[classId]?.find { candidate -> candidate.id == fieldId }
            ?: return false
        if (field.fieldtype != "enumerated") return true
        return optionsForField(classId, fieldId).any { option -> option.id == value }
    }

    /**
     * User search for user-type fields; PersonPicker wants [User]s whose
     * fingerprint is the person's entity id.
     */
    suspend fun searchPeople(query: String): List<User> {
        return try {
            repository.searchUsers(query).map { person ->
                User(id = 0, name = person.name, fingerprint = person.id)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** The picked file's real name, for labelling a draft attachment. */
    suspend fun fileName(uri: Uri): String = repository.fileName(uri)

    /** Creates an object and reports its id back through [CreateObjectUiState]. */
    fun createObject(
        classId: String,
        title: String,
        parent: String? = null,
        initialValues: Map<String, String> = emptyMap(),
        uris: List<Uri> = emptyList(),
    ) {
        if (classId.isBlank() || _uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, createError = null)
            try {
                val obj = repository.createObject(crmId, classId, parent, title)
                // One field at a time through the per-field endpoint: the bulk
                // values endpoint is form-encoded and silently drops some field
                // types (dates, currency amounts), so stage/value/closedate and
                // friends only land when each is sent as its own JSON body.
                for ((fieldId, value) in initialValues) {
                    repository.setValue(crmId, obj.id, fieldId, value)
                }
                // Upload any attachments picked on the form, mirroring web's
                // create-then-upload flow. One failure shouldn't lose the object
                // or the other files, so each upload is isolated.
                val files = repository.stageFiles(uris)
                for (file in files) {
                    runCatching { repository.createAttachment(crmId, obj.id, file) }
                }
                repository.discardStaged(files)
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createdObjectId = obj.id.takeIf { id -> id.isNotBlank() },
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
