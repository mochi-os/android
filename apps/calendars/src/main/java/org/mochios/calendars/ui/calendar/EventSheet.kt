// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.HtmlContent
import org.mochios.android.ui.components.MochiBottomSheet
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.util.Zones
import org.mochios.android.util.flightLink
import org.mochios.android.util.flightNumber
import org.mochios.android.util.isHtml
import org.mochios.android.util.webUri
import org.mochios.android.util.zoneCity
import org.mochios.calendars.R
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.model.Instance

/** How a summary puts its parts together, in the user's language. */
class Wording(
    /** An all-day occurrence's day or run of days. */
    val allday: (String) -> String,
    /** A day and the two times on it: "Monday 28 September 2026, 10:00 to 11:00". */
    val day: (String, String, String) -> String,
    /** Two ends, each a date and a time: "… 22:00 to … 06:00". */
    val range: (String, String) -> String,
)

/**
 * An occurrence's span as its summary reads it, and beneath it, when it was
 * written in another zone than the user's and the views keep the user's, the
 * span read in its own zones. An all-day one reads as its day or its run of
 * days; a timed one as its long date and its times without seconds, the date
 * once when both ends fall on one day. With cities, each end names the city
 * of its zone: "10:00 London to 13:00 New York". The web's summary reads the
 * same.
 */
fun summary(
    instance: Instance,
    format: Format,
    zones: Boolean,
    wording: Wording,
    registry: Zones.Registry = Zones.Platform,
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
        return wording.allday(days) to null
    }
    val startZone = instance.zone?.start?.takeIf { it.isNotBlank() }
    val finishZone = instance.zone?.finish?.takeIf { it.isNotBlank() }
    val foreign = (startZone != null && !Zones.same(startZone, user, registry)) ||
        (finishZone != null && !Zones.same(finishZone, user, registry))

    // The span read in a pair of zones, the user's own when none is given.
    fun describe(start: String?, finish: String?, cities: Boolean): String {
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
    val span = if (zones && foreign) describe(startZone, finishZone, cities = true) else describe(null, null, cities = false)
    val own = if (foreign && !zones) describe(startZone, finishZone, cities = true) else null
    return span to own
}

/**
 * The summary of one occurrence, anchored to the bottom of the screen: what
 * it is called, when it is, whether it is cancelled or tentative, where it
 * is, which calendar it is in and the first of its description, with "Copy"
 * beneath, which [onCopy] answers.
 * Only a read-only occurrence - a subscription's or a birthday - lands here;
 * a tap on an editable one opens the editor. A copy is what a read-only
 * occurrence is for, into a calendar of the user's own.
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
    val allday = stringResource(R.string.calendars_event_allday)
    val day = stringResource(R.string.calendars_span_day)
    val range = stringResource(R.string.calendars_range)
    val (span, own) = summary(
        instance,
        format,
        zones,
        Wording(
            allday = { allday + " · " + it },
            day = { date, from, to -> String.format(day, date, from, to) },
            range = { from, to -> String.format(range, from, to) },
        ),
    )

    MochiBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (instance.untitled) stringResource(R.string.calendars_untitled) else instance.summary,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (instance.untitled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (instance.recurring) {
                    Icon(
                        Icons.Outlined.Repeat,
                        contentDescription = stringResource(R.string.calendars_event_recurring),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = span,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (own != null) {
                Text(
                    text = own,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            status(instance)?.let { label ->
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (instance.location.isNotBlank()) {
                // A flight number opens on the user's flight tracker; anything
                // else is handed to whichever map app the phone has.
                val context = LocalContext.current
                val flight = flightNumber(instance.location)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        if (flight != null) {
                            webUri(flightLink(flight, format.preferences.flights))?.let { target ->
                                runCatching { CustomTabsIntent.Builder().build().launchUrl(context, target) }
                            }
                        } else {
                            try {
                                // launch-ok: a geo: URI the phone's own map app answers, not a web URL
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geo(instance.location))))
                            } catch (_: Exception) {
                                // no map app
                            }
                        }
                    },
                ) {
                    Icon(
                        if (flight != null) Icons.Outlined.Flight else Icons.Outlined.Place,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = instance.location,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The calendar's own colour beside its name, which an event
                // with a colour of its own does not change.
                org.mochios.calendars.ui.components.ColourCheckbox(calendar?.colour ?: instance.colour, shown = true, size = 14.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = calendar?.name.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (instance.description.isNotBlank()) {
                if (isHtml(instance.description)) {
                    // A subscription's description may be HTML, as Google's
                    // are: rendered, so its breaks and emphasis show rather
                    // than their tags.
                    HtmlContent(html = instance.description, maxLines = 6)
                } else {
                    Text(
                        text = instance.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 6,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            MochiOutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.calendars_event_copy))
            }
        }
    }
}

/**
 * A `geo:` URI searching for a free-text location, which the phone's own map
 * app answers; the encoder's `+` for a space becomes `%20`, which every map
 * app reads.
 */
fun geo(location: String): String =
    "geo:0,0?q=" + URLEncoder.encode(location.trim(), "UTF-8").replace("+", "%20")

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
