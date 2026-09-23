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
import org.mochios.calendars.model.Zone
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.repository.EventChangedException
import org.mochios.calendars.storage.VisibilityStore
import java.time.ZoneId
import javax.inject.Inject

/**
 * The event editor. [event] is null for a new event. [occurrence] is the
 * occurrence the user opened, epoch seconds, which "This event" detaches; it
 * is 0 for a new event or one that does not repeat. [zone] is the zone each
 * end is typed and written in, the user's own for a new event; [revealed]
 * says the user asked to see the zones in this edit, which otherwise show
 * only when an end reads in another zone than their own.
 */
data class EditorUiState(
    val event: String? = null,
    val occurrence: Long = 0,
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
        load(event, occurrence, start)
    }

    private fun load(event: String?, occurrence: Long, start: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val calendars = try {
                repository.listCalendars()
                    .filterNot { it.readonly }
                    .sortedWith(compareByDescending<Calendar> { it.default }.thenBy(NaturalCompare) { it.name })
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
                return@launch
            }
            if (event == null) {
                val preferences = runCatching { repository.getPreferences() }.getOrNull()
                val begins = if (start > 0) start else nextHour()
                val length = 60L * (preferences?.duration ?: 60)
                _uiState.value = EditorUiState(
                    calendars = calendars,
                    calendar = VisibilityStore.recent(context)
                        ?.takeIf { recent -> calendars.any { it.id == recent } }
                        ?: calendars.firstOrNull { it.default }?.id
                        ?: calendars.firstOrNull()?.id.orEmpty(),
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

    /** The form for a loaded event, showing the occurrence the user opened. */
    private fun fill(loaded: Event, occurrence: Long, calendars: List<Calendar>) {
        carried = loaded
        val master = loaded.master()
        val override = loaded.overrides().firstOrNull {
            it.property("RECURRENCE-ID")?.let { property -> CalendarsMapping.moment(property) / 1000 } == occurrence
        }
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

    fun recurrence(value: Recurrence) = edit { copy(recurrence = value) }

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
            null -> Unit
        }
    }

    fun dismiss() {
        _uiState.value = _uiState.value.copy(prompt = null, confirming = false)
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

    private suspend fun write(state: EditorUiState, scope: Scope, etag: String): Event {
        val components = components(state, scope)
        return if (state.event == null) {
            repository.createEvent(state.calendar, components)
        } else {
            repository.updateEvent(state.event, etag, state.calendar, components)
        }
    }

    private fun erase(scope: Scope) {
        val state = _uiState.value
        val event = state.event ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(isDeleting = true, error = null)
            try {
                if (scope == Scope.ALL) {
                    repository.deleteEvent(event, state.etag)
                } else {
                    repository.excludeOccurrence(event, state.occurrence)
                }
                _uiState.value = _uiState.value.copy(isDeleting = false, deleted = true)
            } catch (_: EventChangedException) {
                try {
                    // Only an "all events" delete can be stale here; the one
                    // occurrence path reads the event itself and retries.
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
