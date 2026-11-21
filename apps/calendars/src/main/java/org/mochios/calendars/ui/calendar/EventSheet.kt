// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.MochiBottomSheet
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiSheetHeader
import org.mochios.android.util.Zones
import org.mochios.android.util.descriptionText
import org.mochios.android.util.flightNumber
import org.mochios.android.util.zoneCity
import org.mochios.calendars.R
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.model.Instance
import org.mochios.android.R as MochiR

/** How a summary puts its parts together, in the user's language. */
class Wording(
    /** A day and the two times on it: "Monday 28 September 2026, 10:00 to 11:00". */
    val day: (String, String, String) -> String,
    /** Two ends, each a date and a time: "… 22:00 to … 06:00". */
    val range: (String, String) -> String,
    /**
     * A timed span written whole, from its start, its finish and the zones
     * each is read in; null leaves it to [day] and [range].
     */
    val times: ((Long, Long, String?, String?) -> String)? = null,
)

/**
 * An occurrence's span as its summary reads it, and beneath it, when it was
 * written in another zone than the user's and the views keep the user's, the
 * span read in its own zones. An all-day one reads as its day or its run of
 * days, with nothing before them; a timed one as its long date and its times without seconds, the date
 * once when both ends fall on one day. With cities, each end names the city
 * of its zone: "10:00 London to 13:00 New York". The web's summary reads the
 * same. Without [cities], the span read in its own zones leaves the cities
 * out, for a caller that shows them on a line of their own with [places].
 */
fun summary(
    instance: Instance,
    format: Format,
    zones: Boolean,
    wording: Wording,
    registry: Zones.Registry = Zones.Platform,
    cities: Boolean = true,
): Pair<String, String?> {
    val user = format.preferences.timezone
    val home = zoneOf(user, ZoneId.of("UTC"))
    if (instance.allday) {
        val first = instance.date?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: Instant.ofEpochSecond(instance.start).atZone(home).toLocalDate()
        val last = last(first, instance.start, instance.finish)
        val days = if (first == last) {
            format.formatLongDate(first.atTime(12, 0).atZone(home).toEpochSecond(), home.id)
        } else {
            format.formatDayRange(first, last)
        }
        return days to null
    }
    val startZone = instance.zone?.start?.takeIf { it.isNotBlank() }
    val finishZone = instance.zone?.finish?.takeIf { it.isNotBlank() }
    val foreign = (startZone != null && !Zones.same(startZone, user, registry)) ||
        (finishZone != null && !Zones.same(finishZone, user, registry))

    // The span read in a pair of zones, the user's own when none is given.
    fun describe(start: String?, finish: String?, cities: Boolean): String {
        val whole = wording.times
        if (whole != null && !cities) {
            return whole(instance.start, instance.finish, start, finish)
        }
        fun label(zone: String?) = if (cities) " " + zoneCity(zone ?: user) else ""
        val first = Instant.ofEpochSecond(instance.start).atZone(zoneOf(start, home)).toLocalDate()
        val last = Instant.ofEpochSecond(maxOf(instance.start, instance.finish - 1)).atZone(zoneOf(finish, home)).toLocalDate()
        val from = format.formatTime(instance.start, start) + label(start)
        val to = format.formatTime(instance.finish, finish) + label(finish)
        if (first == last) return wording.day(format.formatLongDate(instance.start, start), from, to)
        return wording.range(
            format.formatLongDate(instance.start, start) + " " + from,
            format.formatLongDate(instance.finish, finish) + " " + to,
        )
    }
    val span = if (zones && foreign) describe(startZone, finishZone, cities) else describe(null, null, cities = false)
    val own = if (foreign && !zones) describe(startZone, finishZone, cities = true) else null
    return span to own
}

/**
 * The cities of the zones a timed occurrence's span reads in, when [zones]
 * has it read in its own and that is not the user's: "Addis Ababa to Asmara",
 * or the one city when both ends share a zone. Null otherwise.
 */
fun places(
    instance: Instance,
    user: String,
    zones: Boolean,
    range: (String, String) -> String,
    registry: Zones.Registry = Zones.Platform,
): String? {
    if (instance.allday || !zones) return null
    val start = instance.zone?.start?.takeIf { zone -> zone.isNotBlank() } ?: user
    val finish = instance.zone?.finish?.takeIf { zone -> zone.isNotBlank() } ?: user
    if (Zones.same(start, user, registry) && Zones.same(finish, user, registry)) return null
    if (Zones.same(start, finish, registry)) return zoneCity(start)
    return range(zoneCity(start), zoneCity(finish))
}

/**
 * A read-only occurrence - a subscription's or a birthday - shown when it is
 * tapped, in a sheet that takes 85% of the screen, under the header every
 * sheet has: its colour and what it is called, with a menu beside them.
 * Beneath, each beside its icon: when it is and whether it is cancelled or
 * tentative, the zones it is written in when they are not the user's, that it
 * repeats, where it is, which calendar it is in and its description. The
 * menu holds "Copy", which [onCopy] answers, into a calendar of the user's
 * own. An occurrence the user can change opens in the editor instead.
 *
 * A timed occurrence written in another zone than the user's names the
 * zones: with [zones] on, the span reads each end in its own zone with the
 * zone's city after it; with it off, the span stays in the user's zone and a
 * second line beneath reads the ends in their own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSheet(
    instance: Instance,
    calendar: Calendar?,
    zones: Boolean = false,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    val format = LocalFormat.current
    val day = stringResource(R.string.calendars_span_day)
    val range = stringResource(R.string.calendars_range)
    val (span, own) = summary(
        instance,
        format,
        zones,
        Wording(
            day = { date, from, to -> String.format(day, date, from, to) },
            range = { from, to -> String.format(range, from, to) },
            times = { start, finish, opens, closes ->
                format.formatTimeRange(start, finish, opens, closes)
            },
        ),
        cities = false,
    )
    val cities = places(instance, format.preferences.timezone, zones, { from, to -> String.format(range, from, to) })
    var menu by remember { mutableStateOf(false) }

    MochiBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            MochiSheetHeader(
                title = if (instance.untitled) stringResource(R.string.calendars_untitled) else instance.summary,
                titleColor = if (instance.untitled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                titleDecoration = if (instance.cancelled) TextDecoration.LineThrough else null,
                titleModifier = Modifier.testTag("sheet-title"),
                leading = {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(instance.colour.toColour(MaterialTheme.colorScheme.primary))
                            .testTag("sheet-dot"),
                    )
                },
            ) {
                Box {
                    MochiIconButton(onClick = { menu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(MochiR.string.common_more_options),
                        )
                    }
                    MochiDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        MochiDropdownMenuItem(
                            text = { Text(stringResource(R.string.calendars_event_copy)) },
                            onClick = {
                                menu = false
                                onCopy()
                            },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 12.dp),
            ) {
                Detail(icon = Icons.Outlined.Schedule) {
                    Text(text = span, style = MaterialTheme.typography.bodyLarge)
                    if (own != null) {
                        Text(
                            text = own,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // A cancelled occurrence's status leads with a ban, a
                // tentative one's with a dashed ring, as the web's has them.
                status(instance)?.let { label ->
                    Detail(
                        lead = {
                            if (instance.cancelled) {
                                Icon(
                                    Icons.Outlined.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Dashed(MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    ) {
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("sheet-status"),
                        )
                    }
                }
                if (cities != null) {
                    Detail(icon = Icons.Outlined.Public) {
                        Text(text = cities, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (instance.recurring) {
                    Detail(icon = Icons.Outlined.Repeat) {
                        Text(text = stringResource(R.string.calendars_event_recurring), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (instance.location.isNotBlank()) {
                    val context = LocalContext.current
                    Detail(
                        icon = if (flightNumber(instance.location) != null) Icons.Outlined.Flight else Icons.Outlined.Place,
                        modifier = Modifier.clickable { openLocation(context, instance.location, format.preferences) },
                    ) {
                        Text(
                            text = instance.location,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Detail(icon = Icons.Outlined.CalendarToday) {
                    Text(text = calendar?.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                }
                // A subscription's description is written by whoever publishes
                // the calendar and may be HTML, as Google's are: it shows as its
                // text, as the web's summary does, so nothing it names is fetched.
                val description = descriptionText(instance.description)
                if (description.isNotBlank()) {
                    Detail(icon = Icons.AutoMirrored.Outlined.Notes) {
                        Text(text = description, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** A tentative occurrence's status glyph: a dashed ring, as the web's has it. */
@Composable
private fun Dashed(colour: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp).testTag("sheet-dashed")) {
        val stroke = 1.5.dp.toPx()
        drawCircle(
            color = colour,
            radius = size.minDimension / 2 - stroke,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
            ),
        )
    }
}

/** One row of the event page: [icon] in the left column and [content] beside it. */
@Composable
private fun Detail(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Detail(
        lead = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = modifier,
        content = content,
    )
}

/** One row of the event page: [lead] in the left column and [content] beside it. */
@Composable
private fun Detail(
    lead: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.TopCenter) {
            lead()
        }
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

/**
 * The line the summary shows for an occurrence's status: "Cancelled" or
 * "Tentative", and nothing for a confirmed one or one that says nothing.
 */
@StringRes
fun status(instance: Instance): Int? = when {
    instance.cancelled -> R.string.calendars_status_cancelled
    instance.tentative -> R.string.calendars_status_tentative
    else -> null
}
