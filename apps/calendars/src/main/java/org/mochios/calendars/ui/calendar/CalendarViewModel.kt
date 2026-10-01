// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.PreferencesManager
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.util.NaturalCompare
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
import org.mochios.calendars.ui.editor.defaultStart
import org.mochios.calendars.ui.editor.draft
import org.mochios.calendars.ui.editor.instant
import org.mochios.calendars.ui.editor.matches
import org.mochios.calendars.ui.editor.split
import org.mochios.calendars.ui.router.CALENDARS_FEATURE
import org.mochios.calendars.ui.router.CalendarsSection
import org.mochios.calendars.ui.router.calendarsView
import org.mochios.calendars.ui.router.sharedView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * The calendar screen. [anchor] is the date the view is built around,
 * [focus] the day the user last chose, which the date panel circles and a new
 * event lands on, and [hidden] the calendars this device does not show,
 * which is a viewing choice and never leaves the phone. [instances] is
 * everything the server returned for the range, unfiltered, so flipping a
 * checkbox redraws without a fetch.
 */
data class CalendarUiState(
    val view: String = CalendarsSection.MONTH,
    val anchor: LocalDate = LocalDate.now(),
    val focus: LocalDate = LocalDate.now(),
    val calendars: List<Calendar> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val instances: List<Instance> = emptyList(),
    val truncated: Boolean = false,
    val preferences: Preferences = Preferences(),
    val workweek: Boolean = false,
    val search: String = "",
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
) {
    /** How many pages the list view is holding. */
    val pages: Int get() = latest - earliest + 1
    /** The occurrences the views draw. */
    val visible: List<Instance> get() = instances.filterNot { it.calendar in hidden }
}

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
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: CalendarsRepository,
    private val preferencesManager: PreferencesManager,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState(hidden = VisibilityStore.hidden(context)))
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CalendarEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CalendarEvent> = _events.asSharedFlow()

    private var loading: Job? = null

    /** The zone every range is measured in: the user's, not the device's. */
    private val zone: ZoneId
        get() = runCatching { ZoneId.of(preferencesManager.preferences.value.timezone) }
            .getOrDefault(ZoneId.systemDefault())

    /** The first day of a week for this user: 0 Sunday, 1 Monday, 6 Saturday. */
    private val weekStart: Int get() = preferencesManager.preferences.value.weekStartsOn

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
            )
            try {
                if (state.view == CalendarsSection.LIST) {
                    pages(state, reset)
                } else {
                    // The periods either side are read too, so a swipe
                    // brings its neighbour in already drawn.
                    val (start, _) = range(state.copy(anchor = step(state.view, state.anchor, -1)))
                    val (_, finish) = range(state.copy(anchor = step(state.view, state.anchor, 1)))
                    // With events shown in their own zones, a day's
                    // occurrences can begin or end up to a day away by the
                    // user's clock, so the range reaches a day each side.
                    val margin = if (state.preferences.zones) 86_400L else 0L
                    val (instances, truncated) = repository.listEvents(start - margin, finish + margin, emptyList(), zone.id)
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false, error = e.toMochiError())
            }
        }
    }

    /**
     * The list view's pages: how far the shown calendars reach, and then each
     * page held. A page is a quarter, so even the whole cap's worth is read a
     * page at a time — the server lists at most a year in one call.
     */
    private suspend fun pages(state: CalendarUiState, reset: Boolean) {
        val first = if (reset) 0 else state.earliest
        val last = if (reset) 0 else state.latest
        val bounds = runCatching { repository.eventBounds(shown()) }.getOrDefault(Bounds())
        val gathered = mutableListOf<Instance>()
        var truncated = false
        for (index in first..last) {
            val span = page(state.anchor, index, zone)
            val (instances, cut) = repository.listEvents(span.start, span.finish, emptyList(), zone.id)
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
        earlier(state.anchor, state.earliest, state.bounds, zone, state.pages)

    fun hasLater(state: CalendarUiState = _uiState.value): Boolean =
        later(state.anchor, state.latest, state.bounds, zone, state.pages)

    private fun paginate(forward: Boolean) {
        val state = _uiState.value
        if (state.paging || state.isLoading) return
        if (if (forward) !hasLater(state) else !hasEarlier(state)) return
        val index = if (forward) state.latest + 1 else state.earliest - 1
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(paging = true)
            try {
                val span = page(state.anchor, index, zone)
                val (instances, truncated) = repository.listEvents(span.start, span.finish, emptyList(), zone.id)
                val current = _uiState.value
                _uiState.value = current.copy(
                    // Merged rather than appended: a multi-day occurrence
                    // reaches into both pages and the server lists it in each.
                    instances = ordered((current.instances + instances).distinctBy { it.event to it.start }),
                    truncated = current.truncated || truncated,
                    earliest = if (forward) current.earliest else index,
                    latest = if (forward) index else current.latest,
                    paging = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(paging = false)
                _events.tryEmit(CalendarEvent.Failed(e.toMochiError()))
            }
        }
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
        _uiState.value = _uiState.value.copy(view = value)
        remember(value)
        share(value)
        load()
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
        if (date == _uiState.value.anchor) {
            focus(date)
            return
        }
        _uiState.value = _uiState.value.copy(anchor = date, focus = date)
        load()
    }

    /** Chooses a day the view already shows, without moving the view. */
    fun focus(date: LocalDate) {
        if (date == _uiState.value.focus) return
        _uiState.value = _uiState.value.copy(focus = date)
    }

    fun workweek(value: Boolean) {
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
    }

    /**
     * Redraws for the calendars now shown. The views filter what is already
     * loaded, so nothing is fetched again — except the list view's bounds,
     * which say how far it may page and are about the shown calendars alone.
     */
    private fun shade() {
        _uiState.value = _uiState.value.copy(hidden = VisibilityStore.hidden(context))
        if (_uiState.value.view != CalendarsSection.LIST) return
        viewModelScope.launch {
            val bounds = runCatching { repository.eventBounds(shown()) }.getOrNull() ?: return@launch
            _uiState.value = _uiState.value.copy(bounds = bounds)
        }
    }

    /** The calendars the views are drawing, for the actions that take a list. */
    private fun shown(): List<String> {
        val state = _uiState.value
        return state.calendars.map { it.id }.filterNot { it in state.hidden }
    }

    /**
     * Fetches a subscription now. A fetch that fails is still a successful
     * poll — the server records why on the calendar rather than refusing the
     * call — so the drawer's line under the calendar is only right once the
     * list has been read again, which the repository's own announcement does.
     */
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

    fun poll(calendar: String) {
        viewModelScope.launch {
            try {
                _events.tryEmit(CalendarEvent.Polled(repository.pollCalendar(calendar).calendar.linked))
            } catch (e: Exception) {
                _events.tryEmit(CalendarEvent.Failed(e.toMochiError()))
            }
        }
    }

    /**
     * Deletes the occurrence the summary sheet is showing. A recurring one is
     * taken out of its series by an exclusion; anything else goes outright.
     * The etag comes from the server rather than the occurrence, which does
     * not carry one.
     */
    fun rename(calendar: String, name: String) = act { repository.renameCalendar(calendar, name) }

    fun recolour(calendar: String, colour: String) = act { repository.recolourCalendar(calendar, colour) }

    fun remove(calendar: String) = act { repository.deleteCalendar(calendar) }

    fun preferences(value: Preferences) = act {
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
    }

    // ---- moving an occurrence ----

    /** How to put the last move back, until another move replaces it. */
    private var undo: (suspend () -> Unit)? = null

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
            try {
                val event = repository.getEvent(instance.event)
                val master = event.master() ?: return@launch
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
                        return@launch
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
    }

    /** Puts the last move back: a split's new event goes, and the old one is restored. */
    fun undo() {
        val restore = undo ?: return
        undo = null
        viewModelScope.launch {
            try {
                restore()
            } catch (_: EventChangedException) {
                _events.tryEmit(CalendarEvent.Changed)
            } catch (e: Exception) {
                _events.tryEmit(CalendarEvent.Failed(e.toMochiError()))
            }
        }
    }

    /** One call whose only outcome that matters is whether it failed. */
    private inline fun act(crossinline request: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                request()
            } catch (e: Exception) {
                _events.tryEmit(CalendarEvent.Failed(e.toMochiError()))
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
        viewModelScope.launch {
            _link.value = LinkState(calendar = calendar, busy = true)
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
                _link.value = LinkState(calendar = calendar)
                _events.tryEmit(CalendarEvent.Failed(e.toMochiError()))
            }
        }
    }

    fun closeLink() {
        _link.value = LinkState()
    }

    fun revokeLink(calendar: String) {
        closeLink()
        act { repository.revokeLink(calendar) }
    }

    /** The server the link's address is built on, as the session holds it. */
    private suspend fun server(): String = sessionManager.serverUrl.first().trimEnd('/')

    // ---- new events ----

    /**
     * Where a new event with no time of its own starts, in epoch seconds: on
     * [day] when a day cell was tapped, and otherwise on the day the user last
     * chose, which is today until they pick, tap or page to another.
     */
    fun creation(day: LocalDate? = null, state: CalendarUiState = _uiState.value): Long {
        val now = ZonedDateTime.now(zone)
        val today = now.toLocalDate()
        val chosen = day ?: state.focus
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

    /** The days the week view draws: the anchor's week, less the days off in a work week. */
    fun days(state: CalendarUiState = _uiState.value): List<LocalDate> {
        val start = week(state.anchor)
        return (0 until 7).map { offset -> start.plusDays(offset.toLong()) }
            .filter { day ->
                !state.workweek || state.preferences.days.contains(day.dayOfWeek.value % 7)
            }
            .ifEmpty { listOf(state.anchor) }
    }

    /**
     * The first day the view shows, whose month the toolbar names while the
     * date panel is closed. The month view names its own month rather than
     * the previous month's days that lead its grid.
     */
    fun first(state: CalendarUiState = _uiState.value): LocalDate = when (state.view) {
        CalendarsSection.WEEK -> days(state).first()
        CalendarsSection.MULTIWEEK -> weeks(state).first()
        CalendarsSection.MONTH -> state.anchor.withDayOfMonth(1)
        else -> state.anchor
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
        _uiState.value = _uiState.value.copy(
            anchor = date,
            focus = date,
            view = CalendarsSection.DAY,
        )
        remember(CalendarsSection.DAY)
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
 * The anchor [direction] steps forward (positive) or back (negative) by the
 * view's own unit, as a swipe pages it. The multiweek view steps a week at a
 * time, so its span slides a row rather than jumping its length; the list
 * view, which pages as the reader scrolls, counts in months.
 */
fun step(view: String, anchor: LocalDate, direction: Int): LocalDate = when (view) {
    CalendarsSection.DAY -> anchor.plusDays(direction.toLong())
    CalendarsSection.WEEK, CalendarsSection.MULTIWEEK -> anchor.plusWeeks(direction.toLong())
    else -> anchor.plusMonths(direction.toLong())
}

/**
 * How many of the view's own steps [to] lies from [from], the inverse of
 * [step]: days in the day view, weeks in the week and multiweek views, and
 * months otherwise. [week] gives the first day of a date's week, so two days
 * of the same week are no steps apart.
 */
fun steps(
    view: String,
    from: LocalDate,
    to: LocalDate,
    week: (LocalDate) -> LocalDate,
): Int = when (view) {
    CalendarsSection.DAY -> ChronoUnit.DAYS.between(from, to)
    CalendarsSection.WEEK, CalendarsSection.MULTIWEEK ->
        ChronoUnit.WEEKS.between(week(from), week(to))
    else -> ChronoUnit.MONTHS.between(from.withDayOfMonth(1), to.withDayOfMonth(1))
}.toInt()

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
