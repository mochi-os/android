// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.project

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import org.mochios.projects.lib.ActiveViewStore
import org.mochios.projects.model.Person
import org.mochios.projects.model.ProjectDetails
import org.mochios.projects.model.ProjectObject
import org.mochios.projects.model.ProjectView
import org.mochios.projects.repository.ProjectsRepository
import javax.inject.Inject

data class CreateObjectUiState(
    val details: ProjectDetails? = null,
    val objects: List<ProjectObject> = emptyList(),
    val people: List<Person> = emptyList(),
    val activeView: ProjectView? = null,
    val isLoading: Boolean = true,
    val loadError: MochiError? = null,
    val isCreating: Boolean = false,
    val createError: MochiError? = null,
    val createdObjectId: String? = null,
    /** The attachments of the object a copy is made from, which Create copies. */
    val copied: List<Attachment> = emptyList(),
    /**
     * The object a create made while some of its attachments did not go up:
     * a retry uploads them to it instead of creating another.
     */
    val created: String? = null,
    /** The names of the files and copied attachments [created] still lacks. */
    val unattached: List<String> = emptyList(),
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
     * The object a new one copies, as the web's Copy opens its form: its
     * class, parent and values fill the form, and Create copies its
     * attachments. Null for a plain new object.
     */
    val copyOf: String? = savedStateHandle.get<String>("copy")
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

    private val tracker = UploadTracker()

    /** Picked files already uploaded to [CreateObjectUiState.created]. */
    private val attached = mutableSetOf<Uri>()

    /** Copied attachments already on [CreateObjectUiState.created]. */
    private val copiedOnto = mutableSetOf<String>()

    /** The attachments picked on the form, while Create uploads them. */
    val upload: StateFlow<Upload?> = tracker.state

    // The form, held here rather than in the screen, so a rotation or a
    // change of theme or font size, which recreate the screen, keep it. Each
    // is seeded once from the design, the route and the object copied, and
    // the user's edits are left alone after that.

    /** The class picked, "" until the design has loaded. */
    var classId by mutableStateOf("")

    /** The parent picked; null for none or not yet picked. */
    var parentId by mutableStateOf<String?>(null)

    /** The values typed, by field id. */
    val values = mutableStateMapOf<String, String>()

    /** Files picked to attach, uploaded once the object exists. */
    val files = mutableStateListOf<Uri>()

    /** The attachments a copy carries over, which Create copies; any may be taken off first. */
    val copies = mutableStateListOf<Attachment>()

    /** The opening class the parent was last seeded for. */
    private var parentSeed: String? = null

    /** What the values were last seeded from: the class, its fields and options, and the preset. */
    private var valuesSeed: Any? = null

    private var copiesSeeded = false

    /** Opens the form on [initial] until a class is picked. */
    fun seedClass(initial: String) {
        if (classId.isBlank()) classId = initial
    }

    /** Puts [parent] in the parent picker once for each opening class [initial]. */
    fun seedParent(initial: String, parent: String?) {
        if (parentSeed == initial) return
        parentSeed = initial
        parentId = parent
    }

    /**
     * Replaces the values with [next] of them when [seed], what they are
     * seeded from, has changed since the last time; a screen drawn again over
     * the same class keeps what was typed.
     */
    fun seedValues(seed: Any, next: (Map<String, String>) -> Map<String, String>) {
        if (seed == valuesSeed) return
        valuesSeed = seed
        val seeded = next(values.toMap())
        values.clear()
        values.putAll(seeded)
    }

    /** Carries over a copy's attachments once they are read. */
    fun seedCopies(copied: List<Attachment>) {
        if (copiesSeeded || copied.isEmpty()) return
        copies.addAll(copied)
        copiesSeeded = true
    }

    init {
        load()
    }

    /** Fetches the project's design, objects and members. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)
            try {
                val details = repository.getProjectInfo(projectId)
                val objects = repository.getObjects(projectId)
                val people = runCatching { repository.getPeople(projectId) }
                    .getOrDefault(emptyList())
                val copied = copyOf?.let { id -> repository.getAttachments(projectId, id) }.orEmpty()
                val remembered = activeViewStore.get(projectId)
                val activeView = details.views.firstOrNull { view -> view.id == remembered }
                    ?: details.views.firstOrNull()
                _uiState.value = _uiState.value.copy(
                    details = details,
                    objects = objects,
                    people = people,
                    activeView = activeView,
                    copied = copied,
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

    /** Clears the pending-navigation id once the screen has left the form. */
    fun consumeCreatedObject() {
        _uiState.value = _uiState.value.copy(createdObjectId = null)
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

    /**
     * Creates an object with its values in one request, which the server
     * refuses whole, then uploads [uris] and copies [copies] (attachments of
     * the object a copy is made from) onto it, and reports its id back through
     * [CreateObjectUiState]. When an attachment does not go up the form stays,
     * naming what is missing, and the next call uploads to the object already
     * made instead of creating another.
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
            _uiState.value = _uiState.value.copy(isCreating = true, createError = null)
            val objectId = _uiState.value.created ?: try {
                repository.createObject(projectId, classId, parent, title, initialValues)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing was created, so the form stays put for a retry; the
                // message below Create says why.
                _uiState.value = _uiState.value.copy(isCreating = false, createError = e.toMochiError())
                return@launch
            }
            // The list the form returns to may read the cache, which does not
            // hold the new object.
            repository.invalidateCache(projectId)
            val missing = mutableListOf<String>()
            // One request per file, so one failure loses neither the object
            // nor the other files.
            val pending = uris.filter { uri -> uri !in attached }
            tracker.track(pending) {
                val staged = pending.map { uri -> uri to repository.stageFile(uri) }
                tracker.expect(staged.mapNotNull { (_, file) -> file })
                for ((uri, file) in staged) {
                    val sent = file != null && succeeds {
                        repository.createAttachment(projectId, objectId, listOf(file), tracker)
                    }
                    if (sent) attached += uri else missing += repository.fileName(uri).ifBlank { uri.lastPathSegment.orEmpty() }
                }
                repository.discardStaged(staged.mapNotNull { (_, file) -> file })
            }
            // A copy's attachments are copied by the server, which holds the
            // bytes.
            val copying = copies.filter { id -> id !in copiedOnto }
            if (copying.isNotEmpty()) {
                if (succeeds { repository.copyAttachments(projectId, objectId, copying) }) {
                    copiedOnto += copying
                } else {
                    val names = _uiState.value.copied.associate { attachment -> attachment.id to attachment.name }
                    missing += copying.map { id -> names[id].orEmpty() }
                }
            }
            if (missing.isEmpty()) {
                attached.clear()
                copiedOnto.clear()
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    created = null,
                    unattached = emptyList(),
                    createdObjectId = objectId.takeIf { id -> id.isNotBlank() },
                )
            } else {
                _uiState.value = _uiState.value.copy(isCreating = false, created = objectId, unattached = missing)
            }
        }
    }

    /** Whether [block] ran without failing; a cancellation still cancels. */
    private suspend fun succeeds(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}
