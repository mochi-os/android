// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.editor

import android.content.res.Resources
import org.mochios.android.i18n.Format
import org.mochios.android.share.Shared
import org.mochios.android.util.Zones
import org.mochios.calendars.R
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * An event as it is shared: its name (untitled where it has none), when it
 * is, how it repeats, its location and its description, each a paragraph of
 * its own, since a feed's post joins lines a single newline parts; and its
 * own address, often none. [own] is the user's zone, which a blank zone of
 * the event's means.
 */
internal fun eventShare(state: EditorUiState, own: String, resources: Resources, locale: Locale, format: Format): Shared {
    val fields = listOf(
        state.title.trim().ifEmpty { resources.getString(R.string.calendars_untitled) },
        eventWhen(state, own, format),
        eventRepeat(state.recurrence, resources, format, locale),
        state.location.trim(),
        state.description.trim(),
    )
    return Shared(text = fields.filter { it.isNotEmpty() }.joinToString("\n\n"), url = state.url.trim())
}

/**
 * How a series repeats, in words: "Every week, on Monday", or "Repeats" for a
 * rule with parts the summary cannot say. Empty for an event that does not.
 */
internal fun eventRepeat(recurrence: Recurrence, resources: Resources, format: Format, locale: Locale): String {
    if (recurrence.rule(allday = true) == null) return ""
    return ruleWords(recurrence, resources, format, locale) ?: resources.getString(R.string.calendars_event_recurring)
}

/**
 * When an event is, as the user's language writes it: its day, or its days
 * for an all-day event that runs longer; for a timed one its day, its clock
 * and its finish, and the zone it was written in.
 */
internal fun eventWhen(state: EditorUiState, own: String, format: Format): String {
    if (state.allday) {
        // An all-day day is held at its UTC midnight, the finish the day after its last.
        val first = Instant.ofEpochSecond(state.start).atZone(ZoneOffset.UTC).toLocalDate()
        val last = Instant.ofEpochSecond(maxOf(state.start, state.finish - DAY)).atZone(ZoneOffset.UTC).toLocalDate()
        return if (first == last) format.formatLongDate(state.start, "UTC") else format.formatDayRange(first, last)
    }
    val begins = state.zone.start.ifBlank { own }
    val ends = state.zone.finish.ifBlank { begins }
    val zones = if (Zones.same(begins, ends)) Zones.label(begins) else format.formatRange(Zones.label(begins), Zones.label(ends))
    return format.formatTimeRange(state.start, state.finish, begins, ends) + " ($zones)"
}

/** Seconds in a day. */
private const val DAY = 86_400L
