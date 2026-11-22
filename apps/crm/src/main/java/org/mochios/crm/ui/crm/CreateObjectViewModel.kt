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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.files.Upload
import org.mochios.android.files.UploadTracker
import org.mochios.android.model.Attachment
import org.mochios.android.model.User
import org.mochios.crm.lib.ActiveViewStore
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmObject
import org.mochios.crm.model.CrmView
import org.mochios.crm.model.FieldOption
import org.mochios.crm.model.Person
import org.mochios.crm.repository.CrmsRepository
import java.io.File
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
    /** How many attachments the last Create could not add, [createError] the first one's. */
    val failedAttachments: Int = 0,
    /**
     * The object a Create made while some of its attachments did not arrive.
     * The form's values are its own from then, and Create again sends only
     * the attachments still to go, so no second object is made.
     */
    val made: String? = null,
    val createdObjectId: String? = null,
    /** The attachments of the object a copy is made from, which Create copies. */
    val copied: List<Attachment> = emptyList(),
)

/**
 * What the create form holds. It lives in the view model, which a rotation or
 * a theme change keeps, so the form is not emptied by one; the screen's
 * seeding runs again on its return and finds it already done.
 *
 * @property classId The class picked, "" until the design has loaded.
 * @property parent The parent picked, null for the top level.
 * @property values Field values typed or picked, by field id.
 * @property files Files picked to attach once the object exists.
 * @property copies The attachments a copy carries over, which Create copies.
 */
data class CreateObjectForm(
    val classId: String = "",
    val parent: String? = null,
    val values: Map<String, String> = emptyMap(),
    val files: List<Uri> = emptyList(),
    val copies: List<Attachment> = emptyList(),
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
     * The object a new one copies, as the web's Copy opens its form: its
     * class, parent and values fill the form, and Create copies its
     * attachments. Null for a plain new object.
     */
    val copyOf: String? = savedStateHandle.get<String>("copy")
        ?.takeIf { id -> id.isNotBlank() }

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

    private val _form = MutableStateFlow(CreateObjectForm())

    /** What the form holds. */
    val form: StateFlow<CreateObjectForm> = _form.asStateFlow()

    /** Whether a copy's parent has been put on the form, which is done once. */
    private var parentSeeded = false

    /** Whether a copy's attachments have been put on the form, which is done once. */
    private var copiesSeeded = false

    /** The class [CreateObjectForm.values] were seeded for; another class seeds them afresh. */
    private var valuesSeeded: String? = null

    private val tracker = UploadTracker()

    /** The attachments picked on the form, while Create uploads them. */
    val upload: StateFlow<Upload?> = tracker.state

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
                val copied = copyOf?.let { id -> repository.getAttachments(crmId, id) }.orEmpty()
                val remembered = activeViewStore.get(crmId)
                val activeView = details.views.firstOrNull { view -> view.id == remembered }
                _uiState.value = _uiState.value.copy(
                    details = details,
                    objects = objects,
                    people = people,
                    activeView = activeView,
                    copied = copied,
                    isLoading = false,
                )
                // Once: a later load finds them on the form, or taken off it.
                if (!copiesSeeded) {
                    copiesSeeded = true
                    _form.value = _form.value.copy(copies = copied)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadError = e.toMochiError(),
                )
            }
        }
    }

    /** Opens the form on [classId] once the design is in; a class already picked stays. */
    fun seedClass(classId: String) {
        if (_form.value.classId.isBlank() && classId.isNotBlank()) {
            _form.value = _form.value.copy(classId = classId)
        }
    }

    /** The user picked [classId]: its parent and its values are chosen afresh. */
    fun selectClass(classId: String) {
        _form.value = _form.value.copy(classId = classId, parent = null)
    }

    /** Opens a copy under its original's [parent], once. */
    fun seedParent(parent: String?) {
        if (parentSeeded || parent.isNullOrBlank()) return
        parentSeeded = true
        if (_form.value.parent == null) _form.value = _form.value.copy(parent = parent)
    }

    /** Puts [parent] on the form, null for the top level. */
    fun selectParent(parent: String?) {
        if (_form.value.parent != parent) _form.value = _form.value.copy(parent = parent)
    }

    /**
     * Seeds the values of [classId] once: [preset] (a board column's "+", a
     * copy's every value) where the class can take it, then the first option
     * of each required enumerated field still blank. The grouping field is
     * left to the preset. A return to the screen finds them seeded and keeps
     * what was typed since.
     */
    fun seedValues(classId: String, preset: Map<String, String>) {
        if (classId.isBlank() || valuesSeeded == classId) return
        val details = _uiState.value.details ?: return
        valuesSeeded = classId
        val values = mutableMapOf<String, String>()
        preset.forEach { (fieldId, value) ->
            if (usableValue(classId, fieldId, value)) values[fieldId] = value
        }
        details.fields[classId].orEmpty().forEach { field ->
            if (field.fieldtype == "enumerated" && field.isRequired && values[field.id].isNullOrBlank()) {
                optionsForField(classId, field.id).firstOrNull()?.let { option -> values[field.id] = option.id }
            }
        }
        _form.value = _form.value.copy(values = values)
    }

    /** Sets the form's value of [fieldId]. */
    fun setValue(fieldId: String, value: String) {
        _form.value = _form.value.copy(values = _form.value.values + (fieldId to value))
    }

    /** Adds picked files to attach. */
    fun addFiles(uris: List<Uri>) {
        _form.value = _form.value.copy(files = _form.value.files + uris)
    }

    /** Takes the picked file at [index] off. */
    fun removeFile(index: Int) {
        _form.value = _form.value.copy(files = _form.value.files.filterIndexed { at, _ -> at != index })
    }

    /** Takes the copied attachment at [index] off. */
    fun removeCopy(index: Int) {
        _form.value = _form.value.copy(copies = _form.value.copies.filterIndexed { at, _ -> at != index })
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

    /** Picked files already uploaded to [CreateObjectUiState.made]. */
    private val uploaded = mutableSetOf<Uri>()

    /** Whether a copy's attachments are already on [CreateObjectUiState.made]. */
    private var copied = false

    /**
     * Creates an object with its values in one request and reports its id
     * back through [CreateObjectUiState]; [copies] are attachments of the
     * object a copy is made from, copied onto the new one. When attachments
     * fail the form stays, saying so, and a second call sends only those to
     * the object already made.
     */
    fun createObject(
        classId: String,
        title: String,
        parent: String? = null,
        initialValues: Map<String, String> = emptyMap(),
        uris: List<Uri> = emptyList(),
        copies: List<String> = emptyList(),
    ) {
        if (classId.isBlank() || _uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, createError = null, failedAttachments = 0)
            val id = _uiState.value.made ?: try {
                // The server checks every value before it writes the object,
                // so a refused one makes nothing and the form stays to fix it.
                repository.createObject(crmId, classId, parent, title, initialValues).id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isCreating = false, createError = e.toMochiError())
                return@launch
            }
            val failures = attach(id, uris, copies)
            if (failures.isEmpty()) {
                uploaded.clear()
                copied = false
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    made = null,
                    createdObjectId = id.takeIf { created -> created.isNotBlank() },
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    made = id,
                    createError = failures.first(),
                    failedAttachments = failures.size,
                )
            }
        }
    }

    /**
     * Uploads the picked files not yet on [objectId], one request each so a
     * failure costs only its own file, and copies a copy's attachments.
     * Returns a failure per attachment not added.
     */
    private suspend fun attach(objectId: String, uris: List<Uri>, copies: List<String>): List<MochiError> {
        val failures = mutableListOf<MochiError>()
        val waiting = uris.filter { uri -> uri !in uploaded }
        tracker.track(waiting) {
            val staged = mutableListOf<Pair<Uri, File>>()
            for (uri in waiting) {
                try {
                    staged += uri to repository.stageFiles(listOf(uri)).single()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures += e.toMochiError()
                }
            }
            tracker.expect(staged.map { (_, file) -> file })
            try {
                for ((uri, file) in staged) {
                    try {
                        repository.createAttachment(crmId, objectId, listOf(file), tracker)
                        uploaded += uri
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failures += e.toMochiError()
                    }
                }
            } finally {
                repository.discardStaged(staged.map { (_, file) -> file })
            }
        }
        // A copy's attachments are copied by the server, which holds the
        // bytes, in one request.
        if (copies.isNotEmpty() && !copied) {
            try {
                repository.copyAttachments(crmId, objectId, copies)
                copied = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = e.toMochiError()
                repeat(copies.size) { failures += error }
            }
        }
        return failures
    }
}
