// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.theme.LocalEntityRadius
import org.mochios.calendars.R
import org.mochios.calendars.model.Instance
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The height of one hour row; the whole day is twenty-four of these. */
private val HOUR = 56.dp

/** The gutter the hour labels sit in. */
private val GUTTER = 52.dp

/** The height of one all-day chip in the band above the grid. */
private val BAND = 22.dp

/** How faded a past or cancelled occurrence is drawn. */
internal const val DIM = 0.6f

/** How faded the block or chip a drag has lifted stays where it was. */
internal const val CARRIED = 0.4f

/** How strong the primary colour's tint is behind the occurrence whose summary is open. */
internal const val TINT = 0.1f

/**
 * How opaque an occurrence is drawn: faded where it was while a drag
 * [carried] it away, dimmed once it is [over] or [cancelled], and whole
 * otherwise.
 */
fun opacity(carried: Boolean, over: Boolean, cancelled: Boolean): Float = when {
    carried -> CARRIED
    over || cancelled -> DIM
    else -> 1f
}

/** Whether [other] is the same occurrence as [instance]: the same event at the same start. */
fun same(instance: Instance, other: Instance?): Boolean =
    other != null && other.event == instance.event && other.start == instance.start

/** The roundest corner a block or bar takes, whatever the user's radius. */
private val CORNER = 6.dp

/** The dot in an occurrence's calendar colour that every line opens with. */
internal val DOT = 8.dp

/** The least space between the dot, the title, each mark and the time. */
private val GAP = 4.dp

/** How big a mark is in the grids. */
private val GLYPH = 12.dp

/** The strip along a block's bottom edge that resizes it. */
private val HANDLE = 12.dp

/** How close to the grid's edge a drag scrolls it, and how far a frame moves. */
private val EDGE = 56.dp
private val SPEED = 6.dp

/**
 * A block lifted by a long press: which occurrence, the day and the block it
 * was lifted from, how far down the block the finger took it in pixels, and
 * whether the end handle was taken, which resizes rather than moves.
 */
private data class Lift(
    val instance: Instance,
    val day: LocalDate,
    val cut: Cut,
    val grab: Float,
    val resize: Boolean,
)

/**
 * Where a lifted block would land: the day, the block on it, and the
 * occurrence's ends there, epoch seconds.
 */
private data class Drop(val day: LocalDate, val cut: Cut, val start: Long, val finish: Long)

/**
 * The day and week views: an all-day band above a scrolling time grid of
 * [days] columns. Non-working hours are shaded, today carries the
 * current-time line, and occurrences that overlap share the column's width.
 * A single column draws each block on one line; the week's narrow columns
 * stack the time and marks beneath the title.
 *
 * A tap on an occurrence opens its summary; a tap on empty grid starts an
 * event at that hour on that day. A long press lifts a block, which then
 * follows the finger to the quarter hour, on any of the days shown, and a
 * long press on the strip along its bottom edge drags its end instead; the
 * grid scrolls while the finger rests near its top or bottom. Letting go
 * somewhere else asks [onMove] to move the occurrence there. The occurrence
 * whose summary is open, [selected], is drawn in the primary colour's tint.
 *
 * [scroll] is the grid's vertical position, shared by the pages either side
 * so a swipe keeps the same hours in view.
 *
 * With [stacked], each column header puts the weekday above the day number,
 * as the week view does; otherwise it reads on one line, as in the day view.
 *
 * The hour labels are not part of the grid: [HourGutter] draws them beside
 * the pager, so a swipe moves the days and leaves the hours where they are.
 * [onTop] is told how far down the grid its hours start, below the headings
 * and the all-day band, for the gutter to line up with.
 */
@Composable
fun TimeGrid(
    days: List<LocalDate>,
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    onOpen: (Instance) -> Unit,
    onCreate: (LocalDate, Int) -> Unit,
    onMove: (Instance, Long, Long) -> Unit,
    scroll: ScrollState,
    stacked: Boolean = false,
    selected: Instance? = null,
    onTop: (Dp) -> Unit = {},
) {
    val today = LocalDate.now(viewModel.timezone())
    val columns = LocalConfiguration.current.screenWidthDp.dp - GUTTER
    val width = columns / days.size.coerceAtLeast(1)

    val byDay = remember(state.instances, state.hidden, days, state.preferences.zones) {
        days.associateWith { day -> state.visible.filter { viewModel.covers(it, day) } }
    }

    val density = LocalDensity.current

    // The lifted block, the finger and the grid's geometry, all in the
    // root's coordinates: the finger stays put while the grid scrolls under
    // it, so where the block would land is read from the finger and the
    // scroll rather than from where the finger last moved.
    var lift by remember { mutableStateOf<Lift?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf<Rect?>(null) }
    val bounds = remember { mutableStateMapOf<LocalDate, Rect>() }
    val hourPx = with(density) { HOUR.toPx() }
    val user = viewModel.timezone()
    val zones = viewModel.zones()

    fun landing(lifted: Lift): Drop? {
        val window = viewport ?: return null
        val day = if (lifted.resize) {
            lifted.day
        } else {
            days.minByOrNull { day ->
                val column = bounds[day] ?: return@minByOrNull Float.MAX_VALUE
                when {
                    finger.x < column.left -> column.left - finger.x
                    finger.x >= column.right -> finger.x - column.right
                    else -> 0f
                }
            } ?: lifted.day
        }
        val content = (finger.y - window.top + scroll.value) / hourPx
        val cut = if (lifted.resize) {
            resized(lifted.cut, content)
        } else {
            moved(lifted.cut, content - lifted.grab / hourPx - lifted.cut.from)
        }
        val startZone = if (zones) zoneOf(lifted.instance.zone?.start, user) else user
        val finishZone = if (zones) zoneOf(lifted.instance.zone?.finish, user) else user
        val (start, finish) = dropped(lifted.instance, lifted.day, lifted.cut, day, cut, startZone, finishZone, lifted.resize)
        return Drop(day, cut, start, finish)
    }
    val drop = lift?.let { landing(it) }

    // The grid scrolls while the finger rests near its top or bottom edge,
    // faster the nearer it is.
    LaunchedEffect(lift != null) {
        if (lift == null) return@LaunchedEffect
        val edge = with(density) { EDGE.toPx() }
        val speed = with(density) { SPEED.toPx() }
        while (true) {
            withFrameMillis { }
            val window = viewport ?: continue
            val fromTop = finger.y - window.top
            val fromBottom = window.bottom - finger.y
            when {
                fromTop < edge -> scroll.scrollBy(-speed * ((edge - fromTop) / edge).coerceIn(0f, 1f))
                fromBottom < edge -> scroll.scrollBy(speed * ((edge - fromBottom) / edge).coerceIn(0f, 1f))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (day in days) {
                DayHeading(day, today, width, stacked, Modifier.clickable { viewModel.open(day) })
            }
        }
        AllDayBand(days, byDay, width, viewModel, selected, onOpen)
        HorizontalDivider()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { coordinates ->
                    viewport = coordinates.rectInRoot()
                    onTop(with(density) { coordinates.positionInParent().y.toDp() })
                }
                .verticalScroll(scroll),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (day in days) {
                    DayColumn(
                        day = day,
                        today = today,
                        width = width,
                        stacked = days.size > 1,
                        instances = byDay[day].orEmpty().filterNot { it.allday },
                        state = state,
                        viewModel = viewModel,
                        lifted = lift?.instance,
                        selected = selected,
                        drop = drop?.takeIf { it.day == day },
                        onOpen = onOpen,
                        onCreate = onCreate,
                        onPlaced = { bounds[day] = it },
                        onLift = { placed, grab, resize ->
                            lift = Lift(placed.instance, day, Cut(placed.top, placed.top + placed.height, placed.backwards), grab, resize)
                        },
                        onDrag = { finger = it },
                        onDrop = {
                            val lifted = lift
                            lift = null
                            val target = lifted?.let { landing(it) }
                            if (lifted != null && target != null &&
                                (target.start != lifted.instance.start || target.finish != lifted.instance.finish)
                            ) {
                                onMove(lifted.instance, target.start, target.finish)
                            }
                        },
                        onCancel = { lift = null },
                    )
                }
            }
        }
    }
}

/**
 * The hour labels down the left of the day and week views, which stay put
 * while the pager beside them swipes. [top] is how far down the grid's hours
 * start, and [scroll] is the grid's own vertical position, so the labels move
 * with the hours they name.
 */
@Composable
fun HourGutter(top: Dp, scroll: ScrollState, modifier: Modifier = Modifier) {
    val format = LocalFormat.current
    Column(modifier = modifier.width(GUTTER).fillMaxHeight()) {
        Spacer(Modifier.height((top - DividerDefaults.Thickness).coerceAtLeast(0.dp)))
        HorizontalDivider()
        Column(modifier = Modifier.weight(1f).verticalScroll(scroll)) {
            for (hour in 0 until 24) {
                Box(modifier = Modifier.height(HOUR).fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                    Text(
                        text = format.formatHour(hour),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * A time grid's vertical position, opened on the working day rather than at
 * midnight, as Thunderbird does. The scroll is in pixels, so the hour rows are
 * measured through the density rather than taken as their own dp number.
 */
@Composable
fun rememberHourScroll(start: Int): ScrollState {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(start) {
        scroll.scrollTo(with(density) { (HOUR * start.coerceIn(0, 23)).roundToPx() })
    }
    return scroll
}

/** A layout's bounds in the root's coordinates, unclipped by its parents. */
private fun LayoutCoordinates.rectInRoot(): Rect {
    val origin = positionInRoot()
    return Rect(origin.x, origin.y, origin.x + size.width, origin.y + size.height)
}

@Composable
private fun DayHeading(
    day: LocalDate,
    today: LocalDate,
    width: Dp,
    stacked: Boolean,
    modifier: Modifier,
) {
    val current = day == today
    val colour = if (current) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val weight = if (current) FontWeight.Bold else FontWeight.Normal
    val locale = LocalConfiguration.current.locales[0]
    val pattern = remember(locale) { android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEd") }
    Column(
        modifier = modifier
            .width(width)
            .then(if (current) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (stacked) {
            Text(
                text = weekdayLabel(day),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = weight,
                color = colour,
            )
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = weight,
                color = colour,
            )
        } else {
            Text(
                text = heading(day, pattern, locale),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = weight,
                color = colour,
            )
        }
    }
}

/**
 * A column header's weekday and day on one line, in the order the locale's
 * own pattern gives: "Tue 22" in British English, "22 Tue" in American,
 * "mar. 22" in French. [pattern] is the platform's best fit for a weekday
 * and a day, which the caller asks for once per locale.
 */
fun heading(day: LocalDate, pattern: String, locale: java.util.Locale): String =
    java.time.format.DateTimeFormatter.ofPattern(pattern, locale).format(day)

/**
 * The band above the grid, holding the all-day occurrences as bars; a timed
 * one that crosses midnight is a block on each day it covers instead.
 */
@Composable
private fun AllDayBand(
    days: List<LocalDate>,
    byDay: Map<LocalDate, List<Instance>>,
    width: Dp,
    viewModel: CalendarViewModel,
    selected: Instance?,
    onOpen: (Instance) -> Unit,
) {
    val rows = days.maxOfOrNull { byDay[it].orEmpty().count { instance -> instance.allday } } ?: 0
    if (rows == 0) return
    Row(modifier = Modifier.fillMaxWidth().heightIn(max = BAND * 4)) {
        for (day in days) {
            Column(
                modifier = Modifier.width(width).padding(horizontal = 1.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (instance in byDay[day].orEmpty().filter { it.allday }.take(4)) {
                    Chip(
                        instance,
                        Modifier
                            .height(BAND)
                            .fillMaxWidth()
                            .alpha(opacity(carried = false, over = viewModel.past(instance), cancelled = instance.cancelled)),
                        chosen = same(instance, selected),
                    ) { onOpen(instance) }
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: LocalDate,
    today: LocalDate,
    width: Dp,
    stacked: Boolean,
    instances: List<Instance>,
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    lifted: Instance?,
    selected: Instance?,
    drop: Drop?,
    onOpen: (Instance) -> Unit,
    onCreate: (LocalDate, Int) -> Unit,
    onPlaced: (Rect) -> Unit,
    onLift: (Placed, Float, Boolean) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
) {
    val format = LocalFormat.current
    val shading = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val line = MaterialTheme.colorScheme.error
    val working = state.preferences.days.contains(day.dayOfWeek.value % 7)
    val layout = remember(instances, day, state.preferences.zones) { lay(instances, viewModel, day) }
    val zones = viewModel.zones()

    Box(
        modifier = Modifier
            .width(width)
            .height(HOUR * 24)
            .onGloballyPositioned { onPlaced(it.rectInRoot()) },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (hour in 0 until 24) {
                val shaded = !working || hour < state.preferences.hours.start || hour >= state.preferences.hours.finish
                Box(
                    modifier = Modifier
                        .height(HOUR)
                        .fillMaxWidth()
                        .background(if (shaded) shading else Color.Transparent)
                        .clickable { onCreate(day, hour) },
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
        for (placed in layout) {
            val columnWidth = width / placed.columns
            val own = same(placed.instance, lifted)
            Box(
                modifier = Modifier
                    .offset(x = columnWidth * placed.column, y = HOUR * placed.top)
                    .width(columnWidth)
                    .height((HOUR * placed.height).coerceAtLeast(18.dp))
                    .padding(end = 2.dp)
                    .alpha(opacity(carried = own, over = viewModel.past(placed.instance), cancelled = placed.instance.cancelled)),
            ) {
                Block(
                    instance = placed.instance,
                    backwards = placed.backwards,
                    zones = zones,
                    handle = placed.instance.editable && !placed.backwards,
                    stacked = stacked,
                    chosen = same(placed.instance, selected),
                    modifier = Modifier.lift(placed, onLift, onDrag, onDrop, onCancel) { onOpen(placed.instance) },
                )
            }
        }
        if (drop != null && lifted != null) {
            Box(
                modifier = Modifier
                    .offset(y = HOUR * drop.cut.from)
                    .fillMaxWidth()
                    .height((HOUR * (drop.cut.to - drop.cut.from)).coerceAtLeast(18.dp))
                    .padding(end = 2.dp)
                    .zIndex(1f),
            ) {
                Block(
                    instance = lifted,
                    zones = zones,
                    span = interval(lifted.copy(start = drop.start, finish = drop.finish), zones, format::formatTime, ranged()),
                    stacked = stacked,
                    modifier = Modifier.shadow(6.dp, corners()),
                )
            }
        }
        if (day == today) {
            val now = LocalTime.now(viewModel.timezone())
            val fraction = (now.hour + now.minute / 60f)
            Box(
                modifier = Modifier
                    .offset(y = HOUR * fraction)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(line),
            )
        }
    }
}

/**
 * A tap opens the block; a long press lifts it, with the finger's distance
 * down the block, or takes its end handle when the press is on the strip
 * along the bottom. The finger is reported in the root's coordinates, which
 * the block's own keep up with as the grid scrolls under it. The lift and
 * the drag consume their events, so the tap and the scroll do not also run.
 * A read-only occurrence, and a birthday, cannot be lifted.
 */
@Composable
private fun Modifier.lift(
    placed: Placed,
    onLift: (Placed, Float, Boolean) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
    onClick: () -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val handle = with(LocalDensity.current) { HANDLE.toPx() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val lift by rememberUpdatedState(onLift)
    val drag by rememberUpdatedState(onDrag)
    val drop by rememberUpdatedState(onDrop)
    val cancel by rememberUpdatedState(onCancel)
    val clicked = this.clickable(onClick = onClick)
    if (!placed.instance.editable) return clicked
    return clicked
        .onGloballyPositioned { coordinates = it }
        .pointerInput(placed.instance.event, placed.instance.start, placed.backwards) {
            detectDragGesturesAfterLongPress(
                onDragStart = { position ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val resize = !placed.backwards && position.y >= size.height - handle
                    lift(placed, position.y, resize)
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

/** One occurrence placed in a day column. */
private data class Placed(
    val instance: Instance,
    val top: Float,
    val height: Float,
    val column: Int,
    val columns: Int,
    /** The end reads before the start by the clock, across zones. */
    val backwards: Boolean,
)

/**
 * Lays a day's timed occurrences out, giving overlapping ones a share of the
 * width each. Each is cut to the day by [CalendarViewModel.cut]; the cuts
 * are taken in start order and put in the first column whose last occurrence
 * has finished, and a run that overlaps is then as wide as the columns it
 * needed.
 */
private fun lay(instances: List<Instance>, viewModel: CalendarViewModel, day: LocalDate): List<Placed> {
    if (instances.isEmpty()) return emptyList()
    val cuts = instances
        .mapNotNull { instance -> viewModel.cut(instance, day)?.let { instance to it } }
        .sortedBy { it.second.from }

    val out = mutableListOf<Placed>()
    var group = mutableListOf<Pair<Instance, Cut>>()
    var columns = mutableListOf<Float>()
    var assigned = mutableListOf<Int>()

    fun flush() {
        if (group.isEmpty()) return
        val total = columns.size
        for ((index, placed) in group.withIndex()) {
            val (instance, cut) = placed
            out.add(Placed(instance, cut.from, cut.to - cut.from, assigned[index], total, cut.backwards))
        }
        group = mutableListOf()
        columns = mutableListOf()
        assigned = mutableListOf()
    }

    for (placed in cuts) {
        val cut = placed.second
        if (columns.isNotEmpty() && columns.all { it <= cut.from }) flush()
        var column = columns.indexOfFirst { it <= cut.from }
        if (column < 0) {
            columns.add(cut.to)
            column = columns.size - 1
        } else {
            columns[column] = cut.to
        }
        group.add(placed)
        assigned.add(column)
    }
    flush()
    return out
}

/**
 * A timed occurrence's block: the neutral surface of the time grid,
 * its outline dashed for a tentative one and in the primary colour, over the
 * primary colour's tint, when [chosen]. Its time is its span, "09:00 to
 * 10:00", each end read in its own zone when [zones] is on, or [span] when
 * given, which a lifted block uses for where it would land. A [backwards] block stands in for an occurrence whose end reads
 * before its start, and says so with a mark. With [handle] on, a strip along
 * the bottom edge marks where a long press takes the end.
 *
 * On one line, as the day view draws it, the block reads dot, title, marks
 * and the time at the far end, with the location beneath when the block is
 * tall enough. [stacked], as the week view's narrow columns draw it, puts
 * the dot and title on the first line, the time and marks on a second in a
 * smaller type, and the location on a third, each only when the block is
 * tall enough to hold it.
 */
@Composable
fun Block(
    instance: Instance,
    backwards: Boolean = false,
    zones: Boolean = false,
    span: String? = null,
    handle: Boolean = false,
    stacked: Boolean = false,
    chosen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val format = LocalFormat.current
    val time = span ?: interval(instance, zones, format::formatTime, ranged())
    BoxWithConstraints(modifier = modifier.fillMaxSize().panel(corners(), dashed = instance.tentative, chosen = chosen)) {
        val height = maxHeight
        if (stacked) {
            val first = MaterialTheme.typography.labelMedium.packed()
            val second = MaterialTheme.typography.labelSmall.packed()
            val two = leading(first) + leading(second) + 1.dp
            val three = two + leading(second)
            Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 1.dp)) {
                Title(instance, first)
                if (height >= two) {
                    Detail(instance, time, second, backwards = backwards)
                }
                if (height >= three && instance.location.isNotBlank()) {
                    Text(
                        text = instance.location,
                        style = second,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = DOT + GAP),
                    )
                }
            }
        } else {
            val first = MaterialTheme.typography.labelMedium
            val second = MaterialTheme.typography.labelSmall
            val two = leading(first) + leading(second) + 4.dp
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                Line(
                    instance = instance,
                    time = time,
                    style = first,
                    clock = second,
                    backwards = backwards,
                )
                if (height >= two && instance.location.isNotBlank()) {
                    Text(
                        text = instance.location,
                        style = second,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = DOT + GAP),
                    )
                }
            }
        }
        if (handle) {
            val description = stringResource(R.string.calendars_event_resize)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(HANDLE)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

/**
 * An occurrence in the all-day band or a month or multiweek cell, standing
 * on the cell itself with no surface or outline of its own, and tinted in
 * the primary colour when [chosen]. The chip a drag carries is [raised]: on
 * the cards' own tone in an outline, dashed for a tentative occurrence, so
 * it reads over the entries it passes. On one line it reads dot,
 * title, marks and [time] when given; [stacked], as a timed occurrence within
 * a day is drawn in a month or multiweek cell, puts the time and marks on a
 * second line beneath the title. [lift] comes after the tap in the chain,
 * so a long press that lifts the chip takes its events before the tap can.
 */
@Composable
fun Chip(
    instance: Instance,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    time: String? = null,
    chosen: Boolean = false,
    raised: Boolean = false,
    lift: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = corners()
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = TINT)
    Box(
        modifier = modifier
            .then(
                when {
                    raised -> Modifier.panel(shape, dashed = instance.tentative, chosen = chosen)
                    chosen -> Modifier.clip(shape).background(tint)
                    else -> Modifier.clip(shape)
                },
            )
            .clickable(onClick = onClick)
            .then(lift),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (stacked) {
            val style = MaterialTheme.typography.labelSmall.packed()
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Title(instance, style)
                Detail(instance, time, style)
            }
        } else {
            Line(
                instance = instance,
                time = time,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/**
 * The line every view opens an occurrence with: its [Dot], its [Name], which
 * takes the width left over and is cut short with an ellipsis, then its [marks], then [time] in a muted [clock] style at the
 * far end, which is never cut. An all-day occurrence has no time, so its
 * marks sit at the far end instead. Each mark is [glyph] square.
 */
@Composable
fun Line(
    instance: Instance,
    time: String?,
    style: TextStyle,
    modifier: Modifier = Modifier,
    clock: TextStyle = style,
    gap: Dp = GAP,
    glyph: Dp = GLYPH,
    backwards: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        Dot(instance)
        Name(instance, style, Modifier.weight(1f))
        for (mark in marks(instance, backwards)) {
            Glyph(mark, glyph)
        }
        if (time != null) {
            Text(
                text = time,
                style = clock,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** A glyph an occurrence's line carries between its title and its time. */
enum class Mark(@param:StringRes val label: Int, val icon: () -> ImageVector) {
    /** The occurrence's event carries a reminder. */
    REMINDER(R.string.calendars_event_reminder, { Icons.Outlined.Notifications }),

    /** One of a series. */
    REPEAT(R.string.calendars_event_recurring, { Icons.Outlined.Repeat }),

    /** One of a series, changed apart from the rest. */
    EXCEPTION(R.string.calendars_event_exception, { Icons.Outlined.RepeatOne }),

    /** Its end reads before its start, which only happens across zones. */
    BACKWARDS(R.string.calendars_event_backwards, { Icons.Outlined.History }),
}

/**
 * The marks an occurrence carries, in the order they are drawn. On one line
 * they sit between the title and the time: the reminder bell, then the
 * repeat glyph, or the changed-occurrence one in its place, then the glyph
 * for a block that ends before it starts when [backwards]. [stacked], on the
 * second line after the time, the bell comes last instead.
 */
fun marks(instance: Instance, backwards: Boolean = false, stacked: Boolean = false): List<Mark> = buildList {
    if (instance.alarm && !stacked) add(Mark.REMINDER)
    when {
        instance.exception -> add(Mark.EXCEPTION)
        instance.recurring -> add(Mark.REPEAT)
    }
    if (backwards) add(Mark.BACKWARDS)
    if (instance.alarm && stacked) add(Mark.REMINDER)
}

/** A mark's glyph, muted, read out by its label. */
@Composable
private fun Glyph(mark: Mark, size: Dp) {
    Icon(
        imageVector = mark.icon(),
        contentDescription = stringResource(mark.label),
        modifier = Modifier.size(size),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The first of a stacked entry's lines: its [Dot] and its [Name], cut short with an ellipsis. */
@Composable
fun Title(instance: Instance, style: TextStyle, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Dot(instance)
        Name(instance, style, Modifier.weight(1f))
    }
}

/**
 * The second of a stacked entry's lines, muted and starting under the title
 * rather than the dot: [time] when there is one, then the marks, all from
 * the start of the line.
 */
@Composable
fun Detail(instance: Instance, time: String?, style: TextStyle, modifier: Modifier = Modifier, backwards: Boolean = false) {
    Row(
        modifier = modifier.padding(start = DOT + GAP),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        if (time != null) {
            Text(
                text = time,
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
        for (mark in marks(instance, backwards, stacked = true)) {
            Glyph(mark, GLYPH)
        }
    }
}

/** Two ends put together in the locale's own range wording, "09:00 to 10:00". */
@Composable
fun ranged(): (String, String) -> String {
    val resources = LocalResources.current
    return remember(resources) { { start, finish -> resources.getString(R.string.calendars_range, start, finish) } }
}

/**
 * The dot an occurrence opens with, in its colour: filled, or a ring for a
 * tentative one.
 */
@Composable
fun Dot(instance: Instance) {
    val colour = instance.colour.toColour(MaterialTheme.colorScheme.primary)
    Box(
        modifier = Modifier
            .size(DOT)
            .clip(CircleShape)
            .then(if (instance.tentative) Modifier.border(1.5.dp, colour, CircleShape) else Modifier.background(colour)),
    )
}

/**
 * An occurrence's title on one line, cut short with an ellipsis: "(No title)"
 * in a muted colour when it has none, and struck through when it has been
 * called off.
 */
@Composable
fun Name(instance: Instance, style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        text = if (instance.untitled) stringResource(R.string.calendars_untitled) else instance.summary,
        style = style,
        color = if (instance.untitled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textDecoration = if (instance.cancelled) TextDecoration.LineThrough else null,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * [style] with its lines packed closer than its own line height, so a short
 * block or a month cell fits two or three of them.
 */
fun TextStyle.packed(): TextStyle = if (fontSize.isSp) copy(lineHeight = fontSize * 1.15f) else this

/**
 * The corner every block and bar takes: the user's own radius, but no
 * rounder than [CORNER], which keeps a short block or a one-line bar from
 * turning into a pill.
 */
@Composable
fun corners(): Shape = RoundedCornerShape(minOf(LocalEntityRadius.current, CORNER))

/**
 * The neutral surface a timed block or a carried chip sits on: the cards'
 * own tone, with a thin outline, [dashed] for a tentative occurrence.
 * [chosen] lays the primary colour's tint over it and draws the outline in
 * the primary colour.
 */
@Composable
fun Modifier.panel(shape: Shape, dashed: Boolean = false, chosen: Boolean = false): Modifier {
    val primary = MaterialTheme.colorScheme.primary
    val outline = if (chosen) primary else MaterialTheme.colorScheme.outlineVariant
    return this
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .then(if (chosen) Modifier.background(primary.copy(alpha = TINT)) else Modifier)
        .then(if (dashed) Modifier.dashed(outline, shape) else Modifier.border(1.dp, outline, shape))
}

/**
 * A 1dp outline of [shape] in dashes. The stroke is drawn twice as wide on
 * the outline itself, and the shape's clip keeps its inner half.
 */
private fun Modifier.dashed(colour: Color, shape: Shape): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val stroke = Stroke(
        width = 2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
    )
    onDrawWithContent {
        drawContent()
        drawPath(path, colour, style = stroke)
    }
}

/** The height one line of [style] takes on this screen, at the reader's font scale. */
@Composable
internal fun leading(style: TextStyle): Dp = with(LocalDensity.current) {
    when {
        style.lineHeight.isSp -> style.lineHeight.toDp()
        style.fontSize.isSp -> (style.fontSize * 1.4f).toDp()
        else -> 16.sp.toDp()
    }
}

/** `#rrggbb` as a Compose colour, [fallback] when it will not parse. */
fun String.toColour(fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(this))
} catch (_: IllegalArgumentException) {
    fallback
} catch (_: StringIndexOutOfBoundsException) {
    fallback
}

/** The weekday's short name, in the language the screen is being read in. */
@Composable
fun weekdayLabel(day: LocalDate): String =
    day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, LocalConfiguration.current.locales[0])

/** The moment an occurrence's day starts, for the views' own arithmetic. */
fun Instance.moment(zone: ZoneId): java.time.ZonedDateTime =
    Instant.ofEpochSecond(start).atZone(zone)
