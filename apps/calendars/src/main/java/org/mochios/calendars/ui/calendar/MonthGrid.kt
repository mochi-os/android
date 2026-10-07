// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.InlineErrorState
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.util.NaturalCompare
import org.mochios.calendars.R
import org.mochios.calendars.model.Instance
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
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

/** A run of days being picked for a new all-day event, from the [anchor] pressed to the [day] last under the finger. */
private data class Pick(val anchor: LocalDate, val day: LocalDate)

/** The first and last days of a run picked from [anchor] to [day], whichever way it went. */
internal fun ends(anchor: LocalDate, day: LocalDate): Pair<LocalDate, LocalDate> =
    if (day.isBefore(anchor)) day to anchor else anchor to day

/**
 * The month and multiweek views: [weeks] rows of seven days, each led by its
 * ISO week number and each cell holding every one of its occurrences from
 * its own top: one-line bars for the all-day and multi-day ones, and two
 * lines on the cell for each of the rest, the title above the time and
 * marks. The bars come first unless the user put all-day events last; each
 * group scrolls within the cell when it holds more than the cell has room
 * for. In the month view days outside [month] are shaded and their numbers
 * muted, but drawn; in the multiweek view [month] is null and every day
 * reads the same.
 *
 * A tap on a chip opens its summary, a tap on empty cell space starts an
 * event on that day, and the day number opens the day view. A long press
 * lifts a chip, which then follows the finger from cell to cell; resting it
 * at the top or bottom of the weeks turns to the previous or next range by
 * [onStep], with [onLifted] hearing when a drag starts and when it ends so
 * a pager can hold the page under it, and letting go on another day asks
 * [onMove] to move the
 * occurrence so that its first day moves by as many days, and that day's
 * cell scrolls to show it once it lands there. A long press anywhere else in
 * a cell picks a run of days instead, tinted as the finger takes it on to
 * other days; letting go asks [onCreateRange] for an all-day event over
 * them, or [onCreate] when the run is the one day. A chip that cannot be
 * lifted keeps its long press too, as the web's chips are buttons a pick
 * never starts on. The occurrence whose summary is open, [selected], is
 * drawn in the primary colour's tint.
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
    onCreateRange: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    onStep: (Int) -> Unit = {},
    onLifted: (Boolean) -> Unit = {},
    selected: Instance? = null,
) {
    val today = LocalDate.now(viewModel.timezone())
    val format = LocalFormat.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val byDay = remember(state.instances, state.hidden, weeks, state.preferences.zones) {
        weeks.flatMap { week -> (0 until 7).map { week.plusDays(it.toLong()) } }
            .associateWith { day -> state.visible.filter { viewModel.covers(it, day) } }
    }

    // The lifted chip, the finger and the weeks' bounds, all in the root's
    // coordinates; the grid's own origin turns the finger into where the
    // carried chip is drawn. The chip that was pressed lifts itself, and the
    // grid then follows the finger, so the drag outlives the chip when a
    // page turns under it.
    var lift by remember { mutableStateOf<Hold?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var body by remember { mutableStateOf<Rect?>(null) }
    // The day a chip was last dropped on and its event, which that day's
    // cell scrolls into view when the moved occurrence arrives there.
    var landed by remember(weeks) { mutableStateOf<Pair<LocalDate, String>?>(null) }
    // The run of days being picked, and whether a chip took the current
    // press: a chip's long press and its cell's end together, the chip's
    // heard first, as the press reaches the chip before the cell beneath.
    var pick by remember { mutableStateOf<Pick?>(null) }
    var claimed by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    /** The day under the finger, from the weeks' bounds: rows of equal height, seven equal columns. */
    fun under(): LocalDate? {
        val box = body ?: return null
        if (!box.contains(finger) || weeks.isEmpty()) return null
        val row = ((finger.y - box.top) / (box.height / weeks.size)).toInt().coerceIn(0, weeks.size - 1)
        // The first day is on the right in a right-to-left layout.
        val across = if (rtl) box.right - finger.x else finger.x - box.left
        val column = (across / (box.width / 7)).toInt().coerceIn(0, 6)
        return weeks[row].plusDays(column.toLong())
    }

    /** Takes a run being picked on to the day under the finger, keeping the last one while the finger is off the weeks. */
    fun follow() {
        val picking = pick ?: return
        val day = under() ?: return
        if (day != picking.day) pick = picking.copy(day = day)
    }

    fun cancel() {
        lift = null
        pick = null
    }

    fun drop() {
        pick?.let { picking ->
            pick = null
            val (first, last) = ends(picking.anchor, under() ?: picking.day)
            if (first == last) onCreate(first) else onCreateRange(first, last)
            return
        }
        val lifted = lift
        lift = null
        val dropped = under()
        if (lifted != null && dropped != null && dropped != lifted.day) {
            landed = dropped to lifted.instance.event
            val shift = ChronoUnit.DAYS.between(lifted.day, dropped)
            onMove(lifted.instance, viewModel.day(lifted.instance).plusDays(shift))
        }
    }

    // The gesture outlives a composition, so it reaches this one's drop
    // through a holder set afresh after each, as the time grid's does.
    val hands = remember { Grip() }
    SideEffect {
        hands.drop = ::drop
        hands.cancel = ::cancel
        hands.follow = ::follow
        hands.step = onStep
    }
    val target = if (lift != null) under() else null
    val run = pick?.let { ends(it.anchor, it.day) }
    val hearing by rememberUpdatedState(onLifted)
    val holding = lift != null || pick != null
    LaunchedEffect(holding) {
        hearing(holding)
    }

    // A chip resting at the top or bottom of the weeks turns the page, once
    // and then again each hold.
    LaunchedEffect(lift != null) {
        if (lift == null) return@LaunchedEffect
        val near = with(density) { SIDE.toPx() }
        val dwell = Dwell()
        while (lift != null) {
            val time = withFrameMillis { it }
            val box = body ?: continue
            val inside = finger.x >= box.left && finger.x <= box.right
            val direction = when {
                !inside -> 0
                finger.y >= box.top - near && finger.y < box.top + near -> -1
                finger.y > box.bottom - near && finger.y <= box.bottom + near -> 1
                else -> 0
            }
            val turn = dwell.step(direction, time)
            if (turn != 0) hands.step(turn)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    claimed = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val up = change.changedToUpIgnoreConsumed()
                        // The system taking the finger away ends the gesture
                        // with an up that arrives already consumed, which
                        // lands and makes nothing.
                        val cancelled = up && change.isConsumed
                        if (lift != null || pick != null) {
                            // Once a chip is lifted or days are being picked
                            // the finger is the gesture's, so no cell's scroll
                            // or tap beneath it acts.
                            finger = origin + change.position
                            change.consume()
                            hands.follow()
                            if (cancelled) hands.cancel() else if (up) hands.drop()
                        }
                        if (up) break
                    }
                }
            },
    ) {
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("weeks")
                    .onGloballyPositioned { body = it.rectInRoot() },
            ) {
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
                                week = if (offset == 0) number(week) else null,
                                today = today,
                                allday = state.preferences.allday,
                                outside = month != null && day.monthValue != month,
                                instances = occurrences,
                                viewModel = viewModel,
                                lifted = lift?.instance,
                                selected = selected,
                                targeted = target == day && lift?.day != day ||
                                    run != null && !day.isBefore(run.first) && !day.isAfter(run.second),
                                modifier = Modifier.weight(1f),
                                onDay = { viewModel.open(day) },
                                onCreate = { onCreate(day) },
                                onOpen = onOpen,
                                landing = landed?.takeIf { it.first == day }?.second,
                                onLift = { instance, at, grab, width ->
                                    claimed = true
                                    landed = null
                                    finger = at
                                    lift = Hold(instance, day, grab, width)
                                },
                                onHold = { claimed = true },
                                onPick = { at ->
                                    if (!claimed) {
                                        finger = at
                                        pick = Pick(day, day)
                                    }
                                },
                            )
                        }
                    }
                    HorizontalDivider()
                }
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
                    time = if (look.time) clock(lifted.instance, viewModel) else null,
                    raised = true,
                ) {}
            }
        }
    }
}

/** What the month grid's gesture calls on, from the latest composition. */
private class Grip {
    var drop: () -> Unit = {}
    var cancel: () -> Unit = {}
    var follow: () -> Unit = {}
    var step: (Int) -> Unit = {}
}

/** How close to the weeks' top or bottom a carried chip turns the page. */
private val SIDE = 28.dp

/**
 * The ISO week number a row of seven days is labelled with: that of its
 * middle day, so a row starting on Sunday or Saturday takes the number of
 * the week most of its days are in.
 */
fun number(first: LocalDate): Int = first.plusDays(3).get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)

/** A layout's bounds in the root's coordinates, unclipped by its parents. */
private fun LayoutCoordinates.rectInRoot(): Rect {
    val at = positionInRoot()
    return Rect(at.x, at.y, at.x + size.width, at.y + size.height)
}

@Composable
private fun Cell(
    day: LocalDate,
    week: Int?,
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
    onLift: (Instance, Offset, Offset, Float) -> Unit,
    onHold: () -> Unit,
    onPick: (Offset) -> Unit,
) {
    val current = day == today
    val format = LocalFormat.current
    val zones = viewModel.zones()
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(
                when {
                    targeted -> Modifier.background(tint)
                    outside -> Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    else -> Modifier
                },
            )
            .testTag(if (outside) "outside" else "inside")
            .clickable(onClick = onCreate)
            .pick(day, onPick),
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
                        outside -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            // The row's week number sits in its first day's corner, opposite
            // the date, taking no width of its own.
            if (week != null) {
                Text(
                    text = week.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (current) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 3.dp)
                        .testTag("week-number"),
                )
            }
        }
        // The bars and the timed entries stack from the top of the cell in the
        // order the user chose, each group scrolling on its own. The upper one
        // is held so that one entry of the lower stays in view.
        val entries = instances.map { it to look(it, viewModel.day(it), viewModel.finish(it), day) }
        // A bar is one line; a timed entry is two, its dot, time and marks
        // above its title.
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
                time = if (look.time) clock(instance, viewModel) else null,
                chosen = same(instance, selected),
                lift = Modifier.lift(instance, onLift, onHold),
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

/**
 * An occurrence's start as a month or multiweek cell writes it, in its own
 * zone when the views read events in theirs.
 */
@Composable
private fun clock(instance: Instance, viewModel: CalendarViewModel): String =
    LocalFormat.current.formatTime(instance.start, clockZone(instance.zone?.start, viewModel.zones()))

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
 * A long press lifts the chip: [onLift] has the occurrence, the finger in
 * the root's coordinates, where in the chip the finger is and the chip's
 * width. The grid follows the finger from there, so the chip's own drag
 * ends, unheeded, at the first move. A read-only occurrence, and a
 * birthday, cannot be lifted; its long press is [onHold]'s, so the cell
 * beneath picks no days from it, and takes none of the press's events, so
 * letting go still opens it.
 */
@Composable
private fun Modifier.lift(
    instance: Instance,
    onLift: (Instance, Offset, Offset, Float) -> Unit,
    onHold: () -> Unit,
): Modifier {
    if (!instance.editable) {
        val hold by rememberUpdatedState(onHold)
        return pointerInput(instance.event, instance.start) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (awaitLongPressOrCancellation(down.id) != null) hold()
            }
        }
    }
    val haptic = LocalHapticFeedback.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val lift by rememberUpdatedState(onLift)
    return this
        .onGloballyPositioned { coordinates = it }
        .pointerInput(instance.event, instance.start) {
            detectDragGesturesAfterLongPress(
                onDragStart = { position ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    lift(instance, coordinates?.localToRoot(position) ?: position, position, size.width.toFloat())
                },
                onDrag = { _, _ -> },
            )
        }
}

/**
 * A long press on a cell starts picking a run of days from [day]: [onPick]
 * has the finger in the root's coordinates, and the grid follows it from
 * there, so this drag ends, unheeded, at the first move, as a lifted chip's
 * does.
 */
@Composable
private fun Modifier.pick(day: LocalDate, onPick: (Offset) -> Unit): Modifier {
    val haptic = LocalHapticFeedback.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val picking by rememberUpdatedState(onPick)
    return this
        .onGloballyPositioned { coordinates = it }
        .pointerInput(day) {
            detectDragGesturesAfterLongPress(
                onDragStart = { position ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    picking(coordinates?.localToRoot(position) ?: position)
                },
                onDrag = { _, _ -> },
            )
        }
}

/**
 * The list view, as the web's draws it: each day under a heading across the
 * whole width that stays at the top while its day is in view, its
 * occurrences beneath as rows that each open with the occurrence's dot, and
 * a line across today at the present moment. A day's occurrences go in the
 * order of the clock times they show, all day ones first, as the day view
 * stacks them, and the line falls before the first one still to end by that
 * clock.
 * [onTop] is told the day of the topmost row in view as the list scrolls,
 * whose month the toolbar names. The day it opens on, the anchor, leads it
 * even when empty, with a row that [onCreate] answers with a new event that
 * day, and the list scrolls back to it whenever the anchor moves. A new
 * anchor the pages already held reach is shown at once; one beyond them
 * shows a spinner in place of the old range until it has been read, so
 * nothing is drawn and then pushed aside. A page read in before the earliest, from a pull
 * at the top, brings the list to its new first day rather than holding it
 * where it was. On a wide screen each row also names its calendar, as the
 * web's list does.
 *
 * It opens on the anchor day and pages on as the reader scrolls: further
 * pages while the foot is in view, so a first page with nothing on it still
 * reaches the events after it, and earlier ones from "Earlier events", a
 * pull, or a scroll that reaches the top, stopping at the calendars' own
 * bounds. A page that fails stops the paging and says so with Retry. The
 * search box filters what has been loaded rather than asking the server
 * again.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE
    val names = remember(state.calendars) { state.calendars.associate { it.id to it.name } }
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
    val earlier = viewModel.hasEarlier(state)
    val later = viewModel.hasLater(state)

    // The foot in view asks for the next page, so a short or empty list keeps
    // loading until it fills the screen; reaching the top under the reader's
    // own finger asks for the one before. The gesture is part of the second
    // condition on purpose: without it a prepend that leaves the list at the
    // top would immediately ask for another.
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
    LaunchedEffect(atFoot, state.paging, state.latest, state.bounds, state.stalled) {
        if (atFoot && !state.paging) viewModel.later()
    }
    LaunchedEffect(atHead, state.paging, state.earliest, state.bounds) {
        if (atHead && !state.paging) viewModel.earlier()
    }
    // A search that has matched nothing so far keeps loading later pages, as
    // the web's list does, until something matches or there is none left.
    val searched = searched(search, matched.size, state.paging, later)
    LaunchedEffect(searched, state.paging, state.latest, state.bounds) {
        if (searched == Searched.LOADING && !state.paging && later) viewModel.later()
    }

    val known = state.fetched == state.anchor || viewModel.holds(state.anchor, state)
    if (!known) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Paging()
        }
        return
    }
    // A failed page leaves a search with nothing to wait on, so it falls
    // through to the list, which offers Retry.
    if (searched == Searched.LOADING && state.stalled == null) {
        Paging()
        return
    }
    if (searched == Searched.NONE && state.stalled == null) {
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
            // Past the "Earlier events" button, which leads the list.
            listState.scrollToItem(landing + if (earlier) 1 else 0)
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
        if (earlier) {
            item(key = "earlier") {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    MochiOutlinedButton(onClick = { viewModel.earlier() }, enabled = !state.paging) {
                        if (state.paging) {
                            CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        }
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.calendars_list_earlier))
                    }
                }
            }
        }
        for (row in rows) {
            when (row) {
                is Listed.Day -> stickyHeader(key = row.key) {
                    Heading(row.day, current = row.day == today)
                }
                is Listed.Now -> item(key = row.key) { NowLine() }
                is Listed.Empty -> item(key = row.key) {
                    EmptyDay { onCreate(row.day) }
                }
                is Listed.Event -> item(key = row.key) {
                    AgendaRow(
                        instance = row.instance,
                        viewModel = viewModel,
                        chosen = same(row.instance, selected),
                        calendar = if (wide) names[row.instance.calendar].orEmpty() else null,
                        divided = !row.last,
                    ) { onOpen(row.instance) }
                }
            }
        }
        state.stalled?.let { stalled ->
            item(key = "stalled") {
                InlineErrorState(error = stalled.error, onRetry = { viewModel.resume() })
            }
        }
        if (state.paging) {
            item(key = "paging") { Paging() }
        }
        // Always last, so a list too short to scroll still has its foot in view.
        item(key = "foot") { Spacer(Modifier.height(1.dp)) }
    }
}

/**
 * One row of the list view, each knowing the [day] it belongs to, which the
 * toolbar names the month of while it is the topmost row in view.
 */
private sealed class Listed(val key: String, val day: LocalDate) {
    /** The heading [day]'s rows sit under. */
    class Day(day: LocalDate) : Listed("day:$day", day)

    /** [day], the day the list opened on, which has nothing on it. */
    class Empty(day: LocalDate) : Listed("empty:$day", day)

    /** The line across today at the present moment. */
    class Now(day: LocalDate) : Listed("now:$day", day)

    /** An occurrence on [day]; [last] when nothing of its day follows it. */
    class Event(day: LocalDate, val instance: Instance, val last: Boolean) :
        Listed("$day-${instance.event}-${instance.start}", day)
}

/**
 * The list view's rows in order: each day's heading and its occurrences, and
 * on [today] the line at [clock], in hours, before the first occurrence still
 * to end by it. [opened], the day the list opened on, has a row of its own
 * saying it is empty when nothing is on it; null while the list is searched.
 */
private fun rows(
    grouped: Map<LocalDate, List<Instance>>,
    today: LocalDate,
    clock: Float,
    opened: LocalDate?,
    viewModel: CalendarViewModel,
): List<Listed> = buildList {
    val days = (grouped.keys + listOfNotNull(opened)).toSortedSet()
    for (day in days) {
        add(Listed.Day(day))
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
                add(Listed.Now(day))
            }
            add(Listed.Event(day, instance, last = index == occurrences.lastIndex))
        }
        if (cut == occurrences.size) {
            add(Listed.Now(day))
        }
    }
}

/** From this screen width, in dp, a list row names its calendar, as the web's does from a tablet's width. */
private const val WIDE = 600

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

/** An agenda row's title: larger than the body text, at its regular weight. */
private val TITLE = 18.sp

/** The space around an agenda row's words. */
private val PAD = 16.dp

/**
 * One agenda row, as the web's list lays one out: the occurrence's dot, a
 * ring for a tentative one, and its title with its marks on the first line;
 * its time on the second, all day, a span of clock times within its day, or
 * its two ends across days, each read in its own zone when the views show
 * events in theirs; where it is on a third. A past or cancelled occurrence
 * is faded, and the row is tinted in the primary colour while its summary is
 * open, [chosen]. The [calendar]'s name, when given, goes last. A [divided]
 * row draws a hairline under itself, before the next row of its day.
 */
@Composable
fun AgendaRow(
    instance: Instance,
    viewModel: CalendarViewModel,
    chosen: Boolean = false,
    calendar: String? = null,
    divided: Boolean = false,
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
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // The second and third lines start under the title, clear of the dot.
    val under = Modifier.padding(start = DOT + SPACE)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (chosen) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = TINT)) else Modifier)
            .clickable(onClick = onClick)
            .testTag("row"),
    ) {
        Column(
            modifier = Modifier
                .alpha(
                    opacity(
                        carried = false,
                        over = viewModel.past(instance),
                        cancelled = instance.cancelled,
                    ),
                )
                .padding(horizontal = PAD, vertical = PAD),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SPACE),
            ) {
                Dot(instance)
                Name(
                    instance,
                    MaterialTheme.typography.bodyLarge.copy(fontSize = TITLE, lineHeight = TITLE * 1.3f),
                    Modifier.weight(1f),
                )
                for (mark in marks(instance)) {
                    Glyph(mark, 16.dp)
                }
            }
            Text(
                text = time,
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                modifier = under,
            )
            if (instance.location.isNotBlank()) {
                Text(
                    text = instance.location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = under,
                )
            }
            if (calendar != null) {
                Text(
                    text = calendar,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = under,
                )
            }
        }
        if (divided) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = PAD),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/**
 * A day's heading in the list view, across the whole width: its short
 * weekday and its date in the user's date format, put together the way the
 * user's language orders them. Today's is a band in the primary colour, as
 * every grid marks today; the other days' sit on a muted band, which also
 * keeps the rows from showing through while it stays at the top.
 */
@Composable
private fun Heading(day: LocalDate, current: Boolean) {
    val format = LocalFormat.current
    val locale = LocalConfiguration.current.locales[0]
    val colours = MaterialTheme.colorScheme
    Text(
        text = stringResource(
            R.string.calendars_list_heading,
            day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
            // A date, read at noon in no zone, so no offset can tip it.
            format.formatDate(day.atTime(12, 0).toEpochSecond(ZoneOffset.UTC), "UTC"),
        ),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (current) colours.onPrimary else colours.onSurface,
        modifier = Modifier
            .testTag("heading")
            .fillMaxWidth()
            .background(colours.surface)
            .background(if (current) colours.primary else colours.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = PAD, vertical = 8.dp),
    )
}

/**
 * The row under a day's heading when nothing is on it: "Nothing planned. Tap
 * to create.", which [onCreate] answers with a new event that day.
 */
@Composable
private fun EmptyDay(onCreate: () -> Unit) {
    Text(
        text = stringResource(R.string.calendars_list_nothing),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCreate)
            .padding(horizontal = PAD, vertical = PAD),
    )
}

/**
 * The line across today in the list view at the present moment, between
 * the occurrences that have ended and those still to come, a dot at its
 * start as the time grids draw it.
 */
@Composable
private fun NowLine() {
    val colour = MaterialTheme.colorScheme.error
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAD, vertical = 2.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colour))
        Box(modifier = Modifier.weight(1f).height(2.dp).background(colour))
    }
}
