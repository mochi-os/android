// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.calendars.R
import org.mochios.calendars.model.Instance
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** The height of one hour row; the whole day is twenty-four of these. */
private val HOUR = 56.dp

/** The gutter the hour labels sit in. */
private val GUTTER = 52.dp

/** The height of one all-day chip in the band above the grid. */
private val BAND = 22.dp

/**
 * The day and week views: an all-day band above a scrolling time grid of
 * [days] columns. Non-working hours are shaded, today carries the current-time
 * line, and occurrences that overlap share the column's width.
 *
 * A tap on an occurrence opens its summary; a tap on empty grid starts an
 * event at that hour on that day.
 */
@Composable
fun TimeGrid(
    days: List<LocalDate>,
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    onOpen: (Instance) -> Unit,
    onCreate: (LocalDate, Int) -> Unit,
) {
    val format = LocalFormat.current
    val scroll = rememberScrollState()
    val today = LocalDate.now(viewModel.timezone())
    val columns = LocalConfiguration.current.screenWidthDp.dp - GUTTER
    val width = columns / days.size.coerceAtLeast(1)

    val byDay = remember(state.instances, state.hidden, days) {
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
                        onOpen = onOpen,
                        onCreate = onCreate,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeading(day: LocalDate, today: LocalDate, width: androidx.compose.ui.unit.Dp, modifier: Modifier) {
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
    width: androidx.compose.ui.unit.Dp,
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
    width: androidx.compose.ui.unit.Dp,
    instances: List<Instance>,
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    onOpen: (Instance) -> Unit,
    onCreate: (LocalDate, Int) -> Unit,
) {
    val shading = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val line = MaterialTheme.colorScheme.error
    val working = state.preferences.days.contains(day.dayOfWeek.value % 7)
    val layout = remember(instances, day) { lay(instances, viewModel, day) }

    Box(modifier = Modifier.width(width).height(HOUR * 24)) {
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
            Box(
                modifier = Modifier
                    .offset(x = columnWidth * placed.column, y = HOUR * placed.top)
                    .width(columnWidth)
                    .height((HOUR * placed.height).coerceAtLeast(18.dp))
                    .padding(end = 2.dp),
            ) {
                Block(placed.instance) { onOpen(placed.instance) }
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

/** One occurrence placed in a day column. */
private data class Placed(
    val instance: Instance,
    val top: Float,
    val height: Float,
    val column: Int,
    val columns: Int,
)

/**
 * Lays a day's timed occurrences out, giving overlapping ones a share of the
 * width each. Occurrences are taken in start order and put in the first
 * column whose last occurrence has finished; a run that overlaps is then as
 * wide as the columns it needed.
 */
private fun lay(instances: List<Instance>, viewModel: CalendarViewModel, day: LocalDate): List<Placed> {
    if (instances.isEmpty()) return emptyList()
    val zone = viewModel.timezone()
    val midnight = day.atStartOfDay(zone).toEpochSecond()
    val ends = midnight + 86_400
    data class Cut(val instance: Instance, val from: Float, val to: Float)

    val cuts = instances
        .map { instance ->
            val from = ((instance.start.coerceAtLeast(midnight) - midnight) / 3600f).coerceIn(0f, 24f)
            val raw = if (instance.finish > instance.start) instance.finish else instance.start + 1800
            val to = ((raw.coerceAtMost(ends) - midnight) / 3600f).coerceIn(0f, 24f)
            Cut(instance, from, maxOf(to, from + 0.25f))
        }
        .sortedBy { it.from }

    val out = mutableListOf<Placed>()
    var group = mutableListOf<Cut>()
    var columns = mutableListOf<Float>()
    var assigned = mutableListOf<Int>()

    fun flush() {
        if (group.isEmpty()) return
        val total = columns.size
        for ((index, cut) in group.withIndex()) {
            out.add(Placed(cut.instance, cut.from, cut.to - cut.from, assigned[index], total))
        }
        group = mutableListOf()
        columns = mutableListOf()
        assigned = mutableListOf()
    }

    for (cut in cuts) {
        if (columns.isNotEmpty() && columns.all { it <= cut.from }) flush()
        var column = columns.indexOfFirst { it <= cut.from }
        if (column < 0) {
            columns.add(cut.to)
            column = columns.size - 1
        } else {
            columns[column] = cut.to
        }
        group.add(cut)
        assigned.add(column)
    }
    flush()
    return out
}

/** A timed occurrence's block: the calendar's colour, faded, with a solid leading edge. */
@Composable
fun Block(instance: Instance, onClick: () -> Unit) {
    val format = LocalFormat.current
    val colour = instance.colour.toColour(MaterialTheme.colorScheme.primary)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .background(colour.copy(alpha = 0.22f))
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxSize().background(colour))
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text(
                text = instance.summary.ifBlank { stringResource(R.string.calendars_event_untitled) },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = format.formatTime(instance.start),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** An all-day or month-grid chip: one line in the calendar's colour. */
@Composable
fun Chip(instance: Instance, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colour = instance.colour.toColour(MaterialTheme.colorScheme.primary)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colour.copy(alpha = 0.22f))
            .clickable(onClick = onClick),
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
fun Instance.moment(zone: java.time.ZoneId): java.time.ZonedDateTime =
    Instant.ofEpochSecond(start).atZone(zone)
