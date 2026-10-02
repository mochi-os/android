// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.ColorPicker
import org.mochios.android.ui.components.FieldLabel
import org.mochios.android.ui.components.LabeledSelectField
import org.mochios.android.ui.components.LabeledSwitchRow
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.ZonePicker
import org.mochios.android.util.Zones
import org.mochios.android.util.zoneCity
import org.mochios.calendars.R
import org.mochios.calendars.model.Zone
import org.mochios.calendars.ui.dialogs.DeleteEventDialog
import org.mochios.calendars.ui.dialogs.ScopeDialog
import org.mochios.calendars.ui.dialogs.reminderChoices
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.mochios.android.R as MochiR

/**
 * The event editor: Title, Calendar, All day, Start and its zone, End and its
 * zone, Location, Repeat, Reminder, Description, in that order. A recurring
 * event asks whether a save, a delete or a copy is for the one occurrence or
 * the whole series. [onCopy] opens the editor on a copy of the open event,
 * with the occurrence and the scope the user chose; [onCopied] is a saved
 * copy, which [onSaved] is not told of.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onCopied: () -> Unit,
    onDeleted: () -> Unit,
    onCopy: (String, Long, Scope) -> Unit,
    viewModel: EventEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    // Back and the arrow ask before dropping a changed form, and nothing
    // leaves while a save is running, as the web editor's close does.
    val dirty = viewModel.dirty(uiState)
    var leaving by remember { mutableStateOf(false) }
    val close = {
        when {
            uiState.isSaving -> Unit
            dirty -> leaving = true
            else -> onBack()
        }
    }
    BackHandler(enabled = dirty || uiState.isSaving) { close() }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            if (uiState.copying) onCopied() else onSaved()
        }
    }
    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onDeleted() }
    LaunchedEffect(uiState.copy) {
        val scope = uiState.copy ?: return@LaunchedEffect
        val event = uiState.event ?: return@LaunchedEffect
        viewModel.routed()
        onCopy(event, uiState.occurrence, scope)
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbar.showSnackbar(it.userMessage()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            uiState.copying -> stringResource(R.string.calendars_editor_copy_title)
                            uiState.event == null -> stringResource(R.string.calendars_event_new)
                            else -> stringResource(R.string.calendars_event_edit)
                        },
                    )
                },
                navigationIcon = {
                    MochiIconButton(onClick = close) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
                actions = {
                    if (uiState.event != null) {
                        MochiIconButton(onClick = viewModel::copy, enabled = !uiState.isLoading) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.calendars_event_copy),
                            )
                        }
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
            LabeledSelectField(
                label = stringResource(R.string.calendars_event_calendar),
                placeholder = "",
                options = uiState.calendars.map { it.id to it.name },
                selected = uiState.calendar,
                onSelect = viewModel::calendar,
                icon = Icons.Outlined.CalendarMonth,
            )
            EventText(
                title = uiState.title,
                description = uiState.description,
                location = uiState.location,
                url = uiState.url,
                untitled = uiState.untitled,
                enabled = !uiState.isSaving,
                onTitle = viewModel::title,
                onDescription = viewModel::description,
                onLocation = viewModel::location,
                onUrl = viewModel::url,
                // The keyboard's action takes the Save button's own path, prompts and all.
                onSave = { if (uiState.writable) viewModel.save() },
                select = uiState.copying,
            )
            LabeledSwitchRow(
                icon = Icons.Outlined.Schedule,
                label = stringResource(R.string.calendars_event_allday),
                checked = uiState.allday,
                onCheckedChange = viewModel::allday,
                enabled = !uiState.isSaving,
            )
            EventMoments(
                start = uiState.start,
                finish = uiState.finish,
                allday = uiState.allday,
                zone = uiState.zone,
                own = viewModel.zone,
                revealed = uiState.revealed,
                enabled = !uiState.isSaving,
                onStart = viewModel::start,
                onFinish = viewModel::finish,
                onZone = viewModel::zone,
                onReveal = viewModel::reveal,
            )
            RepeatField(
                recurrence = uiState.recurrence,
                onChange = viewModel::recurrence,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(
                    text = stringResource(R.string.calendars_event_reminder),
                    icon = Icons.Outlined.Notifications,
                )
                uiState.reminders.forEachIndexed { index, minutes ->
                    ReminderRow(
                        minutes = minutes,
                        onSelect = { viewModel.reminder(index, it) },
                        onRemove = { viewModel.removeReminder(index) },
                    )
                }
                MochiTextButton(onClick = viewModel::addReminder) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.calendars_reminder_add))
                }
            }
            EventColour(
                colour = uiState.colour,
                enabled = !uiState.isSaving,
                onColour = viewModel::colour,
            )
            MochiButton(
                onClick = viewModel::save,
                enabled = !uiState.isSaving && uiState.writable,
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
        // A copy is of the one occurrence or of the whole series; there is
        // no series to cut.
        ScopeDialog(
            deleting = uiState.prompt == Prompt.DELETE,
            copying = uiState.prompt == Prompt.COPY,
            following = uiState.prompt != Prompt.COPY,
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
    if (leaving) {
        MochiAlertDialog(
            onDismissRequest = { leaving = false },
            title = stringResource(R.string.calendars_discard_title),
            text = stringResource(R.string.calendars_discard_message),
            confirmText = stringResource(R.string.calendars_discard),
            onConfirm = {
                leaving = false
                onBack()
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}

/**
 * The event's own colour: the compact picker, and above it a text box for a
 * colour written as something other than #rrggbb, such as a CSS name, which
 * the picker cannot show and the event keeps until it is changed.
 */
@Composable
internal fun EventColour(colour: String, enabled: Boolean, onColour: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(text = stringResource(R.string.calendars_colour), icon = Icons.Outlined.Palette)
        val hex = HEX.matches(colour)
        if (colour.isNotEmpty() && !hex) {
            val label = stringResource(R.string.calendars_event_colour_value)
            MochiTextField(
                value = colour,
                onValueChange = onColour,
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
        ColorPicker(
            hex = if (hex) colour else "",
            onHexChange = onColour,
            collapsible = true,
            onClear = { onColour("") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A colour the picker can show: `#rrggbb`. */
private val HEX = Regex("^#[0-9a-fA-F]{6}$")

/**
 * The event's text, in the web editor's order: the title, the description
 * straight below it - two lines to start, growing with what is typed - then
 * the location and the link. The keyboard's action in the title is [onSave].
 * With [select], as on a copy, the title takes the focus with its text
 * selected, ready to be typed over.
 */
@Composable
internal fun EventText(
    title: String,
    description: String,
    location: String,
    url: String,
    untitled: Boolean,
    enabled: Boolean,
    onTitle: (String) -> Unit,
    onDescription: (String) -> Unit,
    onLocation: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSave: () -> Unit,
    select: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(title)) }
    // A title set from outside the field - loaded, or a copy opened - replaces it.
    if (field.text != title) field = TextFieldValue(title, TextRange(title.length))
    LaunchedEffect(select) {
        if (select) {
            field = TextFieldValue(title, TextRange(0, title.length))
            focus.requestFocus()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MochiTextField(
            value = field,
            onValueChange = { value ->
                field = value
                if (value.text != title) onTitle(value.text)
            },
            label = { Text(stringResource(R.string.calendars_event_title)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
            isError = untitled,
            supportingText = if (untitled) {
                { Text(stringResource(R.string.calendars_title_required)) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        MochiTextField(
            value = description,
            onValueChange = onDescription,
            label = { Text(stringResource(R.string.calendars_event_description)) },
            enabled = enabled,
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        MochiTextField(
            value = location,
            onValueChange = onLocation,
            label = { Text(stringResource(R.string.calendars_event_location)) },
            leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        MochiTextField(
            value = url,
            onValueChange = onUrl,
            label = { Text(stringResource(R.string.calendars_event_url)) },
            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
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
    // An all-day day is a date held at its UTC midnight, so it is read in UTC.
    val id = remember(zone, allday) { shownIn(allday, zone) }
    val local = remember(moment, id) { Instant.ofEpochSecond(moment).atZone(id) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(text = label, icon = Icons.Outlined.Event)
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
 * The Start and End rows. Each end is typed in its own zone, named beneath it
 * when an end reads in another zone than the user's [own] or they asked to see
 * the zones; otherwise a globe beside the End row reveals them, and the Start
 * row keeps the globe's room so the two rows line up. An all-day event has no
 * clock, so its dates stay in the user's own zone.
 */
@Composable
internal fun EventMoments(
    start: Long,
    finish: Long,
    allday: Boolean,
    zone: Zone,
    own: String,
    revealed: Boolean,
    enabled: Boolean,
    onStart: (Long) -> Unit,
    onFinish: (Long) -> Unit,
    onZone: (Zone) -> Unit,
    onReveal: () -> Unit,
) {
    val zoned = !allday && (revealed || foreign(zone, own))
    val globe = !allday && !zoned
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MomentField(
            label = stringResource(R.string.calendars_event_start),
            moment = start,
            allday = allday,
            zone = if (allday) own else zone.start,
            onChange = onStart,
            trailing = if (globe) {
                { Spacer(Modifier.minimumInteractiveComponentSize().size(GLOBE)) }
            } else {
                null
            },
        )
        if (zoned) {
            ZoneField(
                label = stringResource(R.string.calendars_event_zone_start),
                zone = zone.start,
                onChange = { onZone(follow(zone, it)) },
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MomentField(
            label = stringResource(R.string.calendars_event_finish),
            moment = finish,
            allday = allday,
            zone = if (allday) own else zone.finish,
            onChange = onFinish,
            trailing = if (globe) {
                {
                    MochiIconButton(onClick = onReveal, enabled = enabled) {
                        Icon(
                            Icons.Outlined.Public,
                            contentDescription = stringResource(R.string.calendars_event_timezone),
                        )
                    }
                }
            } else {
                null
            },
        )
        if (zoned) {
            ZoneField(
                label = stringResource(R.string.calendars_event_zone_finish),
                zone = zone.finish,
                onChange = { onZone(zone.copy(finish = it)) },
            )
        }
    }
}

/** An icon button's own size, which the Start row keeps free for the globe. */
private val GLOBE = 40.dp

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
            // By its current name: an event written in Asia/Calcutta reads Kolkata.
            text = zoneCity(Zones.current(zone)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (picking) {
        ZonePicker(
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
            icon = Icons.Outlined.Repeat,
            placeholder = "",
            options = options.map { it.first.name to stringResource(it.second) },
            selected = recurrence.frequency.name,
            onSelect = { chosen ->
                val frequency = Frequency.entries.firstOrNull { it.name == chosen } ?: Frequency.NEVER
                onChange(recurrence.copy(frequency = frequency))
            },
        )
        if (recurrence.frequency == Frequency.CUSTOM && !recurrence.expressible) {
            // A rule the settings cannot express is kept as written; choosing
            // a repeat replaces it.
            Text(
                text = recurrence.rule.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (recurrence.frequency == Frequency.CUSTOM) {
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

/** One of an event's reminders: its choice, and a button that removes it. */
@Composable
private fun ReminderRow(minutes: Int, onSelect: (Int) -> Unit, onRemove: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val choices = reminderChoices(minutes)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            MochiOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = choices.firstOrNull { it.first == minutes.toString() }?.second.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            MochiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { (value, label) ->
                    MochiDropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            onSelect(value.toInt())
                        },
                        selected = value == minutes.toString(),
                    )
                }
            }
        }
        MochiIconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.calendars_reminder_remove))
        }
    }
}
