// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
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
import org.mochios.calendars.ui.components.CalendarAction
import org.mochios.calendars.ui.components.CalendarDrawer
import org.mochios.calendars.ui.dialogs.ColourCalendarDialog
import org.mochios.calendars.ui.dialogs.DeleteCalendarDialog
import org.mochios.calendars.ui.dialogs.LinkDialog
import org.mochios.calendars.ui.dialogs.PreferencesDialog
import org.mochios.calendars.ui.dialogs.RenameCalendarDialog
import org.mochios.calendars.ui.dialogs.ScopeDialog
import org.mochios.calendars.ui.editor.Scope
import org.mochios.calendars.ui.router.CalendarsSection
import java.time.LocalDate

/**
 * The calendars app's one screen: the drawer of calendars, the toolbar that
 * moves the range, the view itself and the "New event" button. Every view
 * renders from the same occurrence list, so switching between them is a
 * redraw and not a fetch of a different shape. [onCopyEvent] opens the
 * editor on a copy of a stored event's occurrence, with how far the copy
 * reaches; [onCopyOccurrence] on a copy of one the editor cannot load, a
 * subscription's or a birthday. [copied] says a copy was just saved, which
 * the screen reports once and [onCopiedShown] clears.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onCreateCalendar: () -> Unit,
    onSubscribe: () -> Unit,
    onConnectDevice: () -> Unit,
    onNewEvent: (Long, Boolean?) -> Unit,
    onEditEvent: (String, Long) -> Unit,
    onCopyEvent: (String, Long, Scope) -> Unit,
    onCopyOccurrence: (Instance) -> Unit,
    copied: Boolean = false,
    onCopiedShown: () -> Unit = {},
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
    var preferences by remember { mutableStateOf(false) }

    DisposableRefresh(lifecycle) { viewModel.load(refreshing = true, reset = false) }

    LaunchedEffect(Unit) {
        // Each message on its own, so a move's "Undo" waiting to be tapped
        // does not hold up the next.
        viewModel.events.collect { event ->
            scope.launch {
                when (event) {
                    is CalendarEvent.Failed -> snackbar.showSnackbar(event.error.userMessage())
                    is CalendarEvent.Polled -> snackbar.showSnackbar(
                        resources.getQuantityString(
                            R.plurals.calendars_polled,
                            event.changed,
                            event.changed,
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
                CalendarAction.DELETE -> deleting = calendar
            }
        },
        onCreate = onCreateCalendar,
        onSubscribe = onSubscribe,
        onPreferences = { preferences = true },
        onConnectDevice = onConnectDevice,
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
                    onView = viewModel::view,
                    onWorkweek = { viewModel.workweek(!uiState.workweek) },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                MochiFab(onClick = { onNewEvent(0, null) }) {
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
                                onOpen = { instance ->
                                    if (instance.editable) {
                                        onEditEvent(instance.event, if (instance.recurring) instance.start else 0)
                                    } else {
                                        selected = instance
                                    }
                                },
                                onNewEvent = onNewEvent,
                                onMove = { instance, start, finish ->
                                    request(instance) { scope -> viewModel.move(instance, start, finish, scope) }
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
            deleting = false,
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
            deleting = false,
            copying = true,
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

    renaming?.let { calendar ->
        RenameCalendarDialog(
            calendar = calendar,
            saving = false,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                viewModel.rename(calendar.id, name)
            },
        )
    }
    colouring?.let { calendar ->
        ColourCalendarDialog(
            calendar = calendar,
            saving = false,
            onDismiss = { colouring = null },
            onConfirm = { colour ->
                colouring = null
                viewModel.recolour(calendar.id, colour)
            },
        )
    }
    deleting?.let { calendar ->
        DeleteCalendarDialog(
            calendar = calendar,
            deleting = false,
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                viewModel.remove(calendar.id)
            },
        )
    }
    linking?.let { calendar ->
        val link by viewModel.link.collectAsState()
        LaunchedEffect(calendar.id) { viewModel.openLink(calendar.id) }
        LinkDialog(
            calendar = calendar,
            url = link.url,
            exists = link.exists,
            busy = link.busy,
            onDismiss = {
                linking = null
                viewModel.closeLink()
            },
            onReplace = { viewModel.openLink(calendar.id, regenerate = true) },
            onRevoke = {
                linking = null
                viewModel.revokeLink(calendar.id)
            },
        )
    }
    if (preferences) {
        PreferencesDialog(
            preferences = uiState.preferences,
            saving = false,
            onDismiss = { preferences = false },
            onConfirm = {
                preferences = false
                viewModel.preferences(it)
            },
        )
    }
}

/** A dragged repeating occurrence, waiting for the user to say which occurrences move. */
private class Move(val instance: Instance, val run: (Scope) -> Unit)

/**
 * The view the state names, drawn from the same occurrence list. [onMove]
 * is a block dragged or resized in a time grid, with the occurrence's new
 * ends; [onMoveDay] a chip dropped on a day in a month grid, with the
 * occurrence's new first day.
 */
@Composable
private fun View(
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    onOpen: (Instance) -> Unit,
    onNewEvent: (Long, Boolean?) -> Unit,
    onMove: (Instance, Long, Long) -> Unit,
    onMoveDay: (Instance, LocalDate) -> Unit,
) {
    // A tap on a cell names a day and, in a time grid, an hour; the editor
    // wants the moment, measured in the user's own zone rather than the
    // device's. A cell is a timed event, and says so, which the editor's
    // memory of the last new event does not override.
    val moment = { day: LocalDate, hour: Int ->
        day.atStartOfDay(viewModel.timezone()).plusHours(hour.toLong()).toEpochSecond()
    }
    when (state.view) {
        CalendarsSection.DAY -> TimeGrid(
            days = listOf(state.anchor),
            state = state,
            viewModel = viewModel,
            onOpen = onOpen,
            onCreate = { day, hour -> onNewEvent(moment(day, hour), false) },
            onMove = onMove,
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
                onCreate = { day, hour -> onNewEvent(moment(day, hour), false) },
                onMove = onMove,
            )
        }
        CalendarsSection.MULTIWEEK -> MonthGrid(
            weeks = viewModel.weeks(state),
            month = null,
            state = state,
            viewModel = viewModel,
            onOpen = onOpen,
            onCreate = { day -> onNewEvent(moment(day, 9), null) },
            onMove = onMoveDay,
        )
        CalendarsSection.MONTH -> MonthGrid(
            weeks = viewModel.weeks(state),
            month = state.anchor.monthValue,
            state = state,
            viewModel = viewModel,
            onOpen = onOpen,
            onCreate = { day -> onNewEvent(moment(day, 9), null) },
            onMove = onMoveDay,
        )
        // The list view opens on the anchor day and pages on as the reader
        // scrolls, so it has no range to pick — only something to search.
        else -> Column(modifier = Modifier.fillMaxSize()) {
            MochiTextField(
                value = state.search,
                onValueChange = viewModel::search,
                label = { Text(stringResource(R.string.calendars_list_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AgendaList(state, viewModel, onOpen)
        }
    }
}

/** Previous, today, next, the range's title, and the view switcher. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Toolbar(
    state: CalendarUiState,
    title: String,
    onMenu: () -> Unit,
    onToday: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onView: (String) -> Unit,
    onWorkweek: () -> Unit,
) {
    var views by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    return when (state.view) {
        CalendarsSection.DAY -> format.formatDate(start)
        CalendarsSection.MONTH -> state.anchor.month
            .getDisplayName(java.time.format.TextStyle.FULL, LocalConfiguration.current.locales[0]) +
            " " + state.anchor.year
        else -> stringResource(
            R.string.calendars_range,
            format.formatDate(start),
            format.formatDate(finish - 86_400),
        )
    }
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
