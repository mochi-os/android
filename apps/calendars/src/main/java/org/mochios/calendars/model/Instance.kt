// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.model

import java.time.LocalDate
import java.time.ZoneOffset

/**
 * One occurrence of an event inside a range, as `-/events` returns it. The
 * server expands recurrences itself, so every view renders from this shape.
 *
 * [start] and [finish] are epoch seconds. [date] is present on an all-day
 * occurrence only, as `YYYY-MM-DD`, and is what a view should place it by:
 * an all-day occurrence's [start] is the midnight of that date in the user's
 * own timezone, not in the viewer's. [exception] marks an occurrence the user
 * has detached from its series. [zone] is the zone each end was written in,
 * on a timed occurrence only.
 */
data class Instance(
    val event: String = "",
    val calendar: String = "",
    val colour: String = "",
    val readonly: Boolean = false,
    val uid: String = "",
    val component: String = "",
    val summary: String = "",
    val location: String = "",
    val description: String = "",
    val status: String = "",
    val start: Long = 0,
    val finish: Long = 0,
    val allday: Boolean = false,
    val date: String? = null,
    val recurring: Boolean = false,
    val exception: Boolean = false,
    val zone: Zone? = null,
) {
    /** A birthday occurrence is derived from a contact and has no event to open. */
    val birthday: Boolean get() = event.startsWith(BIRTHDAY)

    /** Whether a tap opens the editor; a read-only occurrence opens the summary sheet instead. */
    val editable: Boolean get() = !readonly && !birthday

    /**
     * The start an override of this occurrence is matched by, which is how
     * the event's tree names it: a timed occurrence's own instant, and the
     * UTC midnight of an all-day one's date, which is what its `DATE` value
     * reads as. [start] itself is the user's midnight for an all-day one.
     */
    val occurrence: Long
        get() = date?.let { value ->
            runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toEpochSecond() }.getOrNull()
        } ?: start

    companion object {
        const val BIRTHDAY = "birthday-"
    }
}

/**
 * The IANA zone each end of a timed occurrence was written in, its `TZID`:
 * a flight can start in one and end in another. Blank means UTC or floating,
 * which is read in the user's own zone.
 */
data class Zone(
    val start: String = "",
    val finish: String = "",
)

/**
 * The body of `-/events`. [truncated] means the range held more occurrences
 * than the server will list, so the view is showing part of it.
 */
data class InstancesResponse(
    val instances: List<Instance> = emptyList(),
    val truncated: Boolean = false,
)
