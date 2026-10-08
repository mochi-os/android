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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.sync.CalendarsMapping
import org.mochios.android.sync.EventComponent
import org.mochios.android.util.NaturalCompare
import org.mochios.android.util.descriptionText
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.model.Event
import org.mochios.calendars.model.Hours
import org.mochios.calendars.model.Instance
import org.mochios.calendars.model.Zone
import org.mochios.calendars.di.Viewer
import org.mochios.calendars.model.defaultCalendar
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.repository.EventChangedException
import org.mochios.calendars.storage.Memory
import org.mochios.calendars.storage.MemoryStore
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
 * own for a new event. [copy] is a copy the user asked for, with how far it
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
    /** A save was tried without a title, which the title field says until one is typed. */
    val untitled: Boolean = false,
    /** How many saves were tried without a title; each takes the user to the title. */
    val asked: Int = 0,
    val allday: Boolean = false,
    val start: Long = 0,
    val finish: Long = 0,
    val zone: Zone = Zone(),
    val location: String = "",
    /** The event's own colour, blank for its calendar's. */
    val colour: String = "",
    val url: String = "",
    /** The event is only tentative, its `STATUS`. */
    val tentative: Boolean = false,
    val description: String = "",
    /** The description as the event holds it, which [description] shows as text. */
    val original: String = "",
    val recurrence: Recurrence = Recurrence(),
    /** The repeat's custom settings are open, as the web editor's Custom choice opens them. */
    val custom: Boolean = false,
    val reminders: List<Int> = emptyList(),
    /**
     * The times of day the ends had, minutes past midnight, while All day is
     * on: turning it off again puts them back. Null for an event that opened
     * all day with no times of its own, which comes back from midnight to the
     * last minute of its day.
     */
    val clock: Pair<Int, Int>? = null,
    val etag: String = "",
    val recurring: Boolean = false,
    /**
     * The master's own start, epoch seconds, when a recurring event is open;
     * the form shows the occurrence, so a change to the whole series moves it
     * by the same amount rather than onto the occurrence's date.
     */
    val series: Long = 0,
    val prompt: Prompt? = null,
    /** The save the prompt asks about is the one leaving makes: the screen goes once it lands. */
    val closing: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val confirming: Boolean = false,
    val error: MochiError? = null,
    /** The event, or what the form needs, failed to load: no form is shown, only this and Retry. */
    val failure: MochiError? = null,
    /** A save or delete was refused because the event changed elsewhere; Reload reads it again. */
    val changed: Boolean = false,
    /**
     * The form and calendar as the editor opened them, which an edit is
     * measured against: leaving with anything different asks first.
     */
    val opened: Pair<EventForm, String>? = null,
    val deleted: Boolean = false,
    /** An event the screen goes on as: the one a create or a copy made, or a save left the occurrence in. */
    val follow: Follow? = null,
    /** Leaving saved what was pending, so the screen can go. */
    val left: Boolean = false,
    /** Leaving with a change that cannot be saved, no title or an end before the start: ask before it is dropped. */
    val discarding: Boolean = false,
    /** What the editor that opened this one said it did, once: made, or copied. */
    val said: Said? = null,
) {
    val writable: Boolean get() = calendars.firstOrNull { it.id == calendar }?.readonly != true

    /**
     * Whether the end follows the start as instants, which Save waits for.
     * A timed event may end as it starts; an all-day one holds the day after
     * its last, so its last day may be its first but no earlier.
     */
    val ordered: Boolean get() = if (allday) finish > start else finish >= start
}

/** An event to go on as, at the occurrence listed at [moment], and what was done to make it. */
data class Follow(val event: String, val moment: Long, val said: Said? = null)

/** What an editor says it did as it hands over to the next one. */
enum class Said {
    CREATED,
    COPIED,
}

/** How long typing in a one-time event rests before it is saved. */
private const val PAUSE = 1_000L

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
    private val viewer: Viewer,
    handle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    /** The tree the server last sent, whose unmanaged properties are kept. */
    private var carried: Event? = null

    /** The zone the editor writes in when the event names none. */
    val zone: String
        get() = viewer.zone()

    /** How the editor was opened, which Retry does again after a failure to load. */
    private var opening: () -> Unit = {}

    init {
        val event = handle.get<String>("event")?.takeIf { it.isNotBlank() && it != "new" }
        val occurrence = handle.get<String>("occurrence")?.toLongOrNull() ?: 0
        val start = handle.get<String>("start")?.toLongOrNull() ?: 0
        // The route says all-day or timed when a tap on the grid chose it,
        // and nothing when the screen's own "new event" action opened us.
        val allday = when (handle.get<String>("allday")) {
            "1" -> true
            "0" -> false
            else -> null
        }
        val source = handle.get<String>("source")
        val copied = handle.get<String>("copy").orEmpty()
        val scope = Scope.entries.firstOrNull { it.name.equals(handle.get<String>("scope"), ignoreCase = true) } ?: Scope.ALL
        val zones = handle.get<String>("zones").orEmpty().split(",")
        val said = Said.entries.firstOrNull { it.name.equals(handle.get<String>("said"), ignoreCase = true) }
        val instance = Instance(
            summary = handle.get<String>("summary").orEmpty(),
            location = handle.get<String>("location").orEmpty(),
            description = handle.get<String>("description").orEmpty(),
            start = start,
            finish = handle.get<String>("finish")?.toLongOrNull() ?: 0,
            allday = handle.get<String>("allday") == "1",
            date = handle.get<String>("date")?.takeIf { it.isNotBlank() },
            zone = Zone(zones.getOrElse(0) { "" }, zones.getOrElse(1) { "" }),
        )
        opening = {
            when (source) {
                "event" -> copy(copied, occurrence, scope)
                "occurrence" -> copy(instance)
                else -> load(event, occurrence, start, allday, instance.finish)
            }
        }
        opening()
        if (said != null) _uiState.value = _uiState.value.copy(said = said)
    }

    /** The editor has said what the one before it did. */
    fun heard() {
        _uiState.value = _uiState.value.copy(said = null)
    }

    /** Saves, and everything that writes the event, go one at a time. */
    private val writing = Mutex()
    private var pause: Job? = null

    /**
     * A save was refused because the event changed elsewhere: nothing more
     * is written over it until Reload reads it again.
     */
    private var refused = false

    /** Opens the editor again after it failed to load. */
    fun retry() = opening()

    /**
     * Reads the open event again after a save or delete was refused because
     * it changed elsewhere, replacing the form with what the server now holds.
     */
    fun reload() {
        val state = _uiState.value
        val event = state.event ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(changed = false, error = null)
            try {
                fill(repository.getEvent(event), state.moment, state.calendars)
                refused = false
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /**
     * Opens the stored [event], or a new one at [start] when it names none:
     * over [finish] when a span was marked out on the grid, all day across
     * the days from [start] to [finish] when a run of days was picked, and
     * for the default length otherwise.
     */
    private fun load(event: String?, occurrence: Long, start: Long, allday: Boolean?, finish: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, failure = null)
            val calendars = calendars() ?: return@launch
            if (event == null) {
                val preferences = runCatching { repository.getPreferences() }.getOrNull()
                val memory = remembered(MemoryStore.memory(context), zone, start, allday)
                // A tap keeps the clock time it was made at in the user's
                // zone, read in whatever zone is remembered; an all-day form
                // holds the UTC midnight of its day, as a loaded one does,
                // and runs to the end of it.
                val begins = when {
                    memory.allday -> day(if (start > 0) start else Instant.now().epochSecond)
                    start > 0 -> moved(start, zone, memory.zone.start)
                    else -> opening(preferences?.hours ?: Hours())
                }
                val length = when {
                    memory.allday -> 86_400L
                    start > 0 && finish > start -> finish - start
                    else -> 60L * (preferences?.duration ?: 60)
                }
                val opened = EditorUiState(
                    calendars = calendars,
                    calendar = preferred(calendars, preferences?.calendar.orEmpty()),
                    allday = memory.allday,
                    start = begins,
                    finish = begins + length,
                    zone = memory.zone,
                    reminders = defaultReminders(preferences?.reminder ?: 15),
                    isLoading = false,
                )
                // A run of days picked on the grid comes all day with a span:
                // it opens over the days the span falls on, and keeps the
                // span's times beneath for turning all day off, as switching
                // a timed event to all day does.
                _uiState.value = if (allday == true && start > 0 && finish > start) {
                    val timed = opened.copy(
                        allday = false,
                        start = moved(start, zone, memory.zone.start),
                        finish = moved(finish, zone, memory.zone.finish),
                    )
                    toggled(timed, true, zone)
                } else {
                    opened
                }
                settled()
                return@launch
            }
            try {
                fill(repository.getEvent(event), occurrence, calendars)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, failure = e.toMochiError())
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, failure = null)
            val calendars = calendars() ?: return@launch
            try {
                val loaded = repository.getEvent(event)
                val form = copied(loaded.components, occurrence, scope, zone) ?: EventForm()
                val preference = runCatching { repository.getPreferences().calendar }.getOrNull().orEmpty()
                open(form, calendars, calendars.firstOrNull { it.id == loaded.calendar }?.id ?: preferred(calendars, preference))
                create()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, failure = e.toMochiError())
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, failure = null)
            val calendars = calendars() ?: return@launch
            val preferences = runCatching { repository.getPreferences() }.getOrNull()
            open(copied(instance, zone, preferences?.reminder ?: 15), calendars, preferred(calendars, preferences?.calendar.orEmpty()))
            create()
        }
    }

    /**
     * The editor on a copy's [form], in [calendar]: a new event, so saving
     * creates. [baseline] is what closing measures edits against when the
     * copy already carries some: the copy as the stored event would give it.
     */
    private fun open(
        form: EventForm,
        calendars: List<Calendar>,
        calendar: String,
        baseline: Pair<EventForm, String>? = null,
    ) {
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
            colour = form.colour,
            url = form.url,
            tentative = form.tentative,
            description = form.description,
            original = form.original,
            recurrence = form.recurrence,
            custom = !form.recurrence.plain,
            reminders = form.reminders,
            isLoading = false,
        )
        settled()
        if (baseline != null) _uiState.value = _uiState.value.copy(opened = baseline)
    }

    /** The calendars the editor offers, the default first; null once a failure to list them is shown. */
    private suspend fun calendars(): List<Calendar>? = try {
        repository.listCalendars()
            .filterNot { it.readonly }
            .sortedWith(compareByDescending<Calendar> { it.default }.thenBy(NaturalCompare) { it.name })
    } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(isLoading = false, failure = e.toMochiError())
        null
    }

    /**
     * Keeps a created event's all-day setting and zones for the next new
     * event on this device, as [preferred] keeps its calendar; an edit of a
     * stored event says nothing about what the user makes next.
     */
    private fun remember(state: EditorUiState) {
        if (state.event != null) return
        MemoryStore.memory(context, Memory(allday = state.allday, zone = form(state).zone))
    }

    /** The calendar a new event lands in: the one the preferences name, else the built-in default. */
    private fun preferred(calendars: List<Calendar>, preference: String): String =
        defaultCalendar(calendars, preference)

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
        // An override replaces its occurrence whole: its reminders are its own,
        // and one with none has none.
        val alarms = alarms(shown)
        // A timed series' own start, which says the last day its end lets in.
        val first = master?.property("DTSTART")
            ?.takeIf { !CalendarsMapping.date(it) }
            ?.let { CalendarsMapping.moment(it) / 1000 } ?: 0L
        val repeat = recurrence(master?.value("RRULE"), master?.let { written(it, zone).start } ?: zone, first)
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
            colour = shown.value("COLOR"),
            url = shown.value("URL"),
            tentative = tentative(shown),
            description = descriptionText(shown.value("DESCRIPTION")),
            original = shown.value("DESCRIPTION"),
            recurrence = repeat,
            custom = !repeat.plain,
            reminders = alarms.mapNotNull(::alarmMinutes).distinct(),
            etag = loaded.etag,
            recurring = master?.property("RRULE") != null || master?.property("RDATE") != null,
            series = master?.property("DTSTART")?.let { CalendarsMapping.moment(it) / 1000 } ?: 0,
            isLoading = false,
            said = _uiState.value.said,
        )
        settled()
    }

    // ---- the form ----

    fun title(value: String) = edit { copy(title = value, untitled = untitled && value.isBlank()) }

    fun calendar(value: String) = edit { copy(calendar = value) }

    fun location(value: String) = edit { copy(location = value) }

    fun colour(value: String) = edit { copy(colour = value) }

    fun url(value: String) = edit { copy(url = value) }

    fun tentative(value: Boolean) = edit { copy(tentative = value) }

    fun description(value: String) = edit { copy(description = value) }

    /** Sets the reminder at [index] to [minutes] before the start. */
    fun reminder(index: Int, minutes: Int) =
        edit { copy(reminders = reminders.mapIndexed { at, each -> if (at == index) minutes else each }) }

    fun addReminder() = edit { copy(reminders = reminders + nextReminder(reminders)) }

    fun removeReminder(index: Int) = edit { copy(reminders = reminders.filterIndexed { at, _ -> at != index }) }

    fun repeat(frequency: Frequency) = edit { repeated(this, frequency) }

    fun custom() = edit { customised(this) }

    fun recurrence(value: Recurrence) = edit { recurred(this, value, this@EventEditViewModel.zone) }

    fun allday(value: Boolean) = edit { toggled(this, value, this@EventEditViewModel.zone) }

    fun start(value: Long) = edit {
        val length = finish - start
        copy(start = value, finish = value + length)
    }

    /**
     * The end as picked, even before the start: the End field then says so
     * and Save waits, as the web editor does, rather than moving it.
     */
    fun finish(value: Long) = edit { copy(finish = value) }

    /**
     * The zones the ends read in. A time typed is in its own zone, so an end
     * whose zone changes keeps its clock reading and its instant moves; an
     * end that then falls before the start moves on by whole days until it
     * follows, which is where an eastbound arrival lands anyway.
     */
    fun zone(value: Zone) = edit {
        val begins = moved(start, zone.start, value.start)
        val ends = moved(finish, zone.finish, value.finish)
        copy(zone = value, start = begins, finish = following(begins, ends))
    }

    private inline fun edit(change: EditorUiState.() -> EditorUiState) {
        _uiState.value = _uiState.value.change().copy(error = null)
        later()
    }

    /**
     * A one-time event saves once typing rests. A series waits for its field
     * or the screen to be left, since each of its saves asks which
     * occurrences it is for; a new event waits for Create.
     */
    private fun later() {
        val state = _uiState.value
        if (state.event == null || state.copying || state.recurring) return
        pause?.cancel()
        pause = viewModelScope.launch {
            delay(PAUSE)
            save()
        }
    }

    /** Marks the form now showing as the one the editor opened. */
    private fun settled() {
        val state = _uiState.value
        _uiState.value = state.copy(opened = form(state) to state.calendar)
    }

    /** Whether the form or calendar differs from the one the editor opened. */
    fun dirty(state: EditorUiState): Boolean = dirty(state, zone)

    // ---- saving ----

    /**
     * Saves what the form holds, as leaving a field or a pause in typing
     * asks: an edit saves as it goes, as the web's side panel does. A series
     * asks first which occurrences the change is for. A change that cannot
     * be saved, no title or an end before the start, waits; leaving with one
     * asks before it is dropped. [closing] is the save leaving makes, after
     * which the screen goes.
     */
    fun save(closing: Boolean = false) {
        pause?.cancel()
        val state = _uiState.value
        if (state.event == null || state.copying || state.prompt != null || refused) return
        if (!dirty(state)) {
            if (closing) after(closing = true)
            return
        }
        if (state.title.isBlank() || !state.ordered) {
            _uiState.value = state.copy(
                untitled = state.title.isBlank(),
                asked = if (state.title.isBlank() && !closing) state.asked + 1 else state.asked,
                discarding = closing,
            )
            return
        }
        if (state.recurring && state.occurrence > 0) {
            _uiState.value = state.copy(prompt = Prompt.SAVE, closing = closing)
            return
        }
        commit(Scope.ALL, closing)
    }

    /** Leaving the editor: whatever is pending saves first. */
    fun leave() = save(closing = true)

    /** A change that cannot be saved is dropped, and the screen goes. */
    fun discard() {
        _uiState.value = _uiState.value.copy(discarding = false, left = true)
    }

    fun keep() {
        _uiState.value = _uiState.value.copy(discarding = false)
    }

    /** Leaving once the save it waited on has landed. */
    private fun after(closing: Boolean) {
        if (closing) _uiState.value = _uiState.value.copy(left = true)
    }

    /**
     * Makes the new event, as Create asks; the screen then goes on as its
     * editor. A copy's editor makes its event as soon as the copy is read.
     */
    fun create() {
        val state = _uiState.value
        if ((state.event != null && !state.copying) || state.isSaving) return
        if (state.title.isBlank()) {
            _uiState.value = state.copy(untitled = true, asked = state.asked + 1)
            return
        }
        if (!state.ordered) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val made = repository.createEvent(state.calendar, components(form(state), emptyList(), Scope.ALL))
                VisibilityStore.reveal(context, made.calendar)
                remember(state)
                val said = if (state.copying) Said.COPIED else Said.CREATED
                _uiState.value = _uiState.value.copy(isSaving = false, follow = Follow(made.id, listed(state), said))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    /** Where the server lists the occurrence the form starts at: an all-day one at the user's own midnight. */
    private fun listed(state: EditorUiState): Long {
        if (state.recurrence.frequency == Frequency.NEVER) return 0
        if (!state.allday) return state.start
        val id = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault())
        return Instant.ofEpochSecond(state.start).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(id).toEpochSecond()
    }

    fun scope(scope: Scope) {
        val prompt = _uiState.value.prompt
        val closing = _uiState.value.closing
        _uiState.value = _uiState.value.copy(prompt = null, closing = false)
        when (prompt) {
            Prompt.SAVE -> commit(scope, closing)
            Prompt.DELETE -> erase(scope)
            Prompt.COPY -> clone(scope)
            null -> Unit
        }
    }

    fun dismiss() {
        _uiState.value = _uiState.value.copy(prompt = null, closing = false, confirming = false)
    }

    /** The conflict has been said. */
    fun told() {
        _uiState.value = _uiState.value.copy(changed = false)
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
            clone(Scope.ALL)
        }
    }

    /**
     * Makes the copy asked for at once and goes on as its editor, as the
     * web's side panel does: [Scope.ONE] without its repeat, the whole series
     * moved back to its own start. A change not yet saved goes into the copy
     * rather than being dropped. It lands in the event's calendar when that
     * can be written to.
     */
    private fun clone(scope: Scope) {
        pause?.cancel()
        viewModelScope.launch {
            writing.withLock {
                val state = _uiState.value
                val form = if (dirty(state)) {
                    duplicate(form(state), scope)
                } else {
                    carried?.components?.let { copied(it, state.occurrence, scope, zone) }
                } ?: return@withLock
                val calendar = state.calendars.firstOrNull { it.id == state.calendar }?.id
                    ?: state.calendars.firstOrNull()?.id.orEmpty()
                _uiState.value = _uiState.value.copy(isSaving = true, error = null)
                try {
                    // The copy is a new event: nothing of the original's tree goes with it.
                    val made = repository.createEvent(calendar, components(form, emptyList(), Scope.ALL))
                    VisibilityStore.reveal(context, made.calendar)
                    val moment = if (form.recurrence.frequency == Frequency.NEVER) 0 else form.start
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        follow = Follow(made.id, moment, Said.COPIED),
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
                }
            }
        }
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

    private fun commit(scope: Scope, closing: Boolean = false) {
        pause?.cancel()
        viewModelScope.launch {
            writing.withLock {
                val state = _uiState.value
                // A save queued behind one that was refused writes nothing over the change.
                if (refused) return@withLock
                if (!dirty(state)) {
                    after(closing)
                    return@withLock
                }
                _uiState.value = state.copy(isSaving = true, error = null)
                try {
                    val saved = write(state, scope, state.etag)
                    VisibilityStore.reveal(context, saved.calendar)
                    if (saved.id != state.event) {
                        // The series from this occurrence on, or the occurrence
                        // moved to another calendar, is an event of its own now.
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            left = closing,
                            follow = if (closing) null else Follow(saved.id, listed(state)),
                        )
                        return@withLock
                    }
                    carried = saved
                    _uiState.value = written(_uiState.value, state, scope, saved.etag, zone).copy(isSaving = false)
                    after(closing)
                } catch (_: EventChangedException) {
                    // The server's copy moved on since it was read: writing the
                    // form over it would undo that change, so the save is refused
                    // and Reload reads it again, as the web editor does.
                    refused = true
                    _uiState.value = _uiState.value.copy(isSaving = false, changed = true)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
                }
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
        // One occurrence taken to another calendar leaves the series behind
        // and becomes an event of its own there.
        val home = carried?.calendar
        if (scope == Scope.ONE && state.recurring && state.occurrence > 0 && home != null && state.calendar != home) {
            return moveOccurrence(
                create = { repository.createEvent(state.calendar, single(form(state), carried?.components.orEmpty())) },
                exclude = { repository.excludeOccurrence(state.event, state.occurrence) },
                delete = { created -> repository.deleteEvent(created.id, created.etag) },
            )
        }
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
        // What Undo puts back, as the web's delete does: the event as it was
        // read, recreated whole, or written back over its series when only
        // an occurrence, or the ones from it on, went.
        val stored = carried
        val whole: suspend () -> Unit = { stored?.let { repository.createEvent(it.calendar, it.components) } }
        val series: (Event) -> (suspend () -> Unit) = { changed ->
            { stored?.let { repository.updateEvent(changed.id, changed.etag, null, it.components) } }
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isDeleting = true, error = null)
            try {
                // Every path writes over the event as it was read, so one
                // that changed elsewhere since is refused rather than deleted.
                val back: suspend () -> Unit = when (scope) {
                    Scope.ALL -> {
                        repository.deleteEvent(event, state.etag)
                        whole
                    }
                    Scope.FOLLOWING -> repository.truncateEvent(event, state.occurrence, stored)?.let { series(it) } ?: whole
                    Scope.ONE -> series(repository.excludeOccurrence(event, state.occurrence, stored))
                }
                if (stored != null) repository.deleted(back)
                _uiState.value = _uiState.value.copy(isDeleting = false, deleted = true)
            } catch (_: EventChangedException) {
                _uiState.value = _uiState.value.copy(isDeleting = false, changed = true)
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
    private fun form(state: EditorUiState) = form(state, zone)

    /**
     * Where a new event opened with no time at all starts: today's next whole
     * hour, or tomorrow's working [hours] once today has none left.
     */
    private fun opening(hours: Hours): Long {
        val id = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault())
        val now = java.time.ZonedDateTime.now(id)
        return defaultStart(now.toLocalDate(), now.toLocalDate(), now.toLocalTime(), hours).atZone(id).toEpochSecond()
    }

    /** The day [moment] falls on in the user's zone, at its UTC midnight, which is how an all-day form holds a day. */
    private fun day(moment: Long): Long =
        Instant.ofEpochSecond(moment)
            .atZone(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault()))
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toEpochSecond()
}

/** The component's `VALARM`s. */
fun alarms(component: EventComponent): List<EventComponent> =
    component.components.filter { it.name.equals("VALARM", ignoreCase = true) }

/**
 * The minutes before the start an alarm fires, when the reminder setting can
 * say it: a duration relative to the start, at or before it. An alarm relative
 * to the end, after the start or at a fixed time is null; the editor leaves it
 * as it is.
 */
fun alarmMinutes(alarm: EventComponent): Int? {
    val trigger = alarm.property("TRIGGER") ?: return null
    if (trigger.parameter("RELATED").equals("END", ignoreCase = true)) return null
    if (trigger.parameter("VALUE").equals("DATE-TIME", ignoreCase = true)) return null
    val value = trigger.value.trim()
    if (!value.startsWith("-P") && !value.startsWith("P")) return null
    return minutes(value).takeIf { it >= 0 }
}

/** A `TRIGGER` as minutes before the start, -1 when it says something else. */
fun minutes(trigger: String): Int {
    val value = trigger.trim()
    if (!value.startsWith("-P") && !value.startsWith("P")) return -1
    val seconds = CalendarsMapping.seconds(value.removePrefix("-"))
    if (seconds < 0) return -1
    return if (value.startsWith("-")) (seconds / 60).toInt() else -(seconds / 60).toInt()
}

/** The editor's fields, as the tree builder wants them; a blank zone is the [user]'s. */
internal fun form(state: EditorUiState, user: String) = EventForm(
    title = state.title,
    start = state.start,
    finish = state.finish,
    allday = state.allday,
    zone = Zone(state.zone.start.ifBlank { user }, state.zone.finish.ifBlank { user }),
    location = state.location,
    colour = state.colour,
    url = state.url,
    tentative = state.tentative,
    description = state.description,
    original = state.original,
    recurrence = state.recurrence,
    reminders = state.reminders,
    occurrence = state.occurrence,
    series = state.series,
)

/**
 * Whether the form or calendar differs from the one the editor opened, so
 * leaving would drop something; an edit undone by hand is no change.
 */
internal fun dirty(state: EditorUiState, user: String): Boolean =
    state.opened != null && (form(state, user) to state.calendar) != state.opened

/**
 * The state once [saved], the form it wrote, has landed under [etag]: that
 * form is what the next change is measured against, while what was typed
 * since stays. A save of the whole series that moved its start moves the
 * occurrence open with it; a save of this occurrence alone leaves it where
 * its series put it, now listed at its new start.
 */
internal fun written(current: EditorUiState, saved: EditorUiState, scope: Scope, etag: String, user: String): EditorUiState {
    val moved = saved.start - (saved.opened?.first?.start ?: saved.start)
    val shifted = when {
        !saved.recurring || saved.occurrence == 0L -> current
        scope == Scope.ONE -> current.copy(moment = saved.start)
        else -> current.copy(
            occurrence = saved.occurrence + moved,
            moment = saved.moment + moved,
            series = saved.series + moved,
        )
    }
    return shifted.copy(etag = etag, opened = form(saved, user) to saved.calendar)
}

/**
 * One of the plain repeat choices, which says everything: nothing of a custom
 * rule stays behind it, as the web editor resets to an empty repeat.
 */
internal fun repeated(state: EditorUiState, frequency: Frequency): EditorUiState =
    state.copy(custom = false, recurrence = Recurrence(frequency))

/**
 * The Custom choice: the settings open on the rule as it is, a weekly one for
 * an event that did not repeat, and a rule they could not express is replaced
 * by what they show.
 */
internal fun customised(state: EditorUiState): EditorUiState {
    val recurrence = state.recurrence
    val frequency = if (recurrence.frequency == Frequency.NEVER) Frequency.WEEKLY else recurrence.frequency
    return state.copy(custom = true, recurrence = recurrence.copy(frequency = frequency, rule = null, expressible = true))
}

/**
 * A change in the custom settings, which then say the rule. An end on a day
 * starts four weeks after the event does, on the day it starts in its own
 * zone, a blank one the [user]'s.
 */
internal fun recurred(state: EditorUiState, value: Recurrence, user: String): EditorUiState {
    val until = if (value.ending == Ending.UNTIL && value.until == null) {
        Instant.ofEpochSecond(state.start)
            .atZone(shownIn(state.allday, state.zone.start.ifBlank { user }))
            .toLocalDate()
            .plusDays(28)
    } else {
        value.until
    }
    return state.copy(recurrence = value.copy(until = until, rule = null, expressible = true))
}

/**
 * All day on holds each end's day, the last day included, and keeps the times
 * aside; off puts the times back on whatever days the ends now have, as the
 * web editor keeps its dates and times apart. An event that opened all day
 * comes back from midnight to the last minute of its day, as the web's does.
 * A blank zone is the [user]'s.
 */
internal fun toggled(state: EditorUiState, value: Boolean, user: String): EditorUiState {
    if (value == state.allday) return state
    val begins = shownIn(false, state.zone.start.ifBlank { user })
    val ends = shownIn(false, state.zone.finish.ifBlank { state.zone.start.ifBlank { user } })
    if (value) {
        val first = Instant.ofEpochSecond(state.start).atZone(begins)
        val last = Instant.ofEpochSecond(state.finish).atZone(ends)
        return state.copy(
            allday = true,
            start = first.toLocalDate().atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
            finish = last.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
            clock = (first.hour * 60 + first.minute) to (last.hour * 60 + last.minute),
        )
    }
    val (from, to) = state.clock ?: (0 to 1_439)
    val first = Instant.ofEpochSecond(state.start).atZone(ZoneOffset.UTC).toLocalDate()
    val last = Instant.ofEpochSecond(state.finish).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
    return state.copy(
        allday = false,
        start = first.atTime(from / 60, from % 60).atZone(begins).toEpochSecond(),
        finish = last.atTime(to / 60, to % 60).atZone(ends).toEpochSecond(),
        clock = null,
    )
}
