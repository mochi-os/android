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
import org.mochios.android.files.MIME_CSV
import org.mochios.android.files.MIME_ZIP
import org.mochios.android.files.PendingExport
import org.mochios.android.files.SavedExport
import org.mochios.android.model.WebSocketEvent
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.crm.lib.ActiveViewStore
import org.mochios.crm.model.CrmClass
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmField
import org.mochios.crm.model.CrmObject
import org.mochios.crm.model.CrmView
import org.mochios.crm.model.FieldOption
import org.mochios.crm.repository.CrmsRepository
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/** Seconds in a day, for turning an ISO date into an epoch-second sort key. */
private const val SECONDS_PER_DAY = 86_400L

data class CrmUiState(
    val crmDetails: CrmDetails? = null,
    val objects: List<CrmObject> = emptyList(),
    val activeViewId: String? = null,
    val searchQuery: String = "",
    val watchedOnly: Boolean = false,
    /** Object ids the local user watches, from the last objects fetch. */
    val watched: List<String> = emptyList(),
    /** Selected option ids per field id. Empty when nothing is filtered. */
    val fieldFilters: Map<String, Set<String>> = emptyMap(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: MochiError? = null,
    val selectedObjectId: String? = null,
    /**
     * Sort override per view id: "rank", "created", "updated" or
     * "field:<fieldId>" (the web sort key scheme). Absent means view.sort, else
     * "rank".
     */
    val sortByView: Map<String, String> = emptyMap(),
    /** Sort direction per view id. "asc" or "desc". */
    val sortDirByView: Map<String, String> = emptyMap(),
    /** CRM members, for resolving user-field display names in list/board views. */
    val people: List<org.mochios.crm.model.Person> = emptyList(),
    /** True while an export is being fetched, before the save dialog opens. */
    val isExporting: Boolean = false,
    /** An export waiting for the user to say where it goes. */
    val pendingExport: PendingExport? = null,
    /** An export that landed, ready to be offered to the share sheet. */
    val savedExport: SavedExport? = null,
    val exportFailed: Boolean = false,
)

@HiltViewModel
class CrmViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CrmsRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
    private val activeViewStore: ActiveViewStore
) : ViewModel() {

    val crmId: String = savedStateHandle.get<String>("crmId") ?: ""

    private val _uiState = MutableStateFlow(CrmUiState())
    val uiState: StateFlow<CrmUiState> = _uiState.asStateFlow()

    /** Emits the CRM's share link once fetched, for the screen to share. */
    private val _shareLink = MutableSharedFlow<String>()
    val shareLink: SharedFlow<String> = _shareLink.asSharedFlow()

    private var wsSubscriptionId: String? = null

    init {
        loadCrm()
        subscribeWebSocket()
    }

    override fun onCleared() {
        super.onCleared()
        wsSubscriptionId?.let { webSocket.unsubscribe(it) }
    }

    fun loadCrm() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)

            // Show cached data immediately if available
            val cachedDetails = repository.getCachedCrmInfo(crmId)
            val cachedObjects = repository.getCachedObjects(crmId)
            if (cachedDetails != null && cachedObjects != null) {
                val activeViewId = _uiState.value.activeViewId
                    ?: rememberedViewId(cachedDetails.views)
                    ?: defaultViewId(cachedDetails, cachedObjects)
                _uiState.value = _uiState.value.copy(
                    crmDetails = cachedDetails,
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
                val details = repository.getCrmInfo(crmId)
                val objects = repository.getObjects(crmId)
                val watched = repository.getWatched(crmId)
                val people = runCatching { repository.getPeople(crmId) }
                    .getOrDefault(_uiState.value.people)
                val activeViewId = _uiState.value.activeViewId
                    ?: rememberedViewId(details.views)
                    ?: defaultViewId(details, objects)
                _uiState.value = _uiState.value.copy(
                    crmDetails = details,
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
            val details = repository.getCrmInfo(crmId)
            val objects = repository.getObjects(crmId)
            val watched = repository.getWatched(crmId)
            val people = runCatching { repository.getPeople(crmId) }
                .getOrDefault(_uiState.value.people)
            _uiState.value = _uiState.value.copy(
                crmDetails = details,
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
                val details = repository.getCrmInfo(crmId)
                val objects = repository.getObjects(crmId)
                val watched = repository.getWatched(crmId)
                val people = runCatching { repository.getPeople(crmId) }
                    .getOrDefault(_uiState.value.people)
                _uiState.value = _uiState.value.copy(
                    crmDetails = details,
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
        activeViewStore.set(crmId, viewId)
    }

    /**
     * The view the user last opened in this CRM, if it still exists — a view
     * deleted since then falls back to the CRM's own first view.
     */
    private fun rememberedViewId(views: List<CrmView>): String? {
        val remembered = activeViewStore.get(crmId) ?: return null
        return remembered.takeIf { id -> views.any { view -> view.id == id } }
    }

    /**
     * Default view for a CRM the user has never picked one in: the first whose
     * classes include one that can be created now (a view naming no classes
     * qualifies), else the CRM's first view.
     */
    private fun defaultViewId(details: CrmDetails, objects: List<CrmObject>): String? {
        val creatable = details.classes
            .filter { cls ->
                val parents = (details.hierarchy[cls.id] ?: emptyList())
                    .filter { id -> id.isNotBlank() }
                parents.isEmpty() || objects.any { obj -> obj.objectClass in parents }
            }
            .map { cls -> cls.id }
            .toSet()
        val opening = details.views.firstOrNull { view ->
            view.classes.isEmpty() || view.classes.any { id -> id in creatable }
        }
        return (opening ?: details.views.firstOrNull())?.id
    }

    /** Fetch the CRM's share link and emit it for the screen to share. */
    fun shareCrm() {
        viewModelScope.launch {
            try {
                val link = repository.getShareLink(crmId)
                if (link.isNotBlank()) {
                    _shareLink.emit(link)
                }
            } catch (_: Exception) {
                // Best-effort: a failed share link simply does nothing.
            }
        }
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
     * Filterable fields of the active view's classes with a fixed value set:
     * enumerated options, or the CRM's people for user fields.
     */
    fun getFilterableFields(): List<Pair<CrmField, List<FieldOption>>> {
        val details = _uiState.value.crmDetails ?: return emptyList()
        val view = getActiveView()
        val classIds = if (view != null && view.classes.isNotEmpty()) {
            view.classes
        } else {
            details.classes.map { crmClass -> crmClass.id }
        }
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Pair<CrmField, List<FieldOption>>>()
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

    /**
     * Active sort field ("rank" | "created" | "updated" | "field:<fieldId>");
     * defaults to view.sort, else "rank".
     */
    fun getActiveSortField(): String {
        val viewId = _uiState.value.activeViewId ?: return "rank"
        val override = _uiState.value.sortByView[viewId]
        if (override != null) return override
        val view = getActiveView() ?: return "rank"
        if (view.sort.isNotBlank()) {
            // view.sort stores a bare fieldId; crm it onto the field: prefix
            // unless it matches a built-in.
            return when (view.sort) {
                "rank", "created", "updated" -> view.sort
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

    /**
     * Sortable fields across all classes, for the sort sheet. Iterates the info
     * response's `fields` map, not `classes`: the two arrive in different
     * orders.
     */
    fun getSortFieldOptions(): List<Pair<String, String>> {
        val details = _uiState.value.crmDetails ?: return emptyList()
        val seenIds = mutableSetOf<String>()
        val seenNames = mutableSetOf<String>()
        val result = mutableListOf<Pair<String, String>>()
        for ((_, fields) in details.fields) {
            for (field in fields) {
                if (!field.isSortable) continue
                if (!seenIds.add(field.id)) continue
                // Classes routinely repeat a field name ("Name", "Due"). One
                // chip covers all of them — the comparator resolves each
                // object's own same-named field, so the twins sort together
                // instead of crowding the sheet with identical chips.
                if (!seenNames.add(field.name.lowercase())) continue
                result += "field:${field.id}" to field.name
            }
        }
        return result
    }

    fun selectObject(objectId: String?) {
        val closing = objectId == null && _uiState.value.selectedObjectId != null
        _uiState.value = _uiState.value.copy(selectedObjectId = objectId)
        // The detail sheet edits values through its own ViewModel, and local
        // writes don't reach the websocket (crm has no commit hook) — so
        // refresh on sheet close to reflect any field changes (card placement).
        if (closing) refreshObjects()
    }

    fun deleteObject(objectId: String) {
        viewModelScope.launch {
            try {
                repository.deleteObject(crmId, objectId)
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
                repository.moveObject(crmId, objectId, field, value, rank, rowField, rowValue, scopeParent, promote)
                refreshObjects()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    private fun refreshObjects() {
        viewModelScope.launch {
            try {
                val objects = repository.getObjects(crmId)
                val watched = repository.getWatched(crmId)
                _uiState.value = _uiState.value.copy(objects = objects, watched = watched)
            } catch (_: Exception) { }
        }
    }

    private fun subscribeWebSocket() {
        viewModelScope.launch {
            val serverUrl = sessionManager.serverUrl.let {
                sessionManager.getServerUrlBlocking()
            }
            wsSubscriptionId = webSocket.subscribe(serverUrl, crmId) { event ->
                handleWebSocketEvent(event)
            }
        }
    }

    private fun handleWebSocketEvent(event: WebSocketEvent) {
        // Server event types are slash-namespaced (object/update, values/update,
        // ...) — see the mochi.websocket.write calls in crm.star. The previous
        // underscore names here never matched anything, so the board ignored
        // every push and cards only moved on a manual reload.
        val type = event.type ?: return
        when {
            type in setOf(
                "object/create", "object/update", "object/delete",
                "object/ranks", "values/update",
            ) -> refreshObjects()
            // Structure changes: board columns derive from field options, plus
            // views/classes/hierarchy — reload the whole crm.
            type == "crm/update" || type == "crm/resynced" ||
                type == "hierarchy/set" ||
                type.substringBefore("/") in setOf("class", "field", "option", "view") ->
                loadCrm()
        }
    }

    // ---- Helpers for views ----

    fun getActiveView(): CrmView? {
        val details = _uiState.value.crmDetails ?: return null
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

    fun getFieldById(fieldId: String): CrmField? {
        val details = _uiState.value.crmDetails ?: return null
        for ((_, fields) in details.fields) {
            fields.find { it.id == fieldId }?.let { return it }
        }
        return null
    }

    fun getOptionsForField(classId: String, fieldId: String): List<FieldOption> {
        val details = _uiState.value.crmDetails ?: return emptyList()
        return details.options[classId]?.get(fieldId) ?: emptyList()
    }

    /**
     * Options for [fieldId] across [classIds] (empty = every class), in class
     * order, deduplicated by id. Options are per class, and classes share field
     * ids.
     */
    fun getOptionsForClasses(fieldId: String, classIds: List<String>): List<FieldOption> {
        val details = _uiState.value.crmDetails ?: return emptyList()
        val ids = classIds.ifEmpty { details.classes.map { crmClass -> crmClass.id } }
        val seen = mutableSetOf<String>()
        val result = mutableListOf<FieldOption>()
        for (classId in ids) {
            for (option in details.options[classId]?.get(fieldId).orEmpty()) {
                if (seen.add(option.id)) {
                    result += option
                }
            }
        }
        return result
    }

    /**
     * Options for [fieldId] on an object of [classId]: its own class's set,
     * since the stored value is an id from there; every class's when it defines
     * none.
     */
    fun getOptionsForObject(classId: String, fieldId: String): List<FieldOption> {
        val own = getOptionsForField(classId, fieldId)
        if (own.isNotEmpty()) return own
        return getAllOptionsForField(fieldId)
    }

    /** Every option [fieldId] can take, across all of the CRM's classes. */
    fun getAllOptionsForField(fieldId: String): List<FieldOption> =
        getOptionsForClasses(fieldId, emptyList())

    fun reparentObject(objectId: String, newParentId: String) {
        viewModelScope.launch {
            try {
                repository.updateObject(crmId, objectId, newParentId)
                refreshObjects()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun getClassById(classId: String): CrmClass? {
        return _uiState.value.crmDetails?.classes?.find { it.id == classId }
    }

    /** Find the classId that owns a given fieldId. */
    private fun findClassForField(fieldId: String): String? {
        val details = _uiState.value.crmDetails ?: return null
        for ((classId, fields) in details.fields) {
            if (fields.any { it.id == fieldId }) return classId
        }
        return null
    }

    fun addColumnOption(fieldId: String, name: String, colour: String? = null) {
        val classId = findClassForField(fieldId) ?: return
        viewModelScope.launch {
            try {
                repository.createOption(crmId, classId, fieldId, name, colour)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun renameColumnOption(fieldId: String, optionId: String, name: String) {
        val classId = findClassForField(fieldId) ?: return
        viewModelScope.launch {
            try {
                repository.updateOption(crmId, classId, fieldId, optionId, name, null, null)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteColumnOption(fieldId: String, optionId: String) {
        val classId = findClassForField(fieldId) ?: return
        viewModelScope.launch {
            try {
                repository.deleteOption(crmId, classId, fieldId, optionId)
                loadCrm()
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
                repository.reorderOptions(crmId, classId, fieldId, order.joinToString(","))
                loadCrm()
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

    /**
     * Ids the board may show, or null when no filter is active. Ancestors of a
     * match are included so a matching child keeps its parent card on the
     * board.
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

    fun getFilteredObjects(): List<CrmObject> {
        val state = _uiState.value
        // Null-guard each view-derived filter rather than returning early: the
        // search query and the watched toggle belong to the user, not the view,
        // and returning early left both unapplied whenever no view was active.
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
            objects = objects.filter { obj -> watchedSet.contains(obj.id) }
        }

        return sortObjects(objects)
    }

    /**
     * Sorts by the active sort field and direction, mirroring web
     * board-container.tsx sortObjects: compare by field type, else
     * case-insensitive text.
     */
    fun sortObjects(objects: List<CrmObject>): List<CrmObject> {
        val field = getActiveSortField()
        val multiplier = if (getActiveSortDirection() == "desc") -1 else 1
        // rank is a fractional-index text key whose ASCII (binary) order is the
        // intended order (#53), so compare the keys as strings — matching the
        // web's rankCompare. The remaining built-ins are still numeric.
        if (field == "rank") {
            return objects.sortedWith(Comparator { a, b -> a.rank.compareTo(b.rank) * multiplier })
        }
        val numericFields = setOf("created", "updated")
        return if (field in numericFields) {
            objects.sortedWith(Comparator { a, b ->
                val av = when (field) {
                    "created" -> a.created
                    "updated" -> a.updated
                    else -> 0L
                }
                val bv = when (field) {
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
            val optionFor = { obj: CrmObject, id: String, value: String ->
                optionsCache.getOrPut(obj.objectClass to id) {
                    getOptionsForObject(obj.objectClass, id)
                }.find { option -> option.id == value }
            }
            objects.sortedWith(Comparator { a, b ->
                val aId = sortFieldIdFor(a, sortField, fieldId)
                val bId = sortFieldIdFor(b, sortField, fieldId)
                val av = a.stringValue(aId)
                val bv = b.stringValue(bId)
                // Empty values sink to the bottom whichever way the sort runs.
                // Flipping to descending is meant to bring the far end of the
                // data into view, not a wall of objects that never filled the
                // field in.
                if (av.isBlank() || bv.isBlank()) {
                    return@Comparator when {
                        av.isBlank() && bv.isBlank() -> 0
                        av.isBlank() -> 1
                        else -> -1
                    }
                }
                val comparison = when (sortField?.fieldtype) {
                    "number" -> {
                        val an = av.toDoubleOrNull()
                        val bn = bv.toDoubleOrNull()
                        if (an != null && bn != null) {
                            an.compareTo(bn)
                        } else {
                            av.compareTo(bv, ignoreCase = true)
                        }
                    }
                    "date" -> {
                        val ad = dateSortKey(av)
                        val bd = dateSortKey(bv)
                        if (ad != null && bd != null) {
                            ad.compareTo(bd)
                        } else {
                            av.compareTo(bv, ignoreCase = true)
                        }
                    }
                    // Enumerated values are stored as opaque option ids, so
                    // compare the options themselves: their rank is the order
                    // the designer gave them (the board's column order), which
                    // beats alphabetising a pipeline.
                    "enumerated" -> {
                        val ao = optionFor(a, aId, av)
                        val bo = optionFor(b, bId, bv)
                        if (ao != null && bo != null) {
                            ao.rank.compareTo(bo.rank)
                        } else {
                            (ao?.name ?: av).compareTo(bo?.name ?: bv, ignoreCase = true)
                        }
                    }
                    else -> av.compareTo(bv, ignoreCase = true)
                }
                comparison * multiplier
            })
        }
    }

    /**
     * Field id to read on [obj] for [sortField]: the object's own same-named
     * field when its class lacks that id, since the sheet offers one chip per
     * field name. [fallbackId] applies when the sort key names no known field.
     */
    private fun sortFieldIdFor(obj: CrmObject, sortField: CrmField?, fallbackId: String): String {
        if (sortField == null) return fallbackId
        if (obj.values.containsKey(sortField.id)) return sortField.id
        val fields = _uiState.value.crmDetails?.fields?.get(obj.objectClass) ?: return sortField.id
        val twin = fields.find { candidate ->
            candidate.name.equals(sortField.name, ignoreCase = true)
        }
        return twin?.id ?: sortField.id
    }

    /**
     * Epoch seconds for a date field value, which the server stores either as
     * epoch seconds or as an ISO date. Null when it is neither.
     */
    private fun dateSortKey(value: String): Long? {
        value.toLongOrNull()?.let { seconds -> return seconds }
        return try {
            LocalDate.parse(value).toEpochDay() * SECONDS_PER_DAY
        } catch (_: Exception) {
            null
        }
    }

    // ---- Export ----

    /**
     * Handle for the data endpoints: canonical id, else fingerprint, else the
     * route's id.
     */
    private fun entityId(): String {
        val crm = _uiState.value.crmDetails?.crm ?: return crmId
        return crm.id.ifEmpty { crm.fingerprint.ifEmpty { crmId } }
    }

    /**
     * Warms the CRM's attachments (the export only includes what has been
     * warmed), then asks the screen where to save. [writeExportTo] fetches the
     * zip straight into the chosen file.
     */
    fun exportCrm() {
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
                val name = _uiState.value.crmDetails?.crm?.name
                _uiState.value = _uiState.value.copy(
                    pendingExport = PendingExport(
                        suggestedName = repository.exportFileName(name, "crm-backup", "zip"),
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
     * Builds a CSV of the active view's objects as shown and parks it for the
     * save dialog; local only, no server call.
     */
    fun exportCsv() {
        val csv = buildCsv()
        val name = _uiState.value.crmDetails?.crm?.name
        _uiState.value = _uiState.value.copy(
            pendingExport = PendingExport(
                suggestedName = repository.exportDisplayName(name, "csv"),
                mimeType = MIME_CSV,
                content = csv,
            )
        )
    }

    /**
     * CSV in the web export's shape: `ID`, `Class`, `Parent`, then one column
     * per field name across the CRM's classes. No title column - a class titles
     * itself with one of its fields. Rows are the active view's filtered
     * objects, values as displayed.
     */
    private fun buildCsv(): String {
        val details = _uiState.value.crmDetails
        val objects = getFilteredObjects()
        // Columns are field names, not ids: classes define their own fields and
        // web gives same-named ones one column. Order by design rank, not
        // server order.
        val columns = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        val orderedClasses = details?.classes.orEmpty().sortedBy { crmClass -> crmClass.rank }
        for (crmClass in orderedClasses) {
            val classFields = details?.fields?.get(crmClass.id).orEmpty()
                .sortedBy { field -> field.rank }
            for (field in classFields) {
                if (seen.add(field.name)) columns += field.name
            }
        }
        val rows = mutableListOf<List<String>>()
        rows += listOf("ID", "Class", "Parent") + columns
        for (obj in objects) {
            // Resolve each column against this object's own class, so a shared
            // label still reads that class's field. A class without the field
            // leaves the cell empty.
            val byName = details?.fields?.get(obj.objectClass)
                ?.associateBy { field -> field.name }
                .orEmpty()
            rows += listOf(
                obj.id,
                getClassById(obj.objectClass)?.name.orEmpty(),
                obj.parent,
            ) + columns.map { column ->
                byName[column]?.let { field -> csvValue(obj, field) }.orEmpty()
            }
        }
        return rows.joinToString("\n") { row ->
            row.joinToString(",") { cell -> csvCell(cell) }
        }
    }

    /** One cell's text: option and person ids resolved to their names. */
    private fun csvValue(obj: CrmObject, field: CrmField): String {
        val values = obj.listValue(field.id).ifEmpty {
            obj.stringValue(field.id).takeIf { value -> value.isNotBlank() }?.let { value ->
                listOf(value)
            }.orEmpty()
        }
        if (values.isEmpty()) return ""
        return values.joinToString(", ") { value ->
            when (field.fieldtype) {
                "enumerated" -> getOptionsForObject(obj.objectClass, field.id)
                    .find { option -> option.id == value }?.name ?: value
                "user" -> _uiState.value.people
                    .find { person -> person.id == value }?.name ?: value
                else -> value
            }
        }
    }

    /** Quotes a cell when it holds a comma, a quote or a newline. */
    private fun csvCell(value: String): String {
        if (value.none { char -> char == ',' || char == '"' || char == '\n' || char == '\r' }) {
            return value
        }
        return "\"${value.replace("\"", "\"\"")}\""
    }

    /**
     * Puts the pending export in the destination the user picked: writes the
     * CSV the app built, or downloads the server's backup zip into it.
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

    fun getCardFields(classId: String): List<CrmField> {
        val details = _uiState.value.crmDetails ?: return emptyList()
        val allFields = details.fields[classId] ?: return emptyList()
        val viewFieldIds = getActiveViewFieldIds().toSet()
        if (viewFieldIds.isNotEmpty()) {
            return allFields.filter { it.id in viewFieldIds }
        }
        return allFields.filter { it.showOnCard }
    }

    private companion object {
        /** Cap on export warm-up rounds, so a stuck stage can't spin forever. */
        const val MAX_WARM_ROUNDS = 60
    }
}
