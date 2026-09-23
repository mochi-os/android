// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
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
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.HtmlContent
import org.mochios.android.ui.components.MochiBottomSheet
import org.mochios.android.util.flightLink
import org.mochios.android.util.flightNumber
import org.mochios.android.util.isHtml
import org.mochios.android.util.webUri
import org.mochios.calendars.R
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.model.Instance

/**
 * The summary of one occurrence, anchored to the bottom of the screen: what
 * it is called, when it is, where it is, which calendar it is in and the
 * first of its description. Only a read-only occurrence - a subscription's
 * or a birthday - lands here; a tap on an editable one opens the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSheet(
    instance: Instance,
    calendar: Calendar?,
    onDismiss: () -> Unit,
) {
    val format = LocalFormat.current
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
                    text = instance.summary.ifBlank { stringResource(R.string.calendars_event_untitled) },
                    style = MaterialTheme.typography.titleLarge,
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
                text = if (instance.allday) {
                    stringResource(R.string.calendars_event_allday) + " · " + format.formatDate(instance.start)
                } else {
                    format.formatDateTime(instance.start) + " – " + format.formatTime(instance.finish)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
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
                org.mochios.calendars.ui.components.ColourCheckbox(instance.colour, shown = true, size = 14.dp)
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
