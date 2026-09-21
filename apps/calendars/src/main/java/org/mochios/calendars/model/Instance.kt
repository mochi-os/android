// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.model

/**
 * One occurrence of an event inside a range, as `-/events` returns it. The
 * server expands recurrences itself, so every view renders from this shape.
 *
 * [start] and [finish] are epoch seconds. [date] is present on an all-day
 * occurrence only, as `YYYY-MM-DD`, and is what a view should place it by:
 * an all-day occurrence's [start] is the midnight of that date in the user's
 * own timezone, not in the viewer's. [exception] marks an occurrence the user
 * has detached from its series.
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
) {
    /** A birthday occurrence is derived from a contact and has no event to open. */
    val birthday: Boolean get() = event.startsWith(BIRTHDAY)

    companion object {
        const val BIRTHDAY = "birthday-"
    }
}

/**
 * The body of `-/events`. [truncated] means the range held more occurrences
 * than the server will list, so the view is showing part of it.
 */
data class InstancesResponse(
    val instances: List<Instance> = emptyList(),
    val truncated: Boolean = false,
)
