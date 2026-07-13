// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.project

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
import org.mochios.android.auth.SessionManager
import org.mochios.android.model.WebSocketEvent
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.projects.model.FieldOption
import org.mochios.projects.model.ProjectClass
import org.mochios.projects.model.ProjectDetails
import org.mochios.projects.model.ProjectField
import org.mochios.projects.model.ProjectObject
import org.mochios.projects.model.ProjectView
import org.mochios.projects.repository.ProjectsRepository
import javax.inject.Inject

data class ProjectUiState(
    val projectDetails: ProjectDetails? = null,
    val objects: List<ProjectObject> = emptyList(),
    val activeViewId: String? = null,
    val searchQuery: String = "",
    val watchedOnly: Boolean = false,
    /** Object ids the local user watches, from the server objects/list response. */
    val watched: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: MochiError? = null,
    val showCreateObjectDialog: Boolean = false,
    /**
     * Pre-selected parent for the create-object dialog when invoked from
     * an "Add child" affordance on an existing object. Null means the
     * dialog is opened from the FAB and lets the user pick a parent (or
     * none) themselves.
     */
    val createObjectParent: String? = null,
    val isCreatingObject: Boolean = false,
    val selectedObjectId: String? = null,
    /**
     * Sort field per view id. Field is one of "rank", "number", "created",
     * "updated", or "field:<fieldId>" matching the web sort key scheme.
     * Null entry => fall back to view.sort or "rank".
     */
    val sortByView: Map<String, String> = emptyMap(),
    /** Sort direction per view id. "asc" or "desc". */
    val sortDirByView: Map<String, String> = emptyMap(),
    /** Project members, for resolving user-field display names on cards. */
    val people: List<org.mochios.projects.model.Person> = emptyList(),
)

@HiltViewModel
class ProjectViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectsRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager
) : ViewModel() {

    val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    private val _uiState = MutableStateFlow(ProjectUiState())
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()

    private var wsSubscriptionId: String? = null

    init {
        loadProject()
        subscribeWebSocket()
    }

    override fun onCleared() {
        super.onCleared()
        wsSubscriptionId?.let { webSocket.unsubscribe(it) }
    }

    fun loadProject() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)

            // Show cached data immediately if available
            val cachedDetails = repository.getCachedProjectInfo(projectId)
            val cachedObjects = repository.getCachedObjects(projectId)
            if (cachedDetails != null && cachedObjects != null) {
                val activeViewId = _uiState.value.activeViewId
                    ?: cachedDetails.views.firstOrNull()?.id
                _uiState.value = _uiState.value.copy(
                    projectDetails = cachedDetails,
                    objects = cachedObjects,
                    activeViewId = activeViewId,
                    isLoading = false
                )
                // Refresh in background
                refreshSilently()
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val details = repository.getProjectInfo(projectId)
                val objects = repository.getObjects(projectId)
                val people = runCatching { repository.getPeople(projectId) }
                    .getOrDefault(_uiState.value.people)
                val watched = repository.getWatched(projectId)
                val activeViewId = _uiState.value.activeViewId
                    ?: details.views.firstOrNull()?.id
                _uiState.value = _uiState.value.copy(
                    projectDetails = details,
                    objects = objects,
                    watched = watched,
                    people = people,
                    activeViewId = activeViewId,
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

    private suspend fun refreshSilently() {
        try {
            val details = repository.getProjectInfo(projectId)
            val objects = repository.getObjects(projectId)
            val people = runCatching { repository.getPeople(projectId) }
                .getOrDefault(_uiState.value.people)
            val watched = repository.getWatched(projectId)
            _uiState.value = _uiState.value.copy(
                projectDetails = details,
                objects = objects,
                watched = watched,
                people = people
            )
        } catch (_: Exception) {
            // Silent — cached data is still showing
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val details = repository.getProjectInfo(projectId)
                val objects = repository.getObjects(projectId)
                val people = runCatching { repository.getPeople(projectId) }
                    .getOrDefault(_uiState.value.people)
                val watched = repository.getWatched(projectId)
                _uiState.value = _uiState.value.copy(
                    projectDetails = details,
                    objects = objects,
                    watched = watched,
                    people = people,
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

    fun setActiveView(viewId: String) {
        _uiState.value = _uiState.value.copy(activeViewId = viewId)
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleWatchedOnly() {
        _uiState.value = _uiState.value.copy(watchedOnly = !_uiState.value.watchedOnly)
    }

    /**
     * Active sort field for the current view. Mirrors the web sort key scheme:
     * "rank" | "number" | "created" | "updated" | "field:<fieldId>".
     * Defaults to view.sort (when set) or "rank".
     */
    fun getActiveSortField(): String {
        val viewId = _uiState.value.activeViewId ?: return "rank"
        val override = _uiState.value.sortByView[viewId]
        if (override != null) return override
        val view = getActiveView() ?: return "rank"
        if (view.sort.isNotBlank()) {
            // view.sort stores a bare fieldId; project it onto the field: prefix
            // unless it matches a built-in.
            return when (view.sort) {
                "rank", "number", "created", "updated" -> view.sort
                else -> "field:${view.sort}"
            }
        }
        return "rank"
    }

    /** Active sort direction for the current view. "asc" or "desc". */
    fun getActiveSortDirection(): String {
        val viewId = _uiState.value.activeViewId ?: return "asc"
        val override = _uiState.value.sortDirByView[viewId]
        if (override != null) return override
        val view = getActiveView()
        return if (view?.direction == "desc") "desc" else "asc"
    }

    fun setSortField(field: String) {
        val viewId = _uiState.value.activeViewId ?: return
        _uiState.value = _uiState.value.copy(
            sortByView = _uiState.value.sortByView + (viewId to field)
        )
    }

    fun setSortDirection(direction: String) {
        val viewId = _uiState.value.activeViewId ?: return
        _uiState.value = _uiState.value.copy(
            sortDirByView = _uiState.value.sortDirByView + (viewId to direction)
        )
    }

    fun toggleSortDirection() {
        setSortDirection(if (getActiveSortDirection() == "asc") "desc" else "asc")
    }

    /**
     * Sort field options available for the current view. Matches the web bar:
     * built-in fields plus class fields whose flags include "sort". When a view
     * filters to specific classes, only those classes' fields are offered.
     */
    fun getSortFieldOptions(): List<Pair<String, String>> {
        val details = _uiState.value.projectDetails ?: return emptyList()
        val view = getActiveView()
        val classIds = if (view != null && view.classes.isNotEmpty()) {
            view.classes
        } else {
            details.classes.map { it.id }
        }
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Pair<String, String>>()
        for (classId in classIds) {
            val fields = details.fields[classId] ?: continue
            for (field in fields) {
                if (!field.isSortable) continue
                if (!seen.add(field.id)) continue
                result += "field:${field.id}" to field.name
            }
        }
        return result
    }

    fun showCreateObjectDialog(parent: String? = null) {
        _uiState.value = _uiState.value.copy(
            showCreateObjectDialog = true,
            createObjectParent = parent,
        )
    }

    fun hideCreateObjectDialog() {
        _uiState.value = _uiState.value.copy(
            showCreateObjectDialog = false,
            createObjectParent = null,
        )
    }

    fun selectObject(objectId: String?) {
        val closing = objectId == null && _uiState.value.selectedObjectId != null
        _uiState.value = _uiState.value.copy(selectedObjectId = objectId)
        // The detail sheet edits values through its own ViewModel, and local
        // writes don't reach the websocket (projects has no commit hook) — so
        // refresh on sheet close to reflect any field changes (card placement).
        if (closing) refreshObjects()
    }

    fun createObject(
        classId: String,
        title: String,
        parent: String? = null,
        initialValues: Map<String, String> = emptyMap(),
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingObject = true)
            try {
                val objectId = repository.createObject(projectId, classId, parent, title)
                if (initialValues.isNotEmpty()) {
                    repository.setValues(projectId, objectId, initialValues)
                }
                _uiState.value = _uiState.value.copy(
                    isCreatingObject = false,
                    showCreateObjectDialog = false,
                    createObjectParent = null,
                )
                refreshObjects()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreatingObject = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun deleteObject(objectId: String) {
        viewModelScope.launch {
            try {
                repository.deleteObject(projectId, objectId)
                if (_uiState.value.selectedObjectId == objectId) {
                    _uiState.value = _uiState.value.copy(selectedObjectId = null)
                }
                refreshObjects()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun moveObject(
        objectId: String,
        field: String? = null,
        value: String? = null,
        rank: Int? = null,
        rowField: String? = null,
        rowValue: String? = null,
        scopeParent: String? = null,
        promote: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                repository.moveObject(projectId, objectId, field, value, rank, rowField, rowValue, scopeParent, promote)
                refreshObjects()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    private fun refreshObjects() {
        viewModelScope.launch {
            try {
                val objects = repository.getObjects(projectId)
                val watched = repository.getWatched(projectId)
                _uiState.value = _uiState.value.copy(
                    objects = objects,
                    watched = watched
                )
            } catch (_: Exception) { }
        }
    }

    private fun subscribeWebSocket() {
        viewModelScope.launch {
            val serverUrl = sessionManager.serverUrl.let {
                sessionManager.getServerUrlBlocking()
            }
            wsSubscriptionId = webSocket.subscribe(serverUrl, projectId) { event ->
                handleWebSocketEvent(event)
            }
        }
    }

    private fun handleWebSocketEvent(event: WebSocketEvent) {
        // Server event types are slash-namespaced (object/update, values/update,
        // ...) — see the mochi.websocket.write calls in projects.star. The
        // previous underscore names here never matched anything, so the board
        // ignored every push and cards only moved on a manual reload.
        val type = event.type ?: return
        when {
            type in setOf(
                "object/create", "object/update", "object/delete",
                "object/ranks", "values/update",
            ) -> refreshObjects()
            // Structure changes: board columns derive from field options, plus
            // views/classes/hierarchy — reload the whole project.
            type == "project/update" || type == "project/resynced" ||
                type == "hierarchy/set" ||
                type.substringBefore("/") in setOf("class", "field", "option", "view") ->
                loadProject()
        }
    }

    // ---- Helpers for views ----

    fun getActiveView(): ProjectView? {
        val details = _uiState.value.projectDetails ?: return null
        val activeId = _uiState.value.activeViewId ?: return details.views.firstOrNull()
        return details.views.find { it.id == activeId } ?: details.views.firstOrNull()
    }

    fun getFieldById(fieldId: String): ProjectField? {
        val details = _uiState.value.projectDetails ?: return null
        for ((_, fields) in details.fields) {
            fields.find { it.id == fieldId }?.let { return it }
        }
        return null
    }

    fun getOptionsForField(classId: String, fieldId: String): List<FieldOption> {
        val details = _uiState.value.projectDetails ?: return emptyList()
        return details.options[classId]?.get(fieldId) ?: emptyList()
    }

    fun getAllOptionsForField(fieldId: String): List<FieldOption> {
        val details = _uiState.value.projectDetails ?: return emptyList()
        for ((_, classOptions) in details.options) {
            val options = classOptions[fieldId]
            if (!options.isNullOrEmpty()) return options
        }
        return emptyList()
    }

    fun reparentObject(objectId: String, newParentId: String) {
        viewModelScope.launch {
            try {
                repository.updateObject(projectId, objectId, newParentId)
                refreshObjects()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun getClassById(classId: String): ProjectClass? {
        return _uiState.value.projectDetails?.classes?.find { it.id == classId }
    }

    /** Find the classId that owns a given fieldId. */
    private fun findClassForField(fieldId: String): String? {
        val details = _uiState.value.projectDetails ?: return null
        for ((classId, fields) in details.fields) {
            if (fields.any { it.id == fieldId }) return classId
        }
        return null
    }

    fun addColumnOption(fieldId: String, name: String, colour: String? = null) {
        val classId = findClassForField(fieldId) ?: return
        viewModelScope.launch {
            try {
                repository.createOption(projectId, classId, fieldId, name, colour)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun renameColumnOption(fieldId: String, optionId: String, name: String) {
        val classId = findClassForField(fieldId) ?: return
        viewModelScope.launch {
            try {
                repository.updateOption(projectId, classId, fieldId, optionId, name, null, null)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteColumnOption(fieldId: String, optionId: String) {
        val classId = findClassForField(fieldId) ?: return
        viewModelScope.launch {
            try {
                repository.deleteOption(projectId, classId, fieldId, optionId)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /**
     * Persist a new column-option ordering after a drag-reorder. [order] is
     * the full list of option ids in display order — the server replaces the
     * stored ranks accordingly.
     */
    fun reorderColumnOptions(fieldId: String, order: List<String>) {
        val classId = findClassForField(fieldId) ?: return
        viewModelScope.launch {
            try {
                repository.reorderOptions(projectId, classId, fieldId, order.joinToString(","))
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /**
     * Returns descendant ids of [objectId] in the current object set. Used
     * for tree drag-drop cycle prevention — a row may not be reparented under
     * itself or any of its own descendants.
     */
    fun collectDescendants(objectId: String): Set<String> {
        val byParent = _uiState.value.objects.groupBy { it.parent }
        val result = mutableSetOf<String>()
        val stack = ArrayDeque<String>()
        stack.add(objectId)
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            for (child in byParent[id].orEmpty()) {
                if (result.add(child.id)) stack.add(child.id)
            }
        }
        return result
    }

    fun getFilteredObjects(): List<ProjectObject> {
        val state = _uiState.value
        val view = getActiveView() ?: return sortObjects(state.objects)
        var objects = state.objects

        // Filter by view's class filter
        if (view.classes.isNotEmpty()) {
            objects = objects.filter { it.objectClass in view.classes }
        }

        // Filter by search query
        val query = state.searchQuery.lowercase()
        if (query.isNotBlank()) {
            objects = objects.filter {
                it.readable.lowercase().contains(query) ||
                    it.values.values.any { v -> v?.toString()?.lowercase()?.contains(query) == true }
            }
        }

        // Filter by view's filter field
        if (view.filter.isNotBlank()) {
            val parts = view.filter.split(":")
            if (parts.size == 2) {
                val filterFieldId = parts[0]
                val filterValue = parts[1]
                objects = objects.filter { it.stringValue(filterFieldId) == filterValue }
            }
        }

        // Filter to watched objects only
        if (state.watchedOnly) {
            val watchedSet = state.watched.toSet()
            objects = objects.filter { watchedSet.contains(it.id) }
        }

        return sortObjects(objects)
    }

    /**
     * Sort objects by the active sort field/direction. Mirrors the web logic
     * in `web/src/features/board/components/board-container.tsx::sortObjects`
     * — built-in numeric fields compare numerically, custom fields compare as
     * strings (case-insensitive).
     */
    fun sortObjects(objects: List<ProjectObject>): List<ProjectObject> {
        val field = getActiveSortField()
        val multiplier = if (getActiveSortDirection() == "desc") -1 else 1
        // rank is a fractional-index text key whose ASCII (binary) order is the
        // intended order (#53), so compare the keys as strings — matching the
        // web's rankCompare. The remaining built-ins are still numeric.
        if (field == "rank") {
            return objects.sortedWith(Comparator { a, b -> a.rank.compareTo(b.rank) * multiplier })
        }
        val numericFields = setOf("number", "created", "updated")
        return if (field in numericFields) {
            objects.sortedWith(Comparator { a, b ->
                val av = when (field) {
                    "number" -> a.number.toLong()
                    "created" -> a.created
                    "updated" -> a.updated
                    else -> 0L
                }
                val bv = when (field) {
                    "number" -> b.number.toLong()
                    "created" -> b.created
                    "updated" -> b.updated
                    else -> 0L
                }
                av.compareTo(bv) * multiplier
            })
        } else {
            val fieldId = if (field.startsWith("field:")) field.substring(6) else field
            objects.sortedWith(Comparator { a, b ->
                val av = a.stringValue(fieldId).lowercase()
                val bv = b.stringValue(fieldId).lowercase()
                av.compareTo(bv) * multiplier
            })
        }
    }

    fun getCardFields(classId: String): List<ProjectField> {
        val details = _uiState.value.projectDetails ?: return emptyList()
        val allFields = details.fields[classId] ?: return emptyList()
        val view = getActiveView()
        if (view != null && view.fields.isNotBlank()) {
            val viewFieldIds = view.fields.split(",").map { it.trim() }.toSet()
            return allFields.filter { it.id in viewFieldIds }
        }
        return allFields.filter { it.showOnCard }
    }
}
