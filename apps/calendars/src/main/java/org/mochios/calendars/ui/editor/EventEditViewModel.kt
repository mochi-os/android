// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.editor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.i18n.PreferencesManager
import org.mochios.android.sync.CalendarsMapping
import org.mochios.android.sync.EventComponent
import org.mochios.android.util.NaturalCompare
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.model.Event
import org.mochios.calendars.model.Instance
import org.mochios.calendars.model.Zone
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.repository.EventChangedException
import org.mochios.calendars.storage.VisibilityStore
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * The event editor. [event] is null for a new event, a copy of another
 * included: [copying] says the form was filled from an original, and saving
 * still creates. [occurrence] is the occurrence the user opened, which "This
 * event" detaches, as the event's tree names it: a timed occurrence's own
 * start, epoch seconds, and the UTC midnight of an all-day one's date;
 * [moment] is the same occurrence's start as the server listed it, which a
 * series cut there is told. Both are 0 for a new event or one that does not
 * repeat. [zone] is the zone each end is typed and written in, the user's
 * own for a new event; [revealed] says the user asked to see the zones in
 * this edit, which otherwise show only when an end reads in another zone
 * than their own. [copy] is a copy the user asked for, with how far it
 * reaches, which the screen opens the editor on.
 */
data class EditorUiState(
    val event: String? = null,
    val copying: Boolean = false,
    val occurrence: Long = 0,
    val moment: Long = 0,
    val calendars: List<Calendar> = emptyList(),
    val calendar: String = "",
    val title: String = "",
    val allday: Boolean = false,
    val start: Long = 0,
    val finish: Long = 0,
    val zone: Zone = Zone(),
    val revealed: Boolean = false,
    val location: String = "",
    val description: String = "",
    val recurrence: Recurrence = Recurrence(),
    val reminder: Int = 15,
    val etag: String = "",
    val recurring: Boolean = false,
    /**
     * The master's own start, epoch seconds, when a recurring event is open;
     * the form shows the occurrence, so a change to the whole series moves it
     * by the same amount rather than onto the occurrence's date.
     */
    val series: Long = 0,
    val prompt: Prompt? = null,
    val copy: Scope? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val confirming: Boolean = false,
    val error: MochiError? = null,
    val changed: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
) {
    val writable: Boolean get() = calendars.firstOrNull { it.id == calendar }?.readonly != true
}

/** Which question the screen is asking: "This event or all events?", and why. */
enum class Prompt {
    SAVE,
    DELETE,
    COPY,
}

@HiltViewModel
class EventEditViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: CalendarsRepository,
    private val preferencesManager: PreferencesManager,
    handle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    /** The tree the server last sent, whose unmanaged properties are kept. */
    private var carried: Event? = null

    /** The zone the editor writes in when the event names none. */
    val zone: String
        get() = runCatching { ZoneId.of(preferencesManager.preferences.value.timezone).id }
            .getOrDefault(ZoneId.systemDefault().id)

    init {
        val event = handle.get<String>("event")?.takeIf { it.isNotBlank() && it != "new" }
        val occurrence = handle.get<String>("occurrence")?.toLongOrNull() ?: 0
        val start = handle.get<String>("start")?.toLongOrNull() ?: 0
        when (handle.get<String>("source")) {
            "event" -> copy(
                handle.get<String>("copy").orEmpty(),
                occurrence,
                Scope.entries.firstOrNull { it.name.equals(handle.get<String>("scope"), ignoreCase = true) } ?: Scope.ALL,
            )
            "occurrence" -> {
                val zones = handle.get<String>("zones").orEmpty().split(",")
                copy(
                    Instance(
                        summary = handle.get<String>("summary").orEmpty(),
                        location = handle.get<String>("location").orEmpty(),
                        description = handle.get<String>("description").orEmpty(),
                        start = start,
                        finish = handle.get<String>("finish")?.toLongOrNull() ?: 0,
                        allday = handle.get<String>("allday") == "1",
                        date = handle.get<String>("date")?.takeIf { it.isNotBlank() },
                        zone = Zone(zones.getOrElse(0) { "" }, zones.getOrElse(1) { "" }),
                    ),
                )
            }
            else -> load(event, occurrence, start)
        }
    }

    private fun load(event: String?, occurrence: Long, start: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val calendars = calendars() ?: return@launch
            if (event == null) {
                val preferences = runCatching { repository.getPreferences() }.getOrNull()
                val begins = if (start > 0) start else nextHour()
                val length = 60L * (preferences?.duration ?: 60)
                _uiState.value = EditorUiState(
                    calendars = calendars,
                    calendar = preferred(calendars),
                    start = begins,
                    finish = begins + length,
                    zone = Zone(zone, zone),
                    reminder = preferences?.reminder ?: 15,
                    isLoading = false,
                )
                return@launch
            }
            try {
                fill(repository.getEvent(event), occurrence, calendars)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    /**
     * A copy of the stored [event], opened as a new event whose form is the
     * original's: the occurrence at [occurrence] alone, or the whole series,
     * as [scope] says. It lands in the original's calendar when that can be
     * written to, else where a new event would.
     */
    private fun copy(event: String, occurrence: Long, scope: Scope) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val calendars = calendars() ?: return@launch
            try {
                val loaded = repository.getEvent(event)
                val form = copied(loaded.components, occurrence, scope, zone) ?: EventForm()
                open(form, calendars, calendars.firstOrNull { it.id == loaded.calendar }?.id ?: preferred(calendars))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    /**
     * A copy of an occurrence with no stored event to load - a subscription's
     * or a birthday - opened as a new event from the occurrence as listed,
     * with the reminder a new event gets.
     */
    private fun copy(instance: Instance) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val calendars = calendars() ?: return@launch
            val preferences = runCatching { repository.getPreferences() }.getOrNull()
            open(copied(instance, zone, preferences?.reminder ?: 15), calendars, preferred(calendars))
        }
    }

    /** The editor on a copy's [form], in [calendar]: a new event, so saving creates. */
    private fun open(form: EventForm, calendars: List<Calendar>, calendar: String) {
        _uiState.value = EditorUiState(
            copying = true,
            calendars = calendars,
            calendar = calendar,
            title = form.title,
            allday = form.allday,
            start = form.start,
            finish = form.finish,
            zone = form.zone,
            location = form.location,
            description = form.description,
            recurrence = form.recurrence,
            reminder = form.reminder,
            isLoading = false,
        )
    }

    /** The calendars the editor offers, the default first; null once a failure to list them is shown. */
    private suspend fun calendars(): List<Calendar>? = try {
        repository.listCalendars()
            .filterNot { it.readonly }
            .sortedWith(compareByDescending<Calendar> { it.default }.thenBy(NaturalCompare) { it.name })
    } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
        null
    }

    /** The calendar a new event lands in: the one last written to, else the default, else the first. */
    private fun preferred(calendars: List<Calendar>): String =
        VisibilityStore.recent(context)
            ?.takeIf { recent -> calendars.any { it.id == recent } }
            ?: calendars.firstOrNull { it.default }?.id
            ?: calendars.firstOrNull()?.id.orEmpty()

    /** The form for a loaded event, showing the occurrence the user opened. */
    private fun fill(loaded: Event, moment: Long, calendars: List<Calendar>) {
        carried = loaded
        val master = loaded.master()
        // The tree names an all-day occurrence by its date, which reads as
        // that day's UTC midnight; the server lists it at the user's own.
        val series = master?.property("DTSTART")
        val occurrence = if (moment > 0 && series != null && CalendarsMapping.date(series)) {
            Instant.ofEpochSecond(moment).atZone(ZoneId.of(zone)).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        } else {
            moment
        }
        val override = loaded.overrides().firstOrNull { matches(it, occurrence) }
        val shown = override ?: master ?: EventComponent("VEVENT")
        val starts = shown.property("DTSTART")
        val allday = starts != null && CalendarsMapping.date(starts)
        val declared = starts?.let { CalendarsMapping.moment(it) / 1000 } ?: loaded.start
        val length = shown.property("DTEND")?.let { CalendarsMapping.moment(it) / 1000 - declared }
            ?: shown.value("DURATION").takeIf { it.isNotBlank() }?.let { CalendarsMapping.seconds(it) }
            ?: if (allday) 86_400L else 3_600L
        // An occurrence with no override of its own still shows its own times
        // rather than the series', which is the one the master declares.
        val begins = if (override == null && occurrence > 0) occurrence else declared
        val ends = begins + length
        val alarm = shown.components.firstOrNull { it.name.equals("VALARM", ignoreCase = true) }
            ?: master?.components?.firstOrNull { it.name.equals("VALARM", ignoreCase = true) }
        _uiState.value = EditorUiState(
            event = loaded.id,
            occurrence = occurrence,
            moment = moment,
            calendars = calendars,
            calendar = loaded.calendar,
            title = shown.value("SUMMARY"),
            allday = allday,
            start = begins,
            finish = ends,
            zone = written(shown, zone),
            location = shown.value("LOCATION"),
            description = shown.value("DESCRIPTION"),
            recurrence = recurrence(master?.value("RRULE")),
            reminder = alarm?.property("TRIGGER")?.let { minutes(it.value) } ?: -1,
            etag = loaded.etag,
            recurring = master?.property("RRULE") != null || master?.property("RDATE") != null,
            series = master?.property("DTSTART")?.let { CalendarsMapping.moment(it) / 1000 } ?: 0,
            isLoading = false,
        )
    }

    // ---- the form ----

    fun title(value: String) = edit { copy(title = value) }

    fun calendar(value: String) = edit { copy(calendar = value) }

    fun location(value: String) = edit { copy(location = value) }

    fun description(value: String) = edit { copy(description = value) }

    fun reminder(value: Int) = edit { copy(reminder = value) }

    fun recurrence(value: Recurrence) = edit { copy(recurrence = recurrence.revised(value)) }

    fun allday(value: Boolean) = edit {
        // An all-day event runs to the end of its last day; a timed one keeps
        // the length it had, so switching back does not collapse it.
        if (value) {
            copy(allday = true, finish = maxOf(finish, start + 86_400))
        } else {
            copy(allday = false, finish = if (finish - start >= 86_400) start + 3_600 else finish)
        }
    }

    fun start(value: Long) = edit {
        val length = (finish - start).coerceAtLeast(0)
        copy(start = value, finish = value + length)
    }

    fun finish(value: Long) = edit { copy(finish = maxOf(value, start)) }

    /**
     * The zones the ends read in. A time typed is in its own zone, so an end
     * whose zone changes keeps its clock reading and its instant moves; the
     * finish may not then precede the start.
     */
    fun zone(value: Zone) = edit {
        val begins = moved(start, zone.start, value.start)
        val ends = moved(finish, zone.finish, value.finish)
        copy(zone = value, start = begins, finish = maxOf(ends, begins))
    }

    /** Shows the zones for the rest of this edit; not an edit in itself. */
    fun reveal() {
        _uiState.value = _uiState.value.copy(revealed = true)
    }

    private inline fun edit(change: EditorUiState.() -> EditorUiState) {
        _uiState.value = _uiState.value.change().copy(changed = true, error = null)
    }

    // ---- saving ----

    /**
     * Saves. A recurring event asks first whether the edit is for the one
     * occurrence or the whole series, unless it is being created.
     */
    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.title.isBlank()) return
        if (state.recurring && state.event != null && state.occurrence > 0) {
            _uiState.value = state.copy(prompt = Prompt.SAVE)
            return
        }
        commit(Scope.ALL)
    }

    fun scope(scope: Scope) {
        val prompt = _uiState.value.prompt
        _uiState.value = _uiState.value.copy(prompt = null)
        when (prompt) {
            Prompt.SAVE -> commit(scope)
            Prompt.DELETE -> erase(scope)
            Prompt.COPY -> _uiState.value = _uiState.value.copy(copy = scope)
            null -> Unit
        }
    }

    fun dismiss() {
        _uiState.value = _uiState.value.copy(prompt = null, confirming = false)
    }

    /**
     * Asks for a copy of the open event. A recurring one asks first whether
     * the copy is of the one occurrence or the whole series.
     */
    fun copy() {
        val state = _uiState.value
        if (state.event == null) return
        if (state.recurring && state.occurrence > 0) {
            _uiState.value = state.copy(prompt = Prompt.COPY)
        } else {
            _uiState.value = state.copy(copy = Scope.ALL)
        }
    }

    /** The screen has opened the editor on the copy asked for. */
    fun routed() {
        _uiState.value = _uiState.value.copy(copy = null)
    }

    fun confirm() {
        val state = _uiState.value
        if (state.event == null) return
        if (state.recurring && state.occurrence > 0) {
            _uiState.value = state.copy(prompt = Prompt.DELETE, confirming = false)
        } else {
            _uiState.value = state.copy(confirming = true)
        }
    }

    fun delete() {
        _uiState.value = _uiState.value.copy(confirming = false)
        erase(Scope.ALL)
    }

    private fun commit(scope: Scope) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, error = null)
            try {
                val saved = write(state, scope, state.etag)
                VisibilityStore.recent(context, saved.calendar)
                _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
            } catch (_: EventChangedException) {
                // The server's copy moved on: reload it, fold this edit onto
                // it and send it again, which is what the user asked for.
                try {
                    val fresh = repository.getEvent(state.event.orEmpty())
                    carried = fresh
                    val saved = write(state.copy(etag = fresh.etag), scope, fresh.etag)
                    VisibilityStore.recent(context, saved.calendar)
                    _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    /**
     * Sends the form. This occurrence and the ones after it become a series
     * of their own, starting where this one now falls, in one call that cuts
     * the old series before it; the first occurrence has nothing before it,
     * so that is the whole series.
     */
    private suspend fun write(state: EditorUiState, scope: Scope, etag: String): Event {
        if (state.event == null) return repository.createEvent(state.calendar, components(state, scope))
        if (scope == Scope.FOLLOWING && state.recurring) {
            val halves = split(carried?.components.orEmpty(), form(state), state.occurrence, zone)
            if (halves != null) {
                val (before, after) = halves
                return repository.splitEvent(state.event, etag, state.moment, before, after, state.calendar).second
            }
        }
        return repository.updateEvent(state.event, etag, state.calendar, components(state, scope))
    }

    private fun erase(scope: Scope) {
        val state = _uiState.value
        val event = state.event ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(isDeleting = true, error = null)
            try {
                when (scope) {
                    Scope.ALL -> repository.deleteEvent(event, state.etag)
                    Scope.FOLLOWING -> repository.truncateEvent(event, state.occurrence)
                    Scope.ONE -> repository.excludeOccurrence(event, state.occurrence)
                }
                _uiState.value = _uiState.value.copy(isDeleting = false, deleted = true)
            } catch (_: EventChangedException) {
                try {
                    // Only an "all events" delete can be stale here; the other
                    // paths read the event themselves and retry.
                    repository.deleteEvent(event, repository.getEvent(event).etag)
                    _uiState.value = _uiState.value.copy(isDeleting = false, deleted = true)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isDeleting = false, error = e.toMochiError())
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isDeleting = false, error = e.toMochiError())
            }
        }
    }

    // ---- the component tree ----

    /** The tree to send, built from the form over what the server holds. */
    fun components(state: EditorUiState, scope: Scope): List<EventComponent> =
        components(form(state), carried?.components.orEmpty(), scope)

    /** The editor's fields, as the tree builder wants them. */
    private fun form(state: EditorUiState) = EventForm(
        title = state.title,
        start = state.start,
        finish = state.finish,
        allday = state.allday,
        zone = Zone(state.zone.start.ifBlank { zone }, state.zone.finish.ifBlank { zone }),
        location = state.location,
        description = state.description,
        recurrence = state.recurrence,
        reminder = state.reminder,
        occurrence = state.occurrence,
        series = state.series,
    )

    /** The next whole hour, in the user's zone, for a new event with no time. */
    private fun nextHour(): Long =
        java.time.ZonedDateTime.now(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault()))
            .plusHours(1)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .toEpochSecond()
}

/** A `TRIGGER` as minutes before the start, -1 when it says something else. */
fun minutes(trigger: String): Int {
    val value = trigger.trim()
    if (!value.startsWith("-P") && !value.startsWith("P")) return -1
    val seconds = CalendarsMapping.seconds(value.removePrefix("-"))
    if (seconds < 0) return -1
    return if (value.startsWith("-")) (seconds / 60).toInt() else -(seconds / 60).toInt()
}
