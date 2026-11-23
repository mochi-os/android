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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.model.WebSocketEvent
import org.mochios.android.model.canCreate
import org.mochios.android.model.canDesign
import org.mochios.android.model.canWrite
import org.mochios.android.files.MIME_ZIP
import org.mochios.android.files.PendingExport
import org.mochios.android.files.SavedExport
import org.mochios.android.util.NaturalCompare
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.REOPEN
import org.mochios.android.share.Shared
import org.mochios.projects.ui.board.allOptions
import org.mochios.projects.ui.board.boardClass
import org.mochios.projects.ui.board.boardOptions
import org.mochios.projects.ui.board.topLevelObjects
import org.mochios.projects.api.MovedRank
import org.mochios.projects.lib.ActiveViewStore
import org.mochios.projects.lib.CollapsedColumnsStore
import org.mochios.projects.model.FieldOption
import org.mochios.projects.model.Person
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

/** The saved-state key that records the route's object as opened. */
private const val INITIAL_OPENED = "initialOpened"

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
    private val savedStateHandle: SavedStateHandle,
    private val repository: ProjectsRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
    private val activeViewStore: ActiveViewStore,
    private val collapsedColumnsStore: CollapsedColumnsStore,
) : ViewModel() {

    val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    private val _uiState = MutableStateFlow(ProjectUiState())
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()

    /** Collapsed board columns, by column field. */
    private val _collapsedColumns = MutableStateFlow(collapsedColumnsStore.get(projectId))
    val collapsedColumns: StateFlow<Map<String, Set<String>>> = _collapsedColumns.asStateFlow()

    private val _shares = MutableSharedFlow<Shared>(extraBufferCapacity = 1)

    /** The project, readied for the system share sheet. */
    val shares: SharedFlow<Shared> = _shares.asSharedFlow()

    // A failure after the project has loaded - a mutation, an export, a pulled
    // refresh: uiState.error only renders while nothing is loaded, so these
    // are toasted by the screen.
    private val _actionFailed = MutableSharedFlow<MochiError>(extraBufferCapacity = 4)
    val actionFailed: SharedFlow<MochiError> = _actionFailed.asSharedFlow()

    private var wsSubscriptionId: String? = null

    /** The pending or in-flight objects refresh; cancelled when a newer one starts. */
    private var objectsJob: Job? = null

    /** The pending or in-flight project refresh; cancelled when a newer one starts. */
    private var projectJob: Job? = null

    /** Set while [objectsJob] waits out its debounce, before its fetch starts. */
    @Volatile
    private var objectsPending = false

    /** Set while [projectJob] waits out its debounce, before its fetch starts. */
    @Volatile
    private var projectPending = false

    /** Set while another screen covers the board: frames then only mark it stale. */
    @Volatile
    private var hidden = false

    /** Items changed while [hidden], to be read on coming back. */
    @Volatile
    private var objectsStale = false

    /** The project's structure changed while [hidden], to be read on coming back. */
    @Volatile
    private var projectStale = false

    // Above init: a project held from before lands in loadProject while the
    // view model is still being made.

    /**
     * The objects as the server last listed them, with what each move it has
     * answered since wrote; the list shown is this with [pending] drawn over
     * it, so a reload while moves wait keeps them where they were dropped.
     */
    private var settled: List<ProjectObject> = emptyList()

    /** The moves drawn and not yet answered, in the order they were made. */
    private val pending = mutableListOf<Move>()

    /**
     * Sends the moves one at a time, in the order they were made: sent at
     * once, a later drag could reach the server first, and a drop beside a
     * card whose own move had not landed would go beside where it was.
     */
    private val moving = Mutex()

    init {
        loadProject()
        subscribeWebSocket()
    }

    override fun onCleared() {
        super.onCleared()
        wsSubscriptionId?.let { webSocket.unsubscribe(it) }
        // Nothing follows the frames for the held list now.
        repository.forgetObjects(projectId)
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
                val (details, objects, people) = fetch()
                val watched = repository.getWatched(projectId)
                val activeViewId = _uiState.value.activeViewId
                    ?: rememberedViewId(details.views)
                    ?: details.views.firstOrNull()?.id
                _uiState.value = _uiState.value.copy(
                    projectDetails = details,
                    objects = land(objects),
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
     * The project's information, its items and its people, asked for at once.
     * The information is awaited first: a project gone or closed to the user
     * reports that, whatever the items' answer, which is then discarded. The
     * people are best-effort, null when they cannot be read.
     */
    private suspend fun fetch(): Triple<ProjectDetails, List<ProjectObject>, List<Person>?> = coroutineScope {
        val details = async { repository.getProjectInfo(projectId) }
        val objects = async { attempt { repository.getObjects(projectId) } }
        val people = async { attempt { repository.getPeople(projectId) } }
        Triple(details.await(), objects.await().getOrThrow(), people.await().getOrNull())
    }

    /**
     * [refreshSilently] in the background: structure frames and our own column
     * edits. Cancels the pending refresh and waits [REFRESH_DEBOUNCE] first,
     * so an edit and its echo frame make one round of calls.
     */
    private fun refreshProject() {
        projectJob?.cancel()
        if (objectsPending) {
            objectsJob?.cancel()
            objectsPending = false
        }
        projectPending = true
        projectJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            projectPending = false
            refreshSilently()
        }
    }

    /**
     * The board's ON_PAUSE: a create, copy or design screen opened over it, or
     * the app left. Frames from now on only mark the board stale, and a
     * refresh still waiting out its debounce is put off until [onScreenResumed].
     */
    fun onScreenPaused() {
        hidden = true
        if (projectPending) {
            projectJob?.cancel()
            projectPending = false
            projectStale = true
            repository.forgetObjects(projectId)
        }
        if (objectsPending) {
            objectsJob?.cancel()
            objectsPending = false
            objectsStale = true
            repository.forgetObjects(projectId)
        }
    }

    /**
     * The board's ON_RESUME. Reads once what changed while it was covered, or
     * the whole project when [changed] says the screen that covered it wrote
     * to the project; the first resume, as the board opens, reads nothing.
     */
    fun onScreenResumed(changed: Boolean = false) {
        hidden = false
        val project = changed || projectStale
        val objects = objectsStale
        projectStale = false
        objectsStale = false
        if (project) {
            refreshProject()
        } else if (objects) {
            refreshObjects()
        }
    }

    private suspend fun refreshSilently() {
        try {
            val (details, objects, people) = fetch()
            val watched = repository.getWatched(projectId)
            _uiState.value = _uiState.value.copy(
                projectDetails = details,
                objects = land(objects),
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
                val (details, objects, people) = fetch()
                val watched = repository.getWatched(projectId)
                _uiState.value = _uiState.value.copy(
                    projectDetails = details,
                    objects = land(objects),
                    watched = watched,
                    people = people ?: _uiState.value.people,
                    isRefreshing = false,
                    error = null
                )
            } catch (e: Exception) {
                if (_uiState.value.projectDetails != null) {
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                    _actionFailed.tryEmit(e.toMochiError())
                } else {
                    _uiState.value = _uiState.value.copy(isRefreshing = false, error = e.toMochiError())
                }
            }
        }
    }

    fun setActiveView(viewId: String) {
        _uiState.value = _uiState.value.copy(activeViewId = viewId)
        activeViewStore.set(projectId, viewId)
    }

    /**
     * Share the project: by its name and address, and to other apps by the
     * link another server joins it by, which the server assembles.
     */
    fun share() {
        viewModelScope.launch {
            try {
                // share refuses a fingerprint; the route id may be one.
                val link = repository.getShareLink(entityId())
                val project = _uiState.value.projectDetails?.project
                val address = sessionManager.getServerUrlBlocking().trimEnd('/') + "/projects/" +
                    (project?.fingerprint?.takeIf { it.isNotEmpty() } ?: projectId)
                _shares.emit(Shared(text = project?.name.orEmpty(), url = address, link = link))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
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
                _actionFailed.tryEmit(error.toMochiError())
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

    /**
     * Opens the sheet of [objectId], or closes it for null. Closing reads the
     * items again unless [refresh] is false, for a sheet that gives way to a
     * screen whose return reads the whole project anyway.
     */
    fun selectObject(objectId: String?, refresh: Boolean = true) {
        val closing = objectId == null && _uiState.value.selectedObjectId != null
        _uiState.value = _uiState.value.copy(selectedObjectId = objectId)
        // The detail sheet edits values through its own ViewModel, and local
        // writes don't reach the websocket (projects has no commit hook) — so
        // refresh on sheet close to reflect any field changes (card placement).
        if (closing && refresh) {
            refreshObjects()
        }
    }

    /**
     * Opens the object the route named ([objectId]) once for this screen: a
     * return from the create form or the design, a rotation or a process
     * restart leaves the sheet as the user left it. The record of it is kept
     * with the route's own state.
     */
    fun openInitialObject(objectId: String?) {
        if (objectId == null || savedStateHandle.get<Boolean>(INITIAL_OPENED) == true) return
        savedStateHandle[INITIAL_OPENED] = true
        selectObject(objectId)
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

    /** A list the server answered, with the moves still waiting drawn over it. */
    private fun land(objects: List<ProjectObject>): List<ProjectObject> {
        settled = objects
        return drawn()
    }

    /** [settled] with every move still waiting drawn over it, in turn. */
    private fun drawn(): List<ProjectObject> = pending.fold(settled, ::placed)

    /**
     * Moves [objectId] directly [after] one object and [before] another, the
     * two it was dropped between, either of which may be null; into column
     * [value] of [field], lane [rowValue] of [rowField], and the children of
     * [parent] when given, or among the children of [scope] when the parent
     * stays. An empty [value] keeps the column, and [clear] empties it. It is
     * drawn there at once, sent once every move made before it has been
     * answered, and takes the keys the server wrote when it answers. When it
     * fails it goes back to where it was, and a move still waiting that was
     * dropped beside it keeps only its other neighbour.
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
        viewModelScope.launch {
            moving.withLock {
                try {
                    // Read at its turn: a failed move before it may have
                    // taken one of its neighbours away.
                    val ranks = repository.moveObject(projectId, objectId, field, value, move.after, move.before, rowField, rowValue, parent, clear, scope)
                    pending.remove(move)
                    settled = reranked(placed(settled, move), ranks)
                    _uiState.value = _uiState.value.copy(objects = drawn())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fail(move)
                    _actionFailed.tryEmit(e.toMochiError())
                }
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
     * [objects] with [move] drawn as the server will write it: its column,
     * lane and parent changed, and beside the first of its neighbours that is
     * still where it goes, as the server places it, with that object's key
     * and the place next to it in the list, which a sort by key keeps, since
     * a tie keeps its order. With neither, a move to another column goes
     * after every key, and anything else keeps its own.
     */
    private fun placed(objects: List<ProjectObject>, move: Move): List<ProjectObject> {
        val previous = objects.firstOrNull { obj -> obj.id == move.objectId } ?: return objects
        val field = move.field?.takeIf { field -> field.isNotBlank() }
        val lane = move.rowField?.takeIf { lane -> lane.isNotBlank() }
        var values = previous.values
        // An empty value keeps the object's own, as the server does, and a
        // clear empties it.
        if (field != null && move.clear) values = values + (field to "")
        else if (field != null && !move.value.isNullOrEmpty()) values = values + (field to move.value)
        if (lane != null) values = values + (lane to move.rowValue.orEmpty())
        var moved = previous.copy(values = values, parent = move.parent ?: previous.parent)
        val column = field?.let { moved.stringValue(it) }
        val among = move.scope ?: move.parent
        // A neighbour counts only in the column, lane and parent the move puts
        // the object in, as the server takes one: one moved elsewhere since
        // the drop leaves the other.
        val beside = { id: String? ->
            objects.firstOrNull { obj ->
                obj.id == id && obj.id != move.objectId &&
                    (field == null || obj.stringValue(field) == column) &&
                    (lane == null || obj.stringValue(lane) == move.rowValue.orEmpty()) &&
                    (among == null || obj.parent == among)
            }
        }
        val after = beside(move.after)
        val anchor = after ?: beside(move.before)
        val others = objects.filter { obj -> obj.id != move.objectId }.toMutableList()
        when {
            anchor != null -> {
                moved = moved.copy(rank = anchor.rank)
                others.add(others.indexOf(anchor) + if (after != null) 1 else 0, moved)
            }
            field != null && column != previous.stringValue(field) -> {
                // The last key, and last in the list, which a tie keeps last.
                others.add(moved.copy(rank = others.maxOfOrNull { obj -> obj.rank } ?: moved.rank))
            }
            else -> return objects.map { obj -> if (obj.id == move.objectId) moved else obj }
        }
        return others
    }

    /** [objects] with the keys a move wrote given to the objects that hold them. */
    private fun reranked(objects: List<ProjectObject>, ranks: List<MovedRank>): List<ProjectObject> {
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
     * the lane for none, likewise. [scope] is the parent whose children a
     * drop that keeps its parent goes among, as the tree names it.
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
        val sent = sent(objectId, field, value, rowField, rowValue)
        val far = other?.takeIf { id -> id != objectId && id != target }
        when (placement()) {
            Placement.Ascending -> moveObject(
                objectId, field, sent.value,
                after = if (below) target else far, before = if (below) far else target,
                rowField = sent.lane, rowValue = sent.row, parent = parent, clear = clear, scope = scope,
            )
            Placement.Descending -> moveObject(
                objectId, field, sent.value,
                after = if (below) far else target, before = if (below) target else far,
                rowField = sent.lane, rowValue = sent.row, parent = parent, clear = clear, scope = scope,
            )
            Placement.None -> if (clear || changes(objectId, field, sent.value, sent.lane, sent.row, parent)) {
                moveObject(objectId, field, sent.value, rowField = sent.lane, rowValue = sent.row, parent = parent, clear = clear, scope = scope)
            }
        }
    }

    /** What a board move sends of its column and lane: see [sent]. */
    private data class Sent(val value: String?, val lane: String?, val row: String?)

    /**
     * What a move of [objectId] into column [value] of [field] and lane
     * [rowValue] of [rowField] sends, which the server places it among: the
     * column's value, or none for the column it is drawn in, which keeps its
     * own; and, on a board that draws lanes, their field with the lane it
     * goes to, or its own value when it stays in the lane it is drawn in,
     * which writes nothing. A card whose value no lane is drawn for is drawn
     * in the lane for none, and a drop there keeps that value rather than
     * writing "" over it. A move that names no column, as the tree's, names
     * no lane either.
     */
    private fun sent(objectId: String, field: String?, value: String?, rowField: String?, rowValue: String?): Sent {
        val obj = _uiState.value.objects.firstOrNull { candidate -> candidate.id == objectId }
            ?: return Sent(value, rowField, rowValue?.takeIf { rowField != null })
        val column = if (!field.isNullOrBlank() && value.orEmpty() == boardColumn(obj, field)) "" else value
        val lane = rowField ?: boardLaneField().takeIf { !field.isNullOrBlank() }
            ?: return Sent(column, null, null)
        val row = (if (rowField != null) rowValue.orEmpty() else null)?.takeIf { target -> boardLane(obj, lane) != target }
            ?: obj.stringValue(lane)
        return Sent(column, lane, row)
    }

    /** The field the active board draws lanes of, when it draws any. */
    private fun boardLaneField(): String? {
        val view = getActiveView()?.takeIf { view -> view.viewtype == "board" } ?: return null
        return view.rows.takeIf { rows -> rows.isNotBlank() && getBoardOptions(view, rows).isNotEmpty() }
    }

    /** Whether a move without a place changes [objectId]'s column, lane or parent. */
    private fun changes(objectId: String, field: String?, value: String?, rowField: String?, rowValue: String?, parent: String?): Boolean {
        val obj = _uiState.value.objects.firstOrNull { candidate -> candidate.id == objectId } ?: return true
        return (!field.isNullOrBlank() && !value.isNullOrEmpty() && obj.stringValue(field) != value) ||
            (rowField != null && obj.stringValue(rowField) != rowValue.orEmpty()) ||
            (parent != null && parent != obj.parent)
    }

    /**
     * The lane of [field] the active board draws [obj] in: its value when
     * the board draws a lane for it, else "", the lane for none.
     */
    fun boardLane(obj: ProjectObject, field: String): String =
        obj.stringValue(field).takeIf { value -> value in boardColumns(field) }.orEmpty()

    /** The values of [field] the active board draws a column for. */
    private fun boardColumns(field: String): Set<String> =
        getBoardOptions(getActiveView(), field).map { option -> option.id }.toSet()

    /**
     * The column of [field] the active board draws [obj] in, of [columns]:
     * its value when the board draws a column for it, else "", the column
     * for no value.
     */
    fun boardColumn(obj: ProjectObject, field: String, columns: Set<String> = boardColumns(field)): String =
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
     * Reload the object list in the background. Cancels the pending reload
     * and waits [REFRESH_DEBOUNCE] first, so one drag - its move response and
     * the object/update and object/ranks frames it echoes - makes one fetch.
     */
    private fun refreshObjects() {
        if (projectPending) {
            return
        }
        objectsJob?.cancel()
        objectsPending = true
        objectsJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            objectsPending = false
            refreshObjectsNow()
        }
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
                objects = land(objects),
                watched = watched
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { }
    }

    private fun subscribeWebSocket() {
        viewModelScope.launch {
            val serverUrl = sessionManager.serverUrl.let {
                sessionManager.getServerUrlBlocking()
            }
            wsSubscriptionId = webSocket.subscribe(
                serverUrl,
                projectId,
                app = "projects",
            ) { event ->
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
        if (hidden) {
            markStale(type)
            return
        }
        when {
            type in OBJECT_FRAMES -> refreshObjects()
            // Structure changes: board columns derive from field options, plus
            // views/classes/hierarchy — reload the whole project.
            // A reopened socket missed whatever was sent while it was down.
            isProjectFrame(type) -> refreshProject()
        }
    }

    /** Notes what a frame of [type] changed, for [onScreenResumed] to read. */
    private fun markStale(type: String) {
        if (type in OBJECT_FRAMES) {
            objectsStale = true
            // The held list an item's pickers read is now behind.
            repository.forgetObjects(projectId)
        } else if (isProjectFrame(type)) {
            projectStale = true
            repository.forgetObjects(projectId)
        }
    }

    private fun isProjectFrame(type: String): Boolean =
        type == REOPEN ||
            type == "project/update" || type == "project/resynced" ||
            type == "hierarchy/set" ||
            type.substringBefore("/") in PROJECT_FRAME_KINDS

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

    /** Every option [fieldId] can take, across all of the project's classes. */
    fun getAllOptionsForField(fieldId: String): List<FieldOption> {
        val details = _uiState.value.projectDetails ?: return emptyList()
        return allOptions(details, fieldId)
    }

    /** The class [view]'s board keeps its columns in, or null before the project loads. */
    fun getBoardClass(view: ProjectView? = getActiveView()): String? {
        val details = _uiState.value.projectDetails ?: return null
        return boardClass(details, view)
    }

    /** The options of [fieldId] that [view]'s board shows as its columns or lanes. */
    fun getBoardOptions(view: ProjectView?, fieldId: String): List<FieldOption> {
        val details = _uiState.value.projectDetails ?: return emptyList()
        return boardOptions(details, view, fieldId)
    }

    /**
     * Moves [objectId] under [newParentId], or to the top level for "", in
     * turn with the moves made before it. The server gives it a key after
     * its new siblings, which it answers and tells subscribers of; it is
     * drawn there when the answer comes, and the list read then holds it.
     */
    fun reparentObject(objectId: String, newParentId: String) {
        viewModelScope.launch {
            moving.withLock {
                try {
                    val ranks = repository.updateObject(projectId, objectId, newParentId)
                    settled = reranked(settled.map { obj -> if (obj.id == objectId) obj.copy(parent = newParentId) else obj }, ranks)
                    _uiState.value = _uiState.value.copy(objects = drawn())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _actionFailed.tryEmit(e.toMochiError())
                }
            }
            if (pending.isEmpty()) refreshObjects()
        }
    }

    fun getClassById(classId: String): ProjectClass? {
        return _uiState.value.projectDetails?.classes?.find { it.id == classId }
    }

    fun renameColumnOption(fieldId: String, optionId: String, name: String) {
        // A column is an option of the board class, so that is the class changed.
        val classId = getBoardClass() ?: return
        viewModelScope.launch {
            try {
                repository.updateOption(projectId, classId, fieldId, optionId, name, null, null)
                refreshProject()
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
            }
        }
    }

    /**
     * Deletes the empty column [optionId] of [fieldId]. A column that holds an
     * object by now is left alone: its objects would keep an option that no
     * longer exists.
     */
    fun deleteColumnOption(fieldId: String, optionId: String) {
        // A column is an option of the board class, so that is the class changed.
        val classId = getBoardClass() ?: return
        if (columnHolds(fieldId, optionId)) return
        viewModelScope.launch {
            try {
                repository.deleteOption(projectId, classId, fieldId, optionId)
                refreshProject()
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
            }
        }
    }

    /**
     * Whether any object of the project, at any depth and of any class, holds
     * [optionId] in [fieldId] - not only the cards the board shows.
     */
    fun columnHolds(fieldId: String, optionId: String): Boolean =
        _uiState.value.objects.any { obj ->
            obj.stringValue(fieldId) == optionId
        }

    /** Whether the signed-in person may change the project's design: its columns among it. */
    fun canDesign(): Boolean = canDesign(_uiState.value.projectDetails?.project?.access)

    /** Whether the signed-in person may change objects: move, drag, delete and copy them. */
    fun canWrite(): Boolean = canWrite(_uiState.value.projectDetails?.project?.access)

    /** Whether the signed-in person may create objects. */
    fun canCreate(): Boolean = canCreate(_uiState.value.projectDetails?.project?.access)

    /**
     * The objects a card dropped into column [value] of [field] is placed
     * among: every object the board draws in that column, at any depth and of
     * any class, except [moved], in rank order. An empty [value] keeps the
     * moved object's own column, as the server does, unless [clear] moves it
     * to the column for no value, which also holds every value the board
     * draws no column for. With [lane], only those holding [row] in it: a
     * move that changes lane is placed only beside an object of its new one,
     * as the server takes a neighbour.
     */
    fun rankedColumn(field: String, value: String, moved: String, clear: Boolean = false, lane: String? = null, row: String = ""): List<String> {
        val objects = _uiState.value.objects
        val columns = boardColumns(field)
        val target = if (clear) "" else value.ifBlank { objects.firstOrNull { obj -> obj.id == moved }?.let { obj -> boardColumn(obj, field, columns) }.orEmpty() }
        return objects
            .filter { obj -> obj.id != moved && boardColumn(obj, field, columns) == target && (lane == null || obj.stringValue(lane) == row) }
            .sortedBy { obj -> obj.rank }
            .map { obj -> obj.id }
    }

    /**
     * Moves [objectId] to the end of column [value] of [field] as the board
     * draws it, past every object the column holds, and into lane [rowValue]
     * of [rowField] when given: after the last key, or before the first under
     * a descending manual sort. Under any other sort the column and lane
     * change alone, and a drop that changes neither writes nothing. An empty
     * [value] is the column for no value, which clears a value the board
     * draws a column for, and an empty [rowValue] the lane for none,
     * likewise. A drop in the lane the card is drawn in keeps its lane, and
     * one with no lane on a board that draws them keeps the card's own: the
     * end is the last of the column in that lane, as the server places it.
     */
    fun moveToEnd(objectId: String, field: String, value: String, rowField: String? = null, rowValue: String? = null) {
        val clear = clears(objectId, field, value)
        val sent = sent(objectId, field, value, rowField, rowValue)
        val column = rankedColumn(field, value, objectId, clear, sent.lane, sent.row.orEmpty())
        when (placement()) {
            Placement.Ascending -> moveObject(objectId, field, sent.value, after = column.lastOrNull(), rowField = sent.lane, rowValue = sent.row, clear = clear)
            Placement.Descending -> moveObject(objectId, field, sent.value, before = column.firstOrNull(), rowField = sent.lane, rowValue = sent.row, clear = clear)
            Placement.None -> if (clear || changes(objectId, field, sent.value, sent.lane, sent.row, null)) {
                moveObject(objectId, field, sent.value, rowField = sent.lane, rowValue = sent.row, clear = clear)
            }
        }
    }

    /**
     * Moves [objectId] to the top of column [value] of [field] as the board
     * draws it, above every object the column holds: before the first key, or
     * after the last under a descending manual sort. Under any other sort the
     * column changes alone, and a move that changes nothing writes nothing.
     * An empty [value] is the column for no value, as in [moveToEnd]. On a
     * board that draws lanes it keeps the card's own, and the top is the
     * first of the column in that lane.
     */
    fun moveToTop(objectId: String, field: String, value: String) {
        val clear = clears(objectId, field, value)
        val sent = sent(objectId, field, value, null, null)
        val column = rankedColumn(field, value, objectId, clear, sent.lane, sent.row.orEmpty())
        when (placement()) {
            Placement.Ascending -> moveObject(objectId, field, sent.value, before = column.firstOrNull(), rowField = sent.lane, rowValue = sent.row, clear = clear)
            Placement.Descending -> moveObject(objectId, field, sent.value, after = column.lastOrNull(), rowField = sent.lane, rowValue = sent.row, clear = clear)
            Placement.None -> if (clear || changes(objectId, field, sent.value, sent.lane, sent.row, null)) {
                moveObject(objectId, field, sent.value, rowField = sent.lane, rowValue = sent.row, clear = clear)
            }
        }
    }

    /**
     * The cards the Move sheet offers to put [objectId] after: the others the
     * board draws in its column, and in its lane when the view has lanes, in
     * the order the board draws them. None under a sort by a field, which
     * draws no place to go.
     */
    fun movePlaces(objectId: String): List<ProjectObject> {
        if (placement() == Placement.None) return emptyList()
        val view = getActiveView() ?: return emptyList()
        val columnField = view.columns.takeIf { field -> field.isNotBlank() } ?: return emptyList()
        val rowField = view.rows.takeIf { field -> field.isNotBlank() }
        val shown = topLevelObjects(_uiState.value.objects, view)
        val moved = shown.firstOrNull { obj -> obj.id == objectId } ?: return emptyList()
        // A value no column or lane is drawn for sits in the board's unset one.
        val columns = getBoardOptions(view, columnField).map { option -> option.id }.toSet()
        val rows = rowField?.let { field -> getBoardOptions(view, field).map { option -> option.id }.toSet() }.orEmpty()
        val cell = { obj: ProjectObject ->
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
     * Collapses or expands the board column [optionId] of [fieldId]. Kept on
     * this device, per project and column field.
     */
    fun toggleColumnCollapsed(fieldId: String, optionId: String) {
        val next = collapsedColumnsStore.toggle(projectId, fieldId, optionId)
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
                repository.reorderOptions(projectId, classId, fieldId, order.joinToString(","))
                refreshProject()
            } catch (e: Exception) {
                _actionFailed.tryEmit(e.toMochiError())
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
            objects = objects.filter { obj -> obj.stringValue(fieldId) in selected }
        }

        // A view's stored filter is not applied: the web does not read it, and
        // the editor labels it as not yet applied.

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
        // intended order, so compare the keys as strings — matching the
        // web's rankCompare. The remaining built-ins are still numeric.
        // Descending is the ascending order upside down, ties included, so a
        // moved card drawn beside the object whose key it holds for now
        // shows on the side the drop put it.
        if (field == "rank") {
            val ascending = objects.sortedWith(Comparator { a, b -> a.rank.compareTo(b.rank) })
            return if (multiplier < 0) ascending.asReversed() else ascending
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

        /** Frames that change the items alone. */
        val OBJECT_FRAMES = setOf(
            "object/create", "object/update", "object/delete",
            "object/ranks", "values/update",
        )

        /** Kinds of frame that change the project's structure. */
        val PROJECT_FRAME_KINDS = setOf("class", "field", "option", "view")
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
