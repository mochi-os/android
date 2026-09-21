// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.MochiBottomSheet
import org.mochios.calendars.R
import org.mochios.calendars.model.Instance
import java.time.LocalDate

/** How many chips a cell shows before it collapses the rest into "+N more". */
private const val CHIPS = 3

/**
 * The month and multiweek views: [weeks] rows of seven days, each cell
 * holding its occurrences as chips. In the month view days outside [month]
 * are dimmed but drawn; in the multiweek view [month] is null and every day
 * reads the same.
 *
 * A tap on a chip opens its summary, a tap on empty cell space starts an
 * event on that day, and the day number opens the day view.
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
) {
    val today = LocalDate.now(viewModel.timezone())
    var listing by remember { mutableStateOf<LocalDate?>(null) }
    val byDay = remember(state.instances, state.hidden, weeks) {
        weeks.flatMap { week -> (0 until 7).map { week.plusDays(it.toLong()) } }
            .associateWith { day -> state.visible.filter { viewModel.covers(it, day) } }
    }

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
                        modifier = Modifier.weight(1f),
                        onDay = { viewModel.open(day) },
                        onCreate = { onCreate(day) },
                        onOpen = onOpen,
                        onMore = { listing = day },
                    )
                }
            }
            HorizontalDivider()
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
    modifier: Modifier,
    onDay: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (Instance) -> Unit,
    onMore: () -> Unit,
) {
    val current = day == today
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onCreate)
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .then(
                    if (current) {
                        Modifier.background(MaterialTheme.colorScheme.primary)
                    } else {
                        Modifier
                    },
                )
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
        for (instance in instances.take(CHIPS)) {
            Chip(instance, Modifier.fillMaxWidth().padding(top = 1.dp)) { onOpen(instance) }
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
    val listState = rememberLazyListState()
    val search = state.search.trim()
    val matched = remember(state.instances, state.hidden, search) {
        state.visible.filter { instance ->
            search.isEmpty() ||
                listOf(instance.summary, instance.location, instance.description)
                    .any { it.contains(search, ignoreCase = true) }
        }
    }
    val grouped = remember(matched) { matched.groupBy { viewModel.day(it) }.toSortedMap() }

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
                Text(
                    text = format.formatDate(day.atStartOfDay(viewModel.timezone()).toEpochSecond()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
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

/** One agenda row: time or "All day", the calendar's colour, title and place. */
@Composable
fun AgendaRow(instance: Instance, viewModel: CalendarViewModel, onClick: () -> Unit) {
    val format = LocalFormat.current
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
                    format.formatTime(instance.start)
                },
                style = MaterialTheme.typography.labelMedium,
            )
            if (!instance.allday && instance.finish > instance.start) {
                Text(
                    text = format.formatTime(instance.finish),
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
