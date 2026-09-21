// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.model

/** The working hours a day and week view shade, `start` to `finish` of the clock. */
data class Hours(val start: Int = 8, val finish: Int = 17)

/** How much a multiweek view shows: [weeks] rows, [previous] of them before this one. */
data class Multiweek(val weeks: Int = 4, val previous: Int = 0)

/**
 * The user's own calendar preferences, shared with the web through
 * `-/preferences/get` and `-/preferences/set`. [days] are the work days,
 * 0 for Sunday through 6 for Saturday. [duration] is the default event length
 * in minutes and [reminder] the default reminder in minutes before the start,
 * -1 for none. [view] is the view the app opens on.
 */
data class Preferences(
    val hours: Hours = Hours(),
    val days: List<Int> = listOf(1, 2, 3, 4, 5),
    val multiweek: Multiweek = Multiweek(),
    val duration: Int = 60,
    val reminder: Int = 15,
    val view: String = "month",
)

/** The body of `-/preferences/get` and `-/preferences/set`. */
data class PreferencesResponse(val preferences: Preferences = Preferences())

/**
 * The body of `-/link`: the first call mints the token and says where the
 * file is, later calls only say one exists until `regenerate` replaces it.
 */
data class LinkResponse(
    val token: String = "",
    val path: String = "",
    val exists: Boolean = false,
)

/**
 * A connected device's credential as `-/token/list` returns it. [hash]
 * identifies it for deletion; the token itself is shown only when created.
 * [created] and [used] are epoch seconds, [used] 0 until the device first
 * signs in with it. [scopes] separates a device credential (`dav`) from the
 * tokens an ICS link mints (`ics`), which are not devices and are revoked
 * from the calendar's own menu.
 */
data class DeviceToken(
    val hash: String = "",
    val name: String = "",
    val created: Long = 0,
    val used: Long = 0,
    val scopes: List<String> = emptyList(),
    val action: String = "",
) {
    val device: Boolean get() = scopes.contains(DAV)

    companion object {
        const val DAV = "dav"
    }
}

/** The body of `-/token/list`. */
data class TokensResponse(val tokens: List<DeviceToken> = emptyList())

/** The body of `-/token/create`: the credential, shown once. */
data class TokenResponse(val token: String = "")

/** The body of `-/token/delete`. */
data class TokenDeleteResponse(val ok: Boolean = false)
