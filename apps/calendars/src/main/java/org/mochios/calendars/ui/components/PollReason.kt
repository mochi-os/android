// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.components

/**
 * Why a subscription's last fetch failed, as the server records it. The
 * server writes a token rather than a sentence — it has no screen to write
 * for — so the drawer turns one into words.
 */
sealed interface PollReason {

    /** The calendar was larger than the server will take. */
    object Large : PollReason

    /** What the address returned was not a calendar. */
    object Invalid : PollReason

    /** Nothing answered at the address. */
    object Unreachable : PollReason

    /** The address answered, with [code]. */
    data class Status(val code: Int) : PollReason

    /**
     * A token this release has no words for, shown as it came. A server one
     * release ahead can record a reason the phone does not know, and a blank
     * line would be worse than a bare token.
     */
    data class Other(val token: String) : PollReason
}

/** The reason a failure token names, or null when the last fetch succeeded. */
fun pollReason(failure: String): PollReason? {
    val token = failure.trim()
    if (token.isEmpty()) return null
    if (token == "too_large") return PollReason.Large
    if (token == "invalid") return PollReason.Invalid
    if (token.startsWith(STATUS)) {
        val code = token.removePrefix(STATUS).toIntOrNull()
        // The server writes a status of 0 when nothing answered at all.
        if (code == 0) return PollReason.Unreachable
        if (code != null) return PollReason.Status(code)
    }
    return PollReason.Other(token)
}

/** How the server prefixes a failure it has only an HTTP status for. */
private const val STATUS = "status:"
