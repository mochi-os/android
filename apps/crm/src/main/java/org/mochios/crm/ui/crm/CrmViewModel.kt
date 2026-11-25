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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import org.mochios.android.files.MIME_CSV
import org.mochios.android.files.MIME_ZIP
import org.mochios.android.files.PendingExport
import org.mochios.android.files.SavedExport
import org.mochios.android.model.WebSocketEvent
import org.mochios.android.model.canCreate
import org.mochios.android.model.canDesign
import org.mochios.android.model.canWrite
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.REOPEN
import org.mochios.android.share.Shared
import org.mochios.crm.ui.board.boardClass
import org.mochios.crm.ui.board.boardOptions
import org.mochios.crm.ui.board.cascaded
import org.mochios.crm.ui.board.parentValues
import org.mochios.crm.ui.board.topLevelObjects
import org.mochios.crm.api.MovedRank
import org.mochios.crm.lib.ActiveViewStore
import org.mochios.crm.lib.CollapsedColumnsStore
import org.mochios.crm.model.CrmClass
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmField
import org.mochios.crm.model.CrmObject
import org.mochios.crm.model.CrmView
import org.mochios.crm.model.FieldOption
import org.mochios.crm.model.Person
import org.mochios.crm.repository.CrmsRepository
import org.mochios.crm.util.csvCell
import org.mochios.crm.util.textCompare
import java.time.LocalDate
import javax.inject.Inject

/** Seconds in a day, for turning an ISO date into an epoch-second sort key. */
private const val SECONDS_PER_DAY = 86_400L

/**
 * How a drop on the board or the tree goes beside the object it lands on, by
 * the sort the view is drawn in: by key, by key upside down under a
 * descending manual sort, or not at all under any other sort, which draws
 * the objects by a field and never shows their keys.
 */
enum class Placement { Ascending, Descending, None }

/**
 * The object [drawn], the ids a view shows in one column, lane or parent in
 * the order it draws them, has on the far side of a drop on [target]: the
 * next one down for a drop on its lower half ([below]), else the next one up,
 * passing over [moved], the object dropped. Null at either end, or when
 * [target] is not drawn there.
 */
fun drawnBeside(drawn: List<String>, target: String, below: Boolean, moved: String): String? {
    val others = drawn.filter { id -> id != moved }
    val index = others.indexOf(target)
    if (index < 0) return null
    return others.getOrNull(if (below) index + 1 else index - 1)
}

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
    /** Attachments the export could not fetch, which the saved file lacks. */
    val exportMissing: Int = 0,
)

@HiltViewModel
class CrmViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: CrmsRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
    private val activeViewStore: ActiveViewStore,
    private val collapsedColumnsStore: CollapsedColumnsStore,
) : ViewModel() {

    val crmId: String = savedStateHandle.get<String>("crmId") ?: ""

    private val _uiState = MutableStateFlow(CrmUiState())
    val uiState: StateFlow<CrmUiState> = _uiState.asStateFlow()

    /** Collapsed board columns, by column field. */
    private val _collapsedColumns = MutableStateFlow(collapsedColumnsStore.get(crmId))
    val collapsedColumns: StateFlow<Map<String, Set<String>>> = _collapsedColumns.asStateFlow()

    private val _shares = MutableSharedFlow<Shared>(extraBufferCapacity = 1)

    /** The CRM, readied for the system share sheet. */
    val shares: SharedFlow<Shared> = _shares.asSharedFlow()

    // A mutation that fails after the CRM has loaded: uiState.error only
    // renders while nothing is loaded, so these are toasted by the screen.
    private val _actionFailed = MutableSharedFlow<MochiError>(extraBufferCapacity = 4)
    val actionFailed: SharedFlow<MochiError> = _actionFailed.asSharedFlow()

    private var wsSubscriptionId: String? = null

    /** The pending or in-flight objects refresh; cancelled when a newer one starts. */
    private var objectsJob: Job? = null

    /** The pending or in-flight crm refresh; cancelled when a newer one starts. */
    private var crmJob: Job? = null

    /** Set while [objectsJob] waits out its debounce, before its fetch starts. */
    @Volatile
    private var objectsPending = false

    /** Set while [crmJob] waits out its debounce, before its fetch starts. */
    @Volatile
    private var crmPending = false

    /** Set while another screen covers the board: frames then only mark it stale. */
    @Volatile
    private var hidden = false

    /** Items changed while [hidden], to be read on coming back. */
    @Volatile
    private var objectsStale = false

    /** The crm's structure changed while [hidden], to be read on coming back. */
    @Volatile
    private var crmStale = false

    // Above init: a CRM held from before lands in loadCrm while the view
    // model is still being made.

    /**
     * The objects as the server last listed them, with what each move it has
     * answered since wrote; the list shown is this with [pending] drawn over
     * it, so a reload while moves wait keeps them where they were dropped.
     */
    private var settled: List<CrmObject> = emptyList()

    /** The moves drawn and not yet answered, in the order they were made. */
    private val pending = mutableListOf<Move>()

    /** How many reads of the objects have begun. */
    private var fetches = 0

    /**
     * How many reads of the objects had begun when the server last answered
     * a move or a reparent: a list asked for before then may have been read
     * without it, and is dropped (see [land]).
     */
    private var answered = 0

    init {
        loadCrm()
        subscribeWebSocket()
    }

    override fun onCleared() {
        super.onCleared()
        wsSubscriptionId?.let { webSocket.unsubscribe(it) }
        // Nothing follows the frames for the held list now.
        repository.forgetObjects(crmId)
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
                    objects = land(cachedObjects),
                    activeViewId = activeViewId,
                    isLoading = false
                )
                // Refresh in background
                refreshSilently()
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val (details, objects, people, begun) = fetch()
                val watched = repository.getWatched(crmId)
                val activeViewId = _uiState.value.activeViewId
                    ?: rememberedViewId(details.views)
                    ?: defaultViewId(details, objects)
                _uiState.value = _uiState.value.copy(
                    crmDetails = details,
                    objects = land(objects, begun),
                    watched = watched,
                    people = people ?: _uiState.value.people,
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

    /**
     * The CRM's information, its items and its people as [fetch] reads them,
     * with the count of reads of the items begun when this one began.
     */
    private data class Fetched(
        val details: CrmDetails,
        val objects: List<CrmObject>,
        val people: List<Person>?,
        val begun: Int,
    )

    /**
     * The CRM's information, its items and its people, asked for at once. The
     * information is awaited first: a CRM gone or closed to the user reports
     * that, whatever the items' answer, which is then discarded. The people
     * are best-effort, null when they cannot be read.
     */
    private suspend fun fetch(): Fetched = coroutineScope {
        val begun = ++fetches
        val details = async { repository.getCrmInfo(crmId) }
        val objects = async { attempt { repository.getObjects(crmId) } }
        val people = async { attempt { repository.getPeople(crmId) } }
        Fetched(details.await(), objects.await().getOrThrow(), people.await().getOrNull(), begun)
    }

    /**
     * [refreshSilently] in the background: structure frames and our own column
     * edits. Cancels the pending refresh and waits [REFRESH_DEBOUNCE] first,
     * so an edit and its echo frame make one round of calls.
     */
    private fun refreshCrm() {
        crmJob?.cancel()
        if (objectsPending) {
            objectsJob?.cancel()
            objectsPending = false
        }
        crmPending = true
        crmJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            crmPending = false
            refreshSilently()
        }
    }

    /**
     * Reads the whole crm again on the way back from a screen that wrote to
     * it, through the same debounced job as the frames, so the two make one
     * round of calls.
     */
    fun refreshOnReturn() {
        refreshCrm()
    }

    /**
     * The board's ON_PAUSE: a create, copy or design screen opened over it, or
     * the app left. Frames from now on only mark the board stale, and a
     * refresh still waiting out its debounce is put off until [onScreenResumed].
     */
    fun onScreenPaused() {
        hidden = true
        if (crmPending) {
            crmJob?.cancel()
            crmPending = false
            crmStale = true
            repository.forgetObjects(crmId)
        }
        if (objectsPending) {
            objectsJob?.cancel()
            objectsPending = false
            objectsStale = true
            repository.forgetObjects(crmId)
        }
    }

    /**
     * The board's ON_RESUME. Reads once what changed while it was covered; the
     * first resume, as the board opens, reads nothing.
     */
    fun onScreenResumed() {
        hidden = false
        val crm = crmStale
        val objects = objectsStale
        crmStale = false
        objectsStale = false
        if (crm) {
            refreshCrm()
        } else if (objects) {
            refreshObjects()
        }
    }

    private suspend fun refreshSilently() {
        try {
            val (details, objects, people, begun) = fetch()
            val watched = repository.getWatched(crmId)
            _uiState.value = _uiState.value.copy(
                crmDetails = details,
                objects = land(objects, begun),
                watched = watched,
                people = people ?: _uiState.value.people
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Silent — cached data is still showing
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val (details, objects, people, begun) = fetch()
                val watched = repository.getWatched(crmId)
                _uiState.value = _uiState.value.copy(
                    crmDetails = details,
                    objects = land(objects, begun),
                    watched = watched,
                    people = people ?: _uiState.value.people,
                    isRefreshing = false,
                    error = null
                )
            } catch (e: Exception) {
                // Over a loaded CRM the error state is not drawn, so say it.
                if (_uiState.value.crmDetails == null) {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = e.toMochiError()
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                    _actionFailed.tryEmit(e.toMochiError())
                }
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

    /**
     * Share the CRM: by its name and address, and to other apps by the link
     * another server joins it by, which the server assembles.
     */
    fun share() {
        viewModelScope.launch {
            try {
                // share refuses a fingerprint; the route id may be one.
                val link = repository.getShareLink(entityId())
                val crm = _uiState.value.crmDetails?.crm
                val address = sessionManager.getServerUrlBlocking().trimEnd('/') + "/crm/" +
                    (crm?.fingerprint?.takeIf { it.isNotEmpty() } ?: crmId)
                _shares.emit(Shared(text = crm?.name.orEmpty(), url = address, link = link))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
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

    /**
     * Opens the sheet of [objectId], or closes it for null. Closing reads the
     * items again unless [refresh] is false, for a sheet that gives way to a
     * screen whose return reads the whole crm anyway.
     */
    fun selectObject(objectId: String?, refresh: Boolean = true) {
        val closing = objectId == null && _uiState.value.selectedObjectId != null
        _uiState.value = _uiState.value.copy(selectedObjectId = objectId)
        // The detail sheet edits values through its own ViewModel, and local
        // writes don't reach the websocket (crm has no commit hook) — so
        // refresh on sheet close to reflect any field changes (card placement).
        if (closing && refresh) {
            refreshObjects()
        }
    }

    /**
     * Opens [objectId], the object a link or a notification named, the first
     * time the screen asks: a return from another screen, or the activity
     * being made again, leaves the sheet as the user left it. Kept in the
     * saved state, so a recreated process does not open it again either.
     */
    fun openLinked(objectId: String?) {
        if (objectId == null || savedStateHandle.get<String>(OPENED) == objectId) return
        savedStateHandle[OPENED] = objectId
        selectObject(objectId)
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
                _actionFailed.tryEmit(e.toMochiError())
            }
        }
    }

    /**
     * A move drawn and not yet answered, as [moveObject] takes it. Its
     * neighbours can change while it waits its turn: a move of one of them
     * that fails leaves it beside the other.
     */
    private class Move(
        val objectId: String,
        val field: String?,
        val value: String?,
        var after: String?,
        var before: String?,
        val rowField: String?,
        val rowValue: String?,
        val parent: String?,
        val clear: Boolean,
        val scope: String?,
    )

    /**
     * A list the server answered, with the moves still waiting drawn over it.
     * One whose read began, as [begun] counts it, before the server last
     * answered a move or a reparent may have been read without it, and is
     * dropped: the list held has the move, and the read made once the moves
     * are answered brings what else changed.
     */
    private fun land(objects: List<CrmObject>, begun: Int? = null): List<CrmObject> {
        if (begun == null || begun > answered) settled = objects
        return drawn()
    }

    /** [settled] with every move still waiting drawn over it, in turn. */
    private fun drawn(): List<CrmObject> = pending.fold(settled, ::placed)

    /**
     * Moves [objectId] directly [after] one object and [before] another, the
     * two it was dropped between, either of which may be null; into column
     * [value] of [field], lane [rowValue] of [rowField], and the children of
     * [parent] when given, or among the children of [scope] when the parent
     * stays. An empty [value] keeps the column, and [clear] empties it. It is
     * drawn there at once, sent once every move made before it has been
     * answered, and takes the keys the server wrote when it answers. When it
     * fails it goes back to where it was, and a move still waiting that was
     * dropped beside it keeps only its other neighbour. The moves wait in the
     * repository's queue for the CRM, so those made before the screen closes
     * are still sent, in turn, after it has.
     */
    fun moveObject(
        objectId: String,
        field: String? = null,
        value: String? = null,
        after: String? = null,
        before: String? = null,
        rowField: String? = null,
        rowValue: String? = null,
        parent: String? = null,
        clear: Boolean = false,
        scope: String? = null,
    ) {
        val move = Move(objectId, field, value, after, before, rowField, rowValue, parent, clear, scope)
        pending += move
        _uiState.value = _uiState.value.copy(objects = drawn())
        repository.queue(crmId) {
            try {
                // Read at its turn: a failed move before it may have taken
                // one of its neighbours away.
                val ranks = repository.moveObject(crmId, objectId, field, value, move.after, move.before, rowField, rowValue, parent, clear, scope)
                answered = fetches
                pending.remove(move)
                settled = reranked(placed(settled, move), ranks)
                _uiState.value = _uiState.value.copy(objects = drawn())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(move)
                _actionFailed.tryEmit(e.toMochiError())
            }
            // The list is read again once nothing waits: a reload between
            // moves would only be drawn over.
            if (pending.isEmpty()) refreshObjects()
        }
    }

    /**
     * Lets go of [move], which failed. A move waiting behind it that was
     * dropped beside its object, while that was drawn where [move] put it,
     * keeps only its other neighbour: beside the object it would land where
     * the object still is, not where the user saw it.
     */
    private fun fail(move: Move) {
        pending.remove(move)
        for (later in pending) {
            // A later move of the object draws it again; drops after that
            // were made beside it there.
            if (later.objectId == move.objectId) break
            if (later.after == move.objectId) later.after = null
            if (later.before == move.objectId) later.before = null
        }
        _uiState.value = _uiState.value.copy(objects = drawn())
    }

    /**
     * [objects] with [move] drawn as the server will write it: under its new
     * parent, taking that parent's board columns and lanes with each of its
     * descendants, as far as each one's class takes them; its column and lane
     * changed, the change going to its descendants the same way; and beside
     * the first of its neighbours that is still where it goes, as the server
     * places it, with that object's key and the place next to it in the list,
     * which a sort by key keeps, since a tie keeps its order. With neither, a
     * move to another column goes after every key, and anything else keeps
     * its own.
     */
    private fun placed(objects: List<CrmObject>, move: Move): List<CrmObject> {
        val previous = objects.firstOrNull { obj -> obj.id == move.objectId } ?: return objects
        val field = move.field?.takeIf { field -> field.isNotBlank() }
        val lane = move.rowField?.takeIf { lane -> lane.isNotBlank() }
        val details = _uiState.value.crmDetails
        // A new parent's values come first, as the server copies them before
        // it writes the move's own.
        val adopted = move.parent?.takeIf { parent -> parent.isNotBlank() && parent != previous.parent }
            ?.let { parent -> objects.firstOrNull { obj -> obj.id == parent } }
        val inherited = if (adopted != null && details != null) parentValues(details, adopted) else emptyMap()
        val start = if (details != null) cascaded(details, previous, inherited) else previous
        var values = start.values
        // An empty value keeps the object's own, as the server does, and a
        // clear empties it.
        if (field != null && move.clear) values = values + (field to "")
        else if (field != null && !move.value.isNullOrEmpty()) values = values + (field to move.value)
        if (lane != null) values = values + (lane to move.rowValue.orEmpty())
        var moved = previous.copy(values = values, parent = move.parent ?: previous.parent)
        val changed = buildMap {
            if (field != null && moved.stringValue(field) != start.stringValue(field)) put(field, moved.stringValue(field))
            if (lane != null && moved.stringValue(lane) != start.stringValue(lane)) put(lane, moved.stringValue(lane))
        }
        val below = if (details == null || (inherited.isEmpty() && changed.isEmpty())) emptySet() else collectDescendants(move.objectId, objects)
        val drawn = if (below.isEmpty()) objects else objects.map { obj ->
            if (obj.id in below) cascaded(details!!, cascaded(details, obj, inherited), changed) else obj
        }
        val column = field?.let { moved.stringValue(it) }
        val among = move.scope ?: move.parent
        // Whether obj is in the column or lane holding value of name. The one
        // for no value also holds, as boards draw them there, the objects
        // whose value no option of the field has for the moved object's
        // class, when the class enumerates it: a deleted option's, or another
        // class's.
        fun holds(obj: CrmObject, name: String, value: String): Boolean {
            val held = obj.stringValue(name)
            if (held == value) return true
            if (value.isNotEmpty() || details == null) return false
            val declared = details.fields[previous.objectClass]?.firstOrNull { candidate -> candidate.id == name }
            return declared?.fieldtype == "enumerated" &&
                details.options[previous.objectClass]?.get(name).orEmpty().none { option -> option.id == held }
        }
        // A neighbour counts only in the column, lane and parent the move puts
        // the object in, as the server takes one: one moved elsewhere since
        // the drop leaves the other.
        val beside = { id: String? ->
            drawn.firstOrNull { obj ->
                obj.id == id && obj.id != move.objectId &&
                    (field == null || holds(obj, field, column.orEmpty())) &&
                    (lane == null || holds(obj, lane, move.rowValue.orEmpty())) &&
                    (among == null || obj.parent == among)
            }
        }
        val after = beside(move.after)
        val anchor = after ?: beside(move.before)
        val others = drawn.filter { obj -> obj.id != move.objectId }.toMutableList()
        when {
            anchor != null -> {
                moved = moved.copy(rank = anchor.rank)
                others.add(others.indexOf(anchor) + if (after != null) 1 else 0, moved)
            }
            field != null && column != start.stringValue(field) -> {
                // The last key, and last in the list, which a tie keeps last.
                others.add(moved.copy(rank = others.maxOfOrNull { obj -> obj.rank } ?: moved.rank))
            }
            else -> return drawn.map { obj -> if (obj.id == move.objectId) moved else obj }
        }
        return others
    }

    /** [objects] with the keys a move wrote given to the objects that hold them. */
    private fun reranked(objects: List<CrmObject>, ranks: List<MovedRank>): List<CrmObject> {
        if (ranks.isEmpty()) return objects
        val keys = ranks.associateBy { rank -> rank.id }
        return objects.map { obj ->
            val key = keys[obj.id] ?: return@map obj
            obj.copy(rank = key.rank, updated = key.updated ?: obj.updated)
        }
    }

    /**
     * How a drop goes beside the object it lands on, by the view's sort:
     * only a manual sort shows the keys a drop places by.
     */
    fun placement(): Placement = when {
        getActiveSortField() != "rank" -> Placement.None
        getActiveSortDirection() == "desc" -> Placement.Descending
        else -> Placement.Ascending
    }

    /**
     * Moves [objectId] dropped on [target], directly [below] it or above it
     * as the view draws it, into column [value] of [field], lane [rowValue]
     * of [rowField] and the children of [parent] when given. [other] is the
     * object drawn on the far side of the drop from [target], if any: both
     * go, so the server can place it beside the other when [target] has
     * moved elsewhere since. A descending manual sort draws the keys upside
     * down, so below is before. Any other sort shows no place to go, so only
     * the column, lane and parent change, and a drop that changes none of
     * them writes nothing. An empty [value] is the column for no value, which
     * clears a value the board draws a column for, and an empty [rowValue]
     * the lane for none, likewise. The move names the column and the lane
     * only when it changes them ([sent]). [scope] is the parent whose
     * children a drop that keeps its parent goes among, as the tree names it.
     */
    fun moveBeside(
        objectId: String,
        target: String,
        below: Boolean,
        field: String? = null,
        value: String? = null,
        rowField: String? = null,
        rowValue: String? = null,
        parent: String? = null,
        other: String? = null,
        scope: String? = null,
    ) {
        val clear = clears(objectId, field, value)
        val sent = sent(objectId, field, value, rowField, rowValue, clear)
        val far = other?.takeIf { id -> id != objectId && id != target }
        when (placement()) {
            Placement.Ascending -> moveObject(
                objectId, sent.field, sent.value,
                after = if (below) target else far, before = if (below) far else target,
                rowField = sent.lane, rowValue = sent.row, parent = parent, clear = clear, scope = scope,
            )
            Placement.Descending -> moveObject(
                objectId, sent.field, sent.value,
                after = if (below) far else target, before = if (below) target else far,
                rowField = sent.lane, rowValue = sent.row, parent = parent, clear = clear, scope = scope,
            )
            Placement.None -> if (clear || changes(objectId, sent.field, sent.value, sent.lane, sent.row, parent)) {
                moveObject(objectId, sent.field, sent.value, rowField = sent.lane, rowValue = sent.row, parent = parent, clear = clear, scope = scope)
            }
        }
    }

    /** What a board move names of its column and lane: see [sent]. */
    private data class Sent(val field: String?, val value: String?, val lane: String?, val row: String?)

    /**
     * What a move of [objectId] into column [value] of [field] and lane
     * [rowValue] of [rowField] names, which the server takes its neighbours
     * among: the column's field and value only when the move changes the
     * column it is drawn in, to another with a value or, with [clear], to
     * the one for no value; and the lane's only when it changes the lane it
     * is drawn in. The column for no value also draws the cards whose value
     * no column is drawn for, and so does the lane for none, and a card's
     * class may have no such field: naming the field there would refuse the
     * neighbours drawn beside the card whose value is not its own, and the
     * move itself where the class lacks it.
     */
    private fun sent(objectId: String, field: String?, value: String?, rowField: String?, rowValue: String?, clear: Boolean): Sent {
        val obj = _uiState.value.objects.firstOrNull { candidate -> candidate.id == objectId }
        val named = field?.takeIf { name -> name.isNotBlank() }
        val column = value?.takeIf { target -> target.isNotEmpty() && (obj == null || named == null || target != boardColumn(obj, named)) }
        val lane = rowField?.takeIf { name -> name.isNotBlank() }
        val row = if (lane == null) null else rowValue.orEmpty().takeIf { target -> obj == null || boardLane(obj, lane) != target }
        return Sent(
            field = named.takeIf { clear || column != null },
            value = if (clear) "" else column,
            lane = lane.takeIf { row != null },
            row = row,
        )
    }

    /** The field the active board draws lanes of, when it draws any. */
    private fun boardLaneField(): String? {
        val view = getActiveView()?.takeIf { view -> view.viewtype == "board" } ?: return null
        return view.rows.takeIf { rows -> rows.isNotBlank() && getBoardOptions(view, rows).isNotEmpty() }
    }

    /**
     * The lane of the active board [objectId] goes to: [rowValue] of
     * [rowField] when a drop names one, else the one it is drawn in; with
     * the lanes' field, or null on a board that draws none.
     */
    private fun boardTarget(objectId: String, rowField: String?, rowValue: String?): Pair<String, String>? {
        val lane = rowField?.takeIf { name -> name.isNotBlank() } ?: boardLaneField() ?: return null
        if (rowField != null) return lane to rowValue.orEmpty()
        val obj = _uiState.value.objects.firstOrNull { candidate -> candidate.id == objectId } ?: return null
        return lane to boardLane(obj, lane)
    }

    /**
     * The lane of [field] the active board draws [obj] in: its value when
     * the board draws a lane for it, else "", the lane for none.
     */
    fun boardLane(obj: CrmObject, field: String): String =
        obj.stringValue(field).takeIf { value -> value in boardColumns(field) }.orEmpty()

    /** Whether a move without a place changes [objectId]'s column, lane or parent. */
    private fun changes(objectId: String, field: String?, value: String?, rowField: String?, rowValue: String?, parent: String?): Boolean {
        val obj = _uiState.value.objects.firstOrNull { candidate -> candidate.id == objectId } ?: return true
        return (!field.isNullOrBlank() && !value.isNullOrEmpty() && obj.stringValue(field) != value) ||
            (rowField != null && obj.stringValue(rowField) != rowValue.orEmpty()) ||
            (parent != null && parent != obj.parent)
    }

    /** The values of [field] the active board draws a column for. */
    private fun boardColumns(field: String): Set<String> =
        getBoardOptions(getActiveView(), field).map { option -> option.id }.toSet()

    /**
     * The column of [field] the active board draws [obj] in, of [columns]:
     * its value when the board draws a column for it, else "", the column
     * for no value.
     */
    fun boardColumn(obj: CrmObject, field: String, columns: Set<String> = boardColumns(field)): String =
        obj.stringValue(field).takeIf { value -> value in columns }.orEmpty()

    /**
     * Whether a move of [objectId] into column [value] of [field] clears the
     * field: a move to the column for no value from a column with one. An
     * empty value alone keeps the column, as a reorder sends it.
     */
    private fun clears(objectId: String, field: String?, value: String?): Boolean {
        if (field.isNullOrBlank() || !value.isNullOrEmpty()) return false
        val obj = _uiState.value.objects.firstOrNull { candidate -> candidate.id == objectId } ?: return false
        return boardColumn(obj, field).isNotEmpty()
    }

    /**
     * Moves [objectId] to the end of the column of [value] as the board draws
     * it, past every object it holds, the board's hidden ones too, and into
     * the lane of [rowValue] when [rowField] is given: after the last key, or
     * before the first under a descending manual sort. Under any other sort
     * the column and lane change alone, and a drop that changes neither
     * writes nothing. An empty [value] is the column for no value, which
     * clears a value the board draws a column for, and an empty [rowValue]
     * the lane for none, likewise. A drop in the lane the card is drawn in
     * keeps its lane, and one with no lane on a board that draws them keeps
     * the card's own: the end is the last of the column in that lane, as the
     * board draws it. The move names the column and the lane only when it
     * changes them ([sent]).
     */
    fun moveToEnd(objectId: String, field: String, value: String, rowField: String? = null, rowValue: String? = null) {
        val clear = clears(objectId, field, value)
        val sent = sent(objectId, field, value, rowField, rowValue, clear)
        val lane = boardTarget(objectId, rowField, rowValue)
        val column = rankOrder(field, value, objectId, clear, lane?.first, lane?.second.orEmpty())
        when (placement()) {
            Placement.Ascending -> moveObject(objectId, sent.field, sent.value, after = column.lastOrNull()?.id, rowField = sent.lane, rowValue = sent.row, clear = clear)
            Placement.Descending -> moveObject(objectId, sent.field, sent.value, before = column.firstOrNull()?.id, rowField = sent.lane, rowValue = sent.row, clear = clear)
            Placement.None -> if (clear || changes(objectId, sent.field, sent.value, sent.lane, sent.row, null)) {
                moveObject(objectId, sent.field, sent.value, rowField = sent.lane, rowValue = sent.row, clear = clear)
            }
        }
    }

    /**
     * Moves [objectId] to the top of the column of [value] as the board draws
     * it, above every object it holds, the board's hidden ones too: before the
     * first key, or after the last under a descending manual sort. Under any
     * other sort the column changes alone, and a move that changes nothing
     * writes nothing. An empty [value] is the column for no value, as in
     * [moveToEnd]. On a board that draws lanes it keeps the card's own, and
     * the top is the first of the column in that lane.
     */
    fun moveToTop(objectId: String, field: String, value: String) {
        val clear = clears(objectId, field, value)
        val sent = sent(objectId, field, value, null, null, clear)
        val lane = boardTarget(objectId, null, null)
        val column = rankOrder(field, value, objectId, clear, lane?.first, lane?.second.orEmpty())
        when (placement()) {
            Placement.Ascending -> moveObject(objectId, sent.field, sent.value, before = column.firstOrNull()?.id, rowField = sent.lane, rowValue = sent.row, clear = clear)
            Placement.Descending -> moveObject(objectId, sent.field, sent.value, after = column.lastOrNull()?.id, rowField = sent.lane, rowValue = sent.row, clear = clear)
            Placement.None -> if (clear || changes(objectId, sent.field, sent.value, sent.lane, sent.row, null)) {
                moveObject(objectId, sent.field, sent.value, rowField = sent.lane, rowValue = sent.row, clear = clear)
            }
        }
    }

    /**
     * Moves [objectId] into lane [rowValue] of [rowField], keeping its column
     * and its key, as the Move sheet's lanes do. A lane it is drawn in
     * already writes nothing.
     */
    fun moveToLane(objectId: String, rowField: String, rowValue: String) {
        val sent = sent(objectId, null, null, rowField, rowValue, false)
        if (sent.lane != null) moveObject(objectId, rowField = sent.lane, rowValue = sent.row)
    }

    /**
     * The cards the Move sheet offers to put [objectId] after: the others the
     * board draws in its column, and in its lane when the view has lanes, in
     * the order the board draws them. None under a sort by a field, which
     * draws no place to go.
     */
    fun movePlaces(objectId: String): List<CrmObject> {
        if (placement() == Placement.None) return emptyList()
        val view = getActiveView() ?: return emptyList()
        val columnField = view.columns.takeIf { field -> field.isNotBlank() } ?: return emptyList()
        val rowField = view.rows.takeIf { field -> field.isNotBlank() }
        val shown = topLevelObjects(_uiState.value.objects, view)
        val moved = shown.firstOrNull { obj -> obj.id == objectId } ?: return emptyList()
        // A value no column or lane is drawn for sits in the board's unset one.
        val columns = getBoardOptions(view, columnField).map { option -> option.id }.toSet()
        val rows = rowField?.let { field -> getBoardOptions(view, field).map { option -> option.id }.toSet() }.orEmpty()
        val cell = { obj: CrmObject ->
            obj.stringValue(columnField).takeIf { value -> value in columns }.orEmpty() to
                rowField?.let { field -> obj.stringValue(field).takeIf { value -> value in rows } }.orEmpty()
        }
        val own = cell(moved)
        val visible = getVisibleObjectIds()
        return sortObjects(
            shown.filter { obj ->
                obj.id != objectId && cell(obj) == own && (visible == null || obj.id in visible)
            }
        )
    }

    /**
     * Reload the object list in the background. Cancels the pending reload
     * and waits [REFRESH_DEBOUNCE] first, so one drag - its move response and
     * the object/update and object/ranks frames it echoes - makes one fetch.
     */
    private fun refreshObjects() {
        if (crmPending) {
            return
        }
        objectsJob?.cancel()
        objectsPending = true
        objectsJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            objectsPending = false
            try {
                val begun = ++fetches
                val objects = repository.getObjects(crmId)
                val watched = repository.getWatched(crmId)
                _uiState.value = _uiState.value.copy(objects = land(objects, begun), watched = watched)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { }
        }
    }

    private fun subscribeWebSocket() {
        viewModelScope.launch {
            val serverUrl = sessionManager.serverUrl.let {
                sessionManager.getServerUrlBlocking()
            }
            wsSubscriptionId = webSocket.subscribe(serverUrl, crmId, app = "crm") { event ->
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
        if (hidden) {
            markStale(type)
            return
        }
        when {
            type in OBJECT_FRAMES -> refreshObjects()
            // Structure changes: board columns derive from field options, plus
            // views/classes/hierarchy — reload the whole crm.
            // A reopened socket missed whatever was sent while it was down.
            isCrmFrame(type) -> refreshCrm()
        }
    }

    /** Notes what a frame of [type] changed, for [onScreenResumed] to read. */
    private fun markStale(type: String) {
        if (type in OBJECT_FRAMES) {
            objectsStale = true
            // The held list an item's pickers read is now behind.
            repository.forgetObjects(crmId)
        } else if (isCrmFrame(type)) {
            crmStale = true
            repository.forgetObjects(crmId)
        }
    }

    private fun isCrmFrame(type: String): Boolean =
        type == REOPEN ||
            type == "crm/update" || type == "crm/resynced" ||
            type == "hierarchy/set" ||
            type.substringBefore("/") in CRM_FRAME_KINDS

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

    /**
     * Moves [objectId] under [newParentId], or to the top level for "", in
     * turn with the moves made before it, in the repository's queue for the
     * CRM, as [moveObject] sends them. The server gives it a key after its
     * new siblings, which it answers and tells subscribers of, and it and
     * its descendants take the new parent's board columns and lanes, as far
     * as each one's class takes them; it is drawn there when the answer
     * comes, and the list read then holds it.
     */
    fun reparentObject(objectId: String, newParentId: String) {
        repository.queue(crmId) {
            try {
                val ranks = repository.updateObject(crmId, objectId, newParentId)
                answered = fetches
                settled = reranked(reparented(settled, objectId, newParentId), ranks)
                _uiState.value = _uiState.value.copy(objects = drawn())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
            }
            if (pending.isEmpty()) refreshObjects()
        }
    }

    /**
     * [objects] with [objectId] under [parent], it and its descendants taking
     * the board columns and lanes of a parent other than the top level, as
     * the server copies them, each as far as its own class takes them.
     */
    private fun reparented(objects: List<CrmObject>, objectId: String, parent: String): List<CrmObject> {
        val details = _uiState.value.crmDetails
        val previous = objects.firstOrNull { obj -> obj.id == objectId } ?: return objects
        val adopted = parent.takeIf { id -> id.isNotBlank() && id != previous.parent }
            ?.let { id -> objects.firstOrNull { obj -> obj.id == id } }
        val inherited = if (adopted != null && details != null) parentValues(details, adopted) else emptyMap()
        val below = if (inherited.isEmpty()) emptySet() else collectDescendants(objectId, objects)
        return objects.map { obj ->
            when {
                obj.id == objectId -> (if (inherited.isEmpty()) obj else cascaded(details!!, obj, inherited)).copy(parent = parent)
                obj.id in below -> cascaded(details!!, obj, inherited)
                else -> obj
            }
        }
    }

    fun getClassById(classId: String): CrmClass? {
        return _uiState.value.crmDetails?.classes?.find { it.id == classId }
    }

    /** The class [view]'s board keeps its columns in, or null before the CRM loads. */
    fun getBoardClass(view: CrmView? = getActiveView()): String? {
        val details = _uiState.value.crmDetails ?: return null
        return boardClass(details, view)
    }

    /** The options of [fieldId] that [view]'s board shows as its columns or lanes. */
    fun getBoardOptions(view: CrmView?, fieldId: String): List<FieldOption> {
        val details = _uiState.value.crmDetails ?: return emptyList()
        return boardOptions(details, view, fieldId)
    }

    fun renameColumnOption(fieldId: String, optionId: String, name: String) {
        // A column is an option of the board class, so that is the class changed.
        val classId = getBoardClass() ?: return
        viewModelScope.launch {
            try {
                repository.updateOption(crmId, classId, fieldId, optionId, name, null, null)
                refreshCrm()
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
            }
        }
    }

    /**
     * How many of the CRM's objects hold [optionId] in [fieldId]: every class
     * and every depth, filtered out or not. A column is only deleted while
     * this is none, since its objects would keep an option that no longer is.
     */
    fun columnHolds(fieldId: String, optionId: String): Int =
        _uiState.value.objects.count { obj -> obj.stringValue(fieldId) == optionId }

    /** Whether the signed-in person may change the CRM's design, columns included. */
    fun canDesign(): Boolean = canDesign(_uiState.value.crmDetails?.crm?.access)

    /** Whether the signed-in person may change, move and delete the CRM's objects. */
    fun canWrite(): Boolean = canWrite(_uiState.value.crmDetails?.crm?.access)

    /** Whether the signed-in person may create objects in the CRM. */
    fun canCreate(): Boolean = canCreate(_uiState.value.crmDetails?.crm?.access)

    /** Deletes an empty column; one that has gained objects since is left alone. */
    fun deleteColumnOption(fieldId: String, optionId: String) {
        if (columnHolds(fieldId, optionId) > 0) return
        // A column is an option of the board class, so that is the class changed.
        val classId = getBoardClass() ?: return
        viewModelScope.launch {
            try {
                repository.deleteOption(crmId, classId, fieldId, optionId)
                refreshCrm()
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
            }
        }
    }

    /**
     * Collapses or expands the board column [optionId] of [fieldId]. Kept on
     * this device, per CRM and column field.
     */
    fun toggleColumnCollapsed(fieldId: String, optionId: String) {
        val next = collapsedColumnsStore.toggle(crmId, fieldId, optionId)
        _collapsedColumns.value = _collapsedColumns.value + (fieldId to next)
    }

    /**
     * Persists a drag-reorder: [order] is every option id in display order.
     */
    fun reorderColumnOptions(fieldId: String, order: List<String>) {
        // A column is an option of the board class, so that is the class changed.
        val classId = getBoardClass() ?: return
        viewModelScope.launch {
            try {
                repository.reorderOptions(crmId, classId, fieldId, order.joinToString(","))
                refreshCrm()
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
            }
        }
    }

    /**
     * The objects a card dropped into the column of [value] in [field] is
     * placed among, in rank order: where [moveToEnd] and [moveToTop] find the
     * neighbour a move names. Every object the board draws in the column is in
     * it except [moved], children and classes the view hides included, so an
     * end is past the board's hidden objects too. An empty [value] keeps the
     * moved object's own column, as the server does, unless [clear] moves it
     * to the column for no value, which also holds every value the board
     * draws no column for. With [lane], only those in lane [row] of it: the
     * objects holding [row], or for "" every one the board draws in the lane
     * for none, which holds every value no lane is drawn for too. A move is
     * placed beside a card of the lane it goes to, as the board draws it.
     */
    fun rankOrder(field: String, value: String, moved: String, clear: Boolean = false, lane: String? = null, row: String = ""): List<CrmObject> {
        val objects = _uiState.value.objects
        val columns = boardColumns(field)
        val rows = lane?.let { name -> boardColumns(name) }.orEmpty()
        val target = if (clear) "" else value.ifBlank { objects.firstOrNull { obj -> obj.id == moved }?.let { obj -> boardColumn(obj, field, columns) }.orEmpty() }
        val inLane = { obj: CrmObject ->
            lane == null || if (row.isEmpty()) boardColumn(obj, lane, rows).isEmpty() else obj.stringValue(lane) == row
        }
        return objects
            .filter { obj -> obj.id != moved && boardColumn(obj, field, columns) == target && inLane(obj) }
            .sortedBy { obj -> obj.rank }
    }

    /**
     * Descendant ids of [objectId] among [objects], for refusing a drop that
     * would reparent a row under itself, and for what a move cascades to.
     */
    fun collectDescendants(objectId: String, objects: List<CrmObject> = _uiState.value.objects): Set<String> {
        val byParent = objects.groupBy { it.parent }
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
            objects = objects.filter { obj -> obj.stringValue(fieldId) in selected }
        }

        // A view's stored filter is not applied: the view editor says so, and
        // the web leaves it out too.

        // Filter to watched objects only
        if (state.watchedOnly) {
            val watchedSet = state.watched.toSet()
            objects = objects.filter { obj -> watchedSet.contains(obj.id) }
        }

        return sortObjects(objects)
    }

    /**
     * Sorts by the active sort field and direction: numbers, dates and
     * option ranks by type, everything else as text through the shared
     * natural compare, so a view orders the same here as on web.
     */
    fun sortObjects(objects: List<CrmObject>): List<CrmObject> {
        val field = getActiveSortField()
        val multiplier = if (getActiveSortDirection() == "desc") -1 else 1
        // rank is a fractional-index text key whose ASCII (binary) order is the
        // intended order, so compare the keys as strings — matching the
        // web's rankCompare. The remaining built-ins are still numeric.
        // Descending is the ascending order upside down, ties included, so a
        // moved card drawn beside the object whose key it holds for now
        // shows on the side the drop put it.
        if (field == "rank") {
            val ascending = objects.sortedWith(Comparator { a, b -> a.rank.compareTo(b.rank) })
            return if (multiplier < 0) ascending.asReversed() else ascending
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
                            textCompare(av, bv)
                        }
                    }
                    "date" -> {
                        val ad = dateSortKey(av)
                        val bd = dateSortKey(bv)
                        if (ad != null && bd != null) {
                            ad.compareTo(bd)
                        } else {
                            textCompare(av, bv)
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
                            textCompare(ao?.name ?: av, bo?.name ?: bv)
                        }
                    }
                    else -> textCompare(av, bv)
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
                var missing = 0
                while (rounds < MAX_WARM_ROUNDS) {
                    val warm = repository.warmExport(entityId())
                    // The last round's failures are what the export leaves out.
                    missing = warm.failed
                    if (warm.remaining <= 0) {
                        break
                    }
                    rounds++
                }
                val name = _uiState.value.crmDetails?.crm?.name
                _uiState.value = _uiState.value.copy(
                    exportMissing = missing,
                    pendingExport = PendingExport(
                        suggestedName = repository.exportFileName(name, "crm-backup", "zip"),
                        mimeType = MIME_ZIP,
                    )
                )
            } catch (error: Exception) {
                // The CRM is loaded, where uiState.error is not drawn.
                _actionFailed.tryEmit(error.toMochiError())
            } finally {
                _uiState.value = _uiState.value.copy(isExporting = false)
            }
        }
    }

    /**
     * Builds a CSV of the active view's objects as shown and parks it for the
     * save dialog; local only, no server call. [headings] name the ID, Class
     * and Parent columns in the user's language.
     */
    fun exportCsv(headings: List<String>) {
        val csv = buildCsv(headings)
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
     * CSV in the web export's shape: [headings] (ID, Class, Parent), then one
     * column per field name across the CRM's classes. No title column - a
     * class titles itself with one of its fields. Rows are the active view's
     * filtered objects, values as displayed.
     */
    private fun buildCsv(headings: List<String>): String {
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
        rows += headings + columns
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

    /**
     * One cell's text: an option's or a person's id resolved to its name. A
     * person the CRM has no name for keeps the id, which the file's ID and
     * Parent columns are made of too, rather than losing who it was.
     */
    private fun csvValue(obj: CrmObject, field: CrmField): String {
        val value = obj.stringValue(field.id)
        if (value.isBlank()) return ""
        return when (field.fieldtype) {
            "enumerated" -> getOptionsForObject(obj.objectClass, field.id)
                .find { option -> option.id == value }?.name ?: value
            "user" -> _uiState.value.people
                .find { person -> person.id == value }?.name?.takeIf { name -> name.isNotBlank() } ?: value
            else -> value
        }
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
        _uiState.value = _uiState.value.copy(savedExport = null, exportFailed = false, exportMissing = 0)
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

        /** The saved-state key of the linked object already opened. */
        const val OPENED = "opened"

        /** Frames that change the items alone. */
        val OBJECT_FRAMES = setOf(
            "object/create", "object/update", "object/delete",
            "object/ranks", "values/update",
        )

        /** Kinds of frame that change the crm's structure. */
        val CRM_FRAME_KINDS = setOf("class", "field", "option", "view")
    }
}

/** [block]'s answer as a [Result], except a cancellation, which still cancels. */
private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
