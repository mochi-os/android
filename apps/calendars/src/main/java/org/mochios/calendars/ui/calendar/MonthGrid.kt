// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.mochios.android.i18n.LocalFormat
import org.mochios.calendars.R
import org.mochios.calendars.model.Instance
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** The space between a cell's entries. */
private val STEP = 2.dp

/** The space between an agenda row's dot, title and time. */
private val SPACE = 10.dp

/**
 * A chip lifted by a long press: which occurrence, the day of the cell it
 * was lifted from, where in the chip the finger took it, and the chip's
 * width in pixels, which the chip carried under the finger keeps.
 */
private data class Hold(val instance: Instance, val day: LocalDate, val grab: Offset, val width: Float)

/**
 * The month and multiweek views: [weeks] rows of seven days, each cell
 * holding every one of its occurrences from its own top: one-line bars for
 * the all-day and multi-day ones, and two lines on the cell for each of the
 * rest, the title above the time and marks. The bars come first unless the
 * user put all-day events last; each group scrolls within the cell when it
 * holds more than the cell has room for. In the month view days outside
 * [month] are dimmed but drawn; in the multiweek view [month] is null and
 * every day reads the same.
 *
 * A tap on a chip opens its summary, a tap on empty cell space starts an
 * event on that day, and the day number opens the day view. A long press
 * lifts a chip, which then follows the finger from cell to cell; letting go
 * on another day asks [onMove] to move the occurrence so that its first day
 * moves by as many days, and that day's cell scrolls to show it once it
 * lands there. The occurrence whose summary is open, [selected], is drawn in
 * the primary colour's tint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthGrid(
    weeks: List<LocalDate>,
    month: Int?,
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    onOpen: (Instance) -> Unit,
    onCreate: (LocalDate) -> Unit,
    onMove: (Instance, LocalDate) -> Unit,
    selected: Instance? = null,
) {
    val today = LocalDate.now(viewModel.timezone())
    val format = LocalFormat.current
    val byDay = remember(state.instances, state.hidden, weeks, state.preferences.zones) {
        weeks.flatMap { week -> (0 until 7).map { week.plusDays(it.toLong()) } }
            .associateWith { day -> state.visible.filter { viewModel.covers(it, day) } }
    }

    // The lifted chip, the finger and every cell's bounds, all in the root's
    // coordinates; the grid's own origin turns the finger into where the
    // carried chip is drawn.
    var lift by remember { mutableStateOf<Hold?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val cells = remember { mutableStateMapOf<LocalDate, Rect>() }
    // The day a chip was last dropped on and its event, which that day's
    // cell scrolls into view when the moved occurrence arrives there.
    var landed by remember(weeks) { mutableStateOf<Pair<LocalDate, String>?>(null) }
    fun under(): LocalDate? = cells.entries.firstOrNull { it.value.contains(finger) }?.key
    val target = if (lift != null) under() else null
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                for (offset in 0 until 7) {
                    Text(
                        text = weekdayLabel(weeks.first().plusDays(offset.toLong())),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            HorizontalDivider()
            for (week in weeks) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (offset in 0 until 7) {
                        val day = week.plusDays(offset.toLong())
                        val occurrences = byDay[day].orEmpty()
                        Cell(
                            day = day,
                            today = today,
                            allday = state.preferences.allday,
                            outside = month != null && day.monthValue != month,
                            instances = occurrences,
                            viewModel = viewModel,
                            lifted = lift?.instance,
                            selected = selected,
                            targeted = target == day && lift?.day != day,
                            modifier = Modifier.weight(1f),
                            onDay = { viewModel.open(day) },
                            onCreate = { onCreate(day) },
                            onOpen = onOpen,
                            landing = landed?.takeIf { it.first == day }?.second,
                            onPlaced = { cells[day] = it },
                            onLift = { instance, grab, width ->
                                landed = null
                                lift = Hold(instance, day, grab, width)
                            },
                            onDrag = { finger = it },
                            onDrop = {
                                val lifted = lift
                                lift = null
                                val dropped = under()
                                if (lifted != null && dropped != null && dropped != lifted.day) {
                                    landed = dropped to lifted.instance.event
                                    val shift = ChronoUnit.DAYS.between(lifted.day, dropped)
                                    onMove(lifted.instance, viewModel.day(lifted.instance).plusDays(shift))
                                }
                            },
                            onCancel = { lift = null },
                        )
                    }
                }
                HorizontalDivider()
            }
        }
        // The carried chip always sits on the neutral surface, which its
        // shadow needs to read as lifted, even when it was lifted from an
        // entry drawn on the cell itself; it keeps that entry's two lines.
        lift?.let { lifted ->
            val look = look(lifted.instance, viewModel.day(lifted.instance), viewModel.finish(lifted.instance), lifted.day)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (finger.x - origin.x - lifted.grab.x).roundToInt(),
                            (finger.y - origin.y - lifted.grab.y).roundToInt(),
                        )
                    }
                    .width(with(density) { lifted.width.toDp() })
                    .zIndex(1f),
            ) {
                Chip(
                    lifted.instance,
                    Modifier.fillMaxWidth().shadow(6.dp, corners()),
                    stacked = !look.bar,
                    raised = true,
                    time = if (look.time) {
                        format.formatTime(lifted.instance.start, clockZone(lifted.instance.zone?.start, viewModel.zones()))
                    } else {
                        null
                    },
                ) {}
            }
        }
    }
}

@Composable
private fun Cell(
    day: LocalDate,
    today: LocalDate,
    allday: String,
    outside: Boolean,
    instances: List<Instance>,
    viewModel: CalendarViewModel,
    lifted: Instance?,
    selected: Instance?,
    targeted: Boolean,
    modifier: Modifier,
    onDay: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (Instance) -> Unit,
    landing: String?,
    onPlaced: (Rect) -> Unit,
    onLift: (Instance, Offset, Float) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
) {
    val current = day == today
    val format = LocalFormat.current
    val zones = viewModel.zones()
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    Column(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                val at = it.positionInRoot()
                onPlaced(Rect(at.x, at.y, at.x + it.size.width, at.y + it.size.height))
            }
            .then(if (targeted) Modifier.background(tint) else Modifier)
            .clickable(onClick = onCreate),
    ) {
        // Today's number sits inside a band in the primary colour across the
        // top of its cell; every other day's number sits on the cell itself.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (current) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
                .padding(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDay)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        current -> MaterialTheme.colorScheme.onPrimary
                        outside -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
        // The bars and the timed entries stack from the top of the cell in the
        // order the user chose, each group scrolling on its own. The upper one
        // is held so that one entry of the lower stays in view.
        val entries = instances.map { it to look(it, viewModel.day(it), viewModel.finish(it), day) }
        val line = leading(MaterialTheme.typography.labelSmall)
        val pair = leading(MaterialTheme.typography.labelSmall.packed()) * 2
        val groups = stack(
            Group(entries.filter { it.second.bar }, line),
            Group(entries.filterNot { it.second.bar }, pair),
            allday,
        ).filter { it.entries.isNotEmpty() }

        @Composable
        fun Entry(instance: Instance, look: Look) {
            val requester = remember { BringIntoViewRequester() }
            if (landing == instance.event) {
                LaunchedEffect(instance.event, instance.start) {
                    // Once the cell has laid the entry out, so there is somewhere to scroll to.
                    withFrameNanos { }
                    requester.bringIntoView()
                }
            }
            Chip(
                instance,
                Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(requester)
                    .alpha(opacity(carried = same(instance, lifted), over = viewModel.past(instance), cancelled = instance.cancelled)),
                stacked = !look.bar,
                time = if (look.time) format.formatTime(instance.start, clockZone(instance.zone?.start, zones)) else null,
                chosen = same(instance, selected),
                lift = Modifier.lift(instance, onLift, onDrag, onDrop, onCancel),
            ) { onOpen(instance) }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, top = 1.dp, bottom = 2.dp),
        ) {
            val upper = groups.getOrNull(0)
            val lower = groups.getOrNull(1)
            val held = upper?.let {
                band(it.entries.size, it.height.value, STEP.value, maxHeight.value, lower?.height?.value ?: 0f, below = lower != null)
            }
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(STEP)) {
                for ((index, group) in groups.withIndex()) {
                    val last = index == groups.size - 1
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                when {
                                    last -> Modifier.weight(1f)
                                    held != null -> Modifier.height(held.dp)
                                    else -> Modifier
                                },
                            )
                            .nestedScroll(Contained)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(STEP),
                    ) {
                        for ((instance, look) in group.entries) {
                            key(instance.event, instance.start) { Entry(instance, look) }
                        }
                    }
                }
            }
        }
    }
}

/** One of a cell's two groups: its entries, and how tall each one is. */
private data class Group(val entries: List<Pair<Instance, Look>>, val height: Dp)

/**
 * A cell's [bars] and its timed [lines] in the order they stack from its
 * top: the bars first, unless the user put all-day events "last".
 */
fun <T> stack(bars: T, lines: T, allday: String): List<T> =
    if (allday == "last") listOf(lines, bars) else listOf(bars, lines)

/**
 * Keeps a cell's scrolling to the cell: whatever its list cannot take, at
 * either end, goes no further, so a swipe in a cell never reaches the pull
 * that reloads the other views, which the month grid does not have.
 */
private object Contained : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/**
 * How an occurrence is drawn in a month or multiweek cell: as a one-line
 * [bar] held at the top of the cell, or as a two-line entry in the list that
 * scrolls beneath, and whether it says its start [time].
 */
data class Look(val bar: Boolean, val time: Boolean)

/**
 * How an occurrence running from its [first] day to its [last] is drawn in
 * the cell for [day]. An all-day one is a bar with no time. A timed one that
 * crosses midnight is a bar too, with its start time on its first day only.
 * A timed one within a day is a line with its start time.
 */
fun look(instance: Instance, first: LocalDate, last: LocalDate, day: LocalDate): Look = when {
    instance.allday -> Look(bar = true, time = false)
    first != last -> Look(bar = true, time = day == first)
    else -> Look(bar = false, time = true)
}

/**
 * The height a cell's upper group is held to, in the same unit as [room], or
 * null when it needs no holding: [count] entries, each [line] tall and [gap]
 * apart, take their own height while that leaves room beneath for one
 * [entry] of the group [below] when there is one, or fits the cell when there
 * is none. Past that they are held to what is left, and never to less than
 * one entry, and scroll within it.
 */
fun band(count: Int, line: Float, gap: Float, room: Float, entry: Float, below: Boolean): Float? {
    if (count == 0) return null
    val natural = count * line + (count - 1) * gap
    val limit = if (below) room - gap - entry else room
    if (natural <= limit) return null
    return maxOf(limit, line)
}

/**
 * A long press lifts the chip, with where in it the finger is and its
 * width; the finger is then reported in the root's coordinates. The lift
 * and the drag consume their events, so the chip's tap and the cell's do
 * not also run. A read-only occurrence, and a birthday, cannot be lifted.
 */
@Composable
private fun Modifier.lift(
    instance: Instance,
    onLift: (Instance, Offset, Float) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
): Modifier {
    if (!instance.editable) return this
    val haptic = LocalHapticFeedback.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val lift by rememberUpdatedState(onLift)
    val drag by rememberUpdatedState(onDrag)
    val drop by rememberUpdatedState(onDrop)
    val cancel by rememberUpdatedState(onCancel)
    return this
        .onGloballyPositioned { coordinates = it }
        .pointerInput(instance.event, instance.start) {
            detectDragGesturesAfterLongPress(
                onDragStart = { position ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    lift(instance, position, size.width.toFloat())
                    drag(coordinates?.localToRoot(position) ?: position)
                },
                onDrag = { change, _ ->
                    change.consume()
                    drag(coordinates?.localToRoot(change.position) ?: change.position)
                },
                onDragEnd = { drop() },
                onDragCancel = { cancel() },
            )
        }
}

/**
 * The list view: occurrences under a heading per day, each row showing its
 * calendar's dot, its title, its marks, its start time and where it is.
 *
 * It opens on the anchor day and pages on as the reader scrolls — a further
 * page when the foot comes into view, an earlier one on a pull or a scroll
 * that reaches the top — stopping at the calendars' own bounds. The search
 * box filters what has been loaded rather than asking the server again.
 */
@Composable
fun AgendaList(
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    onOpen: (Instance) -> Unit,
    selected: Instance? = null,
) {
    val format = LocalFormat.current
    val today = LocalDate.now(viewModel.timezone())
    val listState = rememberLazyListState()
    val search = state.search.trim()
    val matched = remember(state.instances, state.hidden, search) {
        state.visible.filter { instance ->
            search.isEmpty() ||
                listOf(instance.summary, instance.location, instance.description)
                    .any { it.contains(search, ignoreCase = true) }
        }
    }
    val grouped = remember(matched, state.preferences.zones) {
        matched.groupBy { viewModel.day(it) }.toSortedMap()
    }

    // The foot coming into view asks for the next page; reaching the top
    // under the reader's own finger asks for the one before. The gesture is
    // part of the second condition on purpose: without it a prepend that
    // leaves the list at the top would immediately ask for another.
    val atFoot by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    val atHead by remember {
        derivedStateOf {
            listState.isScrollInProgress &&
                listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
        }
    }
    LaunchedEffect(atFoot, state.paging, state.latest, state.bounds) {
        if (atFoot && !state.paging) viewModel.later()
    }
    LaunchedEffect(atHead, state.paging, state.earliest, state.bounds) {
        if (atHead && !state.paging) viewModel.earlier()
    }
    // A search that has matched nothing so far keeps loading later pages, as
    // the web's list does, until something matches or there is none left;
    // nothing to scroll means nothing else would ask for them.
    val searched = searched(search, matched.size, state.paging, viewModel.hasLater(state))
    LaunchedEffect(searched, state.paging, state.latest, state.bounds) {
        if (searched == Searched.LOADING && !state.paging && viewModel.hasLater(state)) viewModel.later()
    }

    if (searched == Searched.LOADING) {
        Paging()
        return
    }
    if (searched == Searched.NONE) {
        // Matched nothing in everything there is to load, which is not the
        // same as a calendar with nothing on it.
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.calendars_list_unmatched),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    if (grouped.isEmpty() && !state.paging) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.calendars_list_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        if (state.paging && viewModel.hasEarlier(state)) {
            item(key = "earlier") { Paging() }
        }
        for ((day, occurrences) in grouped) {
            item(key = "day:$day") {
                // Today's heading is a band in the primary colour, as every
                // grid marks today; the other days' headings sit on the list.
                val current = day == today
                Text(
                    text = format.formatDate(day.atStartOfDay(viewModel.timezone()).toEpochSecond()),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (current) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(occurrences, key = { "${day}-${it.event}-${it.start}" }) { instance ->
                AgendaRow(instance, viewModel, chosen = same(instance, selected)) { onOpen(instance) }
            }
        }
        if (state.paging) {
            item(key = "later") { Paging() }
        }
    }
}

/** What the list shows for a search. */
internal enum class Searched { MATCHES, LOADING, NONE }

/**
 * What the list shows for [search]: its [matches], or with none yet a
 * spinner while a page is [paging] or [later] ones may still match, and
 * "No matches" once there are none left to load. No search shows the list.
 */
internal fun searched(search: String, matches: Int, paging: Boolean, later: Boolean): Searched = when {
    search.isEmpty() || matches > 0 -> Searched.MATCHES
    paging || later -> Searched.LOADING
    else -> Searched.NONE
}

/** The spinner at either end of the list while a page is on its way. */
@Composable
private fun Paging() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

/**
 * One agenda row: the calendar's dot, the title, its marks and, for a timed
 * occurrence, its start's clock at the far end, read in the start's own zone
 * when the views show events in theirs; where it is goes beneath. A past or
 * cancelled occurrence is faded, and the row is tinted in the primary colour
 * while its summary is open, [chosen].
 */
@Composable
fun AgendaRow(instance: Instance, viewModel: CalendarViewModel, chosen: Boolean = false, onClick: () -> Unit) {
    val format = LocalFormat.current
    val zones = viewModel.zones()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (chosen) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = TINT)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(opacity(carried = false, over = viewModel.past(instance), cancelled = instance.cancelled)),
    ) {
        Line(
            instance = instance,
            time = if (instance.allday) null else format.formatTime(instance.start, clockZone(instance.zone?.start, zones)),
            style = MaterialTheme.typography.bodyLarge,
            clock = MaterialTheme.typography.bodyMedium,
            gap = SPACE,
            glyph = 16.dp,
        )
        if (instance.location.isNotBlank()) {
            Text(
                text = instance.location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = DOT + SPACE),
            )
        }
    }
}
