// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.mochios.android.i18n.LocalFormat
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
 * [days] columns. Non-working hours are shaded, today carries the current-time
 * line, and occurrences that overlap share the column's width.
 *
 * A tap on an occurrence opens its summary; a tap on empty grid starts an
 * event at that hour on that day. A long press lifts a block, which then
 * follows the finger to the quarter hour, on any of the days shown, and a
 * long press on the strip along its bottom edge drags its end instead; the
 * grid scrolls while the finger rests near its top or bottom. Letting go
 * somewhere else asks [onMove] to move the occurrence there.
 */
@Composable
fun TimeGrid(
    days: List<LocalDate>,
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    onOpen: (Instance) -> Unit,
    onCreate: (LocalDate, Int) -> Unit,
    onMove: (Instance, Long, Long) -> Unit,
) {
    val format = LocalFormat.current
    val scroll = rememberScrollState()
    val today = LocalDate.now(viewModel.timezone())
    val columns = LocalConfiguration.current.screenWidthDp.dp - GUTTER
    val width = columns / days.size.coerceAtLeast(1)

    val byDay = remember(state.instances, state.hidden, days, state.preferences.zones) {
        days.associateWith { day -> state.visible.filter { viewModel.covers(it, day) } }
    }

    // Opens on the working day rather than at midnight, as Thunderbird does.
    // The scroll is in pixels, so the hour rows are measured through the
    // density rather than taken as their own dp number.
    val density = LocalDensity.current
    LaunchedEffect(state.preferences.hours.start, days.firstOrNull()) {
        val hours = state.preferences.hours.start.coerceIn(0, 23)
        scroll.scrollTo(with(density) { (HOUR * hours).roundToPx() })
    }

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
            Spacer(Modifier.width(GUTTER))
            for (day in days) {
                DayHeading(day, today, width, Modifier.clickable { viewModel.open(day) })
            }
        }
        AllDayBand(days, byDay, width, onOpen)
        HorizontalDivider()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { viewport = it.rectInRoot() }
                .verticalScroll(scroll),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.width(GUTTER)) {
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
                for (day in days) {
                    DayColumn(
                        day = day,
                        today = today,
                        width = width,
                        instances = byDay[day].orEmpty().filterNot { it.allday },
                        state = state,
                        viewModel = viewModel,
                        lifted = lift?.instance,
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

/** A layout's bounds in the root's coordinates, unclipped by its parents. */
private fun LayoutCoordinates.rectInRoot(): Rect {
    val origin = positionInRoot()
    return Rect(origin.x, origin.y, origin.x + size.width, origin.y + size.height)
}

@Composable
private fun DayHeading(day: LocalDate, today: LocalDate, width: Dp, modifier: Modifier) {
    val current = day == today
    val locale = LocalConfiguration.current.locales[0]
    val pattern = remember(locale) { android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEd") }
    Box(
        modifier = modifier
            .width(width)
            .then(if (current) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = heading(day, pattern, locale),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            color = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
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

/** The band above the grid, holding the all-day and multi-day occurrences. */
@Composable
private fun AllDayBand(
    days: List<LocalDate>,
    byDay: Map<LocalDate, List<Instance>>,
    width: Dp,
    onOpen: (Instance) -> Unit,
) {
    val rows = days.maxOfOrNull { byDay[it].orEmpty().count { instance -> instance.allday } } ?: 0
    if (rows == 0) return
    Row(modifier = Modifier.fillMaxWidth().heightIn(max = BAND * 4)) {
        Spacer(Modifier.width(GUTTER))
        for (day in days) {
            Column(
                modifier = Modifier.width(width).padding(horizontal = 1.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (instance in byDay[day].orEmpty().filter { it.allday }.take(4)) {
                    Chip(instance, Modifier.height(BAND).fillMaxWidth()) { onOpen(instance) }
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
    instances: List<Instance>,
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    lifted: Instance?,
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
            val own = lifted != null && lifted.event == placed.instance.event && lifted.start == placed.instance.start
            Box(
                modifier = Modifier
                    .offset(x = columnWidth * placed.column, y = HOUR * placed.top)
                    .width(columnWidth)
                    .height((HOUR * placed.height).coerceAtLeast(18.dp))
                    .padding(end = 2.dp)
                    .alpha(if (own) 0.4f else 1f),
            ) {
                Block(
                    instance = placed.instance,
                    backwards = placed.backwards,
                    zones = zones,
                    handle = placed.instance.editable && !placed.backwards,
                    modifier = Modifier.lift(placed, onLift, onDrag, onDrop, onCancel) { onOpen(placed.instance) },
                )
            }
        }
        if (drop != null && lifted != null) {
            val startZone = clockZone(lifted.zone?.start, zones)
            val finishZone = clockZone(lifted.zone?.finish, zones)
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
                    span = format.formatTime(drop.start, startZone) + " – " + format.formatTime(drop.finish, finishZone),
                    modifier = Modifier.shadow(6.dp, RoundedCornerShape(6.dp)),
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
 * A timed occurrence's block: the calendar's colour, faded, with a solid
 * leading edge. Its clock reads in the start's own zone when [zones] is on,
 * or says [span] when given, which a lifted block uses for where it would
 * land. A [backwards] block stands in for an occurrence whose end reads
 * before its start, and says so with a glyph. With [handle] on, a strip
 * along the bottom edge marks where a long press takes the end.
 */
@Composable
fun Block(
    instance: Instance,
    backwards: Boolean = false,
    zones: Boolean = false,
    span: String? = null,
    handle: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val format = LocalFormat.current
    val colour = instance.colour.toColour(MaterialTheme.colorScheme.primary)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .background(colour.copy(alpha = 0.22f)),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(3.dp).fillMaxSize().background(colour))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text(
                    text = instance.summary.ifBlank { stringResource(R.string.calendars_event_untitled) },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = span ?: format.formatTime(instance.start, clockZone(instance.zone?.start, zones)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (backwards) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = stringResource(R.string.calendars_event_backwards),
                    modifier = Modifier.padding(top = 2.dp, end = 3.dp).size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        .background(colour.copy(alpha = 0.6f)),
                )
            }
        }
    }
}

/**
 * An all-day or month-grid chip: one line in the calendar's colour. [lift]
 * comes after the tap in the chain, so a long press that lifts the chip
 * takes its events before the tap can.
 */
@Composable
fun Chip(instance: Instance, modifier: Modifier = Modifier, lift: Modifier = Modifier, onClick: () -> Unit) {
    val colour = instance.colour.toColour(MaterialTheme.colorScheme.primary)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colour.copy(alpha = 0.22f))
            .clickable(onClick = onClick)
            .then(lift),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxSize().background(colour))
        Text(
            text = instance.summary.ifBlank { stringResource(R.string.calendars_event_untitled) },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
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
