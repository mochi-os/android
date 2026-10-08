// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.mochios.android.R as MochiR
import org.mochios.android.api.userMessage
import org.mochios.android.files.rememberFileSaveLauncher
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiFab
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiSearchTopBar
import org.mochios.android.util.Zones
import org.mochios.calendars.R
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.model.Instance
import org.mochios.calendars.navigation.CalendarsApp
import org.mochios.calendars.navigation.Reminder
import org.mochios.calendars.ui.components.CalendarAction
import org.mochios.calendars.ui.components.CalendarDrawer
import org.mochios.calendars.ui.dialogs.ColourCalendarDialog
import org.mochios.calendars.ui.dialogs.DeleteCalendarDialog
import org.mochios.calendars.ui.dialogs.ImportDialog
import org.mochios.calendars.ui.dialogs.LinkDialog
import org.mochios.calendars.ui.dialogs.RenameCalendarDialog
import org.mochios.calendars.ui.dialogs.ReplaceLinkDialog
import org.mochios.calendars.ui.dialogs.ScopeDialog
import org.mochios.calendars.ui.editor.Scope
import org.mochios.calendars.ui.editor.picked
import org.mochios.calendars.ui.router.CalendarsSection

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
    onPreferences: () -> Unit,
    onCalendarSettings: (String) -> Unit,
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
    var snackbarHeight by remember { mutableIntStateOf(0) }
    val fabLift by animateDpAsState(
        targetValue = with(LocalDensity.current) { snackbarHeight.toDp() },
        label = "fabLift",
    )
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    val resources = LocalResources.current

    var selected by remember { mutableStateOf<Instance?>(null) }
    var picking by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var listed by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(uiState.view) {
        if (uiState.view != CalendarsSection.LIST && searching) {
            searching = false
            viewModel.search("")
        }
    }
    var copying by remember { mutableStateOf<Instance?>(null) }
    var moving by remember { mutableStateOf<Move?>(null) }
    var renaming by remember { mutableStateOf<Calendar?>(null) }
    var colouring by remember { mutableStateOf<Calendar?>(null) }
    var deleting by remember { mutableStateOf<Calendar?>(null) }
    var linking by remember { mutableStateOf<Calendar?>(null) }
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
        viewModel.load(reset = false)
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
                    is CalendarEvent.Open -> open(event.instance, onEditEvent) { instance ->
                        selected = instance
                        viewModel.details(instance)
                    }
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
        if (saved == CalendarsApp.PREFERENCES) {
            viewModel.reload()
        }
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
                CalendarAction.SETTINGS -> onCalendarSettings(calendar.id)
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
        onPreferences = onPreferences,
        onConnectDevice = onConnectDevice,
        onLogout = onLogout,
        onAbout = { about = true },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    Toolbar(
                        state = uiState,
                        title = monthTitle(
                            when {
                                picking -> uiState.focus
                                uiState.view == CalendarsSection.LIST -> listed ?: uiState.anchor
                                else -> viewModel.first(uiState)
                            },
                        ),
                        picking = picking,
                        onMenu = { scope.launch { drawerState.open() } },
                        onTitle = { picking = !picking },
                        onToday = viewModel::today,
                        onView = viewModel::view,
                        onWorkweek = { viewModel.workweek(!uiState.workweek) },
                        searching = searching,
                        onSearch = {
                            // Results are listed, so a search from another view
                            // goes to the list, as the web's box does.
                            if (uiState.view != CalendarsSection.LIST) viewModel.seek()
                            searching = true
                        },
                        onSearchChange = viewModel::search,
                        onSearchClose = {
                            searching = false
                            viewModel.search("")
                        },
                    )
                },
                floatingActionButton = {
                    MochiFab(
                        onClick = { onNewEvent(viewModel.creation(), null, null) },
                        modifier = Modifier.offset(y = -fabLift),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.calendars_event_new))
                    }
                },
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    AnimatedVisibility(
                        visible = picking,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        val days = uiState.view != CalendarsSection.MONTH
                        key(days) {
                            DatePanel(
                                focus = uiState.focus,
                                today = LocalDate.now(viewModel.timezone()),
                                weekStart = viewModel.start(),
                                onPick = viewModel::anchor,
                                days = days,
                            )
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
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
                                        onOpen = { instance ->
                                            open(instance, onEditEvent) { shown ->
                                                selected = shown
                                                viewModel.details(shown)
                                            }
                                        },
                                        onNewEvent = onNewEvent,
                                        onMove = { instance, moved ->
                                            request(instance) { scope -> viewModel.move(instance, moved, scope) }
                                        },
                                        onMoveDay = { instance, day ->
                                            request(instance) { scope -> viewModel.move(instance, day, scope) }
                                        },
                                        onListed = { day -> listed = day },
                                    )
                                }
                            }
                        }
                        if (picking) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            awaitFirstDown().consume()
                                            picking = false
                                        }
                                    },
                            )
                        }
                    }
                }
            }
            SnackbarHost(
                snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .onSizeChanged { size -> snackbarHeight = size.height },
            )
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
            details = uiState.details?.takeIf { details ->
                details.event == instance.event && details.occurrence == instance.occurrence
            },
            onDismiss = { selected = null },
            onCopy = {
                selected = null
                when {
                    !instance.editable -> onCopyOccurrence(instance)
                    instance.recurring -> copying = instance
                    else -> onCopyEvent(instance.event, 0, Scope.ALL)
                }
            },
            onEdit = if (instance.editable) {
                {
                    selected = null
                    onEditEvent(instance.event, if (instance.recurring) instance.start else 0)
                }
            } else {
                null
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
        )
    }
    if (about) {
        AboutDialog(onDismiss = { about = false })
    }

    val tally by viewModel.importing.collectAsState()
    tally?.let { ImportDialog(tally = it, onClose = viewModel::closeImport, onRetry = viewModel::retryImport) }

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
 * The view the state names. The day, week, multiweek and month views sit in
 * a pager, so a swipe brings the period either side in under the finger, as
 * Google Calendar does, and settling on it moves the anchor there. The today
 * button slides the pager the same way. The list view scrolls on its own and
 * is drawn as it is, telling [onListed] the day atop it as it scrolls. The
 * occurrence whose summary is open, [selected], is tinted in each. The
 * pager's page is not saved across a trip to the editor: its pages count
 * from the anchor it opened with, and a restored page counted from a newer
 * anchor would move the view.
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
    onListed: (LocalDate) -> Unit = {},
) {
    val scroll = rememberHourScroll(state.preferences.hours.start)
    if (state.view == CalendarsSection.LIST) {
        Page(
            state,
            viewModel,
            selected,
            scroll,
            onOpen,
            onNewEvent,
            onMove,
            onMoveDay,
            onListed = onListed,
        )
        return
    }
    key(state.view) {
        val base = remember { state.anchor }
        val pager = remember { PagerState(currentPage = SWIPE_CENTRE) { SWIPE_PAGES } }
        val target = SWIPE_CENTRE + steps(state.view, base, state.anchor, viewModel::week)
        val anchor by rememberUpdatedState(state.anchor)
        // A drag resting at a grid's side turns the range under it: the page
        // holding the drag stays on screen and shows the new range, so the
        // gesture carries on, and the pager catches up without a slide once
        // the drag is let go.
        var dragging by remember { mutableStateOf(false) }
        var held by remember { mutableStateOf<Int?>(null) }
        val turn = { direction: Int ->
            if (held == null) {
                held = pager.currentPage
            }
            if (direction < 0) {
                viewModel.previous()
            } else {
                viewModel.next()
            }
        }

        LaunchedEffect(target, dragging) {
            if (held != null) {
                if (!dragging) {
                    pager.scrollToPage(target)
                    held = null
                }
                return@LaunchedEffect
            }
            if (pager.currentPage != target) {
                if (abs(pager.currentPage - target) == 1) {
                    pager.animateScrollToPage(target)
                } else {
                    pager.scrollToPage(target)
                }
            }
        }
        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.collect { page ->
                if (SWIPE_CENTRE + steps(state.view, base, anchor, viewModel::week) != page) {
                    viewModel.anchor(step(state.view, base, page - SWIPE_CENTRE))
                }
            }
        }

        val timed = state.view == CalendarsSection.DAY || state.view == CalendarsSection.WEEK
        var top by remember { mutableStateOf(0.dp) }
        val seamed = state.view == CalendarsSection.MONTH || state.view == CalendarsSection.MULTIWEEK
        val seam = MaterialTheme.colorScheme.outlineVariant
        val previous = stringResource(R.string.calendars_previous)
        val next = stringResource(R.string.calendars_next)
        Row(modifier = Modifier.fillMaxSize()) {
            if (timed) {
                HourGutter(top, scroll, zone = if (viewModel.zones()) Zones.offset(viewModel.timezone().id) else null)
            }
            // With no arrows on screen, TalkBack offers Previous and Next as
            // actions on the pager and a keyboard pages it with the arrow and
            // page keys, each moving the view one period as a swipe does.
            HorizontalPager(
                state = pager,
                pageSpacing = if (seamed) SEAM else 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(previous) {
                                viewModel.previous()
                                true
                            },
                            CustomAccessibilityAction(next) {
                                viewModel.next()
                                true
                            },
                        )
                    }
                    .onPreviewKeyEvent { event ->
                        when {
                            event.type != KeyEventType.KeyDown -> false
                            event.key == Key.DirectionLeft || event.key == Key.PageUp -> {
                                viewModel.previous()
                                true
                            }
                            event.key == Key.DirectionRight || event.key == Key.PageDown -> {
                                viewModel.next()
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable(),
            ) { page ->
                val shown = if (page == target || page == held) {
                    state
                } else {
                    state.copy(anchor = step(state.view, base, page - SWIPE_CENTRE))
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (seamed) {
                                Modifier.drawBehind {
                                    drawRect(seam, Offset(-SEAM.toPx(), 0f), Size(SEAM.toPx(), size.height))
                                }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Page(
                        shown,
                        viewModel,
                        selected,
                        scroll,
                        onOpen,
                        onNewEvent,
                        onMove,
                        onMoveDay,
                        onStep = turn,
                        onLifted = { lifted -> dragging = lifted },
                    ) { offset ->
                        if (page == pager.currentPage) {
                            top = offset
                        }
                    }
                }
            }
        }
    }
}

/**
 * The band between two pages of the month and multiweek views' pager, off
 * screen while a page is settled and shown while a swipe carries one in
 * under the other.
 */
private val SEAM = 4.dp

/** How many pages the pager holds; it opens in the middle, so either way is endless. */
private const val SWIPE_PAGES = Int.MAX_VALUE

/** The page the pager opens on, which shows the anchor it opened with. */
private const val SWIPE_CENTRE = SWIPE_PAGES / 2

/**
 * One page of the view the state names, drawn from the same occurrence list,
 * with [selected] tinted. [scroll] is the time grid's vertical position. [onMove] is an
 * occurrence dragged in a time grid, with where it went; [onMoveDay] a
 * chip dropped on a day in a month grid, with the occurrence's new first day.
 * [onStep] turns the range while a drag rests at a grid's side, and
 * [onLifted] hears when a drag starts and ends.
 * [onTop] is how far down a time grid's hours start, for the hour gutter;
 * [onListed] is the day atop the list view as it scrolls, for the toolbar.
 */
@Composable
internal fun Page(
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    selected: Instance?,
    scroll: ScrollState,
    onOpen: (Instance) -> Unit,
    onNewEvent: (Long, Boolean?, Long?) -> Unit,
    onMove: (Instance, Moved) -> Unit,
    onMoveDay: (Instance, LocalDate) -> Unit,
    onStep: (Int) -> Unit = {},
    onLifted: (Boolean) -> Unit = {},
    onListed: (LocalDate) -> Unit = {},
    onTop: (Dp) -> Unit = {},
) {
    // A tap or a drag on empty grid is a timed event, and says so, which the
    // editor's memory of the last new event does not override. Either makes
    // its day the chosen one, as a tap on a day cell does.
    val create = { start: Long, finish: Long? ->
        viewModel.focus(Instant.ofEpochSecond(start).atZone(viewModel.timezone()).toLocalDate())
        onNewEvent(start, false, finish)
    }
    val dated = { day: LocalDate ->
        viewModel.focus(day)
        onNewEvent(viewModel.creation(day), null, null)
    }
    // A run of days picked across the month grid or along the all-day band
    // is an all-day event over them, with the times it opens at beneath.
    val ranged = { first: LocalDate, last: LocalDate ->
        viewModel.focus(first)
        val (start, finish) = picked(first, last, state.preferences.hours, state.preferences.duration, viewModel.timezone())
        onNewEvent(start, true, finish)
    }
    when (state.view) {
        CalendarsSection.DAY -> TimeGrid(
            days = listOf(state.anchor),
            state = state,
            viewModel = viewModel,
            onOpen = onOpen,
            onCreate = create,
            onCreateRange = ranged,
            onMove = onMove,
            scroll = scroll,
            onStep = onStep,
            onLifted = onLifted,
            selected = selected,
            onTop = onTop,
        )
        CalendarsSection.WEEK -> {
            TimeGrid(
                days = viewModel.days(state),
                state = state,
                viewModel = viewModel,
                onOpen = onOpen,
                onCreate = create,
                onCreateRange = ranged,
                onMove = onMove,
                scroll = scroll,
                stacked = true,
                onStep = onStep,
                onLifted = onLifted,
                selected = selected,
                onTop = onTop,
            )
        }
        CalendarsSection.MULTIWEEK -> MonthGrid(
            weeks = viewModel.weeks(state),
            month = null,
            state = state,
            viewModel = viewModel,
            onOpen = onOpen,
            onCreate = dated,
            onCreateRange = ranged,
            onMove = onMoveDay,
            onStep = onStep,
            onLifted = onLifted,
            selected = selected,
        )
        CalendarsSection.MONTH -> MonthGrid(
            weeks = viewModel.weeks(state),
            month = state.anchor.monthValue,
            state = state,
            viewModel = viewModel,
            onOpen = onOpen,
            onCreate = dated,
            onCreateRange = ranged,
            onMove = onMoveDay,
            onStep = onStep,
            onLifted = onLifted,
            selected = selected,
        )
        // The list view opens on the anchor day and pages on as the reader
        // scrolls, so it has no range to pick; its search is in the toolbar.
        else -> AgendaList(state, viewModel, onOpen, selected, onTop = onListed, onCreate = dated)
    }
}

/**
 * The title, which opens and closes the date panel under it, today, and the
 * view switcher, with a search button before them in every view, which
 * searches in the list view as the web's box does. While
 * [searching], the bar is a search field instead, with back to close it,
 * which also clears it, and a button to clear what was typed. The title is
 * the month and year of the view's first day, or
 * of the picked day while the panel is open. There are no previous and next arrows:
 * the views page with a swipe, and the panel jumps anywhere further.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Toolbar(
    state: CalendarUiState,
    title: String,
    picking: Boolean,
    onMenu: () -> Unit,
    onTitle: () -> Unit,
    onToday: () -> Unit,
    onView: (String) -> Unit,
    onWorkweek: () -> Unit,
    searching: Boolean = false,
    onSearch: () -> Unit = {},
    onSearchChange: (String) -> Unit = {},
    onSearchClose: () -> Unit = {},
) {
    var views by remember { mutableStateOf(false) }
    if (searching) {
        BackHandler(onBack = onSearchClose)
        MochiSearchTopBar(
            query = state.search,
            placeholder = stringResource(R.string.calendars_list_search_events),
            onQueryChange = onSearchChange,
            onClose = onSearchClose,
        )
        return
    }
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onTitle)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(
                    if (picking) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                )
            }
        },
        navigationIcon = {
            MochiIconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.calendars_drawer_open))
            }
        },
        actions = {
            MochiIconButton(onClick = onSearch) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.calendars_list_search),
                )
            }
            MochiIconButton(onClick = onToday) {
                Icon(Icons.Outlined.Today, contentDescription = stringResource(R.string.calendars_today))
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

/** The toolbar's title: [date]'s month and year, in the locale's own short form. */
@Composable
private fun monthTitle(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = remember(locale) {
        android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMyyyy")
    }
    return java.time.format.DateTimeFormatter.ofPattern(pattern, locale).format(date)
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
        )
    }
}
