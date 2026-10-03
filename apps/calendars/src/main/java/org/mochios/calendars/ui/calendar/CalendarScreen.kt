// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.files.rememberFileSaveLauncher
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.LabeledSelectField
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiFab
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.calendars.R
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.model.Instance
import org.mochios.calendars.navigation.CalendarsApp
import org.mochios.calendars.navigation.Reminder
import org.mochios.calendars.ui.components.CalendarAction
import org.mochios.calendars.ui.components.CalendarDrawer
import org.mochios.calendars.ui.components.DateDialog
import org.mochios.calendars.ui.dialogs.ColourCalendarDialog
import org.mochios.calendars.ui.dialogs.DeleteCalendarDialog
import org.mochios.calendars.ui.dialogs.ImportDialog
import org.mochios.calendars.ui.dialogs.LinkDialog
import org.mochios.calendars.ui.dialogs.PreferencesDialog
import org.mochios.calendars.ui.dialogs.RenameCalendarDialog
import org.mochios.calendars.ui.dialogs.ReplaceLinkDialog
import org.mochios.calendars.ui.dialogs.RevokeLinkDialog
import org.mochios.calendars.ui.dialogs.ScopeDialog
import org.mochios.calendars.ui.editor.Scope
import org.mochios.calendars.ui.router.CalendarsSection
import org.mochios.android.R as MochiR
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The calendars app's one screen: the drawer of calendars, the toolbar that
 * moves the range, the view itself and the "New event" button. Every view
 * renders from the same occurrence list, so switching between them is a
 * redraw and not a fetch of a different shape. [onCopyEvent] opens the
 * editor on a copy of a stored event's occurrence, with how far the copy
 * reaches; [onCopyOccurrence] on a copy of one the editor cannot load, a
 * subscription's or a birthday. [copied] says a copy was just saved, which
 * the screen reports once and [onCopiedShown] clears; [deleted] that the
 * editor deleted something, reported once with Undo and cleared by
 * [onDeletedShown]; [saved] that the editor saved an event,
 * [CalendarsApp.CREATED] or [CalendarsApp.CHANGED], reported once and
 * cleared by [onSavedShown].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onCreateCalendar: () -> Unit,
    onSubscribe: () -> Unit,
    onConnectDevice: () -> Unit,
    onNewEvent: (Long, Boolean?, Long?) -> Unit,
    onEditEvent: (String, Long) -> Unit,
    onCopyEvent: (String, Long, Scope) -> Unit,
    onCopyOccurrence: (Instance) -> Unit,
    copied: Boolean = false,
    onCopiedShown: () -> Unit = {},
    deleted: Boolean = false,
    onDeletedShown: () -> Unit = {},
    saved: String = "",
    onSavedShown: () -> Unit = {},
    reminder: Reminder? = null,
    onReminderShown: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    val resources = LocalResources.current

    var selected by remember { mutableStateOf<Instance?>(null) }
    var copying by remember { mutableStateOf<Instance?>(null) }
    var moving by remember { mutableStateOf<Move?>(null) }
    var renaming by remember { mutableStateOf<Calendar?>(null) }
    var colouring by remember { mutableStateOf<Calendar?>(null) }
    var deleting by remember { mutableStateOf<Calendar?>(null) }
    var linking by remember { mutableStateOf<Calendar?>(null) }
    var revoking by remember { mutableStateOf<Calendar?>(null) }
    var preferences by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    // The calendar a file is being picked for, kept by id so it outlasts the
    // activity being recreated behind the picker.
    var importing by rememberSaveable { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val calendar = uiState.calendars.firstOrNull { it.id == importing }
        importing = null
        if (uri != null && calendar != null) viewModel.import(calendar, uri)
    }
    // The picker only says where the export goes; the ViewModel writes it.
    val saver = rememberFileSaveLauncher(Icalendar.TYPE) { uri -> viewModel.save(uri) }

    DisposableRefresh(lifecycle) {
        viewModel.load(refreshing = true, reset = false)
        viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        // Each message on its own, so a move's "Undo" waiting to be tapped
        // does not hold up the next.
        viewModel.events.collect { event ->
            scope.launch {
                when (event) {
                    is CalendarEvent.Failed -> snackbar.showSnackbar(event.error.userMessage())
                    is CalendarEvent.Polled -> snackbar.showSnackbar(
                        resources.getString(
                            if (event.linked) R.string.calendars_polled_synced else R.string.calendars_polled_current,
                        ),
                    )
                    CalendarEvent.Moved -> {
                        val chosen = snackbar.showSnackbar(
                            message = resources.getString(R.string.calendars_event_moved),
                            actionLabel = resources.getString(R.string.calendars_undo),
                            duration = SnackbarDuration.Long,
                        )
                        if (chosen == SnackbarResult.ActionPerformed) viewModel.undo()
                    }
                    CalendarEvent.Changed -> snackbar.showSnackbar(resources.getString(R.string.calendars_event_changed))
                    CalendarEvent.Unreadable -> snackbar.showSnackbar(resources.getString(MochiR.string.common_file_open_failed))
                    is CalendarEvent.Save -> saver.launch(event.name)
                    is CalendarEvent.Exported -> snackbar.showSnackbar(
                        resources.getString(if (event.saved) R.string.calendars_exported else R.string.calendars_export_failed),
                    )
                    is CalendarEvent.Open -> open(event.instance, onEditEvent) { selected = it }
                    is CalendarEvent.Done -> snackbar.showSnackbar(resources.getString(event.message))
                }
            }
        }
    }

    LaunchedEffect(copied) {
        // Said from the screen's own scope: the flag clears at once, and the
        // message outlives this effect.
        if (copied) {
            onCopiedShown()
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.calendars_event_copied)) }
        }
    }

    LaunchedEffect(reminder) {
        val link = reminder ?: return@LaunchedEffect
        onReminderShown()
        viewModel.remind(link.event, link.occurrence, link.date)
    }

    LaunchedEffect(saved) {
        val message = CalendarsApp.said(saved) ?: return@LaunchedEffect
        onSavedShown()
        scope.launch { snackbar.showSnackbar(resources.getString(message)) }
    }

    // A refresh that failed with events on screen keeps them and says so,
    // with Retry; the error takes the view's place only when there are none.
    LaunchedEffect(uiState.stale) {
        val stale = uiState.stale ?: return@LaunchedEffect
        viewModel.told()
        scope.launch {
            val chosen = snackbar.showSnackbar(
                message = stale.userMessage(),
                actionLabel = resources.getString(MochiR.string.common_retry),
                duration = SnackbarDuration.Long,
            )
            if (chosen == SnackbarResult.ActionPerformed) viewModel.reload(refreshing = true)
        }
    }

    LaunchedEffect(deleted) {
        if (deleted) {
            onDeletedShown()
            scope.launch {
                val chosen = snackbar.showSnackbar(
                    message = resources.getString(R.string.calendars_event_deleted),
                    actionLabel = resources.getString(R.string.calendars_undo),
                    duration = SnackbarDuration.Long,
                )
                if (chosen == SnackbarResult.ActionPerformed) viewModel.restore()
            }
        }
    }

    // A drag on a repeating occurrence has to say which occurrences it moved.
    fun request(instance: Instance, run: (Scope) -> Unit) {
        if (instance.recurring) {
            moving = Move(instance, run)
        } else {
            run(Scope.ALL)
        }
    }

    CalendarDrawer(
        drawerState = drawerState,
        calendars = uiState.calendars,
        hidden = uiState.hidden,
        onToggle = viewModel::toggle,
        onAction = { action, calendar ->
            when (action) {
                CalendarAction.ONLY -> viewModel.only(calendar.id)
                CalendarAction.RENAME -> renaming = calendar
                CalendarAction.COLOUR -> colouring = calendar
                CalendarAction.LINK -> linking = calendar
                CalendarAction.POLL -> viewModel.poll(calendar.id)
                CalendarAction.IMPORT -> {
                    importing = calendar.id
                    picker.launch(Icalendar.ACCEPTED)
                }
                CalendarAction.EXPORT -> viewModel.export(calendar)
                CalendarAction.DELETE -> deleting = calendar
            }
        },
        onCreate = onCreateCalendar,
        onSubscribe = onSubscribe,
        onPreferences = { preferences = true },
        onConnectDevice = onConnectDevice,
        onLogout = onLogout,
        onAbout = { about = true },
    ) {
        Scaffold(
            topBar = {
                Toolbar(
                    state = uiState,
                    title = rangeTitle(uiState, viewModel),
                    onMenu = { scope.launch { drawerState.open() } },
                    onToday = viewModel::today,
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                    onDate = viewModel::anchor,
                    onSearch = viewModel::seek,
                    onView = viewModel::view,
                    onWorkweek = { viewModel.workweek(!uiState.workweek) },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                MochiFab(onClick = { onNewEvent(viewModel.creation(), null, null) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.calendars_event_new))
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                val error = uiState.error
                when {
                    uiState.isLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    error != null && uiState.instances.isEmpty() ->
                        ErrorState(error = error, onRetry = { viewModel.reload() })

                    // A pull on the list view reaches further back rather
                    // than starting the range again, which is what the reader
                    // is asking for at the top of an agenda.
                    else -> PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = {
                            if (uiState.view == CalendarsSection.LIST) {
                                viewModel.earlier()
                            } else {
                                viewModel.reload(refreshing = true)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (uiState.truncated) {
                                Text(
                                    text = stringResource(R.string.calendars_truncated),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                            View(
                                uiState,
                                viewModel,
                                selected = selected,
                                onOpen = { instance -> open(instance, onEditEvent) { selected = it } },
                                onNewEvent = onNewEvent,
                                onMove = { instance, moved ->
                                    request(instance) { scope -> viewModel.move(instance, moved, scope) }
                                },
                                onMoveDay = { instance, day ->
                                    request(instance) { scope -> viewModel.move(instance, day, scope) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    moving?.let { move ->
        ScopeDialog(
            title = stringResource(R.string.calendars_scope_move),
            onDismiss = { moving = null },
            onOne = {
                moving = null
                move.run(Scope.ONE)
            },
            onFollowing = {
                moving = null
                move.run(Scope.FOLLOWING)
            },
            onAll = {
                moving = null
                move.run(Scope.ALL)
            },
        )
    }

    // A copy of a repeating stored event asks how far it reaches; one of an
    // event that does not repeat, or of an occurrence with no stored event
    // to load, is a single event and opens at once.
    copying?.let { instance ->
        ScopeDialog(
            title = stringResource(R.string.calendars_scope_copy),
            following = false,
            onDismiss = { copying = null },
            onOne = {
                copying = null
                onCopyEvent(instance.event, instance.occurrence, Scope.ONE)
            },
            onAll = {
                copying = null
                onCopyEvent(instance.event, instance.occurrence, Scope.ALL)
            },
        )
    }

    selected?.let { instance ->
        EventSheet(
            instance = instance,
            calendar = uiState.calendars.firstOrNull { it.id == instance.calendar },
            zones = uiState.preferences.zones,
            onDismiss = { selected = null },
            onCopy = {
                selected = null
                when {
                    !instance.editable -> onCopyOccurrence(instance)
                    instance.recurring -> copying = instance
                    else -> onCopyEvent(instance.event, 0, Scope.ALL)
                }
            },
        )
    }

    // Each dialog stays open while its change is saved, and after a failure,
    // so what was entered can be tried again; it closes once the change lands.
    val working by viewModel.working.collectAsState()
    renaming?.let { calendar ->
        RenameCalendarDialog(
            calendar = calendar,
            saving = working,
            onDismiss = { renaming = null },
            onConfirm = { name -> viewModel.rename(calendar.id, name) { renaming = null } },
        )
    }
    colouring?.let { calendar ->
        ColourCalendarDialog(
            calendar = calendar,
            saving = working,
            onDismiss = { colouring = null },
            onConfirm = { colour -> viewModel.recolour(calendar.id, colour) { colouring = null } },
        )
    }
    deleting?.let { calendar ->
        DeleteCalendarDialog(
            calendar = calendar,
            deleting = working,
            onDismiss = { deleting = null },
            onConfirm = { viewModel.remove(calendar) { deleting = null } },
        )
    }
    linking?.let { calendar ->
        val link by viewModel.link.collectAsState()
        LaunchedEffect(calendar.id) { viewModel.openLink(calendar.id) }
        AddressDialogs(
            calendar = calendar,
            link = link,
            onReplace = { viewModel.openLink(calendar.id, regenerate = true) },
            onClose = {
                linking = null
                viewModel.closeLink()
            },
            onRevoke = {
                linking = null
                viewModel.closeLink()
                revoking = calendar
            },
        )
    }
    revoking?.let { calendar ->
        RevokeLinkDialog(
            busy = working,
            onDismiss = { revoking = null },
            onConfirm = { viewModel.revokeLink(calendar.id) { revoking = null } },
        )
    }
    if (about) {
        AboutDialog(onDismiss = { about = false })
    }

    val tally by viewModel.importing.collectAsState()
    tally?.let { ImportDialog(tally = it, onClose = viewModel::closeImport, onRetry = viewModel::retryImport) }

    if (preferences) {
        PreferencesDialog(
            preferences = uiState.preferences,
            calendars = uiState.calendars,
            saving = working,
            onDismiss = { preferences = false },
            onConfirm = { viewModel.preferences(it) { preferences = false } },
        )
    }
}

/**
 * Opens an occurrence as a tap on it does: the editor for one the user can
 * change, else its summary, [show]n in a sheet, as a subscription's or a
 * birthday is.
 */
internal fun open(instance: Instance, onEditEvent: (String, Long) -> Unit, show: (Instance) -> Unit) {
    if (instance.editable) {
        onEditEvent(instance.event, if (instance.recurring) instance.start else 0)
    } else {
        show(instance)
    }
}

/** A dragged repeating occurrence, waiting for the user to say which occurrences move. */
private class Move(val instance: Instance, val run: (Scope) -> Unit)

/**
 * The view the state names, drawn from the same occurrence list, with the
 * occurrence whose summary is open, [selected], tinted in each. [onMove]
 * is a block dragged or resized in a time grid, with the occurrence's new
 * ends; [onMoveDay] a chip dropped on a day in a month grid, with the
 * occurrence's new first day.
 */
@Composable
private fun View(
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    selected: Instance?,
    onOpen: (Instance) -> Unit,
    onNewEvent: (Long, Boolean?, Long?) -> Unit,
    onMove: (Instance, Moved) -> Unit,
    onMoveDay: (Instance, LocalDate) -> Unit,
) {
    // A tap or a drag on empty grid is a timed event, and says so, which the
    // editor's memory of the last new event does not override.
    val create = { start: Long, finish: Long? -> onNewEvent(start, false, finish) }
    val step = { direction: Int -> if (direction < 0) viewModel.previous() else viewModel.next() }
    when (state.view) {
        CalendarsSection.DAY -> TimeGrid(
            days = listOf(state.anchor),
            state = state,
            viewModel = viewModel,
            onOpen = onOpen,
            onCreate = create,
            onMove = onMove,
            onStep = step,
            selected = selected,
        )
        CalendarsSection.WEEK -> {
            val week = viewModel.week(state.anchor)
            val days = (0 until 7).map { week.plusDays(it.toLong()) }
                .filter { !state.workweek || state.preferences.days.contains(it.dayOfWeek.value % 7) }
            TimeGrid(
                days = days.ifEmpty { listOf(state.anchor) },
                state = state,
                viewModel = viewModel,
                onOpen = onOpen,
                onCreate = create,
                onMove = onMove,
                onStep = step,
                selected = selected,
            )
        }
        CalendarsSection.MULTIWEEK -> MonthGrid(
            weeks = viewModel.weeks(state),
            month = null,
            state = state,
            viewModel = viewModel,
            onOpen = onOpen,
            onCreate = { day -> onNewEvent(viewModel.creation(day), null, null) },
            onMove = onMoveDay,
            onStep = step,
            selected = selected,
        )
        CalendarsSection.MONTH -> MonthGrid(
            weeks = viewModel.weeks(state),
            month = state.anchor.monthValue,
            state = state,
            viewModel = viewModel,
            onOpen = onOpen,
            onCreate = { day -> onNewEvent(viewModel.creation(day), null, null) },
            onMove = onMoveDay,
            onStep = step,
            selected = selected,
        )
        // The list view opens on the anchor day and pages on as the reader
        // scrolls, so it has no range to pick — only something to search.
        else -> Column(modifier = Modifier.fillMaxSize()) {
            val searching = stringResource(R.string.calendars_list_search)
            val focus = remember { FocusRequester() }
            LaunchedEffect(state.seeking) { if (state.seeking > 0) focus.requestFocus() }
            MochiTextField(
                value = state.search,
                onValueChange = viewModel::search,
                placeholder = { Text(stringResource(R.string.calendars_list_search_events)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focus)
                    .semantics { contentDescription = searching },
            )
            AgendaList(state, viewModel, onOpen, selected)
        }
    }
}

/**
 * Previous, today, next, the range's title, search and the view switcher.
 * The title opens a date picker to jump to any day, as the web's opens its
 * mini month; search takes the reader to the list, where results are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Toolbar(
    state: CalendarUiState,
    title: String,
    onMenu: () -> Unit,
    onToday: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDate: (LocalDate) -> Unit,
    onSearch: () -> Unit,
    onView: (String) -> Unit,
    onWorkweek: () -> Unit,
) {
    var views by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(role = Role.Button) { picking = true },
            )
        },
        navigationIcon = {
            MochiIconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.calendars_drawer_open))
            }
        },
        actions = {
            MochiIconButton(onClick = onPrevious) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = stringResource(R.string.calendars_previous),
                )
            }
            MochiIconButton(onClick = onToday) {
                Icon(Icons.Outlined.Today, contentDescription = stringResource(R.string.calendars_today))
            }
            MochiIconButton(onClick = onNext) {
                Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.calendars_next))
            }
            // The list carries its own box; elsewhere this takes the reader there.
            if (state.view != CalendarsSection.LIST) {
                MochiIconButton(onClick = onSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.calendars_list_search_events))
                }
            }
            Box {
                MochiIconButton(onClick = { views = true }) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.calendars_view))
                }
                MochiDropdownMenu(expanded = views, onDismissRequest = { views = false }) {
                    for ((token, label, icon) in VIEWS) {
                        MochiDropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            leadingIcon = { Icon(icon(), contentDescription = null) },
                            selected = state.view == token,
                            onClick = {
                                views = false
                                onView(token)
                            },
                        )
                    }
                    if (state.view == CalendarsSection.WEEK) {
                        MochiDropdownMenuItem(
                            text = { Text(stringResource(R.string.calendars_workweek)) },
                            selected = state.workweek,
                            onClick = {
                                views = false
                                onWorkweek()
                            },
                        )
                    }
                }
            }
        },
    )
    if (picking) {
        DateDialog(
            day = state.anchor,
            onDismiss = { picking = false },
            onPick = {
                picking = false
                onDate(it)
            },
        )
    }
}

/** The switcher's entries: the token the state holds, its label and its glyph. */
private val VIEWS = listOf(
    Triple(CalendarsSection.DAY, R.string.calendars_view_day, { Icons.Outlined.CalendarToday }),
    Triple(CalendarsSection.WEEK, R.string.calendars_view_week, { Icons.Outlined.CalendarViewWeek }),
    Triple(CalendarsSection.MULTIWEEK, R.string.calendars_view_multiweek, { Icons.Outlined.CalendarViewMonth }),
    Triple(CalendarsSection.MONTH, R.string.calendars_view_month, { Icons.Outlined.CalendarMonth }),
    Triple(CalendarsSection.LIST, R.string.calendars_view_list, { Icons.AutoMirrored.Filled.List }),
)

/** The range the view covers, in words. */
@Composable
private fun rangeTitle(state: CalendarUiState, viewModel: CalendarViewModel): String {
    val format = LocalFormat.current
    val (start, finish) = viewModel.range(state)
    val zone = viewModel.timezone()
    return title(
        state.view,
        state.anchor,
        Instant.ofEpochSecond(start).atZone(zone).toLocalDate(),
        Instant.ofEpochSecond(finish).atZone(zone).toLocalDate().minusDays(1),
        format,
    )
}

/**
 * The toolbar's title for a view, as the web's: a day's long date, a month
 * and its year for the month and the list, which pages on from its month,
 * and the span of days otherwise, each as the user's language writes it.
 */
internal fun title(view: String, anchor: LocalDate, first: LocalDate, last: LocalDate, format: Format): String =
    when (view) {
        CalendarsSection.DAY -> format.formatLongDate(anchor.atTime(12, 0).toEpochSecond(ZoneOffset.UTC), "UTC")
        CalendarsSection.MONTH, CalendarsSection.LIST -> format.formatMonthYear(anchor)
        else -> format.formatDayRange(first, last)
    }

/** Reloads the range whenever the screen comes back to the foreground. */
@Composable
private fun DisposableRefresh(owner: androidx.lifecycle.LifecycleOwner, onResume: () -> Unit) {
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/**
 * A calendar's address: the address dialog, and in its place, once Replace
 * is tapped, the question whether to break the address everyone subscribed
 * has. The question stays while the new address is minted and after a
 * failure, so it can be tried again; the new address shows once it is out.
 */
@Composable
internal fun AddressDialogs(
    calendar: Calendar,
    link: LinkState,
    onReplace: () -> Unit,
    onClose: () -> Unit,
    onRevoke: () -> Unit,
) {
    var replacing by remember(calendar.id) { mutableStateOf(false) }
    LaunchedEffect(link.url) { if (link.url != null) replacing = false }
    if (replacing) {
        ReplaceLinkDialog(
            busy = link.busy,
            onDismiss = { replacing = false },
            onConfirm = onReplace,
        )
    } else {
        LinkDialog(
            calendar = calendar,
            url = link.url,
            exists = link.exists,
            busy = link.busy,
            onDismiss = onClose,
            onReplace = { replacing = true },
            onRevoke = onRevoke,
        )
    }
}
