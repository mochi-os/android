// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.model

/**
 * A connected account a calendar can be linked through, as
 * `-/calendars/accounts` lists it. [type] is `google`, `apple` or `caldav`,
 * [label] what the user called it and [identifier] the address it signs in
 * as. [granted] holds the capabilities the account carries now: a Google
 * account linked for sign-in alone holds `login` and cannot be linked until
 * calendar access is granted, a consent the wizard asks Google for.
 */
data class CalendarAccount(
    val id: String = "",
    val type: String = "",
    val label: String = "",
    val identifier: String = "",
    val granted: List<String> = emptyList(),
) {
    /** Whether the account may hold calendars now. */
    val calendars: Boolean get() = CAPABILITY_CALENDAR in granted

    companion object {
        const val TYPE_GOOGLE = "google"
        const val TYPE_APPLE = "apple"
        const val TYPE_CALDAV = "caldav"
        const val CAPABILITY_CALENDAR = "calendar"
    }
}

/**
 * The body of `-/calendars/accounts`: the accounts that can hold a calendar,
 * and the OAuth provider types the server can grant a new one from.
 */
data class AccountsResponse(
    val accounts: List<CalendarAccount> = emptyList(),
    val providers: List<String> = emptyList(),
    /** Whether the user may set a missing provider up in the system settings. */
    val administrator: Boolean = false,
)

/** The body of `-/calendars/account`: the account the server kept. */
data class AccountResponse(val account: CalendarAccount = CalendarAccount())

/**
 * One calendar an account's server offers, as `-/calendars/remote` lists it.
 * [href] is the collection to link, [colour] "#rrggbb" or empty, and [linked]
 * the id of the calendar here that already mirrors it, or empty.
 */
data class RemoteCalendar(
    val href: String = "",
    val name: String = "",
    val description: String = "",
    val colour: String = "",
    val readonly: Boolean = false,
    val linked: String = "",
)

/** The body of `-/calendars/remote`. */
data class RemoteResponse(val calendars: List<RemoteCalendar> = emptyList())

/**
 * What `-/calendars/grant` answers a native app: the provider's consent to
 * open in the system browser, and the nonce the return carries so the app
 * can tell its own return from an injected one (null against an older
 * server).
 */
data class GrantResponse(
    val url: String = "",
    val nonce: String? = null,
)
