// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.model

/**
 * One calendar of the signed-in identity, as `-/calendars` returns it.
 * [kind] is `own`, `subscription`, `birthdays` or `linked`; a subscription
 * and a birthday calendar are read-only, which [readonly] says outright. A
 * linked calendar mirrors a collection on another server through a connected
 * account and is written to as the user's own is: [account] is the account it
 * syncs through and [collection] the remote collection's URL. [url] is a
 * subscription's source, [fetched] when the calendar was last fetched and
 * [failure] what went wrong the last time, all empty on a calendar of the
 * user's own.
 */
data class Calendar(
    val id: String = "",
    val fingerprint: String = "",
    val slug: String = "",
    val name: String = "",
    val colour: String = "",
    val kind: String = KIND_OWN,
    val url: String = "",
    val account: String = "",
    val collection: String = "",
    val readonly: Boolean = false,
    val default: Boolean = false,
    val version: Long = 0,
    val fetched: Long = 0,
    val failure: String = "",
    val created: Long = 0,
    val updated: Long = 0,
) {
    val subscription: Boolean get() = kind == KIND_SUBSCRIPTION

    val birthdays: Boolean get() = kind == KIND_BIRTHDAYS

    val linked: Boolean get() = kind == KIND_LINKED

    companion object {
        const val KIND_OWN = "own"
        const val KIND_SUBSCRIPTION = "subscription"
        const val KIND_BIRTHDAYS = "birthdays"
        const val KIND_LINKED = "linked"
    }
}

/** The body of `-/calendars`. */
data class CalendarsResponse(val calendars: List<Calendar> = emptyList())

/** The body of every action that answers one calendar. */
data class CalendarResponse(val calendar: Calendar = Calendar())

/** The body of `-/calendars/poll`: whether the poll changed anything, and the calendar after it. */
data class PollResponse(val changed: Boolean = false, val calendar: Calendar = Calendar())

/** `-/calendars/refresh`: whether syncing the stale linked calendars changed anything. */
data class RefreshResponse(val changed: Boolean = false)

/**
 * One round of `-/calendars/import`. [import] names the file the server
 * staged on the first round, and the next round sends it back with [offset],
 * how far into the file's [total] objects this round reached, until
 * [finished]. The counts are this round's own: written, [skipped] as already
 * in the calendar, and [failed] as unreadable.
 */
data class ImportResponse(
    val import: String = "",
    val offset: Int = 0,
    val total: Int = 0,
    val imported: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val finished: Boolean = false,
)
