// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.MochiBottomSheet
import org.mochios.calendars.R
import org.mochios.calendars.model.Instance
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** How many chips a cell shows before it collapses the rest into "+N more". */
private const val CHIPS = 3

/**
 * A chip lifted by a long press: which occurrence, the day of the cell it
 * was lifted from, where in the chip the finger took it, and the chip's
 * width in pixels, which the chip carried under the finger keeps.
 */
private data class Hold(val instance: Instance, val day: LocalDate, val grab: Offset, val width: Float)

/**
 * The month and multiweek views: [weeks] rows of seven days, each cell
 * holding its occurrences as chips. In the month view days outside [month]
 * are dimmed but drawn; in the multiweek view [month] is null and every day
 * reads the same.
 *
 * A tap on a chip opens its summary, a tap on empty cell space starts an
 * event on that day, and the day number opens the day view. A long press
 * lifts a chip, which then follows the finger from cell to cell; letting go
 * on another day asks [onMove] to move the occurrence so that its first day
 * moves by as many days.
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
) {
    val today = LocalDate.now(viewModel.timezone())
    var listing by remember { mutableStateOf<LocalDate?>(null) }
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
                            outside = month != null && day.monthValue != month,
                            instances = occurrences,
                            lifted = lift?.instance,
                            targeted = target == day && lift?.day != day,
                            modifier = Modifier.weight(1f),
                            onDay = { viewModel.open(day) },
                            onCreate = { onCreate(day) },
                            onOpen = onOpen,
                            onMore = { listing = day },
                            onPlaced = { cells[day] = it },
                            onLift = { instance, grab, width -> lift = Hold(instance, day, grab, width) },
                            onDrag = { finger = it },
                            onDrop = {
                                val lifted = lift
                                lift = null
                                val dropped = under()
                                if (lifted != null && dropped != null && dropped != lifted.day) {
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
        lift?.let { lifted ->
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
                Chip(lifted.instance, Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(4.dp))) {}
            }
        }
    }

    val listed = listing
    if (listed != null) {
        MochiBottomSheet(onDismissRequest = { listing = null }) {
            Text(
                text = LocalFormat.current.formatDate(listed.atStartOfDay(viewModel.timezone()).toEpochSecond()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                items(byDay[listed].orEmpty(), key = { it.event + it.start }) { instance ->
                    AgendaRow(instance, viewModel) {
                        listing = null
                        onOpen(instance)
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(
    day: LocalDate,
    today: LocalDate,
    outside: Boolean,
    instances: List<Instance>,
    lifted: Instance?,
    targeted: Boolean,
    modifier: Modifier,
    onDay: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (Instance) -> Unit,
    onMore: () -> Unit,
    onPlaced: (Rect) -> Unit,
    onLift: (Instance, Offset, Float) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
) {
    val current = day == today
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
        Column(
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            for (instance in instances.take(CHIPS)) {
                val own = lifted != null && lifted.event == instance.event && lifted.start == instance.start
                Chip(
                    instance,
                    Modifier.fillMaxWidth().padding(top = 1.dp).alpha(if (own) 0.4f else 1f),
                    lift = Modifier.lift(instance, onLift, onDrag, onDrop, onCancel),
                ) { onOpen(instance) }
            }
            if (instances.size > CHIPS) {
                Text(
                    text = pluralStringResource(
                        R.plurals.calendars_more,
                        instances.size - CHIPS,
                        instances.size - CHIPS,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onMore).padding(start = 3.dp),
                )
            }
        }
    }
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
 * time, its calendar's colour, its title and where it is.
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
                AgendaRow(instance, viewModel) { onOpen(instance) }
            }
        }
        if (state.paging) {
            item(key = "later") { Paging() }
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

/**
 * One agenda row: time or "All day", the calendar's colour, title and place.
 * The clock reads in each end's own zone when the views show events in
 * theirs.
 */
@Composable
fun AgendaRow(instance: Instance, viewModel: CalendarViewModel, onClick: () -> Unit) {
    val format = LocalFormat.current
    val zones = viewModel.zones()
    val colour = instance.colour.toColour(MaterialTheme.colorScheme.primary)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .padding(end = 4.dp)
                .clip(CircleShape)
                .background(colour)
                .border(1.dp, colour, CircleShape)
                .fillMaxSize(),
        )
        Column(modifier = Modifier.width(76.dp)) {
            Text(
                text = if (instance.allday) {
                    stringResource(R.string.calendars_event_allday)
                } else {
                    format.formatTime(instance.start, clockZone(instance.zone?.start, zones))
                },
                style = MaterialTheme.typography.labelMedium,
            )
            if (!instance.allday && instance.finish > instance.start) {
                Text(
                    text = format.formatTime(instance.finish, clockZone(instance.zone?.finish, zones)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = instance.summary.ifBlank { stringResource(R.string.calendars_event_untitled) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (instance.location.isNotBlank()) {
                Text(
                    text = instance.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}
