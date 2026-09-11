// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
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
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.crm.model.Activity
import org.mochios.crm.model.Link
import org.mochios.crm.model.CrmObject
import org.mochios.crm.model.Watcher
import org.mochios.crm.repository.CrmsRepository
import java.io.File
import javax.inject.Inject

data class ObjectDetailUiState(
    val obj: CrmObject? = null,
    val comments: List<Comment> = emptyList(),
    val activity: List<Activity> = emptyList(),
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
    val siblingObjects: List<CrmObject> = emptyList(),
    val people: List<org.mochios.crm.model.Person> = emptyList()
)

@HiltViewModel
class ObjectDetailViewModel @Inject constructor(
    private val repository: CrmsRepository,
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

    private val _saveFailed = MutableSharedFlow<MochiError>(extraBufferCapacity = 1)
    /** Emits when an auto-save write fails — the screen shows a toast. */
    val saveFailed: SharedFlow<MochiError> = _saveFailed.asSharedFlow()

    private val _actionFailed = MutableSharedFlow<MochiError>(extraBufferCapacity = 4)
    /**
     * Comment/attachment failures while the sheet is open; uiState.error only
     * renders when no object is loaded.
     */
    val actionFailed: SharedFlow<MochiError> = _actionFailed.asSharedFlow()

    private var currentCrmId: String = ""
    private var currentObjectId: String = ""
    /** The in-flight detail fetch, cancelled when the sheet switches object. */
    private var loadJob: Job? = null

    /** Pending background refetches, one per slice of the object. */
    private val refreshJobs = mutableMapOf<String, Job>()

    private var wsSubscriptionId: String? = null
    private var wsSubscribedCrmId: String = ""

    override fun onCleared() {
        super.onCleared()
        wsSubscriptionId?.let { webSocket.unsubscribe(it) }
    }

    fun loadWithInitialObject(crmId: String, objectId: String, initialObject: CrmObject?, access: String = "") {
        currentCrmId = crmId
        currentObjectId = objectId
        // Reset rather than copy: the previous selection's comments,
        // attachments, links and watchers would otherwise stay on screen while
        // the new object's header loads. Projects does the same. An object is
        // carried over only when it is the one being opened.
        val carried = initialObject ?: _uiState.value.obj?.takeIf { current -> current.id == objectId }
        _uiState.value = ObjectDetailUiState(
            obj = carried,
            access = access,
            isLoading = carried == null,
        )
        subscribeWebSocket(crmId)
        // Cancel a fetch still in flight for the object we just left, or a slow
        // response for A lands after the sheet switched to B and puts one
        // object's header above another's tabs.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val fetched = repository.getObject(crmId, objectId)
                // Cancellation is cooperative and the tab loaders read the
                // current ids, so re-check before writing anything.
                if (currentCrmId != crmId || currentObjectId != objectId) return@launch
                // Keep the values already on screen when the fetch carries
                // none, so the fields do not blank out mid-load.
                val existing = _uiState.value.obj
                val merged = if (fetched.values.isEmpty() && existing != null && existing.values.isNotEmpty()) {
                    fetched.copy(values = existing.values)
                } else {
                    fetched
                }
                _uiState.value = _uiState.value.copy(obj = merged, isLoading = false)
                loadComments()
                loadActivity()
                loadAttachments()
                loadWatchers()
                loadLinks()
                loadSiblingObjects()
                loadPeople()
            } catch (e: Exception) {
                if (currentCrmId != crmId || currentObjectId != objectId) return@launch
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    private fun subscribeWebSocket(crmId: String) {
        if (crmId.isBlank() || crmId == wsSubscribedCrmId) return
        wsSubscriptionId?.let { webSocket.unsubscribe(it) }
        wsSubscribedCrmId = crmId
        viewModelScope.launch {
            val url = sessionManager.getServerUrlBlocking()
            wsSubscriptionId = webSocket.subscribe(url, crmId, app = "crm") { event ->
                handleWebSocketEvent(event)
            }
        }
    }

    /**
     * Run [load] in the background after [REFRESH_DEBOUNCE], replacing any
     * pending run for the same [slice]: one save sends values/update and
     * object/update, and our own comment, link or attachment echoes back, so
     * each burst makes one fetch per slice.
     */
    private fun refreshSlice(slice: String, load: () -> Unit) {
        refreshJobs[slice]?.cancel()
        refreshJobs[slice] = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            load()
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
                    refreshSlice("comments") { loadComments() }
                    refreshSlice("activity") { loadActivity() }
                }
            }
            "values/update" -> {
                if (event.id == currentObjectId) {
                    refreshSlice("object") { loadObjectOnly() }
                    refreshSlice("activity") { loadActivity() }
                }
            }
            "object/update" -> {
                if (event.id == currentObjectId) {
                    refreshSlice("object") { loadObjectOnly() }
                    refreshSlice("activity") { loadActivity() }
                }
            }
            "attachment/create", "attachment/add" -> {
                if (event.objectId == currentObjectId) {
                    refreshSlice("attachments") { loadAttachments() }
                }
            }
            "attachment/delete", "attachment/remove" -> {
                // No object id on these events — refetch our slice unconditionally.
                // The list endpoint is scoped to the current object so this is cheap.
                refreshSlice("attachments") { loadAttachments() }
            }
            "link/create", "link/delete" -> {
                if (event.source == currentObjectId || event.target == currentObjectId) {
                    refreshSlice("links") { loadLinks() }
                }
            }
        }
    }

    private fun loadObjectOnly() {
        viewModelScope.launch {
            try {
                val fetched = repository.getObject(currentCrmId, currentObjectId)
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
        val crmId = currentCrmId
        val objectId = currentObjectId
        scheduleSave("value_$fieldId") {
            repository.setValue(crmId, objectId, fieldId, value)
        }
    }

    // ---- Comments ----

    private fun loadComments() {
        val objectId = currentObjectId
        viewModelScope.launch {
            try {
                val comments = repository.getComments(currentCrmId, objectId)
                if (objectId != currentObjectId) return@launch
                _uiState.value = _uiState.value.copy(comments = comments)
            } catch (_: Exception) { }
        }
    }

    /** Stages any picked attachments, then posts the comment. */
    fun createComment(content: String, parent: String? = null, uris: List<Uri> = emptyList()) {
        viewModelScope.launch {
            val files = repository.stageFiles(uris)
            try {
                repository.createComment(currentCrmId, currentObjectId, content, parent, files)
                refreshSlice("comments") { loadComments() }
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
            } finally {
                repository.discardStaged(files)
            }
        }
    }

    /** The picked file's real name, for labelling a draft attachment. */
    suspend fun fileName(uri: Uri): String = repository.fileName(uri)

    fun updateComment(commentId: String, content: String) {
        viewModelScope.launch {
            try {
                repository.updateComment(currentCrmId, currentObjectId, commentId, content)
                refreshSlice("comments") { loadComments() }
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
            }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            try {
                repository.deleteComment(currentCrmId, currentObjectId, commentId)
                refreshSlice("comments") { loadComments() }
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
            }
        }
    }

    // ---- Activity ----

    private fun loadActivity() {
        val objectId = currentObjectId
        viewModelScope.launch {
            try {
                val activity = repository.getActivity(currentCrmId, objectId)
                if (objectId != currentObjectId) return@launch
                _uiState.value = _uiState.value.copy(activity = activity)
            } catch (_: Exception) { }
        }
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
        val objectId = currentObjectId
        viewModelScope.launch {
            try {
                val attachments = repository.getAttachments(currentCrmId, objectId)
                if (objectId != currentObjectId) return@launch
                _uiState.value = _uiState.value.copy(attachments = attachments)
            } catch (_: Exception) { }
        }
    }

    /** Stages the picked file, then uploads it as an attachment. */
    fun createAttachment(uri: Uri) {
        viewModelScope.launch {
            val file = repository.stageFile(uri) ?: return@launch
            try {
                repository.createAttachment(currentCrmId, currentObjectId, file)
                refreshSlice("attachments") { loadAttachments() }
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
            } finally {
                repository.discardStaged(listOf(file))
            }
        }
    }

    fun deleteAttachment(attachmentId: String) {
        viewModelScope.launch {
            try {
                repository.deleteAttachment(currentCrmId, currentObjectId, attachmentId)
                refreshSlice("attachments") { loadAttachments() }
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
            }
        }
    }

    // ---- Links ----

    private fun loadLinks() {
        val objectId = currentObjectId
        viewModelScope.launch {
            try {
                val result = repository.getLinks(currentCrmId, objectId)
                if (objectId != currentObjectId) return@launch
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
                repository.createLink(currentCrmId, currentObjectId, target, linktype)
                refreshSlice("links") { loadLinks() }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun createReverseLink(source: String, linktype: String) {
        // Creates a link FROM source TO the current object (e.g. for "blocked by")
        viewModelScope.launch {
            try {
                repository.createLink(currentCrmId, source, currentObjectId, linktype)
                refreshSlice("links") { loadLinks() }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteOutgoingLink(target: String, linktype: String) {
        viewModelScope.launch {
            try {
                repository.deleteLink(currentCrmId, currentObjectId, target, linktype)
                refreshSlice("links") { loadLinks() }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteIncomingLink(source: String, linktype: String) {
        // To delete an incoming link, ask the source object to delete its outgoing link to us
        viewModelScope.launch {
            try {
                repository.deleteLink(currentCrmId, source, currentObjectId, linktype)
                refreshSlice("links") { loadLinks() }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---- People (crm members, used for resolving user-field display names) ----

    private fun loadPeople() {
        viewModelScope.launch {
            try {
                val people = repository.getPeople(currentCrmId)
                _uiState.value = _uiState.value.copy(people = people)
            } catch (_: Exception) { }
        }
    }

    // ---- Sibling objects (for parent / link pickers) ----

    private fun loadSiblingObjects() {
        viewModelScope.launch {
            try {
                val cached = repository.getCachedObjects(currentCrmId)
                if (cached != null) {
                    _uiState.value = _uiState.value.copy(siblingObjects = cached)
                    return@launch
                }
                val objects = repository.getObjects(currentCrmId)
                _uiState.value = _uiState.value.copy(siblingObjects = objects)
            } catch (_: Exception) { }
        }
    }

    // ---- Parent ----

    fun updateParent(newParent: String) {
        val obj = _uiState.value.obj ?: return
        _uiState.value = _uiState.value.copy(obj = obj.copy(parent = newParent))
        val crmId = currentCrmId
        val objectId = currentObjectId
        scheduleSave("parent", delayMs = 0) {
            repository.updateObject(crmId, objectId, parent = newParent)
        }
    }

    // ---- Watchers ----

    private fun loadWatchers() {
        val objectId = currentObjectId
        viewModelScope.launch {
            try {
                val result = repository.getWatchers(currentCrmId, objectId)
                if (objectId != currentObjectId) return@launch
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
                    repository.removeWatcher(currentCrmId, currentObjectId)
                } else {
                    repository.addWatcher(currentCrmId, currentObjectId)
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
        val job = saveScope.launch {
            delay(delayMs)
            savedResetJob?.cancel()
            activeSaves++
            _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.Saving)
            val failure = try {
                save()
                null
            } catch (e: CancellationException) {
                // A newer edit to the same key superseded this write: not a
                // failure, and the newer job owns the status from here.
                activeSaves--
                throw e
            } catch (e: Exception) {
                e.toMochiError()
            }
            activeSaves--
            if (failure != null) {
                _saveFailed.tryEmit(failure)
                _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.Error)
            } else if (activeSaves == 0) {
                _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.Saved)
                savedResetJob = saveScope.launch {
                    delay(2000)
                    if (_uiState.value.saveStatus == SaveStatus.Saved) {
                        _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.Idle)
                    }
                }
            }
        }
        debounceJobs[key] = job
        // Only the job that owns the slot clears it: a superseded job finishing
        // late must not drop its successor's entry.
        job.invokeOnCompletion { if (debounceJobs[key] === job) debounceJobs.remove(key) }
    }
}
