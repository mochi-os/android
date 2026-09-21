// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.router

import org.mochios.android.ui.components.LastViewedStore

// Where the app opens. The five views are one screen with a switcher rather
// than five routes, so there is no router screen to pass through: the screen's
// view model resolves the stored token itself through calendarsView() and
// writes the new one back on every switch. The set of calendars shown is a
// separate choice, kept per device by the visibility store, and is never in a
// route either.

/** [LastViewedStore] key for the calendars module. */
const val CALENDARS_FEATURE = "calendars"

/** View tokens written to [LastViewedStore], the same names the server's preferences use. */
object CalendarsSection {
    const val DAY = "day"
    const val WEEK = "week"
    const val MULTIWEEK = "multiweek"
    const val MONTH = "month"
    const val LIST = "list"

    /** What the app opens on when nothing has been stored. */
    const val DEFAULT = MONTH

    val ALL = listOf(DAY, WEEK, MULTIWEEK, MONTH, LIST)
}

/**
 * The view a stored token names. An unknown token — one written by a later
 * release, or a value that never was one — falls back to the default rather
 * than opening on a view that does not exist.
 */
fun calendarsView(stored: String): String =
    if (stored in CalendarsSection.ALL) stored else CalendarsSection.DEFAULT
