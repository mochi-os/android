// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.ColorPicker
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.ZonePicker
import org.mochios.android.util.Zones
import org.mochios.android.util.flightNumber
import org.mochios.android.util.zoneCity
import org.mochios.calendars.R
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.model.Zone
import org.mochios.calendars.ui.calendar.openLocation
import org.mochios.calendars.ui.components.DateDialog
import org.mochios.calendars.ui.dialogs.DeleteEventDialog
import org.mochios.calendars.ui.dialogs.ScopeDialog
import org.mochios.calendars.ui.dialogs.reminderChoices
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import org.mochios.android.R as MochiR

/**
 * The event editor: Title, Calendar, All day, Start and its zone, End and its
 * zone, Location, Repeat, Reminder, Description, in that order. An open event
 * saves as it goes, as the web's side panel does, and has no Save; a new one
 * is made by Create, a copy as any other. A recurring event asks whether a
 * save, a delete or a copy is for the one occurrence or the whole series.
 * [onOpen] goes on as another event's editor: the one a save left the
 * occurrence in. [onCreated] leaves once a new event is made, for the
 * calendar to say so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    onBack: () -> Unit,
    onOpen: (event: String, moment: Long) -> Unit,
    onDeleted: () -> Unit,
    onCreated: () -> Unit,
    viewModel: EventEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val creating = uiState.event == null
    // A new event asks before something typed is dropped; an open one saves
    // on the way out, and asks only of a change that cannot be saved.
    val dirty = viewModel.dirty(uiState)
    var leaving by remember { mutableStateOf(false) }
    val close = {
        when {
            uiState.isSaving -> Unit
            !creating -> viewModel.leave()
            dirty -> leaving = true
            else -> onBack()
        }
    }
    BackHandler(enabled = !creating || dirty || uiState.isSaving) { close() }

    LaunchedEffect(uiState.follow) {
        uiState.follow?.let { onOpen(it.event, it.moment) }
    }
    LaunchedEffect(uiState.left) { if (uiState.left) onBack() }
    LaunchedEffect(uiState.created) { if (uiState.created) onCreated() }
    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onDeleted() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbar.showSnackbar(it.userMessage()) }
    }
    // A save or delete refused because the event changed elsewhere says so,
    // and Reload reads it again, as the web editor's toast does. Said from
    // the screen's own scope: the flag clears at once, which restarts this
    // effect, and the message has to outlive it.
    val changed = stringResource(R.string.calendars_event_changed)
    val reload = stringResource(R.string.calendars_reload)
    val scope = rememberCoroutineScope()
    LaunchedEffect(uiState.changed) {
        if (uiState.changed) {
            viewModel.told()
            scope.launch {
                val chosen = snackbar.showSnackbar(changed, actionLabel = reload, duration = SnackbarDuration.Long)
                if (chosen == SnackbarResult.ActionPerformed) viewModel.reload()
            }
        }
    }
    // Nothing more can be asked of the event while it saves or deletes, or
    // when it never loaded.
    val busy = uiState.isLoading || uiState.isSaving || uiState.isDeleting || uiState.failure != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            creating -> stringResource(R.string.calendars_event_new)
                            else -> uiState.opened?.first?.title?.ifBlank { null }
                                ?: stringResource(R.string.calendars_event_edit)
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
                    if (uiState.event != null && uiState.failure == null) {
                        MochiIconButton(onClick = viewModel::copy, enabled = !busy) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.calendars_event_copy),
                            )
                        }
                        MochiIconButton(onClick = viewModel::confirm, enabled = !busy && uiState.writable) {
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
        // An event that did not load shows why and offers Retry, never an
        // empty form whose Save would create something new.
        uiState.failure?.let { failure ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                ErrorState(error = failure, onRetry = viewModel::retry)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            CalendarField(
                calendars = uiState.calendars,
                selected = uiState.calendar,
                onSelect = viewModel::calendar,
            )
            EventText(
                title = uiState.title,
                description = uiState.description,
                location = uiState.location,
                url = uiState.url,
                untitled = uiState.untitled,
                asked = uiState.asked,
                enabled = !uiState.isSaving,
                onTitle = viewModel::title,
                onDescription = viewModel::description,
                onLocation = viewModel::location,
                onUrl = viewModel::url,
                // The keyboard's action saves, prompts and all, or makes a new event.
                onSave = {
                    when {
                        !uiState.writable -> Unit
                        creating -> viewModel.create()
                        else -> viewModel.save()
                    }
                },
                onLeave = { if (!creating && uiState.writable) viewModel.save() },
                select = uiState.copying,
            )
            EditorDivider()
            EditorRow(icon = Icons.Outlined.Schedule) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.calendars_event_allday),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    Switch(checked = uiState.allday, onCheckedChange = viewModel::allday, enabled = !uiState.isSaving)
                }
                Spacer(Modifier.width(16.dp))
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Dashed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.calendars_status_tentative),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    Switch(checked = uiState.tentative, onCheckedChange = viewModel::tentative, enabled = !uiState.isSaving)
                }
            }
            EventMoments(
                start = uiState.start,
                finish = uiState.finish,
                allday = uiState.allday,
                zone = uiState.zone,
                own = viewModel.zone,
                ordered = uiState.ordered,
                onStart = viewModel::start,
                onFinish = viewModel::finish,
                onZone = viewModel::zone,
            )
            RepeatField(
                recurrence = uiState.recurrence,
                custom = uiState.custom,
                start = uiState.start,
                allday = uiState.allday,
                zone = uiState.zone.start.ifBlank { viewModel.zone },
                onRepeat = viewModel::repeat,
                onCustom = viewModel::custom,
                onChange = viewModel::recurrence,
            )
            EditorDivider()
            // The bell leads the first reminder, or Add reminder when there is none.
            uiState.reminders.forEachIndexed { index, minutes ->
                ReminderRow(
                    minutes = minutes,
                    first = index == 0,
                    onSelect = { viewModel.reminder(index, it) },
                    onRemove = { viewModel.removeReminder(index) },
                )
            }
            EditorRow(
                icon = if (uiState.reminders.isEmpty()) Icons.Outlined.Notifications else null,
                modifier = Modifier.clickable(onClick = viewModel::addReminder),
            ) {
                Text(
                    text = stringResource(R.string.calendars_reminder_add),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EditorDivider()
            EventColour(
                colour = uiState.colour,
                enabled = !uiState.isSaving,
                onColour = viewModel::colour,
            )
            if (creating) {
                Spacer(Modifier.height(16.dp))
                MochiButton(
                    onClick = viewModel::create,
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
                            Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                    }
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.calendars_create_submit))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (uiState.prompt != null) {
        // A copy is of the one occurrence or of the whole series; there is
        // no series to cut.
        ScopeDialog(
            title = stringResource(
                when (uiState.prompt) {
                    Prompt.DELETE -> R.string.calendars_scope_delete
                    Prompt.COPY -> R.string.calendars_scope_copy
                    else -> R.string.calendars_scope_save
                },
            ),
            destructive = uiState.prompt == Prompt.DELETE,
            following = uiState.prompt != Prompt.COPY,
            onDismiss = viewModel::dismiss,
            onOne = { viewModel.scope(Scope.ONE) },
            onFollowing = { viewModel.scope(Scope.FOLLOWING) },
            onAll = { viewModel.scope(Scope.ALL) },
        )
    }
    if (uiState.confirming) {
        DeleteEventDialog(
            deleting = uiState.isDeleting,
            onDismiss = viewModel::dismiss,
            onConfirm = viewModel::delete,
        )
    }
    if (leaving || uiState.discarding) {
        MochiAlertDialog(
            onDismissRequest = {
                leaving = false
                viewModel.keep()
            },
            title = stringResource(R.string.calendars_discard_title),
            text = stringResource(R.string.calendars_discard_message),
            confirmText = stringResource(R.string.calendars_discard),
            onConfirm = {
                leaving = false
                if (creating) onBack() else viewModel.discard()
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}

/** Calls [left] when this field loses the focus it had. */
internal fun Modifier.leaving(left: () -> Unit): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { state ->
        if (focused && !state.isFocused) left()
        focused = state.isFocused
    }
}

/** The width of the column the editor's glyphs sit in, before what each row holds. */
private val GLYPH = 40.dp

/** The least height of one of the editor's rows: a touch target. */
private val ROW = 48.dp

/**
 * One of the editor's rows: [icon] in the start column, level with the first
 * line, then [content]. A row with no icon keeps the column empty, so all the
 * form holds starts on one line, as a phone's calendar lays its editor out.
 */
@Composable
internal fun EditorRow(
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.width(GLYPH).heightIn(min = ROW), contentAlignment = Alignment.CenterStart) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            modifier = Modifier.weight(1f).heightIn(min = ROW),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** The rule between the editor's groups of rows. */
@Composable
private fun EditorDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * A field drawn on the page, with no box: what is typed, or [placeholder]
 * where nothing is, in the row's own column. The field's name is its
 * placeholder and its row's glyph, and is read out from [placeholder]
 * whatever it holds.
 */
@Composable
private fun PlainField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    error: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colours = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics { contentDescription = placeholder },
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        textStyle = style.copy(color = if (enabled) colours.onSurface else colours.onSurface.copy(alpha = 0.38f)),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        cursorBrush = SolidColor(colours.primary),
        decorationBox = { field ->
            Box(modifier = Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (value.text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = style,
                        color = if (error) colours.error else colours.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                field()
            }
        },
    )
}

/** [PlainField] for a plain string, the cursor kept across what is typed. */
@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // A value set from outside the field replaces it.
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    PlainField(
        value = field,
        onValueChange = { typed ->
            field = typed
            if (typed.text != value) onValueChange(typed.text)
        },
        placeholder = placeholder,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
    )
}

/**
 * Text on the page that opens a picker, as a date, a time or a choice is
 * shown: the value in the body style, with the press shown across it.
 */
@Composable
private fun Choice(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
    trailing: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .heightIn(min = ROW),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = align,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = true),
        )
        if (trailing) {
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The event's own colour: the compact picker, and above it a text box for a
 * colour written as something other than #rrggbb, such as a CSS name, which
 * the picker cannot show and the event keeps until it is changed.
 */
@Composable
internal fun EventColour(colour: String, enabled: Boolean, onColour: (String) -> Unit) {
    EditorRow(icon = Icons.Outlined.Palette) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            val hex = HEX.matches(colour)
            if (colour.isNotEmpty() && !hex) {
                PlainField(
                    value = colour,
                    onValueChange = onColour,
                    placeholder = stringResource(R.string.calendars_event_colour_value),
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
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
}

/** A colour the picker can show: `#rrggbb`. */
private val HEX = Regex("^#[0-9a-fA-F]{6}$")

/**
 * The event's text, in the web editor's order: the title, the description
 * straight below it - two lines to start, growing with what is typed - then
 * the location and the link. The keyboard's action in the title is [onSave].
 * With [select], as on a copy, the title takes the focus with its text
 * selected, ready to be typed over. Each save tried without a title, counted
 * by [asked], brings the title into view and puts the cursor in it.
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
    /** A field was left: an edit saves it. */
    onLeave: () -> Unit = {},
    select: Boolean = false,
    asked: Int = 0,
) {
    val focus = remember { FocusRequester() }
    val view = remember { BringIntoViewRequester() }
    LaunchedEffect(asked) {
        if (asked > 0) {
            focus.requestFocus()
            view.bringIntoView()
        }
    }
    var field by remember { mutableStateOf(TextFieldValue(title)) }
    // A title set from outside the field - loaded, or a copy opened - replaces it.
    if (field.text != title) field = TextFieldValue(title, TextRange(title.length))
    LaunchedEffect(select) {
        if (select) {
            field = TextFieldValue(title, TextRange(0, title.length))
            focus.requestFocus()
        }
    }
    Column {
        EditorRow(icon = null) {
            Column(modifier = Modifier.weight(1f)) {
                PlainField(
                    value = field,
                    onValueChange = { value ->
                        field = value
                        if (value.text != title) onTitle(value.text)
                    },
                    placeholder = stringResource(R.string.calendars_event_title),
                    style = MaterialTheme.typography.headlineSmall,
                    singleLine = true,
                    enabled = enabled,
                    error = untitled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                    modifier = Modifier.fillMaxWidth().bringIntoViewRequester(view).focusRequester(focus).leaving(onLeave),
                )
                if (untitled) {
                    Text(
                        text = stringResource(R.string.calendars_title_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
        EditorDivider()
        EditorRow(icon = Icons.AutoMirrored.Outlined.Notes) {
            PlainField(
                value = description,
                onValueChange = onDescription,
                placeholder = stringResource(R.string.calendars_event_description),
                enabled = enabled,
                minLines = 2,
                modifier = Modifier.weight(1f).leaving(onLeave),
            )
        }
        val context = LocalContext.current
        val format = LocalFormat.current
        EditorRow(icon = Icons.Outlined.LocationOn) {
            PlainField(
                value = location,
                onValueChange = onLocation,
                placeholder = stringResource(R.string.calendars_event_location),
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.weight(1f).leaving(onLeave),
            )
            // Opens the place on a map, or the flight on the user's tracker, as
            // the event sheet's location does.
            if (location.isNotBlank()) {
                val flight = flightNumber(location) != null
                MochiIconButton(onClick = { openLocation(context, location, format.preferences) }) {
                    Icon(
                        if (flight) Icons.Outlined.Flight else Icons.Outlined.Map,
                        contentDescription = stringResource(
                            if (flight) R.string.calendars_location_flight else R.string.calendars_location_map,
                        ),
                    )
                }
            }
        }
        EditorRow(icon = Icons.Outlined.Link) {
            PlainField(
                value = url,
                onValueChange = onUrl,
                placeholder = stringResource(R.string.calendars_event_url),
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.weight(1f).leaving(onLeave),
            )
        }
    }
}

/**
 * A date, and a time beside it unless the event is all day, both read in
 * [zone]. Tapping either field opens
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
) {
    val format = LocalFormat.current
    var picking by remember { mutableStateOf(false) }
    var timing by remember { mutableStateOf(false) }
    // An all-day day is a date held at its UTC midnight, so it is read in UTC.
    val id = remember(zone, allday) { shownIn(allday, zone) }
    val local = remember(moment, id) { Instant.ofEpochSecond(moment).atZone(id) }

    Column {
        Caption(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Choice(
                text = format.formatDate(moment, id.id),
                onClick = { picking = true },
                modifier = Modifier.weight(1f),
            )
            if (allday) {
                // The time's room stays, so turning All day on hides the time
                // without widening the date.
                Spacer(Modifier.width(TIME))
            } else {
                Choice(
                    text = format.formatTime(moment, id.id),
                    onClick = { timing = true },
                    align = TextAlign.End,
                    modifier = Modifier.width(TIME),
                )
            }
        }
    }

    if (picking) {
        DateDialog(
            day = local.toLocalDate(),
            onDismiss = { picking = false },
            onPick = { date ->
                onChange(date.atTime(local.toLocalTime()).atZone(id).toEpochSecond())
                picking = false
            },
        )
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
 * The Start and End rows, each with the zone its end is typed in beneath it,
 * always, as the web shows it beside the time; a phone has no room beside.
 * A blank zone is the user's [own]. An all-day event has no clock, so its
 * dates stay in the user's own zone and show none, and its End shows its
 * last day, where the form holds the day after it. An end before the start,
 * not [ordered], says so beneath End.
 */
@Composable
internal fun EventMoments(
    start: Long,
    finish: Long,
    allday: Boolean,
    zone: Zone,
    own: String,
    ordered: Boolean = true,
    onStart: (Long) -> Unit,
    onFinish: (Long) -> Unit,
    onZone: (Zone) -> Unit,
) {
    val begins = zone.start.ifBlank { own }
    val ends = zone.finish.ifBlank { begins }
    EditorRow(icon = null) {
        Column(modifier = Modifier.weight(1f)) {
            MomentField(
                label = stringResource(R.string.calendars_event_start),
                moment = start,
                allday = allday,
                zone = if (allday) own else begins,
                onChange = onStart,
            )
            if (!allday) {
                ZoneField(
                    label = stringResource(R.string.calendars_event_zone_start),
                    zone = begins,
                    onChange = { onZone(follow(zone, it)) },
                )
            }
        }
    }
    EditorRow(icon = null) {
        Column(modifier = Modifier.weight(1f)) {
            MomentField(
                label = stringResource(R.string.calendars_event_finish),
                moment = if (allday) finish - DAY else finish,
                allday = allday,
                zone = if (allday) own else ends,
                onChange = { onFinish(if (allday) it + DAY else it) },
            )
            if (!ordered) {
                Text(
                    text = stringResource(R.string.calendars_event_backwards),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!allday) {
                ZoneField(
                    label = stringResource(R.string.calendars_event_zone_finish),
                    zone = ends,
                    onChange = { onZone(zone.copy(finish = it)) },
                )
            }
        }
    }
}

/**
 * The calendar an event is in, with no label of its own: the calendar icon
 * beside the chosen calendar's name, and beside each calendar in the list.
 * The field's name is read out from [R.string.calendars_event_calendar].
 */
@Composable
internal fun CalendarField(
    calendars: List<Calendar>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val name = stringResource(R.string.calendars_event_calendar)
    Box {
        EditorRow(
            icon = Icons.Outlined.CalendarMonth,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .semantics { contentDescription = name },
        ) {
            Text(
                text = calendars.firstOrNull { it.id == selected }?.name.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MochiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            calendars.forEach { calendar ->
                MochiDropdownMenuItem(
                    text = { Text(calendar.name) },
                    onClick = {
                        expanded = false
                        onSelect(calendar.id)
                    },
                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                    selected = calendar.id == selected,
                )
            }
        }
    }
}

/** The width of an end's time beside its date. */
private val TIME = 140.dp

/** A day, in seconds: an all-day form's end is the day after its last. */
private const val DAY = 86_400L


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
 * The Repeat field, as the web editor has it: the plain choices and Custom.
 * A plain choice says everything and leaves nothing of a custom rule behind;
 * Custom opens the settings - their own frequency, every how many, the
 * weekdays of a weekly rule in the user's week order, and how the series
 * ends. A rule the settings cannot express is kept as written, shows as
 * Custom and is said in words; choosing a repeat replaces it. An end on a
 * date counts from the event's [start], in [zone], or on its own day when
 * the event is [allday].
 */
@Composable
internal fun RepeatField(
    recurrence: Recurrence,
    custom: Boolean,
    start: Long,
    allday: Boolean,
    zone: String,
    onRepeat: (Frequency) -> Unit,
    onCustom: () -> Unit,
    onChange: (Recurrence) -> Unit,
) {
    val kept = !recurrence.expressible
    val selected = if (custom || kept) CUSTOM else recurrence.frequency.name
    val options = listOf(
        Frequency.NEVER.name to stringResource(R.string.calendars_repeat_never),
        Frequency.DAILY.name to stringResource(R.string.calendars_repeat_daily),
        Frequency.WEEKLY.name to stringResource(R.string.calendars_repeat_weekly),
        Frequency.MONTHLY.name to stringResource(R.string.calendars_repeat_monthly),
        Frequency.YEARLY.name to stringResource(R.string.calendars_repeat_yearly),
        CUSTOM to stringResource(R.string.calendars_repeat_custom),
    )
    Column {
        var expanded by rememberSaveable { mutableStateOf(false) }
        val name = stringResource(R.string.calendars_event_repeat)
        Box {
            EditorRow(
                icon = Icons.Outlined.Repeat,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .semantics { contentDescription = name },
            ) {
                Text(
                    text = options.firstOrNull { it.first == selected }?.second.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MochiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, label) ->
                    MochiDropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            when {
                                value == selected -> Unit
                                value == CUSTOM -> onCustom()
                                else -> onRepeat(Frequency.valueOf(value))
                            }
                        },
                        selected = value == selected,
                    )
                }
            }
        }
        if (kept) {
            val format = LocalFormat.current
            val resources = LocalContext.current.resources
            val locale = LocalConfiguration.current.locales[0]
            val summary = remember(recurrence.rule, format, locale) {
                ruleSummary(recurrence.rule.orEmpty(), resources, format, locale)
            }
            if (summary != null) {
                EditorRow(icon = null) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).testTag("kept-rule"),
                    )
                }
            }
        } else if (custom) {
            EditorRow(icon = null) {
                Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                    CustomRepeat(recurrence = recurrence, start = start, allday = allday, zone = zone, onChange = onChange)
                }
            }
        }
    }
}

/** The Repeat field's value for the Custom choice. */
private const val CUSTOM = "CUSTOM"

/**
 * The custom repeat settings beneath the Repeat field, as the rest of the
 * editor is: each choice under its caption, and the numbers typed into
 * outlined boxes, which, with the weekday chips, are what takes typing or
 * toggling here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomRepeat(
    recurrence: Recurrence,
    start: Long,
    allday: Boolean,
    zone: String,
    onChange: (Recurrence) -> Unit,
) {
    val format = LocalFormat.current
    val locale = LocalConfiguration.current.locales[0]
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) {
                Select(
                    label = stringResource(R.string.calendars_repeat_frequency),
                    options = listOf(
                        Frequency.DAILY.name to stringResource(R.string.calendars_repeat_daily),
                        Frequency.WEEKLY.name to stringResource(R.string.calendars_repeat_weekly),
                        Frequency.MONTHLY.name to stringResource(R.string.calendars_repeat_monthly),
                        Frequency.YEARLY.name to stringResource(R.string.calendars_repeat_yearly),
                    ),
                    selected = recurrence.frequency.name,
                    onSelect = { onChange(recurrence.copy(frequency = Frequency.valueOf(it))) },
                )
            }
            Box(Modifier.weight(1f)) {
                CountField(
                    label = stringResource(R.string.calendars_repeat_interval),
                    value = recurrence.interval,
                    maximum = 366,
                    onChange = { onChange(recurrence.copy(interval = it)) },
                )
            }
        }
        if (recurrence.frequency == Frequency.WEEKLY) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val first = format.preferences.weekStartsOn
                for (offset in 0..6) {
                    val day = (first + offset) % 7
                    val on = day in recurrence.days
                    FilterChip(
                        selected = on,
                        onClick = {
                            onChange(recurrence.copy(days = if (on) recurrence.days - day else recurrence.days + day))
                        },
                        // The rule indexes Sunday 0; java.time indexes Monday 1.
                        label = {
                            Text(java.time.DayOfWeek.of(if (day == 0) 7 else day).getDisplayName(TextStyle.SHORT, locale))
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) {
                Select(
                    label = stringResource(R.string.calendars_repeat_ends),
                    options = listOf(
                        Ending.NEVER.name to stringResource(R.string.calendars_repeat_end_never),
                        Ending.UNTIL.name to stringResource(R.string.calendars_repeat_until),
                        Ending.COUNT.name to stringResource(R.string.calendars_repeat_after),
                    ),
                    selected = recurrence.ending.name,
                    onSelect = { onChange(recurrence.copy(ending = Ending.valueOf(it))) },
                )
            }
            Box(Modifier.weight(1f)) {
                when (recurrence.ending) {
                    Ending.UNTIL -> Column {
                        val label = stringResource(R.string.calendars_repeat_last)
                        Caption(label)
                        val day = recurrence.until
                            ?: Instant.ofEpochSecond(start).atZone(shownIn(allday, zone)).toLocalDate()
                        Choice(
                            text = format.formatDate(day.atTime(12, 0).toEpochSecond(ZoneOffset.UTC), "UTC"),
                            onClick = { picking = true },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                        )
                        if (picking) {
                            DateDialog(
                                day = day,
                                onDismiss = { picking = false },
                                onPick = {
                                    onChange(recurrence.copy(until = it))
                                    picking = false
                                },
                            )
                        }
                    }
                    Ending.COUNT -> CountField(
                        label = stringResource(R.string.calendars_repeat_count),
                        value = recurrence.count,
                        maximum = 999,
                        onChange = { onChange(recurrence.copy(count = it)) },
                    )
                    Ending.NEVER -> Unit
                }
            }
        }
    }
}

/** A field's name above it, small and quiet, as the editor's captions are. */
@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** A choice among [options] under its [label], its menu opened from the choice. */
@Composable
private fun Select(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        Caption(label)
        Box {
            Choice(
                text = options.firstOrNull { it.first == selected }?.second.orEmpty(),
                onClick = { expanded = true },
                trailing = true,
                modifier = Modifier.fillMaxWidth(),
            )
            MochiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, text) ->
                    MochiDropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            expanded = false
                            onSelect(value)
                        },
                        selected = value == selected,
                    )
                }
            }
        }
    }
}

/**
 * A whole number from 1 to [maximum], typed into an outlined box under its
 * caption. What is typed may be cleared to type another; a number in range is
 * taken as it is typed, one past [maximum] as [maximum], and the box shows the
 * value again once it loses the focus.
 */
@Composable
private fun CountField(label: String, value: Int, maximum: Int, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf(false) }
    Column {
        Caption(label)
        PlainField(
            value = text ?: value.toString(),
            onValueChange = { typed ->
                text = typed
                val number = typed.trim().toIntOrNull()
                if (number != null && number >= 1) onChange(minOf(maximum, number))
            },
            placeholder = label,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 12.dp)
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (!state.isFocused) text = null
                },
        )
    }
}


/**
 * One of an event's reminders: its choice, and a button that removes it. The
 * [first] is led by the bell.
 */
@Composable
private fun ReminderRow(minutes: Int, first: Boolean, onSelect: (Int) -> Unit, onRemove: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val choices = reminderChoices(minutes)
    EditorRow(icon = if (first) Icons.Outlined.Notifications else null) {
        Box(Modifier.weight(1f)) {
            Choice(
                text = choices.firstOrNull { it.first == minutes.toString() }?.second.orEmpty(),
                onClick = { expanded = true },
                trailing = true,
                modifier = Modifier.fillMaxWidth(),
            )
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
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.calendars_reminder_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A tentative event's glyph: a ring of eight dashes, as the event sheet
 * draws it and the web's editor and popover show it.
 */
private val Dashed: ImageVector by lazy {
    ImageVector.Builder("Dashed", 24.dp, 24.dp, 24f, 24f).path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
    ) {
        for (dash in 0 until 8) {
            val from = Math.toRadians(dash * 45.0 - 11.25)
            val to = Math.toRadians(dash * 45.0 + 11.25)
            moveTo(12f + 10f * cos(from).toFloat(), 12f + 10f * sin(from).toFloat())
            arcTo(10f, 10f, 0f, false, true, 12f + 10f * cos(to).toFloat(), 12f + 10f * sin(to).toFloat())
        }
    }.build()
}
