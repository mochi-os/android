// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.project

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.model.WebSocketEvent
import org.mochios.android.files.MIME_ZIP
import org.mochios.android.files.PendingExport
import org.mochios.android.files.SavedExport
import org.mochios.android.util.NaturalCompare
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.projects.lib.ActiveViewStore
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
    /**
     * Selected option ids per field: OR within a field, AND across fields;
     * empty means unconstrained.
     */
    val fieldFilters: Map<String, Set<String>> = emptyMap(),
    /** Object ids the local user watches, from the server objects/list response. */
    val watched: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: MochiError? = null,
    val isExporting: Boolean = false,
    /** Export data fetched and waiting for the user to pick a destination. */
    val pendingExport: PendingExport? = null,
    /** An export that landed, ready to be offered to the share sheet. */
    val savedExport: SavedExport? = null,
    val exportFailed: Boolean = false,
    val selectedObjectId: String? = null,
    /**
     * Sort override per view id: "rank", "number", "created", "updated" or
     * "field:<fieldId>".
     */
    val sortByView: Map<String, String> = emptyMap(),
    /** Sort direction per view id. "asc" or "desc". */
    val sortDirByView: Map<String, String> = emptyMap(),
    /** Project members, for resolving user-field display names on cards. */
    val people: List<org.mochios.projects.model.Person> = emptyList(),
)

/** Seconds in a day, for turning an ISO date into an epoch-second sort key. */
private const val SECONDS_PER_DAY = 86_400L

/** Seconds for a date value, or null when it is neither an epoch nor an ISO
 * date. */
private fun dateSortKey(value: String): Long? {
    value.toLongOrNull()?.let { seconds -> return seconds }
    return try {
        LocalDate.parse(value).toEpochDay() * SECONDS_PER_DAY
    } catch (_: Exception) {
        null
    }
}

/**
 * Order two custom-field values. Text compares with NaturalCompare, so the
 * order is accent- and case-blind and "Sprint 2" precedes "Sprint 10"; a
 * number compares numerically and a date by its epoch key, so neither sorts
 * as text. An enumerated value is an opaque option id, so the options
 * themselves are compared on the rank the designer gave them - the board's
 * column order, which beats alphabetising a pipeline.
 *
 * Empty values sink to the bottom whichever way the sort runs: flipping to
 * descending is meant to bring the far end of the data into view, not a wall
 * of objects that never filled the field in. That is why the blank branch
 * returns before the multiplier is applied.
 */
internal fun compareFieldValues(
    av: String,
    bv: String,
    fieldtype: String?,
    aOption: FieldOption?,
    bOption: FieldOption?,
    multiplier: Int,
): Int {
    if (av.isBlank() || bv.isBlank()) {
        return when {
            av.isBlank() && bv.isBlank() -> 0
            av.isBlank() -> 1
            else -> -1
        }
    }
    val comparison = when (fieldtype) {
        "number" -> {
            val an = av.toDoubleOrNull()
            val bn = bv.toDoubleOrNull()
            if (an != null && bn != null) an.compareTo(bn) else NaturalCompare.compare(av, bv)
        }
        "date" -> {
            val ad = dateSortKey(av)
            val bd = dateSortKey(bv)
            if (ad != null && bd != null) ad.compareTo(bd) else NaturalCompare.compare(av, bv)
        }
        "enumerated" -> {
            if (aOption != null && bOption != null) {
                aOption.rank.compareTo(bOption.rank)
            } else {
                NaturalCompare.compare(aOption?.name ?: av, bOption?.name ?: bv)
            }
        }
        else -> NaturalCompare.compare(av, bv)
    }
    return comparison * multiplier
}

@HiltViewModel
class ProjectViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectsRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
    private val activeViewStore: ActiveViewStore,
) : ViewModel() {

    val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    private val _uiState = MutableStateFlow(ProjectUiState())
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()

    /** Emits the project's share link once fetched, for the screen to share. */
    private val _shareLink = MutableSharedFlow<String>()
    val shareLink: SharedFlow<String> = _shareLink.asSharedFlow()

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
                    ?: rememberedViewId(cachedDetails.views)
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
                    ?: rememberedViewId(details.views)
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
        activeViewStore.set(projectId, viewId)
    }

    /** Fetch the project's share link and emit it for the screen to share. */
    fun shareProject() {
        viewModelScope.launch {
            try {
                val link = repository.getShareLink(projectId)
                if (link.isNotBlank()) {
                    _shareLink.emit(link)
                }
            } catch (_: Exception) {
                // Best-effort: a failed share link simply does nothing.
            }
        }
    }

    /**
     * The handle the data endpoints want: the project's canonical id, falling
     * back to its fingerprint and then to whatever the screen was routed with.
     */
    private fun entityId(): String {
        val project = _uiState.value.projectDetails?.project ?: return projectId
        return project.id.ifEmpty { project.fingerprint.ifEmpty { projectId } }
    }

    /**
     * Warms the server-side export, then asks the screen for a destination; the
     * zip is streamed into it by [writeExportTo].
     */
    fun exportProject() {
        if (_uiState.value.isExporting) {
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)
            try {
                var rounds = 0
                while (rounds < MAX_WARM_ROUNDS) {
                    val warm = repository.warmExport(entityId())
                    if (warm.remaining <= 0) {
                        break
                    }
                    rounds++
                }
                val name = _uiState.value.projectDetails?.project?.name
                _uiState.value = _uiState.value.copy(
                    pendingExport = PendingExport(
                        suggestedName = repository.exportFileName(name, "projects-backup", "zip"),
                        mimeType = MIME_ZIP,
                    )
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(error = error.toMochiError())
            } finally {
                _uiState.value = _uiState.value.copy(isExporting = false)
            }
        }
    }

    /**
     * Downloads the pending export into the destination the user picked.
     */
    fun writeExportTo(uri: Uri) {
        val pending = _uiState.value.pendingExport ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(pendingExport = null, isExporting = true)
            val content = pending.content
            val ok = try {
                if (content != null) {
                    repository.saveTextFile(uri, content)
                } else {
                    repository.downloadExport(entityId(), uri)
                }
            } catch (_: Exception) {
                false
            }
            _uiState.value = _uiState.value.copy(
                isExporting = false,
                savedExport = if (ok) {
                    SavedExport(uri, pending.mimeType, pending.suggestedName)
                } else {
                    null
                },
                exportFailed = !ok
            )
        }
    }

    /** Drops the pending export when the user backs out of the save dialog. */
    fun cancelExport() {
        _uiState.value = _uiState.value.copy(pendingExport = null)
    }

    fun clearExportResult() {
        _uiState.value = _uiState.value.copy(savedExport = null, exportFailed = false)
    }

    /**
     * The view the user last opened in this project, if it still exists — a
     * view deleted since then falls back to the project's own first view.
     */
    private fun rememberedViewId(views: List<ProjectView>): String? {
        val remembered = activeViewStore.get(projectId) ?: return null
        return remembered.takeIf { id -> views.any { view -> view.id == id } }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleWatchedOnly() {
        _uiState.value = _uiState.value.copy(watchedOnly = !_uiState.value.watchedOnly)
    }

    /** Adds or removes [optionId] from the selection for [fieldId]. */
    fun toggleFieldFilter(fieldId: String, optionId: String) {
        val current = _uiState.value.fieldFilters[fieldId].orEmpty()
        val updated = if (optionId in current) current - optionId else current + optionId
        val filters = _uiState.value.fieldFilters.toMutableMap()
        if (updated.isEmpty()) filters.remove(fieldId) else filters[fieldId] = updated
        _uiState.value = _uiState.value.copy(fieldFilters = filters)
    }

    /** Drops every selected value for [fieldId], leaving the field unconstrained. */
    fun clearFieldFilter(fieldId: String) {
        _uiState.value = _uiState.value.copy(
            fieldFilters = _uiState.value.fieldFilters - fieldId,
        )
    }

    /**
     * Resets filters and the active view's sort override; the search query
     * belongs to the top bar and stays.
     */
    fun clearFilters() {
        val viewId = _uiState.value.activeViewId
        _uiState.value = _uiState.value.copy(
            watchedOnly = false,
            fieldFilters = emptyMap(),
            sortByView = if (viewId == null) {
                _uiState.value.sortByView
            } else {
                _uiState.value.sortByView - viewId
            },
            sortDirByView = if (viewId == null) {
                _uiState.value.sortDirByView
            } else {
                _uiState.value.sortDirByView - viewId
            },
        )
    }

    /** True when anything beyond the view's own definition narrows the list. */
    fun hasActiveFilters(): Boolean {
        val state = _uiState.value
        return state.watchedOnly ||
            state.fieldFilters.isNotEmpty() ||
            state.searchQuery.isNotBlank()
    }

    /** True when the user overrode the active view's stored sort. */
    fun hasSortOverride(): Boolean {
        val viewId = _uiState.value.activeViewId ?: return false
        return viewId in _uiState.value.sortByView || viewId in _uiState.value.sortDirByView
    }

    /**
     * Filterable fields with their option sets: enumerated options, or the
     * project's people for a user field.
     */
    fun getFilterableFields(): List<Pair<ProjectField, List<FieldOption>>> {
        val details = _uiState.value.projectDetails ?: return emptyList()
        val view = getActiveView()
        val classIds = if (view != null && view.classes.isNotEmpty()) {
            view.classes
        } else {
            details.classes.map { projectClass -> projectClass.id }
        }
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Pair<ProjectField, List<FieldOption>>>()
        for (classId in classIds) {
            val fields = details.fields[classId] ?: continue
            for (field in fields) {
                if (!field.isFilterable) continue
                if (!seen.add(field.id)) continue
                val options = if (field.fieldtype == "user") {
                    _uiState.value.people.map { person ->
                        FieldOption(id = person.id, name = person.name)
                    }
                } else {
                    getAllOptionsForField(field.id)
                }
                if (options.isEmpty()) continue
                result += field to options
            }
        }
        return result
    }

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

    /**
     * Sort key to highlight in the sheet, or null: the "rank" fallback is not a
     * selection, but a sort stored on the view is.
     */
    fun getSelectedSortField(): String? {
        val viewId = _uiState.value.activeViewId
        val override = viewId?.let { id -> _uiState.value.sortByView[id] }
        if (override != null) return override
        val view = getActiveView()
        if (view != null && view.sort.isNotBlank()) return getActiveSortField()
        return null
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

    fun selectObject(objectId: String?) {
        val closing = objectId == null && _uiState.value.selectedObjectId != null
        _uiState.value = _uiState.value.copy(selectedObjectId = objectId)
        // The detail sheet edits values through its own ViewModel, and local
        // writes don't reach the websocket (projects has no commit hook) — so
        // refresh on sheet close to reflect any field changes (card placement).
        if (closing) refreshObjects()
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
        viewModelScope.launch { refreshObjectsNow() }
    }

    /**
     * Reloads the object list and waits. Only the list endpoint returns field
     * values, so callers that need them must await this rather than fire
     * [refreshObjects].
     */
    private suspend fun refreshObjectsNow() {
        try {
            val objects = repository.getObjects(projectId)
            val watched = repository.getWatched(projectId)
            _uiState.value = _uiState.value.copy(
                objects = objects,
                watched = watched
            )
        } catch (_: Exception) { }
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

    /**
     * Field ids the active view pins; empty when it pins none, and callers then
     * use their own default.
     */
    fun getActiveViewFieldIds(): List<String> {
        val view = getActiveView() ?: return emptyList()
        return view.fields.split(",")
            .map { part -> part.trim() }
            .filter { part -> part.isNotBlank() }
    }

    fun getFieldById(fieldId: String): ProjectField? {
        val details = _uiState.value.projectDetails ?: return null
        for ((_, fields) in details.fields) {
            fields.find { it.id == fieldId }?.let { return it }
        }
        return null
    }

    /** Options a class declares for a field, falling back to any class's set.
     * Two classes may share a field id and hold their own options under it, so
     * an object's value only means anything against its own class's set. */
    fun getOptionsForObject(classId: String, fieldId: String): List<FieldOption> {
        val own = _uiState.value.projectDetails?.options?.get(classId)?.get(fieldId)
        if (!own.isNullOrEmpty()) return own
        return getAllOptionsForField(fieldId)
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
     * Persists a drag-reorder: [order] is every option id in display order.
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
     * Descendant ids of [objectId], for refusing a drop that would reparent a
     * row under itself.
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
        val view = getActiveView()
        var objects = state.objects

        // Filter by view's class filter
        if (view != null && view.classes.isNotEmpty()) {
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

        // Field-value filters: OR within one field, AND across fields
        for ((fieldId, selected) in state.fieldFilters) {
            if (selected.isEmpty()) continue
            objects = objects.filter { obj ->
                obj.stringValue(fieldId) in selected ||
                    obj.listValue(fieldId).any { value -> value in selected }
            }
        }

        // Filter by view's filter field
        if (view != null && view.filter.isNotBlank()) {
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
     * Ids the board may show, or null when unfiltered. Ancestors of a match are
     * included so a matching subtask keeps its parent card on the board.
     */
    fun getVisibleObjectIds(): Set<String>? {
        if (!hasActiveFilters()) return null
        val matched = getFilteredObjects().map { obj -> obj.id }.toSet()
        val byId = _uiState.value.objects.associateBy { obj -> obj.id }
        val result = matched.toMutableSet()
        val walked = mutableSetOf<String>()
        for (id in matched) {
            var parent = byId[id]?.parent.orEmpty()
            while (parent.isNotBlank() && walked.add(parent)) {
                result += parent
                parent = byId[parent]?.parent.orEmpty()
            }
        }
        return result
    }

    /**
     * Sorts by the active field and direction as the web's `sortObjects` does:
     * built-in fields numerically, custom fields as case-insensitive strings.
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
            val sortField = getFieldById(fieldId)
            // Keyed by class as well as field: two classes sharing a field id
            // hold their own options under it, and an object's value only
            // means anything against its own class's set.
            val optionsCache = mutableMapOf<Pair<String, String>, List<FieldOption>>()
            val optionFor = { obj: ProjectObject, id: String, value: String ->
                optionsCache.getOrPut(obj.objectClass to id) {
                    getOptionsForObject(obj.objectClass, id)
                }.find { option -> option.id == value }
            }
            objects.sortedWith(Comparator { a, b ->
                val aId = sortFieldIdFor(a, sortField, fieldId)
                val bId = sortFieldIdFor(b, sortField, fieldId)
                val av = a.stringValue(aId)
                val bv = b.stringValue(bId)
                compareFieldValues(
                    av, bv,
                    sortField?.fieldtype,
                    optionFor(a, aId, av), optionFor(b, bId, bv),
                    multiplier,
                )
            })
        }
    }

    /** The field id to read this object's value under. A sort field chosen on
     * one class may exist under a different id on another, so fall back to the
     * twin that carries the same name. */
    private fun sortFieldIdFor(obj: ProjectObject, sortField: ProjectField?, fallbackId: String): String {
        if (sortField == null) return fallbackId
        if (obj.values.containsKey(sortField.id)) return sortField.id
        val fields = _uiState.value.projectDetails?.fields?.get(obj.objectClass) ?: return sortField.id
        val twin = fields.find { candidate ->
            candidate.name.equals(sortField.name, ignoreCase = true)
        }
        return twin?.id ?: sortField.id
    }

    fun getCardFields(classId: String): List<ProjectField> {
        val details = _uiState.value.projectDetails ?: return emptyList()
        val allFields = details.fields[classId] ?: return emptyList()
        val viewFieldIds = getActiveViewFieldIds().toSet()
        if (viewFieldIds.isNotEmpty()) {
            return allFields.filter { it.id in viewFieldIds }
        }
        return allFields.filter { it.showOnCard }
    }

    private companion object {
        const val MAX_WARM_ROUNDS = 60
    }
}
