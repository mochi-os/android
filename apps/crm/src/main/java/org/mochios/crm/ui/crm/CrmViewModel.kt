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
     * Sort field per view id. Field is one of "rank", "created",
     * "updated", or "field:<fieldId>" matching the web sort key scheme.
     * Null entry => fall back to view.sort or "rank".
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
     * The view to open in a CRM the user has never picked a view in: the first
     * one showing a class they can actually create. A design that lists its
     * child classes first — Contacts ahead of Companies — otherwise opens an
     * empty list whose "+" leads straight to "create the parent first", with
     * the view that does work a menu away.
     *
     * A view naming no classes shows all of them, so it always qualifies.
     * Falls back to the CRM's first view when nothing can be created yet.
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
     * Resets every filter axis and the sort override for the active view, so
     * the list falls back to what the view itself defines. The search query is
     * owned by the top bar and is left alone.
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
     * Fields the sheet can filter on: fields flagged "filter" on the active
     * view's classes that carry a fixed value set — enumerated options, or the
     * CRM's people for a user field. Free-text and date fields are left out;
     * search covers the former and sort covers the latter.
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
     * Active sort field for the current view. Mirrors the web sort key scheme:
     * "rank" | "created" | "updated" | "field:<fieldId>".
     * Defaults to view.sort (when set) or "rank".
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
     * Sort key to show as chosen in the sheet, or null when nothing has been
     * chosen. The list still falls back to "rank" in that case, but the fallback
     * isn't a selection — showing Manual highlighted would claim the user picked
     * it. A sort stored on the view itself does count as chosen.
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
     * Sort field options available for the current view: every field flagged
     * "sort", across all of the CRM's classes, matching the web sort menu.
     *
     * Order comes from the info response's own `fields` map — its keys, then
     * each class's field list — not from the `classes` array, which arrives in
     * a different order and put the wrong class's fields first.
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
     * Field ids the active view pins. Empty when there is no active view or it
     * pins none — callers then fall back to their own default (card flags for
     * cards, every field of the class for the object detail form).
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
     * Every option [fieldId] can take across [classIds], in class order and
     * deduplicated by option id.
     *
     * Options belong to a class, not to a field id, and classes share field
     * ids — a board that mixes classes needs a column per distinct option,
     * not just the ones whichever class came first in the response defines.
     *
     * @param classIds the classes to draw from; empty means every class the
     *   CRM defines.
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
     * Options for [fieldId] as an object of [classId] can take them. An
     * object's stored value is an id from its own class's set, so resolving
     * it against another class's set is what renders a raw id in place of the
     * option's name and colour.
     *
     * Falls back to every class's options when the class defines none of its
     * own, which keeps a value set before the class was reshaped readable.
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
     * Persist a new column-option ordering after a drag-reorder. [order] is
     * the full list of option ids in display order — the server replaces the
     * stored ranks accordingly.
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

    /**
     * Ids the board may render, or null when nothing narrows the set — the
     * board still receives every object so hierarchy, column grouping and
     * drop ranks stay computed against the real list, and only hides what
     * falls outside this set.
     *
     * Ancestors of a match are included: a board card is a container for its
     * children, so a matching child keeps its parent card on the board
     * instead of dropping the whole branch out of sight.
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
     * Sort objects by the active sort field/direction. Mirrors the web logic
     * in `web/src/features/board/components/board-container.tsx::sortObjects`
     * — built-in numeric fields compare numerically. Custom fields compare by
     * their type: numbers as numbers, dates as dates, enumerated values by
     * option rank, everything else as case-insensitive text.
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
     * Field id to read on [obj] when sorting by [sortField]. The sheet offers
     * one chip per field name, so an object of another class carries its own
     * same-named field — fall back to that twin rather than reading a blank
     * and dumping every object of that class at the end of the list.
     *
     * @param fallbackId Used when the sort key names no field the CRM knows.
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
     * The handle the data endpoints want: the CRM's canonical id, falling back
     * to its fingerprint and then to whatever the screen was routed with.
     * Mirrors `CrmSettingsViewModel.entityId()`.
     */
    private fun entityId(): String {
        val crm = _uiState.value.crmDetails?.crm ?: return crmId
        return crm.id.ifEmpty { crm.fingerprint.ifEmpty { crmId } }
    }

    /**
     * Stages the CRM's attachments, then asks the screen for somewhere to put
     * the backup. The export endpoint only includes what has been warmed, so
     * this can take a while on a CRM with many files.
     *
     * Nothing is downloaded here. The server builds the zip and it runs to
     * megabytes, so it is fetched straight into the file the user picks — see
     * [writeExportTo].
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
     * Builds a spreadsheet of the objects the active view is showing — the
     * same ones, in the same order, narrowed by the same filters — and parks
     * it for the save dialog. Local work only: everything it needs is already
     * loaded, so unlike [exportCrm] it never touches the server.
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
     * A CSV in the shape web exports: `ID`, `Class` and `Parent` in front,
     * then one column for every field of every class in the CRM, in class
     * order and deduped by field id so a field two classes share is written
     * once.
     *
     * There is no separate title column. A class titles itself with one of its
     * own fields, so a title column would repeat that field's column on every
     * row — which is what the earlier shape did.
     *
     * Columns cover the whole CRM, but rows are the active view's objects, so
     * what is filtered out on screen stays out of the file. Values are written
     * as they read on screen — an option's name rather than its id, a person's
     * name rather than their entity id.
     */
    private fun buildCsv(): String {
        val details = _uiState.value.crmDetails
        val objects = getFilteredObjects()
        // Columns are field names, not field ids. Each class defines its own
        // fields, so the "Owner" on a task and the "Owner" on a deal are two
        // ids wearing one label — web gives them a single column, and a
        // spreadsheet reader expects that too.
        // Both orders come from the design, by rank — the same order the
        // classes and fields are listed in everywhere else in the app, and
        // the one web lays the columns out in. Server order is not it.
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
