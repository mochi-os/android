// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.preferences

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.ChoiceRow
import org.mochios.android.ui.components.CreateEntityForm
import org.mochios.android.ui.components.CreateEntityScaffold
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.LabeledSwitchRow
import org.mochios.android.util.NaturalCompare
import org.mochios.calendars.R
import org.mochios.calendars.api.PreferencesRequest
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.model.Hours
import org.mochios.calendars.model.Multiweek
import org.mochios.calendars.model.Preferences
import org.mochios.calendars.model.defaultCalendar
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.ui.dialogs.DURATIONS
import org.mochios.calendars.ui.dialogs.reminderOptions
import org.mochios.android.R as MochiR
import java.time.DayOfWeek
import java.time.format.TextStyle
import javax.inject.Inject

/**
 * The preferences screen's state. [opened] is the preferences as loaded, with
 * the calendar the picker shows in place of one since deleted or made
 * read-only, so loading changes nothing; [draft] is what the user has made of
 * them. [calendars] are the ones a new event can go in, the built-in default
 * first.
 */
data class PreferencesUiState(
    val opened: Preferences? = null,
    val draft: Preferences? = null,
    val calendars: List<Calendar> = emptyList(),
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val loadError: MochiError? = null,
    val error: MochiError? = null,
    val saved: Boolean = false,
)

/** Loads, edits and saves the calendar preferences the web shares. */
@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val repository: CalendarsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreferencesUiState())

    /** The screen's state. */
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Reads the preferences and the calendars a new event can go in. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)
            try {
                val calendars = repository.listCalendars()
                    .filterNot { calendar -> calendar.readonly }
                    .sortedWith(
                        compareByDescending<Calendar> { calendar -> calendar.default }
                            .thenBy(NaturalCompare) { calendar -> calendar.name },
                    )
                val preferences = repository.getPreferences()
                val opened = preferences.copy(
                    days = preferences.days.sorted(),
                    calendar = defaultCalendar(calendars, preferences.calendar),
                )
                _uiState.value = _uiState.value.copy(
                    opened = opened,
                    draft = opened,
                    calendars = calendars,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, loadError = e.toMochiError())
            }
        }
    }

    /** Changes the draft. */
    fun edit(change: (Preferences) -> Preferences) {
        val draft = _uiState.value.draft ?: return
        _uiState.value = _uiState.value.copy(draft = change(draft), error = null)
    }

    /** Saves the draft; [PreferencesUiState.saved] turns true once the server has it. */
    fun save() {
        val state = _uiState.value
        val draft = state.draft ?: return
        if (state.isBusy) {
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            try {
                repository.setPreferences(
                    PreferencesRequest(
                        hours = draft.hours,
                        days = draft.days,
                        multiweek = draft.multiweek,
                        duration = draft.duration,
                        reminder = draft.reminder,
                        zones = draft.zones,
                        calendar = draft.calendar.ifEmpty { null },
                        allday = draft.allday,
                    ),
                )
                _uiState.value = _uiState.value.copy(isBusy = false, saved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isBusy = false, error = e.toMochiError())
            }
        }
    }
}

/**
 * The preferences the views read: the working hours they shade, the work
 * days, how many weeks a multiweek view shows, where all-day events go, the
 * default event length, the default reminder, the calendar a new event goes
 * in and whether events show in their own zones. Shared with the web, so a
 * change here shows there. [onSaved] is called once the server has them.
 */
@Composable
fun PreferencesScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            onSaved()
        }
    }

    PreferencesContent(
        uiState = uiState,
        onBack = onBack,
        onSave = viewModel::save,
        onRetry = viewModel::load,
        onChange = viewModel::edit,
    )
}

/**
 * The preferences screen drawn from [uiState] alone: the form once the
 * preferences are in, the load error, or a spinner. Save turns on once the
 * draft differs from what was loaded and its hours run forwards.
 */
@Composable
internal fun PreferencesContent(
    uiState: PreferencesUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onChange: ((Preferences) -> Preferences) -> Unit,
) {
    val draft = uiState.draft
    CreateEntityScaffold(
        title = stringResource(R.string.calendars_preferences),
        submitLabel = stringResource(MochiR.string.common_save),
        submitEnabled = draft != null && draft.hours.finish > draft.hours.start &&
            draft != uiState.opened && !uiState.isBusy,
        isBusy = uiState.isBusy,
        error = uiState.error,
        onBack = onBack,
        onSubmit = onSave,
    ) { padding ->
        val loadError = uiState.loadError
        when {
            draft != null -> CreateEntityForm(padding) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PreferencesFields(
                        preferences = draft,
                        calendars = uiState.calendars,
                        enabled = !uiState.isBusy,
                        onChange = onChange,
                    )
                }
            }

            loadError != null -> Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                ErrorState(error = loadError, onRetry = onRetry)
            }

            else -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/** The preferences' fields, each change handed to [onChange]. */
@Composable
private fun PreferencesFields(
    preferences: Preferences,
    calendars: List<Calendar>,
    enabled: Boolean,
    onChange: ((Preferences) -> Preferences) -> Unit,
) {
    val format = LocalFormat.current
    val locale = LocalConfiguration.current.locales[0]
    val start = preferences.hours.start
    val finish = preferences.hours.finish
    // Hours as the user's clock names them; the last hour of the day is the
    // start of the next, which is what it is.
    val hours = (0..23).map { hour -> hour.toString() to format.formatHour(hour) }
    val ends = (start + 1..24).map { hour -> hour.toString() to format.formatHour(hour) }

    ChoiceRow(
        label = stringResource(R.string.calendars_hours_start),
        fallbackLabel = "",
        options = hours,
        selected = start.toString(),
        onSelect = { chosen ->
            val hour = chosen.toIntOrNull() ?: start
            // The end follows a start moved past it, as on the web.
            onChange { current -> current.copy(hours = Hours(hour, maxOf(current.hours.finish, hour + 1))) }
        },
    )
    ChoiceRow(
        label = stringResource(R.string.calendars_hours_finish),
        fallbackLabel = "",
        options = ends,
        selected = finish.toString(),
        onSelect = { chosen ->
            val hour = chosen.toIntOrNull() ?: finish
            onChange { current -> current.copy(hours = current.hours.copy(finish = hour)) }
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.calendars_work_days),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // In the order the user's own week runs, as toggles.
        val first = format.preferences.weekStartsOn
        val week = (0..6).map { offset -> (first + offset) % 7 }
        // The preference indexes Sunday 0; java.time indexes Monday 1.
        val name = { day: Int, style: TextStyle ->
            DayOfWeek.of(if (day == 0) 7 else day).getDisplayName(style, locale)
        }
        val measurer = rememberTextMeasurer()
        val labelStyle = MaterialTheme.typography.labelMedium
        BoxWithConstraints(modifier = Modifier.padding(top = 4.dp)) {
            val gap = 4.dp
            val slot = (maxWidth - gap * 6) / 7
            val slotPx = with(LocalDensity.current) { slot.roundToPx() }
            // One letter for every day once any short name is wider than its slot.
            val style = remember(week, locale, labelStyle, slotPx) {
                val fits = week.all { day ->
                    measurer.measure(name(day, TextStyle.SHORT), labelStyle).size.width <= slotPx
                }
                if (fits) TextStyle.SHORT else TextStyle.NARROW
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (day in week) {
                    val on = day in preferences.days
                    DayToggle(
                        label = name(day, style),
                        description = name(day, TextStyle.FULL),
                        selected = on,
                        enabled = enabled,
                        onClick = {
                            onChange { current ->
                                val days = if (on) current.days - day else current.days + day
                                current.copy(days = days.sorted())
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    ChoiceRow(
        label = stringResource(R.string.calendars_multiweek_weeks),
        fallbackLabel = "",
        options = (2..8).map { weeks -> weeks.toString() to format.formatNumber(weeks) },
        selected = preferences.multiweek.weeks.toString(),
        onSelect = { chosen ->
            val weeks = chosen.toIntOrNull() ?: preferences.multiweek.weeks
            onChange { current -> current.copy(multiweek = current.multiweek.copy(weeks = weeks)) }
        },
    )
    ChoiceRow(
        label = stringResource(R.string.calendars_multiweek_previous),
        fallbackLabel = "",
        options = (0..2).map { weeks -> weeks.toString() to format.formatNumber(weeks) },
        selected = preferences.multiweek.previous.toString(),
        onSelect = { chosen ->
            val previous = chosen.toIntOrNull() ?: preferences.multiweek.previous
            onChange { current -> current.copy(multiweek = Multiweek(current.multiweek.weeks, previous)) }
        },
    )
    ChoiceRow(
        label = stringResource(R.string.calendars_preferences_allday),
        fallbackLabel = "",
        options = listOf(
            "first" to stringResource(R.string.calendars_allday_first),
            "last" to stringResource(R.string.calendars_allday_last),
        ),
        selected = preferences.allday,
        onSelect = { chosen -> onChange { current -> current.copy(allday = chosen) } },
    )
    ChoiceRow(
        label = stringResource(R.string.calendars_default_duration),
        fallbackLabel = "",
        // Zero is a real choice: an event that ends when it starts.
        options = DURATIONS.map { minutes ->
            minutes.toString() to if (minutes > 0 && minutes % 60 == 0) {
                pluralStringResource(R.plurals.calendars_hours, minutes / 60, format.formatNumber(minutes / 60))
            } else {
                pluralStringResource(R.plurals.calendars_minutes, minutes, format.formatNumber(minutes))
            }
        },
        selected = preferences.duration.toString(),
        onSelect = { chosen ->
            val duration = chosen.toIntOrNull() ?: preferences.duration
            onChange { current -> current.copy(duration = duration) }
        },
    )
    ChoiceRow(
        label = stringResource(R.string.calendars_default_reminder),
        fallbackLabel = "",
        options = reminderOptions(),
        selected = preferences.reminder.toString(),
        onSelect = { chosen ->
            val reminder = chosen.toIntOrNull() ?: preferences.reminder
            onChange { current -> current.copy(reminder = reminder) }
        },
    )
    ChoiceRow(
        label = stringResource(R.string.calendars_default_calendar),
        fallbackLabel = "",
        options = calendars.map { calendar -> calendar.id to calendar.name },
        selected = preferences.calendar,
        onSelect = { chosen -> onChange { current -> current.copy(calendar = chosen) } },
    )
    LabeledSwitchRow(
        label = stringResource(R.string.calendars_preferences_zones),
        checked = preferences.zones,
        onCheckedChange = { checked -> onChange { current -> current.copy(zones = checked) } },
        enabled = enabled,
        labelStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

/** One day of the work-day row, as narrow as a seventh of the row. */
@Composable
private fun DayToggle(
    label: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(36.dp)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(50),
        color = if (selected) colors.secondaryContainer else Color.Transparent,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, colors.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clearAndSetSemantics { },
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
