// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.model.Attachment
import org.mochios.android.model.Comment
import org.mochios.android.model.WebSocketEvent
import org.mochios.android.ui.components.SaveStatus
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.projects.model.Activity
import org.mochios.projects.model.Branch
import org.mochios.projects.model.Link
import org.mochios.projects.model.MergeCheck
import org.mochios.projects.model.MergeRequest
import org.mochios.projects.model.ProjectObject
import org.mochios.projects.model.Repository
import org.mochios.projects.model.Watcher
import org.mochios.projects.repository.ProjectsRepository
import java.io.File
import javax.inject.Inject

data class ObjectDetailUiState(
    val obj: ProjectObject? = null,
    val comments: List<Comment> = emptyList(),
    val activity: List<Activity> = emptyList(),
    val requests: List<MergeRequest> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val outgoingLinks: List<Link> = emptyList(),
    val incomingLinks: List<Link> = emptyList(),
    val watchers: List<Watcher> = emptyList(),
    val isWatching: Boolean = false,
    val isLoading: Boolean = false,
    val error: MochiError? = null,
    val selectedTab: Int = 0,
    val saveStatus: SaveStatus = SaveStatus.Idle,
    val access: String = "",
    val siblingObjects: List<ProjectObject> = emptyList(),
    val people: List<org.mochios.projects.model.Person> = emptyList()
)

@HiltViewModel
class ObjectDetailViewModel @Inject constructor(
    private val repository: ProjectsRepository,
    private val sessionManager: SessionManager,
    private val webSocket: MochiWebSocket
) : ViewModel() {

    private val _uiState = MutableStateFlow(ObjectDetailUiState())
    val uiState: StateFlow<ObjectDetailUiState> = _uiState.asStateFlow()

    private var debounceJobs = mutableMapOf<String, Job>()

    // Auto-save runs on a scope deliberately NOT tied to viewModelScope: a
    // save scheduled just before the user leaves the screen must still
    // complete. viewModelScope would cancel it in onCleared().
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeSaves = 0
    private var savedResetJob: Job? = null

    private val _saveFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when an auto-save write fails — the screen shows a toast. */
    val saveFailed: SharedFlow<Unit> = _saveFailed.asSharedFlow()

    private val _actionFailed = MutableSharedFlow<MochiError>(extraBufferCapacity = 4)
    /**
     * Emits when a comment/attachment action fails while the sheet is open.
     * uiState.error only renders when no object is loaded, so without this
     * the failure is invisible — the input clears and nothing appears.
     */
    val actionFailed: SharedFlow<MochiError> = _actionFailed.asSharedFlow()

    private var currentProjectId: String = ""
    private var currentObjectId: String = ""
    private var wsSubscriptionId: String? = null
    private var wsSubscribedProjectId: String = ""

    override fun onCleared() {
        super.onCleared()
        wsSubscriptionId?.let { webSocket.unsubscribe(it) }
    }

    fun loadWithInitialObject(projectId: String, objectId: String, initialObject: ProjectObject?, access: String = "") {
        currentProjectId = projectId
        currentObjectId = objectId
        if (initialObject != null) {
            _uiState.value = _uiState.value.copy(obj = initialObject, access = access)
        } else {
            _uiState.value = _uiState.value.copy(access = access)
        }
        subscribeWebSocket(projectId)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = _uiState.value.obj == null, error = null)
            try {
                val fetched = repository.getObject(projectId, objectId)
                // The single-object endpoint doesn't return values — merge with what we have
                val existing = _uiState.value.obj
                val merged = if (fetched.values.isEmpty() && existing != null && existing.values.isNotEmpty()) {
                    fetched.copy(values = existing.values)
                } else {
                    fetched
                }
                _uiState.value = _uiState.value.copy(obj = merged, isLoading = false)
                loadComments()
                loadActivity()
                loadRequests()
                loadAttachments()
                loadWatchers()
                loadLinks()
                loadSiblingObjects()
                loadPeople()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    private fun subscribeWebSocket(projectId: String) {
        if (projectId.isBlank() || projectId == wsSubscribedProjectId) return
        wsSubscriptionId?.let { webSocket.unsubscribe(it) }
        wsSubscribedProjectId = projectId
        viewModelScope.launch {
            val url = sessionManager.getServerUrlBlocking()
            wsSubscriptionId = webSocket.subscribe(url, projectId) { event ->
                handleWebSocketEvent(event)
            }
        }
    }

    private fun handleWebSocketEvent(event: WebSocketEvent) {
        // Filter to events scoped to the object we're currently displaying.
        // Some server events use "object" for the affected object id (comment/*,
        // attachment/*) while others use "id" (values/update, object/update,
        // object/delete) — handle both keys per event type.
        when (event.type) {
            "comment/create", "comment/update", "comment/delete" -> {
                if (event.objectId == currentObjectId) {
                    loadComments()
                    loadActivity()
                }
            }
            "values/update" -> {
                if (event.id == currentObjectId) {
                    loadObjectOnly()
                    loadActivity()
                }
            }
            "object/update" -> {
                if (event.id == currentObjectId) {
                    loadObjectOnly()
                    loadActivity()
                }
            }
            "attachment/create", "attachment/add" -> {
                if (event.objectId == currentObjectId) {
                    loadAttachments()
                }
            }
            "attachment/delete", "attachment/remove" -> {
                // No object id on these events — refetch our slice unconditionally.
                // The list endpoint is scoped to the current object so this is cheap.
                loadAttachments()
            }
            "request/create", "request/update" -> {
                // Event payload nests the request object under "request" which the
                // shared model doesn't expose. Refetch unconditionally — the list
                // endpoint is scoped to the current object server-side.
                loadRequests()
            }
            "request/delete" -> {
                if (event.objectId == null || event.objectId == currentObjectId) {
                    loadRequests()
                }
            }
            "link/create", "link/delete" -> {
                if (event.source == currentObjectId || event.target == currentObjectId) {
                    loadLinks()
                }
            }
        }
    }

    private fun loadObjectOnly() {
        viewModelScope.launch {
            try {
                val fetched = repository.getObject(currentProjectId, currentObjectId)
                val existing = _uiState.value.obj
                val merged = if (fetched.values.isEmpty() && existing != null && existing.values.isNotEmpty()) {
                    fetched.copy(values = existing.values)
                } else {
                    fetched
                }
                _uiState.value = _uiState.value.copy(obj = merged)
            } catch (_: Exception) { }
        }
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    // ---- Values ----
    // (The title is just the class's title field — edited via setValue below,
    // the same as web. There is no separate title-update path.)

    fun setValue(fieldId: String, value: String) {
        val obj = _uiState.value.obj ?: return
        val newValues = obj.values.toMutableMap()
        newValues[fieldId] = value
        _uiState.value = _uiState.value.copy(
            obj = obj.copy(values = newValues)
        )
        scheduleSave("value_$fieldId") {
            repository.setValue(currentProjectId, currentObjectId, fieldId, value)
        }
    }

    fun setMultiValue(fieldId: String, values: List<String>) {
        val obj = _uiState.value.obj ?: return
        val newVals = obj.values.toMutableMap()
        newVals[fieldId] = values
        _uiState.value = _uiState.value.copy(
            obj = obj.copy(values = newVals)
        )
        scheduleSave("value_$fieldId") {
            repository.setValue(currentProjectId, currentObjectId, fieldId, values.joinToString(","))
        }
    }

    // ---- Comments ----

    private fun loadComments() {
        viewModelScope.launch {
            try {
                val comments = repository.getComments(currentProjectId, currentObjectId)
                _uiState.value = _uiState.value.copy(comments = comments)
            } catch (_: Exception) { }
        }
    }

    fun createComment(content: String, parent: String? = null, files: List<File> = emptyList()) {
        viewModelScope.launch {
            try {
                repository.createComment(currentProjectId, currentObjectId, content, parent, files)
                loadComments()
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
            }
        }
    }

    fun updateComment(commentId: String, content: String) {
        viewModelScope.launch {
            try {
                repository.updateComment(currentProjectId, currentObjectId, commentId, content)
                loadComments()
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
            }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            try {
                repository.deleteComment(currentProjectId, currentObjectId, commentId)
                loadComments()
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
            }
        }
    }

    // ---- Activity ----

    private fun loadActivity() {
        viewModelScope.launch {
            try {
                val activity = repository.getActivity(currentProjectId, currentObjectId)
                _uiState.value = _uiState.value.copy(activity = activity)
            } catch (_: Exception) { }
        }
    }

    // ---- Requests ----

    private fun loadRequests() {
        viewModelScope.launch {
            try {
                val requests = repository.getRequests(currentProjectId, currentObjectId)
                _uiState.value = _uiState.value.copy(requests = requests)
            } catch (_: Exception) { }
        }
    }

    fun createRequest(
        repository: String,
        source: String,
        target: String,
        title: String,
        description: String?,
        draft: Boolean
    ) {
        viewModelScope.launch {
            try {
                this@ObjectDetailViewModel.repository.createRequest(
                    currentProjectId, currentObjectId,
                    repository, source, target, title, description, draft
                )
                loadRequests()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun updateRequest(requestId: String, title: String?, description: String?, status: String?, draft: Boolean?) {
        viewModelScope.launch {
            try {
                repository.updateRequest(
                    currentProjectId, currentObjectId, requestId,
                    title, description, status, draft
                )
                loadRequests()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    suspend fun loadRepositories(): List<Repository> = try {
        repository.getRepositories()
    } catch (_: Exception) {
        emptyList()
    }

    suspend fun loadBranches(repo: String): List<Branch> = try {
        repository.getBranches(repo)
    } catch (_: Exception) {
        emptyList()
    }

    fun deleteRequest(requestId: String) {
        viewModelScope.launch {
            try {
                repository.deleteRequest(currentProjectId, currentObjectId, requestId)
                loadRequests()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    private val _mergeCheck = MutableStateFlow<MergeCheck?>(null)
    val mergeCheck: StateFlow<MergeCheck?> = _mergeCheck.asStateFlow()

    private val _isCheckingMerge = MutableStateFlow(false)
    val isCheckingMerge: StateFlow<Boolean> = _isCheckingMerge.asStateFlow()

    private val _mergeSuccess = MutableStateFlow(false)
    val mergeSuccess: StateFlow<Boolean> = _mergeSuccess.asStateFlow()

    fun checkMerge(repo: String, source: String, target: String) {
        viewModelScope.launch {
            _isCheckingMerge.value = true
            _mergeCheck.value = null
            try {
                _mergeCheck.value = repository.checkMerge(repo, source, target)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            } finally {
                _isCheckingMerge.value = false
            }
        }
    }

    fun performMerge(repo: String, source: String, target: String, message: String, method: String, requestId: String) {
        viewModelScope.launch {
            try {
                repository.merge(repo, source, target, message, method)
                // Mark request as merged
                repository.updateRequest(currentProjectId, currentObjectId, requestId, null, null, "merged", null)
                _mergeSuccess.value = true
                loadRequests()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun clearMergeState() {
        _mergeCheck.value = null
        _mergeSuccess.value = false
    }

    suspend fun searchUsers(query: String): List<org.mochios.android.ui.components.MentionSuggestion> {
        return try {
            repository.searchUsers(query).map {
                org.mochios.android.ui.components.MentionSuggestion(id = it.id.toString(), name = it.name)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // PersonPicker expects List<User>. Person.id is an entity-id string; we
    // round-trip it via User.fingerprint since User.id is a (numeric) Int.
    suspend fun searchPeople(query: String): List<org.mochios.android.model.User> {
        return try {
            repository.searchUsers(query).map {
                org.mochios.android.model.User(id = 0, name = it.name, fingerprint = it.id)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---- Attachments ----

    private fun loadAttachments() {
        viewModelScope.launch {
            try {
                val attachments = repository.getAttachments(currentProjectId, currentObjectId)
                _uiState.value = _uiState.value.copy(attachments = attachments)
            } catch (_: Exception) { }
        }
    }

    fun createAttachment(file: File) {
        viewModelScope.launch {
            try {
                repository.createAttachment(currentProjectId, currentObjectId, file)
                loadAttachments()
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
            }
        }
    }

    fun deleteAttachment(attachmentId: String) {
        viewModelScope.launch {
            try {
                repository.deleteAttachment(currentProjectId, currentObjectId, attachmentId)
                loadAttachments()
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
            }
        }
    }

    // ---- Links ----

    private fun loadLinks() {
        viewModelScope.launch {
            try {
                val result = repository.getLinks(currentProjectId, currentObjectId)
                _uiState.value = _uiState.value.copy(
                    incomingLinks = result.incoming,
                    outgoingLinks = result.outgoing
                )
            } catch (_: Exception) { }
        }
    }

    fun createLink(target: String, linktype: String) {
        viewModelScope.launch {
            try {
                repository.createLink(currentProjectId, currentObjectId, target, linktype)
                loadLinks()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun createReverseLink(source: String, linktype: String) {
        // Creates a link FROM source TO the current object (e.g. for "blocked by")
        viewModelScope.launch {
            try {
                repository.createLink(currentProjectId, source, currentObjectId, linktype)
                loadLinks()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteOutgoingLink(target: String, linktype: String) {
        viewModelScope.launch {
            try {
                repository.deleteLink(currentProjectId, currentObjectId, target, linktype)
                loadLinks()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteIncomingLink(source: String, linktype: String) {
        // To delete an incoming link, ask the source object to delete its outgoing link to us
        viewModelScope.launch {
            try {
                repository.deleteLink(currentProjectId, source, currentObjectId, linktype)
                loadLinks()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---- People (project members, used for resolving user-field display names) ----

    private fun loadPeople() {
        viewModelScope.launch {
            try {
                val people = repository.getPeople(currentProjectId)
                _uiState.value = _uiState.value.copy(people = people)
            } catch (_: Exception) { }
        }
    }

    // ---- Sibling objects (for parent / link pickers) ----

    private fun loadSiblingObjects() {
        viewModelScope.launch {
            try {
                val cached = repository.getCachedObjects(currentProjectId)
                if (cached != null) {
                    _uiState.value = _uiState.value.copy(siblingObjects = cached)
                    return@launch
                }
                val objects = repository.getObjects(currentProjectId)
                _uiState.value = _uiState.value.copy(siblingObjects = objects)
            } catch (_: Exception) { }
        }
    }

    // ---- Parent ----

    fun updateParent(newParent: String) {
        val obj = _uiState.value.obj ?: return
        _uiState.value = _uiState.value.copy(obj = obj.copy(parent = newParent))
        scheduleSave("parent", delayMs = 0) {
            repository.updateObject(currentProjectId, currentObjectId, parent = newParent)
        }
    }

    // ---- Watchers ----

    private fun loadWatchers() {
        viewModelScope.launch {
            try {
                val result = repository.getWatchers(currentProjectId, currentObjectId)
                _uiState.value = _uiState.value.copy(
                    watchers = result.watchers,
                    isWatching = result.watching
                )
            } catch (_: Exception) { }
        }
    }

    fun toggleWatch() {
        viewModelScope.launch {
            try {
                if (_uiState.value.isWatching) {
                    repository.removeWatcher(currentProjectId, currentObjectId)
                } else {
                    repository.addWatcher(currentProjectId, currentObjectId)
                }
                loadWatchers()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---- Helpers ----

    // Debounced auto-save: coalesces rapid edits to the same key, tracks
    // saveStatus for the indicator, and signals saveFailed (→ toast) when
    // the write throws. Runs on saveScope so a pending write survives the
    // user navigating away before the debounce elapses.
    private fun scheduleSave(key: String, delayMs: Long = 500, save: suspend () -> Unit) {
        debounceJobs[key]?.cancel()
        debounceJobs[key] = saveScope.launch {
            delay(delayMs)
            savedResetJob?.cancel()
            activeSaves++
            _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.Saving)
            val ok = try {
                save()
                true
            } catch (_: Exception) {
                false
            }
            activeSaves--
            debounceJobs.remove(key)
            if (!ok) {
                _saveFailed.tryEmit(Unit)
                _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.Error)
            } else if (activeSaves == 0 && _uiState.value.saveStatus != SaveStatus.Error) {
                _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.Saved)
                savedResetJob = saveScope.launch {
                    delay(2000)
                    if (_uiState.value.saveStatus == SaveStatus.Saved) {
                        _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.Idle)
                    }
                }
            }
        }
    }
}
