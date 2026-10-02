// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** How many pages the month pager holds; it opens in the middle, so either way is endless. */
private const val MONTH_PAGES = Int.MAX_VALUE

/** The page the month pager opens on, which shows the month it opened with. */
private const val MONTH_CENTRE = MONTH_PAGES / 2

/** How many months away the small month slides to rather than jumps. */
private const val SLIDE = 3

/** How many months the chip row reaches either side of the month it opened with. */
private const val CHIP_REACH = 120

/** One day cell of the small month. */
private val CELL = 36.dp

/**
 * The date picker that drops down under the toolbar's title, as in Google
 * Calendar: a small month that swipes to the months either side, and a row of
 * month chips under it, with each year's number before its January. A tap
 * on a day calls [onPick] with it, and a tap on a chip with that month's first
 * day; the panel stays open, so several dates can be looked at in turn.
 * [focus], the day the user last chose, is circled and its month shown
 * whenever it moves; [today] is in the primary colour; [weekStart] counts
 * Sunday 0 to Saturday 6. A swipe that settles the small month on another
 * month picks that month's first day, as a tap on its chip does. Without
 * [days], as the month view wants, the panel is the chip row alone. Its page
 * is not saved, for the same reason as the calendar's own pager.
 */
@Composable
fun DatePanel(
    focus: LocalDate,
    today: LocalDate,
    weekStart: Int,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    days: Boolean = true,
) {
    val base = remember { YearMonth.from(focus) }
    val pager = remember { PagerState(currentPage = MONTH_CENTRE) { MONTH_PAGES } }
    val chips = rememberLazyListState(initialFirstVisibleItemIndex = CHIP_REACH - 1)
    val locale = LocalConfiguration.current.locales[0]
    val shown = if (days) {
        base.plusMonths((pager.currentPage - MONTH_CENTRE).toLong())
    } else {
        YearMonth.from(focus)
    }

    LaunchedEffect(YearMonth.from(focus)) {
        if (!days) {
            return@LaunchedEffect
        }
        val target = MONTH_CENTRE + base.until(YearMonth.from(focus), ChronoUnit.MONTHS).toInt()
        if (pager.currentPage != target) {
            if (abs(pager.currentPage - target) <= SLIDE) {
                pager.animateScrollToPage(target)
            } else {
                pager.scrollToPage(target)
            }
        }
    }
    val picked by rememberUpdatedState(YearMonth.from(focus))
    LaunchedEffect(pager) {
        if (!days) {
            return@LaunchedEffect
        }
        snapshotFlow { pager.settledPage }.collect { page ->
            val month = base.plusMonths((page - MONTH_CENTRE).toLong())
            if (month != picked) {
                onPick(month.atDay(1))
            }
        }
    }
    LaunchedEffect(shown) {
        val index = CHIP_REACH + base.until(shown, ChronoUnit.MONTHS).toInt()
        chips.animateScrollToItem((index - 1).coerceIn(0, 2 * CHIP_REACH))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(top = 8.dp, bottom = 8.dp),
    ) {
        if (days) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                verticalAlignment = Alignment.Top,
            ) { page ->
                val month = base.plusMonths((page - MONTH_CENTRE).toLong())
                Month(month, focus, today, weekStart, onPick)
            }
            Spacer(Modifier.height(4.dp))
        }
        LazyRow(
            state = chips,
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(2 * CHIP_REACH + 1) { index ->
                val month = base.plusMonths((index - CHIP_REACH).toLong())
                val current = month == shown
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (month.monthValue == 1) {
                        Text(
                            text = month.year.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilterChip(
                        selected = current,
                        onClick = { onPick(month.atDay(1)) },
                        label = {
                            Text(
                                text = month.month.getDisplayName(TextStyle.SHORT, locale),
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        border = null,
                    )
                }
            }
        }
    }
}

/** How many weeks [month] reaches into, its first starting on [weekStart]. */
private fun rows(month: YearMonth, weekStart: Int): Int =
    (lead(month, weekStart) + month.lengthOfMonth() + 6) / 7

/** How many blank cells come before [month]'s first day in its first week. */
private fun lead(month: YearMonth, weekStart: Int): Int =
    ((month.atDay(1).dayOfWeek.value % 7) - weekStart + 7) % 7

/**
 * One month's weekday letters over its days in rows of seven, as many as the
 * month reaches into, blank outside the month. The month chips sit right under
 * the last row, so they move down for a six-week month and back up after it.
 */
@Composable
private fun Month(
    month: YearMonth,
    focus: LocalDate,
    today: LocalDate,
    weekStart: Int,
    onPick: (LocalDate) -> Unit,
) {
    val lead = lead(month, weekStart)
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (column in 0 until 7) {
                Text(
                    text = weekday(weekStart, column).getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        for (row in 0 until rows(month, weekStart)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val number = row * 7 + column - lead + 1
                    Box(
                        modifier = Modifier.weight(1f).height(CELL),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (number in 1..month.lengthOfMonth()) {
                            Day(month.atDay(number), focus, today, onPick)
                        }
                    }
                }
            }
        }
    }
}

/** A day's number, circled when it is the focus and in the primary colour when it is today. */
@Composable
private fun Day(day: LocalDate, focus: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val picked = day == focus
    val current = day == today
    val circle = MaterialTheme.colorScheme.primaryContainer
    Box(
        modifier = Modifier
            .size(CELL - 4.dp)
            .clip(CircleShape)
            .then(if (picked) Modifier.background(circle) else Modifier)
            .clickable { onPick(day) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (current || picked) FontWeight.Bold else FontWeight.Normal,
            color = when {
                picked -> MaterialTheme.colorScheme.onPrimaryContainer
                current -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** The weekday in [column] of a week that starts on [weekStart], Sunday 0 to Saturday 6. */
private fun weekday(weekStart: Int, column: Int): DayOfWeek {
    val index = (weekStart + column) % 7
    return DayOfWeek.of(if (index == 0) 7 else index)
}
