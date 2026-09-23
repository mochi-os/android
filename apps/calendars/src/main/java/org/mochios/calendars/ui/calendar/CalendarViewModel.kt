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
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.storage.VisibilityStore
import org.mochios.calendars.ui.router.CALENDARS_FEATURE
import org.mochios.calendars.ui.router.CalendarsSection
import org.mochios.calendars.ui.router.calendarsView
import java.time.LocalDate
import java.time.ZoneId
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

/** Whether a delete takes one occurrence or the whole series. */
/** Something the screen has to say once rather than hold in its state. */
sealed class CalendarEvent {
    data class Failed(val error: MochiError) : CalendarEvent()
    data class Polled(val changed: Int) : CalendarEvent()
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

    /** True until the user has picked a view on this device. */
    private var untouched = LastViewedStore.get(context, CALENDARS_FEATURE).isNullOrBlank()

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
                    val (start, finish) = range(state)
                    val (instances, truncated) = repository.listEvents(start, finish, emptyList())
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
            val (instances, cut) = repository.listEvents(span.start, span.finish, emptyList())
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
                val (instances, truncated) = repository.listEvents(span.start, span.finish, emptyList())
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

    /** Occurrences in the order every view draws them. */
    private fun ordered(instances: List<Instance>): List<Instance> = instances.sortedWith(
        compareBy<Instance> { it.start }.thenByDescending { it.allday }.thenBy(NaturalCompare) { it.summary },
    )

    // ---- the toolbar ----

    fun view(value: String) {
        if (value == _uiState.value.view) return
        _uiState.value = _uiState.value.copy(view = value)
        remember(value)
        load()
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
        _uiState.value = _uiState.value.copy(workweek = value)
    }

    fun search(value: String) {
        _uiState.value = _uiState.value.copy(search = value)
    }

    // ---- the drawer ----

    fun toggle(calendar: String) {
        VisibilityStore.toggle(context, calendar)
        shade()
    }

    fun only(calendar: String) {
        VisibilityStore.only(context, calendar, _uiState.value.calendars.map { it.id })
        shade()
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
    fun poll(calendar: String) {
        viewModelScope.launch {
            try {
                _events.tryEmit(CalendarEvent.Polled(repository.pollCalendar(calendar)))
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
            ),
        )
        _uiState.value = _uiState.value.copy(preferences = saved)
        load(refreshing = true)
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

    /** The day an occurrence belongs to, in the user's zone. */
    fun day(instance: Instance): LocalDate = when {
        instance.date != null -> runCatching { LocalDate.parse(instance.date) }
            .getOrElse { java.time.Instant.ofEpochSecond(instance.start).atZone(zone).toLocalDate() }
        else -> java.time.Instant.ofEpochSecond(instance.start).atZone(zone).toLocalDate()
    }

    /** The last day an occurrence covers, for a chip stretched across days. */
    fun finish(instance: Instance): LocalDate {
        if (instance.date != null) return last(day(instance), instance.start, instance.finish)
        val ends = java.time.Instant.ofEpochSecond(maxOf(instance.finish, instance.start)).atZone(zone)
        // A range that ends exactly at midnight belongs to the day before.
        val date = ends.toLocalDate()
        return if (ends.toLocalTime() == java.time.LocalTime.MIDNIGHT && date.isAfter(day(instance))) {
            date.minusDays(1)
        } else {
            date
        }
    }

    /** Whether an occurrence touches [day], a multi-day one on every day it spans. */
    fun covers(instance: Instance, day: LocalDate): Boolean {
        val from = day(instance)
        val until = finish(instance)
        return !day.isBefore(from) && !day.isAfter(until)
    }

    /** Opens the day view on a date, from a column heading or a month cell. */
    fun open(date: LocalDate) {
        _uiState.value = _uiState.value.copy(anchor = date, view = CalendarsSection.DAY)
        remember(CalendarsSection.DAY)
        load()
    }

    /** The zone the screen draws in, for the views' own arithmetic. */
    fun timezone(): ZoneId = zone

    /** The first day of the week, for the views' column headers. */
    fun start(): Int = weekStart
}

/**
 * The anchor one step forward (1) or back (-1) by the view's own unit. The
 * multiweek view steps a week at a time, so its span slides a row rather than
 * jumping its length; the list view pages as the reader scrolls, so its arrows
 * move a month at a time rather than by a range it no longer has.
 */
fun step(view: String, anchor: LocalDate, direction: Int): LocalDate = when (view) {
    CalendarsSection.DAY -> anchor.plusDays(direction.toLong())
    CalendarsSection.WEEK, CalendarsSection.MULTIWEEK -> anchor.plusWeeks(direction.toLong())
    else -> anchor.plusMonths(direction.toLong())
}

/**
 * The last day an all-day occurrence covers: its date plus its whole days less
 * one. By the date and the day count rather than the finish instant, so a
 * device in another zone than the server expanded in does not draw it a day
 * out.
 */
fun last(date: LocalDate, start: Long, finish: Long): LocalDate =
    date.plusDays(maxOf(1L, Math.round((finish - start) / 86400.0)) - 1)
