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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.theme.LocalEntityRadius
import org.mochios.calendars.R
import org.mochios.calendars.model.Instance

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

/** How close to the columns' side a drag turns to the previous or next range. */
private val SIDE = 28.dp

/** One row of the all-day band: a bar and the gap beneath it. */
private val ROW = BAND + 2.dp

/** How many rows of the band show before it scrolls. */
private const val ROWS = 4.5f

/** The shortest a new event made by a drag across empty grid can be, in hours. */
private const val LEAST = SNAP

/** What a long press on the grid took hold of. */
private sealed interface Drag {
    /** Empty grid on [day], from [anchor] hours past midnight to wherever the finger now is. */
    data class Create(val day: LocalDate, val anchor: Float) : Drag

    /** An empty stretch of the band, a run of days from [anchor] to the column the finger is over. */
    data class Pick(val anchor: LocalDate) : Drag

    /**
     * An occurrence, taken by its piece on [day]: a block lifted from that
     * day's column at [cut], [grab] hours below the block's top, or by its end
     * handle when [resize]; or, with [band], a bar in the all-day band.
     */
    data class Lift(
        val instance: Instance,
        val day: LocalDate,
        val cut: Cut,
        val grab: Float,
        val resize: Boolean,
        val band: Boolean,
    ) : Drag
}

/** Where a lifted occurrence would land. */
private sealed interface Landing {
    /** A block on [day] at [cut], the occurrence running from [start] to [finish], epoch seconds. */
    data class Timed(val day: LocalDate, val cut: Cut, val start: Long, val finish: Long) : Landing

    /** A bar in the band from [first], [length] days long. */
    data class Whole(val first: LocalDate, val length: Long) : Landing
}

/** Where a drag in the day and week views put an occurrence, for [CalendarViewModel.move]. */
sealed interface Moved {
    /** Still timed, now from [start] to [finish], epoch seconds. */
    data class Time(val start: Long, val finish: Long) : Moved

    /** An all-day bar moved within the band, its first day now [first]. */
    data class Days(val first: LocalDate) : Moved

    /** A block dropped in the band: all day from [first], over as many days as it covered. */
    data class Whole(val first: LocalDate) : Moved

    /** A bar dropped in the grid: timed on [day] from [hours] past midnight, as long as a new event. */
    data class Timed(val day: LocalDate, val hours: Float) : Moved
}

/** The key the landing bar is laid out under among the band's own. */
private const val LANDING = "landing"

/**
 * The day and week views: an all-day band above a scrolling time grid of
 * [days] columns. Non-working hours are shaded, today carries the
 * current-time line, which moves on with the clock, and occurrences that
 * overlap share the column's width. A single column draws each block on one
 * line; the week's narrow columns stack the time and marks beneath the
 * title. The band is always there; an all-day occurrence is one bar across
 * every day it covers, and the band scrolls once it holds more rows than it
 * shows.
 *
 * A tap on an occurrence opens its summary; a tap on empty grid starts an
 * event at that quarter hour, as long as a new event is. A long press on
 * empty grid and a drag marks out a new event's span. A tap on an empty
 * stretch of the band asks [onCreateRange] for an all-day event on that
 * day, and a long press there and a drag along the band for one over the
 * run of days it takes in. A long press lifts a
 * block, which then follows the finger to the quarter hour on any of the
 * days shown, or into the band to make it all day; a long press on the strip
 * along its bottom edge drags its end instead. A bar lifted from the band
 * moves by days, or drops into the grid as a timed event. The grid scrolls
 * while the finger rests near its top or bottom, and turns to the previous
 * or next range, by [onStep], while a lifted occurrence rests at its side;
 * [onLifted] hears when a drag starts and when it ends, so a pager can hold
 * the page under it while it turns.
 * Letting go asks [onMove] to move the occurrence there, or [onCreate] to
 * start an event over the span, with no finish for a tap or a press that
 * marked nothing out. The occurrence whose summary is open, [selected], is
 * drawn in the primary colour's tint. [clock] is the time now, epoch seconds.
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
    onCreate: (Long, Long?) -> Unit,
    onMove: (Instance, Moved) -> Unit,
    scroll: ScrollState,
    onCreateRange: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    stacked: Boolean = false,
    onStep: (Int) -> Unit = {},
    onLifted: (Boolean) -> Unit = {},
    selected: Instance? = null,
    onTop: (Dp) -> Unit = {},
    clock: () -> Long = { Instant.now().epochSecond },
) {
    val band = rememberScrollState()
    val user = viewModel.timezone()
    val zones = viewModel.zones()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The current-time line, and which occurrences are over, only have to be
    // right to the minute.
    val now by produceState(clock()) {
        while (true) {
            delay(30_000)
            value = clock()
        }
    }
    val today = Instant.ofEpochSecond(now).atZone(user).toLocalDate()
    val columns = LocalConfiguration.current.screenWidthDp.dp - GUTTER
    val width = columns / days.size.coerceAtLeast(1)
    val length = maxOf(15, state.preferences.duration)

    val byDay = remember(state.instances, state.hidden, days, state.preferences.zones) {
        days.associateWith { day -> state.visible.filter { !it.allday && viewModel.covers(it, day) } }
    }
    val layouts = remember(byDay) { byDay.mapValues { (day, instances) -> lay(instances, viewModel, day) } }
    val wholes = remember(state.instances, state.hidden, days, state.preferences.zones) {
        state.visible.filter { it.allday && days.any { day -> viewModel.covers(it, day) } }
    }

    val density = LocalDensity.current

    // What a long press took, the finger and the grid's geometry, all in the
    // root's coordinates: the finger stays put while the grid scrolls under
    // it, so where a drag would land is read from the finger and the scroll
    // rather than from where the finger last moved. The places are the
    // containers, which stay where they are when the page turns; a day's
    // column and an occurrence under the finger are worked out from them.
    var drag by remember { mutableStateOf<Drag?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf<Rect?>(null) }
    var viewport by remember { mutableStateOf<Rect?>(null) }
    var window by remember { mutableStateOf<Rect?>(null) }
    var strip by remember { mutableStateOf<Rect?>(null) }
    val hourPx = with(density) { HOUR.toPx() }
    val rowPx = with(density) { ROW.toPx() }
    val handlePx = with(density) { HANDLE.toPx() }
    val leastPx = with(density) { 18.dp.toPx() }

    /** The column under [x], by its place among the days, or null off the columns. */
    fun column(x: Float): Int? {
        val row = strip ?: return null
        val across = if (rtl) row.right - x else x - row.left
        if (across < 0 || across >= row.width) return null
        return (across / (row.width / days.size)).toInt().coerceIn(0, days.size - 1)
    }

    /** The column nearest [x], the first or last when the finger is off to a side. */
    fun nearest(x: Float): LocalDate {
        val row = strip ?: return days.first()
        val across = if (rtl) row.right - x else x - row.left
        return days[(across / (row.width / days.size)).toInt().coerceIn(0, days.size - 1)]
    }

    /** Hours past midnight at a height [y] in the root, through the scroll. */
    fun hours(y: Float): Float = (y - (viewport?.top ?: 0f) + scroll.value) / hourPx

    fun landing(lift: Drag.Lift): Landing? {
        val grid = viewport ?: return null
        val day = if (lift.resize) lift.day else nearest(finger.x)
        if (!lift.resize && finger.y < grid.top) {
            val first = viewModel.day(lift.instance)
            val moved = first.plusDays(ChronoUnit.DAYS.between(lift.day, day))
            // A bar that has not left its day goes nowhere.
            if (lift.band && moved == first) return null
            return Landing.Whole(moved, ChronoUnit.DAYS.between(first, viewModel.finish(lift.instance)) + 1)
        }
        if (lift.band) {
            val cut = moved(Cut(0f, length / 60f), hours(finger.y))
            val start = at(day, cut.from, user)
            return Landing.Timed(day, cut, start, start + length * 60L)
        }
        val content = hours(finger.y)
        val cut = if (lift.resize) resized(lift.cut, content) else moved(lift.cut, content - lift.grab - lift.cut.from)
        val startZone = if (zones) zoneOf(lift.instance.zone?.start, user) else user
        val finishZone = if (zones) zoneOf(lift.instance.zone?.finish, user) else user
        val (start, finish) = dropped(lift.instance, lift.day, lift.cut, day, cut, startZone, finishZone, lift.resize)
        return Landing.Timed(day, cut, start, finish)
    }

    /** A new event's span while it is marked out, hours past midnight, never shorter than a quarter hour. */
    fun span(create: Drag.Create): Cut {
        val to = snap(hours(finger.y)).coerceIn(0f, 24f)
        val from = minOf(create.anchor, to)
        return Cut(from, maxOf(create.anchor, to).coerceAtLeast(from + LEAST).coerceAtMost(24f))
    }

    val lifted = drag as? Drag.Lift
    val target = lifted?.let { landing(it) }
    val bars = remember(wholes, days, target, lifted) {
        val spans = wholes.map { Span<Instance?>(it, viewModel.day(it), viewModel.finish(it)) } +
            listOfNotNull((target as? Landing.Whole)?.let { Span<Instance?>(null, it.first, it.first.plusDays(it.length - 1)) })
        rows(days, spans)
    }

    /**
     * What a long press at [at] takes: a bar in the band, an empty stretch of
     * the band, a block or its end handle in a column, or empty grid in a
     * column. Nothing for the headings, the gutter, or an occurrence that
     * cannot be moved.
     */
    fun take(at: Offset): Drag? {
        val grid = viewport ?: return null
        window?.takeIf { it.contains(at) }?.let { shown ->
            val index = column(at.x) ?: return null
            val row = ((at.y - shown.top + band.value) / rowPx).toInt()
            val bar = bars.firstOrNull { it.row == row && index >= it.column && index < it.column + it.span }
                ?: return Drag.Pick(days[index])
            val instance = bar.item?.takeIf { it.editable } ?: return null
            return Drag.Lift(instance, days[index], Cut(0f, 0f), 0f, resize = false, band = true)
        }
        if (!grid.contains(at)) return null
        val index = column(at.x) ?: return null
        val day = days[index]
        val content = hours(at.y)
        val row = strip ?: return null
        val slice = row.width / days.size
        val across = ((if (rtl) row.right - at.x else at.x - row.left) - slice * index) / slice
        val placed = layouts[day].orEmpty().firstOrNull { placed ->
            val tall = maxOf(placed.height, leastPx / hourPx)
            across >= placed.column.toFloat() / placed.columns && across < (placed.column + 1f) / placed.columns &&
                content >= placed.top && content < placed.top + tall
        }
        if (placed != null) {
            if (!placed.instance.editable) return null
            val bottom = (placed.top + maxOf(placed.height, leastPx / hourPx)) * hourPx
            val resize = !placed.backwards && content * hourPx >= bottom - handlePx
            val cut = Cut(placed.top, placed.top + placed.height, placed.backwards)
            return Drag.Lift(placed.instance, day, cut, content - placed.top, resize, band = false)
        }
        return Drag.Create(day, snap(content).coerceIn(0f, 24f - LEAST))
    }

    fun drop() {
        val taken = drag
        drag = null
        when (taken) {
            is Drag.Create -> {
                val cut = span(taken)
                val start = at(taken.day, cut.from, user)
                // A press that marked nothing out is a tap: the default length.
                val marked = snap(hours(finger.y)).coerceIn(0f, 24f) != taken.anchor
                onCreate(start, if (marked) at(taken.day, cut.to, user) else null)
            }
            is Drag.Pick -> {
                val (first, last) = ends(taken.anchor, nearest(finger.x))
                onCreateRange(first, last)
            }
            is Drag.Lift -> when (val landed = landing(taken)) {
                is Landing.Whole -> onMove(
                    taken.instance,
                    if (taken.band) Moved.Days(landed.first) else Moved.Whole(landed.first),
                )
                is Landing.Timed -> when {
                    taken.band -> onMove(taken.instance, Moved.Timed(landed.day, landed.cut.from))
                    landed.start != taken.instance.start || landed.finish != taken.instance.finish ->
                        onMove(taken.instance, Moved.Time(landed.start, landed.finish))
                }
                null -> {}
            }
            null -> {}
        }
    }

    // The gesture outlives a composition, so it reaches what this one built
    // through a holder set afresh after each. Not through remembered state:
    // a reference to a local function equals the last composition's, so the
    // state would keep the first and its days long after the page turned.
    val hands = remember { Hands() }
    SideEffect {
        hands.take = ::take
        hands.drop = ::drop
        hands.step = onStep
    }
    val haptic = LocalHapticFeedback.current
    val hearing by rememberUpdatedState(onLifted)
    LaunchedEffect(drag != null) {
        hearing(drag != null)
    }

    // The grid scrolls while the finger rests near its top or bottom edge,
    // faster the nearer it is, and turns the page while a lifted occurrence
    // rests at the columns' side, once and then again each hold.
    LaunchedEffect(drag != null) {
        if (drag == null) return@LaunchedEffect
        val near = with(density) { EDGE.toPx() }
        val speed = with(density) { SPEED.toPx() }
        val page = with(density) { SIDE.toPx() }
        val dwell = Dwell()
        while (true) {
            val time = withFrameMillis { it }
            val grid = viewport ?: continue
            val current = drag ?: break
            // A run of days is picked along the band alone, which neither
            // scrolls the hours nor turns the page.
            if (current is Drag.Pick) continue
            val fromTop = finger.y - grid.top
            val fromBottom = grid.bottom - finger.y
            // Above the grid is the band, which a lifted block is dropped
            // into rather than scrolled towards.
            val banded = current is Drag.Lift && !current.resize
            when {
                fromTop < near && (fromTop >= 0 || !banded) ->
                    scroll.scrollBy(-speed * ((near - fromTop) / near).coerceIn(0f, 1f))
                fromBottom < near -> scroll.scrollBy(speed * ((near - fromBottom) / near).coerceIn(0f, 1f))
            }
            val row = strip
            val over = area?.contains(finger) == true
            val direction = if (banded && over && row != null) {
                edge(finger.x, if (rtl) row.right else row.left, if (rtl) row.left else row.right, page)
            } else {
                0
            }
            val turn = dwell.step(direction, time)
            if (turn != 0) hands.step(turn)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("grid")
            .onGloballyPositioned { area = it.rectInRoot() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (!held(down)) return@awaitEachGesture
                    val origin = area?.topLeft ?: Offset.Zero
                    val at = origin + down.position
                    val taken = hands.take(at) ?: return@awaitEachGesture
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    finger = at
                    drag = taken
                    try {
                        // Everything the finger does now is the drag's, so
                        // neither the scroll nor a tap beneath it acts.
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            // The system taking the finger away ends the
                            // gesture with an up that arrives already
                            // consumed, which lands and makes nothing.
                            val cancelled = change.isConsumed
                            finger = origin + change.position
                            change.consume()
                            if (change.changedToUpIgnoreConsumed()) {
                                if (!cancelled) hands.drop()
                                break
                            }
                        }
                    } finally {
                        drag = null
                    }
                }
            },
    ) {
        // Every view heads its columns, the day view too: the toolbar names
        // only the month.
        Row(modifier = Modifier.fillMaxWidth()) {
            for (day in days) {
                val opens = if (days.size > 1) Modifier.clickable { viewModel.open(day) } else Modifier
                DayHeading(day, today, width, stacked, opens)
            }
        }
        AllDayBand(
            days = days,
            bars = bars,
            width = width,
            scroll = band,
            lifted = lifted,
            landing = target as? Landing.Whole,
            picked = (drag as? Drag.Pick)?.let { ends(it.anchor, nearest(finger.x)) },
            selected = selected,
            over = { past(it, viewModel.finish(it), now, today) },
            onOpen = onOpen,
            onDay = { day -> onCreateRange(day, day) },
            onPlaced = { window = it },
        )
        HorizontalDivider()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("hours")
                .onGloballyPositioned { coordinates ->
                    viewport = coordinates.rectInRoot()
                    onTop(with(density) { coordinates.positionInParent().y.toDp() })
                }
                .verticalScroll(scroll),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .columnLines(days.size, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    .onGloballyPositioned { strip = it.rectInRoot() },
            ) {
                for (day in days) {
                    DayColumn(
                        day = day,
                        today = today,
                        now = now,
                        width = width,
                        stacked = days.size > 1,
                        layout = layouts[day].orEmpty(),
                        state = state,
                        viewModel = viewModel,
                        lifted = lifted?.instance,
                        selected = selected,
                        drop = (target as? Landing.Timed)?.takeIf { it.day == day },
                        creating = (drag as? Drag.Create)?.takeIf { it.day == day }?.let { span(it) },
                        onOpen = onOpen,
                        onTap = { hours -> onCreate(at(day, hours, user), null) },
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
 * with the hours they name. Above them, beside the all-day band, "All day",
 * under [zone], the zone the hours read in, when events show in their own
 * zones. Midnight goes unlabelled: the top of the day says it.
 */
@Composable
fun HourGutter(top: Dp, scroll: ScrollState, modifier: Modifier = Modifier, zone: String? = null) {
    val format = LocalFormat.current
    Column(modifier = modifier.width(GUTTER).fillMaxHeight()) {
        Box(
            modifier = Modifier
                .height((top - DividerDefaults.Thickness).coerceAtLeast(0.dp))
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                if (zone != null) {
                    Text(
                        text = zone,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("gutter-zone"),
                    )
                }
                Text(
                    text = stringResource(R.string.calendars_event_allday),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
        Column(modifier = Modifier.weight(1f).verticalScroll(scroll)) {
            for (hour in 0 until 24) {
                Box(modifier = Modifier.height(HOUR).fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                    if (hour > 0) {
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

/**
 * A one-pixel line in [colour] between each of [count] equal columns, behind
 * what they draw, so the days of a grid read apart.
 */
internal fun Modifier.columnLines(count: Int, colour: Color): Modifier = drawBehind {
    for (column in 1 until count) {
        val x = size.width * column / count
        drawLine(colour, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
    }
}

/** What the grid's gesture calls on, from the latest composition. */
private class Hands {
    var take: (Offset) -> Drag? = { null }
    var drop: () -> Unit = {}
    var step: (Int) -> Unit = {}
}

/**
 * Waits out a long press: true once the finger has rested, within the touch
 * slop and with nothing beneath taking its moves, for the long-press time;
 * false when it lifts, moves off or a scroll takes it first.
 */
private suspend fun AwaitPointerEventScope.held(down: PointerInputChange): Boolean {
    val slop = viewConfiguration.touchSlop
    val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
            // The press itself, which a tap target beneath has taken.
            if (change.changedToDownIgnoreConsumed()) continue
            if (change.changedToUpIgnoreConsumed() || change.isConsumed) return@withTimeoutOrNull true
            if ((change.position - down.position).getDistance() > slop) return@withTimeoutOrNull true
        }
        @Suppress("UNREACHABLE_CODE")
        true
    }
    return ended == null
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
 * The band above the grid, labelled by [HourGutter] beside the pager: the
 * all-day occurrences laid out as [bars], each one bar across the days it covers. A bar laid out with no
 * occurrence is where the [lifted] one would land, drawn raised; the bar it
 * was lifted from fades once it is going somewhere. The run of days being
 * [picked] is tinted across the band, and a tap on an empty stretch of it
 * is [onDay]'s, for the day under it. The band holds at least one row and
 * shows up to [ROWS] before it scrolls by [scroll].
 */
@Composable
private fun AllDayBand(
    days: List<LocalDate>,
    bars: List<Laid<Instance?>>,
    width: Dp,
    scroll: ScrollState,
    lifted: Drag.Lift?,
    landing: Landing.Whole?,
    picked: Pair<LocalDate, LocalDate>?,
    selected: Instance?,
    over: (Instance) -> Boolean,
    onOpen: (Instance) -> Unit,
    onDay: (LocalDate) -> Unit,
    onPlaced: (Rect) -> Unit,
) {
    val rows = (bars.maxOfOrNull { it.row } ?: -1) + 1
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val tapped by rememberUpdatedState(onDay)
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(width * days.size)
                .heightIn(max = ROW * ROWS + 4.dp)
                .testTag("band")
                .onGloballyPositioned { onPlaced(it.rectInRoot()) }
                .verticalScroll(scroll),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROW * maxOf(1, rows) + 4.dp)
                    .pointerInput(days, rtl) {
                        // A bar's own tap takes the press, so this hears
                        // only taps on the band between them.
                        detectTapGestures { at ->
                            val across = if (rtl) size.width - at.x else at.x
                            val index = (across / (size.width.toFloat() / days.size)).toInt().coerceIn(0, days.size - 1)
                            tapped(days[index])
                        }
                    },
            ) {
                picked?.let { (first, last) ->
                    val from = days.indexOf(first)
                    val to = days.indexOf(last)
                    if (from >= 0 && to >= from) {
                        Box(
                            modifier = Modifier
                                .offset(x = width * from)
                                .width(width * (to - from + 1))
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .testTag("picked"),
                        )
                    }
                }
                for (bar in bars) {
                    val instance = bar.item ?: lifted?.instance ?: continue
                    val raised = bar.item == null
                    key(instance.event, instance.start, raised) {
                        Box(
                            modifier = Modifier
                                .offset(x = width * bar.column, y = ROW * bar.row + 2.dp)
                                .width(width * bar.span - 2.dp)
                                .height(BAND)
                                .zIndex(if (raised) 1f else 0f),
                        ) {
                            if (raised) {
                                Chip(instance, Modifier.fillMaxSize().shadow(6.dp, corners()), raised = true) {}
                            } else {
                                val carried = landing != null && lifted != null && same(instance, lifted.instance)
                                Chip(
                                    instance,
                                    Modifier
                                        .fillMaxSize()
                                        .alpha(opacity(carried = carried, over = over(instance), cancelled = instance.cancelled)),
                                    chosen = same(instance, selected),
                                ) { onOpen(instance) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: LocalDate,
    today: LocalDate,
    now: Long,
    width: Dp,
    stacked: Boolean,
    layout: List<Placed>,
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    lifted: Instance?,
    selected: Instance?,
    drop: Landing.Timed?,
    creating: Cut?,
    onOpen: (Instance) -> Unit,
    onTap: (Float) -> Unit,
) {
    val format = LocalFormat.current
    val shading = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val line = MaterialTheme.colorScheme.error
    val working = state.preferences.days.contains(day.dayOfWeek.value % 7)
    val zones = viewModel.zones()
    val hourPx = with(LocalDensity.current) { HOUR.toPx() }
    val tap by rememberUpdatedState(onTap)

    Box(
        modifier = Modifier
            .width(width)
            .height(HOUR * 24)
            .pointerInput(Unit) {
                // A tap on empty grid starts an event at that quarter hour;
                // a tap on a block is the block's own.
                detectTapGestures(onTap = { at -> tap(snap(at.y / hourPx).coerceIn(0f, 24f - SNAP)) })
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (hour in 0 until 24) {
                val shaded = !working || hour < state.preferences.hours.start || hour >= state.preferences.hours.finish
                Box(
                    modifier = Modifier
                        .height(HOUR)
                        .fillMaxWidth()
                        .background(if (shaded) shading else Color.Transparent),
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
                    .alpha(
                        opacity(
                            carried = own,
                            over = past(placed.instance, viewModel.finish(placed.instance), now, today),
                            cancelled = placed.instance.cancelled,
                        ),
                    ),
            ) {
                Block(
                    instance = placed.instance,
                    backwards = placed.backwards,
                    span = interval(placed.instance, zones, format::formatTime, ranged()),
                    handle = placed.instance.editable && !placed.backwards,
                    stacked = stacked,
                    chosen = same(placed.instance, selected),
                    modifier = Modifier.clickable { onOpen(placed.instance) },
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
                // A bar dropped into the grid reads in the user's own clock,
                // having no zones of its own.
                val moved = lifted.copy(start = drop.start, finish = drop.finish, allday = false, date = null)
                Block(
                    instance = lifted,
                    span = interval(moved, zones && !lifted.allday, format::formatTime, ranged()),
                    stacked = stacked,
                    modifier = Modifier.shadow(6.dp, corners()),
                )
            }
        }
        if (creating != null) {
            Box(
                modifier = Modifier
                    .offset(y = HOUR * creating.from)
                    .fillMaxWidth()
                    .height(HOUR * (creating.to - creating.from))
                    .padding(horizontal = 4.dp)
                    .clip(corners())
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), corners())
                    .zIndex(1f)
                    .testTag("creating"),
            )
        }
        if (day == today) {
            val time = Instant.ofEpochSecond(now).atZone(viewModel.timezone())
            val fraction = time.hour + time.minute / 60f
            Box(
                modifier = Modifier
                    .offset(y = HOUR * fraction)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(line)
                    .zIndex(2f)
                    .testTag("now"),
            )
            // The dot at the line's start, half over the column's edge.
            Box(
                modifier = Modifier
                    .offset(x = (-4).dp, y = HOUR * fraction - 3.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(line)
                    .zIndex(2f)
                    .testTag("now-dot"),
            )
        }
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
 * A timed occurrence's block, as the web's week view draws one: the cards'
 * own neutral tone in a thin outline, dashed for a tentative occurrence, its
 * words in the usual text colours and its colour in its [Dot], and tinted
 * and outlined in the primary colour when [chosen]. It writes its exact
 * time, [span], "09:00 to
 * 10:00", so a few minutes either side of the hour are not lost to the
 * grid; a lifted block's [span] is where it would land. A
 * [backwards] block stands in for an occurrence whose end reads before its
 * start, and in the day view says so with a mark. With [handle] on, a strip
 * along the bottom edge, where a long press takes the end, is named for
 * screen readers, and in the day view a short bar marks it when the block's
 * words leave the strip clear; a short block, and the week view's narrow
 * ones, draw no bar, so it never sits on the title.
 *
 * As the day view draws it, the block opens with its dot, its time and its
 * marks, the repeat glyph and the reminder bell, on a line of their own,
 * then its title in a larger type beneath, and the location under that when
 * the block is tall enough. A block too short for the time and the title on
 * lines of their own reads dot, title, marks and time on one line. The
 * title's size goes by the block's height alone: at full size it wraps onto
 * as many lines as the block holds, up to three, one fewer when the location
 * takes the last; a block too short for one full line shrinks it to fit. A
 * block with room to spare keeps a little more of it at its start and top.
 *
 * [stacked], as the week view's narrow columns draw it, the block puts its
 * dot, time and marks on the first line and its title beneath in a small
 * type, wrapping onto as many lines as it holds; a block too short for both
 * reads its dot and its title.
 */
@Composable
fun Block(
    instance: Instance,
    backwards: Boolean = false,
    span: String? = null,
    handle: Boolean = false,
    stacked: Boolean = false,
    chosen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val card = modifier.fillMaxSize().panel(corners(), dashed = instance.tentative, chosen = chosen)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    BoxWithConstraints(modifier = card) {
        val height = maxHeight
        val density = LocalDensity.current
        var filled by remember { mutableStateOf<Dp?>(null) }
        if (stacked) {
            val small = MaterialTheme.typography.labelSmall
            val size = if (small.fontSize.isSp) small.fontSize else 11.sp
            val line = with(density) { (size * LINE).toDp() }
            // The time line takes a line of its own only when the title
            // still has one beneath it; a shorter block reads dot and title.
            val pair = span != null && height - 3.dp >= line * 2
            val lines = ((height - 3.dp) / line).toInt() - if (pair) 1 else 0
            Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 1.dp)) {
                if (pair) {
                    Detail(instance, span, small.packed(), backwards = backwards)
                    Fitted(instance, small, lines.coerceAtLeast(1))
                } else {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(GAP),
                    ) {
                        Box(modifier = Modifier.padding(top = ((line - DOT) / 2).coerceAtLeast(0.dp))) {
                            Dot(instance)
                        }
                        Fitted(instance, small, lines.coerceAtLeast(1), Modifier.weight(1f))
                    }
                }
            }
        } else {
            val first = MaterialTheme.typography.titleMedium
            val second = MaterialTheme.typography.labelSmall
            val largest = if (first.fontSize.isSp) first.fontSize else 16.sp
            val line = with(LocalDensity.current) { (largest * LINE).toDp() }
            val roomy = height >= line + ROOMY + 2.dp
            val wide = maxWidth >= WIDE
            val top = if (roomy) ROOMY else 2.dp
            val room = height - top - 2.dp
            val smallest = if (second.fontSize.isSp) second.fontSize else 11.sp
            val clock = with(LocalDensity.current) { (smallest * LINE).toDp() }
            val below = span != null && room >= line + clock
            val lines = ((if (below) room - clock else room) / line).toInt()
            val located = instance.location.isNotBlank() && lines >= 2
            val titled = (if (located) lines - 1 else lines).coerceIn(1, LINES)
            val size = if (lines >= 1) {
                largest
            } else {
                with(LocalDensity.current) { maxOf((room / LINE).toSp().value, SMALLEST.value).sp }
            }
            val leading = with(LocalDensity.current) { (size * LINE).toDp() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size -> filled = with(density) { size.height.toDp() } }
                    .padding(
                        start = if (wide) START else 4.dp,
                        end = 4.dp,
                        top = top,
                        bottom = 2.dp,
                    ),
            ) {
                if (span != null && below) {
                    Detail(instance, span, second, backwards = backwards)
                }
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(GAP),
                ) {
                    if (!below) {
                        Box(modifier = Modifier.padding(top = ((leading - DOT) / 2).coerceAtLeast(0.dp))) {
                            Dot(instance)
                        }
                    }
                    Fitted(instance, first.copy(fontSize = size), titled, Modifier.weight(1f))
                    if (!below) {
                        for (mark in marks(instance, backwards)) {
                            Glyph(mark, 16.dp)
                        }
                        if (span != null) {
                            Landing(span, second, Modifier.padding(top = 2.dp))
                        }
                    }
                }
                if (located) {
                    Text(
                        text = instance.location,
                        style = second,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                val used = filled
                if (!stacked && used != null && height - used >= HANDLE) {
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(muted),
                    )
                }
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
 * a day is drawn in a month or multiweek cell, puts the dot, the time and the
 * marks on a line of their own and the title beneath them, with the line's
 * whole width. [lift] comes after the tap in the chain, so a long press that
 * lifts the chip takes its events before the tap can.
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
                Detail(instance, time, style)
                Name(instance, style, Modifier.fillMaxWidth())
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
internal fun Glyph(mark: Mark, size: Dp) {
    Icon(
        imageVector = mark.icon(),
        contentDescription = stringResource(mark.label),
        modifier = Modifier.size(size),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The line of a two-line entry that is not its title, muted: its [Dot],
 * [time] when there is one, then the marks, all from the start of the line,
 * the dot as far from the time as a one-line entry's is from its title.
 */
@Composable
fun Detail(instance: Instance, time: String?, style: TextStyle, modifier: Modifier = Modifier, backwards: Boolean = false) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Dot(instance)
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
 * An occurrence's title as [Name] writes it, wrapping onto as many as
 * [lines] lines at [style]'s size and cut short with an ellipsis after the
 * last. Its line height is [LINE] times its size, as the block sizes it by.
 */
@Composable
internal fun Fitted(
    instance: Instance,
    style: TextStyle,
    lines: Int,
    modifier: Modifier = Modifier,
) {
    val colour = if (instance.untitled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = if (instance.untitled) stringResource(R.string.calendars_untitled) else instance.summary,
        style = style.copy(lineHeight = LINE.em),
        color = colour,
        textDecoration = if (instance.cancelled) TextDecoration.LineThrough else null,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** A block's time, "10:00 to 11:00", or where a lifted one would land, never cut. */
@Composable
private fun Landing(span: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        text = span,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

/** A day card title's line height, as a multiple of its size. */
private const val LINE = 1.2f

/** The most lines a day card's title wraps onto. */
private const val LINES = 3

/** The smallest a day card's title shrinks to on a block too short for one full line. */
private val SMALLEST = 8.sp

/** The room a day card keeps at its top when it has the space. */
private val ROOMY = 6.dp

/** The room a day card keeps at its start when it has the space. */
private val START = 8.dp

/** How wide a day card must be to keep [START] at its start. */
private val WIDE = 96.dp

/**
 * [style] with its lines packed closer than its own line height, so a short
 * block or a month cell fits two or three of them.
 */
fun TextStyle.packed(): TextStyle = if (fontSize.isSp) copy(lineHeight = fontSize * 1.15f) else this

/**
 * The corner every block and bar takes: the user's own radius, but no
 * rounder than [most], [CORNER] by default, which keeps a short block or a
 * one-line bar from turning into a pill.
 */
@Composable
fun corners(most: Dp = CORNER): Shape = RoundedCornerShape(minOf(LocalEntityRadius.current, most))

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
