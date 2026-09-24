// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.LabeledSelectField
import org.mochios.android.ui.components.LabeledSwitchRow
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.util.zoneCity
import org.mochios.calendars.R
import org.mochios.calendars.ui.dialogs.DeleteEventDialog
import org.mochios.calendars.ui.dialogs.ScopeDialog
import org.mochios.calendars.ui.dialogs.reminderOptions
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.mochios.android.R as MochiR

/**
 * The event editor: Title, Calendar, All day, Start and its zone, End and its
 * zone, Location, Repeat, Reminder, Description, in that order. A recurring
 * event asks whether a save or a delete is for the one occurrence or the
 * whole series.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: EventEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saved) { if (uiState.saved) onSaved() }
    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onDeleted() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbar.showSnackbar(it.userMessage()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.event == null) {
                            stringResource(R.string.calendars_event_new)
                        } else {
                            stringResource(R.string.calendars_event_edit)
                        },
                    )
                },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
                actions = {
                    if (uiState.event != null) {
                        MochiIconButton(onClick = viewModel::confirm, enabled = uiState.writable) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.calendars_delete),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MochiTextField(
                value = uiState.title,
                onValueChange = viewModel::title,
                label = { Text(stringResource(R.string.calendars_event_title)) },
                singleLine = true,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            LabeledSelectField(
                label = stringResource(R.string.calendars_event_calendar),
                placeholder = "",
                options = uiState.calendars.map { it.id to it.name },
                selected = uiState.calendar,
                onSelect = viewModel::calendar,
            )
            LabeledSwitchRow(
                label = stringResource(R.string.calendars_event_allday),
                checked = uiState.allday,
                onCheckedChange = viewModel::allday,
                enabled = !uiState.isSaving,
            )
            // Each end is typed in its own zone, named beneath it when an end
            // reads in another zone than the user's or they asked to see the
            // zones; otherwise a globe beside the End row reveals them. An
            // all-day event has no clock, so its dates stay in the user's own.
            val zoned = !uiState.allday && (uiState.revealed || foreign(uiState.zone, viewModel.zone))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MomentField(
                    label = stringResource(R.string.calendars_event_start),
                    moment = uiState.start,
                    allday = uiState.allday,
                    zone = if (uiState.allday) viewModel.zone else uiState.zone.start,
                    onChange = viewModel::start,
                )
                if (zoned) {
                    ZoneField(
                        label = stringResource(R.string.calendars_event_zone_start),
                        zone = uiState.zone.start,
                        onChange = { viewModel.zone(follow(uiState.zone, it)) },
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MomentField(
                    label = stringResource(R.string.calendars_event_finish),
                    moment = uiState.finish,
                    allday = uiState.allday,
                    zone = if (uiState.allday) viewModel.zone else uiState.zone.finish,
                    onChange = viewModel::finish,
                    trailing = if (uiState.allday || zoned) {
                        null
                    } else {
                        {
                            MochiIconButton(onClick = viewModel::reveal, enabled = !uiState.isSaving) {
                                Icon(
                                    Icons.Outlined.Public,
                                    contentDescription = stringResource(R.string.calendars_event_timezone),
                                )
                            }
                        }
                    },
                )
                if (zoned) {
                    ZoneField(
                        label = stringResource(R.string.calendars_event_zone_finish),
                        zone = uiState.zone.finish,
                        onChange = { viewModel.zone(uiState.zone.copy(finish = it)) },
                    )
                }
            }
            MochiTextField(
                value = uiState.location,
                onValueChange = viewModel::location,
                label = { Text(stringResource(R.string.calendars_event_location)) },
                singleLine = true,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            RepeatField(
                recurrence = uiState.recurrence,
                onChange = viewModel::recurrence,
            )
            LabeledSelectField(
                label = stringResource(R.string.calendars_event_reminder),
                placeholder = "",
                options = reminderOptions(),
                selected = uiState.reminder.toString(),
                onSelect = { viewModel.reminder(it.toIntOrNull() ?: -1) },
            )
            MochiTextField(
                value = uiState.description,
                onValueChange = viewModel::description,
                label = { Text(stringResource(R.string.calendars_event_description)) },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
            MochiButton(
                onClick = viewModel::save,
                enabled = uiState.title.isNotBlank() && !uiState.isSaving && uiState.writable,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                }
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(MochiR.string.common_save))
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (uiState.prompt != null) {
        ScopeDialog(
            deleting = uiState.prompt == Prompt.DELETE,
            onDismiss = viewModel::dismiss,
            onOne = { viewModel.scope(Scope.ONE) },
            onFollowing = { viewModel.scope(Scope.FOLLOWING) },
            onAll = { viewModel.scope(Scope.ALL) },
        )
    }
    if (uiState.confirming) {
        DeleteEventDialog(
            summary = uiState.title,
            deleting = uiState.isDeleting,
            onDismiss = viewModel::dismiss,
            onConfirm = viewModel::delete,
        )
    }
}

/**
 * A date, and a time beside it unless the event is all day, both read in
 * [zone], with [trailing] at the end of the row. Tapping either field opens
 * the matching picker; the date picker's first day of the week follows the
 * user's own preference.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MomentField(
    label: String,
    moment: Long,
    allday: Boolean,
    zone: String,
    onChange: (Long) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val format = LocalFormat.current
    var picking by remember { mutableStateOf(false) }
    var timing by remember { mutableStateOf(false) }
    val id = remember(zone) { runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault()) }
    val local = remember(moment, id) { Instant.ofEpochSecond(moment).atZone(id) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                MochiTextField(
                    value = format.formatDate(moment, id.id),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(modifier = Modifier.matchParentSize().clickable { picking = true })
            }
            if (!allday) {
                Box(modifier = Modifier.width(140.dp)) {
                    MochiTextField(
                        value = format.formatTime(moment, id.id),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { timing = true })
                }
            }
            trailing?.invoke()
        }
    }

    if (picking) {
        // Material's date picker takes no first-day-of-week, so the locale it
        // reads is swapped for one whose week starts where the user's does.
        val weekStart = LocalFormat.current.preferences.weekStartsOn
        val configuration = LocalConfiguration.current
        val localised = remember(configuration, weekStart) {
            android.content.res.Configuration(configuration).apply { setLocale(localeForWeekStart(weekStart)) }
        }
        CompositionLocalProvider(LocalConfiguration provides localised) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = local.toLocalDate().toEpochDay() * 86_400_000L,
            )
            DatePickerDialog(
                onDismissRequest = { picking = false },
                confirmButton = {
                    MochiTextButton(onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val date = LocalDate.ofEpochDay(millis / 86_400_000L)
                            onChange(date.atTime(local.toLocalTime()).atZone(id).toEpochSecond())
                        }
                        picking = false
                    }) {
                        Text(stringResource(MochiR.string.common_save))
                    }
                },
                dismissButton = {
                    MochiTextButton(onClick = { picking = false }) {
                        Text(stringResource(MochiR.string.common_cancel))
                    }
                },
            ) {
                DatePicker(state = state)
            }
        }
    }

    if (timing) {
        val state = rememberTimePickerState(
            initialHour = local.hour,
            initialMinute = local.minute,
            is24Hour = LocalFormat.current.preferences.timeFormat == org.mochios.android.i18n.TimeFormat.H24,
        )
        MochiAlertDialog(
            onDismissRequest = { timing = false },
            title = label,
            content = { TimePicker(state = state) },
            confirmText = stringResource(MochiR.string.common_save),
            onConfirm = {
                onChange(
                    local.toLocalDate()
                        .atTime(LocalTime.of(state.hour, state.minute))
                        .atZone(id)
                        .toEpochSecond(),
                )
                timing = false
            },
            dismissText = stringResource(MochiR.string.common_cancel),
            onDismiss = { timing = false },
        )
    }
}

/**
 * The zone one end is typed in, as its city beside a globe; a tap opens the
 * list of zones to choose another.
 */
@Composable
private fun ZoneField(label: String, zone: String, onChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { picking = true }.padding(vertical = 2.dp),
    ) {
        Icon(
            Icons.Outlined.Public,
            contentDescription = label,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = zoneCity(zone),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (picking) {
        ZoneDialog(
            title = label,
            selected = zone,
            onDismiss = { picking = false },
            onSelect = {
                picking = false
                onChange(it)
            },
        )
    }
}

/**
 * Every zone the platform knows, in one list a search box narrows: by the
 * zone's name or by its city, so "york" finds America/New_York.
 */
@Composable
private fun ZoneDialog(
    title: String,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val all = remember { ZoneId.getAvailableZoneIds().sortedWith(String.CASE_INSENSITIVE_ORDER) }
    val shown = remember(search, all) {
        val wanted = search.trim()
        if (wanted.isEmpty()) {
            all
        } else {
            all.filter { it.contains(wanted, ignoreCase = true) || zoneCity(it).contains(wanted, ignoreCase = true) }
        }
    }
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        dismissText = stringResource(MochiR.string.common_cancel),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MochiTextField(
                    value = search,
                    onValueChange = { search = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(shown, key = { it }) { zone ->
                        Text(
                            // A sea zone is its offset from UTC, which is its whole name.
                            text = if (zone.startsWith("Etc/GMT")) zoneCity(zone) else zone,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (zone == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(zone) }
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        },
    )
}

/**
 * The Repeat field: the plain choices, and beneath them the weekday picks,
 * the interval and the end when the rule is a custom one.
 */
@Composable
private fun RepeatField(recurrence: Recurrence, onChange: (Recurrence) -> Unit) {
    val options = listOf(
        Frequency.NEVER to R.string.calendars_repeat_never,
        Frequency.DAILY to R.string.calendars_repeat_daily,
        Frequency.WEEKLY to R.string.calendars_repeat_weekly,
        Frequency.MONTHLY to R.string.calendars_repeat_monthly,
        Frequency.YEARLY to R.string.calendars_repeat_yearly,
        Frequency.CUSTOM to R.string.calendars_repeat_custom,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledSelectField(
            label = stringResource(R.string.calendars_event_repeat),
            placeholder = "",
            options = options.map { it.first.name to stringResource(it.second) },
            selected = recurrence.frequency.name,
            onSelect = { chosen ->
                val frequency = Frequency.entries.firstOrNull { it.name == chosen } ?: Frequency.NEVER
                onChange(recurrence.copy(frequency = frequency))
            },
        )
        if (recurrence.frequency == Frequency.CUSTOM) {
            LabeledSelectField(
                label = stringResource(R.string.calendars_repeat_interval),
                placeholder = "",
                options = (1..12).map { it.toString() to it.toString() },
                selected = recurrence.interval.toString(),
                onSelect = { onChange(recurrence.copy(interval = it.toIntOrNull() ?: 1)) },
            )
            Text(
                text = stringResource(R.string.calendars_repeat_days),
                style = MaterialTheme.typography.labelMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (index in 0..6) {
                    val on = index in recurrence.days
                    MochiTextButton(
                        onClick = {
                            val days = if (on) recurrence.days - index else recurrence.days + index
                            onChange(recurrence.copy(days = days))
                        },
                    ) {
                        Text(
                            text = weekdayInitial(index),
                            color = if (on) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            LabeledSelectField(
                label = stringResource(R.string.calendars_repeat_count),
                placeholder = stringResource(R.string.calendars_repeat_forever),
                options = listOf("" to stringResource(R.string.calendars_repeat_forever)) +
                    listOf(2, 3, 5, 10, 20, 50, 100).map { it.toString() to it.toString() },
                selected = recurrence.count?.toString().orEmpty(),
                onSelect = { onChange(recurrence.copy(count = it.toIntOrNull(), until = null)) },
            )
        }
    }
}

/** The weekday's one-letter label, in the user's own language. */
@Composable
private fun weekdayInitial(index: Int): String {
    // The preference indexes Sunday 0; java.time indexes Monday 1.
    val day = java.time.DayOfWeek.of(if (index == 0) 7 else index)
    return day.getDisplayName(java.time.format.TextStyle.NARROW, LocalConfiguration.current.locales[0])
}

/** A locale whose week starts where the user's does, for the date picker. */
private fun localeForWeekStart(weekStartsOn: Int): java.util.Locale = when (weekStartsOn) {
    0 -> java.util.Locale.US
    1 -> java.util.Locale.UK
    6 -> java.util.Locale.forLanguageTag("ar-SA")
    else -> java.util.Locale.getDefault()
}
