// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import android.content.Context
import androidx.annotation.StringRes
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.util.NaturalCompare
import org.mochios.android.files.PendingExport
import org.mochios.calendars.R
import org.mochios.calendars.di.Viewer
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.model.Instance
import org.mochios.calendars.model.Preferences
import org.mochios.calendars.model.tint
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.repository.EventChangedException
import org.mochios.calendars.storage.VisibilityStore
import org.mochios.calendars.ui.editor.EventForm
import org.mochios.calendars.ui.editor.Scope
import org.mochios.calendars.ui.editor.advanced
import org.mochios.calendars.ui.editor.components
import org.mochios.calendars.ui.editor.creationDay
import org.mochios.calendars.ui.editor.defaultStart
import org.mochios.calendars.ui.editor.draft
import org.mochios.calendars.ui.editor.instant
import org.mochios.calendars.ui.editor.matches
import org.mochios.calendars.ui.editor.split
import org.mochios.calendars.ui.router.CALENDARS_FEATURE
import org.mochios.calendars.ui.router.CalendarsSection
import org.mochios.calendars.ui.router.calendarsView
import org.mochios.calendars.ui.router.sharedView
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * The calendar screen. [anchor] is the date the view is built around and
 * [hidden] the calendars this device does not show, which is a viewing choice
 * and never leaves the phone. [instances] is everything the server returned
 * for the range, unfiltered, so flipping a checkbox redraws without a fetch.
 */
data class CalendarUiState(
    val view: String = CalendarsSection.MONTH,
    val anchor: LocalDate = LocalDate.now(),
    val calendars: List<Calendar> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val instances: List<Instance> = emptyList(),
    val truncated: Boolean = false,
    val preferences: Preferences = Preferences(),
    val workweek: Boolean = false,
    val search: String = "",
    /** How many times search was asked for from the toolbar; each puts the cursor in the list's box. */
    val seeking: Int = 0,
    /** Where the visible calendars' events begin and end, for the list view. */
    val bounds: Bounds = Bounds(),
    /** The pages the list view holds, as offsets from the anchor day. */
    val earliest: Int = 0,
    val latest: Int = 0,
    /** Whether a further page of the list view is on its way. */
    val paging: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: MochiError? = null,
    /** A refresh failed with events already on screen, which stay; said once with Retry. */
    val stale: MochiError? = null,
    /** A list page failed to load; the list stops paging on its own and offers Retry. */
    val stalled: Stalled? = null,
) {
    /** How many pages the list view is holding. */
    val pages: Int get() = latest - earliest + 1
    /** The occurrences the views draw. */
    val visible: List<Instance> get() = instances.filterNot { it.calendar in hidden }
}

/** A list page that failed: why, and whether it was the next page or the one before. */
data class Stalled(val error: MochiError, val forward: Boolean)

/**
 * The calendar's ICS address while its dialog is open. [url] is set once,
 * when the address is minted; [exists] says one is already out there.
 */
data class LinkState(
    val calendar: String = "",
    val url: String? = null,
    val exists: Boolean = false,
    val busy: Boolean = false,
)

/** Something the screen has to say once rather than hold in its state. */
sealed class CalendarEvent {
    data class Failed(val error: MochiError) : CalendarEvent()
    /** A manual poll finished; a linked calendar's is a two-way sync, and says so. */
    data class Polled(val linked: Boolean) : CalendarEvent()

    /** An occurrence was moved, and [CalendarViewModel.undo] puts it back. */
    data object Moved : CalendarEvent()

    /** The event changed elsewhere since it was read, so nothing was written. */
    data object Changed : CalendarEvent()

    /** The file picked to import could not be read. */
    data object Unreadable : CalendarEvent()

    /** A calendar's export is fetched: ask where to save it, offering [name]. */
    data class Save(val name: String) : CalendarEvent()

    /** An export was written where the user chose, or [saved] says it was not. */
    data class Exported(val saved: Boolean) : CalendarEvent()

    /** A reminder's occurrence has loaded: open it as a tap on it would. */
    data class Open(val instance: Instance) : CalendarEvent()

    /** A calendar action succeeded, which [message] says, as the web's toast does. */
    data class Done(@param:StringRes val message: Int) : CalendarEvent()
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: CalendarsRepository,
    private val viewer: Viewer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CalendarUiState(hidden = VisibilityStore.hidden(context), workweek = VisibilityStore.workweek(context)),
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CalendarEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CalendarEvent> = _events.asSharedFlow()

    private var loading: Job? = null

    /** The zone every range is measured in: the user's, not the device's. */
    private val zone: ZoneId
        get() = runCatching { ZoneId.of(viewer.zone()) }.getOrDefault(ZoneId.systemDefault())

    /** The first day of a week for this user: 0 Sunday, 1 Monday, 6 Saturday. */
    private val weekStart: Int get() = viewer.week()

    /** Whether the views show each event at its own wall-clock time, in its own zones. */
    private val zones: Boolean get() = _uiState.value.preferences.zones

    /** True until the user has picked a view on this device. */
    private var untouched = LastViewedStore.get(context, CALENDARS_FEATURE).isNullOrBlank()

    // Redraws whenever the calendars shown change: from the drawer, or from
    // the editor showing a hidden calendar an event was just saved into.
    private val unwatch = VisibilityStore.watch(context) { shade() }

    init {
        val view = calendarsView(LastViewedStore.get(context, CALENDARS_FEATURE).orEmpty())
        _uiState.value = _uiState.value.copy(view = view)
        viewModelScope.launch {
            repository.calendarsChanged.collect { reload(refreshing = true) }
        }
        viewModelScope.launch {
            // An edit must not throw the reader back to the anchor day.
            repository.eventsChanged.collect { load(refreshing = true, reset = false) }
        }
        reload()
    }

    /** The calendars, the preferences and the range, from cold. */
    fun reload(refreshing: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = !refreshing && _uiState.value.instances.isEmpty(),
                isRefreshing = refreshing,
                error = null,
            )
            try {
                val calendars = repository.listCalendars()
                    .sortedWith(compareByDescending<Calendar> { it.default }.thenBy(NaturalCompare) { it.name })
                val preferences = repository.getPreferences()
                _uiState.value = _uiState.value.copy(
                    calendars = calendars,
                    preferences = preferences,
                    hidden = VisibilityStore.hidden(context),
                    // On a device that has not been used yet, open on the view
                    // the user chose in the web, which is the same preference.
                    view = if (untouched) calendarsView(preferences.view) else _uiState.value.view,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false, error = e.toMochiError())
                return@launch
            }
            load(refreshing = refreshing)
        }
    }

    /**
     * The occurrences the current view covers. [reset] is false for a refresh
     * that must not move the reader — coming back from the editor, or the
     * screen resuming — which in the list view keeps the pages already
     * scrolled to rather than dropping back to the anchor day.
     */
    fun load(refreshing: Boolean = false, reset: Boolean = true) {
        loading?.cancel()
        loading = viewModelScope.launch {
            val state = _uiState.value
            _uiState.value = state.copy(
                isRefreshing = refreshing,
                isLoading = state.isLoading && state.instances.isEmpty(),
                error = null,
                stalled = null,
            )
            try {
                val shown = shown()
                if (shown.isEmpty()) {
                    // With every calendar hidden there is nothing to fetch,
                    // and nothing can be too many.
                    _uiState.value = _uiState.value.copy(
                        instances = emptyList(),
                        truncated = false,
                        bounds = Bounds(),
                        earliest = 0,
                        latest = 0,
                        paging = false,
                        isLoading = false,
                        isRefreshing = false,
                    )
                } else if (state.view == CalendarsSection.LIST) {
                    pages(state, reset, shown)
                } else {
                    val (start, finish) = range(state)
                    // With events shown in their own zones, a day's
                    // occurrences can begin or end up to a day away by the
                    // user's clock, so the range reaches a day each side.
                    val margin = if (state.preferences.zones) 86_400L else 0L
                    // Only the calendars shown, so hidden ones never take a
                    // share of the most the server will list.
                    val (instances, truncated) = repository.listEvents(start - margin, finish + margin, shown, zone.id)
                    _uiState.value = _uiState.value.copy(
                        instances = ordered(instances),
                        truncated = truncated,
                        bounds = Bounds(),
                        earliest = 0,
                        latest = 0,
                        paging = false,
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
                answer()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Events already on screen stay, and the failure is said with
                // Retry; with none, the error takes the view's place.
                val current = _uiState.value
                _uiState.value = if (current.instances.isEmpty()) {
                    current.copy(isLoading = false, isRefreshing = false, error = e.toMochiError())
                } else {
                    current.copy(isLoading = false, isRefreshing = false, stale = e.toMochiError())
                }
            }
        }
    }

    /** A reminder's link waiting for its occurrence to load: its event and its start. */
    private var pending: Pair<String, Long>? = null

    /** The pending reminder's calendar has already been shown once for it. */
    private var revealed = false

    /**
     * Opens a reminder's link as the web does: the day view on the day it
     * falls on, then, once the occurrence has loaded, the occurrence as a tap
     * on it would open it. A link from before the day was written keeps the
     * day the screen is on.
     */
    fun remind(event: String, occurrence: Long, date: LocalDate?) {
        pending = event to occurrence
        revealed = false
        // The link chose the view, so the one the web saved does not replace it.
        untouched = false
        _uiState.value = _uiState.value.copy(
            view = CalendarsSection.DAY,
            anchor = date ?: _uiState.value.anchor,
            search = "",
        )
        if (_uiState.value.calendars.isNotEmpty()) load()
    }

    /**
     * Settles a waiting reminder once a range has loaded. Its occurrence
     * there opens. Not there, its calendar may be hidden, since only shown
     * calendars are fetched: that calendar is shown once, which loads again.
     * Its calendar already shown, the occurrence has gone and the link is
     * dropped.
     */
    private fun answer() {
        val (event, occurrence) = pending ?: return
        val found = _uiState.value.instances.firstOrNull { it.event == event && it.start == occurrence }
        if (found != null) {
            pending = null
            _events.tryEmit(CalendarEvent.Open(found))
            return
        }
        if (revealed) {
            pending = null
            return
        }
        viewModelScope.launch {
            val stored = runCatching { repository.getEvent(event) }.getOrNull()
            if (stored == null || stored.calendar !in _uiState.value.hidden) {
                pending = null
                return@launch
            }
            revealed = true
            VisibilityStore.reveal(context, stored.calendar)
        }
    }

    /** The stale refresh has been said. */
    fun told() {
        _uiState.value = _uiState.value.copy(stale = null)
    }

    /**
     * The list view's pages: how far the shown calendars reach, and then each
     * page held. A page is a quarter, so even the whole cap's worth is read a
     * page at a time — the server lists at most a year in one call.
     */
    private suspend fun pages(state: CalendarUiState, reset: Boolean, shown: List<String>) {
        val first = if (reset) 0 else state.earliest
        val last = if (reset) 0 else state.latest
        val bounds = runCatching { repository.eventBounds(shown) }.getOrDefault(Bounds())
        val gathered = mutableListOf<Instance>()
        var truncated = false
        for (index in first..last) {
            val span = page(state.anchor, index, zone)
            val (instances, cut) = repository.listEvents(span.start, span.finish, shown, zone.id)
            gathered.addAll(instances)
            truncated = truncated || cut
        }
        _uiState.value = _uiState.value.copy(
            instances = ordered(gathered.distinctBy { it.event to it.start }),
            truncated = truncated,
            bounds = bounds,
            earliest = first,
            latest = last,
            paging = false,
            isLoading = false,
            isRefreshing = false,
        )
    }

    /**
     * The page before the earliest held, from a pull or a scroll to the top
     * of the list view. Nothing happens once the held pages reach back past
     * the first event there is.
     */
    fun earlier() = paginate(forward = false)

    /**
     * The page after the latest held, as the foot of the list view comes into
     * view. A calendar that recurs without end pages on to the cap.
     */
    fun later() = paginate(forward = true)

    /** Whether the list view has a page to load in either direction. */
    fun hasEarlier(state: CalendarUiState = _uiState.value): Boolean =
        earlier(state.anchor, state.earliest, state.bounds, zone)

    fun hasLater(state: CalendarUiState = _uiState.value): Boolean =
        later(state.anchor, state.latest, state.bounds, zone)

    /**
     * Loads the next page, or the one before. An earlier page with nothing
     * new on it would leave nothing to see, so the search carries on back
     * until something lands or the first event is reached, as the web's list
     * does. A page that fails stops the list paging on its own until Retry.
     */
    private fun paginate(forward: Boolean) {
        val state = _uiState.value
        if (state.paging || state.isLoading || state.stalled != null) return
        if (if (forward) !hasLater(state) else !hasEarlier(state)) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(paging = true)
            try {
                do {
                    val current = _uiState.value
                    val index = if (forward) current.latest + 1 else current.earliest - 1
                    val span = page(current.anchor, index, zone)
                    val (instances, truncated) = repository.listEvents(span.start, span.finish, shown(), zone.id)
                    // Merged rather than appended: a multi-day occurrence
                    // reaches into both pages and the server lists it in each.
                    val merged = ordered((current.instances + instances).distinctBy { it.event to it.start })
                    val grew = merged.size > current.instances.size
                    _uiState.value = current.copy(
                        instances = merged,
                        truncated = current.truncated || truncated,
                        earliest = if (forward) current.earliest else index,
                        latest = if (forward) index else current.latest,
                    )
                } while (!forward && !grew && hasEarlier(_uiState.value))
                _uiState.value = _uiState.value.copy(paging = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(paging = false, stalled = Stalled(e.toMochiError(), forward))
            }
        }
    }

    /** Tries the list page that failed again. */
    fun resume() {
        val stalled = _uiState.value.stalled ?: return
        _uiState.value = _uiState.value.copy(stalled = null)
        paginate(stalled.forward)
    }

    /**
     * Occurrences in the order every view draws them, each in the colour it
     * is drawn in: its event's own, or its calendar's when it has none or
     * one that will not parse.
     */
    private fun ordered(instances: List<Instance>): List<Instance> {
        val colours = _uiState.value.calendars.associate { it.id to it.colour }
        return instances
            .map { it.copy(colour = tint(it.colour, colours[it.calendar])) }
            .sortedWith(compareBy<Instance> { it.start }.thenByDescending { it.allday }.thenBy(NaturalCompare) { it.summary })
    }

    // ---- the toolbar ----

    fun view(value: String) {
        if (value == _uiState.value.view) return
        // A search belongs to the list; leaving it lets it go, as the web does.
        _uiState.value = _uiState.value.copy(
            view = value,
            search = if (value == CalendarsSection.LIST) _uiState.value.search else "",
        )
        remember(value)
        share(value)
        load()
    }

    /** Search from the toolbar: the list, where results are, with the cursor in its box. */
    fun seek() {
        view(CalendarsSection.LIST)
        _uiState.value = _uiState.value.copy(seeking = _uiState.value.seeking + 1)
    }

    /**
     * Saves the view as the one a new browser or device opens on. This
     * device keeps its own either way, so a failure changes nothing it shows.
     */
    private fun share(view: String) {
        val request = sharedView(view, _uiState.value.preferences.view) ?: return
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(preferences = repository.setPreferences(request))
            } catch (_: Exception) {
            }
        }
    }

    /** Records the view so the next launch on this device opens on it. */
    private fun remember(view: String) {
        untouched = false
        LastViewedStore.set(context, CALENDARS_FEATURE, view)
    }

    fun today() {
        anchor(LocalDate.now(zone))
    }

    fun anchor(date: LocalDate) {
        if (date == _uiState.value.anchor) return
        _uiState.value = _uiState.value.copy(anchor = date)
        load()
    }

    fun previous() = anchor(step(_uiState.value.view, _uiState.value.anchor, -1))

    fun next() = anchor(step(_uiState.value.view, _uiState.value.anchor, 1))

    fun workweek(value: Boolean) {
        VisibilityStore.workweek(context, value)
        _uiState.value = _uiState.value.copy(workweek = value)
    }

    fun search(value: String) {
        _uiState.value = _uiState.value.copy(search = value)
    }

    // ---- the drawer ----

    fun toggle(calendar: String) {
        VisibilityStore.toggle(context, calendar)
    }

    fun only(calendar: String) {
        VisibilityStore.only(context, calendar, _uiState.value.calendars.map { it.id })
    }

    override fun onCleared() {
        unwatch()
        retained?.delete()
    }

    /**
     * Redraws for the calendars now shown. Only shown calendars are fetched,
     * so a calendar shown again is fetched now, where the list keeps the
     * pages already scrolled to; what is loaded is filtered meanwhile, so a
     * hidden calendar leaves at once.
     */
    private fun shade() {
        _uiState.value = _uiState.value.copy(hidden = VisibilityStore.hidden(context))
        load(refreshing = true, reset = false)
    }

    /** The calendars the views are drawing, for the actions that take a list. */
    private fun shown(): List<String> {
        val state = _uiState.value
        return state.calendars.map { it.id }.filterNot { it in state.hidden }
    }

    /** Another server's changes, pulled in as the screen comes into view. */
    fun refresh() {
        viewModelScope.launch {
            try {
                repository.refreshCalendars()
            } catch (e: Exception) {
                // The scheduled poll carries on; nothing to tell the user.
            }
        }
    }

    /**
     * Fetches a subscription now. A fetch that fails is still a successful
     * poll — the server records why on the calendar rather than refusing the
     * call — so the drawer's line under the calendar is only right once the
     * list has been read again, which the repository's own announcement does.
     */
    fun poll(calendar: String) {
        viewModelScope.launch {
            try {
                _events.tryEmit(CalendarEvent.Polled(repository.pollCalendar(calendar).calendar.linked))
            } catch (e: Exception) {
                _events.tryEmit(CalendarEvent.Failed(e.toMochiError()))
            }
        }
    }

    /** Renames a calendar; [done] runs once it is renamed, which closes its dialog. */
    fun rename(calendar: String, name: String, done: () -> Unit = {}) = act(done) {
        repository.renameCalendar(calendar, name)
        R.string.calendars_renamed
    }

    fun recolour(calendar: String, colour: String, done: () -> Unit = {}) = act(done) {
        repository.recolourCalendar(calendar, colour)
        R.string.calendars_colour_saved
    }

    /** Deletes a calendar, or removes a subscription or linked one, whose events stay at their source. */
    fun remove(calendar: Calendar, done: () -> Unit = {}) = act(done) {
        repository.deleteCalendar(calendar.id)
        if (calendar.linked || calendar.subscription) R.string.calendars_removed else R.string.calendars_deleted
    }

    fun preferences(value: Preferences, done: () -> Unit = {}) = act(done) {
        val saved = repository.setPreferences(
            org.mochios.calendars.api.PreferencesRequest(
                hours = value.hours,
                days = value.days,
                multiweek = value.multiweek,
                duration = value.duration,
                reminder = value.reminder,
                zones = value.zones,
                // Blank only when no calendar could be offered, which is no choice.
                calendar = value.calendar.ifEmpty { null },
                allday = value.allday,
            ),
        )
        _uiState.value = _uiState.value.copy(preferences = saved)
        load(refreshing = true)
        R.string.calendars_preferences_saved
    }

    // ---- moving an occurrence ----

    /** How to put the last move back, until another move replaces it. */
    private var undo: (suspend () -> Unit)? = null

    /**
     * Moves, and their undoing, one at a time: each reads the event and writes
     * it back, so a second started while the first is saving would read the
     * copy the first is replacing and be refused as changed elsewhere. The
     * web queues them the same way.
     */
    private val writing = Mutex()

    /**
     * A block dragged or resized in the day and week views: the occurrence
     * now runs from [start] to [finish], epoch seconds. The stored event
     * moves by as far as the occurrence did, so a whole series shifts
     * rather than jumping onto the occurrence, and takes the new length.
     */
    fun move(instance: Instance, start: Long, finish: Long, scope: Scope) = rewrite(instance, scope) { form ->
        val begins = instant(form, zone.id) + (start - instance.start)
        form.copy(allday = false, start = begins, finish = begins + (finish - start))
    }

    /**
     * A chip dragged onto another day in the month and multiweek views: the
     * occurrence's first day is now [day], and the stored event moves by as
     * many days, each end keeping its clock reading.
     */
    fun move(instance: Instance, day: LocalDate, scope: Scope) = rewrite(instance, scope) { form ->
        advanced(form, ChronoUnit.DAYS.between(day(instance), day))
    }

    /**
     * Where a drag in the day and week views put an occurrence: a block to a
     * new time, a bar to another day, a block into the all-day band, where
     * it becomes all day over as many days as it covered, or a bar into the
     * grid, where it becomes timed and as long as a new event.
     */
    fun move(instance: Instance, moved: Moved, scope: Scope) {
        val first = day(instance)
        when (moved) {
            is Moved.Time -> move(instance, moved.start, moved.finish, scope)
            is Moved.Days -> move(instance, moved.first, scope)
            is Moved.Whole -> rewrite(instance, scope) { form ->
                // The form's first day read in the zone the views put the
                // occurrence on its days in, so the shift lands where it was dropped.
                val zone = if (zones) zoneOf(form.zone.start, this.zone) else this.zone
                whole(form, ChronoUnit.DAYS.between(first, moved.first), ChronoUnit.DAYS.between(first, finish(instance)) + 1, zone)
            }
            is Moved.Timed -> rewrite(instance, scope) { form ->
                val length = 60L * maxOf(15, _uiState.value.preferences.duration)
                clocked(form, ChronoUnit.DAYS.between(first, moved.day), moved.hours, length, zone)
            }
        }
    }

    /**
     * Rewrites the stored event for a drag. The event is read first: a move
     * rewrites the same component the editor would, so a series keeps its
     * rule and an override keeps being an override. The draft a [change]
     * starts from is the whole series' master, one occurrence's own override
     * or, failing one, the master moved onto the occurrence, so a change by
     * a day or an hour lands where the occurrence is rather than where the
     * series began. Every write says what it did, with a way back.
     */
    private fun rewrite(instance: Instance, scope: Scope, change: (EventForm) -> EventForm) {
        viewModelScope.launch {
            writing.withLock { rewriting(instance, scope, change) }
        }
    }

    private suspend fun rewriting(instance: Instance, scope: Scope, change: (EventForm) -> EventForm) {
        try {
            val event = repository.getEvent(instance.event)
            val master = event.master() ?: return
            val user = zone.id
            val key = instance.occurrence
            val restore: suspend (String) -> Unit = { etag ->
                repository.updateEvent(event.id, etag, null, event.components)
            }

            // This occurrence and the ones after it: the series is cut
            // there. The first occurrence has nothing before it, so that
            // is the whole series.
            var chosen = scope
            if (chosen == Scope.FOLLOWING && event.recurring) {
                val halves = split(event.components, change(draft(master, user, key)), key, user)
                if (halves != null) {
                    val (kept, following) = repository.splitEvent(
                        event.id,
                        event.etag,
                        instance.start,
                        halves.first,
                        halves.second,
                    )
                    undo = {
                        repository.deleteEvent(following.id, following.etag)
                        restore(kept.etag)
                    }
                    _events.tryEmit(CalendarEvent.Moved)
                    return
                }
                chosen = Scope.ALL
            }

            val override = event.overrides().firstOrNull { matches(it, key) }
            val base = when {
                chosen == Scope.ALL || !event.recurring -> draft(master, user)
                override != null -> draft(override, user)
                else -> draft(master, user, key)
            }
            val form = change(base).copy(occurrence = if (chosen == Scope.ONE) key else 0)
            val components = components(form, event.components, if (event.recurring) chosen else Scope.ALL, user)
            val written = repository.updateEvent(event.id, event.etag, null, components)
            undo = { restore(written.etag) }
            _events.tryEmit(CalendarEvent.Moved)
        } catch (_: EventChangedException) {
            _events.tryEmit(CalendarEvent.Changed)
        } catch (e: Exception) {
            _events.tryEmit(CalendarEvent.Failed(e.toMochiError()))
        }
    }

    /** Puts the last move back: a split's new event goes, and the old one is restored. */
    fun undo() {
        val restore = undo ?: return
        undo = null
        viewModelScope.launch {
            try {
                writing.withLock { restore() }
            } catch (_: EventChangedException) {
                _events.tryEmit(CalendarEvent.Changed)
            } catch (e: Exception) {
                _events.tryEmit(CalendarEvent.Failed(e.toMochiError()))
            }
        }
    }

    /** Puts back what the editor last deleted, as its Undo asks. */
    fun restore() {
        val back = repository.restoring() ?: return
        viewModelScope.launch {
            try {
                back()
            } catch (_: EventChangedException) {
                _events.tryEmit(CalendarEvent.Changed)
            } catch (e: Exception) {
                _events.tryEmit(CalendarEvent.Failed(e.toMochiError()))
            }
        }
    }

    /** One call whose only outcome that matters is whether it failed. */
    private val _working = MutableStateFlow(false)

    /** Whether the calendar action a dialog waits on is under way; the dialog stays open until it succeeds. */
    val working: StateFlow<Boolean> = _working.asStateFlow()

    /**
     * Runs a calendar action, saying what it did when it succeeds, which
     * [request] answers with, and then [done]; a failure says why and leaves
     * the dialog open to try again.
     */
    private fun act(done: () -> Unit, request: suspend () -> Int) {
        if (_working.value) return
        viewModelScope.launch {
            _working.value = true
            try {
                _events.tryEmit(CalendarEvent.Done(request()))
                done()
            } catch (e: Exception) {
                _events.tryEmit(CalendarEvent.Failed(e.toMochiError()))
            } finally {
                _working.value = false
            }
        }
    }

    // ---- the ICS link ----

    private val _link = MutableStateFlow(LinkState())

    /** The calendar's ICS address, while its dialog is open. */
    val link: StateFlow<LinkState> = _link.asStateFlow()

    /**
     * Asks for the calendar's ICS address. The first call mints it and it is
     * shown once; later calls only say one exists, until [regenerate] revokes
     * the old address and mints another.
     */
    fun openLink(calendar: String, regenerate: Boolean = false) {
        if (_link.value.busy) return
        if (!regenerate && _link.value.calendar == calendar) return
        // A replace keeps the "already issued" state it started from, so a
        // failed one leaves the address, and its Replace, as they were.
        val issued = _link.value.calendar == calendar && _link.value.exists
        viewModelScope.launch {
            _link.value = LinkState(calendar = calendar, busy = true, exists = issued)
            try {
                val answer = repository.link(calendar, regenerate)
                val base = server()
                _link.value = LinkState(
                    calendar = calendar,
                    url = answer.token.takeIf { it.isNotBlank() }
                        ?.let { "$base${answer.path}?token=$it" },
                    exists = answer.exists || answer.token.isNotBlank(),
                )
            } catch (e: Exception) {
                _link.value = LinkState(calendar = calendar, exists = issued)
                _events.tryEmit(CalendarEvent.Failed(e.toMochiError()))
            }
        }
    }

    fun closeLink() {
        _link.value = LinkState()
    }

    /** Revokes a calendar's address, saying whether there was one to revoke. */
    fun revokeLink(calendar: String, done: () -> Unit = {}) = act(done) {
        if (repository.revokeLink(calendar)) R.string.calendars_link_revoked else R.string.calendars_link_none
    }

    /** The server the link's address is built on, as the session holds it. */
    private suspend fun server(): String = viewer.server().trimEnd('/')

    // ---- importing and exporting ----

    private val _importing = MutableStateFlow<Tally?>(null)

    /** The import under way, just finished, or failed, while its dialog is open. */
    val importing: StateFlow<Tally?> = _importing.asStateFlow()

    /** The staged copy of a file whose import failed, kept while its dialog offers to try again. */
    private var retained: File? = null

    /**
     * Imports the iCalendar file at [uri] into [calendar], round by round,
     * the dialog following each. The file is copied into the cache first, so
     * the first round uploads from a copy the picker's grant cannot take
     * away. A failure keeps the dialog open on what went wrong, as the web's
     * does, with the copy kept to [retryImport]; the events any round wrote
     * are shown whether the import finished or not.
     */
    fun import(calendar: Calendar, uri: Uri) {
        if (_importing.value != null) return
        _importing.value = Tally(calendar = calendar.id, name = calendar.name)
        viewModelScope.launch {
            // The name it is staged under when its provider gives none.
            val file = runCatching { repository.stageFile(uri, "calendar.ics") }.getOrNull()
            if (file == null) {
                _importing.value = null
                _events.tryEmit(CalendarEvent.Unreadable)
                return@launch
            }
            transfer(file)
        }
    }

    /**
     * Imports a failed file again from its start. What the failed try wrote
     * is in the calendar already, so it counts as skipped rather than twice.
     */
    fun retryImport() {
        val failed = _importing.value ?: return
        val file = retained ?: return
        if (failed.error == null) return
        retained = null
        _importing.value = Tally(calendar = failed.calendar, name = failed.name)
        viewModelScope.launch { transfer(file) }
    }

    /** Runs the import the dialog shows, from [file], then redraws. */
    private suspend fun transfer(file: File) {
        val start = _importing.value ?: return
        var kept = false
        try {
            rounds(
                file,
                start,
                round = { part, staged, offset -> repository.importRound(start.calendar, part, staged, offset) },
                progress = { _importing.value = it },
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            retained = file
            kept = true
            _importing.value = (_importing.value ?: start).copy(error = e.toMochiError())
        } finally {
            if (!kept) withContext(NonCancellable) { repository.discardStaged(listOf(file)) }
        }
        load(refreshing = true, reset = false)
    }

    /** Closes a finished or failed import's dialog, letting go of a failed file's copy. */
    fun closeImport() {
        val tally = _importing.value ?: return
        if (!tally.finished && tally.error == null) return
        _importing.value = null
        retained?.let { file ->
            retained = null
            viewModelScope.launch { withContext(NonCancellable) { repository.discardStaged(listOf(file)) } }
        }
    }

    /** The export fetched and waiting for the user to say where it goes. */
    private var exported: PendingExport? = null

    /**
     * Fetches [calendar] as an iCalendar file, then asks where to save it,
     * offering the calendar's name. Fetched first, so a refusal is said
     * before the user has chosen a place for a file that will not come.
     */
    fun export(calendar: Calendar) {
        viewModelScope.launch {
            try {
                val text = repository.exportCalendar(calendar.id)
                val pending = PendingExport(repository.exportDisplayName(calendar.name, "ics"), Icalendar.TYPE, text)
                exported = pending
                _events.tryEmit(CalendarEvent.Save(pending.suggestedName))
            } catch (e: Exception) {
                _events.tryEmit(CalendarEvent.Failed(e.toMochiError()))
            }
        }
    }

    /** Writes the waiting export to [uri], or drops it when the user chose nowhere. */
    fun save(uri: Uri?) {
        val pending = exported ?: return
        exported = null
        if (uri == null) return
        viewModelScope.launch {
            _events.tryEmit(CalendarEvent.Exported(repository.saveTextFile(uri, pending.content.orEmpty())))
        }
    }

    // ---- new events ----

    /**
     * Where a new event with no time of its own starts, in epoch seconds: on
     * [day] when a day cell was tapped, and otherwise on the day "New event"
     * lands on, today when today is on screen and else the day the view is on.
     */
    fun creation(day: LocalDate? = null, state: CalendarUiState = _uiState.value): Long {
        val now = ZonedDateTime.now(zone)
        val today = now.toLocalDate()
        val chosen = day ?: run {
            // The list's page reaches months ahead, so it is the day it is on
            // that counts there, as the web's list does.
            if (state.view == CalendarsSection.LIST) return@run creationDay(today, state.anchor, state.anchor, 1)
            val (first, last) = range(state)
            val from = Instant.ofEpochSecond(first).atZone(zone).toLocalDate()
            val until = Instant.ofEpochSecond(last).atZone(zone).toLocalDate()
            creationDay(today, state.anchor, from, ChronoUnit.DAYS.between(from, until))
        }
        return defaultStart(chosen, today, now.toLocalTime(), state.preferences.hours).atZone(zone).toEpochSecond()
    }

    // ---- ranges ----

    /**
     * The half-open range the current view covers, as epoch seconds in the
     * user's zone. A month view draws six whole weeks, so its range is the
     * grid's rather than the month's.
     */
    fun range(state: CalendarUiState = _uiState.value): Pair<Long, Long> {
        val start: LocalDate
        val days: Long
        when (state.view) {
            CalendarsSection.DAY -> {
                start = state.anchor
                days = 1
            }
            CalendarsSection.WEEK -> {
                start = week(state.anchor)
                days = 7
            }
            CalendarsSection.MULTIWEEK -> {
                start = week(state.anchor).minusWeeks(state.preferences.multiweek.previous.toLong())
                days = 7L * state.preferences.multiweek.weeks
            }
            CalendarsSection.MONTH -> {
                start = week(state.anchor.withDayOfMonth(1))
                days = 42
            }
            // The list view opens on the anchor day and pages on from there.
            else -> {
                start = state.anchor
                days = PAGE
            }
        }
        val from = start.atStartOfDay(zone).toEpochSecond()
        val until = start.plusDays(days).atStartOfDay(zone).toEpochSecond()
        return from to until
    }

    /** The first day of the week [date] falls in, by the user's week start. */
    fun week(date: LocalDate): LocalDate {
        // DayOfWeek counts Monday 1 to Sunday 7; the preference counts Sunday
        // 0 to Saturday 6, which is how the server's work days are indexed.
        val current = date.dayOfWeek.value % 7
        val back = ((current - weekStart) + 7) % 7
        return date.minusDays(back.toLong())
    }

    /** The first day of each week the view draws, in order. */
    fun weeks(state: CalendarUiState = _uiState.value): List<LocalDate> = when (state.view) {
        CalendarsSection.WEEK -> listOf(week(state.anchor))
        CalendarsSection.MULTIWEEK -> {
            val first = week(state.anchor).minusWeeks(state.preferences.multiweek.previous.toLong())
            (0 until state.preferences.multiweek.weeks).map { first.plusWeeks(it.toLong()) }
        }
        CalendarsSection.MONTH -> {
            val first = week(state.anchor.withDayOfMonth(1))
            (0 until 6).map { first.plusWeeks(it.toLong()) }
        }
        else -> listOf(week(state.anchor))
    }

    /**
     * The day an occurrence belongs to: an all-day one's own date, a timed
     * one's start day in the user's zone, or in its own zone when the views
     * show events in theirs.
     */
    fun day(instance: Instance): LocalDate = when {
        instance.date != null -> runCatching { LocalDate.parse(instance.date) }
            .getOrElse { days(instance, zone, zones).first }
        else -> days(instance, zone, zones).first
    }

    /** The last day an occurrence covers, for a chip stretched across days. */
    fun finish(instance: Instance): LocalDate {
        if (instance.date != null) return last(day(instance), instance.start, instance.finish)
        return days(instance, zone, zones).second
    }

    /** The block a timed occurrence puts on [day], null when it does not touch the day. */
    fun cut(instance: Instance, day: LocalDate): Cut? = cut(instance, day, zone, zones)

    /** Whether an occurrence is over, which the views draw faded. */
    fun past(instance: Instance): Boolean =
        past(instance, finish(instance), Instant.now().epochSecond, LocalDate.now(zone))

    /** Whether an occurrence touches [day], a multi-day one on every day it spans. */
    fun covers(instance: Instance, day: LocalDate): Boolean {
        val from = day(instance)
        val until = finish(instance)
        return !day.isBefore(from) && !day.isAfter(until)
    }

    /** Opens the day view on a date, from a column heading or a month cell. */
    fun open(date: LocalDate) {
        _uiState.value = _uiState.value.copy(anchor = date, view = CalendarsSection.DAY, search = "")
        remember(CalendarsSection.DAY)
        share(CalendarsSection.DAY)
        load()
    }

    /** The zone the screen draws in, for the views' own arithmetic. */
    fun timezone(): ZoneId = zone

    /** Whether the views show each event in its own zones, for their clock text. */
    fun zones(): Boolean = zones

    /** The first day of the week, for the views' column headers. */
    fun start(): Int = weekStart
}

/**
 * The anchor one step forward (1) or back (-1) by the view's own unit. The
 * multiweek view steps a week at a time, so its span slides a row rather than
 * jumping its length; the list view pages as the reader scrolls, so its arrows
 * move a month at a time rather than by a range it no longer has. A month's
 * step lands on its 1st, as the web's does, so the list opens on the month's
 * first day rather than a day carried over from the last one.
 */
fun step(view: String, anchor: LocalDate, direction: Int): LocalDate = when (view) {
    CalendarsSection.DAY -> anchor.plusDays(direction.toLong())
    CalendarsSection.WEEK, CalendarsSection.MULTIWEEK -> anchor.plusWeeks(direction.toLong())
    else -> anchor.withDayOfMonth(1).plusMonths(direction.toLong())
}

/**
 * The last day an all-day occurrence covers: its date plus its whole days less
 * one. By the date and the day count rather than the finish instant, so a
 * device in another zone than the server expanded in does not draw it a day
 * out.
 */
fun last(date: LocalDate, start: Long, finish: Long): LocalDate =
    date.plusDays(maxOf(1L, Math.round((finish - start) / 86400.0)) - 1)

/**
 * Whether an occurrence is over: a timed one once its finish, epoch seconds,
 * is before [now], and an all-day one once its [last] day is before [today].
 * A timed one with no length is over once its start is.
 */
fun past(instance: Instance, last: LocalDate, now: Long, today: LocalDate): Boolean =
    if (instance.allday) last.isBefore(today) else maxOf(instance.start, instance.finish) < now
