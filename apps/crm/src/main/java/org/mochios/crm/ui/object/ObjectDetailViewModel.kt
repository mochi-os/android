package org.mochios.crm.ui.`object`

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.model.Attachment
import org.mochios.android.model.Comment
import org.mochios.android.model.WebSocketEvent
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
    val isSaving: Boolean = false,
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

    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private var debounceJobs = mutableMapOf<String, Job>()
    private var currentCrmId: String = ""
    private var currentObjectId: String = ""
    private var wsSubscriptionId: String? = null
    private var wsSubscribedCrmId: String = ""

    override fun onCleared() {
        super.onCleared()
        wsSubscriptionId?.let { webSocket.unsubscribe(it) }
    }

    fun loadWithInitialObject(crmId: String, objectId: String, initialObject: CrmObject?, access: String = "") {
        currentCrmId = crmId
        currentObjectId = objectId
        if (initialObject != null) {
            _uiState.value = _uiState.value.copy(obj = initialObject, access = access)
        } else {
            _uiState.value = _uiState.value.copy(access = access)
        }
        subscribeWebSocket(crmId)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = _uiState.value.obj == null, error = null)
            try {
                val fetched = repository.getObject(crmId, objectId)
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

    private fun subscribeWebSocket(crmId: String) {
        if (crmId.isBlank() || crmId == wsSubscribedCrmId) return
        wsSubscriptionId?.let { webSocket.unsubscribe(it) }
        wsSubscribedCrmId = crmId
        viewModelScope.launch {
            val url = sessionManager.getServerUrlBlocking()
            wsSubscriptionId = webSocket.subscribe(url, crmId) { event ->
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

    // ---- Title ----

    fun updateTitle(title: String) {
        val obj = _uiState.value.obj ?: return
        _uiState.value = _uiState.value.copy(
            obj = obj.copy(readable = title)
        )
        debounce("title") {
            try {
                repository.updateObject(currentCrmId, currentObjectId, title = title)
            } catch (_: Exception) { }
        }
    }

    // ---- Values ----

    fun setValue(fieldId: String, value: String) {
        val obj = _uiState.value.obj ?: return
        val newValues = obj.values.toMutableMap()
        newValues[fieldId] = value
        _uiState.value = _uiState.value.copy(
            obj = obj.copy(values = newValues)
        )
        debounce("value_$fieldId") {
            try {
                repository.setValue(currentCrmId, currentObjectId, fieldId, value)
            } catch (_: Exception) { }
        }
    }

    fun setMultiValue(fieldId: String, values: List<String>) {
        val obj = _uiState.value.obj ?: return
        val newVals = obj.values.toMutableMap()
        newVals[fieldId] = values
        _uiState.value = _uiState.value.copy(
            obj = obj.copy(values = newVals)
        )
        debounce("value_$fieldId") {
            try {
                repository.setValue(currentCrmId, currentObjectId, fieldId, values.joinToString(","))
            } catch (_: Exception) { }
        }
    }

    // ---- Comments ----

    private fun loadComments() {
        viewModelScope.launch {
            try {
                val comments = repository.getComments(currentCrmId, currentObjectId)
                _uiState.value = _uiState.value.copy(comments = comments)
            } catch (_: Exception) { }
        }
    }

    fun createComment(content: String, parent: String? = null, files: List<File> = emptyList()) {
        viewModelScope.launch {
            try {
                repository.createComment(currentCrmId, currentObjectId, content, parent, files)
                loadComments()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun updateComment(commentId: String, content: String) {
        viewModelScope.launch {
            try {
                repository.updateComment(currentCrmId, currentObjectId, commentId, content)
                loadComments()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            try {
                repository.deleteComment(currentCrmId, currentObjectId, commentId)
                loadComments()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---- Activity ----

    private fun loadActivity() {
        viewModelScope.launch {
            try {
                val activity = repository.getActivity(currentCrmId, currentObjectId)
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
        viewModelScope.launch {
            try {
                val attachments = repository.getAttachments(currentCrmId, currentObjectId)
                _uiState.value = _uiState.value.copy(attachments = attachments)
            } catch (_: Exception) { }
        }
    }

    fun createAttachment(file: File) {
        viewModelScope.launch {
            try {
                repository.createAttachment(currentCrmId, currentObjectId, file)
                loadAttachments()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteAttachment(attachmentId: String) {
        viewModelScope.launch {
            try {
                repository.deleteAttachment(currentCrmId, currentObjectId, attachmentId)
                loadAttachments()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---- Links ----

    private fun loadLinks() {
        viewModelScope.launch {
            try {
                val result = repository.getLinks(currentCrmId, currentObjectId)
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
                repository.createLink(currentCrmId, source, currentObjectId, linktype)
                loadLinks()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteOutgoingLink(target: String, linktype: String) {
        viewModelScope.launch {
            try {
                repository.deleteLink(currentCrmId, currentObjectId, target, linktype)
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
                repository.deleteLink(currentCrmId, source, currentObjectId, linktype)
                loadLinks()
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
        viewModelScope.launch {
            try {
                repository.updateObject(currentCrmId, currentObjectId, parent = newParent)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---- Watchers ----

    private fun loadWatchers() {
        viewModelScope.launch {
            try {
                val result = repository.getWatchers(currentCrmId, currentObjectId)
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

    private fun debounce(key: String, delayMs: Long = 500, action: suspend () -> Unit) {
        debounceJobs[key]?.cancel()
        debounceJobs[key] = viewModelScope.launch {
            delay(delayMs)
            action()
        }
    }
}
