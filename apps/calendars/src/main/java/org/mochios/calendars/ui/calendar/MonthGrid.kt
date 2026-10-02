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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.util.NaturalCompare
import org.mochios.calendars.R
import org.mochios.calendars.model.Instance
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .columnLines(7, MaterialTheme.colorScheme.outlineVariant),
                ) {
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
                    Modifier.fillMaxWidth().shadow(6.dp, corners(SNUG)),
                    raised = true,
                    filled = true,
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
        val groups = stack(
            Group(entries.filter { it.second.bar }, line),
            Group(entries.filterNot { it.second.bar }, line),
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
                filled = true,
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
 * The list view, laid out as Google Calendar's schedule is: each day's
 * occurrences as cards in their colours beside the day in a date column, a
 * label such as "Oct 4 – 10" where a new week starts, and a line across
 * today at the present moment. A day's occurrences go in the order of the
 * clock times they show, all day ones first, as the day view stacks them,
 * and the line falls before the first one still to end by that clock.
 * [onTop] is told the day of the topmost row in view as the list scrolls,
 * whose month the toolbar names. The day it opens on, the anchor, leads it
 * even when empty, with a row that [onCreate] answers with a new event that
 * day, and the list scrolls back to it whenever the anchor moves. A new
 * anchor the pages already held reach is shown at once; one beyond them
 * shows a spinner in place of the old range until it has been read, so
 * nothing is drawn and then pushed aside. A page read in before the earliest, from a pull
 * at the top, brings the list to its new first day rather than holding it
 * where it was.
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
    onTop: (LocalDate) -> Unit = {},
    onCreate: (LocalDate) -> Unit = {},
) {
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
        matched.groupBy { viewModel.day(it) }.toSortedMap().mapValues { (day, occurrences) ->
            occurrences.sortedWith(
                compareBy<Instance> { instance ->
                    if (instance.allday) -1f else viewModel.cut(instance, day)?.from ?: 0f
                }.thenBy(NaturalCompare) { instance -> instance.summary },
            )
        }
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

    val known = state.fetched == state.anchor || viewModel.holds(state.anchor, state)
    if (!known) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Paging()
        }
        return
    }
    if (grouped.isEmpty() && !state.paging && search.isNotEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.calendars_list_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val clock = LocalTime.now(viewModel.timezone()).let { time -> time.hour + time.minute / 60f }
    val opened = state.anchor.takeIf { search.isEmpty() }
    val rows = remember(grouped, today, opened) { rows(grouped, today, clock, opened, viewModel) }
    var head by remember { mutableStateOf(state.earliest) }
    LaunchedEffect(state.earliest) {
        if (state.earliest < head) {
            listState.scrollToItem(0)
        }
        head = state.earliest
    }
    val landing = rows.indexOfFirst { row -> row.day >= state.anchor }
    LaunchedEffect(state.anchor, rows.getOrNull(landing)?.key) {
        if (landing >= 0) {
            val spinner = if (state.paging && viewModel.hasEarlier(state)) 1 else 0
            listState.scrollToItem(landing + spinner)
        }
    }
    val days = remember(rows) { rows.associate { row -> row.key to row.day } }
    val top by rememberUpdatedState(onTop)
    LaunchedEffect(listState, days) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { info -> info.key in days }?.key
        }.collect { key ->
            days[key]?.let { day -> top(day) }
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        if (state.paging && viewModel.hasEarlier(state)) {
            item(key = "earlier") { Paging() }
        }
        items(rows, key = { row -> row.key }) { row ->
            when (row) {
                is Listed.Week -> WeekDivider(row.day)
                is Listed.Now -> NowLine(first = row.first)
                is Listed.Empty -> EmptyDay(row.day, current = row.day == today) {
                    onCreate(row.day)
                }
                is Listed.Event -> AgendaRow(
                    instance = row.instance,
                    viewModel = viewModel,
                    day = if (row.first) row.day else null,
                    today = today,
                    chosen = same(row.instance, selected),
                ) { onOpen(row.instance) }
            }
        }
        if (state.paging) {
            item(key = "later") { Paging() }
        }
    }
}

/**
 * One row of the list view, each knowing the [day] it belongs to, which the
 * toolbar names the month of while it is the topmost row in view.
 */
private sealed class Listed(val key: String, val day: LocalDate) {
    /** The label opening the week that starts on [day]. */
    class Week(day: LocalDate) : Listed("week:$day", day)

    /** [day], the day the list opened on, which has nothing on it. */
    class Empty(day: LocalDate) : Listed("empty:$day", day)

    /** The line across today at the present moment; [first] when it leads the day. */
    class Now(day: LocalDate, val first: Boolean) : Listed("now:$day", day)

    /** An occurrence on [day]; [first] when it opens the day and carries the date. */
    class Event(day: LocalDate, val instance: Instance, val first: Boolean) :
        Listed("$day-${instance.event}-${instance.start}", day)
}

/**
 * The list view's rows in order: each day's occurrences, a week's label
 * where a new week starts, and on [today] the line at [clock], in hours,
 * before the first occurrence still to end by it. [opened], the day the list
 * opened on, has a row of its own saying it is empty when nothing is on it,
 * as Google Calendar's schedule does; null while the list is searched.
 */
private fun rows(
    grouped: Map<LocalDate, List<Instance>>,
    today: LocalDate,
    clock: Float,
    opened: LocalDate?,
    viewModel: CalendarViewModel,
): List<Listed> = buildList {
    var week: LocalDate? = null
    val days = (grouped.keys + listOfNotNull(opened)).toSortedSet()
    for (day in days) {
        val start = viewModel.week(day)
        if (week != null && start != week) {
            add(Listed.Week(start))
        }
        week = start
        val occurrences = grouped[day]
        if (occurrences == null) {
            add(Listed.Empty(day))
            continue
        }
        val cut = if (day == today) {
            occurrences.indexOfFirst { instance ->
                !instance.allday && (viewModel.cut(instance, day)?.to ?: 0f) > clock
            }.let { index -> if (index < 0) occurrences.size else index }
        } else {
            -1
        }
        occurrences.forEachIndexed { index, instance ->
            if (index == cut) {
                add(Listed.Now(day, first = index == 0))
            }
            add(Listed.Event(day, instance, first = index == 0))
        }
        if (cut == occurrences.size) {
            add(Listed.Now(day, first = false))
        }
    }
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

/** The width of the list view's date column. */
private val GUTTER = 56.dp

/**
 * One agenda row, as Google Calendar's schedule lays one out: [day] in the
 * date column when this is the day's first occurrence, its weekday over its
 * number, today's in a filled circle, and beside it a card in the
 * occurrence's colour with its title and marks on the first line and its
 * time on the second, all day, a span of clock times within its day, or its
 * two ends across days, each read in its own zone when the views show
 * events in theirs; where it is goes on a third. A past or cancelled
 * occurrence is faded, and the card is ringed while its summary is open,
 * [chosen].
 */
@Composable
fun AgendaRow(
    instance: Instance,
    viewModel: CalendarViewModel,
    day: LocalDate?,
    today: LocalDate,
    chosen: Boolean = false,
    onClick: () -> Unit,
) {
    val format = LocalFormat.current
    val zones = viewModel.zones()
    val opens = clockZone(instance.zone?.start, zones)
    val closes = clockZone(instance.zone?.finish, zones)
    val time = when {
        instance.allday -> stringResource(R.string.calendars_event_allday)
        viewModel.day(instance) == viewModel.finish(instance) ->
            format.formatClockRange(instance.start, instance.finish, opens, closes)
        else -> format.formatTimeRange(instance.start, instance.finish, opens, closes)
    }
    val colour = instance.colour.toColour(MaterialTheme.colorScheme.primary)
    val ink = if (instance.tentative) {
        Ink(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Ink(Color.White, Color.White.copy(alpha = 0.85f))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 4.dp,
                end = 16.dp,
                top = if (day != null) 12.dp else 4.dp,
                bottom = 4.dp,
            ),
    ) {
        Box(modifier = Modifier.width(GUTTER), contentAlignment = Alignment.TopCenter) {
            if (day != null) {
                DateMark(day, current = day == today)
            }
        }
        CompositionLocalProvider(LocalInk provides ink) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(
                        opacity(
                            carried = false,
                            over = viewModel.past(instance),
                            cancelled = instance.cancelled,
                        ),
                    )
                    .filled(corners(), colour, instance.tentative, chosen)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(SPACE),
                ) {
                    Fitted(instance, MaterialTheme.typography.titleMedium, 1, Modifier.weight(1f))
                    for (mark in marks(instance)) {
                        Glyph(mark, 16.dp)
                    }
                }
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink.muted,
                )
                if (instance.location.isNotBlank()) {
                    Text(
                        text = instance.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = ink.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * A day in the list view's date column: its short weekday over its number,
 * today's weekday in the primary colour and its number in a filled circle.
 */
@Composable
private fun DateMark(day: LocalDate, current: Boolean) {
    val format = LocalFormat.current
    val locale = LocalConfiguration.current.locales[0]
    val colours = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
            style = MaterialTheme.typography.labelMedium,
            color = if (current) colours.primary else colours.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .then(if (current) Modifier.background(colours.primary) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = format.formatNumber(day.dayOfMonth),
                style = MaterialTheme.typography.titleLarge,
                color = if (current) colours.onPrimary else colours.onSurface,
            )
        }
    }
}

/**
 * A day in the list view with nothing on it: its date in the date column and
 * "Nothing planned. Tap to create." beside it, which [onCreate] answers with a
 * new event that day.
 */
@Composable
private fun EmptyDay(day: LocalDate, current: Boolean, onCreate: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Box(modifier = Modifier.width(GUTTER), contentAlignment = Alignment.TopCenter) {
            DateMark(day, current)
        }
        Text(
            text = stringResource(R.string.calendars_list_nothing),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .clip(corners())
                .clickable(onClick = onCreate)
                .padding(horizontal = 12.dp, vertical = 16.dp),
        )
    }
}

/** The label that opens a new week in the list view: "Oct 4 – 10". */
@Composable
private fun WeekDivider(start: LocalDate) {
    val format = LocalFormat.current
    Text(
        text = format.formatDayRange(start, start.plusDays(6), "dMMM"),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp + GUTTER, top = 20.dp, bottom = 4.dp),
    )
}

/**
 * The line across today in the list view at the present moment, between
 * the occurrences that have ended and those still to come, a dot at its
 * start as the time grids draw it. [first] when nothing today has ended yet,
 * so it sits a little lower to clear the date column's weekday.
 */
@Composable
private fun NowLine(first: Boolean) {
    val colour = MaterialTheme.colorScheme.error
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = GUTTER, end = 16.dp, top = if (first) 12.dp else 4.dp, bottom = 2.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colour))
        Box(modifier = Modifier.weight(1f).height(2.dp).background(colour))
    }
}
